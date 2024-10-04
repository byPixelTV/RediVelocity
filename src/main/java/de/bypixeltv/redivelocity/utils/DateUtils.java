package de.bypixeltv.redivelocity.utils;

import jakarta.inject.Singleton;
import java.text.SimpleDateFormat;
import java.util.Date;

@Singleton
public class DateUtils {

    public static String asDateString(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }
}