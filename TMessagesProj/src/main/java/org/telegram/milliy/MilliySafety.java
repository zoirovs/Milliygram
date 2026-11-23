package org.telegram.milliy;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.Locale;

/** Xavfsizlik bo'limidagi amallar: xotiradagi APK'larni tozalash va akkauntni butunlay o'chirish. */
public class MilliySafety {

    // ---------- APK tozalash ----------

    private static boolean isApkName(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".apkm");
    }

    private static int sweep(File dir, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 3) {
            return 0;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int removed = 0;
        for (File f : files) {
            if (f.isDirectory()) {
                removed += sweep(f, depth + 1);
            } else if (isApkName(f.getName())) {
                try {
                    if (f.delete()) {
                        removed++;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        return removed;
    }

    /** Ilova yuklab olgan papkalardagi APK fayllarini o'chiradi (faqat ilovaning o'z papkalari). Fon oqimida. */
    public static int cleanApks() {
        int removed = 0;
        for (int type : new int[]{FileLoader.MEDIA_DIR_DOCUMENT, FileLoader.MEDIA_DIR_CACHE, FileLoader.MEDIA_DIR_FILES}) {
            try {
                removed += sweep(FileLoader.checkDirectory(type), 0);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return removed;
    }

    public static void cleanApks(BaseFragment fragment) {
        new Thread(() -> {
            final int n = cleanApks();
            AndroidUtilities.runOnUIThread(() -> {
                if (fragment.getParentActivity() == null) {
                    return;
                }
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.ic_delete, n > 0
                        ? LocaleController.formatString(R.string.MilliyApkCleaned, n)
                        : LocaleController.getString(R.string.MilliyApkCleanNothing)).show();
            });
        }, "MilliyApkClean").start();
    }

    // ---------- Akkauntni o'chirish ----------

    /** Ikki bosqichli tasdiq: ogohlantirish → «O'CHIRISH» so'zini yozish → so'rov. */
    public static void deleteAccount(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyDeleteAccTitle));
        b.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyDeleteAccText)));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.setPositiveButton(LocaleController.getString(R.string.MilliyDeleteAccNext), (d, w) -> confirmWord(fragment));
        AlertDialog dialog = b.create();
        fragment.showDialog(dialog);
        red(dialog);
    }

    private static void confirmWord(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final String word = LocaleController.getString(R.string.MilliyDeleteAccWord);
        EditTextBoldCursor input = new EditTextBoldCursor(fragment.getParentActivity());
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setHint(word);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyDeleteAccTitle));
        b.setMessage(LocaleController.formatString(R.string.MilliyDeleteAccType, word));
        android.widget.FrameLayout box = new android.widget.FrameLayout(fragment.getParentActivity());
        box.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, 0, 24, 6, 24, 0));
        b.setView(box);
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        b.setPositiveButton(LocaleController.getString(R.string.MilliyDeleteAccConfirm), null);
        AlertDialog dialog = b.create();
        fragment.showDialog(dialog);
        red(dialog);
        TextView ok = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (ok != null) {
            ok.setOnClickListener(v -> {
                String typed = input.getText() == null ? "" : input.getText().toString().trim();
                if (!typed.equalsIgnoreCase(word)) {
                    AndroidUtilities.shakeViewSpring(input, -6);
                    return;
                }
                dialog.dismiss();
                send(fragment);
            });
        }
    }

    private static void send(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        TL_account.deleteAccount req = new TL_account.deleteAccount();
        req.reason = "Deleted by user from Milliygram";
        fragment.getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                progress.dismiss();
            } catch (Exception ignore) {
            }
            if (err == null) {
                fragment.getMessagesController().performLogout(0);
                return;
            }
            if (fragment.getParentActivity() == null) {
                return;
            }
            String text;
            if (err.text != null && err.text.startsWith("2FA_CONFIRM_WAIT_")) {
                // Ikki bosqichli himoya bor: Telegram bir haftalik kutish qo'yadi, bu vaqtda kirilsa bekor bo'ladi
                text = LocaleController.getString(R.string.MilliyDeleteAccWait);
            } else {
                text = LocaleController.formatString(R.string.MilliyDeleteAccError, TextUtils.isEmpty(err.text) ? "?" : err.text);
            }
            AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
            b.setTitle(LocaleController.getString(R.string.MilliyDeleteAccTitle));
            b.setMessage(AndroidUtilities.replaceTags(text));
            b.setPositiveButton(LocaleController.getString(R.string.OK), null);
            fragment.showDialog(b.create());
        }));
    }

    private static void red(AlertDialog dialog) {
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }
}
