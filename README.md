# CryptoMiddleman

End-to-end encrypted messaging using the Signal Protocol's Double Ratchet algorithm. Exchange encryption keys via QR codes and communicate securely.

## Features

- **Signal Protocol encryption** - Industry-standard E2EE with forward secrecy
- **QR code key exchange** - No server required for session establishment
- **Multi-contact support** - Manage encrypted sessions with multiple contacts
- **Auto-decrypt** - Automatically identifies sender when decrypting messages

## How It Works

### 1. Key Exchange (First Time Setup)

**Device A (Alice):**
1. Open CryptoMiddleman
2. Tap "Show My QR Code"
3. Display all 6 QR codes to Bob (swipe through them)

**Device B (Bob):**
1. Open CryptoMiddleman  
2. Tap "Scan QR Code"
3. Scan all 6 of Alice's QR codes
4. Enter contact name (e.g., "Alice")
5. Bob shows his QR codes to Alice

**Device A (Alice):**
1. Scan all 6 of Bob's QR codes
2. Enter contact name (e.g., "Bob")
3. ✓ Session established!

### 2. Sending Encrypted Messages

1. Select contact from dropdown
2. Type your message in the text field
3. Tap "Encrypt"
4. Encrypted text appears and is copied to clipboard
5. Paste into any messaging app and send

### 3. Receiving Encrypted Messages

1. Copy the encrypted message
2. Paste into CryptoMiddleman text field
3. Tap "Decrypt"
4. Message shows plaintext and identifies sender

## Important Notes

- **One-time decryption**: Due to forward secrecy, each message can only be decrypted once
- **Session persistence**: Sessions are saved locally - no re-scanning needed unless session is deleted
- **Case-insensitive contacts**: Contact names are treated as case-insensitive

## Keyboard Integration (Planned)

This standalone app was built with Android Studio as a foundation for keyboard integration. A forked version with HeliBoard integration allows:

- Encrypt/decrypt directly from keyboard without app switching
- Quick contact selection via keyboard button
- Clipboard-based decryption with popup display
- Seamless workflow: Copy encrypted message → Press decrypt button → View plaintext

The integrated version reduces friction by eliminating the need to paste into CryptoMiddleman manually.

## Technical Details

- **Protocol**: Signal Protocol (libsignal-protocol-java)
- **Key Storage**: Android EncryptedSharedPreferences
- **Session Files**: Local file system with secure naming

## Building
```bash
git clone https://github.com/yourusername/cryptomiddleman.git
cd cryptomiddleman
./gradlew build