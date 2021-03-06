package com.example.chatme

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.EmojiPopup
import com.vanniktech.emoji.google.GoogleEmojiProvider
import kotlinx.android.synthetic.main.activity_chat.*
import kotlinx.android.synthetic.main.list_item.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


const val UID = "uid"
const val NAME = "name"
const val IMAGE = "photo"



class ChatActivity : AppCompatActivity() {

    private val name : String by lazy {
        intent.getStringExtra(NAME)!!
    }

    private val friendid : String by lazy {
        intent.getStringExtra(UID)!!
    }
    private val image : String by lazy {
        intent.getStringExtra(IMAGE)!!
    }

    private val mCurrentUid : String by lazy {
        FirebaseAuth.getInstance().uid!!
    }

    private val db :FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance()
    }

    lateinit var currentUser:User

    private val  messages = mutableListOf<ChatEvent>()

    lateinit var chatAdapter:ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        EmojiManager.install(GoogleEmojiProvider())
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chat)

        FirebaseFirestore.getInstance().collection("users").document(mCurrentUid).get()
                .addOnSuccessListener {
                    currentUser = it.toObject(User::class.java)!!
                }

        chatAdapter = ChatAdapter(messages,mCurrentUid)

        msgRv.apply{
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = chatAdapter
        }

        nameTv.text = name
        Picasso.get().load(image).into(dp)

        listenToMessages()

        val emojiPopup = EmojiPopup.Builder.fromRootView(rootView).build(msgEdtv)

        smileBtn.setOnClickListener {
            emojiPopup.toggle()
        }
        pic.setOnClickListener {
            val intent = Intent()
            intent.action= Intent.ACTION_GET_CONTENT
            intent.type= "image/*"
            startActivityForResult(Intent.createChooser(intent,"Pick Image"),438)
        }

        swipeToLoad.setOnRefreshListener {
            val workerScope = CoroutineScope(Dispatchers.Main)
            workerScope.launch {
                delay(2000)
                swipeToLoad.isRefreshing = false
            }
        }

       sendBtn.setOnClickListener {
           msgEdtv.text?.let {
               if (it.isNotEmpty()){
                   sendMessage(it.toString())
                   it.clear()
               }
           }
       }

        updateReadCount()

        toolbar.setNavigationOnClickListener {
            startActivity(Intent(this,MainActivity::class.java))
        }

    }

    private fun updateReadCount(){
        getInbox(mCurrentUid,friendid).child("count").setValue(0)
    }



    private fun listenToMessages(){
        getMessages(friendid)
                .orderByKey()
                .addChildEventListener(object :ChildEventListener{
                    override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                       val msg = snapshot.getValue(Message::class.java)!!
                        addMessage(msg)
                    }

                    override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun onChildRemoved(snapshot: DataSnapshot) {
                        TODO("Not yet implemented")
                    }

                    override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        TODO("Not yet implemented")
                    }

                })
    }

    private fun addMessage(msg: Message) {
        val eventBefore = messages.lastOrNull()

        if((eventBefore!=null && !eventBefore.sentAt.isSameDayAs(msg.sentAt) )|| eventBefore ==null){
            messages.add(DateHeader(msg.sentAt,context = this))

        }
        messages.add(msg)

        chatAdapter.notifyItemInserted(messages.size - 1 )
        msgRv.scrollToPosition(messages.size - 1)
    }

    private fun sendMessage(msg: String) {
        val id = getMessages(friendid).push().key
        checkNotNull(id){"Cannot be null"}
        val msgMap = Message(msg,mCurrentUid,id)
        getMessages(friendid).child(id).setValue(msgMap).addOnSuccessListener {

        }.addOnFailureListener {

        }
        updateMessage(msgMap)

    }

    private fun updateMessage(msgMap: Message) {
        val inboxMap = Inbox(
                msgMap.msg,
                friendid,
                name,
                image,
                count = 0
        )

        getInbox(mCurrentUid,friendid).setValue(inboxMap).addOnSuccessListener {
            getInbox(friendid,mCurrentUid).addListenerForSingleValueEvent(object: ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    val value = snapshot.getValue(Inbox::class.java)

                    inboxMap.apply {
                        from = msgMap.senderId
                        name = currentUser.name
                        image = currentUser.thumbImage
                        count = 1
                    }
                    value?.let{
                        if (it.from == msgMap.senderId){
                            inboxMap.count = value.count + 1
                        }
                    }
                    getInbox(friendid, mCurrentUid).setValue(inboxMap)
                }

                override fun onCancelled(error: DatabaseError) {
                    TODO("Not yet implemented")
                }

            })
        }
    }

    private fun markAsRead(){
        getInbox(friendid,mCurrentUid).child("count").setValue(0)
    }

    private fun getInbox(toUser:String,fromUser:String) = db.reference.child("chats/$toUser/$fromUser")

    private fun getMessages(friendId: String) = db.reference.child("messages/${getId(friendId)}") 

    private fun getId(friendId:String):String{  // id for the messages
        return if(friendId > mCurrentUid){
            mCurrentUid + friendId
        }else{
            friendId + mCurrentUid
        }
    }
    companion object {

        fun createChatActivity(context: Context, id: String, name: String, image: String): Intent {
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra(UID,id)
            intent.putExtra(NAME,name)
            intent.putExtra(IMAGE,image)

            return intent
        }
    }


}


