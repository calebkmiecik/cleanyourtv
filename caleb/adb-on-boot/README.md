# Debugging on at start

The MoGo 4 turns Wireless debugging off at every restart. This app has no screen: after each start it
waits for Wi-Fi, then switches Wireless debugging back on, so `tools/tv.py` and adb reach the projector
again without a walk to Developer options.

Build and install (from the repo root, with ADB on):

```bash
tools/docker-build.sh caleb/adb-on-boot
adb install -r caleb/adb-on-boot/app/build/outputs/apk/release/app-release.apk
adb shell pm grant com.caleb.adbonboot android.permission.WRITE_SECURE_SETTINGS
adb shell cmd deviceidle whitelist +com.caleb.adbonboot   # lets its backup job run: an app with no screen counts as "never used"
adb shell am start -n com.caleb.adbonboot/.RunNow   # test run; also wakes the app for its first boot
adb logcat -d -s AdbOnBoot                            # "wireless debugging is on"
```

Trade-off: debugging stays on at home. A new computer still needs "Allow" on the projector's screen.
To stop it: `adb uninstall com.caleb.adbonboot`.
