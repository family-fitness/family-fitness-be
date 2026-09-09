-- 로컬·테스트 전용 데모 가족. 프론트가 가입 흐름 없이 바로 붙어 볼 수 있게 계정·가족·프로필을 미리 넣는다.
-- 사용: POST /api/v1/auth/dev-login {"providerUserId":"demo-parent"} → nextStep HOME, 프로필 3개.
--       두 번째 부모는 {"providerUserId":"demo-parent-2"} (아직 프로필 없음 → 초대코드 K7M2QT 로 /claim 가능).
-- 없는 행만 넣는다(재기동해도 중복되지 않음).
insert into users (id, provider, provider_user_id, email, status, created_at, updated_at)
select '00000000-0000-4000-8000-000000000001', 'DEV', 'demo-parent', 'demo-parent@example.com', 'ACTIVE',
       timestamp with time zone '2026-09-01 09:00:00+09', timestamp with time zone '2026-09-01 09:00:00+09'
where not exists (select 1 from users where id = '00000000-0000-4000-8000-000000000001');
insert into users (id, provider, provider_user_id, email, status, created_at, updated_at)
select '00000000-0000-4000-8000-000000000002', 'DEV', 'demo-parent-2', 'demo-parent-2@example.com', 'ACTIVE',
       timestamp with time zone '2026-09-01 09:00:00+09', timestamp with time zone '2026-09-01 09:00:00+09'
where not exists (select 1 from users where id = '00000000-0000-4000-8000-000000000002');

insert into families (id, name, region_code, created_at, updated_at)
select '00000000-0000-4000-8000-000000000010', '데모네', null,
       timestamp with time zone '2026-09-01 09:00:00+09', timestamp with time zone '2026-09-01 09:00:00+09'
where not exists (select 1 from families where id = '00000000-0000-4000-8000-000000000010');

-- 부모(owner, 계정 연결됨, 응원 모드 FULL)
insert into profiles (id, family_id, user_id, display_name, birth_date, sex, role, is_owner, height_cm, weight_kg, support_mode,
                      claim_code, claim_code_expires_at, claim_code_claimed_at, consent_personal_at, consent_health_at,
                      consent_by_user_id, consent_revoked_at, created_at, updated_at)
select '00000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000010', '00000000-0000-4000-8000-000000000001',
       '데모 엄마', date '1988-04-12', 'F', 'PARENT', true, 162.0, 57.0, 'FULL',
       null, null, null, null, null, null, null,
       timestamp with time zone '2026-09-01 09:00:00+09', timestamp with time zone '2026-09-01 09:00:00+09'
where not exists (select 1 from profiles where id = '00000000-0000-4000-8000-000000000011');
-- 아이(유소년, 계정 없음, 보호자 동의 있음)
insert into profiles (id, family_id, user_id, display_name, birth_date, sex, role, is_owner, height_cm, weight_kg, support_mode,
                      claim_code, claim_code_expires_at, claim_code_claimed_at, consent_personal_at, consent_health_at,
                      consent_by_user_id, consent_revoked_at, created_at, updated_at)
select '00000000-0000-4000-8000-000000000012', '00000000-0000-4000-8000-000000000010', null,
       '데모 첫째', date '2015-06-02', 'F', 'CHILD', false, 148.0, 41.0, null,
       null, null, null, timestamp with time zone '2026-09-01 09:00:00+09', timestamp with time zone '2026-09-01 09:00:00+09',
       '00000000-0000-4000-8000-000000000001', null,
       timestamp with time zone '2026-09-01 09:00:00+09', timestamp with time zone '2026-09-01 09:00:00+09'
where not exists (select 1 from profiles where id = '00000000-0000-4000-8000-000000000012');
-- 배우자(PARENT, 계정 없음, 초대코드 K7M2QT 발급됨 — /claim 시연용, 만료 2099년)
insert into profiles (id, family_id, user_id, display_name, birth_date, sex, role, is_owner, height_cm, weight_kg, support_mode,
                      claim_code, claim_code_expires_at, claim_code_claimed_at, consent_personal_at, consent_health_at,
                      consent_by_user_id, consent_revoked_at, created_at, updated_at)
select '00000000-0000-4000-8000-000000000013', '00000000-0000-4000-8000-000000000010', null,
       '데모 아빠', date '1986-11-23', 'M', 'PARENT', false, null, null, null,
       'K7M2QT', timestamp with time zone '2099-12-31 00:00:00+09', null, null, null, null, null,
       timestamp with time zone '2026-09-01 09:00:00+09', timestamp with time zone '2026-09-01 09:00:00+09'
where not exists (select 1 from profiles where id = '00000000-0000-4000-8000-000000000013');
