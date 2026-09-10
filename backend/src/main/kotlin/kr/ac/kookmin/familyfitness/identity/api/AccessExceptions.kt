package kr.ac.kookmin.familyfitness.identity.api

import kr.ac.kookmin.familyfitness.shared.domain.DomainException
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind
import java.util.UUID

class NotSameFamilyException(
    message: String = "이 가족의 구성원이 아닙니다",
) : DomainException("NOT_SAME_FAMILY", ErrorKind.FORBIDDEN, message)

class NotAParentException(
    message: String = "보호자만 할 수 있습니다",
) : DomainException("NOT_A_PARENT", ErrorKind.FORBIDDEN, message)

class ProfileNotFoundException(
    profileId: UUID,
) : DomainException("PROFILE_NOT_FOUND", ErrorKind.NOT_FOUND, "프로필이 없습니다: $profileId")

class FamilyNotFoundException(
    familyId: UUID,
) : DomainException("FAMILY_NOT_FOUND", ErrorKind.NOT_FOUND, "가족이 없습니다: $familyId")
