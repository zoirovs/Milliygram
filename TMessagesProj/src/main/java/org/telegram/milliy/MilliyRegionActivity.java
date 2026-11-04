package org.telegram.milliy;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Hudud tanlash: viloyat → tuman. Kanal ro'yxati ko'rsatilmaydi: rozilikdan keyin hudud kanallariga fonda qo'shiladi.
 * required=true bo'lsa (birinchi kirish) — tanlamaguncha yopilmaydi.
 */
public class MilliyRegionActivity extends BaseFragment {

    private final boolean required;
    private LinearLayout list;
    private JSONArray regions;
    private JSONObject selectedRegion;
    private String districtCode, districtName;
    private int step;

    public MilliyRegionActivity(boolean required) {
        this.required = required;
    }

    /** Hudud tanlanmagan bo'lsa — majburiy ekranni ochadi. */
    public static void checkRequired(BaseFragment fragment) {
        if (MilliyData.getDistrict(fragment.getCurrentAccount()) != null) {
            return;
        }
        if (fragment.getParentLayout() == null || fragment.getParentLayout().getLastFragment() instanceof MilliyRegionActivity) {
            return;
        }
        fragment.presentFragment(new MilliyRegionActivity(true));
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.MilliyRegionTitle));
        if (!required) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1 && onBackPressed(true)) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        ScrollView scrollView = new ScrollView(context);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(list, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        root.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = root;
        loadRegions();
        return fragmentView;
    }

    // ---------- UI yordamchilar ----------

    private void clear(String header) {
        list.removeAllViews();
        HeaderCell headerCell = new HeaderCell(getContext());
        headerCell.setText(header);
        list.addView(headerCell);
    }

    private TextView addRow(CharSequence text, boolean accent, View.OnClickListener click) {
        TextView row = new TextView(getContext());
        row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        row.setTextColor(Theme.getColor(accent ? Theme.key_windowBackgroundWhiteBlueText4 : Theme.key_windowBackgroundWhiteBlackText));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
        row.setMinHeight(AndroidUtilities.dp(50));
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setText(text);
        if (click != null) {
            row.setOnClickListener(click);
        }
        list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return row;
    }

    private void addInfo(CharSequence text) {
        TextView info = new TextView(getContext());
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        info.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        info.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(8), AndroidUtilities.dp(21), AndroidUtilities.dp(12));
        info.setText(text);
        list.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void showError(Runnable retry) {
        clear(LocaleController.getString(R.string.MilliyRegionTitle));
        addInfo(LocaleController.getString(R.string.MilliyNetworkError));
        addRow(LocaleController.getString(R.string.MilliyRetry), true, v -> retry.run());
    }

    // ---------- Bosqichlar ----------

    private void loadRegions() {
        step = 0;
        clear(LocaleController.getString(R.string.MilliyLoading));
        MilliyData.get("regions", 24L * 3600 * 1000, body -> {
            try {
                regions = new JSONArray(body);
                showRegions();
            } catch (Exception e) {
                showError(this::loadRegions);
            }
        }, () -> showError(this::loadRegions));
    }

    private void showRegions() {
        step = 0;
        clear(LocaleController.getString(R.string.MilliyChooseRegion));
        for (int i = 0; i < regions.length(); i++) {
            JSONObject r = regions.optJSONObject(i);
            addRow(r.optString("name"), false, v -> {
                selectedRegion = r;
                showDistricts();
            });
        }
    }

    private void showDistricts() {
        step = 1;
        clear(selectedRegion.optString("name") + " — " + LocaleController.getString(R.string.MilliyChooseDistrict));
        if (!required && !MilliyData.isJoinDeclined(currentAccount)) {
            addInfo(LocaleController.getString(R.string.MilliyRegionAutoJoinInfo));
        }
        JSONArray ds = selectedRegion.optJSONArray("districts");
        for (int i = 0; ds != null && i < ds.length(); i++) {
            JSONObject d = ds.optJSONObject(i);
            addRow(d.optString("name"), false, v -> {
                districtCode = d.optString("code");
                districtName = selectedRegion.optString("name") + ", " + d.optString("name");
                selectDistrict();
            });
        }
    }

    /**
     * Tuman tanlandi. Akkaunt qo'shilayotganda (yoki avval rad etgan bo'lsa) bitta oddiy taklif ko'rsatiladi;
     * hududni Sozlamalardan o'zgartirganda kanallarga darhol fonda qo'shiladi.
     */
    private void selectDistrict() {
        if (required || MilliyData.isJoinDeclined(currentAccount)) {
            showOffer();
        } else {
            applyDistrict(true);
        }
    }

    private void showOffer() {
        step = 2;
        list.removeAllViews();
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(24));

        FrameLayout circle = new FrameLayout(getContext());
        circle.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(40), Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton), 0.12f)));
        ImageView icon = new ImageView(getContext());
        icon.setImageResource(R.drawable.msg_channel);
        icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN));
        circle.addView(icon, LayoutHelper.createFrame(40, 40, Gravity.CENTER));
        box.addView(circle, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));

        TextView title = new TextView(getContext());
        title.setText(LocaleController.getString(R.string.MilliyRegionOfferTitle));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.CENTER);
        box.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView place = new TextView(getContext());
        place.setText(districtName);
        place.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        place.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        place.setGravity(Gravity.CENTER);
        box.addView(place, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView desc = new TextView(getContext());
        desc.setText(LocaleController.getString(R.string.MilliyRegionOfferText));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        desc.setLineSpacing(AndroidUtilities.dp(3), 1.0f);
        desc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        desc.setGravity(Gravity.CENTER);
        box.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 28));

        TextView yes = new TextView(getContext());
        yes.setText(LocaleController.getString(R.string.MilliyRegionOfferYes));
        yes.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        yes.setTypeface(AndroidUtilities.bold());
        yes.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        yes.setGravity(Gravity.CENTER);
        yes.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 8));
        yes.setOnClickListener(v -> applyDistrict(true));
        box.addView(yes, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 8));

        TextView no = new TextView(getContext());
        no.setText(LocaleController.getString(R.string.MilliyRegionOfferNo));
        no.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        no.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        no.setGravity(Gravity.CENTER);
        no.setBackground(Theme.getSelectorDrawable(false));
        no.setOnClickListener(v -> applyDistrict(false));
        box.addView(no, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        list.addView(box, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void applyDistrict(boolean join) {
        MilliyData.setDistrict(currentAccount, districtCode, districtName);
        MilliyData.setJoinDeclined(currentAccount, !join);
        if (join) {
            MilliyData.joinDistrictChannels(currentAccount);
        }
        BaseFragment prev = getParentLayout() != null && getParentLayout().getFragmentStack().size() > 1
                ? getParentLayout().getFragmentStack().get(getParentLayout().getFragmentStack().size() - 2) : null;
        finishFragment();
        if (prev != null) {
            BulletinFactory.of(prev).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(join ? R.string.MilliyRegionSaved : R.string.MilliyRegionSavedOnly)).show();
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (step == 2) {
            if (invoked) showDistricts();
            return false;
        }
        if (step == 1) {
            if (invoked) showRegions();
            return false;
        }
        return !required;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return !required && step == 0;
    }
}
