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
