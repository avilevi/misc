// tasker_hc_query.js
// Tasker "JavaScript" action — runs after two "Health Connect: Read Records"
// actions populate %HC_EXERCISES and %HC_SLEEP with JSON arrays.
//
// Reads those Tasker globals, normalises field names, and writes
// /sdcard/health_sync/data.json in the format hc_to_drive.py expects.
//
// Tasker task structure (5 actions):
//   1. Health Connect → Read Records → ExerciseSession  → Output: %HC_EXERCISES
//   2. Health Connect → Read Records → SleepSession     → Output: %HC_SLEEP
//   3. JavaScriptlet  → this file
//   4. Termux:Tasker  → run_hc_to_drive.sh

var OUTPUT = "/sdcard/health_sync/data.json";

// ── helpers ────────────────────────────────────────────────────────────────

function toIso(v) {
    if (!v && v !== 0) return null;
    var n = Number(v);
    // epoch milliseconds
    if (!isNaN(n) && n > 1e10) return new Date(n).toISOString();
    // epoch seconds (older devices)
    if (!isNaN(n) && n > 1e7)  return new Date(n * 1000).toISOString();
    return String(v);
}

function num(v) {
    var n = parseFloat(v);
    return isNaN(n) || n <= 0 ? null : n;
}

function parseRecords(varName) {
    try {
        var raw = global(varName);
        if (!raw || raw === 'undefined') return [];
        return JSON.parse(raw);
    } catch (e) {
        flash('Parse error for ' + varName + ': ' + e);
        return [];
    }
}

// ── exercises ──────────────────────────────────────────────────────────────
// Tasker's Health Connect output uses camelCase field names matching the
// Health Connect SDK (startTime, endTime, exerciseType, totalDistance, etc.)

var exercises = parseRecords('HC_EXERCISES').map(function(r) {
    return {
        start_time:         toIso(r.startTime         || r.start_time),
        end_time:           toIso(r.endTime           || r.end_time),
        type_code:          parseInt(r.exerciseType   || r.type_code  || 0),
        title:              r.title                   || '',
        distance_meters:    num(r.totalDistance       || r.distance   || r.distance_meters),
        energy_calories:    num(r.totalCalories       || r.calories   || r.energy_calories),
        avg_heart_rate_bpm: num(r.avgHeartRate        || r.avg_heart_rate_bpm),
        max_heart_rate_bpm: num(r.maxHeartRate        || r.max_heart_rate_bpm),
        notes:              r.notes                   || ''
    };
});

// ── sleep sessions ─────────────────────────────────────────────────────────

var sleep_sessions = parseRecords('HC_SLEEP').map(function(s) {
    var stages = (s.stages || []).map(function(seg) {
        return {
            start_time: toIso(seg.startTime || seg.start_time),
            end_time:   toIso(seg.endTime   || seg.end_time),
            stage_code: parseInt(seg.stage  || seg.stageType || seg.stage_code || 0)
        };
    });
    return {
        start_time: toIso(s.startTime || s.start_time),
        end_time:   toIso(s.endTime   || s.end_time),
        stages:     stages
    };
});

// ── write output ───────────────────────────────────────────────────────────

var json = JSON.stringify({
    generated_at:   new Date().toISOString(),
    exercises:      exercises,
    sleep_sessions: sleep_sessions
}, null, 2);

try {
    // Ensure directory exists
    new java.io.File("/sdcard/health_sync").mkdirs();

    var writer = new java.io.OutputStreamWriter(
        new java.io.FileOutputStream(OUTPUT), "UTF-8"
    );
    writer.write(json);
    writer.close();

    flash('HC export OK: ' + exercises.length + ' workouts, ' + sleep_sessions.length + ' sleep sessions');
} catch (e) {
    flash('HC write error: ' + e);
}
