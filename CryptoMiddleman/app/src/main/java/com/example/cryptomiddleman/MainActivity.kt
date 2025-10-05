package com.example.cryptomiddleman

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.signal.libsignal.protocol.SignalProtocolAddress
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var btnShowMyQR: Button
    private lateinit var btnScanQR: Button
    private lateinit var btnSelectContact: Button
    private lateinit var btnDeleteContact: Button
    private lateinit var etMessage: EditText
    private lateinit var btnEncrypt: Button
    private lateinit var btnDecrypt: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvCurrentContact: TextView
    private lateinit var ivClearMessage: ImageView

    private lateinit var protocolManager: SignalProtocolManager
    private var currentRemoteAddress: SignalProtocolAddress? = null
    private val scannedParts = mutableSetOf<Int>()
    private val keyParts = mutableMapOf<Int, String>()
    private var expectedTotal: Int = 6
    private var scannedDeviceId: String? = null

    private lateinit var prefs: android.content.SharedPreferences
    private val contacts = mutableMapOf<String, SignalProtocolAddress>()

    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val qrCode = result.data?.getStringExtra(QRScannerActivity.EXTRA_QR_RESULT)
            if (qrCode != null) {
                processScannedPart(qrCode)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize secure storage FIRST
        SecureStorage.init(this)

        btnShowMyQR = findViewById(R.id.btnShowMyQR)
        btnScanQR = findViewById(R.id.btnScanQR)
        btnSelectContact = findViewById(R.id.btnSelectContact)
        btnDeleteContact = findViewById(R.id.btnDeleteContact)
        etMessage = findViewById(R.id.etMessage)
        btnEncrypt = findViewById(R.id.btnEncrypt)
        btnDecrypt = findViewById(R.id.btnDecrypt)
        tvStatus = findViewById(R.id.tvStatus)
        tvCurrentContact = findViewById(R.id.tvCurrentContact)
        ivClearMessage = findViewById(R.id.ivClearMessage)

        prefs = SecureStorage.getPreferences()
        protocolManager = SignalProtocolManager.getInstance(this)

        loadContacts()

        val lastContactId = prefs.getString("last_contact_id", null)
        if (lastContactId != null && contacts.containsKey(lastContactId)) {
            currentRemoteAddress = contacts[lastContactId]
        }

        btnShowMyQR.setOnClickListener {
            showMultiPartQRDialog()
        }

        btnScanQR.setOnClickListener {
            val intent = Intent(this, QRScannerActivity::class.java)
            qrScannerLauncher.launch(intent)
        }

        btnSelectContact.setOnClickListener {
            showContactSelector()
        }

        btnDeleteContact.setOnClickListener {
            showDeleteContactDialog()
        }

        ivClearMessage.setOnClickListener {
            etMessage.text.clear()
            etMessage.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT)
        }

        btnEncrypt.setOnClickListener {
            if (currentRemoteAddress == null) {
                Toast.makeText(this, "First select a contact or scan QR codes!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val message = etMessage.text.toString()
            if (message.isEmpty()) {
                Toast.makeText(this, "Enter message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val ciphertext = protocolManager.encrypt(message, currentRemoteAddress!!)
                etMessage.setText(ciphertext)
                copyToClipboard(ciphertext)

                val contactName = contacts.entries.find { it.value == currentRemoteAddress }?.key
                if (contactName != null) {
                    prefs.edit().putLong("contact_${contactName}_last_used", System.currentTimeMillis()).apply()
                }

                Toast.makeText(this, "Encrypted & copied", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Encryption failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnDecrypt.setOnClickListener {
            val ciphertext = etMessage.text.toString()
            if (ciphertext.isEmpty()) {
                Toast.makeText(this, "Paste ciphertext first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            autoDecrypt(ciphertext)
        }

        updateStatus()
    }

    private fun hasActiveSession(address: SignalProtocolAddress): Boolean {
        return try {
            val sessionDir = File(filesDir, "signal_sessions")
            val sessionFile = File(sessionDir, "${address.name}_${address.deviceId}.session")
            sessionFile.exists()
        } catch (e: Exception) {
            false
        }
    }

    private fun autoDecrypt(ciphertext: String) {
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No contacts. Scan QR codes first.", Toast.LENGTH_SHORT).show()
            return
        }

        var decrypted = false

        for ((contactName, address) in contacts) {
            try {
                val plaintext = protocolManager.decrypt(ciphertext, address)
                etMessage.setText(plaintext)
                currentRemoteAddress = address
                prefs.edit().apply {
                    putString("last_contact_id", contactName)
                    putLong("contact_${contactName}_last_used", System.currentTimeMillis())
                    apply()
                }
                updateStatus()
                Toast.makeText(this, "Decrypted from $contactName", Toast.LENGTH_SHORT).show()
                decrypted = true
                break
            } catch (e: Exception) {
                continue
            }
        }

        if (!decrypted) {
            Toast.makeText(this, "Could not decrypt with any contact", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadContacts() {
        val contactsSet = prefs.getStringSet("contact_ids", setOf()) ?: setOf()
        contacts.clear()
        for (contactId in contactsSet) {
            val name = prefs.getString("contact_${contactId}_name", null)
            val deviceId = prefs.getInt("contact_${contactId}_device", -1)
            if (name != null && deviceId != -1) {
                contacts[contactId] = SignalProtocolAddress(name, deviceId)
            }
        }
    }

    private fun saveContact(contactId: String, address: SignalProtocolAddress) {
        val contactsSet = prefs.getStringSet("contact_ids", setOf())?.toMutableSet() ?: mutableSetOf()
        contactsSet.add(contactId)

        prefs.edit().apply {
            putStringSet("contact_ids", contactsSet)
            putString("contact_${contactId}_name", address.name)
            putInt("contact_${contactId}_device", address.deviceId)
            apply()
        }

        contacts[contactId] = address
    }

    private fun deleteContact(contactName: String) {
        val address = contacts[contactName] ?: return

        val sessionDir = File(filesDir, "signal_sessions")
        val sessionFile = File(sessionDir, "${address.name}_${address.deviceId}.session")
        sessionFile.delete()

        val contactsSet = prefs.getStringSet("contact_ids", setOf())?.toMutableSet() ?: mutableSetOf()
        contactsSet.remove(contactName)

        prefs.edit().apply {
            putStringSet("contact_ids", contactsSet)
            remove("contact_${contactName}_name")
            remove("contact_${contactName}_device")
            remove("contact_${contactName}_last_used")
            apply()
        }

        contacts.remove(contactName)

        if (currentRemoteAddress == address) {
            currentRemoteAddress = null
            prefs.edit().remove("last_contact_id").apply()
        }

        updateStatus()
    }

    private fun contactExists(name: String): Boolean {
        return contacts.keys.any { it.equals(name, ignoreCase = true) }
    }

    private fun showDeleteContactDialog() {
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No contacts to delete", Toast.LENGTH_SHORT).show()
            return
        }

        val contactNames = contacts.keys.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setItems(contactNames) { _, which ->
                val contactToDelete = contactNames[which]

                AlertDialog.Builder(this)
                    .setTitle("Confirm Delete")
                    .setMessage("Delete contact '$contactToDelete' and its session?")
                    .setPositiveButton("Delete") { _, _ ->
                        deleteContact(contactToDelete)
                        Toast.makeText(this, "Deleted $contactToDelete", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showContactSelector() {
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No contacts yet. Scan QR codes first.", Toast.LENGTH_SHORT).show()
            return
        }

        val contactNames = contacts.keys.toTypedArray()
        val contactInfo = contactNames.map { name ->
            val address = contacts[name]!!
            val sessionStatus = if (hasActiveSession(address)) {
                "✓ Session Active"
            } else {
                "✗ No Session"
            }

            val lastUsed = prefs.getLong("contact_${name}_last_used", 0)
            val timeAgo = if (lastUsed > 0) {
                val diff = System.currentTimeMillis() - lastUsed
                when {
                    diff < 60000 -> "Just now"
                    diff < 3600000 -> "${diff / 60000}min ago"
                    diff < 86400000 -> "${diff / 3600000}h ago"
                    else -> "${diff / 86400000}d ago"
                }
            } else {
                "Never used"
            }

            "$name - $sessionStatus - $timeAgo"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Contact")
            .setItems(contactInfo) { _, which ->
                val selectedId = contactNames[which]
                currentRemoteAddress = contacts[selectedId]

                if (!hasActiveSession(currentRemoteAddress!!)) {
                    Toast.makeText(this, "Warning: No session found for $selectedId. Re-scan QR codes.", Toast.LENGTH_LONG).show()
                }

                prefs.edit().putString("last_contact_id", selectedId).apply()
                updateStatus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMultiPartQRDialog() {
        val parts = protocolManager.getMyPreKeyBundleParts()
        var currentPart = 0

        val dialogView = layoutInflater.inflate(R.layout.dialog_qr_code_multipart, null)
        val ivQRCode = dialogView.findViewById<ImageView>(R.id.ivQRCode)
        val tvPartNumber = dialogView.findViewById<TextView>(R.id.tvPartNumber)
        val btnPrevious = dialogView.findViewById<Button>(R.id.btnPrevious)
        val btnNext = dialogView.findViewById<Button>(R.id.btnNext)

        val dialog = AlertDialog.Builder(this)
            .setTitle("My Keys")
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()

        fun updateQR() {
            val qrBitmap = QRCodeGenerator.generateQRCode(parts[currentPart])
            ivQRCode.setImageBitmap(qrBitmap)
            tvPartNumber.text = "QR ${currentPart + 1} of ${parts.size}"
            btnPrevious.isEnabled = currentPart > 0
            btnNext.text = if (currentPart < parts.size - 1) "Next" else "Done"
        }

        btnPrevious.setOnClickListener {
            if (currentPart > 0) {
                currentPart--
                updateQR()
            }
        }

        btnNext.setOnClickListener {
            if (currentPart < parts.size - 1) {
                currentPart++
                updateQR()
            } else {
                dialog.dismiss()
            }
        }

        updateQR()
        dialog.show()
    }

    private fun processScannedPart(partJson: String) {
        try {
            val partNumber = extractJsonInt(partJson, "part")
            val total = extractJsonInt(partJson, "total")

            if (partNumber == 1) {
                scannedDeviceId = extractJsonString(partJson, "deviceId")
            }

            if (expectedTotal == 0 || expectedTotal != total) {
                expectedTotal = total
            }

            if (scannedParts.contains(partNumber)) {
                Toast.makeText(this, "Already scanned part $partNumber", Toast.LENGTH_SHORT).show()
                return
            }

            keyParts[partNumber] = partJson
            scannedParts.add(partNumber)

            Toast.makeText(this, "Scanned part $partNumber of $total", Toast.LENGTH_SHORT).show()
            updateStatus()

            if (scannedParts.size == total) {
                showContactNameDialog()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid QR: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showContactNameDialog() {
        val input = EditText(this)
        input.hint = "Enter contact name"

        val dialog = AlertDialog.Builder(this)
            .setTitle("Name this contact")
            .setMessage("Contact names are case-insensitive")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val contactName = input.text.toString().trim()

                if (contactName.isEmpty()) {
                    Toast.makeText(this, "Contact name cannot be empty", Toast.LENGTH_SHORT).show()
                    scannedParts.clear()
                    keyParts.clear()
                    expectedTotal = 6
                    scannedDeviceId = null
                    return@setPositiveButton
                }

                if (contactExists(contactName)) {
                    Toast.makeText(this, "Contact '$contactName' already exists (case-insensitive)", Toast.LENGTH_LONG).show()
                    scannedParts.clear()
                    keyParts.clear()
                    expectedTotal = 6
                    scannedDeviceId = null
                    return@setPositiveButton
                }

                val sortedParts = (1..expectedTotal).map { keyParts[it]!! }
                val remoteAddress = SignalProtocolAddress(scannedDeviceId ?: "remote", 1)
                protocolManager.processRemotePreKeyBundleParts(sortedParts, remoteAddress)

                saveContact(contactName, remoteAddress)
                currentRemoteAddress = remoteAddress
                prefs.edit().putString("last_contact_id", contactName).apply()

                scannedParts.clear()
                keyParts.clear()
                expectedTotal = 6
                scannedDeviceId = null
                updateStatus()
                Toast.makeText(this, "Session established with $contactName!", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel") { _, _ ->
                scannedParts.clear()
                keyParts.clear()
                expectedTotal = 6
                scannedDeviceId = null
            }
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun extractJsonInt(json: String, key: String): Int {
        val regex = "\"$key\":\\s*(\\d+)".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.toInt() ?: 0
    }

    private fun extractJsonString(json: String, key: String): String {
        val regex = "\"$key\":\\s*\"([^\"]+)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1) ?: ""
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("data", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun updateStatus() {
        if (currentRemoteAddress != null) {
            val contactName = contacts.entries.find { it.value == currentRemoteAddress }?.key ?: "Unknown"
            val hasSession = hasActiveSession(currentRemoteAddress!!)

            if (hasSession) {
                tvStatus.text = "✓ Session Active"
                tvStatus.setBackgroundColor(getColor(android.R.color.holo_green_light))
                tvCurrentContact.text = "Contact: $contactName"
            } else {
                tvStatus.text = "✗ Session Lost"
                tvStatus.setBackgroundColor(getColor(android.R.color.holo_red_light))
                tvCurrentContact.text = "Contact: $contactName (Re-scan QR codes)"
            }
        } else {
            val scanned = scannedParts.size
            if (scanned > 0) {
                tvStatus.text = "⏳ Scanned $scanned/$expectedTotal QR codes"
                tvStatus.setBackgroundColor(getColor(android.R.color.holo_orange_light))
            } else {
                tvStatus.text = "⚠ No Session"
                tvStatus.setBackgroundColor(getColor(android.R.color.holo_orange_light))
            }
            tvCurrentContact.text = if (contacts.isEmpty()) "No contacts" else "Select a contact"
        }
    }
}