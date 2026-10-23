package org.telegram.milliy;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sozlamalarni zaxiralash va tiklash: Milliygram sozlamalari JSON faylga yoziladi va «Saqlangan xabarlar»ga yuboriladi.
 * Tiklashda fayl tanlanadi. Qulf PIN'i, seyf va akkauntga bog'liq ro'yxatlar zaxiraga kirmaydi.
 */
public class MilliyBackup {

    public static final int REQUEST_CODE = 7703;
    private static final int VERSION = 1;

    private static boolean skip(String key) {
        return key.endsWith("_pin") || key.startsWith("locked_") || key.startsWith("vault_") || key.startsWith("local_pins_")
                || key.startsWith("status_") || key.equals("lite_prev_flags");
    }

    private static JSONObject dump(SharedPreferences p, String prefix) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (skip(k) || prefix != null && !k.startsWith(prefix) || v == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            if (v instanceof Boolean) {
                item.put("t", "b");
            } else if (v instanceof Integer) {
                item.put("t", "i");
            } else if (v instanceof Long) {
                item.put("t", "l");
            } else if (v instanceof Float) {
                item.put("t", "f");
            } else if (v instanceof String) {
                item.put("t", "s");
            } else if (v instanceof Set) {
                item.put("t", "set");
                JSONArray arr = new JSONArray();
                for (Object x : (Set<?>) v) {
                    arr.put(String.valueOf(x));
                }
                v = arr;
            } else {
                continue;
            }
            item.put("v", v);
            o.put(k, item);
        }
        return o;
    }

    private static void load(SharedPreferences p, JSONObject o) throws Exception {
        SharedPreferences.Editor ed = p.edit();
        JSONArray names = o.names();
        for (int i = 0; names != null && i < names.length(); i++) {
            String k = names.getString(i);
            if (skip(k)) {
                continue;
            }
            JSONObject item = o.getJSONObject(k);
            switch (item.optString("t")) {
                case "b":
                    ed.putBoolean(k, item.getBoolean("v"));
                    break;
                case "i":
                    ed.putInt(k, item.getInt("v"));
                    break;
                case "l":
                    ed.putLong(k, item.getLong("v"));
                    break;
                case "f":
                    ed.putFloat(k, (float) item.getDouble("v"));
                    break;
                case "s":
                    ed.putString(k, item.getString("v"));
                    break;
                case "set": {
                    JSONArray arr = item.getJSONArray("v");
                    HashSet<String> set = new HashSet<>();
                    for (int j = 0; j < arr.length(); j++) {
                        set.add(arr.getString(j));
                    }
                    ed.putStringSet(k, set);
                    break;
                }
            }
        }
        ed.apply();
    }

    /** Zaxira fayl yaratib, «Saqlangan xabarlar»ga yuboradi. */
    public static void export(BaseFragment fragment) {
        final int account = fragment.getCurrentAccount();
        try {
            JSONObject root = new JSONObject()
                    .put("app", "Milliygram")
                    .put("version", VERSION)
                    .put("date", System.currentTimeMillis() / 1000)
                    .put("pro", dump(MilliyPro.prefs(), null))
                    .put("main", dump(MessagesController.getMainSettings(account), "milliy_"))
                    .put("lite", LiteMode.getValue(true));
            String name = "milliygram_sozlamalar_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date()) + ".json";
            File dir = new File(AndroidUtilities.getCacheDir(), "milliy_backup");
            dir.mkdirs();
            File file = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(root.toString(1).getBytes("UTF-8"));
            }
            long self = UserConfig.getInstance(account).getClientUserId();
            SendMessagesHelper.prepareSendingDocument(AccountInstance.getInstance(account), file.getAbsolutePath(), file.getAbsolutePath(),
                    null, LocaleController.getString(R.string.MilliyBackupCaption), "application/json", self,
                    null, null, null, null, null, true, 0, null, SendMessageChatArguments.EMPTY, false);
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.MilliyBackupSaved)).show();
        } catch (Exception e) {
            FileLog.e(e);
            BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.MilliyBackupError)).show();
        }
    }

    /** Tiklash uchun faylni tanlash oynasi. */
    public static void pick(BaseFragment fragment) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            fragment.startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.MilliyBackupRestore)), REQUEST_CODE);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void onPicked(BaseFragment fragment, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(data.getData())) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while (in != null && (n = in.read(b)) > 0 && buf.size() < 2 * 1024 * 1024) {
                buf.write(b, 0, n);
            }
            JSONObject root = new JSONObject(buf.toString("UTF-8"));
            if (!"Milliygram".equals(root.optString("app"))) {
                throw new IllegalArgumentException("not a Milliygram backup");
            }
            load(MilliyPro.prefs(), root.getJSONObject("pro"));
            if (root.has("main")) {
                load(MessagesController.getMainSettings(fragment.getCurrentAccount()), root.getJSONObject("main"));
            }
            if (root.has("lite")) {
                LiteMode.setAllFlags(root.getInt("lite"));
            }
            AlertDialog.Builder b2 = new AlertDialog.Builder(fragment.getParentActivity());
            b2.setTitle(LocaleController.getString(R.string.MilliyBackupRestore));
            b2.setMessage(LocaleController.getString(R.string.MilliyBackupRestored));
            b2.setPositiveButton(LocaleController.getString(R.string.OK), null);
            fragment.showDialog(b2.create());
        } catch (Exception e) {
            FileLog.e(e);
            BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.MilliyBackupBadFile)).show();
        }
    }
}
