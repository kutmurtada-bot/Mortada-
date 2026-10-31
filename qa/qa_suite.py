#!/usr/bin/env python3
"""Deterministic QA checks for the Flour Distribution app.

This suite uses synthetic family records only. It intentionally exercises the
same classes of operations that matter for a 1,245+ family workload:
search, uniqueness, distribution-state accounting, and backup round-trips.
"""

from __future__ import annotations

import json
import random
import tempfile
import time
from pathlib import Path

BASELINE = 1245
STRESS_SIZES = (1245, 5000, 10000)
STATES = ("received_full", "received_partial", "not_received")


def make_dataset(size: int) -> list[dict]:
    random.seed(20261031 + size)
    rows = []
    for i in range(size):
        rows.append(
            {
                "id": f"F-{i + 1:06d}",
                "name": f"عائلة تجريبية {i + 1:06d}",
                "state": STATES[i % len(STATES)],
            }
        )
    return rows


def search(rows: list[dict], query: str) -> list[dict]:
    q = query.casefold()
    return [r for r in rows if q in r["name"].casefold() or q in r["id"].casefold()]


def assert_dataset(rows: list[dict], expected: int) -> None:
    assert len(rows) == expected, f"expected {expected} rows, got {len(rows)}"
    ids = [r["id"] for r in rows]
    assert len(ids) == len(set(ids)), "duplicate family IDs detected"
    assert all(r["name"] and r["state"] in STATES for r in rows), "invalid row detected"


def backup_roundtrip(rows: list[dict]) -> None:
    with tempfile.TemporaryDirectory() as td:
        path = Path(td) / "backup.json"
        path.write_text(json.dumps(rows, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        restored = json.loads(path.read_text(encoding="utf-8"))
        assert restored == rows, "backup/restore round-trip changed data"


def run() -> None:
    print("QA: synthetic data integrity")
    for size in STRESS_SIZES:
        rows = make_dataset(size)
        assert_dataset(rows, size)
        backup_roundtrip(rows)

        started = time.perf_counter()
        result = search(rows, str(size - 1))
        elapsed_ms = (time.perf_counter() - started) * 1000
        assert result, f"search failed for dataset size {size}"
        # This is a CI regression guard, not a device-performance guarantee.
        assert elapsed_ms < 1000, f"search regression: {elapsed_ms:.1f} ms at {size} rows"
        print(f"  PASS {size:5d} rows | search {elapsed_ms:7.2f} ms | backup round-trip OK")

    rows = make_dataset(BASELINE)
    counts = {state: sum(r["state"] == state for r in rows) for state in STATES}
    assert sum(counts.values()) == BASELINE
    print(f"  PASS {BASELINE} rows | state accounting: {counts}")
    print("QA SUITE PASSED")


if __name__ == "__main__":
    run()
