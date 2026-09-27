import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

serial = os.environ.get("ANDROID_SERIAL", "emulator-5554")
assert serial.startswith("emulator-"), "Use a disposable emulator, not a personal device"
package = "com.farol.launcher.debug"
output = Path(os.environ.get("ABOUT_SCREENSHOTS", "/tmp/farol-about"))
output.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.check_output(["adb", "-s", serial, *args], timeout=40)


def shell(*args):
    return adb("shell", *args).decode().strip()


def swipe():
    shell("input", "swipe", str(width // 2), str(height * 3 // 4),
          str(width // 2), str(height // 3), "800")
    time.sleep(1)


def tree():
    for _ in range(3):
        if "dumped" in shell("uiautomator", "dump", "/sdcard/about-check.xml"):
            return ET.fromstring(shell("cat", "/sdcard/about-check.xml"))
        time.sleep(1)
    raise AssertionError("No accessibility tree available")


def find(root, name):
    return next((node for node in root.iter("node")
                 if node.get("resource-id") == f"{package}:id/{name}"), None)


def tap(node):
    assert node is not None, "Missing target"
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    assert x2 > x1 and y2 > y1, "Empty target"
    shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1.5)


def capture(name):
    root = tree()
    time.sleep(.5)
    (output / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))
    ET.ElementTree(root).write(output / f"{name}.xml")
    return root


width, height = map(int, re.findall(r"(\d+)x(\d+)", shell("wm", "size"))[-1])
font = shell("settings", "get", "system", "font_scale")
scale = shell("settings", "get", "global", "animator_duration_scale")
night = shell("cmd", "uimode", "night").split()[-1]
try:
    for mode, text_scale in (("no", "1.0"), ("yes", "1.0"), ("yes", "2.0")):
        shell("cmd", "uimode", "night", mode)
        shell("settings", "put", "system", "font_scale", text_scale)
        shell("settings", "put", "global", "animator_duration_scale", "0" if text_scale == "2.0" else "1")
        shell("am", "force-stop", package)
        shell("am", "start", "-W", "-n", f"{package}/com.farol.launcher.activities.launcher.Launcher")
        time.sleep(2)
        shell("am", "start", "-W", "-n", f"{package}/com.farol.launcher.activities.settings.Settings")
        time.sleep(2)
        for _ in range(5):
            root = tree()
            about = find(root, "category_about")
            if about is not None:
                tap(about)
                break
            swipe()
        else:
            raise AssertionError("About category is not reachable")
        pid = shell("pidof", package)
        assert pid, "App stopped"
        label = f"{mode}-{text_scale}"
        root = capture(f"{label}-hero")
        beacon = find(root, "about_beacon")
        assert beacon is not None and beacon.get("content-desc") == "Replay beacon light"
        tap(beacon)
        seen = set()
        for page in range(8):
            root = capture(f"{label}-{page}")
            seen.update(node.get("resource-id", "").split("/")[-1] for node in root.iter("node"))
            if {"creator_profile", "creator_website", "creator_github", "creator_telegram", "creator_instagram", "licenses"} <= seen:
                break
            swipe()
        assert {"creator_profile", "creator_website", "creator_github", "creator_telegram", "creator_instagram", "licenses"} <= seen, seen
        assert shell("pidof", package) == pid, "App restarted during About navigation"
        shell("input", "keyevent", "KEYCODE_BACK")
        assert find(tree(), "category_home") is not None, "Back did not return to Settings"
        print(f"PASS: About, creator links, licenses, replay, back ({label})", flush=True)
finally:
    for namespace, key, value in (("system", "font_scale", font), ("global", "animator_duration_scale", scale)):
        if value == "null":
            shell("settings", "delete", namespace, key)
        else:
            shell("settings", "put", namespace, key, value)
    shell("cmd", "uimode", "night", night)
print(f"Screenshots: {output}")
