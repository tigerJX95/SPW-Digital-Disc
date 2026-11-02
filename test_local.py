#!/usr/bin/env python3
"""Offline regression runner. Run after build.cmd/build.sh. No CI workflow required."""
import os
import subprocess
import sys
from pathlib import Path

root = Path(__file__).resolve().parent
os.chdir(root)
host = Path(sys.argv[1]).resolve()
java_home = Path(os.environ['JAVA_HOME'])
suffix = '.exe' if os.name == 'nt' else ''
java = java_home / 'bin' / ('java' + suffix)
javac = java_home / 'bin' / ('javac' + suffix)
classes = root / 'build/classes/java/main'
if not classes.is_dir():
    sys.exit('Build the plugin first.')
output = root / 'build/tests'
output.mkdir(parents=True, exist_ok=True)
classpath = os.pathsep.join(map(str, [classes, host]))
subprocess.run([str(javac), '--release', '21', '-encoding', 'UTF-8', '-cp', classpath,
                '-d', str(output), *map(str, sorted(root.glob('tests/*.java')))], check=True)
classpath = os.pathsep.join(map(str, [output, classes, host]))
tests = [
    'CoverSelectionTest', 'CoverRecoveryTest', 'DiscTransitionTest', 'TrackEntryGateTest',
    'LocalDiscServiceTest', 'LocalDiscFixTest', 'DiscArtworkTest', 'DiscGenerationTest', 'LocalDiscReuseTest',
    'ProductionResumeTest', 'ProductionLayoutRecoveryTest', 'ObservableHandoffTest',
    'HostEntryTest', 'HostImageDrawTest', 'RootRasterTest', 'CoverDiscTest',
    'LocalDiscRasterTest', 'DiscMotionRasterTest', 'ReflectionTest', 'RevealOrderTest',
    'RevealCycleTest', 'DiscExposureTest', 'DiscSettingsTest', 'PreferenceSchemaTest', 'FrontSettingsTest', 'FrontRendererTest', 'FrontDiscMotionTest', 'DiscRotationTest', 'RotationSettingsTest', 'RotationRendererTest', 'HostCompatibilityTest', 'PluginSourceMetadataTest', 'HostBindingsTest',
]
qa = root / 'build/qa'
qa.mkdir(parents=True, exist_ok=True)
for test in tests:
    subprocess.run([str(java), '-Djava.awt.headless=true', '-cp', classpath,
                    'io.github.tigerjx95.spwcdprobe.' + test, str(qa / (test + '.png'))], check=True)
live = root / 'build/tests-live'
live.mkdir(parents=True, exist_ok=True)
subprocess.run([str(javac), '--release', '21', '-encoding', 'UTF-8', '-cp', classpath,
                '-d', str(live), *map(str, sorted(root.glob('tests/live-node-double/*.java')))], check=True)
subprocess.run([str(java), '-Djava.awt.headless=true', '-cp', os.pathsep.join(map(str, [live, classes, host])),
                'io.github.tigerjx95.spwcdprobe.SourceSwitchTest'], check=True)
handoff = root / 'build/tests-handoff'
handoff.mkdir(parents=True, exist_ok=True)
subprocess.run([str(javac), '--release', '21', '-encoding', 'UTF-8', '-cp', classpath,
                '-d', str(handoff), *map(str, sorted(root.glob('tests/handoff-double/*.java')))], check=True)
subprocess.run([str(java), '-Djava.awt.headless=true', '-cp', os.pathsep.join(map(str, [handoff, classes, host])),
                'io.github.tigerjx95.spwcdprobe.ContinuousCoverTest'], check=True)
print('PASS: 35 offline regression programs. OwnedCoverTest requires a matching native Skiko backend and is not run here.')
print('Offline scope excludes Windows SPW playback and native Skia decode.')
