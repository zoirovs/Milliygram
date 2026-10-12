package org.telegram.milliy;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.FilterCreateActivity;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Plus uslubidagi papkalar: Barcha chatlar | Shaxsiy | Guruhlar | Kanallar | Botlar.
 * Har bir akkauntda bir marta yaratiladi; foydalanuvchi o'chirsa — qayta yaratilmaydi.
 */
public class MilliyFolders {

    private static final String KEY_DONE = "milliy_folders_done";
    private static final HashSet<Integer> running = new HashSet<>();

    public static void ensure(BaseFragment fragment) {
        final int account = fragment.getCurrentAccount();
        final SharedPreferences prefs = MessagesController.getMainSettings(account);
        final MessagesController mc = MessagesController.getInstance(account);
        if (prefs.getBoolean(KEY_DONE, false) || !mc.dialogFiltersLoaded || running.contains(account)) {
            return;
        }
        final int[] flags = {
                MessagesController.DIALOG_FILTER_FLAG_CONTACTS | MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS,
                MessagesController.DIALOG_FILTER_FLAG_GROUPS,
                MessagesController.DIALOG_FILTER_FLAG_CHANNELS,
                MessagesController.DIALOG_FILTER_FLAG_BOTS,
        };
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
