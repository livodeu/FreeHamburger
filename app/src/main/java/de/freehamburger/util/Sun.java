package de.freehamburger.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import de.freehamburger.BuildConfig;

/**
 *
 */
public class Sun {

    private static final String TAG = "Sun";
    private final double sunriseGMT;
    private final double sunsetGMT;

    /**
     * Calculates sunrise and sunset for the given day.<br>
     * based on the formula found <a href="https://lexikon.astronomie.info/zeitgleichung/">here</a><br>
     * The capital of the country determined by the user's Locale is used as location.
     * @param c Calendar (optional, only its {@link Calendar#DAY_OF_YEAR DAY_OF_YEAR} field and its {@link TimeZone} are used)
     * @return Sun
     */
    public static Sun sunriseAndSunset(@Nullable Calendar c) {
        final String country = Locale.getDefault().getCountry();
        final double lat, lon;
        switch (country) {
            // europe, roughly from NW to SE
            case "GL": lat = 64.175; lon = -51.738889; break;
            case "IS": lat = 64.133333; lon = -21.933333; break;
            case "FO": lat = 62.011667; lon = -6.7675; break;
            case "NO": lat = 59.916667; lon = 10.733333; break;
            case "DK": lat = 55.676111; lon = 12.568333; break;
            case "SE": lat = 59.329444; lon = 18.068611; break;
            case "AX": lat = 60.1; lon = 19.933333; break;
            case "FI": lat = 60.170833; lon = 24.9375; break;
            case "EE": lat = 59.437222; lon = 24.745278; break;
            case "LT": lat = 54.683333; lon =  25.283333; break;
            case "LV": lat = 56.948889; lon = 24.106389; break;

            case "IE": lat = 53.349722; lon = -6.260278; break;
            case "GB": lat = 51.507222; lon = -0.1275; break;
            case "GG": lat = 49.45; lon = -2.58; break;
            case "JE": lat = 49.187; lon = -2.107; break;
            case "FR": lat = 48.8567; lon = 2.3508; break;
            case "BE": lat = 50.833333; lon = 4.; break;
            case "NL": lat = 52.366667; lon = 4.9; break;
            case "LU": lat = 49.6106; lon = 6.1328; break;
            case "CH": lat = 46.95; lon = 7.45; break;
            case "LI": lat = 47.141; lon = 9.521; break;
            case "AT": lat = 48.2; lon = 16.366667; break;
            case "CZ": lat = 50.083333; lon = 14.416667; break;
            case "SK": lat = 48.143889; lon = 17.109722; break;
            case "UK": lat = 50.45; lon = 30.523333; break;

            case "PT": lat = 38.713889; lon = -9.139444; break;
            case "ES": lat = 40.383333; lon = -3.716667; break;
            case "AD": lat = 42.5; lon = 1.5; break;
            case "MC": lat = 43.733333; lon = 7.416667; break;
            case "VA":
            case "IT": lat = 41.9; lon = 12.5; break;
            case "SI": lat = 46.055556; lon = 14.508333; break;
            case "HR": lat = 45.816667; lon = 15.983333; break;
            case "BA": lat = 43.866667; lon = 18.416667; break;
            case "ME": lat = 42.441286; lon = 19.262892; break;
            case "MK": lat = 42; lon = 21.433333; break;
            case "BG": lat = 42.7; lon = 23.33; break;
            case "RO": lat = 44.4325; lon = 26.103889; break;
            case "MD": lat = 47; lon = 28.916667; break;

            case "MT": lat = 35.884444; lon = 14.506944; break;
            case "CY": lat = 35.166667; lon = 33.366667; break;

            // extra-european entities
            case "DZ": lat = 36.753889; lon = 3.058889; break;
            case "CA": lat = 45.416667; lon = -75.683333; break;
            case "PS":
            case "IL": lat = 31.783333; lon = 35.216667; break;
            case "JP": lat = 35.683333; lon = 139.683333; break;
            case "AU": lat = -35.3075; lon = 149.124417; break;
            case "NA": lat = -22.57; lon = 17.083611; break;

            default: lat = 52.53; lon = 13.4;
        }
        return sunriseAndSunset(c, lat, lon);
    }

    /**
     * Calculates sunrise and sunset for the given day.<br>
     * based on the formula found <a href="https://lexikon.astronomie.info/zeitgleichung/">here</a>
     * @param c Calendar (optional, only its {@link Calendar#DAY_OF_YEAR DAY_OF_YEAR} field and its {@link TimeZone} are used)
     * @param lat latitude
     * @param lon longitude
     * @return Sun
     */
    @NonNull
    public static Sun sunriseAndSunset(@Nullable Calendar c, double lat, double lon) {
        if (c == null) c = new GregorianCalendar();
        double latRad = lat * Math.PI / 180.;
        int t = c.get(Calendar.DAY_OF_YEAR);
        double declSun = .409526325277017 * Math.sin(.0169060504029192 * (t - 80.0856919827619));
        double h = -.0145;
        double delta = 12. * Math.acos((Math.sin(h) - Math.sin(latRad) * Math.sin(declSun)) / (Math.cos(latRad) * Math.cos(declSun))) / Math.PI;
        double sunriseWoz = 12. - delta;
        double sunsetWoz = 12. + delta;
        double wozmoz = -.170869921174742 * Math.sin(.0336997028793971 * t + .465419984181394) - .129890681040717 * Math.sin(.0178674832556871 * t - .167936777524864);
        double sunriseMoz = sunriseWoz - wozmoz;
        double sunsetMoz = sunsetWoz - wozmoz;
        double sunriseGMT = sunriseMoz - (lon / 15.);
        double sunsetGMT = sunsetMoz - (lon / 15.);

        TimeZone tz = c.getTimeZone();
        int offset = tz.getOffset(c.getTimeInMillis());
        //double sunriseLocal = sunriseGMT + offset;
        //double sunsetLocal = sunsetGMT + offset;

        Sun sun = new Sun(sunriseGMT, sunsetGMT);

        if (BuildConfig.DEBUG) {
            DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
            NumberFormat nf = NumberFormat.getInstance();
            String day = df.format(c.getTime());
            String msg = "On " + day + " at " + nf.format(lat) + "°N/S, " + nf.format(lon) + "°E/W, "
                    + sun.toString()
                    + " (Time zone offset is "
                    + nf.format(offset / 3_600_000.)
                    + " h)"
                    ;
            android.util.Log.i(TAG, msg);
        }

        return sun;
    }

    /**
     * Constructor.
     * @param sunriseGMT time of sunrise
     * @param sunsetGMT time of sunset
     */
    private Sun(double sunriseGMT, double sunsetGMT) {
        super();
        this.sunriseGMT = sunriseGMT;
        this.sunsetGMT = sunsetGMT;
    }

    /*public double getSunriseGMT() {
        return sunriseGMT;
    }

    public double getSunsetGMT() {
        return sunsetGMT;
    }*/

    /**
     * @param c Calendar
     * @return {@code true} if the time specified by the given Calendar is between sunrise and sunset
     * @throws NullPointerException if {@code c} is {@code null}
     */
    public boolean isDay(@NonNull final Calendar c) {
        int h = c.get(Calendar.HOUR_OF_DAY);
        int m = c.get(Calendar.MINUTE);
        TimeZone tz = c.getTimeZone();
        double offset = tz.getOffset(c.getTimeInMillis()) / 3_600_000.;
        final double sunrise = sunriseGMT + offset;
        final double sunset = sunsetGMT + offset;
        if (h > sunrise && h < Math.floor(sunset)) return true;
        if (h == sunrise) {
            int sunriseMinutes = (int)Math.floor((sunrise - Math.floor(sunrise)) * 60.);
            return m >= sunriseMinutes;
        } else if (h == sunset) {
            int sunsetMinutes = (int)Math.floor((sunset - Math.floor(sunset)) * 60.);
            return m < sunsetMinutes;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        int sunriseHours = (int)Math.floor(sunriseGMT);
        int sunriseMinutes = (int)Math.floor((sunriseGMT - sunriseHours) * 60.);
        int sunsetHours = (int)Math.floor(sunsetGMT);
        int sunsetMinutes = (int)Math.floor((sunsetGMT - sunsetHours) * 60.);
        return "Sunrise is at " + sunriseHours + ":" + (sunriseMinutes < 10 ? "0" : "") + sunriseMinutes
                + ", sunset is at " + sunsetHours + ":" + (sunsetMinutes < 10 ? "0" : "") + sunsetMinutes
                + " GMT";
    }
}
