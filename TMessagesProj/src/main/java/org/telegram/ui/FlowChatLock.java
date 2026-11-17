package org.telegram.ui;

import android.app.Activity;
import android.app.Dialog;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FlowConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/** FlowGram: PIN lock for chosen chats. */
public class FlowChatLock {

    private static Dialog lockDialog;

    /** Called from ChatActivity.onResume(): covers the chat with a PIN screen if it's locked. */
    public static void ensureUnlocked(BaseFragment fragment, long dialogId) {
        if (!FlowConfig.isChatLocked(dialogId) || FlowConfig.isTemporarilyUnlocked(dialogId)) {
            return;
        }
        Activity activity = fragment.getParentActivity();
        if (activity == null || (lockDialog != null && lockDialog.isShowing())) {
            return;
        }

        Dialog dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        dialog.setCancelable(false);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        layout.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);

        TextView icon = new TextView(activity);
        icon.setText("🔒");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 56);
        icon.setGravity(Gravity.CENTER);
        layout.addView(icon, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        TextView title = new TextView(activity);
        title.setText("Чат защищён паролем");
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        EditText input = createPinField(activity);
        layout.addView(input, LayoutHelper.createLinear(200, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

        TextView button = new TextView(activity);
        button.setText("Открыть");
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_featuredStickers_addButton), 0x22000000));
        layout.addView(button, LayoutHelper.createLinear(200, 48, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));

        Runnable tryUnlock = () -> {
            if (FlowConfig.checkPin(input.getText().toString())) {
                FlowConfig.markTemporarilyUnlocked(dialogId);
                AndroidUtilities.hideKeyboard(input);
                dialog.dismiss();
            } else {
                input.setText("");
                input.setError("Неверный PIN");
                AndroidUtilities.shakeViewSpring(input, 6);
            }
        };
        button.setOnClickListener(v -> tryUnlock.run());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryUnlock.run();
                return true;
            }
            return false;
        });

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                d.dismiss();
                fragment.finishFragment();
                return true;
            }
            return false;
        });
        dialog.setOnDismissListener(d -> lockDialog = null);
        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        lockDialog = dialog;
        dialog.show();
        input.requestFocus();
    }

    /** Lock or unlock a chat from its ⋮ menu. Asks to create a PIN the first time. */
    public static void toggleLock(BaseFragment fragment, long dialogId) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (FlowConfig.isChatLocked(dialogId)) {
            askPin(fragment, "Убрать пароль", "Введите PIN, чтобы снять защиту с этого чата.", pin -> {
                if (FlowConfig.checkPin(pin)) {
                    FlowConfig.setChatLocked(dialogId, false);
                    Toast.makeText(activity, "Пароль с чата снят", Toast.LENGTH_SHORT).show();
                    return null;
                }
                return PIN_WRONG;
            });
        } else if (!FlowConfig.hasPin()) {
            setupPin(fragment, () -> {
                FlowConfig.setChatLocked(dialogId, true);
                FlowConfig.markTemporarilyUnlocked(dialogId);
                Toast.makeText(activity, "Чат защищён паролем 🔒", Toast.LENGTH_SHORT).show();
            });
        } else {
            FlowConfig.setChatLocked(dialogId, true);
            FlowConfig.markTemporarilyUnlocked(dialogId);
            Toast.makeText(activity, "Чат защищён паролем 🔒", Toast.LENGTH_SHORT).show();
        }
    }

    /** Removes the PIN from every locked chat after the PIN is entered. */
    public static void askUnlockAll(BaseFragment fragment, Runnable onDone) {
        askPin(fragment, "Снять все пароли", "Введите PIN, чтобы снять защиту со всех чатов.", pin -> {
            if (!FlowConfig.checkPin(pin)) {
                return PIN_WRONG;
            }
            FlowConfig.unlockAllChats();
            if (onDone != null) {
                onDone.run();
            }
            return null;
        });
    }

    /** Create a new PIN, or change it (asks for the old one first). */
    public static void setupPin(BaseFragment fragment, Runnable onDone) {
        if (FlowConfig.hasPin()) {
            askPin(fragment, "Текущий PIN", "Введите текущий PIN.", pin -> {
                if (!FlowConfig.checkPin(pin)) {
                    return PIN_WRONG;
                }
                AndroidUtilities.runOnUIThread(() -> createPin(fragment, onDone), 200);
                return null;
            });
        } else {
            createPin(fragment, onDone);
        }
    }

    private static void createPin(BaseFragment fragment, Runnable onDone) {
        askPin(fragment, "Новый PIN", "Придумайте PIN из 4–8 цифр. Он будет нужен, чтобы открыть защищённые чаты.", pin -> {
            if (pin.length() < 4 || pin.length() > 8) {
                return "Нужно 4–8 цифр";
            }
            FlowConfig.setPin(pin);
            if (onDone != null) {
                onDone.run();
            }
            return null;
        });
    }

    private static final String PIN_WRONG = "Неверный PIN";

    private interface PinCallback {
        /** @return null if the PIN is accepted and the dialog can close, otherwise the error to show */
        String onPin(String pin);
    }

    private static void askPin(BaseFragment fragment, String title, String message, PinCallback callback) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        EditText input = createPinField(activity);
        LinearLayout container = new LinearLayout(activity);
        container.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        container.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, fragment.getResourceProvider());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setView(container);
        builder.setPositiveButton("OK", null);
        builder.setNegativeButton("Отмена", null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        View ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (ok != null) {
            ok.setOnClickListener(v -> {
                String error = callback.onPin(input.getText().toString());
                if (error == null) {
                    AndroidUtilities.hideKeyboard(input);
                    dialog.dismiss();
                } else {
                    input.setText("");
                    input.setError(error);
                    AndroidUtilities.shakeViewSpring(input, 6);
                }
            });
        }
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(input), 200);
    }

    private static EditText createPinField(Activity activity) {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        input.setHint("PIN");
        input.setSingleLine(true);
        return input;
    }
}
