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
 * Admin guruhlar, Admin kanallar (yaratgan yoki admin bo'lgan), Tanlanganlar (foydalanuvchi belgilagan chatlar), Superguruhlar
 * va foydalanuvchi kategoriyalari.
 * Bular faqat shu ilovadagi «virtual» jildlar — serverga yuborilmaydi, limitga kirmaydi.
 */
public class MilliyAdminFolders {

    public static final int ADMIN_GROUPS = 0, ADMIN_CHANNELS = 1, FAVORITES = 2, SUPERGROUPS = 3;
    public static final int COUNT = 4;
    private static final int BASE_ID = 250;
    /** Foydalanuvchi kategoriyalari: tur = CATEGORY_BASE + kategoriya raqami, jild id = CATEGORY_ID_BASE + raqam. */
    public static final int CATEGORY_BASE = 100;
    private static final int CATEGORY_ID_BASE = 400;
    public static final int[] NAMES = {R.string.MilliyAdminGroups, R.string.MilliyAdminChannels, R.string.MilliyFavorites, R.string.MilliySupergroups};
    public static final int[] ICONS = {R.drawable.msg_folders_groups, R.drawable.msg_folders_channels, R.drawable.msg_fave, R.drawable.msg_groups};
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
        // Kategoriyalar (bo'sh bo'lmaganlari)
        int account = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (MessagesController.getInstance(a) == mc) {
                account = a;
                break;
            }
        }
        for (Category c : categories(account)) {
            int id = CATEGORY_ID_BASE + c.id;
            if (c.chats.isEmpty() || mc.dialogFiltersById.get(id) != null) {
                continue;
            }
            MessagesController.DialogFilter f = new MessagesController.DialogFilter();
            f.id = id;
            f.milliyAdmin = CATEGORY_BASE + c.id;
            f.name = c.name;
            f.color = -1;
            f.order = 200000 + c.id;
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
        if (type >= CATEGORY_BASE) {
            Category c = category(account, type - CATEGORY_BASE);
            return c != null && c.chats.contains(dialogId);
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
            case SUPERGROUPS:
                return ChatObject.isMegagroup(chat);
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

    // ---------- Kategoriyalar (mahalliy, cheksiz, serverga yuborilmaydi) ----------

    public static class Category {
        public int id;
        public String name;
        public final HashSet<Long> chats = new HashSet<>();
    }

    private static final String KEY_CATS = "milliy_categories";
    private static final ArrayList<Category>[] cats = new ArrayList[UserConfig.MAX_ACCOUNT_COUNT];

    public static ArrayList<Category> categories(int account) {
        if (cats[account] == null) {
            ArrayList<Category> list = new ArrayList<>();
            try {
                org.json.JSONArray arr = new org.json.JSONArray(MessagesController.getMainSettings(account).getString(KEY_CATS, "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    Category c = new Category();
                    c.id = o.getInt("id");
                    c.name = o.getString("name");
                    org.json.JSONArray ch = o.optJSONArray("chats");
                    for (int j = 0; ch != null && j < ch.length(); j++) {
                        c.chats.add(ch.getLong(j));
                    }
                    list.add(c);
                }
            } catch (Exception e) {
                org.telegram.messenger.FileLog.e(e);
            }
            cats[account] = list;
        }
        return cats[account];
    }

    private static Category category(int account, int id) {
        for (Category c : categories(account)) {
            if (c.id == id) {
                return c;
            }
        }
        return null;
    }

    public static void saveCategories(int account) {
        org.json.JSONArray arr = new org.json.JSONArray();
        try {
            for (Category c : categories(account)) {
                org.json.JSONArray ch = new org.json.JSONArray();
                for (Long d : c.chats) {
                    ch.put(d);
                }
                arr.put(new org.json.JSONObject().put("id", c.id).put("name", c.name).put("chats", ch));
            }
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e(e);
        }
        MessagesController.getMainSettings(account).edit().putString(KEY_CATS, arr.toString()).apply();
        apply(account);
    }

    public static Category createCategory(int account, String name) {
        int next = 0;
        for (Category c : categories(account)) {
            next = Math.max(next, c.id + 1);
        }
        Category c = new Category();
        c.id = next;
        c.name = name;
        categories(account).add(c);
        return c;
    }

    public static void deleteCategory(int account, Category c) {
        categories(account).remove(c);
        saveCategories(account);
    }

    /** Tanlangan chatlarni kategoriyaga qo'shish (yoki yangi kategoriya yaratish). */
    public static void showAddToCategory(org.telegram.ui.ActionBar.BaseFragment fragment, ArrayList<Long> dialogIds) {
        final int account = fragment.getCurrentAccount();
        ArrayList<Category> list = categories(account);
        CharSequence[] items = new CharSequence[list.size() + 1];
        for (int i = 0; i < list.size(); i++) {
            Category c = list.get(i);
            items[i] = (c.chats.containsAll(dialogIds) ? "✓ " : "") + c.name;
        }
        items[list.size()] = "➕ " + LocaleController.getString(R.string.MilliyCategoryNew);
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyCategoryAdd));
        b.setItems(items, (d, w) -> {
            if (w == list.size()) {
                askName(fragment, null, name -> {
                    Category c = createCategory(account, name);
                    c.chats.addAll(dialogIds);
                    saveCategories(account);
                    added(fragment, c.name);
                });
                return;
            }
            Category c = list.get(w);
            if (c.chats.containsAll(dialogIds)) {
                c.chats.removeAll(dialogIds);
                saveCategories(account);
                org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                        LocaleController.formatString(R.string.MilliyCategoryRemoved, c.name)).show();
            } else {
                c.chats.addAll(dialogIds);
                saveCategories(account);
                added(fragment, c.name);
            }
        });
        fragment.showDialog(b.create());
    }

    private static void added(org.telegram.ui.ActionBar.BaseFragment fragment, String name) {
        org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                LocaleController.formatString(R.string.MilliyCategoryAdded, name)).show();
    }

    public static void askName(org.telegram.ui.ActionBar.BaseFragment fragment, String current, org.telegram.messenger.Utilities.Callback<String> done) {
        android.widget.EditText edit = new android.widget.EditText(fragment.getParentActivity());
        edit.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        edit.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        edit.setHintTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextGray3));
        edit.setHint(LocaleController.getString(R.string.MilliyCategoryNameHint));
        edit.setSingleLine(true);
        edit.setBackground(org.telegram.ui.ActionBar.Theme.createEditTextDrawable(fragment.getParentActivity(), true));
        if (current != null) {
            edit.setText(current);
        }
        android.widget.LinearLayout box = new android.widget.LinearLayout(fragment.getParentActivity());
        box.addView(edit, org.telegram.ui.Components.LayoutHelper.createLinear(org.telegram.ui.Components.LayoutHelper.MATCH_PARENT, org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, 24, 4, 24, 8));
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyCategoryNew));
        b.setView(box);
        b.setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
            String t = edit.getText().toString().trim();
            if (!t.isEmpty()) {
                done.run(t.length() > 24 ? t.substring(0, 24) : t);
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
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
