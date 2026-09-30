package kr.ac.kookmin.familyfitness.fitness.application.port;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.domain.GradeDistribution;

public interface GradeDistributionRepository {
    List<GradeDistribution.Row> findAll();
}
