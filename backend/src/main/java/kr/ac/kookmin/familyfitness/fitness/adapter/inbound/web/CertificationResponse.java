package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.domain.Certification;
import kr.ac.kookmin.familyfitness.fitness.domain.CertificationStatus;
import kr.ac.kookmin.familyfitness.fitness.domain.Grade;
import org.jspecify.annotations.Nullable;

/**
 * 회차의 국민체력100 인증 등급(한 사람에게 하나). 보호자만 본다.
 * {@code grade} 는 `1등급` · `2등급` · `3등급` · `참가` 또는 null(판정하지 못함 · 기준 없음).
 * {@code missingItems} 는 더 재면 판정할 수 있는 것, {@code peers} 는 같은 나이 · 성별 참가자의 등급별 비율이다.
 * OpenAPI 스키마 이름은 FE `schema.ts` 가 쓰는 `Certification` · `MissingItem` · `PeerGrade` 로 맞춘다.
 */
@Schema(name = "Certification")
public record CertificationResponse(
        @Nullable Grade grade,
        CertificationStatus status,
        List<MissingItemResponse> missingItems,
        List<PeerGradeResponse> peers) {
    /** 사람이 한 번에 재는 것 하나. 035 · 037 은 한 칸에 둘이 들어간다. */
    @Schema(name = "MissingItem")
    public record MissingItemResponse(List<String> itemCodes, String label) {}

    @Schema(name = "PeerGrade")
    public record PeerGradeResponse(Grade grade, BigDecimal ratio) {}

    static CertificationResponse of(Certification certification) {
        return new CertificationResponse(
                certification.grade(),
                certification.status(),
                certification.missingItems().stream()
                        .map(it -> new MissingItemResponse(it.itemCodes(), it.label()))
                        .toList(),
                certification.peers().stream()
                        .map(it -> new PeerGradeResponse(it.grade(), it.ratio()))
                        .toList());
    }
}
