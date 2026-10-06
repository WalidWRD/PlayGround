# KakuAudit v3 — موديول المراقبة (OBSERVE_ONLY) بدون روت

> متوافق مع **LSPosed / LSPatch / NPatch / HKPatch (وضع App بدون روت)**.
> لا يحتوي أي تجاوز، تعطيل، تزوير، تعديل رد، أو كسر حماية — مراقبة وتوثيق فقط
> حسب `kakuAudit_improved_v3`.

## ما تم بناؤه

```
kakuaudit/
├── app/src/main/AndroidManifest.xml      # xposedmodule + xposedscope + InitProvider
├── app/src/main/assets/xposed_init       # com.kakuaudit.observe.KakuAuditXposed
├── app/src/main/java/com/kakuaudit/observe/
│   ├── KakuAuditXposed.java              # مدخل LSPosed (reflection فقط)
│   ├── KakuAuditHook.java                # handleLoadPackage عبر lpparam.classLoader
│   ├── KakuAuditInitProvider.java        # دخول مبكر للتطبيقات المضغوطة (packed)
│   ├── core/
│   │   ├── ObserveMode.java              # حارس OBSERVE_ONLY + fail-closed
│   │   ├── ReflectionDiscovery.java      # بحث سلوكي بالانعكاس بدل الأسماء الثابتة
│   │   ├── VersionCompat.java            # فروع API أعلى/أقل + reflective invoke
│   │   ├── ProtectedComponentRegistry.java # PROTECTED_OR_UNAVAILABLE بدون كسر
│   │   ├── Redactor.java / PathSafety.java / KakuClock.java
│   │   └── EventBus.java                 # bounded queue + sampling + drop accounting
│   ├── watchers/                         # License / Entitlement / Subscription (metadata فقط)
│   ├── observers/                        # HookKit + Store + Lifecycle + NativeNet
│   ├── storage/                          # StorageRouter + AtomicFile + SessionManager
│   └── report/                           # ReportWriter + JsonValidator + HtmlEscaper
```

## المبادئ المطبقة

1. **الإصدارات الأعلى والأقل**: كل بحث عبر `lpparam.classLoader` مع مرشحين مرتبين
   جديد→قديم (`loadBest`) + `VersionCompat.atLeast()` + `method()` الانعكاسي.
   لا توجد تواقيع ثابتة لإصدار واحد.
2. **بدل الأسماء الثابتة**: `ReflectionDiscovery.filterMethods` بفلاتر سلوكية
   (regex + returnType + arity). الاسم وحده = `DISCOVERY_HINT` بثقة ≤0.24 فقط.
3. **التطبيقات المضغوطة/المحمية**: فحص سلسلة ClassLoader، تسجيل
   `PACKED / OBFUSCATED / DYNAMICALLY_LOADED / ACCESS_DENIED` ومواصلة المراقبة.
   لا فك ضغط، لا فك تشفير، لا تعطيل حماية.
4. **OBSERVE_ONLY**: فقط `afterHookedMethod` للقراءة. لا `param.args=` ولا `setResult`
   ولا `XC_MethodReplacement` في أي مكان. أي مسار تجاوز = `UNSUPPORTED_OBSERVATION`.
5. **بدون روت**: لا `su`، لا `/data` مباشر. التخزين: MediaStore (Q+) → SAF → legacy →
   app-private مع `requestedRoot` مقابل `actualRoot` واختبار كتابة tmp حقيقي.

## الباتش بدون روت (App)

### LSPatch (موصى به)
1. ثبّت `KakuAudit Observe` كتطبيق عادي.
2. في LSPatch: اختر التطبيق الهدف → Patch → Local mode → أضف موديول KakuAudit.
3. فعّل النطاق (scope) على الحزمة الهدف فقط — لا حاجة لنطاق system-wide.
4. شغّل التطبيق المرقّع؛ التقارير في `Download/KakuAudit/<pkg>/<ver>/<session>/`
   أو المسار الاحتياطي الموثق في `actualRoot`.

### NPatch / HKPatch (App)
- نفس الفكرة: حقن LSPosed محلي داخل APK، ثم تفعيل KakuAudit على الحزمة الهدف.
- إن كان التطبيق مضغوطًا ( DexClassLoader متعدد)، الموديول يسجل `PACKED` ويستمر
  بمراقبة الواجهات المتاحة (Activity/WebView/Runtime/Network) بدل الفشل.

## التحقق

```bash
./gradlew :app:testDebugUnitTest   # Redactor/Confidence/PathSafety/EventBus
./gradlew :app:assembleDebug       # APK للباتش المحلي
```

## السلامة

- البيانات الوصفية فقط قبل الحفظ (redaction-first).
- JSONL يُتحقق سطرًا بسطر؛ الفشل = `PARTIAL` لا `COMPLETE`.
- `analysis-complete.json` يُكتب أخيرًا؛ `filesGenerated` محسوب لا ثابت.
- القفل per-session + index ذري؛ لا كتابة فوق جلسات سابقة.
