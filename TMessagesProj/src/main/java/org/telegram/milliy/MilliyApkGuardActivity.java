package org.telegram.milliy;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * «Aqlli APK Himoyachi» sozlamalari:
 *  - Xizmatni yoqish/o'chirish;
 *  - Oq va qora ro'yxat holati va serverdan yangilash;
 *  - Himoya qoidalarini sozlash.
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
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
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
        root.removeAllViews();

        // 1. Asosiy Yoqish/O'chirish
        TextCheckCell mainSwitch = new TextCheckCell(getParentActivity());
        mainSwitch.setBackground(Theme.getSelectorDrawable(true));
        boolean enabled = MilliyApkGuard.isEnabled();
        mainSwitch.setTextAndCheck(LocaleController.getString(R.string.MilliyApkGuardEnable), enabled, false);
        mainSwitch.setOnClickListener(v -> {
            boolean nv = !MilliyApkGuard.isEnabled();
            MilliyApkGuard.setEnabled(nv);
            mainSwitch.setChecked(nv);
            build();
        });
        root.addView(mainSwitch, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextInfoPrivacyCell desc = new TextInfoPrivacyCell(getParentActivity());
        desc.setText(LocaleController.getString(R.string.MilliyApkGuardDesc));
        desc.setBackground(Theme.getThemedDrawableByKey(getParentActivity(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        root.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (!enabled) {
            return;
        }

        // 2. Oq va Qora Ro'yxat Bazasi
        header(R.string.MilliyApkGuardRulesTitle);

        TextSettingsCell countCell = new TextSettingsCell(getParentActivity());
        countCell.setBackground(Theme.getSelectorDrawable(true));
        countCell.setTextAndValue(
                LocaleController.getString(R.string.MilliyApkGuardRulesTitle),
                LocaleController.formatString(R.string.MilliyApkGuardRulesActive, MilliyApkGuard.getWhitelistedCount()),
                true
        );
        root.addView(countCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        long lastSync = MilliyApkGuard.getLastSyncTime();
        String timeStr = lastSync > 0
                ? new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date(lastSync))
                : LocaleController.getString(R.string.MilliyNotSet);

        TextSettingsCell syncCell = new TextSettingsCell(getParentActivity());
        syncCell.setBackground(Theme.getSelectorDrawable(true));
        syncCell.setTextAndValue(
                LocaleController.formatString(R.string.MilliyApkGuardRulesLastUpdate, timeStr),
                updatingRules ? LocaleController.getString(R.string.MilliyApkGuardRulesUpdating) : LocaleController.getString(R.string.MilliyApkGuardRulesUpdateBtn),
                false
        );
        syncCell.setOnClickListener(v -> {
            if (updatingRules) return;
            updatingRules = true;
            build();
            MilliyApkGuard.syncRules(true, () -> {
                updatingRules = false;
                build();
                if (getParentActivity() != null) {
                    BulletinFactory.of(this).createSimpleBulletin(
                            R.raw.done,
                            LocaleController.getString(R.string.MilliyApkGuardRulesUpdated)
                    ).show();
                }
            });
        });
        root.addView(syncCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        info(R.string.MilliyApkGuardRulesTitle);

        // 3. Himoya qoidalari
        header(R.string.MilliySecurity);

        check(
                R.string.MilliyApkGuardAutoTrustWhitelist,
                R.string.MilliyApkGuardAutoTrustWhitelistInfo,
                MilliyApkGuard.isAutoTrustWhitelist(),
                MilliyApkGuard::setAutoTrustWhitelist
        );

        check(
                R.string.MilliyApkGuardWarnDangerous,
                R.string.MilliyApkGuardWarnDangerousInfo,
                MilliyApkGuard.isWarnDangerous(),
                MilliyApkGuard::setWarnDangerous
        );

        check(
                R.string.MilliyApkGuardAutoDeleteBlacklist,
                R.string.MilliyApkGuardAutoDeleteBlacklistInfo,
                MilliyApkGuard.isAutoDeleteBlacklist(),
                MilliyApkGuard::setAutoDeleteBlacklist
        );

        TextInfoPrivacyCell footerInfo = new TextInfoPrivacyCell(getParentActivity());
        footerInfo.setText(LocaleController.getString(R.string.MilliyApkWarnText));
        footerInfo.setBackground(Theme.getThemedDrawableByKey(getParentActivity(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        root.addView(footerInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    interface ValueSetter {
        void set(boolean val);
    }

    private void check(int title, int subtitle, boolean current, ValueSetter setter) {
        TextCheckCell c = new TextCheckCell(getParentActivity());
        c.setBackground(Theme.getSelectorDrawable(true));
        c.setTextAndValueAndCheck(LocaleController.getString(title), LocaleController.getString(subtitle), current, true, true);
        c.setOnClickListener(v -> {
            boolean nv = !current;
            setter.set(nv);
            c.setChecked(nv);
            build();
        });
        root.addView(c, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void header(int text) {
        HeaderCell h = new HeaderCell(getParentActivity());
        h.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        h.setText(LocaleController.getString(text));
        root.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void info(int text) {
        TextInfoPrivacyCell c = new TextInfoPrivacyCell(getParentActivity());
        c.setText("");
        c.setBackground(Theme.getThemedDrawableByKey(getParentActivity(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        root.addView(c, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
}
