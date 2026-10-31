# SPW Digital Disc

[中文](README.md) | [English](README_EN.md)

Adds a digital CD presentation to Salt Player for Windows (SPW), with a complete album cover and a CD extending from its right side.

Current version: **V1.0.0**, for **SPW 1.19.0 Microsoft Store**.

Building on the album cover/CD display and local disc artwork support, this version adds disc eject/retract animations, track-change animations, protection for rapid track changes, reopening behavior, and a translucent center hub ring.

## Background

This plugin was inspired by [**Georgia-ReBORN**](https://github.com/TT-ReBORN/Georgia-ReBORN), a foobar2000 theme I use regularly.

I love the way it presents an album cover together with a disc. I used to collect physical records, and I enjoyed holding an album and looking at its cover and disc artwork. But preserving physical CDs is not easy. Even with careful storage, they can age and deteriorate over the years. For me, digital music is a better fit for long-term collecting and everyday listening.

I wanted to bring this digital record experience to Salt Player: enjoying the convenience of digital music while keeping some of the pleasure of collecting and appreciating physical albums, and sharing it with others who feel the same way.

## Results Showcase

https://github.com/user-attachments/assets/f5748b0e-60d9-479d-b989-3f14c318be39


## Features

- **Primary detail page**: keeps SPW's native album cover, lyrics, and playback controls.
- **Secondary display page**: clicking the album cover opens a larger, centered presentation of the complete cover and a CD on its right. The cover is not squeezed into half an image.
- **Local disc artwork**: searches the current song's directory in the order `cd.png → cd.jpg → disc.png → disc.jpg`. If a higher-priority file cannot be decoded, the next file is tried.
- **Generated disc artwork**: when no usable local disc image is available, a copy of the album cover is cropped into a circular CD with a transparent center hole and a clearly defined translucent hub ring. The album cover in front remains complete.
- **Transparent images**: local PNG files retain their existing transparent backgrounds and center holes. No extra hole or hub ring is added.
- **Page animations**: the cover enlarges first, then the CD gradually slides out from behind it. Clicking the combined display to return retracts the CD before invoking the native return action. The reflection follows the same animation progress. Normal track changes replay the eject animation without rescaling the cover; rapid changes eject only the last stably selected song's disc.
- **Combined reflection**: the cover and CD have a short reflection that fades downward.
- Local files are read only. The plugin does not modify songs or original disc images, or save generated disc artwork to disk.

## Installation and use

The current adaptation is based on **SPW 1.19.0 Microsoft Store**. The plugin uses SPW's internal drawing interfaces, so later SPW versions may require a new adaptation.

1. Obtain the compiled `.spmod` file. The source ZIP cannot be installed directly; see the build instructions below to compile it yourself.
2. In SPW's plugin manager, use the local plugin import option to select the `.spmod` file.
3. Grant `class-transform` and `library-read` when prompted, enable the plugin, and restart SPW.
4. Play a local song. Click the bottom playback bar to open the primary detail page, then click the album cover to open the secondary display page.
5. On the secondary page, click the combined cover/CD display to return. The CD retracts before the primary page returns. You can also return while the CD is sliding out; repeated clicks do not queue multiple return actions.

After a track change, the plugin waits for a selection-stability interval of about 280 ms and for the cover and disc lookup to be ready before ejecting the disc. Further track changes during this interval replace the pending request; intermediate songs are not replayed in sequence. Once a cover source takes over, old nodes from that transition cannot overwrite it. When the page is collapsed to the playback bar and reopened, the plugin waits until the host has fully expanded before replaying the cover-first entry and disc eject animations. The expansion process does not consume the disc animation's time.

Collapsing the entire playback detail page or disabling the plugin cancels unfinished animations and delayed return actions, preventing remnants from appearing over the mini player. Different return paths and rapid interactions still leave room for improvement.

The current installation package is `SPW_Digital_Disc_V1.0.0.spmod`. The source ZIP is for development and building; choose the `.spmod` file when installing.

## Where to put local disc images

Place the disc image in **the same directory as the audio file**. For example, if the song is at `D:\Music\Album\01.flac`, put the disc image at `D:\Music\Album\cd.png`.

| Priority | Filename |
| --- | --- |
| 1 | `cd.png` |
| 2 | `cd.jpg` |
| 3 | `disc.png` |
| 4 | `disc.jpg` |

If all four files exist and are readable, `cd.png` is used. Use the lowercase filenames shown above. Songs in the same directory share that disc image. The plugin does not currently search parent directories or subdirectories, support custom disc image paths, or download disc artwork automatically.

For a transparent background or center hole, use a PNG with those transparent areas already prepared. The plugin also works without a disc image: it automatically generates a CD from the album cover.

## Prepare local CD artwork (optional)

You can use the plugin without preparing disc artwork: it will generate a CD from the album cover. To display actual disc artwork, follow the steps below. Photoshop is used as an example; basic knowledge of selections, layer masks, and transparent layers is enough. Other image editors supporting these features can also be used.

**The recommended size is 1000 × 1000 pixels, using a square PNG image.** Other dimensions have not been thoroughly tested and are not guaranteed to produce the same display results. Use the recommended size for consistency. This is a preparation recommendation, not the only size the plugin accepts.

1. **Start with a clear disc image**: use a clear CD scan or photograph, preferably taken straight on, without obvious tilt, distortion, or strong reflections. Keep the original file and edit a separate copy.
2. **Cut out the CD**: use an elliptical selection or layer mask to retain the complete disc and remove the surrounding background. Keep the outer edge smooth, without white or black fringes or jagged edges. Do not include the album cover in the disc image.
3. **Make the small center hole transparent**: remove only the actual small circular hole. The surrounding plastic hub is part of the disc; preserve its original appearance instead of cutting out the entire center area. Transparency already present in local artwork is retained. The plugin will not cut an extra hole or add a hub ring to that image.
4. **Prepare the canvas**: crop to a square canvas, center the disc with equal width and height, and resize proportionally to **1000 × 1000 pixels**. Bring the outer edge close to the canvas boundary without clipping it. Avoid excessive transparent margins, which make the displayed disc look smaller. Do not stretch an elliptical disc into a circle or expect enlarging a small image to recover missing detail.
5. **Export with transparency**: hide or remove unwanted background layers. Confirm that the outer background and center hole show the editor's transparency checkerboard rather than a white background. Keep an editable copy, then export a PNG with transparency enabled and name it `cd.png`. **JPG does not support a transparent background or center hole.** Renaming a JPG file does not convert it into a transparent PNG. The plugin can read JPG files but will not remove their backgrounds automatically.
6. **Place the file and check the result**: put `cd.png` in the song's directory. On Windows, show file extensions to avoid accidentally naming it `cd.png.png`. Inspect the outer edge, center hole, and any remaining fringes against both dark and light backgrounds. Then play a song from that directory and open the secondary display page to check the actual result.

Recommended result: **1000 × 1000 pixels, PNG format, transparent background, transparent small center hole, centered disc, and a complete outer edge without excessive margins**. The search order remains `cd.png → cd.jpg → disc.png → disc.jpg`. To use a lower-priority file, first move any higher-priority image you no longer intend to use out of the same directory.

## Areas for improvement

- **Transitions**: cover enlargement, eject, retract, and track-change animations are implemented. Timing and smoothness during rapid interactions, different return paths, and repeated expansion/collapse can still be improved.
- **Limited compatibility**: only the SPW version listed above has been tested. Different window sizes, scaling factors, and SPW updates require continued testing.
- **Limited configuration**: there is no plugin settings page yet. Disc filenames, search order, and display dimensions currently follow fixed settings.

## V1.0.0 initial release

Repository release versions start at V1.0.0. This release brings together features implemented during development, including these recent updates:

- **Enlarge first, then eject**: the enlarged cover appears first, then the CD slides out to the right along a smooth curve. The combined reflection moves with it.
- **Retract before returning**: clicking the secondary display waits for retraction to finish before returning to the primary page. Returning during ejection is supported, and repeated clicks do not queue extra returns.
- **Eject on track changes**: normal track changes replay ejection while keeping the cover's position and size. Rapid changes are coalesced so only the final stable selection ejects.
- **Cover source locking**: a new cover takes over after briefly stabilizing, excluding reverse overwrites from old nodes and addressing the flicker path where old and new covers alternate.
- **Reopening behavior**: reopening after collapsing the secondary page to the playback bar starts a new entry animation once fully expanded, preventing it from finishing while hidden.
- **Translucent center hub**: when there is no local disc artwork, a CD is generated from the album cover with a fully transparent center hole. The ring retains a silver-gray outline while obscuring less of the artwork. The currently selected ring colors reduce transparency slightly to give the hub a more solid appearance.

See [CHANGELOG.md](CHANGELOG.md) (Chinese) for detailed updates. Further work includes investigating occasional delays before an old cover refreshes, and improving transitions and compatibility.

V1.0.0 uses P4.10.5 as its code and color baseline. The installation package updates color constants and version information in the existing compiled output, with corresponding source changes included. A full Gradle build was not rerun for this version-packaging step. Instructions for a full build from source follow below.

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
| SPW host file | `app\ffmpeg-x64.dll` from the installation directory of the supported SPW 1.19.0 Microsoft Store version |

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
SPW_Digital_Disc_V1.0.0.spmod
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
