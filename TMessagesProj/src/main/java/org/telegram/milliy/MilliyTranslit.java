package org.telegram.milliy;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.util.HashMap;

/** O'zbek lotin ↔ kirill o'girish (oflayn, qoidaga asoslangan). */
public class MilliyTranslit {

    private static final HashMap<Character, String> CYR2LAT = new HashMap<>();

    static {
        String[][] map = {
                {"а", "a"}, {"б", "b"}, {"в", "v"}, {"г", "g"}, {"ғ", "gʻ"}, {"д", "d"}, {"ё", "yo"},
                {"ж", "j"}, {"з", "z"}, {"и", "i"}, {"й", "y"}, {"к", "k"}, {"қ", "q"}, {"л", "l"},
                {"м", "m"}, {"н", "n"}, {"о", "o"}, {"п", "p"}, {"р", "r"}, {"с", "s"}, {"т", "t"},
                {"у", "u"}, {"ў", "oʻ"}, {"ф", "f"}, {"х", "x"}, {"ҳ", "h"}, {"ч", "ch"}, {"ш", "sh"},
                {"щ", "sh"}, {"ъ", "ʼ"}, {"ы", "i"}, {"ь", ""}, {"э", "e"}, {"ю", "yu"}, {"я", "ya"},
        };
        for (String[] p : map) {
            CYR2LAT.put(p[0].charAt(0), p[1]);
        }
    }

    private static boolean isCyr(char c) {
        return (c >= 'А' && c <= 'я') || c == 'ё' || c == 'Ё' || c == 'ў' || c == 'Ў' || c == 'қ' || c == 'Қ' || c == 'ғ' || c == 'Ғ' || c == 'ҳ' || c == 'Ҳ';
    }

    private static boolean isLat(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isApostrophe(char c) {
        return c == '\'' || c == 'ʻ' || c == 'ʼ' || c == '‘' || c == '’' || c == '`';
    }

    private static boolean isVowelCyr(char c) {
        return "аеёиоуўэюяы".indexOf(Character.toLowerCase(c)) >= 0;
    }

    /** Matnda kirill harflari ko'pmi? */
    public static boolean isMostlyCyrillic(CharSequence text) {
        int cyr = 0, lat = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCyr(c)) cyr++;
            else if (isLat(c)) lat++;
        }
        return cyr > lat;
    }

    private static String applyCase(String lat, boolean upper, boolean nextUpper) {
        if (!upper || lat.isEmpty()) return lat;
        if (lat.length() == 1 || nextUpper) return lat.toUpperCase();
        return Character.toUpperCase(lat.charAt(0)) + lat.substring(1);
    }

    public static String toLatin(CharSequence text) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char lc = Character.toLowerCase(c);
            boolean upper = c != lc;
            char prev = i > 0 ? text.charAt(i - 1) : ' ';
            char next = i + 1 < text.length() ? text.charAt(i + 1) : ' ';
            boolean nextUpper = Character.isUpperCase(next) || (!Character.isLetter(next) && i > 0 && Character.isUpperCase(prev));
            String out;
            if (lc == 'е') {
                // so'z boshida va unlidan keyin "ye"
                out = (!isCyr(prev) || isVowelCyr(prev) || prev == 'ъ' || prev == 'ь') ? "ye" : "e";
            } else if (lc == 'ц') {
                out = (isCyr(prev) && isVowelCyr(prev)) ? "ts" : "s";
            } else {
                out = CYR2LAT.get(lc);
            }
            if (out == null) {
                sb.append(c);
            } else {
                sb.append(applyCase(out, upper, nextUpper));
            }
        }
        return sb.toString();
    }

    public static String toCyrillic(CharSequence text) {
        StringBuilder sb = new StringBuilder(text.length());
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            char lc = Character.toLowerCase(c);
            boolean upper = c != lc;
            char next = i + 1 < n ? Character.toLowerCase(text.charAt(i + 1)) : ' ';
            char prev = i > 0 ? text.charAt(i - 1) : ' ';
            String out = null;
            int skip = 0;
            if ((lc == 'o' || lc == 'g') && isApostrophe(next)) {
                out = lc == 'o' ? "ў" : "ғ";
                skip = 1;
            } else if (lc == 's' && next == 'h') {
                out = "ш"; skip = 1;
            } else if (lc == 'c' && next == 'h') {
                out = "ч"; skip = 1;
            } else if (lc == 'y' && (next == 'o' || next == 'u' || next == 'a' || next == 'e')) {
                if (next == 'o' && i + 2 < n && isApostrophe(text.charAt(i + 2))) {
                    out = "й"; // y + oʻ
                } else {
                    out = next == 'o' ? "ё" : next == 'u' ? "ю" : next == 'a' ? "я" : "е";
                    skip = 1;
                }
            } else if (isApostrophe(c)) {
                // tutuq belgisi: harfdan keyin kelsa — ъ
                if (isLat(prev) && isLat(next)) {
                    sb.append(Character.isUpperCase(prev) ? 'Ъ' : 'ъ');
                } else {
                    sb.append(c);
                }
                continue;
            } else if (isLat(c)) {
                switch (lc) {
                    case 'a': out = "а"; break;
                    case 'b': out = "б"; break;
                    case 'c': out = "с"; break;
                    case 'd': out = "д"; break;
                    case 'e': out = isLat(prev) ? "е" : "э"; break;
                    case 'f': out = "ф"; break;
                    case 'g': out = "г"; break;
                    case 'h': out = "ҳ"; break;
                    case 'i': out = "и"; break;
                    case 'j': out = "ж"; break;
                    case 'k': out = "к"; break;
                    case 'l': out = "л"; break;
                    case 'm': out = "м"; break;
                    case 'n': out = "н"; break;
                    case 'o': out = "о"; break;
                    case 'p': out = "п"; break;
                    case 'q': out = "қ"; break;
                    case 'r': out = "р"; break;
                    case 's': out = "с"; break;
                    case 't': out = "т"; break;
                    case 'u': out = "у"; break;
                    case 'v': out = "в"; break;
                    case 'w': out = "в"; break;
                    case 'x': out = "х"; break;
                    case 'y': out = "й"; break;
                    case 'z': out = "з"; break;
                }
            }
            if (out == null) {
                sb.append(c);
                continue;
            }
            sb.append(upper ? out.toUpperCase() : out);
            i += skip;
        }
        return sb.toString();
    }

    /** Xabar matnini o'girib, natijani oynada ko'rsatadi. */
    public static void showAlert(BaseFragment fragment, CharSequence text) {
        if (fragment == null || fragment.getParentActivity() == null || TextUtils.isEmpty(text)) {
            return;
        }
        final boolean cyr = isMostlyCyrillic(text);
        final String result = cyr ? toLatin(text) : toCyrillic(text);
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString(cyr ? R.string.MilliyTranslitToLatin : R.string.MilliyTranslitToCyrillic));
        builder.setMessage(result);
        builder.setPositiveButton(LocaleController.getString(R.string.Copy), (d, w) -> {
            AndroidUtilities.addToClipboard(result);
            BulletinFactory.of(fragment).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Close), null);
        fragment.showDialog(builder.create());
    }
}
