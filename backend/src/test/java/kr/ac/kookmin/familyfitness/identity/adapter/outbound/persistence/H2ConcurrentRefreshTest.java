package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** test 프로필의 H2(PostgreSQL 모드, 기본 격리 수준 READ COMMITTED)에서 돈다. Docker 가 없어도 돈다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConcurrentRefreshTestBase.RotateBarrierConfig.class)
class H2ConcurrentRefreshTest extends ConcurrentRefreshTestBase {}
