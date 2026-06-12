package com.legacy.shop.common.util;

import java.util.List;

/**
 * 문자열 유틸. (commons-lang3 에 이미 있는 것들을 다시 만들어 쓴다)
 */
public class StringUtils {

    private StringUtils() {
    }

    public static boolean isEmpty(String s) {
        return s == null || s.length() == 0;
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().length() == 0;
    }

    public static String nvl(String s, String def) {
        return isEmpty(s) ? def : s;
    }

    /** 카드번호 마스킹: 뒤 4자리만 남긴다. */
    public static String maskCard(String cardNo) {
        if (cardNo == null || cardNo.length() < 4) {
            return cardNo;
        }
        String last4 = cardNo.substring(cardNo.length() - 4);
        return "****-****-****-" + last4;
    }

    public static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
