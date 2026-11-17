package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * FlowGram plugins, v1: declarative JSON only. A plugin can NOT run code — it only describes text rules,
 * so a malicious plugin can't read chats or steal the session. Format (all fields except id/name optional):
 *
 * {
 *   "id": "kaomoji", "name": "Каомодзи", "version": "1.0", "author": "...", "description": "...",
 *   "commands": { "/shrug": "¯\\_(ツ)_/¯" },                       // whole message equals the command -> replaced
 *   "replace":  [ { "find": "спс", "with": "спасибо", "wholeWord": true, "ignoreCase": true } ],
 *   "suffix":   "\n— отправлено из FlowGram"                          // appended to every outgoing text
 * }
 *
 * Rules are plain text (no regex), so a rule can't hang the app. Messages with formatting (entities) are left
 * untouched, because changing the text would break their offsets. Bots never get command substitutions.
 */
public class FlowPlugins {

    public static final int API_VERSION = 1;
    private static final String DIR = "flow_plugins";
    private static final String PREFS = "flowconfig";
    private static final int MAX_FILE = 64 * 1024;
    private static final int MAX_COMMANDS = 300;
    private static final int MAX_RULES = 200;
    private static final int MAX_TEXT = 4000;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]{2,40}");

    public static class Plugin {
        public String id, name, version, author, description;
        public final LinkedHashMap<String, String> commands = new LinkedHashMap<>();
        public final ArrayList<Rule> rules = new ArrayList<>();
        public String suffix;
        public boolean builtIn;

        public String summary() {
            ArrayList<String> parts = new ArrayList<>();
            if (!commands.isEmpty()) parts.add("команд: " + commands.size());
            if (!rules.isEmpty()) parts.add("замен: " + rules.size());
            if (!TextUtils.isEmpty(suffix)) parts.add("подпись");
            String meta = (TextUtils.isEmpty(version) ? "" : "v" + version) + (TextUtils.isEmpty(author) ? "" : " · " + author);
            return (TextUtils.isEmpty(description) ? "" : description + "\n") + TextUtils.join(", ", parts) + (meta.isEmpty() ? "" : "  (" + meta.trim() + ")");
        }
    }

    public static class Rule {
        String find, with;
        boolean wholeWord, ignoreCase;
    }

    private static final ArrayList<Plugin> plugins = new ArrayList<>();
    private static final HashSet<String> enabled = new HashSet<>();
    private static boolean loaded;

    // ---------------------------------------------------------------- loading

    public static synchronized ArrayList<Plugin> getPlugins() {
        ensureLoaded();
        return new ArrayList<>(plugins);
    }

    public static synchronized boolean isEnabled(String id) {
        ensureLoaded();
        return enabled.contains(id);
    }

    public static synchronized void setEnabled(String id, boolean on) {
        ensureLoaded();
        if (on) enabled.add(id); else enabled.remove(id);
        prefs().edit().putStringSet("plugins_enabled", new HashSet<>(enabled)).apply();
    }

    public static synchronized int enabledCount() {
        ensureLoaded();
        int n = 0;
        for (Plugin p : plugins) if (enabled.contains(p.id)) n++;
        return n;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File dir() {
        File d = new File(ApplicationLoader.applicationContext.getFilesDir(), DIR);
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static void ensureLoaded() {
        if (loaded || ApplicationLoader.applicationContext == null) {
            return;
        }
        loaded = true;
        plugins.clear();
        try {
            enabled.clear();
            enabled.addAll(prefs().getStringSet("plugins_enabled", new HashSet<>()));
        } catch (Throwable e) {
            FileLog.e(e);
        }
        for (String json : BUILT_IN) {
            try {
                Plugin p = parse(json);
                p.builtIn = true;
                plugins.add(p);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        File[] files = dir().listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.getName().endsWith(".json") || f.length() > MAX_FILE) continue;
                try {
                    Plugin p = parse(readFile(f));
                    if (find(p.id) == null) plugins.add(p);
                } catch (Throwable e) {
                    FileLog.e(e); // a broken plugin file is skipped, never crashes the app
                }
            }
        }
    }

    private static Plugin find(String id) {
        for (Plugin p : plugins) if (p.id.equals(id)) return p;
        return null;
    }

    // ---------------------------------------------------------------- install / remove

    /** Validates and installs a plugin from JSON text. Returns the plugin, or throws with a user-readable message. */
    public static synchronized Plugin install(String json) throws Exception {
        ensureLoaded();
        if (json == null || json.length() > MAX_FILE) {
            throw new Exception("Файл плагина слишком большой (максимум 64 КБ)");
        }
        Plugin p = parse(json);
        Plugin existing = find(p.id);
        if (existing != null && existing.builtIn) {
            throw new Exception("Плагин с id «" + p.id + "» уже встроен в FlowGram");
        }
        try (FileOutputStream out = new FileOutputStream(new File(dir(), p.id + ".json"), false)) {
            out.write(json.getBytes("UTF-8"));
        }
        if (existing != null) plugins.remove(existing);
        plugins.add(p);
        setEnabled(p.id, true);
        return p;
    }

    public static synchronized void remove(String id) {
        ensureLoaded();
        Plugin p = find(id);
        if (p == null || p.builtIn) return;
        //noinspection ResultOfMethodCallIgnored
        new File(dir(), id + ".json").delete();
        plugins.remove(p);
        setEnabled(id, false);
    }

    private static Plugin parse(String json) throws Exception {
        JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (Exception e) {
            throw new Exception("Это не JSON-плагин: " + e.getMessage());
        }
        int api = o.optInt("api", 1);
        if (api > API_VERSION) {
            throw new Exception("Плагину нужна более новая версия FlowGram (api " + api + ")");
        }
        Plugin p = new Plugin();
        p.id = o.optString("id", "").trim().toLowerCase(Locale.US);
        p.name = clip(o.optString("name", "").trim(), 60);
        if (!ID_PATTERN.matcher(p.id).matches()) {
            throw new Exception("Неверный id плагина: латиница, цифры, _ . - (2–40 символов)");
        }
        if (p.name.isEmpty()) {
            throw new Exception("У плагина нет названия (name)");
        }
        p.version = clip(o.optString("version", ""), 20);
        p.author = clip(o.optString("author", ""), 60);
        p.description = clip(o.optString("description", ""), 300);
        p.suffix = clip(o.optString("suffix", ""), 200);

        JSONObject cmds = o.optJSONObject("commands");
        if (cmds != null) {
            Iterator<String> keys = cmds.keys();
            while (keys.hasNext() && p.commands.size() < MAX_COMMANDS) {
                String k = keys.next().trim();
                String v = cmds.optString(k, "");
                if (k.length() >= 2 && k.length() <= 64 && k.startsWith("/") && !v.isEmpty()) {
                    p.commands.put(k.toLowerCase(Locale.ROOT), clip(v, MAX_TEXT));
                }
            }
        }
        JSONArray rules = o.optJSONArray("replace");
        if (rules != null) {
            for (int i = 0; i < rules.length() && p.rules.size() < MAX_RULES; i++) {
                JSONObject r = rules.optJSONObject(i);
                if (r == null) continue;
                Rule rule = new Rule();
                rule.find = clip(r.optString("find", ""), 200);
                rule.with = clip(r.optString("with", ""), 1000);
                rule.wholeWord = r.optBoolean("wholeWord", true);
                rule.ignoreCase = r.optBoolean("ignoreCase", true);
                if (!rule.find.isEmpty()) p.rules.add(rule);
            }
        }
        if (p.commands.isEmpty() && p.rules.isEmpty() && TextUtils.isEmpty(p.suffix)) {
            throw new Exception("Плагин пустой: нет commands, replace или suffix");
        }
        return p;
    }

    private static String clip(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }

    private static String readFile(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
            return new String(data, 0, read, "UTF-8");
        }
    }

    // ---------------------------------------------------------------- applying

    /** Called right before a text message is sent. Never throws: on any problem the original text is sent. */
    public static String processOutgoing(int account, long peer, String text, ArrayList<TLRPC.MessageEntity> entities) {
        if (TextUtils.isEmpty(text) || (entities != null && !entities.isEmpty())) {
            return text;
        }
        try {
            ArrayList<Plugin> active;
            synchronized (FlowPlugins.class) {
                ensureLoaded();
                if (enabled.isEmpty()) return text;
                active = new ArrayList<>();
                for (Plugin p : plugins) if (enabled.contains(p.id)) active.add(p);
            }
            if (active.isEmpty()) return text;

            boolean toBot = false;
            if (peer > 0) {
                TLRPC.User user = MessagesController.getInstance(account).getUser(peer);
                toBot = user != null && user.bot;
            }
            String result = text;
            if (!toBot && result.startsWith("/")) {
                String key = result.trim().toLowerCase(Locale.ROOT);
                for (Plugin p : active) {
                    String v = p.commands.get(key);
                    if (v != null) {
                        result = v;
                        break;
                    }
                }
            }
            for (Plugin p : active) {
                for (Rule r : p.rules) {
                    result = replace(result, r);
                }
            }
            for (Plugin p : active) {
                if (!TextUtils.isEmpty(p.suffix)) result = result + p.suffix;
            }
            return result.length() > MAX_TEXT ? text : result;
        } catch (Throwable e) {
            FileLog.e(e);
            return text;
        }
    }

    private static String replace(String text, Rule r) {
        String hay = r.ignoreCase ? text.toLowerCase(Locale.ROOT) : text;
        String needle = r.ignoreCase ? r.find.toLowerCase(Locale.ROOT) : r.find;
        if (needle.isEmpty() || hay.length() != text.length()) return text; // lowercasing changed length: skip safely
        StringBuilder sb = null;
        int from = 0, idx;
        while ((idx = hay.indexOf(needle, from)) >= 0) {
            int end = idx + needle.length();
            boolean ok = !r.wholeWord || (isBoundary(text, idx - 1) && isBoundary(text, end));
            if (ok) {
                if (sb == null) sb = new StringBuilder(text.length() + 16);
                sb.append(text, from, idx).append(r.with);
                from = end;
            } else {
                if (sb == null) sb = new StringBuilder(text.length() + 16);
                sb.append(text, from, idx + 1);
                from = idx + 1;
            }
        }
        if (sb == null) return text;
        sb.append(text, from, text.length());
        return sb.toString();
    }

    private static boolean isBoundary(String s, int i) {
        return i < 0 || i >= s.length() || !Character.isLetterOrDigit(s.charAt(i));
    }

    // ---------------------------------------------------------------- built-in examples

    private static final String[] BUILT_IN = {
            "{\"id\":\"flow.kaomoji\",\"name\":\"Каомодзи\",\"version\":\"1.0\",\"author\":\"FlowGram\","
                    + "\"description\":\"Команды-смайлики: /shrug, /lenny, /flip, /unflip, /bear, /love\","
                    + "\"commands\":{\"/shrug\":\"¯\\\\_(ツ)_/¯\",\"/lenny\":\"( ͡° ͜ʖ ͡°)\",\"/flip\":\"(╯°□°)╯︵ ┻━┻\","
                    + "\"/unflip\":\"┬─┬ノ( º _ ºノ)\",\"/bear\":\"ʕ•ᴥ•ʔ\",\"/love\":\"(｡♥‿♥｡)\"}}",
            "{\"id\":\"flow.shortcuts\",\"name\":\"Расшифровка сокращений\",\"version\":\"1.0\",\"author\":\"FlowGram\","
                    + "\"description\":\"спс → спасибо, пж → пожалуйста, хз → не знаю, мб → может быть\","
                    + "\"replace\":[{\"find\":\"спс\",\"with\":\"спасибо\"},{\"find\":\"пж\",\"with\":\"пожалуйста\"},"
                    + "{\"find\":\"пжлст\",\"with\":\"пожалуйста\"},{\"find\":\"хз\",\"with\":\"не знаю\"},"
                    + "{\"find\":\"мб\",\"with\":\"может быть\"},{\"find\":\"щас\",\"with\":\"сейчас\"}]}",
    };

    /** Sample plugin for the "how to make a plugin" screen. */
    public static final String SAMPLE =
            "{\n  \"id\": \"my.plugin\",\n  \"name\": \"Мой плагин\",\n  \"version\": \"1.0\",\n  \"author\": \"@username\",\n"
                    + "  \"description\": \"Что делает плагин\",\n  \"commands\": {\n    \"/hi\": \"Привет! 👋\"\n  },\n"
                    + "  \"replace\": [\n    { \"find\": \"кст\", \"with\": \"кстати\", \"wholeWord\": true }\n  ],\n"
                    + "  \"suffix\": \"\"\n}";
}
