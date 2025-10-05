package com.example.cryptomiddleman

import android.content.Context
import android.util.Log
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import java.io.File

class SessionStorage(private val context: Context) {

    private val TAG = "SessionStorage"

    // Directory for storing session data
    private val sessionDir = File(context.filesDir, "signal_sessions")

    init {
        if (!sessionDir.exists()) {
            sessionDir.mkdirs()
        }
    }

    fun saveSession(address: SignalProtocolAddress, record: SessionRecord) {
        try {
            val fileName = "${address.name}_${address.deviceId}.session"
            val file = File(sessionDir, fileName)
            file.writeBytes(record.serialize())
            Log.d(TAG, "Session saved: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session", e)
        }
    }

    fun loadSession(address: SignalProtocolAddress): SessionRecord? {
        try {
            val fileName = "${address.name}_${address.deviceId}.session"
            val file = File(sessionDir, fileName)

            if (!file.exists()) {
                Log.d(TAG, "No saved session found: $fileName")
                return null
            }

            val bytes = file.readBytes()
            val record = SessionRecord(bytes)
            Log.d(TAG, "Session loaded: $fileName")
            return record
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session", e)
            return null
        }
    }

    fun sessionExists(address: SignalProtocolAddress): Boolean {
        val fileName = "${address.name}_${address.deviceId}.session"
        return File(sessionDir, fileName).exists()
    }

    fun deleteSession(address: SignalProtocolAddress) {
        try {
            val fileName = "${address.name}_${address.deviceId}.session"
            val file = File(sessionDir, fileName)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Session deleted: $fileName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session", e)
        }
    }

    fun clearAllSessions() {
        try {
            sessionDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "All sessions cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear sessions", e)
        }
    }
}