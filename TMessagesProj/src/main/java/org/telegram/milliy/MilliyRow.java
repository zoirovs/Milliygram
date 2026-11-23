package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.SettingsActivity;

/**
 * Milliygram sozlamalari qatori: chapda rangli (gradient) ikonka, sarlavha va izoh, o'ngda kalit yoki qiymat.
 * Telegram 13 Sozlamalaridagi ko'rinishga mos.
 */
public class MilliyRow extends LinearLayout {

    private final TextView titleView, subtitleView, valueView;
    private final ImageView chevron;
    private Switch switchView;
    private boolean divider;
    private final Paint dividerPaint = new Paint();

    public MilliyRow(Context context, int icon, int colorTop, int colorBottom) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(dp(56));
        setPadding(dp(14), dp(8), dp(14), dp(8));
        setWillNotDraw(false);

        FrameLayout iconLayout = new FrameLayout(context);
        SettingsActivity.SettingCell.Background bg = new SettingsActivity.SettingCell.Background();
        bg.setColor(colorTop, colorBottom);
        iconLayout.setBackground(bg);
        ImageView iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (icon != 0) {
            iconView.setImageResource(icon);
        }
        iconView.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        iconLayout.addView(iconView, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        addView(iconLayout, LayoutHelper.createLinear(30, 30, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView);
        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setMaxLines(3);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setVisibility(GONE);
        texts.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        valueView = new TextView(context);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        valueView.setSingleLine();
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        valueView.setMaxWidth(dp(150));
        valueView.setVisibility(GONE);
        addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

        chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.msg_arrowright);
        chevron.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
        chevron.setVisibility(GONE);
        addView(chevron, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));

        setBackground(Theme.getSelectorDrawable(false));
        dividerPaint.setColor(Theme.getColor(Theme.key_divider));
    }

    public MilliyRow setTexts(CharSequence title, CharSequence subtitle) {
        titleView.setText(title);
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);
        return this;
    }

    /** O'ngda qiymat va «>» belgisi (bosilsa ochiladigan qator). */
    public MilliyRow setValue(CharSequence value, boolean arrow) {
        valueView.setText(value);
        valueView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
        chevron.setVisibility(arrow ? VISIBLE : GONE);
        return this;
    }

    /** O'ngda kalit (yoqish/o'chirish). */
    public MilliyRow setSwitch(boolean checked) {
        if (switchView == null) {
            switchView = new Switch(getContext());
            switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
            addView(switchView, LayoutHelper.createLinear(37, 20, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        }
        switchView.setChecked(checked, false);
        return this;
    }

    public void setChecked(boolean checked) {
        if (switchView != null) {
            switchView.setChecked(checked, true);
        }
    }

    public void setDivider(boolean divider) {
        this.divider = divider;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (divider) {
            float left = LocaleController.isRTL ? 0 : dp(58);
            float right = getMeasuredWidth() - (LocaleController.isRTL ? dp(58) : 0);
            canvas.drawRect(left, getMeasuredHeight() - Math.max(1, AndroidUtilities.dp(0.5f)), right, getMeasuredHeight(), dividerPaint);
        }
    }
}
