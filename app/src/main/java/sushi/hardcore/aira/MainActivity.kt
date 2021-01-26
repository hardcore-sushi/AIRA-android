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
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.aira.adapters.Session
import sushi.hardcore.aira.adapters.SessionAdapter
import sushi.hardcore.aira.background_service.AIRAService
import sushi.hardcore.aira.background_service.ReceiveFileTransfer
import sushi.hardcore.aira.databinding.ActivityMainBinding
import sushi.hardcore.aira.utils.FileUtils

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var airaService: AIRAService
    private lateinit var onlineSessionAdapter: SessionAdapter
    private lateinit var offlineSessionAdapter: SessionAdapter
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
                        offlineSessionAdapter.add(session)
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
                    AdapterView.OnItemClickListener { _, _, position, _ ->
                        askShareFileTo(onlineSessionAdapter.getItem(position))
                    }
                } else {
                    AdapterView.OnItemClickListener { _, _, position, _ ->
                        launchChatActivity(onlineSessionAdapter.getItem(position))
                    }
                }
        }
        offlineSessionAdapter = SessionAdapter(this)
        binding.offlineSessions.apply {
            adapter = offlineSessionAdapter
            onItemClickListener = if (openedToShareFile) {
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    askShareFileTo(offlineSessionAdapter.getItem(position))
                }
            } else {
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    launchChatActivity(offlineSessionAdapter.getItem(position))
                }
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
                offlineSessionAdapter.reset()
                loadContacts()
                loadSessions()
                title = airaService.identityName
            } else {
                finish()
            }
        }
    }

    private fun loadContacts() {
        for ((sessionId, contact) in airaService.contacts) {
            offlineSessionAdapter.add(Session(sessionId, true, contact.verified, contact.seen, null, contact.name))
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
            offlineSessionAdapter.remove(sessionId)
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