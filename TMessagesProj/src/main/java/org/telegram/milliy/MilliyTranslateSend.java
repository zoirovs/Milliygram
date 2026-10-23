package org.telegram.milliy;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.LaunchActivity;

/**
 * Yuborish tugmasini bosib turganda «Tarjima qilib yuborish»: yozilgan matn tanlangan tilga o'giriladi va
 * maydonga qo'yiladi — foydalanuvchi ko'rib, keyin yuboradi. Bepul tarjimon ishlatiladi (Premium shart emas).
 */
public class MilliyTranslateSend {

    private static final String[] CODES = {"ru", "en", "uz", "tr", "kk", "ky", "tg", "zh", "ar", "ko", "de"};
    private static final String[] NAMES = {"Русский", "English", "O'zbekcha", "Türkçe", "Қазақша", "Кыргызча", "Тоҷикӣ", "中文", "العربية", "한국어", "Deutsch"};

    public static void show(ChatActivityEnterView view) {
        BaseFragment f = LaunchActivity.getLastFragment();
        if (view == null || f == null || f.getParentActivity() == null) {
            return;
        }
        final CharSequence field = view.getFieldText();
        if (field == null || field.toString().trim().isEmpty()) {
            return;
        }
        String last = MilliyPro.prefs().getString("translate_send_last", null);
        CharSequence[] items = new CharSequence[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            items[i] = (CODES[i].equals(last) ? "✓ " : "") + NAMES[i];
        }
        AlertDialog.Builder b = new AlertDialog.Builder(f.getParentActivity(), f.getResourceProvider());
        b.setTitle(LocaleController.getString(R.string.MilliyTranslateSend));
        b.setItems(items, (d, which) -> {
            String to = CODES[which];
            MilliyPro.prefs().edit().putString("translate_send_last", to).apply();
            TranslateAlert2.alternativeTranslate(field.toString(), null, to, (result, rateLimit) -> AndroidUtilities.runOnUIThread(() -> {
                BaseFragment cur = LaunchActivity.getLastFragment();
                if (result == null || result.isEmpty()) {
                    if (cur != null) {
                        BulletinFactory.of(cur).createErrorBulletin(LocaleController.getString(Boolean.TRUE.equals(rateLimit) ? R.string.TranslationFailedAlert1 : R.string.TranslationFailedAlert2)).show();
                    }
                    return;
                }
                view.setFieldText(result);
                if (cur != null) {
                    BulletinFactory.of(cur).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.MilliyTranslateReady)).show();
                }
            }));
        });
        f.showDialog(b.create());
    }
}
