package kr.ac.kookmin.familyfitness.fitness.application.port;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.domain.PeerQuantiles;

public interface PeerQuantileRepository {
    List<PeerQuantiles> findAll();
}
