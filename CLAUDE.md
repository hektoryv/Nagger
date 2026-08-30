# CLAUDE.md

Context for working on this repo. Read this before changing anything.

## What this is

A personal Android habit-reminder app called Nag, built for one user, distributed as a
sideloaded APK. It asks "Did you do pull-ups?" at 23:00 and keeps asking about things
that get postponed. There is no Play Store listing, no other users, no analytics.

The owner uses it daily on a Samsung-family Android phone with a Swedish locale. He
installs updates through Obtainium, which watches this repo's GitHub Releases.

## Working agreement

- The owner is not an Android developer. Explain trade-offs in plain terms and don't
  assume familiarity with Gradle, Compose or the Android lifecycle.
- He interacts with this project mostly from his phone. Prefer changes that don't
  require him to sit at a computer.
- Verify before claiming something works. Much of this project was written without a
  compiler available, and that cost several round trips. See "Verification" below.
- When something breaks, diagnose the actual cause rather than guessing. Past bugs had
  precise causes worth finding: an `unzip` exit code 9 meant a missing file, not a
  corrupt zip.
- Before touching any screen, dialog, or menu, read `docs/FEATURES.md` — the running
  inventory of every user-facing control that currently exists. Update it in the same
  commit as any change that adds, renames, or removes one. This exists because a
  feature can go missing without a single line being deleted: data gets saved
  correctly but nothing in the UI ever shows it again. Check `docs/FEATURES.md`'s
  "Known gaps" section before reporting a task done.

## Build and release pipeline

`.github/workflows/build.yml` runs on every push:

1. Computes `BUILD_NUMBER` as the Actions run number **plus 100**, exported via
   `$GITHUB_ENV`. The offset exists because this repo replaced an earlier one and run
   numbers restarted at 1, which would have produced a `versionCode` lower than the
   build already installed. Android refuses to install a downgrade. Do not remove it.
2. Decodes the `KEYSTORE_B64` repository secret into `signing/nag.jks`.
3. Runs the unit tests.
4. Builds `assembleRelease` and publishes a GitHub Release tagged `v1.$BUILD_NUMBER`
   with the APK attached.

Obtainium reads that Releases feed. If releases stop appearing, updates stop reaching
the phone, so treat a red build as user-facing breakage.

**GitHub Actions expressions have no arithmetic operators.** `${{ github.run_number + 100 }}`
is a syntax error that fails the whole workflow. Arithmetic goes in a shell step.

## The signing key — read this before touching anything related

`signing/nag.jks` is **never committed**. It lives in the `KEYSTORE_B64` repository
secret and `.gitignore` excludes `signing/*.jks`.

Android only allows an in-place update when the new APK carries the same signature as
the installed one. If the key changes, the owner has to uninstall and reinstall, losing
any data not backed up. So:

- Never regenerate the keystore.
- Never commit it, not even temporarily. It's the only reason this repo can be public,
  and public is what lets Obtainium work without an access token.
- Never remove the "fail if the secret is missing" guard in the workflow. An unsigned
  APK builds successfully and then refuses to install, which is a confusing failure.

Current key fingerprint (SHA-256):
`59:01:AA:7B:43:B4:9F:A8:C2:73:16:CA:87:CF:D6:0B:89:23:F6:51:69:68:0B:C2:69:8E:8D:14:29:46:7E:B6`

The keystore password sits in plain text in `app/build.gradle.kts`. That's deliberate:
the password is worthless without the key file, and the key file is the secret.

## Architecture

```
app/src/main/java/com/example/nag/
  MainActivity.kt      navigation, menus, dialogs, notification-tap handling
  data/                Habit, Entry, JSON, SharedPreferences storage, backup
  logic/Schedule.kt    every date calculation in the app
  notify/              alarm scheduling, notifications, broadcast receivers
  ui/                  Compose screens, canvas chart, dialogs
  widget/              home screen widget (RemoteViews, not Glance)
app/src/test/java/     JVM unit tests for the schedule engine
```

**`data` and `logic` deliberately avoid Compose and nearly all Android types.** That
keeps the scheduling rules testable on the JVM without an emulator. Preserve this. If
something in `logic` needs a `Context`, the design has gone wrong — pass the data in.

### Invariants worth protecting

- **Occurrences are anchored to `startDate`, never to the last completion.** Doing
  something a day late must not shift future occurrences. This is what keeps a pair of
  every-other-day habits interleaved instead of slowly colliding, which was an explicit
  user request. There's a test for it.
- **A missed day stays visibly missed** on the calendar even after the user recovers.
  Also an explicit request. Don't "helpfully" backfill it.
- **`MissPolicy` is per-habit.** `ROLL_OVER` nags every night until done; `SKIP` marks
  the day missed and waits for the next slot. For `TIMES_PER_WEEK`, `SKIP` means "stay
  quiet until you'd have to go every remaining day this week".
- **Storage migrates forward.** `Json.habitFromJson` still reads v1 and v2 records
  (`type` instead of `kind`, missing `startDate`, a single `lastDone` instead of a log).
  The owner has real history in this app. Never ship a change that drops it.

## Verification

Run the unit tests before pushing:

```
./gradlew testReleaseUnitTest
```

These cover the schedule engine and run on the JVM in seconds — no emulator. If you
change `Schedule.kt`, add cases. They have caught real behaviour, not just compilation.

For the Compose UI and the widget there is no automated coverage, and CI only proves
they compile. Say so plainly rather than implying a change is verified when it isn't.

Lint workflow changes with `actionlint` before pushing. A malformed workflow is a
wasted round trip for the owner.

## Environment gotchas learned the hard way

- **Battery optimisation kills the alarm.** The app must be set to Unrestricted in
  Android's battery settings or the 23:00 check-in silently never fires. This is the
  single most common cause of "the app stopped working".
- **Exact alarms** need `USE_EXACT_ALARM` (sideloaded, so the Play Store restriction on
  it doesn't apply). The code falls back to inexact alarms if permission is refused.
- **RemoteInput notification actions need a mutable `PendingIntent`.** Everything else
  must stay immutable.
- **Notification action buttons need real icons.** Passing `0` renders nothing on some
  OEM skins, which looked like a missing-feature bug.
- **Alarms are cleared on reboot**, hence `BootReceiver`.
- **Compose versions are pinned** via the BOM in `app/build.gradle.kts`. Bumping the
  Kotlin version, AGP, or the BOM independently tends to break the build. Change them
  together, deliberately, and expect to verify.
- **Obtainium wants the bare repo URL** — no `.git` suffix, or it can't find releases.

## Adding dependencies

Prefer not to. The widget uses RemoteViews rather than Glance and the chart is drawn on
a Compose `Canvas` rather than pulling in a charting library, specifically so there's
less to break when versions move. `androidx.documentfile` is the only concession, for
SAF folder access in the backup.

## Discussed but not built

Ideas raised and deliberately deferred, roughly in the order they seemed worth doing:

- Per-habit reminder times (currently one global check-in time for everything).
- Multiple entries per day for amount-tracking habits, i.e. sets rather than a daily total.
- Notes attached to an entry.
- Snooze durations other than one hour.
- An in-app "check for updates" button (Obtainium covers this).

## User-facing behaviour that is intentional, not a bug

- "Until done" notifications are `setOngoing(true)` so they can't be swiped away. This
  is the point of the feature.
- The stats window excludes today, so an evening that hasn't happened yet doesn't drag
  the completion percentage down.
- `perfectDayStreak` ignores `TIMES_PER_WEEK` habits, which have no per-day obligation.
- Future calendar days can't be ticked.
