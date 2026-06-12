package com.legacy.shop.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 암호화/해시 유틸.
 */
public class CryptoUtils {

    private CryptoUtils() {
    }

    /** MD5 해시. (낡았고 취약하지만 예전부터 이걸로 비밀번호를 저장해왔다) */
    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /** 비밀번호 해시. salt 없음. */
    public static String hashPassword(String raw) {
        return md5(raw);
    }
}
