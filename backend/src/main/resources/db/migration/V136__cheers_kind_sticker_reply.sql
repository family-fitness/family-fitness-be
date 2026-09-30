-- identity: 응원에 종류(kind) · 스티커 코드(sticker_id) · 고마워요가 답한 스티커(reply_to_cheer_id)를 둔다(FE 요청서 2장 · ASKS 0-2).
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.

-- (1) emoji 는 FE 가 스티커 코드를 실어 보내던 칸이다. 이름만 sticker_id 로 바꿔 지난 스티커를 그대로 옮긴다.
--     내용 검사 제약이 emoji 를 가리키므로 먼저 걷고, 이름을 바꾼 뒤 다시 건다.
alter table cheers drop constraint ck_cheers_content;
alter table cheers rename column emoji to sticker_id;
alter table cheers add constraint ck_cheers_content check (sticker_id is not null or message is not null);

-- (2) kind. 지난 행은 kind 를 안 보낸 요청과 같은 규칙(FE 목이 알림을 가르는 방식)으로 채운다.
--     보낸 사람이 부모이거나 받은 사람이 아이면 PRAISE, 아이 → 부모는 스티커가 있으면 THANKS · 없으면 DONE.
alter table cheers add column kind varchar(10);
update cheers set kind = case
    when (select p.role from profiles p where p.id = cheers.from_profile_id) = 'PARENT' then 'PRAISE'
    when (select p.role from profiles p where p.id = cheers.to_profile_id) = 'CHILD' then 'PRAISE'
    when sticker_id is not null then 'THANKS'
    else 'DONE'
end;
alter table cheers alter column kind set not null;
alter table cheers add constraint ck_cheers_kind check (kind in ('DONE', 'PRAISE', 'THANKS'));

-- (3) THANKS 가 답한 스티커. 스티커 하나에 THANKS 한 번(FE 규칙 12).
--     유니크 인덱스에서 NULL 끼리는 겹치지 않으므로(H2 · PostgreSQL 공통) THANKS 가 아닌 행(null)은 여럿 있어도 된다.
alter table cheers add column reply_to_cheer_id uuid;
alter table cheers add constraint fk_cheers_reply_to foreign key (reply_to_cheer_id) references cheers (id);
alter table cheers add constraint ck_cheers_reply_thanks check (reply_to_cheer_id is null or kind = 'THANKS');
create unique index ux_cheers_reply_to on cheers (reply_to_cheer_id);

-- (4) 받은 응원 목록(GET cheers?toProfileId=) · 받은 응원 구간 조회.
create index ix_cheers_to_profile_created on cheers (to_profile_id, created_at);
