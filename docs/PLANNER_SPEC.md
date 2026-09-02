# Everything Planner — design spec (pre-implementation)

Captured from planning discussion, not yet built. This is the spec to start
coding from next session — read this before writing any code for the
planner feature.

## What it is

An auto-scheduling day/week planner, separate in concept from Nag's habit
tracking (Nag asks "did you do X"; this decides "when should you do X").
Ingests a school timetable (ICS feed) as fixed events, takes tasks with
duration estimates and optional deadlines, and places everything into the
week automatically using a simple heuristic — not a constraint solver.

Interpersonal scheduling (seeing when two people are both free) is an
explicitly deferred future idea. Not in scope now, don't design for it yet.

## Core entities

- **Event** — comes from the ICS feed (e.g.
  `https://cloud.timeedit.net/kth/web/stud02/ri.ics?...`). Fixed time,
  immovable by default. Per-instance override state (see below) — a skip or
  flex applies to one occurrence, not the whole recurring class.
- **Task** — user-entered. Duration estimate (e.g. "2h"), optional hard
  deadline, optional recurrence (e.g. "2x/week" bouldering, daily training).
  Placed automatically by the scheduler; user can also manually move a task
  and pin it.
- **Day shape config** — work hours, dinner slot, wind-down/no-go window
  before sleep. Small set of constants, not per-task.

## Per-instance override state (the "red border" model)

Every calendar block — event or task — has a locked/flexible state, shown
as a red border in the weekly view when locked:

- ICS events are **red-bordered (locked) by default**.
- Tapping a specific class instance offers **Skip** (removed for that
  occurrence only) or **Self Study / Make Flexible** (that occurrence
  becomes a movable block the scheduler can place elsewhere).
- A manually-placed task can be pinned by the user, which also gives it the
  red border — the nightly replan must never move a locked block.
- This is instance-level state, not definition-level. Keyed by (source
  event/task id, occurrence date), not by the recurring class itself.

## Scheduling: greedy heuristic, not a solver

Deliberately not an optimizer. Walk the week's free slots chronologically:
1. Place all locked/fixed blocks first (ICS events, pinned tasks).
2. Place remaining tasks in priority order — deadline urgency first, then
   even daily distribution — into remaining free slots, respecting day
   shape config (don't schedule over dinner, don't run past wind-down).
3. Leave everything else as open free time.

This should be testable the same way `Schedule.kt` is tested today: pure
functions on plain data, no Android/Compose types, JVM unit tests over
scenarios (deadline pressure, missed-task carryover, locked blocks staying
put).

## Replanning triggers

Replan happens in three cases, all running the same placer function:
1. **Nightly** — also re-fetches the ICS feed at this point (catches
   moved/cancelled classes) and rolls forward anything not completed
   (missed task/habit gets elevated priority in tomorrow's pass — this is
   how "didn't do the insurance thing" or "skipped pushups" get pulled
   into tonight's replan without any special-casing beyond "incomplete
   items feed back in with higher priority").
2. **Manual "Recalculate" button** — user-triggered re-run of the placer
   for when something needs to be somewhere specific right now, without
   waiting for the nightly cycle.
3. Locked/red-bordered blocks are never touched by any replan — only
   flexible blocks move.

## UI

- **Weekly view** is the primary screen — as much as comfortably fits on a
  phone screen at once. Locked blocks (events by default, pinned tasks)
  get a red border; everything else is visually "movable."
  - Weekly view is the one screen worth investing real design time in;
    per earlier scoping discussion, drag-to-reflow is the highest-risk,
    highest-iteration part of this feature — more so than the placement
    algorithm itself. Consider shipping a simpler list-based "today" view
    first to validate the placement logic feels right before building
    the drag interaction.
- Tap a class instance → Skip / Self Study (Make Flexible).
- Manual recalculate button, always available.
- ICS sync happens automatically as part of nightly replan; no separate
  sync UI needed unless the automatic path proves unreliable.

## Suggested build order (from prior scoping)

1. Data model (Event, Task, day-shape config, override state).
2. ICS ingestion + parse + diff against previous fetch.
3. Greedy placer (the algorithmic core, unit-testable).
4. Missed-item carryover (reuses the placer, just re-run with updated
   completion state).
5. Simple list-based daily view, to validate placement quality early.
6. Weekly view with drag-to-move and the red-border lock UI.
7. Per-instance override persistence (skip/flex), if not already covered
   by step 1's data model.

## Open questions for next session

- New app vs. new module inside Nag vs. separate top-level package in the
  same repo — not decided yet.
- Where task/event data lives (SharedPreferences like Nag, or something
  else — the data volume here is bigger than habit logs).
- Exact ICS diffing behavior when a class time changes after a flex/skip
  override was already set on that occurrence.
