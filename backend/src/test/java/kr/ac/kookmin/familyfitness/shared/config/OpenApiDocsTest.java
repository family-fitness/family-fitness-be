package kr.ac.kookmin.familyfitness.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * /v3/api-docs 가 실제 응답 모양과 같은지 본다. FE 는 이 문서로 타입을 만든다(openapi-typescript).
 * 실제 응답은 null 을 싣는데 문서에 "null" 이 없으면 FE 목이 null 을 넣을 때 타입 검사가 깨진다(QA CT-04).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    private JsonNode docs;

    @BeforeEach
    void loadDocs() throws Exception {
        docs = json.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode property(String schema, String property) {
        JsonNode node = docs.path("components")
                .path("schemas")
                .path(schema)
                .path("properties")
                .path(property);
        assertThat(node.isMissingNode())
                .as(schema + "." + property + " 가 문서에 있다")
                .isFalse();
        return node;
    }

    private static List<String> typesOf(JsonNode schema) {
        JsonNode type = schema.path("type");
        if (type.isArray())
            return StreamSupport.stream(type.spliterator(), false)
                    .map(JsonNode::asString)
                    .toList();
        return type.isString() ? List.of(type.asString()) : List.of();
    }

    @Test
    @DisplayName("jspecify @Nullable 칸은 type 에 \"null\" 이 붙고, 아닌 칸은 그대로다")
    void Nullable_칸은_type_에_null_이_붙는다() {
        assertThat(docs.path("openapi").asString()).startsWith("3.1");

        assertThat(typesOf(property("ProfileSummary", "supportMode"))).containsExactlyInAnyOrder("string", "null");
        assertThat(property("ProfileSummary", "supportMode").path("enum"))
                .extracting(JsonNode::isNull)
                .contains(true);
        assertThat(typesOf(property("MissionView", "coachRunId"))).containsExactlyInAnyOrder("string", "null");
        assertThat(typesOf(property("MissionView", "rationale"))).containsExactlyInAnyOrder("string", "null");
        assertThat(typesOf(property("XpLineView", "fromProfileId"))).containsExactlyInAnyOrder("string", "null");

        // null 이 아닌 칸은 건드리지 않는다
        assertThat(typesOf(property("ProfileSummary", "profileId"))).containsExactly("string");
        assertThat(typesOf(property("ProfileSummary", "role"))).containsExactly("string");
        assertThat(typesOf(property("MissionView", "missionId"))).containsExactly("string");
        assertThat(typesOf(property("XpLineView", "amount"))).containsExactly("integer");
    }

    @Test
    @DisplayName("@Nullable 인 다른 스키마 칸은 $ref 와 null 의 oneOf 다 — $ref 옆에 type 을 붙이지 않는다")
    void Nullable_인_참조_칸은_oneOf_다() {
        JsonNode video = property("MissionView", "video");

        assertThat(video.has("$ref")).isFalse();
        List<JsonNode> choices = new ArrayList<>();
        video.path("oneOf").forEach(choices::add);
        assertThat(choices).hasSize(2);
        assertThat(choices.get(0).path("$ref").asString()).isEqualTo("#/components/schemas/MissionVideoView");
        assertThat(typesOf(choices.get(1))).containsExactly("null");
        // 같은 스키마를 null 없이 쓰는 칸은 $ref 그대로다
        assertThat(docs.path("components")
                        .path("schemas")
                        .path("MissionVideoView")
                        .isMissingNode())
                .isFalse();
    }

    @Test
    @DisplayName("검증으로 null 을 막는 요청 칸(@NotNull)은 null 을 싣지 않고, required 목록은 그대로다")
    void NotNull_요청_칸은_null_을_싣지_않는다() {
        assertThat(typesOf(property("CheerRequest", "fromProfileId"))).containsExactly("string");
        assertThat(typesOf(property("CheerRequest", "message"))).containsExactlyInAnyOrder("string", "null");
        assertThat(docs.path("components").path("schemas").path("CheerRequest").path("required"))
                .extracting(JsonNode::asString)
                .containsExactlyInAnyOrder("fromProfileId", "toProfileId");
    }

    @Test
    @DisplayName("측정 요청 · 응답의 체지방률 · 허리둘레는 null 을 싣는 number 다")
    void 체지방률_허리둘레는_null_을_싣는_number_다() {
        for (String schema : List.of("RegisterFitnessTestRequest", "FitnessTestResponse", "LatestFitnessResponse")) {
            for (String name : List.of("bodyFatPct", "waistCm")) {
                assertThat(typesOf(property(schema, name)))
                        .as(schema + "." + name)
                        .containsExactlyInAnyOrder("number", "null");
            }
        }
    }

    @Test
    @DisplayName("요청 검증용 getter(isContentPresent)는 CheerRequest 칸으로 나가지 않는다")
    void CheerRequest_에_contentPresent_가_없다() {
        JsonNode properties =
                docs.path("components").path("schemas").path("CheerRequest").path("properties");

        assertThat(properties.has("fromProfileId")).isTrue();
        assertThat(properties.has("contentPresent")).isFalse();
    }

    private boolean deprecated(String path, String method) {
        JsonNode operation = docs.path("paths").path(path).path(method);
        assertThat(operation.isMissingNode())
                .as(method + " " + path + " 가 문서에 있다")
                .isFalse();
        JsonNode flag = operation.path("deprecated");
        return flag.isBoolean() && flag.booleanValue();
    }

    @Test
    @DisplayName("전환기 별칭(FE 가 부르는 옛 이름)은 문서에 deprecated 로 실리고, 같은 핸들러의 계약 이름은 그대로다")
    void 전환기_별칭은_deprecated_다() {
        Map<String, List<String>> aliases = Map.of(
                "/api/v1/missions/{missionId}/sessions/{seq}/done", List.of("post"),
                "/api/v1/families/{familyId}/rest-days", List.of("get", "post"),
                "/api/v1/families/{familyId}/rest-days/{restDate}", List.of("delete"),
                "/api/v1/clips", List.of("get"),
                "/api/v1/clips/{exerciseId}/favorite", List.of("post"));
        Map<String, List<String>> contract = Map.of(
                "/api/v1/missions/{missionId}/sessions/{seq}/complete", List.of("post"),
                "/api/v1/families/{familyId}/rest-cards", List.of("get", "post"),
                "/api/v1/families/{familyId}/rest-cards/{restDate}", List.of("delete"),
                "/api/v1/exercises", List.of("get"),
                "/api/v1/exercises/{exerciseId}/favorite", List.of("post"));

        aliases.forEach((path, methods) -> methods.forEach(method ->
                assertThat(deprecated(path, method)).as(method + " " + path).isTrue()));
        contract.forEach((path, methods) -> methods.forEach(method ->
                assertThat(deprecated(path, method)).as(method + " " + path).isFalse()));
        // 걷은 별칭이 목록에만 남아 있지 않게 — 목록의 경로는 모두 문서에 있다
        assertThat(OpenApiConfig.TRANSITIONAL_ALIASES).containsExactlyInAnyOrderElementsOf(aliases.keySet());
    }

    private String operationId(String path, String method) {
        return docs.path("paths").path(path).path(method).path("operationId").asString();
    }

    @Test
    @DisplayName("별칭의 operationId 는 「계약 이름_transitional」 이다 — 계약 경로의 operationId 가 별칭 때문에 뒤붙임을 받지 않게")
    void 계약_경로가_operationId_를_지킨다() {
        assertThat(operationId("/api/v1/missions/{missionId}/sessions/{seq}/complete", "post"))
                .isEqualTo("completeSession");
        assertThat(operationId("/api/v1/missions/{missionId}/sessions/{seq}/done", "post"))
                .isEqualTo("completeSession_transitional");
        assertThat(operationId("/api/v1/families/{familyId}/rest-cards", "get")).isEqualTo("month");
        assertThat(operationId("/api/v1/families/{familyId}/rest-days", "get")).isEqualTo("month_transitional");
        assertThat(operationId("/api/v1/families/{familyId}/rest-cards", "post"))
                .isEqualTo("use");
        assertThat(operationId("/api/v1/families/{familyId}/rest-cards/{restDate}", "delete"))
                .isEqualTo("cancel");
        // 다른 컨트롤러와 겹치는 메서드 이름(list · favorite)은 계약 경로 쪽만 springdoc 이 뒤붙임으로 가른다
        assertThat(operationId("/api/v1/clips", "get")).isEqualTo("list_transitional");
        assertThat(operationId("/api/v1/clips/{exerciseId}/favorite", "post")).isEqualTo("favorite_transitional");
    }

    @Test
    @DisplayName("로그인 계정 인자(CurrentUser)는 어느 주소에도 user 쿼리 파라미터로 나가지 않는다")
    void 어느_주소에도_user_쿼리_파라미터가_없다() {
        List<String> withUser = new ArrayList<>();
        int operations = 0;
        for (var path : docs.path("paths").properties()) {
            for (var operation : path.getValue().properties()) {
                operations++;
                for (JsonNode parameter : operation.getValue().path("parameters")) {
                    if ("user".equals(parameter.path("name").asString())) {
                        withUser.add(operation.getKey() + " " + path.getKey());
                    }
                }
            }
        }

        assertThat(operations).isGreaterThan(40);
        assertThat(withUser).isEmpty();
    }
}
