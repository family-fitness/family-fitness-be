package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.CompleteSessionCommand;
import kr.ac.kookmin.familyfitness.coaching.application.MissionActivityService;
import kr.ac.kookmin.familyfitness.coaching.application.MissionService;
import kr.ac.kookmin.familyfitness.coaching.application.SessionCompletedView;
import kr.ac.kookmin.familyfitness.coaching.application.SessionCompletionService;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 칸 끝 컨트롤러가 무결성 오류를 가르는지 본다. 같은 칸 동시 요청(기본 키 위반, SQLSTATE 23505)만 한 번 다시 부르고,
 * FK 같은 다른 위반은 다시 부르지 않고 그대로 던진다(ApiErrorHandler 가 500 으로 남긴다).
 */
class SessionCompleteRetryTest {
    private static final Instant STARTED = Instant.parse("2026-09-29T01:00:00Z");
    private static final CompleteSessionRequest BODY =
            new CompleteSessionRequest(UUID.randomUUID(), 120, STARTED, STARTED.plusSeconds(120));

    private final SessionCompletionService sessions = mock(SessionCompletionService.class);
    private final MissionController controller =
            new MissionController(mock(MissionService.class), mock(MissionActivityService.class), sessions);
    private final CurrentUser user = new CurrentUser(UUID.randomUUID());
    private final UUID missionId = UUID.randomUUID();

    @Test
    @DisplayName("기본 키 위반(23505)이면 한 번 다시 불러 그 답(이미 끝낸 칸)을 돌려준다")
    void 기본_키_위반이면_한_번_다시_부른다() {
        SessionCompletedView already = new SessionCompletedView(1, VerifiedBy.VIDEO_PROGRESS, 0.5, false, 0);
        when(sessions.complete(any(UUID.class), any(UUID.class), anyInt(), any(CompleteSessionCommand.class)))
                .thenThrow(violation("23505"))
                .thenReturn(already);

        SessionCompletedView answer = controller.completeSession(user, missionId, 1, BODY);

        assertThat(answer).isEqualTo(already);
        verify(sessions, times(2))
                .complete(any(UUID.class), any(UUID.class), anyInt(), any(CompleteSessionCommand.class));
    }

    @Test
    @DisplayName("FK 위반(23503)은 다시 부르지 않고 그대로 던진다")
    void FK_위반은_다시_부르지_않고_던진다() {
        DataIntegrityViolationException fk = violation("23503");
        when(sessions.complete(any(UUID.class), any(UUID.class), anyInt(), any(CompleteSessionCommand.class)))
                .thenThrow(fk);

        assertThatThrownBy(() -> controller.completeSession(user, missionId, 1, BODY))
                .isSameAs(fk);
        verify(sessions, times(1))
                .complete(any(UUID.class), any(UUID.class), anyInt(), any(CompleteSessionCommand.class));
    }

    private static DataIntegrityViolationException violation(String sqlState) {
        return new DataIntegrityViolationException("무결성 위반", new SQLException("무결성 위반", sqlState));
    }
}
