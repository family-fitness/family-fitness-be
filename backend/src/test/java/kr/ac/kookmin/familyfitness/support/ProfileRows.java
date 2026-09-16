package kr.ac.kookmin.familyfitness.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * identity 를 거치지 않고 `families`·`profiles` 행만 꽂는다. fitness·activity 테이블이 profiles 를 FK 로 참조하므로
 * identity 를 목으로 바꾼 모듈 테스트에서도 실제 행이 있어야 한다.
 */
@Component
public class ProfileRows {
    private final JdbcTemplate jdbc;

    public ProfileRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID family() {
        return family("테스트 가족");
    }

    public UUID family(String name) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        jdbc.update("insert into families (id, name, created_at, updated_at) values (?, ?, ?, ?)", id, name, now, now);
        return id;
    }

    public UUID profile(UUID familyId, LocalDate birthDate) {
        return profile(familyId, birthDate, Sex.F, ProfileRole.CHILD, "아이");
    }

    public UUID profile(UUID familyId, LocalDate birthDate, Sex sex) {
        return profile(familyId, birthDate, sex, ProfileRole.CHILD, "아이");
    }

    public UUID profile(UUID familyId, LocalDate birthDate, Sex sex, ProfileRole role) {
        return profile(familyId, birthDate, sex, role, "아이");
    }

    public UUID profile(UUID familyId, LocalDate birthDate, Sex sex, ProfileRole role, String name) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        jdbc.update("""
                insert into profiles (id, family_id, display_name, birth_date, sex, role, is_owner, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)\
                """, id, familyId, name, birthDate, sex.name(), role.name(), role == ProfileRole.PARENT, now, now);
        return id;
    }
}
