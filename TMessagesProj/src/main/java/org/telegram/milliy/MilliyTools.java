package org.telegram.milliy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

/** Mayda qulayliklar: chat topuvchi (ID / @username / havola) va avto-yuklashni bir bosishda o'chirish. */
public class MilliyTools {

    // ---- Chat topuvchi ----
    public static void showFinder(BaseFragment fragment) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        EditText edit = new EditText(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        edit.setHint(LocaleController.getString(R.string.MilliyFinderHint));
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setBackground(Theme.createEditTextDrawable(context, true));
        LinearLayout box = new LinearLayout(context);
        box.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 24, 4, 24, 8));
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(LocaleController.getString(R.string.MilliyFinder));
        b.setView(box);
        b.setPositiveButton(LocaleController.getString(R.string.MilliyFinderGo), (d, w) -> find(fragment, edit.getText().toString()));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
        AndroidUtilities.runOnUIThread(() -> {
            edit.requestFocus();
            AndroidUtilities.showKeyboard(edit);
        }, 200);
    }

    private static void find(BaseFragment fragment, String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.isEmpty()) {
            return;
        }
        MessagesController mc = fragment.getMessagesController();
        // havola yoki @username
        String lower = q.toLowerCase();
        for (String p : new String[]{"https://", "http://", "t.me/", "telegram.me/", "@"}) {
            if (lower.startsWith(p)) {
                q = q.substring(p.length());
                lower = q.toLowerCase();
            }
        }
        int slash = q.indexOf('/');
        if (slash > 0) {
            q = q.substring(0, slash);
        }
        if (q.matches("-?\\d+")) {
            long id;
            try {
                id = Long.parseLong(q);
            } catch (NumberFormatException e) {
                notFound(fragment);
                return;
            }
            Bundle args = new Bundle();
            if (id > 0 && mc.getUser(id) != null) {
                args.putLong("user_id", id);
            } else {
                long cid = Math.abs(id);
                String s = String.valueOf(cid);
                if (s.startsWith("100") && s.length() > 10 && mc.getChat(cid) == null) {
                    cid = Long.parseLong(s.substring(3)); // -100XXXXXXXXXX ko'rinishi
                }
                TLRPC.Chat chat = mc.getChat(cid);
                if (chat == null) {
                    notFound(fragment);
                    return;
                }
                args.putLong("chat_id", cid);
            }
            fragment.presentFragment(new ChatActivity(args));
            return;
        }
        if (TextUtils.isEmpty(q) || !q.matches("[A-Za-z0-9_]{3,}")) {
            notFound(fragment);
            return;
        }
        mc.openByUserName(q, fragment, 0);
    }

    private static void notFound(BaseFragment fragment) {
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.MilliyFinderNotFound)).show();
    }

    // ---- Avto-yuklash ----
    public static boolean autoDownloadOn(int account) {
        DownloadController dc = DownloadController.getInstance(account);
        return dc.mobilePreset.enabled || dc.wifiPreset.enabled || dc.roamingPreset.enabled;
    }

    public static void setAutoDownload(int account, boolean on) {
        DownloadController dc = DownloadController.getInstance(account);
        dc.mobilePreset.enabled = on;
        dc.wifiPreset.enabled = on;
        dc.roamingPreset.enabled = on;
        SharedPreferences.Editor e = MessagesController.getMainSettings(account).edit();
        e.putString("mobilePreset", dc.mobilePreset.toString());
        e.putString("wifiPreset", dc.wifiPreset.toString());
        e.putString("roamingPreset", dc.roamingPreset.toString());
        e.apply();
        dc.checkAutodownloadSettings();
        for (int t = 0; t < 3; t++) {
            dc.savePresetToServer(t);
        }
    }

    public static void toggleAutoDownload(BaseFragment fragment) {
        int account = fragment.getCurrentAccount();
        boolean on = !autoDownloadOn(account);
        setAutoDownload(account, on);
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                LocaleController.getString(on ? R.string.MilliyAutoDlOnDone : R.string.MilliyAutoDlOffDone)).show();
    }
}
