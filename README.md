# MyMiles Android APK Test

This is a simple Android-only live GPS mileage tracker test.

It avoids Expo/EAS completely.

## What it does

- Start GPS tracking
- Foreground service notification
- Background/locked-screen GPS attempt
- Live GPS miles
- Live speed
- Accepted/rejected GPS point counts
- Optional start/end odometer
- Save records
- Export CSV by Android share sheet

## How to build APK in GitHub

1. Open your GitHub repo.
2. Go to **Code** tab, not Settings.
3. Upload all files/folders from this zip into the repo.
4. Commit changes.
5. Open **Actions** tab.
6. Run / open the latest **Build Android APK** workflow.
7. Download artifact named **mymiles-debug-apk**.
8. Install APK on Android.

## Test protocol

1. Reset car trip computer.
2. Open MyMiles.
3. Optional: enter start odometer.
4. Press Start Tracking.
5. Drive with screen on, screen locked, and app backgrounded.
6. Stop & Save.
7. Compare app miles vs car trip computer.
