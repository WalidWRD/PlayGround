# SubscriptionAnalyzer v6.0

## الميزات
- 12 نقطة اعتراض بقياس كامل (event/args/original/patched)
- وضعان: ANALYZE (صفر تعديل) وPATCH (تعديل + قياس)
- تقرير JSON: `/sdcard/SubscriptionAnalyzer/report_*.json`
- صفر كراش (PROTECTIVE + try/catch مستقل لكل طبقة)

## البناء
```bash
gradle :app:assembleRelease --project-dir SubscriptionAnalyzer
```

## التثبيت
1. ثبّت `SubscriptionAnalyzer-v6.0.apk` (نفس حزمة AppHook — أزل AppHook أولاً)
2. فعّله في LSPosed/LSPatch/HKP بالنطاق المطلوب
3. تنقّل في التطبيق المستهدف، ثم اسحب التقرير:
```bash
adb shell "ls /sdcard/SubscriptionAnalyzer/ 2>/dev/null || ls /data/data/ai.mainfunc.genspark/cache/SubscriptionAnalyzer/"
```

## التحويل إلى PATCH
غيّر `Config.PATCH_MODE=true` في `app/src/main/java/com/apphook/log/Config.java` وأعد البناء.
