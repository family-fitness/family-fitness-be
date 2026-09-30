package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.identity.api.ReviewFamilyDays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 심사용 로그인으로 들어가면 체험 가족에 지난 2주 기록이 들어 있다. 화면마다 읽는 API 로 확인하고, 화면끼리 숫자가 서로 맞는지 본다.
 * 날마다 무엇을 했는지는 {@link ReviewFamilyDays} 에 있다.
 */
@SpringBootTest(properties = "app.auth.review-login.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewFamilyHistoryApiTest {
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private ExerciseClipRepository clips;

    private final ZoneId seoul = ZoneId.of("Asia/Seoul");
    private final LocalDate today = LocalDate.now(seoul);

    private record Login(UUID familyId, String bearer, Map<String, String> ids) {}

    private Login login() throws Exception {
        int n = NEXT_IP.getAndIncrement();
        String ip = "10.77." + (n / 250) + "." + (n % 250 + 1);
        JsonNode body = read(mvc.perform(post("/api/v1/auth/review-login").with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }))
                .andExpect(status().isOk()));
        UUID familyId =
                UUID.fromString(body.path("profiles").get(0).get("familyId").asString());
        String bearer = "Bearer " + body.get("accessToken").asString();
        JsonNode family = read(
                mvc.perform(get("/api/v1/families/" + familyId + "/profiles").header(HttpHeaders.AUTHORIZATION, bearer))
                        .andExpect(status().isOk()));
        Map<String, String> ids = new LinkedHashMap<>();
        family.get("profiles")
                .forEach(it ->
                        ids.put(it.get("name").asString(), it.get("profileId").asString()));
        return new Login(familyId, bearer, ids);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode getJson(Login login, String path) throws Exception {
        return read(mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, login.bearer()))
                .andExpect(status().isOk()));
    }

    private static List<JsonNode> list(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    private List<JsonNode> missions(Login login) throws Exception {
        return list(getJson(login, "/api/v1/families/" + login.familyId() + "/missions")
                .get("missions"));
    }

    private Map<LocalDate, JsonNode> calendar(Login login, String name) throws Exception {
        JsonNode view = getJson(
                login,
                "/api/v1/families/" + login.familyId() + "/calendar?profileId="
                        + login.ids().get(name) + "&from=" + today.minusDays(ReviewFamilyDays.HISTORY_DAYS) + "&to="
                        + today);
        return list(view.get("days")).stream()
                .collect(Collectors.toMap(it -> LocalDate.parse(it.get("date").asString()), Function.identity()));
    }

    private JsonNode progress(Login login, String name) throws Exception {
        return getJson(login, "/api/v1/profiles/" + login.ids().get(name) + "/progress");
    }

    private List<JsonNode> cheersTo(Login login, String name) throws Exception {
        return list(getJson(
                        login,
                        "/api/v1/families/" + login.familyId() + "/cheers?size=100&toProfileId="
                                + login.ids().get(name))
                .get("cheers"));
    }

    /** 알림은 커밋 뒤 따로 만든다. 기대한 종류가 다 생길 때까지 잠깐 기다린다. */
    private Set<String> notificationKinds(Login login, String name, Set<String> expected) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        Set<String> kinds;
        do {
            kinds =
                    list(getJson(
                                            login,
                                            "/api/v1/notifications?profileId="
                                                    + login.ids().get(name))
                                    .get("items"))
                            .stream()
                            .map(it -> it.get("kind").asString())
                            .collect(Collectors.toSet());
            if (kinds.containsAll(expected)) return kinds;
            Thread.sleep(100);
        } while (Instant.now().isBefore(deadline));
        return kinds;
    }

    private static boolean doneBy(JsonNode mission, String profileId) {
        return list(mission.get("participants")).stream()
                .anyMatch(p -> p.get("profileId").asString().equals(profileId)
                        && p.get("completed").asBoolean());
    }

    private static int doneSessions(JsonNode mission, String profileId) {
        return list(mission.get("participants")).stream()
                .filter(p -> p.get("profileId").asString().equals(profileId))
                .mapToInt(p -> p.get("doneSessions").size())
                .sum();
    }

    private static boolean joins(JsonNode mission, String profileId) {
        return list(mission.get("participants")).stream()
                .anyMatch(p -> p.get("profileId").asString().equals(profileId));
    }

    @Test
    @DisplayName("지난 2주 미션이 거의 날마다 있고, 네 식구가 함께 한 미션과 빠진 날, 쉬는 날이 있고, 오늘 하윤 미션은 아직 안 끝났다")
    void 지난_2주_미션() throws Exception {
        Login login = login();
        String hayun = login.ids().get("하윤");
        String seojun = login.ids().get("서준");
        List<JsonNode> missions = missions(login);

        int pastDays = ReviewFamilyDays.HISTORY_DAYS - 1;
        int familyDays = ReviewFamilyDays.FAMILY_DAYS.size();
        assertThat(missions).hasSize(familyDays + (pastDays - familyDays) * 2 + 2);
        assertThat(missions.stream().filter(it -> it.get("participants").size() == 4))
                .hasSize(familyDays);
        List<JsonNode> todays = missions.stream()
                .filter(it -> it.get("startDate").asString().equals(today.toString()))
                .toList();
        assertThat(todays.stream().filter(it -> joins(it, hayun)).toList())
                .singleElement()
                .satisfies(it -> assertThat(doneBy(it, hayun)).isFalse());
        assertThat(todays.stream().filter(it -> joins(it, seojun)).toList())
                .singleElement()
                .satisfies(it -> assertThat(doneBy(it, seojun)).isTrue());
        assertThat(missions).noneMatch(it -> it.get("startDate")
                .asString()
                .equals(today.minusDays(ReviewFamilyDays.REST_DAY).toString()));
        assertThat(missions.stream()
                        .filter(it -> joins(it, hayun) && !doneBy(it, hayun))
                        .map(it -> it.get("startDate").asString())
                        .toList())
                .containsExactlyInAnyOrder(
                        today.minusDays(ReviewFamilyDays.YOUTH_MISSED_DAY).toString(), today.toString());
    }

    @Test
    @DisplayName("칸 영상은 모두 실제 클립 표에 있고, 공단 영상과 유튜브 구간이 섞이며, 날마다 다르다")
    void 칸_영상은_실제_클립이다() throws Exception {
        Login login = login();
        List<JsonNode> sessions = missions(login).stream()
                .flatMap(it -> list(it.get("sessions")).stream())
                .toList();

        assertThat(sessions).isNotEmpty().allSatisfy(it -> {
            JsonNode clip = it.get("clip");
            assertThat(clip.isNull()).isFalse();
            ExerciseClip found = clips.findById(ExerciseClip.idOf(
                    clip.get("videoId").asString(), clip.get("startSec").asInt()));
            assertThat(found).as(clip.toString()).isNotNull();
            assertThat(found.active()).isTrue();
        });
        assertThat(sessions).anyMatch(it -> !it.get("clip").get("mediaUrl").isNull());
        assertThat(sessions).anyMatch(it -> it.get("clip").get("mediaUrl").isNull());
        Set<String> mainClips = sessions.stream()
                .filter(it -> it.get("phase").asString().equals("MAIN"))
                .map(it -> it.get("clip").get("videoId").asString() + "-"
                        + it.get("clip").get("startSec").asInt())
                .collect(Collectors.toSet());
        assertThat(mainClips).hasSizeGreaterThan(5);
    }

    @Test
    @DisplayName("캘린더, 연속 기록, 경험치가 미션 기록과 서로 맞는다")
    void 캘린더_연속_기록_경험치가_서로_맞는다() throws Exception {
        Login login = login();
        List<JsonNode> missions = missions(login);
        for (String name : List.of("하윤", "서준")) {
            String id = login.ids().get(name);
            Map<LocalDate, JsonNode> days = calendar(login, name);
            JsonNode progress = progress(login, name);

            assertThat(days.get(today.minusDays(ReviewFamilyDays.REST_DAY))
                            .path("rest")
                            .asBoolean())
                    .as(name)
                    .isTrue();
            Set<LocalDate> moved = new HashSet<>();
            days.forEach((date, day) -> {
                if (day.get("minutes").asInt() > 0) moved.add(date);
            });
            Set<LocalDate> doneDays = missions.stream()
                    .filter(it -> doneBy(it, id))
                    .map(it -> LocalDate.parse(it.get("startDate").asString()))
                    .collect(Collectors.toSet());
            assertThat(moved).as(name).isEqualTo(doneDays);
            assertThat(moved).as(name).hasSizeGreaterThanOrEqualTo(10);

            int streak = 0;
            if (moved.contains(today)) streak++;
            for (int ago = 1; ago <= ReviewFamilyDays.HISTORY_DAYS; ago++) {
                LocalDate date = today.minusDays(ago);
                JsonNode day = days.get(date);
                if (day.path("rest").asBoolean()) continue;
                if (moved.contains(date)) streak++;
                else if (!day.get("entries").isEmpty()) break;
            }
            assertThat(progress.get("streakDays").asInt())
                    .as(name)
                    .isEqualTo(streak)
                    .isGreaterThanOrEqualTo(5);

            int sessions =
                    missions.stream().mapToInt(it -> doneSessions(it, id)).sum();
            long stickers = cheersTo(login, name).stream()
                    .filter(it -> it.get("kind").asString().equals("PRAISE"))
                    .count();
            int remeasure = 20;
            assertThat(progress.get("xp").asInt())
                    .as(name)
                    .isEqualTo(sessions * 5 + doneDays.size() * 20 + (int) stickers * 10 + remeasure);
            assertThat(progress.get("level").asInt()).as(name).isGreaterThan(1);
            assertThat(list(progress.get("achievements")).stream()
                            .map(it -> it.get("code").asString())
                            .toList())
                    .as(name)
                    .contains("FIRST_STEP", "FULL_SET", "TOGETHER", "REMEASURE", "FIRST_STICKER");
        }
    }

    @Test
    @DisplayName("아이마다 두 번, 보호자는 한 번 쟀고, 아이의 나중 값이 조금 낫다")
    void 측정_기록() throws Exception {
        Login login = login();
        for (String name : List.of("하윤", "서준")) {
            JsonNode history = getJson(login, "/api/v1/profiles/" + login.ids().get(name) + "/fitness-tests");
            assertThat(history.toString())
                    .contains(today.minusDays(ReviewFamilyDays.FIRST_TEST_DAY).toString());
            assertThat(history.toString()).contains(today.minusDays(3).toString());
        }
        for (String name : List.of("엄마", "아빠")) {
            JsonNode latest = getJson(login, "/api/v1/profiles/" + login.ids().get(name) + "/fitness-tests/latest");
            assertThat(latest.get("testedOn").asString())
                    .as(name)
                    .isEqualTo(today.minusDays(ReviewFamilyDays.PARENT_TEST_DAY).toString());
        }
    }

    @Test
    @DisplayName("응원 스티커와 고마워요가 오가고, 알림은 실제로 있는 종류로 생긴다")
    void 응원과_알림() throws Exception {
        Login login = login();
        List<JsonNode> toHayun = cheersTo(login, "하윤");
        List<JsonNode> toSeojun = cheersTo(login, "서준");
        List<JsonNode> toMom = cheersTo(login, "엄마");

        assertThat(toHayun).hasSize(2).allMatch(it -> it.get("kind").asString().equals("PRAISE"));
        assertThat(toSeojun).hasSize(2).allMatch(it -> it.get("kind").asString().equals("PRAISE"));
        assertThat(toMom.stream().map(it -> it.get("kind").asString()).toList())
                .containsExactlyInAnyOrder("THANKS", "THANKS", "DONE");
        Map<LocalDate, JsonNode> days = calendar(login, "하윤");
        int stickersOnCalendar =
                days.values().stream().mapToInt(it -> it.get("stickers").size()).sum();
        assertThat(stickersOnCalendar).isEqualTo(toHayun.size());

        assertThat(notificationKinds(login, "하윤", Set.of("PRAISE", "ACHIEVEMENT")))
                .contains("PRAISE", "ACHIEVEMENT");
        assertThat(notificationKinds(login, "엄마", Set.of("KID_THANKS", "KID_DONE")))
                .contains("KID_THANKS", "KID_DONE");
    }

    @Test
    @DisplayName("체험 리그 방에서 우리 가족 칸에 달성률과 순위 점수가 있다")
    void 리그_달성률() throws Exception {
        Login login = login();
        JsonNode league = getJson(login, "/api/v1/families/" + login.familyId() + "/league");

        assertThat(league.get("rate").isNull()).isFalse();
        assertThat(league.get("rate").asInt()).isPositive();
        assertThat(league.get("score").asDouble()).isPositive();
        JsonNode mine = list(league.get("standings")).stream()
                .filter(it -> it.get("me").asBoolean())
                .findFirst()
                .orElseThrow();
        assertThat(mine.get("rate").asInt()).isEqualTo(league.get("rate").asInt());
    }

    @Test
    @DisplayName("체험 가족을 여러 번 만들어도 기록이 서로 섞이지 않는다")
    void 여러_번_만들어도_섞이지_않는다() throws Exception {
        Login first = login();
        Login second = login();

        List<JsonNode> a = missions(first);
        List<JsonNode> b = missions(second);
        assertThat(a).hasSameSizeAs(b);
        Set<String> firstMembers = new HashSet<>(first.ids().values());
        Set<String> secondMembers = new HashSet<>(second.ids().values());
        List<String> participantsA = new ArrayList<>();
        a.forEach(it -> it.get("participants")
                .forEach(p -> participantsA.add(p.get("profileId").asString())));
        assertThat(participantsA).allMatch(firstMembers::contains).noneMatch(secondMembers::contains);
        assertThat(progress(second, "하윤").get("xp").asInt())
                .isEqualTo(progress(first, "하윤").get("xp").asInt());
        assertThat(cheersTo(second, "하윤")).hasSize(2);
    }
}
