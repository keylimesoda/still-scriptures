# Still Scriptures — project plan

## Purpose and priorities

A standalone Wear OS scripture reader for bedtime reading, designed around
Galaxy Watch 6 Classic and its rotating bezel. No server or phone is required
for bundled scripture navigation and reading.

First-version priorities:
1. Navigate to a scripture and read it or its entire chapter.
2. Read scriptures assigned in this week's Come, Follow Me.
3. Open a meaningful daily verse from that week's readings.

## Implemented in 0.6 / 0.6.1

- Continuous warm serif reading on black, scalable type, one resume position,
  next chapter, optional keep-awake.
- Select volume, then book/chapter/verse in three timer-style rollers.
- Bundled 2026/2027 lesson calendars and curated daily reference pools.
- Native lesson text downloaded on demand; private eight-lesson cache.
- Random opening: choose scope, turn the bezel to flip an open-book animation,
  stop to open a randomly selected verse in context.
- Local hearts; verse QR link for manual phone/Gospel Library handoff.
- Expandable chapter overview; tap a verse for full-screen footnotes.
  Explicit first request downloads official chapter study helps; retain them
  privately for offline use. Notes include source words and cross-references.
  Bundled references open locally; other study aids use phone QR links.
- Come, Follow Me date and actions simplified for a round screen.

## Remaining work

- On-wrist feedback for the new overview, footnotes, return position and rollers.
- Official Declarations, front matter, facsimiles and standalone JST study aids
  are absent from the bundled dataset. Study helps need internet once per
  requested chapter; a completely bundled offline study edition is unmet.
- Theme search (e.g. Atonement, Prayer, Sacrament, Moses).
- Portable saved-list backup. Prefer supported Gospel Library integration if
  one exists; no documented third-party annotation-write API has been found.
  Opening a verse link does not synchronize watch saves.
- Dedicated distribution signing and Store readiness.
- Future calendar years: current refresh URL follows the four-year study cycle;
  future published data and curated selections need confirmation.

## Validation status

Release compilation and installation are recorded per version. Earlier 0.1
unit/emulator checks do not validate later features. No new automated tests
have been run for 0.6. Public releases are early previews with honest gaps.

## Documentation and display polish — October 11, 2026

README now includes the existing app icon, five actual optimized-release captures
from the round Wear OS emulator, feature captions, download links, an offline
availability table and a collapsible developer section. These are documentation
captures, not automated feature tests. No official downloaded prose is pictured.
While capturing, garbled UTF-8 punctuation was found in menu and study labels.
0.6.1 fixes those strings and completes the compact centered Come, Follow Me date.
The optimized patch compiles and was installed in the emulator for captures.
The physical watch was offline; its previous 0.6 install remains in place.
