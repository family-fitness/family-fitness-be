-- identity: 운동할 수 있는 시간. 한 사람의 한 주를 요일마다 한 칸(시작 시각 · 분)으로 적는다.
-- 운동을 막는 데 쓰지 않는다 — AI 편성의 「몇 분」 기본값과 「이번 주 며칠」 목표의 출처일 뿐이다.
-- PUT 은 한 트랜잭션에서 그 프로필의 행을 전부 지우고 다시 넣는다(한 주 통째로 바꾸기). 빈 목록이면 행이 없다.
-- 하루 한 칸은 (profile_id, day_of_week) 기본 키가 막는다. 요일 · 분 범위는 앱(WeeklyAvailability)이 먼저 보고
-- 400 INVALID_SLOT 으로 답하며, check 제약은 앱을 거치지 않은 쓰기를 막는 두 번째 줄이다.
-- start_time 은 한국 시각(KST)의 시:분이다. created_by 는 저장한 보호자 프로필.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
create table profile_availability_slots (
    profile_id  uuid       not null,
    day_of_week varchar(3) not null,
    start_time  time       not null,
    minutes     smallint   not null,
    created_by  uuid       not null,
    created_at  timestamp with time zone not null,
    constraint pk_profile_availability_slots primary key (profile_id, day_of_week),
    constraint fk_profile_availability_slots_profile foreign key (profile_id) references profiles (id),
    constraint fk_profile_availability_slots_created_by foreign key (created_by) references profiles (id),
    constraint ck_profile_availability_slots_day check (day_of_week in ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')),
    constraint ck_profile_availability_slots_minutes check (minutes between 5 and 120)
);
