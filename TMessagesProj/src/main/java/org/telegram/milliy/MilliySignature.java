package org.telegram.milliy;

import android.text.TextUtils;
import android.util.Base64;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;

/**
 * Avto-imzo: har bir chat uchun saqlangan matn (uslublar va premium emoji bilan) har xabar oxiriga qo'shiladi.
 * Imzo chatning yozish maydonidan olinadi — u yerda istalgan formatlashni qilish mumkin.
 */
public class MilliySignature {

    private static class Sig {
        String text;
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        boolean on;
    }

    private static String key(int account, long dialogId) {
        return "sig_" + account + "_" + dialogId;
    }

    private static Sig load(int account, long dialogId) {
        String raw = MilliyPro.prefs().getString(key(account, dialogId), null);
        if (raw == null) {
            return null;
        }
        try {
            SerializedData data = new SerializedData(Base64.decode(raw, Base64.NO_WRAP));
            Sig s = new Sig();
            s.on = data.readBool(false);
            s.text = data.readString(false);
            int n = data.readInt32(false);
            for (int i = 0; i < n; i++) {
                TLRPC.MessageEntity e = TLRPC.MessageEntity.TLdeserialize(data, data.readInt32(false), false);
                if (e != null) {
                    s.entities.add(e);
                }
            }
            data.cleanup();
            return TextUtils.isEmpty(s.text) ? null : s;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void save(int account, long dialogId, Sig s) {
        if (s == null) {
            MilliyPro.prefs().edit().remove(key(account, dialogId)).apply();
            return;
        }
        SerializedData data = new SerializedData();
        data.writeBool(s.on);
        data.writeString(s.text);
        data.writeInt32(s.entities.size());
        for (TLRPC.MessageEntity e : s.entities) {
            e.serializeToStream(data);
        }
        MilliyPro.prefs().edit().putString(key(account, dialogId), Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP)).apply();
        data.cleanup();
    }

    /** SendMessagesHelper.sendMessage boshida: matn yoki izoh oxiriga imzo qo'shiladi. */
    public static void apply(int account, SendMessagesHelper.SendMessageParams p) {
        if (p == null || p.retryMessageObject != null || p.peer == 0 || p.scheduleDate != 0 && p.scheduleRepeatPeriod != 0) {
            return;
        }
        Sig s = load(account, p.peer);
        if (s == null || !s.on) {
            return;
        }
        MessagesController mc = MessagesController.getInstance(account);
        if (!TextUtils.isEmpty(p.message) && p.photo == null && p.document == null && p.location == null && p.poll == null
                && p.game == null && p.invoice == null && p.user == null && p.todo == null && !p.message.startsWith("/")
                && (mc.diceEmojies == null || !mc.diceEmojies.contains(p.message.replace("\ufe0f", "")))) {
            int base = p.message.length() + 2;
            if (base + s.text.length() > mc.maxMessageLength) {
                return;
            }
            p.message = p.message + "\n\n" + s.text;
            p.entities = shifted(p.entities, s.entities, base);
        } else if (!TextUtils.isEmpty(p.caption)) {
            int base = p.caption.length() + 2;
            if (base + s.text.length() > mc.getCaptionMaxLengthLimit()) {
                return;
            }
            p.caption = p.caption + "\n\n" + s.text;
            p.entities = shifted(p.entities, s.entities, base);
        }
    }

    private static ArrayList<TLRPC.MessageEntity> shifted(ArrayList<TLRPC.MessageEntity> own, ArrayList<TLRPC.MessageEntity> sig, int base) {
        ArrayList<TLRPC.MessageEntity> out = own != null ? new ArrayList<>(own) : new ArrayList<>();
        for (TLRPC.MessageEntity e : sig) {
            SerializedData d = new SerializedData();
            e.serializeToStream(d);
            SerializedData r = new SerializedData(d.toByteArray());
            TLRPC.MessageEntity copy = TLRPC.MessageEntity.TLdeserialize(r, r.readInt32(false), false);
            d.cleanup();
            r.cleanup();
            if (copy != null) {
                copy.offset += base;
                out.add(copy);
            }
        }
        return out;
    }

    /** Chat menyusidan: yozish maydonidagi matnni imzo qilib saqlash, yoqish/o'chirish yoki o'chirib tashlash. */
    public static void showMenu(ChatActivity chat) {
        if (chat.getParentActivity() == null) {
            return;
        }
        int account = chat.getCurrentAccount();
        long did = chat.getDialogId();
        CharSequence input = chat.getChatActivityEnterView() != null ? chat.getChatActivityEnterView().getFieldText() : null;
        Sig cur = load(account, did);
        if (!TextUtils.isEmpty(input) && input.toString().trim().length() > 0) {
            CharSequence[] text = {input};
            Sig s = new Sig();
            ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(account).getEntities(text, true);
            s.text = text[0].toString();
            if (entities != null) {
                s.entities.addAll(entities);
            }
            s.on = true;
            save(account, did, s);
            chat.getChatActivityEnterView().setFieldText("");
            BulletinFactory.of(chat).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.MilliySigSaved)).show();
            return;
        }
        if (cur == null) {
            AlertDialog.Builder b = new AlertDialog.Builder(chat.getParentActivity());
            b.setTitle(LocaleController.getString(R.string.MilliySig));
            b.setMessage(LocaleController.getString(R.string.MilliySigHowTo));
            b.setPositiveButton(LocaleController.getString(R.string.OK), null);
            chat.showDialog(b.create());
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(chat.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliySig));
        b.setMessage(cur.text);
        b.setPositiveButton(LocaleController.getString(cur.on ? R.string.MilliySigOff : R.string.MilliySigOn), (d, w) -> {
            cur.on = !cur.on;
            save(account, did, cur);
        });
        b.setNeutralButton(LocaleController.getString(R.string.Delete), (d, w) -> save(account, did, null));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        chat.showDialog(b.create());
    }
}
