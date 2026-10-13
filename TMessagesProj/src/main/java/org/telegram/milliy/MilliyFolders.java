package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.FilterCreateActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Plus uslubidagi jildlar: Barcha | Shaxsiy | Guruhlar | Kanallar | Botlar — birinchi kirishda yaratiladi.
 * Tablarda faqat ikonka (nomi Sozlamalar → Chat jildlari'da ko'rinadi); tartibni foydalanuvchi o'sha yerda o'zgartiradi.
 * Har bir akkauntda bir marta yaratiladi; foydalanuvchi o'chirsa — qayta yaratilmaydi.
 * Milliygram o'rnatilgunga qadar boshqa klientlarda yaratilgan papkalar faqat bu ilovada yashiriladi (serverda qoladi);
 * foydalanuvchi ularni «Jildlar» bo'limidan tahrirlab saqlasa, yana ko'rinadi. Keyin yaratilganlari oxirida chiqadi.
 */
public class MilliyFolders {

    private static final String KEY_DONE = "milliy_folders_done";
    private static final String KEY_HIDDEN = "milliy_folders_hidden";
    private static final String KEY_HIDDEN_DONE = "milliy_folders_hidden_done";

    private static final int[] FLAGS = {
            MessagesController.DIALOG_FILTER_FLAG_CONTACTS | MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS,
            MessagesController.DIALOG_FILTER_FLAG_GROUPS,
            MessagesController.DIALOG_FILTER_FLAG_CHANNELS,
            MessagesController.DIALOG_FILTER_FLAG_BOTS,
    };
    private static final int[] ICONS = {R.drawable.msg_folders_private, R.drawable.msg_folders_groups, R.drawable.msg_folders_channels, R.drawable.msg_folders_bots};
    private static final int ICON_ALL = R.drawable.msg_limit_chats;
    private static final int ICON_DEFAULT = R.drawable.msg_folders;

    /** Foydalanuvchi tanlay oladigan jild ikonkalari. */
    public static final int[] ICON_CHOICES = {
            R.drawable.msg_folders_private, R.drawable.msg_folders_groups, R.drawable.msg_folders_channels, R.drawable.msg_folders_bots,
            R.drawable.msg_folders_read, R.drawable.msg_folders_muted, R.drawable.msg_folders_archive, R.drawable.msg_fave,
            R.drawable.msg_home, R.drawable.msg_work, R.drawable.msg_payment_card, R.drawable.msg_media,
            R.drawable.msg_gift_premium, R.drawable.msg_calendar2, R.drawable.msg_location, R.drawable.msg_message,
            R.drawable.msg_emoji_activities, R.drawable.msg_emoji_travel, R.drawable.msg_emoji_flags, R.drawable.msg_emoji_cat,
    };
    private static final int[] ICON_NAMES = {
            R.string.MilliyIcon0, R.string.MilliyIcon1, R.string.MilliyIcon2, R.string.MilliyIcon3, R.string.MilliyIcon4,
            R.string.MilliyIcon5, R.string.MilliyIcon6, R.string.MilliyIcon7, R.string.MilliyIcon8, R.string.MilliyIcon9,
            R.string.MilliyIcon10, R.string.MilliyIcon11, R.string.MilliyIcon12, R.string.MilliyIcon13, R.string.MilliyIcon14,
            R.string.MilliyIcon15, R.string.MilliyIcon16, R.string.MilliyIcon17, R.string.MilliyIcon18, R.string.MilliyIcon19,
    };

    private static int customIcon(int account, int filterId) {
        return MessagesController.getMainSettings(account).getInt("milliy_folder_icon_" + filterId, -1);
    }

    /** Jild ikonkasi: tanlangani, bo'lmasa milliy/virtual jild ikonkasi, bo'lmasa umumiy jild ikonkasi. */
    public static int iconFor(int account, MessagesController.DialogFilter f) {
        int c = customIcon(account, f.id);
        if (c >= 0 && c < ICON_CHOICES.length) {
            return ICON_CHOICES[c];
        }
        int t = milliyType(f);
        if (t >= 0) {
            return ICONS[t];
        }
        if (f.milliyAdmin >= 0 && f.milliyAdmin < MilliyAdminFolders.ICONS.length) {
            return MilliyAdminFolders.ICONS[f.milliyAdmin];
        }
        return ICON_DEFAULT;
    }

    public static void chooseIcon(BaseFragment fragment, MessagesController.DialogFilter f) {
        if (fragment.getParentActivity() == null || f == null) {
            return;
        }
        int account = fragment.getCurrentAccount();
        CharSequence[] items = new CharSequence[ICON_CHOICES.length + 1];
        int[] icons = new int[ICON_CHOICES.length + 1];
        for (int i = 0; i < ICON_CHOICES.length; i++) {
            items[i] = LocaleController.getString(ICON_NAMES[i]);
            icons[i] = ICON_CHOICES[i];
        }
        items[ICON_CHOICES.length] = LocaleController.getString(R.string.MilliyIconDefault);
        icons[ICON_CHOICES.length] = R.drawable.msg_reset;
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyFolderIcon));
        b.setItems(items, icons, (d, which) -> {
            SharedPreferences.Editor e = MessagesController.getMainSettings(account).edit();
            if (which >= ICON_CHOICES.length) {
                e.remove("milliy_folder_icon_" + f.id);
            } else {
                e.putInt("milliy_folder_icon_" + f.id, which);
            }
            e.apply();
            org.telegram.messenger.NotificationCenter.getInstance(account).postNotificationName(org.telegram.messenger.NotificationCenter.dialogFiltersUpdated);
        });
        fragment.showDialog(b.create());
    }

    /** Jildni faqat shu ilovada yashirish (serverda qoladi). */
    public static void hide(int account, int filterId) {
        Set<String> set = new HashSet<>(hidden(account));
        set.add(String.valueOf(filterId));
        MessagesController.getMainSettings(account).edit().putStringSet(KEY_HIDDEN, set).apply();
        org.telegram.messenger.NotificationCenter.getInstance(account).postNotificationName(org.telegram.messenger.NotificationCenter.dialogFiltersUpdated);
    }

    public static boolean isHidden(int account, int filterId) {
        return hidden(account).contains(String.valueOf(filterId));
    }

    public static void unhideAndNotify(int account, int filterId) {
        unhide(account, filterId);
        org.telegram.messenger.NotificationCenter.getInstance(account).postNotificationName(org.telegram.messenger.NotificationCenter.dialogFiltersUpdated);
    }

    /** Milliy papka turi (0..3) yoki -1. */
    public static int milliyType(MessagesController.DialogFilter f) {
        if (f == null || f.isDefault() || !f.alwaysShow.isEmpty() || !f.neverShow.isEmpty()) {
            return -1;
        }
        int all = f.flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS;
        for (int i = 0; i < FLAGS.length; i++) {
            if (all == FLAGS[i]) {
                return i;
            }
        }
        return -1;
    }

    private static Set<String> hidden(int account) {
        return MessagesController.getMainSettings(account).getStringSet(KEY_HIDDEN, new HashSet<>());
    }

    /** Foydalanuvchi papkani tahrirlab saqlasa — u yana ko'rinadi. */
    public static void unhide(int account, int filterId) {
        Set<String> set = new HashSet<>(hidden(account));
        if (set.remove(String.valueOf(filterId))) {
            MessagesController.getMainSettings(account).edit().putStringSet(KEY_HIDDEN, set).apply();
        }
    }

    /** Tablar tartibi (getDialogFilters() indekslari): foydalanuvchi belgilagan tartibda, yashirinlari tashlanadi. */
    public static ArrayList<Integer> tabOrder(int account, ArrayList<MessagesController.DialogFilter> filters) {
        final Set<String> hide = hidden(account);
        ArrayList<Integer> order = new ArrayList<>();
        for (int a = 0; a < filters.size(); a++) {
            MessagesController.DialogFilter f = filters.get(a);
            if (f.isDefault() || !hide.contains(String.valueOf(f.id))) {
                order.add(a);
            }
        }
        return order;
    }

    /** Tab sarlavhasi: faqat ikonka (nomsiz). */
    public static CharSequence tabTitle(int account, MessagesController.DialogFilter f, CharSequence name) {
        if (f == null || f.isDefault()) {
            return allTitle();
        }
        SpannableStringBuilder sb = new SpannableStringBuilder("\u2060");
        sb.setSpan(new IconSpan(iconFor(account, f), 0), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** Sozlamalardagi ro'yxat uchun: ikonka + nom. */
    public static CharSequence labelWithIcon(int account, MessagesController.DialogFilter f, CharSequence name) {
        SpannableStringBuilder sb = new SpannableStringBuilder("\u2060");
        sb.setSpan(new IconSpan(f == null || f.isDefault() ? ICON_ALL : iconFor(account, f), dp(8)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(name);
        return sb;
    }

    public static ArrayList<MessagesController.DialogFilter> hiddenFolders(int account) {
        ArrayList<MessagesController.DialogFilter> out = new ArrayList<>();
        for (MessagesController.DialogFilter f : MessagesController.getInstance(account).getDialogFilters()) {
            if (!f.isDefault() && isHidden(account, f.id)) {
                out.add(f);
            }
        }
        return out;
    }

    /** Yashirin jildlar ro'yxati: bosilgani qayta ko'rsatiladi. */
    public static void showHiddenFolders(BaseFragment fragment, Runnable onChange) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        int account = fragment.getCurrentAccount();
        ArrayList<MessagesController.DialogFilter> list = hiddenFolders(account);
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyHiddenFolders));
        if (list.isEmpty()) {
            b.setMessage(LocaleController.getString(R.string.MilliyHiddenFoldersEmpty));
            b.setPositiveButton(LocaleController.getString(R.string.OK), null);
        } else {
            CharSequence[] items = new CharSequence[list.size()];
            for (int i = 0; i < list.size(); i++) {
                items[i] = list.get(i).name;
            }
            b.setItems(items, (d, w) -> {
                unhideAndNotify(account, list.get(w).id);
                if (onChange != null) {
                    onChange.run();
                }
            });
        }
        fragment.showDialog(b.create());
    }

    private static CharSequence allTitle;

    public static CharSequence allTitle() {
        if (allTitle == null) {
            SpannableStringBuilder sb = new SpannableStringBuilder("\u2060");
            sb.setSpan(new IconSpan(ICON_ALL, 0), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            allTitle = sb;
        }
        return allTitle;
    }

    /** Tab matni rangida (tanlangan/tanlanmagan holatga mos) chiziladigan ikonka. */
    public static class IconSpan extends ReplacementSpan {
        private final Drawable drawable;
        private final int gap;
        private int lastColor;

        IconSpan(int res, int gap) {
            drawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, res).mutate();
            this.gap = gap;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return dp(20) + gap;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            final int color = paint.getColor();
            if (color != lastColor) {
                drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                lastColor = color;
            }
            drawable.setAlpha(paint.getAlpha());
            final int size = dp(20), cy = (top + bottom) / 2;
            drawable.setBounds((int) x, cy - size / 2, (int) x + size, cy + size / 2);
            drawable.draw(canvas);
        }
    }
    private static final HashSet<Integer> running = new HashSet<>();

    public static void ensure(BaseFragment fragment) {
        final int account = fragment.getCurrentAccount();
        final SharedPreferences prefs = MessagesController.getMainSettings(account);
        final MessagesController mc = MessagesController.getInstance(account);
        if (!mc.dialogFiltersLoaded || running.contains(account)) {
            return;
        }
        if (!prefs.getBoolean(KEY_HIDDEN_DONE, false)) {
            HashSet<String> hide = new HashSet<>();
            for (MessagesController.DialogFilter f : mc.getDialogFilters()) {
                if (!f.isDefault() && milliyType(f) < 0) {
                    hide.add(String.valueOf(f.id));
                }
            }
            prefs.edit().putStringSet(KEY_HIDDEN, hide).putBoolean(KEY_HIDDEN_DONE, true).apply();
            org.telegram.messenger.NotificationCenter.getInstance(account).postNotificationName(org.telegram.messenger.NotificationCenter.dialogFiltersUpdated);
        }
        if (prefs.getBoolean(KEY_DONE, false)) {
            return;
        }
        final int[] flags = FLAGS;
        final int[] names = {R.string.MilliyFolderPrivate, R.string.MilliyFolderGroups, R.string.MilliyFolderChannels, R.string.MilliyFolderBots};

        ArrayList<Integer> todo = new ArrayList<>();
        for (int i = 0; i < flags.length; i++) {
            boolean exists = false;
            for (MessagesController.DialogFilter f : mc.getDialogFilters()) {
                if ((f.flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) == flags[i] && f.alwaysShow.isEmpty() && f.neverShow.isEmpty()) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                todo.add(i);
            }
        }
        prefs.edit().putBoolean(KEY_DONE, true).apply();
        if (todo.isEmpty()) {
            return;
        }
        running.add(account);
        createNext(fragment, mc, todo, 0, flags, names, account);
    }

    private static void createNext(BaseFragment fragment, MessagesController mc, ArrayList<Integer> todo, int index, int[] flags, int[] names, int account) {
        if (index >= todo.size() || fragment.getParentActivity() == null) {
            running.remove(account);
            return;
        }
        final int i = todo.get(index);
        MessagesController.DialogFilter filter = new MessagesController.DialogFilter();
        filter.id = 2;
        while (mc.dialogFiltersById.get(filter.id) != null) {
            filter.id++;
        }
        filter.name = LocaleController.getString(names[i]);
        filter.color = -1;
        FilterCreateActivity.saveFilterToServer(filter, flags[i], filter.name, new ArrayList<>(), false, -1,
                new ArrayList<>(), new ArrayList<>(), new LongSparseIntArray(), true, false, true, true, false, fragment,
                null);
        AndroidUtilities.runOnUIThread(() -> createNext(fragment, mc, todo, index + 1, flags, names, account), 700);
    }
}
