#!/usr/bin/env python3
"""AI 가 낸 백분위를 기대값으로 적은 시험 자료(fixture)를 만든다. 서버 시험(AiPercentileParityTest)이 이 파일의 줄마다
서버 계산(fitness.domain.PeerTable)을 대조해, 서버와 AI 가 같은 값에 같은 백분위를 내는지 못박는다.

기대값은 AI 코드를 직접 불러서 낸다 — family_fitness_ai.stats.tables 의 peer() · percentile_of() 와
common/items.py ITEMS 의 lower_is_better. AI 규칙을 여기에 다시 적지 않는다. AI 는 표본 30 미만이면 백분위를 내지 않는다
(assess.factor_rows) — 그런 줄은 percentile 칸을 비운다.

값은 표의 (연령대, 성별, 나이, 항목) 칸마다 여럿을 고른다:
  - 분위 점 위(0 · 1 · 10 · 25 · 50 · 75 · 90 · 99 · 100 백분위 값) — 가운데 자리와 짝수 쪽 반올림이 걸린다
  - 서로 다른 분위 점 사이(아래 · 1/3 · 2/3 · 위쪽 네 곳)
  - 범위 밖(가장 작은 값보다 1 작게, 가장 큰 값보다 1 크게)
  - 같은 값이 몰린 곳(가장 많이 몰린 값 셋까지)
그리고 표에 줄이 없는 나이(유소년 만 7~10세 · 어르신 019)도 몇 줄 넣는다.

사용(AI 저장소의 .venv 파이썬으로, backend 폴더에서):
  ../../family-fitness-ai/.venv/Scripts/python.exe scripts/ai_percentile_fixture.py ../../family-fitness-ai \\
      > src/test/resources/fitness/ai-percentiles.csv
  (macOS · Linux 는 .venv/bin/python). AI 의 value_quantiles.csv 가 바뀌면 V 마이그레이션(value_quantiles_to_sql.py)과
  이 파일을 같이 다시 만든다.
"""
import argparse
import csv
import sys
from collections import Counter
from pathlib import Path

POINTS = (0, 1, 10, 25, 50, 75, 90, 99, 100)
BETWEEN = (0.0, 1 / 3, 2 / 3, 1.0)
MISSING = (
    ("유소년", "F", 7, "012"),
    ("유소년", "M", 9, "028"),
    ("유소년", "F", 10, "020"),
    ("어르신", "M", 70, "019"),
    ("성인", "F", 30, "009"),
)


def values_of(quantiles: list[float]) -> list[float]:
    picked: list[float] = [quantiles[i] for i in POINTS]
    distinct = sorted(set(quantiles))
    if len(distinct) > 1:
        for share in BETWEEN:
            i = min(int(share * (len(distinct) - 1)), len(distinct) - 2)
            picked.append((distinct[i] + distinct[i + 1]) / 2)
    picked.append(quantiles[0] - 1)
    picked.append(quantiles[-1] + 1)
    for value, count in Counter(quantiles).most_common(3):
        if count > 1:
            picked.append(value)
    out: list[float] = []
    for value in picked:
        if value not in out:
            out.append(value)
    return out


def number(value: float) -> str:
    """파이썬이 되읽으면 같은 float 이 되는 가장 짧은 글자. 서버는 이것을 BigDecimal 로 읽어 double 로 바꾼다."""
    text = repr(float(value))
    return text[:-2] if text.endswith(".0") else text


def main(ai_root: Path) -> None:
    sys.path.insert(0, str(ai_root / "src"))
    from family_fitness_ai.common.items import ITEMS
    from family_fitness_ai.stats import tables

    out = csv.writer(sys.stdout, lineterminator="\n")
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    out.writerow(["age_group", "sex", "age", "item_code", "value", "percentile"])
    path = ai_root / "data" / "release" / "value_quantiles.csv"
    with path.open(encoding="utf-8", newline="") as fh:
        rows = list(csv.DictReader(fh))
    for r in rows:
        age_group, sex, age, code = r["age_group"], r["sex"], int(r["age"]), r["item_code"]
        sample = tables.peer(age_group, sex, age, code)
        if sample is None:
            sys.exit(f"ai_percentile_fixture: AI 가 읽지 못한 줄: {age_group} {sex} {age} {code}")
        lower = ITEMS[code].lower_is_better
        for value in values_of([float(v) for v in sample.quantiles]):
            expected = tables.percentile_of(sample, value, lower) if sample.enough else None
            out.writerow([age_group, sex, age, code, number(value), "" if expected is None else expected])
    for age_group, sex, age, code in MISSING:
        if tables.peer(age_group, sex, age, code) is not None:
            sys.exit(f"ai_percentile_fixture: 없다고 둔 칸이 AI 표에 있다: {age_group} {sex} {age} {code}")
        out.writerow([age_group, sex, age, code, "10", ""])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI 백분위 기대값 fixture")
    parser.add_argument("ai_root", type=Path, help="family-fitness-ai 저장소 경로")
    main(parser.parse_args().ai_root)
