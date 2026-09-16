package kr.ac.kookmin.familyfitness.fitness.application;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.application.port.NormRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.NormPoint;

class StaticNormRepository implements NormRepository {
    private final List<NormPoint> points;

    StaticNormRepository(List<NormPoint> points) {
        this.points = points;
    }

    @Override
    public List<NormPoint> findAll() {
        return points;
    }
}
