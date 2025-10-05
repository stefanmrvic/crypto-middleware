package com.example.cryptomiddleware

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.impl.InMemoryIdentityKeyStore
import org.signal.libsignal.protocol.state.impl.InMemoryPreKeyStore
import org.signal.libsignal.protocol.state.impl.InMemorySessionStore
import org.signal.libsignal.protocol.state.impl.InMemorySignedPreKeyStore
import org.signal.libsignal.protocol.ecc.ECKeyPair
import kotlin.random.Random

class SignalKeyManager {

    private val registrationId = Random.nextInt(1, 16384)

    // Generate identity key pair
    private val identityECKeyPair = ECKeyPair.generate()
    private val identityKeyPair: IdentityKeyPair = IdentityKeyPair(
        IdentityKey(identityECKeyPair.publicKey),
        identityECKeyPair.privateKey
    )

    val identityStore = InMemoryIdentityKeyStore(identityKeyPair, registrationId)
    val preKeyStore: PreKeyStore = InMemoryPreKeyStore()
    val signedPreKeyStore: SignedPreKeyStore = InMemorySignedPreKeyStore()
    val sessionStore: SessionStore = InMemorySessionStore()

    fun generatePreKeys(count: Int = 100) {
        // Generate a batch of pre-keys
        for (i in 1..count) {
            val preKeyPair = ECKeyPair.generate()
            val preKey = PreKeyRecord(i, preKeyPair)
            preKeyStore.storePreKey(i, preKey)
        }

        // Generate a signed pre-key
        val timestamp = System.currentTimeMillis()
        val signedPreKeyPair = ECKeyPair.generate()
        val signedPreKeySignature = identityKeyPair.privateKey.calculateSignature(
            signedPreKeyPair.publicKey.serialize()
        )
        val signedPreKey = SignedPreKeyRecord(
            1,
            timestamp,
            signedPreKeyPair,
            signedPreKeySignature
        )
        signedPreKeyStore.storeSignedPreKey(1, signedPreKey)
    }
}