package kr.ac.kookmin.familyfitness.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCreated;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.CheerSent;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** 07:30 · 09:00 스케줄러와 기동 따라잡기, 새 미션 이벤트의 07:30 판정, 리스너의 실패 삼키기. 시계는 고정한다. */
class NotificationSchedulingTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LocalDate today = LocalDate.of(2026, 9, 29);
    private final Instant readyAt = Instant.parse("2026-09-28T22:30:00Z"); // 9/29 07:30 KST
    private final Instant remeasureAt = Instant.parse("2026-09-29T00:00:00Z"); // 9/29 09:00 KST
    private final UUID familyA = UUID.randomUUID();
    private final UUID familyB = UUID.randomUUID();
    private final NotificationWriter writer = mock(NotificationWriter.class);
    private final ProfileQuery profiles = mock(ProfileQuery.class);

    private NotificationScheduler schedulerAt(Instant now) {
        when(profiles.allFamilyIds()).thenReturn(List.of(familyA, familyB));
        return new NotificationScheduler(profiles, writer, Clock.fixed(now, KST), KST);
    }

    private NotificationEventListener listenerAt(Instant now) {
        return new NotificationEventListener(writer, Clock.fixed(now, KST), KST);
    }

    @Nested
    @DisplayName("스케줄러")
    class Scheduler {
        @Test
        @DisplayName("07:30 몫 — 가족마다 오늘 · 07:30 으로 부르고, 한 가족이 실패해도 나머지는 계속한다")
        void 가족마다() {
            when(writer.missionReadyForFamily(familyA, today, readyAt)).thenThrow(new IllegalStateException("고장"));
            when(writer.missionReadyForFamily(familyB, today, readyAt)).thenReturn(2);

            assertThat(schedulerAt(readyAt).missionReady()).isEqualTo(new NotificationScheduler.Run(2, 1));
        }

        @Test
        @DisplayName("09:00 몫 — 오늘 · 09:00 으로 부른다")
        void 다시_재기() {
            when(writer.remeasureForFamily(familyA, today, remeasureAt)).thenReturn(1);
            when(writer.remeasureForFamily(familyB, today, remeasureAt)).thenReturn(2);

            assertThat(schedulerAt(remeasureAt.plusSeconds(1)).remeasure())
                    .isEqualTo(new NotificationScheduler.Run(3, 0));
        }

        @Test
        @DisplayName("이벤트 리스너와 같은 알림을 동시에 넣어 유니크 제약에 걸리면 그 가족을 한 번 더 쓴다")
        void 유니크_충돌은_한_번_더() {
            DataIntegrityViolationException duplicate =
                    new DataIntegrityViolationException("duplicate", new SQLException("duplicate key", "23505"));
            when(writer.missionReadyForFamily(familyA, today, readyAt))
                    .thenThrow(duplicate)
                    .thenReturn(1);

            assertThat(schedulerAt(readyAt).missionReady()).isEqualTo(new NotificationScheduler.Run(1, 0));
        }

        @Test
        @DisplayName("기동 따라잡기 — 07:30 전이면 아무것도, 07:30~09:00 이면 MISSION_READY 만, 09:00 뒤면 둘 다")
        void 기동_따라잡기() {
            schedulerAt(readyAt.minusSeconds(1)).onReady();
            verifyNoInteractions(writer);

            schedulerAt(readyAt.plusSeconds(60)).onReady();
            verify(writer).missionReadyForFamily(familyA, today, readyAt);
            verify(writer, never()).remeasureForFamily(any(), any(), any());

            schedulerAt(remeasureAt).onReady();
            verify(writer).remeasureForFamily(familyA, today, remeasureAt);
        }
    }

    @Nested
    @DisplayName("새 미션 이벤트")
    class NewMission {
        private MissionCreated created(LocalDate from, LocalDate to, Instant at) {
            return new MissionCreated(UUID.randomUUID(), familyA, "스쿼트", from, to, List.of(UUID.randomUUID()), at);
        }

        @Test
        @DisplayName("오늘이 기간 안이고 07:30 이 지났으면 그 자리에서 — 만든 시각은 미션이 생긴 시각")
        void 지났으면_곧바로() {
            Instant at = readyAt.plusSeconds(3600);
            MissionCreated event = created(today, today.plusDays(2), at);

            listenerAt(at.plusSeconds(1)).on(event);

            verify(writer).missionReadyForMission(event.missionId(), today, at);
        }

        @Test
        @DisplayName("07:30 전이면 스케줄러에 맡기고, 오늘이 기간 밖이면 만들지 않는다")
        void 전이거나_기간_밖() {
            listenerAt(readyAt.minusSeconds(1)).on(created(today, today, readyAt.minusSeconds(10)));
            listenerAt(readyAt.plusSeconds(60)).on(created(today.plusDays(1), today.plusDays(1), readyAt));
            listenerAt(readyAt.plusSeconds(60)).on(created(today.minusDays(3), today.minusDays(1), readyAt));

            verifyNoInteractions(writer);
        }

        @Test
        @DisplayName("07:30 직전에 만들어 07:30 이 지나 커밋됐으면 — 지금 시각으로 가르므로 놓치지 않는다")
        void 커밋이_늦으면() {
            MissionCreated event = created(today, today, readyAt.minusSeconds(1));

            listenerAt(readyAt.plusSeconds(1)).on(event);

            verify(writer).missionReadyForMission(event.missionId(), today, readyAt.minusSeconds(1));
        }
    }

    @Test
    @DisplayName("알림 쓰기가 실패해도 리스너는 예외를 올리지 않는다 — 커밋 뒤라 원래 요청이 500 이 되면 안 된다")
    void 실패는_삼킨다() {
        CheerSent cheer = new CheerSent(
                UUID.randomUUID(),
                familyA,
                UUID.randomUUID(),
                UUID.randomUUID(),
                CheerKind.DONE,
                null,
                "했어요",
                null,
                null,
                readyAt);
        when(writer.fromCheer(cheer)).thenThrow(new IllegalStateException("DB 고장"));

        assertThatCode(() -> listenerAt(readyAt).on(cheer)).doesNotThrowAnyException();
    }
}
