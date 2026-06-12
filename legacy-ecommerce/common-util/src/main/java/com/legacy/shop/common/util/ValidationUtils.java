package com.legacy.shop.common.util;

import java.util.regex.Pattern;

/**
 * 검증 유틸.
 */
public class ValidationUtils {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE = Pattern.compile("^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$");

    private ValidationUtils() {
    }

    public static boolean isEmail(String s) {
        return s != null && EMAIL.matcher(s).matches();
    }

    public static boolean isPhone(String s) {
        return s != null && PHONE.matcher(s).matches();
    }

    public static boolean isPositive(int n) {
        return n > 0;
    }
}
