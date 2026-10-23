package org.telegram.milliy;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/** O'chirilgan xabarlarni saqlash va tahrirlangan xabarlar tarixi (faqat shu qurilmada). */
public class MilliyKeep {

    private static final SharedPreferences[] prefs = new SharedPreferences[org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT];
    private static final HashSet<String>[] deleted = new HashSet[org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT];
    /** Foydalanuvchi o'zi o'chirgan xabarlar (server javobi qaytganda «suhbatdosh o'chirdi» deb hisoblanmasin). */
    private static final HashSet<String> recentSelf = new HashSet<>();

    private static SharedPreferences prefs(int account) {
        if (prefs[account] == null) {
            prefs[account] = ApplicationLoader.applicationContext.getSharedPreferences("milliy_keep_" + account, Context.MODE_PRIVATE);
        }
        return prefs[account];
    }

    private static String key(long channelId, int mid) {
        return channelId + "_" + mid;
    }

    private static synchronized HashSet<String> deletedSet(int account) {
        if (deleted[account] == null) {
            HashSet<String> s = new HashSet<>();
            for (String k : prefs(account).getAll().keySet()) {
                if (k.startsWith("d_")) {
                    s.add(k.substring(2));
                }
            }
            deleted[account] = s;
        }
        return deleted[account];
    }

    /** Kim o'chirgani: 1 — men, 2 — boshqa tomon (suhbatdosh, kanal yoki admin). */
    private static final int BY_SELF = 1, BY_OTHER = 2;

    private static synchronized void mark(int account, String k, boolean on) {
        mark(account, k, on, 0);
    }

    private static synchronized void mark(int account, String k, boolean on, int by) {
        HashSet<String> s = deletedSet(account);
        if (on) {
            s.add(k);
            SharedPreferences.Editor e = prefs(account).edit().putBoolean("d_" + k, true);
            if (by != 0) {
                e.putInt("w_" + k, by);
            }
            e.apply();
        } else {
            s.remove(k);
            prefs(account).edit().remove("d_" + k).remove("w_" + k).apply();
        }
    }

    /** Vaqt yonidagi belgi: kim o'chirgani bilan. */
    public static String deletedMark(MessageObject m) {
        long channelId = m.messageOwner.peer_id != null ? m.messageOwner.peer_id.channel_id : 0;
        int by = prefs(m.currentAccount).getInt("w_" + key(channelId, m.getId()), 0);
        if (by == BY_SELF) {
            return LocaleController.getString(R.string.MilliyDeletedBySelf);
        }
        if (by == BY_OTHER) {
            long dialogId = m.getDialogId();
            if (dialogId > 0) {
                return LocaleController.getString(R.string.MilliyDeletedByPeer);
            }
            TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(m.currentAccount).getChat(-dialogId);
            if (chat != null && org.telegram.messenger.ChatObject.isChannelAndNotMegaGroup(chat)) {
                return LocaleController.getString(R.string.MilliyDeletedByChannel);
            }
            if (m.isOutOwner()) {
                return LocaleController.getString(R.string.MilliyDeletedByAdmin);
            }
        }
        return LocaleController.getString(R.string.MilliyDeletedMark);
    }

    public static boolean isDeleted(MessageObject m) {
        if (m == null || m.messageOwner == null || m.getId() <= 0 || m.scheduled) {
            return false;
        }
        long channelId = m.messageOwner.peer_id != null ? m.messageOwner.peer_id.channel_id : 0;
        synchronized (MilliyKeep.class) {
            return deletedSet(m.currentAccount).contains(key(channelId, m.getId()));
        }
    }

    /**
     * Serverdan kelgan «o'chirildi» yangilanishi. Qaytgan ro'yxatdagilar haqiqatan o'chiriladi,
     * qolganlari chatda «o'chirilgan» belgisi bilan qoladi.
     */
    public static ArrayList<Integer> onRemoteDelete(int account, long channelId, ArrayList<Integer> ids) {
        int mode = MilliyPro.keepDeletedMode();
        if (ids == null) {
            return new ArrayList<>();
        }
        if (mode == 0 || ids.isEmpty()) {
            return ids;
        }
        ArrayList<Integer> really = new ArrayList<>();
        synchronized (MilliyKeep.class) {
            for (Integer mid : ids) {
                String k = key(channelId, mid);
                boolean self = recentSelf.remove(k);
                if (self && mode == 1) {
                    really.add(mid); // o'zim o'chirganman — saqlanmaydi (mahalliy nusxa allaqachon o'chgan)
                } else {
                    mark(account, k, true, self ? BY_SELF : BY_OTHER);
                }
            }
        }
        return really;
    }

    /**
     * Foydalanuvchi o'zi o'chirayotganda. «Hammasi» rejimida xabar serverdan o'chadi, lekin bu yerda qoladi;
     * allaqachon «o'chirilgan» belgili xabar ikkinchi marta o'chirilsa — butunlay o'chadi.
     */
    public static ArrayList<Integer> onSelfDelete(int account, long channelId, ArrayList<Integer> ids, boolean forAll) {
        int mode = MilliyPro.keepDeletedMode();
        if (ids == null) {
            return null;
        }
        ArrayList<Integer> local = new ArrayList<>();
        int kept = 0;
        synchronized (MilliyKeep.class) {
            HashSet<String> s = deletedSet(account);
            for (Integer mid : ids) {
                String k = key(channelId, mid);
                if (mid <= 0) {
                    local.add(mid);
                } else if (s.contains(k)) {
                    mark(account, k, false);
                    local.add(mid);
                } else if (mode == 2 && forAll) {
                    mark(account, k, true, BY_SELF);
                    recentSelf.add(k);
                    kept++;
                } else {
                    if (mode != 0) {
                        recentSelf.add(k);
                    }
                    local.add(mid);
                }
            }
        }
        if (kept > 0) {
            AndroidUtilities.runOnUIThread(() -> {
                BaseFragment f = org.telegram.ui.LaunchActivity.getLastFragment();
                if (f != null) {
                    org.telegram.ui.Components.BulletinFactory.of(f).createSimpleBulletin(R.raw.ic_delete,
                            LocaleController.getString(R.string.MilliyKeptSelfDeleted)).show();
                }
            });
        }
        return local;
    }

    // ---- Tahrir tarixi ----

    /** Tahrir yangilanishi DB'ga yozilishidan oldin eski matnni saqlab qo'yadi (storage navbatida, tartib saqlanadi). */
    public static void onEdit(int account, TLRPC.Message edited) {
        if (!MilliyPro.keepEdits() || edited == null || edited.id <= 0) {
            return;
        }
        final long dialogId = edited.dialog_id;
        final int mid = edited.id;
        final long channelId = edited.peer_id != null ? edited.peer_id.channel_id : 0;
        final String newText = edited.message;
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            SQLiteCursor cursor = null;
            try {
                cursor = storage.getDatabase().queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE mid = %d AND uid = %d", mid, dialogId));
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message old = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        data.reuse();
                        if (old != null && !TextUtils.isEmpty(old.message) && !old.message.equals(newText)) {
                            addHistory(account, key(channelId, mid), old.message, old.edit_date != 0 ? old.edit_date : old.date);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        });
    }

    private static synchronized void addHistory(int account, String k, String text, int date) {
        try {
            JSONArray arr = new JSONArray(prefs(account).getString("e_" + k, "[]"));
            if (arr.length() > 0 && text.equals(arr.getJSONObject(arr.length() - 1).optString("t"))) {
                return;
            }
            arr.put(new JSONObject().put("t", text).put("d", date));
            while (arr.length() > 20) {
                arr.remove(0);
            }
            prefs(account).edit().putString("e_" + k, arr.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static String historyKey(MessageObject m) {
        long channelId = m.messageOwner.peer_id != null ? m.messageOwner.peer_id.channel_id : 0;
        return "e_" + key(channelId, m.getId());
    }

    public static boolean hasHistory(MessageObject m) {
        return m != null && m.messageOwner != null && m.getId() > 0 && prefs(m.currentAccount).contains(historyKey(m));
    }

    public static void showHistory(BaseFragment fragment, MessageObject m) {
        if (fragment.getParentActivity() == null || !hasHistory(m)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        try {
            JSONArray arr = new JSONArray(prefs(m.currentAccount).getString(historyKey(m), "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                sb.append("🕓 ").append(LocaleController.formatDateAudio(o.optLong("d"), true)).append('\n');
                sb.append(o.optString("t")).append("\n\n");
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        sb.append("✏️ ").append(LocaleController.getString(R.string.MilliyEditNow)).append('\n');
        sb.append(m.messageOwner.message == null ? "" : m.messageOwner.message);
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyEditHistory));
        b.setMessage(sb.toString());
        b.setPositiveButton(LocaleController.getString(R.string.OK), null);
        fragment.showDialog(b.create());
    }
}
