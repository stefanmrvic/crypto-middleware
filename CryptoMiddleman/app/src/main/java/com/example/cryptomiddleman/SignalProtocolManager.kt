package com.example.cryptomiddleman

import android.content.Context
import android.util.Log
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UsePqRatchet
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import java.security.SecureRandom
import java.util.UUID

class SignalProtocolManager private constructor(context: Context) {

    companion object {
        private const val TAG = "SignalProtocolManager"
        private const val PREFS_NAME = "signal_identity"
        private const val KEY_REGISTRATION_ID = "registration_id"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_IDENTITY_PUBLIC = "identity_public_key"
        private const val KEY_IDENTITY_PRIVATE = "identity_private_key"
        private const val KEY_PREKEY_ID = "prekey_id"
        private const val KEY_PREKEY_PUBLIC = "prekey_public"
        private const val KEY_PREKEY_PRIVATE = "prekey_private"
        private const val KEY_SIGNED_PREKEY_ID = "signed_prekey_id"
        private const val KEY_SIGNED_PREKEY_PUBLIC = "signed_prekey_public"
        private const val KEY_SIGNED_PREKEY_PRIVATE = "signed_prekey_private"
        private const val KEY_SIGNED_PREKEY_SIGNATURE = "signed_prekey_signature"
        private const val KEY_SIGNED_PREKEY_TIMESTAMP = "signed_prekey_timestamp"
        private const val KEY_KYBER_PREKEY_ID = "kyber_prekey_id"
        private const val KEY_KYBER_PREKEY_PUBLIC = "kyber_prekey_public"
        private const val KEY_KYBER_PREKEY_PRIVATE = "kyber_prekey_private"
        private const val KEY_KYBER_PREKEY_SIGNATURE = "kyber_prekey_signature"
        private const val KEY_KYBER_PREKEY_TIMESTAMP = "kyber_prekey_timestamp"

        @Volatile
        private var INSTANCE: SignalProtocolManager? = null

        fun getInstance(context: Context): SignalProtocolManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SignalProtocolManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    private val myRegistrationId: Int
    private val myDeviceId: String
    private val myIdentityKeyPair: IdentityKeyPair
    private val myStore: PersistentSignalProtocolStore

    private val myPreKeyRecord: PreKeyRecord
    private val mySignedPreKeyRecord: SignedPreKeyRecord
    private val myKyberPreKeyRecord: KyberPreKeyRecord

    init {
        // Registration ID
        myRegistrationId = if (prefs.contains(KEY_REGISTRATION_ID)) {
            prefs.getInt(KEY_REGISTRATION_ID, 0)
        } else {
            val regId = secureRandom.nextInt(16383) + 1
            prefs.edit().putInt(KEY_REGISTRATION_ID, regId).apply()
            regId
        }

        // Device ID
        myDeviceId = if (prefs.contains(KEY_DEVICE_ID)) {
            prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString()
        } else {
            val deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            deviceId
        }

        // Identity Key Pair - LOAD OR GENERATE
        myIdentityKeyPair = if (prefs.contains(KEY_IDENTITY_PUBLIC)) {
            Log.d(TAG, "Loading existing identity keys")
            val publicBytes = android.util.Base64.decode(
                prefs.getString(KEY_IDENTITY_PUBLIC, ""),
                android.util.Base64.NO_WRAP
            )
            val privateBytes = android.util.Base64.decode(
                prefs.getString(KEY_IDENTITY_PRIVATE, ""),
                android.util.Base64.NO_WRAP
            )
            val publicKey = IdentityKey(publicBytes)
            val privateKey = ECPrivateKey(privateBytes)
            IdentityKeyPair(publicKey, privateKey)
        } else {
            Log.d(TAG, "Generating new identity keys")
            val keyPair = ECKeyPair.generate()
            val identityKeyPair = IdentityKeyPair(IdentityKey(keyPair.publicKey), keyPair.privateKey)

            // Save to preferences
            prefs.edit().apply {
                putString(KEY_IDENTITY_PUBLIC,
                    android.util.Base64.encodeToString(identityKeyPair.publicKey.serialize(), android.util.Base64.NO_WRAP))
                putString(KEY_IDENTITY_PRIVATE,
                    android.util.Base64.encodeToString(identityKeyPair.privateKey.serialize(), android.util.Base64.NO_WRAP))
                apply()
            }
            identityKeyPair
        }

        myStore = PersistentSignalProtocolStore(appContext, myIdentityKeyPair, myRegistrationId)

        // PreKey - LOAD OR GENERATE
        myPreKeyRecord = if (prefs.contains(KEY_PREKEY_ID)) {
            Log.d(TAG, "Loading existing prekey")
            val preKeyId = prefs.getInt(KEY_PREKEY_ID, 0)
            val publicBytes = android.util.Base64.decode(
                prefs.getString(KEY_PREKEY_PUBLIC, ""),
                android.util.Base64.NO_WRAP
            )
            val privateBytes = android.util.Base64.decode(
                prefs.getString(KEY_PREKEY_PRIVATE, ""),
                android.util.Base64.NO_WRAP
            )
            val keyPair = ECKeyPair(
                org.signal.libsignal.protocol.ecc.ECPublicKey(publicBytes),
                ECPrivateKey(privateBytes)
            )
            PreKeyRecord(preKeyId, keyPair)
        } else {
            Log.d(TAG, "Generating new prekey")
            val preKeyId = secureRandom.nextInt(16383) + 1
            val keyPair = ECKeyPair.generate()
            val record = PreKeyRecord(preKeyId, keyPair)

            prefs.edit().apply {
                putInt(KEY_PREKEY_ID, preKeyId)
                putString(KEY_PREKEY_PUBLIC,
                    android.util.Base64.encodeToString(keyPair.publicKey.serialize(), android.util.Base64.NO_WRAP))
                putString(KEY_PREKEY_PRIVATE,
                    android.util.Base64.encodeToString(keyPair.privateKey.serialize(), android.util.Base64.NO_WRAP))
                apply()
            }
            record
        }

        // Signed PreKey - LOAD OR GENERATE
        mySignedPreKeyRecord = if (prefs.contains(KEY_SIGNED_PREKEY_ID)) {
            Log.d(TAG, "Loading existing signed prekey")
            val signedPreKeyId = prefs.getInt(KEY_SIGNED_PREKEY_ID, 0)
            val timestamp = prefs.getLong(KEY_SIGNED_PREKEY_TIMESTAMP, 0)
            val publicBytes = android.util.Base64.decode(
                prefs.getString(KEY_SIGNED_PREKEY_PUBLIC, ""),
                android.util.Base64.NO_WRAP
            )
            val privateBytes = android.util.Base64.decode(
                prefs.getString(KEY_SIGNED_PREKEY_PRIVATE, ""),
                android.util.Base64.NO_WRAP
            )
            val signatureBytes = android.util.Base64.decode(
                prefs.getString(KEY_SIGNED_PREKEY_SIGNATURE, ""),
                android.util.Base64.NO_WRAP
            )
            val keyPair = ECKeyPair(
                org.signal.libsignal.protocol.ecc.ECPublicKey(publicBytes),
                ECPrivateKey(privateBytes)
            )
            SignedPreKeyRecord(signedPreKeyId, timestamp, keyPair, signatureBytes)
        } else {
            Log.d(TAG, "Generating new signed prekey")
            val signedPreKeyId = secureRandom.nextInt(16383) + 1
            val timestamp = System.currentTimeMillis()
            val keyPair = ECKeyPair.generate()
            val signature = myIdentityKeyPair.privateKey.calculateSignature(keyPair.publicKey.serialize())
            val record = SignedPreKeyRecord(signedPreKeyId, timestamp, keyPair, signature)

            prefs.edit().apply {
                putInt(KEY_SIGNED_PREKEY_ID, signedPreKeyId)
                putLong(KEY_SIGNED_PREKEY_TIMESTAMP, timestamp)
                putString(KEY_SIGNED_PREKEY_PUBLIC,
                    android.util.Base64.encodeToString(keyPair.publicKey.serialize(), android.util.Base64.NO_WRAP))
                putString(KEY_SIGNED_PREKEY_PRIVATE,
                    android.util.Base64.encodeToString(keyPair.privateKey.serialize(), android.util.Base64.NO_WRAP))
                putString(KEY_SIGNED_PREKEY_SIGNATURE,
                    android.util.Base64.encodeToString(signature, android.util.Base64.NO_WRAP))
                apply()
            }
            record
        }

        // Kyber PreKey - ALWAYS GENERATE (cannot be serialized/deserialized easily)
        myKyberPreKeyRecord = run {
            Log.d(TAG, "Generating new kyber prekey")
            val kyberPreKeyId = secureRandom.nextInt(16383) + 1
            val timestamp = System.currentTimeMillis()
            val keyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
            val signature = myIdentityKeyPair.privateKey.calculateSignature(keyPair.publicKey.serialize())
            val record = KyberPreKeyRecord(kyberPreKeyId, timestamp, keyPair, signature)

            prefs.edit().apply {
                putInt(KEY_KYBER_PREKEY_ID, kyberPreKeyId)
                putLong(KEY_KYBER_PREKEY_TIMESTAMP, timestamp)
                putString(KEY_KYBER_PREKEY_PUBLIC,
                    android.util.Base64.encodeToString(keyPair.publicKey.serialize(), android.util.Base64.NO_WRAP))
                putString(KEY_KYBER_PREKEY_PRIVATE,
                    android.util.Base64.encodeToString(keyPair.secretKey.serialize(), android.util.Base64.NO_WRAP))
                putString(KEY_KYBER_PREKEY_SIGNATURE,
                    android.util.Base64.encodeToString(signature, android.util.Base64.NO_WRAP))
                apply()
            }
            record
        }

        myStore.storePreKey(myPreKeyRecord.id, myPreKeyRecord)
        myStore.storeSignedPreKey(mySignedPreKeyRecord.id, mySignedPreKeyRecord)
        myStore.storeKyberPreKey(myKyberPreKeyRecord.id, myKyberPreKeyRecord)

        Log.d(TAG, "Initialized with registration ID: $myRegistrationId, Device ID: $myDeviceId")
    }

    fun getMyPreKeyBundleParts(): List<String> {
        val part1 = """
        {
            "part": 1,
            "total": 6,
            "deviceId": "$myDeviceId",
            "registrationId": $myRegistrationId,
            "identityKey": "${android.util.Base64.encodeToString(myIdentityKeyPair.publicKey.serialize(), android.util.Base64.NO_WRAP)}",
            "preKeyId": ${myPreKeyRecord.id},
            "preKeyPublic": "${android.util.Base64.encodeToString(myPreKeyRecord.keyPair.publicKey.serialize(), android.util.Base64.NO_WRAP)}"
        }
        """.trimIndent()

        val part2 = """
        {
            "part": 2,
            "total": 6,
            "signedPreKeyId": ${mySignedPreKeyRecord.id},
            "signedPreKeyPublic": "${android.util.Base64.encodeToString(mySignedPreKeyRecord.keyPair.publicKey.serialize(), android.util.Base64.NO_WRAP)}",
            "signedPreKeySignature": "${android.util.Base64.encodeToString(mySignedPreKeyRecord.signature, android.util.Base64.NO_WRAP)}"
        }
        """.trimIndent()

        val kyberPublicBytes = myKyberPreKeyRecord.keyPair.publicKey.serialize()
        val kyberPublicBase64 = android.util.Base64.encodeToString(kyberPublicBytes, android.util.Base64.NO_WRAP)
        val quarterLength = kyberPublicBase64.length / 4

        val part3 = """
        {
            "part": 3,
            "total": 6,
            "kyberPreKeyId": ${myKyberPreKeyRecord.id},
            "kyberPart1": "${kyberPublicBase64.substring(0, quarterLength)}"
        }
        """.trimIndent()

        val part4 = """
        {
            "part": 4,
            "total": 6,
            "kyberPart2": "${kyberPublicBase64.substring(quarterLength, quarterLength * 2)}"
        }
        """.trimIndent()

        val part5 = """
        {
            "part": 5,
            "total": 6,
            "kyberPart3": "${kyberPublicBase64.substring(quarterLength * 2, quarterLength * 3)}"
        }
        """.trimIndent()

        val part6 = """
        {
            "part": 6,
            "total": 6,
            "kyberPart4": "${kyberPublicBase64.substring(quarterLength * 3)}",
            "kyberSignature": "${android.util.Base64.encodeToString(myKyberPreKeyRecord.signature, android.util.Base64.NO_WRAP)}"
        }
        """.trimIndent()

        return listOf(part1, part2, part3, part4, part5, part6)
    }

    fun processRemotePreKeyBundleParts(parts: List<String>, remoteAddress: SignalProtocolAddress) {
        require(parts.size == 6) { "Expected 6 parts, got ${parts.size}" }

        val deviceId = extractJsonString(parts[0], "deviceId")
        val registrationId = extractJsonInt(parts[0], "registrationId")
        val identityKeyBytes = extractJsonBase64(parts[0], "identityKey")
        val preKeyId = extractJsonInt(parts[0], "preKeyId")
        val preKeyPublicBytes = extractJsonBase64(parts[0], "preKeyPublic")

        val signedPreKeyId = extractJsonInt(parts[1], "signedPreKeyId")
        val signedPreKeyPublicBytes = extractJsonBase64(parts[1], "signedPreKeyPublic")
        val signedPreKeySignature = extractJsonBase64(parts[1], "signedPreKeySignature")

        val kyberPreKeyId = extractJsonInt(parts[2], "kyberPreKeyId")
        val kyberPart1 = extractJsonString(parts[2], "kyberPart1")
        val kyberPart2 = extractJsonString(parts[3], "kyberPart2")
        val kyberPart3 = extractJsonString(parts[4], "kyberPart3")
        val kyberPart4 = extractJsonString(parts[5], "kyberPart4")
        val kyberPublicBase64 = kyberPart1 + kyberPart2 + kyberPart3 + kyberPart4
        val kyberPreKeyPublicBytes = android.util.Base64.decode(kyberPublicBase64, android.util.Base64.NO_WRAP)
        val kyberPreKeySignature = extractJsonBase64(parts[5], "kyberSignature")

        Log.d(TAG, "Processing remote device: $deviceId with regId: $registrationId")

        val identityKey = IdentityKey(identityKeyBytes)
        val preKeyPublic = org.signal.libsignal.protocol.ecc.ECPublicKey(preKeyPublicBytes)
        val signedPreKeyPublic = org.signal.libsignal.protocol.ecc.ECPublicKey(signedPreKeyPublicBytes)
        val kyberPreKeyPublic = org.signal.libsignal.protocol.kem.KEMPublicKey(kyberPreKeyPublicBytes)

        val bundle = PreKeyBundle(
            registrationId, 1, preKeyId, preKeyPublic,
            signedPreKeyId, signedPreKeyPublic, signedPreKeySignature,
            identityKey, kyberPreKeyId, kyberPreKeyPublic, kyberPreKeySignature
        )

        val sessionBuilder = SessionBuilder(myStore, remoteAddress)
        sessionBuilder.process(bundle, UsePqRatchet.YES)
        Log.d(TAG, "Session established with ${remoteAddress.name}")
    }

    fun encrypt(message: String, remoteAddress: SignalProtocolAddress): String {
        try {
            Log.d(TAG, "Encrypting message: '$message' for ${remoteAddress.name}")
            val cipher = SessionCipher(myStore, remoteAddress)
            val cipherMessage = cipher.encrypt(message.toByteArray())
            val encoded = android.util.Base64.encodeToString(cipherMessage.serialize(), android.util.Base64.NO_WRAP)
            Log.d(TAG, "Encrypted successfully. Type: ${cipherMessage.type}, Length: ${encoded.length}")
            Log.d(TAG, "Ciphertext: $encoded")
            return encoded
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            throw e
        }
    }

    fun decrypt(ciphertext: String, remoteAddress: SignalProtocolAddress): String {
        try {
            Log.d(TAG, "Decrypting message from ${remoteAddress.name}")

            val cleanedCiphertext = ciphertext.replace("\\s".toRegex(), "")
            Log.d(TAG, "Cleaned ciphertext length: ${cleanedCiphertext.length}")

            val cipher = SessionCipher(myStore, remoteAddress)
            val ciphertextBytes = android.util.Base64.decode(cleanedCiphertext, android.util.Base64.NO_WRAP)

            Log.d(TAG, "Ciphertext bytes length: ${ciphertextBytes.size}")
            Log.d(TAG, "First 20 bytes: ${ciphertextBytes.take(20).joinToString(",") { it.toInt().and(0xFF).toString() }}")

            // Try PreKeySignalMessage first (most likely for first message)
            val plaintext = try {
                Log.d(TAG, "Attempting to decrypt as PreKeySignalMessage")
                val preKeyMessage = PreKeySignalMessage(ciphertextBytes)
                cipher.decrypt(preKeyMessage, UsePqRatchet.YES)
            } catch (e: Exception) {
                Log.d(TAG, "PreKeySignalMessage failed, trying SignalMessage: ${e.message}")
                try {
                    val signalMessage = SignalMessage(ciphertextBytes)
                    cipher.decrypt(signalMessage)
                } catch (e2: Exception) {
                    Log.e(TAG, "Both message types failed")
                    throw e2
                }
            }

            val result = String(plaintext)
            Log.d(TAG, "Message decrypted successfully: '$result'")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            throw e
        }
    }

    private fun extractJsonInt(json: String, key: String): Int {
        val regex = "\"$key\":\\s*(\\d+)".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toInt() ?: 0
    }

    private fun extractJsonBase64(json: String, key: String): ByteArray {
        val regex = "\"$key\":\\s*\"([^\"]+)\"".toRegex()
        val base64 = regex.find(json)?.groupValues?.get(1) ?: ""
        return android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
    }

    private fun extractJsonString(json: String, key: String): String {
        val regex = "\"$key\":\\s*\"([^\"]+)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1) ?: ""
    }
}