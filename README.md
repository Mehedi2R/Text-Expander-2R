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

## Features

- **High-Performance Expansion:** Expands short keywords into long text snippets instantly.
- **Large Text Support:** Supports snippets up to 1 MB using chunked database transactions.
- **Background Persistence:** Kept alive with dual mechanisms (`TextExpanderForegroundService` and `KeepAliveWorker`).
- **Delimiter Triggering:** Configurable trigger delimiters (Space, Enter, Punctuation).
- **Clipboard Fallback:** Ensures 100% paste reliability across stubborn rich-text editors.
- **Human Typing Effect:** Optional toggle to simulate human keystroke delays (100ms) when pasting.
- **Dark & AMOLED Themes:** Full Material 3 dark mode design.

## Accessibility Service Configuration

- **Flags used:**
  - `accessibilityFlags="flagDefault|flagReportViewIds|flagRequestFilterKeyEvents"`
  - `accessibilityEventTypes="typeViewTextChanged|typeViewFocused"`
  - `notificationTimeout="50"`

## Building the Project

Open the project directory in Android Studio (Giraffe / Hedgehog / Iguana / Jellyfish or newer) and build with Gradle:
```bash
./gradlew assembleDebug
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
