-- 로컬·테스트 전용: 데모 가족의 운동할 수 있는 시간. FE 목의 시연 시간표(fe:src/mocks/db.ts seedAvailability)와 같다.
-- 아이(…012)는 월 · 수 · 금 19:00 20분과 토 10:00 30분, 엄마(…011)는 토 10:00 30분. 저장한 사람은 엄마로 둔다.
-- Flyway 는 반복 마이그레이션을 설명 이름 차례로 돌리므로 R__sample_demo_family.sql(프로필) 다음에 돈다.
-- 프로필이 있고 그 요일 칸이 없을 때만 넣는다(재기동해도 중복되지 않음).
insert into profile_availability_slots (profile_id, day_of_week, start_time, minutes, created_by, created_at)
select '00000000-0000-4000-8000-000000000012', 'MON', time '19:00:00', 20, '00000000-0000-4000-8000-000000000011',
       timestamp with time zone '2026-09-01 09:00:00+09'
where exists (select 1 from profiles where id = '00000000-0000-4000-8000-000000000012')
  and not exists (select 1 from profile_availability_slots
                  where profile_id = '00000000-0000-4000-8000-000000000012' and day_of_week = 'MON');
insert into profile_availability_slots (profile_id, day_of_week, start_time, minutes, created_by, created_at)
select '00000000-0000-4000-8000-000000000012', 'WED', time '19:00:00', 20, '00000000-0000-4000-8000-000000000011',
       timestamp with time zone '2026-09-01 09:00:00+09'
where exists (select 1 from profiles where id = '00000000-0000-4000-8000-000000000012')
  and not exists (select 1 from profile_availability_slots
                  where profile_id = '00000000-0000-4000-8000-000000000012' and day_of_week = 'WED');
insert into profile_availability_slots (profile_id, day_of_week, start_time, minutes, created_by, created_at)
select '00000000-0000-4000-8000-000000000012', 'FRI', time '19:00:00', 20, '00000000-0000-4000-8000-000000000011',
       timestamp with time zone '2026-09-01 09:00:00+09'
where exists (select 1 from profiles where id = '00000000-0000-4000-8000-000000000012')
  and not exists (select 1 from profile_availability_slots
                  where profile_id = '00000000-0000-4000-8000-000000000012' and day_of_week = 'FRI');
insert into profile_availability_slots (profile_id, day_of_week, start_time, minutes, created_by, created_at)
select '00000000-0000-4000-8000-000000000012', 'SAT', time '10:00:00', 30, '00000000-0000-4000-8000-000000000011',
       timestamp with time zone '2026-09-01 09:00:00+09'
where exists (select 1 from profiles where id = '00000000-0000-4000-8000-000000000012')
  and not exists (select 1 from profile_availability_slots
                  where profile_id = '00000000-0000-4000-8000-000000000012' and day_of_week = 'SAT');
insert into profile_availability_slots (profile_id, day_of_week, start_time, minutes, created_by, created_at)
select '00000000-0000-4000-8000-000000000011', 'SAT', time '10:00:00', 30, '00000000-0000-4000-8000-000000000011',
       timestamp with time zone '2026-09-01 09:00:00+09'
where exists (select 1 from profiles where id = '00000000-0000-4000-8000-000000000011')
  and not exists (select 1 from profile_availability_slots
                  where profile_id = '00000000-0000-4000-8000-000000000011' and day_of_week = 'SAT');
