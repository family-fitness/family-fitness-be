package kr.ac.kookmin.familyfitness.fitness.application;

import jakarta.annotation.PostConstruct;
import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.application.port.GradeThresholdRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.GradeTable;
import kr.ac.kookmin.familyfitness.fitness.domain.GradeThreshold;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 `fitness_grade_thresholds`(국민체력100 공식 등급 기준표, AI `grade_thresholds.csv` 와 같은 표)를 {@link GradeTable}
 * 로 메모리에 올린다. 기준표를 다시 적재하면 {@link #refresh} 로 통째로 바꾼다. 등급은 저장 시점 값으로 굳으므로 표를 바꿔도
 * 과거 기록은 그대로다.
 */
@Component
public class GradeCatalog {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final GradeThresholdRepository repository;

    private volatile GradeTable current = GradeTable.EMPTY;

    public GradeCatalog(GradeThresholdRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void refresh() {
        List<GradeThreshold> rows = repository.findAll();
        current = GradeTable.of(rows);
        log.info("체력 등급 기준표 적재: {}행", current.getSize());
    }

    public GradeTable table() {
        return current;
    }
}
