package com.legacy.shop.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * 날짜 유틸.
 */
public class DateUtils {

    // DateTimeFormatter 는 불변이라 thread-safe — static 공유가 안전하다(R3, 종전 SimpleDateFormat 대체).
    // 패턴에 zone/offset 이 없으므로 java.util.Date 와의 변환은 시스템 기본 시간대로 해석한다
    // (종전 SimpleDateFormat 의 기본 동작과 동일).
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateUtils() {
    }

    public static String format(Date date) {
        LocalDateTime ldt = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        return FMT.format(ldt);
    }

    /**
     * "yyyy-MM-dd HH:mm:ss" 문자열을 시스템 기본 시간대 기준으로 파싱한다.
     * 형식이 맞지 않으면 {@link DateTimeParseException}(unchecked)을 던진다 — 예외를 삼키고 {@code null}
     * 을 반환해 호출부 NPE 를 유발하던 종전 동작(C4)을 fail-fast 로 바꾼 것이다.
     */
    public static Date parse(String s) {
        LocalDateTime ldt = LocalDateTime.parse(s, FMT);
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static String today() {
        return LocalDate.now().toString(); // ISO-8601 = "yyyy-MM-dd"
    }

    /** 주문 시각 등에 쓰는 현재시각. 항상 UTC 로 박는다. */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("UTC"));
    }

    /**
     * 오늘 날짜(서버 로컬 기준). 쿠폰 만료 등 달력 날짜 비교용.
     * 주의: UTC 로 저장되는 주문 시각({@link #now()}) 집계에는 쓰지 말 것 — 기준이 어긋나
     * 자정 부근에서 날짜 경계가 틀어진다(B7). 그런 집계는 UTC 기준 날짜를 써야 한다.
     */
    public static LocalDate localToday() {
        return LocalDate.now();
    }
}
