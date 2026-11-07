# SPW Digital Disc

[中文](README.md) | [English](README_EN.md)

Adds a digital CD presentation to Salt Player (SPW), with a complete album cover and a CD extending from its right side.

Current version: **v1.5.0**

- **Fix abrupt transitions when returning during cover enlargement**: before the disc appears, the cover shrinks smoothly from its current size before the primary detail view returns.
- **Independent speed settings**: choose Slow, Standard or Fast for ejection/retraction and rotation independently. When rotation is enabled, the three presets take about 30, 20 and 12 seconds per turn.
- **Steam compatibility**: verified on Windows and Ubuntu 26.04.1 LTS (Linux).

[Update history](CHANGELOG.md) (Chinese)

## Background

This plugin was inspired by [**Georgia-ReBORN**](https://github.com/TT-ReBORN/Georgia-ReBORN), a foobar2000 theme I use regularly.

I love the way it presents an album cover together with a disc. I used to collect physical records, and I enjoyed holding an album and looking at its cover and disc artwork. But preserving physical CDs is not easy. Even with careful storage, they can age and deteriorate over the years. For me, digital music is a better fit for long-term collecting and everyday listening.

I wanted to bring this digital record experience to Salt Player: enjoying the convenience of digital music while keeping some of the pleasure of collecting and appreciating physical albums, and sharing it with others who feel the same way.

## Features

- **Missing album cover**: after the host finishes loading without a usable cover, show the native placeholder beside a silver-white blank CD. Custom disc artwork still takes priority. Uses SPW's valid cover and completed read result; no album-cover filename whitelist or timeout-based absence detection.

- **Primary detail page**: keeps SPW's native album cover, lyrics, and playback controls.
- **Secondary display page**: clicking the album cover opens a larger, centered presentation of the complete cover and a CD on its right. The cover is not squeezed into half an image.
- **Custom disc artwork**: searches the current song's directory in the order `cd.png → cd.jpg → disc.png → disc.jpg`. If a higher-priority file cannot be decoded, the next file is tried.
- **Generated disc artwork**: when custom artwork is unavailable, a copy of the album cover fills a circular CD with a narrow white edge, translucent annular hub, translucent center hole and tiny white spindle. Two narrow silver bevels use adjacent highlights and grooves around a translucent hub field, preserving the artwork underneath; the main cover remains complete.
- **Transparent images**: custom PNG files retain their existing transparent backgrounds and center holes. An antialiased circular outer mask is applied. No extra center hole or hub ring is added.
- **Front-disc display**: enable the native plugin switch to draw the disc over the cover. Off by default. Front mode uses whole-disc motion with a short uniform fade. Compare at exposure 50; in front mode exposure controls horizontal travel, while 0 still shows only the cover.
- **Disc exposure setting**: open SPW Digital Disc settings in the plugin manager and adjust 0–100. Default 50 preserves the existing view; 100 shows the full disc. The host saves the value. The group fits the window, and cover-only mode returns without a retract delay.
- **Cross-album disc cache**: keeps up to 4 custom images under a combined 32 MiB normalized-pixel budget, including the active image. Least recently used entries are evicted. Candidate priority, file size, modification time and available file identity are checked before reuse; changed or deleted files are invalidated. Disabling the plugin or clearing current media releases the cache. Generated cover discs continue to use the host’s valid cover result.
- **Playback-linked rotation**: enable “Rotate while playing” to start gently over about 240 ms after ejection, then rotate once every 20 seconds at Standard speed. Pause and retraction freeze the angle; resume starts gently from that angle. Off by default; the cover stays still.
- **Page animations**: the cover enlarges first, then the CD gradually slides out from behind it. Clicking the combined display to return retracts the CD before invoking the native return action. The reflection follows the same animation progress. Normal track changes replay the eject animation without rescaling the cover; rapid changes eject only the last stably selected song's disc.
- **Combined reflection**: the cover and CD have a short reflection that fades downward.
- Artwork on local disks and accessible network shares is read only. Generated artwork is kept in memory for display.

## Results Showcase

https://github.com/user-attachments/assets/8a289172-d79b-4e2e-9ba2-c7dc9ee5d86c

## Installation and use

V1.5.0 has been tested successfully with the **Steam edition of SPW on Windows and Ubuntu 26.04.1 LTS (Linux)**. Interface mappings cover SPW 1.19.0 and 1.19.3 from the Microsoft Store. The plugin uses SPW's internal drawing interfaces, so later SPW versions may require a new adaptation.

1. Obtain the compiled `.spmod` file. The source ZIP cannot be installed directly; see the build instructions below to compile it yourself.
2. In SPW's plugin manager, use the local plugin import option to select the `.spmod` file.
3. Grant `class-transform` and `library-read` when prompted, enable the plugin, and restart SPW.
4. Play a song from the local machine or an accessible network share. Click the bottom playback bar to open the primary detail page, then click the album cover to open the secondary display page.
5. On the secondary page, click the combined cover/CD display to return. The CD retracts before the primary page returns. You can also return while the CD is sliding out; repeated clicks do not queue multiple return actions.

At Standard speed, ejection takes about 700 ms and full retraction up to about 650 ms, with quintic easing at the endpoints. A final displacement below one physical pixel can complete early before invoking native return. Returning during ejection preserves the current pose and brakes for up to about 80 ms before retracting, without passing the original endpoint. Returning from a fully open disc adds no braking delay.

After a track change, the plugin waits for a selection-stability interval of about 280 ms and for the cover and disc lookup to be ready before ejecting the disc. Further track changes during this interval replace the pending request; intermediate songs are not replayed in sequence. Once a cover source takes over, old nodes from that transition cannot overwrite it. When the page is collapsed to the playback bar and reopened, the plugin waits until the host has fully expanded before replaying the cover-first entry and disc eject animations. The expansion process does not consume the disc animation's time.

Collapsing the entire playback detail page or disabling the plugin cancels unfinished animations and delayed return actions, preventing remnants from appearing over the mini player.

## Where to put custom disc images

> [!IMPORTANT]
> **The audio and custom disc image must be regular files in the same folder.** The folder can be on a local drive, a mapped network drive, or an accessible UNC share. Artwork lookup skips symbolic links to either file.

For example, use `D:\Music\Album\cd.png` alongside `D:\Music\Album\01.flac`, or `\\NAS\Music\Album\cd.png` alongside `\\NAS\Music\Album\01.flac`.

| Priority | Filename |
| --- | --- |
| 1 | `cd.png` |
| 2 | `cd.jpg` |
| 3 | `disc.png` |
| 4 | `disc.jpg` |

If all four files are readable, `cd.png` takes priority. Use the lowercase names above. Songs in one directory share the artwork. Lookups use the access rights of the account running the player. HTTP and HTTPS streaming URLs are excluded from artwork lookup.

For a transparent background or center hole, use a PNG with those transparent areas already prepared.

The main supported and tested range is **1000 × 1000 to 4000 × 4000 pixels**, including 3000 × 3000 custom PNG artwork. Display copies are capped at **2048 × 2048 pixels**; smaller images are not upscaled and originals are unchanged. Generated discs use an independent host-cover snapshot. Custom artwork is normalized in the background and reused across same-directory track changes or returns to another album after file validation. The budget covers retained custom-artwork pixels only, excluding temporary decoding allocations, host covers and other player memory. High-memory artwork is subsampled during decoding based on its pixel format and the JVM heap limit. Images over 32 MiB, 8192 pixels on either side, or 16 Mi pixels in total are rejected. Images above 4000 × 4000 pixels fall outside the main supported and tested range.

## Prepare custom CD artwork (optional)

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
| Rotate while playing | Off | Soft start after ejection, then clockwise at about 20 seconds per turn at Standard speed; freezes on pause and continues on resume |
| Eject/retract speed | Standard | Slow, Standard or Fast for the cover transition and disc travel |
| Disc rotation speed | Standard | Applies when rotation is enabled. Slow, Standard and Fast take about 30, 20 and 12 seconds per turn |

Front mode moves the complete disc with a short uniform fade at entry and exit. Front and back modes share the same travel. At exposure 100, the cover and disc barely overlap, so layer order has little visible effect. The native slider can display decimals; rendering rounds to an integer percentage, so `50.062717` is treated as 50.

Only the disc rotates; the cover stays upright and the reflection shares the angle. Each start accelerates smoothly from rest over about 240 ms, then follows the selected speed (20 seconds per turn at Standard). Pausing or starting retraction freezes the current angle immediately. Playback resumes gently from that angle without replaying paused time. A new track starts upright. Ejection, retraction and exposure 0 do not rotate. Collapsing the secondary page to the mini player freezes the angle; reopening resumes after ejection completes. Returning to the primary page ends that presentation, so entering the secondary page again starts upright. Disabling rotation restores the upright orientation.

## Verified SPW host versions

| SPW host | Interface mapping | V1.4 offline regression |
| --- | --- | --- |
| 1.19.0 Microsoft Store | Adapted | 48 programs pass |
| 1.19.3 Microsoft Store | Adapted | 48 programs pass |

Verified on physical machines:

- Windows 10 with SPW 1.19.3 from the Microsoft Store.
- Windows with the Steam edition of SPW (plugin V1.5.0).
- Ubuntu 26.04.1 LTS (Linux) with the Steam edition of SPW (plugin V1.5.0).

Offline tests cover interface resolution, cover calls, configuration storage, images, animations and rotation state. They do not run Windows native playback or Skia rendering. Startup verifies playback-controller and sheet-state fingerprints, then locates the cover entry, caller and sheet-state accessor using metadata, signatures and a unique call relationship. The cover painter must be the unique structural match and pass an implementation fingerprint normalized for its own obfuscated identifiers and debug metadata. Discovery results are cached per host class loader. Missing or ambiguous targets prevent activation with a detailed error. Startup also validates playback and artwork-loading interfaces and rejects incompatible hosts. If playback state cannot be read during operation, rotation stops and a warning is logged.

Host updates that prevent loading take priority for compatibility fixes and may receive a patch release. A version-number change alone does not require a release when features still work. New adaptations retain verified older profiles where possible. Include the host version, channel, plugin version and full error when reporting a problem.

## Compatibility and limitations

- SPW 1.19.0 and 1.19.3 rendering and state verification profiles are included. The three cover-entry classes and the cover painter are discovered dynamically. The painter retains a normalized implementation fingerprint; sheet-state, playback and artwork-loading classes retain their existing fingerprint checks.
- Disc images are searched only in the audio file's directory using fixed filenames, without recursion, custom paths or automatic downloads.
- JPG preparation masks the outer circle but does not detect the disc, remove backgrounds inside the circle or create a center hole.
- If playback state cannot be read, rotation stops and logs `ROTATION_STATE_UNAVAILABLE`; artwork display retains its existing behavior.
- Window sizes, display scaling and rapid page changes depend on host behavior. Report problems using the repository's Issue templates.

## Implementation

The plugin is written in **Java** and uses the SPW plugin API, Compose drawing, and Skia image decoding. It connects through the identified `FullScreenCover` entry point and renders the complete cover and CD in an independent Canvas. Disc images are read asynchronously. Compose observable state coordinates the transition from the native cover to the custom display.

## Build an installation package from source

These steps generate a `.spmod` plugin. You do not need these development tools to install and use a prebuilt plugin.

### 1. Prepare the build environment

| Component | Requirement and purpose |
| --- | --- |
| JDK | Use the project's existing JDK 25 setup, including both `java` and `javac` |
| Gradle | Install Gradle 9.8 |
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
SPW_Digital_Disc_v1.5.0.spmod
```

This is the installation package that SPW can import. You do not need to ZIP the entire source directory or rename its extension. The `build` directory contains intermediate build output, not the installation package.

Import the `.spmod` through SPW's plugin manager, grant `class-transform` and `library-read`, enable it, and restart SPW. For page navigation and disc image placement, see “Installation and use” and “Where to put custom disc images” above.

### Build on Linux

Set `JAVA_HOME`, add the JDK and Gradle `bin` directories to `PATH`, and make sure `python3` is available. Run this in the source directory:

```sh
bash build.sh "/path/to/SPW/app/ffmpeg-x64.dll"
```

You still need the matching SPW Windows host file. Output is also placed in `dist/`. The build platform and host compatibility are separate: runtime compatibility still depends on matching host interfaces. See the verified versions above.

### Common build problems

| Message or symptom | What to do |
| --- | --- |
| `Set JAVA_HOME` | Point `JAVA_HOME` to the root directory of your JDK 25 installation |
| `gradle` or `python` is not recognized | Install the tool, configure `PATH`, and retry in the same terminal |
| `Usage: build.cmd ...` | The host path argument is missing; use the complete command shown above |
| Host file is missing or cannot be read as a ZIP | Check that the file is the matching SPW installation's `app\ffmpeg-x64.dll`, not a different file with the same name |
| `fingerprint mismatch`, missing host classes, or interface-related compilation errors | The host may not match the supported version; use the matching version instead of bypassing the checks |
| The build failed, but an older file is still in `dist` | An existing file does not mean this build succeeded; check the current Gradle result |

The pre-build check verifies SHA-256 fingerprints for the host archive and key classes, then resolves the cover entry and target by signature and verifies the unique static call site.

## Third-party components and license

The project uses the [MIT License](LICENSE).

See [Third-party notices](THIRD_PARTY_NOTICES.md) (Chinese) for details. The project does not distribute sample commercial album covers, disc artwork, or the SPW host and its dependencies. User-provided music and images are outside the scope of this project's MIT license.

