package com.legacy.shop.common.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 날짜 유틸.
 */
public class DateUtils {

    // 주의: SimpleDateFormat 은 thread-safe 하지 않다. 그런데 static 으로 공유하고 있다.
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private DateUtils() {
    }

    public static String format(Date date) {
        return SDF.format(date);
    }

    public static Date parse(String s) {
        try {
            return SDF.parse(s);
        } catch (ParseException e) {
            return null; // 그냥 null 리턴
        }
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    /** 주문 시각 등에 쓰는 현재시각. 항상 UTC 로 박는다. */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("UTC"));
    }

    /** 오늘 날짜(서버 로컬 기준). 집계/조회에서 이걸 쓴다. */
    public static LocalDate localToday() {
        return LocalDate.now();
    }
}
