package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.application.port.NormRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.NormAgeUnit;
import kr.ac.kookmin.familyfitness.fitness.domain.NormPoint;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class NormRepositoryAdapter implements NormRepository {
    private final FitnessNormJpaRepository jpa;

    public NormRepositoryAdapter(FitnessNormJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<NormPoint> findAll() {
        return jpa.findAll().stream()
                .map(it -> new NormPoint(
                        it.getItemCode(),
                        Sex.valueOf(it.getSex()),
                        it.getAgeFrom(),
                        it.getAgeTo(),
                        it.getPercentile(),
                        it.getNormValue().doubleValue(),
                        it.getSourceYear(),
                        NormAgeUnit.of(it.getAgeUnit())))
                .toList();
    }
}
