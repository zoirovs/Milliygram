package org.telegram.milliy;

import android.app.Activity;
import android.os.Bundle;
import android.content.DialogInterface;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.messenger.SendMessagesHelper;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/**
 * Firibgarlikka qarshi: APK faqat ZIRH bot tekshirib qaytargan bo'lsa o'rnatiladi, firibgarlik shikoyati (ZIRH ko'rib chiqadi).
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

    /** ZIRH bot qabul qiladigan eng katta fayl (Bot API limiti). */
    private static final long ZIRH_MAX_FILE = 20L * 1024 * 1024;

    public static boolean isApk(String fileName, String mime) {
        return fileName != null && fileName.toLowerCase(Locale.US).endsWith(".apk") || "application/vnd.android.package-archive".equals(mime);
    }

    public static boolean isApk(MessageObject m) {
        return m != null && m.getDocument() != null && isApk(FileLoader.getDocumentFileName(m.getDocument()), m.getDocument().mime_type);
    }

    private static String approveKey(File f) {
        return f.getAbsolutePath() + ":" + f.length() + ":" + f.lastModified();
    }

    /** ZIRH bot o'z chatida yuborgan fayl — u tekshirib, xavfsiz deb qaytargan nusxa. */
    public static boolean isFromZirh(MessageObject m) {
        long zirh = MilliyService.getZirhBotId();
        return m != null && zirh != 0 && !m.isOutOwner() && m.getDialogId() == zirh && m.getFromChatId() == zirh;
    }

    /**
     * Xabardagi APK'ni ochishdan oldin. true — ushlab qolindi. ZIRH qaytargan faylga ogohlantirish bilan ruxsat beriladi,
     * boshqa chatlardagi APK ochilmaydi: «ZIRH'da tekshirish» taklif qilinadi.
     */
    public static boolean interceptApk(MessageObject m, Activity activity, File f, Runnable proceed) {
        if (activity == null || f == null || !f.exists() || !isApk(m)) {
            return false;
        }
        final String key = approveKey(f);
        if (approved.contains(key)) {
            return false; // fayl darajasidagi tekshiruv bir martalik ruxsatni oladi
        }
        if (!isFromZirh(m)) {
            return blockApkExport(activity, m);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.MilliyApkWarnTitle));
        b.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyApkZirhOk)));
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
        }
        return true;
    }

    /** «Ulashish» va «Yuklanmalarga saqlash»: ZIRH tekshirmagan APK tashqariga chiqarilmaydi. true — to'xtatildi. */
    public static boolean blockApkExport(Activity activity, MessageObject m) {
        if (activity == null || !isApk(m) || isFromZirh(m)) {
            return false;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.MilliyApkBlockedTitle));
        b.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyApkBlockedText)));
        b.setPositiveButton(LocaleController.getString(R.string.MilliyApkCheck), (d, w) -> sendToZirh(m));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        try {
            b.show();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    /**
     * Xabarsiz yo'llar (kesh, maqola, «Yuklanganlar»): APK faqat ZIRH qaytargan xabar orqali ochiladi. true — ushlab qolindi.
     */
    public static boolean interceptApk(Activity activity, File f, String fileName, String mime, Runnable proceed) {
        if (activity == null || f == null || !f.exists() || !isApk(fileName != null ? fileName : f.getName(), mime)) {
            return false;
        }
        if (approved.remove(approveKey(f))) {
            return false;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(LocaleController.getString(R.string.MilliyApkBlockedTitle));
        b.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyApkBlockedNoChat)));
        b.setPositiveButton(LocaleController.getString(R.string.OK), null);
        try {
            b.show();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    /** APK'ni ZIRH botga yuboradi (forward mumkin bo'lmasa — nusxasini yuklaydi) va bot chatini ochadi. */
    public static void sendToZirh(MessageObject m) {
        if (m == null || m.getDocument() == null) {
            return;
        }
        final int account = m.currentAccount;
        final BaseFragment fragment = org.telegram.ui.LaunchActivity.getLastFragment();
        if (m.getDocument().size > ZIRH_MAX_FILE) {
            if (fragment != null) {
                org.telegram.ui.Components.BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.MilliyApkTooBig)).show();
            }
            return;
        }
        MessagesController.getInstance(account).getUserNameResolver().resolve(MilliyService.getZirhBotUsername(), peerId -> {
            if (peerId == null || peerId <= 0) {
                if (fragment != null) {
                    org.telegram.ui.Components.BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.MilliyNetworkError)).show();
                }
                return;
            }
            final MessagesController mc = MessagesController.getInstance(account);
            final boolean canForward = m.canForwardMessage() && !mc.isChatNoForwards(-m.getDialogId()) && !(m.getDialogId() > 0 && mc.isUserNoForwards(m.getDialogId()));
            if (canForward) {
                ArrayList<MessageObject> list = new ArrayList<>();
                list.add(m);
                SendMessagesHelper.getInstance(account).sendMessage(list, peerId, true, false, true, 0, 0);
            } else {
                uploadCopy(account, m, peerId);
            }
            Bundle args = new Bundle();
            args.putLong("user_id", peerId);
            BaseFragment last = org.telegram.ui.LaunchActivity.getLastFragment();
            if (last != null) {
                last.presentFragment(new org.telegram.ui.ChatActivity(args));
            }
        });
    }

    /** Forward taqiqlangan chatdan: yuklangan faylni asl nomi bilan nusxalab, botga hujjat sifatida yuboradi. */
    private static void uploadCopy(int account, MessageObject m, long peerId) {
        File src = FileLoader.getInstance(account).getPathToMessage(m.messageOwner);
        if (m.messageOwner.attachPath != null && !m.messageOwner.attachPath.isEmpty() && new File(m.messageOwner.attachPath).exists()) {
            src = new File(m.messageOwner.attachPath);
        }
        final File from = src;
        String name = FileLoader.getDocumentFileName(m.getDocument());
        if (name == null || !name.toLowerCase(Locale.US).endsWith(".apk")) {
            name = "app.apk";
        }
        final File dir = new File(AndroidUtilities.getCacheDir(), "zirh_" + System.currentTimeMillis());
        final File to = new File(dir, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
        Utilities.globalQueue.postRunnable(() -> {
            try {
                dir.mkdirs();
                AndroidUtilities.copyFile(from, to);
            } catch (Exception e) {
                FileLog.e(e);
                return;
            }
            AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingDocument(AccountInstance.getInstance(account), to.getAbsolutePath(), to.getAbsolutePath(),
                    null, null, "application/vnd.android.package-archive", peerId, null, null, null, null, null, true, 0, null, SendMessageChatArguments.EMPTY, false));
        });
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
