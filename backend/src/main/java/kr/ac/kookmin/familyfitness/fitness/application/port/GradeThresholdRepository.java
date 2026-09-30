package kr.ac.kookmin.familyfitness.fitness.application.port;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.domain.GradeThreshold;

public interface GradeThresholdRepository {
    List<GradeThreshold> findAll();
}
