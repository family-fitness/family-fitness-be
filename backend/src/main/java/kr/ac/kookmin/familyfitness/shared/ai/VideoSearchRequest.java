package kr.ac.kookmin.familyfitness.shared.ai;

import java.util.List;

public record VideoSearchRequest(String ageGroup, List<String> fitnessFactors, List<String> exerciseNames, int k) {}
