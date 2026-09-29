package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.application.port.GradeDistributionRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.Grade;
import kr.ac.kookmin.familyfitness.fitness.domain.GradeDistribution;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class GradeDistributionRepositoryAdapter implements GradeDistributionRepository {
    private final FitnessGradeDistributionJpaRepository jpa;

    public GradeDistributionRepositoryAdapter(FitnessGradeDistributionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<GradeDistribution.Row> findAll() {
        return jpa.findAll().stream()
                .map(it -> new GradeDistribution.Row(
                        AgeGroup.fromLabel(it.getAgeGroup()),
                        Sex.valueOf(it.getSex()),
                        it.getAge(),
                        Grade.fromLabel(it.getGrade()),
                        it.getRatio()))
                .toList();
    }
}
