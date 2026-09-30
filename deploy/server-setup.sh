#!/usr/bin/env bash
# 새 Lightsail 우분투 서버를 BE 운영 서버로 한 번 설정한다. 여러 번 돌려도 결과가 같다.
# deploy 폴더를 통째로 서버에 올린 뒤 root 로 돌린다(deploy/README.md 「처음 한 번」):
#   scp -r deploy ubuntu@<서버>:/tmp/ && ssh ubuntu@<서버> sudo bash /tmp/deploy/server-setup.sh
#
# 하는 일
#   1. Docker 엔진과 compose 플러그인을 깔고, ubuntu 계정이 sudo 없이 docker 를 쓰게 한다(배포 작업이 ubuntu 로 들어온다)
#   2. 스왑 2GB 를 만든다. 1GB 서버라 메모리가 잠깐 넘쳐도 OOM 으로 죽지 않고 느려지게
#   3. 가상 서버에서 쓰지 않는 서비스(모뎀, 디스크 자동 마운트, 멀티패스, 펌웨어 갱신)를 꺼서 메모리 100MB 쯤을 돌려받는다
#   4. journald 가 로그를 30일만 남기게 한다(backend/README.md 「로그와 IP 보관」)
#   5. /opt/familyfitness 에 compose 파일을 두고, .env 가 없으면 예시 파일(.env.example)에서 만든다(DB 비밀번호와 JWT 키는 무작위, 사설 IP 는 서버에서 읽는다)
set -euo pipefail

SRC="$(cd "$(dirname "$0")" && pwd)"
APP_DIR=/opt/familyfitness
APP_USER=ubuntu

if [[ $EUID -ne 0 ]]; then
  echo "root 로 돌린다: sudo bash $0" >&2
  exit 1
fi

# 1. Docker
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
usermod -aG docker "$APP_USER"
systemctl enable --now docker

# 2. 스왑
if [[ ! -f /swapfile ]]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
fi
swapon --show=NAME --noheadings | grep -qx /swapfile || swapon /swapfile
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
# 메모리가 남아 있는 동안은 스왑으로 내보내지 않는다
echo 'vm.swappiness=10' > /etc/sysctl.d/99-familyfitness.conf
sysctl -q -p /etc/sysctl.d/99-familyfitness.conf

# 3. 쓰지 않는 서비스
for unit in ModemManager.service udisks2.service multipathd.service multipathd.socket fwupd.service fwupd-refresh.timer; do
  systemctl disable --now "$unit" >/dev/null 2>&1 || true
done

# 4. 로그 보관 30일
mkdir -p /etc/systemd/journald.conf.d
cat > /etc/systemd/journald.conf.d/familyfitness.conf <<'EOF'
[Journal]
Storage=persistent
MaxRetentionSec=30day
SystemMaxUse=1G
EOF
systemctl restart systemd-journald

# 5. 배포 폴더와 .env
install -d -o "$APP_USER" -g "$APP_USER" "$APP_DIR"
install -o "$APP_USER" -g "$APP_USER" -m 644 "$SRC/compose.yaml" "$APP_DIR/"
install -o "$APP_USER" -g "$APP_USER" -m 755 "$SRC/deploy.sh" "$APP_DIR/"

if [[ ! -f "$APP_DIR/.env" ]]; then
  # 기본 경로로 나가는 주소가 이 서버의 사설 IP 다(Lightsail 은 172.26.x.x)
  private_ip="$(ip -4 route get 1.1.1.1 | awk '{for (i = 1; i < NF; i++) if ($i == "src") print $(i + 1)}')"
  install -o "$APP_USER" -g "$APP_USER" -m 600 "$SRC/.env.example" "$APP_DIR/.env"
  sed -i \
    -e "s/^BE_PRIVATE_IP=$/BE_PRIVATE_IP=${private_ip}/" \
    -e "s/^POSTGRES_PASSWORD=$/POSTGRES_PASSWORD=$(openssl rand -hex 24)/" \
    -e "s/^APP_JWT_SECRET=$/APP_JWT_SECRET=$(openssl rand -hex 32)/" \
    "$APP_DIR/.env"
  echo ".env 를 만들었다(사설 IP ${private_ip}). 빈 칸(GOOGLE_*, APP_FRONTEND_BASE_URL)을 채운다: sudo -u $APP_USER nano $APP_DIR/.env"
else
  echo ".env 가 이미 있어 그대로 둔다"
fi

echo "서버 준비 끝. 첫 배포는 GitHub Actions 의 backend-deploy 를 돌린다"
