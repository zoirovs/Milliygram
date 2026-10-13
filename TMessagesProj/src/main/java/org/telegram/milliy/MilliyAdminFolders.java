package org.telegram.milliy;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Adminlik papkalari (sozlamadan yoqiladi): Admin guruhlari, Admin kanallari, Mening kanallarim, Mening guruhlarim.
 * Bular faqat shu ilovadagi «virtual» papkalar — serverga yuborilmaydi, tahrirlanmaydi, limitga kirmaydi.
 */
public class MilliyAdminFolders {

    public static final int ADMIN_GROUPS = 0, ADMIN_CHANNELS = 1, MY_CHANNELS = 2, MY_GROUPS = 3;
    private static final int BASE_ID = 250;
    private static final int[] NAMES = {R.string.MilliyAdminGroups, R.string.MilliyAdminChannels, R.string.MilliyMyChannels, R.string.MilliyMyGroups};

    public static boolean enabled() {
        return MilliyPro.get("admin_folders", false);
    }

    public static boolean isVirtual(MessagesController.DialogFilter f) {
        return f != null && f.milliyAdmin >= 0;
    }

    /** Papkalar ro'yxati (qayta) yuklanganda chaqiriladi — UI oqimida. */
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
        if (!enabled()) {
            return;
        }
        for (int t = 0; t < NAMES.length; t++) {
            int id = BASE_ID + t;
            if (mc.dialogFiltersById.get(id) != null) {
                continue; // serverdagi haqiqiy papka shu id'ni olgan bo'lsa — tegmaymiz
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

    /** Sozlamada yoqilganda/o'chirilganda. */
    public static void apply(int account) {
        MessagesController mc = MessagesController.getInstance(account);
        inject(mc);
        mc.sortDialogs(null);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogFiltersUpdated);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public static boolean matches(int account, int type, long dialogId) {
        if (dialogId >= 0) {
            return false;
        }
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        if (chat == null || ChatObject.isNotInChat(chat)) {
            return false;
        }
        boolean channel = ChatObject.isChannelAndNotMegaGroup(chat);
        boolean admin = !chat.creator && (chat.admin_rights != null);
        switch (type) {
            case ADMIN_GROUPS:
                return !channel && admin;
            case ADMIN_CHANNELS:
                return channel && admin;
            case MY_CHANNELS:
                return channel && chat.creator;
            case MY_GROUPS:
                return !channel && chat.creator;
        }
        return false;
    }

    /** Foydalanuvchiga ko'rinadigan (haqiqiy) papkalar. */
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

    /** Serverga yuboriladigan tartib ro'yxatidan virtual papkalarni olib tashlaydi. */
    public static void cleanOrder(int account, ArrayList<Integer> order) {
        MessagesController mc = MessagesController.getInstance(account);
        for (int i = order.size() - 1; i >= 0; i--) {
            if (isVirtual(mc.dialogFiltersById.get(order.get(i)))) {
                order.remove(i);
            }
        }
    }
}
