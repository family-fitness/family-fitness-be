package kr.ac.kookmin.familyfitness.fitness.application;

import jakarta.annotation.PostConstruct;
import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.application.port.NormRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.NormPoint;
import kr.ac.kookmin.familyfitness.fitness.domain.NormTable;
import kr.ac.kookmin.familyfitness.fitness.domain.PercentileCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 `fitness_norms` 를 {@link NormTable} 로 메모리에 올린다. 규준을 다시 적재하면 {@link #refresh} 로 통째로 바꾼다.
 * 백분위는 저장 시점 값으로 굳으므로 표를 바꿔도 과거 기록은 그대로다.
 */
@Component
public class NormCatalog {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final NormRepository normRepository;

    private volatile NormTable current = NormTable.EMPTY;

    public NormCatalog(NormRepository normRepository) {
        this.normRepository = normRepository;
    }

    @PostConstruct
    public void refresh() {
        List<NormPoint> points = normRepository.findAll();
        current = NormTable.of(points);
        log.info("체력 규준 적재: {}행 → {}구간", points.size(), current.getSize());
    }

    public NormTable table() {
        return current;
    }

    public PercentileCalculator calculator() {
        return new PercentileCalculator(current);
    }
}
