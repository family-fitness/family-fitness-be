-- identity: 보호자(PARENT)에게 잘못 걸린 「보호자 동의 철회」 를 푼다(QA KP-01).
-- 보호자 동의는 아이(CHILD)에게만 뜻이 있다. 전에는 한 보호자가 PATCH /profiles/{다른 보호자}/consent 로 그 보호자의 동의를
-- 거둘 수 있었고, 거둔 채인 프로필은 나이와 상관없이 막혀(Profile.consentRequired) 그 보호자의 칸 끝 · 측정 · 미션 참여가 422 가 됐다.
-- 본인은 SELF_CONSENT 로 되돌릴 수 없었다. 이제 앱은 PARENT 대상 동의 변경을 422 CONSENT_NOT_APPLICABLE 로 막고, 여기서는 이미
-- 거둔 채인 PARENT 를 풀어 준다. 아이의 철회는 건드리지 않는다.
-- 이력(consent_events)은 지우지 않는다: 거둔 줄(REVOKED)은 그대로 두고, 무효로 했다는 줄(VOIDED)을 하나 더 넣는다.
--   VOIDED  앞선 철회를 무효로 했다. actor_user_id · personal_data · health_data 는 null 이다(사람이 아니라 이 마이그레이션이 했다).
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.

alter table consent_events drop constraint ck_consent_events_kind;
alter table consent_events add constraint ck_consent_events_kind check (kind in ('GRANTED', 'REVOKED', 'VOIDED'));

insert into consent_events (profile_id, actor_user_id, kind, personal_data, health_data, occurred_at)
select id, null, 'VOIDED', null, null, current_timestamp
from profiles
where role = 'PARENT' and consent_revoked_at is not null;

-- 프로필 행 버전(V145 의 낙관적 잠금)도 올린다 — 이 행을 읽어 둔 요청이 옛 철회 값으로 덮지 못하게.
update profiles
set consent_revoked_at = null,
    version = version + 1,
    updated_at = current_timestamp
where role = 'PARENT' and consent_revoked_at is not null;
