package org.telegram.milliy;

import android.app.Activity;
import android.content.DialogInterface;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;

/**
 * Firibgarlikka qarshi: APK ochishdan oldin ogohlantirish va firibgarlik shikoyati (ZIRH ko'rib chiqadi).
 * Havola va APK'ni avtomatik tekshirish — mustaqil tekshiruv tizimi tayyor bo'lgach qo'shiladi.
 */
public class MilliyShield {

    private static final HashSet<String> approved = new HashSet<>();

    // ---------- HTTP ----------

    interface Result {
        void run(int code, String body);
    }

    static void http(String method, String path, byte[] body, String contentType, int timeoutMs, Result result) {
        Utilities.globalQueue.postRunnable(() -> {
            int code = 0;
            String text = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(MilliyData.API + path).openConnection();
                c.setRequestMethod(method);
                c.setConnectTimeout(10000);
                c.setReadTimeout(timeoutMs);
                c.setRequestProperty("User-Agent", "Milliygram/" + BuildVars.BUILD_VERSION_STRING);
                String district = MilliyData.getDistrict(UserConfig.selectedAccount);
                if (district != null) {
                    c.setRequestProperty("X-Milliy-District", district);
                }
                if (body != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", contentType);
                    c.setFixedLengthStreamingMode(body.length);
                    try (OutputStream out = c.getOutputStream()) {
                        out.write(body);
                    }
                }
                code = c.getResponseCode();
                try (InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream()) {
                    if (in != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                        text = out.toString("UTF-8");
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (c != null) {
                    c.disconnect();
                }
            }
            final int fc = code;
            final String ft = text;
            AndroidUtilities.runOnUIThread(() -> result.run(fc, ft));
        });
    }

    // ---------- APK ----------

    public static boolean isApk(String fileName, String mime) {
        return fileName != null && fileName.toLowerCase(Locale.US).endsWith(".apk") || "application/vnd.android.package-archive".equals(mime);
    }

    /**
     * APK ochilishidan oldin ogohlantirish (mustaqil tekshiruv tizimi tayyor bo'lguncha). true — ushlab qolindi;
     * foydalanuvchi rozi bo'lsa proceed chaqiriladi va u qayta shu metodga kelganda bir martalik ruxsat bilan o'tadi.
     */
    public static boolean interceptApk(Activity activity, File f, String fileName, String mime, Runnable proceed) {
        if (activity == null || f == null || !f.exists() || !isApk(fileName != null ? fileName : f.getName(), mime)) {
            return false;
        }
        final String key = f.getAbsolutePath() + ":" + f.length() + ":" + f.lastModified();
        if (approved.remove(key)) {
            return false;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.MilliyApkWarnTitle));
        b.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyApkWarnText)));
        b.setPositiveButton(LocaleController.getString(R.string.Cancel), null);
        b.setNegativeButton(LocaleController.getString(R.string.MilliyInstallAnyway), (d, w) -> {
            approved.add(key);
            proceed.run();
        });
        b.makeRed(DialogInterface.BUTTON_NEGATIVE);
        try {
            b.show();
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
        return true;
    }

    // ---------- Shikoyat ----------

    private static final String[] REASON_KEYS = {"fraud", "money", "code", "fake", "spam", "other"};
    private static final int[] REASON_TEXTS = {R.string.MilliyReportFraud, R.string.MilliyReportMoney, R.string.MilliyReportCode,
            R.string.MilliyReportFake, R.string.MilliyReportSpam, R.string.MilliyReportOther};

    /** Bot API ko'rinishidagi ID: foydalanuvchi musbat, kanal/supergroup -100…, oddiy guruh -id. */
    public static long botApiId(int account, long dialogId) {
        if (dialogId >= 0) {
            return dialogId;
        }
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        return chat != null && ChatObject.isChannel(chat) ? -1000000000000L + dialogId : dialogId;
    }

    /**
     * Firibgarlik shikoyati. dialogId — shikoyat qilinayotgan foydalanuvchi/kanal; message — ixtiyoriy (xabar menyusidan).
     */
    public static void report(BaseFragment fragment, long dialogId, MessageObject message) {
        if (fragment == null || fragment.getParentActivity() == null || dialogId == 0) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        CharSequence[] items = new CharSequence[REASON_TEXTS.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = LocaleController.getString(REASON_TEXTS[i]);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        b.setTitle(LocaleController.getString(R.string.MilliyReport));
        b.setItems(items, (d, which) -> send(fragment, account, dialogId, message, REASON_KEYS[which]));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
    }

    private static void send(BaseFragment fragment, int account, long dialogId, MessageObject message, String reason) {
        final MessagesController mc = MessagesController.getInstance(account);
        String username = null, title = null;
        if (dialogId > 0) {
            TLRPC.User u = mc.getUser(dialogId);
            if (u != null) {
                username = UserObject.getPublicUsername(u);
                title = UserObject.getUserName(u);
            }
        } else {
            TLRPC.Chat c = mc.getChat(-dialogId);
            if (c != null) {
                username = ChatObject.getPublicUsername(c);
                title = c.title;
            }
        }
        byte[] body;
        try {
            JSONObject o = new JSONObject()
                    .put("peer_id", botApiId(account, dialogId))
                    .put("username", username == null ? "" : username)
                    .put("title", title == null ? "" : title)
                    .put("reason", reason)
                    .put("reporter_id", UserConfig.getInstance(account).getClientUserId());
            if (message != null && message.messageText != null) {
                String t = message.messageText.toString();
                o.put("message_text", t.length() > 2000 ? t.substring(0, 2000) : t);
            }
            body = o.toString().getBytes("UTF-8");
        } catch (Exception e) {
            return;
        }
        http("POST", "report", body, "application/json", 20000, (code, text) -> {
            if (fragment.getParentActivity() == null) {
                return;
            }
            Toast.makeText(fragment.getParentActivity(), LocaleController.getString(code == 200 ? R.string.MilliyReportSent : R.string.MilliyNetworkError),
                    Toast.LENGTH_LONG).show();
        });
    }
}
