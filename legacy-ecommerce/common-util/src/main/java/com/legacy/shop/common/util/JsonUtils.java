package com.legacy.shop.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 유틸. ObjectMapper 를 static 으로 하나 공유한다.
 *
 * 오류 정책은 fail-fast 로 통일한다(CU2): 직렬화/역직렬화 실패 시 둘 다 {@link JsonException}
 * 을 던진다. (이전: toJson 은 실패를 삼키고 null 을 반환, fromJson 은 RuntimeException 을 던져
 * 호출부가 일관 처리할 수 없었다.)
 */
public class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    public static String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            throw new JsonException("json serialize fail", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new JsonException("json parse fail", e);
        }
    }
}
