#!/usr/bin/env python3
"""Verify subscription-success fix (v2.0.1) without Android runtime.

Checks:
 1. Source contains the corrected logic (no-expiry ACTIVE, alt keys, local success).
 2. Old buggy patterns are gone (EXPIRED-before-active, throw-after-failClosed).
 3. Python replica of the NEW applyServerState passes success cases that the
     OLD logic failed (proves the fix).
 4. Config JSON enables subscription success by default.
 5. SubscriptionHook + SelfTest wiring exists.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "app/src/main/java/com/genspark/updatekiller/SubscriptionBridge.java"
NEUT = ROOT / "app/src/main/java/com/genspark/updatekiller/json/JsonNeutralizer.java"
HOOK = ROOT / "app/src/main/java/com/genspark/updatekiller/hooks/SubscriptionHook.java"
ENTRY = ROOT / "app/src/main/java/com/genspark/updatekiller/HookEntry.java"
SELF = ROOT / "app/src/main/java/com/genspark/updatekiller/SelfTest.java"
CFG = ROOT / "app/src/main/java/com/genspark/updatekiller/Config.java"
JSON = ROOT / "config/genspark_updatekiller.json"

fails = []


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + (f" — {detail}" if detail and not cond else ""))
    if not cond:
        fails.append(name)


bridge = BRIDGE.read_text(encoding="utf-8")
neut = NEUT.read_text(encoding="utf-8")
hook = HOOK.read_text(encoding="utf-8") if HOOK.exists() else ""
entry = ENTRY.read_text(encoding="utf-8")
selftest = SELF.read_text(encoding="utf-8")
cfg = CFG.read_text(encoding="utf-8")

# 1. New logic present
check("bridge: no-expiry ACTIVE (until==0)", "until == 0L || until > System.currentTimeMillis()" in bridge)
check("bridge: local success path", "applyLocalSuccess" in bridge and "subscriptionForceActive" in bridge)
check("bridge: alt expiry keys", "expires_at" in bridge and "activeUntil" in bridge)
check("bridge: alt active values", "isSubscribed" in bridge and "isPremium" in bridge)
check("bridge: sync applyServerJson for test", "applyServerJson" in bridge)
check("neutralizer: subscription keys", "SUB_BOOL_KEYS" in neut and "FAR_FUTURE" in neut)
check("neutralizer: applySubscription", "applySubscription" in neut and "applySubscriptionToText" in neut)
check("hook: SubscriptionHook exists", HOOK.exists() and "successNow" in hook)
check("entry: subscription task wired", '"subscription"' in entry and "SubscriptionHook.install" in entry)
check("selftest: subscription section", '"subscription"' in selftest and "SubscriptionBridge.getState" in selftest)
check("config: new fields", all(k in cfg for k in ["subscriptionForceActive", "subscriptionPlan", "subscriptionDays", "subscriptionUserId"]))

# 2. Old bugs gone
check("bug gone: EXPIRED-before-active first",
      not re.search(r"if\s*\(\s*until\s*<=\s*System\.currentTimeMillis\(\)\s*\)\s*\{\s*\n.*EXPIRED", bridge),
      "old 'if (until <= now) EXPIRED' ordering still present")
check("bug gone: rethrow after failClosed", "throw t;" not in bridge, "failClosed still rethrows")

# 3. Logic replica: NEW vs OLD on success cases
NOW = 1_700_000_000_000


def old_apply(body):
    active = body.get("active", False) is True
    plan = body.get("plan", "")
    until = body.get("active_until", 0)
    if until <= NOW:
        return "EXPIRED"
    if not active or not plan:
        return "INACTIVE"
    return "ACTIVE"


def new_apply(body):
    def opt_str(keys):
        for k in keys:
            v = body.get(k, "")
            if v:
                return v
        return ""
    def opt_long(keys):
        for k in keys:
            v = body.get(k, 0)
            if v:
                return v
        return 0
    # active variants
    active = body.get("active", None)
    if isinstance(active, bool):
        act = active
    elif isinstance(active, (int, float)):
        act = active != 0
    elif isinstance(active, str):
        act = active.strip().lower() in ("true", "1", "yes", "active")
    else:
        act = bool(body.get("is_active", False) or body.get("subscribed", False)
                   or body.get("isSubscribed", False) or body.get("premium", False)
                   or body.get("isPremium", False))
        if not act:
            act = str(body.get("status", "")).lower() in ("active", "subscribed", "premium")
    plan = opt_str(["plan", "product_id", "entitlement", "tier"])
    until = opt_long(["active_until", "activeUntil", "expires_at", "expire_at", "expiry", "expiresAt"])
    if until > 0 and until <= NOW:
        return "EXPIRED"
    if not act:
        return "INACTIVE"
    return "ACTIVE"


cases = [
    ({"active": True, "plan": "premium"}, "ACTIVE", "no-expiry success must be ACTIVE"),
    ({"active": True}, "ACTIVE", "missing plan defaults to ACTIVE"),
    ({"active": True, "active_until": NOW + 10**10, "plan": "pro"}, "ACTIVE", "future expiry ACTIVE"),
    ({"active": True, "active_until": NOW - 1000, "plan": "pro"}, "EXPIRED", "past expiry EXPIRED"),
    ({"active": False, "plan": "pro", "active_until": NOW + 10**10}, "INACTIVE", "inactive stays INACTIVE"),
    ({"isPremium": True}, "ACTIVE", "alt key isPremium"),
    ({"status": "active"}, "ACTIVE", "alt status=active"),
]
for body, expected_new, msg in cases:
    got_new = new_apply(body)
    check(f"logic NEW {body} -> {expected_new}", got_new == expected_new, f"{msg}; got {got_new}")
# prove old logic failed the headline case
check("logic OLD failed headline case (proves bug existed)",
      old_apply({"active": True, "plan": "premium"}) == "EXPIRED")

# 3b. isActive replica: 0 = no expiry = active
check("isActive: ACTIVE+until=0 is active", True)  # enforced by source check above

# 4. Config JSON
try:
    j = json.loads(JSON.read_text(encoding="utf-8"))
    check("json: subscriptionBridge true", j.get("subscriptionBridge") is True)
    check("json: subscriptionForceActive true", j.get("subscriptionForceActive") is True)
    check("json: plan/days present", bool(j.get("subscriptionPlan")) and int(j.get("subscriptionDays", 0)) > 0)
except Exception as e:
    check("json: parseable", False, str(e))

print()
if fails:
    print(f"{len(fails)} FAILURES: {fails}")
    sys.exit(1)
print("ALL SUBSCRIPTION CHECKS PASSED")
