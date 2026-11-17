package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FlowConfig;
import org.telegram.messenger.R;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** FlowGram mod settings screen. */
public class FlowSettingsActivity extends UniversalFragment {

    private static final int ID_HIDE_SPONSORED = 1;
    private static final int ID_SHOW_ID = 2;
    private static final int ID_HIDE_STORIES = 3;
    private static final int ID_GHOST_FULL = 10;
    private static final int ID_GHOST_TYPING = 11;
    private static final int ID_GHOST_READ = 12;
    private static final int ID_GHOST_ONLINE = 13;
    private static final int ID_GHOST_STORIES = 14;
    private static final int ID_SAVE_DELETED = 40;
    private static final int ID_SHOW_SECONDS = 70;
    private static final int ID_BACKGROUND = 94;
    private static final int ID_BATTERY = 95;
    private static final int ID_PLUGINS = 93;
    private static final int ID_CRASH_SHARE = 90;
    private static final int ID_CRASH_CLEAR = 91;
    private static final int ID_LEAVE_SAFE = 92;
    private static final int ID_DOUBLE_TAP = 80;
    private static final int ID_SILENT = 81;
    private static final int ID_SHAKE = 82;
    private static final int ID_HIDE_PREMIUM = 83;
    private static final int ID_REG_DATE = 84;
    private static final int ID_GHOST_BUTTON = 72;
    private static final int ID_MARK_DELETED = 73;
    private static final int ID_FORWARD_NO_AUTHOR = 71;
    private static final int ID_SHORTS = 60;
    private static final int ID_CHANGE_PIN = 61;
    private static final int ID_UNLOCK_ALL = 62;
    private static final int ID_BUILTIN_PROXY = 50;
    private static final int ID_PROXY_LIST = 51;
    private static final int ID_HIDE_PHONE = 30;
    private static final int ID_CONFIRM_CALLS = 31;
    private static final int ID_CHANNEL = 20;
    private static final int ID_CHAT = 21;
    private static final int ID_SHARE = 22;

    private FlowCommunity.HeaderView headerView;

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
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false); // FlowGram: refresh plugin count etc.
        }
    }

    @Override
    protected CharSequence getTitle() {
        return "Настройки FlowGram";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (headerView == null) {
            headerView = new FlowCommunity.HeaderView(getContext());
        }
        items.add(UItem.asCustomShadow(headerView, 160));

        items.add(UItem.asHeader("Работа в фоне"));
        items.add(UItem.asCheck(ID_BACKGROUND, "Получать сообщения в фоне").setChecked(FlowConfig.backgroundWork));
        if (FlowConfig.backgroundWork && !FlowConfig.isIgnoringBatteryOptimizations()) {
            items.add(UItem.asButton(ID_BATTERY, R.drawable.msg2_battery, "Отключить экономию батареи для FlowGram").accent());
        }
        items.add(UItem.asShadow("FlowGram держит соединение с Telegram, поэтому сообщения и уведомления приходят, даже когда приложение свёрнуто. В шторке будет тихий значок «FlowGram работает в фоне» — его можно скрыть в настройках уведомлений Android. Если сообщения всё равно запаздывают, отключите экономию батареи."));

        items.add(UItem.asHeader("Обход блокировок"));
        if (FlowConfig.hasBuiltinProxy()) {
            items.add(UItem.asCheck(ID_BUILTIN_PROXY, "Встроенный прокси FlowGram").setChecked(FlowConfig.builtinProxy));
        }
        items.add(UItem.asButton(ID_PROXY_LIST, R.drawable.msg2_secret, "Свои прокси"));
        items.add(UItem.asShadow(FlowConfig.hasBuiltinProxy()
                ? "FlowGram подключается через свой сервер, если Telegram заблокирован в вашей стране. Включается автоматически при первом запуске, ещё до входа в аккаунт."
                : "Добавьте свой MTProto- или веб-прокси, если Telegram заблокирован в вашей стране."));

        items.add(UItem.asHeader("Режим призрака"));
        items.add(UItem.asCheck(ID_GHOST_FULL, "Включить всё").setChecked(FlowConfig.isGhostModeFull()));
        items.add(UItem.asCheck(ID_GHOST_BUTTON, "Кнопка 👻 в списке чатов").setChecked(FlowConfig.showGhostButton));
        items.add(UItem.asCheck(ID_SHAKE, "Встряхнуть телефон — призрак").setChecked(FlowConfig.shakeGhost));
        items.add(UItem.asCheck(ID_GHOST_TYPING, "Не отправлять «печатает…»").setChecked(FlowConfig.ghostTyping));
        items.add(UItem.asCheck(ID_GHOST_READ, "Не отправлять «прочитано»").setChecked(FlowConfig.ghostRead));
        items.add(UItem.asCheck(ID_GHOST_ONLINE, "Скрывать статус «в сети»").setChecked(FlowConfig.ghostOnline));
        items.add(UItem.asCheck(ID_GHOST_STORIES, "Не отмечать истории просмотренными").setChecked(FlowConfig.ghostStories));
        items.add(UItem.asShadow("Собеседники не увидят, что вы печатаете, прочитали сообщение, прослушали голосовое, посмотрели историю или находитесь в сети. Прочитать чат вручную: ⋮ → «Прочитать (призрак)». Когда вы отправляете сообщение, Telegram всё равно помечает чат прочитанным."));

        items.add(UItem.asHeader("Эксклюзив FlowGram"));
        items.add(UItem.asButton(ID_SHORTS, R.drawable.msg_video, "FlowShorts — видео из каналов").accent());
        final int activePlugins = org.telegram.messenger.FlowPlugins.enabledCount();
        items.add(UItem.asButton(ID_PLUGINS, R.drawable.msg_fave, activePlugins > 0 ? "🧩 Плагины (включено: " + activePlugins + ")" : "🧩 Плагины").accent());
        items.add(UItem.asCheck(ID_SAVE_DELETED, "Сохранять удалённые сообщения").setChecked(FlowConfig.saveDeleted));
        items.add(UItem.asCheck(ID_MARK_DELETED, "Помечать удалённые 🗑").setChecked(FlowConfig.markDeleted));
        items.add(UItem.asShadow("Если собеседник удалит сообщение, у вас оно останется. Работает для сообщений, полученных, пока функция включена. Ваши собственные удаления работают как обычно."));

        items.add(UItem.asHeader("Пароль на чаты"));
        items.add(UItem.asButton(ID_CHANGE_PIN, R.drawable.msg_secret, FlowConfig.hasPin() ? "Изменить PIN" : "Придумать PIN"));
        if (FlowConfig.lockedChatsCount() > 0) {
            items.add(UItem.asButton(ID_UNLOCK_ALL, R.drawable.msg_permissions, "Снять пароль со всех чатов (" + FlowConfig.lockedChatsCount() + ")").red());
        }
        items.add(UItem.asShadow("Чтобы защитить чат, откройте его и нажмите ⋮ → «Поставить пароль». Превью таких чатов в списке скрыто. Забытый PIN восстановить нельзя — только очисткой данных приложения."));

        items.add(UItem.asHeader("Приватность"));
        items.add(UItem.asCheck(ID_HIDE_PHONE, "Скрывать мой номер").setChecked(FlowConfig.hidePhone));
        items.add(UItem.asCheck(ID_CONFIRM_CALLS, "Подтверждать звонки").setChecked(FlowConfig.confirmCalls));
        items.add(UItem.asShadow("Номер не показывается в шапке настроек: можно спокойно делать скриншоты. Перед звонком появится вопрос, чтобы не позвонить случайно."));

        items.add(UItem.asHeader("Чаты"));
        items.add(UItem.asCheck(ID_SHOW_SECONDS, "Секунды во времени сообщений").setChecked(FlowConfig.showSeconds));
        items.add(UItem.asCheck(ID_FORWARD_NO_AUTHOR, "Пересылать без автора").setChecked(FlowConfig.forwardNoAuthor));
        items.add(UItem.asCheck(ID_DOUBLE_TAP, "Двойной тап — ответить").setChecked(FlowConfig.doubleTapReply));
        items.add(UItem.asCheck(ID_SILENT, "Отправлять без звука").setChecked(FlowConfig.silentSend));
        items.add(UItem.asShadow("Время под сообщениями показывается как 14:32:07 — откройте чат заново, чтобы увидеть. Пересланные сообщения уходят без подписи «Переслано от», как будто их написали вы."));

        items.add(UItem.asHeader("Интерфейс"));
        items.add(UItem.asCheck(ID_HIDE_SPONSORED, "Скрывать рекламу").setChecked(FlowConfig.hideSponsored));
        items.add(UItem.asCheck(ID_HIDE_STORIES, "Скрывать истории").setChecked(FlowConfig.hideStories));
        items.add(UItem.asCheck(ID_SHOW_ID, "Показывать ID в профиле").setChecked(FlowConfig.showIdInProfile));
        items.add(UItem.asCheck(ID_REG_DATE, "Дата регистрации в профиле").setChecked(FlowConfig.showRegDate));
        items.add(UItem.asCheck(ID_HIDE_PREMIUM, "Скрыть Premium и Stars в настройках").setChecked(FlowConfig.hidePremium));
        items.add(UItem.asShadow("Реклама не загружается в каналах, ботах, поиске и видео. Истории убираются из списка чатов. ID копируется нажатием на строку в профиле. До 8 аккаунтов: Настройки → Добавить аккаунт."));

        items.add(UItem.asHeader("Надёжность"));
        if (org.telegram.messenger.FlowCrash.isSafeMode()) {
            items.add(UItem.asButton(ID_LEAVE_SAFE, R.drawable.msg_retry, "Выйти из безопасного режима").accent());
        }
        final int crashes = org.telegram.messenger.FlowCrash.crashCount();
        items.add(UItem.asButton(ID_CRASH_SHARE, R.drawable.msg_share, crashes > 0 ? "Отправить отчёт о сбоях (" + crashes + ")" : "Сбоев не было 👍"));
        if (crashes > 0) {
            items.add(UItem.asButton(ID_CRASH_CLEAR, R.drawable.msg_delete, "Очистить отчёт").red());
        }
        items.add(UItem.asShadow(org.telegram.messenger.FlowCrash.isSafeMode()
                ? "FlowGram несколько раз подряд закрылся при запуске, поэтому часть функций мода временно выключена. Отправьте отчёт в чат FlowGram и выйдите из безопасного режима."
                : "Если FlowGram вылетит, отчёт сохранится здесь — отправьте его в чат FlowGram, это помогает быстро исправлять ошибки. После 3 сбоев подряд при запуске включится безопасный режим."));

        items.add(UItem.asHeader("Сообщество"));
        items.add(UItem.asButton(ID_CHANNEL, R.drawable.msg_channel, "Канал FlowGram").accent());
        items.add(UItem.asButton(ID_CHAT, R.drawable.msg_groups, "Чат пользователей"));
        items.add(UItem.asButton(ID_SHARE, R.drawable.msg_share, "Поделиться FlowGram"));
        items.add(UItem.asShadow("В канале выходят обновления и секретные функции раньше всех. В чате можно предложить идею, и её добавят в следующую версию."));

        items.add(UItem.asShadow("FlowGram " + BuildVars.BUILD_VERSION_STRING));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_CHANNEL:
                FlowCommunity.openChannel(this);
                return;
            case ID_CHAT:
                FlowCommunity.openChat(this);
                return;
            case ID_SHARE:
                FlowCommunity.shareMod(getParentActivity());
                return;
            case ID_GHOST_FULL:
                FlowConfig.toggleGhostModeFull();
                break;
            case ID_GHOST_TYPING:
                FlowConfig.toggleGhostTyping();
                break;
            case ID_GHOST_READ:
                FlowConfig.toggleGhostRead();
                break;
            case ID_GHOST_ONLINE:
                FlowConfig.toggleGhostOnline();
                break;
            case ID_GHOST_STORIES:
                FlowConfig.toggleGhostStories();
                break;
            case ID_BUILTIN_PROXY:
                FlowConfig.toggleBuiltinProxy();
                break;
            case ID_PROXY_LIST:
                presentFragment(new ProxyListActivity());
                return;
            case ID_SHORTS:
                FlowShorts.open(this);
                return;
            case ID_CHANGE_PIN:
                FlowChatLock.setupPin(this, () -> listView.adapter.update(true));
                return;
            case ID_UNLOCK_ALL:
                FlowChatLock.askUnlockAll(this, () -> listView.adapter.update(true));
                return;
            case ID_SHOW_SECONDS:
                FlowConfig.toggleShowSeconds();
                break;
            case ID_FORWARD_NO_AUTHOR:
                FlowConfig.toggleForwardNoAuthor();
                break;
            case ID_GHOST_BUTTON:
                FlowConfig.toggleShowGhostButton();
                break;
            case ID_MARK_DELETED:
                FlowConfig.toggleMarkDeleted();
                break;
            case ID_DOUBLE_TAP:
                FlowConfig.toggleDoubleTapReply();
                break;
            case ID_SILENT:
                FlowConfig.toggleSilentSend();
                break;
            case ID_SHAKE:
                FlowConfig.toggleShakeGhost();
                FlowShake.onResume(getParentActivity());
                break;
            case ID_HIDE_PREMIUM:
                FlowConfig.toggleHidePremium();
                break;
            case ID_REG_DATE:
                FlowConfig.toggleShowRegDate();
                break;
            case ID_CRASH_SHARE:
                if (org.telegram.messenger.FlowCrash.crashCount() > 0) {
                    org.telegram.messenger.FlowCrash.shareLog(getParentActivity());
                }
                return;
            case ID_CRASH_CLEAR:
                org.telegram.messenger.FlowCrash.clearLog();
                break;
            case ID_LEAVE_SAFE:
                FlowConfig.leaveSafeMode();
                break;
            case ID_PLUGINS:
                presentFragment(new FlowPluginsActivity());
                return;
            case ID_BACKGROUND:
                FlowConfig.toggleBackgroundWork();
                break;
            case ID_BATTERY:
                FlowConfig.requestIgnoreBatteryOptimizations(getParentActivity());
                return;
            case ID_SAVE_DELETED:
                FlowConfig.toggleSaveDeleted();
                break;
            case ID_HIDE_PHONE:
                FlowConfig.toggleHidePhone();
                break;
            case ID_CONFIRM_CALLS:
                FlowConfig.toggleConfirmCalls();
                break;
            case ID_HIDE_SPONSORED:
                FlowConfig.toggleHideSponsored();
                break;
            case ID_HIDE_STORIES:
                FlowConfig.toggleHideStories();
                break;
            case ID_SHOW_ID:
                FlowConfig.toggleShowIdInProfile();
                break;
        }
        listView.adapter.update(true);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
