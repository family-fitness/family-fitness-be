package kr.ac.kookmin.familyfitness.identity.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

/**
 * 가족 초대코드로 들어온 사람이 넣는 자기 정보({@link Family#join}). 이름, 생년월일, 성별은 꼭 있어야 하고 키와 몸무게는 없어도
 * 된다. 범위는 구성원 추가와 같고 요청 검증이 본다.
 */
public record NewMember(
        String name,
        LocalDate birthDate,
        Sex sex,
        @Nullable BigDecimal heightCm,
        @Nullable BigDecimal weightKg) {
    public NewMember {
        if (name.isBlank()) throw new IllegalArgumentException("이름은 비어 있을 수 없다");
    }
}
