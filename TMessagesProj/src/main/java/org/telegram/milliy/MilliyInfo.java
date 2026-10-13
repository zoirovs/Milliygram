package org.telegram.milliy;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.Locale;

/** Sozlamalardagi ob-havo va valyuta kursi. */
public class MilliyInfo {

    private static final long WEATHER_TTL = 30 * 60 * 1000L;
    private static final long CURRENCY_TTL = 60 * 60 * 1000L;

    public static String weatherPath(int account) {
        String d = MilliyData.getDistrict(account);
        return d == null ? null : "weather?district=" + MilliyData.enc(d);
    }

    /** Ma'lumotlarni yangilaydi va tayyor bo'lganda onUpdate chaqiradi. */
    public static void refresh(int account, Runnable onUpdate) {
        MilliyData.get("currency", CURRENCY_TTL, b -> onUpdate.run(), null);
        String wp = weatherPath(account);
        if (wp != null) {
            MilliyData.get(wp, WEATHER_TTL, b -> onUpdate.run(), null);
        }
    }

    public static String emoji(int code) {
        if (code == 0) return "☀️";
        if (code <= 2) return "🌤";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄️";
        if (code >= 95) return "⛈";
        return "🌧";
    }

    private static String deg(int t) {
        return (t > 0 ? "+" : "") + t + "°";
    }

    private static String money(double v) {
        return String.format(Locale.US, "%,.2f", v).replace(",", " ");
    }

    // ---------- Batafsil oynalar ----------

    public static void showWeather(BaseFragment fragment) {
        String wp = weatherPath(fragment.getCurrentAccount());
        if (wp == null) {
            fragment.presentFragment(new MilliyRegionActivity(false));
            return;
        }
        MilliyData.get(wp, WEATHER_TTL, body -> {
            try {
                JSONObject w = new JSONObject(body);
                StringBuilder sb = new StringBuilder();
                sb.append("📍 ").append(w.optString("region")).append(", ").append(w.optString("place")).append("\n\n");
                sb.append(emoji(w.optInt("code"))).append(" ").append(deg(w.getInt("temp"))).append(" — ").append(w.optString("text")).append("\n");
                sb.append(LocaleController.formatString(R.string.MilliyWeatherFeels, deg(w.optInt("feels")))).append("\n");
                sb.append(LocaleController.formatString(R.string.MilliyWeatherWind, w.optInt("wind"), w.optInt("humidity"))).append("\n\n");
                JSONArray days = w.optJSONArray("days");
                String[] names = {LocaleController.getString(R.string.MilliyToday), LocaleController.getString(R.string.MilliyTomorrow), LocaleController.getString(R.string.MilliyAfterTomorrow)};
                for (int i = 0; days != null && i < days.length() && i < names.length; i++) {
                    JSONObject d = days.getJSONObject(i);
                    sb.append(emoji(d.optInt("code"))).append(" ").append(names[i]).append(": ")
                            .append(deg(d.optInt("min"))).append(" … ").append(deg(d.optInt("max")))
                            .append(", ").append(d.optString("text")).append("\n");
                }
                show(fragment, LocaleController.getString(R.string.MilliyWeather), sb.toString().trim());
            } catch (Exception e) {
                show(fragment, LocaleController.getString(R.string.MilliyWeather), LocaleController.getString(R.string.MilliyNetworkError));
            }
        }, () -> show(fragment, LocaleController.getString(R.string.MilliyWeather), LocaleController.getString(R.string.MilliyNetworkError)));
    }

    private static String flag(String code) {
        switch (code) {
            case "USD": return "🇺🇸";
            case "EUR": return "🇪🇺";
            case "RUB": return "🇷🇺";
            case "KZT": return "🇰🇿";
            case "CNY": return "🇨🇳";
            case "GBP": return "🇬🇧";
            case "TRY": return "🇹🇷";
            case "KRW": return "🇰🇷";
            case "AED": return "🇦🇪";
            case "JPY": return "🇯🇵";
            default: return "💱";
        }
    }

    public static void showCurrency(BaseFragment fragment) {
        MilliyData.get("currency", CURRENCY_TTL, body -> {
            try {
                JSONArray arr = new JSONArray(body);
                StringBuilder sb = new StringBuilder();
                if (arr.length() > 0) {
                    sb.append(LocaleController.formatString(R.string.MilliyCurrencyDate, arr.getJSONObject(0).optString("date"))).append("\n\n");
                }
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    double diff = c.optDouble("diff");
                    sb.append(flag(c.getString("code"))).append(" ").append(c.optInt("nominal", 1)).append(" ").append(c.getString("code"))
                            .append(" = ").append(money(c.getDouble("rate"))).append(" soʻm  ")
                            .append(diff > 0 ? "▲" : diff < 0 ? "▼" : "").append(diff != 0 ? money(Math.abs(diff)) : "").append("\n");
                }
                show(fragment, LocaleController.getString(R.string.MilliyCurrency), sb.toString().trim());
            } catch (Exception e) {
                show(fragment, LocaleController.getString(R.string.MilliyCurrency), LocaleController.getString(R.string.MilliyNetworkError));
            }
        }, () -> show(fragment, LocaleController.getString(R.string.MilliyCurrency), LocaleController.getString(R.string.MilliyNetworkError)));
    }

    private static void show(BaseFragment fragment, String title, String text) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(title);
        builder.setMessage(text);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        fragment.showDialog(builder.create());
    }
}
