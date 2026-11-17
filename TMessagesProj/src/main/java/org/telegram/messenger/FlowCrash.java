package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * FlowGram reliability helpers, no Firebase needed:
 *  - local crash log (Java exceptions) that the user can share from the mod menu;
 *  - safe mode: if the app dies within 15 s of starting 3 times in a row (Java or native crash),
 *    risky mod features are switched off until the user leaves safe mode in the menu.
 */
public class FlowCrash {

    private static final String PREFS = "flowcrash";
    private static final String LOG_FILE = "flowgram_crashes.txt";
    private static final int SAFE_MODE_AFTER = 3;
    private static final long STABLE_AFTER_MS = 15_000;
    private static final long MAX_LOG_BYTES = 128 * 1024;
    private static final String ENTRY_MARK = "=== FlowGram crash ";

    private static boolean started;
    private static volatile boolean safeMode;
    private static Context appContext;

    /** Call once, as early as possible in Application.onCreate. */
    public static synchronized void onAppStart(Context context) {
        if (started || context == null) {
            return;
        }
        started = true;
        appContext = context.getApplicationContext();
        try {
            final SharedPreferences p = prefs();
            int unstable = p.getInt("unstableStarts", 0);
            boolean wasSafe = p.getBoolean("safeMode", false);
            safeMode = wasSafe || unstable >= SAFE_MODE_AFTER;
            p.edit().putInt("unstableStarts", unstable + 1).putBoolean("safeMode", safeMode).commit();
            // survived long enough -> this start was stable
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    p.edit().putInt("unstableStarts", 0).apply();
                } catch (Throwable ignore) {
                }
            }, STABLE_AFTER_MS);
        } catch (Throwable ignore) {
        }
        installHandler();
    }

    public static boolean isSafeMode() {
        return safeMode;
    }

    public static void leaveSafeMode() {
        safeMode = false;
        try {
            prefs().edit().putBoolean("safeMode", false).putInt("unstableStarts", 0).apply();
        } catch (Throwable ignore) {
        }
    }

    private static SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void installHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                writeCrash(thread, error);
            } catch (Throwable ignore) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    private static File logFile() {
        return new File(appContext.getFilesDir(), LOG_FILE);
    }

    private static synchronized void writeCrash(Thread thread, Throwable error) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println(ENTRY_MARK + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + " ===");
        pw.println("FlowGram " + BuildConfig.BUILD_VERSION_STRING + ", Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + "), " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("Safe mode: " + safeMode + ", thread: " + thread.getName());
        error.printStackTrace(pw);
        pw.println();
        pw.flush();

        File file = logFile();
        String old = file.exists() ? readFile(file) : "";
        String all = old + sw;
        if (all.length() > MAX_LOG_BYTES) {
            all = all.substring(all.length() - (int) (MAX_LOG_BYTES / 2)); // keep the newest half
        }
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(all.getBytes("UTF-8"));
        }
    }

    private static String readFile(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return new String(data, 0, read, "UTF-8");
        }
    }

    /** Number of crashes in the local log. */
    public static int crashCount() {
        try {
            File file = logFile();
            if (!file.exists()) {
                return 0;
            }
            String text = readFile(file);
            int count = 0;
            for (int i = text.indexOf(ENTRY_MARK); i >= 0; i = text.indexOf(ENTRY_MARK, i + 1)) {
                count++;
            }
            return count;
        } catch (Throwable e) {
            return 0;
        }
    }

    public static void clearLog() {
        try {
            //noinspection ResultOfMethodCallIgnored
            logFile().delete();
        } catch (Throwable ignore) {
        }
    }

    /** Opens the share sheet with the crash log as text (to send to the developer chat). */
    public static boolean shareLog(Activity activity) {
        if (activity == null) {
            return false;
        }
        try {
            File file = logFile();
            if (!file.exists()) {
                return false;
            }
            String text = readFile(file);
            if (text.length() > 60_000) {
                text = text.substring(text.length() - 60_000);
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "FlowGram crash report");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            activity.startActivity(Intent.createChooser(intent, "Отправить отчёт о сбоях"));
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }
}
