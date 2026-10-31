# Lume family — final build spec (Now-first)

Applies to Sigil, Vow, Ren, Kasane and Katachi. Reference images (view them): `kasane-android/design/lume-nowfirst-main.png`
(drawer closed) and `kasane-android/design/lume-nowfirst-drawer.png` (drawer open), on branch `claude/awesome-planck-qerygs`.

## 1. One screen, no tab bar
- Each app opens on ONE main screen: small header (emblem, app name and one context word, gear button for Settings), then a
  **hero** with the single thing that matters now, then a **pull-up drawer** at the bottom edge.
- The drawer is a bottom sheet with a grab handle. Closed: a title, a short summary on the right and 2-3 labelled shortcut
  chips. Open (swipe up or tap the handle): the full list for that app. Swipe down returns to the hero.
- Shortcut chips open one more screen with a back arrow (`< AppName`); no bottom navigation bar anywhere. Remove any existing
  `NavigationBar`/tab row and fold its screens behind chips or the drawer.
- Secondary screens that already exist keep their code; only the way to reach them changes.
- Keep it minimal: remove anything not listed below from the main screen. Do not delete data or features that still have a
  shortcut. Cut features only where this spec says so.

| App | Hero | Primary action | Drawer title / contents | Shortcut chips |
|---|---|---|---|---|
| Sigil | "What is on your mind?" with a New note button, then "Continue: <last note title>" | New note | Notes: pinned, then recent, with search at the top; Vault row | Vault, Checkup, Clipboard |
| Vow | focus ring (time reclaimed today), "Focus is holding", active profile | profile on/off switch in the drawer | Controls: profile switches, cooling-off, today's top blocked | Stats, Rules |
| Ren | due-cards ring with count, "5 new, about 9 min" | Start session | More to learn: path, reader, progress, word of the day | Path, Reader, Progress |
| Kasane | NEXT UP task title and time, big check button, "4 of 7 done today" | check the next task off | Today: Overdue, Today, Tomorrow groups (Reminder-style order); add button | Lists, Add |
| Katachi | the next puzzle preview and a Solve button, "Puzzle 7 of 10" | Solve | Today: the three puzzle types with counts, best time | Stats, Streak |

**Kasane has no statistics.** Remove the Momentum and Week screens, tabs and every "% momentum" text (app and widgets and
what it writes into Sigil's diary line). Keep the calculation code that orders the "Next up" pick and notifications, only
remove what the user sees.

## 2. Look
- Shared neutrals that follow the time of day exactly as `LumeColors` does today (Washi, Paper, Warm graphite, Obsidian).
  Do not change surface or semantic colours.
- One accent per app: Sigil sapphire `#2A68B7` / dark `#76B2F5`; Vow deep cyan `#0B6E8A` / dark `#6CCBE0` (new, replaces teal);
  Ren akane `#BB443A` / dark `#F18477`; Kasane indigo `#3F52AF` / dark `#97A8F7`; Katachi jade `#1E7D68` / dark `#5DD2B0`.
  Errors and overdue use the existing terracotta slip colour, never an app accent.
- Rounded cards (16 dp), large hero type, hairline dividers, Lume springs and haptics as today. Light and dark both work.
- Minimum body text 14 sp, labels 12 sp. Tap targets 48 dp.
- Hero type in the drawer-closed state must leave the drawer peek (about 170 dp) fully visible on a 360x780 dp screen.

## 3. Companions: absent means hidden
- A cross-app feature may only be composed if the companion is installed AND its provider answers. Put this in one small
  helper per app (`Companion.available(context, packageName)`); any exception or null result means "absent".
- When absent: no button, no pill, no empty row, no error text. Nothing else moves or breaks.
- Never add a settings card that lists companions.
- Existing contracts (keep): status providers `content://<package>.provider/status`; deep links `sigil://note/quick`,
  `ren://session/quick`, `katachi://puzzle/next`; broadcasts `app.kasane.action.LOG_CHECK|LOG_COUNT`, `app.lume.action.FLOOR_MODE`,
  `app.lume.action.RULE_EVENT`.
- New or extended contracts (each app owns its side):
  - Sigil provider `com.sigil.app.provider/status`: `noteCount`, `sealedCount`, `clipArmed` (0/1), `lastTitle`, `lastEditedAt`.
  - Vow provider adds `focusOn` (0/1), `profileName`; new deep link `vow://focus/toggle` (needs no unlock if the profile is being turned ON; turning OFF still goes through the cooling-off rules).
  - Ren provider keeps `due, retention, streak, doneToday`; adds `newToday`, `minutes` (estimate).
  - Katachi provider keeps `solved, target, doneToday`; adds `streak`.
  - Kasane provider keeps its columns minus momentum; adds `nextTitle`, `nextAtMinute` (or -1), `openCount`, `doneCount`.
- Provider access stays protected exactly as it is today (same permission / signature trust mechanism). Do not weaken it.

## 4. Widgets (decided with Jev)
Home-screen widgets are Glance (Kasane, already) or the app's existing widget stack. They cannot animate or take text; they use taps
that run actions, checkboxes, toggles, progress bars and state swaps. Same neutrals and accent as the app, rounded 22 dp.
Every widget must keep working when it has no data or a companion is missing.

| App | Widget | Behaviour |
|---|---|---|
| Kasane | Horizon list (exists) | keep: grouped Overdue/Today/Tomorrow/Next 7 days/Later, checkbox, snooze, tap row opens that task, list chip; Ren/Katachi/Sigil pills at the bottom only if installed |
| Kasane | Next up (small, new) | next task title and time, big Done button, "N left" |
| Kasane | Universal Today (new) | one line per installed app: next task, cards due, puzzles left, focus state, last note. Each line opens its app. Lines of missing apps are absent |
| Kasane | Universal Dock (new) | row of launch tiles with a live badge (tasks left, cards due, puzzles left, focus on/off, notes), only for installed apps |
| Kasane | Universal Quick add (new) | buttons: new task, new note, start review, solve puzzle, start focus; each only if its app is installed |
| Ren | Due and Start (new) | progress ring with count and a Start session button |
| Ren | Word of the day (new) | Japanese word; tap swaps in the reading and meaning in place; tap again swaps back |
| Katachi | Today (new) | ten segments for solved/left and a Solve button |
| Vow | Focus switch (new) | on/off switch for the active profile and time reclaimed today |
| Sigil | Quick note (new) | New note button and the last edited note title |
| Sigil | Scrub clipboard (new) | one tap scrubs the clipboard now, shows "Scrubbed" for a moment |

Remove Kasane's older Today, Coming up and Habits-and-tasks widgets (their receivers, XML, strings, manifest entries).
Universal widgets live in Kasane only (it already reads every other app); no sixth app.

## 5. Delivery rules for every app
- Work on the app's own `clean/<app>` branch only, in the app's own folder. Never touch another app's folder.
- Each app keeps its package name and its signing setup. Bump versionCode by one and the minor version (Sigil 1.3.0, Vow 1.3.0,
  Ren 1.3.0, Kasane 0.5.0, Katachi 0.3.0). Data formats must stay readable: old data loads unchanged.
- Run the app's unit tests and a release-variant Kotlin compile before pushing; fix what you break. Add unit tests for any new
  pure logic (grouping, ordering, provider row mapping, companion helper).
- Commit as `Claude <noreply@anthropic.com>`. Never print, commit or ask for signing keys or tokens. Never put another
  person's or account's name, email or repository URL into code, docs or commit messages.
- Final reply: what shipped, what is untested, anything you could not do, and the final commit hash.
