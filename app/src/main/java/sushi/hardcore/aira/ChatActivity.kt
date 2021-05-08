package sushi.hardcore.aira

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import sushi.hardcore.aira.adapters.ChatAdapter
import sushi.hardcore.aira.background_service.*
import sushi.hardcore.aira.databinding.ActivityChatBinding
import sushi.hardcore.aira.databinding.DialogFingerprintsBinding
import sushi.hardcore.aira.databinding.DialogInfoBinding
import sushi.hardcore.aira.utils.FileUtils
import sushi.hardcore.aira.utils.StringUtils

class ChatActivity : ServiceBoundActivity() {
    private external fun generateFingerprint(publicKey: ByteArray): String

    private lateinit var binding: ActivityChatBinding
    private var sessionId = -1
    private lateinit var sessionName: String
    private lateinit var chatAdapter: ChatAdapter
    private var lastLoadedMessageOffset = 0
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (isServiceInitialized() && uris.size > 0) {
            airaService.sendFilesFromUris(sessionId, uris)?.let { msgs ->
                for (msg in msgs) {
                    chatAdapter.newMessage(ChatItem(true, msg))
                    if (airaService.contacts.contains(sessionId)) {
                        lastLoadedMessageOffset += 1
                    }
                }
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
                    layoutManager = LinearLayoutManager(this@ChatActivity, LinearLayoutManager.VERTICAL, false).apply {
                        stackFromEnd = true
                    }
                    addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        fun loadMsgsIfNeeded(recyclerView: RecyclerView) {
                            if (!recyclerView.canScrollVertically(-1) && isServiceInitialized()) {
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
                serviceConnection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                        val binder = service as AIRAService.AIRABinder
                        airaService = binder.getService()

                        chatAdapter.clear()
                        airaService.contacts[sessionId]?.let { contact ->
                            displayIconTrustLevel(true, contact.verified)
                            loadMsgs(contact.uuid)
                        }
                        airaService.savedMsgs[sessionId]?.let {
                            for (chatItem in it.asReversed()) {
                                chatAdapter.newLoadedMessage(chatItem)
                            }
                        }
                        airaService.receiveFileTransfers[sessionId]?.let {
                            if (it.shouldAsk) {
                                it.ask(this@ChatActivity, sessionName)
                            }
                        }
                        binding.recyclerChat.smoothScrollToPosition(chatAdapter.itemCount)
                        val showBottomPanel = {
                            findViewById<ConstraintLayout>(R.id.bottom_panel).visibility = View.VISIBLE
                        }
                        airaService.uiCallbacks = object : AIRAService.UiCallbacks {
                            override fun onNewSession(sessionId: Int, ip: String) {
                                if (this@ChatActivity.sessionId == sessionId) {
                                    runOnUiThread {
                                        showBottomPanel()
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
                                    airaService.isAppInBackground
                                } else {
                                    false
                                }
                            }

                            override fun onAskLargeFiles(sessionId: Int, name: String, filesReceiver: FilesReceiver): Boolean {
                                return if (this@ChatActivity.sessionId == sessionId) {
                                    runOnUiThread {
                                        filesReceiver.ask(this@ChatActivity, name)
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                        airaService.isAppInBackground = false
                        if (airaService.isOnline(sessionId)) {
                            showBottomPanel()
                            binding.recyclerChat.updatePadding(bottom = 0)
                        }
                        airaService.setSeen(sessionId, true)
                    }
                    override fun onServiceDisconnected(name: ComponentName?) {}
                }
            }
        }
    }

    private fun displayIconTrustLevel(isContact: Boolean, isVerified: Boolean) {
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

    private fun loadMsgs(contactUuid: String) {
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
                val dialogBinding = DialogInfoBinding.inflate(layoutInflater)
                dialogBinding.textAvatar.setLetterFrom(sessionName)
                dialogBinding.textFingerprint.text = StringUtils.beautifyFingerprint(generateFingerprint(publicKey!!))
                if (session == null) {
                    dialogBinding.onlineFields.visibility = View.GONE
                } else {
                    dialogBinding.textIp.text = session.ip
                    dialogBinding.textOutgoing.text = getString(if (session.outgoing) {
                        R.string.outgoing
                    } else {
                        R.string.incoming
                    })
                }
                AlertDialog.Builder(this)
                        .setTitle(sessionName)
                        .setView(dialogBinding.root)
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
                    val dialogBinding = DialogFingerprintsBinding.inflate(layoutInflater)
                    dialogBinding.textLocalFingerprint.text = localFingerprint
                    dialogBinding.textPeerFingerprint.text = peerFingerprint
                    AlertDialog.Builder(this)
                        .setTitle(R.string.verifying_contact)
                        .setView(dialogBinding.root)
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
        if (isServiceInitialized()) {
            airaService.setSeen(sessionId, true)
        }
    }

    override fun onPause() {
        super.onPause()
        lastLoadedMessageOffset = 0
    }

    private fun onClickSaveFile(fileName: String, rawUuid: ByteArray) {
        val buffer = AIRADatabase.loadFile(rawUuid)
        if (buffer == null) {
            Toast.makeText(this, R.string.loadFile_failed, Toast.LENGTH_SHORT).show()
        } else {
            FileUtils.openFileForDownload(this, fileName)?.apply {
                write(buffer)
                close()
                Toast.makeText(this@ChatActivity, R.string.file_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }
}