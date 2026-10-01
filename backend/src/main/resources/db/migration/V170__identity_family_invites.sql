-- identity: 가족 초대(초대 먼저). 보호자는 역할만 정해 코드를 내고, 이름과 생년월일은 코드로 들어온 사람이 넣어 자기 프로필을 만든다.
-- 폰 없는 아이처럼 보호자가 정보를 먼저 넣어 만든 자리의 초대코드는 지금처럼 profiles.claim_code 에 있다.
-- 두 코드는 모양(6자리, 0/O 1/I 없는 대문자와 숫자)과 만료(7일)가 같고, 앱이 두 표를 함께 보고 겹치지 않게 만든다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 도는 문법만 쓴다(부분 인덱스 없음).
--
-- code                  초대코드. 한 번만 쓴다
-- role                  들어올 사람의 역할. 받는 사람이 고르지 못한다
-- consent_personal_data, consent_health_data
--                       CHILD 초대를 만들 때 보호자가 보낸 동의. 아이가 만 14세 미만일 수 있어 미리 받아 두고, 합류할 때 그 아이
--                       프로필의 동의와 동의 이력(consent_events)으로 옮긴다. PARENT 초대는 null
-- consent_by_user_id    그 동의를 한 보호자 계정. 그 계정이 가족에서 빠지거나 탈퇴하면 비운다(consent_events.actor_user_id 와 같다)
-- issued_by_profile_id  초대를 낸 보호자 프로필. 그 사람이 가족에서 빠지면 오너 프로필로 돌린다
-- claimed_at, claimed_by_user_id
--                       코드를 쓴 때와 계정. 쓴 계정이 탈퇴하면 계정 칸만 비운다
create table family_invites (
    code                  varchar(8)  not null,
    family_id             uuid        not null,
    role                  varchar(10) not null,
    consent_personal_data boolean,
    consent_health_data   boolean,
    consent_by_user_id    uuid,
    issued_by_profile_id  uuid        not null,
    created_at            timestamp with time zone not null,
    expires_at            timestamp with time zone not null,
    claimed_at            timestamp with time zone,
    claimed_by_user_id    uuid,
    constraint pk_family_invites primary key (code),
    constraint fk_family_invites_family foreign key (family_id) references families (id),
    constraint fk_family_invites_issued_by foreign key (issued_by_profile_id) references profiles (id),
    constraint fk_family_invites_consent_by foreign key (consent_by_user_id) references users (id),
    constraint fk_family_invites_claimed_by foreign key (claimed_by_user_id) references users (id),
    constraint ck_family_invites_role check (role in ('PARENT', 'CHILD')),
    constraint ck_family_invites_child_consent
        check (role <> 'CHILD' or (consent_personal_data = true and consent_health_data = true))
);
-- 한 가족의 초대 목록(살아 있는 것만 거른다)을 만든 차례로.
create index ix_family_invites_family on family_invites (family_id, created_at);
