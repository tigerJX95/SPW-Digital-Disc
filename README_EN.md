# SPW Digital Disc

[中文](README.md) | [English](README_EN.md)

Adds a digital CD presentation to Salt Player for Windows (SPW), with a complete album cover and a CD extending from its right side.

Current version: **v1.2.0**. Includes interface mappings for **SPW 1.19.0 and 1.19.3 Microsoft Store**; see the compatibility table below for validation status.

V1.2.0 refines eject/retract easing, braking on early return, gentle rotation starts and the default CD’s narrow white edge and translucent metallic hub. Fine grooves, silver highlights and a milky inner lip add depth while retaining the translucent hole and white spindle.

[Update history](CHANGELOG.md) (Chinese)

## Background

This plugin was inspired by [**Georgia-ReBORN**](https://github.com/TT-ReBORN/Georgia-ReBORN), a foobar2000 theme I use regularly.

I love the way it presents an album cover together with a disc. I used to collect physical records, and I enjoyed holding an album and looking at its cover and disc artwork. But preserving physical CDs is not easy. Even with careful storage, they can age and deteriorate over the years. For me, digital music is a better fit for long-term collecting and everyday listening.

I wanted to bring this digital record experience to Salt Player: enjoying the convenience of digital music while keeping some of the pleasure of collecting and appreciating physical albums, and sharing it with others who feel the same way.

## Features

- **Primary detail page**: keeps SPW's native album cover, lyrics, and playback controls.
- **Secondary display page**: clicking the album cover opens a larger, centered presentation of the complete cover and a CD on its right. The cover is not squeezed into half an image.
- **Local disc artwork**: searches the current song's directory in the order `cd.png → cd.jpg → disc.png → disc.jpg`. If a higher-priority file cannot be decoded, the next file is tried.
- **Generated disc artwork**: when local artwork is unavailable, a copy of the album cover fills a circular CD with a narrow white edge, translucent annular hub, translucent center hole and tiny white spindle. Two narrow silver bevels use adjacent highlights and grooves around a translucent hub field, preserving the artwork underneath; the main cover remains complete.
- **Transparent images**: local PNG files retain their existing transparent backgrounds and center holes. An antialiased circular outer mask is applied. No extra center hole or hub ring is added.
- **Front-disc display**: enable the native plugin switch to draw the disc over the cover. Off by default. Front mode uses whole-disc motion with a short uniform fade. Compare at exposure 50; in front mode exposure controls horizontal travel, while 0 still shows only the cover.
- **Disc exposure setting**: open SPW Digital Disc settings in the plugin manager and adjust 0–100. Default 50 preserves the existing view; 100 shows the full disc. The host saves the value. The group fits the window, and cover-only mode returns without a retract delay.
- **Playback-linked rotation**: enable “Rotate while playing” to start gently over about 240 ms after ejection, then rotate once every 20 seconds. Pause and retraction freeze the angle; resume starts gently from that angle. Off by default; the cover stays still.
- **Page animations**: the cover enlarges first, then the CD gradually slides out from behind it. Clicking the combined display to return retracts the CD before invoking the native return action. The reflection follows the same animation progress. Normal track changes replay the eject animation without rescaling the cover; rapid changes eject only the last stably selected song's disc.
- **Combined reflection**: the cover and CD have a short reflection that fades downward.
- Local files are read only. The plugin does not modify songs or original disc images, or save generated disc artwork to disk.

## Results Showcase

https://github.com/user-attachments/assets/8a289172-d79b-4e2e-9ba2-c7dc9ee5d86c

## Installation and use

Current interface mappings cover **SPW 1.19.0 and 1.19.3 Microsoft Store**. The plugin uses SPW's internal drawing interfaces, so later SPW versions may require a new adaptation.

1. Obtain the compiled `.spmod` file. The source ZIP cannot be installed directly; see the build instructions below to compile it yourself.
2. In SPW's plugin manager, use the local plugin import option to select the `.spmod` file.
3. Grant `class-transform` and `library-read` when prompted, enable the plugin, and restart SPW.
4. Play a local song. Click the bottom playback bar to open the primary detail page, then click the album cover to open the secondary display page.
5. On the secondary page, click the combined cover/CD display to return. The CD retracts before the primary page returns. You can also return while the CD is sliding out; repeated clicks do not queue multiple return actions.

Normal ejection takes about 700 ms and full retraction about 650 ms, with quintic easing at the endpoints. Returning during ejection preserves the current pose and brakes for up to about 80 ms before retracting, without passing the original endpoint. Returning from a fully open disc adds no braking delay.

After a track change, the plugin waits for a selection-stability interval of about 280 ms and for the cover and disc lookup to be ready before ejecting the disc. Further track changes during this interval replace the pending request; intermediate songs are not replayed in sequence. Once a cover source takes over, old nodes from that transition cannot overwrite it. When the page is collapsed to the playback bar and reopened, the plugin waits until the host has fully expanded before replaying the cover-first entry and disc eject animations. The expansion process does not consume the disc animation's time.

Collapsing the entire playback detail page or disabling the plugin cancels unfinished animations and delayed return actions, preventing remnants from appearing over the mini player. Different return paths and rapid interactions still leave room for improvement.

The current installation package is `SPW_Digital_Disc_v1.2.0.spmod`. The source ZIP is for development and building; choose the `.spmod` file when installing.

## Where to put local disc images

Place the disc image in **the same directory as the audio file**. For example, if the song is at `D:\Music\Album\01.flac`, put the disc image at `D:\Music\Album\cd.png`.

| Priority | Filename |
| --- | --- |
| 1 | `cd.png` |
| 2 | `cd.jpg` |
| 3 | `disc.png` |
| 4 | `disc.jpg` |

If all four files exist and are readable, `cd.png` is used. Use the lowercase filenames shown above. Songs in the same directory share that disc image. The plugin does not currently search parent directories or subdirectories, support custom disc image paths, or download disc artwork automatically.

For a transparent background or center hole, use a PNG with those transparent areas already prepared.

## Prepare local CD artwork (optional)

> [!NOTE]
> **Create disc artwork with the PSD template**
>
> [Download cdArt_Template.psd](templates/cdArt_Template.psd). This Photoshop template uses a **1000 × 1000 pixel** canvas with a circular outer edge, center hole, hub rings and optional shading. It helps turn disc scans, photographs or custom designs into transparent PNG artwork for the player.
>
> 1. **Open the template and save a copy.** Keep the original PSD for future use.
> 2. **Choose a disc style.** Enable one style group containing a `YOUR IMAGE HERE` placeholder and disable the other equivalent style groups.
> 3. **Insert your image.** Place it inside the selected group in place of the placeholder, retaining the group mask. Scale proportionally and reposition it to cover the disc without stretching.
> 4. **Adjust the appearance.** Toggle or adjust the opacity of the `Shading` group and `Optional` effects to blend the hub rings and outer edge with the artwork.
> 5. **Check transparency.** Temporarily enable `Test Colours` to inspect the edges and center hole against different backgrounds. Before exporting, disable that group and the `test sizes and basic rules` helper layer, leaving the area outside the disc and the center hole transparent.
> 6. **Export a PNG.** Keep **1000 × 1000 pixels**, enable transparency, name the image `cd.png` and place it in the same directory as the audio files.
>
> The PSD is for editing; the player reads the exported PNG. Keep an editable PSD copy for later changes.

## Settings

Open SPW Digital Disc settings in the plugin manager. The host saves and restores these settings.

| Setting | Default | Behavior |
| --- | --- | --- |
| Disc exposure | 50 | Range 0–100; 0 shows only the cover, 100 moves the entire disc out; controls horizontal travel |
| Disc in front | Off | Controls overlap order independently of exposure |
| Rotate while playing | Off | Soft start after ejection, then clockwise at about 20 seconds per turn; freezes on pause and continues on resume |

Front mode moves the complete disc with a short uniform fade at entry and exit. Front and back modes share the same travel. At exposure 100, the cover and disc barely overlap, so layer order has little visible effect. The native slider can display decimals; rendering rounds to an integer percentage, so `50.062717` is treated as 50.

Only the disc rotates; the cover stays upright and the reflection shares the angle. Each start accelerates smoothly from rest over about 240 ms, then maintains a 20-second period. Pausing or starting retraction freezes the current angle immediately. Playback resumes gently from that angle without replaying paused time. A new track starts upright. Ejection, retraction and exposure 0 do not rotate. Collapsing the secondary page to the mini player freezes the angle; reopening resumes after ejection completes. Returning to the primary page ends that presentation, so entering the secondary page again starts upright. Disabling rotation restores the upright orientation.

## SPW host compatibility

| SPW host | Interface mapping | V1.2.0 offline regression |
| --- | --- | --- |
| 1.19.0 Microsoft Store | Adapted | 40 programs pass |
| 1.19.3 Microsoft Store | Adapted | 40 programs pass |
| Other versions or distributions | Unverified | Unverified |

Offline tests cover interface resolution, cover calls, configuration storage, images, animations and rotation state. They do not run Windows native playback or Skia rendering. Startup matches a complete profile by actual class fingerprints, rather than Windows 10/11 or channel labels alone. Unknown interfaces prevent activation with a detailed error. Rotation stops and logs a warning if its playback-state checks fail.

Host updates that prevent loading take priority for compatibility fixes and may receive a patch release. A version-number change alone does not require a release when features still work. New adaptations retain verified older profiles where possible. Include the host version, channel, plugin version and full error when reporting a problem.

## Compatibility and limitations

- Complete SPW 1.19.0 and 1.19.3 interface mappings are selected by critical class fingerprints. Unknown interfaces require review; checks are not bypassed.
- Disc images are searched only in the audio file's directory using fixed filenames, without recursion, custom paths or automatic downloads.
- JPG preparation masks the outer circle but does not detect the disc, remove backgrounds inside the circle or create a center hole.
- If playback state cannot be read, rotation stops and logs `ROTATION_STATE_UNAVAILABLE`; artwork display retains its existing behavior.
- Window sizes, display scaling and rapid page changes depend on host behavior. Report problems using the repository's Issue templates.

## Implementation

The plugin is written in **Java** and uses the SPW plugin API, Compose drawing, and Skia image decoding. It connects through the identified `FullScreenCover` entry point and renders the complete cover and CD in an independent Canvas. Local images are read asynchronously, and the drawing handoff uses Compose observable state.

## Build an installation package from source

These steps generate a `.spmod` plugin. You do not need these development tools to install and use a prebuilt plugin.

### 1. Prepare the build environment

| Component | Requirement and purpose |
| --- | --- |
| JDK | Use the project's existing JDK 25 setup, including both `java` and `javac` |
| Gradle | Use Gradle 9.8; the source package does not include a Gradle Wrapper, so install Gradle separately |
| Python | Python 3; on Windows, it must be available as the `python` command |
| SPW host file | `app\ffmpeg-x64.dll` from the installation directory of the supported SPW 1.19.0 or 1.19.3 Microsoft Store version |

Provide the host file from the matching SPW installation. A generic FFmpeg DLL with the same filename cannot be used instead. The host and development tools are not included in the source package.

### 2. Extract the source and configure Windows

Extract the entire source ZIP and open the directory containing `build.cmd`, `build.gradle`, `MANIFEST.MF`, and `src`. Do not run scripts from inside the ZIP archive.

Open **Command Prompt (CMD)** and set the environment variables for your installation. These paths are examples:

```bat
set "JAVA_HOME=C:\Tools\jdk-25"
set "PATH=%JAVA_HOME%\bin;C:\Tools\gradle-9.8.0\bin;%PATH%"
```

`JAVA_HOME` must point to the JDK root directory, not its `bin` subdirectory. These settings apply only to the current CMD window. The Python installation must also be on `PATH`.

Check that the tools are available:

```bat
java -version
javac -version
gradle --version
python --version
```

Confirm that Java and javac are version 25, Gradle is version 9.8, and Gradle reports the intended JDK as its runtime JVM. If a command is not found, check the installation path and `PATH`. If Windows can run `py` but not `python`, fix Python's command path first: the existing build script invokes `python`.

### 3. Compile the plugin

In the same CMD window, switch to the extracted source directory, for example:

```bat
cd /d "D:\Projects\SPW-Digital-Disc"
build.cmd "C:\Path\To\SPW\app\ffmpeg-x64.dll"
```

Replace the host path on the second line with the real path. Keep the double quotes if it contains spaces. The script invokes Gradle's `plugin` task to check host components, compile the Java source, and package the plugin. The bytecode target remains Java 21. The host is a `compileOnly` dependency and is not bundled into the plugin.

If using PowerShell, configure the same environment variables and run this in the source directory:

```powershell
.\build.cmd "C:\Path\To\SPW\app\ffmpeg-x64.dll"
```

`build.cmd` requires the host path argument and does not install development tools automatically. Double-clicking it does not complete a build. To rebuild after changing the source, run the same command again.

### 4. Find and install the generated plugin

After the build reports `BUILD SUCCESSFUL`, look in the source directory's `dist` folder for:

```text
SPW_Digital_Disc_v1.2.0.spmod
```

This is the installation package that SPW can import. You do not need to ZIP the entire source directory or rename its extension. The `build` directory contains intermediate build output, not the installation package.

Import the `.spmod` through SPW's plugin manager, grant `class-transform` and `library-read`, enable it, and restart SPW. For page navigation and disc image placement, see “Installation and use” and “Where to put local disc images” above.

### Build on Linux

Set `JAVA_HOME`, add the JDK and Gradle `bin` directories to `PATH`, and make sure `python3` is available. Run this in the source directory:

```sh
bash build.sh "/path/to/SPW/app/ffmpeg-x64.dll"
```

You still need the matching SPW Windows host file. Output is also placed in `dist/`. Building on Linux does not mean the plugin runs in a Linux player; this project targets SPW for Windows.

### Common build problems

| Message or symptom | What to do |
| --- | --- |
| `Set JAVA_HOME` | Point `JAVA_HOME` to the root directory of your JDK 25 installation |
| `gradle` or `python` is not recognized | Install the tool, configure `PATH`, and retry in the same terminal |
| `Usage: build.cmd ...` | The host path argument is missing; use the complete command shown above |
| Host file is missing or cannot be read as a ZIP | Check that the file is the matching SPW installation's `app\ffmpeg-x64.dll`, not a different file with the same name |
| `fingerprint mismatch`, missing host classes, or interface-related compilation errors | The host may not match the supported version; use the matching version instead of bypassing the checks |
| The build failed, but an older file is still in `dist` | An existing file does not mean this build succeeded; check the current Gradle result |

## Third-party components and license

The project uses the [MIT License](LICENSE).

See [Third-party notices](THIRD_PARTY_NOTICES.md) (Chinese) for details. The project does not distribute sample commercial album covers, disc artwork, or the SPW host and its dependencies. User-provided music and images are outside the scope of this project's MIT license.
