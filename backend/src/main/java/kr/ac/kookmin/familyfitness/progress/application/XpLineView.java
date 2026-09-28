package kr.ac.kookmin.familyfitness.progress.application;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.domain.XpKind;
import org.jspecify.annotations.Nullable;

/**
 * 최근 경험치 한 줄. 문장 대신 까닭(kind)과 보낸 사람(fromProfileId)을 준다 — 「엄마가 붙여 준 스티커」 처럼 보는 사람마다 부르는 말이
 * 달라 문장은 화면이 짓는다(FE 요청서 0-2).
 *
 * <pre>
 * SESSION_DONE  그날 운동을 했다(칸들의 합)                   「운동을 했어요」
 * MISSION_DONE  그날 운동을 했고 하나 이상 끝까지 했다(그날 합) 「운동을 다 했어요」
 * STICKER       칭찬 스티커 한 장 — fromProfileId 가 붙인 사람  「○○가 붙여 준 스티커」
 * REMEASURE     다시 잰 회차 하나                              「키 · 몸무게를 새로 쟀어요」
 * </pre>
 *
 * @param fromProfileId 스티커를 붙인 사람. STICKER 가 아니면 null
 * @param occurredOn 그 일이 있었던 날(KST)
 */
public record XpLineView(XpKind kind, @Nullable UUID fromProfileId, int amount, LocalDate occurredOn) {}
