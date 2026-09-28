package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCancelled;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionAlreadyStartedException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionEndedException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeedback;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeel;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionProgress;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionCompletion;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionFeedbackRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemorySessionCompletionRepository;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MissionDeletionServiceTest {
    private final Family family = new Family();
    private final Family other = new Family();
    private final FakeIdentity identity = new FakeIdentity(family, other);
    private final InMemorySessionCompletionRepository completions = new InMemorySessionCompletionRepository();
    private final InMemoryMissionRepository missions = new InMemoryMissionRepository(completions);
    private final InMemoryMissionFeedbackRepository feedbacks = new InMemoryMissionFeedbackRepository();
    private final List<Object> events = new ArrayList<>();
    private final MissionDeletionService service =
            new MissionDeletionService(missions, completions, feedbacks, identity, events::add, Fixed.time());

    private final UUID child = family.child.profileId();
    private final UUID parent = family.parent.profileId();

    /** 10분 타이머 미션(칸 없음)을 저장소에 바로 넣는다 — 지난 날짜도 넣을 수 있게. */
    private Mission mission(LocalDate startsOn, LocalDate endsOn, UUID... participants) {
        return missions.save(Mission.manual(
                UUID.randomUUID(),
                family.familyId,
                "함께 운동",
                TargetMetric.TIMER_MINUTES,
                10,
                null,
                startsOn,
                endsOn,
                List.of(participants),
                List.of(),
                parent,
                Fixed.NOW.minusSeconds(3600)));
    }

    @Test
    @DisplayName("보호자는 아무도 안 한 오늘 · 앞날 · 기간 중인 미션을 지운다 — 느낌도 같이 지워지고 MissionCancelled 가 나간다")
    void 보호자는_아무도_안_한_미션을_지운다() {
        Mission today = mission(Fixed.TODAY, Fixed.TODAY, child);
        Mission later = mission(Fixed.TODAY.plusDays(3), Fixed.TODAY.plusDays(3), child);
        Mission ongoing = mission(Fixed.TODAY.minusDays(1), Fixed.TODAY.plusDays(1), child, parent);
        feedbacks.upsert(new MissionFeedback(today.getId(), child, MissionFeel.EASY, Fixed.NOW));

        service.delete(family.parentUser, today.getId());
        service.delete(family.parentUser, later.getId());
        service.delete(family.parentUser, ongoing.getId());

        assertThat(missions.missions).isEmpty();
        assertThat(feedbacks.rows).isEmpty();
        assertThat(events)
                .containsExactly(
                        new MissionCancelled(today.getId(), family.familyId, Fixed.NOW),
                        new MissionCancelled(later.getId(), family.familyId, Fixed.NOW),
                        new MissionCancelled(ongoing.getId(), family.familyId, Fixed.NOW));
    }

    @Test
    @DisplayName("자녀 계정은 403 NOT_A_PARENT, 다른 가족은 403 NOT_SAME_FAMILY, 없는 미션은 404 — 아무것도 지우지 않는다")
    void 보호자가_아니면_지울_수_없다() {
        Mission today = mission(Fixed.TODAY, Fixed.TODAY, child);

        assertThat(assertThrows(NotAParentException.class, () -> service.delete(family.childUser, today.getId()))
                        .getCode())
                .isEqualTo("NOT_A_PARENT");
        assertThrows(NotSameFamilyException.class, () -> service.delete(other.parentUser, today.getId()));
        assertThat(assertThrows(
                                MissionNotFoundException.class,
                                () -> service.delete(family.parentUser, UUID.randomUUID()))
                        .getCode())
                .isEqualTo("MISSION_NOT_FOUND");

        assertThat(missions.findById(today.getId())).isNotNull();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("기간이 끝난 미션(endDate < 오늘)은 409 MISSION_ENDED — 누가 했든 안 했든")
    void 기간이_끝난_미션은_MISSION_ENDED() {
        Mission yesterday = mission(Fixed.TODAY.minusDays(1), Fixed.TODAY.minusDays(1), child);

        MissionEndedException e =
                assertThrows(MissionEndedException.class, () -> service.delete(family.parentUser, yesterday.getId()));

        assertThat(e.getCode()).isEqualTo("MISSION_ENDED");
        assertThat(e.getKind()).isEqualTo(ErrorKind.CONFLICT);
        assertThat(missions.findById(yesterday.getId())).isNotNull();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("누군가 칸을 끝냈으면(보호자 한 명뿐이어도) 409 MISSION_ALREADY_STARTED")
    void 누군가_칸을_끝냈으면_MISSION_ALREADY_STARTED() {
        Mission together = mission(Fixed.TODAY, Fixed.TODAY, child, parent);
        completions.insert(new SessionCompletion(
                together.getId(), 1, parent, Fixed.NOW, Fixed.TODAY, 600, VerifiedBy.VIDEO_PROGRESS));

        MissionAlreadyStartedException e = assertThrows(
                MissionAlreadyStartedException.class, () -> service.delete(family.parentUser, together.getId()));

        assertThat(e.getCode()).isEqualTo("MISSION_ALREADY_STARTED");
        assertThat(e.getKind()).isEqualTo(ErrorKind.CONFLICT);
        assertThat(missions.findById(together.getId())).isNotNull();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("칸 끝 기록이 없어도 참여자가 완료됐으면(옛 타이머 경로) 409 MISSION_ALREADY_STARTED — 진행도만 있으면 지운다")
    void 참여자가_완료됐으면_MISSION_ALREADY_STARTED() {
        Mission done = mission(Fixed.TODAY, Fixed.TODAY, child);
        done.recordProgress(child, MissionProgress.of(10, 10, VerifiedBy.TIMER), Fixed.NOW);
        Mission partly = mission(Fixed.TODAY, Fixed.TODAY, child);
        partly.recordProgress(child, MissionProgress.of(3, 10, VerifiedBy.TIMER), Fixed.NOW);

        assertThrows(MissionAlreadyStartedException.class, () -> service.delete(family.parentUser, done.getId()));
        service.delete(family.parentUser, partly.getId());

        assertThat(missions.findById(done.getId())).isNotNull();
        assertThat(missions.findById(partly.getId())).isNull();
    }
}
