-- Android 12+ FrameTimeline analysis. Replace {{PID}} with the captured integer PID.
-- Run with a trace processor supporting android.frames.timeline (verified with v59.1).
-- Keep coverage, dropped frames and queue latency alongside duration percentiles.
-- These derived metrics are not a Macrobenchmark execution or a universal FPS claim.
-- https://perfetto.dev/docs/data-sources/frametimeline
-- https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics
-- AndroidX FrameTimingQuery uses max(actual end, RenderThread end) for overrun:
-- https://android.googlesource.com/platform/frameworks/support/+/dd97834aa54671ee1f56d65fa46668b4ffeb57e8/benchmark/benchmark-macro/src/main/java/androidx/benchmark/macro/perfetto/FrameTimingQuery.kt

INCLUDE PERFETTO MODULE android.frames.timeline;

CREATE PERFETTO TABLE leaf_target AS SELECT upid FROM process WHERE pid = {{PID}};

CREATE PERFETTO TABLE leaf_frames AS
SELECT
  f.frame_id,
  f.actual_frame_timeline_count AS actual_count,
  f.expected_frame_timeline_count AS expected_count,
  f.draw_frame_count,
  a.id AS actual_id,
  e.id AS expected_id,
  a.surface_frame_token AS actual_token,
  e.surface_frame_token AS expected_token,
  a.jank_type,
  a.present_type,
  a.on_time_finish,
  a.prediction_type,
  CASE WHEN a.id IS NOT NULL AND rt.id IS NOT NULL
    THEN a.ts + a.dur < rt.ts + rt.dur END AS actual_end_before_rt,
  a.dur / 1e6 AS actual_duration_ms,
  CASE WHEN a.dur > 0 AND e.dur > 0 AND ui.dur > 0 AND rt.dur > 0
    AND a.surface_frame_token = e.surface_frame_token
    AND a.upid = e.upid AND f.actual_frame_timeline_count = 1
    AND f.expected_frame_timeline_count = 1 AND f.draw_frame_count = 1
    THEN (MAX(a.ts + a.dur, rt.ts + rt.dur) - e.ts - e.dur) / 1e6
    END AS deadline_overrun_ms,
  CASE WHEN ui.dur > 0 AND rt.dur > 0 AND rt.ts + rt.dur >= ui.ts
    THEN (rt.ts + rt.dur - ui.ts) / 1e6 END AS ui_to_rt_completion_ms,
  ui.dur / 1e6 AS ui_elapsed_ms,
  rt.dur / 1e6 AS rt_elapsed_ms
FROM android_frames f
LEFT JOIN actual_frame_timeline_slice a ON f.actual_frame_timeline_id = a.id
LEFT JOIN expected_frame_timeline_slice e ON f.expected_frame_timeline_id = e.id
LEFT JOIN slice ui ON f.do_frame_id = ui.id
LEFT JOIN slice rt ON f.draw_frame_id = rt.id
WHERE f.upid IN leaf_target;

SELECT COUNT(*) AS canonical_frames,
  COUNT(DISTINCT frame_id) AS unique_frame_ids,
  SUM(actual_id IS NOT NULL) AS actual_matched,
  SUM(expected_id IS NOT NULL) AS expected_matched,
  SUM(actual_token != expected_token) AS token_mismatches,
  SUM(actual_count > 1 OR expected_count > 1 OR draw_frame_count > 1) AS ambiguous_frames,
  SUM(actual_end_before_rt) AS actual_end_before_rt_frames,
  COUNT(deadline_overrun_ms) AS overrun_eligible,
  ROUND(100.0 * COUNT(deadline_overrun_ms) / NULLIF(COUNT(*), 0), 2) AS overrun_coverage_pct,
  COUNT(ui_to_rt_completion_ms) AS cpu_completion_eligible
FROM leaf_frames;

-- UI-to-RT completion is elapsed production time, not a sum of active CPU run time.
-- Positive overrun can include queue latency; inspect Buffer Stuffing and presentation.
SELECT ROUND(PERCENTILE(actual_duration_ms, 95), 3) AS actual_duration_p95_ms,
  ROUND(PERCENTILE(deadline_overrun_ms, 50), 3) AS overrun_p50_ms,
  ROUND(PERCENTILE(deadline_overrun_ms, 95), 3) AS overrun_p95_ms,
  ROUND(PERCENTILE(deadline_overrun_ms, 99), 3) AS overrun_p99_ms,
  ROUND(MAX(deadline_overrun_ms), 3) AS overrun_max_ms,
  ROUND(PERCENTILE(ui_to_rt_completion_ms, 95), 3) AS ui_to_rt_p95_ms,
  ROUND(PERCENTILE(ui_elapsed_ms, 95), 3) AS ui_p95_ms,
  ROUND(PERCENTILE(rt_elapsed_ms, 95), 3) AS rt_p95_ms
FROM leaf_frames;

-- Include all canonical flags even when a missing slice makes timing ineligible.
SELECT COALESCE(jank_type, 'MISSING_ACTUAL') AS jank_type,
  present_type, on_time_finish, prediction_type, COUNT(*) AS frames
FROM leaf_frames GROUP BY jank_type, present_type, on_time_finish, prediction_type
ORDER BY frames DESC;

-- Raw surface records include records not linked to a canonical produced frame.
SELECT a.jank_type, a.present_type, a.on_time_finish, a.prediction_type, COUNT(*) AS raw_records
FROM actual_frame_timeline_slice a WHERE a.upid IN leaf_target
GROUP BY a.jank_type, a.present_type, a.on_time_finish, a.prediction_type
ORDER BY raw_records DESC;

SELECT frame_id, actual_token, expected_token, ROUND(deadline_overrun_ms, 3) AS overrun_ms,
  ROUND(ui_to_rt_completion_ms, 3) AS ui_to_rt_ms, jank_type, present_type, on_time_finish
FROM leaf_frames WHERE deadline_overrun_ms IS NOT NULL
ORDER BY deadline_overrun_ms DESC LIMIT 10;

SELECT name, value, severity FROM stats WHERE value > 0 AND
  (name GLOB '*overwrit*' OR name GLOB '*overrun*' OR severity IN ('error', 'data_loss'));
