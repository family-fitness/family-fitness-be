package kr.ac.kookmin.familyfitness.coaching.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 연령대 거르기(팀 결정: 65세 이상은 성인 영상을 똑같이 쓴다)와 공단 영상 줄 펼치기. */
class ExerciseClipTest {
    private static final VideoMedia WALK_MEDIA = new VideoMedia(
            "https://openapi.kspo.or.kr/web/video/0AUDLJ08S_00181.mp4",
            "https://openapi.kspo.or.kr/web/image/0AUDLJ08S_00181/0AUDLJ08S_00181_SC_00001.jpeg");

    /** 「공통」 걷기(00181) 클립 행. V165 는 첫 줄(성인) 값을 클립 행에 둔다. */
    private static final ExerciseClip WALK = new ExerciseClip(
            "0AUDLJ08S_00181-0",
            "0AUDLJ08S_00181",
            1,
            "걷기",
            "걷기",
            "걷기",
            FitnessFactor.CARDIO,
            SessionPhase.MAIN,
            0,
            40,
            false,
            true,
            false,
            true,
            AgeGroup.ADULT,
            "kspo",
            true,
            WALK_MEDIA);

    /** AI 표 두 줄(성인 · 청소년)을 펼친 후보. 클립 목록(ExerciseClipPersistenceAdapter)이 내는 모양이다. */
    private static final List<ExerciseClip> WALK_ROWS = List.of(
            WALK.withLabel(AgeGroup.ADULT, FitnessFactor.CARDIO, SessionPhase.MAIN),
            WALK.withLabel(AgeGroup.ADOLESCENT, FitnessFactor.CARDIO, SessionPhase.MAIN));

    @Test
    @DisplayName("65세 이상은 성인 구간을 받고 청소년 구간은 받지 않는다 — 「공통」 영상은 성인 줄로 한 번 받는다")
    void 어르신은_성인_줄만_받는다() {
        assertThat(WALK_ROWS.stream().filter(it -> it.suits(AgeGroup.SENIOR)))
                .singleElement()
                .extracting(ExerciseClip::ageGroup)
                .isEqualTo(AgeGroup.ADULT);
        assertThat(WALK.withLabel(AgeGroup.ADOLESCENT, FitnessFactor.CARDIO, SessionPhase.MAIN)
                        .suits(AgeGroup.SENIOR))
                .isFalse();
    }

    @Test
    @DisplayName("청소년 · 성인은 제 줄 하나씩 받고, 성인은 어르신 구간을 받지 않는다")
    void 청소년과_성인은_제_줄을_받는다() {
        assertThat(WALK_ROWS.stream().filter(it -> it.suits(AgeGroup.ADOLESCENT)))
                .singleElement()
                .extracting(ExerciseClip::ageGroup)
                .isEqualTo(AgeGroup.ADOLESCENT);
        assertThat(WALK_ROWS.stream().filter(it -> it.suits(AgeGroup.ADULT)))
                .singleElement()
                .extracting(ExerciseClip::ageGroup)
                .isEqualTo(AgeGroup.ADULT);
        assertThat(WALK_ROWS).noneMatch(it -> it.suits(AgeGroup.YOUTH));
        assertThat(WALK.withLabel(AgeGroup.SENIOR, null, SessionPhase.MAIN).suits(AgeGroup.ADULT))
                .isFalse();
    }

    @Test
    @DisplayName("withLabel 은 연령대 · 요인 · 단계만 바꾸고 clipId · 제목 · 주소는 그대로 둔다")
    void withLabel_은_연령대_요인_단계만_바꾼다() {
        ExerciseClip warmup = WALK.withLabel(AgeGroup.ADOLESCENT, null, SessionPhase.WARMUP);

        assertThat(warmup.ageGroup()).isEqualTo(AgeGroup.ADOLESCENT);
        assertThat(warmup.factor()).isNull();
        assertThat(warmup.phase()).isEqualTo(SessionPhase.WARMUP);
        assertThat(warmup)
                .usingRecursiveComparison()
                .ignoringFields("ageGroup", "factor", "phase")
                .isEqualTo(WALK);
    }
}
