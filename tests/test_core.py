#!/usr/bin/env python3
"""Run the Java regression tests that do not need the Salt Player archive."""
import os
import subprocess
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "io.github.tigerjx95.spwcdprobe"
TESTS = (
    "DiscTransitionTest",
    "RevealCycleTest",
    "TrackEntryGateTest",
    "DiscRotationTest",
    "CoverSelectionTest",
    "CoverRecoveryTest",
    "VinylLabelFramingTest",
    "DiscArtworkTest",
    "LocalDiscServiceTest",
    "LocalDiscFixTest",
    "LocalDiscReuseTest",
    "LocalDiscRetryTest",
    "CrossAlbumCacheTest",
    "PluginSourceMetadataTest",
)


def main():
    java_home = os.environ.get("JAVA_HOME")
    java_bin = Path(java_home) / "bin" if java_home else Path()
    javac = str(java_bin / "javac")
    java = str(java_bin / "java")
    sources = [f"tests/{test}.java" for test in TESTS]
    with TemporaryDirectory(prefix="spw-core-tests-") as output:
        subprocess.run(
            [javac, "--release", "21", "-encoding", "UTF-8", "-classpath", output,
             "-sourcepath", "src", "-d", output, *sources],
            cwd=ROOT, check=True,
        )
        for test in TESTS:
            print(f"Running {test}", flush=True)
            subprocess.run(
                [java, "-Djava.awt.headless=true", "-classpath", output, f"{PACKAGE}.{test}"],
                cwd=ROOT, check=True,
            )
    print(f"PASS: {len(TESTS)} core regression programs.")


if __name__ == "__main__":
    main()
