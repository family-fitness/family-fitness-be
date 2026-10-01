package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.identity.domain.NewMember;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

/**
 * 초대코드 쓰기. 자리 초대코드는 claimCode 만 보낸다. 가족 초대코드는 들어올 사람의 이름, 생년월일, 성별을 함께 보내고 키와
 * 몸무게는 골라 보낸다. 칸마다 범위는 구성원 추가(AddMemberRequest)와 같다. 자리 초대코드에 함께 온 정보는 쓰지 않는다.
 */
public record ClaimRequest(
        @NotBlank @Size(max = 20) String claimCode,
        @Size(max = 20) @Nullable String name,
        @PastOrPresent @Nullable LocalDate birthDate,
        @Nullable Sex sex,
        @DecimalMin("30") @DecimalMax("230") @Nullable BigDecimal heightCm,
        @DecimalMin("5") @DecimalMax("250") @Nullable BigDecimal weightKg) {
    /** 이름, 생년월일, 성별이 다 있으면 들어올 사람의 정보, 하나라도 없거나 이름이 비었으면 null(가족 초대코드면 400 이 된다). */
    public @Nullable NewMember newMember() {
        if (name == null || name.isBlank() || birthDate == null || sex == null) return null;
        return new NewMember(name.strip(), birthDate, sex, heightCm, weightKg);
    }
}
