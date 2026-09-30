package kr.ac.kookmin.familyfitness.fitness.application.port;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.domain.NormPoint;

public interface NormRepository {
    List<NormPoint> findAll();
}
