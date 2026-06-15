package com.legacy.shop.common.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * 암호화/해시 유틸.
 *
 * <p>비밀번호 해시는 PBKDF2(HMAC-SHA256, 임의 salt 포함)를 쓴다. 반환 문자열 자체에 알고리즘·
 * 반복수·salt 가 들어 있어(self-describing) 검증 시 별도 salt 보관이 필요 없다. 과거 MD5·무 salt
 * 로 저장된 비밀번호는 {@link #verifyPassword(String, String)} 가 그대로 검증해 주므로(점진
 * 마이그레이션), 로그인 시 {@link #needsRehash(String)} 로 골라 재해시하면 된다. (CU1)
 */
public class CryptoUtils {

    private static final String PBKDF2_ALG = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 210_000;   // OWASP 권장 하한(HMAC-SHA256)
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final String PBKDF2_PREFIX = "pbkdf2$";
    private static final SecureRandom RANDOM = new SecureRandom();   // SecureRandom 은 thread-safe

    private CryptoUtils() {
    }

    /**
     * 비밀번호를 PBKDF2(HMAC-SHA256, 매번 새 임의 salt)로 해시한다.
     * 형식: {@code pbkdf2$<iterations>$<base64 salt>$<base64 hash>}. salt 가 매번 달라
     * 같은 비밀번호라도 해시가 매번 다르다(레인보우테이블·동일비번 노출 차단).
     */
    public static String hashPassword(String raw) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(raw.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS);
        Base64.Encoder b64 = Base64.getEncoder();
        return PBKDF2_PREFIX + PBKDF2_ITERATIONS + "$"
                + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    /**
     * 평문이 저장된 해시와 일치하는지 검증한다.
     *
     * <p>저장값이 PBKDF2 형식이면 같은 파라미터로 재계산해 상수시간 비교하고, 그 외(레거시 MD5)는
     * MD5 비교로 폴백한다. 덕분에 기존 MD5 로 저장된 비밀번호도 그대로 검증돼 점진 마이그레이션이
     * 가능하다. null/빈 입력은 안전하게 {@code false}.
     */
    public static boolean verifyPassword(String raw, String storedHash) {
        if (raw == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        if (storedHash.startsWith(PBKDF2_PREFIX)) {
            return verifyPbkdf2(raw, storedHash);
        }
        // 레거시 MD5 폴백 (상수시간 비교)
        return constantTimeEquals(md5(raw), storedHash);
    }

    /**
     * 저장된 해시가 구식(레거시 MD5)이라 재해시가 필요한지 여부.
     * 로그인 성공 시 이 값이 {@code true} 면 {@link #hashPassword(String)} 로 다시 해시해 저장하면
     * 사용자가 로그인할 때마다 PBKDF2 로 자연스럽게 옮겨간다(upgrade-on-login).
     */
    public static boolean needsRehash(String storedHash) {
        return storedHash == null || !storedHash.startsWith(PBKDF2_PREFIX);
    }

    private static boolean verifyPbkdf2(String raw, String storedHash) {
        String[] parts = storedHash.split("\\$");   // ["pbkdf2", iterations, salt, hash]
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(raw.toCharArray(), salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {   // 형식 깨짐(파싱/base64 실패)
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
            return SecretKeyFactory.getInstance(PBKDF2_ALG).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * MD5 해시. (낡았고 취약 — 레거시 비밀번호 검증 폴백 용도로만 남겨둔다. 신규 해시엔 쓰지 말 것.)
     *
     * @deprecated 비밀번호는 {@link #hashPassword(String)}(PBKDF2)로 해시한다. (CU1)
     */
    @Deprecated
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
}
