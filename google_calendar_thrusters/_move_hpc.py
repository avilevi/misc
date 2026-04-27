#!/usr/bin/env python3
"""Move HPC from Thruster days to Press and Pull days starting 2026-04-27.
HPC resets to 40kg for technique work and progresses from there.
Run once, then delete this file."""

CUTOFF = "2026-04-27"

# All P&P session dates from Apr 28 with assigned HPC weights
# (sets, reps, weight_kg, is_deload)
HPC_PP = {
    "2026-04-28": (5, 3, 40.0,  False),
    "2026-05-03": (5, 3, 40.0,  False),
    "2026-05-07": (5, 3, 42.5,  False),
    "2026-05-12": (5, 3, 42.5,  False),
    "2026-05-17": (5, 3, 45.0,  False),
    "2026-05-21": (5, 3, 45.0,  False),
    "2026-05-26": (5, 3, 47.5,  False),
    "2026-05-31": (5, 3, 47.5,  False),
    "2026-06-04": (5, 3, 50.0,  False),
    "2026-06-09": (5, 3, 50.0,  False),
    "2026-06-14": (5, 3, 52.5,  False),
    "2026-06-18": (5, 3, 52.5,  False),
    "2026-06-23": (3, 3, 40.0,  True),   # Week 13 DELOAD
    "2026-06-28": (5, 3, 55.0,  False),
    "2026-07-02": (5, 3, 55.0,  False),
    "2026-07-07": (5, 3, 57.5,  False),
    "2026-07-12": (5, 3, 57.5,  False),
    "2026-07-16": (5, 3, 60.0,  False),
    "2026-07-21": (5, 3, 60.0,  False),
    "2026-07-26": (5, 3, 62.5,  False),
    "2026-07-30": (5, 3, 62.5,  False),
    "2026-08-04": (5, 3, 65.0,  False),
    "2026-08-09": (5, 3, 65.0,  False),
    "2026-08-13": (5, 3, 67.5,  False),
    "2026-08-18": (5, 3, 67.5,  False),
    "2026-08-23": (5, 3, 70.0,  False),
    "2026-08-27": (5, 3, 70.0,  False),
    "2026-09-01": (3, 3, 52.5,  True),   # Week 23 DELOAD
    "2026-09-06": (5, 3, 72.5,  False),
    "2026-09-10": (5, 3, 72.5,  False),
    "2026-09-15": (5, 3, 75.0,  False),
    "2026-09-20": (5, 3, 75.0,  False),
    "2026-09-24": (5, 3, 77.5,  False),
    "2026-09-29": (5, 3, 77.5,  False),
    "2026-10-04": (5, 3, 80.0,  False),
    "2026-10-08": (5, 3, 80.0,  False),
    "2026-10-13": (5, 3, 82.5,  False),
    "2026-10-18": (5, 3, 82.5,  False),
    "2026-10-22": (5, 3, 82.5,  False),
    "2026-10-27": (5, 3, 82.5,  False),
    "2026-11-01": (5, 3, 85.0,  False),
    "2026-11-05": (5, 3, 85.0,  False),
    "2026-11-10": (5, 3, 85.0,  False),
    "2026-11-15": (3, 3, 63.0,  True),   # Week 34 DELOAD
    "2026-11-19": (3, 3, 63.0,  True),   # Week 34 DELOAD
    "2026-11-24": (5, 3, 87.5,  False),
    "2026-11-29": (5, 3, 87.5,  False),
    "2026-12-03": (5, 3, 87.5,  False),
    "2026-12-08": (5, 3, 87.5,  False),
    "2026-12-13": (5, 3, 90.0,  False),
    "2026-12-17": (5, 3, 90.0,  False),
    "2026-12-22": (3, 3, 67.5,  True),   # Week 39 DELOAD
}


def fmt_weight(w):
    return f"{int(w)}kg" if w == int(w) else f"{w}kg"


def hpc_exercise_line(sets, reps, weight, is_deload):
    line = f"  Hang Power Clean: {sets}x{reps} @ {fmt_weight(weight)}"
    return line + " [DELOAD]" if is_deload else line


def process_thruster_block(lines):
    """Remove HPC warm-up and exercise lines."""
    return [
        l for l in lines
        if "Empty bar hang power clean" not in l
        and not l.strip().startswith("Hang Power Clean:")
    ]


def process_pp_block(lines, sets, reps, weight, is_deload):
    """Insert HPC warm-up line and HPC exercise before Push Press."""
    out = []
    warmup_hpc_added = False
    exercise_hpc_added = False
    for line in lines:
        # Insert after "Wall slides x10" in warm-up
        if not warmup_hpc_added and "Wall slides x10" in line:
            out.append(line)
            out.append("    Empty bar hang power clean x5")
            warmup_hpc_added = True
            continue
        # Insert before first Push Press line
        if not exercise_hpc_added and line.strip().startswith("Push Press:"):
            out.append(hpc_exercise_line(sets, reps, weight, is_deload))
            exercise_hpc_added = True
        out.append(line)
    return out


def main():
    with open("thruster_events.txt") as f:
        content = f.read()

    trailing_newline = content.endswith("\n")
    blocks = content.rstrip("\n").split("\n---\n")

    processed = []
    for block in blocks:
        lines = block.split("\n")
        # Find date
        date_val = ""
        for l in lines:
            if l.startswith("date:"):
                date_val = l.replace("date:", "").strip()
                break

        if date_val < CUTOFF:
            processed.append(block)
            continue

        # Determine day type
        summary = next((l for l in lines if l.startswith("summary:")), "")
        if "Thruster" in summary:
            lines = process_thruster_block(lines)
        elif "Press and Pull" in summary:
            if date_val in HPC_PP:
                s, r, w, d = HPC_PP[date_val]
                lines = process_pp_block(lines, s, r, w, d)
            else:
                print(f"WARNING: no HPC weight defined for P&P on {date_val}")

        processed.append("\n".join(lines))

    result = "\n---\n".join(processed)
    if trailing_newline:
        result += "\n"

    with open("thruster_events.txt", "w") as f:
        f.write(result)

    print(f"Done. Processed {len(processed)} event blocks.")


if __name__ == "__main__":
    main()
