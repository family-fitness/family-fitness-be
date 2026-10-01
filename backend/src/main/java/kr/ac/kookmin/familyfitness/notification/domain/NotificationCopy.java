package kr.ac.kookmin.familyfitness.notification.domain;

import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * 알림 문구. 서버가 짓고 화면은 그대로 내보낸다(FE 요청서 ASKS 4장). 규칙은 FE 목(fe:src/mocks/notifications.ts)과
 * 목이 쓰는 함수(fe:src/lib/utils.ts josa · fe:src/lib/family.ts callName)를 그대로 옮겼다.
 *
 * <pre>
 * KID_DONE       「서준이 운동을 마쳤어요」                · 본문 = 아이가 보낸 한마디
 * KID_THANKS     「서준이 고맙대요」                       · 본문 = 스티커 이름(모르는 스티커면 한마디)
 * PRAISE         「은영이 스티커를 붙여 줬어요」 · 스티커가 없거나 모르는 스티커면 「은영이 칭찬을 보냈어요」 · 본문 = 한마디
 *                — 보낸 보호자의 프로필 이름이다. 「엄마」 · 「아빠」 로 박아 부르지 않는다
 * MISSION_READY  「새 운동이 생겼어요」                     · 본문 = 미션 제목
 * ACHIEVEMENT    「새 업적: 3일 연속」                     · 본문 = 업적 설명을 과거형으로(「3일 연속 운동했어요」)
 * REMEASURE      「서준 키와 몸무게를 다시 측정해 볼까요?」 · 본문 = 「마지막 측정 후 30일」
 * </pre>
 *
 * 남는 말이라 「오늘」 을 넣지 않는다. 「오래됐어요」 · 「안 했어요」 처럼 탓하는 말도 쓰지 않는다(ASKS 4장 · FE 규칙 11).
 */
public final class NotificationCopy {
    /** 받침이 있는 숫자(영 · 일 · 삼 · 육 · 칠 · 팔). 이 · 사 · 오 · 구는 받침이 없다. */
    private static final String DIGITS_WITH_FINAL = "013678";

    /** 끝에 붙은 괄호는 읽지 않는다 — 「왕복오래달리기(15m)」 는 「달리기」 에 조사가 붙는다. */
    private static final Pattern TRAILING_PAREN = Pattern.compile("\\s*\\([^()]*\\)$");

    /**
     * 업적 설명(조건 「~해요」)을 지난 말로 바꾸는 표. 앞에서부터 처음 맞는 끝말 하나만 바꾼다 — 「해 봐요」 가 「해요」 보다 앞이다.
     * 맞는 것이 없으면 그대로 둔다(fe:src/mocks/notifications.ts PAST).
     */
    private static final List<String[]> PAST = List.of(
            new String[] {"해 봐요", "해 봤어요"},
            new String[] {"받아요", "받았어요"},
            new String[] {"움직여요", "움직였어요"},
            new String[] {"끝내요", "끝냈어요"},
            new String[] {"재요", "쟀어요"},
            new String[] {"해요", "했어요"});

    private NotificationCopy() {}

    public static String kidDoneTitle(String kidName) {
        return subject(kidName) + " 운동을 마쳤어요";
    }

    public static String kidThanksTitle(String kidName) {
        return subject(kidName) + " 고맙대요";
    }

    /** 스티커 이름, 모르는 스티커면 한마디. */
    public static @Nullable String kidThanksBody(@Nullable String stickerId, @Nullable String message) {
        String label = StickerLabels.labelOf(stickerId);
        return label != null ? label : message;
    }

    /** {@code senderCall} 은 {@link #callNameForKid} 로 부른 이름이다. */
    public static String praiseTitle(String senderCall, @Nullable String stickerId) {
        return StickerLabels.labelOf(stickerId) != null
                ? subject(senderCall) + " 스티커를 붙여 줬어요"
                : subject(senderCall) + " 칭찬을 보냈어요";
    }

    public static String missionReadyTitle() {
        return "새 운동이 생겼어요";
    }

    public static String achievementTitle(String achievementTitle) {
        return "새 업적: " + achievementTitle;
    }

    public static String remeasureTitle(String kidName) {
        return kidName + " 키와 몸무게를 다시 측정해 볼까요?";
    }

    public static String remeasureBody(long daysSinceLastTest) {
        return "마지막 측정 후 " + daysSinceLastTest + "일";
    }

    /** 업적 설명을 지난 말로. 알림은 이미 일어난 일이라 「받아요」 가 오면 아직 안 받은 것처럼 읽힌다. */
    public static String earned(String description) {
        for (String[] pair : PAST) {
            if (description.endsWith(pair[0])) {
                return description.substring(0, description.length() - pair[0].length()) + pair[1];
            }
        }
        return description;
    }

    /** 이름 + 받침에 맞춘 「이/가」. 「서준이」 · 「철수가」 — 「서준이가」 가 아니다. */
    public static String subject(String name) {
        return name + iGa(name);
    }

    /**
     * 앞말의 받침으로 「이」 · 「가」 를 고른다(fe:src/lib/utils.ts josa 의 「이가」).
     *
     * <ol>
     *   <li>끝의 괄호와 뒤 공백을 떼고 마지막 글자를 본다. 없으면 「가」
     *   <li>숫자는 읽는 소리로 — 0 · 1 · 3 · 6 · 7 · 8 은 받침이 있다
     *   <li>한글 음절(가~힣)이 아니면 받침 없음
     *   <li>한글 음절은 (글자 − 0xAC00) % 28 이 0 이 아니면 받침이 있다
     * </ol>
     */
    public static String iGa(String word) {
        String stem = TRAILING_PAREN.matcher(word.stripTrailing()).replaceFirst("");
        if (stem.isEmpty()) return "가";
        char last = stem.charAt(stem.length() - 1);
        if (last >= '0' && last <= '9') return DIGITS_WITH_FINAL.indexOf(last) >= 0 ? "이" : "가";
        if (last < 0xAC00 || last > 0xD7A3) return "가";
        return (last - 0xAC00) % 28 != 0 ? "이" : "가";
    }
}
