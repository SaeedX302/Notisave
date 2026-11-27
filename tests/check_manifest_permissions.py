import xml.etree.ElementTree as ET
from pathlib import Path

root = ET.parse(Path(__file__).parents[1] / "AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
permissions = [node.get(android + "name") for node in root.findall("uses-permission")]
assert set(permissions) == {
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.USE_BIOMETRIC",
}, permissions
assert len(permissions) == 2, permissions
assert "android.permission.INTERNET" not in permissions
print("Manifest permission allowlist OK")
