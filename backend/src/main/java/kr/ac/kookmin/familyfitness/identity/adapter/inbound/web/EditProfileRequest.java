package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.identity.domain.ProfileEdit;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

/**
 * 프로필 고치기. 보낸 칸만 바꾸고 빠진 칸(null)은 그대로 둔다.
 * 이름 길이 · 생년월일 범위는 구성원 추가({@link AddMemberRequest})와 같다 — 이름 1~20자(공백만은 안 됨), 생년월일은 오늘까지.
 */
public record EditProfileRequest(
        @Size(min = 1, max = 20) @Pattern(regexp = "(?s).*\\S.*", message = "공백만으로 된 이름은 안 됩니다") @Nullable
        String name,

        @PastOrPresent @Nullable LocalDate birthDate,
        @Nullable Sex sex) {
    public ProfileEdit toDomain() {
        return new ProfileEdit(name, birthDate, sex);
    }
}
