package kr.ac.kookmin.familyfitness.identity.domain;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * 초대 코드 값 객체. 6자리 대문자+숫자이며 헷갈리는 0/O·1/I 는 쓰지 않는다. 발급 후 7일에 만료.
 * 살아 있는 코드가 있으면 다시 발급해도 같은 코드다. 만료됐을 때만 새 코드로 바뀐다({@link Family#issueInvite}).
 */
public record ClaimCode(String code, Instant expiresAt) {
    public static final int LENGTH = 6;
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final Duration TTL = Duration.ofDays(7);

    public ClaimCode {
        if (code.length() != LENGTH || code.chars().anyMatch(c -> ALPHABET.indexOf(c) < 0)) {
            throw new IllegalArgumentException("초대 코드 형식이 아니다: " + code);
        }
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public static ClaimCode generate(Instant now, SecureRandom random) {
        char[] chars = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            chars[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new ClaimCode(new String(chars), now.plus(TTL));
    }

    /** 사용자가 입력한 코드는 대소문자를 가리지 않는다. */
    public static String normalize(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    /** 정규화한 값이 코드 모양(여섯 글자 · 알파벳 안)인가. 아니면 찾아볼 것도 없이 없는 코드다. */
    public static boolean isWellFormed(String normalized) {
        return normalized.length() == LENGTH && normalized.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0);
    }
}
