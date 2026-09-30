package kr.ac.kookmin.familyfitness.fitness.adapter.outbound.persistence;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.application.port.PeerQuantileRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.PeerQuantiles;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class PeerQuantileRepositoryAdapter implements PeerQuantileRepository {
    private final FitnessValueQuantileJpaRepository jpa;

    public PeerQuantileRepositoryAdapter(FitnessValueQuantileJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** 나이 단위(age_unit)는 연령대로 정해져 있어(유아기만 개월) 도메인에 따로 싣지 않는다. 어긋나면 적재를 멈춘다. */
    @Override
    public List<PeerQuantiles> findAll() {
        return jpa.findAll().stream()
                .map(it -> {
                    AgeGroup ageGroup = AgeGroup.fromLabel(it.getAgeGroup());
                    if (!ageGroup.getAgeUnit().equals(it.getAgeUnit())) {
                        throw new IllegalStateException(
                                "또래 분포 나이 단위가 연령대와 맞지 않는다: " + it.getAgeGroup() + " " + it.getAgeUnit());
                    }
                    return new PeerQuantiles(
                            ageGroup,
                            Sex.valueOf(it.getSex()),
                            it.getAge(),
                            it.getItemCode(),
                            it.getN(),
                            PeerQuantiles.parse(it.getQuantiles()));
                })
                .toList();
    }
}
