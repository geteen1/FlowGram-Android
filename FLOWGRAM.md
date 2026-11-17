# FlowGram

Unofficial Telegram client for Android, based on the official [Telegram source](https://github.com/DrKLO/Telegram) (v13.2.2). Not affiliated with Telegram.

## Features

- Ghost mode: no "typing", no read receipts, hidden online status, stories viewed secretly. Toggle from the 👻 button in the chat list or by shaking the phone
- Saved deleted messages with a 🗑 mark
- PIN lock for individual chats (hidden preview and notification text)
- Built-in proxy support for blocked regions
- No sponsored messages, optional hidden stories bar
- Up to 8 accounts
- User/chat ID and an approximate registration date in profiles
- Seconds in message time, forward without author, double tap to reply, silent send
- Message details, copy without formatting, save to Saved Messages, chat statistics, jump to the first message
- FlowShorts: a feed of the latest videos from your channels

All mod settings live in `TMessagesProj/src/main/java/org/telegram/messenger/FlowConfig.java`; the menu is `ui/FlowSettingsActivity.java`. Changes to stock files are marked `// FlowGram`.

## Building

Requirements: Android Studio, Android SDK 36, NDK 27.2.12479018, CMake 3.22.1, JDK 21.

```bash
git clone --recursive https://github.com/<you>/FlowGram.git
```

Put your own secrets in `~/.gradle/gradle.properties` (never commit them):

```properties
# https://my.telegram.org -> API development tools
FLOW_API_ID=123456
FLOW_API_HASH=0123456789abcdef0123456789abcdef

# release signing key
FLOW_STORE_FILE=C:/path/to/flowgram-release.keystore
FLOW_STORE_PASSWORD=...
FLOW_KEY_ALIAS=flowgram
FLOW_KEY_PASSWORD=...
```

Without them the build falls back to Telegram's public test API key (login will fail with `API_ID_PUBLISHED_FLOOD`) and the stock dummy signing key.

Release APK for arm64 phones:

```bash
./gradlew :TMessagesProj_App:assembleAfatRelease -PflowAbi=arm64-v8a
```

`-PflowAbi` limits native code to one ABI for faster builds; omit it to build all four.

## License

GPL v2, same as the original Telegram for Android. See `LICENSE`.
