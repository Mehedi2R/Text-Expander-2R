# Privacy Policy for Text Expander 2R

**Last updated:** October 1, 2026

**Text Expander 2R** ("we", "our", or "the App") is developed with a strict privacy-first architecture. This Privacy Policy describes how your information is handled when using Text Expander 2R on Android devices.

---

## 1. 100% Offline Architecture & Zero Data Collection
Text Expander 2R is designed to function **entirely offline**. 
- The App does **NOT** request or possess the `android.permission.INTERNET` permission.
- The App cannot connect to the internet, cloud servers, or third-party analytics services.
- We do **NOT** collect, transmit, sell, or share any personal information, keystrokes, clipboard records, or device identifiers.
- All shortcuts, templates, categories, and settings created by the user are stored strictly inside the device's local SQLite Room database and are never accessible outside the device.

---

## 2. Accessibility Service API Disclosure (Google Play Compliance)
Text Expander 2R requires the Android Accessibility Service API (`android.permission.BIND_ACCESSIBILITY_SERVICE`) to perform its core function:

### Why We Use the Accessibility Service:
- **Core Functionality:** The Accessibility Service is used solely to detect when a user types a user-configured keyword abbreviation (e.g. `@@email` or `addr`) across apps and input fields, and to replace that abbreviation with the user's predefined expanded text snippet.
- **Floating Overlay:** It provides a lightweight floating suggestion card above the soft keyboard so the user can preview and tap to expand their snippet.

### What the Accessibility Service Does NOT Do:
- It does **NOT** record, monitor, or log your keystrokes.
- It does **NOT** monitor or intercept sensitive input fields such as password boxes, PINs, or financial inputs (`isPassword == true` fields are automatically bypassed).
- It does **NOT** send any typed text or information to any external server or third party.
- It does **NOT** alter device settings or interfere with other applications without user action.

---

## 3. Clipboard Data Handling
- Text Expander 2R includes an optional local clipboard history feature to help users convert frequently copied text into reusable shortcuts.
- Clipboard data captured by the user is stored solely on the local device.
- Users can disable clipboard recording at any time in Settings (`Settings > Clipboard > Enable clipboard`) or delete any item instantly.

---

## 4. Local Backup and Storage
- When you create a backup (`Settings > Backup Data > Create backup`), the backup file is saved directly to your local device storage using the Android Storage Access Framework (SAF).
- Backups contain only your custom text shortcuts and folder structures. Clipboard history is **strictly excluded** from backup files.
- You have full ownership and control over your backup files.

---

## 5. Security of Your Information
Because Text Expander 2R operates 100% offline without network capabilities, your data remains within the Android sandbox of your device and is protected by your device's operating system security.

---

## 6. Children's Privacy
Text Expander 2R does not collect any personal data from anyone, including children under the age of 13.

---

## 7. Contact Us
If you have any questions or feedback regarding this Privacy Policy, please contact the developer via:
- **Developer:** @Mehedi2R
- **Email:** support@textexpander2r.app
