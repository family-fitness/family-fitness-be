package kr.ac.kookmin.familyfitness

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * 실제 PostgreSQL 에서 같은 마이그레이션이 돌아가는지 확인한다. Docker 가 없는 환경에서는 건너뛴다(CI 에서는 돈다).
 * `@ServiceConnection` 컨테이너가 test 프로필의 H2 datasource 설정보다 우선한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test")
class PostgresMigrationTests {
    @Test
    fun migrationsApplyOnPostgres() {
    }
}
