#!/usr/bin/env python3
"""family-fitness-ai 의 산출물 `data/release/age_band_value_quantiles.csv` → Flyway 데이터 마이그레이션.

국민체력100 공공데이터(체력측정·운동처방 월별 CSV, 2024-07~2026-07 전수)에서 AI 팀이 낸
(연령대·성별·항목) 구간별 원값 분위수 q01~q99 를 `fitness_norms` 행으로 바꾼다. API 키는 필요 없다.

- 백분위 포인트는 1 · 5 · 10 · … · 95 · 99 의 21개만 넣는다. 사이 값은 PercentileCalculator 가 선형 보간한다.
- ↓(낮을수록 좋은) 항목은 성능 백분위 p 에 원값 분위수 q(100-p) 를 둔다 — 표의 percentile 은 언제나 "성능" 백분위다.
- 유아기는 개월 단위 구간(48~83개월)이라 age_unit='개월' 로 넣는다.

사용:
  python3 scripts/kspo_norms_to_sql.py ../../family-fitness-ai/data/release/age_band_value_quantiles.csv 2026 \
      > src/main/resources/db/migration/V3__fitness_norms_kspo_2024_2026.sql
"""
import csv
import sys

LOWER_IS_BETTER = {"013", "017", "021", "040", "044", "050", "051"}
PERCENTILES = [1] + list(range(5, 100, 5)) + [99]


def main(path: str, source_year: str) -> None:
    rows = list(csv.DictReader(open(path, encoding="utf-8-sig")))
    print("-- 국민체력100 공공데이터 기반 규준 (family-fitness-ai data/release/age_band_value_quantiles.csv, 2024-07~2026-07 전수).")
    print("-- scripts/kspo_norms_to_sql.py 가 생성한다. 손으로 고치지 말고 산출물이 갱신되면 다시 만든다.")
    print(f"-- percentile 은 성능 백분위다(↓ 항목은 원값 분위수를 뒤집어 넣음). 구간 {len(rows)}개 × 포인트 {len(PERCENTILES)}개.")
    print(f"delete from fitness_norms where source_year = {source_year};")
    for r in rows:
        code = r["item_code"]
        flip = code in LOWER_IS_BETTER
        for p in PERCENTILES:
            q = (100 - p) if flip else p
            value = r[f"q{q:02d}"]
            print(
                "insert into fitness_norms (item_code, sex, age_unit, age_from, age_to, percentile, norm_value, source_year) values "
                f"('{code}', '{r['sex']}', '{r['age_unit']}', {int(r['age_lo'])}, {int(r['age_hi'])}, {p}, {float(value)}, {source_year});"
            )


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
