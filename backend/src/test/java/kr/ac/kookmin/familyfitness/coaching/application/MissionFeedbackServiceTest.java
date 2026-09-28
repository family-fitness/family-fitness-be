package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeedback;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeel;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.NotParticipantException;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantConsentRequiredException;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionFeedbackRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MissionFeedbackServiceTest {
    private final Family family = new Family();
    private final FakeIdentity identity = new FakeIdentity(family);
    private final InMemoryMissionRepository missions = new InMemoryMissionRepository();
    private final InMemoryMissionFeedbackRepository feedbacks = new InMemoryMissionFeedbackRepository();

    private final UUID child = family.child.profileId();
    private final UUID parent = family.parent.profileId();

    private MissionFeedbackService serviceAt(Instant now) {
        return new MissionFeedbackService(missions, feedbacks, identity, Fixed.time(now));
    }

    private Mission mission(UUID... participants) {
        return missions.save(Mission.manual(
                UUID.randomUUID(),
                family.familyId,
                "함께 운동",
                TargetMetric.TIMER_MINUTES,
                10,
                null,
                Fixed.TODAY,
                Fixed.TODAY,
                List.of(participants),
                List.of(),
                parent,
                Fixed.NOW));
    }

    private void send(UUID userId, UUID missionId, UUID profileId, MissionFeel feel) {
        serviceAt(Fixed.NOW).send(userId, missionId, new MissionFeedbackCommand(profileId, feel));
    }

    @Test
    @DisplayName("참여자는 느낌을 남기고, 다시 보내면 느낌 · 시각을 덮어쓴다")
    void 참여자는_느낌을_남기고_다시_보내면_덮어쓴다() {
        Mission m = mission(child);

        send(family.childUser, m.getId(), child, MissionFeel.HARD);
        serviceAt(Fixed.NOW.plusSeconds(60))
                .send(family.childUser, m.getId(), new MissionFeedbackCommand(child, MissionFeel.GOOD));

        assertThat(feedbacks.rows).hasSize(1);
        assertThat(feedbacks.find(m.getId(), child))
                .isEqualTo(new MissionFeedback(m.getId(), child, MissionFeel.GOOD, Fixed.NOW.plusSeconds(60)));
    }

    @Test
    @DisplayName("부모 계정은 계정 없는 아이 이름으로 보낼 수 있고, 계정 있는 아이 이름으로는 403")
    void 부모는_계정_없는_아이_이름으로만_보낸다() {
        ProfileDetails toddler = family.addChild("서아", Fixed.TODAY.minusYears(5));
        Mission m = mission(child, toddler.profileId());

        send(family.parentUser, m.getId(), toddler.profileId(), MissionFeel.EASY);

        assertThat(feedbacks.find(m.getId(), toddler.profileId())).isNotNull();
        assertThrows(
                CannotActAsProfileException.class, () -> send(family.parentUser, m.getId(), child, MissionFeel.EASY));
    }

    @Test
    @DisplayName("참여자가 아니면 403 NOT_A_PARTICIPANT, 없는 미션은 404, 동의를 거둔 아이는 422 CONSENT_REQUIRED — 저장하지 않는다")
    void 참여자_아님_없는_미션_동의_없음은_저장하지_않는다() {
        Mission parentOnly = mission(parent);
        Mission withChild = mission(child);

        assertThat(assertThrows(
                                NotParticipantException.class,
                                () -> send(family.childUser, parentOnly.getId(), child, MissionFeel.EASY))
                        .getCode())
                .isEqualTo("NOT_A_PARTICIPANT");
        assertThat(assertThrows(
                                MissionNotFoundException.class,
                                () -> send(family.childUser, UUID.randomUUID(), child, MissionFeel.EASY))
                        .getCode())
                .isEqualTo("MISSION_NOT_FOUND");
        family.withdrawConsent(child);
        assertThat(assertThrows(
                                ParticipantConsentRequiredException.class,
                                () -> send(family.childUser, withChild.getId(), child, MissionFeel.EASY))
                        .getCode())
                .isEqualTo("CONSENT_REQUIRED");

        assertThat(feedbacks.rows).isEmpty();
    }
}
