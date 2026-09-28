package kr.ac.kookmin.familyfitness.coaching.application;

import kr.ac.kookmin.familyfitness.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 운영과 같은 PostgreSQL(Testcontainers, READ COMMITTED)에서 돈다. Docker 가 없는 환경에서는 건너뛴다(CI 에서는 돈다). */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PostgresSessionCompletionRaceTest extends SessionCompletionRaceTestBase {}
