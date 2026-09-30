package kr.ac.kookmin.familyfitness.fitness.application;

import jakarta.annotation.PostConstruct;
import kr.ac.kookmin.familyfitness.fitness.application.port.PeerQuantileRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.PeerTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 부팅 시 `fitness_value_quantiles`(AI `value_quantiles.csv` 와 같은 또래 분포 표)를 {@link PeerTable} 로 메모리에 올린다.
 * 표를 다시 적재하면 {@link #refresh} 로 통째로 바꾼다. 백분위는 저장 시점 값으로 굳으므로 표를 바꿔도 지난 회차는 그대로다.
 */
@Component
public class PeerCatalog {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final PeerQuantileRepository repository;

    private volatile PeerTable current = PeerTable.EMPTY;

    public PeerCatalog(PeerQuantileRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void refresh() {
        current = PeerTable.of(repository.findAll());
        log.info("또래 분포 표 적재: {}칸", current.getSize());
    }

    public PeerTable table() {
        return current;
    }
}
