package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.View;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * FlowGram community: links to your channel and chat, the animated header for
 * the mod menu, and a one-time invite shown after the first launch.
 */
public class FlowCommunity {

    // ===== Replace these with your own public usernames (without @) =====
    public static final String CHANNEL_USERNAME = "flowgram_channel";
    public static final String CHAT_USERNAME = "flowgram_chat";
    // =====================================================================

    public static String channelLink() {
        return "https://t.me/" + CHANNEL_USERNAME;
    }

    public static void openChannel(BaseFragment fragment) {
        open(fragment, CHANNEL_USERNAME);
    }

    public static void openChat(BaseFragment fragment) {
        open(fragment, CHAT_USERNAME);
    }

    private static void open(BaseFragment fragment, String username) {
        if (fragment == null) {
            return;
        }
        MessagesController.getInstance(fragment.getCurrentAccount()).openByUserName(username, fragment, 0);
    }

    public static void shareMod(Context context) {
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT,
                    "Пользуюсь FlowGram — Telegram с режимом призрака, без рекламы и с ID в профилях. Присоединяйся: " + channelLink());
            Intent chooser = Intent.createChooser(intent, "Поделиться FlowGram");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ---------------------------------------------------------------------
    // One-time invite after login
    // ---------------------------------------------------------------------

    private static final String PREFS = "flowconfig";
    private static final String KEY_INVITE_SHOWN = "communityInviteShown";
    private static boolean scheduled;

    /** Call from the chat list. Shows the invite once per install, a few seconds after opening. */
    public static void maybeShowInvite(BaseFragment fragment) {
        if (scheduled || fragment == null || !UserConfig.getInstance(fragment.getCurrentAccount()).isClientActivated()) {
            return;
        }
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_INVITE_SHOWN, false)) {
            return;
        }
        scheduled = true;
        AndroidUtilities.runOnUIThread(() -> {
            if (fragment.getParentActivity() == null || fragment.isFinished) {
                scheduled = false;
                return;
            }
            prefs.edit().putBoolean(KEY_INVITE_SHOWN, true).apply();
            AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
            builder.setTitle("Добро пожаловать в FlowGram 👻");
            builder.setMessage("В канале FlowGram — новые версии мода, секретные функции раньше всех и голосования за то, что добавить дальше.\n\nПодпишитесь, чтобы не пропустить обновления.");
            builder.setPositiveButton("Открыть канал", (dialog, which) -> openChannel(fragment));
            builder.setNegativeButton("Позже", null);
            fragment.showDialog(builder.create());
        }, 4000);
    }

    // ---------------------------------------------------------------------
    // Animated header for the mod menu
    // ---------------------------------------------------------------------

    public static class HeaderView extends View {

        private static final int[] COLORS = {0xFF7B5CFF, 0xFF3FA9FF, 0xFF2EE6C5, 0xFFB45CFF, 0xFF7B5CFF};

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint pillTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF pillRect = new RectF();
        private final Matrix matrix = new Matrix();
        private LinearGradient gradient;
        private final long startTime = SystemClock.elapsedRealtime();
        private int taps;

        public HeaderView(Context context) {
            super(context);
            titlePaint.setColor(0xFFFFFFFF);
            titlePaint.setTextSize(AndroidUtilities.dp(32));
            titlePaint.setTypeface(AndroidUtilities.bold());
            subtitlePaint.setColor(0xDDFFFFFF);
            subtitlePaint.setTextSize(AndroidUtilities.dp(14));
            pillPaint.setColor(0x33FFFFFF);
            pillTextPaint.setColor(0xFFFFFFFF);
            pillTextPaint.setTextSize(AndroidUtilities.dp(12));
            pillTextPaint.setTypeface(AndroidUtilities.bold());
            dotPaint.setColor(0xFFFFFFFF);
            setOnClickListener(v -> onTap());
        }

        private void onTap() {
            taps++;
            if (taps == 7) {
                taps = 0;
                Toast.makeText(getContext(), "👻 Вы нашли пасхалку FlowGram! Расскажите в чате сообщества.", Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(160), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            gradient = new LinearGradient(0, 0, w * 2f, 0, COLORS, null, Shader.TileMode.MIRROR);
            bgPaint.setShader(gradient);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float t = (SystemClock.elapsedRealtime() - startTime) / 1000f;
            final int w = getWidth(), h = getHeight();
            final float pad = AndroidUtilities.dp(16);

            rect.set(pad, AndroidUtilities.dp(12), w - pad, h - AndroidUtilities.dp(8));
            if (gradient != null) {
                matrix.setTranslate(-(t * w * 0.25f) % (w * 2f), 0);
                gradient.setLocalMatrix(matrix);
            }
            canvas.drawRoundRect(rect, AndroidUtilities.dp(20), AndroidUtilities.dp(20), bgPaint);

            // floating sparkles
            canvas.save();
            canvas.clipRect(rect);
            for (int i = 0; i < 14; i++) {
                float seed = i * 37.17f;
                float x = rect.left + (float) ((Math.sin(seed) * 0.5 + 0.5) * rect.width());
                float y = rect.bottom - ((t * (12 + i % 5 * 6) + seed * 11) % (rect.height() + 20));
                float alpha = (float) (0.25 + 0.35 * Math.sin(t * 2 + seed));
                dotPaint.setAlpha((int) (255 * Math.max(0, alpha)));
                canvas.drawCircle(x, y, AndroidUtilities.dp(1.5f + i % 3), dotPaint);
            }
            canvas.restore();

            float textX = rect.left + AndroidUtilities.dp(20);
            canvas.drawText("FlowGram", textX, rect.top + AndroidUtilities.dp(56), titlePaint);
            canvas.drawText("Telegram, который работает на вас", textX, rect.top + AndroidUtilities.dp(82), subtitlePaint);

            String version = "v" + BuildVars.BUILD_VERSION_STRING;
            float pw = pillTextPaint.measureText(version) + AndroidUtilities.dp(20);
            pillRect.set(textX, rect.bottom - AndroidUtilities.dp(40), textX + pw, rect.bottom - AndroidUtilities.dp(16));
            canvas.drawRoundRect(pillRect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), pillPaint);
            canvas.drawText(version, pillRect.left + AndroidUtilities.dp(10), pillRect.centerY() + AndroidUtilities.dp(4.5f), pillTextPaint);

            if (isAttachedToWindow()) {
                postInvalidateOnAnimation();
            }
        }
    }
}
