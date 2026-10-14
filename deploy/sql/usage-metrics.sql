-- 이용 지표 집계. 운영 DB(PostgreSQL)에서 읽기만 한다. 심사용 계정(provider REVIEW)과 개발용 계정(DEV)이 낀 가족은 모두 뺀다.
--
-- 돌리는 법(Lightsail 서버, deploy 폴더에서). from 은 그날 0시부터, to 는 그날 0시 전까지(한국 시각)다.
--   docker compose exec -T postgres psql -U familyfitness -d familyfitness \
--     -v from=2026-10-01 -v to=2026-11-01 -f - < sql/usage-metrics.sql
--
-- 지표마다 결과 표가 하나씩 나온다. 사람 이름, 이메일 같은 개인정보는 내지 않는다(숫자와 캠페인 이름만).
-- plan_source, fallback_reason, finished_at 은 V173, signup_utm_* 은 V174 부터 차므로 그 앞의 행은 「(기록 없음)」 으로 센다.

\set ON_ERROR_STOP on
\pset footer off

-- 기간을 한국 시각 경계의 timestamptz 로 바꿔 둔다
select (:'from'::date)::timestamp at time zone 'Asia/Seoul' as from_at,
       (:'to'::date)::timestamp at time zone 'Asia/Seoul'   as to_at
\gset

create temporary view real_families as
select f.id, f.created_at
  from families f
 where not exists (select 1
                     from profiles p
                     join users u on u.id = p.user_id
                    where p.family_id = f.id
                      and u.provider in ('REVIEW', 'DEV'));

-- 1. 유입 경로별 가입. 기간 안에 가입한 구글 계정과, 그 가운데 가족을 만들거나 합류한(프로필이 생긴) 계정 수
\echo '1. 유입 경로별 가입'
select coalesce(u.signup_utm_source, '(기록 없음)') as utm_source,
       coalesce(u.signup_utm_medium, '-')           as utm_medium,
       coalesce(u.signup_utm_campaign, '-')         as utm_campaign,
       count(*)                                     as signed_up,
       count(*) filter (where exists (select 1 from profiles p where p.user_id = u.id)) as joined_family
  from users u
 where u.provider = 'GOOGLE'
   and u.created_at >= :'from_at' and u.created_at < :'to_at'
 group by 1, 2, 3
 order by signed_up desc;

-- 2. AI 제안 승인율. 기간 안에 시작한 편성 가운데 제안이 나온 것(승인 대기, 승인, 거절)을 분모로 한다.
--    같은 날 새 편성이 들어와 밀려난 제안(거절 사유 「새 제안으로 바뀌었어요」)은 보호자가 고른 것이 아니어서 분모에서 뺀다.
\echo '2. AI 제안 승인율'
select count(*) filter (where r.status = 'APPROVED')          as approved,
       count(*) filter (where r.status = 'REJECTED')          as rejected,
       count(*) filter (where r.status = 'AWAITING_APPROVAL') as awaiting,
       round(100.0 * count(*) filter (where r.status = 'APPROVED') / nullif(count(*), 0), 1) as approval_rate_pct
  from coach_runs r
  join real_families f on f.id = r.family_id
 where r.created_at >= :'from_at' and r.created_at < :'to_at'
   and r.status in ('AWAITING_APPROVAL', 'APPROVED', 'REJECTED')
   and coalesce(r.rejected_reason, '') <> '새 제안으로 바뀌었어요';

-- 3. 편성이 어떻게 끝났나. 누가 짰는지(AI, 대체 편성, 실패)별 건수와 편성에 걸린 시간(초)
\echo '3. 편성 결과와 걸린 시간'
select case when r.status = 'FAILED' then 'FAILED(' || coalesce(r.failure_code, '?') || ')'
            else coalesce(r.plan_source, '(기록 없음)') end              as outcome,
       coalesce(r.fallback_reason, '-')                                  as fallback_reason,
       count(*)                                                          as runs,
       round(avg(extract(epoch from r.finished_at - r.created_at))::numeric, 1) as avg_sec,
       round((percentile_cont(0.5) within group (
                  order by extract(epoch from r.finished_at - r.created_at)))::numeric, 1) as median_sec
  from coach_runs r
  join real_families f on f.id = r.family_id
 where r.created_at >= :'from_at' and r.created_at < :'to_at'
   and r.status <> 'RUNNING'
 group by 1, 2
 order by runs desc;

-- 4. 승인된 미션의 완료율. 기간 안에 시작하는 코치 미션의 참여자 가운데 완료한 비율을 아이와 보호자로 나눠 본다
\echo '4. 승인된 미션 완료율'
select p.role,
       count(*)                                         as participants,
       count(*) filter (where mp.status = 'COMPLETED')  as completed,
       round(100.0 * count(*) filter (where mp.status = 'COMPLETED') / nullif(count(*), 0), 1) as completion_rate_pct
  from missions m
  join real_families f on f.id = m.family_id
  join mission_participants mp on mp.mission_id = m.id
  join profiles p on p.id = mp.profile_id
 where m.origin = 'COACH'
   and m.starts_on >= :'from'::date and m.starts_on < :'to'::date
 group by p.role
 order by p.role;

-- 5. 가족 운동일. 가족마다 기간 안에 누구든 운동 칸을 하나라도 끝낸 날 수. 운동한 가족만 센다
\echo '5. 가족 운동일 분포'
with family_days as (
    select p.family_id, count(distinct c.completed_on) as days
      from mission_session_completions c
      join profiles p on p.id = c.profile_id
      join real_families f on f.id = p.family_id
     where c.completed_on >= :'from'::date and c.completed_on < :'to'::date
     group by p.family_id
)
select count(*)                     as active_families,
       round(avg(days), 1)          as avg_days,
       percentile_cont(0.5) within group (order by days) as median_days,
       count(*) filter (where days >= 8) as families_8_days_or_more
  from family_days;

-- 6. 응원. 기간 안에 주고받은 응원을 종류별로
\echo '6. 응원'
select c.kind, count(*) as cheers, count(distinct c.family_id) as families
  from cheers c
  join real_families f on f.id = c.family_id
 where c.created_at >= :'from_at' and c.created_at < :'to_at'
 group by c.kind
 order by cheers desc;
