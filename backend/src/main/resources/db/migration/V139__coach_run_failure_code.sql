-- coaching: FAILED 로 끝난 편성의 까닭을 화면이 가를 수 있는 코드로 남긴다(CoachRunFailureCode 이름).
-- failure_reason 은 개발자용 원문이라 응답에 싣지 않는다. 응답의 failureCode 는 이 칸이다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.
alter table coach_runs add column failure_code varchar(20);

-- 이미 FAILED 인 행은 원문에서 까닭을 옮긴다. 접두어는 이 변경 전 CoachRunExecutor · CoachRunTimeLimit 가 쓰던 것이다.
-- (timeout: · failed: 는 이제 대체 편성으로 넘기지만, 그때는 대체 편성 없이 FAILED 로 끝났으므로 AI_FAILED 로 옮긴다.)
update coach_runs
   set failure_code = case
           when ai_refused = true then 'NO_CITATIONS'
           when failure_reason like 'stale:%' then 'STALE'
           when failure_reason like '%보호자 동의%' then 'CONSENT_REQUIRED'
           when failure_reason like 'timeout:%'
                or failure_reason like 'failed:%'
                or failure_reason like 'AI 장애%' then 'AI_FAILED'
           else 'ERROR'
       end
 where status = 'FAILED';

alter table coach_runs add constraint ck_coach_runs_failure_code
    check (failure_code in ('NO_CITATIONS', 'AI_FAILED', 'CONSENT_REQUIRED', 'BUSY', 'STALE', 'ERROR'));
