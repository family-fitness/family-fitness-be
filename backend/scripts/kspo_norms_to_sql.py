#!/usr/bin/env python3
"""family-fitness-ai 의 분위수 산출물 → Flyway 데이터 마이그레이션(`fitness_norms`).

국민체력100 공공데이터(체력측정·운동처방 월별 CSV, 2024-07~2026-07 전수)에서 AI 팀이 낸 구간별 원값 분위수를
`fitness_norms` 행으로 바꾼다. API 키는 필요 없다. 입력은 두 모양을 읽는다.
- `age_band_value_quantiles.csv`(예전 판, V3): 칸 item_code · sex · age_unit · age_lo · age_hi · q01 … q99
- `value_quantiles.csv`(지금 판): 칸 age_group · sex · age_unit · age · item_code · n · mean · sd · quantiles
  (quantiles 는 0~100 백분위 101칸을 ; 로 이은 것, 나이 한 살 = 구간 하나). AI 처럼 표본 30 미만(n < 30)은 넣지 않는다
  (stats/tables.py MIN_SAMPLE — AI 도 그 칸은 백분위를 내지 않는다).

- 백분위 포인트는 1 · 5 · 10 · … · 95 · 99 의 21개만 넣는다. 사이 값은 PercentileCalculator 가 선형 보간한다.
- ↓(낮을수록 좋은) 항목은 성능 백분위 p 에 원값 분위수 q(100-p) 를 둔다 — 표의 percentile 은 언제나 "성능" 백분위다.
  ↓ 항목은 서버 카탈로그(FitnessItem.higherIsBetter=false) · AI common/items.py(lower_is_better) 와 같다.
- 유아기는 개월 단위 구간(48~83개월)이라 age_unit='개월' 로 넣는다.
- `--items` 를 주면 그 항목만 넣고, 지우는 것도 그 항목의 행만 지운다(적용된 V3 는 두고 항목을 더할 때).

사용(backend 폴더에서):
  python3 scripts/kspo_norms_to_sql.py ../../family-fitness-ai/data/release/age_band_value_quantiles.csv 2026 \\
      > src/main/resources/db/migration/V3__fitness_norms_kspo_2024_2026.sql
  python3 scripts/kspo_norms_to_sql.py ../../family-fitness-ai/data/release/value_quantiles.csv 2026 --items 044 \\
      > src/main/resources/db/migration/V155__fitness_norms_044_wall_pass.sql
"""
import argparse
import csv
import subprocess
import sys
from pathlib import Path

LOWER_IS_BETTER = {"013", "017", "021", "040", "050", "051"}
PERCENTILES = [1] + list(range(5, 100, 5)) + [99]
MIN_SAMPLE = 30


def buckets(path: Path) -> list[dict]:
    """(item_code, sex, age_unit, age_lo, age_hi, 분위수 q → 원값) 목록. 입력 모양은 칸 이름으로 가린다."""
    rows = list(csv.DictReader(path.open(encoding="utf-8-sig")))
    out = []
    for r in rows:
        if "quantiles" in r:
            if int(r["n"]) < MIN_SAMPLE:
                continue
            values = [float(v) for v in r["quantiles"].split(";")]
            if len(values) != 101:
                sys.exit(f"kspo_norms_to_sql: 분위수가 101칸이 아니다: {r['item_code']} {r['sex']} {r['age']}")
            out.append({**r, "age_lo": r["age"], "age_hi": r["age"], "q": lambda q, v=values: v[q]})
        else:
            out.append({**r, "q": lambda q, row=r: float(row[f"q{q:02d}"])})
    return out


def source_commit(path: Path) -> str:
    """입력 파일을 마지막으로 바꾼 AI 커밋. git 밖이면 빈 문자열."""
    try:
        return subprocess.run(
            ["git", "-C", str(path.parent), "log", "-1", "--format=%h %cI", "--", path.name],
            check=True, capture_output=True, text=True, encoding="utf-8",
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return ""


def main(path: Path, source_year: str, items: list[str] | None) -> None:
    rows = [r for r in buckets(path) if items is None or r["item_code"] in items]
    if items is not None:
        missing = sorted(set(items) - {r["item_code"] for r in rows})
        if missing:
            sys.exit(f"kspo_norms_to_sql: 입력에 없는 항목: {', '.join(missing)}")
    out = sys.stdout
    out.reconfigure(encoding="utf-8", newline="\n")
    commit = source_commit(path)
    scope = "" if items is None else f" 항목 {' · '.join(items)} 만"
    print(f"-- 국민체력100 공공데이터 기반 규준{scope} (family-fitness-ai data/release/{path.name}, 2024-07~2026-07 전수).", file=out)
    if commit:
        print(f"-- 입력 파일 마지막 변경: family-fitness-ai {commit}", file=out)
    print("-- scripts/kspo_norms_to_sql.py 가 생성한다. 손으로 고치지 말고 산출물이 갱신되면 다시 만든다.", file=out)
    print(f"-- percentile 은 성능 백분위다(↓ 항목은 원값 분위수를 뒤집어 넣음). 구간 {len(rows)}개 × 포인트 {len(PERCENTILES)}개.", file=out)
    if items is None:
        print(f"delete from fitness_norms where source_year = {source_year};", file=out)
    else:
        codes = ", ".join(f"'{code}'" for code in items)
        print(f"delete from fitness_norms where source_year = {source_year} and item_code in ({codes});", file=out)
    for r in rows:
        code = r["item_code"]
        flip = code in LOWER_IS_BETTER
        for p in PERCENTILES:
            value = r["q"]((100 - p) if flip else p)
            print(
                "insert into fitness_norms (item_code, sex, age_unit, age_from, age_to, percentile, norm_value, source_year) values "
                f"('{code}', '{r['sex']}', '{r['age_unit']}', {int(r['age_lo'])}, {int(r['age_hi'])}, {p}, {float(value)}, {source_year});",
                file=out,
            )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI 분위수 산출물 → fitness_norms 마이그레이션 SQL")
    parser.add_argument("path", type=Path, help="age_band_value_quantiles.csv 또는 value_quantiles.csv")
    parser.add_argument("source_year", help="source_year 칸 값(예: 2026)")
    parser.add_argument("--items", help="이 항목만(쉼표로 구분, 예: 044). 지우는 것도 이 항목만")
    args = parser.parse_args()
    main(args.path, args.source_year, None if args.items is None else [c.strip() for c in args.items.split(",")])
