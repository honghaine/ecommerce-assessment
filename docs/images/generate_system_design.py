"""Generates docs/images/system-design.svg (hand-laid-out layered system design). Run: python3 generate_system_design.py"""
from pathlib import Path
from xml.sax.saxutils import escape as e

W, H = 1500, 1290
out = []
FONT = "Inter, 'Segoe UI', Helvetica, Arial, sans-serif"
C = {'client': ('#eef4ff', '#4a6fd8', '#1b2b55'), 'edge': ('#f4f4f5', '#6b7280', '#1f2937'),
     'api': ('#e8f6ee', '#2e8b57', '#10331f'), 'core': ('#fff5e2', '#d08a00', '#3d2a00'),
     'job': ('#fdecef', '#c2185b', '#4a0a24'), 'redis': ('#fff0f0', '#d32f2f', '#4a0d0d'),
     'pg': ('#eaf2fb', '#1f5fa8', '#0c2a4d'), 'ext': ('#fafafa', '#9ca3af', '#374151')}


def add(s):
    out.append(s)


def box(x, y, w, h, title, lines=(), kind='edge', dashed=False, tsize=14):
    fill, stroke, txt = C[kind]
    dash = ' stroke-dasharray="6 4"' if dashed else ''
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="{fill}" stroke="{stroke}" stroke-width="1.6"{dash}/>')
    total = 18 + len(lines) * 15
    ty = y + (h - total) / 2 + 14
    add(f'<text x="{x + w / 2}" y="{ty}" text-anchor="middle" font-family="{FONT}" font-size="{tsize}" '
        f'font-weight="600" fill="{txt}">{e(title)}</text>')
    for i, line in enumerate(lines):
        add(f'<text x="{x + w / 2}" y="{ty + 19 + i * 15}" text-anchor="middle" font-family="{FONT}" '
            f'font-size="11.5" fill="{txt}" opacity="0.9">{e(line)}</text>')


def group(x, y, w, h, text, stroke, fill='none', size=15, dash=False):
    d = ' stroke-dasharray="7 5"' if dash else ''
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="{fill}" stroke="{stroke}" stroke-width="1.8"{d}/>')
    add(f'<text x="{x + 16}" y="{y + 24}" font-family="{FONT}" font-size="{size}" font-weight="700" '
        f'fill="{stroke}">{e(text)}</text>')


def label(x, y, t, size=12, color='#374151', anchor='middle', weight='500', italic=False):
    st = ' font-style="italic"' if italic else ''
    add(f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-family="{FONT}" font-size="{size}" '
        f'font-weight="{weight}" fill="{color}"{st}>{e(t)}</text>')


def arrow(x1, y1, x2, y2, dashed=False, color='#374151'):
    d = ' stroke-dasharray="6 4"' if dashed else ''
    add(f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="1.8" marker-end="url(#arr)"{d}/>')


add(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">')
add('<defs><marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
    'orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#374151"/></marker></defs>')
add(f'<rect width="{W}" height="{H}" fill="#ffffff"/>')
label(W / 2, 38, 'FlashSale Service — System Design', 22, '#111827', weight='700')
label(W / 2, 60, 'Stateless Spring Boot instances · PostgreSQL = source of truth · Redis = accelerator · '
                 'transactional outbox', 13, '#6b7280')

# Clients → load balancer
cx = [60 + i * (220 + 73.3) for i in range(4)]
clients = [('🛒 Buyer', ['browse · purchase', 'JWT · role USER']),
           ('🏪 Seller', ['products · stock · rules', 'JWT · role SELLER']),
           ('🛠 Platform admin', ['schedule · audit · outbox', 'JWT · role PLATFORM_ADMIN']),
           ('🏭 Warehouse (WMS)', ['stock deltas', 'X-Api-Key'])]
for x, (t, lines) in zip(cx, clients):
    box(x, 80, 220, 74, t, lines, 'client')
box(350, 196, 560, 58, '⚖️  Load balancer — nginx / Cloudflare tunnel',
    ['TLS · distributes requests across N instances · X-Forwarded-For'], 'edge')
for i, x in enumerate(cx):
    arrow(x + 110, 154, 430 + i * 133, 196)

# App cluster (stacked outlines = N instances)
for off in (16, 8):
    add(f'<rect x="{40 + off}" y="{296 + off}" width="1140" height="476" rx="12" fill="#ffffff" '
        f'stroke="#14b8a6" stroke-width="1.2" opacity="0.55"/>')
group(40, 296, 1140, 476, '☕ Spring Boot app — N identical, stateless instances (Java 25 · virtual threads)',
      '#0f766e', '#f0fdfa')
label(1168, 320, '× N', 13, '#0f766e', 'end', '700')
arrow(840, 254, 840, 340)
label(850, 290, 'HTTPS', 11.5, '#374151', 'start', italic=True)
box(60, 340, 1100, 46, 'Security filter chain',
    ['JWT verify + Redis blacklist · role checks · Redis rate limits · correlation id'], 'edge', tsize=13)

label(62, 414, 'REST API  /api/v1', 12, '#2e8b57', 'start', '700')
aw = (1100 - 4 * 16) / 5
apis = [('Auth', ['register · OTP · login', 'refresh · logout']),
        ('Flash sale (buyer)', ['GET current (cached 2 s)', 'POST purchase']),
        ('Seller', ['products · stock', 'recurring flash-sale rules']),
        ('Platform admin', ['schedule · slots by seller', 'inventory audit · outbox']),
        ('Integrations', ['warehouse stock deltas', 'idempotent per event'])]
for i, (t, lines) in enumerate(apis):
    box(60 + i * (aw + 16), 422, aw, 78, t, lines, 'api')
arrow(610, 386, 610, 422)

label(62, 532, 'Domain services', 12, '#b45309', 'start', '700')
dw = (1100 - 3 * 16) / 4
cores = [('Purchase', ['Redis gate → ONE DB transaction', 'lock order: item row → wallet row']),
         ('Slot generator', ['config + seller rules →', 'slots · items · reserve quota']),
         ('Inventory', ['reserve · settle at slot end', 'warehouse deltas · ledger']),
         ('Outbox publisher', ['event written in the SAME', 'transaction as the business change'])]
for i, (t, lines) in enumerate(cores):
    box(60 + i * (dw + 16), 540, dw, 78, t, lines, 'core')
arrow(610, 500, 610, 540)

label(62, 650, 'Background jobs — on every instance, coordinated through the database', 12, '#be185d',
      'start', '700')
jobs = [('Outbox poller', ['FOR UPDATE SKIP LOCKED', 'dedupe: processed_events']),
        ('Slot settlement', ['UPDATE … settled_at IS NULL', '→ FLASH_SALE_ITEM_CLOSED']),
        ('Slot generator', ['every 10 min + on change', 'advisory lock per region']),
        ('Stock reconciler', ['Redis stock := quota − sold', 'ShedLock · every 15 s']),
        ('Notification dispatcher', ['SKIP LOCKED · mock send', 'payload redacted after send'])]
for i, (t, lines) in enumerate(jobs):
    box(60 + i * (aw + 16), 658, aw, 78, t, lines, 'job')

# External systems
group(1210, 470, 270, 302, 'External', '#6b7280', dash=True, size=14)
box(1230, 512, 230, 86, '📨 Email / SMS provider', ['mocked → application log', '(OTP delivery)'], 'ext', dashed=True)
box(1230, 650, 230, 100, 'Kafka (future)', ['swap OutboxDispatcher', 'partition key = region',
                                            'no domain code changes'], 'ext', dashed=True)
arrow(1160, 697, 1230, 570, dashed=True)
label(1186, 616, 'OTP', 11, '#6b7280', 'middle', italic=True)
arrow(1180, 740, 1230, 710, dashed=True)
label(1200, 762, 'future', 11, '#6b7280', 'middle', italic=True)

# Data stores
group(40, 838, 440, 300, '🟥 Redis 7 — shared accelerator (fail-open)', '#d32f2f', '#fffafa')
reds = [('Stock gate (Lua, atomic)', ['fs:{region}:stock:{item}', 'fs:{region}:user:{uid}:{day}']),
        ('Listing cache', ['fs:{region}:current · TTL 2 s']),
        ('Auth state', ['otp:* · ratelimit:* · jwt:blacklist:*'])]
for i, (t, lines) in enumerate(reds):
    box(60, 876 + i * 86, 400, 74, t, lines, 'redis')
group(500, 838, 680, 300, '🐘 PostgreSQL 16 — source of truth (constraints enforce every rule)', '#1f5fa8', '#f7fbff')
pw = (680 - 60) / 2
pgs = [('Flash sale', ['configs · seller rules · sessions', 'items: CHECK sold ≤ quota · orders',
                       'user_daily_purchases: PK (user, day)']),
       ('Catalog & inventory', ['products · inventory', 'CHECK available + reserved = total',
                                'movements ledger · warehouse sync log']),
       ('Identity', ['users · refresh_tokens (hashed)', 'wallets: CHECK balance ≥ 0', 'wallet_transactions']),
       ('Messaging & coordination', ['outbox_events · processed_events', 'notification_outbox · shedlock'])]
for i, (t, lines) in enumerate(pgs):
    box(520 + (i % 2) * (pw + 20), 876 + (i // 2) * 128, pw, 114, t, lines, 'pg')
arrow(260, 772, 260, 838)
label(272, 798, 'Lua gate · cache · OTP', 11.5, '#374151', 'start', italic=True)
label(272, 812, 'rate limits · blacklist', 11.5, '#374151', 'start', italic=True)
arrow(840, 772, 840, 838)
label(852, 798, 'short ACID transactions', 11.5, '#374151', 'start', italic=True)
label(852, 812, 'conditional UPDATEs · unique keys', 11.5, '#374151', 'start', italic=True)

# Observability (optional compose profile)
group(1210, 838, 270, 300, '📈 Observability', '#7c3aed', '#faf5ff', size=14)
obs = [('Prometheus', ['scrapes :8081 · DB/Redis exporters']),
       ('Loki  ·  Alloy', ['JSON logs · traceId']),
       ('Tempo', ['OTLP traces · SQL spans']),
       ('Grafana', ['4 dashboards · 8 alerts'])]
for i, (t, lines) in enumerate(obs):
    fill, stroke, txt = '#f3e8ff', '#7c3aed', '#3b0764'
    add(f'<rect x="1226" y="{874 + i * 64}" width="238" height="54" rx="8" fill="{fill}" stroke="{stroke}" stroke-width="1.4"/>')
    label(1345, 894 + i * 64, t, 13, txt, weight='600')
    label(1345, 911 + i * 64, ' · '.join(lines), 10.5, txt)
arrow(1172, 772, 1300, 838, dashed=True)
label(1300, 800, 'metrics · logs · traces', 11, '#7c3aed', 'start', italic=True)

# Guarantees
add('<rect x="40" y="1160" width="1440" height="100" rx="10" fill="#f9fafb" stroke="#e5e7eb"/>')
label(60, 1186, 'Guarantees', 13, '#111827', 'start', '700')
label(60, 1210, 'No oversell: UPDATE … WHERE sold < quota + CHECK (sold ≤ quota)  ·  1 product / user / day: '
                'PK (user_id, purchase_date)  ·  Retries: UNIQUE (user_id, Idempotency-Key)', 11.5, '#374151', 'start')
label(60, 1230, 'Inventory: quota locked before the slot, settled once after it (processed_events dedupe)  ·  '
                'Multi-instance: SKIP LOCKED · advisory lock · ShedLock  ·  Redis down → purchases continue on the DB path',
      11.5, '#374151', 'start')
add('</svg>')

Path(__file__).with_name('system-design.svg').write_text('\n'.join(out))
