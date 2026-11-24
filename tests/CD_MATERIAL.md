# CD 材质对照与性能基线

v1.13.0 正式版作为基线。自定义盘面直接使用原图。主画面与倒影复用封面 CD 的旋转图层。

## test.1 离线绘制记录

用相同的 JDK 和 SPW 1.19.3 宿主归档构建两个版本。`test_local.py` 编译测试程序后，使用测试版的 `build/tests` 与待测版本的生产类目录，并保持宿主归档和 JVM 选项相同。下例为 Linux。`classes` 设为待测版本的 `build/classes/java/main`，也可从 `.spmod` 提取 `classes/`。

```sh
cp="build/tests:$classes:$host"
mkdir -p "$preview_dir"
java -Djava.awt.headless=true -cp "$cp" io.github.tigerjx95.spwcdprobe.CdMaterialComparison "$preview_dir"
java -Xmx384m -XX:+UseSerialGC -Djava.awt.headless=true -cp "$cp" io.github.tigerjx95.spwcdprobe.CdMaterialComparison 420 0
```

最后两个参数分别是绘制边长和素材编号。`0` 为空白 CD，`1` 为深色封面 CD，`2` 为浅色封面 CD。每次预热 180 帧，测量 140 帧，按从小到大排序的第 133 帧取 P95。固定处理器亲和性并交替测试两个版本，取三轮各自的 P95 中位数。测量范围为生产绘制入口在 Java2D 适配器上的即时绘制，包含前景专辑封面，不含倒影合成、准备好的原生纹理或宿主换歌。

| 画面边长 | 场景 | v1.13.0 P95 | v1.14.0-cd-test.1 P95 | 基线比例 |
| --- | --- | ---: | ---: | ---: |
| 420 px | 空白 CD | 9.92 ms | 9.97 ms | 100.5% |
| 420 px | 深色封面 CD | 9.23 ms | 9.72 ms | 105.4% |
| 620 px | 空白 CD | 12.39 ms | 13.06 ms | 105.4% |
| 620 px | 深色封面 CD | 13.76 ms | 12.83 ms | 93.2% |

## RC1 离线复测

从 v1.13.0 正式包中提取生产类，与 RC1 使用同一个 Java2D 测试画布、深色封面、SPW 1.19.3 MS 归档和 JDK 25.0.5。每种尺寸交替运行三轮，第二轮先测 RC1。每次运行固定到同一逻辑处理器，JVM 使用 `-XX:ActiveProcessorCount=1 -XX:+UseSerialGC -Xmx384m`。每轮预热 180 帧，再测 140 帧。表内耗时单位为毫秒。

| 画面边长 | v1.13.0 三轮 P95 | RC1 三轮 P95 | 两版 P95 中位数 | RC1 / 基线 |
| --- | --- | --- | --- | ---: |
| 420 px | 7.60 / 7.00 / 8.55 | 7.73 / 7.83 / 7.15 | 7.60 → 7.73 | 101.7% |
| 620 px | 9.40 / 13.05 / 9.18 | 8.85 / 8.49 / 8.29 | 9.40 → 8.49 | 90.3% |

两组离线 P95 均在基线的 110% 以内。该结果只覆盖封面 CD 即时绘制。

## 实机对照

分别验证 Windows Microsoft Store、Windows Steam 和 Ubuntu Linux Steam。同一设备上的两个版本使用相同的播放器版本、窗口尺寸、系统缩放、素材、出碟量、倒影及旋转设置。每组交替测试并保留原始记录。新装首次出碟和预热后的重复出碟分别记录。

- 空白 CD、深浅封面 CD、带透明孔的自定义 CD 各观察一次，检查盘面反射、中心孔和旋转。对同一张封面 CD 观察一整圈，比较暂停与播放时白边外侧的轮廓。验证 1000–4000 px 封面和已有取景设置。
- 在二级页以相同顺序切换曲目，配对 `TRACK_ENTRY_PENDING` 和 `TRACK_ENTRY_START` 记录从观察到曲目变化到启动出碟的耗时。每组测量至少 20 次。
- 重复收回再展开，使用 `FIRST_REVEAL_DRAW elapsedUs` 对比首个出碟绘制回调，按 `cdRaster` 区分实时绘制和预合成盘面。同时观察旋转起步和退回封面的连贯性。离屏合成与屏幕帧耗时如有差异，以实机帧时间记录为准。

实机 P95 发布门槛以各平台 v1.13.0 同场景的 110% 为准。
