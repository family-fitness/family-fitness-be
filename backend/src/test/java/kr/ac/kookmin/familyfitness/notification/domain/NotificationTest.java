package kr.ac.kookmin.familyfitness.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 종류마다 받는 사람 · 칸 · 멱등 키 — 목의 항목 하나하나와 같은지. */
class NotificationTest {
    private final UUID mom = UUID.randomUUID();
    private final UUID kid = UUID.randomUUID();
    private final UUID cheer = UUID.randomUUID();
    private final UUID mission = UUID.randomUUID();
    private final LocalDate day = LocalDate.of(2026, 9, 29);
    private final Instant at = Instant.parse("2026-09-29T10:00:00Z");

    @Test
    @DisplayName("KID_DONE — 부모에게, about 은 아이, 보낸 이 칸은 비고(목과 같다) 미션 · 응원 · 날짜를 싣는다")
    void 다_했어요() {
        Notification n = Notification.kidDone(mom, kid, "서준", cheer, "운동 3개 했어요!", mission, day, at);
        assertThat(n.profileId()).isEqualTo(mom);
        assertThat(n.kind()).isEqualTo(NotificationKind.KID_DONE);
        assertThat(n.title()).isEqualTo("서준이 운동을 마쳤어요");
        assertThat(n.body()).isEqualTo("운동 3개 했어요!");
        assertThat(n.aboutProfileId()).isEqualTo(kid);
        assertThat(n.fromProfileId()).isNull();
        assertThat(n.missionId()).isEqualTo(mission);
        assertThat(n.cheerId()).isEqualTo(cheer);
        assertThat(n.stickerId()).isNull();
        assertThat(n.date()).isEqualTo(day);
        assertThat(n.createdAt()).isEqualTo(at);
        assertThat(n.isRead()).isFalse();
        assertThat(n.dedupeKey()).isEqualTo("done-" + cheer);
    }

    @Test
    @DisplayName("KID_THANKS — 부모에게, about · from 은 아이, 스티커를 싣고 미션은 싣지 않는다")
    void 고마워요() {
        Notification n = Notification.kidThanks(mom, kid, "서준", cheer, "heart", null, day, at);
        assertThat(n.title()).isEqualTo("서준이 고맙대요");
        assertThat(n.body()).isEqualTo("사랑해");
        assertThat(n.aboutProfileId()).isEqualTo(kid);
        assertThat(n.fromProfileId()).isEqualTo(kid);
        assertThat(n.missionId()).isNull();
        assertThat(n.stickerId()).isEqualTo("heart");
        assertThat(n.dedupeKey()).isEqualTo("thanks-" + cheer);
    }

    @Test
    @DisplayName("PRAISE — 아이에게, about 은 그 아이, from 은 보낸 부모, 스티커 · 미션 · 응원을 싣는다")
    void 칭찬() {
        Notification n = Notification.praise(kid, mom, "엄마", cheer, "star", null, mission, day, at);
        assertThat(n.profileId()).isEqualTo(kid);
        assertThat(n.title()).isEqualTo("엄마가 스티커를 붙여 줬어요");
        assertThat(n.body()).isNull();
        assertThat(n.aboutProfileId()).isEqualTo(kid);
        assertThat(n.fromProfileId()).isEqualTo(mom);
        assertThat(n.missionId()).isEqualTo(mission);
        assertThat(n.stickerId()).isEqualTo("star");
        assertThat(n.dedupeKey()).isEqualTo("praise-" + cheer);
    }

    @Test
    @DisplayName("MISSION_READY · ACHIEVEMENT · REMEASURE — 목의 id 모양이 멱등 키다")
    void 나머지() {
        Notification ready = Notification.missionReady(kid, mission, "스쿼트", day, at);
        assertThat(ready.title()).isEqualTo("새 운동이 생겼어요");
        assertThat(ready.body()).isEqualTo("스쿼트");
        assertThat(ready.aboutProfileId()).isEqualTo(kid);
        assertThat(ready.dedupeKey()).isEqualTo("ready-" + mission + "-2026-09-29");

        Notification badge = Notification.achievement(kid, "STREAK_3", "3일 연속", "3일 연속 운동해요", day, at);
        assertThat(badge.title()).isEqualTo("새 업적: 3일 연속");
        assertThat(badge.body()).isEqualTo("3일 연속 운동했어요");
        assertThat(badge.date()).isEqualTo(day);
        assertThat(badge.dedupeKey()).isEqualTo("badge-" + kid + "-STREAK_3");

        Notification remeasure = Notification.remeasure(mom, kid, "서준", day.minusDays(31), day, at);
        assertThat(remeasure.profileId()).isEqualTo(mom);
        assertThat(remeasure.title()).isEqualTo("서준 키와 몸무게를 다시 측정해 볼까요?");
        assertThat(remeasure.body()).isEqualTo("마지막 측정 후 31일");
        assertThat(remeasure.aboutProfileId()).isEqualTo(kid);
        assertThat(remeasure.date()).isNull();
        assertThat(remeasure.dedupeKey()).isEqualTo("remeasure-" + kid + "-2026-08-29");
    }

    @Test
    @DisplayName("다시 재기 기준은 30일 이상(목 days < 30 이면 건너뜀) · 업적은 받은 날부터 14일 보인다")
    void 규칙() {
        assertThat(NotificationRules.remeasureDue(day.minusDays(30), day)).isTrue();
        assertThat(NotificationRules.remeasureDue(day.minusDays(29), day)).isFalse();
        assertThat(NotificationRules.achievementsShownSince(day)).isEqualTo(day.minusDays(14));
    }
}
