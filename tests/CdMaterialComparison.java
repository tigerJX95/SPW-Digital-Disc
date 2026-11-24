package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.drawscope.DrawScope;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;

public final class CdMaterialComparison {
 static void render(RootRasterTest.Raster raster, RootRenderer.Source source, float angle) throws Exception {
  RootRenderer.paint(raster.scope, 1, source, (s, x, y, d) -> false, raster::path,
      1, 100, false, null, angle);
  raster.masks.clear();
  raster.outlines.clear();
 }

 static RootRenderer.Source fixture(RootRasterTest.Raster raster, int color, boolean blank) {
  var ink = new Color(color, true);
  return new RootRenderer.Source() {
   public void draw(DrawScope scope, float width, float height) {
    raster.graphics.setColor(ink);
    raster.graphics.fill(new Rectangle2D.Float(0, 0, width, height));
   }
   public boolean blankDisc() { return blank; }
  };
 }

 static void clear(RootRasterTest.Raster raster) {
  raster.graphics.setComposite(AlphaComposite.Clear);
  raster.graphics.fillRect(0, 0, (int)raster.width, (int)raster.height);
  raster.graphics.setComposite(AlphaComposite.SrcOver);
 }

 public static void main(String[] args) throws Exception {
  int[] colors = {0xff514c49, 0xff152436, 0xffe4ded0};
  String[] names = {"blank", "dark-cover", "light-cover"};
  if (args.length == 1) {
   int side = 620;
   for (int kind = 0; kind < colors.length; kind++) {
    var raster = new RootRasterTest.Raster(side * 2, side);
    render(raster, fixture(raster, colors[kind], kind == 0), 0);
    ImageIO.write(raster.bitmap.getSubimage(side, 0, side, side), "png", Path.of(args[0], names[kind] + ".png").toFile());
    raster.graphics.dispose();
   }
  } else {
   int side = Integer.parseInt(args[0]);
   int kind = Integer.parseInt(args[1]);
   var raster = new RootRasterTest.Raster(side * 2, side);
   var source = fixture(raster, colors[kind], kind == 0);
   for (int i = 0; i < 180; i++) {
    clear(raster);
    render(raster, source, i % 360);
   }
   var times = new long[140];
   for (int i = 0; i < times.length; i++) {
    clear(raster);
    long start = System.nanoTime();
    render(raster, source, i % 360);
    times[i] = System.nanoTime() - start;
   }
   Arrays.sort(times);
   System.out.printf(Locale.ROOT, "%s,%d,%.4f,%.4f%n", names[kind], side, times[69] / 1e6, times[132] / 1e6);
   raster.graphics.dispose();
  }
 }
}
