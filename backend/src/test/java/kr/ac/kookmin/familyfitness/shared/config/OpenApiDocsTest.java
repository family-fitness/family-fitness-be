package kr.ac.kookmin.familyfitness.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
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
    @DisplayName("요청 검증용 getter(isContentPresent)는 CheerRequest 칸으로 나가지 않는다")
    void CheerRequest_에_contentPresent_가_없다() {
        JsonNode properties =
                docs.path("components").path("schemas").path("CheerRequest").path("properties");

        assertThat(properties.has("fromProfileId")).isTrue();
        assertThat(properties.has("contentPresent")).isFalse();
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
