package kr.ac.kookmin.familyfitness.fitness.application;

import kr.ac.kookmin.familyfitness.fitness.domain.Certification;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;

/** 방금 저장한 회차와 그 회차의 인증 등급. 등록은 보호자만 해서 등급은 늘 있다. */
public record RegisteredFitnessTest(FitnessTest test, Certification certification) {}
