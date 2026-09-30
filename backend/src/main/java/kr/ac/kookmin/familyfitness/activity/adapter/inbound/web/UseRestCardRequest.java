package kr.ac.kookmin.familyfitness.activity.adapter.inbound.web;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.jspecify.annotations.Nullable;

/**
 * 쉬는 날 카드 쓰기. {@code date} 는 「YYYY-MM-DD」(FE 요청서 3장 {@code { date }}). 설계안 이름 {@code restDate} 도 받는다.
 * 없거나 형식이 틀려도 400 이 아니라 422 INVALID_DATE 로 답하려고 문자열로 받는다(FE 목과 같다).
 */
public record UseRestCardRequest(
        @JsonAlias("restDate") @Nullable String date) {}
