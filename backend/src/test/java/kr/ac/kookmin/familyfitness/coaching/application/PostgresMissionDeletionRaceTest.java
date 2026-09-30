package kr.ac.kookmin.familyfitness.coaching.application;

import kr.ac.kookmin.familyfitness.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 운영과 같은 PostgreSQL(Testcontainers, READ COMMITTED)에서 돈다. Docker 가 없는 환경에서는 건너뛴다(CI 에서는 돈다).
 *
 * <p>칸 끝이 미션 행을 FOR UPDATE 로 잠그기 전에는 칸 끝과 지우기가 칸 끝 행 INSERT 의 외래 키 잠금(FOR KEY SHARE)으로만
 * 서로를 기다려, 그 차례를 PostgreSQL 에서만 볼 수 있었다. 지금은 세 요청 모두 같은 행 잠금을 먼저 잡으므로 H2 판과 같은 시험을 돈다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PostgresMissionDeletionRaceTest extends MissionDeletionRaceTestBase {}
