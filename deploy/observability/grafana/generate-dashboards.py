#!/usr/bin/env python3
import json
import pathlib

OUT = pathlib.Path(__file__).parent / "dashboards"
OUT.mkdir(exist_ok=True)
PROM = {"type": "prometheus", "uid": "prometheus"}
LOKI = {"type": "loki", "uid": "loki"}


def panel(pid, title, exprs, x, y, w=12, h=8, unit="short", kind="timeseries", ds=None, stack=False):
    ds = ds or PROM
    custom = {"fillOpacity": 12, "lineWidth": 2, "showPoints": "never"}
    if stack:
        custom["stacking"] = {"mode": "normal"}
    return {
        "id": pid,
        "type": kind,
        "title": title,
        "datasource": ds,
        "gridPos": {"x": x, "y": y, "w": w, "h": h},
        "fieldConfig": {"defaults": {"unit": unit, "custom": custom if kind == "timeseries" else {}}, "overrides": []},
        "options": {"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}},
        "targets": [{"refId": chr(65 + i), "expr": e, "legendFormat": legend, "datasource": ds} for i, (e, legend) in enumerate(exprs)],
    }


def logs_panel(pid, title, expr, x, y, w=24, h=10):
    return {
        "id": pid,
        "type": "logs",
        "title": title,
        "datasource": LOKI,
        "gridPos": {"x": x, "y": y, "w": w, "h": h},
        "options": {"showTime": True, "wrapLogMessage": True, "enableLogDetails": True, "sortOrder": "Descending"},
        "targets": [{"refId": "A", "expr": expr, "datasource": LOKI}],
    }


def dashboard(uid, title, panels, tags=("drift",)):
    return {
        "uid": uid,
        "title": title,
        "tags": list(tags),
        "timezone": "browser",
        "schemaVersion": 39,
        "version": 1,
        "refresh": "10s",
        "time": {"from": "now-30m", "to": "now"},
        "panels": panels,
        "templating": {"list": []},
        "annotations": {"list": []},
        "links": [{"title": "Drift dashboards", "type": "dashboards", "tags": ["drift"], "asDropdown": True}],
    }


def write(name, dash):
    (OUT / f"{name}.json").write_text(json.dumps(dash, indent=2) + "\n")


HTTP = "http_server_requests_seconds"
SERVICES = 'job=~"user-service|room-service|message-service|file-service"'

write(
    "drift-overview",
    dashboard(
        "drift-overview",
        "Drift overview",
        [
            panel(1, "Request rate per service", [(f"sum by (job) (rate({HTTP}_count{{{SERVICES}}}[1m]))", "{{job}}")], 0, 0, unit="reqps"),
            panel(
                2,
                "Error rate (5xx) per service",
                [(f'sum by (job) (rate({HTTP}_count{{{SERVICES},status=~"5.."}}[1m])) / sum by (job) (rate({HTTP}_count{{{SERVICES}}}[1m]))', "{{job}}")],
                12, 0, unit="percentunit",
            ),
            panel(3, "Latency p50", [(f"histogram_quantile(0.50, sum by (le, job) (rate({HTTP}_bucket{{{SERVICES}}}[5m])))", "{{job}}")], 0, 8, 8, unit="s"),
            panel(4, "Latency p95", [(f"histogram_quantile(0.95, sum by (le, job) (rate({HTTP}_bucket{{{SERVICES}}}[5m])))", "{{job}}")], 8, 8, 8, unit="s"),
            panel(5, "Latency p99", [(f"histogram_quantile(0.99, sum by (le, job) (rate({HTTP}_bucket{{{SERVICES}}}[5m])))", "{{job}}")], 16, 8, 8, unit="s"),
            panel(6, "Services up", [("up{" + SERVICES + "}", "{{job}}")], 0, 16, 8, 6, kind="stat"),
            logs_panel(7, "Errors and warnings (all services)", '{service=~".+", level=~"ERROR|WARN"}', 0, 22),
        ],
    ),
)

write(
    "drift-realtime",
    dashboard(
        "drift-realtime",
        "Drift real-time",
        [
            panel(1, "Active sockets", [("sum(drift_sockets_active)", "sockets"), ("sum by (job) (drift_sockets_active)", "{{job}}")], 0, 0),
            panel(2, "Rooms occupied", [("sum(drift_rooms_occupied)", "rooms with someone inside")], 12, 0),
            panel(3, "Messages per second", [("sum by (type) (rate(drift_messages_sent_total[1m]))", "{{type}}")], 0, 8, unit="ops", stack=True),
            panel(
                4,
                "Fan-out latency (message saved → delivered to a socket)",
                [
                    ("histogram_quantile(0.50, sum by (le) (rate(drift_messages_fanout_latency_seconds_bucket[5m])))", "p50"),
                    ("histogram_quantile(0.95, sum by (le) (rate(drift_messages_fanout_latency_seconds_bucket[5m])))", "p95"),
                    ("histogram_quantile(0.99, sum by (le) (rate(drift_messages_fanout_latency_seconds_bucket[5m])))", "p99"),
                ],
                12, 8, unit="s",
            ),
        ],
    ),
)

write(
    "drift-storage",
    dashboard(
        "drift-storage",
        "Drift storage",
        [
            panel(1, "Uploads completed per minute", [("sum by (type) (rate(drift_uploads_completed_total[5m])) * 60", "{{type}}")], 0, 0, stack=True),
            panel(2, "Uploads started per minute", [("sum(rate(drift_uploads_started_total[5m])) * 60", "started")], 12, 0),
            panel(
                3,
                "Average upload size",
                [("sum by (type) (rate(drift_uploads_size_sum[15m])) / sum by (type) (rate(drift_uploads_size_count[15m]))", "{{type}}")],
                0, 8, unit="bytes",
            ),
            panel(4, "Files stored per minute (file-service)", [("sum by (kind) (rate(drift_files_uploaded_total[5m])) * 60", "{{kind}}")], 12, 8),
            panel(5, "MinIO usage", [("sum(minio_bucket_usage_total_bytes) by (bucket)", "{{bucket}}")], 0, 16, unit="bytes"),
            panel(6, "MinIO objects", [("sum(minio_bucket_usage_object_total) by (bucket)", "{{bucket}}")], 12, 16),
        ],
    ),
)

write(
    "drift-cleanup",
    dashboard(
        "drift-cleanup",
        "Drift cleanup",
        [
            panel(1, "Rooms deleted (idle for ROOM_IDLE_TTL)", [("sum(increase(drift_rooms_deleted_total[1h]))", "per hour")], 0, 0),
            panel(2, "Users expired (session ended)", [("sum(increase(drift_users_expired_total[1h]))", "per hour")], 12, 0),
            panel(3, "Kafka events consumed per second", [("sum by (job) (rate(spring_kafka_listener_seconds_count[1m]))", "{{job}}")], 0, 8, 24, unit="ops"),
            logs_panel(4, "Cleanup activity", '{service=~"room-service|user-service|file-service|message-service"} |~ "(?i)deleting idle room|Deleted|removed|room.deleted|user.deleted"', 0, 16),
        ],
    ),
)

write(
    "drift-kafka",
    dashboard(
        "drift-kafka",
        "Drift Kafka",
        [
            panel(
                1,
                "Consumer lag per topic (max over partitions)",
                [("max by (job, topic) (kafka_consumer_fetch_manager_records_lag_max)", "{{job}} · {{topic}}")],
                0, 0, 24, 10,
            ),
            panel(2, "Records consumed per second", [("sum by (job, topic) (rate(kafka_consumer_fetch_manager_records_consumed_total[1m]))", "{{job}} · {{topic}}")], 0, 10),
            panel(3, "Records produced per second", [("sum by (job) (rate(kafka_producer_record_send_total[1m]))", "{{job}}")], 12, 10),
        ],
    ),
)

write(
    "drift-jvm",
    dashboard(
        "drift-jvm",
        "Drift JVM",
        [
            panel(1, "Heap used", [('sum by (job) (jvm_memory_used_bytes{area="heap",' + SERVICES + '})', "{{job}}")], 0, 0, unit="bytes"),
            panel(2, "GC pause time", [(f"sum by (job) (rate(jvm_gc_pause_seconds_sum{{{SERVICES}}}[1m]))", "{{job}}")], 12, 0, unit="s"),
            panel(3, "Live threads", [(f"jvm_threads_live_threads{{{SERVICES}}}", "{{job}}")], 0, 8),
            panel(4, "Virtual and platform thread states", [(f"sum by (job, state) (jvm_threads_states_threads{{{SERVICES}}})", "{{job}} · {{state}}")], 12, 8),
            panel(5, "CPU usage", [(f"process_cpu_usage{{{SERVICES}}}", "{{job}}")], 0, 16, unit="percentunit"),
            panel(6, "Loaded classes", [(f"jvm_classes_loaded_classes{{{SERVICES}}}", "{{job}}")], 12, 16),
        ],
        tags=("drift", "jvm"),
    ),
)
print("wrote", ", ".join(sorted(p.name for p in OUT.glob("*.json"))))
