package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Locale;

/** Sozlamalar (profil) tepasidagi ixcham panel: chapda ob-havo, o'ngda valyuta kursi (kun.uz uslubida). */
public class MilliyInfoBar extends LinearLayout {

    private static final String[] CODES = {"USD", "EUR", "RUB"};
    private static final int UP = 0xFF2EA44F, DOWN = 0xFFE5534B;

    private final int account;
    private final LinearLayout weatherBox, currencyBox;
    private final TextView tempView, placeView;
    private final TextView[] rateViews = new TextView[CODES.length];
    private final View divider;

    public MilliyInfoBar(Context context, BaseFragment fragment) {
        super(context);
        account = fragment.getCurrentAccount();
        setOrientation(HORIZONTAL);
        setPadding(dp(8), dp(6), dp(8), dp(6));

        weatherBox = new LinearLayout(context);
        weatherBox.setOrientation(VERTICAL);
        weatherBox.setGravity(Gravity.CENTER_VERTICAL);
        weatherBox.setPadding(dp(12), dp(8), dp(8), dp(8));
        tempView = new TextView(context);
        tempView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        tempView.setTypeface(AndroidUtilities.bold());
        tempView.setSingleLine();
        weatherBox.addView(tempView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        placeView = new TextView(context);
        placeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        placeView.setMaxLines(2);
        placeView.setEllipsize(TextUtils.TruncateAt.END);
        weatherBox.addView(placeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        weatherBox.setOnClickListener(v -> {
            if (MilliyData.getDistrict(account) == null) {
                fragment.presentFragment(new MilliyRegionActivity(false));
            } else {
                MilliyInfo.showWeather(fragment);
            }
        });
        addView(weatherBox, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

        divider = new View(context);
        addView(divider, LayoutHelper.createLinear(1, LayoutHelper.MATCH_PARENT, 0, 10, 0, 10));

        currencyBox = new LinearLayout(context);
        currencyBox.setOrientation(VERTICAL);
        currencyBox.setGravity(Gravity.CENTER_VERTICAL);
        currencyBox.setPadding(dp(14), dp(8), dp(12), dp(8));
        for (int i = 0; i < CODES.length; i++) {
            TextView t = new TextView(context);
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            t.setSingleLine();
            rateViews[i] = t;
            currencyBox.addView(t, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, i == 0 ? 0 : 3, 0, 0));
        }
        currencyBox.setOnClickListener(v -> MilliyInfo.showCurrency(fragment));
        addView(currencyBox, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.15f));

        update();
    }

    /** Keshdagi ma'lumotdan qayta chizadi (MilliyInfo.refresh tugaganda chaqiriladi). */
    public void update() {
        final int black = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        final int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        weatherBox.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        currencyBox.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        tempView.setTextColor(black);
        placeView.setTextColor(gray);

        // ob-havo
        String wp = MilliyInfo.weatherPath(account);
        String body = wp == null ? null : MilliyData.cached(wp);
        if (wp == null) {
            tempView.setText(Emoji.replaceEmoji("📍", tempView.getPaint().getFontMetricsInt(), false));
            placeView.setText(LocaleController.getString(R.string.MilliyChooseRegion));
        } else if (body == null) {
            tempView.setText("…");
            placeView.setText(LocaleController.getString(R.string.MilliyLoading));
        } else {
            try {
                JSONObject w = new JSONObject(body);
                int t = w.getInt("temp");
                CharSequence temp = MilliyInfo.emoji(w.optInt("code")) + " " + (t > 0 ? "+" : "") + t + "°";
                tempView.setText(Emoji.replaceEmoji(temp, tempView.getPaint().getFontMetricsInt(), false));
                placeView.setText(w.getString("place") + " · " + w.optString("text"));
            } catch (Exception e) {
                tempView.setText("—");
                placeView.setText("");
            }
        }

        // valyuta
        JSONArray arr = null;
        try {
            String c = MilliyData.cached("currency");
            arr = c == null ? null : new JSONArray(c);
        } catch (Exception ignore) {
        }
        for (int i = 0; i < CODES.length; i++) {
            JSONObject o = find(arr, CODES[i]);
            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append(CODES[i]).append("  ");
            sb.setSpan(new ForegroundColorSpan(gray), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (o == null) {
                sb.append("—");
            } else {
                int s = sb.length();
                sb.append(String.format(Locale.US, "%,.2f", o.optDouble("rate")).replace(",", " "));
                sb.setSpan(new ForegroundColorSpan(black), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                double diff = o.optDouble("diff", 0);
                if (diff != 0) {
                    s = sb.length();
                    sb.append(diff > 0 ? "  ▲" : "  ▼");
                    sb.setSpan(new ForegroundColorSpan(diff > 0 ? UP : DOWN), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            rateViews[i].setText(sb);
        }
    }

    private static JSONObject find(JSONArray arr, String code) {
        if (arr == null) {
            return null;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && code.equals(o.optString("code"))) {
                return o;
            }
        }
        return null;
    }
}
