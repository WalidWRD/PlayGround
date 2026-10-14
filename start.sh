#!/usr/bin/env bash
# AppHook preview server — builds a static directory inside PROJECT_DIR and serves it.
# Writes deployment-output.json for the controller, then serves in the foreground.
set -euo pipefail

cd "$(dirname "$0")"
PROJECT_ROOT="$PWD"
/usr/bin/time -p bash -c 'echo "project root: $0"' "$PROJECT_ROOT"

/usr/bin/time -p python3 --version
PORT="${PORT:-3000}"
export PORT
DIST="$PROJECT_ROOT/dist"
/usr/bin/time -p mkdir -p "$DIST"

/usr/bin/time -p bash -c '
set -euo pipefail
root="$0"
mkdir -p "$root/dist/assets"
cp -f "$root/AppHook/docs/ANALYSIS.md" "$root/dist/assets/ANALYSIS.md" 2>/dev/null || true
cp -f "$root/AppHook/docs/fingerprints.json" "$root/dist/assets/fingerprints.json" 2>/dev/null || true
cp -f "$root/AppHook/docs/PATCH_GUIDE.md" "$root/dist/assets/PATCH_GUIDE.md" 2>/dev/null || true
cp -f "$root/AppHook/dist/AppHook-v2.0.apk" "$root/dist/assets/AppHook-v2.0.apk" 2>/dev/null || true
cp -f "$root/AppHook/dist/AppHook-v2.0-source.zip" "$root/dist/assets/AppHook-v2.0-source.zip" 2>/dev/null || true
ls -la "$root/dist" "$root/dist/assets"
' "$PROJECT_ROOT"

/usr/bin/time -p tee "$DIST/index.html" > /dev/null <<'HTML'
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>AppHook v2.0 — الموديول الاحترافي الشامل</title>
<style>
  :root { color-scheme: light; --bg:#0f172a; --card:#ffffff; --ink:#0f172a; --mut:#475569; --acc:#16a34a; }
  * { box-sizing: border-box; }
  body { margin:0; font-family: system-ui, "Segoe UI", Tahoma, Arial, sans-serif; background:#f1f5f9; color:var(--ink); }
  header { background:var(--bg); color:#fff; padding:28px 20px; text-align:center; }
  header h1 { margin:0 0 6px; font-size:26px; }
  header p { margin:4px 0; color:#cbd5e1; font-size:14px; }
  .badge { display:inline-block; background:var(--acc); color:#fff; border-radius:999px; padding:3px 14px; font-size:13px; margin-top:8px; }
  main { max-width:960px; margin:0 auto; padding:20px 16px 48px; }
  .grid { display:grid; grid-template-columns:1fr 1fr; gap:14px; }
  @media (max-width:700px){ .grid{ grid-template-columns:1fr; } }
  .card { background:var(--card); border-radius:12px; padding:16px 18px; box-shadow:0 1px 4px rgba(15,23,42,.08); }
  .card h2 { margin:0 0 10px; font-size:17px; }
  table { width:100%; border-collapse:collapse; font-size:13.5px; }
  th, td { border:1px solid #e2e8f0; padding:7px 9px; text-align:right; }
  th { background:#f8fafc; }
  code, .mono { font-family: ui-monospace, Consolas, monospace; font-size:12.5px; direction:ltr; display:inline-block; }
  pre { background:#0f172a; color:#e2e8f0; padding:12px 14px; border-radius:10px; overflow:auto; font-size:12.5px; direction:ltr; text-align:left; }
  a.btn { display:inline-block; background:var(--acc); color:#fff; text-decoration:none; border-radius:8px; padding:8px 16px; margin:4px 6px 4px 0; font-size:14px; }
  a.btn.ghost { background:#0f172a; }
  footer { text-align:center; color:var(--mut); font-size:12.5px; padding:18px; }
  ul { margin:8px 0; padding-right:20px; font-size:14px; line-height:1.9; }
</style>
</head>
<body>
<header>
  <h1>AppHook v2.0 — الموديول الاحترافي الشامل</h1>
  <p class="mono">com.apphook.log &nbsp;|&nbsp; v2.0.0 (code 2) &nbsp;|&nbsp; Android 8.0 → 15 (API 26–35) &nbsp;|&nbsp; libxposed 101 → 102</p>
  <p>انعكاس + بصمة بلا أسماء ثابتة &nbsp;•&nbsp; آمن ضد الكراش &nbsp;•&nbsp; كل الإصدارات والحزم &nbsp;•&nbsp; LSPatch / NPatch / HKP بلا روت</p>
  <span class="badge">PROTECTIVE mode • packed-app ready • fingerprint cache</span>
</header>
<main>
  <div class="grid">
    <section class="card">
      <h2>🪝 نقاط الهوك السبع</h2>
      <table>
        <tr><th>الميزة</th><th>الهدف</th><th>الإجراء</th></tr>
        <tr><td>حالة الشراء</td><td class="mono">Purchase.getPurchaseState</td><td>إجبار <code>0</code></td></tr>
        <tr><td>التأكيد</td><td class="mono">Purchase.isAcknowledged</td><td>إجبار <code>true</code></td></tr>
        <tr><td>RevenueCat</td><td class="mono">EntitlementInfo.isActive</td><td>إجبار <code>true</code></td></tr>
        <tr><td>أعلام Premium</td><td>مسح بصمة ديناميكي</td><td><code>true / 1</code></td></tr>
        <tr><td>الشبكة</td><td class="mono">OkHttp / HttpURLConnection</td><td>مراقبة</td></tr>
        <tr><td>Integrity</td><td class="mono">requestIntegrityToken</td><td>تحييد</td></tr>
        <tr><td>الاستعلام</td><td class="mono">queryPurchasesAsync</td><td>مراقبة</td></tr>
      </table>
    </section>
    <section class="card">
      <h2>📦 التنزيلات</h2>
      <p style="font-size:14px;color:var(--mut)">تُبنى هذه الملفات من سورس الموديول وتُنسخ هنا عند كل تشغيل.</p>
      <a class="btn" href="assets/AppHook-v2.0.apk">⬇ APK v2.0</a>
      <a class="btn ghost" href="assets/AppHook-v2.0-source.zip">⬇ Source ZIP</a>
      <a class="btn ghost" href="assets/ANALYSIS.md">التحليل</a>
      <a class="btn ghost" href="assets/fingerprints.json">البصمات</a>
      <ul>
        <li>الدخول: <code>MainHook</code> (حديث) + <code>LegacyHook</code> (قديم)</li>
        <li>الوصف المدمج يظهر في مدير الموديولات وسجل التشغيل</li>
        <li>التثبيت: دمج عبر LSPatch / NPatch / HKP ثم تحديد النطاق</li>
      </ul>
    </section>
  </div>
  <section class="card" style="margin-top:14px">
    <h2>🧪 سجل التشغيل المتوقع</h2>
<pre>[AppHook] AppHook v2.0 | billing+features+network+integrity | ...
[Billing] getPurchaseState args=0 result=2 → forced 0
[Feature] isPremium unlocked: 5 times
[Network] GET /api/sub → 200
[Safe] queryPurchasesAsync skipped: signature differs</pre>
  </section>
  <section class="card" style="margin-top:14px">
    <h2>🔧 الصيانة والتطوير</h2>
    <ul>
      <li>كل ميزة في ملف مستقل + <code>SafeHook</code> و <code>FingerprintEngine</code> مشتركة</li>
      <li>إضافة ميزة = ملف جديد + سطر في <code>MainHook.installAll</code> ومرآته في <code>LegacyBridge</code></li>
      <li>قاعدة البصمات: <code>assets/fingerprints.json</code> — تُقرأ بدل إعادة التحليل</li>
      <li>أول ما سيفشل عند التحديث موثق في <code>ANALYSIS.md §3</code> مع البدائل</li>
    </ul>
  </section>
</main>
<footer>AppHook v2.0 • com.apphook.log • preview served from project dist/</footer>
</body>
</html>
HTML

/usr/bin/time -p bash -c 'test -f "$0/dist/index.html" && echo "index.html ready"' "$PROJECT_ROOT"

/usr/bin/time -p python3 -c '
import json, os
web = os.environ.get("OPENCODE_WEB_DIR", "/home/runner/work/_temp/omgithub-web")
root = os.environ.get("PROJECT_DIR", os.getcwd())
d = os.path.join(root, "dist")
os.makedirs(web, exist_ok=True)
p = os.path.join(web, "deployment-output.json")
json.dump({"project": root, "directory": d}, open(p, "w"))
print("wrote", p)
'

/usr/bin/time -p bash -c 'echo "serving $0 on port ${PORT:-3000}"' "$PROJECT_ROOT"
exec /usr/bin/time -p python3 -m http.server "$PORT" --directory "$DIST"
