package io.github.tigerjx95.spwcdprobe;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;

/** Run with a graphical display. Uses the real settings action and native Swing windows. */
public class FramingWindowInteractionTest {
 private static JDialog visibleDialog(String title){
  for(var window:Window.getWindows())
   if(window instanceof JDialog dialog&&dialog.isShowing()&&title.equals(dialog.getTitle()))return dialog;
  return null;
 }

 private static JButton button(Container parent,String text){
  for(var component:parent.getComponents()){
   if(!component.isShowing())continue;
   if(component instanceof JButton b&&text.equals(b.getText()))return b;
   if(component instanceof Container child){
    var match=button(child,text);
    if(match!=null)return match;
   }
  }
  return null;
 }

 public static void main(String[] args)throws Exception{
  var config=Files.createTempDirectory("framing-window").resolve("config.json");
  var manager=new DiscSettingsTest.Manager(config,DiscSettingsTest.helper(config));
  var player=new JFrame("Player");
  var robot=new Robot();
  var changes=new AtomicInteger();
  var album=new DiscSettings.AlbumKey("Test",null,0,null);
  var first=new FramingAlbumLibrary.Entry(album,List.of("track 1"));
  var second=new FramingAlbumLibrary.Entry(album,List.of("track 2"));
  var playing=new AtomicReference<>(first);
  var slow=new java.util.concurrent.CompletableFuture<BufferedImage>();
  var red=new BufferedImage(200,200,BufferedImage.TYPE_INT_RGB);
  var graphics=red.createGraphics();
  graphics.setColor(Color.RED);graphics.fillRect(0,0,200,200);graphics.dispose();
  var reads=new AtomicInteger();
  var previews=new PlayingCoverPreview(entry->{
   if(SwingUtilities.isEventDispatchThread())throw new AssertionError("cover lookup ran on the UI thread");
   reads.incrementAndGet();
   return entry.equals(first)?java.util.concurrent.CompletableFuture.completedFuture(red):slow;
  });
  String title="封面裁切与盘面取景";
  try(var settings=new DiscSettings(manager,values->{});
      var framing=new CoverFramingDialog(settings,playing::get,(key,color)->{throw new AssertionError("duplicate read");},previews)){
   SwingUtilities.invokeAndWait(()->{
    player.setUndecorated(true);
    player.getContentPane().setBackground(Color.ORANGE);
    player.setBounds(50,50,1400,900);
    player.setVisible(true);
   });
   previews.request(first).get(5,TimeUnit.SECONDS);
   var firstOpen=new CountDownLatch(1);
   var firstPixel=new AtomicInteger();
   java.awt.event.AWTEventListener listener=event->{
    if(event.getID()==java.awt.event.WindowEvent.WINDOW_OPENED&&event.getSource() instanceof JDialog d&&title.equals(d.getTitle())){
     var box=d.getBounds();
     firstPixel.set(robot.getPixelColor(box.x+box.width/2,box.y+box.height/2).getRGB());
     firstOpen.countDown();
    }
   };
   Toolkit.getDefaultToolkit().addAWTEventListener(listener,AWTEvent.WINDOW_EVENT_MASK);
   try{
    CoverFramingDialog.open();
    if(!firstOpen.await(5,TimeUnit.SECONDS)||firstPixel.get()!=Color.RED.getRGB()||reads.get()!=1)
     throw new AssertionError("warm cover was not in the initial window paint");
   }finally{Toolkit.getDefaultToolkit().removeAWTEventListener(listener);}
   SwingUtilities.invokeAndWait(()->button(visibleDialog(title),"取消").doClick(0));
   robot.waitForIdle();
   playing.set(second);
   previews.request(second);
   CoverFramingDialog.open();
   robot.waitForIdle();
   SwingUtilities.invokeAndWait(()->{
    var box=visibleDialog(title).getBounds();
    if(robot.getPixelColor(box.x+box.width/2,box.y+box.height/2).equals(Color.RED))
     throw new AssertionError("same-album track showed a stale cover");
    if(!button(visibleDialog(title),"取消").isEnabled())throw new AssertionError("cold editor inaccessible");
   });
   Thread.sleep(1000); // Hold the artwork future while native controls process input.
   var blue=new BufferedImage(200,200,BufferedImage.TYPE_INT_RGB);
   var brush=blue.createGraphics();brush.setColor(Color.BLUE);brush.fillRect(0,0,200,200);brush.dispose();
   slow.complete(blue);
   robot.waitForIdle();
   SwingUtilities.invokeAndWait(()->{
    var box=visibleDialog(title).getBounds();
    if(!robot.getPixelColor(box.x+box.width/2,box.y+box.height/2).equals(Color.BLUE)||reads.get()!=2)
     throw new AssertionError("cold artwork did not appear from the shared background read");
   });
   var initial=new Rectangle();
   SwingUtilities.invokeAndWait(()->{
    var dialog=visibleDialog(title);
    var workspace=(CoverFramingDialog.AlbumWorkspace)dialog.getContentPane();
    var content=(CoverFramingDialog.FramingContent)workspace.getComponent(0);
    content.tabs.setSelectedIndex(1);
    initial.setBounds(dialog.getBounds());
    dialog.addComponentListener(new ComponentAdapter(){
     @Override public void componentMoved(ComponentEvent e){changes.incrementAndGet();}
     @Override public void componentResized(ComponentEvent e){changes.incrementAndGet();}
    });
   });
   for(int i=0;i<4;i++){
    SwingUtilities.invokeAndWait(()->{
     var editor=visibleDialog(title);
     button(editor,"选择专辑").doClick(0);
     var list=visibleDialog("选择专辑");
     if(list==null||list.getOwner()!=editor||!initial.equals(editor.getBounds()))
      throw new AssertionError("opening the picker moved the editor or failed to show the list");
     button(list,"收起列表").doClick(0);
    });
    robot.waitForIdle();
    SwingUtilities.invokeAndWait(()->{
     if(visibleDialog("选择专辑")!=null||!initial.equals(visibleDialog(title).getBounds())||changes.get()!=0)
      throw new AssertionError("toggling the list moved or resized the editor");
    });
   }
   SwingUtilities.invokeAndWait(()->{
    var editor=visibleDialog(title);
    button(editor,"选择专辑").doClick(0);
    editor.setLocation(65,80);
   });
   robot.waitForIdle();
   SwingUtilities.invokeAndWait(()->{
    var editor=visibleDialog(title);
    var list=visibleDialog("选择专辑");
    if(list.getX()!=editor.getX()+editor.getWidth()||list.getY()!=editor.getY())
     throw new AssertionError("picker did not follow the editor");
    button(editor,"取消").doClick(0);
   });
   robot.waitForIdle();
   SwingUtilities.invokeAndWait(()->{
    if(visibleDialog(title)!=null||visibleDialog("选择专辑")!=null)throw new AssertionError("owned window still visible");
   });
   System.out.println("PASS prepared cover present in first window paint; same-album cold open responsive during a 1 s read; four open/hide cycles with zero editor move/resize events; picker followed a move and closed with its owner");
  }finally{SwingUtilities.invokeAndWait(player::dispose);}
 }
}
