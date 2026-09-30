#!/usr/bin/env python3
"""family-fitness-ai 의 공단 영상 표 → Flyway 버전 마이그레이션(공단 영상 · 클립 적재).

공단 「국민체력100 동영상 정보」 오픈API(공공데이터포털 15108846) 영상은 한 편에 운동 하나(1~2분)라
자르지 않는다. 영상 한 편 = 클립 하나다. 유튜브 영상 · 클립(ai_clips_to_sql.py)은 그대로 두고 이 영상을 더한다.

입력(AI 저장소의 커밋에서 읽는다 — 작업 트리 파일이 아니다):
- data/release/kspo_videos.csv  영상 한 편이 연령대 · 요인 · 단계마다 한 줄이다. 라벨이 이 파일 하나에 다 있다.
  칸: video_id, seq, name_on_video, phase_on_video, start_sec, end_sec, duration_sec, exercise_name, fitness_factor, phase,
  is_exercise, home_ok, quiet, needs_props, source, score, age_group, level_lo, level_hi, sets, reps, hold, url, citation_label
  「공통」 영상은 청소년 · 성인 두 줄로, 요인이나 단계가 둘인 영상은 그만큼 줄이 더 있다.
V161 ~ V164 는 입력이 두 파일(kspo_videos.csv 영상 한 편에 한 줄 + kspo_video_labels.csv)이던 때 이 스크립트의 앞 버전으로
만들었다(git log 로 본다). 옛 형식 표를 주면 멈춘다.

규칙은 AI 의 src/family_fitness_ai/video/catalog.py _kspo() 와 같다. AI 는 표 한 줄을 클립 후보 하나로 본다.
- BE 클립 표(video_exercises)는 (video_id, start_sec) 가 겹칠 수 없어 영상 한 편에 클립 하나다: clip_id = {video_id}-0, seq 1,
  0초 ~ duration_sec. 클립 행의 연령대 · 요인 · 단계 칸에는 그 영상의 첫 줄 값을 둔다.
- 표의 줄은 모두 video_exercise_labels 에 (clip_id, 차례, 연령대, 요인, 단계)로 싣는다. 클립 목록을 읽을 때 이 줄마다 후보 하나로
  펼친다(ExerciseClipPersistenceAdapter) — 「공통」 영상은 청소년에게도 성인에게도, 준비 · 정리 둘인 스트레칭은 두 단계 모두에 나온다.
- 제목은 exercise_name → name_on_video 순이다(AI Clip.title). 단계는 phase_on_video → phase → 본운동 순이다.
- is_exercise 는 표 값이다. AI 는 운동 아님 · 물속 영상을 표를 만들 때 이미 뺐다.
- 영상: 표에 있는 칸만 고친다 — 길이, 연령 범위(줄들의 연령대를 모두 덮는 범위), 요인(줄들의 요인, 쉼표로), 소음(quiet → QUIET),
  공간(home_ok → SMALL_ROOM), mp4 주소(url), 인용 이름(citation_label). 표에 없는 영상 제목 · 첫 장면 주소 · 준비물은 이미 실린
  값을 그대로 둔다. 새로 들어오는 영상만 제목을 클립 제목으로 넣고 첫 장면 주소 · 준비물은 비운다.

낸 SQL 은 이미 적재된 DB 위에 다시 돌려도 된다(ai_clips_to_sql.py 와 같은 방식).
- 클립: 같은 clip_id 가 있으면 이번 표 값으로 고치고 켠다. 없으면 넣는다. 이번 표에 없는 공단 클립(media_url 이 있는 영상의
  클립)은 지우지 않고 끈다. 유튜브 클립은 건드리지 않는다.
- 연령대 · 요인 · 단계 줄: 공단 클립의 줄을 모두 지우고 이번 표 줄을 넣는다.
- 영상: 이 스크립트가 넣은 행(labeled_by='AI')만 고치고, 없으면 넣는다.
문장은 PostgreSQL 과 H2 양쪽에서 도는 alter table · create table · update … where · delete … where · insert … values ·
insert … select … where not exists 만 쓴다.

사용(backend 폴더에서, 파이썬 3.10+):
  이 형식 첫 적재(인용 이름 칸 · 연령대 · 요인 · 단계 줄 표 만들기 포함):
    python scripts/kspo_videos_to_sql.py --add-labels --ref <AI 커밋> ../../family-fitness-ai \
        > src/main/resources/db/migration/V165__coaching_kspo_release_<AI 커밋>.sql
  다음 표(칸과 표는 이미 있다):
    python scripts/kspo_videos_to_sql.py --ref <AI 커밋> ../../family-fitness-ai \
        --note "앞 표에서 바뀐 것 한 줄" > src/main/resources/db/migration/V<다음 번호>__coaching_kspo_release_<AI 커밋>.sql
적용된 버전 마이그레이션은 고칠 수 없으니 새 V 파일을 만든다.
"""
import argparse
import csv
import io
import sys
from collections import Counter
from pathlib import Path

from ai_clips_to_sql import AGE_GROUPS, FACTORS, PHASES, assignments, fail, git, sql_bool, sql_text

CHANNEL_NAME = "국민체력100 동영상 정보"
CHANNEL_TYPE = "PUBLIC"
MEDIA_PREFIX = "https://openapi.kspo.or.kr/web/video/"
INPUT = "data/release/kspo_videos.csv"
COLUMNS = (
    "video_id", "seq", "name_on_video", "phase_on_video", "start_sec", "end_sec", "duration_sec", "exercise_name",
    "fitness_factor", "phase", "is_exercise", "home_ok", "quiet", "needs_props", "source", "score", "age_group",
    "level_lo", "level_hi", "sets", "reps", "hold", "url", "citation_label",
)
# 한 영상의 줄끼리 같아야 하는 칸. 다른 칸(연령대 · 요인 · 단계)만 줄마다 다르다.
PER_VIDEO = (
    "seq", "name_on_video", "phase_on_video", "start_sec", "end_sec", "duration_sec", "exercise_name", "is_exercise",
    "home_ok", "quiet", "needs_props", "source", "url", "citation_label",
)

ADD_LABELS = """alter table exercise_videos add column citation_label varchar(120);
create table video_exercise_labels (
    clip_id        varchar(48) not null,
    seq            smallint    not null,
    age_group      varchar(12) not null,
    fitness_factor varchar(20),
    phase          varchar(10) not null,
    constraint pk_video_exercise_labels primary key (clip_id, seq),
    constraint fk_video_exercise_labels_clip foreign key (clip_id) references video_exercises (clip_id),
    constraint ck_video_exercise_labels_phase check (phase in ('WARMUP', 'MAIN', 'COOLDOWN')),
    constraint ck_video_exercise_labels_factor check (fitness_factor is null or fitness_factor in
        ('CARDIO', 'STRENGTH', 'MUSCULAR_ENDURANCE', 'FLEXIBILITY', 'AGILITY', 'POWER', 'COORDINATION', 'BALANCE')),
    constraint ck_video_exercise_labels_age_group check (age_group in
        ('TODDLER', 'YOUTH', 'ADOLESCENT', 'ADULT', 'SENIOR'))
);"""


def read_rows(ai_root: Path, ref: str) -> list[dict[str, str]]:
    text = git(ai_root, "show", f"{ref}:{INPUT}")
    reader = csv.DictReader(io.StringIO(text))
    missing = [column for column in COLUMNS if column not in (reader.fieldnames or [])]
    if missing:
        fail(f"{INPUT} 에 칸이 없다: {missing} — 두 파일(kspo_video_labels.csv)이던 옛 형식은 이 스크립트의 앞 버전으로 만든다")
    return list(reader)


def factor_of(korean: str) -> str | None:
    if not korean:
        return None
    if korean not in FACTORS:
        fail(f"모르는 체력 요인 {korean!r}")
    return FACTORS[korean]


def age_of(korean: str, video_id: str) -> tuple[str, int, int]:
    if korean not in AGE_GROUPS:
        fail(f"모르는 연령대 {korean!r} ({video_id})")
    return AGE_GROUPS[korean]


def phase_of(row: dict[str, str]) -> str:
    korean = row["phase_on_video"] or row["phase"] or "본운동"
    if korean not in PHASES:
        fail(f"모르는 단계 {korean!r} ({row['video_id']})")
    return PHASES[korean]


def build(rows: list[dict[str, str]]) -> list[dict]:
    by_id: dict[str, list[dict[str, str]]] = {}
    for row in rows:
        by_id.setdefault(row["video_id"], []).append(row)
    out = []
    for video_id, group in by_id.items():
        first = group[0]
        for column in PER_VIDEO:
            values = {row[column] for row in group}
            if len(values) > 1:
                fail(f"한 영상의 줄끼리 {column} 가 다르다: {video_id} {sorted(values)}")
        start, end, duration = int(first["start_sec"]), int(first["end_sec"]), int(first["duration_sec"])
        if start != 0 or end != duration or duration <= 0:
            fail(f"공단 영상은 0초부터 영상 끝까지 한 클립이다: {video_id} {start}~{end} (길이 {duration})")
        if not first["url"].startswith(MEDIA_PREFIX):
            fail(f"공단 mp4 주소가 아니다: {video_id} {first['url']}")
        labels = []
        for row in group:
            label = (age_of(row["age_group"], video_id)[0], factor_of(row["fitness_factor"]), phase_of(row))
            if label in labels:
                fail(f"같은 (연령대, 요인, 단계) 줄이 둘이다: {video_id} {label}")
            labels.append(label)
        ages = [age_of(row["age_group"], video_id) for row in group]
        factor_labels = list(dict.fromkeys(row["fitness_factor"] for row in group if row["fitness_factor"]))
        exercise_name = first["exercise_name"] or None
        title = exercise_name or first["name_on_video"]
        out.append({
            "video_id": video_id,
            "duration_sec": duration,
            "age_from": min(age[1] for age in ages),
            "age_to": max(age[2] for age in ages),
            "factors": ",".join(factor_labels) or None,
            "media_url": first["url"],
            "citation_label": first["citation_label"] or None,
            "clip_id": f"{video_id}-0",
            "name_on_video": first["name_on_video"],
            "exercise_name": exercise_name,
            "title": title,
            "labels": labels,
            "home_ok": first["home_ok"] == "True",
            "quiet": first["quiet"] == "True",
            "needs_props": first["needs_props"] == "True",
            "is_exercise": first["is_exercise"] == "True",
            "source": first["source"] or None,
        })
    for row in out:
        for column in ("name_on_video", "title"):
            if not row[column] or len(row[column]) > 60:
                fail(f"{column} 이 비었거나 60자를 넘는다: {row['video_id']}")
        if row["citation_label"] and len(row["citation_label"]) > 120:
            fail(f"citation_label 이 120자를 넘는다: {row['video_id']}")
    return out


def video_statements(row: dict, collected_at: str) -> str:
    key = sql_text(row["video_id"])
    sourced = {
        "duration_sec": str(row["duration_sec"]),
        "age_from": str(row["age_from"]),
        "age_to": str(row["age_to"]),
        "factors": sql_text(row["factors"]),
        "space": sql_text("SMALL_ROOM" if row["home_ok"] else None),
        "noise": sql_text("QUIET" if row["quiet"] else None),
        "media_url": sql_text(row["media_url"]),
        "citation_label": sql_text(row["citation_label"]),
        "collected_at": f"timestamp with time zone '{collected_at}'",
    }
    full = {
        "video_id": key,
        "title": sql_text(row["title"]),
        "channel_name": sql_text(CHANNEL_NAME),
        "channel_type": sql_text(CHANNEL_TYPE),
        "duration_sec": sourced["duration_sec"],
        "age_from": sourced["age_from"],
        "age_to": sourced["age_to"],
        "factors": sourced["factors"],
        "intensity": "null",
        "space": sourced["space"],
        "noise": sourced["noise"],
        "equipment": "null",
        "labeled_by": sql_text("AI"),
        "label_model": "null",
        "media_url": sourced["media_url"],
        "thumbnail_url": "null",
        "citation_label": sourced["citation_label"],
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
    age_group, factor, phase = row["labels"][0]
    changing = {
        "seq": "1",
        "name_on_video": sql_text(row["name_on_video"]),
        "exercise_name": sql_text(row["exercise_name"]),
        "title": sql_text(row["title"]),
        "fitness_factor": sql_text(factor),
        "phase": sql_text(phase),
        "end_sec": str(row["duration_sec"]),
        "home_ok": sql_bool(row["home_ok"]),
        "quiet": sql_bool(row["quiet"]),
        "needs_props": sql_bool(row["needs_props"]),
        "is_exercise": sql_bool(row["is_exercise"]),
        "age_group": sql_text(age_group),
        "source": sql_text(row["source"]),
        "active": "true",
    }
    full = {"clip_id": key, "video_id": sql_text(row["video_id"]), "start_sec": "0", **changing}
    return (
        f"update video_exercises set {assignments(changing)} where clip_id = {key};\n"
        f"insert into video_exercises ({', '.join(full)}) select {', '.join(full.values())}\n"
        f"where not exists (select 1 from video_exercises where clip_id = {key});"
    )


CLEAR_LABELS = """delete from video_exercise_labels where clip_id in (
    select c.clip_id from video_exercises c join exercise_videos v on v.video_id = c.video_id where v.media_url is not null
);"""


def label_statement(row: dict) -> str:
    key = sql_text(row["clip_id"])
    values = ", ".join(
        f"({key}, {seq}, {sql_text(age)}, {sql_text(factor)}, {sql_text(phase)})"
        for seq, (age, factor, phase) in enumerate(row["labels"], start=1)
    )
    return f"insert into video_exercise_labels (clip_id, seq, age_group, fitness_factor, phase) values {values};"


def deactivate_statement(clip_ids: list[str]) -> str:
    """이번 표에 없는 공단 클립은 끈다. 유튜브 클립(media_url 이 없는 영상)은 건드리지 않는다."""
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


def main(ai_root: Path, ref: str, add_labels: bool, notes: list[str]) -> None:
    commit = git(ai_root, "rev-parse", ref)
    input_commit, committed_at = git(ai_root, "log", "-1", "--format=%h %cI", commit, "--", INPUT).split(" ", 1)
    collected_at = committed_at.replace("T", " ")
    rows = read_rows(ai_root, commit)
    videos = build(rows)

    order = [code for code, _, _ in AGE_GROUPS.values()]
    reach = Counter(age for video in videos for age in dict.fromkeys(label[0] for label in video["labels"]))
    first_age = Counter(video["labels"][0][0] for video in videos)
    shared = sum(1 for video in videos if len({label[0] for label in video["labels"]}) > 1)
    candidates = sum(1 for video in videos if video["is_exercise"])
    many_phases = sum(1 for video in videos if len({label[2] for label in video["labels"]}) > 1)
    many_factors = sum(1 for video in videos if len({label[1] for label in video["labels"]}) > 1)

    def by_age(counter: Counter) -> str:
        return " · ".join(f"{code} {counter[code]}" for code in order if counter[code])

    out = sys.stdout
    out.reconfigure(encoding="utf-8", newline="\n")
    command = "scripts/kspo_videos_to_sql.py" + (" --add-labels" if add_labels else "")
    print("-- coaching: 공단 「국민체력100 동영상 정보」 오픈API(15108846) 영상을 영상 · 클립 표에 싣는다.", file=out)
    print(f"-- {command} --ref {commit[:7]} 가 생성한다. 손으로 고치지 말고, AI 표가 바뀌면 새 V 파일로 다시 만든다.", file=out)
    print(f"-- 출처: family-fitness-ai 커밋 {commit} (입력 파일 마지막 변경 {input_commit}, {collected_at})", file=out)
    print(f"--   {INPUT} {len(rows)}줄(영상 한 편이 연령대 · 요인 · 단계마다 한 줄)", file=out)
    print(f"-- 이번 표: 영상 {len(videos)}편 = 클립 {len(videos)}개, 그중 운동 후보 {candidates}개.", file=out)
    print(f"--   연령대마다 받는 클립: {by_age(reach)}. 두 연령대에 드는 영상(「공통」) {shared}편은 클립 하나에 연령대 줄이 둘이다.", file=out)
    print(f"--   클립 행의 연령대 칸(첫 줄 값): {by_age(first_age)}.", file=out)
    print(f"--   단계가 둘인 영상 {many_phases}편 · 요인이 둘 이상인 영상 {many_factors}편도 줄마다 후보가 된다.", file=out)
    print("-- 영상 한 편 = 클립 하나: clip_id = {video_id}-0, seq 1, 0초 ~ duration_sec. 표의 줄은 모두 video_exercise_labels 에 싣는다.", file=out)
    print("-- 영상은 media_url(mp4) · thumbnail_url 로 튼다. 표에 없는 제목 · 첫 장면 주소 · 준비물은 이미 실린 값을 그대로 둔다.", file=out)
    print("-- 이미 적재된 DB 에 다시 돌려도 된다: 있는 clip_id 는 고치고 켜고, 없으면 넣고, 이번 표에 없는 공단 클립은 끈다.", file=out)
    print("--   공단 클립의 연령대 · 요인 · 단계 줄은 모두 지우고 다시 넣는다. 영상은 labeled_by='AI' 행만 고치고, 없으면 넣는다.", file=out)
    print("--   유튜브 영상 · 클립은 건드리지 않는다. PostgreSQL 과 H2(MODE=PostgreSQL) 양쪽에서 그대로 실행되는 문법만 쓴다.", file=out)
    for note in notes:
        print(f"-- {note}", file=out)
    if add_labels:
        print(ADD_LABELS, file=out)
    for video in videos:
        print(video_statements(video, collected_at), file=out)
    for video in videos:
        print(clip_statements(video), file=out)
    print(CLEAR_LABELS, file=out)
    for video in videos:
        print(label_statement(video), file=out)
    print(deactivate_statement([video["clip_id"] for video in videos]), file=out)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="family-fitness-ai 공단 영상 표 → Flyway 마이그레이션 SQL")
    parser.add_argument("ai_root", type=Path, help="family-fitness-ai 저장소 경로")
    parser.add_argument("--ref", default="HEAD", help="읽을 AI 커밋(브랜치 이름도 된다). 기본 HEAD")
    parser.add_argument(
        "--add-labels",
        action="store_true",
        help="exercise_videos.citation_label 칸과 video_exercise_labels 표도 만든다. 이 형식 첫 적재(V165)에만 쓴다.",
    )
    parser.add_argument(
        "--note", action="append", default=[], help="머리 주석에 한 줄 더 적는다(앞 표에서 무엇이 바뀌었는지). 여러 번 줄 수 있다."
    )
    args = parser.parse_args()
    main(args.ai_root, args.ref, args.add_labels, args.note)
