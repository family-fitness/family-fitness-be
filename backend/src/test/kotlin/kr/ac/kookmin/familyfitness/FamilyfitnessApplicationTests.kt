package kr.ac.kookmin.familyfitness

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/** H2(PostgreSQL 모드) 위에서 Flyway 마이그레이션·시드·보안 설정까지 포함해 컨텍스트가 뜨는지 본다. Docker 가 없어도 돈다. */
@SpringBootTest
@ActiveProfiles("test")
class FamilyfitnessApplicationTests {
    @Test
    fun contextLoads() {
    }
}
