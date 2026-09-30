package kr.ac.kookmin.familyfitness.fitness.application;

import jakarta.annotation.PostConstruct;
import kr.ac.kookmin.familyfitness.fitness.application.port.GradeDistributionRepository;
import kr.ac.kookmin.familyfitness.fitness.application.port.GradeThresholdRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.Certifier;
import kr.ac.kookmin.familyfitness.fitness.domain.GradeDistribution;
import kr.ac.kookmin.familyfitness.fitness.domain.GradeTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 `fitness_grade_thresholds`(국민체력100 공식 등급 기준표, AI `grade_thresholds.csv` 와 같은 표)와
 * `fitness_grade_distribution`(또래 등급 비율, AI `grade_distribution.csv`)을 {@link Certifier} 로 메모리에 올린다.
 * 표를 다시 적재하면 {@link #refresh} 로 통째로 바꾼다. 등급은 저장하지 않고 읽을 때 셈하므로 표를 바꾸면 지난 회차의
 * 등급도 새 표를 따른다.
 */
@Component
public class GradeCatalog {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final GradeThresholdRepository thresholds;
    private final GradeDistributionRepository distribution;

    private volatile Certifier current = Certifier.EMPTY;

    public GradeCatalog(GradeThresholdRepository thresholds, GradeDistributionRepository distribution) {
        this.thresholds = thresholds;
        this.distribution = distribution;
    }

    @PostConstruct
    public void refresh() {
        current = new Certifier(GradeTable.of(thresholds.findAll()), GradeDistribution.of(distribution.findAll()));
        log.info(
                "체력 등급 기준표 적재: 기준 {}행 · 등급 비율 {}행",
                current.getThresholds().getSize(),
                current.getDistribution().getSize());
    }

    public Certifier certifier() {
        return current;
    }
}
