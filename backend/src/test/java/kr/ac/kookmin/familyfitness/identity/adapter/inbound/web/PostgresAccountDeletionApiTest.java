package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import kr.ac.kookmin.familyfitness.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 운영과 같은 PostgreSQL(Testcontainers)에서 같은 시험을 돈다. 외래 키 검사 시점, uuid 칸의 형, information_schema 의 이름이
 * H2 와 달라서다. Docker 가 없는 환경에서는 건너뛴다(CI 에서는 돈다).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PostgresAccountDeletionApiTest extends AccountDeletionApiTestBase {}
