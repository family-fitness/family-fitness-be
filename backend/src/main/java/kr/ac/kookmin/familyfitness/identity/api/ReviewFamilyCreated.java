package kr.ac.kookmin.familyfitness.identity.api;

import java.util.UUID;

/**
 * 심사용 계정 로그인이 체험 가족을 만들었다. 가족을 만든 트랜잭션 안에서 발행한다.
 * 측정(fitness)이 같은 트랜잭션에서 동기로 듣고 두 아이의 측정 회차를 넣는다 — 로그인 응답이 나가기 전에 측정과 등급이 서 있어야
 * 심사위원이 홈에서 바로 결과를 본다. 커밋 뒤에 받으면 첫 화면이 빈 채로 뜰 수 있다.
 *
 * @param guardianUserId 체험 가족을 만든 보호자 계정. 측정은 이 계정 이름으로 넣는다
 * @param youthProfileId 유소년 아이(하윤) — 유소년 종목 일곱 가지와 키 · 몸무게 · 허리둘레를 재 둔다
 * @param toddlerProfileId 유아기 아이(서준) — 유아기 종목 몇 가지만 재 둔다
 */
public record ReviewFamilyCreated(UUID familyId, UUID guardianUserId, UUID youthProfileId, UUID toddlerProfileId) {}
