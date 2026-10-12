package org.telegram.milliy;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Milliygram: 2FA majburiy. Akkauntda ikki bosqichli parol bo'lmasa,
 * MilliyTwoFaRequiredActivity ochiladi va parol qo'yilmaguncha yopilmaydi.
 */
public class MilliyTwoFa {

    private static final boolean[] verified = new boolean[UserConfig.MAX_ACCOUNT_COUNT];
    private static final boolean[] checking = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    public static void check(BaseFragment fragment) {
        final int account = fragment.getCurrentAccount();
        if (verified[account] || checking[account] || !UserConfig.getInstance(account).isClientActivated()) {
            return;
        }
        checking[account] = true;
        ConnectionsManager.getInstance(account).sendRequest(new TL_account.getPassword(), (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            checking[account] = false;
            if (!(response instanceof TL_account.Password)) {
                return; // tarmoq xatosi — keyingi onResume'da qayta tekshiriladi
            }
            TL_account.Password password = (TL_account.Password) response;
            if (password.has_password) {
                verified[account] = true;
                return;
            }
            if (fragment.getParentLayout() == null || fragment.getParentLayout().getLastFragment() instanceof MilliyTwoFaRequiredActivity) {
                return;
            }
            fragment.presentFragment(new MilliyTwoFaRequiredActivity(password));
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    static void markVerified(int account) {
        verified[account] = true;
    }
}
