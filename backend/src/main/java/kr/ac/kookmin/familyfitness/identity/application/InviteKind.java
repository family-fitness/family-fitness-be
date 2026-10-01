package kr.ac.kookmin.familyfitness.identity.application;

/** 초대코드가 가리키는 것. 두 코드는 같은 입력칸과 같은 주소(미리 보기, 코드 쓰기)를 쓴다. */
public enum InviteKind {
    /** 가족 초대(초대 먼저). 역할만 정해져 있고, 들어오는 사람이 이름과 생년월일을 넣어 자기 프로필을 만든다 */
    FAMILY,
    /** 자리 초대. 보호자가 정보를 넣어 만든 프로필 자리에 계정을 붙인다 */
    PROFILE
}
