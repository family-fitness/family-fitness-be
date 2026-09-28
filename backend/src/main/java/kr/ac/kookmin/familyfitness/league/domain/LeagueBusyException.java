package kr.ac.kookmin.familyfitness.league.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 방 배정이 다른 요청과 여러 번 겹쳐 끝내 자리를 잡지 못했다. 다시 부르면 된다. */
public class LeagueBusyException extends DomainException {
    public LeagueBusyException() {
        super("LEAGUE_BUSY", ErrorKind.CONFLICT, "리그 방 배정이 다른 요청과 겹쳤습니다. 다시 시도해 주세요");
    }
}
