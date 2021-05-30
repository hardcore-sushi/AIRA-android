package sushi.hardcore.aira

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
    private var sessionName: String? = null
    private var avatar: ByteArray? = null
    private lateinit var chatAdapter: ChatAdapter
    private var lastLoadedMessageOffset = 0
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (isServiceInitialized() && uris.size > 0) {
            airaService.sendFilesFromUris(sessionId, uris)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        sessionId = intent.getIntExtra("sessionId", -1)
        if (sessionId != -1) {
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
            binding.toolbar.toolbar.setOnClickListener {
                showSessionInfo()
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
                override fun onServiceConnected(componentName: ComponentName?, service: IBinder) {
                    val binder = service as AIRAService.AIRABinder
                    airaService = binder.getService()

                    chatAdapter.clear()
                    val contact = airaService.contacts[sessionId]
                    val avatar = if (contact == null) {
                        sessionName = airaService.savedNames[sessionId]
                        airaService.savedAvatars[sessionId]
                    } else {
                        sessionName = contact.name
                        contact.avatar
                    }
                    val ipName = sessionName ?: airaService.sessions[sessionId]!!.ip
                    binding.toolbar.title.text = ipName
                    if (avatar == null) {
                        binding.toolbar.avatar.setTextAvatar(sessionName)
                    } else {
                        AIRADatabase.loadAvatar(avatar)?.let { image ->
                            this@ChatActivity.avatar = image
                            binding.toolbar.avatar.setImageAvatar(image)
                        }
                    }
                    if (contact != null) {
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
                            it.ask(this@ChatActivity, ipName)
                        }
                    }
                    binding.recyclerChat.smoothScrollToPosition(chatAdapter.itemCount)
                    val showBottomPanel = {
                        binding.bottomPanel.visibility = View.VISIBLE
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
                                    val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                    inputManager.hideSoftInputFromWindow(binding.editMessage.windowToken, 0)
                                    binding.bottomPanel.visibility = View.GONE
                                    invalidateOptionsMenu()
                                }
                            }
                        }
                        override fun onNameTold(sessionId: Int, name: String) {
                            if (this@ChatActivity.sessionId == sessionId) {
                                runOnUiThread {
                                    sessionName = name
                                    binding.toolbar.title.text = name
                                    if (avatar == null) {
                                        binding.toolbar.avatar.setTextAvatar(name)
                                    }
                                }
                            }
                        }
                        override fun onAvatarChanged(sessionId: Int, avatar: ByteArray?) {
                            if (this@ChatActivity.sessionId == sessionId) {
                                runOnUiThread {
                                    this@ChatActivity.avatar = avatar
                                    if (avatar == null) {
                                        binding.toolbar.avatar.setTextAvatar(sessionName)
                                    } else {
                                        binding.toolbar.avatar.setImageAvatar(avatar)
                                    }
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
                                !airaService.isAppInBackground
                            } else {
                                false
                            }
                        }
                        override fun onAskLargeFiles(sessionId: Int, filesReceiver: FilesReceiver): Boolean {
                            return if (this@ChatActivity.sessionId == sessionId) {
                                runOnUiThread {
                                    filesReceiver.ask(this@ChatActivity, sessionName ?: airaService.sessions[sessionId]!!.ip)
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
        val isOnline = airaService.isOnline(sessionId)
        menu.findItem(R.id.delete_conversation).isVisible = contact != null
        menu.findItem(R.id.set_as_contact).isVisible = contact == null && isOnline
        menu.findItem(R.id.remove_contact).isVisible = contact != null
        if (contact == null) {
            menu.findItem(R.id.verify).isVisible = false
        } else {
            menu.findItem(R.id.verify).isVisible = !contact.verified
        }
        menu.findItem(R.id.refresh_profile).isEnabled = isOnline
        menu.findItem(R.id.session_info).isVisible = isOnline || contact != null
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.session_info -> {
                showSessionInfo()
                true
            }
            R.id.set_as_contact -> {
                if (sessionName == null) {
                    Toast.makeText(this, R.string.no_name_error, Toast.LENGTH_SHORT).show()
                } else {
                    if (airaService.setAsContact(sessionId, sessionName!!)) {
                        invalidateOptionsMenu()
                        displayIconTrustLevel(true, false)
                    }
                }
                true
            }
            R.id.remove_contact -> {
                AlertDialog.Builder(this, R.style.CustomAlertDialog)
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
                    AlertDialog.Builder(this, R.style.CustomAlertDialog)
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
                AlertDialog.Builder(this, R.style.CustomAlertDialog)
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
            R.id.refresh_profile -> {
                airaService.sendTo(sessionId, Protocol.askProfileInfo())
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

    private fun showSessionInfo() {
        val contact = airaService.contacts[sessionId]
        val session = airaService.sessions[sessionId]
        (contact?.publicKey ?: session?.peerPublicKey)?.let { publicKey -> //can be null if disconnected and not a contact
            val dialogBinding = DialogInfoBinding.inflate(layoutInflater)
            if (avatar == null) {
                dialogBinding.avatar.setTextAvatar(sessionName)
            } else {
                dialogBinding.avatar.setImageAvatar(avatar!!)
            }
            dialogBinding.textFingerprint.text = StringUtils.beautifyFingerprint(generateFingerprint(publicKey))
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
            dialogBinding.textIsContact.text = getString(if (contact == null) {
                dialogBinding.fieldIsVerified.visibility = View.GONE
                R.string.no
            } else {
                dialogBinding.textIsVerified.text = getString(if (contact.verified) {
                    R.string.yes
                } else {
                    R.string.no
                })
                R.string.yes
            })
            AlertDialog.Builder(this, R.style.CustomAlertDialog)
                .setTitle(sessionName)
                .setView(dialogBinding.root)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }
}