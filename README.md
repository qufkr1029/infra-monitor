# infra-monitor

Linux 호스트 전체 CPU·메모리와 루트 파일시스템(`/`)을 수집하는 초기 데모입니다.
백엔드·웹 화면·DB 저장은 아직 구현하지 않았으며, 10초마다 JSON 한 줄을 콘솔과 파일에 출력합니다.

## Jenkins 배포 준비

기존 boot 서비스처럼 Jenkins에서 이미지를 빌드하고, 배포 폴더에는 Compose 파일만 복사합니다.
파일명은 기존 서비스에 맞춰 `docker-compose.yml`을 사용합니다.

전제: Jenkins 작업은 A서버의 Docker 엔진을 사용해야 합니다. Jenkins가 컨테이너에서 실행된다면
기존 boot 작업처럼 A서버의 Docker 소켓과 Docker CLI·Compose를 사용할 수 있어야 합니다.
빌드와 배포 단계도 같은 Docker 엔진과 작업 공간을 사용해야 합니다.
Jenkins에서 보이는 배포 경로가 호스트 경로와 다른 마운트라면 파일도 호스트의 다른 위치에 저장될 수 있으므로 확인합니다.
다른 Jenkins 노드에서 실행하면 해당 노드를 모니터링하므로, 여러 노드가 있다면 `agent any`를 A서버 전용 라벨로 바꿉니다.

1. 이 프로젝트의 변경 파일을 GitHub `main` 브랜치에 커밋·푸시합니다.
2. Jenkins에 Pipeline 작업을 생성하고 다음을 설정합니다.
   - Definition: Pipeline script from SCM
   - SCM: Git
   - Repository URL: `https://github.com/qufkr1029/infra-monitor.git`
   - Branch Specifier: `*/main`
   - Script Path: `Jenkinsfile`
   - 비공개 저장소라면 Git 조회용 Credentials 선택
3. 기존 boot 작업에서 사용 중인 Generic Webhook Trigger 플러그인을 사용합니다.
   Jenkins Credentials에 Secret text를 만들고 ID를 `infra-monitor-webhook-token`으로 지정합니다.
   비밀 값은 boot와 다른 임의의 토큰을 사용하며 Git에 저장하지 않습니다.
4. Jenkins 작업을 처음 한 번 수동으로 빌드합니다. Jenkinsfile의 웹훅 트리거를 등록하고 최초 배포를 수행합니다.
5. GitHub 저장소의 Webhooks에 다음을 설정합니다.
   - Payload URL: `https://<Jenkins주소>/generic-webhook-trigger/invoke?token=<위 Secret text 값>`
   - Content type: `application/json`
   - 이벤트: Push events
   - Active 활성화
   - 위 토큰은 GitHub의 별도 Secret 입력란에 넣는 값이 아니라 Jenkins 트리거 인증 토큰입니다.

파이프라인은 이미지를 `infra-monitor-collector:latest`로 빌드하고,
`/home/ubuntu/docker/projects/infra-monitor`를 생성한 뒤 Compose 파일을 복사해 컨테이너를 교체합니다.
Jenkins 실행 계정에 해당 경로의 생성·쓰기 권한이 있어야 합니다. 권한이 없다면 호스트에서 미리 생성하고
기존 boot 배포 폴더와 같은 방식으로 권한을 설정합니다. 기존 boot 이미지·컨테이너·네트워크는 사용하지 않습니다.
이미지 정리는 공유 Docker 엔진의 다른 프로젝트에 영향을 줄 수 있어 파이프라인에 넣지 않았습니다.

## 배포 후 서버에서 확인

```bash
cd /home/ubuntu/docker/projects/infra-monitor
docker compose ps
docker compose logs -f --tail=10 collector
```

첫 지표는 시작 약 10초 후 출력됩니다. 로그 보기를 종료하려면 `Ctrl+C`를 누릅니다.
수집기는 백그라운드에서 계속 실행됩니다.

```bash
# 상태 확인
docker compose ps

# 중지 및 컨테이너 제거
docker compose down
```

## 설정

기본 수집 간격은 10초입니다. 배포 폴더에서 `.env`를 만들면 변경할 수 있습니다.
이 파일은 Jenkins가 덮어쓰지 않습니다.

```bash
cd /home/ubuntu/docker/projects/infra-monitor
echo 'COLLECTION_INTERVAL_SECONDS=10' > .env
# 원하는 간격으로 수정 후 적용
docker compose up -d
```

최초 Jenkins 배포 후에는 서버의 Compose 파일과 로컬 이미지로 재실행할 수 있습니다.
`docker compose restart`는 기존 컨테이너 재시작이며 새 이미지나 환경변수를 적용하지 않습니다.

## 수집 로그 파일

서버의 `/home/ubuntu/docker/projects/infra-monitor/collector_logs`에 저장합니다.

```text
collector_logs/
  2026-11-25-1.log
  2026-11-25-2.log
  2026-11-26-1.log
```

- 한국 시간(UTC+9) 기준 날짜가 바뀌면 새 날짜의 1번 파일을 사용합니다.
- 기본 파일당 5MiB(5,242,880바이트)이며, 다음 줄이 한도를 넘으면 순번을 증가시킵니다.
  한 줄 자체가 한도보다 크면 해당 줄은 하나의 파일에 온전히 기록합니다.
- 재시작·재배포 시 같은 날짜의 마지막 파일에 이어 쓰며, 가득 찼으면 다음 번호를 사용합니다.
- 파일 개수·보관 기간 제한과 자동 삭제는 없습니다. 삭제는 운영자가 직접 관리합니다.
- 수집 오류도 JSON 오류 기록으로 파일에 남깁니다. 폴더 권한·디스크 부족으로 기록할 수 없는 오류는 콘솔로 확인합니다.
- `.env`의 `LOG_MAX_BYTES`로 파일당 크기를 변경할 수 있습니다.

```bash
cd /home/ubuntu/docker/projects/infra-monitor
ls -lh collector_logs
# 실제 생성된 파일명으로 바꿔서 확인
tail -f collector_logs/2026-11-25-1.log
```

Jenkins는 최초 배포 시 호스트 로그 폴더를 준비하고 수집기 사용자(UID/GID 65534)에 소유권을 부여합니다.
서버에서 파일을 직접 삭제할 때 권한이 없다면 `sudo`를 사용합니다. 재배포 시 기존 로그는 삭제하지 않습니다.
Docker의 `json-file` 로그는 `docker compose logs`용 별도 사본이며, 이 사본만 기존 5MB·2개 제한을 유지합니다.

## Jenkins 없이 직접 실행할 경우

대상 Linux 서버에 전체 소스를 내려받은 뒤 실행합니다.
ARM64 서버에서는 해당 아키텍처 이미지를 자동으로 사용합니다.
Windows Docker Desktop은 A서버 지표 검증에 사용할 수 없습니다.

```bash
docker build -t infra-monitor-collector:latest ./collector
mkdir -p collector_logs
sudo chown 65534:65534 collector_logs
docker compose up -d
docker compose logs -f --tail=10 collector
```

## 수집 기준

- CPU: 호스트 `/proc/stat`의 전체 CPU 카운터를 구간별 비교합니다. idle과 iowait를 비사용 시간으로 계산하며, 전체 CPU 합산 기준 0~100%입니다.
- 메모리: 호스트 `/proc/meminfo`의 `MemTotal - MemAvailable`을 사용량으로 계산합니다.
- 파일시스템: 호스트 `/`를 `/host/root`에 마운트하고 `statvfs`로 용량을 조회합니다. 하위 폴더를 순회하지 않으며, 별도 파일시스템인 `/boot` 등은 집계하지 않습니다.
- 파일시스템 사용률: `사용량 / (사용량 + 가용량)`입니다. 예약 블록을 제외하며 `df`와 반올림 방식 때문에 소수점 차이가 날 수 있습니다.
- 용량은 바이트, 사용률은 퍼센트, 수집 시각은 UTC로 출력합니다. `host_id`는 호스트 `/etc/hostname`에서 읽습니다.

호스트 `/proc`와 `/`는 읽기 전용으로 마운트합니다. 호스트 PID·네트워크 모드나 privileged 권한은 사용하지 않습니다.
외부 포트와 CPU·메모리 제한은 설정하지 않았습니다. 수집 로그 파일은 자동 삭제하지 않습니다.
수집 오류는 표준 오류로 출력하고 다음 주기에 다시 시도합니다.

## A서버 검증

```bash
free -b
df -B1 /
vmstat 10
```

JSON의 메모리 전체·가용량을 `free -b`와, 파일시스템 용량을 `df -B1 /`와 비교합니다.
CPU는 `vmstat 10`의 첫 행(부팅 이후 평균)을 제외하고, 이후 행의 `100 - id - wa`와 비교합니다.
수집 시점이 달라 사용량은 조금 차이 날 수 있습니다.

기준 문서: [Linux /proc](https://www.kernel.org/doc/html/latest/filesystems/proc.html),
[Docker Compose 서비스 설정](https://docs.docker.com/reference/compose-file/services/).
