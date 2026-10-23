package org.telegram.milliy;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Kontakt o'zgarishlari jurnali va maxsus kontaktlar. Kontaktlar va yozishgan odamlar ismi, username, raqami yoki
 * rasmini o'zgartirsa — jurnalga yoziladi (faqat shu telefonda). Maxsus kontakt o'zgarsa yoki onlayn bo'lsa — bildirishnoma.
 * ZIRH uchun: notanish odam yaqinda ism/username/raqamini almashtirgan bo'lsa, chatdagi himoya panelida ko'rsatiladi.
 */
public class MilliyContacts {

    public static final int NAME = 1, USERNAME = 2, PHONE = 3, PHOTO = 4, PHOTO_REMOVED = 5;
    private static final int MAX_LOG = 300;
    private static final String CHANNEL = "milliy_contacts";
    private static final long ONLINE_NOTIFY_GAP = 30 * 60 * 1000L;

    private static final SharedPreferences[] prefs = new SharedPreferences[UserConfig.MAX_ACCOUNT_COUNT];
    private static final HashMap<Long, Long> lastOnlineNotify = new HashMap<>();

    private static SharedPreferences prefs(int account) {
        if (prefs[account] == null) {
            prefs[account] = ApplicationLoader.applicationContext.getSharedPreferences("milliy_contacts_" + account, Context.MODE_PRIVATE);
        }
        return prefs[account];
    }

    public static boolean enabled() {
        return MilliyPro.get("contact_log", true);
    }

    // ---------- Maxsus kontaktlar ----------

    public static boolean isSpecial(int account, long userId) {
        return MilliyPro.inSet(account, "special", userId);
    }

    public static void toggleSpecial(BaseFragment fragment, long userId) {
        int account = fragment.getCurrentAccount();
        boolean on = !isSpecial(account, userId);
        MilliyPro.toggleSet(account, "special", userId, on);
        org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                LocaleController.getString(on ? R.string.MilliySpecialAdded : R.string.MilliySpecialRemoved)).show();
    }

    // ---------- Kuzatuv (MessagesController ilgaklari) ----------

    /** putUser: serverdan yangi ma'lumot keldi — eski bilan solishtiriladi. */
    public static void onUserUpdate(int account, TLRPC.User old, TLRPC.User user) {
        if (!enabled() || old == null || user == null || old.min || user.min || user.bot || user.self || UserObject.isDeleted(user)) {
            return;
        }
        MessagesController mc = MessagesController.getInstance(account);
        boolean special = isSpecial(account, user.id);
        if (!special && !user.contact && !user.mutual_contact && mc.dialogs_dict.get(user.id) == null) {
            return;
        }
        String oldName = UserObject.getUserName(old), newName = UserObject.getUserName(user);
        if (!TextUtils.isEmpty(old.first_name) && !TextUtils.equals(oldName, newName)) {
            add(account, user, NAME, oldName, newName, special);
        }
        String oldU = UserObject.getPublicUsername(old), newU = UserObject.getPublicUsername(user);
        if (!TextUtils.equals(oldU, newU) && (oldU != null || newU != null)) {
            add(account, user, USERNAME, oldU == null ? "—" : "@" + oldU, newU == null ? "—" : "@" + newU, special);
        }
        if (!TextUtils.isEmpty(old.phone) && !TextUtils.isEmpty(user.phone) && !TextUtils.equals(old.phone, user.phone)) {
            add(account, user, PHONE, "+" + old.phone, "+" + user.phone, special);
        }
        long oldPhoto = old.photo != null ? old.photo.photo_id : 0, newPhoto = user.photo != null ? user.photo.photo_id : 0;
        // rasm faqat ikkala obyektda ham bor bo'lsa solishtiriladi (to'liq bo'lmagan obyekt «o'chirildi» deb hisoblanmasin)
        if (old.photo != null && user.photo != null && oldPhoto != newPhoto) {
            if (user.photo instanceof TLRPC.TL_userProfilePhotoEmpty || newPhoto == 0) {
                add(account, user, PHOTO_REMOVED, null, null, special);
            } else {
                add(account, user, PHOTO, null, null, special);
            }
        }
    }

    /** updateUserStatus: maxsus kontakt onlayn bo'ldi. */
    public static void onStatus(int account, long userId, TLRPC.UserStatus prev, TLRPC.UserStatus now) {
        if (!(now instanceof TLRPC.TL_userStatusOnline) || prev instanceof TLRPC.TL_userStatusOnline || !isSpecial(account, userId)) {
            return;
        }
        long t = System.currentTimeMillis();
        Long last = lastOnlineNotify.get(userId);
        if (last != null && t - last < ONLINE_NOTIFY_GAP) {
            return;
        }
        lastOnlineNotify.put(userId, t);
        TLRPC.User u = MessagesController.getInstance(account).getUser(userId);
        if (u != null) {
            notify(account, u, LocaleController.formatString(R.string.MilliySpecialOnline, UserObject.getUserName(u)));
        }
    }

    private static synchronized void add(int account, TLRPC.User user, int type, String from, String to, boolean special) {
        try {
            JSONArray arr = new JSONArray(prefs(account).getString("log", "[]"));
            JSONObject o = new JSONObject().put("u", user.id).put("t", type).put("d", System.currentTimeMillis() / 1000)
                    .put("n", UserObject.getUserName(user));
            if (from != null) {
                o.put("from", from);
            }
            if (to != null) {
                o.put("to", to);
            }
            arr.put(o);
            while (arr.length() > MAX_LOG) {
                arr.remove(0);
            }
            prefs(account).edit().putString("log", arr.toString()).putLong("last_" + user.id + "_" + type, System.currentTimeMillis() / 1000).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (special) {
            notify(account, user, describe(type, UserObject.getUserName(user), from, to));
        }
    }

    private static String describe(int type, String name, String from, String to) {
        switch (type) {
            case NAME:
                return LocaleController.formatString(R.string.MilliyChangeName, from, to);
            case USERNAME:
                return LocaleController.formatString(R.string.MilliyChangeUsername, name, from, to);
            case PHONE:
                return LocaleController.formatString(R.string.MilliyChangePhone, name, from, to);
            case PHOTO:
                return LocaleController.formatString(R.string.MilliyChangePhoto, name);
            default:
                return LocaleController.formatString(R.string.MilliyChangePhotoRemoved, name);
        }
    }

    /** ZIRH himoya paneli uchun: oxirgi 30 kunda ism/username/raqam o'zgargan bo'lsa — qisqa ogohlantirish, aks holda null. */
    public static String recentChangeWarning(int account, long userId) {
        long since = System.currentTimeMillis() / 1000 - 30L * 24 * 3600;
        SharedPreferences p = prefs(account);
        if (p.getLong("last_" + userId + "_" + PHONE, 0) > since) {
            return LocaleController.getString(R.string.MilliyGuardChangedPhone);
        }
        if (p.getLong("last_" + userId + "_" + NAME, 0) > since || p.getLong("last_" + userId + "_" + USERNAME, 0) > since) {
            return LocaleController.getString(R.string.MilliyGuardChangedName);
        }
        return null;
    }

    private static void notify(int account, TLRPC.User user, String text) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, LocaleController.getString(R.string.MilliySpecialChannel), NotificationManager.IMPORTANCE_DEFAULT));
            }
            Intent open = new Intent(ctx, LaunchActivity.class);
            open.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.putExtra("userId", user.id);
            open.putExtra("currentAccount", account);
            PendingIntent pi = PendingIntent.getActivity(ctx, (int) (user.id % Integer.MAX_VALUE), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle("⭐ " + LocaleController.getString(R.string.MilliySpecialTitle))
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            nm.notify((int) ((user.id * 31 + System.currentTimeMillis() / 1000) % Integer.MAX_VALUE), nb.build());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ---------- Jurnal sahifasi ----------

    public static class LogActivity extends BaseFragment {

        private LinearLayout list;

        @Override
        public View createView(Context context) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(LocaleController.getString(R.string.MilliyContactLog));
            actionBar.createMenu().addItem(1, R.drawable.msg_delete);
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == 1) {
                        prefs(currentAccount).edit().clear().apply();
                        fill();
                    }
                }
            });
            ScrollView scroll = new ScrollView(context);
            scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
            fragmentView = scroll;
            fill();
            return scroll;
        }

        private void fill() {
            list.removeAllViews();
            Context ctx = getParentActivity();
            JSONArray arr;
            try {
                arr = new JSONArray(prefs(currentAccount).getString("log", "[]"));
            } catch (Exception e) {
                arr = new JSONArray();
            }
            if (arr.length() == 0) {
                TextView empty = new TextView(ctx);
                empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                empty.setText(LocaleController.getString(R.string.MilliyContactLogEmpty));
                list.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 24, 20, 24));
                return;
            }
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                long uid = o.optLong("u");
                TextView row = new TextView(ctx);
                row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                row.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                row.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(10));
                row.setBackground(Theme.getSelectorDrawable(false));
                String when = LocaleController.formatDateAudio(o.optLong("d"), true);
                String star = isSpecial(currentAccount, uid) ? "⭐ " : "";
                row.setText(star + describe(o.optInt("t"), o.optString("n"), o.optString("from", "—"), o.optString("to", "—")) + "\n" + when);
                row.setOnClickListener(v -> {
                    Bundle args = new Bundle();
                    args.putLong("user_id", uid);
                    presentFragment(new ProfileActivity(args));
                });
                list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        }
    }
}
