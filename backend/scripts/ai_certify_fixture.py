#!/usr/bin/env python3
"""AI 가 낸 인증 등급을 기대값으로 적은 시험 자료(fixture)를 만든다. 서버 시험(AiCertifyParityTest)이 이 파일의 줄마다
서버 계산(fitness.domain.Certifier)을 대조해, 같은 사람 · 같은 값에 서버와 AI 가 같은 등급을 내는지 못박는다.

기대값은 AI 코드를 직접 불러서 낸다 — family_fitness_ai.stats.assess 의 _with_body(키 · 몸무게로 BMI 018, 허리둘레 004 와
키로 허리둘레-신장비 042 를 셈)와 stats.tables.certify. AI 규칙을 여기에 다시 적지 않는다.

기준표(grade_thresholds.csv)에 줄이 있는 (연령대, 성별, 나이) 칸 전부에 대해 여러 사람을 만든다:
  - 전부 잰 경우: 모든 항목을 1 · 2 · 3등급 기준값 그대로(경계값) 둔 셋, 기준값과 그 앞뒤 값을 섞은 여럿
  - 하나 빠진 경우: 1등급 경계 사람에게서 필수 항목 하나를 뺀 것
  - 035/037: 둘 중 하나만 잰 것 · 둘 다 잰 것(하나는 못 미침)
  - 3등급 조합: 신체조성(BMI · 체지방률 · 허리둘레)을 기준 안 · 밖 · 경계로 바꾼 것, 키 없이 허리둘레만 · 몸 값 없이
그리고 기준표에 줄이 없는 나이(만 7~10세 · 어르신)도 몇 명 넣는다. 난수는 칸마다 고정된 씨앗을 써서 다시 만들어도 같다.

한 줄: age_group,sex,age,height_cm,weight_kg,measurements,grade
  measurements 는 "코드=값" 을 ; 로 이은 것. 체지방률은 003, 허리둘레는 004 로 들어 있다(서버는 이 둘을 bodyFatPct ·
  waistCm 으로 받는다). grade 가 비면 AI 가 등급을 내지 않은 것(None)이다.

사용(AI 저장소의 .venv 파이썬으로, backend 폴더에서):
  ../../family-fitness-ai/.venv/Scripts/python.exe scripts/ai_certify_fixture.py ../../family-fitness-ai \\
      > src/test/resources/fitness/ai-certify.csv
  (macOS · Linux 는 .venv/bin/python). AI 의 grade_thresholds.csv 가 바뀌면 V 마이그레이션(grade_thresholds_to_sql.py)과
  이 파일을 같이 다시 만든다.
"""
import argparse
import csv
import random
import sys
import zlib
from collections import defaultdict
from pathlib import Path

GRADES = ("1등급", "2등급", "3등급")
BODY = {"003", "018", "042"}
ALTERNATIVES = ("035", "037")
HEIGHTS = {"유아기": (104.3, 112.0, 118.6), "유소년": (138.2, 145.0, 152.7), "청소년": (158.0, 166.4, 174.1),
           "성인": (155.5, 168.0, 179.9)}
NO_CRITERIA = (("유소년", "F", 9), ("유소년", "M", 10), ("유소년", "M", 7), ("어르신", "F", 70), ("어르신", "M", 81))


def number(value: float) -> str:
    text = repr(round(float(value), 3))
    return text[:-2] if text.endswith(".0") else text


def cells(rows: list[dict]) -> dict[tuple[str, str, int], dict[str, list[dict]]]:
    out: dict[tuple[str, str, int], dict[str, list[dict]]] = defaultdict(lambda: defaultdict(list))
    for r in rows:
        for age in range(int(r["age_lo"]), int(r["age_hi"]) + 1):
            out[(r["age_group"], r["sex"], age)][r["grade"]].append(r)
    return out


def step(code: str, cutoffs: list[float]) -> float:
    if all(float(c).is_integer() for c in cutoffs) and code not in ("014", "040", "041"):
        return 1.0
    return 0.1 if all(round(c, 1) == c for c in cutoffs) else 0.01


class Person:
    def __init__(self, height: float | None, weight: float | None, values: dict[str, float]):
        self.height = height
        self.weight = weight
        self.values = values

    def copy(self) -> "Person":
        return Person(self.height, self.weight, dict(self.values))


def build(by_grade: dict[str, list[dict]], age_group: str, rng: random.Random):
    items: dict[str, dict[str, dict]] = defaultdict(dict)
    for grade, rows in by_grade.items():
        for r in rows:
            items[r["item_code"]][grade] = r
    sport = [code for code in items if code not in BODY]
    cut = {code: [float(r["value"]) for r in items[code].values()] for code in items}
    eps = {code: step(code, cut[code]) for code in items}

    def at_grade(grade: str) -> dict[str, float]:
        values = {}
        for code in sport:
            row = items[code].get(grade)
            if row is None:
                # 이 등급 줄이 없는 항목(3등급의 043 등)은 2등급에 조금 못 미치게 둔다
                row = items[code].get("2등급") or next(iter(items[code].values()))
                lower = row["op"] in ("<=", "<")
                values[code] = float(row["value"]) + (eps[code] if lower else -eps[code])
            else:
                values[code] = float(row["value"])
        return values

    def body(person: Person, mode: str) -> Person:
        """3등급 신체조성 관문. mode: pass(기준 안) · fail(기준 밖) · edge(경계) · mix(아무렇게나)."""
        height = rng.choice(HEIGHTS[age_group])
        person.height = height
        metres = height / 100
        bmi_row = items.get("018", {}).get("3등급")
        target = 20.0
        if bmi_row is not None:
            lo, hi = float(bmi_row["value"]), float(bmi_row["value2"])
            if bmi_row["op"] == "between":
                choices = {"pass": [(lo + hi) / 2], "fail": [lo - 0.4, hi + 0.4], "edge": [lo, hi]}
            else:
                choices = {"pass": [lo - 1.0], "fail": [lo + 0.5], "edge": [lo, lo - 0.01]}
            choices["mix"] = sum(choices.values(), [])
            target = rng.choice(choices[mode])
        person.weight = round(target * metres * metres, 1)
        whtr_row = items.get("042", {}).get("3등급")
        whtr = 0.44
        if whtr_row is not None:
            c = float(whtr_row["value"])
            choices = {"pass": [c - 0.04], "fail": [c + 0.03], "edge": [c, c - 0.001]}
            choices["mix"] = sum(choices.values(), [])
            whtr = rng.choice(choices[mode])
        person.values["004"] = round(whtr * height, 1)
        fat_row = items.get("003", {}).get("3등급")
        fat = 20.0
        if fat_row is not None:
            lo, hi = float(fat_row["value"]), float(fat_row["value2"])
            if fat_row["op"] == "between":
                choices = {"pass": [(lo + hi) / 2], "fail": [lo - 1, hi + 1], "edge": [lo, hi]}
            else:
                choices = {"pass": [lo - 2], "fail": [lo + 1], "edge": [lo, lo - 0.1]}
            choices["mix"] = sum(choices.values(), [])
            fat = rng.choice(choices[mode])
        person.values["003"] = round(fat, 1)
        return person

    people: list[Person] = []
    for grade in GRADES:
        people.append(body(Person(None, None, at_grade(grade)), "pass"))
    for mode in ("fail", "edge", "mix"):
        people.append(body(Person(None, None, at_grade("3등급")), mode))
    for _ in range(6):
        values = {}
        for code in sport:
            base = rng.choice(cut[code])
            values[code] = base + rng.choice((-1, 0, 0, 1)) * eps[code]
        people.append(body(Person(None, None, values), "mix"))
    first = body(Person(None, None, at_grade("1등급")), "pass")
    required = [code for code in sport if code not in ALTERNATIVES]
    if required:
        dropped = first.copy()
        dropped.values.pop(rng.choice(required))
        people.append(dropped)
    if all(code in items for code in ALTERNATIVES):
        for keep in ALTERNATIVES:
            only = first.copy()
            only.values.pop(ALTERNATIVES[1] if keep == ALTERNATIVES[0] else ALTERNATIVES[0])
            people.append(only)
        both = first.copy()
        both.values[ALTERNATIVES[1]] = both.values[ALTERNATIVES[1]] - 5
        people.append(both)
        neither = first.copy()
        for code in ALTERNATIVES:
            neither.values.pop(code)
        people.append(neither)
    third = body(Person(None, None, at_grade("3등급")), "pass")
    bare = third.copy()
    bare.height = bare.weight = None
    bare.values.pop("003", None)
    bare.values.pop("004", None)
    people.append(bare)
    waist_only = third.copy()
    waist_only.height = None
    people.append(waist_only)
    no_fat = third.copy()
    no_fat.values.pop("003", None)
    people.append(no_fat)
    return people


def main(ai_root: Path) -> None:
    sys.path.insert(0, str(ai_root / "src"))
    from family_fitness_ai.stats import assess, tables

    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    out = csv.writer(sys.stdout, lineterminator="\n")
    out.writerow(["age_group", "sex", "age", "height_cm", "weight_kg", "measurements", "grade"])
    with (ai_root / "data" / "release" / "grade_thresholds.csv").open(encoding="utf-8", newline="") as fh:
        rows = list(csv.DictReader(fh))

    def emit(age_group: str, sex: str, age: int, person: Person) -> None:
        unit = "개월" if age_group == "유아기" else "세"
        profile = assess.Profile("fixture", age, unit, sex, person.height, person.weight, dict(person.values))
        if profile.age_group != age_group:
            sys.exit(f"ai_certify_fixture: 연령대가 어긋난다: {age_group} {age}{unit}")
        grade = tables.certify(age_group, sex, age, assess._with_body(profile))
        measurements = ";".join(f"{code}={number(v)}" for code, v in person.values.items())
        out.writerow([
            age_group,
            sex,
            age,
            "" if person.height is None else number(person.height),
            "" if person.weight is None else number(person.weight),
            measurements,
            grade or "",
        ])

    for (age_group, sex, age), by_grade in sorted(cells(rows).items()):
        rng = random.Random(zlib.crc32(f"{age_group}/{sex}/{age}".encode()))
        for person in build(by_grade, age_group, rng):
            emit(age_group, sex, age, person)
    for age_group, sex, age in NO_CRITERIA:
        emit(age_group, sex, age, Person(140.0, 35.0, {"012": 10.0, "028": 40.0}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI 인증 등급 기대값 fixture")
    parser.add_argument("ai_root", type=Path, help="family-fitness-ai 저장소 경로")
    main(parser.parse_args().ai_root)
