-- league: 가족 리그. 한 달이 한 판이고, 같은 티어의 가족을 열 가족까지 한 방(묶음)에 넣는다.
-- league_rounds 는 (그달, 티어, 방 번호)마다 한 행이다. settled_at 이 차면 그 방의 정산이 끝났다(두 번 정산하지 않는다).
-- league_members 는 (방, 가족)마다 한 행이다. (family_id, round_month) 유니크가 한 가족을 한 달에 한 방에만 둔다.
-- seat_no 는 방 안의 몇 번째 자리인가(1부터). (round_id, seat_no) 유니크가 두 가족이 동시에 들어올 때 방 정원을 넘기지 않게 막는다.
-- 정원(열 가족)은 앱이 고르는 자리 번호 범위로 지킨다. final_rate · final_rank · moved 는 정산 때 채운다(그 전엔 null).
-- 지금 달의 달성률은 저장하지 않고 조회 때마다 센다(칸을 끝내면 곧바로 다시 부르기 때문이다).
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
create table league_rounds (
    id          uuid        not null,
    round_month date        not null,
    tier        varchar(10) not null,
    group_no    smallint    not null,
    created_at  timestamp with time zone not null,
    settled_at  timestamp with time zone,
    constraint pk_league_rounds primary key (id),
    constraint ck_league_rounds_month check (extract(day from round_month) = 1),
    constraint ck_league_rounds_tier check (tier in ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND')),
    constraint ck_league_rounds_group_no check (group_no >= 1)
);
create unique index ux_league_rounds_month_tier_group on league_rounds (round_month, tier, group_no);

create table league_members (
    round_id    uuid     not null,
    family_id   uuid     not null,
    round_month date     not null,
    seat_no     smallint not null,
    joined_at   timestamp with time zone not null,
    final_rate  smallint,
    final_rank  smallint,
    moved       varchar(4),
    constraint pk_league_members primary key (round_id, family_id),
    constraint fk_league_members_round foreign key (round_id) references league_rounds (id),
    constraint fk_league_members_family foreign key (family_id) references families (id),
    constraint ck_league_members_month check (extract(day from round_month) = 1),
    constraint ck_league_members_seat check (seat_no >= 1),
    constraint ck_league_members_rate check (final_rate is null or (final_rate >= 0 and final_rate <= 100)),
    constraint ck_league_members_rank check (final_rank is null or final_rank >= 1),
    constraint ck_league_members_moved check (moved is null or moved in ('UP', 'STAY', 'DOWN'))
);
create unique index ux_league_members_family_month on league_members (family_id, round_month);
create unique index ux_league_members_round_seat on league_members (round_id, seat_no);
