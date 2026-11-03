package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.PasscodeActivity;
import org.telegram.ui.PrivacyControlActivity;
import org.telegram.ui.SessionsActivity;
import org.telegram.ui.TwoStepVerificationActivity;

import java.util.ArrayList;

/** «Xavfsizlik holati»: akkaunt himoyasi bitta ekranda, har bandga baho va «Tuzatish» tugmasi. */
public class MilliySecurityActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int OK = 0xFF1E9E5A, WARN = 0xFFE08A00, BAD = 0xFFD93025;

    private LinearLayout list;
    private TextView scoreView;
    private TL_account.Password password;
    private int otherSessions = -1, oldSessions;

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.privacyRulesUpdated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.privacyRulesUpdated);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.MilliySecurity));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        scoreView = new TextView(context);
        scoreView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        scoreView.setTypeface(AndroidUtilities.bold());
        scoreView.setGravity(Gravity.CENTER);
        scoreView.setPadding(dp(16), dp(18), dp(16), dp(6));
        root.addView(scoreView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView hint = new TextView(context);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(24), 0, dp(24), dp(14));
        hint.setText(LocaleController.getString(R.string.MilliySecurityHint));
        root.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        root.addView(list, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        fragmentView = scroll;
        render();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        ConnectionsManager cm = getConnectionsManager();
        cm.sendRequest(new TL_account.getPassword(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TL_account.Password) {
                password = (TL_account.Password) res;
                render();
            }
        }));
        cm.sendRequest(new TL_account.getAuthorizations(), (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TL_account.authorizations) {
                int others = 0, old = 0;
                int now = cm.getCurrentTime();
                for (TLRPC.TL_authorization a : ((TL_account.authorizations) res).authorizations) {
                    if (!a.current) {
                        others++;
                        if (now - a.date_active > 30 * 86400) {
                            old++;
                        }
                    }
                }
                otherSessions = others;
                oldSessions = old;
                render();
            }
        }));
        getContactsController().loadPrivacySettings();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.privacyRulesUpdated) {
            render();
        }
    }

    /** Hamma ko'radi/qo'sha oladi bo'lsa true. null — hali yuklanmagan. */
    private Boolean everybody(int type) {
        ArrayList<TLRPC.PrivacyRule> rules = getContactsController().getPrivacyRules(type);
        if (rules == null) {
            return null;
        }
        for (TLRPC.PrivacyRule r : rules) {
            if (r instanceof TLRPC.TL_privacyValueAllowAll) {
                return true;
            }
        }
        return false;
    }

    private void render() {
        if (list == null || getParentActivity() == null) {
            return;
        }
        list.removeAllViews();
        int good = 0, total = 0;

        // 1. 2FA
        Boolean has2fa = password == null ? null : password.has_password;
        total++;
        if (Boolean.TRUE.equals(has2fa)) good++;
        addRow(R.string.MilliySec2fa, has2fa == null ? -1 : has2fa ? OK : BAD,
                has2fa == null ? R.string.MilliyLoading : has2fa ? R.string.MilliySec2faOn : R.string.MilliySec2faOff,
                () -> presentFragment(new TwoStepVerificationActivity()));

        // 2. Tiklash pochtasi
        total++;
        int mailState = password == null ? -1 : !password.has_password ? BAD
                : password.has_recovery ? OK : !TextUtils.isEmpty(password.email_unconfirmed_pattern) ? WARN : BAD;
        if (mailState == OK) good++;
        addRow(R.string.MilliySecEmail, mailState, mailState == -1 ? R.string.MilliyLoading : mailState == OK ? R.string.MilliySecEmailOn
                : mailState == WARN ? R.string.MilliySecEmailUnconfirmed : R.string.MilliySecEmailOff, () -> presentFragment(new TwoStepVerificationActivity()));

        // 3. Seanslar
        total++;
        int sessState = otherSessions < 0 ? -1 : oldSessions > 0 ? WARN : OK;
        if (sessState == OK) good++;
        String sessText = otherSessions < 0 ? LocaleController.getString(R.string.MilliyLoading)
                : otherSessions == 0 ? LocaleController.getString(R.string.MilliySecSessionsNone)
                : oldSessions > 0 ? LocaleController.formatString(R.string.MilliySecSessionsOld, otherSessions, oldSessions)
                : LocaleController.formatString(R.string.MilliySecSessions, otherSessions);
        addRow(LocaleController.getString(R.string.MilliySecSessionsTitle), sessState, sessText, () -> presentFragment(new SessionsActivity(0)));

        // 4. Telefon raqami kimga ko'rinadi
        total++;
        Boolean phoneAll = everybody(ContactsController.PRIVACY_RULES_TYPE_PHONE);
        if (Boolean.FALSE.equals(phoneAll)) good++;
        addRow(R.string.MilliySecPhone, phoneAll == null ? -1 : phoneAll ? WARN : OK,
                phoneAll == null ? R.string.MilliyLoading : phoneAll ? R.string.MilliySecPhoneAll : R.string.MilliySecPhoneLimited,
                () -> presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHONE)));

        // 5. Guruhlarga kim qo'sha oladi (spam guruhlar)
        total++;
        Boolean inviteAll = everybody(ContactsController.PRIVACY_RULES_TYPE_INVITE);
        if (Boolean.FALSE.equals(inviteAll)) good++;
        addRow(R.string.MilliySecInvite, inviteAll == null ? -1 : inviteAll ? WARN : OK,
                inviteAll == null ? R.string.MilliyLoading : inviteAll ? R.string.MilliySecInviteAll : R.string.MilliySecInviteLimited,
                () -> presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_INVITE)));

        // 6. Ilova kod-paroli
        total++;
        boolean passcode = SharedConfig.passcodeHash.length() > 0;
        if (passcode) good++;
        addRow(R.string.MilliySecPasscode, passcode ? OK : WARN, passcode ? R.string.MilliySecPasscodeOn : R.string.MilliySecPasscodeOff,
                () -> presentFragment(PasscodeActivity.determineOpenFragment()));

        int color = good == total ? OK : good >= total - 2 ? WARN : BAD;
        scoreView.setTextColor(color);
        scoreView.setText(LocaleController.formatString(R.string.MilliySecScore, good, total));
    }

    private void addRow(int title, int state, int value, Runnable fix) {
        addRow(LocaleController.getString(title), state, LocaleController.getString(value), fix);
    }

    private void addRow(String title, int state, String value, Runnable fix) {
        Context context = getParentActivity();
        FrameLayout row = new FrameLayout(context);
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView dot = new TextView(context);
        dot.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        dot.setTypeface(AndroidUtilities.bold());
        dot.setText(state == OK ? "✓" : state == -1 ? "…" : "!");
        dot.setTextColor(state == -1 ? Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2) : state);
        row.addView(dot, LayoutHelper.createFrame(28, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        t.setText(title);
        texts.addView(t);
        TextView v = new TextView(context);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        v.setTextColor(state == OK || state == -1 ? Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2) : state);
        v.setText(value);
        texts.addView(v, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        row.addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 36, 0, 90, 0));

        TextView btn = new TextView(context);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        btn.setTypeface(AndroidUtilities.bold());
        btn.setText(LocaleController.getString(state == OK ? R.string.MilliySecOpen : R.string.MilliySecFix));
        btn.setTextColor(state == OK || state == -1 ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4) : 0xFFFFFFFF);
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        if (state != OK && state != -1) {
            btn.setBackground(Theme.createRoundRectDrawable(dp(14), state));
        }
        row.addView(btn, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        row.setOnClickListener(x -> fix.run());
        list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
}
