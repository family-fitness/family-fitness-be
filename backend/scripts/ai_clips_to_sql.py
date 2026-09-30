#!/usr/bin/env python3
"""family-fitness-ai 의 클립 릴리스 → Flyway 버전 마이그레이션(운동 영상 · 클립 적재).

입력(AI 저장소 안. --ref 를 주면 작업 트리가 아니라 그 커밋에서 읽는다):
- data/release/video_clips.csv  클립 한 줄 = 영상 안의 한 동작 구간(video_id, seq, name_on_video, phase_on_video, start_sec, end_sec)
- data/release/clip_labels.csv  동작 이름(name_on_video)마다 붙인 라벨(exercise_name, fitness_factor, phase, is_exercise, home_ok, quiet, needs_props, source)
- data/index/corpus_meta.csv    영상 제목 · 연령대 · 영상 단위 요인은 source=video 청크에만 있다(BOM 이 있어 utf-8-sig 로 읽는다)

규칙은 AI 의 src/family_fitness_ai/video/catalog.py clips() 와 같다.
- 라벨은 name_on_video 가 완전히 같은 줄을 붙인다. 라벨이 없으면 불리언은 모두 false 다.
- 단계는 영상 화면의 표시(phase_on_video) → 라벨 phase → 본운동 순으로 정한다.
- 제목은 exercise_name 이 있으면 그것, 없으면 name_on_video 다.
- 연령대는 코퍼스의 영상 청크 값이다.
- catalog.py 는 is_exercise=True 만 남기지만 여기서는 모든 행을 넣고 is_exercise 칸으로 구분한다.
  라벨이 뒤집혀도 clip_id 가 그대로 남게 하려는 것이다.

clip_id 는 `{video_id}-{start_sec}` 다. seq 는 AI 가 영상 안에서 다시 매기는 순번이라 클립을 다시 끊으면 밀린다.

exercise_videos 에는 자료에 있는 칸만 채운다. 영상 길이 · 강도 · 공간 · 소음 · 준비물은 영상 단위 자료가 없어 null 이다.

낸 SQL 은 이미 적재된 DB 위에 다시 돌려도 된다(첫 적재와 다음 릴리스가 같은 문장을 쓴다).
- 클립: 같은 clip_id 가 있으면 이번 판 값으로 고치고 active=true 로 켠다. 없으면 넣는다.
- 이번 판에 없는 클립은 지우지 않고 active=false 로 끈다. 찜처럼 clip_id 를 가리키는 행을 살리려는 것이다.
  공단 영상 클립(kspo_videos_to_sql.py 가 싣는다)은 여기서 끄지 않는다 — 이 스크립트는 유튜브 클립만 다룬다.
- 영상: 이 스크립트가 넣은 행(labeled_by='AI')만 이번 판 값으로 고친다. 없으면 넣는다. 시드 같은 다른 출처 행은 건드리지 않는다.
문장은 PostgreSQL 과 H2 양쪽에서 도는 update … where 와 insert … select … where not exists 만 쓴다.

사용(backend 폴더에서. AI 작업 트리가 다른 브랜치여도 되게 --ref <AI 커밋 · 브랜치> 로 커밋에서 읽는 것을 권한다):
  첫 적재(표 만들기 포함):
    python3 scripts/ai_clips_to_sql.py --create-table ../../family-fitness-ai \
        > src/main/resources/db/migration/V132__coaching_video_exercises.sql
  다음 릴리스(표는 이미 있다):
    python3 scripts/ai_clips_to_sql.py --ref <AI 커밋> ../../family-fitness-ai \
        > src/main/resources/db/migration/V<다음 번호>__coaching_clip_release_<AI 커밋>.sql
적용된 버전 마이그레이션은 고칠 수 없으니 V132 는 그대로 두고 새 V 파일을 만든다.
"""
import argparse
import csv
import io
import subprocess
import sys
from collections import Counter
from pathlib import Path

VIDEO_TITLE_PREFIX = "국민체력100 운동영상 · "
CHANNEL_NAME = "국민체력100"
CHANNEL_TYPE = "PUBLIC"
YOUTUBE_WATCH = "https://www.youtube.com/watch?v="

# coaching.domain.SessionPhase.fromKorean 과 같은 표. 모르는 값은 조용히 본운동으로 보내지 않고 멈춘다.
PHASES = {"준비운동": "WARMUP", "본운동": "MAIN", "정리운동": "COOLDOWN"}
# shared.domain.FitnessFactor 의 라벨 → 이름
FACTORS = {
    "심폐지구력": "CARDIO",
    "근력": "STRENGTH",
    "근지구력": "MUSCULAR_ENDURANCE",
    "유연성": "FLEXIBILITY",
    "민첩성": "AGILITY",
    "순발력": "POWER",
    "협응력": "COORDINATION",
    "평형성": "BALANCE",
}
# shared.domain.AgeGroup 의 라벨 → (이름, coaching.domain.AgeRange.of 의 만 나이 범위)
AGE_GROUPS = {
    "유아기": ("TODDLER", 0, 6),
    "유소년": ("YOUTH", 7, 12),
    "청소년": ("ADOLESCENT", 13, 18),
    "성인": ("ADULT", 19, 64),
    "어르신": ("SENIOR", 65, 120),
}
INPUTS = ("data/release/video_clips.csv", "data/release/clip_labels.csv", "data/index/corpus_meta.csv")


def fail(message: str) -> None:
    sys.exit(f"ai_clips_to_sql: {message}")


def git(ai_root: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(ai_root), *args], check=True, capture_output=True, text=True, encoding="utf-8"
    ).stdout.strip()


def sql_text(value: str | None) -> str:
    return "null" if value is None else "'" + value.replace("'", "''") + "'"


def sql_bool(value: bool) -> str:
    return "true" if value else "false"


def read_text(ai_root: Path, ref: str | None, path: str) -> str:
    """ref 가 있으면 그 커밋의 파일, 없으면 작업 트리 파일. BOM 은 뗀다(corpus_meta.csv 에 있다)."""
    if ref:
        text = subprocess.run(
            ["git", "-C", str(ai_root), "show", f"{ref}:{path}"],
            check=True, capture_output=True, text=True, encoding="utf-8",
        ).stdout
    else:
        text = (ai_root / path).read_text(encoding="utf-8")
    return text.removeprefix("\ufeff")


def read_labels(text: str) -> dict[str, dict[str, str]]:
    labels: dict[str, dict[str, str]] = {}
    for row in csv.DictReader(io.StringIO(text, newline="")):
        labels[row["name_on_video"]] = row
    return labels


def read_videos(text: str) -> dict[str, dict[str, str]]:
    # 청크 본문이 기본 한도(128KB)를 넘을 수 있다. Windows 는 C long 이 32비트라 sys.maxsize 를 못 받는다.
    csv.field_size_limit(2**31 - 1)
    return {
        row["chunk_id"].split(":", 1)[1]: row
        for row in csv.DictReader(io.StringIO(text, newline=""))
        if row["source"] == "video"
    }


def phase_of(row: dict[str, str], label: dict[str, str]) -> str:
    korean = row["phase_on_video"] or label.get("phase") or "본운동"
    if korean not in PHASES:
        fail(f"모르는 단계 {korean!r} ({row['video_id']} seq {row['seq']})")
    return PHASES[korean]


def factor_of(korean: str) -> str | None:
    if not korean:
        return None
    if korean not in FACTORS:
        fail(f"모르는 체력 요인 {korean!r}")
    return FACTORS[korean]


def age_of(korean: str) -> tuple[str, int, int] | None:
    if not korean:
        return None
    if korean not in AGE_GROUPS:
        fail(f"모르는 연령대 {korean!r}")
    return AGE_GROUPS[korean]


def build_clips(clip_rows: list[dict[str, str]], labels: dict, videos: dict) -> list[dict]:
    clips = []
    for row in clip_rows:
        label = labels.get(row["name_on_video"], {})
        video = videos.get(row["video_id"])
        if video is None:
            fail(f"코퍼스에 영상 청크가 없다: {row['video_id']}")
        start, end = int(row["start_sec"]), int(row["end_sec"])
        if end <= start:
            fail(f"끝이 시작보다 앞선다: {row['video_id']} {start}~{end}")
        exercise_name = label.get("exercise_name") or None
        age = age_of(video.get("age_group") or "")
        clips.append({
            "clip_id": f"{row['video_id']}-{start}",
            "video_id": row["video_id"],
            "seq": int(row["seq"]),
            "name_on_video": row["name_on_video"],
            "exercise_name": exercise_name,
            "title": exercise_name or row["name_on_video"],
            "fitness_factor": factor_of(label.get("fitness_factor") or ""),
            "phase": phase_of(row, label),
            "start_sec": start,
            "end_sec": end,
            "home_ok": label.get("home_ok") == "True",
            "quiet": label.get("quiet") == "True",
            "needs_props": label.get("needs_props") == "True",
            "is_exercise": label.get("is_exercise") == "True",
            "age_group": age[0] if age else None,
            "source": label.get("source") or None,
        })
    duplicated = [key for key, n in Counter(c["clip_id"] for c in clips).items() if n > 1]
    if duplicated:
        fail(f"(video_id, start_sec) 가 겹친다: {duplicated[:5]}")
    return clips


def assignments(fields: dict[str, str]) -> str:
    return ", ".join(f"{column} = {value}" for column, value in fields.items())


def video_statements(video_id: str, chunk: dict[str, str], collected_at: str) -> str:
    """이 스크립트가 넣은 영상(labeled_by='AI')은 이번 판 값으로 고치고, 없으면 넣는다."""
    label = chunk["citation_label"]
    title = label[len(VIDEO_TITLE_PREFIX):] if label.startswith(VIDEO_TITLE_PREFIX) else label
    if chunk.get("citation_url") != YOUTUBE_WATCH + video_id:
        fail(f"코퍼스 주소가 YouTube watch 주소가 아니다: {video_id} {chunk.get('citation_url')}")
    age = age_of(chunk.get("age_group") or "")
    factors = [f for f in (chunk.get("fitness_factors") or "").split(";") if f]
    for factor in factors:
        factor_of(factor)
    key = sql_text(video_id)
    # 자료에서 오는 칸만 고친다. 길이 · 강도 같은 칸은 자료가 없어 넣을 때만 null 로 둔다.
    sourced = {
        "title": sql_text(title),
        "age_from": str(age[1]) if age else "null",
        "age_to": str(age[2]) if age else "null",
        "factors": sql_text(",".join(factors) or None),
        "collected_at": f"timestamp with time zone '{collected_at}'",
    }
    row = {
        "video_id": key,
        "title": sourced["title"],
        "channel_name": sql_text(CHANNEL_NAME),
        "channel_type": sql_text(CHANNEL_TYPE),
        "duration_sec": "null",
        "age_from": sourced["age_from"],
        "age_to": sourced["age_to"],
        "factors": sourced["factors"],
        "intensity": "null",
        "space": "null",
        "noise": "null",
        "equipment": "null",
        "labeled_by": sql_text("AI"),
        "label_model": "null",
        "collected_at": sourced["collected_at"],
    }
    return (
        f"update exercise_videos set {assignments(sourced)} where video_id = {key} and labeled_by = 'AI';\n"
        f"insert into exercise_videos ({', '.join(row)})\n"
        f"select {', '.join(row.values())}\n"
        f"where not exists (select 1 from exercise_videos where video_id = {key});"
    )


def clip_statements(clip: dict) -> str:
    """같은 clip_id 는 이번 판 값으로 고치고 다시 켠다. 없으면 넣는다."""
    key = sql_text(clip["clip_id"])
    # clip_id · video_id · start_sec 는 clip_id 를 이루는 값이라 고칠 일이 없다.
    changing = {
        "seq": str(clip["seq"]),
        "name_on_video": sql_text(clip["name_on_video"]),
        "exercise_name": sql_text(clip["exercise_name"]),
        "title": sql_text(clip["title"]),
        "fitness_factor": sql_text(clip["fitness_factor"]),
        "phase": sql_text(clip["phase"]),
        "end_sec": str(clip["end_sec"]),
        "home_ok": sql_bool(clip["home_ok"]),
        "quiet": sql_bool(clip["quiet"]),
        "needs_props": sql_bool(clip["needs_props"]),
        "is_exercise": sql_bool(clip["is_exercise"]),
        "age_group": sql_text(clip["age_group"]),
        "source": sql_text(clip["source"]),
        "active": "true",
    }
    row = {"clip_id": key, "video_id": sql_text(clip["video_id"]), "start_sec": str(clip["start_sec"]), **changing}
    return (
        f"update video_exercises set {assignments(changing)} where clip_id = {key};\n"
        f"insert into video_exercises ({', '.join(row)}) select {', '.join(row.values())}\n"
        f"where not exists (select 1 from video_exercises where clip_id = {key});"
    )


def deactivate_statement(clip_ids: list[str]) -> str:
    """이번 판에 없는 유튜브 클립은 지우지 않고 끈다.

    공단 영상 클립(kspo_videos_to_sql.py 가 싣는다, media_url 이 있는 영상)은 이 판에 없어도 끄지 않는다.
    media_url 칸은 V161 이 더했으니 그 뒤에 만드는 마이그레이션에서만 이 문장이 돈다(V132 는 이 조건 없이 만들었다).
    """
    per_line = 6
    lines = [
        "    " + ", ".join(sql_text(i) for i in clip_ids[start:start + per_line])
        for start in range(0, len(clip_ids), per_line)
    ]
    return (
        "update video_exercises set active = false where active = true\n"
        "  and video_id in (select video_id from exercise_videos where media_url is null)\n"
        "  and clip_id not in (\n" + ",\n".join(lines) + "\n);"
    )


CREATE_TABLE = """create table video_exercises (
    clip_id        varchar(48) not null,
    video_id       varchar(32) not null,
    seq            smallint    not null,
    name_on_video  varchar(60) not null,
    exercise_name  varchar(60),
    title          varchar(60) not null,
    fitness_factor varchar(20),
    phase          varchar(10) not null,
    start_sec      integer     not null,
    end_sec        integer     not null,
    home_ok        boolean     not null,
    quiet          boolean     not null,
    needs_props    boolean     not null,
    is_exercise    boolean     not null,
    age_group      varchar(12),
    source         varchar(10),
    active         boolean     not null default true,
    constraint pk_video_exercises primary key (clip_id),
    constraint fk_video_exercises_video foreign key (video_id) references exercise_videos (video_id),
    constraint uq_video_exercises_start unique (video_id, start_sec),
    constraint ck_video_exercises_range check (start_sec >= 0 and end_sec > start_sec),
    constraint ck_video_exercises_phase check (phase in ('WARMUP', 'MAIN', 'COOLDOWN')),
    constraint ck_video_exercises_factor check (fitness_factor is null or fitness_factor in
        ('CARDIO', 'STRENGTH', 'MUSCULAR_ENDURANCE', 'FLEXIBILITY', 'AGILITY', 'POWER', 'COORDINATION', 'BALANCE')),
    constraint ck_video_exercises_age_group check (age_group is null or age_group in
        ('TODDLER', 'YOUTH', 'ADOLESCENT', 'ADULT', 'SENIOR'))
);"""


def main(ai_root: Path, create_table: bool, ref: str | None) -> None:
    if ref is None:
        dirty = git(ai_root, "status", "--porcelain", "--", *INPUTS)
        if dirty:
            fail(f"AI 입력 파일에 커밋하지 않은 변경이 있다 — 커밋된 표로만 만든다(--ref 로 커밋을 주어도 된다):\n{dirty}")
    head = git(ai_root, "rev-parse", ref or "HEAD")
    release_commit, committed_at = git(ai_root, "log", "-1", "--format=%h %cI", head, "--", *INPUTS).split(" ", 1)
    # 영상의 collected_at = AI 가 이 자료를 커밋한 시각. seed 와 같은 'YYYY-MM-DD HH:MM:SS+09:00' 모양으로 쓴다.
    collected_at = committed_at.replace("T", " ")

    labels = read_labels(read_text(ai_root, ref, INPUTS[1]))
    videos = read_videos(read_text(ai_root, ref, INPUTS[2]))
    clip_rows = list(csv.DictReader(io.StringIO(read_text(ai_root, ref, INPUTS[0]), newline="")))
    clips = build_clips(clip_rows, labels, videos)
    video_ids = list(dict.fromkeys(c["video_id"] for c in clips))
    exercises = sum(1 for c in clips if c["is_exercise"])

    out = sys.stdout
    out.reconfigure(encoding="utf-8", newline="\n")
    command = "scripts/ai_clips_to_sql.py" + (" --create-table" if create_table else "")
    print("-- coaching: AI 운동 영상과 그 클립(한 동작 구간)을 이번 판으로 맞춘다.", file=out)
    print(f"-- {command} 가 생성한다. 손으로 고치지 말고, AI 릴리스가 바뀌면 새 V 파일로 다시 만든다.", file=out)
    print(f"-- 출처: family-fitness-ai 커밋 {head} (입력 파일 마지막 변경 {release_commit}, {collected_at})", file=out)
    print(f"--   {INPUTS[0]} {len(clip_rows)}행 · {INPUTS[1]} {len(labels)}행 · {INPUTS[2]} 영상 청크 {len(videos)}행", file=out)
    print(f"-- 이번 판: 영상 {len(video_ids)}편 · 클립 {len(clips)}개(그중 운동 {exercises}개, 운동 아님 {len(clips) - exercises}개).", file=out)
    print("-- 규칙은 AI video/catalog.py 와 같다: name_on_video 로 라벨 조인, 단계 = 화면 표시 → 라벨 → 본운동,", file=out)
    print("--   제목 = exercise_name → name_on_video, 연령대 = 코퍼스 영상 청크. 운동 아닌 클립도 넣고 is_exercise 로 구분한다.", file=out)
    print("-- clip_id = {video_id}-{start_sec}. 영상 길이 · 강도 · 공간 · 소음 · 준비물은 영상 단위 자료가 없어 null 이다.", file=out)
    print("-- 이미 적재된 DB 에 다시 돌려도 된다: 있는 clip_id 는 고치고 켜고, 없으면 넣고, 이번 판에 없는 클립은 끈다(active=false).", file=out)
    print("--   영상은 labeled_by='AI' 행만 고치고, 없으면 넣는다.", file=out)
    print("-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.", file=out)
    if create_table:
        print(CREATE_TABLE, file=out)
    for video_id in video_ids:
        print(video_statements(video_id, videos[video_id], collected_at), file=out)
    for clip in clips:
        print(clip_statements(clip), file=out)
    print(deactivate_statement([c["clip_id"] for c in clips]), file=out)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="family-fitness-ai 클립 릴리스 → Flyway 마이그레이션 SQL")
    parser.add_argument("ai_root", type=Path, help="family-fitness-ai 저장소 경로")
    parser.add_argument(
        "--create-table", action="store_true", help="video_exercises 표 정의도 낸다. 첫 적재(V132)에만 쓴다."
    )
    parser.add_argument(
        "--ref", default=None, help="읽을 AI 커밋(브랜치 이름도 된다). 주지 않으면 작업 트리 파일을 읽는다."
    )
    args = parser.parse_args()
    main(args.ai_root, args.create_table, args.ref)
