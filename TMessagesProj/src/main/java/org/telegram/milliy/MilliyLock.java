package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/** Chat qulfi va maxfiy seyf: PIN (son yoki matn) + barmoq izi. */
public class MilliyLock {

    public static final String LOCK = "lock", VAULT = "vault";
    private static final long UNLOCK_TTL = 5 * 60_000;
    private static final HashMap<Long, Long> unlocked = new HashMap<>();

    public static boolean isLocked(int account, long dialogId) {
        return MilliyPro.inSet(account, "locked", dialogId);
    }

    public static boolean isVault(int account, long dialogId) {
        return MilliyPro.inSet(account, "vault", dialogId);
    }

    public static boolean hasPin(String kind) {
        return !TextUtils.isEmpty(MilliyPro.prefs().getString(kind + "_pin", null));
    }

    private static String hash(String pin) {
        return Utilities.bytesToHex(Utilities.computeSHA256(("milliy:" + pin).getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean check(String kind, String pin) {
        return hash(pin).equals(MilliyPro.prefs().getString(kind + "_pin", ""));
    }

    public static boolean fingerprint() {
        return MilliyPro.get("lock_fingerprint", true);
    }

    /** Ilova orqa fonga o'tganda hamma chat qayta qulflanadi. */
    public static void relockAll() {
        unlocked.clear();
    }

    private static boolean isUnlocked(long dialogId) {
        Long t = unlocked.get(dialogId);
        return t != null && System.currentTimeMillis() - t < UNLOCK_TTL;
    }

    private static long dialogIdOf(Bundle args) {
        if (args == null) {
            return 0;
        }
        long userId = args.getLong("user_id", 0);
        if (userId != 0) {
            return userId;
        }
        long chatId = args.getLong("chat_id", 0);
        if (chatId != 0) {
            return -chatId;
        }
        int encId = args.getInt("enc_id", 0);
        return encId != 0 ? DialogObject.makeEncryptedDialogId(encId) : 0;
    }

    /** ActionBarLayout.presentFragment boshida chaqiriladi: qulflangan chat ochilsa, avval PIN so'raladi. */
    public static boolean intercept(INavigationLayout layout, INavigationLayout.NavigationParams params) {
        if (!(params.fragment instanceof ChatActivity)) {
            return false;
        }
        BaseFragment last = layout.getLastFragment();
        if (last == null || last.getParentActivity() == null) {
            return false;
        }
        int account = params.fragment.getCurrentAccount();
        long dialogId = dialogIdOf(params.fragment.getArguments());
        if (dialogId == 0 || !isLocked(account, dialogId) || isUnlocked(dialogId) || !hasPin(LOCK)) {
            return false;
        }
        BaseFragment fragment = params.fragment;
        ask(last, LOCK, LocaleController.getString(R.string.MilliyLockAsk), () -> {
            unlocked.put(dialogId, System.currentTimeMillis());
            layout.presentFragment(new INavigationLayout.NavigationParams(fragment));
        });
        return true;
    }

    /** PIN so'rash oynasi; barmoq izi yoqilgan bo'lsa, avval barmoq izi taklif qilinadi. */
    public static void ask(BaseFragment fragment, String kind, String title, Runnable onSuccess) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        EditText edit = pinField(context, LocaleController.getString(R.string.MilliyPinHint));
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(title);
        b.setView(wrap(context, edit));
        b.setPositiveButton(LocaleController.getString(R.string.MilliyPinOpen), (d, w) -> {
            if (check(kind, edit.getText().toString())) {
                onSuccess.run();
            } else {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.MilliyPinWrong)).show();
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = b.create();
        fragment.showDialog(dialog);
        AndroidUtilities.runOnUIThread(() -> {
            edit.requestFocus();
            AndroidUtilities.showKeyboard(edit);
        }, 200);
        if (fingerprint()) {
            biometric(context, () -> {
                dialog.dismiss();
                onSuccess.run();
            });
        }
    }

    private static void biometric(Context context, Runnable onSuccess) {
        if (Build.VERSION.SDK_INT < 23 || LaunchActivity.instance == null) {
            return;
        }
        try {
            if (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
                return;
            }
            BiometricPrompt prompt = new BiometricPrompt(LaunchActivity.instance, ContextCompat.getMainExecutor(context), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    onSuccess.run();
                }
            });
            prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(LocaleController.getString(R.string.MilliyLockAsk))
                    .setNegativeButtonText(LocaleController.getString(R.string.UsePIN))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** Yangi PIN o'rnatish (ikki marta kiritiladi). Kamida 4 belgi. */
    public static void setupPin(BaseFragment fragment, String kind, Runnable onDone) {
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        boolean digits = MilliyPro.get("pin_digits", true);
        EditText p1 = pinField(context, LocaleController.getString(R.string.MilliyPinNew));
        EditText p2 = pinField(context, LocaleController.getString(R.string.MilliyPinRepeat));
        TextView hint = new TextView(context);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        hint.setText(LocaleController.getString(digits ? R.string.MilliyPinDigitsHint : R.string.MilliyPinTextHint));
        box.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));
        box.addView(p1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 24, 0, 24, 8));
        box.addView(p2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 24, 0, 24, 8));
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(LocaleController.getString(VAULT.equals(kind) ? R.string.MilliyVaultPinTitle : R.string.MilliyLockPinTitle));
        b.setView(box);
        b.setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
            String a = p1.getText().toString(), c = p2.getText().toString();
            if (a.length() < 4) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.MilliyPinShort)).show();
            } else if (!a.equals(c)) {
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.MilliyPinMismatch)).show();
            } else {
                MilliyPro.prefs().edit().putString(kind + "_pin", hash(a)).apply();
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.MilliyPinSaved)).show();
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
    }

    private static EditText pinField(Context context, String hint) {
        EditText e = new EditText(context);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        e.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        e.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        e.setHint(hint);
        e.setSingleLine(true);
        e.setGravity(Gravity.CENTER);
        e.setBackground(Theme.createEditTextDrawable(context, true));
        boolean digits = MilliyPro.get("pin_digits", true);
        e.setInputType(digits ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private static LinearLayout wrap(Context context, EditText edit) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 24, 4, 24, 8));
        return box;
    }

    // ---- Chat menyusidagi amallar ----
    public static void toggleLock(BaseFragment fragment, long dialogId) {
        int account = fragment.getCurrentAccount();
        if (!hasPin(LOCK)) {
            setupPin(fragment, LOCK, () -> toggleLock(fragment, dialogId));
            return;
        }
        boolean lock = !isLocked(account, dialogId);
        MilliyPro.toggleSet(account, "locked", dialogId, lock);
        if (lock) {
            unlocked.put(dialogId, System.currentTimeMillis());
        }
        BulletinFactory.of(fragment).createSimpleBulletin(lock ? R.raw.passcode_lock_close : R.raw.contact_check,
                LocaleController.getString(lock ? R.string.MilliyLockDone : R.string.MilliyUnlockDone)).show();
    }

    public static void toggleVault(BaseFragment fragment, long dialogId) {
        int account = fragment.getCurrentAccount();
        if (!hasPin(VAULT)) {
            setupPin(fragment, VAULT, () -> toggleVault(fragment, dialogId));
            return;
        }
        boolean add = !isVault(account, dialogId);
        MilliyPro.toggleSet(account, "vault", dialogId, add);
        if (add) {
            // seyfdagi chat bildirishnomalari yashirinligini buzmasin
            NotificationsController.getInstance(account).muteDialog(dialogId, 0, true);
        }
        fragment.getNotificationCenter().postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload);
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                LocaleController.getString(add ? R.string.MilliyVaultAdded : R.string.MilliyVaultRemoved)).show();
    }

    public static void openVault(BaseFragment fragment) {
        if (!hasPin(VAULT)) {
            setupPin(fragment, VAULT, () -> fragment.presentFragment(new MilliyChatsActivity(MilliyChatsActivity.VAULT)));
            return;
        }
        ask(fragment, VAULT, LocaleController.getString(R.string.MilliyVault), () -> fragment.presentFragment(new MilliyChatsActivity(MilliyChatsActivity.VAULT)));
    }
}
