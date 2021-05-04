package sushi.hardcore.aira

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.aira.adapters.Session
import sushi.hardcore.aira.adapters.SessionAdapter
import sushi.hardcore.aira.background_service.AIRAService
import sushi.hardcore.aira.background_service.ReceiveFileTransfer
import sushi.hardcore.aira.databinding.ActivityMainBinding
import sushi.hardcore.aira.databinding.DialogIpAddressesBinding
import sushi.hardcore.aira.utils.FileUtils
import sushi.hardcore.aira.utils.StringUtils
import java.lang.StringBuilder
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var airaService: AIRAService
    private lateinit var onlineSessionAdapter: SessionAdapter
    private var offlineSessionAdapter: SessionAdapter? = null
    private val onSessionsItemClick = AdapterView.OnItemClickListener { adapter, _, position, _ ->
        launchChatActivity(adapter.getItemAtPosition(position) as Session)
    }
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

        override fun onAskLargeFile(sessionId: Int, name: String, receiveFileTransfer: ReceiveFileTransfer): Boolean {
            runOnUiThread {
                receiveFileTransfer.ask(this@MainActivity, name)
            }
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val identityName = intent.getStringExtra("identityName")
        identityName?.let { title = it }

        val openedToShareFile = intent.action == Intent.ACTION_SEND

        onlineSessionAdapter = SessionAdapter(this)
        binding.onlineSessions.apply {
            adapter = onlineSessionAdapter
            onItemClickListener = if (openedToShareFile) {
                    onSessionsItemClickSendFile
                } else {
                    onSessionsItemClick
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
                    onSessionsItemClick
                }
                setOnScrollListener(onSessionsScrollListener)
            }
        }
        Intent(this, AIRAService::class.java).also { serviceIntent ->
            bindService(serviceIntent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                    val binder = service as AIRAService.AIRABinder
                    airaService = binder.getService()
                    airaService.uiCallbacks = uiCallbacks
                    airaService.isAppInBackground = false
                    loadContacts()
                    if (AIRAService.isServiceRunning) {
                        title = airaService.identityName
                        loadSessions()
                    } else {
                        airaService.identityName = identityName
                        startService(serviceIntent)
                    }
                    binding.refresher.setOnRefreshListener {
                        airaService.restartDiscovery()
                        binding.refresher.isRefreshing = false
                    }
                }
                override fun onServiceDisconnected(name: ComponentName?) {}
            }, Context.BIND_AUTO_CREATE)
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
            if (::airaService.isInitialized){
                airaService.connectTo(binding.editPeerIp.text.toString())
            }
            binding.editPeerIp.text.clear()
            true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.close -> {
                if (::airaService.isInitialized) {
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
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::airaService.isInitialized) {
            airaService.isAppInBackground = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (::airaService.isInitialized) {
            if (AIRAService.isServiceRunning) {
                airaService.isAppInBackground = false
                airaService.uiCallbacks = uiCallbacks //restoring callbacks
                onlineSessionAdapter.reset()
                offlineSessionAdapter?.reset()
                loadContacts()
                loadSessions()
                title = airaService.identityName
            } else {
                finish()
            }
        }
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
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri == null) {
            Toast.makeText(this, R.string.share_uri_null, Toast.LENGTH_SHORT).show()
        } else {
            val cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor == null) {
                Toast.makeText(this, R.string.file_open_failed, Toast.LENGTH_SHORT).show()
            } else {
                if (cursor.moveToFirst()) {
                    val fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    val fileSize = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        Toast.makeText(this, R.string.file_open_failed, Toast.LENGTH_SHORT).show()
                    } else {
                        AlertDialog.Builder(this)
                                .setTitle(R.string.warning)
                                .setMessage(getString(R.string.ask_send_file, fileName, FileUtils.formatSize(fileSize), session.name ?: session.ip))
                                .setPositiveButton(R.string.yes) { _, _ ->
                                    airaService.sendFileTo(session.sessionId, fileName, fileSize, inputStream)
                                    finish()
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .show()
                    }
                }
                cursor.close()
            }
        }
    }
}