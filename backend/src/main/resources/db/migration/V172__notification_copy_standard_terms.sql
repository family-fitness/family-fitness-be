-- notification: 이미 쌓인 알림의 제목과 본문을 표준 용어로 고친 새 문구로 바꾼다.
--   알림은 만들 때의 문구를 행에 그대로 저장해서(V149), 업적 이름과 설명(Achievement), 다시 측정 알림(NotificationCopy)을 고쳐도
--   지난 알림에는 예전 문구가 남는다. 알림 목록은 최신 30건을 그대로 보여 주므로 예전 문구와 새 문구가 섞여 보이지 않게 맞춘다.
--   1) 업적 알림(ACHIEVEMENT): 「사흘 이어서」는 「3일 연속」, 「일주일 이어서」는 「7일 연속」, 「움직였어요」는 「운동했어요」,
--      「운동 한 칸」은 「운동 1개」, 「준비, 본운동, 정리」는 「준비운동, 본운동, 정리운동」, 「새로 쟀어요」는 「다시 측정했어요」,
--      「여섯 가지 힘」은 「여섯 가지 체력 요인」으로.
--   2) 다시 측정 알림(REMEASURE): 「{이름} 키와 몸무게를 새로 재 볼까요」는 「{이름} 키와 몸무게를 다시 측정해 볼까요?」,
--      「지난번에 잰 지 N일」은 「마지막 측정 후 N일」로.
-- 문구가 똑같은 행만 고치고, 그 밖의 알림은 건드리지 않는다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 도는 update ... where 와 replace 만 쓴다.

update notifications set title = '새 업적: 3일 연속' where kind = 'ACHIEVEMENT' and title = '새 업적: 사흘 이어서';
update notifications set title = '새 업적: 7일 연속' where kind = 'ACHIEVEMENT' and title = '새 업적: 일주일 이어서';
update notifications set title = '새 업적: 준비운동부터 정리운동까지' where kind = 'ACHIEVEMENT' and title = '새 업적: 준비부터 정리까지';
update notifications set title = '새 업적: 다시 측정' where kind = 'ACHIEVEMENT' and title = '새 업적: 자란 만큼 다시';
update notifications set title = '새 업적: 여섯 가지 체력 요인' where kind = 'ACHIEVEMENT' and title = '새 업적: 여섯 가지 힘';

update notifications set body = '운동 1개를 처음 완료했어요' where kind = 'ACHIEVEMENT' and body = '운동 한 칸을 처음 끝냈어요';
update notifications set body = '3일 연속 운동했어요' where kind = 'ACHIEVEMENT' and body = '3일 이어서 움직였어요';
update notifications set body = '7일 연속 운동했어요' where kind = 'ACHIEVEMENT' and body = '7일 이어서 움직였어요';
update notifications set body = '준비운동, 본운동, 정리운동을 한 번에 다 했어요'
    where kind = 'ACHIEVEMENT' and body = '준비, 본운동, 정리를 한 번에 다 했어요';
update notifications set body = '모두 합쳐 30분 운동했어요' where kind = 'ACHIEVEMENT' and body = '모두 합쳐 30분 움직였어요';
update notifications set body = '모두 합쳐 100분 운동했어요' where kind = 'ACHIEVEMENT' and body = '모두 합쳐 100분 움직였어요';
update notifications set body = '모두 합쳐 300분 운동했어요' where kind = 'ACHIEVEMENT' and body = '모두 합쳐 300분 움직였어요';
update notifications set body = '키와 몸무게를 다시 측정했어요' where kind = 'ACHIEVEMENT' and body = '키와 몸무게를 새로 쟀어요';
update notifications set body = '여섯 가지 체력 요인을 기르는 운동을 다 해 봤어요'
    where kind = 'ACHIEVEMENT' and body = '여섯 가지 힘을 기르는 운동을 다 해 봤어요';

update notifications set title = replace(title, ' 키와 몸무게를 새로 재 볼까요', ' 키와 몸무게를 다시 측정해 볼까요?')
    where kind = 'REMEASURE' and title like '% 키와 몸무게를 새로 재 볼까요';
update notifications set body = replace(body, '지난번에 잰 지 ', '마지막 측정 후 ')
    where kind = 'REMEASURE' and body like '지난번에 잰 지 %일';
