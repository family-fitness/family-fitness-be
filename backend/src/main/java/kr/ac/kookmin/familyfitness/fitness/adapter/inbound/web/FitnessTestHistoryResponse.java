package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.application.FitnessHistoryView;

// ---- GET /profiles/{profileId}/fitness-tests?size= ----
/** 측정 이력. 최근 회차(testedOn 이 늦은 것)가 먼저 온다. 이력이 없으면 빈 목록(200). */
public record FitnessTestHistoryResponse(List<FitnessTestSummaryResponse> tests) {
    static FitnessTestHistoryResponse of(FitnessHistoryView view) {
        return new FitnessTestHistoryResponse(view.tests().stream()
                .map(it -> FitnessTestSummaryResponse.of(it, view.parentScope()))
                .toList());
    }
}
