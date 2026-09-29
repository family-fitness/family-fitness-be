package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.application.port.GradeThresholdRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.Grade;
import kr.ac.kookmin.familyfitness.fitness.domain.GradeThreshold;
import kr.ac.kookmin.familyfitness.fitness.domain.NormAgeUnit;
import kr.ac.kookmin.familyfitness.fitness.domain.ThresholdOp;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class GradeThresholdRepositoryAdapter implements GradeThresholdRepository {
    private final FitnessGradeThresholdJpaRepository jpa;

    public GradeThresholdRepositoryAdapter(FitnessGradeThresholdJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<GradeThreshold> findAll() {
        return jpa.findAll().stream()
                .map(it -> new GradeThreshold(
                        AgeGroup.fromLabel(it.getAgeGroup()),
                        Sex.valueOf(it.getSex()),
                        NormAgeUnit.of(it.getAgeUnit()),
                        it.getAgeFrom(),
                        it.getAgeTo(),
                        Grade.fromLabel(it.getGrade()),
                        it.getItemCode(),
                        ThresholdOp.of(it.getOp()),
                        it.getCutoff(),
                        it.getCutoffUpper()))
                .toList();
    }
}
