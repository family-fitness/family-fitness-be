package kr.ac.kookmin.familyfitness.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 화면이 부르는 조회 주소마다 DB 에 보내는 SQL 수를 센다(11주차 서비스 점검).
 * <ul>
 *   <li>체험 가족(심사용 로그인, 식구 넷 · 측정 · 지난 2주 기록)으로 부른 뒤, 아이 둘을 더 넣고 다시 부른다.
 *       식구가 늘어도 SQL 수가 늘지 않아야 한다 — 식구마다 한 번씩 더 묻는 코드(N+1)가 들어오면 여기서 걸린다.
 *   <li>어느 주소든 한 번에 {@link #BUDGET} 개를 넘지 않는다.
 * </ul>
 * SQL 수는 커넥션에서 문장을 준비한 횟수다(JPA · JdbcClient 모두 센다). 처음 한 번은 캐시를 데우려고 버리고 두 번째 값을 쓴다.
 */
@SpringBootTest(properties = "app.auth.review-login.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScreenQueryBudgetTest {
    /** 한 요청이 보내도 되는 SQL 수. 11주차 측정에서 가장 많은 주소가 14개였다. */
    static final int BUDGET = 20;

    static final AtomicInteger STATEMENTS = new AtomicInteger();

    @TestConfiguration
    static class CountingDataSource {
        @Bean
        static BeanPostProcessor countStatements() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource dataSource)) return bean;
                    return Proxy.newProxyInstance(
                            DataSource.class.getClassLoader(),
                            new Class<?>[] {DataSource.class},
                            (proxy, method, args) -> {
                                Object result = invoke(method, dataSource, args);
                                return result instanceof Connection c ? counting(c) : result;
                            });
                }
            };
        }

        private static Connection counting(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.startsWith("prepare") || name.equals("createStatement")) {
                            STATEMENTS.incrementAndGet();
                        }
                        return invoke(method, connection, args);
                    });
        }

        private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

    private record Family(String bearer, String familyId, String parentId, String kidId) {}

    private Family reviewFamily() throws Exception {
        JsonNode login =
                json.readTree(mvc.perform(post("/api/v1/auth/review-login").with(request -> {
                            request.setRemoteAddr("10.77.0.1");
                            return request;
                        }))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        JsonNode me = login.path("profiles").get(0);
        String bearer = "Bearer " + login.get("accessToken").asString();
        String familyId = me.get("familyId").asString();
        String kidId = null;
        for (JsonNode profile :
                read(bearer, "/api/v1/families/" + familyId + "/profiles").path("profiles")) {
            if (profile.path("name").asString().equals("하윤"))
                kidId = profile.get("profileId").asString();
        }
        assertThat(kidId).isNotNull();
        return new Family(bearer, familyId, me.get("profileId").asString(), kidId);
    }

    private JsonNode read(String bearer, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer))
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private List<String> screens(Family f) {
        String family = "/api/v1/families/" + f.familyId();
        return List.of(
                "/api/v1/me",
                family + "/profiles",
                family + "/missions",
                family + "/calendar?profileId=" + f.kidId() + "&from=" + today.minusDays(41) + "&to=" + today,
                family + "/cheers",
                family + "/league",
                family + "/report/weekly",
                family + "/fitness-map",
                "/api/v1/notifications?profileId=" + f.parentId(),
                "/api/v1/notifications?profileId=" + f.kidId(),
                "/api/v1/profiles/" + f.kidId() + "/progress",
                "/api/v1/profiles/" + f.kidId() + "/fitness-tests/latest",
                "/api/v1/profiles/" + f.kidId() + "/fitness-tests",
                "/api/v1/profiles/" + f.parentId() + "/fitness-age",
                "/api/v1/profiles/" + f.kidId() + "/availability",
                "/api/v1/videos",
                "/api/v1/exercises");
    }

    /** 주소마다 두 번 불러 두 번째의 SQL 수. 응답은 모두 200 이어야 한다. */
    private Map<String, Integer> measure(Family f) throws Exception {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String path : screens(f)) {
            for (int round = 0; round < 2; round++) {
                STATEMENTS.set(0);
                mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, f.bearer()))
                        .andExpect(status().isOk());
                if (round == 1) counts.put(templateOf(path, f), STATEMENTS.get());
            }
        }
        return counts;
    }

    private static String templateOf(String path, Family f) {
        String template = path.replace(f.familyId(), "{familyId}")
                .replace(f.kidId(), "{kid}")
                .replace(f.parentId(), "{parent}");
        int query = template.indexOf("&from=");
        return query < 0 ? template : template.substring(0, query);
    }

    private void addChild(Family f, String name) throws Exception {
        mvc.perform(post("/api/v1/families/" + f.familyId() + "/profiles")
                        .header(HttpHeaders.AUTHORIZATION, f.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"birthDate\":\"" + today.minusYears(9)
                                + "\",\"sex\":\"M\",\"role\":\"CHILD\","
                                + "\"guardianConsent\":{\"personalData\":true,\"healthData\":true}}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("화면 조회 주소 17곳은 SQL 을 20개 넘게 보내지 않고, 식구가 넷에서 여섯으로 늘어도 SQL 수가 그대로다")
    void 식구가_늘어도_SQL_수가_그대로다() throws Exception {
        Family family = reviewFamily();
        Map<String, Integer> four = measure(family);

        addChild(family, "도윤");
        addChild(family, "지아");
        Map<String, Integer> six = measure(family);

        System.out.println("화면별 SQL 수(식구 넷 → 여섯)");
        four.forEach((path, n) -> System.out.printf("  %-60s %2d → %2d%n", path, n, six.get(path)));
        assertThat(four.values()).allSatisfy(n -> assertThat(n).isLessThanOrEqualTo(BUDGET));
        assertThat(six).isEqualTo(four);
    }
}
