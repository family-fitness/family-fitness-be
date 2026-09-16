package kr.ac.kookmin.familyfitness.fitness.application.port;

import kr.ac.kookmin.familyfitness.fitness.domain.Prediction;

public interface PredictionRepository {
    Prediction save(Prediction prediction);
}
