#!/usr/bin/env python3
"""family-fitness-ai 의 공단 영상 표 → Flyway 버전 마이그레이션(공단 영상 · 클립 적재).

공단 「국민체력100 동영상 정보」 오픈API(공공데이터포털 15108846) 영상은 한 편에 운동 하나(1~2분)라
자르지 않는다. 영상 한 편 = 클립 하나다. 유튜브 영상 · 클립(ai_clips_to_sql.py)은 그대로 두고 이 영상을 더한다.

입력(AI 저장소의 커밋에서 읽는다 — 작업 트리 파일이 아니다):
- data/release/kspo_videos.csv        영상 한 편에 한 줄(video_id, title, age_group, duration_sec, media_url, thumbnail_url, tool, place …)
- data/release/kspo_video_labels.csv  영상 id 마다 처방 어휘 · 체력요인 · 단계 · 조건(is_exercise, home_ok, quiet, needs_props) · 라벨 방법

규칙은 AI 의 src/family_fitness_ai/video/catalog.py _kspo_clips() 와 같다.
- 클립: clip_id = {video_id}-0, seq 1, 0초 ~ duration_sec. 연령대는 영상 표의 age_group(공통은 AI 가 이미 성인으로 세웠다).
- 제목은 exercise_name → name_on_video → 영상 제목 순이다. 단계는 라벨 phase(없으면 본운동)다.
- AI 는 운동이 아니라고 라벨된 것과 물속 영상(장소가 수영장뿐이거나 제목에 수영 · 아쿠아 · 물속 · 영법 이름)을 후보에서 뺀다.
  여기서는 행은 모두 넣고 그 둘을 is_exercise=false 로 둔다 — 운동 찾기 · 대체 편성이 is_exercise 로 후보를 가른다.
- 영상: channel_name '국민체력100 동영상 정보', channel_type 'PUBLIC', media_url(mp4) · thumbnail_url(첫 장면 이미지),
  duration_sec, 연령 범위, 요인(라벨의 체력요인), 준비물(API 도구 칸), 소음(라벨 quiet → QUIET), 공간(라벨 home_ok → SMALL_ROOM).

낸 SQL 은 이미 적재된 DB 위에 다시 돌려도 된다(ai_clips_to_sql.py 와 같은 방식).
- 클립: 같은 clip_id 가 있으면 이번 판 값으로 고치고 켠다. 없으면 넣는다. 이번 판에 없는 공단 클립(media_url 이 있는 영상의
  클립)은 지우지 않고 끈다. 유튜브 클립은 건드리지 않는다.
- 영상: 이 스크립트가 넣은 행(labeled_by='AI')만 고치고, 없으면 넣는다.
문장은 PostgreSQL 과 H2 양쪽에서 도는 alter table … add column · update … where · insert … select … where not exists 만 쓴다.

사용(backend 폴더에서, 파이썬 3.10+):
  첫 적재(media_url · thumbnail_url 칸 만들기 포함):
    python scripts/kspo_videos_to_sql.py --add-columns --ref <AI 커밋> ../../family-fitness-ai \
        > src/main/resources/db/migration/V161__coaching_kspo_videos.sql
  다음 판(칸은 이미 있다):
    python scripts/kspo_videos_to_sql.py --ref <AI 커밋> ../../family-fitness-ai \
        > src/main/resources/db/migration/V<다음 번호>__coaching_kspo_release_<AI 커밋>.sql
적용된 버전 마이그레이션은 고칠 수 없으니 새 V 파일을 만든다.
"""
import argparse
import csv
import io
import re
import sys
from collections import Counter
from pathlib import Path

from ai_clips_to_sql import AGE_GROUPS, FACTORS, PHASES, assignments, fail, git, sql_bool, sql_text

CHANNEL_NAME = "국민체력100 동영상 정보"
CHANNEL_TYPE = "PUBLIC"
MEDIA_PREFIX = "https://openapi.kspo.or.kr/web/video/"
INPUTS = ("data/release/kspo_videos.csv", "data/release/kspo_video_labels.csv")

# AI catalog.py in_water 와 같은 기준. 집이나 동네에서 가족이 할 수 없는 물속 영상이다.
WATER_PLACES = frozenset({"수영장"})
WATER_TITLE = re.compile(r"수영|아쿠아|물속|자유형|배영|평영|접영")

ADD_COLUMNS = """alter table exercise_videos add column media_url varchar(300);
alter table exercise_videos add column thumbnail_url varchar(300);"""


def read_csv(ai_root: Path, ref: str, path: str) -> list[dict[str, str]]:
    text = git(ai_root, "show", f"{ref}:{path}")
    return list(csv.DictReader(io.StringIO(text)))


def in_water(video: dict[str, str]) -> bool:
    places = {piece.strip() for piece in re.split(r"[·/]", video["place"]) if piece.strip()}
    return (bool(places) and places <= WATER_PLACES) or bool(WATER_TITLE.search(video["title"]))


def factor_of(korean: str) -> str | None:
    if not korean:
        return None
    if korean not in FACTORS:
        fail(f"모르는 체력 요인 {korean!r}")
    return FACTORS[korean]


def age_of(video: dict[str, str]) -> tuple[str, int, int]:
    korean = video["age_group"]
    if korean not in AGE_GROUPS:
        fail(f"모르는 연령대 {korean!r} ({video['video_id']})")
    return AGE_GROUPS[korean]


def phase_of(label: dict[str, str]) -> str:
    korean = label.get("phase") or "본운동"
    if korean not in PHASES:
        fail(f"모르는 단계 {korean!r} ({label['video_id']})")
    return PHASES[korean]


def build(videos: list[dict[str, str]], labels: list[dict[str, str]]) -> list[dict]:
    by_id = {video["video_id"]: video for video in videos}
    duplicated = [key for key, n in Counter(v["video_id"] for v in videos).items() if n > 1]
    if duplicated:
        fail(f"영상 id 가 겹친다: {duplicated[:5]}")
    label_by_id = {label["video_id"]: label for label in labels}
    missing = sorted(set(by_id) - set(label_by_id))
    if missing:
        fail(f"라벨이 없는 영상: {missing[:5]}")
    out = []
    for video_id, video in by_id.items():
        label = label_by_id[video_id]
        duration = int(video["duration_sec"])
        if duration <= 0:
            fail(f"길이가 0초 이하다: {video_id}")
        if not video["media_url"].startswith(MEDIA_PREFIX):
            fail(f"공단 mp4 주소가 아니다: {video_id} {video['media_url']}")
        if not video["thumbnail_url"].startswith("https://"):
            fail(f"썸네일 주소가 https 가 아니다: {video_id} {video['thumbnail_url']}")
        exercise_name = label.get("exercise_name") or None
        name_on_video = label.get("name_on_video") or video["title"]
        factor = label.get("fitness_factor") or ""
        age = age_of(video)
        water = in_water(video)
        out.append({
            "video_id": video_id,
            "video_title": video["title"],
            "duration_sec": duration,
            "age": age,
            "factor_label": factor,
            "factor": factor_of(factor),
            "tool": video["tool"] or None,
            "media_url": video["media_url"],
            "thumbnail_url": video["thumbnail_url"],
            "clip_id": f"{video_id}-0",
            "name_on_video": name_on_video,
            "exercise_name": exercise_name,
            "title": exercise_name or name_on_video,
            "phase": phase_of(label),
            "home_ok": label.get("home_ok") == "True",
            "quiet": label.get("quiet") == "True",
            "needs_props": label.get("needs_props") == "True",
            "is_exercise": label.get("is_exercise") == "True" and not water,
            "water": water,
            "source": label.get("source") or None,
        })
    for row in out:
        for column in ("name_on_video", "title"):
            if len(row[column]) > 60:
                fail(f"{column} 이 60자를 넘는다: {row['video_id']}")
    return out


def video_statements(row: dict, collected_at: str) -> str:
    key = sql_text(row["video_id"])
    sourced = {
        "title": sql_text(row["video_title"]),
        "duration_sec": str(row["duration_sec"]),
        "age_from": str(row["age"][1]),
        "age_to": str(row["age"][2]),
        "factors": sql_text(row["factor_label"] or None),
        "space": sql_text("SMALL_ROOM" if row["home_ok"] else None),
        "noise": sql_text("QUIET" if row["quiet"] else None),
        "equipment": sql_text(row["tool"]),
        "media_url": sql_text(row["media_url"]),
        "thumbnail_url": sql_text(row["thumbnail_url"]),
        "collected_at": f"timestamp with time zone '{collected_at}'",
    }
    full = {
        "video_id": key,
        "title": sourced["title"],
        "channel_name": sql_text(CHANNEL_NAME),
        "channel_type": sql_text(CHANNEL_TYPE),
        "duration_sec": sourced["duration_sec"],
        "age_from": sourced["age_from"],
        "age_to": sourced["age_to"],
        "factors": sourced["factors"],
        "intensity": "null",
        "space": sourced["space"],
        "noise": sourced["noise"],
        "equipment": sourced["equipment"],
        "labeled_by": sql_text("AI"),
        "label_model": "null",
        "media_url": sourced["media_url"],
        "thumbnail_url": sourced["thumbnail_url"],
        "collected_at": sourced["collected_at"],
    }
    return (
        f"update exercise_videos set {assignments(sourced)} where video_id = {key} and labeled_by = 'AI';\n"
        f"insert into exercise_videos ({', '.join(full)})\n"
        f"select {', '.join(full.values())}\n"
        f"where not exists (select 1 from exercise_videos where video_id = {key});"
    )


def clip_statements(row: dict) -> str:
    key = sql_text(row["clip_id"])
    changing = {
        "seq": "1",
        "name_on_video": sql_text(row["name_on_video"]),
        "exercise_name": sql_text(row["exercise_name"]),
        "title": sql_text(row["title"]),
        "fitness_factor": sql_text(row["factor"]),
        "phase": sql_text(row["phase"]),
        "end_sec": str(row["duration_sec"]),
        "home_ok": sql_bool(row["home_ok"]),
        "quiet": sql_bool(row["quiet"]),
        "needs_props": sql_bool(row["needs_props"]),
        "is_exercise": sql_bool(row["is_exercise"]),
        "age_group": sql_text(row["age"][0]),
        "source": sql_text(row["source"]),
        "active": "true",
    }
    full = {"clip_id": key, "video_id": sql_text(row["video_id"]), "start_sec": "0", **changing}
    return (
        f"update video_exercises set {assignments(changing)} where clip_id = {key};\n"
        f"insert into video_exercises ({', '.join(full)}) select {', '.join(full.values())}\n"
        f"where not exists (select 1 from video_exercises where clip_id = {key});"
    )


def deactivate_statement(clip_ids: list[str]) -> str:
    """이번 판에 없는 공단 클립은 끈다. 유튜브 클립(media_url 이 없는 영상)은 건드리지 않는다."""
    per_line = 6
    lines = [
        "    " + ", ".join(sql_text(i) for i in clip_ids[start:start + per_line])
        for start in range(0, len(clip_ids), per_line)
    ]
    return (
        "update video_exercises set active = false where active = true\n"
        "  and video_id in (select video_id from exercise_videos where media_url is not null)\n"
        "  and clip_id not in (\n" + ",\n".join(lines) + "\n);"
    )


def main(ai_root: Path, ref: str, add_columns: bool) -> None:
    commit = git(ai_root, "rev-parse", ref)
    input_commit, committed_at = git(ai_root, "log", "-1", "--format=%h %cI", commit, "--", *INPUTS).split(" ", 1)
    collected_at = committed_at.replace("T", " ")
    videos = read_csv(ai_root, commit, INPUTS[0])
    labels = read_csv(ai_root, commit, INPUTS[1])
    rows = build(videos, labels)

    ages = Counter(row["age"][0] for row in rows)
    candidates = Counter(row["age"][0] for row in rows if row["is_exercise"])
    not_exercise = sum(1 for row in rows if not row["is_exercise"] and not row["water"])
    water = sum(1 for row in rows if row["water"])
    order = [code for code, _, _ in AGE_GROUPS.values()]

    def by_age(counter: Counter) -> str:
        return " · ".join(f"{code} {counter[code]}" for code in order if counter[code])

    out = sys.stdout
    out.reconfigure(encoding="utf-8", newline="\n")
    command = "scripts/kspo_videos_to_sql.py" + (" --add-columns" if add_columns else "")
    print("-- coaching: 공단 「국민체력100 동영상 정보」 오픈API(15108846) 영상을 영상 · 클립 표에 더한다.", file=out)
    print(f"-- {command} --ref {commit[:7]} 가 생성한다. 손으로 고치지 말고, AI 표가 바뀌면 새 V 파일로 다시 만든다.", file=out)
    print(f"-- 출처: family-fitness-ai 커밋 {commit} (입력 파일 마지막 변경 {input_commit}, {collected_at})", file=out)
    print(f"--   {INPUTS[0]} {len(videos)}행 · {INPUTS[1]} {len(labels)}행", file=out)
    print(f"-- 이번 판: 영상 {len(rows)}편 = 클립 {len(rows)}개({by_age(ages)}).", file=out)
    print(f"--   그중 운동 후보 {sum(candidates.values())}개({by_age(candidates)}),", file=out)
    print(f"--   운동 아님 라벨 {not_exercise}개 · 물속 영상 {water}개는 is_exercise=false 로 싣는다(AI catalog.py 가 후보에서 빼는 것과 같다).", file=out)
    print("-- 영상 한 편 = 클립 하나: clip_id = {video_id}-0, seq 1, 0초 ~ duration_sec. 공통 영상은 AI 가 성인으로 세웠다.", file=out)
    print("-- 영상은 media_url(mp4) · thumbnail_url 로 튼다. 유튜브 영상은 두 칸이 null 이다.", file=out)
    print("-- 이미 적재된 DB 에 다시 돌려도 된다: 있는 clip_id 는 고치고 켜고, 없으면 넣고, 이번 판에 없는 공단 클립은 끈다.", file=out)
    print("--   영상은 labeled_by='AI' 행만 고치고, 없으면 넣는다. 유튜브 영상 · 클립은 건드리지 않는다.", file=out)
    print("-- PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.", file=out)
    if add_columns:
        print(ADD_COLUMNS, file=out)
    for row in rows:
        print(video_statements(row, collected_at), file=out)
    for row in rows:
        print(clip_statements(row), file=out)
    print(deactivate_statement([row["clip_id"] for row in rows]), file=out)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="family-fitness-ai 공단 영상 표 → Flyway 마이그레이션 SQL")
    parser.add_argument("ai_root", type=Path, help="family-fitness-ai 저장소 경로")
    parser.add_argument("--ref", default="HEAD", help="읽을 AI 커밋(브랜치 이름도 된다). 기본 HEAD")
    parser.add_argument(
        "--add-columns", action="store_true", help="exercise_videos 에 media_url · thumbnail_url 칸도 더한다. 첫 적재에만 쓴다."
    )
    args = parser.parse_args()
    main(args.ai_root, args.ref, args.add_columns)
