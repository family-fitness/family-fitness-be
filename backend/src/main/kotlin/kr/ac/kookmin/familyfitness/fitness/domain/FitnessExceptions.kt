package kr.ac.kookmin.familyfitness.fitness.domain

import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.DomainException
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind
import java.time.LocalDate
import java.util.UUID

/** 항목 0개면 저장하지 않는다. */
class NoItemsException : DomainException("NO_ITEMS", ErrorKind.BAD_REQUEST, "측정 항목이 하나 이상 있어야 합니다")

/** 005·006(혈압)은 입력으로 받지 않는다. */
class ItemNotAllowedException(
    itemCode: String,
) : DomainException("ITEM_NOT_ALLOWED", ErrorKind.BAD_REQUEST, "입력할 수 없는 항목입니다: $itemCode")

/** 카탈로그에 없는 코드(신체조성 003·004·018·042 포함). */
class UnknownItemException(
    itemCode: String,
) : DomainException("UNKNOWN_ITEM", ErrorKind.BAD_REQUEST, "알 수 없는 측정 항목입니다: $itemCode")

/** 같은 항목이 한 요청에 두 번 들어왔다. ▲ 확정 필요 — 계약에 없는 경우라 공통 400 코드로 낸다. */
class DuplicateItemException(
    itemCode: String,
) : DomainException("BAD_REQUEST", ErrorKind.BAD_REQUEST, "같은 항목이 두 번 들어왔습니다: $itemCode")

class ItemNotForAgeGroupException(
    itemCode: String,
    ageGroup: AgeGroup,
) : DomainException("ITEM_NOT_FOR_AGE_GROUP", ErrorKind.RULE_VIOLATION, "${ageGroup.label} 측정 항목이 아닙니다: $itemCode")

/** 만 4세 미만은 규준이 없어 측정 대상이 아니다. */
class NotMeasurableException(
    message: String = "만 4세 미만은 측정 대상이 아닙니다",
) : DomainException("NOT_MEASURABLE", ErrorKind.RULE_VIOLATION, message)

/** 만 14세 미만은 보호자 동의가 살아 있어야 저장한다. */
class ConsentRequiredException(
    message: String = "보호자 동의가 필요합니다",
) : DomainException("CONSENT_REQUIRED", ErrorKind.RULE_VIOLATION, message)

/** 한 프로필의 같은 날짜 측정은 하나뿐이다. */
class DuplicateDateException(
    testedOn: LocalDate,
) : DomainException("DUPLICATE_DATE", ErrorKind.CONFLICT, "같은 날짜의 측정이 이미 있습니다: $testedOn")

/** 예측하려면 측정 기록이 하나는 있어야 한다. */
class NoFitnessTestException : DomainException("NO_FITNESS_TEST", ErrorKind.RULE_VIOLATION, "측정 기록이 없습니다")

class FitnessTestNotFoundException(
    fitnessTestId: UUID,
) : DomainException("FITNESS_TEST_NOT_FOUND", ErrorKind.NOT_FOUND, "측정 기록이 없습니다: $fitnessTestId")

/** 측정일은 미래일 수 없다. 계약의 공통 400 코드를 그대로 쓴다. */
class FutureTestDateException(
    testedOn: LocalDate,
) : DomainException("BAD_REQUEST", ErrorKind.BAD_REQUEST, "측정일은 미래일 수 없습니다: $testedOn")
