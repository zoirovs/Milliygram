package org.telegram.milliy;

import android.content.SharedPreferences;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Milliygram jildlari (Sozlamalar → Chat jildlari → «Milliygram jildlari», har biri alohida yoqiladi):
 * Admin guruhlar, Admin kanallar (yaratgan yoki admin bo'lgan), Tanlanganlar (foydalanuvchi belgilagan chatlar).
 * Bular faqat shu ilovadagi «virtual» jildlar — serverga yuborilmaydi, limitga kirmaydi.
 */
public class MilliyAdminFolders {

    public static final int ADMIN_GROUPS = 0, ADMIN_CHANNELS = 1, FAVORITES = 2;
    public static final int COUNT = 3;
    private static final int BASE_ID = 250;
    public static final int[] NAMES = {R.string.MilliyAdminGroups, R.string.MilliyAdminChannels, R.string.MilliyFavorites};
    public static final int[] ICONS = {R.drawable.msg_folders_groups, R.drawable.msg_folders_channels, R.drawable.msg_fave};
    private static final String KEY_FAV = "milliy_favorites";
    private static final HashSet<Long>[] favorites = new HashSet[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        // eski «Adminlik papkalari» (bitta umumiy kalit) → alohida kalitlar
        SharedPreferences p = MilliyPro.prefs();
        if (p.contains("admin_folders")) {
            boolean on = p.getBoolean("admin_folders", false);
            p.edit().putBoolean("vf_" + ADMIN_GROUPS, on).putBoolean("vf_" + ADMIN_CHANNELS, on).remove("admin_folders").apply();
        }
    }

    public static boolean enabled(int type) {
        return MilliyPro.get("vf_" + type, false);
    }

    public static void setEnabled(int account, int type, boolean on) {
        MilliyPro.prefs().edit().putBoolean("vf_" + type, on).apply();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                apply(a);
            }
        }
    }

    public static boolean isVirtual(MessagesController.DialogFilter f) {
        return f != null && f.milliyAdmin >= 0;
    }

    /** Jildlar ro'yxati (qayta) yuklanganda chaqiriladi — UI oqimida. */
    public static void inject(MessagesController mc) {
        for (int i = mc.dialogFilters.size() - 1; i >= 0; i--) {
            MessagesController.DialogFilter f = mc.dialogFilters.get(i);
            if (isVirtual(f)) {
                mc.dialogFilters.remove(i);
                if (mc.dialogFiltersById.get(f.id) == f) {
                    mc.dialogFiltersById.remove(f.id);
                }
            }
        }
        for (int t = 0; t < COUNT; t++) {
            int id = BASE_ID + t;
            if (!enabled(t) || mc.dialogFiltersById.get(id) != null) {
                continue; // serverdagi haqiqiy jild shu id'ni olgan bo'lsa — tegmaymiz
            }
            MessagesController.DialogFilter f = new MessagesController.DialogFilter();
            f.id = id;
            f.milliyAdmin = t;
            f.name = LocaleController.getString(NAMES[t]);
            f.color = -1;
            f.order = 100000 + t;
            mc.dialogFilters.add(f);
            mc.dialogFiltersById.put(id, f);
        }
    }

    /** Yoqilganda/o'chirilganda yoki Tanlanganlar o'zgarganda. */
    public static void apply(int account) {
        MessagesController mc = MessagesController.getInstance(account);
        inject(mc);
        mc.sortDialogs(null);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogFiltersUpdated);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public static boolean matches(int account, int type, long dialogId) {
        if (type == FAVORITES) {
            return favorites(account).contains(dialogId);
        }
        if (dialogId >= 0) {
            return false;
        }
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        if (chat == null || ChatObject.isNotInChat(chat)) {
            return false;
        }
        boolean channel = ChatObject.isChannelAndNotMegaGroup(chat);
        boolean admin = chat.creator || chat.admin_rights != null;
        switch (type) {
            case ADMIN_GROUPS:
                return !channel && admin;
            case ADMIN_CHANNELS:
                return channel && admin;
        }
        return false;
    }

    // ---------- Tanlanganlar ----------

    private static HashSet<Long> favorites(int account) {
        if (favorites[account] == null) {
            HashSet<Long> set = new HashSet<>();
            for (String s : MessagesController.getMainSettings(account).getStringSet(KEY_FAV, new HashSet<>())) {
                try {
                    set.add(Long.parseLong(s));
                } catch (NumberFormatException ignore) {
                }
            }
            favorites[account] = set;
        }
        return favorites[account];
    }

    public static boolean isFavorite(int account, long dialogId) {
        return favorites(account).contains(dialogId);
    }

    /** Hammasi tanlanganlarda bo'lsa — olib tashlaydi, aks holda qo'shadi. Qo'shilsa jild avtomatik yoqiladi. Qo'shildimi — true. */
    public static boolean toggleFavorites(int account, Collection<Long> dialogIds) {
        HashSet<Long> set = favorites(account);
        boolean all = set.containsAll(dialogIds);
        if (all) {
            set.removeAll(dialogIds);
        } else {
            set.addAll(dialogIds);
        }
        Set<String> out = new HashSet<>();
        for (Long id : set) {
            out.add(String.valueOf(id));
        }
        MessagesController.getMainSettings(account).edit().putStringSet(KEY_FAV, out).apply();
        if (!all && !enabled(FAVORITES)) {
            setEnabled(account, FAVORITES, true);
        } else {
            apply(account);
        }
        return !all;
    }

    // ----------

    /** Foydalanuvchiga ko'rinadigan (haqiqiy) jildlar. */
    public static ArrayList<MessagesController.DialogFilter> real(ArrayList<MessagesController.DialogFilter> list) {
        boolean any = false;
        for (MessagesController.DialogFilter f : list) {
            if (isVirtual(f)) {
                any = true;
                break;
            }
        }
        if (!any) {
            return list;
        }
        ArrayList<MessagesController.DialogFilter> out = new ArrayList<>(list.size());
        for (MessagesController.DialogFilter f : list) {
            if (!isVirtual(f)) {
                out.add(f);
            }
        }
        return out;
    }

    /** Serverga yuboriladigan tartib ro'yxatidan virtual jildlarni olib tashlaydi. */
    public static void cleanOrder(int account, ArrayList<Integer> order) {
        MessagesController mc = MessagesController.getInstance(account);
        for (int i = order.size() - 1; i >= 0; i--) {
            if (isVirtual(mc.dialogFiltersById.get(order.get(i)))) {
                order.remove(i);
            }
        }
    }
}
