package kr.ac.kookmin.familyfitness.league.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 지난달을 물었는데 그달 이 가족이 든 방이 없다(그달 리그에 없던 가족). 지난달 값을 지어내지 않는다. */
public class LeagueNotFoundException extends DomainException {
    public LeagueNotFoundException() {
        super("LEAGUE_NOT_FOUND", ErrorKind.NOT_FOUND, "그달 이 가족의 리그 기록이 없습니다");
    }
}
