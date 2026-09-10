package kr.ac.kookmin.familyfitness.shared.domain

/** 가족 안의 역할. 프로필을 만들 때 정해지고 초대받는 쪽이 못 고친다. 부모 권한 = 코치 승인 권한. */
enum class ProfileRole {
    PARENT,
    CHILD,
}

/** 부모의 참여 수준. CHILD 에게는 없는 개념. 다음 편성부터 반영된다. */
enum class SupportMode {
    CHEER_ONLY,
    WEEKEND,
    FULL,
}
