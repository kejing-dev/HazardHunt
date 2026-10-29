# Hazard Hunt (隐患排查)

A 1:1 workshop bay wraps around you in passthrough: bench ahead, rack on the left, pallets and a gas cylinder on the
right, the safety door behind. Eight hazards (ten in walk mode) hide at your feet, on the bench, up on the rack and
behind your back. Turn in place, look down and up, pinch (or pull the trigger) on a checkpoint, pick its category and
its fix; the debrief lights every hazard red / yellow / green in place with a GB / OSHA teaching line.
PICO OS 6, Kotlin, PICO Spatial SDK 6.1.9, one Mixed Stage (Full Space, passthrough), package
`io.embodit.hazardhunt`, v0.1.0 (debug).

Design package (binding): `../spatial-app-factory/apps/hazard-hunt/design-package.json` (revision 3, archetype D,
container STAGE_MIXED, window model single_panel_with_popup, 24 states S01 - S24, 28 checkpoints, rules, copy).
Mockup: `../spatial-app-factory/apps/hazard-hunt/sample.html`. Gate B was passed by the user's chat line
"可以这四个研发QA" without a per-item review, so the build follows the design package literally; the four open gate-B
questions were settled by the design's fallbacks: Spatial SDK (not Unity), procedural props (no Tripo models yet),
turn in place + stick snap turn (walk mode off by default), 45 degree steps, 28 checkpoints in one scene.

Skeleton: `../figure-model` (Mixed Stage, head / plane / controller trackers, pooled attachment panels, lazy-follow
panel, debug receiver, fonts, icon script), which in turn comes from `../spatial-ruler` and `../robot-cell`.

## Structure

```
app/src/main/java/io/embodit/hazardhunt/
  Main.kt                      DefaultStage { HazardTheme { HhScreen() } }
  platform/                    SpatialApplication, LaunchActivity, HhGraph, HhLog (tag HazardHunt)
    tracking/HeadTracker       HMD pose, block reason (supportState)                       [Spatial Ruler]
    sense/PlaneTracker         plane detection (only FLOOR height is used)                 [Spatial Ruler]
    input/ControllerInput      controller ray + thumbstick X                                [Fire Plan]
  domain/model                 Vec3 / Quat4, HazardModel (categories, zones, levels, Poi, settings), Catalog (the
                               28 checkpoints, bilingual names / fixes / basis, pointer boxes), Props (the procedural
                               bay: fixtures and every checkpoint's safe / hazard looks as parts)
  domain/usecase               Rules: RoundRules (seeded draw), Scoring (debrief, grade, order, preset), BayPose
                               (placement, turn about the viewer, bring to front), StickStepper (45 degree snap),
                               rayBox, hint priority; FacePanel / ViewFrame (panel facing)
  data/SettingsStore           SharedPreferences: settings, coach seen, last round
  ui/theme                     HazardTheme (PicoTheme, all 16 roles inherited), HhColors (brand tokens), fonts
  ui/hh                        HhViewModel (state machine), HhUiState / HhEvent, HhScreen
  ui/hh/components             HhStage (SpatialView + panel pool), InspectPanel (6 layouts), Popups (classify card,
                               menu, snackbar, dialog, coach), Common, HhStrings
  ui/hh/scene                  HhScene (ECS frame system: placement, floor glide, snap turn, app-side hover, markers,
                               panel follow / placement, taps), BayViews (meshes, materials, the bay), Markers (hover
                               outline + tag, flags, verdict tags, beam), SignArt (self-drawn sign / tag bitmaps),
                               MeshKit (unit shapes, merging), PanelRig, PanelSpec, SceneKit
app/src/debug/                 DebugCommandReceiver + DebugCommands (QA hooks)
tools/make_icon.py             adaptive icon foreground, legacy PNG and the 512 export from one shape list
tools/*.sh                     relaunch, cold_start (ANR watch), qa_shot, shots_all, stress_panels (DEV=emulator-xxxx)
```

One direction of data: panel controls, scene callbacks (head 10 Hz, FLOOR height, block reason, stick steps, hover,
taps, the 100 ms tick) and debug hooks -> `HhViewModel.onEvent` -> `HhUiState` -> `HhScene.render` and the panels.
Per-frame work lives in `HhScene.onFrame` only. The tick only changes the state when a snackbar ends or the shown
timer second changes.

## The bay

- Bay coordinates = the design's: origin on the floor under the viewer at placement, -Z = the head's heading, +X right.
  One root entity carries everything; placement and turns only move that root.
- Placement (S01 -> S03): once the scene is built, after 0.5 s of head samples: origin = head floor point, heading =
  head yaw, floor = median head height - 1.60 m. A FLOOR plane reported later replaces the height silently
  (0.1 s glide, `HH-FLOOR <y>`); the menu's floor offset (+-30 cm, 2 cm steps) is added on top. "Recenter" and
  coming back from the background place it again at the current head pose; flags and the round stay.
- Fixtures (floor plate texture, three partitions with the door, bench and pegboard, rack, cable tray): 195 parts
  merged into one mesh per paint (12 meshes) + 4 sign quads. Checkpoints: one root per checkpoint with its pointer box
  (`InteractableComponent` + the system `HoverEffectComponent`) and one child group per look (safe, hazard, H12b);
  238 part entities share six unit meshes. Built one checkpoint per paced step (meshes two per step).
- Snap turn: thumbstick X past 0.6 -> one 45 degree step about the viewer's floor point (left = counter-clockwise
  seen from above), re-armed under 0.3; disabled in walk rounds (snackbar). No fade (design R1-5).
- Hover (app side, the platform does not hand pointer hover to the app): controller ray when a controller delivers a
  pose, else the head's forward ray; nearest pointer box (flags count as their checkpoint; the rack's far end panel
  occludes P27). 0.1 s to light, 0.2 s to clear; paused while a card / the menu / a dialog is open. Selection never
  uses the head ray: a pinch / trigger lands on the checkpoint collider through the system pointer.
- Markers: hover = 12 yellow edges round the pointer box (dashed floor ring for P01) + a name tag; flags (pole, orange
  pennant, billboard number) are pointer targets too; debrief = verdict tags (dot + name + n/3) lit clockwise over
  0.6 s, the selected one 1.25x breathing, and a red beam (1.2 m, 0.12 m) above the detail target.
- Tags and signs are textured quads; the engine samples v from the bitmap's bottom row, so the quad has v = 0 on its
  bottom edge (checked on the emulator 2026-10-28: with v = 0 on top every sign was upside down). Every
  tag / sign / number billboards about the vertical only. `testtex=1` + restart draws "F↑" on all of them to check
  orientation (taste D10).

## Panels (all SpatialUI inside PicoTheme; no Material)

Pooled attachment panels at 0.75 (1 dp = 0.6 mm):
`inspectPanel` 400 x 488 box (glass from the top: compact 120, ready 256, inspecting 424, strip 56, debrief / detail
488), lazy follow at the lower left (28 degrees left, 20 degrees down (design 25, its spike fallback), 0.65 m, tilted back 10; slides after 0.5 s
outside a 45 degree window; holds while looking down past 40 degrees or with the menu open; slides back to the
current view's lower left when a dialog opens); `classifyCard` 360 x 400, 20 degrees right of the checkpoint (left when it is more than 40
degrees right), pitch clamped to -25 .. +10, 0.8 m, fixed in the room while open; `moreMenu` 300 x 384 above the
inspect panel; `snackbar` 360 x 56, 14 degrees above the gaze, 0.9 m; `dialog` 400 x 208 straight ahead 0.8 m;
`coach` 320 x 120, ahead 0.9 m, 10 degrees down. Copy: `res/values` (en) and `res/values-zh` through `rs()`;
checkpoint texts from `Catalog` (both languages).

## QA hooks (debug build only)

```
adb -s <serial> shell "am broadcast -a io.embodit.hazardhunt.DEBUG -p io.embodit.hazardhunt --es cmd 'state=S04'"
adb -s <serial> shell "am broadcast -a io.embodit.hazardhunt.DEBUG -p io.embodit.hazardhunt --es cmd 'mark=P12,fire,0'"
```

| command | effect |
|---|---|
| `state=S01..S23` | straight to a designed state (see the list below). Sets `frozen`: hover, FLOOR and automatic placement stop pushing state; `clear=1` lets sensing drive again |
| `seed=<n>` | new round with that layout (frozen). `seed=1` = P01 P04 P10 P14 P15 P19 P21 P22 (all hazard, H12 not drawn) |
| `poi=<Pxx>` / `hover=<Pxx>\|off` | open the classify card on that checkpoint / only highlight it |
| `mark=<Pxx>[,<cat>[,<fix>]]` | plant a flag (cat floor\|elec\|fire\|chem\|mach\|store\|ppe, fix 0 = right); `marks=<n>` the first n hazards right, then safe ones |
| `result=preset` | 6 found / 6 type / 5 fix / 2 false alarms = 17 / 24 good, then the debrief |
| `stick=1\|-1` | one snap step right / left; `turn=<deg>` any turn; `face=<Pxx>` bring it straight ahead (in the debrief: its detail) |
| `nofloor=1\|0`, `blocked=1\|0`, `walk=1\|0` | head-height floor fallback; S23; walk mode on + new round |
| `lang=zh\|en\|system`, `floor=<cm>`, `timer=1\|0`, `sound=1\|0` | settings |
| `coach=reset`, `last=clear` | show the first-run coach again; forget the last round ("First round", replay disabled) |
| `camera=<x>,<z>,<yaw>,<pitch>\|off` | simulated head pose (Stage metres / degrees) for hover and panel follow |
| `testtex=1\|0` | asymmetric "F↑" textures on signs / tags / numbers (takes effect on the next start) |
| `start`, `submit`, `menu`, `recenter`, `again`, `replay`, `missed`, `back`, `next`, `prev`, `exit` | panel actions |
| `ui <control>` | press a panel control by name: start, recenter, submit, mute, more, cat_fire (cat_floor, cat_elec, ...), fix_0..3 (canonical: 0 = right), card_close, card_cancel, card_remove, card_back, dialog_ok, dialog_cancel, dialog_exit, missed, again, replay, overview, detail_back, detail_prev, detail_next, row_p12, edit_p12, lang_zh, lang_en, lang_system, sound_on, sound_off, timer_on, timer_off, walk_on, walk_off, floor_plus, floor_minus, menu_reset, menu_exit, coach_ok, snack_submit |
| `dump`, `hmd`, `panels`, `bay`, `poiview=<Pxx>` | queries (`DUMP {json}` also in files/dump.json; `poiview` = bearing / pitch / distance from the view) |

`state=` targets: S01 starting; S03 ready; S20 ready + coach; S04 inspecting; S05 hover P12; S06 card step 1 on the
first hazard; S07 step 2 with its right category; S08 one flag; S09 the flag's card (editing); S10 one 45 degree
turn; S11 all hazards flagged (snackbar); S12 at the cap + a tap (limit snackbar); S13 submit with three flags
(dialog); S14 preset debrief; S15 detail of the first missed; S16 missed tour; S17 new-round dialog; S18 menu; S19
menu + floor +10 cm; S21 replay last round; S22 walk round; S23 blocked. S02 (FLOOR) and S24 (background) come from
sensing and the lifecycle only.

Log tag `HazardHunt`: `HH-STATE <Sxx> marked=<n> seed=<n> [+S22] [frozen]` on every state change (overlay first:
S23, S13, S17, S18, S09, S07, S06, S11, S12, S16, S15, S14, S05, S04, S20, S03, S01), `HH-FLOOR <y>`, plus `ROUND`,
`PLACE`, `RECENTER`, `TURN`, `STICK`, `HOVER`, `TAP`, `CARD`, `MARK`, `DEBRIEF`, `DETAIL`, `FRONT`, `SOUND`,
`BLOCKED`, `LIFECYCLE`, `SCENE`, `PANEL`, `FOLLOW`, `CMD`.

Screenshot list (design `stateModel.qaCombos`, `tools/shots_all.sh` with DEV set): S01; S02 (two shots: right after
placement and after `HH-FLOOR`; with `nofloor=1` the second does not change); S03; S03 + S20; S04 (seed=1); S05 P12;
S05 P01 looking down; S05 P20 looking up; S06; S07; S08; S09; S10 before / after; S11; S12; S13; S14 (preset); S15
(red); S16; S17 dialog; S18 zh; S18 en; S19 +10 cm; S22 ready; S22 at P26 (camera moved to about (1.0, -0.6) with
the harness); S23; S24 after HOME and back; S04 turned 180 degrees (the five checkpoints behind); S04 en; S21 (same
seed as the log's last `ROUND`); S14 first round (replay disabled, `last=clear` first).

## Build

```
# PowerShell (Git Bash fails on gradlew: CRLF)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug testDebugUnitTest --no-daemon
```

Unit tests (`app/src/test`, 20): catalog integrity, pointer boxes >= 0.25 m, low / high levels against the sight
check, 0.85 m clearance of every part, round-draw rules over 400 seeds, seeded determinism, walk mode, fix shuffle,
scoring preset 17 / 24, grades, ordering, placement, snap turn, bring to front, ray-box, occlusion of P26 / P27,
hint priority.

Material gate: `grep -rEf ../spatial-app-factory/bin/material-grep.txt --include=*.kt --include=*.kts app/src`
must be empty.

## Emulator self-test (2026-10-28, builds 5 and 6, emulator-5560, HD graphics)

All designed states captured into `../spatial-app-factory/apps/hazard-hunt/build/shots` (`tools/shots_all.sh`);
`tools/stress_panels.sh 30` (menu, classify card, language, debrief <-> inspecting) with the process alive and no black
panels; `tools/cold_start.sh 60` three times: no ANR, no crash (the scene builds in about 46 s on the idle emulator,
one checkpoint per paced step; S01 shows meanwhile); HOME -> Back to Home -> reopen keeps flags and re-places the bay.
The UI audit is `../spatial-app-factory/apps/hazard-hunt/build/ui-audit.md`.

Found and fixed on the emulator: textured quads came out upside down (the engine samples v from the bitmap's bottom
row: the quad now has v = 0 on its bottom edge); short category chips, the menu's language segments and English hint
lines were clipped; the dialog's PanelDodge pushed the inspect panel out of view (now it slides back to the lower
left of the current view); 488 dp layouts' bottom row sat at the edge of a level view (panel now 20 degrees down);
an occasional start-up crash `UnlitMaterial.create: Invalid MaterialInstance` when the runtime was short of memory
(the scene build now retries 5 times). Before the run the emulator's `openxr_runtime` held 4.7 GB after a day of QA
of other apps and the low-memory killer kept killing the app: an Android reboot of the same AVD fixed it; this app
adds about 7 MB at start and stays flat over stress and cold starts.

An independent review of build 5 (`../spatial-app-factory/apps/hazard-hunt/build/selftest-qa.md`) found two blockers,
fixed in build 6: the debrief made 8 - 10 tag textures in one frame and the idle emulator ANR'd (textures are now
made at most two per frame: 10 idle debrief rounds, 0 ANR); after a real head turn of 17 - 45 degrees the inspect panel
stayed out of view (the follow window now uses the panel's own bearing, 8 - 45 degrees left, and Recenter brings it
along). Also from that review: the debrief's replay key replays the round just debriefed ("重玩这轮"; design S21 said
the stored previous round, asked back to the design station); `camera=` no longer freezes hover; rack tags stand in
front of the rack.

Not verified: the thumbstick itself (the emulator reports the controller DEVICE_NOT_SUPPORTED;
`stick=` drives the same turn); sound output; the headset.
