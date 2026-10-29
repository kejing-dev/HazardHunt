#!/usr/bin/env bash
# All design states (design stateModel.qaCombos), one qa_shot each, into $SH (default
# ../spatial-app-factory/apps/hazard-hunt/build/shots). The bay must be placed (HH-STATE S03 reached) before running.
# Camera moves (turn 180, look down / up, walk to P26) are done with the emulator camera harness (tools/harness);
# camera= only simulates the head pose for hover and panels.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export SH="${SH:-$ROOT/../spatial-app-factory/apps/hazard-hunt/build/shots}"
Q="bash $ROOT/tools/qa_shot.sh"
$Q - setup "lang=zh" "walk=0" "seed=1" "clear=1" >/dev/null
$Q S03 S03
$Q - S20 "coach=reset" "state=S20"
$Q S04 S04-seed1
$Q S05 S05-P12 "hover=P12" "face=P12"
$Q S04 S05-P01-down "hover=P01" "face=P01"
$Q S04 S05-P20-up "hover=P20" "face=P20"
$Q S06 S06
$Q S07 S07
$Q S08 S08
$Q S09 S09
$Q S04 S10-before
$Q - S10-after "stick=1"
$Q S11 S11
$Q S12 S12
$Q S13 S13
$Q - - "last=clear" >/dev/null
$Q S14 S14-first-round
$Q S14 S14-preset
$Q S15 S15
$Q S16 S16
$Q S17 S17
$Q S18 S18-zh
$Q - S18-en "lang=en"
$Q - - "lang=zh" "floor=0" >/dev/null
$Q S19 S19-plus10
$Q - - "floor=0" >/dev/null
$Q S21 S21
$Q S22 S22
$Q - - "walk=0" >/dev/null
$Q S23 S23
$Q - - "blocked=0" "clear=1" >/dev/null
$Q S04 S04-en "lang=en"
$Q - - "lang=zh" >/dev/null
$Q S01 S01
