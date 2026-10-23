package org.telegram.milliy;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegramQRCodeWriter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LoginActivity;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Kirish oynasidagi qo'shimcha usullar: QR kod (boshqa qurilmadagi Telegram/Milliygram'da skanerlanadi) va bot token.
 */
public class MilliyLogin {

    /** auth.importBotAuthorization — Telegram klientida yo'q, shu yerda e'lon qilingan. */
    private static class ImportBotAuthorization extends TLObject {
        static final int constructor = 0x67a3ff2c;

        int flags;
        int api_id;
        String api_hash;
        String bot_auth_token;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.auth_Authorization.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt32(flags);
            stream.writeInt32(api_id);
            stream.writeString(api_hash);
            stream.writeString(bot_auth_token);
        }
    }

    public static boolean isBot(int account) {
        TLRPC.User me = UserConfig.getInstance(account).getCurrentUser();
        return me != null && me.bot;
    }

    // ---------- QR ----------

    private static LoginActivity qrFragment;
    private static AlertDialog qrDialog;
    private static ImageView qrImage;
    private static boolean qrBusy;
    private static final Runnable qrRefresh = MilliyLogin::exportToken;

    public static void showQr(LoginActivity fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        qrFragment = fragment;
        LinearLayout box = new LinearLayout(fragment.getParentActivity());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        qrImage = new ImageView(fragment.getParentActivity());
        qrImage.setBackgroundColor(0xFFFFFFFF);
        int pad = AndroidUtilities.dp(10);
        qrImage.setPadding(pad, pad, pad, pad);
        box.addView(qrImage, LayoutHelper.createLinear(240, 240, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 12));
        TextView hint = new TextView(fragment.getParentActivity());
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        hint.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        hint.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyQrHint)));
        box.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyQrLogin));
        b.setView(box);
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        qrDialog = b.create();
        fragment.showDialog(qrDialog, d -> stopQr());
        exportToken();
    }

    private static void stopQr() {
        AndroidUtilities.cancelRunOnUIThread(qrRefresh);
        qrFragment = null;
        qrDialog = null;
        qrImage = null;
        qrBusy = false;
    }

    /** ConnectionsManager: QR boshqa qurilmada tasdiqlanganda server updateLoginToken yuboradi. */
    public static void onLoginTokenUpdate(int account) {
        AndroidUtilities.runOnUIThread(() -> {
            if (qrFragment != null && qrFragment.getCurrentAccount() == account) {
                exportToken();
            }
        });
    }

    private static ArrayList<Long> loggedInIds() {
        ArrayList<Long> ids = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig uc = UserConfig.getInstance(a);
            if (uc.isClientActivated()) {
                ids.add(uc.getClientUserId());
            }
        }
        return ids;
    }

    private static void exportToken() {
        final LoginActivity fragment = qrFragment;
        if (fragment == null || qrBusy) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(qrRefresh);
        qrBusy = true;
        final int account = fragment.getCurrentAccount();
        TLRPC.TL_auth_exportLoginToken req = new TLRPC.TL_auth_exportLoginToken();
        req.api_id = BuildVars.APP_ID;
        req.api_hash = BuildVars.APP_HASH;
        req.except_ids = loggedInIds();
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            qrBusy = false;
            if (qrFragment != fragment) {
                return;
            }
            onTokenResponse(fragment, account, response, error);
        }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private static void onTokenResponse(LoginActivity fragment, int account, TLObject response, TLRPC.TL_error error) {
        if (error != null) {
            if (error.text != null && error.text.contains("SESSION_PASSWORD_NEEDED")) {
                dismissQr();
                fragment.milliyPasswordNeeded();
            } else {
                AndroidUtilities.runOnUIThread(qrRefresh, 5000);
            }
            return;
        }
        if (response instanceof TLRPC.TL_auth_loginToken) {
            TLRPC.TL_auth_loginToken t = (TLRPC.TL_auth_loginToken) response;
            showToken(t.token);
            int now = ConnectionsManager.getInstance(account).getCurrentTime();
            long delay = Math.max(5, Math.min(25, t.expires - now)) * 1000L;
            AndroidUtilities.runOnUIThread(qrRefresh, delay);
        } else if (response instanceof TLRPC.TL_auth_loginTokenMigrateTo) {
            TLRPC.TL_auth_loginTokenMigrateTo m = (TLRPC.TL_auth_loginTokenMigrateTo) response;
            ConnectionsManager.getInstance(account).setDefaultDatacenterId(m.dc_id);
            TLRPC.TL_auth_importLoginToken imp = new TLRPC.TL_auth_importLoginToken();
            imp.token = m.token;
            qrBusy = true;
            AndroidUtilities.runOnUIThread(() -> ConnectionsManager.getInstance(account).sendRequest(imp, (r2, e2) -> AndroidUtilities.runOnUIThread(() -> {
                qrBusy = false;
                if (qrFragment == fragment) {
                    onTokenResponse(fragment, account, r2, e2);
                }
            }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors), 1500);
        } else if (response instanceof TLRPC.TL_auth_loginTokenSuccess) {
            TLRPC.auth_Authorization auth = ((TLRPC.TL_auth_loginTokenSuccess) response).authorization;
            if (auth instanceof TLRPC.TL_auth_authorization) {
                dismissQr();
                fragment.milliyAuthSuccess((TLRPC.TL_auth_authorization) auth);
            }
        }
    }

    private static void dismissQr() {
        AlertDialog d = qrDialog;
        stopQr();
        if (d != null) {
            try {
                d.dismiss();
            } catch (Exception ignore) {
            }
        }
    }

    private static void showToken(byte[] token) {
        if (qrImage == null || token == null) {
            return;
        }
        String url = "tg://login?token=" + Base64.encodeToString(token, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        try {
            HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            Bitmap bitmap = new TelegramQRCodeWriter().encode(url, 768, 768, hints, null);
            qrImage.setImageBitmap(bitmap);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ---------- Bot token ----------

    public static void showBotToken(LoginActivity fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        LinearLayout box = new LinearLayout(fragment.getParentActivity());
        box.setOrientation(LinearLayout.VERTICAL);
        TextView hint = new TextView(fragment.getParentActivity());
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hint.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        hint.setText(LocaleController.getString(R.string.MilliyBotTokenHint));
        box.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));
        EditText edit = new EditText(fragment.getParentActivity());
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        edit.setHint("123456789:AAE…");
        edit.setSingleLine(true);
        edit.setBackground(Theme.createEditTextDrawable(fragment.getParentActivity(), true));
        box.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 8));
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyBotTokenLogin));
        b.setView(box);
        b.setPositiveButton(LocaleController.getString(R.string.MilliyBotTokenEnter), (d, w) -> importBot(fragment, edit.getText().toString().trim()));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
    }

    private static void importBot(LoginActivity fragment, String token) {
        if (!token.matches("\\d+:[A-Za-z0-9_-]{30,}")) {
            fragment.milliyShowAlert(LocaleController.getString(R.string.MilliyBotTokenLogin), LocaleController.getString(R.string.MilliyBotTokenInvalid));
            return;
        }
        final int account = fragment.getCurrentAccount();
        ImportBotAuthorization req = new ImportBotAuthorization();
        req.api_id = BuildVars.APP_ID;
        req.api_hash = BuildVars.APP_HASH;
        req.bot_auth_token = token;
        final AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                progress.dismiss();
            } catch (Exception ignore) {
            }
            if (response instanceof TLRPC.TL_auth_authorization) {
                fragment.milliyAuthSuccess((TLRPC.TL_auth_authorization) response);
            } else {
                fragment.milliyShowAlert(LocaleController.getString(R.string.MilliyBotTokenLogin),
                        error != null && error.text != null && error.text.contains("ACCESS_TOKEN")
                                ? LocaleController.getString(R.string.MilliyBotTokenInvalid)
                                : (error != null ? error.text : LocaleController.getString(R.string.MilliyNetworkError)));
            }
        }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
    }
}
