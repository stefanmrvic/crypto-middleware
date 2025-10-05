package com.example.cryptomiddleman

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import org.signal.libsignal.protocol.SignalProtocolAddress

class CryptoProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.cryptomiddleman.provider"
        const val PATH_CONTACTS = "contacts"
        const val PATH_ENCRYPT = "encrypt"
        const val PATH_DECRYPT = "decrypt"
        const val PATH_CURRENT_CONTACT = "current_contact"
        const val PATH_SET_CONTACT = "set_contact"

        private const val CONTACTS = 1
        private const val ENCRYPT = 2
        private const val DECRYPT = 3
        private const val CURRENT_CONTACT = 4
        private const val SET_CONTACT = 5

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_CONTACTS, CONTACTS)
            addURI(AUTHORITY, PATH_ENCRYPT, ENCRYPT)
            addURI(AUTHORITY, PATH_DECRYPT, DECRYPT)
            addURI(AUTHORITY, PATH_CURRENT_CONTACT, CURRENT_CONTACT)
            addURI(AUTHORITY, PATH_SET_CONTACT, SET_CONTACT)
        }
    }

    private lateinit var protocolManager: SignalProtocolManager
    private lateinit var prefs: android.content.SharedPreferences
    private val contacts = mutableMapOf<String, SignalProtocolAddress>()

    override fun onCreate(): Boolean {
        context?.let {
            SecureStorage.init(it)
            prefs = SecureStorage.getPreferences()
            protocolManager = SignalProtocolManager.getInstance(it)
            loadContacts()
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        // Reload contacts on every query to ensure fresh data
        loadContacts()

        return when (uriMatcher.match(uri)) {
            CONTACTS -> getContactsCursor()
            CURRENT_CONTACT -> getCurrentContactCursor()
            else -> null
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            PATH_ENCRYPT -> {
                val message = arg ?: return Bundle().apply { putString("error", "No message") }
                val contactName = extras?.getString("contact") ?: return Bundle().apply {
                    putString("error", "No contact selected")
                }
                encrypt(message, contactName)
            }
            PATH_DECRYPT -> {
                val ciphertext = arg ?: return Bundle().apply { putString("error", "No ciphertext") }
                decrypt(ciphertext)
            }
            PATH_SET_CONTACT -> {
                val contactName = arg ?: return Bundle().apply { putString("error", "No contact name") }
                setCurrentContact(contactName)
            }
            else -> null
        }
    }

    private fun getContactsCursor(): Cursor {
        Log.d("CryptoProvider", "getContactsCursor() called")
        val cursor = MatrixCursor(arrayOf("name", "address", "hasSession"))
        for ((name, address) in contacts) {
            val hasSession = hasActiveSession(address)
            Log.d("CryptoProvider", "Adding contact: $name, hasSession: $hasSession")
            cursor.addRow(arrayOf(name, "${address.name}_${address.deviceId}", if (hasSession) 1 else 0))
        }
        Log.d("CryptoProvider", "Returning ${cursor.count} contacts")
        return cursor
    }

    private fun getCurrentContactCursor(): Cursor {
        val cursor = MatrixCursor(arrayOf("name", "address"))
        val lastContactId = prefs.getString("last_contact_id", null)
        if (lastContactId != null && contacts.containsKey(lastContactId)) {
            val address = contacts[lastContactId]!!
            cursor.addRow(arrayOf(lastContactId, "${address.name}_${address.deviceId}"))
        }
        return cursor
    }

    private fun encrypt(message: String, contactName: String): Bundle {
        return try {
            // Reload contacts to get latest
            loadContacts()

            val address = contacts[contactName] ?: return Bundle().apply {
                putString("error", "Contact not found")
            }

            val ciphertext = protocolManager.encrypt(message, address)

            // Update last used
            prefs.edit().putLong("contact_${contactName}_last_used", System.currentTimeMillis()).apply()

            Bundle().apply {
                putString("ciphertext", ciphertext)
                putString("success", "true")
            }
        } catch (e: Exception) {
            Log.e("CryptoProvider", "Encryption failed", e)
            Bundle().apply {
                putString("error", e.message ?: "Encryption failed")
            }
        }
    }

    private fun decrypt(ciphertext: String): Bundle {
        // Reload contacts to get latest
        loadContacts()

        for ((contactName, address) in contacts) {
            try {
                val plaintext = protocolManager.decrypt(ciphertext, address)

                // Update last used and current contact
                prefs.edit().apply {
                    putString("last_contact_id", contactName)
                    putLong("contact_${contactName}_last_used", System.currentTimeMillis())
                    apply()
                }

                return Bundle().apply {
                    putString("plaintext", plaintext)
                    putString("sender", contactName)
                    putString("success", "true")
                }
            } catch (e: Exception) {
                continue
            }
        }

        return Bundle().apply {
            putString("error", "Could not decrypt with any contact")
        }
    }

    private fun setCurrentContact(contactName: String): Bundle {
        // Reload contacts to get latest
        loadContacts()

        return if (contacts.containsKey(contactName)) {
            prefs.edit().putString("last_contact_id", contactName).apply()
            Bundle().apply {
                putString("success", "true")
            }
        } else {
            Bundle().apply {
                putString("error", "Contact not found")
            }
        }
    }

    private fun loadContacts() {
        Log.d("CryptoProvider", "loadContacts() called")
        val contactsSet = prefs.getStringSet("contact_ids", setOf()) ?: setOf()
        Log.d("CryptoProvider", "Found ${contactsSet.size} contact IDs in prefs")
        contacts.clear()
        for (contactId in contactsSet) {
            val name = prefs.getString("contact_${contactId}_name", null)
            val deviceId = prefs.getInt("contact_${contactId}_device", -1)
            Log.d("CryptoProvider", "Loading contact: id=$contactId, name=$name, deviceId=$deviceId")
            if (name != null && deviceId != -1) {
                contacts[contactId] = SignalProtocolAddress(name, deviceId)
            }
        }
        Log.d("CryptoProvider", "Loaded ${contacts.size} contacts total")
    }

    private fun hasActiveSession(address: SignalProtocolAddress): Boolean {
        return try {
            val sessionDir = context?.filesDir?.let { java.io.File(it, "signal_sessions") }
            val sessionFile = java.io.File(sessionDir, "${address.name}_${address.deviceId}.session")
            sessionFile.exists()
        } catch (e: Exception) {
            false
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}