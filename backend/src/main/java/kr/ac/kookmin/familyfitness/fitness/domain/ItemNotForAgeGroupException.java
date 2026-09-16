package kr.ac.kookmin.familyfitness.fitness.domain;

import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class ItemNotForAgeGroupException extends DomainException {
    public ItemNotForAgeGroupException(String itemCode, AgeGroup ageGroup) {
        super("ITEM_NOT_FOR_AGE_GROUP", ErrorKind.RULE_VIOLATION, ageGroup.getLabel() + " 측정 항목이 아닙니다: " + itemCode);
    }
}
