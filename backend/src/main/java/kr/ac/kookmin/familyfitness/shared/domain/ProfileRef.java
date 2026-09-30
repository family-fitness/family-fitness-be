package kr.ac.kookmin.familyfitness.shared.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI 서비스에 넘기는 불투명 프로필 참조. 이름·생년월일·계정 식별자를 담지 않는다.
 * 같은 프로필은 항상 같은 ref 가 나오며(UUID 의 SHA-256 앞 16자), 역매핑은 요청을 보낸 서버가 {@link #indexOf} 로 보관한다.
 * UUID 앞부분을 자르면 시드처럼 규칙적인 ID 끼리 충돌하므로 해시를 쓴다.
 */
public final class ProfileRef {
    private ProfileRef() {}

    public static String of(UUID profileId) {
        byte[] digest = sha256(profileId.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append("%02x".formatted(b));
        }
        return "p_" + hex.substring(0, 16);
    }

    public static Map<String, UUID> indexOf(Collection<UUID> profileIds) {
        Map<String, UUID> index = new LinkedHashMap<>();
        for (UUID profileId : profileIds) {
            index.put(of(profileId), profileId);
        }
        return index;
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
