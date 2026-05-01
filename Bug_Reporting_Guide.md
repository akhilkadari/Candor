# Bug Reporting Guide

This guide explains how to capture a useful Android bug report for Candor.

The most helpful report includes:

- what you were doing
- what you expected
- what happened instead
- device model
- Android version
- whether a model was loaded
- a full Android bug report if the issue is difficult to reproduce

## Recommended Method: Android Full Bug Report

### 1. Enable Developer Options

1. Open `Settings`.
2. Go to `About phone`.
3. Tap `Build number` 7 times.

### 2. Capture The Report

1. Reproduce the issue.
2. Open `Settings > Developer options`.
3. Tap `Take bug report`.
4. Choose `Full report`.

### 3. Share The Output

When Android finishes collecting diagnostics, it creates a `.zip` file. Attach it to the issue or upload it and share the link.

## Useful Context To Include

- Was the issue in `Log`, `Insights`, or `History`?
- Had you already downloaded `Gemma-4-E2B-it`?
- How many entries existed when the issue occurred?
- Did the problem happen during model initialization, generation, save, or edit?

## ADB Method

For developers:

```sh
adb bugreport ./candor-bugreport
```

If multiple devices are connected:

```sh
adb devices
adb -s <device_serial> bugreport
```

## When Reporting Insight Issues

Insight bugs are easier to debug if you also include:

- whether the model was running on NPU, GPU, or CPU
- whether the app said `No model`, `Not enough data`, `Loading`, or `Error`
- whether the issue affected generation or previously saved snapshots
