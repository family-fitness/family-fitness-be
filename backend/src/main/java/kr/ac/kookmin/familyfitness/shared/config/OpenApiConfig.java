package kr.ac.kookmin.familyfitness.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    public static final String BEARER = "bearerAuth";

    /**
     * 전환기 별칭 → 같은 핸들러의 계약 경로. 지금 FE(fe:src/lib/api/queries.ts)가 계약과 다른 이름을 불러서, 계약 이름을 그대로
     * 두고 FE 이름도 같은 핸들러로 받는다(docs/api-contract.md 「전환기 별칭」). FE 가 계약 이름으로 옮기면 매핑과 함께 여기서 걷는다.
     */
    private static final Map<String, String> ALIAS_TO_CONTRACT = Map.of(
            "/api/v1/missions/{missionId}/sessions/{seq}/done", "/api/v1/missions/{missionId}/sessions/{seq}/complete",
            "/api/v1/families/{familyId}/rest-days", "/api/v1/families/{familyId}/rest-cards",
            "/api/v1/families/{familyId}/rest-days/{restDate}", "/api/v1/families/{familyId}/rest-cards/{restDate}",
            "/api/v1/clips", "/api/v1/exercises",
            "/api/v1/clips/{exerciseId}/favorite", "/api/v1/exercises/{exerciseId}/favorite");

    /** 문서에 deprecated 로 싣는 전환기 별칭 경로. */
    public static final Set<String> TRANSITIONAL_ALIASES = ALIAS_TO_CONTRACT.keySet();

    /** springdoc 이 겹친 operationId 뒤에 붙이는 「_1」 · 「_2」. */
    private static final Pattern OPERATION_ID_SUFFIX = Pattern.compile("_(\\d+)$");

    static {
        // 로그인 계정 인자는 JWT 에서 채운다(CurrentUserArgumentResolver). 요청 값이 아니라 문서에 user 쿼리 파라미터로 싣지 않는다.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    /** jspecify @Nullable 칸을 스키마의 null 로 싣는다. springdoc 이 ModelConverter 빈을 변환기 목록 앞에 넣는다. */
    @Bean
    public NullableModelConverter nullableModelConverter() {
        return new NullableModelConverter();
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("우리가족 체력키움 API")
                        .version("v1")
                        .description("성공은 payload 그대로, 실패는 `{\"error\": {\"code\", \"message\"}}` 한 형태다. "
                                + "`error.message` 는 개발자용이며 화면에 그대로 노출하지 않는다. "
                                + "인증은 `Authorization: Bearer <accessToken>`."))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /**
     * 전환기 별칭의 연산을 deprecated 로 표시하고, 설명에 옮겨 갈 계약 경로를 적는다. 한 핸들러가 두 경로라 둘 다 같은
     * operationId(메서드 이름)로 나오는데, 그대로 두면 springdoc 의 OperationIdCustomizer 가 경로 차례대로 「_1」 을 붙여 계약
     * 경로가 「completeSession_1」 이 되기도 한다. 별칭에 「계약 이름_transitional」 을 주어 계약 경로의 operationId 가 별칭
     * 때문에 바뀌지 않게 한다 — FE 가 만든 타입(openapi-typescript)의 연산 이름이 그대로다.
     */
    @Bean
    public OpenApiCustomizer transitionalAliasCustomizer() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths == null) return;
            ALIAS_TO_CONTRACT.forEach((alias, contract) -> {
                PathItem aliasItem = paths.get(alias);
                if (aliasItem == null) return;
                PathItem contractItem = paths.get(contract);
                String note = "전환기 별칭 — `" + contract + "` 와 같은 핸들러다. FE 가 계약 이름으로 옮기면 걷는다.";
                aliasItem.readOperationsMap().forEach((method, operation) -> {
                    operation.setDeprecated(true);
                    operation.setDescription(note);
                    @Nullable
                    Operation contractOperation = contractItem == null
                            ? null
                            : contractItem.readOperationsMap().get(method);
                    if (contractOperation != null) renameAliasOperation(contractOperation, operation);
                });
            });
        };
    }

    /**
     * 별칭 연산의 operationId 를 「계약 operationId_transitional」 로. 보통은 OperationIdCustomizer 보다 먼저 돌아(사용자 설정 빈이
     * 자동 설정 빈보다 앞) 두 이름이 같을 때 바꾼다. 이미 뒤붙임이 달려 있으면 작은 쪽을 계약 경로에 돌려준 뒤 바꾼다.
     */
    private static void renameAliasOperation(Operation contract, Operation alias) {
        String contractId = contract.getOperationId();
        String aliasId = alias.getOperationId();
        if (contractId == null || aliasId == null) return;
        if (suffixOf(aliasId) < suffixOf(contractId)) {
            contract.setOperationId(aliasId);
            contractId = aliasId;
        }
        alias.setOperationId(contractId + "_transitional");
    }

    private static int suffixOf(String operationId) {
        Matcher suffix = OPERATION_ID_SUFFIX.matcher(operationId);
        return suffix.find() ? Integer.parseInt(suffix.group(1)) : 0;
    }
}
