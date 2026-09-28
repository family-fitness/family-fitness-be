-- identity: 발급한 리프레시 토큰 기록. 토큰 문자열은 저장하지 않고 jti 만 둔다.
-- refresh 할 때마다 새 토큰을 주고 옛 행에 revoked_at · replaced_by(다음 토큰의 jti)를 채운다(회전).
-- 이미 폐기된 jti 가 다시 오면 같은 family_id(한 번 로그인에서 이어진 회전 묶음)의 살아 있는 행을 모두 폐기한다.
-- 로그아웃도 그 묶음을 폐기한다. replaced_by 는 기록용이라 외래 키를 걸지 않는다(새 행보다 먼저 옛 행을 바꾼다).
-- 이 표가 생기기 전에 발급된 리프레시 토큰은 행이 없어 거부된다 — 배포 뒤 한 번 다시 로그인해야 한다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
create table refresh_tokens (
    jti          uuid not null,
    user_id      uuid not null,
    family_id    uuid not null,
    expires_at   timestamp with time zone not null,
    revoked_at   timestamp with time zone,
    replaced_by  uuid,
    created_at   timestamp with time zone not null,
    constraint pk_refresh_tokens primary key (jti),
    constraint fk_refresh_tokens_user foreign key (user_id) references users (id)
);

-- 재사용 감지 · 로그아웃이 묶음 단위로 폐기한다.
create index ix_refresh_tokens_family on refresh_tokens (family_id);
-- 계정 단위 조회(모든 기기에서 로그아웃 · 계정 정리)와 외래 키 검사용.
create index ix_refresh_tokens_user on refresh_tokens (user_id);
