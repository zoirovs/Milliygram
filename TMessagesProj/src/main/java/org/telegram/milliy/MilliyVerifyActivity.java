package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.nio.charset.StandardCharsets;

/**
 * «Tasdiqlash belgisini so'rash» formasi (kanal/guruh egasi yoki o'z boti uchun).
 * 1) @milliygramsupportbot admin bo'lishi shart — shu sahifadan bitta tugma bilan admin qilinadi; bot uchun tavsifga kod.
 * 2) Telefon (Telegram raqami avtomatik, o'zgartirish mumkin) va izoh.
 * 3) Ixtiyoriy: OneID orqali shaxsni tasdiqlash (rozilik bilan) — tezroq va tasdiqlanish ehtimoli yuqori.
 */
public class MilliyVerifyActivity extends BaseFragment {

    private final TLRPC.Chat chat;
    private final TLRPC.User bot;

    private TextView stepOneText;
    private TextView stepOneButton;
    private EditTextBoldCursor phoneField;
    private EditTextBoldCursor commentField;
    private TextCheckCell oneIdCell;
    private TextCheckCell consentCell;
    private TextView submitButton;
    private TextView statusView;

    private JSONObject state;
    private boolean loading;
    private boolean sending;

    public MilliyVerifyActivity(TLRPC.Chat chat, TLRPC.User bot) {
        this.chat = chat;
        this.bot = bot;
    }

    private String query() {
        long me = UserConfig.getInstance(currentAccount).getClientUserId();
        if (chat != null) {
            return "kind=chat&chat_id=" + chat.id + "&user_id=" + me;
        }
        return "kind=bot&username=" + MilliyData.enc(UserObject.getPublicUsername(bot)) + "&user_id=" + me;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.MilliyVerifyRequest));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(12), dp(16), dp(28));
        scrollView.addView(layout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        TextView title = text(context, 20, Theme.key_windowBackgroundWhiteBlackText, true);
        String name = chat != null ? chat.title : UserObject.getUserName(bot);
        String username = chat != null ? ChatObject.getPublicUsername(chat) : UserObject.getPublicUsername(bot);
        title.setText(name + (username != null ? "  @" + username : ""));
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 4, 4, 0));

        TextView intro = text(context, 14, Theme.key_windowBackgroundWhiteGrayText6, false);
        intro.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyVerifyIntro)));
        layout.addView(intro, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 6, 4, 4));

        // 1. Egalik
        LinearLayout card1 = card(context, layout, LocaleController.getString(chat != null ? R.string.MilliyVerifyStepBot : R.string.MilliyVerifyStepBotCode));
        stepOneText = text(context, 15, Theme.key_windowBackgroundWhiteBlackText, false);
        stepOneText.setTextIsSelectable(true);
        card1.addView(stepOneText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        stepOneButton = button(context, false);
        stepOneButton.setOnClickListener(v -> {
            if (chat != null) {
                makeBotAdmin();
            } else {
                load();
            }
        });
        card1.addView(stepOneButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 12, 0, 0));

        // 2. Aloqa
        LinearLayout card2 = card(context, layout, LocaleController.getString(R.string.MilliyVerifyStepContact));
        phoneField = field(context, LocaleController.getString(R.string.MilliyVerifyPhone));
        phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
        TLRPC.User me = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (me != null && me.phone != null && !me.phone.isEmpty()) {
            phoneField.setText("+" + me.phone);
        }
        card2.addView(phoneField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 8, 0, 0));
        TextView phoneHint = text(context, 13, Theme.key_windowBackgroundWhiteGrayText6, false);
        phoneHint.setText(LocaleController.getString(R.string.MilliyVerifyPhoneHint));
        card2.addView(phoneHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 2, 4, 2, 0));
        commentField = field(context, LocaleController.getString(R.string.MilliyVerifyComment));
        commentField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        commentField.setMinLines(2);
        commentField.setMaxLines(6);
        card2.addView(commentField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        // 3. OneID (ixtiyoriy)
        LinearLayout card3 = card(context, layout, LocaleController.getString(R.string.MilliyVerifyStepOneId));
        TextView oneIdInfo = text(context, 14, Theme.key_windowBackgroundWhiteGrayText6, false);
        oneIdInfo.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.MilliyVerifyOneIdInfo)));
        card3.addView(oneIdInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 4));
        oneIdCell = new TextCheckCell(context);
        oneIdCell.setTextAndCheck(LocaleController.getString(R.string.MilliyVerifyOneIdUse), false, true);
        oneIdCell.setOnClickListener(v -> {
            if (!oneIdCell.isEnabled()) {
                return;
            }
            oneIdCell.setChecked(!oneIdCell.isChecked());
            update();
        });
        card3.addView(oneIdCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -16, 0, -16, 0));
        consentCell = new TextCheckCell(context);
        consentCell.setTextAndValueAndCheck(LocaleController.getString(R.string.MilliyVerifyConsent),
                LocaleController.getString(R.string.MilliyVerifyConsentInfo), false, true, false);
        consentCell.setOnClickListener(v -> {
            if (!oneIdCell.isChecked()) {
                return;
            }
            consentCell.setChecked(!consentCell.isChecked());
            update();
        });
        card3.addView(consentCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -16, 0, -16, 0));

        statusView = text(context, 14, Theme.key_windowBackgroundWhiteGrayText6, false);
        statusView.setGravity(Gravity.CENTER);
        layout.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 14, 4, 0));
        submitButton = button(context, true);
        submitButton.setText(LocaleController.getString(R.string.MilliyVerifySend));
        submitButton.setOnClickListener(v -> send());
        layout.addView(submitButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 10, 0, 0));

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                update();
            }
        };
        phoneField.addTextChangedListener(watcher);

        fragmentView = scrollView;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        update();
        load();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (fragmentView != null && state != null) {
            load(); // bot tavsifi tahrirlangach yoki boshqa ilovadan qaytganda
        }
    }

    // ---------- Holat ----------

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        update();
        MilliyShield.http("GET", "verify/status?" + query(), null, null, 20000, (code, body) -> {
            loading = false;
            try {
                if (code == 200 && body != null) {
                    state = new JSONObject(body);
                }
            } catch (Exception ignore) {
            }
            update();
        });
    }

    private void update() {
        if (submitButton == null) {
            return;
        }
        boolean ready = state != null && state.optBoolean("ready");
        boolean already = state != null && state.optBoolean("already");
        boolean pending = state != null && state.optJSONObject("request") != null;
        boolean oneIdAvailable = state != null && state.optBoolean("oneid");
        String botName = state != null ? "@" + state.optString("bot_username", MilliyService.getBotUsername()) : "@" + MilliyService.getBotUsername();

        if (state == null) {
            stepOneText.setText(LocaleController.getString(loading ? R.string.MilliyVerifyChecking : R.string.MilliyVerifyNoConnection));
            stepOneButton.setText(LocaleController.getString(R.string.MilliyVerifyRecheck));
            stepOneButton.setOnClickListener(v -> load());
        } else if (chat != null) {
            stepOneButton.setOnClickListener(v -> makeBotAdmin());
            if (!state.optBoolean("bot_admin")) {
                stepOneText.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.MilliyVerifyBotNeeded, botName)));
                stepOneButton.setText(LocaleController.formatString(R.string.MilliyVerifyMakeAdmin, botName));
                stepOneButton.setVisibility(View.VISIBLE);
            } else if (!state.optBoolean("public")) {
                stepOneText.setText(LocaleController.getString(R.string.MilliyVerifyNotPublic));
                stepOneButton.setVisibility(View.GONE);
            } else if (!state.optBoolean("creator")) {
                stepOneText.setText(LocaleController.getString(R.string.MilliyVerifyNotOwner));
                stepOneButton.setVisibility(View.GONE);
            } else {
                stepOneText.setText("✓ " + LocaleController.formatString(R.string.MilliyVerifyBotOk, botName));
                stepOneButton.setVisibility(View.GONE);
            }
        } else {
            stepOneButton.setOnClickListener(v -> load());
            if (state.optBoolean("code_ok")) {
                stepOneText.setText("✓ " + LocaleController.getString(R.string.MilliyVerifyCodeOk));
                stepOneButton.setVisibility(View.GONE);
            } else {
                stepOneText.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.MilliyVerifyCodeNeeded, state.optString("code"))));
                stepOneButton.setText(LocaleController.getString(R.string.MilliyVerifyRecheck));
                stepOneButton.setVisibility(View.VISIBLE);
            }
        }

        oneIdCell.setEnabled(oneIdAvailable, null);
        if (!oneIdAvailable && oneIdCell.isChecked()) {
            oneIdCell.setChecked(false);
        }
        oneIdCell.setTextAndCheck(LocaleController.getString(oneIdAvailable ? R.string.MilliyVerifyOneIdUse : R.string.MilliyVerifyOneIdSoon), oneIdCell.isChecked(), true);
        consentCell.setEnabled(oneIdCell.isChecked(), null);
        if (!oneIdCell.isChecked() && consentCell.isChecked()) {
            consentCell.setChecked(false);
        }

        String phoneDigits = phoneField.getText().toString().replaceAll("\\D", "");
        boolean phoneOk = phoneDigits.length() >= 9 && phoneDigits.length() <= 15;
        boolean consentOk = !oneIdCell.isChecked() || consentCell.isChecked();
        boolean canSend = ready && !already && !pending && phoneOk && consentOk && !sending && !loading;
        submitButton.setEnabled(canSend);
        submitButton.setAlpha(canSend ? 1f : 0.5f);

        String status;
        if (already) {
            status = LocaleController.getString(R.string.MilliyVerifyAlready);
        } else if (pending) {
            status = LocaleController.getString(R.string.MilliyVerifyPending);
        } else if (state != null && !ready) {
            status = LocaleController.getString(R.string.MilliyVerifyFinishStepOne);
        } else if (!phoneOk) {
            status = LocaleController.getString(R.string.MilliyVerifyPhoneBad);
        } else if (!consentOk) {
            status = LocaleController.getString(R.string.MilliyVerifyConsentNeeded);
        } else {
            status = "";
        }
        statusView.setText(status);
        statusView.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---------- Bot admin (shu sahifadan) ----------

    private void makeBotAdmin() {
        if (chat == null) {
            return;
        }
        long botId = state != null ? state.optLong("bot_id", MilliyService.getBotId()) : MilliyService.getBotId();
        TLRPC.User user = botId != 0 ? getMessagesController().getUser(botId) : null;
        if (user != null) {
            setAdmin(user);
            return;
        }
        String username = state != null ? state.optString("bot_username", MilliyService.getBotUsername()) : MilliyService.getBotUsername();
        stepOneButton.setEnabled(false);
        getMessagesController().getUserNameResolver().resolve(username, id -> {
            stepOneButton.setEnabled(true);
            TLRPC.User u = id != null ? getMessagesController().getUser(id) : null;
            if (u != null) {
                setAdmin(u);
            } else {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.MilliyVerifyAdminError)).show();
            }
        });
    }

    private void setAdmin(TLRPC.User user) {
        TLRPC.TL_chatAdminRights rights = new TLRPC.TL_chatAdminRights();
        rights.other = true; // hech qanday huquqsiz admin — faqat egalikni tekshirish uchun
        stepOneButton.setEnabled(false);
        getMessagesController().setUserAdminRole(chat.id, user, rights, "", ChatObject.isChannelAndNotMegaGroup(chat), this, true, true, null, () -> {
            stepOneButton.setEnabled(true);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.MilliyVerifyAdminDone)).show();
            AndroidUtilities.runOnUIThread(this::load, 1500); // server Bot API orqali qayta tekshiradi
        }, error -> {
            stepOneButton.setEnabled(true);
            return false;
        });
    }

    // ---------- Yuborish ----------

    private void send() {
        if (sending || state == null) {
            return;
        }
        sending = true;
        update();
        try {
            JSONObject o = new JSONObject();
            o.put("kind", chat != null ? "chat" : "bot");
            o.put("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
            if (chat != null) {
                o.put("chat_id", chat.id);
            } else {
                o.put("username", UserObject.getPublicUsername(bot));
            }
            o.put("phone", phoneField.getText().toString());
            o.put("comment", commentField.getText().toString());
            o.put("oneid", oneIdCell.isChecked());
            o.put("consent", consentCell.isChecked());
            MilliyShield.http("POST", "verify/request", o.toString().getBytes(StandardCharsets.UTF_8), "application/json", 30000, (code, body) -> {
                sending = false;
                JSONObject res = null;
                try {
                    res = body != null ? new JSONObject(body) : null;
                } catch (Exception ignore) {
                }
                if (code == 200 && res != null) {
                    String url = res.optString("oneid_url", "");
                    if (!url.isEmpty() && !"null".equals(url) && getParentActivity() != null) {
                        Browser.openUrl(getParentActivity(), url);
                    }
                    BaseFragment prev = getParentLayout() != null && getParentLayout().getFragmentStack().size() > 1
                            ? getParentLayout().getFragmentStack().get(getParentLayout().getFragmentStack().size() - 2) : null;
                    finishFragment();
                    if (prev != null) {
                        BulletinFactory.of(prev).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.MilliyVerifySent)).show();
                    }
                } else {
                    String err = res != null ? res.optString("detail", "") : "";
                    BulletinFactory.of(this).createErrorBulletin(err.isEmpty() ? LocaleController.getString(R.string.MilliyVerifyNoConnection) : err).show();
                    load();
                }
            });
        } catch (Exception e) {
            sending = false;
            update();
        }
    }

    // ---------- Ko'rinish ----------

    private TextView text(Context context, int size, int colorKey, boolean bold) {
        TextView v = new TextView(context);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        v.setTextColor(Theme.getColor(colorKey));
        v.setLineSpacing(dp(2), 1f);
        if (bold) {
            v.setTypeface(AndroidUtilities.bold());
        }
        return v;
    }

    private LinearLayout card(Context context, LinearLayout parent, String header) {
        LinearLayout c = new LinearLayout(context);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.getColor(Theme.key_windowBackgroundWhite)));
        TextView h = text(context, 15, Theme.key_windowBackgroundWhiteBlueHeader, true);
        h.setText(header);
        c.addView(h);
        parent.addView(c, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        return c;
    }

    private EditTextBoldCursor field(Context context, String hint) {
        EditTextBoldCursor e = new EditTextBoldCursor(context);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        e.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        e.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        e.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        e.setHint(hint);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_windowBackgroundGray)));
        return e;
    }

    private TextView button(Context context, boolean filled) {
        TextView b = new TextView(context);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        b.setTypeface(AndroidUtilities.bold());
        if (filled) {
            b.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            b.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 10));
        } else {
            b.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            b.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton), 0.12f)));
        }
        return b;
    }
}
