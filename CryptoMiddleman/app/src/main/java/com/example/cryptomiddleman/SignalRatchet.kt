package com.example.cryptomiddleman

import android.content.Context
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UsePqRatchet
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import java.security.SecureRandom

class SignalRatchet private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: SignalRatchet? = null

        fun getInstance(context: Context): SignalRatchet {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SignalRatchet(context.applicationContext).also {
                    it.initSession()
                    INSTANCE = it
                }
            }
        }
    }

    private val secureRandom = SecureRandom()
    private val aliceRegId = secureRandom.nextInt(16383) + 1
    private val bobRegId = secureRandom.nextInt(16383) + 1

    private val aliceKeyPair = ECKeyPair.generate()
    private val aliceIdentity = IdentityKeyPair(IdentityKey(aliceKeyPair.publicKey), aliceKeyPair.privateKey)

    private val bobKeyPair = ECKeyPair.generate()
    private val bobIdentity = IdentityKeyPair(IdentityKey(bobKeyPair.publicKey), bobKeyPair.privateKey)

    private val aliceStore = PersistentSignalProtocolStore(context, aliceIdentity, aliceRegId)
    private val bobStore = PersistentSignalProtocolStore(context, bobIdentity, bobRegId)

    private lateinit var aliceCipher: SessionCipher
    private lateinit var bobCipher: SessionCipher

    private val aliceAddress = SignalProtocolAddress("alice", 1)
    private val bobAddress = SignalProtocolAddress("bob", 1)

    private fun initSession() {
        if (aliceStore.containsSession(bobAddress)) {
            aliceCipher = SessionCipher(aliceStore, bobAddress)
            bobCipher = SessionCipher(bobStore, aliceAddress)
            return
        }

        val bobPreKeyId = secureRandom.nextInt(16383) + 1
        val bobPreKeyPair = ECKeyPair.generate()
        val bobPreKey = PreKeyRecord(bobPreKeyId, bobPreKeyPair)

        val bobSignedPreKeyId = secureRandom.nextInt(16383) + 1
        val timestamp = System.currentTimeMillis()
        val bobSignedPreKeyPair = ECKeyPair.generate()
        val bobSignedPreKeySignature = bobIdentity.privateKey.calculateSignature(
            bobSignedPreKeyPair.publicKey.serialize()
        )
        val bobSignedPreKey = SignedPreKeyRecord(
            bobSignedPreKeyId,
            timestamp,
            bobSignedPreKeyPair,
            bobSignedPreKeySignature
        )

        val bobKyberPreKeyId = secureRandom.nextInt(16383) + 1
        val bobKyberKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val bobKyberSignature = bobIdentity.privateKey.calculateSignature(
            bobKyberKeyPair.publicKey.serialize()
        )

        val bobKyberPreKey = KyberPreKeyRecord(
            bobKyberPreKeyId,
            timestamp,
            bobKyberKeyPair,
            bobKyberSignature
        )

        bobStore.storePreKey(bobPreKeyId, bobPreKey)
        bobStore.storeSignedPreKey(bobSignedPreKeyId, bobSignedPreKey)
        bobStore.storeKyberPreKey(bobKyberPreKeyId, bobKyberPreKey)

        val bobBundle = PreKeyBundle(
            bobRegId,
            1,
            bobPreKeyId,
            bobPreKey.keyPair.publicKey,
            bobSignedPreKeyId,
            bobSignedPreKey.keyPair.publicKey,
            bobSignedPreKey.signature,
            bobIdentity.publicKey,
            bobKyberPreKeyId,
            bobKyberKeyPair.publicKey,
            bobKyberSignature
        )

        val sessionBuilder = SessionBuilder(aliceStore, bobAddress)
        sessionBuilder.process(bobBundle, UsePqRatchet.YES)

        aliceCipher = SessionCipher(aliceStore, bobAddress)
        bobCipher = SessionCipher(bobStore, aliceAddress)
    }

    fun encryptFromAlice(message: String): CiphertextMessage {
        return aliceCipher.encrypt(message.toByteArray())
    }

    fun decryptOnBob(ciphertext: CiphertextMessage): String {
        val plaintext = if (ciphertext.type == CiphertextMessage.PREKEY_TYPE) {
            bobCipher.decrypt(ciphertext as PreKeySignalMessage, UsePqRatchet.YES)
        } else {
            bobCipher.decrypt(ciphertext as SignalMessage)
        }
        return String(plaintext)
    }
}