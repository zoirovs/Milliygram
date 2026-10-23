package org.telegram.milliy;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Milliygram qo'shimcha imkoniyatlari: sozlamalar va Telegram kodidagi ilgaklar (hook) uchun yagona joy.
 * Ghost va o'chirilgan xabarlar standart holatda O'CHIQ.
 */
public class MilliyPro {

    private static SharedPreferences prefs;

    public static SharedPreferences prefs() {
        if (prefs == null) {
            prefs = ApplicationLoader.applicationContext.getSharedPreferences("milliy_pro", Context.MODE_PRIVATE);
        }
        return prefs;
    }

    public static boolean get(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    public static void set(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    // ---- Ghost rejimi ----
    public static boolean ghost() {
        return get("ghost", false);
    }

    public static boolean ghostOnline() {
        return ghost() && get("ghost_online", true);
    }

    public static boolean ghostTyping() {
        return ghost() && get("ghost_typing", true);
    }

    public static boolean ghostRead() {
        return ghost() && get("ghost_read", true);
    }

    /** Hikoyani ko'rganim egasiga bildirilmaydi: ghost yoki alohida «yashirin ko'rish». */
    public static boolean ghostStory() {
        return ghost() && get("ghost_story", true) || get("story_anon", false);
    }

    public static boolean ghostButton() {
        return get("ghost_button", true);
    }

    // ---- Boshqa sozlamalar ----
    /** 0 — o'chiq, 1 — faqat suhbatdosh o'chirgani, 2 — hammasi. */
    public static int keepDeletedMode() {
        return prefs().getInt("keep_deleted", 0);
    }

    public static boolean keepEdits() {
        return get("keep_edits", false);
    }

    public static boolean strangers() {
        return get("strangers", false);
    }

    public static boolean confirmGift() {
        return get("confirm_gift", true);
    }

    public static boolean showId() {
        return get("show_id", true);
    }

    public static boolean hideStories() {
        return get("hide_stories", false);
    }

    public static boolean storyDownload() {
        return get("story_download", true);
    }

    /** Ovozli xabar va dumaloq video yozish boshlanganda «bir martalik» rejimi yoqilgan bo'ladi. */
    public static boolean oneTimeVoice() {
        return get("one_time_voice", false);
    }

    /** Boshqa tildagi chatlar avtomatik tarjima qilinadi (Premium'siz ham — bepul tarjimon orqali). */
    public static boolean autoTranslate() {
        return get("auto_translate", false);
    }

    /** Premium bo'lmasa tarjima serverdan emas, bepul tarjimon orqali qilinadi. */
    public static boolean useFreeTranslate(int account) {
        return autoTranslate() && !org.telegram.messenger.UserConfig.getInstance(account).isPremium();
    }

    public static boolean hideTabs() {
        return get("hide_tabs", false);
    }

    public static boolean timeSeconds() {
        return get("time_seconds", false);
    }

    public static boolean avatarProfile() {
        return get("avatar_profile", false);
    }

    private static java.text.SimpleDateFormat secondsFormat;
    private static boolean secondsFormat24;

    /** Xabar vaqti: sozlama yoqilgan bo'lsa soniyasi bilan (12:01:45). */
    public static String formatTime(long unixSeconds) {
        if (!timeSeconds()) {
            return org.telegram.messenger.LocaleController.getInstance().getFormatterDay().format(unixSeconds * 1000);
        }
        boolean is24 = org.telegram.messenger.LocaleController.is24HourFormat;
        if (secondsFormat == null || secondsFormat24 != is24) {
            secondsFormat = new java.text.SimpleDateFormat(is24 ? "HH:mm:ss" : "h:mm:ss a", java.util.Locale.US);
            secondsFormat24 = is24;
        }
        return secondsFormat.format(new java.util.Date(unixSeconds * 1000));
    }

    /** Chatlar ro'yxatida avatar bosilganda chat o'rniga profil ochiladi. true — profil ochildi. */
    public static boolean openProfile(org.telegram.ui.ActionBar.BaseFragment fragment, long dialogId) {
        if (fragment == null || dialogId == 0 || DialogObject.isEncryptedDialog(dialogId) || DialogObject.isFolderDialogId(dialogId)) {
            return false;
        }
        int account = fragment.getCurrentAccount();
        if (dialogId == org.telegram.messenger.UserConfig.getInstance(account).getClientUserId()) {
            return false;
        }
        android.os.Bundle args = new android.os.Bundle();
        if (dialogId > 0) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        fragment.presentFragment(new org.telegram.ui.ProfileActivity(args));
        return true;
    }

    /**
     * Yuborishdan oldin tasdiq (stiker, GIF, ovozli xabar). Sozlama o'chiq bo'lsa darhol yuboriladi.
     * cancel — «Bekor qilish» bosilganda (masalan, yozilgan ovoz faylini o'chirish uchun), bo'lishi shart emas.
     */
    public static void confirmSend(String key, int textRes, Runnable go, Runnable cancel) {
        org.telegram.ui.ActionBar.BaseFragment f = org.telegram.ui.LaunchActivity.getLastFragment();
        if (!get(key, false) || f == null || f.getParentActivity() == null) {
            go.run();
            return;
        }
        boolean[] done = new boolean[1];
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(f.getParentActivity(), f.getResourceProvider());
        b.setTitle(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.MilliyConfirmTitle));
        b.setMessage(org.telegram.messenger.LocaleController.getString(textRes));
        b.setPositiveButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Send), (d, w) -> {
            done[0] = true;
            go.run();
        });
        b.setNegativeButton(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Cancel), null);
        org.telegram.ui.ActionBar.AlertDialog dialog = b.create();
        dialog.setOnDismissListener(d -> {
            if (!done[0] && cancel != null) {
                cancel.run();
            }
        });
        dialog.show();
    }

    /** Avatar shakli: 0 — doira (Telegram), 1 — yumaloq kvadrat, 2 — kvadrat. */
    public static int avatarRadius(int circleRadius) {
        switch (prefs().getInt("avatar_shape", 0)) {
            case 1:
                return Math.round(circleRadius * 0.45f);
            case 2:
                return org.telegram.messenger.AndroidUtilities.dp(3);
            default:
                return circleRadius;
        }
    }

    // ---- Lite rejim: animatsiya, avto-ijro va effektlar o'chadi; o'chirilganda avvalgi holat qaytadi ----
    public static boolean lite() {
        return get("lite", false);
    }

    public static void setLite(boolean on) {
        if (on == lite()) {
            return;
        }
        if (on) {
            prefs().edit().putInt("lite_prev_flags", org.telegram.messenger.LiteMode.getValue(true)).putBoolean("lite", true).apply();
            org.telegram.messenger.LiteMode.setAllFlags(0);
        } else {
            int prev = prefs().getInt("lite_prev_flags", org.telegram.messenger.LiteMode.PRESET_HIGH);
            prefs().edit().putBoolean("lite", false).apply();
            org.telegram.messenger.LiteMode.setAllFlags(prev);
        }
    }

    // ---- Akkauntga bog'liq to'plamlar (qulf, seyf, mahalliy pin) ----
    public static Set<Long> idSet(int account, String name) {
        Set<String> raw = prefs().getStringSet(name + "_" + account, null);
        HashSet<Long> out = new HashSet<>();
        if (raw != null) {
            for (String s : raw) {
                try {
                    out.add(Long.parseLong(s));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return out;
    }

    private static final java.util.HashMap<String, Set<Long>> setCache = new java.util.HashMap<>();

    public static synchronized boolean inSet(int account, String name, long id) {
        String key = name + "_" + account;
        Set<Long> s = setCache.get(key);
        if (s == null) {
            s = idSet(account, name);
            setCache.put(key, s);
        }
        return s.contains(id);
    }

    public static synchronized Set<Long> getSet(int account, String name) {
        inSet(account, name, 0);
        return new HashSet<>(setCache.get(name + "_" + account));
    }

    public static synchronized void toggleSet(int account, String name, long id, boolean add) {
        inSet(account, name, 0);
        Set<Long> s = setCache.get(name + "_" + account);
        if (add) {
            s.add(id);
        } else {
            s.remove(id);
        }
        HashSet<String> raw = new HashSet<>();
        for (Long l : s) {
            raw.add(String.valueOf(l));
        }
        prefs().edit().putStringSet(name + "_" + account, raw).apply();
    }

    // ---- Cheksiz pin (server limitidan oshganlari faqat shu qurilmada) ----
    public static boolean isLocalPin(int account, long dialogId) {
        return inSet(account, "local_pins", dialogId);
    }

    // ---- Dialoglar ro'yxatidan yashirish: seyf va begonalar ----
    public static boolean isStranger(int account, TLRPC.Dialog d) {
        if (!strangers() || d == null || d.pinned || !DialogObject.isUserDialog(d.id)) {
            return false;
        }
        TLRPC.User u = MessagesController.getInstance(account).getUser(d.id);
        if (u == null || UserObject.isUserSelf(u) || u.bot || u.contact || u.mutual_contact || u.verified || u.support
                || UserObject.isService(u.id) || u.id == MilliyService.getBotId() || isLocalPin(account, d.id)) {
            return false;
        }
        return true;
    }

    public static boolean isHiddenDialog(int account, TLRPC.Dialog d) {
        return !(d instanceof TLRPC.TL_dialogFolder) && (MilliyLock.isVault(account, d.id) || isStranger(account, d));
    }

    public static ArrayList<TLRPC.Dialog> filterDialogs(int account, ArrayList<TLRPC.Dialog> src) {
        if (src == null || (!strangers() && getSet(account, "vault").isEmpty())) {
            return src;
        }
        ArrayList<TLRPC.Dialog> out = new ArrayList<>(src.size());
        for (int i = 0, n = src.size(); i < n; i++) {
            TLRPC.Dialog d = src.get(i);
            if (!isHiddenDialog(account, d)) {
                out.add(d);
            }
        }
        return out;
    }

    public static ArrayList<TLRPC.Dialog> strangerDialogs(int account) {
        ArrayList<TLRPC.Dialog> out = new ArrayList<>();
        for (TLRPC.Dialog d : MessagesController.getInstance(account).getDialogs(0)) {
            if (isStranger(account, d) && !MilliyLock.isVault(account, d.id)) {
                out.add(d);
            }
        }
        return out;
    }

    // ---- Imzosiz uzatish: keyingi forward oynasida «yuboruvchi nomini yashirish» yoqiladi ----
    private static long noAuthorUntil;

    public static void setNoAuthor() {
        noAuthorUntil = System.currentTimeMillis() + 120_000;
    }

    public static boolean takeNoAuthor() {
        boolean r = System.currentTimeMillis() < noAuthorUntil;
        noAuthorUntil = 0;
        return r;
    }

    /** Profil uchun Bot API uslubidagi ID: foydalanuvchi — id, kanal/superguruh — -100id, oddiy guruh — -id. */
    public static String formatId(TLRPC.User user, TLRPC.Chat chat) {
        if (user != null) {
            return String.valueOf(user.id);
        }
        if (chat != null) {
            return (org.telegram.messenger.ChatObject.isChannel(chat) ? "-100" : "-") + chat.id;
        }
        return "";
    }
}
