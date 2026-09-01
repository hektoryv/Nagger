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

Last updated: after cross-day dragging, push-away collision handling, and a fix for
the week view not refreshing after a move (commit history around `9000053` and the
fixes that follow it).

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
  - **Sync class schedule now** — only shown once a link is saved; re-fetches and
    re-parses the ICS feed immediately (also happens automatically on app open and
    nightly — see "How the schedule stays current" below).
  - **Manage tasks** — opens the full-screen task list (see below).
  - **Day shape** — opens a dialog to change when the planner's day starts, when
    dinner is, and when wind-down starts (see below).
  - **Recalculate schedule** — forgets every not-yet-past one-off task's placement so
    it gets freshly re-decided on the next view. See "How the schedule stays current".
- One shared **"Add"** floating action button, present on both tabs, offering:
  - **Add check-in** → the habit dialog.
  - **Add task** → the task dialog.
  Both options are available from either tab — there's no longer a tab-dependent FAB.

## Today tab (TodayScreen)

- Header card: status line + subtext, both driven off whether there are any habits
  and/or any scheduled blocks today.
- **Today's schedule** section — today's classes and tasks from the planner, in time
  order. A task row has a checkbox (marks that occurrence done/not-done). Tap a row →
  the shared scheduled-block detail dialog (see below).
- **Due today** — habit rows with a checkbox to mark done; tap the row → habit detail
  screen.
- **Done today** — habit rows with a checkbox to un-mark; tap → habit detail screen.
- **Coming up** — active habits not due or done today; tap → habit detail screen.
- **Paused** — paused habits; tap → habit detail screen.
- **Finished** — completed "until done" habits; tap → habit detail screen.

## Calendar tab (CalendarScreen)

- Stats card: rolling 30-day completion %, current perfect-day streak, per-habit
  done/scheduled/streak breakdown; tap a habit row → habit detail screen.
- Week nav: prev/next-week arrows, "Week of …" label.
- Day header row: weekday letters, day-of-month + habit status dot bars per day; tap a
  day → day-detail dialog (lists habits due/done/missed that day; checkbox to toggle,
  disabled for future dates).
- Week timeline: an hour-by-hour (06:00–24:00) grid spanning all 7 days as one shared
  canvas, showing every `ScheduledBlock` positioned by actual day/start time/duration,
  plus shaded dinner and wind-down bands per day (from the current day shape). Locked
  blocks (classes, by default) get a **red border**; flexible blocks (tasks) get a
  neutral outline. Tap any block → the shared scheduled-block detail dialog. A one-off
  task's block can also be **held and dragged** anywhere in the grid — a new time, a
  new day, or both — snapping to the nearest 15 minutes and nearest day column on
  release (see "How the schedule stays current"); it dims while dragging. If the new
  spot overlaps another already-placed one-off task, that other task gets pushed to
  the next free slot the same day rather than sitting underneath it.
- Legend: two rows — Done/Due/Ahead/Missed (habit dots), and Locked/Flexible/Dinner/
  Wind-down (timeline blocks and bands).

## Habit dialog (create/edit a check-in)

Title; frequency kind — **Every N days** (interval number field, start
today/tomorrow toggle for new habits), **Weekdays** (multi-select chips),
**N× a week** (1–7 chip row), **Until done** (no sub-fields); "keep asking if I miss
it" toggle (hidden for Until done); "count an amount" toggle + unit text field; an
8-color picker. Save / Cancel / Delete (edit only, with a confirm sub-dialog).

## Task dialog (create or edit a task)

Title; duration as separate **Hours** and **Minutes** number fields; repeat choice —
**One-and-done**, **Daily**, or **A few times a week** (with a 1–7 count field); a
**Hard deadline** toggle that reveals an Android date picker. Save / Cancel, and when
editing an existing task, **Delete** (with a confirm sub-dialog). Editing an existing
task clears its current placement so it gets re-decided with the new duration/
deadline — see "How the schedule stays current".

## Manage tasks screen (⋮ menu → Manage tasks)

Full-screen list of every task, placed or not — the only place an unplaced task (no
room for it yet) can be reached at all. Tap a row → edit (task dialog). FAB (+) →
create.

## Scheduled block detail dialog (tap any class or task, either tab)

Shows: Type (Class/Task), Day, Time span, Duration, Location (classes only, if
present), Deadline (tasks only, if set), Status (Locked / Flexible / Skipped, in plain
English), Done (tasks only, Yes/Not yet). Buttons shown depending on context:
- **Mark done / Mark not done** — tasks only.
- **Move to a specific time** — one-off (not recurring) tasks only, and only once
  they've actually been placed. Opens the Android time picker; keeps the same day,
  changes the time (dragging in the week timeline is the equivalent for changing the
  day too — this button is a typed, same-day-only alternative). See "How the schedule
  stays current" for what this does and does not survive.
- **Make flexible / Skip / Reset to locked** — classes only, per occurrence; the rest
  of that recurring class is unaffected.
- **Delete task** — tasks only.

## Day shape dialog (⋮ menu → Day shape)

Four tap-to-pick times (each opens the Android time picker): **Day starts**,
**Dinner starts**, **Dinner ends**, **Wind-down starts**. These are exactly what the
placer avoids scheduling flexible tasks over/before/after. Save validates the order
(day start < dinner < wind-down) and shows an inline error rather than saving if it
doesn't hold; Cancel discards changes.

## Planner link dialog (⋮ menu → class schedule link)

ICS URL text field; explanatory text; "Syncing…" or an error message inline; **Remove
link** button (only shown once a link exists — clears the link and any classes from
it). Save & sync (persists the URL and immediately fetches+parses) / Cancel.

## Habit detail screen (tap any habit, either tab)

Title, rhythm text, paused indicator. Range chips (30 days / 90 days / All). Chart —
bar chart of amount-over-time (amount-tracked habits) or sessions-per-week
(others). Stat rows: completed/scheduled, hit rate, current streak, and for
amount-tracked habits also best/total/average, plus a this-week count for N×/week
habits. Pause/Resume button. Edit button (→ habit dialog). History list (up to 60
entries).

## Home screen widget

Today's schedule rows (classes/tasks, read-only, colored dot instead of a checkbox,
tap opens the app) followed by today's habit rows (title, color dot, checkbox to mark
done straight from the home screen), capped at 6 rows total. Tapping the header opens
the app. Uses the same day shape and the same persisted task placements as the app, so
it won't show a one-off task somewhere different than the app does.

## How the schedule stays current

- **The ICS feed** re-fetches on app open (if a link is saved), on demand via "Sync
  class schedule now", and once nightly piggybacked on the existing habit check-in
  alarm.
- **Where a task lands** is decided once and then stays put, rather than being
  recomputed from scratch every time a week is viewed — this is what fixed a bug where
  a one-off task could appear on an already-past day and on every future week at once.
  A one-off task's placement is only decided (or re-decided) when something asks to
  view a week that could contain it — there's no separate nightly "replan" pass beyond
  the ICS re-sync above, so an unplaced task only gets a fresh attempt once the app (or
  widget) is actually asked about that day. It is never placed on a day before today.
  A recurring task (daily / times-a-week) is still recomputed fresh every time its week
  is viewed — that's correct, since it's supposed to happen again each week.
- **Recalculate schedule** (menu item) clears every one-off task's not-yet-past
  placement, including one set with "Move to a specific time" or dragged in the week
  timeline — there's currently no way to tell an auto-placed occurrence apart from a
  manually moved one, so Recalculate discards both and lets them be freshly re-decided.
- **Editing a task** (duration, deadline, etc.) clears its existing placement for the
  same reason — the old slot may no longer be the right size or before/after the new
  deadline.
- **Moving a task manually** — by dialog or by drag — doesn't check against dinner/
  wind-down (it trusts the time you pick, the same way a class's "Make flexible"/"Skip"
  trusts the choice you make), but it does check against other one-off tasks: one that
  now overlaps the moved task gets pushed to the next free slot the same day, the same
  way the placer would have found it a spot originally. If there's no free slot left
  that day, the pushed task is left overlapping rather than losing its placement.
  Locked events are never pushed, and a recurring task's occurrence (not persisted,
  see above) can still silently overlap a moved one-off task — that's a real remaining
  gap, not handled by the push-away logic.

---

## Known gaps (confirmed by reading the code, not memory — check off as fixed)

- **Moving a task only exists for one-off tasks** — a recurring task's occurrence has
  no persisted slot to move at all, whether by drag or by the dialog button (see "How
  the schedule stays current").
- **Push-away doesn't account for recurring task occurrences**, only other one-off
  tasks — a recurring task can still silently overlap a task that was just dragged
  onto it. Locked events are never pushed (correct — they shouldn't move).
- **Not yet verified on a real device** — the drag gesture (long-press-then-drag,
  `detectDragGesturesAfterLongPress`, now tracking both the day column and the time)
  was written and reviewed carefully but the sandbox this was built in can't run the
  app to confirm it feels right alongside the screen's vertical scroll, that the
  column-width math lines up with the visible grid, or that push-away looks right when
  it happens; say if any of that feels off.
- **Dinner/wind-down aren't adjustable by long-pressing them directly in the week
  view**, as originally asked for — they're a global setting, changed instead through
  the menu-based **Day shape** dialog (a per-day-instance long-press would be a
  confusing way to change something that isn't per-day anyway, but if the intent was
  specifically "hold the shaded band to nudge it," that's not what this does).
- **`Placer` is still a pure, one-week-at-a-time function** (see `Placer.kt`'s doc
  comment) — `PlannerScheduler` wraps it with persisted one-off-task placement to fix
  the cross-week duplication bug, but a recurring task's exact time on a given day can
  still shift if you view the same week again after adding/removing something else
  that week (expected — only the day, driven by the weekly quota, is meant to be
  stable; the exact minute isn't promised).

## Fixed this round

- **A moved task's block used to visually disappear until switching tabs and back.**
  Root cause: `AppState.plannerSchedule()` was writing to `plannerTaskAssignments` as
  a side effect, but that same field is also a dependency key of the Compose
  `remember` block that calls `plannerSchedule()` — a function writing the very state
  its caller is keyed on, while the caller is computing, is a self-referential trap
  that left the week view stuck on a stale render. Fixed by making `plannerSchedule()`
  a pure read; every actual mutation already updated the field itself independently.
