package org.telegram.milliy;

import android.app.Dialog;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.TwoStepVerificationSetupActivity;

/** 2FA yoqilmaguncha yopilmaydigan ekran. */
public class MilliyTwoFaRequiredActivity extends BaseFragment {

    private TL_account.Password currentPassword;
    private boolean doneShown;

    public MilliyTwoFaRequiredActivity(TL_account.Password password) {
        currentPassword = password;
    }

    @Override
    public View createView(Context context) {
        actionBar.setVisibility(View.GONE);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(AndroidUtilities.dp(28), AndroidUtilities.statusBarHeight + AndroidUtilities.dp(48), AndroidUtilities.dp(28), AndroidUtilities.dp(32));
        scrollView.addView(layout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setAnimation(R.raw.tsv_setup_intro, 140, 140);
        imageView.playAnimation();
        layout.addView(imageView, LayoutHelper.createLinear(140, 140, Gravity.CENTER_HORIZONTAL));

        TextView titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(LocaleController.getString(R.string.MilliyTwoFaTitle));
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setGravity(Gravity.CENTER);
        textView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        textView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyTwoFaText)));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        TextView tipsView = new TextView(context);
        tipsView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        tipsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tipsView.setLineSpacing(AndroidUtilities.dp(4), 1.0f);
        tipsView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        tipsView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_windowBackgroundGray)));
        tipsView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyTwoFaTips)));
        layout.addView(tipsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

        TextView button = new TextView(context);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        button.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 8));
        button.setText(LocaleController.getString(R.string.MilliyTwoFaButton));
        button.setOnClickListener(v -> openSetup());
        layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 28, 0, 0));

        fragmentView = scrollView;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        return fragmentView;
    }

    private void openSetup() {
        int type = currentPassword != null && !TextUtils.isEmpty(currentPassword.email_unconfirmed_pattern)
                ? TwoStepVerificationSetupActivity.TYPE_EMAIL_CONFIRM
                : TwoStepVerificationSetupActivity.TYPE_INTRO;
        presentFragment(new TwoStepVerificationSetupActivity(currentAccount, type, currentPassword));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Sozlash ekranidan qaytganda: parol qo'yildimi?
        getConnectionsManager().sendRequest(new TL_account.getPassword(), (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TL_account.Password)) {
                return;
            }
            currentPassword = (TL_account.Password) response;
            if (currentPassword.has_password && !doneShown) {
                doneShown = true;
                MilliyTwoFa.markVerified(currentAccount);
                showDoneReminder();
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void showDoneReminder() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.MilliyTwoFaDoneTitle));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyTwoFaDoneText)));
        builder.setPositiveButton(LocaleController.getString(R.string.MilliyTwoFaDoneButton), (d, w) -> finishFragment());
        Dialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        showDialog(dialog);
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        return false;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }
}
