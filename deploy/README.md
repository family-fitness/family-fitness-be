# 운영 배포 (AWS Lightsail)

BE 와 DB 를 Lightsail 서버 `ff-backend` 한 대에 Docker Compose 로 띄운다. main 에 BE 코드가 올라오면 GitHub Actions 가 알아서 배포한다.

```
GitHub main push
  └ backend-deploy
      1. ci      backend-ci 를 그대로 부른다(spotlessCheck, 전체 시험)
      2. image   backend/Dockerfile 로 이미지를 만들어 ghcr.io/family-fitness/family-fitness-be:<커밋> 으로 올린다
      3. deploy  ssh 로 서버에 들어가 deploy.sh 를 돌린다(새 이미지를 받아 띄우고 health 가 UP 이 될 때까지 기다린다)

브라우저 ─ CloudFront ─ ff-frontend(Next) ──사설 IP──> ff-backend ──사설 IP──> ff-ai
                                                      app       <사설 IP>:8080, 127.0.0.1:8080
                                                      postgres  (포트를 열지 않음)
```

모두 서울 리전(`ap-northeast-2`), 같은 AWS 계정이다. Lightsail 방화벽은 공인 IP 로 들어오는 요청에만 걸리고 사설 IP 끼리는 막지 않는다.
그래서 `ff-backend` 는 공인 IP 에 SSH(22)만 연다.

| 서버 | 사설 IP | 쓰는 곳 |
|---|---|---|
| `ff-backend` | 172.26.8.125 | FE 의 `BACKEND_ORIGIN=http://172.26.8.125:8080` |
| `ff-ai` | 172.26.9.167 | BE 의 `APP_AI_BASE_URL=http://172.26.9.167:8000` |
| `ff-frontend` | 172.26.14.203 | 앞에 CloudFront(`https://d249o1o9c1vnhc.cloudfront.net`) |

공개 저장소라 공인 IP 와 AWS 계정 번호는 여기 적지 않는다. `ff-backend` 의 공인 IP 는 고정 IP `ff-backend-ip` 이고, 저장소 Secrets 의 `LIGHTSAIL_HOST` 에 있다.
`aws lightsail get-static-ip --static-ip-name ff-backend-ip --profile ff-backend` 로 본다.

| 파일 | 무엇 |
|---|---|
| `compose.yaml` | 서버에서 도는 두 컨테이너. 배포마다 서버로 다시 올린다 |
| `.env.example` | 서버 `.env` 의 예시 파일. 진짜 값은 서버의 `.env` 에만 있다 |
| `server-setup.sh` | 새 서버를 한 번 설정한다(Docker, 스왑, 쓰지 않는 서비스 끄기, 로그 30일 보관, `.env` 만들기) |
| `deploy.sh` | 서버에서 app 을 새 이미지로 바꿔 띄운다. 배포 작업과 되돌리기가 쓴다 |

## 1GB 서버에 맞춘 설정

`ff-backend` 는 1GB 요금제(`micro_3_0`)다. 운영체제가 300MB 남짓을 쓰고 남는 600MB 안팎에 app 과 postgres 가 들어간다.

- app: `-Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1`, 컨테이너 한도 640MB. GC 뒤 힙은 120MB 안팎이다.
- postgres: `shared_buffers=64MB`, `max_connections=30`.
- 스왑 2GB, `vm.swappiness=10`.

`ff-backend` 에서 새 DB 에 띄워 쟀다(2026-09-30).

- 첫 배포: postgres 이미지 받기와 마이그레이션 39개를 합쳐 1분 남짓
- 메모리: app 390MB, postgres 70MB. 스왑은 300MB 안팎을 쓰는데, 대부분 잘 안 쓰는 메모리가 밀려난 것이라 계속 오가지 않는다
- 응답: 뜬 뒤 요청이 몇 번 지나가면 화면 조회는 한 번에 5~50ms

심사용 로그인은 체험 가족을 한꺼번에 만들어 한 번에 몇 초씩 걸린다.
요청이 몰려 느려지면 서버를 2GB 요금제로 바꾸고(스냅샷으로 새 서버를 만들어 갈아탄다) `-Xmx` 를 512m 로 올린다.

## 처음 한 번

AWS CLI 는 `ff-backend` 역할로 쓴다. 개인 키로 바로 부르면 권한이 없다.

```bash
aws configure --profile biseong        # 개인 액세스 키, ap-northeast-2, json
aws configure set role_arn arn:aws:iam::<계정 번호>:role/ff-backend --profile ff-backend
aws configure set source_profile biseong --profile ff-backend
aws configure set region ap-northeast-2 --profile ff-backend
aws sts get-caller-identity --profile ff-backend   # assumed-role/ff-backend 가 나오면 된다
```

### 1. 고정 IP

GitHub Actions 가 공인 IP 로 들어오므로 서버를 껐다 켜도 바뀌지 않게 고정 IP 를 붙인다. 서버에 붙어 있는 동안은 무료다.

```bash
aws lightsail allocate-static-ip --static-ip-name ff-backend-ip --profile ff-backend
aws lightsail attach-static-ip --static-ip-name ff-backend-ip --instance-name ff-backend --profile ff-backend
aws lightsail get-static-ip --static-ip-name ff-backend-ip --profile ff-backend --query staticIp.ipAddress --output text
```

### 2. 배포 키

배포 작업이 서버에 들어갈 때 쓰는 키를 따로 만들고, 공개 키를 서버의 `~/.ssh/authorized_keys` 에 넣는다.

```bash
ssh-keygen -t ed25519 -N "" -C familyfitness-deploy -f ~/.ssh/familyfitness-deploy
cat ~/.ssh/familyfitness-deploy.pub
```

Lightsail 콘솔에서 `ff-backend` 의 브라우저 SSH 를 열고, 위에서 찍힌 한 줄을 넣는다.

```bash
echo '<familyfitness-deploy.pub 한 줄>' >> ~/.ssh/authorized_keys
```

### 3. 서버 설정하기

```bash
IP=<고정 IP>
scp -i ~/.ssh/familyfitness-deploy -r deploy ubuntu@$IP:/tmp/
ssh -i ~/.ssh/familyfitness-deploy ubuntu@$IP sudo bash /tmp/deploy/server-setup.sh
```

그다음 서버에서 `.env` 의 빈 칸을 채운다: `sudo -u ubuntu nano /opt/familyfitness/.env`

- `APP_FRONTEND_BASE_URL`: FE 주소(`https://d249o1o9c1vnhc.cloudfront.net`)
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`: FE 와 같은 구글 OAuth 클라이언트
- AI 서버가 사설 IP 에서 받게 되면 `APP_AI_BASE_URL=http://<ff-ai 사설 IP>:8000`

### 4. GitHub Secrets

저장소 Settings > Secrets and variables > Actions 에 넣는다.

| 이름 | 값 |
|---|---|
| `LIGHTSAIL_HOST` | 고정 IP |
| `LIGHTSAIL_SSH_KEY` | `~/.ssh/familyfitness-deploy` 파일 내용 전체(개인 키) |
| `LIGHTSAIL_KNOWN_HOSTS` | `ssh-keyscan -t ed25519 <고정 IP>` 가 찍은 줄 |

이미지는 GHCR 에 올린다. 작업의 `GITHUB_TOKEN` 으로 올리고 받아서 따로 넣을 토큰은 없다.
처음 올릴 때 403 이 나면 조직 Settings > Packages 에서 멤버가 패키지를 만들 수 있게 되어 있는지 본다.

### 5. 첫 배포와 FE 연결

Actions > backend-deploy > Run workflow. 첫 기동은 마이그레이션이 모두 돌아 1~2분 걸린다.
서버에서 `curl -s localhost:8080/actuator/health` 가 `{"status":"UP"...}` 이면 된다.
FE 서버의 `BACKEND_ORIGIN` 을 `http://<ff-backend 사설 IP>:8080` 으로 바꾼다(FE 쪽 작업).

## 평소

- develop 을 main 에 합치면 `backend/`, `deploy/` 폴더나 두 워크플로 파일이 바뀐 경우에만 배포한다. `docs/` 만 바뀌면 돌지 않는다.
- 배포 도중 app 이 다시 뜨는 동안(1분 안팎) 응답하지 않는다.
- 새 app 이 10분 안에 health UP 이 되지 않으면 작업이 실패하고 app 로그 끝 200줄을 찍는다. 이때 서버에는 새 이미지가 그대로 남아 있으니 아래 「되돌리기」로 앞 커밋을 띄운다.

## 서버에서 할 일

```bash
cd /opt/familyfitness
docker compose ps
docker compose logs -f app                                  # 로그. journald 에 30일 남는다
docker compose exec postgres psql -U familyfitness          # DB 보기
docker compose up -d                                        # .env 를 고친 뒤 다시 띄우기
free -m                                                     # 메모리와 스왑
```

### 되돌리기

```bash
cd /opt/familyfitness
bash deploy.sh ghcr.io/family-fitness/family-fitness-be:<되돌릴 커밋 해시 전체>
```

서버에 남아 있는 이미지(`docker image ls`, 쓰지 않은 지 7일이 넘으면 지운다)는 받지 않고 바로 띄운다. 없는 것은 GHCR 에서 받는데, 이미지가 비공개라 먼저 `docker login ghcr.io` 가 필요하다(`read:packages` 권한 토큰).
마이그레이션이 이미 돈 뒤라 DB 는 되돌아가지 않는다. 예전 app 이 새 스키마에서 `ddl-auto=validate` 로 멈추면 앞으로 고쳐서 다시 배포한다.

### 백업

- Lightsail 자동 스냅샷을 켠다. 서버 디스크 전체(DB 볼륨 포함)를 매일 찍는다. 스냅샷 저장 용량만큼 요금이 붙는다(GB 당 월 0.05달러).
  `aws lightsail enable-add-on --resource-name ff-backend --add-on-request "addOnType=AutoSnapshot,autoSnapshotAddOnRequest={snapshotTimeOfDay=18:00}" --profile ff-backend` (UTC 18시 = 한국 새벽 3시)
- DB 만 따로 받기: `docker compose exec -T postgres pg_dump -U familyfitness -Fc familyfitness > familyfitness-$(date +%F).dump`
