package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * «Aqlli APK Himoyachi» zamonaviy boshqaruv ekrani.
 * APK Qo'riqchi ilovasining qalqon va metrikalar vizualidan ilhomlangan holda,
 * to'liq Card UI uslubida, invaziv ruxsatlarsiz (No-Accessibility, No-Storage)
 * O'zbekiston bank/to'lov ilovalarini oq ro'yxatda, troyanlarni qora ro'yxatda himoyalaydi.
 */
public class MilliyApkGuardActivity extends BaseFragment {

    private LinearLayout root;
    private boolean updatingRules = false;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.MilliyApkGuardTitle));
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
        scroll.setFillViewport(true);

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(30));

        scroll.addView(root, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        fragmentView = scroll;

        build();
        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (root != null) {
            build();
        }
    }

    private void build() {
        if (root == null || getParentActivity() == null) {
            return;
        }
        root.removeAllViews();
        Context context = getParentActivity();
        boolean enabled = MilliyApkGuard.isEnabled();

        // 1. Markaziy Qalqon Kartasi (Shield Card)
        root.addView(createShieldBanner(context, enabled));

        // 2. 3 talik Statistika Bloki (Metrics Grid)
        root.addView(createMetricsGrid(context, enabled));

        // 3. Asosiy Yoqish/O'chirish Kartasi
        root.addView(createMainToggleCard(context, enabled));

        if (!enabled) {
            TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
            info.setText(LocaleController.getString(R.string.MilliyApkGuardShieldInactiveSub));
            root.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, 16));
            return;
        }

        // 4. Himoya qoidalari
        header(LocaleController.getString(R.string.MilliyApkGuardRulesTitle));

        LinearLayout rulesGroup = createCardContainer(context);

        // Switch 1: Oq ro'yxatga avto-ruxsat
        TextCheckCell autoTrustCell = new TextCheckCell(context);
        autoTrustCell.setBackground(Theme.getSelectorDrawable(true));
        autoTrustCell.setTextAndCheck(
                LocaleController.getString(R.string.MilliyApkGuardAutoTrustWhitelist),
                MilliyApkGuard.isAutoTrustWhitelist(),
                true
        );
        autoTrustCell.setOnClickListener(v -> {
            boolean nv = !MilliyApkGuard.isAutoTrustWhitelist();
            MilliyApkGuard.setAutoTrustWhitelist(nv);
            autoTrustCell.setChecked(nv);
        });
        rulesGroup.addView(autoTrustCell);

        // Switch 2: Shubhali SMS troyanlarni ogohlantirish
        TextCheckCell warnDangerousCell = new TextCheckCell(context);
        warnDangerousCell.setBackground(Theme.getSelectorDrawable(true));
        warnDangerousCell.setTextAndCheck(
                LocaleController.getString(R.string.MilliyApkGuardWarnDangerous),
                MilliyApkGuard.isWarnDangerous(),
                true
        );
        warnDangerousCell.setOnClickListener(v -> {
            boolean nv = !MilliyApkGuard.isWarnDangerous();
            MilliyApkGuard.setWarnDangerous(nv);
            warnDangerousCell.setChecked(nv);
        });
        rulesGroup.addView(warnDangerousCell);

        // Switch 3: Xavfli troyanlarni avto-o'chirish
        TextCheckCell autoDeleteCell = new TextCheckCell(context);
        autoDeleteCell.setBackground(Theme.getSelectorDrawable(true));
        autoDeleteCell.setTextAndCheck(
                LocaleController.getString(R.string.MilliyApkGuardAutoDeleteBlacklist),
                MilliyApkGuard.isAutoDeleteBlacklist(),
                false
        );
        autoDeleteCell.setOnClickListener(v -> {
            boolean nv = !MilliyApkGuard.isAutoDeleteBlacklist();
            MilliyApkGuard.setAutoDeleteBlacklist(nv);
            autoDeleteCell.setChecked(nv);
        });
        rulesGroup.addView(autoDeleteCell);

        root.addView(rulesGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, 10));

        // 5. Onlayn baza va boshqaruv harakatlari
        header(LocaleController.getString(R.string.MilliyApkGuardStatBaseStatus));

        LinearLayout actionsGroup = createCardContainer(context);

        // Baza yangilash tugmasi
        TextSettingsCell updateCell = new TextSettingsCell(context);
        updateCell.setBackground(Theme.getSelectorDrawable(true));
        long lastSync = MilliyApkGuard.getLastSyncTime();
        String lastSyncStr;
        if (lastSync <= 0) {
            lastSyncStr = "Boshlang'ich baza";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            lastSyncStr = sdf.format(new Date(lastSync));
        }
        updateCell.setTextAndValue(
                LocaleController.getString(R.string.MilliyApkGuardRulesUpdateBtn),
                updatingRules ? LocaleController.getString(R.string.MilliyApkGuardRulesUpdating) : lastSyncStr,
                true
        );
        updateCell.setOnClickListener(v -> {
            if (updatingRules) {
                return;
            }
            updatingRules = true;
            updateCell.setTextAndValue(
                    LocaleController.getString(R.string.MilliyApkGuardRulesUpdateBtn),
                    LocaleController.getString(R.string.MilliyApkGuardRulesUpdating),
                    true
            );
            MilliyApkGuard.syncRules(true, () -> {
                updatingRules = false;
                if (getParentActivity() == null) {
                    return;
                }
                build();
                BulletinFactory.of(MilliyApkGuardActivity.this).createSimpleBulletin(
                        R.drawable.msg_bot,
                        LocaleController.getString(R.string.MilliyApkGuardRulesUpdated)
                ).show();
            });
        });
        actionsGroup.addView(updateCell);

        // Oq ro'yxatni ko'rish (Whitelist Dialog)
        TextSettingsCell viewWhitelistCell = new TextSettingsCell(context);
        viewWhitelistCell.setBackground(Theme.getSelectorDrawable(true));
        viewWhitelistCell.setTextAndValue(
                LocaleController.getString(R.string.MilliyApkGuardViewWhitelist),
                String.format(Locale.getDefault(), "%d ta", MilliyApkGuard.getWhitelistedCount()),
                false
        );
        viewWhitelistCell.setOnClickListener(v -> showWhitelistDialog());
        actionsGroup.addView(viewWhitelistCell);

        root.addView(actionsGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, 16));

        // 6. Maxfiylik va Xavfsizlik Kafolati (Info Banner)
        root.addView(createPrivacyAdvantageCard(context));
    }

    /**
     * APK Qo'riqchiga o'xshash markaziy Qalqon holati kartasi
     */
    private View createShieldBanner(Context context, boolean enabled) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(20), dp(24), dp(20), dp(24));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        boolean dark = Theme.isCurrentThemeDark();

        int accentColor = enabled ? 0xFF10B981 : 0xFFEF4444;
        int bgColor = enabled
                ? (dark ? 0xFF063324 : 0xFFECFDF5)
                : (dark ? 0xFF351717 : 0xFFFEF2F2);

        bg.setColor(bgColor);
        bg.setStroke(dp(1.5f), accentColor);
        card.setBackground(bg);

        // Doira ichidagi Qalqon
        FrameLayout shieldBox = new FrameLayout(context);
        GradientDrawable shieldCircle = new GradientDrawable();
        shieldCircle.setShape(GradientDrawable.OVAL);
        shieldCircle.setColor(enabled ? 0x2410B981 : 0x24EF4444);
        shieldBox.setBackground(shieldCircle);

        ImageView shieldIcon = new ImageView(context);
        shieldIcon.setImageResource(R.drawable.msg_bot);
        shieldIcon.setColorFilter(accentColor);
        shieldBox.addView(shieldIcon, LayoutHelper.createFrame(44, 44, Gravity.CENTER));

        card.addView(shieldBox, LayoutHelper.createLinear(84, 84, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        // Sarlavha
        TextView title = new TextView(context);
        title.setText(LocaleController.getString(enabled ? R.string.MilliyApkGuardShieldActive : R.string.MilliyApkGuardShieldInactive));
        title.setTextColor(accentColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        // Tavsif
        TextView sub = new TextView(context);
        sub.setText(LocaleController.getString(enabled ? R.string.MilliyApkGuardShieldActiveSub : R.string.MilliyApkGuardShieldInactiveSub));
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sub.setGravity(Gravity.CENTER);
        card.addView(sub, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 14, 14, 10);
        card.setLayoutParams(lp);
        return card;
    }

    /**
     * 3 ta ko'rsatkichli Grid (Rasmiy ilovalar, Qora ro'yxat, Baza holati)
     */
    private View createMetricsGrid(Context context, boolean enabled) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);

        int whitelisted = MilliyApkGuard.getWhitelistedCount();
        int blacklisted = MilliyApkGuard.getBlacklistedCount();

        row.addView(createMetricItem(context, String.valueOf(whitelisted), LocaleController.getString(R.string.MilliyApkGuardStatSafeApps), 0xFF10B981, v -> showWhitelistDialog()),
                new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));

        View spacer1 = new View(context);
        row.addView(spacer1, LayoutHelper.createLinear(8, LayoutHelper.MATCH_PARENT));

        row.addView(createMetricItem(context, String.valueOf(blacklisted), LocaleController.getString(R.string.MilliyApkGuardStatTrojans), 0xFFEF4444, null),
                new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));

        View spacer2 = new View(context);
        row.addView(spacer2, LayoutHelper.createLinear(8, LayoutHelper.MATCH_PARENT));

        row.addView(createMetricItem(context, LocaleController.getString(R.string.MilliyApkGuardStatBaseStatusVal), LocaleController.getString(R.string.MilliyApkGuardStatBaseStatus), 0xFF3B82F6, null),
                new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, 12);
        row.setLayoutParams(lp);
        return row;
    }

    private View createMetricItem(Context context, String value, String label, int color, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(10), dp(14), dp(10), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        box.setBackground(bg);

        TextView valView = new TextView(context);
        valView.setText(value);
        valView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        valView.setTypeface(Typeface.DEFAULT_BOLD);
        valView.setTextColor(color);
        valView.setGravity(Gravity.CENTER);
        valView.setSingleLine(true);
        box.addView(valView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView lblView = new TextView(context);
        lblView.setText(label);
        lblView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        lblView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        lblView.setGravity(Gravity.CENTER);
        lblView.setSingleLine(true);
        box.addView(lblView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        if (click != null) {
            box.setOnClickListener(click);
            box.setForeground(Theme.getSelectorDrawable(true));
        }

        return box;
    }

    /**
     * Katta Switch kartasi
     */
    private View createMainToggleCard(Context context, boolean enabled) {
        LinearLayout card = createCardContainer(context);

        TextCheckCell toggleCell = new TextCheckCell(context);
        toggleCell.setBackground(Theme.getSelectorDrawable(true));
        toggleCell.setTextAndValueAndCheck(
                LocaleController.getString(R.string.MilliyApkGuardEnable),
                LocaleController.getString(R.string.MilliyApkGuardDesc),
                enabled,
                true,
                false
        );
        toggleCell.setOnClickListener(v -> {
            boolean nv = !MilliyApkGuard.isEnabled();
            MilliyApkGuard.setEnabled(nv);
            toggleCell.setChecked(nv);
            build();
        });

        card.addView(toggleCell);
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, 12);
        card.setLayoutParams(lp);
        return card;
    }

    /**
     * Xavfsizlik va Maxfiylik kafolati kartasi
     */
    private View createPrivacyAdvantageCard(Context context) {
        LinearLayout card = createCardContainer(context);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.MilliyApkGuardPrivacyAdvantage));
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView text = new TextView(context);
        text.setText(LocaleController.getString(R.string.MilliyApkGuardPrivacyAdvantageText));
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        text.setLineSpacing(dp(2), 1f);
        card.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 0, 14, 20);
        card.setLayoutParams(lp);
        return card;
    }

    private LinearLayout createCardContainer(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        card.setBackground(bg);
        card.setClipToOutline(true);
        return card;
    }

    private void header(String text) {
        if (getParentActivity() == null) {
            return;
        }
        HeaderCell cell = new HeaderCell(getParentActivity());
        cell.setText(text);
        root.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    /**
     * Oq ro'yxatdagi rasmiy ilovalar ro'yxatini ko'rsatish dialogi
     */
    private void showWhitelistDialog() {
        if (getParentActivity() == null) {
            return;
        }
        Context context = getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.MilliyApkGuardViewWhitelist));

        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(10), dp(20), dp(10));

        Map<String, String> map = MilliyApkGuard.getDynamicWhitelist();
        ArrayList<String> packages = new ArrayList<>(map.keySet());
        Collections.sort(packages);

        for (String pkg : packages) {
            String name = map.get(pkg);
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(8), 0, dp(8));

            TextView nameView = new TextView(context);
            nameView.setText(name);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            nameView.setTypeface(Typeface.DEFAULT_BOLD);
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            item.addView(nameView);

            TextView pkgView = new TextView(context);
            pkgView.setText(pkg);
            pkgView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            pkgView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            item.addView(pkgView);

            content.addView(item);
        }

        scroll.addView(content);
        builder.setView(scroll);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        showDialog(builder.create());
    }
}
