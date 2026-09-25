# Warning cleanup notes

- Full verification previously passed: `/tmp/farol-controller-final-check-3.log` (`exit=0`): debug/release assembly, debug unit tests, and debug lint.
- Five-minute follow-up: an RSS Parser upgrade trial could not be verified; `bash gradlew :app:lintDebug` timed out after 120s at `:app:compileDebugKotlin`. Both versions were restored to verified `6.1.1`; no unverified dependency upgrade was retained.
- `./gradlew` is not executable here; use `bash gradlew ...`.
- SDK: compileSdk 37 is kept; targetSdk stays at 36 because no Android 17/API 37 target-behavior runtime matrix was run.
- Baseline validation: XML matches exactly the 20 prior findings, all location paths exist, and `git diff --check` passes. The post-baseline `:app:lintDebug` check timed out after 220 seconds at Kotlin compilation; end-to-end filtering is not yet verified (`/tmp/farol-baseline-verify.log`).
- With user approval, `app/lint-baseline.xml` records the 20 accepted warnings below. This suppresses these existing findings rather than fixing them; new unmatched lint findings still surface. Remove baseline entries as their underlying maintenance work is completed.
  - 1 `OldTargetApi`: deferred until Android 17 target behavior is runtime-tested.
  - 9 `GradleDependency` and 3 `NewerVersionAvailable`: deferred toolchain/dependency upgrades. RSS Parser, Compose/Kotlin/Core/Lifecycle remain deferred; previous RSS Parser resolution pulled Kotlin 2.4 metadata incompatible with the current compiler.
  - 7 `UnusedResources`: `notification_dots`, `components`, `license`, `main_dependencies`, `all_dependencies`, `briefing_configurator`, `search_on`. They exist in every translation file, so removing only the base strings would leave translation-only resources and risk `ExtraTranslation`/catalog churn. Base strings and translations are retained for the translation contribution policy.
- Kotlin compiler warnings remaining: 6 unique deprecation diagnostics for `FragmentPagerAdapter` imports and superclass usage in `HideApplicationsPagerAdapter` and `BriefingAdapter`. These require migrating the existing ViewPager integration; no class/file-wide blanket suppression was restored.
- Narrow deprecated legacy calls still suppressed where unavoidable: `LocalBroadcastManager` UI broadcasts, `Dialog.onBackPressed()` fallback, `WindowManager.defaultDisplay` / `Display.getSize` compatibility paths, `AccessibilityEvent.obtain()` / `TYPE_ANNOUNCEMENT` lock-service handoff, `SOFT_INPUT_ADJUST_RESIZE`, `PopupWindow.setBackgroundDrawable`, `ViewCompat.getWindowInsetsController`, `AppWidgetHostView.updateAppWidgetSize`, and drawable opacity overrides.

Remaining major dependency upgrades require an AGP 9.1 / Gradle 9.3.1 migration and Kotlin 2.4-compatible tooling; a trial AGP upgrade failed on the current Kotlin Android plugin configuration. Their current lint notices are now included in the approved baseline; dependency versions are unchanged.
