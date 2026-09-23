"""Validate the tag against AGP's actual output before sending an APK for signing."""
import json
from pathlib import Path
import re
import shutil
import sys


def prepare(metadata_path: Path, tag: str, destination: Path) -> dict:
    if not re.fullmatch(r"v\d+(?:\.\d+){2}(?:fix\d+)?(?:-[0-9A-Za-z]+(?:\.[0-9A-Za-z]+)*)?", tag):
        raise ValueError("Use vMAJOR.MINOR.PATCH[fixN][-prerelease], matching versionName")
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    if metadata.get("applicationId") != "me.rerere.rikkahub":
        raise ValueError("Expected the release applicationId, not a debug build")
    elements = metadata["elements"]
    if len(elements) != 1:
        raise ValueError("Expected exactly one arm64 APK; review the pipeline for ABI splits")
    element = elements[0]
    if element["versionName"] != tag[1:]:
        raise ValueError(f"Tag {tag} does not match built versionName {element['versionName']}")
    code = element["versionCode"]
    if type(code) is not int or not 0 < code <= 2100000000:
        raise ValueError("versionCode must be a positive Android version code")
    source = (metadata_path.parent / element["outputFile"]).resolve()
    if source.parent != metadata_path.parent.resolve() or source.suffix != ".apk":
        raise ValueError("APK must be in the same directory as output-metadata.json")
    if not source.is_file():
        raise ValueError("The APK listed in output-metadata.json is missing")
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination / "app-unsigned.apk")
    info = {"version": element["versionName"], "versionCode": code, "tag": tag}
    (destination / "release-info.json").write_text(json.dumps(info, indent=2) + "\n", encoding="utf-8")
    return info


if __name__ == "__main__":
    try:
        prepare(Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3]))
    except (ValueError, KeyError, OSError) as error:
        sys.exit(f"Release validation failed: {error}")
