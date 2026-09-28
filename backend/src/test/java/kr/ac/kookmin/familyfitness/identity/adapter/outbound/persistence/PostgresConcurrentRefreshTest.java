package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import kr.ac.kookmin.familyfitness.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 운영과 같은 PostgreSQL(Testcontainers, 기본 격리 수준 READ COMMITTED)에서 돈다. Docker 가 없는 환경에서는 건너뛴다(CI 에서는
 * 돈다). {@code @ServiceConnection} 컨테이너가 test 프로필의 H2 datasource 설정보다 우선한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ConcurrentRefreshTestBase.RotateBarrierConfig.class})
class PostgresConcurrentRefreshTest extends ConcurrentRefreshTestBase {}
