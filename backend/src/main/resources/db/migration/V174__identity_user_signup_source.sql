-- identity: 계정이 어디서 왔는지(광고나 공유 링크의 utm 값) 남긴다. 유입 경로별 가입 수를 세는 데 쓴다.
-- FE 가 처음 들어온 주소의 utm_source, utm_medium, utm_campaign 을 기억했다가 구글 로그인 요청에 실어 보낸다.
-- 처음 가입할 때만 남기고, 이미 있는 계정으로 다시 로그인하면 쓰지 않는다(첫 유입 경로가 남는다).
-- 캠페인 이름이지 개인정보가 아니어서 계정 행에 둔다. 탈퇴로 계정 행이 지워지면 함께 지워진다.
-- 값마다 앞뒤 공백과 제어 문자를 걷고 64자로 자른다(SignupSource). 이 파일 앞에 가입했거나 utm 없이 들어온 계정은 null 이다.
-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 도는 문법만 쓴다.
alter table users add column signup_utm_source varchar(64);
alter table users add column signup_utm_medium varchar(64);
alter table users add column signup_utm_campaign varchar(64);
