package kr.ac.kookmin.familyfitness.identity.domain

import kr.ac.kookmin.familyfitness.shared.domain.DomainException
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind

/** 이 가족의 구성원이 아닌 계정이 가족 자원을 건드렸다. */
class FamilyAccessDeniedException(
    message: String = "이 가족의 구성원이 아닙니다",
) : DomainException("NOT_SAME_FAMILY", ErrorKind.FORBIDDEN, message)

/** 만 14세 미만 프로필은 보호자 동의(개인정보·건강정보 둘 다)가 있어야 저장·측정한다. */
class GuardianConsentRequiredException(
    message: String = "보호자 동의가 필요합니다",
) : DomainException("CONSENT_REQUIRED", ErrorKind.RULE_VIOLATION, message)

class AlreadyInFamilyException(
    message: String = "이 계정에 이미 프로필이 붙어 있습니다",
) : DomainException("ALREADY_IN_FAMILY", ErrorKind.CONFLICT, message)

class AlreadyClaimedException(
    message: String = "이미 계정이 붙은 프로필입니다",
) : DomainException("ALREADY_CLAIMED", ErrorKind.CONFLICT, message)

class AlreadyMemberException(
    message: String = "이미 이 가족의 구성원입니다",
) : DomainException("ALREADY_MEMBER", ErrorKind.CONFLICT, message)

class ClaimCodeExpiredException(
    message: String = "만료된 초대 코드입니다",
) : DomainException("CODE_EXPIRED", ErrorKind.GONE, message)

class ClaimCodeNotFoundException(
    message: String = "초대 코드를 찾을 수 없습니다",
) : DomainException("CODE_NOT_FOUND", ErrorKind.NOT_FOUND, message)

/** 참여 수준은 PARENT 프로필에만 있는 개념이다. */
class SupportModeNotApplicableException(
    message: String = "아이 프로필에는 참여 수준이 없습니다",
) : DomainException("NOT_APPLICABLE", ErrorKind.RULE_VIOLATION, message)

/** 본인 계정에 붙은 프로필만 다룰 수 있는 작업에 남의 프로필을 넣었다. */
class NotOwnProfileException(
    message: String = "본인 프로필만 다룰 수 있습니다",
) : DomainException("FORBIDDEN", ErrorKind.FORBIDDEN, message)

class SelfCheerException(
    message: String = "자기 자신에게는 응원을 보낼 수 없습니다",
) : DomainException("SELF_CHEER", ErrorKind.RULE_VIOLATION, message)

class NotFamilyMemberException(
    message: String = "같은 가족의 프로필이 아닙니다",
) : DomainException("NOT_FAMILY_MEMBER", ErrorKind.RULE_VIOLATION, message)

class TooManyCheersException(
    message: String = "같은 대상에게 보낼 수 있는 응원 횟수를 넘었습니다",
) : DomainException("TOO_MANY", ErrorKind.TOO_MANY, message)
