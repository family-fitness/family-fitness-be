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
        assertThat(typesOf(property("ProfileSummary", "isOwner"))).containsExactly("boolean");
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
    @DisplayName("편성 요청의 키와 몸무게는 null 을 싣는 number 이고, 범위는 측정 등록과 같다(키 30~230, 몸무게 5~250)")
    void 편성_요청의_키와_몸무게는_측정_등록과_같은_범위다() {
        for (String schema : List.of("StartCoachRunRequest", "RegisterFitnessTestRequest")) {
            JsonNode height = property(schema, "heightCm");
            JsonNode weight = property(schema, "weightKg");
            assertThat(typesOf(height)).as(schema + ".heightCm").containsExactlyInAnyOrder("number", "null");
            assertThat(typesOf(weight)).as(schema + ".weightKg").containsExactlyInAnyOrder("number", "null");
            assertThat(height.path("minimum").asDouble())
                    .as(schema + ".heightCm 최솟값")
                    .isEqualTo(30.0);
            assertThat(height.path("maximum").asDouble())
                    .as(schema + ".heightCm 최댓값")
                    .isEqualTo(230.0);
            assertThat(weight.path("minimum").asDouble())
                    .as(schema + ".weightKg 최솟값")
                    .isEqualTo(5.0);
            assertThat(weight.path("maximum").asDouble())
                    .as(schema + ".weightKg 최댓값")
                    .isEqualTo(250.0);
        }
        assertThat(property("StartCoachRunRequest", "heightCm")
                        .path("description")
                        .asString())
                .contains("측정 기록이 없는 대상만");
    }

    @Test
    @DisplayName("인증 등급은 회차에 하나다 — 항목 줄에 grade 가 없고, latest 의 certification 은 null 을 싣는 참조다")
    void 인증_등급은_회차에_하나다() {
        JsonNode itemProperties =
                docs.path("components").path("schemas").path("ItemResult").path("properties");
        assertThat(itemProperties.has("percentile")).isTrue();
        assertThat(itemProperties.has("grade")).isFalse();

        JsonNode latest = property("LatestFitnessResponse", "certification");
        List<JsonNode> choices = new ArrayList<>();
        latest.path("oneOf").forEach(choices::add);
        assertThat(choices).hasSize(2);
        assertThat(choices.get(0).path("$ref").asString()).isEqualTo("#/components/schemas/Certification");
        assertThat(typesOf(choices.get(1))).containsExactly("null");
        // 등록 응답은 보호자만 받아 늘 있다
        assertThat(property("FitnessTestResponse", "certification").path("$ref").asString())
                .isEqualTo("#/components/schemas/Certification");
        assertThat(typesOf(property("Certification", "grade"))).containsExactlyInAnyOrder("string", "null");
        assertThat(typesOf(property("Certification", "status"))).containsExactly("string");
        // FE schema.ts 가 쓰는 이름 그대로 — Certification · MissingItem · PeerGrade
        assertThat(property("Certification", "missingItems")
                        .path("items")
                        .path("$ref")
                        .asString())
                .isEqualTo("#/components/schemas/MissingItem");
        assertThat(property("Certification", "peers").path("items").path("$ref").asString())
                .isEqualTo("#/components/schemas/PeerGrade");
        assertThat(typesOf(property("MissingItem", "itemCodes"))).containsExactly("array");
        assertThat(typesOf(property("MissingItem", "label"))).containsExactly("string");
        assertThat(typesOf(property("PeerGrade", "grade"))).containsExactly("string");
        assertThat(typesOf(property("PeerGrade", "ratio"))).containsExactly("number");
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
    @DisplayName("탈퇴와 구성원 내보내기는 본문 없는 204 로 실린다")
    void 탈퇴와_구성원_내보내기는_204_다() {
        JsonNode withdraw = docs.path("paths").path("/api/v1/me").path("delete");
        JsonNode remove = docs.path("paths")
                .path("/api/v1/families/{familyId}/profiles/{profileId}")
                .path("delete");

        assertThat(withdraw.path("operationId").asString()).isEqualTo("withdraw");
        assertThat(withdraw.path("responses").has("204")).isTrue();
        assertThat(remove.path("operationId").asString()).isEqualTo("removeMember");
        assertThat(remove.path("responses").has("204")).isTrue();
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
