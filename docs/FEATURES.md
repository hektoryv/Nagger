# FEATURES.md — running inventory

This is the single source of truth for what the app actually does right now: every
screen, button, dialog field, and menu item. It exists because features have gone
silently missing when rewriting nearby code without checking what was already there
(e.g. a task's hard deadline being saved and used by the placer, but nowhere visible
in the UI after creation — the data never "vanished," but there was no way to see it,
which is indistinguishable from missing if you're the one using the app).

**Rule: read this before starting a change. Update it in the same commit as any change
that adds, renames, or removes a user-facing control.** "User-facing control" means
anything a real tap/screen touches — a button, a field, a menu item, a section of a
screen — not internal refactors.

Last updated: after the Today-screen/times-per-week bug-fix round (commit `015a7b6`).

---

## App shell (MainActivity)

- Bottom nav: **Today** / **Calendar** tabs.
- Top bar "⋮" overflow menu:
  - **Check-in time** — opens the Android time picker, sets the one global habit
    reminder hour/minute.
  - **Allow exact alarms** — only shown if not already granted; opens Android's exact
    alarm settings.
  - **Turn on auto-backup** / **Auto-backup: <folder>** — SAF folder picker; once set,
    every change rewrites `nag-backup.json` there automatically.
  - **Export backup** — save a JSON snapshot (habits + log + reminder time) to a
    chosen file.
  - **Restore backup** — load habits + log + reminder time from a chosen JSON file.
  - **Add class schedule link** / **Class schedule link** — opens the planner link
    dialog (see below).

## Today tab (TodayScreen)

- Header card: status line + subtext, both driven off whether there are any habits
  and/or any scheduled blocks today.
- **Today's schedule** section — today's classes and tasks from the planner, in time
  order. Tap a row → the shared scheduled-block detail dialog (see below).
- **Due today** — habit rows with a checkbox to mark done; tap the row → habit detail
  screen.
- **Done today** — habit rows with a checkbox to un-mark; tap → habit detail screen.
- **Coming up** — active habits not due or done today; tap → habit detail screen.
- **Paused** — paused habits; tap → habit detail screen.
- **Finished** — completed "until done" habits; tap → habit detail screen.
- FAB (+) — opens the **habit** dialog (create a new check-in). Note this is a
  different dialog/data type than the Calendar tab's FAB — see Known gaps.

## Calendar tab (CalendarScreen)

- Stats card: rolling 30-day completion %, current perfect-day streak, per-habit
  done/scheduled/streak breakdown; tap a habit row → habit detail screen.
- Week nav: prev/next-week arrows, "Week of …" label.
- Day header row: weekday letters, day-of-month + habit status dot bars per day; tap a
  day → day-detail dialog (lists habits due/done/missed that day; checkbox to toggle,
  disabled for future dates).
- Week timeline: an hour-by-hour (06:00–24:00) grid, one column per day, showing every
  `ScheduledBlock` for that day positioned by actual start time/duration. Locked
  blocks (classes, by default) get a **red border**; flexible blocks (tasks) get a
  neutral outline. Tap any block → the shared scheduled-block detail dialog.
- Legend: Done/Due/Ahead/Missed color key — for the habit dots only, not the timeline.
- FAB (+) — opens the **task** dialog (create a new planner task). Different
  dialog/data type than the Today tab's FAB — see Known gaps.

## Habit dialog (create/edit a check-in)

Title; frequency kind — **Every N days** (interval number field, start
today/tomorrow toggle for new habits), **Weekdays** (multi-select chips),
**N× a week** (1–7 chip row), **Until done** (no sub-fields); "keep asking if I miss
it" toggle (hidden for Until done); "count an amount" toggle + unit text field; an
8-color picker. Save / Cancel / Delete (edit only, with a confirm sub-dialog).

## Task dialog (create a task)

Title; duration as separate **Hours** and **Minutes** number fields; repeat choice —
**One-and-done**, **Daily**, or **A few times a week** (with a 1–7 count field); a
**Hard deadline** toggle that reveals an Android date picker. Save / Cancel.

## Scheduled block detail dialog (tap any class or task, either tab)

Shows: Type (Class/Task), Day, Time span, Duration, Location (classes only, if
present), Status (Locked / Flexible / Skipped, in plain English). **Delete task**
button, tasks only. Close.

## Planner link dialog (⋮ menu → class schedule link)

ICS URL text field; explanatory text; "Syncing…" or an error message inline. Save &
sync (persists the URL and immediately fetches+parses) / Cancel.

## Habit detail screen (tap any habit, either tab)

Title, rhythm text, paused indicator. Range chips (30 days / 90 days / All). Chart —
bar chart of amount-over-time (amount-tracked habits) or sessions-per-week
(others). Stat rows: completed/scheduled, hit rate, current streak, and for
amount-tracked habits also best/total/average, plus a this-week count for N×/week
habits. Pause/Resume button. Edit button (→ habit dialog). History list (up to 60
entries).

## Home screen widget

Today's habit list: title, color dot, checkbox (tap to mark done). Tapping the header
opens the app. Habit-only.

---

## Known gaps (confirmed by reading the code, not memory — check off as fixed)

- **Task deadline is invisible after creation.** `PlannerTask.deadline` is saved and
  used by `Placer` to prioritize placement, but `ScheduledBlock` doesn't carry it, so
  neither the scheduled-block detail dialog nor the Today's-schedule row ever show it.
  This is what "the hard deadline feature is gone" was — the data isn't lost, but
  there is genuinely nowhere in the UI it appears.
- **No way to edit a task once saved** — only delete, and only reachable by tapping a
  *placed* occurrence. A task that never got placed anywhere this week (no room) has
  no dialog that can reach it at all — it's effectively invisible and undeletable
  until it happens to get placed.
- **No skip/make-flexible control on a class instance.** The spec's "tap a class
  instance → Skip / Self Study" and the `LockState`/`PlannerOverrides` data model
  exist and work, but no UI sets them — every event is permanently `LOCKED`.
- **`DayShape` (day-start 08:00, dinner 18:00–19:00, wind-down 22:00) is invisible.**
  It's why the placer avoids ~17:00–19:00, but nothing in the UI shows the dinner
  window, wind-down cutoff, or lets you change them — the avoidance looks like
  unexplained behavior rather than a deliberate rule.
- **No nightly auto-replan or missed-task carryover.** The spec called for both;
  neither exists. Sync only happens when you save the link or open the app.
- **No manual "recalculate" button independent of editing the feed URL**, and no way
  to clear/remove a saved feed link (only Save & Cancel in the link dialog).
- **The Today and Calendar tabs' "+" buttons open unrelated dialogs** (habit vs.
  task) with no visual cue they're different kinds of things — worth a UX pass, not
  necessarily a bug.
- **Widget doesn't show planner tasks/classes**, only habits.

## Explicitly out of scope for now (not gaps, deferred on purpose)

- Interpersonal/shared scheduling (seeing when two people are both free).
- Per-habit reminder times (one global check-in time for all habits).
- Multiple entries per day, notes on an entry, snooze durations other than 1 hour.
