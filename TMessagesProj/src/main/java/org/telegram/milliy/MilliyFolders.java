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
 * Plus uslubidagi papkalar: [ikonka] | Shaxsiy | Guruhlar | Kanallar | Botlar — doim shu tartibda, ikonkalar bilan.
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

    /** Tablar tartibi (getDialogFilters() indekslari): Barchasi, 4 ta milliy papka, so'ng qolganlari; yashirinlari tashlanadi. */
    public static ArrayList<Integer> tabOrder(int account, ArrayList<MessagesController.DialogFilter> filters) {
        final Set<String> hide = hidden(account);
        ArrayList<Integer> order = new ArrayList<>();
        int[] milliy = {-1, -1, -1, -1};
        ArrayList<Integer> rest = new ArrayList<>();
        int def = -1;
        for (int a = 0; a < filters.size(); a++) {
            MessagesController.DialogFilter f = filters.get(a);
            int t = milliyType(f);
            if (f.isDefault()) {
                def = a;
            } else if (t >= 0 && milliy[t] < 0) {
                milliy[t] = a;
            } else if (!hide.contains(String.valueOf(f.id))) {
                rest.add(a);
            }
        }
        if (def >= 0) {
            order.add(def);
        }
        for (int m : milliy) {
            if (m >= 0) {
                order.add(m);
            }
        }
        order.addAll(rest);
        return order;
    }

    /** Tab sarlavhasi: Barchasi — faqat ikonka, milliy papkalar — ikonka + nom, boshqalar — nomning o'zi. */
    public static CharSequence tabTitle(MessagesController.DialogFilter f, CharSequence name) {
        if (f == null || f.isDefault()) {
            return allTitle();
        }
        int t = milliyType(f);
        if (t < 0) {
            return name;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder("\u2060");
        sb.setSpan(new IconSpan(ICONS[t], dp(5)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(name);
        return sb;
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
            mc.getNotificationCenter().postNotificationName(org.telegram.messenger.NotificationCenter.dialogFiltersUpdated);
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
