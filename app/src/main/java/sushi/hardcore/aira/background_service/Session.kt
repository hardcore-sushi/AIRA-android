package sushi.hardcore.aira.background_service

import android.util.Log
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.whispersystems.curve25519.Curve25519
import sushi.hardcore.aira.AIRADatabase
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.*
import java.nio.channels.spi.SelectorProvider
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

class Session(private val socket: SocketChannel, val outgoing: Boolean): SelectableChannel() {
    private external fun deriveHandshakeKeys(sharedSecret: ByteArray, handshakeHash: ByteArray, iAmBob: Boolean): HandshakeKeys
    private external fun sign(input: ByteArray): ByteArray
    private external fun computeHandshakeFinished(localHandshakeTrafficSecret: ByteArray, handshakeHash: ByteArray): ByteArray
    private external fun verifyHandshakeFinished(peerHandshakeFinished: ByteArray, peerHandshakeTrafficSecret: ByteArray, handshakeHash: ByteArray): Boolean
    private external fun deriveApplicationKeys(handshakeSecret: ByteArray, handshakeHash: ByteArray, iAmBob: Boolean): ApplicationKeys

    companion object {
        private const val RANDOM_LEN = 64
        private const val PUBLIC_KEY_LEN = 32
        private const val SIGNATURE_LEN = 64
        private const val AES_TAG_LEN = 16
        private const val IV_LEN = 12
        private const val HASH_OUTPUT_LEN = 48
        private const val handshakeBufferLen = (2*(RANDOM_LEN+PUBLIC_KEY_LEN))+SIGNATURE_LEN+AES_TAG_LEN
        private const val CIPHER_TYPE = "AES/GCM/NoPadding"
        private const val MESSAGE_LEN_LEN = 4
        private const val PADDED_MAX_SIZE = 32768000
        private const val MAX_RECV_SIZE = MESSAGE_LEN_LEN + PADDED_MAX_SIZE + AES_TAG_LEN
    }

    private val prng = SecureRandom()
    private val handshakeSentBuff = ByteArrayOutputStream(handshakeBufferLen)
    private val handshakeRecvBuff = ByteArrayOutputStream(handshakeBufferLen)
    private val peerCipher = Cipher.getInstance(CIPHER_TYPE)
    private val localCipher = Cipher.getInstance(CIPHER_TYPE)
    private var peerCounter = 0L
    private var localCounter = 0L
    private lateinit var applicationKeys: ApplicationKeys
    lateinit var peerPublicKey: ByteArray
    val ip: String = socket.socket().inetAddress.hostAddress

    private fun handshakeWrite(buffer: ByteArray) {
        writeAll(buffer)
        handshakeSentBuff.write(buffer)
    }

    private fun handshakeRead(buffer: ByteBuffer): Boolean {
        return if (socket.read(buffer) == buffer.position()) {
            handshakeRecvBuff.write(buffer.array())
            true
        } else {
            false
        }
    }
    private fun handshakeRead(buffer: ByteArray): Boolean {
        return handshakeRead(ByteBuffer.wrap(buffer))
    }

    private fun hashHandshake(iAmBob: Boolean): ByteArray {
        MessageDigest.getInstance("SHA-384").apply {
            if (iAmBob) {
                update(handshakeSentBuff.toByteArray())
                update(handshakeRecvBuff.toByteArray())
            } else {
                update(handshakeRecvBuff.toByteArray())
                update(handshakeSentBuff.toByteArray())
            }
            return digest()
        }
    }

    private fun amIBob(): Boolean {
        val s = handshakeSentBuff.toByteArray()
        val r = handshakeRecvBuff.toByteArray()
        for (i in s.indices) {
            if (s[i] != r[i]) {
                return s[i].toInt() and 0xff < r[i].toInt() and 0xff
            }
        }
        throw SecurityException("Handshake buffers are identical")
    }

    private fun ivToNonce(iv: ByteArray, counter: Long): ByteArray {
        val nonce = ByteArray(IV_LEN-Long.SIZE_BYTES)+ByteBuffer.allocate(Long.SIZE_BYTES).putLong(counter).array()
        for (i in nonce.indices) {
            nonce[i] = nonce[i] xor iv[i]
        }
        return nonce
    }

    fun doHandshake(): Boolean {
        val randomBuffer = ByteArray(RANDOM_LEN)
        prng.nextBytes(randomBuffer)
        val curve25519Cipher = Curve25519.getInstance(Curve25519.BEST)
        val keypair = curve25519Cipher.generateKeyPair()
        handshakeWrite(randomBuffer+keypair.publicKey)

        var recvBuffer = ByteBuffer.allocate(RANDOM_LEN+PUBLIC_KEY_LEN)
        if (handshakeRead(recvBuffer)) {
            val peerEphemeralPublicKey = recvBuffer.array().sliceArray(RANDOM_LEN until recvBuffer.capacity())
            val sharedSecret = curve25519Cipher.calculateAgreement(peerEphemeralPublicKey, keypair.privateKey)
            val iAmBob = amIBob() //mutual consensus for keys attribution
            var handshakeHash = hashHandshake(iAmBob)
            val handshakeKeys = deriveHandshakeKeys(sharedSecret, handshakeHash, iAmBob)

            prng.nextBytes(randomBuffer)
            handshakeWrite(randomBuffer)
            if (handshakeRead(randomBuffer)) {
                val localCipher = Cipher.getInstance(CIPHER_TYPE)
                localCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(handshakeKeys.localKey, "AES"), GCMParameterSpec(AES_TAG_LEN*8, ivToNonce(handshakeKeys.localIv, 0)))
                handshakeWrite(localCipher.doFinal(AIRADatabase.getIdentityPublicKey()+sign(keypair.publicKey)))

                recvBuffer = ByteBuffer.allocate(PUBLIC_KEY_LEN+SIGNATURE_LEN+AES_TAG_LEN)
                if (handshakeRead(recvBuffer)) {
                    val peerCipher = Cipher.getInstance(CIPHER_TYPE)
                    peerCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(handshakeKeys.peerKey, "AES"), GCMParameterSpec(AES_TAG_LEN*8, ivToNonce(handshakeKeys.peerIv, 0)))
                    val plainText: ByteArray
                    try {
                        plainText = peerCipher.doFinal(recvBuffer.array())
                    } catch (e: BadPaddingException) {
                        Log.w("BadPaddingException", ip)
                        return false
                    } catch (e: AEADBadTagException) {
                        Log.w("AEADBadTagException", ip)
                        return false
                    }
                    peerPublicKey = plainText.sliceArray(0 until PUBLIC_KEY_LEN)
                    val signature = plainText.sliceArray(PUBLIC_KEY_LEN until plainText.size)

                    val edDSAEngine = EdDSAEngine().apply {
                        initVerify(EdDSAPublicKey(EdDSAPublicKeySpec(peerPublicKey, EdDSANamedCurveTable.ED_25519_CURVE_SPEC)))
                    }
                    if (edDSAEngine.verifyOneShot(peerEphemeralPublicKey, signature)) {
                        handshakeHash = hashHandshake(iAmBob)
                        val handshakeFinished = computeHandshakeFinished(handshakeKeys.localHandshakeTrafficSecret, handshakeHash)
                        writeAll(handshakeFinished)
                        val peerHandshakeFinished = ByteBuffer.allocate(HASH_OUTPUT_LEN)
                        socket.read(peerHandshakeFinished)
                        if (verifyHandshakeFinished(peerHandshakeFinished.array(), handshakeKeys.peerHandshakeTrafficSecret, handshakeHash)){
                            applicationKeys = deriveApplicationKeys(handshakeKeys.handshakeSecret, handshakeHash, iAmBob)
                            handshakeSentBuff.reset()
                            handshakeRecvBuff.reset()
                            return true
                        } else {
                            Log.w("Handshake", "Final verification failed")
                        }
                    } else {
                        Log.w("Handshake", "Signature verification failed")
                    }
                }
            }
        }
        return false
    }

    private fun pad(input: ByteArray, usePadding: Boolean): ByteArray {
        val encodedLen = ByteBuffer.allocate(MESSAGE_LEN_LEN).putInt(input.size).array()
        return if (usePadding) {
            val msgLen = input.size + MESSAGE_LEN_LEN
            var len = 1000
            while (len < msgLen) {
                len *= 2
            }
            val padding = ByteArray(len-msgLen)
            prng.nextBytes(padding)
            encodedLen + input + padding
        } else {
            encodedLen + input
        }
    }

    private fun unpad(input: ByteArray): ByteArray {
        val messageLen = ByteBuffer.wrap(input.sliceArray(0..MESSAGE_LEN_LEN)).int
        return input.sliceArray(MESSAGE_LEN_LEN until MESSAGE_LEN_LEN+messageLen)
    }

    fun writeAll(buffer: ByteArray) {
        val byteBuffer = ByteBuffer.wrap(buffer)
        while (byteBuffer.remaining() > 0) {
            socket.write(byteBuffer)
        }
    }

    fun encrypt(plainText: ByteArray, usePadding: Boolean): ByteArray {
        val padded = pad(plainText, usePadding)
        val rawMsgLen = ByteBuffer.allocate(MESSAGE_LEN_LEN).putInt(padded.size).array()
        val nonce = ivToNonce(applicationKeys.localIv, localCounter)
        localCounter++
        localCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(applicationKeys.localKey, "AES"), GCMParameterSpec(AES_TAG_LEN*8, nonce))
        localCipher.updateAAD(rawMsgLen)
        return rawMsgLen+localCipher.doFinal(padded)
    }

    fun encryptAndSend(plainText: ByteArray, usePadding: Boolean) {
        writeAll(encrypt(plainText, usePadding))
    }

    fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

    private fun readAll(buffer: ByteBuffer): Boolean {
        while (buffer.position() != buffer.capacity()) {
            try {
                if (socket.read(buffer) < 0) {
                    return false
                }
            } catch (e: ClosedChannelException) {
                return false
            }
        }
        return true
    }

    fun receiveAndDecrypt(): ByteArray? {
        val rawMessageLen = ByteBuffer.allocate(MESSAGE_LEN_LEN)
        if (readAll(rawMessageLen)) {
            rawMessageLen.position(0)
            val messageLen = rawMessageLen.int + AES_TAG_LEN
            if (messageLen in 1..MAX_RECV_SIZE) {
                val cipherText = ByteBuffer.allocate(messageLen)
                if (readAll(cipherText)) {
                    val nonce = ivToNonce(applicationKeys.peerIv, peerCounter)
                    peerCounter++
                    peerCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(applicationKeys.peerKey, "AES"), GCMParameterSpec(AES_TAG_LEN*8, nonce))
                    rawMessageLen.position(0)
                    peerCipher.updateAAD(rawMessageLen)
                    try {
                        return unpad(peerCipher.doFinal(cipherText.array()))
                    } catch (e: AEADBadTagException) {
                        Log.w("AEADBadTagException", ip)
                    }
                }
            } else {
                Log.w("Message too large", "$messageLen from $ip")
            }
        }
        return null
    }

    override fun implCloseChannel() {
        socket.close()
    }
    override fun provider(): SelectorProvider {
        return socket.provider()
    }
    override fun validOps(): Int {
        return socket.validOps()
    }
    override fun isRegistered(): Boolean {
        return socket.isRegistered
    }
    override fun keyFor(sel: Selector?): SelectionKey {
        return socket.keyFor(sel)
    }
    override fun register(sel: Selector?, ops: Int, att: Any?): SelectionKey {
        return socket.register(sel, ops, att)
    }
    override fun configureBlocking(block: Boolean): SelectableChannel {
        return socket.configureBlocking(block)
    }
    override fun isBlocking(): Boolean {
        return socket.isBlocking
    }
    override fun blockingLock(): Any {
        return socket.blockingLock()
    }
}