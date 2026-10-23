# Text Expander 2R for Android

A native Android application written in Kotlin that provides real-time text expansion across all apps via an Android `AccessibilityService` and a Room database.

## Architecture Overview

```
                      +-----------------------------+
                      |       Room Database         |
                      |   (Stores up to 1MB text)   |
                      +--------------+--------------+
                                     |
               Flow<List<Shortcut>>  |  (Asynchronous sync)
                                     v
+------------------------+   +------------------------------------+
|      MainActivity      |   |  TextExpanderAccessibilityService  |
| - Service Status Card  |   | - O(1) in-memory ConcurrentHashMap |
| - RecyclerView (Paged) |   | - Delimiter Detection              |
| - Add/Edit (1MB max)   |   | - ACTION_SET_SELECTION             |
+------------------------+   | - ClipboardManager + ACTION_PASTE  |
                             | - Fallback: ACTION_SET_TEXT        |
                             +------------------------------------+
                                     |
                                     v
                       +-----------------------------+
                       | Target App Input Field      |
                       | (WhatsApp, Chrome, Docs...) |
                       +-----------------------------+
```

### 1. Large Payload Architecture (Supporting up to 1MB)
- **Database Safeguards**: When querying the list of shortcuts for the UI, standard Room queries could crash with `CursorWindowAllocationException` if several 1MB entries are loaded at once (Android's `CursorWindow` hard limit is 2MB per query window). Text Expander uses a lightweight projection `ShortcutSummary` (`SUBSTR(expanded_text, 1, 200)` and byte sizes), loading full 1MB payloads only when editing or expanding.
- **Efficient Injection**: Character-by-character typing of a 1MB payload causes severe ANR (Application Not Responding) freezes and key event timeouts. Instead:
  1. The service calculates the exact bounds of the typed shortcut.
  2. It selects the shortcut using `AccessibilityNodeInfo.ACTION_SET_SELECTION`.
  3. It places the payload onto Android's `ClipboardManager`.
  4. It triggers `AccessibilityNodeInfo.ACTION_PASTE`, replacing the shortcut instantly.
  5. If the target application restricts clipboard paste or selection, a fallback mechanism applies `AccessibilityNodeInfo.ACTION_SET_TEXT` for payloads under the safe Binder transaction size limit (~500KB).

### 2. Zero-Latency Keystroke Matching
- Rather than executing SQLite queries on every keystroke in `onAccessibilityEvent` (which would freeze the main thread), the service maintains a thread-safe `ConcurrentHashMap` synchronized via a Room `Flow`. Lookups occur in $O(1)$ sub-millisecond time.
- Supported trigger delimiters include `[Space]`, `[Enter]`, `[Tab]`, `.`, `,`, `;`, `:`, `!`, `?`, `-`, and `)`.
- A reentrancy guard (`AtomicBoolean isCurrentlyExpanding`) prevents recursive expansion loops when the paste event fires subsequent `TYPE_VIEW_TEXT_CHANGED` events.

## Permissions & Configuration

- **Accessibility Service Configuration**: Declared in `res/xml/accessibility_service_config.xml`:
  - `canRetrieveWindowContent="true"`
  - `accessibilityFlags="flagDefault|flagReportViewIds|flagRequestFilterKeyEvents"`
  - `accessibilityEventTypes="typeViewTextChanged|typeViewFocused"`
  - `notificationTimeout="50"`

## Building the Project

Open the project directory in Android Studio (Giraffe / Hedgehog / Iguana / Jellyfish or newer) and build with Gradle:
```bash
./gradlew assembleDebug
```
