package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import kr.ac.kookmin.familyfitness.identity.application.ReviewLoginKind;
import org.jspecify.annotations.Nullable;

/** 심사용 계정 로그인 본문. 본문이 없거나 kind 가 없으면 FAMILY 다(예전 FE 는 본문 없이 부른다). 모르는 값은 본문을 읽지 못해 400. */
public record ReviewLoginRequest(@Nullable ReviewLoginKind kind) {
    ReviewLoginKind kindOrDefault() {
        return kind == null ? ReviewLoginKind.FAMILY : kind;
    }
}
