package org.telegram.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FlowPlugins;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/** FlowGram plugins screen: list, enable/disable, install from file or clipboard, details/remove on long press. */
public class FlowPluginsActivity extends UniversalFragment {

    private static final int REQUEST_PICK_PLUGIN = 4711;
    private static final int ID_FROM_FILE = 1;
    private static final int ID_FROM_CLIPBOARD = 2;
    private static final int ID_HOWTO = 3;
    private static final int ID_PLUGIN_BASE = 1000;

    private ArrayList<FlowPlugins.Plugin> shown = new ArrayList<>();

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        fragmentView = super.createView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        return fragmentView;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    protected CharSequence getTitle() {
        return "Плагины";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        shown = FlowPlugins.getPlugins();
        items.add(UItem.asHeader("Установленные"));
        for (int i = 0; i < shown.size(); i++) {
            FlowPlugins.Plugin p = shown.get(i);
            items.add(UItem.asCheck(ID_PLUGIN_BASE + i, (p.builtIn ? "⭐ " : "🧩 ") + p.name).setChecked(FlowPlugins.isEnabled(p.id)));
        }
        items.add(UItem.asShadow("Нажмите, чтобы включить или выключить. Долгое нажатие — подробности и удаление."));

        items.add(UItem.asHeader("Добавить плагин"));
        items.add(UItem.asButton(ID_FROM_FILE, R.drawable.msg_fave, "Из файла (.json)").accent());
        items.add(UItem.asButton(ID_FROM_CLIPBOARD, R.drawable.msg_copy, "Вставить из буфера обмена"));
        items.add(UItem.asButton(ID_HOWTO, R.drawable.msg_info, "Как сделать свой плагин"));
        items.add(UItem.asShadow("Плагины FlowGram — это файлы с правилами: команды, автозамена, подпись. Они не могут выполнять код, читать переписку или получить доступ к аккаунту, поэтому устанавливать их безопасно."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_PLUGIN_BASE && item.id - ID_PLUGIN_BASE < shown.size()) {
            FlowPlugins.Plugin p = shown.get(item.id - ID_PLUGIN_BASE);
            FlowPlugins.setEnabled(p.id, !FlowPlugins.isEnabled(p.id));
            listView.adapter.update(true);
            return;
        }
        switch (item.id) {
            case ID_FROM_FILE: {
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    getParentActivity().startActivityForResult(Intent.createChooser(intent, "Выберите файл плагина"), REQUEST_PICK_PLUGIN);
                } catch (Exception e) {
                    FileLog.e(e);
                    showError("Не удалось открыть выбор файла");
                }
                break;
            }
            case ID_FROM_CLIPBOARD: {
                String text = null;
                try {
                    ClipboardManager cm = (ClipboardManager) getParentActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = cm != null ? cm.getPrimaryClip() : null;
                    if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null) {
                        text = clip.getItemAt(0).getText().toString();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (text == null || text.trim().isEmpty()) {
                    showError("В буфере обмена нет текста плагина");
                } else {
                    installFromText(text);
                }
                break;
            }
            case ID_HOWTO: {
                AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
                b.setTitle("Свой плагин");
                b.setMessage("Создайте файл .json по образцу и отправьте его в чат или на телефон, затем установите через «Из файла».\n\n"
                        + "commands — сообщение целиком заменяется на текст (не работает в чатах с ботами).\n"
                        + "replace — замена слов в отправляемых сообщениях.\n"
                        + "suffix — подпись в конце каждого сообщения.\n\n" + FlowPlugins.SAMPLE);
                b.setPositiveButton("Копировать образец", (d, w) -> {
                    AndroidUtilities.addToClipboard(FlowPlugins.SAMPLE);
                    BulletinFactory.of(this).createCopyBulletin("Образец плагина скопирован").show();
                });
                b.setNegativeButton("Закрыть", null);
                showDialog(b.create());
                break;
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < ID_PLUGIN_BASE || item.id - ID_PLUGIN_BASE >= shown.size()) {
            return false;
        }
        FlowPlugins.Plugin p = shown.get(item.id - ID_PLUGIN_BASE);
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        b.setTitle(p.name);
        b.setMessage(p.summary() + "\n\nid: " + p.id + (p.builtIn ? "\nВстроен в FlowGram" : ""));
        if (!p.builtIn) {
            b.setPositiveButton("Удалить", (d, w) -> {
                FlowPlugins.remove(p.id);
                listView.adapter.update(true);
            });
        }
        b.setNegativeButton("Закрыть", null);
        AlertDialog dialog = b.create();
        showDialog(dialog);
        if (!p.builtIn) {
            View del = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (del instanceof android.widget.TextView) {
                ((android.widget.TextView) del).setTextColor(getThemedColor(org.telegram.ui.ActionBar.Theme.key_text_RedBold));
            }
        }
        return true;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK_PLUGIN || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try (InputStream in = getParentActivity().getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("empty");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 64 * 1024) {
                    showError("Файл слишком большой для плагина (максимум 64 КБ)");
                    return;
                }
                out.write(buf, 0, n);
            }
            installFromText(out.toString("UTF-8"));
        } catch (Exception e) {
            FileLog.e(e);
            showError("Не удалось прочитать файл");
        }
    }

    private void installFromText(String json) {
        try {
            FlowPlugins.Plugin p = FlowPlugins.install(json.trim());
            listView.adapter.update(true);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Плагин «" + p.name + "» установлен и включён").show();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void showError(String text) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        b.setTitle("Плагин не установлен");
        b.setMessage(text);
        b.setPositiveButton("OK", null);
        showDialog(b.create());
    }
}
