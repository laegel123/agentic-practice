package com.legacy.shop.common.util;

/**
 * JSON 직렬화/역직렬화 실패를 나타내는 런타임 예외.
 *
 * JsonUtils 의 toJson/fromJson 이 실패하면 일관되게 이 예외를 던진다(CU2). 호출부는
 * 단일 타입으로 처리할 수 있다. (이전: toJson 은 null 을 삼키고, fromJson 은 일반
 * RuntimeException 을 던져 처리 정책이 어긋났다.)
 */
public class JsonException extends RuntimeException {

    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
