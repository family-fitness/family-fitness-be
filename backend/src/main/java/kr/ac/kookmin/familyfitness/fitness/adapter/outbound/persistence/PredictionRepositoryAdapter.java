package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import kr.ac.kookmin.familyfitness.fitness.application.port.PredictionRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.Prediction;
import kr.ac.kookmin.familyfitness.fitness.domain.PredictionPoint;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PredictionRepositoryAdapter implements PredictionRepository {
    private final PredictionJpaRepository jpa;

    public PredictionRepositoryAdapter(PredictionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Prediction save(Prediction prediction) {
        jpa.save(toEntity(prediction));
        return prediction;
    }

    private static PredictionEntity toEntity(Prediction prediction) {
        return new PredictionEntity(
                prediction.getId(),
                prediction.getProfileId(),
                prediction.getFitnessTestId(),
                prediction.getItemCode(),
                prediction.getHorizonYears(),
                prediction.getModelVersion(),
                prediction.getBasis(),
                prediction.getNotice(),
                prediction.getCreatedAt(),
                prediction.getPoints().stream()
                        .map(PredictionRepositoryAdapter::toEmbeddable)
                        .toList());
    }

    private static PredictionPointEmbeddable toEmbeddable(PredictionPoint point) {
        return new PredictionPointEmbeddable(
                point.scenario().name(), point.itemCode(), point.yearsFromNow(), point.p10(), point.p50(), point.p90());
    }
}
