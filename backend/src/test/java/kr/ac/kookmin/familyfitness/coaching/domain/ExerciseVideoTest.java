package kr.ac.kookmin.familyfitness.coaching.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import kr.ac.kookmin.familyfitness.coaching.support.Videos;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExerciseVideoTest {
    @Test
    @DisplayName("연령 필터는 라벨 범위와 교차하는 영상만 통과시킨다")
    void 연령_필터는_라벨_범위와_교차하는_영상만_통과시킨다() {
        ExerciseVideo youth = Videos.video("a", 7, 12);
        ExerciseVideo family = Videos.video("b", 4, 64);
        ExerciseVideo adult = Videos.video("c", 19, 64);

        assertThat(youth.matches(AgeGroup.YOUTH, null)).isTrue();
        assertThat(family.matches(AgeGroup.YOUTH, null)).isTrue();
        assertThat(adult.matches(AgeGroup.YOUTH, null)).isFalse();
        assertThat(adult.matches(AgeGroup.ADULT, null)).isTrue();
        assertThat(youth.matches(AgeGroup.TODDLER, null)).isFalse();
    }

    @Test
    @DisplayName("라벨 없는 영상은 아이 연령대에는 나가지 않고 성인·어르신에게는 나간다")
    void 라벨_없는_영상은_아이_연령대에는_나가지_않고_성인_어르신에게는_나간다() {
        ExerciseVideo unlabeled = Videos.video("u", null, null);

        assertThat(unlabeled.matches(AgeGroup.TODDLER, null)).isFalse();
        assertThat(unlabeled.matches(AgeGroup.YOUTH, null)).isFalse();
        assertThat(unlabeled.matches(AgeGroup.ADOLESCENT, null)).isFalse();
        assertThat(unlabeled.matches(AgeGroup.ADULT, null)).isTrue();
        assertThat(unlabeled.matches(AgeGroup.SENIOR, null)).isTrue();
        assertThat(unlabeled.matches(null, null)).isTrue();
    }

    @Test
    @DisplayName("요인 필터는 CSV 라벨을 정확히 나눠 비교한다")
    void 요인_필터는_CSV_라벨을_정확히_나눠_비교한다() {
        ExerciseVideo v = Videos.video("f", 7, 12, "유연성,근지구력", 600);

        assertThat(v.matches(null, "유연성")).isTrue();
        assertThat(v.matches(null, "근지구력")).isTrue();
        assertThat(v.matches(null, "근력")).isFalse();
        assertThat(VideoLabel.parseFactors(" 심폐지구력 , 순발력 ,")).containsExactly("심폐지구력", "순발력");
        assertThat(VideoLabel.parseFactors(null)).isEmpty();
    }

    @Test
    @DisplayName("배지는 조용함·좁은 공간 OK·준비물 없음 순서로 문구가 된다")
    void 배지는_조용함_좁은_공간_OK_준비물_없음_순서로_문구가_된다() {
        assertThat(Videos.video("q").getBadges()).containsExactly("조용함", "좁은 공간 OK", "준비물 없음");
        assertThat(Videos.video("n", 7, 12, "유연성", 600, "NORMAL", "OUTDOOR", "매트")
                        .getBadges())
                .isEmpty();
        assertThat(Videos.video("m", 7, 12, "유연성", 600, "NORMAL", "SMALL_ROOM", "매트")
                        .getBadges())
                .containsExactly("좁은 공간 OK");
    }

    @Test
    @DisplayName("URL·썸네일·적립 분은 영상 id 와 길이에서 나온다")
    void URL_썸네일_적립_분은_영상_id_와_길이에서_나온다() {
        ExerciseVideo v = Videos.video("IdpXx2gm90o", 7, 12, "유연성", 601);

        assertThat(v.getUrl()).isEqualTo("https://www.youtube.com/watch?v=IdpXx2gm90o");
        assertThat(v.getThumbnailUrl()).isEqualTo("https://i.ytimg.com/vi/IdpXx2gm90o/hqdefault.jpg");
        assertThat(v.getCreditMinutes()).isEqualTo(11);
        assertThat(Videos.video("x", 7, 12, "유연성", null).getCreditMinutes()).isEqualTo(0);
    }

    @Test
    @DisplayName("커서 페이지는 size 개를 자르고 더 있으면 마지막 id 를 다음 커서로 낸다")
    void 커서_페이지는_size_개를_자르고_더_있으면_마지막_id_를_다음_커서로_낸다() {
        List<String> ids = List.of("a", "b", "c", "d", "e");

        CursorPage<String> first = CursorPage.of(ids, 2, Function.identity());
        assertThat(first.items()).containsExactly("a", "b");
        assertThat(first.nextCursor()).isEqualTo("b");

        CursorPage<String> last =
                CursorPage.of(ids.stream().filter(it -> it.compareTo("d") > 0).toList(), 2, Function.identity());
        assertThat(last.items()).containsExactly("e");
        assertThat(last.nextCursor()).isNull();

        CursorPage<String> exact = CursorPage.of(List.of("a", "b"), 2, Function.identity());
        assertThat(exact.nextCursor()).isNull();
        assertThrows(IllegalArgumentException.class, () -> CursorPage.of(ids, 0, Function.identity()));
    }

    @Test
    @DisplayName("시청 진행률은 최대값만 남고 0_9 에 닿으면 한 번만 적립 대상이 된다")
    void 시청_진행률은_최대값만_남고_0_9_에_닿으면_한_번만_적립_대상이_된다() {
        Instant at = Instant.parse("2026-09-09T01:00:00Z");
        VideoInteraction i = VideoInteraction.start(UUID.randomUUID(), UUID.randomUUID(), "v", at);

        i.watch(0.5, 300, at);
        assertThat(i.isCompleted()).isFalse();
        assertThat(i.isCreditable()).isFalse();

        i.watch(0.95, 570, at.plusSeconds(1));
        assertThat(i.isCompleted()).isTrue();
        assertThat(i.isCreditable()).isTrue();
        i.markCredited(at.plusSeconds(1));
        assertThat(i.isCreditable()).isFalse();

        i.watch(0.3, 100, at.plusSeconds(2));
        assertThat(i.getMaxProgress()).isEqualTo(0.95);
        assertThat(i.getWatchedSec()).isEqualTo(570);
        assertThat(i.getLastWatchedAt()).isEqualTo(at.plusSeconds(2));
    }

    @Test
    @DisplayName("인용 없는 답변은 서버가 no_citation_generated 거부로 바꾼다")
    void 인용_없는_답변은_서버가_no_citation_generated_거부로_바꾼다() {
        Instant at = Instant.parse("2026-09-09T01:00:00Z");
        UUID conv = UUID.randomUUID();
        UUID profile = UUID.randomUUID();

        CoachMessage uncited =
                CoachMessage.assistant(UUID.randomUUID(), conv, profile, "답", List.of(), false, null, at);
        assertThat(uncited.isRefused()).isTrue();
        assertThat(uncited.getRefusalReason()).isEqualTo("no_citation_generated");

        CoachMessage cited = CoachMessage.assistant(
                UUID.randomUUID(),
                conv,
                profile,
                "답 [1]",
                List.of(new MessageCitation(1, "라벨", "c", "라벨", null)),
                false,
                null,
                at);
        assertThat(cited.isRefused()).isFalse();
        assertThat(cited.getCitations()).hasSize(1);

        CoachMessage refused =
                CoachMessage.assistant(UUID.randomUUID(), conv, profile, "", List.of(), true, "medical_query", at);
        assertThat(refused.isRefused()).isTrue();
        assertThat(refused.getRefusalReason()).isEqualTo("medical_query");
    }
}
