package sushi.hardcore.aira

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import sushi.hardcore.aira.adapters.Session
import sushi.hardcore.aira.adapters.SessionAdapter
import sushi.hardcore.aira.background_service.AIRAService
import sushi.hardcore.aira.background_service.FilesReceiver
import sushi.hardcore.aira.databinding.ActivityMainBinding
import sushi.hardcore.aira.databinding.DialogIpAddressesBinding
import sushi.hardcore.aira.utils.FileUtils
import sushi.hardcore.aira.utils.StringUtils
import java.net.NetworkInterface

class MainActivity : ServiceBoundActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var onlineSessionAdapter: SessionAdapter
    private var offlineSessionAdapter: SessionAdapter? = null
    private val onSessionsItemClickSendFile = AdapterView.OnItemClickListener { adapter, _, position, _ ->
        askShareFileTo(adapter.getItemAtPosition(position) as Session)
    }
    private val onSessionsScrollListener = object : AbsListView.OnScrollListener {
        override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}
        override fun onScroll(listView: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
            if (listView.getChildAt(0) != null) {
                binding.refresher.isEnabled = listView.firstVisiblePosition == 0 && listView.getChildAt(0).top == 0
            }
        }
    }
    private val uiCallbacks = object : AIRAService.UiCallbacks {
        override fun onNewSession(sessionId: Int, ip: String) {
            runOnUiThread {
                handleNewSession(sessionId, ip)
            }
        }
        override fun onSessionDisconnect(sessionId: Int) {
            runOnUiThread {
                onlineSessionAdapter.remove(sessionId)?.let { session ->
                    if (session.isContact) {
                        offlineSessionAdapter?.add(session)
                    }
                }
            }
        }
        override fun onNameTold(sessionId: Int, name: String) {
            runOnUiThread {
                onlineSessionAdapter.setName(sessionId, name)
            }
        }
        override fun onNewMessage(sessionId: Int, data: ByteArray): Boolean {
            runOnUiThread {
                onlineSessionAdapter.setSeen(sessionId, false)
            }
            return false
        }

        override fun onAskLargeFiles(sessionId: Int, name: String, filesReceiver: FilesReceiver): Boolean {
            runOnUiThread {
                filesReceiver.ask(this@MainActivity, name)
            }
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val identityName = intent.getStringExtra("identityName")
        identityName?.let {
            setSupportActionBar(binding.toolbar.toolbar)
            binding.toolbar.textAvatar.setLetterFrom(it)
            binding.toolbar.title.text = it
        }

        val openedToShareFile = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE

        onlineSessionAdapter = SessionAdapter(this)
        binding.onlineSessions.apply {
            adapter = onlineSessionAdapter
            onItemClickListener = if (openedToShareFile) {
                    onSessionsItemClickSendFile
                } else {
                    AdapterView.OnItemClickListener { _, _, position, _ ->
                        if (isSelecting()) {
                            changeSelection(onlineSessionAdapter, position)
                        } else {
                            launchChatActivity(onlineSessionAdapter.getItem(position))
                        }
                    }
                }
            onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
                changeSelection(onlineSessionAdapter, position)
                true
            }
            setOnScrollListener(onSessionsScrollListener)
        }
        if (openedToShareFile) {
            binding.offlineSessions.visibility = View.GONE
            binding.textOfflineSessions.visibility = View.GONE
        } else {
            offlineSessionAdapter = SessionAdapter(this)
            binding.offlineSessions.apply {
                adapter = offlineSessionAdapter
                onItemClickListener = if (openedToShareFile) {
                    onSessionsItemClickSendFile
                } else {
                    AdapterView.OnItemClickListener { _, _, position, _ ->
                        if (isSelecting()) {
                            changeSelection(offlineSessionAdapter!!, position)
                        } else {
                            launchChatActivity(offlineSessionAdapter!!.getItem(position))
                        }
                    }
                }
                onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
                    changeSelection(offlineSessionAdapter!!, position)
                    true
                }
                setOnScrollListener(onSessionsScrollListener)
            }
        }
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                val binder = service as AIRAService.AIRABinder
                airaService = binder.getService()
                airaService.uiCallbacks = uiCallbacks
                airaService.isAppInBackground = false
                refreshSessions()
                if (AIRAService.isServiceRunning) {
                    title = airaService.identityName
                } else {
                    airaService.identityName = identityName
                    startService(serviceIntent)
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {}
        }

        binding.refresher.setOnRefreshListener {
            if (isServiceInitialized()) {
                airaService.restartDiscovery()
            }
            binding.refresher.isRefreshing = false
        }
        binding.buttonShowIp.setOnClickListener {
            val ipAddresses = StringBuilder()
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress) {
                        ipAddresses.appendLine(StringUtils.getIpFromInetAddress(addr)+" ("+iface.displayName+')')
                    }
                }
            }
            val dialogBinding = DialogIpAddressesBinding.inflate(layoutInflater)
            dialogBinding.textIpAddresses.text = ipAddresses.substring(0, ipAddresses.length-1) //remove last LF
            AlertDialog.Builder(this)
                    .setTitle(R.string.your_addresses)
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.ok, null)
                    .show()
        }
        binding.editPeerIp.setOnEditorActionListener { _, _, _ ->
            if (isServiceInitialized()){
                airaService.connectTo(binding.editPeerIp.text.toString())
            }
            binding.editPeerIp.text.clear()
            true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_activity, menu)
        menu.findItem(R.id.remove_contact).isVisible = isSelecting()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.close -> {
                if (isServiceInitialized()) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.warning)
                        .setMessage(R.string.ask_log_out)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            airaService.logOut()
                            if (AIRADatabase.isIdentityProtected(Constants.getDatabaseFolder(this))) {
                                startActivity(Intent(this, LoginActivity::class.java))
                            }
                            finish()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
                true
            }
            R.id.remove_contact -> {
                AlertDialog.Builder(this)
                        .setTitle(R.string.warning)
                        .setMessage(R.string.ask_remove_contacts)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            Thread {
                                for (sessionId in onlineSessionAdapter.getSelectedSessionIds()) {
                                    airaService.removeContact(sessionId)
                                }
                                offlineSessionAdapter?.let {
                                    for (sessionId in it.getSelectedSessionIds()) {
                                        airaService.removeContact(sessionId)
                                    }
                                }
                                runOnUiThread {
                                    unSelectAll()
                                    refreshSessions()
                                }
                            }.start()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isServiceInitialized()) {
            airaService.isAppInBackground = true
        }
    }

    override fun onBackPressed() {
        if (isSelecting()) {
            unSelectAll()
        } else {
            super.onBackPressed()
        }
    }

    private fun refreshSessions() {
        onlineSessionAdapter.reset()
        offlineSessionAdapter?.reset()
        loadContacts()
        loadSessions()
    }

    private fun unSelectAll() {
        onlineSessionAdapter.unSelectAll()
        offlineSessionAdapter?.unSelectAll()
        invalidateOptionsMenu()
    }

    private fun changeSelection(adapter: SessionAdapter, position: Int) {
        val wasSelecting = adapter.selectedItems.isNotEmpty()
        adapter.onSelectionChanged(position)
        val isSelecting = adapter.selectedItems.isNotEmpty()
        if (wasSelecting != isSelecting) {
            invalidateOptionsMenu()
        }
    }

    private fun isSelecting(): Boolean {
        return onlineSessionAdapter.selectedItems.isNotEmpty() || offlineSessionAdapter?.selectedItems?.isNotEmpty() == true
    }

    private fun loadContacts() {
        if (offlineSessionAdapter != null) {
            for ((sessionId, contact) in airaService.contacts) {
                offlineSessionAdapter!!.add(Session(sessionId, true, contact.verified, contact.seen, null, contact.name))
            }
        }
    }

    private fun loadSessions() {
        for ((sessionId, session) in airaService.sessions) {
            handleNewSession(sessionId, session.ip)
        }
    }

    private fun handleNewSession(sessionId: Int, ip: String) {
        val seen = !airaService.notSeen.contains(sessionId)
        val contact = airaService.contacts[sessionId]
        if (contact == null) {
            onlineSessionAdapter.add(Session(sessionId, false, false, seen, ip, airaService.savedNames[sessionId]))
        } else {
            onlineSessionAdapter.add(Session(sessionId, true, contact.verified, seen, ip, contact.name))
            offlineSessionAdapter?.remove(sessionId)
        }
    }

    private fun launchChatActivity(session: Session) {
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra("sessionId", session.sessionId)
            putExtra("sessionName", session.name)
        })
    }

    private fun askShareFileTo(session: Session) {
        var uris: ArrayList<Uri>? = null
        when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let {
                uris = arrayListOf(it)
            }
            Intent.ACTION_SEND_MULTIPLE -> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        if (uris == null) {
            Toast.makeText(this, R.string.open_uri_failed, Toast.LENGTH_SHORT).show()
        } else {
            val msg = if (uris!!.size == 1) {
                val sendFile = FileUtils.openFileFromUri(this, uris!![0])
                if (sendFile == null) {
                    Toast.makeText(this, R.string.open_uri_failed, Toast.LENGTH_SHORT).show()
                    return
                } else {
                    sendFile.inputStream.close()
                    getString(R.string.ask_send_single_file, sendFile.fileName, FileUtils.formatSize(sendFile.fileSize), session.name ?: session.ip)
                }
            } else {
                getString(R.string.ask_send_multiple_files, uris!!.size, session.name ?: session.ip)
            }
            AlertDialog.Builder(this)
                    .setTitle(R.string.warning)
                    .setMessage(msg)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        airaService.sendFilesFromUris(session.sessionId, uris!!)
                        finish()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
        }
    }
}