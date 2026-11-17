package org.telegram.ui;

import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;

/**
 * FlowShorts: a full-screen feed of the latest videos from the channels you follow.
 * Uses Telegram's global media search (channels only, video filter) and the built-in media viewer,
 * so playback, saving, sharing and "go to message" work exactly like in a normal chat.
 */
public class FlowShorts {

    private static final int LIMIT = 80;

    public static void open(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        final AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(true);
        final boolean[] cancelled = new boolean[1];

        TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.broadcasts_only = true;
        req.q = "";
        req.filter = new TLRPC.TL_inputMessagesFilterVideo();
        req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        req.limit = LIMIT;

        final int reqId = ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (cancelled[0]) {
                return;
            }
            try {
                progress.dismiss();
            } catch (Exception ignore) {
            }
            if (fragment.getParentActivity() == null) {
                return;
            }
            if (!(response instanceof TLRPC.messages_Messages)) {
                Toast.makeText(fragment.getParentActivity(), "Не удалось загрузить FlowShorts" + (error != null ? ": " + error.text : ""), Toast.LENGTH_SHORT).show();
                return;
            }
            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
            MessagesController controller = MessagesController.getInstance(account);
            controller.putUsers(res.users, false);
            controller.putChats(res.chats, false);

            ArrayList<MessageObject> videos = new ArrayList<>();
            for (int i = 0; i < res.messages.size(); i++) {
                try {
                    MessageObject obj = new MessageObject(account, res.messages.get(i), false, true);
                    if (obj.isVideo()) {
                        videos.add(obj);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (videos.isEmpty()) {
                Toast.makeText(fragment.getParentActivity(), "В ваших каналах пока нет видео", Toast.LENGTH_SHORT).show();
                return;
            }
            PhotoViewer.getInstance().setParentActivity(fragment);
            PhotoViewer.getInstance().openPhoto(videos, 0, 0, 0, 0, new PhotoViewer.EmptyPhotoViewerProvider());
        }));
        progress.setOnCancelListener(d -> {
            cancelled[0] = true;
            ConnectionsManager.getInstance(account).cancelRequest(reqId, true);
        });
        progress.showDelayed(300);
    }
}
