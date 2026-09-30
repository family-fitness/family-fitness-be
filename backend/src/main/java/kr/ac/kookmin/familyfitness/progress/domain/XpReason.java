package kr.ac.kookmin.familyfitness.progress.domain;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * 최근 경험치 줄의 문장. FE 목(fe:src/mocks/progress.ts progressOf)과 같은 문장을 서버가 짓는다 — 전환기 몫이다. FE 가 kind ·
 * fromProfileId 로 문장을 짓게 되면 이 클래스와 XpLineView 의 reason 을 걷는다.
 *
 * <pre>
 * SESSION_DONE  「운동을 했어요」
 * MISSION_DONE  「운동을 다 했어요」
 * STICKER       「은영이 붙여 준 스티커」(붙인 사람의 프로필 이름) · 붙인 사람을 모르면 「가족이 붙여 준 스티커」
 * REMEASURE     「키 · 몸무게를 새로 쟀어요」
 * </pre>
 */
public final class XpReason {
    /** 붙인 사람을 모를 때(가족을 떠났거나 지워진 프로필) 부르는 말. */
    static final String UNKNOWN_SENDER = "가족";

    /** 받침이 있는 숫자(영 · 일 · 삼 · 육 · 칠 · 팔). 이 · 사 · 오 · 구는 받침이 없다. */
    private static final String DIGITS_WITH_FINAL = "013678";

    /** 끝에 붙은 괄호는 읽지 않는다. */
    private static final Pattern TRAILING_PAREN = Pattern.compile("\\s*\\([^()]*\\)$");

    private XpReason() {}

    /**
     * @param senderCall 스티커를 붙인 사람의 프로필 이름. 보호자도 「엄마」 · 「아빠」 가 아니라 이름이다. STICKER 가 아니면 쓰지 않는다
     */
    public static String of(XpKind kind, @Nullable String senderCall) {
        return switch (kind) {
            case SESSION_DONE -> "운동을 했어요";
            case MISSION_DONE -> "운동을 다 했어요";
            case REMEASURE -> "키 · 몸무게를 새로 쟀어요";
            case STICKER -> {
                String who = senderCall == null || senderCall.isBlank() ? UNKNOWN_SENDER : senderCall;
                yield who + iGa(who) + " 붙여 준 스티커";
            }
        };
    }

    /**
     * 앞말의 받침으로 「이」 · 「가」 를 고른다(fe:src/lib/utils.ts josa 의 「이가」, notification NotificationCopy.iGa 와 같은 규칙).
     * 끝의 괄호를 떼고 마지막 글자를 보며, 숫자는 읽는 소리로, 한글 음절이 아니면 받침 없음으로 친다.
     */
    static String iGa(String word) {
        String stem = TRAILING_PAREN.matcher(word.stripTrailing()).replaceFirst("");
        if (stem.isEmpty()) return "가";
        char last = stem.charAt(stem.length() - 1);
        if (last >= '0' && last <= '9') return DIGITS_WITH_FINAL.indexOf(last) >= 0 ? "이" : "가";
        if (last < 0xAC00 || last > 0xD7A3) return "가";
        return (last - 0xAC00) % 28 != 0 ? "이" : "가";
    }
}
