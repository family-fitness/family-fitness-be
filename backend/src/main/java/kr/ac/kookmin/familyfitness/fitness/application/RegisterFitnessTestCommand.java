package kr.ac.kookmin.familyfitness.fitness.application;

import java.time.LocalDate;
import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.domain.BodyMeasures;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTestSource;
import kr.ac.kookmin.familyfitness.fitness.domain.Measurement;

public record RegisterFitnessTestCommand(
        LocalDate testedOn, FitnessTestSource source, BodyMeasures body, List<Measurement> measurements) {}
