package org.telegram.milliy;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.LaunchActivity;

import java.nio.charset.StandardCharsets;

/**
 * AI yordamchi (Milliygram serveri orqali): xabarni tushuntirish va qisqartirish, yozilayotgan matnni
 * tuzatish yoki boshqa uslubda qayta yozish. O'zbek tilida (lotin/kirill — matn yozuvida).
 */
public class MilliyAI {

    interface Done {
        void run(String text);
    }

    private static void call(String action, String text, Done done) {
        final BaseFragment f = LaunchActivity.getLastFragment();
        if (f == null || f.getParentActivity() == null || text == null || text.trim().isEmpty()) {
            return;
        }
        byte[] body;
        try {
            body = new JSONObject().put("action", action).put("text", text.length() > 4000 ? text.substring(0, 4000) : text)
                    .put("user_id", UserConfig.getInstance(f.getCurrentAccount()).getClientUserId()).toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return;
        }
        final AlertDialog progress = new AlertDialog(f.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.show();
        MilliyShield.http("POST", "ai", body, "application/json", 70000, (code, resp) -> {
            try {
                progress.dismiss();
            } catch (Exception ignore) {
            }
            BaseFragment cur = LaunchActivity.getLastFragment();
            String out = null;
            if (code == 200 && resp != null) {
                try {
                    out = new JSONObject(resp).optString("text", null);
                } catch (Exception ignore) {
                }
            }
            if (out == null || out.isEmpty()) {
                int msg = code == 503 ? R.string.MilliyAiDisabled : code == 429 ? R.string.MilliyAiLimit : code == 422 ? R.string.MilliyAiRefused : R.string.MilliyNetworkError;
                if (cur != null) {
                    BulletinFactory.of(cur).createErrorBulletin(LocaleController.getString(msg)).show();
                }
                return;
            }
            done.run(out);
        });
    }

    /** Xabar menyusidan: tushuntirish yoki qisqartirish — natija oynada, nusxalash mumkin. */
    public static void showForMessage(BaseFragment fragment, MessageObject m) {
        if (fragment.getParentActivity() == null || m == null || m.messageText == null) {
            return;
        }
        final String text = m.messageText.toString();
        CharSequence[] items = {LocaleController.getString(R.string.MilliyAiExplain), LocaleController.getString(R.string.MilliyAiSummarize)};
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        b.setTitle(LocaleController.getString(R.string.MilliyAi));
        b.setItems(items, (d, w) -> call(w == 0 ? "explain" : "summarize", text, out -> {
            BaseFragment cur = LaunchActivity.getLastFragment();
            if (cur == null || cur.getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder r = new AlertDialog.Builder(cur.getParentActivity());
            r.setTitle(items[w]);
            r.setMessage(out);
            r.setPositiveButton(LocaleController.getString(R.string.OK), null);
            r.setNeutralButton(LocaleController.getString(R.string.Copy), (d2, w2) -> AndroidUtilities.addToClipboard(out));
            cur.showDialog(r.create());
        }));
        fragment.showDialog(b.create());
    }

    /** Yuborish tugmasi menyusidan: yozilgan matnni tuzatish/qayta yozish — natija maydonga qo'yiladi. */
    public static void showForInput(ChatActivityEnterView view) {
        BaseFragment f = LaunchActivity.getLastFragment();
        if (view == null || f == null || f.getParentActivity() == null) {
            return;
        }
        final CharSequence field = view.getFieldText();
        if (field == null || field.toString().trim().isEmpty()) {
            return;
        }
        final String[] actions = {"fix", "rewrite_polite", "rewrite_formal", "rewrite_simple", "rewrite_ad"};
        CharSequence[] items = {
                LocaleController.getString(R.string.MilliyAiFix),
                LocaleController.getString(R.string.MilliyAiPolite),
                LocaleController.getString(R.string.MilliyAiFormal),
                LocaleController.getString(R.string.MilliyAiSimple),
                LocaleController.getString(R.string.MilliyAiAd),
        };
        AlertDialog.Builder b = new AlertDialog.Builder(f.getParentActivity(), f.getResourceProvider());
        b.setTitle(LocaleController.getString(R.string.MilliyAiImprove));
        b.setItems(items, (d, w) -> call(actions[w], field.toString(), out -> {
            view.setFieldText(out);
            BaseFragment cur = LaunchActivity.getLastFragment();
            if (cur != null) {
                BulletinFactory.of(cur).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.MilliyAiReady)).show();
            }
        }));
        f.showDialog(b.create());
    }
}
