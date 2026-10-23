package org.telegram.milliy;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.util.Calendar;
import java.util.Map;

/**
 * Xabarga eslatma: tanlangan vaqtda bildirishnoma keladi, bosilsa chat shu xabarda ochiladi.
 * Eslatmalar faqat shu qurilmada saqlanadi; telefon qayta yoqilsa ilova ochilganda tiklanadi.
 */
public class MilliyReminder {

    private static final String CHANNEL = "milliy_reminders";
    private static final String EXTRA_ID = "milliy_reminder_id";

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("milliy_reminders", Context.MODE_PRIVATE);
    }

    private static String id(int account, long dialogId, int mid) {
        return account + "_" + dialogId + "_" + mid;
    }

    public static boolean has(MessageObject m) {
        return m != null && prefs().contains(id(m.currentAccount, m.getDialogId(), m.getId()));
    }

    public static void show(ChatActivity fragment, MessageObject m) {
        if (fragment == null || fragment.getParentActivity() == null || m == null) {
            return;
        }
        final boolean exists = has(m);
        CharSequence[] items = new CharSequence[exists ? 6 : 5];
        items[0] = LocaleController.getString(R.string.MilliyRemind30m);
        items[1] = LocaleController.getString(R.string.MilliyRemind1h);
        items[2] = LocaleController.getString(R.string.MilliyRemind3h);
        items[3] = LocaleController.getString(R.string.MilliyRemindTomorrow);
        items[4] = LocaleController.getString(R.string.MilliyRemindCustom);
        if (exists) {
            items[5] = LocaleController.getString(R.string.MilliyRemindCancel);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        b.setTitle(LocaleController.getString(R.string.MilliyRemind));
        b.setItems(items, (d, which) -> {
            long now = System.currentTimeMillis();
            switch (which) {
                case 0:
                    set(fragment, m, now + 30 * 60_000L);
                    break;
                case 1:
                    set(fragment, m, now + 60 * 60_000L);
                    break;
                case 2:
                    set(fragment, m, now + 3 * 60 * 60_000L);
                    break;
                case 3: {
                    Calendar c = Calendar.getInstance();
                    c.add(Calendar.DAY_OF_YEAR, 1);
                    c.set(Calendar.HOUR_OF_DAY, 9);
                    c.set(Calendar.MINUTE, 0);
                    c.set(Calendar.SECOND, 0);
                    set(fragment, m, c.getTimeInMillis());
                    break;
                }
                case 4: {
                    long self = UserConfig.getInstance(m.currentAccount).getClientUserId();
                    AlertsCreator.createScheduleDatePickerDialog(fragment.getParentActivity(), self,
                            (notify, date, repeat) -> set(fragment, m, date * 1000L), fragment.getResourceProvider()).show();
                    break;
                }
                case 5:
                    cancel(m.currentAccount, m.getDialogId(), m.getId());
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.ic_delete, LocaleController.getString(R.string.MilliyRemindCanceled)).show();
                    break;
            }
        });
        fragment.showDialog(b.create());
    }

    private static void set(ChatActivity fragment, MessageObject m, long at) {
        if (at <= System.currentTimeMillis()) {
            return;
        }
        String text = m.messageText == null ? "" : m.messageText.toString();
        if (text.length() > 200) {
            text = text.substring(0, 200) + "…";
        }
        try {
            JSONObject o = new JSONObject()
                    .put("a", m.currentAccount)
                    .put("d", m.getDialogId())
                    .put("m", m.getId())
                    .put("title", title(m.currentAccount, m.getDialogId()))
                    .put("text", text)
                    .put("at", at);
            String key = id(m.currentAccount, m.getDialogId(), m.getId());
            prefs().edit().putString(key, o.toString()).apply();
            schedule(key, at);
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                LocaleController.formatString(R.string.MilliyRemindSet, LocaleController.formatDateAudio(at / 1000, true))).show();
    }

    private static String title(int account, long dialogId) {
        MessagesController mc = MessagesController.getInstance(account);
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User u = mc.getUser(dialogId);
            return u != null ? UserObject.getUserName(u) : "";
        }
        TLRPC.Chat c = mc.getChat(-dialogId);
        return c != null && c.title != null ? c.title : "";
    }

    private static PendingIntent alarmIntent(String key) {
        Intent i = new Intent(ApplicationLoader.applicationContext, Receiver.class);
        i.setAction("org.telegram.milliy.REMINDER." + key);
        i.putExtra(EXTRA_ID, key);
        return PendingIntent.getBroadcast(ApplicationLoader.applicationContext, key.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(String key, long at) {
        AlarmManager am = (AlarmManager) ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = alarmIntent(key);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception e) {
            FileLog.e(e);
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static void cancel(int account, long dialogId, int mid) {
        String key = id(account, dialogId, mid);
        prefs().edit().remove(key).apply();
        AlarmManager am = (AlarmManager) ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(alarmIntent(key));
        }
    }

    /** Ilova ochilganda va telefon qayta yoqilganda: kelajakdagilari qayta rejalashtiriladi, o'tib ketganlari darhol chiqadi. */
    public static void restore() {
        try {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, ?> e : prefs().getAll().entrySet()) {
                JSONObject o = new JSONObject(String.valueOf(e.getValue()));
                long at = o.optLong("at");
                schedule(e.getKey(), Math.max(at, now + 5000));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void fire(Context context, String key) {
        String raw = prefs().getString(key, null);
        if (raw == null) {
            return;
        }
        prefs().edit().remove(key).apply();
        try {
            JSONObject o = new JSONObject(raw);
            int account = o.optInt("a");
            long dialogId = o.optLong("d");
            int mid = o.optInt("m");
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, LocaleController.getString(R.string.MilliyRemindChannel), NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            Intent open = new Intent(context, LaunchActivity.class);
            open.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (DialogObject.isUserDialog(dialogId)) {
                open.putExtra("userId", dialogId);
            } else {
                open.putExtra("chatId", -dialogId);
            }
            open.putExtra("message_id", mid);
            open.putExtra("currentAccount", account);
            PendingIntent content = PendingIntent.getActivity(context, key.hashCode(), open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String title = "⏰ " + LocaleController.getString(R.string.MilliyRemind) + (o.optString("title").isEmpty() ? "" : ": " + o.optString("title"));
            String text = o.optString("text");
            NotificationCompat.Builder nb = new NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(content);
            nm.notify(key.hashCode(), nb.build());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                restore();
                return;
            }
            String key = intent.getStringExtra(EXTRA_ID);
            if (key != null) {
                fire(context, key);
            }
        }
    }
}
