package kr.ac.kookmin.familyfitness.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.FamilyInvite;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 자리 초대코드와 가족 초대코드는 같은 입력칸으로 들어오므로, 새 코드는 두 표 어디에도 없는 것이어야 한다. */
class InviteCodesTest {
    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);

    private final InMemoryFamilyRepository families = new InMemoryFamilyRepository();
    private final InMemoryFamilyInviteRepository familyInvites = new InMemoryFamilyInviteRepository();
    private final UUID momUser = UUID.randomUUID();

    @Test
    @DisplayName("자리 초대코드나 가족 초대코드로 이미 쓰는 코드는 건너뛰고 다음 코드를 준다")
    void 두_표에서_쓰는_코드는_건너뛴다() {
        Family family = Family.createWithParent(momUser, "우리 가족", "엄마", LocalDate.of(1988, 3, 1), Sex.F, TODAY);
        UUID dad = family.addMember(
                        momUser,
                        "아빠",
                        LocalDate.of(1986, 1, 1),
                        Sex.M,
                        ProfileRole.PARENT,
                        null,
                        null,
                        null,
                        NOW,
                        TODAY)
                .getId();
        family.issueInvite(momUser, dad, NOW, () -> code("AAAAAA"));
        families.save(family);
        familyInvites.add(family.issueFamilyInvite(
                momUser, ProfileRole.CHILD, new GuardianConsent(true, true), NOW, TODAY, () -> code("BBBBBB")));

        InviteCodes codes = new InviteCodes(families, familyInvites, sequence("AAAAAA", "BBBBBB", "CCCCCC"));

        assertThat(codes.fresh(NOW).code()).isEqualTo("CCCCCC");
    }

    @Test
    @DisplayName("열 번 뽑아도 모두 쓰는 코드면 서버 오류다")
    void 열_번_모두_겹치면_서버_오류다() {
        familyInvites.add(new FamilyInvite(
                code("AAAAAA"), UUID.randomUUID(), ProfileRole.PARENT, null, null, UUID.randomUUID(), NOW, null, null));

        InviteCodes codes = new InviteCodes(families, familyInvites, now -> code("AAAAAA"));

        assertThatThrownBy(() -> codes.fresh(NOW)).isInstanceOf(IllegalStateException.class);
    }

    private static ClaimCode code(String value) {
        return new ClaimCode(value, NOW.plus(ClaimCode.TTL));
    }

    private static java.util.function.Function<Instant, ClaimCode> sequence(String... values) {
        Iterator<String> next = List.of(values).iterator();
        return now -> code(next.next());
    }
}
