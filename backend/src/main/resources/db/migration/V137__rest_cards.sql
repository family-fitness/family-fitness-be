-- activity: 쉬는 날 카드. 가족 단위로 한 달 두 장, 오늘부터 그달 안의 날에 보호자가 쓴다.
-- 카드를 미리 만들어 두지 않고 쓴 카드만 한 행으로 남긴다(남은 장 = 두 장 - 그달 행 수). 되돌리면 행을 지운다.
-- card_no 는 그달 몇 번째 카드인가(1부터). (family_id, rest_month, card_no) 유니크가 두 보호자가 동시에 쓸 때
-- 한 달 장수를 넘기지 않게 막는다. 장수 상한(두 장)은 앱이 고르는 번호 범위(1~2)로 지킨다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
create table rest_cards (
    id         uuid     not null,
    family_id  uuid     not null,
    rest_date  date     not null,
    rest_month date     not null,
    card_no    smallint not null,
    created_by uuid     not null,
    created_at timestamp with time zone not null,
    constraint pk_rest_cards primary key (id),
    constraint fk_rest_cards_family foreign key (family_id) references families (id),
    constraint fk_rest_cards_created_by foreign key (created_by) references profiles (id),
    constraint ck_rest_cards_month check (extract(day from rest_month) = 1),
    constraint ck_rest_cards_card_no check (card_no >= 1)
);
create unique index ux_rest_cards_family_date on rest_cards (family_id, rest_date);
create unique index ux_rest_cards_family_card on rest_cards (family_id, rest_month, card_no);
