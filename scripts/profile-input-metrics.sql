-- Input dispatch-to-ACK analysis from existing Android input atrace slices.
-- Replace {{PID}} with the captured integer PID. Verified with trace processor v59.1.
-- https://github.com/google/perfetto/blob/main/src/trace_processor/perfetto_sql/stdlib/android/input.sql
-- ACK is not first-visible response. Report input-reader/frame/presentation coverage.
-- Injected events need not pass InputReader; production traces may omit frame IDs.
-- Do not substitute nearest-frame timing for an unavailable input/presentation link.
-- Keep frame deadline/queue metrics and native behavior checks alongside this result.

INCLUDE PERFETTO MODULE android.input;
SELECT COUNT(*) AS events,
 COUNT(DISTINCT dispatch_track_id || ':' || event_seq || ':' || event_channel) AS unique_events,
 COUNT(dispatch_latency_dur) AS dispatch_coverage,
 COUNT(handling_latency_dur) AS handling_coverage,
 COUNT(ack_latency_dur) AS ack_coverage,
 COUNT(frame_id) AS frame_links, COUNT(read_time) AS input_reader_times,
 COUNT(end_to_end_latency_dur) AS input_to_present_coverage,
 COUNT(CASE WHEN dispatch_latency_dur < 0 OR handling_latency_dur < 0
   OR ack_latency_dur < 0 OR total_latency_dur < 0
   OR dispatch_latency_dur + handling_latency_dur + ack_latency_dur != total_latency_dur
   THEN 1 END) AS invalid_stage_durations,
 ROUND(PERCENTILE(total_latency_dur,95)/1e6,6) AS dispatch_to_ack_p95_ms,
 ROUND(MAX(total_latency_dur)/1e6,6) AS dispatch_to_ack_max_ms
FROM android_input_events WHERE pid = {{PID}};
SELECT event_type, COUNT(*) AS events,
 ROUND(PERCENTILE(dispatch_latency_dur,95)/1e6,6) AS dispatch_p95_ms,
 ROUND(PERCENTILE(handling_latency_dur,95)/1e6,6) AS handling_p95_ms,
 ROUND(PERCENTILE(ack_latency_dur,95)/1e6,6) AS ack_p95_ms,
 ROUND(PERCENTILE(total_latency_dur,95)/1e6,6) AS dispatch_to_ack_p95_ms,
 ROUND(MAX(total_latency_dur)/1e6,6) AS dispatch_to_ack_max_ms
FROM android_input_events WHERE pid = {{PID}} GROUP BY event_type;
SELECT event_seq, event_type, dispatch_ts, dispatch_latency_dur, handling_latency_dur,
 ack_latency_dur,total_latency_dur,frame_id,read_time,end_to_end_latency_dur
FROM android_input_events WHERE pid = {{PID}} ORDER BY dispatch_ts;

SELECT name, value, severity FROM stats
WHERE value > 0 AND (severity IN ('error', 'data_loss') OR name GLOB '*lost*'
  OR name GLOB '*overwrit*' OR name GLOB '*overrun*');
