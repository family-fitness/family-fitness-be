-- 초대코드 발급자 · 한 계정 한 가족.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 도는 문법만 쓴다(부분 인덱스 없음).

-- 초대코드를 보낸 보호자 프로필. 미리 보기의 invitedByName 이 이 프로필의 이름이다. 이 칸이 생기기 전에 발급된 코드는 null.
alter table profiles add column claim_code_issued_by uuid;
alter table profiles add constraint fk_profiles_claim_code_issued_by
    foreign key (claim_code_issued_by) references profiles (id);

-- 한 계정은 프로필 하나(= 가족 하나)에만 붙는다. unique 는 NULL 끼리 같다고 보지 않으므로(PostgreSQL · H2 공통)
-- 계정 없는 프로필은 여럿이어도 된다. 가족 만들기 · 초대 수락이 동시에 와도 이 인덱스가 둘째를 막는다.
-- user_id 가 두 가족에 붙은 행이 이미 있으면 이 문장에서 실패한다 — 어느 가족을 남길지는 사람이 정해야 한다.
create unique index uq_profiles_user on profiles (user_id);

-- (family_id, user_id) 유니크는 위 인덱스에 포함된다. 같은 위반에 이름이 둘이면 어느 쪽이 먼저 걸릴지 DB 마다 달라
-- 어댑터가 ALREADY_IN_FAMILY 로 바꾸지 못하므로 지운다. ix_profiles_user 는 H2 가 fk_profiles_user 의 인덱스로 쓰고 있어 남긴다.
alter table profiles drop constraint uq_profiles_family_user;
