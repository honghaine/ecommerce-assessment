"""Generates the provisioned Grafana dashboards (dashboards/*.json). Run: python3 generate_dashboards.py"""
import json
from pathlib import Path

PROM = {'type': 'prometheus', 'uid': 'prometheus'}
LOKI = {'type': 'loki', 'uid': 'loki'}
PG = {'type': 'grafana-postgresql-datasource', 'uid': 'postgres'}
APP = 'application="flashsale-service"'


class Dash:
    def __init__(self, uid, title, description):
        self.uid, self.title, self.description = uid, title, description
        self.panels, self.x, self.y, self.row_h, self.next_id = [], 0, 0, 0, 1

    def _place(self, w, h):
        if self.x + w > 24:
            self.x, self.y, self.row_h = 0, self.y + self.row_h, 0
        pos = {'x': self.x, 'y': self.y, 'w': w, 'h': h}
        self.x += w
        self.row_h = max(self.row_h, h)
        return pos

    def _add(self, panel, w, h):
        panel['id'] = self.next_id
        panel['gridPos'] = self._place(w, h)
        self.next_id += 1
        self.panels.append(panel)

    def row(self, title):
        self.x, self.y, self.row_h = 0, self.y + self.row_h, 0
        self._add({'type': 'row', 'title': title, 'collapsed': False, 'panels': []}, 24, 1)
        self.x, self.y, self.row_h = 0, self.y + 1, 0

    def ts(self, title, targets, w=12, h=8, unit='short', stack=False, desc=''):
        self._add({'type': 'timeseries', 'title': title, 'description': desc, 'datasource': PROM,
                   'targets': [{'refId': chr(65 + i), 'expr': e, 'legendFormat': l, 'exemplar': True}
                               for i, (e, l) in enumerate(targets)],
                   'fieldConfig': {'defaults': {'unit': unit, 'custom': {
                       'lineWidth': 2, 'fillOpacity': 15 if stack else 5,
                       'stacking': {'mode': 'normal' if stack else 'none'}}}, 'overrides': []},
                   'options': {'legend': {'displayMode': 'table', 'placement': 'right', 'calcs': ['lastNotNull', 'max']},
                               'tooltip': {'mode': 'multi'}}}, w, h)

    def stat(self, title, expr, w=4, h=4, unit='short', thresholds=None, desc='', decimals=None):
        steps = [{'color': 'green', 'value': None}] + [{'color': c, 'value': v} for v, c in (thresholds or [])]
        defaults = {'unit': unit, 'thresholds': {'mode': 'absolute', 'steps': steps}}
        if decimals is not None:
            defaults['decimals'] = decimals
        self._add({'type': 'stat', 'title': title, 'description': desc, 'datasource': PROM,
                   'targets': [{'refId': 'A', 'expr': expr, 'instant': True}],
                   'fieldConfig': {'defaults': defaults, 'overrides': []},
                   'options': {'colorMode': 'background', 'graphMode': 'area', 'reduceOptions': {'calcs': ['lastNotNull']}}},
                  w, h)

    def sql_table(self, title, sql, w=12, h=8, desc=''):
        self._add({'type': 'table', 'title': title, 'description': desc, 'datasource': PG,
                   'targets': [{'refId': 'A', 'format': 'table', 'rawQuery': True, 'rawSql': sql, 'editorMode': 'code'}],
                   'options': {'showHeader': True}}, w, h)

    def logs(self, title, expr, w=24, h=10):
        self._add({'type': 'logs', 'title': title, 'datasource': LOKI,
                   'targets': [{'refId': 'A', 'expr': expr}],
                   'options': {'showTime': True, 'wrapLogMessage': True, 'enableLogDetails': True,
                               'sortOrder': 'Descending', 'prettifyLogMessage': False}}, w, h)

    def json(self):
        return {'uid': self.uid, 'title': self.title, 'description': self.description, 'tags': ['flashsale'],
                'timezone': 'browser', 'schemaVersion': 39, 'refresh': '10s', 'editable': True,
                'time': {'from': 'now-30m', 'to': 'now'}, 'panels': self.panels,
                'links': [{'title': 'FlashSale dashboards', 'type': 'dashboards', 'tags': ['flashsale'], 'asDropdown': True}]}


def rate(metric, by=None, window='1m', where=''):
    sel = f'{metric}{{{where}}}' if where else metric
    return f'sum by ({by}) (rate({sel}[{window}]))' if by else f'sum(rate({sel}[{window}]))'


# ---------------------------------------------------------------- 1. Service overview
d = Dash('fs-overview', 'FlashSale · 1. Service overview', 'Traffic, errors and latency (RED) + resources, all instances')
d.row('Traffic & errors')
d.stat('Instances up', 'count(up{job="flashsale-app"} == 1)', thresholds=[(0, 'red'), (1, 'green')], desc='App instances scraped successfully')
d.stat('Requests / s', f'sum(rate(http_server_requests_seconds_count{{{APP}}}[1m]))', unit='reqps', decimals=1)
d.stat('5xx error rate', f'(sum(rate(http_server_requests_seconds_count{{{APP},status=~"5.."}}[5m])) or vector(0)) / clamp_min(sum(rate(http_server_requests_seconds_count{{{APP}}}[5m])), 1e-9)',
       unit='percentunit', thresholds=[(0.01, 'orange'), (0.05, 'red')], decimals=2)
d.stat('p95 latency', f'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{{{APP}}}[5m])))',
       unit='s', thresholds=[(0.2, 'orange'), (0.5, 'red')], decimals=3)
d.stat('DB pool pending', 'max(hikaricp_connections_pending)', thresholds=[(1, 'orange'), (5, 'red')], desc='Threads waiting for a DB connection')
d.stat('Live threads', 'sum(jvm_threads_live_threads)')
d.ts('Requests / s by endpoint', [(f'sum by (method, uri) (rate(http_server_requests_seconds_count{{{APP},uri!~"/actuator.*"}}[1m]))', '{{method}} {{uri}}')], unit='reqps')
d.ts('Responses / s by status', [(f'sum by (status) (rate(http_server_requests_seconds_count{{{APP}}}[1m]))', '{{status}}')], unit='reqps', stack=True)
d.row('Latency')
d.ts('p95 latency by endpoint (click a dot = trace)', [(f'histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{{{APP},uri!~"/actuator.*"}}[1m])))', '{{uri}}')], unit='s', w=16)
d.ts('Latency percentiles (all)', [(f'histogram_quantile({q}, sum by (le) (rate(http_server_requests_seconds_bucket{{{APP}}}[1m])))', f'p{int(q*100)}') for q in (0.5, 0.95, 0.99)], unit='s', w=8)
d.row('Resources')
d.ts('DB connection pool', [('sum by (instance) (hikaricp_connections_active)', 'active {{instance}}'), ('sum by (instance) (hikaricp_connections_pending)', 'pending {{instance}}'), ('max(hikaricp_connections_max)', 'max')], w=8)
d.ts('JVM heap used', [('sum by (instance) (jvm_memory_used_bytes{area="heap"})', '{{instance}}')], unit='bytes', w=8)
d.ts('CPU usage', [('max by (instance) (process_cpu_usage)', '{{instance}}')], unit='percentunit', w=8)
d.row('Recent errors (Loki)')
d.logs('Application WARN / ERROR logs', '{service=~"app.*", level=~"WARN|ERROR"} != "SpringDoc" | json | line_format "{{.level}} [{{.service}}] {{.logger_name}} — {{.message}}"')
overview = d

# ---------------------------------------------------------------- 2. Flash sale live
d = Dash('fs-flashsale', 'FlashSale · 2. Flash sale live', 'Purchases, Redis gate efficiency and live slot state')
d.row('Purchases')
d.stat('Purchases OK / min', 'sum(rate(flashsale_purchase_total{result="success"}[1m])) * 60', decimals=0)
d.stat('Rejected / min', 'sum(rate(flashsale_purchase_total{result!~"success|replay"}[1m])) * 60', decimals=0)
d.stat('Purchase p95', 'histogram_quantile(0.95, sum by (le) (rate(flashsale_purchase_duration_seconds_bucket[5m])))', unit='s', thresholds=[(0.2, 'orange'), (0.5, 'red')], decimals=3)
d.stat('Rejected by Redis gate', 'sum(rate(flashsale_gate_total{result=~"sold_out|already_purchased"}[5m])) / clamp_min(sum(rate(flashsale_gate_total[5m])), 1e-9)',
       unit='percentunit', decimals=1, desc='Share of purchase attempts rejected in Redis without touching the database')
d.stat('Gate bypassed (Redis down)', 'sum(increase(flashsale_gate_total{result="bypassed"}[5m])) or vector(0)', thresholds=[(1, 'red')])
d._add({'type': 'stat', 'title': 'Oversold items (must be 0)', 'datasource': PG,
        'description': 'Live DB check: items with sold > quota (impossible by CHECK constraint)',
        'targets': [{'refId': 'A', 'format': 'table', 'rawQuery': True, 'editorMode': 'code',
                     'rawSql': 'SELECT count(*) AS oversold FROM flash_sale_items WHERE sold > quota'}],
        'fieldConfig': {'defaults': {'thresholds': {'mode': 'absolute', 'steps': [
            {'color': 'green', 'value': None}, {'color': 'red', 'value': 1}]}}, 'overrides': []},
        'options': {'colorMode': 'background', 'reduceOptions': {'calcs': ['lastNotNull']}}}, 4, 4)
d.ts('Purchase attempts / s by result', [('sum by (result) (rate(flashsale_purchase_total[1m]))', '{{result}}')], unit='reqps', stack=True)
d.ts('Redis gate decisions / s', [('sum by (result) (rate(flashsale_gate_total[1m]))', '{{result}}')], unit='reqps', stack=True)
d.ts('Purchase latency by result (click a dot = trace)', [('histogram_quantile(0.95, sum by (le, result) (rate(flashsale_purchase_duration_seconds_bucket[1m])))', 'p95 {{result}}')], unit='s', w=24)
d.row('Live slots (PostgreSQL)')
d.sql_table('Items on sale right now', '''
SELECT s.region, s.name AS slot, p.sku, p.name AS product, i.sale_price, i.quota, i.sold, i.quota - i.sold AS remaining,
       round(100.0 * i.sold / NULLIF(i.quota, 0), 1) AS sold_pct
FROM flash_sale_items i
JOIN flash_sale_sessions s ON s.id = i.session_id
JOIN products p ON p.id = i.product_id
WHERE now() >= s.start_at AND now() < s.end_at AND i.status = 'APPROVED'
ORDER BY s.region, s.start_at, i.id''', w=24, h=7, desc='Live from the database — sold never exceeds quota (DB constraint)')
d.sql_table('Orders per slot today (VN)', '''
SELECT s.name AS slot, count(o.id) AS orders, COALESCE(sum(o.amount), 0) AS revenue
FROM flash_sale_sessions s
LEFT JOIN flash_sale_items i ON i.session_id = s.id
LEFT JOIN orders o ON o.flash_sale_item_id = i.id
WHERE s.region = 'VN' AND s.sale_date = (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
GROUP BY s.name, s.start_at ORDER BY s.start_at''', w=24, h=8)
d.row('Purchase logs (Loki)')
d.logs('Purchases (open a line → traceId → trace)', '{service=~"app.*"} |= "Flash sale purchase" | json | line_format "[{{.service}}] {{.message}}"', h=8)
flashsale = d

# ---------------------------------------------------------------- 3. Inventory, outbox, auth
d = Dash('fs-async', 'FlashSale · 3. Inventory, outbox & auth', 'Async processing health, data consistency, authentication')
d.row('Consistency')
d.stat('Inventory drift (must be 0)', 'max(inventory_drift_products)', thresholds=[(1, 'red')], desc='Products whose stock disagrees with the movement ledger')
d.stat('Outbox pending', 'max(outbox_pending_events)', thresholds=[(100, 'orange'), (1000, 'red')])
d.stat('Outbox lag', 'max(outbox_lag_seconds)', unit='s', thresholds=[(30, 'orange'), (60, 'red')], desc='Age of the oldest unprocessed event')
d.stat('Dead letters', 'max(outbox_failed_events)', thresholds=[(1, 'red')])
d.stat('Items settled (1h)', 'sum(increase(flashsale_settlement_items_total[1h])) or vector(0)', decimals=0)
d.stat('Skipped: no stock (1h)', 'sum(increase(flashsale_generator_skipped_total[1h])) or vector(0)', thresholds=[(1, 'orange')], decimals=0)
d.ts('Outbox events processed / s', [('sum by (type) (rate(outbox_events_processed_total[1m]))', '{{type}}'), ('sum by (type) (rate(outbox_events_failed_total[1m]))', 'FAILED {{type}}')], unit='ops', w=12)
d.ts('Outbox backlog & lag', [('max(outbox_pending_events)', 'pending'), ('max(outbox_failed_events)', 'failed'), ('max(outbox_lag_seconds)', 'lag (s)')], w=12)
d.ts('Slot generator (per 10 min)', [('sum by (region) (increase(flashsale_generator_slots_total[10m]))', 'slots {{region}}'), ('sum by (region) (increase(flashsale_generator_items_total[10m]))', 'items {{region}}'), ('sum by (region) (increase(flashsale_generator_skipped_total[10m]))', 'skipped {{region}}')], w=12)
d.ts('Warehouse sync events / min', [('sum by (status) (rate(warehouse_sync_events_total[5m])) * 60', '{{status}}')], w=12)
d.sql_table('Stock per product', '''
SELECT p.region, p.sku, p.name, p.status, v.total, v.available, v.reserved, v.updated_at
FROM inventory v JOIN products p ON p.id = v.product_id ORDER BY p.region, p.id''', w=24, h=8)
d.row('Authentication & abuse protection')
d.ts('Logins / min by result', [('sum by (result) (rate(auth_login_total[5m])) * 60', '{{result}}')], w=8)
d.ts('OTP / min', [('sum by (event) (rate(auth_otp_total[5m])) * 60', '{{event}}'), ('sum(rate(auth_register_total[5m])) * 60', 'registrations')], w=8)
d.ts('Rate-limited requests / min', [('sum by (scope) (rate(ratelimit_rejected_total[5m])) * 60', '{{scope}}')], w=8)
asyncd = d

# ---------------------------------------------------------------- 4. Infrastructure
d = Dash('fs-infra', 'FlashSale · 4. Infrastructure', 'PostgreSQL, Redis and container logs')
d.row('PostgreSQL')
d.stat('PostgreSQL up', 'max(pg_up)', thresholds=[(0, 'red'), (1, 'green')])
d.stat('Connections', 'sum(pg_stat_database_numbackends{datname="flashsale"})')
d.stat('Commits / s', 'sum(rate(pg_stat_database_xact_commit{datname="flashsale"}[1m]))', unit='ops', decimals=0)
d.stat('Deadlocks (1h)', 'sum(increase(pg_stat_database_deadlocks{datname="flashsale"}[1h])) or vector(0)', thresholds=[(1, 'red')], decimals=0)
d.stat('Redis up', 'max(redis_up)', thresholds=[(0, 'red'), (1, 'green')])
d.stat('Redis keys', 'sum(redis_db_keys)')
d.ts('Transactions / s', [('sum(rate(pg_stat_database_xact_commit{datname="flashsale"}[1m]))', 'commit'), ('sum(rate(pg_stat_database_xact_rollback{datname="flashsale"}[1m]))', 'rollback')], unit='ops', w=8)
d.ts('Rows / s', [('sum(rate(pg_stat_database_tup_inserted{datname="flashsale"}[1m]))', 'inserted'), ('sum(rate(pg_stat_database_tup_updated{datname="flashsale"}[1m]))', 'updated'), ('sum(rate(pg_stat_database_tup_fetched{datname="flashsale"}[1m]))', 'fetched')], unit='ops', w=8)
d.ts('Locks by mode', [('sum by (mode) (pg_locks_count{datname="flashsale"})', '{{mode}}')], w=8)
d.row('Redis')
d.ts('Redis commands / s', [('sum(rate(redis_commands_processed_total[1m]))', 'commands')], unit='ops', w=8)
d.ts('Redis memory', [('max(redis_memory_used_bytes)', 'used')], unit='bytes', w=8)
d.ts('Redis clients', [('max(redis_connected_clients)', 'clients')], w=8)
d.row('Logs')
d._add({'type': 'timeseries', 'title': 'Log lines / min by service', 'datasource': LOKI,
        'targets': [{'refId': 'A', 'expr': 'sum by (service) (count_over_time({service=~".+"}[1m]))', 'legendFormat': '{{service}}'}],
        'fieldConfig': {'defaults': {'custom': {'fillOpacity': 10}}, 'overrides': []}}, 24, 7)
d.logs('All containers', '{service=~".+"}', h=10)
infra = d

out = Path(__file__).with_name('dashboards')
out.mkdir(exist_ok=True)
for i, dash in enumerate([overview, flashsale, asyncd, infra], start=1):
    (out / f'{i}-{dash.uid}.json').write_text(json.dumps(dash.json(), indent=2))
print('dashboards written:', [p.name for p in sorted(out.iterdir())])
