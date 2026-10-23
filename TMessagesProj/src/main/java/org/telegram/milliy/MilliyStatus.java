package org.telegram.milliy;

import android.content.SharedPreferences;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

/**
 * «Mening holatim»: tayyor holatlardan birini bir bosishda profil bio'siga qo'yadi (Premium'siz ishlaydi).
 * Asl bio saqlanadi va «Holatni tozalash»da qaytariladi.
 */
public class MilliyStatus {

    private static final int[] PRESETS = {R.string.MilliyStatusFree, R.string.MilliyStatusBusy, R.string.MilliyStatusWork,
            R.string.MilliyStatusSleep, R.string.MilliyStatusRoad};

    private static SharedPreferences prefs() {
        return MilliyPro.prefs();
    }

    public static String current(int account) {
        return prefs().getString("status_text_" + account, null);
    }

    public static void show(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        final String cur = current(account);
        int extra = cur != null ? 2 : 1;
        CharSequence[] items = new CharSequence[PRESETS.length + extra];
        for (int i = 0; i < PRESETS.length; i++) {
            items[i] = LocaleController.getString(PRESETS[i]);
        }
        items[PRESETS.length] = LocaleController.getString(R.string.MilliyStatusCustom);
        if (cur != null) {
            items[PRESETS.length + 1] = LocaleController.getString(R.string.MilliyStatusClear);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(cur != null ? LocaleController.getString(R.string.MilliyStatus) + ": " + cur : LocaleController.getString(R.string.MilliyStatus));
        b.setItems(items, (d, which) -> {
            if (which < PRESETS.length) {
                apply(fragment, account, LocaleController.getString(PRESETS[which]));
            } else if (which == PRESETS.length) {
                custom(fragment, account);
            } else {
                clear(fragment, account);
            }
        });
        fragment.showDialog(b.create());
    }

    private static void custom(BaseFragment fragment, int account) {
        EditText edit = new EditText(fragment.getParentActivity());
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        edit.setHint(LocaleController.getString(R.string.MilliyStatusHint));
        edit.setBackground(Theme.createEditTextDrawable(fragment.getParentActivity(), true));
        edit.setSingleLine(true);
        LinearLayout box = new LinearLayout(fragment.getParentActivity());
        box.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 8));
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyStatusCustom));
        b.setView(box);
        b.setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
            String t = edit.getText().toString().replace("\n", " ").trim();
            if (!t.isEmpty()) {
                apply(fragment, account, t);
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
    }

    private static String bio(int account) {
        TLRPC.UserFull full = MessagesController.getInstance(account).getUserFull(UserConfig.getInstance(account).getClientUserId());
        return full != null && full.about != null ? full.about : null;
    }

    private static void apply(BaseFragment fragment, int account, String status) {
        int limit = MessagesController.getInstance(account).getAboutLimit();
        if (current(account) == null) {
            String original = bio(account);
            if (original == null) {
                // to'liq profil hali yuklanmagan — asl bio'ni yo'qotmaslik uchun avval yuklaymiz
                MessagesController.getInstance(account).loadFullUser(UserConfig.getInstance(account).getCurrentUser(), 0, true);
                BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.MilliyStatusRetry)).show();
                return;
            }
            prefs().edit().putString("status_bio_" + account, original).apply();
        }
        String text = status.length() > limit ? status.substring(0, limit) : status;
        update(fragment, account, text, () -> {
            prefs().edit().putString("status_text_" + account, text).apply();
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, LocaleController.formatString(R.string.MilliyStatusSet, text)).show();
        });
    }

    private static void clear(BaseFragment fragment, int account) {
        String original = prefs().getString("status_bio_" + account, "");
        update(fragment, account, original, () -> {
            prefs().edit().remove("status_text_" + account).remove("status_bio_" + account).apply();
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.MilliyStatusCleared)).show();
        });
    }

    private static void update(BaseFragment fragment, int account, String about, Runnable done) {
        TL_account.updateProfile req = new TL_account.updateProfile();
        req.about = about;
        req.flags |= 4;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.MilliyNetworkError)).show();
                return;
            }
            TLRPC.UserFull full = MessagesController.getInstance(account).getUserFull(UserConfig.getInstance(account).getClientUserId());
            if (full != null) {
                full.about = about;
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.userInfoDidLoad, full.id, full);
            }
            done.run();
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }
}
