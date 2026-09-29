package kr.ac.kookmin.familyfitness.progress.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.api.SessionDone;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 레벨 곡선 · 적립 키 · 업적 조건 — FE 목(fe:src/mocks/progress.ts)과 결정 23 · 24 · 27. */
class ProgressRulesTest {
    private final UUID missionId = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final Instant now = Instant.parse("2026-09-24T10:00:00Z");

    private SessionDone done(boolean completed, SessionDone.Verification verifiedBy) {
        return new SessionDone(
                UUID.randomUUID(),
                UUID.randomUUID(),
                missionId,
                3,
                SessionDone.Phase.MAIN,
                FitnessFactor.AGILITY,
                completed,
                verifiedBy,
                LocalDate.of(2026, 9, 24),
                now);
    }

    @Test
    @DisplayName("레벨 구간은 0 · 80 · 200 … 2160, 구간 시작값이면 그 레벨이고 Lv.10 은 다음이 없다")
    void 레벨_구간() {
        assertThat(LevelCurve.levelOf(0)).isEqualTo(1);
        assertThat(LevelCurve.levelOf(79)).isEqualTo(1);
        assertThat(LevelCurve.levelOf(80)).isEqualTo(2);
        assertThat(LevelCurve.levelOf(500)).isEqualTo(4);
        assertThat(LevelCurve.levelOf(2160)).isEqualTo(10);
        assertThat(LevelCurve.levelOf(99_999)).isEqualTo(10);
        assertThat(LevelCurve.floorOf(4)).isEqualTo(360);
        assertThat(LevelCurve.nextFloorOf(4)).isEqualTo(560);
        assertThat(LevelCurve.nextFloorOf(10)).isNull();
    }

    @Test
    @DisplayName("적립표는 칸 5 · 미션 20 · 스티커 10 · 다시 재기 20")
    void 적립표() {
        assertThat(Arrays.stream(XpKind.values()).map(XpKind::amount)).containsExactly(5, 20, 10, 20);
    }

    @Test
    @DisplayName("원장 키 — 칸은 missionId:position, 미션은 missionId, 스티커는 missionId(없으면 cheerId), 다시 재기는 fitnessTestId")
    void 원장_키() {
        UUID cheerId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 9, 24);
        XpEvent session = XpEvent.sessionDone(done(false, null), now);
        assertThat(session.sourceKey()).isEqualTo(missionId + ":3");
        assertThat(session.phase()).isEqualTo(SessionDone.Phase.MAIN);
        assertThat(session.factor()).isEqualTo(FitnessFactor.AGILITY);
        assertThat(XpEvent.missionDone(done(true, SessionDone.Verification.TIMER), now)
                        .sourceKey())
                .isEqualTo(missionId.toString());
        assertThat(XpEvent.sticker(UUID.randomUUID(), UUID.randomUUID(), missionId, cheerId, day, now)
                        .sourceKey())
                .isEqualTo(missionId.toString());
        assertThat(XpEvent.sticker(UUID.randomUUID(), UUID.randomUUID(), null, cheerId, day, now)
                        .sourceKey())
                .isEqualTo(cheerId.toString());
        assertThat(XpEvent.remeasure(UUID.randomUUID(), testId, day, now).sourceKey())
                .isEqualTo(testId.toString());
    }

    @Test
    @DisplayName("최근 경험치 줄의 문장은 FE 목과 같다 — 스티커는 붙인 사람을 받침에 맞춰 부르고, 모르면 「가족」")
    void 최근_줄_문장() {
        assertThat(XpReason.of(XpKind.SESSION_DONE, null)).isEqualTo("운동을 했어요");
        assertThat(XpReason.of(XpKind.MISSION_DONE, null)).isEqualTo("운동을 다 했어요");
        assertThat(XpReason.of(XpKind.REMEASURE, null)).isEqualTo("키 · 몸무게를 새로 쟀어요");
        assertThat(XpReason.of(XpKind.STICKER, "엄마")).isEqualTo("엄마가 붙여 준 스티커");
        assertThat(XpReason.of(XpKind.STICKER, "아빠")).isEqualTo("아빠가 붙여 준 스티커");
        assertThat(XpReason.of(XpKind.STICKER, "서준")).isEqualTo("서준이 붙여 준 스티커");
        assertThat(XpReason.of(XpKind.STICKER, null)).isEqualTo("가족이 붙여 준 스티커");
    }

    @Test
    @DisplayName("끝까지 한 +20 은 서버가 잰 확인(TIMER · VIDEO_PROGRESS)으로 끝났을 때만")
    void 끝까지_한_몫은_서버가_잰_확인일_때만() {
        assertThat(done(true, SessionDone.Verification.TIMER).countsAsMissionDone())
                .isTrue();
        assertThat(done(true, SessionDone.Verification.VIDEO_PROGRESS).countsAsMissionDone())
                .isTrue();
        assertThat(done(true, SessionDone.Verification.SELF_REPORT).countsAsMissionDone())
                .isFalse();
        assertThat(done(false, SessionDone.Verification.TIMER).countsAsMissionDone())
                .isFalse();
        assertThat(done(true, null).countsAsMissionDone()).isFalse();
        assertThatThrownBy(() -> new SessionDone(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        missionId,
                        0,
                        SessionDone.Phase.MAIN,
                        null,
                        false,
                        null,
                        LocalDate.of(2026, 9, 24),
                        now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("업적 열두 개의 코드 차례는 FE 목과 같다")
    void 업적_차례() {
        assertThat(Arrays.stream(Achievement.values()).map(Achievement::code))
                .containsExactly(
                        "FIRST_STEP",
                        "STREAK_3",
                        "FULL_SET",
                        "MIN_30",
                        "MIN_100",
                        "WEEKEND",
                        "TOGETHER",
                        "STREAK_7",
                        "REMEASURE",
                        "FIRST_STICKER",
                        "MIN_300",
                        "SIX_POWERS");
    }

    @Test
    @DisplayName("움직임 업적 — 움직였으면 첫걸음, 연속 3 · 7, 분 30 · 100 · 300 은 넘은 만큼, 여섯 가지 힘은 주지 않는다")
    void 움직임_업적() {
        assertThat(new MoveFacts(1, 10, false, false, false).reached()).containsExactly(Achievement.FIRST_STEP);
        assertThat(new MoveFacts(7, 100, true, true, true).reached())
                .containsExactlyInAnyOrder(
                        Achievement.FIRST_STEP,
                        Achievement.STREAK_3,
                        Achievement.STREAK_7,
                        Achievement.FULL_SET,
                        Achievement.MIN_30,
                        Achievement.MIN_100,
                        Achievement.WEEKEND,
                        Achievement.TOGETHER);
        assertThat(new MoveFacts(3, 300, false, false, false).reached())
                .contains(Achievement.STREAK_3, Achievement.MIN_300)
                .doesNotContain(Achievement.STREAK_7, Achievement.SIX_POWERS);
    }
}
