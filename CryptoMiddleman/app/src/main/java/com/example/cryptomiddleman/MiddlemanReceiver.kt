package com.example.cryptomiddleman

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.signal.libsignal.protocol.message.CiphertextMessage

class MiddlemanReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MiddlemanReceiver"
        private var signalRatchet: SignalRatchet? = null

        fun initSession(context: Context) {
            signalRatchet = SignalRatchet.getInstance(context)
            Log.d(TAG, "Signal session initialized")
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (signalRatchet == null) {
            initSession(context)
        }

        when (intent.action) {
            "com.example.MIDDLEMAN_ENCRYPT" -> {
                val plaintext = intent.getStringExtra("plaintext")
                if (plaintext != null) {
                    try {
                        val cipherMessage = signalRatchet?.encryptFromAlice(plaintext)
                        val ciphertext = android.util.Base64.encodeToString(
                            cipherMessage?.serialize(),
                            android.util.Base64.DEFAULT
                        )

                        val backIntent = Intent("com.example.KEYBOARD_RECEIVE")
                        backIntent.putExtra("ciphertext", ciphertext)
                        context.sendBroadcast(backIntent)

                        Log.d(TAG, "Encrypted and sent to keyboard")
                    } catch (e: Exception) {
                        Log.e(TAG, "Encryption failed", e)
                    }
                }
            }

            "com.example.MIDDLEMAN_DECRYPT" -> {
                val ciphertext = intent.getStringExtra("ciphertext")
                if (ciphertext != null) {
                    try {
                        val ciphertextBytes = android.util.Base64.decode(
                            ciphertext,
                            android.util.Base64.DEFAULT
                        )

                        val cipherMessage: CiphertextMessage = if (ciphertextBytes[0].toInt() == 3) {
                            org.signal.libsignal.protocol.message.PreKeySignalMessage(ciphertextBytes)
                        } else {
                            org.signal.libsignal.protocol.message.SignalMessage(ciphertextBytes)
                        }

                        val plaintext = signalRatchet?.decryptOnBob(cipherMessage)

                        val backIntent = Intent("com.example.KEYBOARD_RECEIVE")
                        backIntent.putExtra("plaintext", plaintext)
                        context.sendBroadcast(backIntent)

                        Log.d(TAG, "Decrypted and sent to keyboard")
                    } catch (e: Exception) {
                        Log.e(TAG, "Decryption failed", e)
                    }
                }
            }
        }
    }
}