# Contributing to Eyeline

Thanks for your interest in contributing to **Eyeline**! Whether you are fixing a bug, refining teleprompter ergonomics, or improving video editing workflows, your help is appreciated.

## How to Contribute

1. **Fork & Branch**: Fork the repository and create a feature or fix branch from `main` (e.g., `fix/waveform-scrub-snap` or `feat/bluetooth-shutter`).
2. **Keep Changes Focused**: Aim for clean, self-contained pull requests written in idiomatic Kotlin and Jetpack Compose (Material 3).
3. **Verify Locally**:
   - Run unit tests (`./gradlew :app:testDebugUnitTest`).
   - Verify the app builds cleanly (`./gradlew :app:assembleDebug`).
   - Test camera capture, light/dark modes, and trim export on a real Android device whenever possible.
4. **Open a Pull Request**: Include a short summary of the change, motivation, and screenshots or screen recordings for any UI updates.

## Reporting Issues

When filing an issue, please include:
- Android device model and OS version
- Steps to reproduce the issue
- Expected vs. actual behavior (and relevant Logcat output if applicable)

## Code Guidelines

- Follow Material Design 3 spacing, `48dp` minimum touch targets, edge-to-edge `WindowInsets`, and `contentDescription` accessibility labels.
- Keep user data private and on-device; avoid requesting broad storage permissions when Android Scoped Storage or system pickers suffice.
- Never commit signing keystores (`*.jks`), `.env` files, or API credentials.
