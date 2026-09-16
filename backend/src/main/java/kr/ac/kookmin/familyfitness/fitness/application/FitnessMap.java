package kr.ac.kookmin.familyfitness.fitness.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FitnessMap(UUID familyId, @Nullable String familyName, List<FitnessMapMember> members) {}
