package kr.ac.kookmin.familyfitness.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 흐름 1: 소셜 인증을 마친 사용자의 최초 가입 또는 기존 계정 조회.
 * provider와 providerUserId는 서버가 검증한 인증 결과에서 가져온다.
 * Google은 테스트 예시이며 지원 제공자를 확정하는 테스트는 아니다.
 * 가족 생성과 PARENT 프로필 등록은 다음 사용자 흐름에서 다룬다.
 */
class UserRegistrationServiceTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final UserRegistrationService service = new UserRegistrationService(repository);

    @Test
    @DisplayName("부모가 처음 소셜 로그인하면 새 계정을 저장한다")
    void 부모가_처음_소셜_로그인하면_새_계정을_저장한다() {
        when(repository.findByProviderAndProviderUserId("GOOGLE", "google-parent-1"))
                .thenReturn(null);

        User user = service.registerOrGet("GOOGLE", "google-parent-1", "parent@example.com");

        assertThat(savedUsers()).hasSize(1);
        User saved = savedUsers().getFirst();
        assertThat(saved.id()).isEqualTo(user.id());
        assertThat(user.id()).isNotNull();
        assertThat(saved.provider()).isEqualTo("GOOGLE");
        assertThat(saved.providerUserId()).isEqualTo("google-parent-1");
        assertThat(saved.email()).isEqualTo("parent@example.com");
    }

    @Test
    @DisplayName("같은 소셜 계정으로 다시 로그인하면 새로 가입시키지 않는다")
    void 같은_소셜_계정으로_다시_로그인하면_새로_가입시키지_않는다() {
        User existing = new User(UUID.randomUUID(), "GOOGLE", "google-parent-1", "parent@example.com");
        when(repository.findByProviderAndProviderUserId("GOOGLE", "google-parent-1"))
                .thenReturn(existing);

        User user = service.registerOrGet("GOOGLE", "google-parent-1", "parent@example.com");

        assertThat(user.id()).isEqualTo(existing.id());
        assertThat(savedUsers()).isEmpty();
    }

    @Test
    @DisplayName("이메일이 같아도 서로 다른 소셜 계정은 별도 계정으로 가입한다")
    void 이메일이_같아도_서로_다른_소셜_계정은_별도_계정으로_가입한다() {
        when(repository.findByProviderAndProviderUserId("GOOGLE", "google-parent-1"))
                .thenReturn(null);
        when(repository.findByProviderAndProviderUserId("GOOGLE", "google-parent-2"))
                .thenReturn(null);

        User first = service.registerOrGet("GOOGLE", "google-parent-1", "shared@example.com");
        User second = service.registerOrGet("GOOGLE", "google-parent-2", "shared@example.com");

        assertThat(first.id()).isNotEqualTo(second.id());
        List<User> saved = savedUsers();
        assertThat(saved).hasSize(2);
        assertThat(saved.stream().map(User::providerUserId).toList())
                .containsExactly("google-parent-1", "google-parent-2");
        assertThat(saved.stream().map(User::email).toList()).containsOnly("shared@example.com");
    }

    private List<User> savedUsers() {
        return mockingDetails(repository).getInvocations().stream()
                .filter(it -> it.getMethod().getName().equals("save"))
                .map(it -> (User) it.getArgument(0))
                .toList();
    }
}
