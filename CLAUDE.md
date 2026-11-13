# CLAUDE.md - kasane

## App Overview & Key Packages

**Kasane 重ね** (`kasane-android/`) is a daily routine and habit tracking application for Android. It combines Jetpack Compose UI screens with high-performance Keel decay math implemented in compiled Lume via JNI (`liblumecore.so`), with an exact Kotlin fallback in `core/`.

- **Working Directory**: `kasane-android/`
- **Architecture & JNI Integration**:
  - `app/src/main/java/app/kasane/LumeCore.kt`: Loads and binds native `liblumecore.so` compiled from Lume source (`tools/build-core.sh`).
  - `app/src/main/jniLibs/*/`: Native pre-built libraries for arm64-v8a, armeabi-v7a, x86_64.
  - `app/src/main/java/app/kasane/`: `KasaneApp.kt`, `MainActivity.kt`, `Store.kt` (habit persistence & state), `PuzzleCountReceiver.kt` (inter-app progress link).
  - `designsystem/`: Complete Lume design system with `LumeColors.kt`, `LumeTokens.kt`, `LumeMotion.kt`, `LumeHaptics.kt`, `LumeType.kt`, `Components.kt`, and `GroupedList.kt`.
  - `ipc/`: `Companion.kt` handling inter-process synchronization with companion apps (Katachi).
- **Core Math Verification**:
  - Keel rolling momentum index: `RMI_t = min(1, RMI_{t-1} * e^(-λ * Δt) + Δ_tier)`.
  - `gradle -PcoreOnly :core:test`: runs pure Kotlin math tests verifying output identical to Lume core.

## Build, Test & Lint Commands (Quiet Mode for Token Saving)

Execute all commands from the `kasane-android/` project folder:

```bash
cd kasane-android

# Run unit & Robolectric tests
./gradlew testDebugUnitTest -q --console=plain --no-scan

# Run a single test class
./gradlew testDebugUnitTest --tests "app.kasane.StoreTest" -q --console=plain --no-scan

# Run pure core math verification (no Android SDK required)
./gradlew -PcoreOnly :core:test -q --console=plain --no-scan

# Build debug APK
./gradlew assembleDebug -q --console=plain --no-scan

# Lint check
./gradlew lintDebug -q --console=plain --no-scan
```

## Android & Cloud Container Engineering Rules (from innovator-repo/memory)

- **No Android Emulator in Cloud Containers**: Cloud containers lack KVM virtualization; do NOT attempt to launch emulators. Use Robolectric (+ Roborazzi where configured) for fast, headless UI and screenshot verification.
- **Compose Performance & Phases**: Defer rapidly changing State reads (such as scroll offsets and animation values) to the Layout or Draw phases using lambda modifiers (`Modifier.offset { ... }`, `Modifier.graphicsLayer { ... }`, `Modifier.drawBehind { ... }`) to avoid re-triggering composition. Never annotate mutable non-snapshot types with `@Immutable` or `@Stable`.
- **Platform Gotchas**:
  - **AccessibilityEvent recycling**: Copy `AccessibilityEvent` fields (`packageName`, `eventType`, `className`) synchronously on API 26–32 before crossing coroutine or async boundaries to avoid recycled event crashes.
  - **Device Admin Policy XML**: Android `<device-admin>` XML must declare at least one active policy (e.g., `<force-lock />`) to be valid on newer Android releases.
  - **System Settings Intents**: Always wrap system settings intents (`com.android.vpndialogs`, Private DNS settings, Device Admin enrollment) with fallback intents and written in-app user directions to prevent `ActivityNotFoundException` on heavily customized OEM ROMs (Samsung OneUI / Android 16).
- **APK Delivery Pattern**: When pushing debug APKs to an `apk/*` orphan branch, use a separate git worktree (`/tmp/apkwt`) and `git add --force` to prevent binary bloat on source branches.

## Token Hygiene & Compaction Keep-List

- **Token Hygiene**: Never read `.gradle/`, `build/`, `captures/`, `*.apk`, `*.aab`, `*.keystore`, or `*.dex`. Use targeted `read_file` with specific line ranges.
- **Cross-Session Memory**: Long-term project memory and cross-session insights live in `innovator-repo/memory` (`MEMORY.md`).
- **Compaction Keep-List**: When running `/compact`, always preserve:
  1. Current task goal and acceptance criteria.
  2. Kotlin/XML files created or modified.
  3. Build, test, or crash stack traces and identified root causes.
  4. Architectural decisions and design trade-offs.
  5. Pending Gradle, test, or APK delivery commands.

