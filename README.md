# Nag

A personal nightly check-in app for Android. It asks "Did you do pull-ups?" at 23:00 and won't let go of the stuff you keep putting off.

## What it does

Four ways to schedule something:

- **Every N days** — anchored to a start date. Start one today and another tomorrow to interleave them.
- **Weekdays** — fixed days, e.g. Mon / Wed / Fri.
- **N× a week** — a loose target counted Monday to Sunday, any days you like.
- **Until done** — asks every night until you tick it. The notification is pinned so it can't be swiped away.

Per habit you can also set whether a missed day keeps nagging or just gets marked missed, whether it counts a number (reps, minutes, pages), a colour, and a pause switch for holidays and injuries.

**Today** lists what's outstanding right now. **Calendar** shows a month grid where each day carries one thin bar per habit: solid for done, faded for scheduled, grey for missed. Missed days stay visibly missed even after you get back on track. Above it sits a 30-day completion percentage, a perfect-day streak, and per-habit hit rates.

Tap any habit for its own page: a bar chart of amounts over time with a trailing-average trend line, personal best, totals, streaks, and the full history. Habits that don't count anything get a sessions-per-week chart instead.

There's a home screen widget with today's outstanding items — tap one to tick it without opening the app.

Notifications carry **Yes, done** and **Not yet** buttons; "Not yet" comes back an hour later. For habits that count a number you can type it straight into the notification. Tapping the notification body opens a direct yes/no prompt.

## Getting the app

### First time

This repo is a normal Android project. Push to it and CI builds a signed APK and
publishes it as a GitHub Release.

It needs one repository secret: **Settings → Secrets and variables → Actions**, named
`KEYSTORE_B64`, containing the base64 of the signing keystore. Without it the build
fails with a clear message. See "About the signing key" below.

### Updating later

Push a change. CI builds it, publishes a release, and Obtainium picks it up.

### Updating without the manual steps

Install Obtainium (github.com/ImranR98/Obtainium) and point it at this repo's URL — the
bare address, with no `.git` suffix, or it won't find the releases. It watches for new
releases and offers to install them, which is about as close to a real app store as you
get without publishing.

### On a computer instead

Open the folder in Android Studio and use **Build → Build Bundle(s) / APK(s)**. The
Gradle wrapper is included so it should sync without complaint. Decode the keystore
base64 into `signing/nag.jks` first, or the release build comes out unsigned and won't
install over an existing copy.

## After installing

- Allow notifications on first launch.
- **Settings → Apps → Nag → Battery → Unrestricted.** Without this some phones will kill the alarm and you'll get nothing at 23:00. This is the most common reason reminder apps go quiet.
- Turn on auto-backup from the ⋮ menu and pick a folder. Every change rewrites `nag-backup.json` there, so a bad update or an uninstall can't take your history with it.

## About the signing key

The key is **not** in this repo. It lives in a repository secret called `KEYSTORE_B64`,
holding the base64 of a Java keystore, and the workflow writes it to `signing/nag.jks`
before building.

This matters because Android only allows an in-place update when the new APK carries the
same signature as the installed one. A fresh CI runner has no keystore, so without a
stable key every build would generate a random one and every install would demand an
uninstall first.

Keeping the key out of the repo is what lets this repo be public, which in turn is what
lets Obtainium watch it without an access token. The keystore password sits in
`app/build.gradle.kts` in plain sight, which is fine: the password is worthless without
the key file, and the key file is the part that's secret. Override it with a
`KEYSTORE_PASSWORD` environment variable if you'd rather it weren't.

If you lose the key you can generate a new one, but you'll have to uninstall and
reinstall the app once, losing anything not backed up.

## Project layout

```
app/src/main/java/com/example/nag/
  MainActivity.kt      navigation, menus, dialogs
  data/                Habit, Entry, JSON, storage, backup
  logic/Schedule.kt    every date calculation in the app
  notify/              alarms, notifications, receivers
  ui/                  Compose screens, chart, dialogs
  widget/              home screen widget (RemoteViews)
```

`data` and `logic` deliberately avoid Compose and most Android types, so the scheduling rules can be compiled and exercised on their own.

## How it works

`Scheduler` books one exact alarm for the next check-in. `ReminderReceiver` wakes up, posts a notification for everything due, refreshes the widget, then books tomorrow's alarm. `BootReceiver` re-books after a reboot, since Android clears alarms on restart.

Fixed schedules are anchored to a habit's start date rather than to when you last ticked it, so doing something late never shifts the rhythm — that's what keeps a pair of every-other-day habits interleaved instead of slowly colliding.

Data lives in SharedPreferences as JSON: a list of habits and a flat log of completions. Older data formats are migrated automatically on first launch.

## Things you might want to change

| What | Where |
|---|---|
| App name | `res/values/strings.xml` |
| Snooze length | `Scheduler.snooze(...)`, the `minutes` default |
| Stop pinning "until done" notifications | `Notifications.kt`, remove `setOngoing(true)` |
| Stats window (default 30 days) | `Schedule.statsFor(...)` |
| Bar colours | `HabitColorsArgb` in `data/Habit.kt` |
| Widget row limit | `MAX_ROWS` in `widget/NagWidget.kt` |

Minimum Android version is 8.0 (API 26).
