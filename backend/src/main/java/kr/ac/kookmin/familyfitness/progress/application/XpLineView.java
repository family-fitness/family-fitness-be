package kr.ac.kookmin.familyfitness.progress.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.domain.XpKind;
import org.jspecify.annotations.Nullable;

/**
 * 최근 경험치 한 줄. 까닭(kind)과 보낸 사람(fromProfileId)이 계약이다 — 「엄마가 붙여 준 스티커」 처럼 보는 사람마다 부르는 말이
 * 달라 문장은 화면이 짓는다(FE 요청서 0-2).
 *
 * <pre>
 * SESSION_DONE  그날 운동을 했다(칸들의 합)                   「운동을 했어요」
 * MISSION_DONE  그날 운동을 했고 하나 이상 끝까지 했다(그날 합) 「운동을 다 했어요」
 * STICKER       칭찬 스티커 한 장 — fromProfileId 가 붙인 사람  「○○가 붙여 준 스티커」
 * REMEASURE     다시 잰 회차 하나                              「키 · 몸무게를 새로 쟀어요」
 * </pre>
 *
 * <p><b>reason · at 은 전환기 칸이다.</b> 지금 FE(fe:src/lib/api/types.ts XpEvent {@code {reason, amount, at}})는 문장과
 * 시각을 그대로 그려서, 둘이 없으면 /kid/badges 가 깨진다. 그래서 서버가 FE 목과 같은 문장({@link
 * kr.ac.kookmin.familyfitness.progress.domain.XpReason})과 원장 시각을 함께 싣는다. FE 가 kind 로 문장을 짓게 되면 두 칸을
 * 걷는다.
 *
 * @param fromProfileId 스티커를 붙인 사람. STICKER 가 아니면 null
 * @param occurredOn 그 일이 있었던 날(KST)
 * @param reason 전환기 칸 — 이 줄의 주인(읽는 사람)의 말로 지은 문장. 아이가 읽으면 보호자는 엄마 · 아빠, 붙인 사람을 모르면
 *     「가족」. FE 가 kind 로 문장을 짓게 되면 걷는다
 * @param at 전환기 칸 — 원장에 적은 시각. 하루로 묶은 운동 줄은 그날 가장 늦게 적은 시각. FE 가 occurredOn 을 쓰게 되면 걷는다
 */
public record XpLineView(
        XpKind kind, @Nullable UUID fromProfileId, int amount, LocalDate occurredOn, String reason, Instant at) {}
