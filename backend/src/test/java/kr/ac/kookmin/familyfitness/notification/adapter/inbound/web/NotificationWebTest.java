package kr.ac.kookmin.familyfitness.notification.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCancelled;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCompleted;
import kr.ac.kookmin.familyfitness.coaching.api.StandingMissionQuery;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessTestRegistered;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.CheerSent;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.notification.application.NotificationWriter;
import kr.ac.kookmin.familyfitness.notification.application.port.NotificationRepository;
import kr.ac.kookmin.familyfitness.notification.domain.Notification;
import kr.ac.kookmin.familyfitness.progress.api.AchievementEarned;
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import kr.ac.kookmin.familyfitness.support.TestAuth;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * H2 + Flyway(V149 notifications) 위에서 알림함을 끝까지 돈다. 리스너는 발행한 트랜잭션이 <b>커밋된 뒤</b> 받으므로 이 시험은
 * 트랜잭션으로 감싸지 않고 실제로 커밋하고, 끝나면 만든 행을 지운다. 운영은 알림을 전용 스레드 풀에서 비동기로 쓰지만 시험 프로필은
 * 부른 스레드에서 곧바로 쓴다(app.notification.executor.async=false) — 커밋이 끝나면 알림도 들어가 있다. 비동기 쪽은
 * NotificationConcurrencyTest 가 본다. 오늘 서는 미션 · 마지막 측정일은 coaching · fitness 의 실제 구현이다.
 * identity 는 목: 식구 · 권한 판단만 흉내 낸다. 「오늘」 은 실제 KST 날짜다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationWebTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    MockMvc mvc;

    @Autowired
    TestAuth auth;

    @Autowired
    ProfileRows rows;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    NotificationWriter writer;

    @Autowired
    MissionRepository missions;

    @Autowired
    StandingMissionQuery standingMissions;

    @MockitoBean
    FamilyAccess familyAccess;

    @MockitoBean
    ProfileQuery profileQuery;

    @MockitoBean
    AiGateway ai;

    private final UUID user = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(KST);
    private TransactionTemplate tx;
    private UUID familyId;
    private UUID mom;
    private UUID dad;
    private UUID kid;
    private UUID sibling;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        familyId = rows.family("서준이네");
        mom = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "은영");
        dad = rows.profile(familyId, LocalDate.of(1986, 7, 1), Sex.M, ProfileRole.PARENT, "철수");
        kid = rows.profile(familyId, LocalDate.of(2016, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        sibling = rows.profile(familyId, LocalDate.of(2018, 2, 1), Sex.F, ProfileRole.CHILD, "지우");
        List<ProfileSummary> family = List.of(
                summary(mom, "은영", ProfileRole.PARENT, Sex.F),
                summary(dad, "철수", ProfileRole.PARENT, Sex.M),
                summary(kid, "서준", ProfileRole.CHILD, Sex.M),
                summary(sibling, "지우", ProfileRole.CHILD, Sex.F));
        when(profileQuery.summariesOfFamily(familyId)).thenReturn(family);
        family.forEach(it -> when(profileQuery.findSummary(it.profileId())).thenReturn(it));
        family.forEach(
                it -> when(familyAccess.requireActingAs(user, it.profileId())).thenReturn(it));
    }

    /** 커밋한 행을 FK 차례로 지운다. */
    @AfterEach
    void cleanUp() {
        String profiles = "select id from profiles where family_id = ?";
        jdbc.update("delete from notifications where profile_id in (" + profiles + ")", familyId);
        // 칭찬 스티커는 progress 가 같은 트랜잭션에서 경험치 · 첫 스티커 업적으로 쌓는다
        jdbc.update("delete from progress_xp_events where profile_id in (" + profiles + ")", familyId);
        jdbc.update("delete from progress_achievements where profile_id in (" + profiles + ")", familyId);
        jdbc.update("delete from cheers where family_id = ?", familyId);
        jdbc.update("delete from rest_cards where family_id = ?", familyId);
        jdbc.update("delete from fitness_tests where profile_id in (" + profiles + ")", familyId);
        String familyMissions = "select id from missions where family_id = ?";
        jdbc.update("delete from mission_participants where mission_id in (" + familyMissions + ")", familyId);
        jdbc.update("delete from mission_sessions where mission_id in (" + familyMissions + ")", familyId);
        jdbc.update("delete from missions where family_id = ?", familyId);
        jdbc.update("delete from profiles where family_id = ?", familyId);
        jdbc.update("delete from families where id = ?", familyId);
    }

    private ProfileSummary summary(UUID profileId, String name, ProfileRole role, Sex sex) {
        return new ProfileSummary(
                profileId,
                familyId,
                name,
                role,
                role == ProfileRole.PARENT ? AgeGroup.ADULT : AgeGroup.YOUTH,
                sex,
                role == ProfileRole.PARENT,
                InviteStatus.NONE,
                null,
                true,
                role == ProfileRole.CHILD,
                true,
                false);
    }

    private ResultActions list(UUID profileId) throws Exception {
        return mvc.perform(get("/api/v1/notifications")
                .param("profileId", profileId.toString())
                .header(HttpHeaders.AUTHORIZATION, auth.bearer(user)));
    }

    private ResultActions read(String body) throws Exception {
        return mvc.perform(post("/api/v1/notifications/read")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(HttpHeaders.AUTHORIZATION, auth.bearer(user)));
    }

    /** 응원을 저장하고 같은 트랜잭션에서 CheerSent 를 낸다(CheerService 와 같은 모양). */
    private CheerSent sendCheer(
            CheerKind kind, UUID from, UUID to, @Nullable String stickerId, @Nullable String message, Instant at) {
        CheerSent cheer =
                new CheerSent(UUID.randomUUID(), familyId, from, to, kind, stickerId, message, null, null, at);
        jdbc.update("""
                insert into cheers (id, family_id, from_profile_id, to_profile_id, kind, sticker_id, message, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)\
                """, cheer.cheerId(), familyId, from, to, kind.name(), stickerId, message, cheer.createdAt());
        events.publishEvent(cheer);
        return cheer;
    }

    private int count(UUID profileId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from notifications where profile_id = ?", Integer.class, profileId);
        return n == null ? 0 : n;
    }

    private Instant kst(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(KST).toInstant();
    }

    @Test
    @DisplayName("응원이 커밋되면 받는 사람 알림함에 곧바로 뜬다 — KID_DONE · KID_THANKS 는 부모, PRAISE 는 아이(보호자 프로필 이름으로), cheerId 를 싣는다")
    void 응원_알림() throws Exception {
        Instant at = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(10);
        CheerSent[] sent = new CheerSent[3];
        tx.executeWithoutResult(status -> {
            sent[0] = sendCheer(CheerKind.DONE, kid, mom, null, "운동 3개 했어요!", at);
            sent[1] = sendCheer(CheerKind.PRAISE, mom, kid, "star", null, at.plusSeconds(1));
        });
        tx.executeWithoutResult(
                status -> sent[2] = sendCheer(CheerKind.THANKS, kid, mom, "heart", null, at.plusSeconds(2)));

        list(mom)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].kind").value("KID_THANKS"))
                .andExpect(jsonPath("$.items[0].title").value("서준이 고맙대요"))
                .andExpect(jsonPath("$.items[0].body").value("사랑해"))
                .andExpect(jsonPath("$.items[0].fromProfileId").value(kid.toString()))
                .andExpect(jsonPath("$.items[0].stickerId").value("heart"))
                .andExpect(jsonPath("$.items[1].kind").value("KID_DONE"))
                .andExpect(jsonPath("$.items[1].title").value("서준이 운동을 마쳤어요"))
                .andExpect(jsonPath("$.items[1].body").value("운동 3개 했어요!"))
                .andExpect(jsonPath("$.items[1].aboutProfileId").value(kid.toString()))
                .andExpect(
                        jsonPath("$.items[1].cheerId").value(sent[0].cheerId().toString()))
                .andExpect(jsonPath("$.items[1].date")
                        .value(LocalDate.ofInstant(sent[0].createdAt(), KST).toString()))
                .andExpect(jsonPath("$.items[1].read").value(false))
                .andExpect(content().string(containsString("\"fromProfileId\":null")))
                .andExpect(content().string(containsString("\"missionId\":null")));
        // 첫 칭찬 스티커라 progress 가 준 업적(첫 스티커)도 AchievementEarned 를 타고 같이 온다 — 받은 시각이 같아 차례는 보지 않는다
        list(kid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.kind == 'PRAISE')].title", contains("은영이 스티커를 붙여 줬어요")))
                .andExpect(jsonPath("$.items[?(@.kind == 'PRAISE')].fromProfileId", contains(mom.toString())))
                .andExpect(jsonPath(
                        "$.items[?(@.kind == 'PRAISE')].cheerId",
                        contains(sent[1].cheerId().toString())))
                .andExpect(jsonPath("$.items[?(@.kind == 'ACHIEVEMENT')].title", contains("새 업적: 첫 스티커")))
                .andExpect(jsonPath("$.items[?(@.kind == 'ACHIEVEMENT')].body", contains("칭찬 스티커를 처음 받았어요")));
        assertThat(count(dad)).isZero();
    }

    @Test
    @DisplayName("응원 트랜잭션이 되돌려지면 알림도 없다 — 커밋 뒤에만 만든다")
    void 되돌린_응원() {
        tx.executeWithoutResult(status -> {
            sendCheer(CheerKind.DONE, kid, mom, null, "했어요", Instant.now());
            status.setRollbackOnly();
        });
        assertThat(count(mom)).isZero();
    }

    @Test
    @DisplayName("알림을 만들다 실패해도 응원은 커밋된 채 남고 예외가 올라가지 않는다")
    void 알림_실패는_응원을_되돌리지_않는다() {
        when(profileQuery.findSummary(kid)).thenThrow(new IllegalStateException("identity 고장"));

        assertThatCode(() -> tx.executeWithoutResult(
                        status -> sendCheer(CheerKind.DONE, kid, mom, null, "했어요", Instant.now())))
                .doesNotThrowAnyException();

        assertThat(jdbc.queryForObject("select count(*) from cheers where family_id = ?", Integer.class, familyId))
                .isEqualTo(1);
        assertThat(count(mom)).isZero();
    }

    @Test
    @DisplayName("업적 — 아이에게만, 본문은 지난 말")
    void 업적_알림() throws Exception {
        Instant earnedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        tx.executeWithoutResult(status -> {
            events.publishEvent(new AchievementEarned(kid, "FIRST_STEP", "첫걸음", "운동 1개를 처음 완료해요", earnedAt));
            events.publishEvent(new AchievementEarned(mom, "FIRST_STEP", "첫걸음", "운동 1개를 처음 완료해요", earnedAt));
        });

        list(kid)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].kind").value("ACHIEVEMENT"))
                .andExpect(jsonPath("$.items[0].title").value("새 업적: 첫걸음"))
                .andExpect(jsonPath("$.items[0].body").value("운동 1개를 처음 완료했어요"));
        assertThat(count(mom)).isZero();
    }

    @Test
    @DisplayName("오늘 서는 미션 — 안 끝낸 아이에게만(걸음수 · 부모 제외), 끝내면 그 사람 것이 빠지고, 미션을 지우면 그 미션 알림이 지워진다")
    void 오늘_서는_미션() throws Exception {
        Instant createdAt = kst(today.minusDays(1), 20);
        Mission squat = mission("스쿼트", TargetMetric.TIMER_MINUTES, createdAt, kid, sibling, mom);
        mission("걸음", TargetMetric.STEPS, createdAt, kid);
        Instant readyAt = today.atTime(7, 30).atZone(KST).toInstant();
        // 07:30 스케줄러는 오늘 기간이 걸친 미션이 있는 가족만 고른다 — 이 가족은 한 번만
        assertThat(standingMissions.familiesWithMissionsOn(today)).containsOnlyOnce(familyId);
        assertThat(standingMissions.familiesWithMissionsOn(today.plusDays(1))).doesNotContain(familyId);

        assertThat(writer.missionReadyForFamily(familyId, today, readyAt)).isEqualTo(2);
        assertThat(writer.missionReadyForFamily(familyId, today, readyAt)).isZero();
        list(kid)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].kind").value("MISSION_READY"))
                .andExpect(jsonPath("$.items[0].title").value("새 운동이 생겼어요"))
                .andExpect(jsonPath("$.items[0].body").value("스쿼트"))
                .andExpect(jsonPath("$.items[0].missionId").value(squat.getId().toString()))
                .andExpect(jsonPath("$.items[0].date").value(today.toString()));
        assertThat(count(mom)).isZero();

        tx.executeWithoutResult(
                status -> events.publishEvent(new MissionCompleted(squat.getId(), kid, familyId, Instant.now())));
        assertThat(count(kid)).isZero();
        assertThat(count(sibling)).isEqualTo(1);

        tx.executeWithoutResult(
                status -> events.publishEvent(new MissionCancelled(squat.getId(), familyId, Instant.now())));
        assertThat(count(sibling)).isZero();
    }

    @Test
    @DisplayName("보호자가 미션을 지우면(DELETE /missions/{id}) 그 미션 알림도 지워진다 — 미션 규칙이 낸 MissionCancelled 를 커밋 뒤에 받는다")
    void 미션을_지우면_알림도_지워진다() throws Exception {
        Mission squat = mission("스쿼트", TargetMetric.TIMER_MINUTES, kst(today.minusDays(1), 20), kid, sibling);
        writer.missionReadyForFamily(
                familyId, today, today.atTime(7, 30).atZone(KST).toInstant());
        assertThat(count(kid)).isEqualTo(1);
        assertThat(count(sibling)).isEqualTo(1);

        mvc.perform(delete("/api/v1/missions/{missionId}", squat.getId())
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(user)))
                .andExpect(status().isNoContent());

        assertThat(count(kid)).isZero();
        assertThat(count(sibling)).isZero();
    }

    @Test
    @DisplayName("다시 재기 — 마지막 측정(여러 회차 중 가장 늦은 날)에서 30일 이상 지난 아이만, 부모 전원에게")
    void 다시_재기() throws Exception {
        test(kid, today.minusDays(45));
        test(kid, today.minusDays(30));
        test(sibling, today.minusDays(29));
        Instant at = today.atTime(9, 0).atZone(KST).toInstant();

        assertThat(writer.remeasureForFamily(familyId, today, at)).isEqualTo(2);

        list(dad)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].kind").value("REMEASURE"))
                .andExpect(jsonPath("$.items[0].title").value("서준 키와 몸무게를 다시 측정해 볼까요?"))
                .andExpect(jsonPath("$.items[0].body").value("마지막 측정 후 30일"))
                .andExpect(jsonPath("$.items[0].aboutProfileId").value(kid.toString()))
                .andExpect(jsonPath("$.items[0].date").isEmpty());
        assertThat(count(mom)).isEqualTo(1);
    }

    @Test
    @DisplayName("다시 재면(측정 등록이 커밋되면) 그 아이의 다시 재기 알림이 부모 모두에게서 빠진다 — 다른 아이 것은 남는다(목은 마지막 측정일로 셈한다)")
    void 다시_재면_다시_재기_알림이_빠진다() throws Exception {
        test(kid, today.minusDays(40));
        test(sibling, today.minusDays(35));
        writer.remeasureForFamily(familyId, today, kst(today, 9));
        assertThat(count(mom)).isEqualTo(2);
        assertThat(count(dad)).isEqualTo(2);

        UUID fresh = test(kid, today);
        tx.executeWithoutResult(status -> events.publishEvent(
                new FitnessTestRegistered(kid, fresh, today, new FitnessTestRegistered.Round(fresh, today))));

        list(mom)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].kind").value("REMEASURE"))
                .andExpect(jsonPath("$.items[0].aboutProfileId").value(sibling.toString()));
        assertThat(count(dad)).isEqualTo(1);
    }

    @Test
    @DisplayName("오늘이 쉬는 날이면 오늘 서는 미션 알림은 목록에서 빠진다 — 07:30 뒤에 쉬는 날 카드를 써도. 다른 알림은 그대로")
    void 쉬는_날에는_오늘_미션_알림이_빠진다() throws Exception {
        mission("스쿼트", TargetMetric.TIMER_MINUTES, kst(today.minusDays(1), 20), kid);
        writer.missionReadyForFamily(
                familyId, today, today.atTime(7, 30).atZone(KST).toInstant());
        tx.executeWithoutResult(status -> sendCheer(CheerKind.DONE, kid, mom, null, "했어요", Instant.now()));
        list(kid).andExpect(jsonPath("$.items[?(@.kind == 'MISSION_READY')]", hasSize(1)));

        restCard(today);

        list(kid)
                .andExpect(jsonPath("$.items[?(@.kind == 'MISSION_READY')]", hasSize(0)))
                .andExpect(jsonPath("$.unread").value(0));
        list(mom).andExpect(jsonPath("$.items[?(@.kind == 'KID_DONE')]", hasSize(1)));
        assertThat(count(kid)).isEqualTo(1);
    }

    @Test
    @DisplayName("목록 — 최신 30건 · 늦은 것부터, MISSION_READY 는 오늘 것만, 업적은 받은 지 14일까지")
    void 보이는_알림() throws Exception {
        for (int i = 0; i < 31; i++) {
            LocalDate last = today.minusDays(40 + i);
            notifications.insertIfAbsent(Notification.remeasure(
                    mom, kid, "서준", last, today, kst(today, 0).minusSeconds(i)));
        }
        list(mom)
                .andExpect(jsonPath("$.items", hasSize(30)))
                .andExpect(jsonPath("$.unread").value(30))
                .andExpect(jsonPath("$.items[0].createdAt").value(kst(today, 0).toString()))
                .andExpect(jsonPath("$.items[29].createdAt")
                        .value(kst(today, 0).minusSeconds(29).toString()));

        UUID mission = UUID.randomUUID();
        notifications.insertIfAbsent(
                Notification.missionReady(kid, mission, "어제 운동", today.minusDays(1), kst(today.minusDays(1), 7)));
        notifications.insertIfAbsent(Notification.missionReady(kid, mission, "오늘 운동", today, kst(today, 7)));
        notifications.insertIfAbsent(Notification.achievement(
                kid, "MIN_30", "30분", "모두 합쳐 30분 운동해요", today.minusDays(15), kst(today.minusDays(15), 12)));
        notifications.insertIfAbsent(Notification.achievement(
                kid, "MIN_100", "100분", "모두 합쳐 100분 운동해요", today.minusDays(14), kst(today.minusDays(14), 12)));

        list(kid)
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].body").value("오늘 운동"))
                .andExpect(jsonPath("$.items[1].title").value("새 업적: 100분"));
    }

    @Test
    @DisplayName("읽음 — 204. upTo 까지만 읽음이 되고 그 뒤 것은 남는다. upTo 가 없으면 전부")
    void 읽음() throws Exception {
        Instant first = kst(today, 0).minusSeconds(120);
        Instant second = kst(today, 0).minusSeconds(60);
        Instant third = kst(today, 0);
        notifications.insertIfAbsent(Notification.remeasure(mom, kid, "서준", today.minusDays(50), today, first));
        notifications.insertIfAbsent(Notification.remeasure(mom, kid, "서준", today.minusDays(40), today, second));
        notifications.insertIfAbsent(Notification.remeasure(mom, kid, "서준", today.minusDays(30), today, third));

        read("{\"profileId\":\"" + mom + "\",\"upTo\":\"" + second + "\"}")
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        list(mom)
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[1].read").value(true))
                .andExpect(jsonPath("$.items[2].read").value(true));

        read("{\"profileId\":\"" + mom + "\"}").andExpect(status().isNoContent());
        list(mom).andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    @DisplayName("권한 — 대신할 수 없는 프로필 403 FORBIDDEN · 다른 가족 403 NOT_SAME_FAMILY · profileId 없으면 400")
    void 권한() throws Exception {
        when(familyAccess.requireActingAs(user, kid)).thenThrow(new CannotActAsProfileException());
        when(familyAccess.requireActingAs(user, dad)).thenThrow(new NotSameFamilyException());

        list(kid)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        read("{\"profileId\":\"" + kid + "\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        list(dad)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
        mvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, auth.bearer(user)))
                .andExpect(status().isBadRequest());
        read("{}").andExpect(status().isBadRequest());
    }

    private Mission mission(String title, TargetMetric metric, Instant createdAt, UUID... participants) {
        Mission mission = Mission.manual(
                UUID.randomUUID(),
                familyId,
                title,
                metric,
                10,
                null,
                today,
                today,
                List.of(participants),
                List.of(),
                mom,
                createdAt);
        tx.executeWithoutResult(status -> missions.save(mission));
        return mission;
    }

    private UUID test(UUID profileId, LocalDate testedOn) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into fitness_tests (id, profile_id, tested_on, source, age_at_test, created_at)
                values (?, ?, ?, 'SELF_INPUT', 9, ?)\
                """, id, profileId, testedOn, Instant.now());
        return id;
    }

    /** 보호자(엄마)가 이 날에 쉬는 날 카드를 썼다. */
    private void restCard(LocalDate day) {
        jdbc.update("""
                insert into rest_cards (id, family_id, rest_date, rest_month, card_no, created_by, created_at)
                values (?, ?, ?, ?, 1, ?, ?)\
                """, UUID.randomUUID(), familyId, day, day.withDayOfMonth(1), mom, Instant.now());
    }
}
