-- Append after profile-frame-metrics.sql in the same fresh query session.
-- Requires its captured-PID substitution and leaf_frames/leaf_target tables.
-- Rank at most 20 eligible frames per trace, even if all finish before deadline.
-- This is tail attribution, not representative whole-process CPU consumption.
-- queueBuffer scopes can nest: retain only the outermost scope within each RT frame.
-- Scheduler states and wakeup provenance are observations, not GPU-stage timings.

CREATE PERFETTO TABLE leaf_ranked AS
SELECT f.frame_id, lf.deadline_overrun_ms, f.ui_thread_utid, f.render_thread_utid,
  f.do_frame_id, f.draw_frame_id, ui.ts AS ui_ts, ui.dur AS ui_dur,
  rt.ts AS rt_ts, rt.dur AS rt_dur
FROM leaf_frames lf JOIN android_frames f USING(frame_id)
JOIN slice ui ON f.do_frame_id = ui.id
JOIN slice rt ON f.draw_frame_id = rt.id
WHERE f.upid IN leaf_target AND lf.deadline_overrun_ms IS NOT NULL
ORDER BY lf.deadline_overrun_ms DESC, f.frame_id LIMIT 20;

CREATE PERFETTO TABLE leaf_frame_spans AS
SELECT frame_id, 'UI' AS role, ui_thread_utid AS utid, ui_ts AS ts, ui_dur AS dur
FROM leaf_ranked
UNION ALL
SELECT frame_id, 'RT', render_thread_utid, rt_ts, rt_dur FROM leaf_ranked;

CREATE PERFETTO TABLE leaf_outer_queue AS
SELECT f.frame_id, f.render_thread_utid AS utid, s.id, s.ts, s.dur
FROM leaf_ranked f JOIN slice rt ON rt.id = f.draw_frame_id
JOIN slice s ON s.track_id = rt.track_id
WHERE s.name = 'queueBuffer' AND s.dur > 0 AND s.depth > rt.depth
  AND s.ts >= rt.ts AND s.ts + s.dur <= rt.ts + rt.dur
  AND NOT EXISTS (
    SELECT 1 FROM slice parent
    WHERE parent.track_id = s.track_id AND parent.name = 'queueBuffer'
      AND parent.depth > rt.depth AND parent.depth < s.depth
      AND parent.ts >= rt.ts AND parent.ts + parent.dur <= rt.ts + rt.dur
      AND parent.ts <= s.ts AND parent.ts + parent.dur >= s.ts + s.dur
  );

CREATE PERFETTO TABLE leaf_wait_spans AS
SELECT * FROM leaf_frame_spans
UNION ALL SELECT frame_id, 'QUEUE', utid, ts, dur FROM leaf_outer_queue;

SELECT role, COUNT(*) AS spans, ROUND(SUM(dur) / 1e6, 6) AS elapsed_ms
FROM leaf_wait_spans GROUP BY role;

SELECT x.role, st.state, COUNT(*) AS scheduler_spans,
  ROUND(SUM(MIN(st.ts + st.dur, x.ts + x.dur) - MAX(st.ts, x.ts)) / 1e6, 6)
    AS overlap_ms
FROM leaf_wait_spans x JOIN thread_state st ON st.utid = x.utid
WHERE st.dur > 0 AND st.ts < x.ts + x.dur AND st.ts + st.dur > x.ts
GROUP BY x.role, st.state ORDER BY x.role, overlap_ms DESC;

-- A state's waker describes entry into that state. For sleeping intervals inspect
-- the following state at the sleep endpoint, rather than the sleep row's waker.
SELECT x.role, next.state AS next_state, waker.name AS waker_thread,
  p.name AS waker_process, next.irq_context, COUNT(*) AS sleep_intersections,
  ROUND(SUM(MIN(st.ts + st.dur, x.ts + x.dur) - MAX(st.ts, x.ts)) / 1e6, 6)
    AS sleep_overlap_ms
FROM leaf_wait_spans x JOIN thread_state st ON st.utid = x.utid
LEFT JOIN thread_state next ON next.utid = st.utid AND next.ts = st.ts + st.dur
LEFT JOIN thread waker ON waker.utid = next.waker_utid
LEFT JOIN process p ON p.upid = waker.upid
WHERE st.state = 'S' AND st.dur > 0
  AND st.ts < x.ts + x.dur AND st.ts + st.dur > x.ts
GROUP BY x.role, next.state, waker.name, p.name, next.irq_context
ORDER BY x.role, sleep_overlap_ms DESC;

-- Direct RT children only. Nested operation totals must not be added together.
SELECT s.name, COUNT(*) AS spans, ROUND(SUM(s.dur) / 1e6, 6) AS inclusive_ms
FROM leaf_ranked f JOIN slice s ON s.parent_id = f.draw_frame_id
GROUP BY s.name ORDER BY inclusive_ms DESC LIMIT 12;

-- Validate nonoverlap within each selected frame before interpreting queue totals.
SELECT COUNT(*) AS overlapping_queue_pairs
FROM leaf_outer_queue a JOIN leaf_outer_queue b
  ON a.frame_id = b.frame_id AND a.id < b.id
WHERE a.ts < b.ts + b.dur AND a.ts + a.dur > b.ts;

SELECT COUNT(*) AS selected_frames,
  (SELECT COUNT(*) FROM leaf_outer_queue) AS outer_queue_spans,
  (SELECT COUNT(*) FROM leaf_ranked f JOIN slice rt ON rt.id = f.draw_frame_id
    JOIN slice s ON s.track_id = rt.track_id
    WHERE s.name = 'queueBuffer' AND s.dur > 0 AND s.depth > rt.depth
      AND s.ts >= rt.ts AND s.ts + s.dur <= rt.ts + rt.dur) AS raw_nested_queue_spans
FROM leaf_ranked;

-- Incomplete thread_state durations (-1) are unknown, not CPU or sleeping time.
-- Empty trace-loss counters do not guarantee complete scheduler attribution.
WITH coverage AS (
  SELECT x.role, x.dur,
    COALESCE((SELECT SUM(MIN(st.ts + st.dur, x.ts + x.dur) - MAX(st.ts, x.ts))
      FROM thread_state st WHERE st.utid = x.utid AND st.dur > 0
        AND st.ts < x.ts + x.dur AND st.ts + st.dur > x.ts), 0) AS covered_ns
  FROM leaf_wait_spans x
)
SELECT role, ROUND(SUM(dur) / 1e6, 6) AS elapsed_ms,
  ROUND(SUM(covered_ns) / 1e6, 6) AS scheduler_covered_ms,
  ROUND(SUM(dur - covered_ns) / 1e6, 6) AS scheduler_uncovered_ms,
  ROUND(100.0 * SUM(covered_ns) / SUM(dur), 6) AS scheduler_coverage_pct
FROM coverage GROUP BY role;
