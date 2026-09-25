#!/usr/bin/env bash
set -euo pipefail
package=com.farol.launcher.debug
adb shell am start -W --user 0 -n "$package/com.farol.launcher.activities.launcher.Launcher" >/dev/null
sleep 2
pid=$(adb shell pidof "$package" | tr -d '\r')
test -n "$pid"
size=$(adb shell wm size | tail -1 | tr -d '\r' | awk '{print $NF}')
width=${size%x*}
height=${size#*x}
for direction in up up up down down down up up up down down down; do
    start=$((height * 3 / 4))
    end=$((height / 4))
    if [[ $direction == down ]]; then
        start=$((height / 4))
        end=$((height * 3 / 4))
    fi
    adb shell input swipe "$((width / 2))" "$start" "$((width / 2))" "$end" 350
    sleep 1
    current=$(adb shell pidof "$package" | tr -d '\r' || true)
    if [[ "$current" != "$pid" ]]; then
        adb logcat -b crash -d --pid="$pid"
        echo 'FAIL: Farol exited or restarted while scrolling.' >&2
        exit 1
    fi
done
echo 'PASS: Farol survived 12 drawer swipes without restarting.'
