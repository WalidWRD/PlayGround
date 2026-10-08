# PlayGround

<!-- omgithub:readme:start -->
## 🚀 Build, play, and remix with OMGithub

**Created using [OMGithub.com](https://omgithub.com).**

[![OMGithub](https://img.shields.io/badge/OMGithub-Open%20project-orange?style=for-the-badge)](https://omgithub.com/WalidWRD/PlayGround)
[![GitHub](https://img.shields.io/badge/GitHub-Source-181717?logo=github&style=for-the-badge)](https://github.com/WalidWRD/PlayGround)

- 🎮 [Open the project](https://omgithub.com/WalidWRD/PlayGround).
- ✨ [Remix this project](https://omgithub.com/?remix=WalidWRD%2FPlayGround).
- 💻 [Explore the source](https://github.com/WalidWRD/PlayGround).
- 🛠️ [Check build runs](https://github.com/WalidWRD/PlayGround/actions).
- 🐛 [Report an issue](https://github.com/WalidWRD/PlayGround/issues).
- 👤 [Explore the creator's projects](https://omgithub.com/WalidWRD).
- 🌍 [Create with OMGithub](https://omgithub.com).
<!-- omgithub:readme:end -->

## LOKTV Hook Pro v3.1.0 (310)

موديول هوك احترافي — أُعيد بناؤه من تفكيك v1.0.0 — يعمل على **أي حزمة/إصدار** عبر **NPatch / LSPatch / HKP بدون روت**.

- **APK:** `dist/LOKTV-Hook-Pro-v3.1.0.apk` (توقيع v1+v2+v3، محاذاة متحقق منها — ثبّت مباشرة بدون MT Manager، واحذف أي نسخة قديمة أولاً لاختلاف مفتاح التوقيع)
- **جديد v3.1.0:** F21 يستدعي المثبتات الـ void بدل تعطيلها (سبب عدم عمل VIP) + F15/F16 يفحصان حزم SDK الخارجية (سبب عدم حجب الإعلانات) + تشخيص `Fxx => +N` لكل ميزة في السجل
- **جديد v3:** كشف مزدوج (alias الحزمة + بصمة dex) يعمل بغض النظر عن اسم الحزمة + وصف مدمج مختصر + بطاقة إصدار (الموديول/أندرويد)
- **التحليل المعتمد:** `docs/ANALYSIS-v3.1.0.md` (يغني عن إعادة التحليل)
- **الموديول:** `com.loktv.hook` v3.0.0 — أندرويد 5.0 (API 21) حتى 14 (API 34) — الدخول `com.loktv.hook.LokTvHook`
- **الميزات (21):** VIP + منع التحديث الإجباري + المجموعة + الفيديو + النوافذ + تحييد التعديل/روت/محاكي/بصمة/ترخيص + تجاوز VPN + تعطيل العائم + الدوران + فاحص dex + دعم المضغوط + درع كراشات + سجل + إعدادات ساخنة + حجب إعلانات + تعطيل تتبع + كتم حوارات التحديث + إخفاء شراء VIP + تقوية VipItem + منع الارتداد للمتجر + **F21 استدعاء المثبتات**
- **البناء:** `bash build.sh` (javac → d8 → aapt2 → zipalign → apksigner)
