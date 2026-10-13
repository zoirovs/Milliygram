package org.telegram.milliy;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/** Avto-javob: shaxsiy chatda yangi xabar kelganda belgilangan matn bilan bir marta javob beradi (har odamga — har N soatda bir). */
public class MilliyAutoReply {

    public static boolean enabled() {
        return MilliyPro.get("auto_reply", false) && !TextUtils.isEmpty(text());
    }

    public static String text() {
        return MilliyPro.prefs().getString("auto_reply_text", "");
    }

    public static int hours() {
        return MilliyPro.prefs().getInt("auto_reply_hours", 6);
    }

    /** NotificationsController.processNewMessages boshida chaqiriladi. */
    public static void onNewMessages(int account, ArrayList<MessageObject> list) {
        if (!enabled() || list == null) {
            return;
        }
        MessagesController mc = MessagesController.getInstance(account);
        int now = (int) (System.currentTimeMillis() / 1000);
        for (MessageObject m : list) {
            if (m == null || m.isOut() || m.messageOwner == null || m.getId() <= 0 || m.scheduled || now - m.messageOwner.date > 600) {
                continue;
            }
            long did = m.getDialogId();
            if (did <= 0 || did != m.getSenderId()) {
                continue; // faqat shaxsiy chatlar
            }
            TLRPC.User u = mc.getUser(did);
            if (u == null || u.bot || UserObject.isUserSelf(u) || UserObject.isService(u.id) || u.id == MilliyService.getBotId()) {
                continue;
            }
            String key = "ar_" + account + "_" + did;
            int last = MilliyPro.prefs().getInt(key, 0);
            if (now - last < hours() * 3600) {
                continue;
            }
            MilliyPro.prefs().edit().putInt(key, now).apply();
            String reply = text();
            AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(account).sendMessage(
                    SendMessagesHelper.SendMessageParams.of(reply, did)));
        }
    }
}
