package org.telegram.milliy;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

/** Sozlamalar → Milliygram: «Botlar» katalogi va «Ilova haqida». Ma'lumot serverdan keladi, panelda o'zgartiriladi. */
public class MilliyCatalogActivity extends BaseFragment {

    private static final long CACHE_MS = 3600_000;

    private final boolean about;
    private LinearLayout list;

    public MilliyCatalogActivity(boolean about) {
        this.about = about;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(about ? R.string.MilliyAbout : R.string.MilliyBots));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        fragmentView = scroll;
        load();
        return scroll;
    }

    private void load() {
        list.removeAllViews();
        if (about) {
            addAppHeader();
        }
        final TextView state = addNote(LocaleController.getString(R.string.MilliyLoading));
        MilliyData.get(about ? "about" : "catalog", CACHE_MS, body -> {
            if (getParentActivity() == null) {
                return;
            }
            list.removeView(state);
            try {
                JSONObject o = new JSONObject(body);
                if (about) {
                    showAbout(o);
                } else {
                    showBots(o.getJSONArray("bots"));
                }
            } catch (Exception e) {
                FileLog.e(e);
                showError();
            }
        }, () -> {
            if (getParentActivity() != null) {
                list.removeView(state);
                showError();
            }
        });
    }

    private void showError() {
        addNote(LocaleController.getString(R.string.MilliyNetworkError));
        TextView retry = addNote(LocaleController.getString(R.string.MilliyRetry));
        retry.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        retry.setOnClickListener(v -> load());
    }

    private void showBots(JSONArray bots) {
        if (bots.length() == 0) {
            addNote(LocaleController.getString(R.string.MilliyBotsEmpty));
            return;
        }
        for (int i = 0; i < bots.length(); i++) {
            JSONObject b = bots.optJSONObject(i);
            String username = b != null ? b.optString("username") : "";
            if (TextUtils.isEmpty(username)) {
                continue;
            }
            String desc = b.optString("description");
            String title = b.optString("title", username);
            addRow(title, TextUtils.isEmpty(desc) ? "@" + username : desc, i < bots.length() - 1, () -> openUsername(username));
        }
    }

    private void showAbout(JSONObject o) {
        String text = o.optString("text");
        if (!TextUtils.isEmpty(text)) {
            addNote(text);
        }
        addUsername(R.string.MilliyAboutChannelUz, o.optString("channel_uz"));
        addUsername(R.string.MilliyAboutChannelRu, o.optString("channel_ru"));
        addUsername(R.string.MilliyAboutSupport, o.optString("support"));
        addUrl(R.string.MilliyAboutSite, o.optString("site"));
        final String email = o.optString("email");
        if (!TextUtils.isEmpty(email)) {
            addRow(LocaleController.getString(R.string.MilliyAboutEmail), email, true, () -> {
                try {
                    getParentActivity().startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email)));
                } catch (Exception e) {
                    AndroidUtilities.addToClipboard(email);
                }
            });
        }
        addUrl(R.string.MilliyAboutRate, o.optString("rate_url"));
        addUrl(R.string.MilliyAboutSource, o.optString("source_url"));
    }

    private void addUsername(int title, String username) {
        if (!TextUtils.isEmpty(username)) {
            addRow(LocaleController.getString(title), "@" + username, true, () -> openUsername(username));
        }
    }

    private void addUrl(int title, String url) {
        if (!TextUtils.isEmpty(url)) {
            addRow(LocaleController.getString(title), url.replaceFirst("^https://", ""), true, () -> Browser.openUrl(getParentActivity(), url));
        }
    }

    private void openUsername(String username) {
        getMessagesController().openByUserName(username, this, 0);
    }

    private void addRow(String title, String detail, boolean divider, Runnable click) {
        TextDetailSettingsCell c = new TextDetailSettingsCell(getParentActivity());
        c.setBackground(Theme.getSelectorDrawable(false));
        c.setMultilineDetail(true);
        c.setTextAndValue(title, detail, divider);
        c.setOnClickListener(v -> click.run());
        list.addView(c, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private TextView addNote(CharSequence text) {
        TextView t = new TextView(getParentActivity());
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        t.setLineSpacing(AndroidUtilities.dp(2), 1f);
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        t.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(14), AndroidUtilities.dp(21), AndroidUtilities.dp(14));
        t.setText(text);
        list.addView(t, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return t;
    }

    private void addAppHeader() {
        ImageView icon = new ImageView(getParentActivity());
        icon.setImageResource(R.mipmap.ic_launcher_round);
        list.addView(icon, LayoutHelper.createLinear(84, 84, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 10));
        TextView name = new TextView(getParentActivity());
        name.setText("Milliygram");
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setGravity(Gravity.CENTER);
        list.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView version = new TextView(getParentActivity());
        version.setText(LocaleController.formatString(R.string.MilliyAboutVersion, BuildVars.BUILD_VERSION_STRING));
        version.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        version.setGravity(Gravity.CENTER);
        list.addView(version, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 4));
    }
}
