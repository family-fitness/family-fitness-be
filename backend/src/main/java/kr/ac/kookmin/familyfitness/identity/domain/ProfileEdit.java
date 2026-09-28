package kr.ac.kookmin.familyfitness.identity.domain;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

/** 프로필 고치기에 온 값. null 인 칸은 그대로 둔다. */
public record ProfileEdit(
        @Nullable String name,
        @Nullable LocalDate birthDate,
        @Nullable Sex sex) {}
