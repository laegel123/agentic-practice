package com.legacy.shop.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JsonUtils 의 오류 처리 정책(CU2)을 고정한다.
 *
 * 직렬화/역직렬화 실패는 둘 다 JsonException 으로 통일한다(fail-fast). 이전에는 toJson 이
 * 실패를 삼키고 null 을 반환했는데, 이제는 toJson 도 예외를 던진다.
 */
class JsonUtilsTest {

    @Test
    void roundTrip_serializesAndDeserializes() {
        String json = JsonUtils.toJson(Map.of("a", 1, "b", "x"));

        @SuppressWarnings("unchecked")
        Map<String, Object> back = JsonUtils.fromJson(json, Map.class);

        assertThat(back).containsEntry("a", 1).containsEntry("b", "x");
    }

    @Test
    void toJson_onSerializationFailure_throwsJsonException() {
        // 순환 참조는 Jackson 직렬화를 실패시킨다 → null 삼키지 않고 예외를 던져야 한다.
        Map<String, Object> circular = new HashMap<>();
        circular.put("self", circular);

        assertThatThrownBy(() -> JsonUtils.toJson(circular))
                .isInstanceOf(JsonException.class);
    }

    @Test
    void fromJson_onParseFailure_throwsJsonException() {
        assertThatThrownBy(() -> JsonUtils.fromJson("{not valid json", Map.class))
                .isInstanceOf(JsonException.class);
    }
}
