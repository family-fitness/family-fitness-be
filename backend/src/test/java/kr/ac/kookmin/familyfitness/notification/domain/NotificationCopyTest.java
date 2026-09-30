package kr.ac.kookmin.familyfitness.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 알림 문구 — FE 목(fe:src/mocks/notifications.ts)과 목이 쓰는 josa · callName · PAST 를 그대로 옮겼는지. */
class NotificationCopyTest {
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "서준, 이",
        "엄마, 가",
        "아빠, 가",
        "은영, 이",
        "하늘, 이",
        "'왕복오래달리기(15m)', 가",
        "'민준 (초1)', 이",
        "'지우  ', 가",
        "Tom, 가",
        "3, 이",
        "2, 가",
        "10, 이",
        "'', 가"
    })
    @DisplayName("이/가 — 받침이 있으면 「이」. 끝 괄호 · 공백은 떼고, 숫자는 읽는 소리로, 한글이 아니면 받침 없음(fe josa)")
    void 이가(String word, String expected) {
        assertThat(NotificationCopy.iGa(word)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "운동 한 칸을 처음 끝내요, 운동 한 칸을 처음 끝냈어요",
        "3일 이어서 움직여요, 3일 이어서 움직였어요",
        "'준비, 본운동, 정리를 한 번에 다 해요', '준비, 본운동, 정리를 한 번에 다 했어요'",
        "모두 합쳐 30분 움직여요, 모두 합쳐 30분 움직였어요",
        "토요일이나 일요일에 운동해요, 토요일이나 일요일에 운동했어요",
        "보호자와 같은 날 운동해요, 보호자와 같은 날 운동했어요",
        "키와 몸무게를 새로 재요, 키와 몸무게를 새로 쟀어요",
        "칭찬 스티커를 처음 받아요, 칭찬 스티커를 처음 받았어요",
        "여섯 가지 힘을 기르는 운동을 다 해 봐요, 여섯 가지 힘을 기르는 운동을 다 해 봤어요",
        "이미 지난 말이에요, 이미 지난 말이에요"
    })
    @DisplayName("업적 설명은 지난 말로 — 앞에서부터 처음 맞는 끝말 하나만 바꾸고, 맞는 것이 없으면 그대로(목 PAST)")
    void 지난_말(String description, String expected) {
        assertThat(NotificationCopy.earned(description)).isEqualTo(expected);
    }

    @Test
    @DisplayName("제목 · 본문 — 목의 문장 그대로")
    void 문장() {
        assertThat(NotificationCopy.kidDoneTitle("서준")).isEqualTo("서준이 운동을 마쳤어요");
        assertThat(NotificationCopy.kidThanksTitle("서준")).isEqualTo("서준이 고맙대요");
        assertThat(NotificationCopy.kidThanksTitle("지우")).isEqualTo("지우가 고맙대요");
        assertThat(NotificationCopy.kidThanksBody("heart", "고마워")).isEqualTo("사랑해");
        assertThat(NotificationCopy.kidThanksBody("모르는것", "고마워")).isEqualTo("고마워");
        assertThat(NotificationCopy.kidThanksBody(null, null)).isNull();
        assertThat(NotificationCopy.praiseTitle("엄마", "star")).isEqualTo("엄마가 스티커를 붙여 줬어요");
        assertThat(NotificationCopy.praiseTitle("아빠", null)).isEqualTo("아빠가 칭찬을 보냈어요");
        assertThat(NotificationCopy.praiseTitle("엄마", "모르는것")).isEqualTo("엄마가 칭찬을 보냈어요");
        assertThat(NotificationCopy.missionReadyTitle()).isEqualTo("새 운동이 생겼어요");
        assertThat(NotificationCopy.achievementTitle("사흘 이어서")).isEqualTo("새 업적: 사흘 이어서");
        assertThat(NotificationCopy.remeasureTitle("서준")).isEqualTo("서준 키와 몸무게를 새로 재 볼까요");
        assertThat(NotificationCopy.remeasureBody(30)).isEqualTo("지난번에 잰 지 30일");
    }

    @Test
    @DisplayName("스티커 이름은 FE 스티커 표 열두 장 그대로, 모르는 코드는 지어내지 않는다")
    void 스티커_이름() {
        assertThat(Stream.of(
                                "star", "thumb", "medal", "heart", "crown", "flag", "sprout", "sparkle", "clap",
                                "rocket", "sun", "kiumi")
                        .map(StickerLabels::labelOf)
                        .toList())
                .containsExactly(
                        "최고야", "엄지척", "멋져", "사랑해", "대단해", "끝까지 했네", "쑥쑥 자라라", "반짝반짝", "짝짝짝", "슝 빨라졌어", "오늘도 맑음",
                        "꼭 안아 줄게");
        assertThat(StickerLabels.labelOf("emoji")).isNull();
        assertThat(StickerLabels.labelOf(null)).isNull();
    }

    @Test
    @DisplayName("서버가 짓는 문장에는 「오늘」 · 탓하는 말(오래됐어요 · 안 했어요 · 부족 · 미달 · 하위)이 없다 — 남는 말이라서")
    void 금지어() {
        List<String> copies = List.of(
                NotificationCopy.kidDoneTitle("서준"),
                NotificationCopy.kidThanksTitle("서준"),
                NotificationCopy.praiseTitle("엄마", "star"),
                NotificationCopy.praiseTitle("엄마", null),
                NotificationCopy.missionReadyTitle(),
                NotificationCopy.achievementTitle("첫걸음"),
                NotificationCopy.remeasureTitle("서준"),
                NotificationCopy.remeasureBody(45));
        assertThat(copies)
                .allSatisfy(copy -> assertThat(copy).doesNotContain("오늘", "오래됐어요", "안 했어요", "부족", "미달", "하위"));
    }
}
