package kr.ac.kookmin.familyfitness.identity.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;

/**
 * 응원 종류(kind)별 규칙. 보내는 쪽 · 받는 쪽 역할은 저장된 프로필에서 읽는다(요청 값을 믿지 않는다).
 *
 * <pre>
 * DONE   아이 → 부모. 「다 했어요」
 * PRAISE 부모 → 아이. 칭찬 스티커 · 한마디(FE 규칙 12 「칭찬은 부모가 보낸다」)
 * THANKS 아이 → 부모. 자기에게 온 PRAISE 스티커 하나에 한 번, 스티커를 붙여 돌려보낸다
 * </pre>
 */
public final class CheerRules {
    private CheerRules() {}

    /**
     * kind 가 안 오면 FE 목이 알림을 가르는 방식(fe:src/mocks/notifications.ts)대로 정한다.
     * 받는 쪽이 아이이거나 보내는 쪽이 부모면 PRAISE, 아이 → 부모는 스티커가 있으면 THANKS · 없으면 DONE.
     */
    public static CheerKind resolveKind(
            @Nullable CheerKind requested, ProfileRole from, ProfileRole to, boolean hasSticker) {
        if (requested != null) return requested;
        if (from == ProfileRole.PARENT || to == ProfileRole.CHILD) return CheerKind.PRAISE;
        return hasSticker ? CheerKind.THANKS : CheerKind.DONE;
    }

    /**
     * 요청 모양. 판정 순서: replyToCheerId 는 THANKS 에만 → THANKS 는 스티커가 있어야 →
     * kind 를 밝혀 보낸 THANKS 는 replyToCheerId 가 있어야. kind 를 안 보낸 옛 요청(전환 기간)은 답할 대상 없이도 받는다.
     */
    public static void checkShape(
            CheerKind kind, boolean kindGiven, boolean hasSticker, @Nullable UUID replyToCheerId) {
        if (replyToCheerId != null && kind != CheerKind.THANKS) {
            throw new InvalidCheerException("replyToCheerId 는 THANKS 에만 보낼 수 있습니다");
        }
        if (kind != CheerKind.THANKS) return;
        if (!hasSticker) throw new InvalidCheerException("THANKS 는 stickerId 가 있어야 합니다");
        if (kindGiven && replyToCheerId == null) {
            throw new InvalidCheerException("THANKS 는 replyToCheerId 가 있어야 합니다");
        }
    }

    /** 방향. PRAISE 를 부모가 아닌 사람이 보내면 403 NOT_A_PARENT, 나머지 어긋남은 422 CHEER_KIND_NOT_ALLOWED. */
    public static void checkDirection(CheerKind kind, ProfileRole from, ProfileRole to) {
        switch (kind) {
            case PRAISE -> {
                if (from != ProfileRole.PARENT) throw new NotAParentException("칭찬은 보호자만 보낼 수 있습니다");
                if (to != ProfileRole.CHILD) throw new CheerKindNotAllowedException("칭찬은 아이에게만 보낼 수 있습니다");
            }
            case DONE, THANKS -> {
                if (from != ProfileRole.CHILD || to != ProfileRole.PARENT) {
                    throw new CheerKindNotAllowedException(kind + " 는 아이가 보호자에게만 보낼 수 있습니다");
                }
            }
        }
    }

    /**
     * THANKS 가 답하는 원래 응원. 스티커가 붙은 PRAISE 이고, 그 PRAISE 를 받은 사람이 보내며, 보낸 사람에게 돌아가야 한다.
     * 아니면 422 NOT_A_REPLY_TARGET.
     */
    public static void checkReplyTarget(Cheer original, UUID fromProfileId, UUID toProfileId) {
        boolean praiseWithSticker = original.kind() == CheerKind.PRAISE && original.stickerId() != null;
        boolean mine = original.toProfileId().equals(fromProfileId);
        boolean backToSender = original.fromProfileId().equals(toProfileId);
        if (!praiseWithSticker || !mine || !backToSender) throw new NotAReplyTargetException();
    }
}
