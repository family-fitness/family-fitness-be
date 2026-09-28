package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 승인할 제안 항목의 기간이 모두 지났다(결정 40 · 46). FE 목에 코드가 없어 새로 둔다. 실행 상태는 바꾸지 않는다. */
public class ProposalExpiredException extends DomainException {
    public ProposalExpiredException(UUID runId, LocalDate today) {
        super("PROPOSAL_EXPIRED", ErrorKind.CONFLICT, "기간이 지난 제안이라 승인할 수 없습니다: run=" + runId + ", today=" + today);
    }
}
