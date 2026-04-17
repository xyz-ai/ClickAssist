---
name: overlay-tutorial-bugfix
description: Fix floating onboarding and overlay tutorial bugs in ClickAssist, especially blocked tutorial flows, missing navigation buttons, swallowed clicks, unreachable dismiss actions, and tutorial cards drifting off-screen. Prefer minimal, targeted fixes to TutorialOverlay, OverlayTutorialHost, settings flags, and string resources.
---

# Overlay Tutorial Bugfix Skill

Use this skill when the task is specifically about the floating tutorial / overlay onboarding system, including:
- tutorial gets stuck
- no visible Next / Back / Skip / Done buttons
- no close action
- overlay blocks all interaction and cannot be dismissed
- tutorial card goes off-screen
- buttons exist visually but are not clickable
- tutorial seen-state is not persisted correctly
- floating tutorial strings need resourceized multilingual labels

## Primary goal
Restore a complete and safe tutorial interaction loop.

The user must always be able to:
- go to next step
- go back
- skip
- finish
- close the tutorial immediately

## Required bugfix principles
1. Do not redesign the entire overlay system.
2. Modify the smallest correct set of files.
3. Keep the tutorial flow visible and dismissible at all times.
4. Prefer fixed bottom action controls over controls that drift with the target rect.
5. Keep all button text resourceized.
6. Preserve English default + Chinese support.

## Priority checks
Always inspect these first:
- `ui/tutorial/TutorialOverlay.kt`
- `ui/tutorial/OverlayTutorialHost.kt`
- `ui/tutorial/TutorialStep.kt`
- `data/settings/DataStoreSettingsRepository.kt`
- `res/values/strings.xml`
- `res/values-zh-rCN/strings.xml`

Check these conditions in order:

### 1. Are there visible controls?
Verify the tutorial has explicit controls:
- Next
- Back
- Skip
- Done
- Close

### 2. Are controls actually clickable?
Check overlay layering and pointer handling:
- the dimmed background may block underlying app input
- tutorial action buttons must remain clickable
- do not rely on tapping the overlay background to advance

### 3. Is there an emergency exit?
There must be a visible close action, such as:
- top-right X
- or equivalent always-visible dismiss button

### 4. Can the card go off-screen?
When targetRect is near screen edges:
- clamp the tutorial card position
- keep action buttons visible
- prefer a fixed bottom control bar if dynamic placement is unstable

### 5. Is tutorial completion persisted?
Ensure:
- Skip marks `hasSeenFloatingTutorial = true`
- Done marks `hasSeenFloatingTutorial = true`
- Close marks `hasSeenFloatingTutorial = true`
- Next / Back only move between steps

## Expected button behavior

### First step
Show:
- Skip
- Next

### Middle steps
Show:
- Back
- Skip
- Next

### Final step
Show:
- Back
- Done

### Close (X)
Always available if implemented.

## Localization rules
All new text must use Android string resources.

Required keys:
- tutorial_back
- tutorial_next
- tutorial_skip
- tutorial_done
- tutorial_close

Keep:
- `res/values/strings.xml` as English default
- `res/values-zh-rCN/strings.xml` as Simplified Chinese

## Layout guidance
Prefer this structure:
- full-screen dim layer
- highlighted target region
- tutorial description card
- fixed bottom action row

If a dynamic card position is used:
- clamp it inside the screen
- never let action controls leave the viewport

## Output style for Codex
When using this skill:
1. First list the files to change and why.
2. Prefer editing existing files over adding new abstractions.
3. Keep changes tightly scoped to tutorial behavior.
4. Ensure the result is compileable.
5. Do not rewrite unrelated overlay logic.

## Completion checklist
A correct fix must ensure:
- tutorial no longer traps the user
- tutorial has visible actions
- tutorial can always be dismissed
- tutorial buttons are clickable
- card and controls stay on-screen
- completion state is persisted
- multilingual strings are present