# Kasane for Android

- `core/` pure Kotlin: the item model, the starting catalog, tiers, the
  Kotlin fallback of the Lume math, analysis, reminders, the sleep-lock rules.
  Tested on the JVM with `gradle -PcoreOnly :core:test`.
- `app/` the Android app: one Now-first screen (next task, pull-up drawer), Lists, reminders
  armed with `setAlarmClock` and re-armed after reboot, update and time change.
- `lume/core.lm` the Lume source, `lume/lume_jni.c` the JNI shim,
  `lume/host_check.c` the desktop check.
- `tools/build-core.sh` builds `liblumecore.so` for arm64-v8a and x86_64.

If the library is missing or disagrees with Kotlin on a known case at
startup, the app uses the Kotlin fallback.
