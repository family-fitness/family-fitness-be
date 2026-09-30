package kr.ac.kookmin.familyfitness.identity.api;

import java.util.UUID;

/**
 * 심사용 계정 로그인이 체험 가족을 만들었다. 가족을 만든 트랜잭션 안에서 발행하고, 아래 모듈이 같은 트랜잭션에서 동기로 듣고
 * 지난 2주 기록을 넣는다. 로그인 응답이 나가기 전에 기록이 서 있어야 심사위원이 홈에서 바로 결과를 본다. 커밋 뒤에 받으면 첫 화면이
 * 빈 채로 뜰 수 있다. 날마다 무엇을 했는지는 {@link ReviewFamilyDays} 에 있고, 듣는 차례는 {@code @Order} 로 정했다.
 *
 * <pre>
 * 10 activity   쉬는 날 카드
 * 20 fitness    측정(아이 두 번, 보호자 한 번)
 * 30 coaching   미션과 칸 끝 기록. 경험치, 연속 기록, 업적, 리그 달성률 재료가 여기서 따라 쌓인다
 * 40 identity   응원 스티커와 고마워요
 * </pre>
 *
 * @param guardianUserId 체험 가족을 만든 보호자 계정. 기록은 모두 이 계정 이름으로 넣는다
 * @param youthProfileId 유소년 아이(하윤). 유소년 종목 일곱 가지와 키, 몸무게, 허리둘레를 재 둔다
 * @param toddlerProfileId 유아기 아이(서준). 유아기 종목 몇 가지만 재 둔다
 */
public record ReviewFamilyCreated(UUID familyId, UUID guardianUserId, UUID youthProfileId, UUID toddlerProfileId) {}
