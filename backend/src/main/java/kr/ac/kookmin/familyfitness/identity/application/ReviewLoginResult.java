package kr.ac.kookmin.familyfitness.identity.application;

import org.jspecify.annotations.Nullable;

/**
 * 심사용 계정 로그인의 결과.
 *
 * @param inviteCode INVITED 로 들어와 아직 합류하지 않은 계정이면 체험 가족 아빠 자리의 초대코드. 그 밖에는 null
 */
public record ReviewLoginResult(AuthResult auth, @Nullable String inviteCode) {}
