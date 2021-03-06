package sushi.hardcore.aira.background_service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.*
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.Glide
import sushi.hardcore.aira.*
import sushi.hardcore.aira.utils.FileUtils
import sushi.hardcore.aira.utils.StringUtils
import sushi.hardcore.aira.utils.TimeUtils
import java.io.IOException
import java.net.*
import java.nio.channels.*

@SuppressLint("UnspecifiedImmutableFlag")
class AIRAService : Service() {
    private external fun releaseIdentity()

    companion object {
        const val SERVICE_NOTIFICATION_CHANNEL_ID = "AIRAService"
        const val MESSAGES_NOTIFICATION_CHANNEL_ID = "Messages"
        const val ASK_FILE_TRANSFER_NOTIFICATION_CHANNEL_ID = "AskFile"
        const val FILE_TRANSFER_NOTIFICATION_CHANNEL_ID = "FileTransfer"
        const val MESSAGE_CONNECT_TO = 0
        const val MESSAGE_SEND_TO = 1
        const val MESSAGE_LOGOUT = 2
        const val MESSAGE_SEND_NAME = 3
        const val MESSAGE_SEND_AVATAR = 4
        const val MESSAGE_CANCEL_FILE_TRANSFER = 5
        const val FLAG_PENDING_INTENT = PendingIntent.FLAG_UPDATE_CURRENT

        var isServiceRunning = false
    }

    private val binder = AIRABinder()
    val sessions = mutableMapOf<Int, Session>()
    private var sessionCounter = 0
    private lateinit var selector: Selector
    private val sessionIdByKey = mutableMapOf<SelectionKey, Int>()
    private val databaseFolder by lazy {
        Constants.getDatabaseFolder(this)
    }
    private val notificationIdManager = NotificationIdManager()
    private val sendFileTransfers = mutableMapOf<Int, FilesSender>()
    val receiveFileTransfers = mutableMapOf<Int, FilesReceiver>()
    lateinit var contacts: HashMap<Int, Contact>
    var usePadding = true
    private lateinit var serviceHandler: Handler
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var nsdManager: NsdManager
    private val nsdRegistrationListener = object : NsdManager.RegistrationListener {
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.w("mDNS","DNS-SD registration failed: $errorCode")
        }
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {}
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {}
    }
    private var shouldRestartDiscovery = false
    private val nsdDiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.w("mDNS", "Failed to start discovery: $errorCode")
        }
        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
        override fun onDiscoveryStarted(serviceType: String?) {}
        override fun onDiscoveryStopped(serviceType: String?) {
            if (shouldRestartDiscovery) {
                startDiscovery()
                shouldRestartDiscovery = false
            }
        }
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (isServiceRunning) {
                        connectTo(serviceInfo.host.hostAddress)
                    }
                }
            })
        }
        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
    }
    lateinit var identityName: String
    val savedMsgs = mutableMapOf<Int, MutableList<ChatItem>>()
    val pendingMsgs = mutableMapOf<Int, MutableList<ByteArray>>()
    val savedNames = mutableMapOf<Int, String>()
    val savedAvatars = mutableMapOf<Int, String>()
    val notSeen = mutableListOf<Int>()
    var uiCallbacks: UiCallbacks? = null
    var isAppInBackground = true

    inner class AIRABinder : Binder() {
        fun getService(): AIRAService = this@AIRAService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    interface UiCallbacks {
        fun onConnectFailed(ip: String, errorMsg: String?)
        fun onNewSession(sessionId: Int, ip: String)
        fun onSessionDisconnect(sessionId: Int)
        fun onNameTold(sessionId: Int, name: String)
        fun onAvatarChanged(sessionId: Int, avatar: ByteArray?)
        fun onSent(sessionId: Int, timestamp: Long, buffer: ByteArray)
        fun onPendingMessagesSent(sessionId: Int)
        fun onNewMessage(sessionId: Int, timestamp: Long, data: ByteArray): Boolean
        fun onAskLargeFiles(sessionId: Int, filesReceiver: FilesReceiver): Boolean
    }

    fun connectTo(ip: String) {
        serviceHandler.obtainMessage().apply {
            what = MESSAGE_CONNECT_TO
            data = Bundle().apply { putString("ip", ip) }
            serviceHandler.sendMessage(this)
        }
    }

    private fun sendTo(sessionId: Int, buffer: ByteArray) {
        serviceHandler.obtainMessage().apply {
            what = MESSAGE_SEND_TO
            data = Bundle().apply {
                putInt("sessionId", sessionId)
                putByteArray("buff", buffer)
            }
            serviceHandler.sendMessage(this)
        }
    }

    fun sendOrAddToPending(sessionId: Int, buffer: ByteArray): Boolean {
        return if (isOnline(sessionId)) {
            sendTo(sessionId, buffer)
            true
        } else {
            pendingMsgs[sessionId]?.add(buffer)
            false
        }
    }

    fun cancelFileTransfer(sessionId: Int) {
        serviceHandler.obtainMessage().apply {
            what = MESSAGE_CANCEL_FILE_TRANSFER
            data = Bundle().apply { putInt("sessionId", sessionId) }
            serviceHandler.sendMessage(this)
        }
    }

    fun sendFilesFromUris(sessionId: Int, uris: List<Uri>, onPendingSmallFile: ((ByteArray) -> Unit)? = null) {
        val files = mutableListOf<SendFile>()
        var useLargeFileTransfer = false
        for (uri in uris) {
            FileUtils.openFileFromUri(this, uri).file?.let { sendFile ->
                files.add(sendFile)
                if (sendFile.fileSize > Constants.fileSizeLimit) {
                    useLargeFileTransfer = true
                }
            }
        }
        if (useLargeFileTransfer) {
            sendLargeFilesTo(sessionId, files)
        } else {
            for (file in files) {
                var buffer = file.inputStream.readBytes()
                file.inputStream.close()
                buffer = Protocol.newFile(file.fileName, buffer)
                if (!sendOrAddToPending(sessionId, buffer)) {
                    onPendingSmallFile?.let { it(buffer) }
                }
            }
        }
    }

    private fun sendLargeFilesTo(sessionId: Int, files: MutableList<SendFile>) {
        if (sendFileTransfers[sessionId] == null && receiveFileTransfers[sessionId] == null) {
            val filesSender = FilesSender(files, this, notificationManager)
            initFileTransferNotification(sessionId, filesSender.fileTransferNotification, filesSender.files[0])
            sendFileTransfers[sessionId] = filesSender
            sendOrAddToPending(sessionId, Protocol.askLargeFiles(files))
        } else {
            Toast.makeText(this, R.string.file_transfer_already_in_progress, Toast.LENGTH_SHORT).show()
        }
    }

    fun logOut() {
        serviceHandler.sendEmptyMessage(MESSAGE_LOGOUT)
        isServiceRunning = false
        releaseIdentity()
    }

    fun isOnline(sessionId: Int): Boolean {
        return sessions.contains(sessionId)
    }

    fun getNameOf(sessionId: Int): String {
        return contacts[sessionId]?.name ?: savedNames[sessionId] ?: sessions[sessionId]!!.ip
    }

    fun isContact(sessionId: Int): Boolean {
        return contacts.contains(sessionId)
    }

    fun setAsContact(sessionId: Int, name: String): Boolean {
        sessions[sessionId]?.peerPublicKey?.let {
            AIRADatabase.addContact(name, savedAvatars[sessionId], it)?.let { contact ->
                contacts[sessionId] = contact
                savedMsgs.remove(sessionId)?.let { msgs ->
                    for (msg in msgs) {
                        AIRADatabase.storeMsg(contact.uuid, msg.outgoing, msg.timestamp, msg.data)
                    }
                }
                savedNames.remove(sessionId)
                pendingMsgs[sessionId] = mutableListOf()
                savedAvatars.remove(sessionId)
                return true
            }
        }
        return false
    }

    fun setVerified(sessionId: Int): Boolean {
        contacts[sessionId]?.let {
            if (AIRADatabase.setVerified(it.uuid)) {
                it.verified = true
                return true
            }
        }
        return false
    }

    fun setSeen(sessionId: Int, seen: Boolean) {
        if (seen) {
            notSeen.remove(sessionId)
            notificationManager.cancel(notificationIdManager.getMessageNotificationId(sessionId))
        } else if (!notSeen.contains(sessionId)) {
            notSeen.add(sessionId)
        }
        contacts[sessionId]?.let { contact ->
            if (contact.seen != seen) {
                if (AIRADatabase.setContactSeen(contact.uuid, seen)) {
                    contact.seen = seen
                }
            }
        }
    }

    fun removeContact(sessionId: Int): Boolean {
        contacts.remove(sessionId)?.let {
            return if (AIRADatabase.removeContact(it.uuid)) {
                savedMsgs[sessionId] = mutableListOf()
                pendingMsgs.remove(sessionId)
                savedNames[sessionId] = it.name
                it.avatar?.let { avatarUuid ->
                    savedAvatars[sessionId] = avatarUuid
                }
                true
            } else {
                false
            }
        }
        return false
    }

    fun deleteConversation(sessionId: Int): Boolean {
        contacts[sessionId]?.let {
            return if (AIRADatabase.deleteConversation(it.uuid)) {
                savedMsgs[sessionId] = mutableListOf()
                true
            } else {
                false
            }
        }
        return true
    }

    fun changeName(newName: String): Boolean {
        return if (AIRADatabase.changeName(newName)) {
            identityName = newName
            serviceHandler.sendEmptyMessage(MESSAGE_SEND_NAME)
            true
        } else {
            false
        }
    }

    fun changeAvatar(avatar: ByteArray?): Boolean {
        val success = if (avatar == null) {
            AIRADatabase.removeIdentityAvatar(databaseFolder)
        } else {
            AIRADatabase.setIdentityAvatar(databaseFolder, avatar)
        }
        return if (success) {
            serviceHandler.obtainMessage().apply {
                what = MESSAGE_SEND_AVATAR
                data = Bundle().apply { putByteArray("avatar", avatar) }
                serviceHandler.sendMessage(this)
            }
            true
        } else {
            false
        }
    }

    private fun handleNewSocket(socket: SocketChannel, outgoing: Boolean) {
        Thread {
            try {
                val session = Session(socket, outgoing)
                if (session.doHandshake()) {
                    synchronized(this) {
                        var isActuallyNewSession = true
                        for (s in sessions.values) {
                            if (s.peerPublicKey.contentEquals(session.peerPublicKey)) {
                                isActuallyNewSession = false
                            }
                        }
                        if (isActuallyNewSession && !session.peerPublicKey.contentEquals(AIRADatabase.getIdentityPublicKey())) {
                            var sessionId: Int? = null
                            for ((i, contact) in contacts) {
                                if (contact.publicKey.contentEquals(session.peerPublicKey)){
                                    sessions[i] = session
                                    sessionId = i
                                }
                            }
                            if (sessionId == null) {
                                sessions[sessionCounter] = session
                                savedMsgs[sessionCounter] = mutableListOf()
                                sessionId = sessionCounter
                                sessionCounter++
                            }
                            session.configureBlocking(false)
                            val key = session.register(selector, SelectionKey.OP_READ)
                            sessionIdByKey[key] = sessionId
                            uiCallbacks?.onNewSession(sessionId, session.ip)
                            if (isContact(sessionId)) {
                                for (i in 0 until pendingMsgs[sessionId]!!.size) {
                                    sendAndSave(sessionId, pendingMsgs[sessionId]!!.removeAt(0))
                                }
                                uiCallbacks?.onPendingMessagesSent(sessionId)
                            } else {
                                session.encryptAndSend(Protocol.askProfileInfo(), usePadding)
                            }
                        } else {
                            session.close()
                        }
                    }
                } else {
                    session.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun avatarToIcon(avatar: ByteArray): IconCompat {
        return IconCompat.createWithBitmap(
            Glide.with(this)
                .asBitmap()
                .load(avatar)
                .submit()
                .get()
        )
    }

    private fun sendNotification(sessionId: Int, msgContent: ByteArray, timestamp: Long) {
        val name = getNameOf(sessionId)
        val text = if (msgContent[0] == Protocol.MESSAGE) {
            msgContent.decodeToString(1)
        } else { //file
            msgContent.decodeToString(17)
        }
        val notification = NotificationCompat.Builder(this, MESSAGES_NOTIFICATION_CHANNEL_ID)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(name)
                .setContentText(text)
                .setStyle(NotificationCompat.MessagingStyle(
                    Person.Builder()
                        .setName(identityName)
                        .apply {
                            AIRADatabase.getIdentityAvatar(databaseFolder)?.let {
                                setIcon(avatarToIcon(it))
                            }
                        }
                        .build()
                    )
                    .addMessage(text, timestamp, Person.Builder()
                        .setName(name)
                        .apply {
                            (savedAvatars[sessionId] ?: contacts[sessionId]?.avatar)?.let { uuid ->
                                AIRADatabase.loadAvatar(uuid)?.let {
                                    setIcon(avatarToIcon(it))
                                }
                            }
                        }
                        .build()
                    )
                )
                .setContentIntent(
                        PendingIntent.getActivity(this, 0, Intent(this, ChatActivity::class.java).apply {
                                putExtra("sessionId", sessionId)
                        }, FLAG_PENDING_INTENT)
                )
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .apply {
                    priority = NotificationCompat.PRIORITY_HIGH
                }
                .addAction(NotificationCompat.Action(
                    R.drawable.ic_launcher,
                    getString(R.string.mark_read),
                    newActionPendingIntent {
                        it.putBinder(sessionId)
                        it.action = NotificationBroadcastReceiver.ACTION_MARK_READ
                    }
                ))
                .addAction(
                    NotificationCompat.Action.Builder(R.drawable.ic_launcher, getString(R.string.reply), newActionPendingIntent {
                        it.putBinder(sessionId)
                        it.action = NotificationBroadcastReceiver.ACTION_REPLY
                    })
                    .addRemoteInput(
                            RemoteInput.Builder(NotificationBroadcastReceiver.KEY_TEXT_REPLY)
                                    .setLabel(getString(R.string.reply))
                                    .build()
                    )
                    .build()
                )
                .build()
        notificationManager.notify(notificationIdManager.getMessageNotificationId(sessionId), notification)
    }

    private fun initFileTransferNotification(sessionId: Int, fileTransferNotification: FileTransferNotification, file: PendingFile) {
        fileTransferNotification.initFileTransferNotification(
                notificationIdManager.getFileTransferNotificationId(sessionId),
                file.fileName,
                file.fileSize.toInt(),
                Intent(this, NotificationBroadcastReceiver::class.java).apply {
                    putBinder(sessionId)
                    action = NotificationBroadcastReceiver.ACTION_CANCEL_FILE_TRANSFER
                }
        )
    }

    private fun Intent.putBinder(sessionId: Int) {
        val bundle = Bundle()
        bundle.putBinder("binder", AIRABinder())
        bundle.putInt("sessionId", sessionId)
        putExtra("bundle", bundle)
    }

    private fun saveMsg(sessionId: Int, timestamp: Long, msg: ByteArray) {
        var msgSaved = false
        contacts[sessionId]?.uuid?.let { uuid ->
            msgSaved = AIRADatabase.storeMsg(uuid, true, timestamp, msg)
        }
        if (!msgSaved) {
            savedMsgs[sessionId]?.add(ChatItem(true, timestamp, msg))
        }
    }

    private fun sendAndSave(sessionId: Int, buffer: ByteArray) {
        sessions[sessionId]?.encryptAndSend(buffer, usePadding)
        val timestamp = TimeUtils.getTimestamp()
        if (buffer[0] == Protocol.MESSAGE) {
            uiCallbacks?.onSent(sessionId, timestamp, buffer)
            saveMsg(sessionId, timestamp, buffer)
        } else if (buffer[0] == Protocol.FILE) {
            Protocol.parseSmallFile(buffer)?.let { file ->
                AIRADatabase.storeFile(contacts[sessionId]?.uuid, file.fileContent)?.let { rawFileUuid ->
                    val msg = byteArrayOf(Protocol.FILE) + rawFileUuid + file.rawFileName
                    uiCallbacks?.onSent(sessionId, timestamp, msg)
                    saveMsg(sessionId, timestamp, msg)
                }
            }
        }
    }

    override fun onCreate() {
        HandlerThread("", THREAD_PRIORITY_BACKGROUND).apply {
            start()
            serviceHandler = object : Handler(looper) {
                override fun handleMessage(msg: Message) {
                    try {
                        when (msg.what) {
                            MESSAGE_SEND_TO -> {
                                val sessionId = msg.data.getInt("sessionId")
                                msg.data.getByteArray("buff")?.let {
                                    val fileTransfer = sendFileTransfers[sessionId]
                                    if (fileTransfer?.nextChunk == null) {
                                        sendAndSave(sessionId, it)
                                    } else {
                                        fileTransfer.msgQueue.add(it)
                                    }
                                }
                            }
                            MESSAGE_CONNECT_TO -> {
                                msg.data.getString("ip")?.let { ip ->
                                    Thread {
                                        val addr = InetSocketAddress(ip, Constants.port)
                                        if (addr.isUnresolved) {
                                            uiCallbacks?.onConnectFailed(ip, getString(R.string.invalid_ip))
                                        } else {
                                            try {
                                                val socket = SocketChannel.open()
                                                if (socket.connect(addr)) {
                                                    handleNewSocket(socket, true)
                                                }
                                            } catch (e: NoRouteToHostException) {
                                                uiCallbacks?.onConnectFailed(ip, e.message)
                                            } catch (e: ConnectException) {
                                                uiCallbacks?.onConnectFailed(ip, e.message)
                                            }
                                        }
                                    }.start()
                                }
                            }
                            MESSAGE_CANCEL_FILE_TRANSFER -> {
                                val sessionId = msg.data.getInt("sessionId")
                                sessions[sessionId]?.let { session ->
                                    cancelFileTransfer(sessionId, session, true)
                                }
                            }
                            MESSAGE_SEND_NAME -> {
                                val tellingName = Protocol.name(identityName)
                                for (session in sessions.values) {
                                    try {
                                        session.encryptAndSend(tellingName, usePadding)
                                    } catch (e: SocketException) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            MESSAGE_SEND_AVATAR -> {
                                val avatar = msg.data.getByteArray("avatar")
                                val avatarMsg = if (avatar == null) {
                                    Protocol.removeAvatar()
                                } else {
                                    Protocol.avatar(avatar)
                                }
                                for (session in sessions.values) {
                                    try {
                                        session.encryptAndSend(avatarMsg, usePadding)
                                    } catch (e: SocketException) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            MESSAGE_LOGOUT -> {
                                nsdManager.unregisterService(nsdRegistrationListener)
                                stopDiscovery()
                                quit()
                                stopSelf()
                                uiCallbacks = null
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
        identityName = AIRADatabase.getIdentityName(databaseFolder)!!
        val contactList = AIRADatabase.loadContacts()
        if (contactList == null) {
            contacts = HashMap(0)
        } else {
            contacts = HashMap(contactList.size)
            for (contact in contactList) {
                contacts[sessionCounter] = contact
                pendingMsgs[sessionCounter] = mutableListOf()
                if (!contact.seen) {
                    notSeen.add(sessionCounter)
                }
                sessionCounter++
            }
        }
        usePadding = AIRADatabase.getUsePadding()
    }

    private fun setAvatarUuid(sessionId: Int, avatarUuid: String?) {
        val contact = contacts[sessionId]
        if (contact == null) {
            if (avatarUuid == null) {
                savedAvatars.remove(sessionId)
            } else {
                savedAvatars[sessionId] = avatarUuid
            }
        } else {
            AIRADatabase.setContactAvatar(contact.uuid, avatarUuid)
            contact.avatar = avatarUuid
        }
    }

    private fun encryptNextChunk(session: Session, filesSender: FilesSender) {
        val nextChunk = ByteArray(Constants.FILE_CHUNK_SIZE)
        nextChunk[0] = Protocol.LARGE_FILE_CHUNK
        val read = try {
            filesSender.files[filesSender.index].inputStream.read(nextChunk, 1, Constants.FILE_CHUNK_SIZE-1)
        } catch (e: IOException) {
            0
        }
        filesSender.nextChunk = if (read > 0) {
            filesSender.lastChunkSizes.add(read+1)
            session.encrypt(nextChunk.sliceArray(0 until read+1), usePadding)
        } else {
            null
        }
    }

    private fun flushSendFileTransfer(sessionId: Int, session: Session, filesSender: FilesSender) {
        synchronized(filesSender) { //prevent sending nextChunk two times when canceling
            filesSender.nextChunk?.let {
                session.writeAll(it)
                filesSender.nextChunk = null
                while (filesSender.msgQueue.size > 0) {
                    val msg = filesSender.msgQueue.removeAt(0)
                    sendAndSave(sessionId, msg)
                }
            }
        }
    }

    private fun cancelFileTransfer(sessionId: Int, session: Session, outgoing: Boolean) {
        sendFileTransfers[sessionId]?.let {
            it.files[it.index].inputStream.close()
            flushSendFileTransfer(sessionId, session, it)
            sendFileTransfers.remove(sessionId)!!.fileTransferNotification.onAborted()
        }
        receiveFileTransfers[sessionId]?.let {
            it.files[it.index].outputStream?.close()
            receiveFileTransfers.remove(sessionId)!!.fileTransferNotification.onAborted()
        }
        if (outgoing) {
            session.encryptAndSend(Protocol.abortFilesTransfer(), usePadding)
        }
    }

    private fun startListening() {
        val server = try {
            ServerSocketChannel.open().apply {
                configureBlocking(false)
                socket().bind(InetSocketAddress(Constants.port))
            }
        } catch (e: SocketException) {
            Log.w("Bind failed", e.message.toString())
            null
        }
        selector = Selector.open()
        server?.register(selector, SelectionKey.OP_ACCEPT)
        Thread {
            while (isServiceRunning) {
                selector.select(100) //timeout to stop this thread if we log out
                val keys = selector.selectedKeys()
                for (key in keys) {
                    if (key.isValid) {
                        if (key.isReadable) {
                            sessionIdByKey[key]?.let { sessionId ->
                                sessions[sessionId]?.let { session ->
                                    var shouldCloseSession = false
                                    try {
                                        val buffer = session.receiveAndDecrypt()
                                        if (buffer == null) {
                                            shouldCloseSession = true
                                        } else {
                                            when (buffer[0]) {
                                                Protocol.LARGE_FILE_CHUNK -> {
                                                    receiveFileTransfers[sessionId]?.let { filesReceiver ->
                                                        val file = filesReceiver.files[filesReceiver.index]
                                                        if (file.outputStream == null) {
                                                            val outputStream = FileUtils.openFileForDownload(this, file.fileName).outputStream
                                                            if (outputStream == null) {
                                                                cancelFileTransfer(sessionId)
                                                            } else {
                                                                file.outputStream = outputStream
                                                            }
                                                        }
                                                        file.outputStream?.let { outputStream ->
                                                            val chunk = buffer.sliceArray(1 until buffer.size)
                                                            try {
                                                                outputStream.write(chunk)
                                                                session.encryptAndSend(Protocol.ackChunk(), usePadding)
                                                                file.transferred += chunk.size
                                                                if (file.transferred >= file.fileSize) {
                                                                    outputStream.close()
                                                                    if (filesReceiver.index == filesReceiver.files.size-1) {
                                                                        receiveFileTransfers.remove(sessionId)
                                                                        filesReceiver.fileTransferNotification.onCompleted()
                                                                    } else {
                                                                        filesReceiver.index += 1
                                                                        val nextFile = filesReceiver.files[filesReceiver.index]
                                                                        initFileTransferNotification(
                                                                                sessionId,
                                                                                filesReceiver.fileTransferNotification,
                                                                                nextFile,
                                                                        )
                                                                    }
                                                                } else {
                                                                    filesReceiver.fileTransferNotification.updateNotificationProgress(chunk.size)
                                                                }
                                                            } catch (e: IOException) {
                                                                cancelFileTransfer(sessionId)
                                                            }
                                                        }
                                                    }
                                                }
                                                Protocol.ACK_CHUNK -> {
                                                    sendFileTransfers[sessionId]?.let { filesSender ->
                                                        flushSendFileTransfer(sessionId, session, filesSender)
                                                        val file = filesSender.files[filesSender.index]
                                                        val chunkSize = filesSender.lastChunkSizes.removeAt(0)
                                                        file.transferred += chunkSize
                                                        if (file.transferred >= file.fileSize) {
                                                            file.inputStream.close()
                                                            if (filesSender.index == filesSender.files.size-1) {
                                                                sendFileTransfers.remove(sessionId)
                                                                filesSender.fileTransferNotification.onCompleted()
                                                            } else {
                                                                filesSender.index += 1
                                                                val nextFile = filesSender.files[filesSender.index]
                                                                initFileTransferNotification(
                                                                        sessionId,
                                                                        filesSender.fileTransferNotification,
                                                                        nextFile,
                                                                )
                                                                encryptNextChunk(session, filesSender)
                                                                filesSender.nextChunk?.let {
                                                                    session.writeAll(it)
                                                                    encryptNextChunk(session, filesSender)
                                                                }
                                                            }
                                                        } else {
                                                            encryptNextChunk(session, filesSender)
                                                            filesSender.fileTransferNotification.updateNotificationProgress(chunkSize)
                                                        }
                                                    }
                                                }
                                                Protocol.ABORT_FILES_TRANSFER -> cancelFileTransfer(sessionId, session, false)
                                                Protocol.ACCEPT_LARGE_FILES -> {
                                                    sendFileTransfers[sessionId]?.let { filesSender ->
                                                        encryptNextChunk(session, filesSender)
                                                        filesSender.nextChunk?.let {
                                                            session.writeAll(it)
                                                            encryptNextChunk(session, filesSender)
                                                        }
                                                    }
                                                }
                                                Protocol.ASK_LARGE_FILES -> {
                                                    if (!receiveFileTransfers.containsKey(sessionId) && !sendFileTransfers.containsKey(sessionId)) {
                                                        Protocol.parseAskFiles(buffer)?.let { files ->
                                                            val filesReceiver = FilesReceiver(
                                                                    files,
                                                                    { filesReceiver ->
                                                                        initFileTransferNotification(
                                                                                sessionId,
                                                                                filesReceiver.fileTransferNotification,
                                                                                filesReceiver.files[0],
                                                                        )
                                                                        sendTo(sessionId, Protocol.acceptLargeFiles())
                                                                    }, { filesReceiver ->
                                                                        receiveFileTransfers.remove(sessionId)
                                                                        sendTo(sessionId, Protocol.abortFilesTransfer())
                                                                        filesReceiver.fileTransferNotification.cancel()
                                                                    },
                                                                    this,
                                                                    notificationManager,
                                                            )
                                                            receiveFileTransfers[sessionId] = filesReceiver
                                                            var shouldSendNotification = true
                                                            if (!isAppInBackground) {
                                                                if (uiCallbacks?.onAskLargeFiles(sessionId, filesReceiver) == true) {
                                                                    shouldSendNotification = false
                                                                }
                                                            }
                                                            if (shouldSendNotification) {
                                                                val notificationBuilder = NotificationCompat.Builder(this, ASK_FILE_TRANSFER_NOTIFICATION_CHANNEL_ID)
                                                                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                                                                        .setSmallIcon(R.drawable.ic_launcher)
                                                                        .setContentTitle(getString(R.string.download_file_request))
                                                                        .setContentText(getString(R.string.want_to_send_files, getNameOf(sessionId)))
                                                                        .setOngoing(true) //not cancelable
                                                                        .setContentIntent(
                                                                                PendingIntent.getActivity(this, 0, Intent(this, ChatActivity::class.java).apply {
                                                                                    putExtra("sessionId", sessionId)
                                                                                }, FLAG_PENDING_INTENT)
                                                                        )
                                                                        .setDefaults(Notification.DEFAULT_ALL)
                                                                        .apply {
                                                                            priority = NotificationCompat.PRIORITY_HIGH
                                                                        }
                                                                notificationManager.notify(notificationIdManager.getFileTransferNotificationId(sessionId), notificationBuilder.build())
                                                            }
                                                        }
                                                    }
                                                }
                                                Protocol.ASK_PROFILE_INFO -> {
                                                    session.encryptAndSend(Protocol.name(identityName), usePadding)
                                                    AIRADatabase.getIdentityAvatar(databaseFolder)?.let { avatar ->
                                                        session.encryptAndSend(Protocol.avatar(avatar), usePadding)
                                                    }
                                                }
                                                Protocol.NAME -> {
                                                    val name = StringUtils.sanitizeName(buffer.sliceArray(1 until buffer.size).decodeToString())
                                                    uiCallbacks?.onNameTold(sessionId, name)
                                                    val contact = contacts[sessionId]
                                                    if (contact == null) {
                                                        savedNames[sessionId] = name
                                                    } else {
                                                        contact.name = name
                                                        AIRADatabase.changeContactName(contact.uuid, name)
                                                    }
                                                }
                                                Protocol.AVATAR -> {
                                                    if (buffer.size < Constants.MAX_AVATAR_SIZE) {
                                                        val avatar = buffer.sliceArray(1 until buffer.size)
                                                        uiCallbacks?.onAvatarChanged(sessionId, avatar)
                                                        AIRADatabase.storeAvatar(avatar)?.let { avatarUuid ->
                                                            setAvatarUuid(sessionId, avatarUuid)
                                                        }
                                                    }
                                                }
                                                Protocol.REMOVE_AVATAR -> {
                                                    uiCallbacks?.onAvatarChanged(sessionId, null)
                                                    setAvatarUuid(sessionId, null)
                                                }
                                                else -> {
                                                    when (buffer[0]){
                                                        Protocol.MESSAGE -> buffer
                                                        Protocol.FILE -> {
                                                            val smallFile = Protocol.parseSmallFile(buffer)
                                                            if (smallFile == null) {
                                                                null
                                                            } else {
                                                                val rawFileUuid = AIRADatabase.storeFile(contacts[sessionId]?.uuid, smallFile.fileContent)
                                                                if (rawFileUuid == null) {
                                                                    null
                                                                } else {
                                                                    byteArrayOf(Protocol.FILE)+rawFileUuid+smallFile.rawFileName
                                                                }
                                                            }
                                                        }
                                                        else -> {
                                                            Log.i("Unknown message type", String.format("%02X", buffer[0]))
                                                            null
                                                        }
                                                    }?.let { handledMsg ->
                                                        val timestamp = TimeUtils.getTimestamp()
                                                        var seen = false
                                                        uiCallbacks?.let { uiCallbacks ->
                                                            seen = uiCallbacks.onNewMessage(sessionId, timestamp, handledMsg)
                                                        }
                                                        setSeen(sessionId, seen)
                                                        var msgSaved = false
                                                        contacts[sessionId]?.let { contact ->
                                                            msgSaved = AIRADatabase.storeMsg(contact.uuid, false, timestamp, handledMsg)
                                                        }
                                                        if (!msgSaved){
                                                            savedMsgs[sessionId]?.add(ChatItem(false, timestamp, handledMsg))
                                                        }
                                                        if (isAppInBackground) {
                                                            sendNotification(sessionId, handledMsg, timestamp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        shouldCloseSession = true
                                    }
                                    if (shouldCloseSession) {
                                        session.close()
                                        key.cancel()
                                        uiCallbacks?.onSessionDisconnect(sessionId)
                                        sessions.remove(sessionId)
                                        savedMsgs.remove(sessionId)
                                        savedNames.remove(sessionId)
                                        sendFileTransfers.remove(sessionId)?.fileTransferNotification?.cancel()
                                        receiveFileTransfers.remove(sessionId)?.fileTransferNotification?.cancel()
                                    }
                                }
                            }
                        } else if (key.isAcceptable) {
                            server?.accept()?.let {
                                handleNewSocket(it, false)
                            }
                        }
                    }
                }
                keys.clear()
            }
            for (session in sessions.values) {
                session.close()
            }
            server?.close()
        }.start()
    }

    fun restartDiscovery() {
        shouldRestartDiscovery = true
        stopDiscovery()
    }

    private fun stopDiscovery() {
        nsdManager.stopServiceDiscovery(nsdDiscoveryListener)
    }

    private fun startDiscovery() {
        nsdManager.discoverServices(Constants.mDNSServiceType, NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannels() {
        notificationManager.createNotificationChannel(
                NotificationChannel(
                        SERVICE_NOTIFICATION_CHANNEL_ID,
                        getString(R.string.service_name),
                        NotificationManager.IMPORTANCE_LOW
                )
        )
        notificationManager.createNotificationChannel(
                NotificationChannel(
                        MESSAGES_NOTIFICATION_CHANNEL_ID,
                        getString(R.string.msg_notification_channel_name),
                        NotificationManager.IMPORTANCE_HIGH
                )
        )
        notificationManager.createNotificationChannel(
                NotificationChannel(
                        ASK_FILE_TRANSFER_NOTIFICATION_CHANNEL_ID,
                        getString(R.string.ask_file_notification_channel),
                        NotificationManager.IMPORTANCE_HIGH
                )
        )
        notificationManager.createNotificationChannel(
                NotificationChannel(
                        FILE_TRANSFER_NOTIFICATION_CHANNEL_ID,
                        getString(R.string.file_transfers),
                        NotificationManager.IMPORTANCE_LOW
                )
        )
    }

    private fun newActionPendingIntent(intentBuilder: (Intent) -> Unit): PendingIntent {
        return PendingIntent.getBroadcast(this, 0,
            Intent(this, NotificationBroadcastReceiver::class.java).apply {
                intentBuilder(this)
            }, FLAG_PENDING_INTENT
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationManager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannels()
        }
        val notificationBuilder = NotificationCompat.Builder(this, SERVICE_NOTIFICATION_CHANNEL_ID)
        val notification: Notification = notificationBuilder
            .setContentTitle(getString(R.string.background_service))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(
                PendingIntent.getActivity(this, 0,
                    Intent(this, MainActivity::class.java).apply {
                        setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                FLAG_PENDING_INTENT)
            )
            .addAction(NotificationCompat.Action(
                R.drawable.ic_launcher,
                getString(R.string.stop),
                newActionPendingIntent {
                    it.action = NotificationBroadcastReceiver.ACTION_LOGOUT
                }
            ))
            .build()
        startForeground(1, notification)

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = Constants.mDNSServiceName
            serviceType = Constants.mDNSServiceType
            port = Constants.port
        }
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener)
            startDiscovery()
        } catch (e: IllegalArgumentException) {
            //can happen if service is restarted too quickly
        }

        startListening()

        isServiceRunning = true

        return START_STICKY
    }

    override fun onDestroy() {
        isServiceRunning = false
    }
}
