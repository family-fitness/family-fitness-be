package kr.ac.kookmin.familyfitness.coaching.application;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** test 프로필의 H2(PostgreSQL 모드, 기본 격리 수준 READ COMMITTED)에서 돈다. Docker 가 없어도 돈다. */
@SpringBootTest
@ActiveProfiles("test")
class H2SessionCompletionRaceTest extends SessionCompletionRaceTestBase {}
