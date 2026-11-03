package org.telegram.milliy;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/** Tizim o'rnatish oynasi yopilgach ko'rsatiladigan tushuntirish. */
public class MilliyInstallBlockedActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(LocaleController.getString(R.string.MilliyGuardBlockedTitle))
                .setMessage(LocaleController.getString(R.string.MilliyGuardBlockedText))
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .setOnDismissListener(d -> finish())
                .show();
    }
}
