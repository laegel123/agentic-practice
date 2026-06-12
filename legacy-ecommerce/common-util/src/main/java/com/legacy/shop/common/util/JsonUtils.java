package com.legacy.shop.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 유틸. ObjectMapper 를 static 으로 하나 공유한다.
 */
public class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    public static String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return null; // 직렬화 실패하면 그냥 null
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("json parse fail", e);
        }
    }
}
