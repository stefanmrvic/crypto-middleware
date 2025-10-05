package com.example.cryptomiddleman

import android.content.Context
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.UUID

class PersistentSignalProtocolStore(
    private val context: Context,
    private val identityKeyPair: IdentityKeyPair,
    private val registrationId: Int
) : SignalProtocolStore {

    private val sessionStorage = SessionStorage(context)
    private val preKeys = mutableMapOf<Int, PreKeyRecord>()
    private val signedPreKeys = mutableMapOf<Int, SignedPreKeyRecord>()
    private val kyberPreKeys = mutableMapOf<Int, KyberPreKeyRecord>()
    private val senderKeys = mutableMapOf<Pair<SignalProtocolAddress, UUID>, SenderKeyRecord>()

    // IdentityKeyStore implementation
    override fun getIdentityKeyPair(): IdentityKeyPair = identityKeyPair
    override fun getLocalRegistrationId(): Int = registrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange {
        return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        return true
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        return null
    }

    // SessionStore implementation
    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        return sessionStorage.loadSession(address) ?: SessionRecord()
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<SessionRecord> {
        return addresses.mapNotNull { sessionStorage.loadSession(it) }.toMutableList()
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        sessionStorage.saveSession(address, record)
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return sessionStorage.sessionExists(address)
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        sessionStorage.deleteSession(address)
    }

    override fun deleteAllSessions(name: String) {
        sessionStorage.clearAllSessions()
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> {
        return mutableListOf()
    }

    // PreKeyStore implementation
    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        return preKeys[preKeyId] ?: throw IllegalStateException("No PreKey with ID $preKeyId")
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeys[preKeyId] = record
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return preKeys.containsKey(preKeyId)
    }

    override fun removePreKey(preKeyId: Int) {
        preKeys.remove(preKeyId)
    }

    // SignedPreKeyStore implementation
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        return signedPreKeys[signedPreKeyId]
            ?: throw IllegalStateException("No SignedPreKey with ID $signedPreKeyId")
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> {
        return signedPreKeys.values.toMutableList()
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signedPreKeys[signedPreKeyId] = record
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return signedPreKeys.containsKey(signedPreKeyId)
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signedPreKeys.remove(signedPreKeyId)
    }

    // KyberPreKeyStore implementation
    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
        return kyberPreKeys[kyberPreKeyId]
            ?: throw IllegalStateException("No KyberPreKey with ID $kyberPreKeyId")
    }

    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> {
        return kyberPreKeys.values.toMutableList()
    }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
        kyberPreKeys[kyberPreKeyId] = record
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
        return kyberPreKeys.containsKey(kyberPreKeyId)
    }

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int) {
        // Mark as used
    }

    // SenderKeyStore implementation
    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
        senderKeys[Pair(sender, distributionId)] = record
    }

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? {
        return senderKeys[Pair(sender, distributionId)]
    }
}