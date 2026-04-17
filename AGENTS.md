# ClickAssist AGENTS.md

## Project overview
This repository is an Android native app for repeated touch automation assistance.

Core stack:
- Kotlin
- Jetpack Compose
- MVVM
- Room
- DataStore
- AccessibilityService
- Overlay window / floating toolbar

## Product direction
- automation assistance for repeated operations
- static user-configured actions only
- local-only data storage
- no OCR, no UI element recognition, no cloud sync by default
- do not add autonomous decision-making unless explicitly requested

## Working style
- Prefer minimal, incremental changes over broad rewrites.
- Keep the existing architecture unless a change is clearly necessary.
- Preserve Task + ActionStep as the core execution model.
- Reuse current repositories, controllers, and viewmodels before adding new abstractions.
- Keep code compileable after each change.
- When fixing bugs, identify the root cause first and modify the smallest correct set of files.

## UI / product rules
- Keep UI simple, readable, and practical.
- Floating execution UI is the primary runtime surface.
- App pages are mainly for task management, settings, and onboarding.
- Do not replace user-visible workflows with hidden implicit behavior.
- Overlay interactions must remain visible, reachable, and dismissible.
- Never leave the user in a blocked state without a visible exit path.
- All user-visible strings must be resource-based, not hardcoded.

## Localization
- English is the default language.
- Simplified Chinese is supported.
- Use Android string resources for all new text.
- Do not hardcode bilingual text in Kotlin files.

## Validation before finishing
Before considering a task done:
- ensure the app still compiles
- ensure changed flows still navigate correctly
- ensure overlays still show/hide correctly if touched
- ensure strings are resourceized
- ensure settings persist if settings-related files were changed

## Skills
Use repository skills when the task clearly matches them:

- file-processing-shell-python
  For file processing, parsing, filtering, renaming, conversion, log analysis, and bulk text/data manipulation.

- overlay-tutorial-bugfix
  For floating onboarding / tutorial / overlay guidance bugs, especially blocked flows, missing buttons, swallowed clicks, and dismiss/visibility issues.