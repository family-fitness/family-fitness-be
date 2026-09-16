package kr.ac.kookmin.familyfitness.fitness.application;

import java.util.ArrayList;
import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.application.port.PredictionRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.Prediction;

class InMemoryPredictionRepository implements PredictionRepository {
    final List<Prediction> saved = new ArrayList<>();

    @Override
    public Prediction save(Prediction prediction) {
        saved.add(prediction);
        return prediction;
    }
}
