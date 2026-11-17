# FlowGram plugins

Plugins v1 are plain JSON files with text rules. They **can't run code**, read chats or access the account, so installing a plugin from someone else is safe.

Install: FlowGram → Settings → FlowGram → 🧩 Plugins → "From file (.json)" or "Paste from clipboard".

## Format

```json
{
  "api": 1,
  "id": "my.plugin",
  "name": "My plugin",
  "version": "1.0",
  "author": "@username",
  "description": "What it does",
  "commands": {
    "/hi": "Привет! 👋",
    "/shrug": "¯\\_(ツ)_/¯"
  },
  "replace": [
    { "find": "кст", "with": "кстати", "wholeWord": true, "ignoreCase": true }
  ],
  "suffix": "\n— sent from FlowGram"
}
```

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | Unique id: `a-z 0-9 _ . -`, 2–40 chars. Installing a plugin with the same id updates it. |
| `name` | yes | Shown in the list (up to 60 chars). |
| `api` | no | Plugin API version, currently `1`. Newer plugins are rejected by older FlowGram. |
| `commands` | no | If the whole outgoing message equals the key (case-insensitive), it's replaced with the value. Keys start with `/`. Not applied in chats with bots, so bot commands keep working. |
| `replace` | no | Plain-text replacements in outgoing messages. `wholeWord` (default `true`) only matches separate words; `ignoreCase` default `true`. No regex. |
| `suffix` | no | Text appended to every outgoing text message. |

At least one of `commands`, `replace`, `suffix` must be present.

## Limits

- File up to 64 KB, up to 300 commands and 200 replace rules per plugin.
- Messages with formatting (bold, links, mentions…) are sent unchanged, because editing the text would break the formatting.
- If anything goes wrong, the original message is sent; a broken plugin file is skipped and never crashes the app.

## Roadmap

Plugins with code (scripting API) are planned. They'll be opt-in, with a clear permission screen, because code plugins can access your chats.
