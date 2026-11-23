package org.telegram.milliy;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanMono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Har bir chat uchun yozuv rejimi: asl holida / kirillda / lotinda ko'rsatish.
 * Faqat ekrandagi ko'rinish o'zgaradi, xabarning o'zi va yuborilgan matn o'zgarmaydi.
 * Havola, @username, #teg, kod va formula qismlari o'girilmaydi.
 */
public class MilliyChatScript {

    public static final int ORIGINAL = 0, CYRILLIC = 1, LATIN = 2;

    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z]+(?:['ʻʼ‘’`][A-Za-z]+)*");
    private static final Pattern CYRILLIC_WORD = Pattern.compile("[\\u0400-\\u04FF]+");

    private static String key(int account, long dialogId) {
        return "script_" + account + "_" + dialogId;
    }

    public static int mode(int account, long dialogId) {
        return MilliyPro.prefs().getInt(key(account, dialogId), ORIGINAL);
    }

    public static void setMode(int account, long dialogId, int mode) {
        if (mode == ORIGINAL) {
            MilliyPro.prefs().edit().remove(key(account, dialogId)).apply();
        } else {
            MilliyPro.prefs().edit().putInt(key(account, dialogId), mode).apply();
        }
    }

    /** O'giriladigan so'z ichida havola/kod va shu kabi span bormi. */
    private static boolean protectedRange(Spanned text, int start, int end) {
        for (Object span : text.getSpans(start, end, Object.class)) {
            if (span instanceof URLSpan || span instanceof ClickableSpan || span instanceof URLSpanMono) {
                return true;
            }
            if (span instanceof TextStyleSpan && ((TextStyleSpan) span).isMono()) {
                return true;
            }
        }
        return false;
    }

    public static CharSequence convert(CharSequence text, int mode) {
        if (text == null || text.length() == 0 || mode == ORIGINAL) {
            return text;
        }
        SpannableStringBuilder sb = text instanceof SpannableStringBuilder ? (SpannableStringBuilder) text : new SpannableStringBuilder(text);
        Matcher m = (mode == CYRILLIC ? LATIN_WORD : CYRILLIC_WORD).matcher(sb.toString());
        // oxiridan boshiga: almashtirish uzunlikni o'zgartiradi, oldingi indekslar buzilmaydi
        java.util.ArrayList<int[]> ranges = new java.util.ArrayList<>();
        while (m.find()) {
            int s = m.start();
            if (s > 0 && (sb.charAt(s - 1) == '@' || sb.charAt(s - 1) == '#' || sb.charAt(s - 1) == '/' || sb.charAt(s - 1) == '$')) {
                continue; // @username, #teg, /buyruq
            }
            ranges.add(new int[]{s, m.end()});
        }
        for (int i = ranges.size() - 1; i >= 0; i--) {
            int s = ranges.get(i)[0], e = ranges.get(i)[1];
            if (protectedRange(sb, s, e)) {
                continue;
            }
            String word = sb.subSequence(s, e).toString();
            String out = mode == CYRILLIC ? MilliyTranslit.toCyrillic(word) : MilliyTranslit.toLatin(word);
            if (!out.equals(word)) {
                sb.replace(s, e, out);
            }
        }
        return sb;
    }

    /** Chat ⋮ menyusidan: rejim tanlash oynasi. Tanlangach chat qayta chiziladi. */
    public static void choose(BaseFragment fragment, long dialogId, Runnable onChanged) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final int account = fragment.getCurrentAccount();
        final int current = mode(account, dialogId);
        String[] items = {
                LocaleController.getString(R.string.MilliyScriptOriginal),
                LocaleController.getString(R.string.MilliyScriptCyrillic),
                LocaleController.getString(R.string.MilliyScriptLatin)
        };
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyScriptTitle));
        items[current] = "✓ " + items[current];
        b.setItems(items, (d, which) -> {
            if (which != current) {
                setMode(account, dialogId, which);
                onChanged.run();
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
    }
}
