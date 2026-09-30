package kr.ac.kookmin.familyfitness.progress.adapter.inbound.web;

import static kr.ac.kookmin.familyfitness.fitness.application.FitnessFakes.detailsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessTestRegistered;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessTestRegistered.Round;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.CheerSent;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.progress.api.ProgressRecorder;
import kr.ac.kookmin.familyfitness.progress.api.SessionDone;
import kr.ac.kookmin.familyfitness.shared.ai.AiGateway;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import kr.ac.kookmin.familyfitness.support.TestAuth;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * H2 + Flyway(V140 progress) 위에서 GET /profiles/{profileId}/progress 를 끝까지 돈다.
 * 적립은 실제 경로로 — 칸 끝은 {@link ProgressRecorder}, 스티커 · 다시 재기는 이벤트 발행(같은 트랜잭션 동기 리스너),
 * 잡힌 날은 coaching 의 실제 구현(미션 표)이다. identity 는 목: 같은 가족 판단과 가족 구성원 목록만 흉내 낸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProgressWebTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    TestAuth auth;

    @Autowired
    ProfileRows rows;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @Autowired
    ActivityRecorder activity;

    @Autowired
    ProgressRecorder progress;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    MissionRepository missions;

    @MockitoBean
    FamilyAccess familyAccess;

    @MockitoBean
    ProfileQuery profileQuery;

    @MockitoBean
    CheerQuery cheerQuery;

    @MockitoBean
    AiGateway ai;

    private final UUID parentUser = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private UUID familyId;
    private UUID momId;
    private UUID kidId;

    @BeforeEach
    void setUp() {
        familyId = rows.family();
        momId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        kidId = rows.profile(familyId, LocalDate.of(2016, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        ProfileSummary mom = summary(momId, ProfileRole.PARENT);
        ProfileSummary kid = summary(kidId, ProfileRole.CHILD);
        when(familyAccess.requireSameFamilyAsProfile(parentUser, kidId)).thenReturn(kid);
        when(familyAccess.requireSameFamilyAsProfile(parentUser, momId)).thenReturn(mom);
        when(profileQuery.summariesOfFamily(familyId)).thenReturn(List.of(mom, kid));
    }

    private ProfileSummary summary(UUID profileId, ProfileRole role) {
        return new ProfileSummary(
                profileId,
                familyId,
                role == ProfileRole.PARENT ? "엄마" : "서준",
                role,
                role == ProfileRole.PARENT ? AgeGroup.ADULT : AgeGroup.YOUTH,
                Sex.F,
                true,
                InviteStatus.CLAIMED,
                null,
                true,
                role == ProfileRole.CHILD,
                true);
    }

    private ResultActions read(UUID userId, UUID profileId) throws Exception {
        return mvc.perform(get("/api/v1/profiles/" + profileId + "/progress")
                .header(HttpHeaders.AUTHORIZATION, auth.bearer(userId)));
    }

    private SessionDone done(UUID missionId, int position, boolean completed) {
        return new SessionDone(
                familyId,
                kidId,
                missionId,
                position,
                position == 1 ? SessionDone.Phase.WARMUP : SessionDone.Phase.MAIN,
                null,
                completed,
                completed ? SessionDone.Verification.TIMER : null,
                today,
                Instant.now());
    }

    private int ledgerRows(UUID profileId) {
        em.flush();
        return Objects.requireNonNull(jdbc.queryForObject(
                "select count(*) from progress_xp_events where profile_id = ?", Integer.class, profileId));
    }

    /** 아이의 측정 회차를 실제 주소로 등록한다(항목 하나). */
    private void registerTest(LocalDate testedOn) throws Exception {
        mvc.perform(post("/api/v1/profiles/" + kidId + "/fitness-tests")
                        .header(HttpHeaders.AUTHORIZATION, auth.bearer(parentUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"testedOn\":\"" + testedOn + "\",\"source\":\"SELF_INPUT\",\"heightCm\":null,"
                                + "\"weightKg\":null,\"items\":[{\"itemCode\":\"028\",\"value\":30}]}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("아무것도 없는 아이 — Lv.1 · 0 · 다음 80 · 업적 열두 개 모두 아직 · 최근 줄 없음")
    void 아무것도_없는_아이() throws Exception {
        read(parentUser, kidId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(kidId.toString()))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.xp").value(0))
                .andExpect(jsonPath("$.levelFloorXp").value(0))
                .andExpect(jsonPath("$.nextLevelXp").value(80))
                .andExpect(jsonPath("$.streakDays").value(0))
                .andExpect(jsonPath("$.activeDays").value(0))
                .andExpect(jsonPath("$.achievements", hasSize(12)))
                .andExpect(jsonPath("$.achievements[0].code").value("FIRST_STEP"))
                .andExpect(jsonPath("$.achievements[0].title").value("첫걸음"))
                .andExpect(jsonPath("$.achievements[0].description").value("운동 한 칸을 처음 끝내요"))
                .andExpect(jsonPath("$.achievements[0].earnedAt").value(nullValue()))
                .andExpect(jsonPath("$.achievements[11].code").value("SIX_POWERS"))
                .andExpect(jsonPath("$.recentXp", empty()));
    }

    @Test
    @DisplayName("칸 끝 · 칭찬 스티커 · 다시 재기가 원장에 쌓여 경험치 · 업적 · 최근 줄에 나온다 — 같은 칸은 두 번 쌓이지 않는다")
    void 적립이_읽기에_나온다() throws Exception {
        UUID missionId = UUID.randomUUID();
        activity.addActiveMinutes(kidId, today, ActivitySource.TIMER, 12);
        assertThat(progress.sessionDone(done(missionId, 1, false))).isEqualTo(5);
        assertThat(progress.sessionDone(done(missionId, 1, false))).isZero();
        assertThat(progress.sessionDone(done(missionId, 2, true))).isEqualTo(25);
        events.publishEvent(new CheerSent(
                UUID.randomUUID(),
                familyId,
                momId,
                kidId,
                CheerKind.PRAISE,
                "star",
                null,
                missionId,
                null,
                Instant.now()));
        events.publishEvent(new CheerSent(
                UUID.randomUUID(), familyId, kidId, momId, CheerKind.THANKS, "heart", null, null, null, Instant.now()));
        events.publishEvent(new FitnessTestRegistered(kidId, UUID.randomUUID(), today.minusDays(30), null));
        UUID remeasured = UUID.randomUUID();
        events.publishEvent(new FitnessTestRegistered(kidId, remeasured, today, new Round(remeasured, today)));

        assertThat(ledgerRows(kidId)).isEqualTo(5);
        assertThat(ledgerRows(momId)).isZero();
        read(parentUser, kidId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xp").value(60))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.activeDays").value(1))
                .andExpect(jsonPath("$.streakDays").value(1))
                .andExpect(jsonPath("$.achievements[0].earnedAt").value(notNullValue()))
                .andExpect(jsonPath("$.achievements[8].code").value("REMEASURE"))
                .andExpect(jsonPath("$.achievements[8].earnedAt").value(notNullValue()))
                .andExpect(jsonPath("$.achievements[9].code").value("FIRST_STICKER"))
                .andExpect(jsonPath("$.achievements[9].earnedAt").value(notNullValue()))
                .andExpect(jsonPath("$.recentXp", hasSize(3)))
                .andExpect(jsonPath("$.recentXp[*].kind", containsInAnyOrder("MISSION_DONE", "STICKER", "REMEASURE")))
                .andExpect(jsonPath("$.recentXp[?(@.kind == 'MISSION_DONE')].amount", containsInAnyOrder(30)))
                .andExpect(jsonPath(
                        "$.recentXp[?(@.kind == 'STICKER')].fromProfileId", containsInAnyOrder(momId.toString())))
                .andExpect(jsonPath(
                        "$.recentXp[?(@.kind == 'REMEASURE')].occurredOn", containsInAnyOrder(today.toString())))
                // 전환기 칸 — 지금 FE 는 reason · at 을 그대로 그린다. 아이가 읽는 줄이라 보호자는 「엄마」 다
                .andExpect(jsonPath(
                        "$.recentXp[*].reason", containsInAnyOrder("운동을 다 했어요", "엄마가 붙여 준 스티커", "키와 몸무게를 새로 쟀어요")))
                .andExpect(jsonPath("$.recentXp[*].at", everyItem(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.+Z"))));

        // 부모 프로필도 답한다 — 고마워요 스티커로는 쌓이지 않는다
        read(parentUser, momId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xp").value(0))
                .andExpect(jsonPath("$.achievements[9].earnedAt").value(nullValue()));
    }

    @Test
    @DisplayName("측정을 주소로 등록하면 testedOn 이 가장 이른 회차를 뺀 회차마다 +20 — 지난 날짜를 나중에 적어도 FE 목(tests.slice(0, -1))과 같다")
    void 측정_등록으로_다시_재기가_쌓인다() throws Exception {
        when(profileQuery.findDetails(kidId))
                .thenReturn(detailsOf(kidId, familyId, LocalDate.of(2016, 5, 1), Sex.M, null, null, true));
        LocalDate first = today.minusDays(8);
        LocalDate later = today.minusDays(1);
        LocalDate backfilled = today.minusDays(30);
        LocalDate between = today.minusDays(15);
        for (LocalDate on : List.of(first, later, backfilled, between)) registerTest(on);
        em.flush();

        Map<LocalDate, String> testIdOn = jdbc
                .query(
                        "select id, tested_on from fitness_tests where profile_id = ?",
                        (rs, i) -> Map.entry(
                                rs.getObject("tested_on", LocalDate.class),
                                rs.getObject("id").toString()),
                        kidId)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<String> remeasureKeys = jdbc.queryForList(
                "select source_key from progress_xp_events where profile_id = ? and kind = 'REMEASURE'",
                String.class,
                kidId);
        // 가장 이른 회차(30일 전)만 빠진다. 처음 등록한 8일 전 회차는 30일 전 회차가 들어온 순간 +20 을 받는다
        assertThat(remeasureKeys)
                .containsExactlyInAnyOrder(testIdOn.get(first), testIdOn.get(later), testIdOn.get(between));
        read(parentUser, kidId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xp").value(60))
                .andExpect(jsonPath("$.achievements[8].code").value("REMEASURE"))
                .andExpect(jsonPath("$.achievements[8].earnedAt").value(notNullValue()));
    }

    @Test
    @DisplayName("이어서 한 날 — 잡힌 날(미션 표)을 빼먹으면 끊기고, 그날을 쉬는 날로 쓰면 건너서 잇는다")
    void 잡힌_날과_쉬는_날() throws Exception {
        activity.addActiveMinutes(kidId, today.minusDays(1), ActivitySource.TIMER, 5);
        activity.addActiveMinutes(kidId, today.minusDays(3), ActivitySource.VIDEO, 5);
        read(parentUser, kidId).andExpect(jsonPath("$.streakDays").value(2));

        LocalDate missed = today.minusDays(2);
        missions.save(Mission.manual(
                UUID.randomUUID(),
                familyId,
                "스쿼트",
                TargetMetric.TIMER_MINUTES,
                10,
                null,
                missed,
                missed,
                List.of(kidId),
                List.of(),
                momId,
                Instant.now()));
        em.flush();
        read(parentUser, kidId).andExpect(jsonPath("$.streakDays").value(1));

        jdbc.update(
                "insert into rest_cards (id, family_id, rest_date, rest_month, card_no, created_by, created_at) values (?, ?, ?, ?, 1, ?, ?)",
                UUID.randomUUID(),
                familyId,
                missed,
                YearMonth.from(missed).atDay(1),
                momId,
                Instant.now());
        read(parentUser, kidId)
                .andExpect(jsonPath("$.streakDays").value(2))
                .andExpect(jsonPath("$.activeDays").value(2));
    }

    @Test
    @DisplayName("같은 가족이 아니면 403 NOT_SAME_FAMILY")
    void 같은_가족이_아니면_403() throws Exception {
        UUID stranger = UUID.randomUUID();
        when(familyAccess.requireSameFamilyAsProfile(stranger, kidId)).thenThrow(new NotSameFamilyException());
        read(stranger, kidId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_SAME_FAMILY"));
    }
}
