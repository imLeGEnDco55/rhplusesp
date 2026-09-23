import json
from pathlib import Path
import tempfile
import unittest

from prepare_release import prepare


class ReleaseValidationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.metadata = self.root / "output-metadata.json"
        self.apk = self.root / "app-release-unsigned.apk"
        self.apk.write_bytes(b"test fixture")
        self.data = {
            "applicationId": "me.rerere.rikkahub",
            "elements": [{"versionName": "2.5.4fix13", "versionCode": 226,
                          "outputFile": self.apk.name}],
        }

    def run_prepare(self, tag="v2.5.4fix13"):
        self.metadata.write_text(json.dumps(self.data), encoding="utf-8")
        return prepare(self.metadata, tag, self.root / "out")

    def test_stable_and_prerelease_versions(self):
        for version in ("2.5.4fix13", "2.5.5", "2.5.5-beta.1"):
            with self.subTest(version=version):
                self.data["elements"][0]["versionName"] = version
                self.assertEqual(self.run_prepare("v" + version)["version"], version)
                self.assertEqual((self.root / "out/app-unsigned.apk").read_bytes(), b"test fixture")

    def test_rejects_mismatched_tag(self):
        with self.assertRaisesRegex(ValueError, "does not match"):
            self.run_prepare("v2.5.5")

    def test_rejects_unsafe_or_unparseable_tag(self):
        for tag in ("nightly", "v2.5.4/evil", "v2.5.4$(id)", "v2.5.4-es-mx"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                self.run_prepare(tag)

    def test_rejects_debug_apk(self):
        self.data["applicationId"] += ".debug"
        with self.assertRaisesRegex(ValueError, "applicationId"):
            self.run_prepare()

    def test_rejects_multiple_apks(self):
        self.data["elements"] *= 2
        with self.assertRaisesRegex(ValueError, "exactly one"):
            self.run_prepare()

    def test_rejects_invalid_version_code(self):
        for code in (0, -1, "226", 2100000001, True):
            with self.subTest(code=code), self.assertRaisesRegex(ValueError, "versionCode"):
                self.data["elements"][0]["versionCode"] = code
                self.run_prepare()

    def test_rejects_missing_apk(self):
        self.apk.unlink()
        with self.assertRaisesRegex(ValueError, "missing"):
            self.run_prepare()

    def test_rejects_path_traversal(self):
        self.data["elements"][0]["outputFile"] = "../outside.apk"
        with self.assertRaisesRegex(ValueError, "same directory"):
            self.run_prepare()


if __name__ == "__main__":
    unittest.main()
