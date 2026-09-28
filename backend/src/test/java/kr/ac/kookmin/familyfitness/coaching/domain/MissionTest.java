package kr.ac.kookmin.familyfitness.coaching.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MissionTest {
    private final UUID familyId = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();
    private final UUID childId = UUID.randomUUID();
    private final Instant at = Instant.parse("2026-09-09T01:00:00Z");
    private final LocalDate monday = LocalDate.of(2026, 9, 7);

    private Mission mission(TargetMetric metric) {
        return mission(metric, 45, List.of(childId));
    }

    private Mission mission(TargetMetric metric, int target) {
        return mission(metric, target, List.of(childId));
    }

    private Mission mission(TargetMetric metric, int target, List<UUID> participants) {
        return manual(metric, target, participants, List.of());
    }

    private Mission withSessions(TargetMetric metric, int target, List<MissionSession> sessions) {
        return manual(metric, target, List.of(childId), sessions);
    }

    private Mission manual(TargetMetric metric, int target, List<UUID> participants, List<MissionSession> sessions) {
        return Mission.manual(
                UUID.randomUUID(),
                familyId,
                "함께 운동",
                metric,
                target,
                null,
                monday,
                monday.plusDays(6),
                participants,
                sessions,
                parentId,
                at);
    }

    private static MissionSession session(int position, SessionPhase phase, int minutes) {
        return new MissionSession(
                position,
                phase,
                "동작" + position,
                FitnessFactor.FLEXIBILITY,
                minutes,
                new SessionClip("-EATykJOvBQ", 6, 78, "거북이 스트레칭"));
    }

    @Test
    @DisplayName("타이머 미션은 목표 분에 닿으면 즉시 완료되고 근거는 TIMER 다")
    void 타이머_미션은_목표_분에_닿으면_즉시_완료되고_근거는_TIMER_다() {
        Mission m = mission(TargetMetric.TIMER_MINUTES, 45);

        m.recordProgress(childId, MissionProgress.of(30, 45, VerifiedBy.TIMER), at);
        MissionParticipant p = m.participantOf(childId);
        assertThat(p.getProgress()).isCloseTo(0.666, Offset.offset(0.001));
        assertThat(p.isCompleted()).isFalse();

        m.recordProgress(childId, MissionProgress.of(60, 45, VerifiedBy.TIMER), at.plusSeconds(1));
        assertThat(p.getProgress()).isEqualTo(1.0);
        assertThat(p.isCompleted()).isTrue();
        assertThat(p.getVerifiedBy()).isEqualTo(VerifiedBy.TIMER);
        assertThat(p.getVerifiedAt()).isEqualTo(at.plusSeconds(1));
        assertThat(p.isNeedsGuardianCheck()).isFalse();
    }

    @Test
    @DisplayName("걸음수 미션은 도달해도 보호자 확인 전에는 완료가 아니다")
    void 걸음수_미션은_도달해도_보호자_확인_전에는_완료가_아니다() {
        Mission m = mission(TargetMetric.STEPS, 1000);

        m.recordProgress(childId, MissionProgress.of(1500, 1000, null), at);
        MissionParticipant p = m.participantOf(childId);

        assertThat(p.getProgress()).isEqualTo(1.0);
        assertThat(p.isCompleted()).isFalse();
        assertThat(p.isNeedsGuardianCheck()).isTrue();
        assertThat(m.isServerVerifiable()).isFalse();

        m.confirm(childId, parentId, at);
        assertThat(p.isCompleted()).isTrue();
        assertThat(p.getVerifiedBy()).isEqualTo(VerifiedBy.SELF_REPORT);
        assertThat(p.getConfirmedBy()).isEqualTo(parentId);
        assertThat(p.isNeedsGuardianCheck()).isFalse();
    }

    @Test
    @DisplayName("목표 도달 전 보호자 확인은 TARGET_NOT_REACHED 다")
    void 목표_도달_전_보호자_확인은_TARGET_NOT_REACHED_다() {
        Mission m = mission(TargetMetric.STEPS, 1000);
        m.recordProgress(childId, MissionProgress.of(400, 1000, null), at);

        TargetNotReachedException e =
                assertThrows(TargetNotReachedException.class, () -> m.confirm(childId, parentId, at));
        assertThat(e.getCode()).isEqualTo("TARGET_NOT_REACHED");
        assertThat(m.participantOf(childId).isCompleted()).isFalse();
    }

    @Test
    @DisplayName("완료된 참여자의 진행도는 되돌리지 않는다")
    void 완료된_참여자의_진행도는_되돌리지_않는다() {
        Mission m = mission(TargetMetric.STEPS, 1000);
        m.recordProgress(childId, MissionProgress.of(1000, 1000, null), at);
        m.confirm(childId, parentId, at);

        boolean changed = m.recordProgress(childId, MissionProgress.of(200, 1000, null), at.plusSeconds(5));

        assertThat(changed).isFalse();
        assertThat(m.participantOf(childId).getProgress()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("참여자가 아니면 권한 없음(403) NOT_A_PARTICIPANT, 지표가 다르면 INVALID_METRIC")
    void 참여자가_아니면_NOT_A_PARTICIPANT_지표가_다르면_INVALID_METRIC() {
        Mission m = mission(TargetMetric.TIMER_MINUTES);

        NotParticipantException notParticipant =
                assertThrows(NotParticipantException.class, () -> m.participantOf(parentId));
        assertThat(notParticipant.getCode()).isEqualTo("NOT_A_PARTICIPANT");
        assertThat(notParticipant.getKind()).isEqualTo(ErrorKind.FORBIDDEN);
        assertThat(assertThrows(InvalidMetricException.class, () -> m.requireMetric(TargetMetric.STEPS))
                        .getCode())
                .isEqualTo("INVALID_METRIC");
    }

    @Test
    @DisplayName("상태는 전원 완료면 DONE, 기간이 지나면 EXPIRED, 아니면 ACTIVE")
    void 상태는_전원_완료면_DONE_기간이_지나면_EXPIRED_아니면_ACTIVE() {
        Mission m = mission(TargetMetric.TIMER_MINUTES, 10, List.of(childId, parentId));

        assertThat(m.statusOn(monday.plusDays(2))).isEqualTo(MissionStatus.ACTIVE);
        assertThat(m.statusOn(monday.plusDays(7))).isEqualTo(MissionStatus.EXPIRED);

        m.recordProgress(childId, MissionProgress.of(10, 10, VerifiedBy.TIMER), at);
        assertThat(m.statusOn(monday.plusDays(2))).isEqualTo(MissionStatus.ACTIVE);
        m.recordProgress(parentId, MissionProgress.of(10, 10, VerifiedBy.TIMER), at);
        assertThat(m.statusOn(monday.plusDays(7))).isEqualTo(MissionStatus.DONE);
    }

    @Test
    @DisplayName("진행도는 1을 넘지 않고 목표가 0이면 0이다")
    void 진행도는_1을_넘지_않고_목표가_0이면_0이다() {
        assertThat(MissionProgress.of(120, 45, VerifiedBy.TIMER).progress()).isEqualTo(1.0);
        assertThat(MissionProgress.of(5, 0, null).progress()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("직접 만든 칸은 position 차례로 들고 목표 분은 칸 시간의 합이다")
    void 직접_만든_칸은_position_차례로_들고_목표_분은_칸_시간의_합이다() {
        Mission m = withSessions(
                TargetMetric.TIMER_MINUTES,
                8,
                List.of(
                        session(2, SessionPhase.WARMUP, 1),
                        session(1, SessionPhase.COOLDOWN, 2),
                        session(3, SessionPhase.MAIN, 5)));

        assertThat(m.getSessions()).extracting(MissionSession::position).containsExactly(1, 2, 3);
        assertThat(m.getSessions())
                .extracting(MissionSession::phase)
                .containsExactly(SessionPhase.COOLDOWN, SessionPhase.WARMUP, SessionPhase.MAIN);
        assertThat(m.getTargetValue()).isEqualTo(8);
        assertThat(mission(TargetMetric.TIMER_MINUTES, 45).getSessions()).isEmpty();
        assertThat(mission(TargetMetric.TIMER_MINUTES, 45).getTargetValue()).isEqualTo(45);
    }

    @Test
    @DisplayName("칸 번호가 1..n 이 아니거나 칸이 있는데 지표가 분이 아니면 만들 수 없다")
    void 칸_번호가_1부터_n이_아니거나_칸이_있는데_지표가_분이_아니면_만들_수_없다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> withSessions(
                        TargetMetric.TIMER_MINUTES,
                        3,
                        List.of(session(1, SessionPhase.MAIN, 1), session(1, SessionPhase.MAIN, 2))));
        assertThrows(
                IllegalArgumentException.class,
                () -> withSessions(
                        TargetMetric.TIMER_MINUTES,
                        3,
                        List.of(session(1, SessionPhase.MAIN, 1), session(3, SessionPhase.MAIN, 2))));
        assertThrows(
                IllegalArgumentException.class,
                () -> withSessions(TargetMetric.STEPS, 3000, List.of(session(1, SessionPhase.MAIN, 1))));
        assertThrows(IllegalArgumentException.class, () -> session(1, SessionPhase.MAIN, 0));
    }

    @Test
    @DisplayName("칸이 있는데 목표 분이 칸 시간의 합과 다르면 고쳐 넣지 않고 만들 수 없다")
    void 칸이_있는데_목표_분이_칸_시간의_합과_다르면_만들_수_없다() {
        List<MissionSession> sessions = List.of(session(1, SessionPhase.WARMUP, 2), session(2, SessionPhase.MAIN, 3));

        IllegalArgumentException larger = assertThrows(
                IllegalArgumentException.class, () -> withSessions(TargetMetric.TIMER_MINUTES, 999, sessions));
        assertThat(larger.getMessage()).contains("999").contains("5분");
        assertThrows(IllegalArgumentException.class, () -> withSessions(TargetMetric.TIMER_MINUTES, 4, sessions));
        assertThat(withSessions(TargetMetric.TIMER_MINUTES, 5, sessions).getTargetValue())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("영상 구간은 끝이 시작보다 뒤여야 하고 끝이 없으면 영상 한 편이다")
    void 영상_구간은_끝이_시작보다_뒤여야_하고_끝이_없으면_영상_한_편이다() {
        assertThrows(IllegalArgumentException.class, () -> new SessionClip("-EATykJOvBQ", 78, 78, null));
        assertThrows(IllegalArgumentException.class, () -> new SessionClip("-EATykJOvBQ", 78, 6, null));
        assertThrows(IllegalArgumentException.class, () -> new SessionClip("-EATykJOvBQ", -1, 6, null));
        assertThat(new SessionClip("-EATykJOvBQ", 0, null, null).endSec()).isNull();
    }

    @Test
    @DisplayName("승인된 제안은 기간 없이도 실행의 주로 복사된다")
    void 승인된_제안은_기간_없이도_실행의_주로_복사된다() {
        CoachRun run = CoachRun.awaitingApproval(
                UUID.randomUUID(),
                familyId,
                List.of(new CoachProposalItem(
                        0,
                        "같이 늘이는 한 주",
                        "TIMER_MINUTES",
                        45,
                        "부모용 문구",
                        null,
                        null,
                        null,
                        List.of(new ProposalParticipant(childId, ProfileRole.CHILD, "주행자")),
                        new ProposalVideo("IdpXx2gm90o", 96),
                        List.of(),
                        null,
                        null)),
                monday,
                List.of(),
                null,
                3,
                15,
                Instant.EPOCH);
        run.approve(new CoachApprover(parentId, familyId, true), at);

        assertThat(run.proposalsForMissionCreation()).hasSize(1);
        Mission mission = Mission.fromProposal(
                UUID.randomUUID(), run, run.proposalsForMissionCreation().getFirst(), parentId, at);

        assertThat(mission.getOrigin()).isEqualTo(MissionOrigin.COACH);
        assertThat(mission.getCoachRunId()).isEqualTo(run.getId());
        assertThat(mission.getStartsOn()).isEqualTo(monday);
        assertThat(mission.getEndsOn()).isEqualTo(monday.plusDays(6));
        assertThat(mission.getVideo()).isEqualTo(new MissionVideo("IdpXx2gm90o", 96));
        assertThat(mission.getParticipants())
                .singleElement()
                .extracting(MissionParticipant::getCoachRole)
                .isEqualTo("주행자");
        assertThat(mission.getRationale()).isEqualTo("부모용 문구");
        assertThat(mission.getSessions()).isEmpty();
    }

    private CoachProposalItem itemWithSessions(int targetValue, String targetMetric, List<MissionSession> sessions) {
        return new CoachProposalItem(
                0,
                "유연성 키우기 7분",
                targetMetric,
                targetValue,
                null,
                null,
                monday,
                monday,
                List.of(new ProposalParticipant(childId, ProfileRole.CHILD, "주행자")),
                null,
                List.of(),
                null,
                null,
                sessions);
    }

    @Test
    @DisplayName("승인된 제안의 칸은 차례 그대로 미션 칸이 된다")
    void 승인된_제안의_칸은_차례_그대로_미션_칸이_된다() {
        List<MissionSession> sessions = List.of(
                new MissionSession(
                        1, SessionPhase.WARMUP, "나비자세", FitnessFactor.FLEXIBILITY, 1, new SessionClip("v", 1, 2, "나비")),
                new MissionSession(2, SessionPhase.MAIN, "가슴펴기", null, 5, null),
                new MissionSession(3, SessionPhase.COOLDOWN, "어깨 늘리기", null, 1, null));
        CoachRun run = CoachRun.awaitingApproval(
                UUID.randomUUID(),
                familyId,
                List.of(itemWithSessions(7, "TIMER_MINUTES", sessions)),
                monday,
                List.of(),
                null,
                3,
                15,
                Instant.EPOCH);
        run.approve(new CoachApprover(parentId, familyId, true), at);

        Mission mission = Mission.fromProposal(
                UUID.randomUUID(), run, run.proposalsForMissionCreation().getFirst(), parentId, at);

        assertThat(mission.getSessions()).isEqualTo(sessions);
        assertThat(mission.getTargetValue()).isEqualTo(7);
    }

    @Test
    @DisplayName("칸이 있는 제안 항목은 목표가 분이고 칸 분의 합과 같아야 한다 — 직접 만들기와 같은 규칙")
    void 칸이_있는_제안_항목은_목표가_분이고_칸_분의_합과_같아야_한다() {
        List<MissionSession> sessions = List.of(
                new MissionSession(1, SessionPhase.MAIN, "가슴펴기", null, 5, null),
                new MissionSession(2, SessionPhase.COOLDOWN, "어깨 늘리기", null, 1, null));

        assertThrows(IllegalArgumentException.class, () -> itemWithSessions(20, "TIMER_MINUTES", sessions));
        assertThrows(IllegalArgumentException.class, () -> itemWithSessions(6, "STEPS", sessions));
        assertThrows(
                IllegalArgumentException.class,
                () -> itemWithSessions(6, "TIMER_MINUTES", List.of(sessions.getLast(), sessions.getLast())));
        assertThat(itemWithSessions(6, "TIMER_MINUTES", sessions.reversed()).sessions())
                .isEqualTo(sessions);
    }
}
