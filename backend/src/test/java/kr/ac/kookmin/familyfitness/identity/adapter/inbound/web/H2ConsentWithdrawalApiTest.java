package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** test 프로필의 H2(PostgreSQL 모드)에서 돈다. Docker 가 없어도 돈다. */
@SpringBootTest
@ActiveProfiles("test")
class H2ConsentWithdrawalApiTest extends ConsentWithdrawalApiTestBase {}
