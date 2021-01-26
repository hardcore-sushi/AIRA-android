package sushi.hardcore.aira

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import sushi.hardcore.aira.adapters.ChatAdapter
import sushi.hardcore.aira.background_service.AIRAService
import sushi.hardcore.aira.background_service.Protocol
import sushi.hardcore.aira.background_service.ReceiveFileTransfer
import sushi.hardcore.aira.databinding.ActivityChatBinding
import sushi.hardcore.aira.utils.FileUtils
import sushi.hardcore.aira.utils.StringUtils
import java.util.*

class ChatActivity : AppCompatActivity() {
    private external fun generateFingerprint(publicKey: ByteArray): String

    private lateinit var binding: ActivityChatBinding
    private var sessionId = -1
    private lateinit var sessionName: String
    private lateinit var airaService: AIRAService
    private lateinit var chatAdapter: ChatAdapter
    private var lastLoadedMessageOffset = 0
    private var isActivityInForeground = false
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (::airaService.isInitialized) {
            contentResolver.query(uri, null, null, null, null)?.let { cursor ->
                if (cursor.moveToFirst()) {
                    contentResolver.openInputStream(uri)?.let { inputStream ->
                        val fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                        val fileSize = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
                        airaService.sendFileTo(sessionId, fileName, fileSize, inputStream)?.let { msg ->
                            chatAdapter.newMessage(ChatItem(true, msg))
                        }
                        if (airaService.contacts.contains(sessionId)) {
                            lastLoadedMessageOffset += 1
                        }
                    }
                }
                cursor.close()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        sessionId = intent.getIntExtra("sessionId", -1)
        if (sessionId != -1) {
            intent.getStringExtra("sessionName")?.let { name ->
                sessionName = name
                title = name

                chatAdapter = ChatAdapter(this@ChatActivity, ::onClickSaveFile)
                binding.recyclerChat.apply {
                    adapter = chatAdapter
                    layoutManager = LinearLayoutManager(this@ChatActivity, LinearLayoutManager.VERTICAL, false)
                    addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        fun loadMsgsIfNeeded(recyclerView: RecyclerView) {
                            if (!recyclerView.canScrollVertically(-1) && ::airaService.isInitialized) {
                                airaService.contacts[sessionId]?.let { contact ->
                                    loadMsgs(contact.uuid)
                                }
                            }
                        }
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            loadMsgsIfNeeded(recyclerView)
                        }
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            loadMsgsIfNeeded(recyclerView)
                        }
                    })
                }
                Intent(this, AIRAService::class.java).also { serviceIntent ->
                    bindService(serviceIntent, object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                            val binder = service as AIRAService.AIRABinder
                            airaService = binder.getService()

                            airaService.contacts[sessionId]?.let { contact ->
                                displayIconTrustLevel(true, contact.verified)
                                loadMsgs(contact.uuid)
                            }
                            airaService.receiveFileTransfers[sessionId]?.let {
                                if (it.shouldAsk) {
                                    it.ask(this@ChatActivity, sessionName)
                                }
                            }
                            airaService.savedMsgs[sessionId]?.let {
                                for (chatItem in it.asReversed()) {
                                    chatAdapter.newLoadedMessage(chatItem)
                                }
                            }
                            binding.recyclerChat.smoothScrollToPosition(chatAdapter.itemCount)
                            val onConnected = {
                                findViewById<ConstraintLayout>(R.id.bottom_panel).visibility = View.VISIBLE
                                binding.buttonSend.setOnClickListener {
                                    val msg = binding.editMessage.text.toString()
                                    airaService.sendTo(sessionId, Protocol.newMessage(msg))
                                    binding.editMessage.text.clear()
                                    chatAdapter.newMessage(ChatItem(true, Protocol.newMessage(msg)))
                                    if (airaService.contacts.contains(sessionId)) {
                                        lastLoadedMessageOffset += 1
                                    }
                                    binding.recyclerChat.smoothScrollToPosition(chatAdapter.itemCount)
                                }
                                binding.buttonAttach.setOnClickListener {
                                    filePicker.launch("*/*")
                                }
                            }
                            airaService.uiCallbacks = object : AIRAService.UiCallbacks {
                                override fun onNewSession(sessionId: Int, ip: String) {
                                    if (this@ChatActivity.sessionId == sessionId) {
                                        runOnUiThread {
                                            onConnected()
                                        }
                                    }
                                }
                                override fun onSessionDisconnect(sessionId: Int) {
                                    if (this@ChatActivity.sessionId == sessionId) {
                                        runOnUiThread {
                                            findViewById<ConstraintLayout>(R.id.bottom_panel).visibility = View.GONE
                                            binding.buttonSend.setOnClickListener(null)
                                            binding.buttonAttach.setOnClickListener(null)
                                        }
                                    }
                                }
                                override fun onNameTold(sessionId: Int, name: String) {
                                    if (this@ChatActivity.sessionId == sessionId) {
                                        runOnUiThread {
                                            sessionName = name
                                            title = name
                                        }
                                    }
                                }
                                override fun onNewMessage(sessionId: Int, data: ByteArray): Boolean {
                                    return if (this@ChatActivity.sessionId == sessionId) {
                                        runOnUiThread {
                                            chatAdapter.newMessage(ChatItem(false, data))
                                            binding.recyclerChat.smoothScrollToPosition(chatAdapter.itemCount)
                                        }
                                        if (airaService.contacts.contains(sessionId)) {
                                            lastLoadedMessageOffset += 1
                                        }
                                        isActivityInForeground
                                    } else {
                                        false
                                    }
                                }

                                override fun onAskLargeFile(sessionId: Int, name: String, receiveFileTransfer: ReceiveFileTransfer): Boolean {
                                    return if (this@ChatActivity.sessionId == sessionId) {
                                        runOnUiThread {
                                            receiveFileTransfer.ask(this@ChatActivity, name)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                            }
                            airaService.isAppInBackground = false
                            if (airaService.isOnline(sessionId)) {
                                onConnected()
                            }
                            airaService.setSeen(sessionId, true)
                        }
                        override fun onServiceDisconnected(name: ComponentName?) {}
                    }, Context.BIND_AUTO_CREATE)
                }
            }
        }
    }

    fun displayIconTrustLevel(isContact: Boolean, isVerified: Boolean) {
        when {
            isVerified -> {
                binding.imageTrustLevel.setImageResource(R.drawable.ic_verified)
            }
            isContact -> {
                binding.imageTrustLevel.setImageDrawable(null)
            }
            else -> {
                binding.imageTrustLevel.setImageResource(R.drawable.ic_warning)
            }
        }
    }

    fun loadMsgs(contactUuid: String) {
        AIRADatabase.loadMsgs(contactUuid, lastLoadedMessageOffset, Constants.MSG_LOADING_COUNT)?.let {
            for (chatItem in it.asReversed()) {
                chatAdapter.newLoadedMessage(chatItem)
            }
            lastLoadedMessageOffset += it.size
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_activity, menu)
        val contact = airaService.contacts[sessionId]
        if (contact == null){
            menu.findItem(R.id.delete_conversation).isVisible = false
            menu.findItem(R.id.set_as_contact).isVisible = true
            menu.findItem(R.id.remove_contact).isVisible = false
            menu.findItem(R.id.verify).isVisible = false
        } else {
            menu.findItem(R.id.delete_conversation).isVisible = true
            menu.findItem(R.id.set_as_contact).isVisible = false
            menu.findItem(R.id.remove_contact).isVisible = true
            menu.findItem(R.id.verify).isVisible = !contact.verified
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.session_info -> {
                val contact = airaService.contacts[sessionId]
                val session = airaService.sessions[sessionId]
                val publicKey = contact?.publicKey ?: session?.peerPublicKey
                val dialogView = layoutInflater.inflate(R.layout.dialog_info, null)
                dialogView.findViewById<TextView>(R.id.text_fingerprint).text = StringUtils.beautifyFingerprint(generateFingerprint(publicKey!!))
                if (session == null) {
                    dialogView.findViewById<LinearLayout>(R.id.online_fields).visibility = View.GONE
                } else {
                    dialogView.findViewById<TextView>(R.id.text_ip).text = session.ip
                    dialogView.findViewById<TextView>(R.id.text_outgoing).text = getString(if (session.outgoing) {
                        R.string.outgoing
                    } else {
                        R.string.incoming
                    })
                }
                AlertDialog.Builder(this)
                        .setTitle(sessionName)
                        .setView(dialogView)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                true
            }
            R.id.set_as_contact -> {
                if (airaService.setAsContact(sessionId, sessionName)) {
                    invalidateOptionsMenu()
                    displayIconTrustLevel(true, false)
                }
                true
            }
            R.id.remove_contact -> {
                AlertDialog.Builder(this)
                        .setTitle(R.string.warning)
                        .setMessage(R.string.ask_remove_contact)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            if (airaService.removeContact(sessionId)) {
                                invalidateOptionsMenu()
                                displayIconTrustLevel(false, false)
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                true
            }
            R.id.verify -> {
                airaService.contacts[sessionId]?.let { contact ->
                    val localFingerprint = StringUtils.beautifyFingerprint(AIRADatabase.getIdentityFingerprint())
                    val peerFingerprint = StringUtils.beautifyFingerprint(generateFingerprint(contact.publicKey))
                    val dialogView = layoutInflater.inflate(R.layout.dialog_fingerprints, null)
                    dialogView.findViewById<TextView>(R.id.text_local_fingerprint).text = localFingerprint
                    dialogView.findViewById<TextView>(R.id.text_peer_fingerprint).text = peerFingerprint
                    AlertDialog.Builder(this)
                        .setTitle(R.string.verifying_contact)
                        .setView(dialogView)
                        .setPositiveButton(R.string.they_match) { _, _ ->
                            if (airaService.setVerified(sessionId)) {
                                invalidateOptionsMenu()
                                displayIconTrustLevel(true, true)
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
                true
            }
            R.id.delete_conversation -> {
                AlertDialog.Builder(this)
                        .setTitle(R.string.warning)
                        .setMessage(R.string.ask_delete_conversation)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            if (airaService.deleteConversation(sessionId)) {
                                chatAdapter.clear()
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityInForeground = true
        if (::airaService.isInitialized) {
            airaService.setSeen(sessionId, true)
            airaService.isAppInBackground = false
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityInForeground = false
        if (::airaService.isInitialized) {
            airaService.isAppInBackground = true
        }
    }

    private fun onClickSaveFile(fileName: String, rawUuid: ByteArray) {
        FileUtils.openFileForDownload(this, fileName)?.apply {
            AIRADatabase.loadFile(rawUuid)?.let {
                write(it)
                Toast.makeText(this@ChatActivity, R.string.file_saved, Toast.LENGTH_SHORT).show()
            }
            close()
        }
    }
}