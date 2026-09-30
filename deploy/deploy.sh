#!/usr/bin/env bash
# 서버에서 app 을 새 이미지로 바꿔 띄운다. 배포 작업(backend-deploy)이 ssh 로 부른다.
#   deploy.sh ghcr.io/family-fitness/family-fitness-be:<커밋>
# ghcr.io 로그인은 부르는 쪽이 먼저 해 둔다.
#
# 되돌릴 때도 이 스크립트를 쓴다. 서버에서 예전 커밋의 이미지를 주면 된다(deploy/README.md 「되돌리기」).
set -euo pipefail

image="${1:?이미지를 준다: deploy.sh ghcr.io/...:<커밋>}"
cd "$(dirname "$0")"

# 서버에서 docker compose 를 손으로 다시 띄워도 지금 이미지가 뜨게 .env 에 적어 둔다
if grep -q '^BACKEND_IMAGE=' .env; then
  sed -i "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=${image}|" .env
else
  echo "BACKEND_IMAGE=${image}" >> .env
fi

# 서버에 이미 있는 이미지(되돌릴 때)는 받지 않는다. 받으려 하면 GHCR 로그인 없이 멈춘다
docker image inspect "$image" >/dev/null 2>&1 || docker compose pull app
docker compose up -d --remove-orphans

# 새 app 이 health UP(200)을 돌려줄 때까지 기다린다. 첫 배포는 마이그레이션(V1 ~ 끝, SQL 11MB)이 모두 돌아 몇 분 걸린다
for _ in $(seq 1 120); do
  # 뜨는 동안에는 연결이 끊겨 오류가 나므로 오류 글은 찍지 않는다
  if curl -fs -o /dev/null http://127.0.0.1:8080/actuator/health; then
    echo "배포 끝: ${image}"
    # 7일 넘게 쓰지 않은 이미지를 지운다. 최근 것은 되돌릴 때 쓰려고 남긴다
    docker image prune -af --filter "until=168h" >/dev/null
    exit 0
  fi
  sleep 5
done

echo "app 이 10분 안에 뜨지 않았다. 마지막 로그:" >&2
docker compose logs --tail 200 app >&2
exit 1
