package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LongSparseArray;

import org.telegram.messenger.time.FastDateFormat;

import java.security.MessageDigest;
import java.util.Locale;
import java.util.HashSet;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.utils.proxy.ProxySettings;

/**
 * FlowGram mod settings. Stored in their own SharedPreferences file
 * so they never collide with the stock Telegram config.
 */
public class FlowConfig {

    private static final String PREFS = "flowconfig";

    /**
     * Built-in proxy for blocked regions. Put your own link here:
     *  - web proxy over HTTPS (works behind Cloudflare): https://t.me/webproxy?server=...&secret=...
     *  - MTProto proxy (fake-TLS "ee" secret recommended): https://t.me/proxy?server=...&port=443&secret=ee...
     * Leave empty to disable the feature.
     */
    public static final String BUILTIN_PROXY_LINK = "";

    // Ads & UI
    public static boolean hideSponsored = true;
    public static boolean showIdInProfile = true;
    public static boolean hideStories = false;

    // Privacy & safety
    public static boolean hidePhone = false;
    public static boolean confirmCalls = true;

    // Exclusive
    public static boolean saveDeleted = true;

    // Background work (keep-alive + push connection, no Firebase needed)
    public static boolean backgroundWork = true;

    // Chats
    public static boolean showSeconds = false;
    public static boolean doubleTapReply = false;
    public static boolean silentSend = false;
    public static boolean shakeGhost = false;
    public static boolean hidePremium = false;
    public static boolean showRegDate = true;
    public static boolean showGhostButton = true;
    public static boolean markDeleted = true;
    public static boolean forwardNoAuthor = false;

    // Bypass blocks
    public static boolean builtinProxy = true;

    // Chat lock
    private static final HashSet<Long> lockedChats = new HashSet<>();
    private static String pinHash = "";
    private static final LongSparseArray<Long> unlockedUntil = new LongSparseArray<>();
    private static final long UNLOCK_WINDOW_MS = 60_000;

    // Ghost mode
    public static boolean ghostTyping = false;
    public static boolean ghostRead = false;
    public static boolean ghostOnline = false;
    public static boolean ghostStories = false;

    private static boolean loaded;

    public static synchronized void load() {
        if (loaded || ApplicationLoader.applicationContext == null) {
            return;
        }
        try { // FlowGram: a corrupted prefs file must never crash the app, defaults are used instead
        SharedPreferences p = prefs();
        hideSponsored = p.getBoolean("hideSponsored", hideSponsored);
        showIdInProfile = p.getBoolean("showIdInProfile", showIdInProfile);
        hideStories = p.getBoolean("hideStories", hideStories);
        hidePhone = p.getBoolean("hidePhone", hidePhone);
        confirmCalls = p.getBoolean("confirmCalls", confirmCalls);
        saveDeleted = p.getBoolean("saveDeleted", saveDeleted);
        builtinProxy = p.getBoolean("builtinProxy", builtinProxy);
        showSeconds = p.getBoolean("showSeconds", showSeconds);
        backgroundWork = p.getBoolean("backgroundWork", backgroundWork);
        doubleTapReply = p.getBoolean("doubleTapReply", doubleTapReply);
        silentSend = p.getBoolean("silentSend", silentSend);
        shakeGhost = p.getBoolean("shakeGhost", shakeGhost);
        hidePremium = p.getBoolean("hidePremium", hidePremium);
        showRegDate = p.getBoolean("showRegDate", showRegDate);
        showGhostButton = p.getBoolean("showGhostButton", showGhostButton);
        markDeleted = p.getBoolean("markDeleted", markDeleted);
        deletedKeys.clear();
        deletedKeys.addAll(p.getStringSet("deletedKeys", new HashSet<>()));
        forwardNoAuthor = p.getBoolean("forwardNoAuthor", forwardNoAuthor);
        pinHash = p.getString("chatPinHash", "");
        lockedChats.clear();
        for (String id : p.getString("lockedChats", "").split(",")) {
            if (!TextUtils.isEmpty(id)) {
                try {
                    lockedChats.add(Long.parseLong(id));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        ghostTyping = p.getBoolean("ghostTyping", ghostTyping);
        ghostRead = p.getBoolean("ghostRead", ghostRead);
        ghostOnline = p.getBoolean("ghostOnline", ghostOnline);
        ghostStories = p.getBoolean("ghostStories", ghostStories);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (FlowCrash.isSafeMode()) {
            applySafeMode();
        }
        loaded = true;
    }

    public static void toggleHideSponsored() {
        hideSponsored = !hideSponsored;
        put("hideSponsored", hideSponsored);
    }

    public static void toggleShowIdInProfile() {
        showIdInProfile = !showIdInProfile;
        put("showIdInProfile", showIdInProfile);
    }

    public static void toggleHideStories() {
        hideStories = !hideStories;
        put("hideStories", hideStories);
    }

    public static void toggleHidePhone() {
        hidePhone = !hidePhone;
        put("hidePhone", hidePhone);
    }

    public static void toggleConfirmCalls() {
        confirmCalls = !confirmCalls;
        put("confirmCalls", confirmCalls);
    }

    public static void toggleSaveDeleted() {
        saveDeleted = !saveDeleted;
        put("saveDeleted", saveDeleted);
    }

    // ---------- Deleted messages mark ----------

    // insertion-ordered, so on overflow the oldest marks go first
    private static final java.util.LinkedHashSet<String> deletedKeys = new java.util.LinkedHashSet<>();
    private static final int MAX_DELETED_KEYS = 5000;
    private static final Runnable saveDeletedKeys = () -> {
        HashSet<String> copy;
        synchronized (deletedKeys) {
            copy = new HashSet<>(deletedKeys);
        }
        prefs().edit().putStringSet("deletedKeys", copy).apply();
    };

    private static String deletedKey(int account, long channelDialogId, int messageId) {
        return account + ":" + channelDialogId + ":" + messageId;
    }

    /** Remember messages that someone deleted while "save deleted" kept them. channelDialogId is 0 for private chats and small groups. */
    public static void rememberDeleted(int account, long channelDialogId, java.util.List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        synchronized (deletedKeys) {
            for (Integer id : ids) {
                deletedKeys.add(deletedKey(account, channelDialogId, id));
            }
            java.util.Iterator<String> it = deletedKeys.iterator();
            while (deletedKeys.size() > MAX_DELETED_KEYS && it.hasNext()) {
                it.next();
                it.remove(); // drop the oldest marks only
            }
        }
        // batch disk writes: many deletions in a row -> one write 2 s later
        AndroidUtilities.cancelRunOnUIThread(saveDeletedKeys);
        AndroidUtilities.runOnUIThread(saveDeletedKeys, 2000);
    }

    public static boolean isDeleted(MessageObject message) {
        if (!markDeleted || message == null || message.messageOwner == null || message.messageOwner.peer_id == null) {
            return false;
        }
        long channelId = message.messageOwner.peer_id.channel_id;
        String key = deletedKey(message.currentAccount, channelId != 0 ? -channelId : 0, message.getId());
        synchronized (deletedKeys) {
            return deletedKeys.contains(key);
        }
    }

    public static void toggleMarkDeleted() {
        markDeleted = !markDeleted;
        put("markDeleted", markDeleted);
    }

    public static void toggleShowGhostButton() {
        showGhostButton = !showGhostButton;
        put("showGhostButton", showGhostButton);
    }

    // ---------- Safe mode ----------

    /**
     * After repeated crashes at startup: switch off the features that touch message rendering and update handling.
     * Privacy features (ghost mode, chat PIN lock, hidden phone, proxy, ads) stay as they are, so nothing hidden gets exposed.
     * In-memory only: the saved settings come back when the user leaves safe mode.
     */
    private static void applySafeMode() {
        saveDeleted = false;
        markDeleted = false;
        showSeconds = false;
        showIdInProfile = false;
        showRegDate = false;
        hideStories = false;
        doubleTapReply = false;
        shakeGhost = false;
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    android.widget.Toast.makeText(ApplicationLoader.applicationContext,
                            "FlowGram: безопасный режим после нескольких сбоев. Подробнее в меню FlowGram.", android.widget.Toast.LENGTH_LONG).show();
                } catch (Throwable ignore) {
                }
            }, 3000);
        } catch (Throwable ignore) {
        }
    }

    /** Leave safe mode and reload the real settings. */
    public static synchronized void leaveSafeMode() {
        FlowCrash.leaveSafeMode();
        loaded = false;
        load();
    }

    // ---------- More toggles ----------

    public static void toggleDoubleTapReply() {
        doubleTapReply = !doubleTapReply;
        put("doubleTapReply", doubleTapReply);
    }

    public static void toggleSilentSend() {
        silentSend = !silentSend;
        put("silentSend", silentSend);
    }

    public static void toggleShakeGhost() {
        shakeGhost = !shakeGhost;
        put("shakeGhost", shakeGhost);
    }

    public static void toggleHidePremium() {
        hidePremium = !hidePremium;
        put("hidePremium", hidePremium);
    }

    public static void toggleShowRegDate() {
        showRegDate = !showRegDate;
        put("showRegDate", showRegDate);
    }

    // ---------- Approximate account creation date by user ID ----------
    // Rough public data points (user id -> year, month). Telegram ids grow over time, so we interpolate.
    private static final long[][] REG_POINTS = {
            {0L, 2013, 8}, {2768409L, 2013, 11}, {7679610L, 2014, 2}, {11538514L, 2014, 4},
            {23646077L, 2014, 8}, {44634663L, 2014, 11}, {54845238L, 2015, 1}, {63263518L, 2015, 2},
            {101260938L, 2015, 4}, {111220210L, 2015, 6}, {124872445L, 2015, 9}, {150000000L, 2016, 1},
            {200000000L, 2016, 6}, {300000000L, 2017, 1}, {400000000L, 2017, 7}, {500000000L, 2018, 1},
            {600000000L, 2018, 5}, {700000000L, 2018, 11}, {800000000L, 2019, 3}, {900000000L, 2019, 7},
            {1000000000L, 2019, 10}, {1100000000L, 2020, 1}, {1200000000L, 2020, 3}, {1300000000L, 2020, 5},
            {1400000000L, 2020, 8}, {1500000000L, 2020, 12}, {1600000000L, 2021, 2}, {1700000000L, 2021, 4},
            {1800000000L, 2021, 7}, {1900000000L, 2021, 10}, {2000000000L, 2021, 12}, {2147483647L, 2022, 2},
            {5000000000L, 2022, 3}, {5500000000L, 2022, 9}, {6000000000L, 2023, 3}, {6500000000L, 2023, 9},
            {7000000000L, 2024, 2}, {7500000000L, 2024, 8}, {8000000000L, 2025, 2}, {8500000000L, 2025, 8},
    };
    private static final String[] MONTHS = {"январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"};

    /** e.g. "≈ март 2019". Only an estimate: ids are not strictly ordered by date. */
    public static String estimateRegistration(long userId) {
        if (userId <= 0) {
            return "";
        }
        long[] last = REG_POINTS[REG_POINTS.length - 1];
        if (userId >= last[0]) {
            return "≈ " + last[1] + "+";
        }
        for (int i = 1; i < REG_POINTS.length; i++) {
            long[] a = REG_POINTS[i - 1], b = REG_POINTS[i];
            if (userId < b[0]) {
                long ma = a[1] * 12 + (a[2] - 1), mb = b[1] * 12 + (b[2] - 1);
                double t = (double) (userId - a[0]) / Math.max(1, b[0] - a[0]);
                long m = Math.round(ma + (mb - ma) * t);
                return "≈ " + MONTHS[(int) (m % 12)] + " " + (m / 12);
            }
        }
        return "";
    }

    // ---------- Chats ----------

    private static FastDateFormat secondsFormatter;
    private static boolean secondsFormatter24h;

    /** Time under messages: stock "14:32", or "14:32:07" with seconds enabled. */
    public static FastDateFormat messageTimeFormatter() {
        if (!showSeconds) {
            return LocaleController.getInstance().getFormatterDay();
        }
        boolean is24h = LocaleController.is24HourFormat;
        if (secondsFormatter == null || secondsFormatter24h != is24h) {
            secondsFormatter = FastDateFormat.getInstance(is24h ? "HH:mm:ss" : "h:mm:ss a", Locale.US);
            secondsFormatter24h = is24h;
        }
        return secondsFormatter;
    }

    public static void toggleShowSeconds() {
        showSeconds = !showSeconds;
        put("showSeconds", showSeconds);
    }

    public static void toggleForwardNoAuthor() {
        forwardNoAuthor = !forwardNoAuthor;
        put("forwardNoAuthor", forwardNoAuthor);
    }

    // ---------- Chat lock ----------

    public static boolean isChatLocked(long dialogId) {
        return dialogId != 0 && lockedChats.contains(dialogId);
    }

    public static int lockedChatsCount() {
        return lockedChats.size();
    }

    public static void setChatLocked(long dialogId, boolean locked) {
        if (locked) {
            lockedChats.add(dialogId);
        } else {
            lockedChats.remove(dialogId);
        }
        saveLockedChats();
    }

    public static void unlockAllChats() {
        lockedChats.clear();
        saveLockedChats();
    }

    private static void saveLockedChats() {
        StringBuilder sb = new StringBuilder();
        for (Long id : lockedChats) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        prefs().edit().putString("lockedChats", sb.toString()).apply();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
    }

    public static boolean hasPin() {
        return !TextUtils.isEmpty(pinHash);
    }

    public static void setPin(String pin) {
        pinHash = sha256(pin);
        prefs().edit().putString("chatPinHash", pinHash).apply();
    }

    public static boolean checkPin(String pin) {
        return hasPin() && !TextUtils.isEmpty(pin) && pinHash.equals(sha256(pin));
    }

    /** After a correct PIN the chat stays open for a minute, so switching apps doesn't ask again immediately. */
    public static void markTemporarilyUnlocked(long dialogId) {
        unlockedUntil.put(dialogId, SystemClock.elapsedRealtime() + UNLOCK_WINDOW_MS);
    }

    public static boolean isTemporarilyUnlocked(long dialogId) {
        Long until = unlockedUntil.get(dialogId);
        return until != null && SystemClock.elapsedRealtime() < until;
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(("flowgram:" + value).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return "";
        }
    }

    public static boolean hasBuiltinProxy() {
        return builtinProxySettings() != null;
    }

    private static ProxySettings builtinProxySettings() {
        if (TextUtils.isEmpty(BUILTIN_PROXY_LINK)) {
            return null;
        }
        try {
            ProxySettings settings = ProxySettings.fromUri(Uri.parse(BUILTIN_PROXY_LINK));
            return settings != null && settings.isValid() ? settings : null;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /**
     * Called at app start before the network layer exists: mirror our "background work" switch into Telegram's own
     * keep-alive service and background push connection settings, which are read when the connection starts.
     */
    public static void applyBackgroundWorkOnStart() {
        load();
        try {
            MessagesController.getGlobalNotificationsSettings().edit()
                    .putBoolean("pushService", backgroundWork)
                    .putBoolean("pushConnection", backgroundWork)
                    .commit();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void toggleBackgroundWork() {
        backgroundWork = !backgroundWork;
        put("backgroundWork", backgroundWork);
        applyBackgroundWorkOnStart();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                ConnectionsManager.getInstance(a).setPushConnectionEnabled(backgroundWork);
            }
        }
        ApplicationLoader.startPushService();
    }

    public static boolean isIgnoringBatteryOptimizations() {
        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return true;
            android.os.PowerManager pm = (android.os.PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(ApplicationLoader.applicationContext.getPackageName());
        } catch (Throwable e) {
            return true;
        }
    }

    /** Opens the system dialog "Allow FlowGram to always run in background?". */
    public static void requestIgnoreBatteryOptimizations(android.app.Activity activity) {
        if (activity == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return;
        try {
            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(android.net.Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Throwable e) {
            try {
                activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable ignore) {
            }
        }
    }

    /** Called once at app start, before the network layer is created. Doesn't touch a proxy the user set up himself. */
    public static void applyBuiltinProxyOnStart() {
        load();
        ProxySettings settings = builtinProxySettings();
        if (!builtinProxy || settings == null) {
            return;
        }
        SharedPreferences main = MessagesController.getGlobalMainSettings();
        if (main.getBoolean("proxy_enabled", false) && !settings.equals(ProxySettings.fromSharedPreferences(main))) {
            return; // the user already uses another proxy
        }
        writeProxy(settings, true);
    }

    public static void toggleBuiltinProxy() {
        builtinProxy = !builtinProxy;
        put("builtinProxy", builtinProxy);
        ProxySettings settings = builtinProxySettings();
        if (settings == null) {
            return;
        }
        SharedPreferences main = MessagesController.getGlobalMainSettings();
        boolean usingBuiltin = settings.equals(ProxySettings.fromSharedPreferences(main));
        if (builtinProxy) {
            writeProxy(settings, true);
            ConnectionsManager.setProxySettings(true, settings);
        } else if (usingBuiltin) {
            main.edit().putBoolean("proxy_enabled", false).apply();
            ConnectionsManager.setProxySettings(false, settings);
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }

    private static void writeProxy(ProxySettings settings, boolean enabled) {
        SharedConfig.ProxyInfo info = SharedConfig.addProxy(new SharedConfig.ProxyInfo(settings));
        SharedConfig.currentProxy = info;
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        settings.toSharedPreferences(editor);
        editor.putBoolean("proxy_enabled", enabled);
        editor.commit();
    }

    public static void toggleGhostTyping() {
        ghostTyping = !ghostTyping;
        put("ghostTyping", ghostTyping);
    }

    public static void toggleGhostRead() {
        ghostRead = !ghostRead;
        put("ghostRead", ghostRead);
    }

    public static void toggleGhostOnline() {
        ghostOnline = !ghostOnline;
        put("ghostOnline", ghostOnline);
        if (ghostOnline) {
            sendOfflineNow();
        }
    }

    public static void toggleGhostStories() {
        ghostStories = !ghostStories;
        put("ghostStories", ghostStories);
    }

    public static boolean isGhostModeFull() {
        return ghostTyping && ghostRead && ghostOnline && ghostStories;
    }

    /** Turns all ghost options on, or all off if they are already all on. */
    public static void toggleGhostModeFull() {
        boolean value = !isGhostModeFull();
        ghostTyping = ghostRead = ghostOnline = ghostStories = value;
        prefs().edit()
                .putBoolean("ghostTyping", value)
                .putBoolean("ghostRead", value)
                .putBoolean("ghostOnline", value)
                .putBoolean("ghostStories", value)
                .apply();
        if (value) {
            sendOfflineNow();
        }
    }

    /** Ghost online just turned on: go "offline" right away on every logged-in account instead of waiting for the timer. */
    private static void sendOfflineNow() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            try {
                if (!UserConfig.isValidAccount(a)) {
                    continue;
                }
                org.telegram.tgnet.tl.TL_account.updateStatus req = new org.telegram.tgnet.tl.TL_account.updateStatus();
                req.offline = true;
                ConnectionsManager.getInstance(a).sendRequest(req, null);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private static void put(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // Must stay at the very end: static fields above are initialized top-down before this runs.
    static {
        load();
    }
}
