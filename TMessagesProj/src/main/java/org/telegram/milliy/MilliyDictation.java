package org.telegram.milliy;

import android.app.Activity;
import android.content.Intent;
import android.speech.RecognizerIntent;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatActivityEnterView;

import java.util.ArrayList;

/**
 * Ovoz bilan yozish: telefonning nutqni tanish xizmati (Google) tanlangan tilda tinglaydi, matn yozish maydoniga qo'shiladi.
 * Server va kalit kerak emas.
 */
public class MilliyDictation {

    public static final int REQUEST_CODE = 7702;

    private static final String[] CODES = {"uz-UZ", "ru-RU", "en-US", "tr-TR", "kk-KZ"};
    private static final String[] NAMES = {"O'zbekcha", "Русский", "English", "Türkçe", "Қазақша"};

    public static void start(ChatActivity chat) {
        if (chat.getParentActivity() == null) {
            return;
        }
        String lang = MilliyPro.prefs().getString("dictation_lang", "uz-UZ");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, LocaleController.getString(R.string.MilliyDictationPrompt));
        try {
            chat.startActivityForResult(intent, REQUEST_CODE);
        } catch (Exception e) {
            BulletinFactory.of(chat).createErrorBulletin(LocaleController.getString(R.string.MilliyDictationNoService)).show();
        }
    }

    public static void onResult(ChatActivity chat, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty() || results.get(0) == null) {
            return;
        }
        ChatActivityEnterView input = chat.getChatActivityEnterView();
        if (input == null) {
            return;
        }
        CharSequence cur = input.getFieldText();
        String text = results.get(0).trim();
        input.setFieldText(cur == null || cur.length() == 0 ? text : cur + " " + text);
    }

    /** Sozlamalarda: ovoz bilan yozish tili. */
    public static void chooseLanguage(org.telegram.ui.ActionBar.BaseFragment fragment, Runnable after) {
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(LocaleController.getString(R.string.MilliyDictationLang));
        b.setItems(NAMES, (d, w) -> {
            MilliyPro.prefs().edit().putString("dictation_lang", CODES[w]).apply();
            if (after != null) {
                after.run();
            }
        });
        fragment.showDialog(b.create());
    }

    public static String languageName() {
        String lang = MilliyPro.prefs().getString("dictation_lang", "uz-UZ");
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equals(lang)) {
                return NAMES[i];
            }
        }
        return NAMES[0];
    }
}
