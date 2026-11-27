package io.github.tigerjx95.spwcdprobe;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.MouseEvent;
import java.awt.im.InputContext;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.swing.*;
import static io.github.tigerjx95.spwcdprobe.DiscSettings.*;

public class CoverFramingDialogTest {
 private static <T extends Component> T find(Container parent,Class<T> type,Predicate<T> matches){
  for(var child:parent.getComponents()){
   if(!child.isVisible())continue;
   if(type.isInstance(child)&&matches.test(type.cast(child)))return type.cast(child);
   if(child instanceof Container container){
    T nested=find(container,type,matches);
    if(nested!=null)return nested;
   }
  }
  return null;
 }

 private static void layout(Container component){
  component.doLayout();
  for(var child:component.getComponents())if(child instanceof Container nested)layout(nested);
 }

 private static BufferedImage paint(JComponent panel){
  panel.setSize(panel.getPreferredSize());
  layout(panel);
  var image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_ARGB);
  var graphics=image.createGraphics();
  try{panel.paint(graphics);}finally{graphics.dispose();}
  return image;
 }

 private record Save(CoverWhiteBorders.Mode mode,AlbumFraming framing){}

 public static void main(String[] args)throws Exception{
  SwingUtilities.invokeAndWait(()->{
   var saves=new ArrayList<Save>();
   var cover=new BufferedImage(100,100,BufferedImage.TYPE_INT_ARGB);
   var graphics=cover.createGraphics();
   graphics.setColor(Color.WHITE);
   graphics.fillRect(0,0,100,100);
   graphics.setColor(new Color(0xa42b31));
   graphics.fillRect(20,0,60,100);
   graphics.setColor(new Color(0x214478));
   graphics.fillRect(28,9,44,82);
   graphics.dispose();
   var album=new DiscSettings.AlbumKey("测试专辑",null,2026,null);
   var panel=CoverFramingDialog.content(CoverWhiteBorders.Mode.OFF,album,AlbumFraming.FOLLOW,new VinylLabelEditor.Sample(cover,0xff449966),Style.CD,
       (mode,framing)->saves.add(new Save(mode,framing)),()->{});
   var tabs=find(panel,JTabbedPane.class,item->true);
   if(tabs.getTabCount()!=2||!List.of("大封面","盘面取景").equals(List.of(tabs.getTitleAt(0),tabs.getTitleAt(1))))
    throw new AssertionError("the shared settings contains both previews");
   var paired=find(panel,JRadioButton.class,item->"自动裁切对边".equals(item.getText()));
   paired.setSelected(true);
   paint(panel);
   var confirm=find(panel,JButton.class,item->"确认".equals(item.getText()));
   tabs.setSelectedIndex(1);
   var framingPage=(Container)tabs.getComponentAt(1);
   var heading=find(framingPage,JLabel.class,item->"当前专辑：测试专辑".equals(item.getText()));
   var reminder=find(framingPage,JLabel.class,item->"取景按专辑和盘面保存，自定义盘面优先。".equals(item.getText()));
   if(heading==null||!heading.getFont().isBold()||reminder==null||reminder.getForeground().getRGB()!=0xffff999f)
    throw new AssertionError("the framing page highlights the current album and the artwork-priority reminder");
   var vinyl=find(framingPage,JRadioButton.class,item->"黑胶／彩胶".equals(item.getText()));
   var cd=find(framingPage,JRadioButton.class,item->"CD".equals(item.getText()));
   var reel=find(framingPage,JRadioButton.class,item->"专辑封面开盘磁带".equals(item.getText()));
   if(!cd.isSelected())throw new AssertionError("open on the album's disc style");
   vinyl.doClick(0);
   paint(panel);
   var discHeading=find(framingPage,JLabel.class,item->"盘面".equals(item.getText()));
   var methodHeading=find(framingPage,JLabel.class,item->"取景方式".equals(item.getText()));
   var follow=find(framingPage,JRadioButton.class,item->"跟随大封面".equals(item.getText()));
   if(methodHeading==null||!methodHeading.getFont().isBold()||
       SwingUtilities.convertPoint(discHeading,0,0,framingPage).x!=SwingUtilities.convertPoint(methodHeading,0,0,framingPage).x||
       SwingUtilities.convertPoint(cd,0,0,framingPage).x!=SwingUtilities.convertPoint(follow,0,0,framingPage).x)
    throw new AssertionError("disc type and framing method are labeled and aligned");
   confirm.doClick(0);
   if(!saves.getLast().equals(new Save(CoverWhiteBorders.Mode.PAIRED,AlbumFraming.FOLLOW)))
    throw new AssertionError("following never saves an album override");
   find(framingPage,JRadioButton.class,item->"单独调整".equals(item.getText())).doClick(0);
   paint(panel);
   var trim=find(framingPage,JButton.class,item->"去成对白边".equals(item.getText()));
   var reset=find(framingPage,JButton.class,item->"重置取景".equals(item.getText()));
   if(Math.abs(trim.getX()*2+trim.getWidth()-trim.getParent().getWidth())>1||
       Math.abs(reset.getX()*2+reset.getWidth()-reset.getParent().getWidth())>1||
       SwingUtilities.convertPoint(trim,0,0,framingPage).y!=SwingUtilities.convertPoint(reset,0,0,framingPage).y)
    throw new AssertionError("editor buttons are centered in their columns and vertically aligned");
   confirm.doClick(0);
   if(!saves.getLast().framing().label().framing().crop().equals(new VinylLabelFraming.Crop(200,0,200,0)))
    throw new AssertionError("independent editor starts from the current global white-border crop");
   find(framingPage,JSlider.class,item->"裁切左侧".equals(item.getAccessibleContext().getAccessibleName())).setValue(280);
   find(framingPage,JSlider.class,item->"裁切上方".equals(item.getAccessibleContext().getAccessibleName())).setValue(90);
   find(framingPage,JSlider.class,item->"裁切右侧".equals(item.getAccessibleContext().getAccessibleName())).setValue(280);
   find(framingPage,JSlider.class,item->"裁切下方".equals(item.getAccessibleContext().getAccessibleName())).setValue(90);
   confirm.doClick(0);
   if(!saves.getLast().framing().label().framing().crop().equals(new VinylLabelFraming.Crop(280,90,280,90)))
    throw new AssertionError("manual trim can remove colored borders on all four sides");
   tabs.setSelectedIndex(0);
   find(panel,JRadioButton.class,item->"关闭".equals(item.getText())).setSelected(true);
   tabs.setSelectedIndex(1);
   trim.doClick(0);
   confirm.doClick(0);
   if(saves.getLast().mode()!=CoverWhiteBorders.Mode.OFF||
       !saves.getLast().framing().label().framing().crop().equals(new VinylLabelFraming.Crop(200,0,200,0)))
    throw new AssertionError("the disc trim uses paired borders on the original image and leaves the separate cover mode alone");
   var zoom=find(framingPage,JSlider.class,item->"标签缩放".equals(item.getAccessibleContext().getAccessibleName()));
   if(!zoom.isEnabled())throw new AssertionError("fill mode retains the horizontal zoom control");
   find(framingPage,JRadioButton.class,item->"保留构图".equals(item.getText())).setSelected(true);
   if(zoom.isEnabled())throw new AssertionError("switching to fit disables zoom");
   paint(panel);
   find(framingPage,JRadioButton.class,item->"铺满圆形".equals(item.getText())).setSelected(true);
   if(!zoom.isEnabled())throw new AssertionError("switching back restores the zoom control");
   if(saves.getLast().framing().label().target()!=FramingTarget.BOTH_VINYL)
    throw new AssertionError("new label defaults to applying to both vinyl finishes");
   cd.doClick(0);
   find(framingPage,JRadioButton.class,item->"单独调整".equals(item.getText())).doClick(0);
   find(framingPage,JSlider.class,item->"盘面缩放".equals(item.getAccessibleContext().getAccessibleName())).setValue(135);
   reel.doClick(0);
   if(!find(framingPage,JRadioButton.class,item->"跟随大封面".equals(item.getText())).isSelected())
    throw new AssertionError("reel starts with its own framing state");
   find(framingPage,JRadioButton.class,item->"单独调整".equals(item.getText())).doClick(0);
   find(framingPage,JSlider.class,item->"盘面缩放".equals(item.getAccessibleContext().getAccessibleName())).setValue(160);
   paint(panel);
   cd.doClick(0);
   if(find(framingPage,JSlider.class,item->"盘面缩放".equals(item.getAccessibleContext().getAccessibleName())).getValue()!=135)
    throw new AssertionError("switching previews restores CD's draft");
   confirm.doClick(0);
   var both=saves.getLast().framing();
   if(both.cd().zoom()!=135||both.reel().zoom()!=160||both.label().target()!=FramingTarget.BOTH_VINYL||
       both.label().framing().crop().equals(both.cd().crop()))
    throw new AssertionError("all three types retain their independent framing");
   vinyl.doClick(0);
   find(framingPage,JRadioButton.class,item->"黑胶".equals(item.getText())).doClick(0);
   confirm.doClick(0);
   if(saves.getLast().framing().label().target()!=FramingTarget.BLACK_VINYL)
    throw new AssertionError("vinyl still offers black and colored scopes");
   find(framingPage,JRadioButton.class,item->"跟随大封面".equals(item.getText())).doClick(0);
   confirm.doClick(0);
   if(saves.getLast().framing().label()!=null||!saves.getLast().framing().cd().equals(both.cd())||
       !saves.getLast().framing().reel().equals(both.reel()))
    throw new AssertionError("following clears only the selected framing");
   checkDelayedPreview(album,cover,both);
   checkLargePreviewWithoutBorders(album);
   checkSliderDrag();
  });
  checkLocatePlayback();
  checkAsyncAlbumSelection();
  checkAlbumSearch();
  System.out.println("PASS disc framing, flicker-free album selection, draft retention, album search, text input and white-border cropping");
 }

 private static void checkSliderDrag(){
  var slider=FramingControls.slider(0,100);
  int x=slider.getPreferredSize().width/2,y=slider.getPreferredSize().height/2;
  if(paint(slider).getRGB(x,y)!=Color.WHITE.getRGB())
   throw new AssertionError("the slider thumb is a solid white circle");
  var repaints=new ArrayList<Rectangle>();
  var previous=RepaintManager.currentManager(slider);
  RepaintManager.setCurrentManager(new RepaintManager(){
   @Override public void addDirtyRegion(JComponent component,int left,int top,int width,int height){
    if(component==slider)repaints.add(new Rectangle(left,top,width,height));
    super.addDirtyRegion(component,left,top,width,height);
   }
  });
  try{
   long time=System.currentTimeMillis();
   slider.dispatchEvent(new MouseEvent(slider,MouseEvent.MOUSE_PRESSED,time,InputEvent.BUTTON1_DOWN_MASK,
       x,y,1,false,MouseEvent.BUTTON1));
   repaints.clear();
   int to=slider.getWidth()*3/4;
   slider.dispatchEvent(new MouseEvent(slider,MouseEvent.MOUSE_DRAGGED,time,InputEvent.BUTTON1_DOWN_MASK,
       to,y,0,false,MouseEvent.NOBUTTON));
   if(slider.getValue()<=65||!repaints.contains(new Rectangle(0,0,slider.getWidth(),slider.getHeight())))
    throw new AssertionError("dragging changes the crop value and repaints the complete track");
   var dragged=paint(slider);
   for(int position=20;position<to-12;position++)
    if(dragged.getRGB(position,y)!=DiscOrderDialog.BLUE.getRGB())
     throw new AssertionError("the active track is continuous behind the moving thumb");
   if(dragged.getRGB(to,y)!=Color.WHITE.getRGB())
    throw new AssertionError("dragging keeps the thumb white at its new position");
   slider.dispatchEvent(new MouseEvent(slider,MouseEvent.MOUSE_RELEASED,time,0,
       to,y,1,false,MouseEvent.BUTTON1));
  }finally{RepaintManager.setCurrentManager(previous);}
 }

 private static void checkLocatePlayback()throws Exception{
  var config=Files.createTempDirectory("spw-cover-locate-").resolve("config.json");
  var manager=new DiscSettingsTest.Manager(config,DiscSettingsTest.helper(config));
  var first=new AlbumKey("专辑 A",null,2025,null);
  var second=new AlbumKey("专辑 B",null,2026,null);
  var playing=new AtomicReference<>(new FramingAlbumLibrary.Entry(first,List.of("a1")));
  var cover=new AtomicReference<>(solidCover(Color.RED));
  try(var settings=new DiscSettings(manager,values->{});
      var dialog=new CoverFramingDialog(settings,playing::get,(album,color)->{
       var entry=playing.get();
       return entry!=null&&entry.key().equals(album)?new VinylLabelEditor.Sample(cover.get(),0):null;
      })){
   SwingUtilities.invokeAndWait(()->{
    var workspace=dialog.new AlbumWorkspace(null,entry->CompletableFuture.completedFuture(null));
    try{
     var view=find(workspace,CoverFramingDialog.FramingContent.class,item->true);
     var large=paint(view);
     var locate=view.locatePlaying;
     var point=SwingUtilities.convertPoint(locate,locate.getWidth()/2,locate.getHeight()/2,view);
     if(point.x<=large.getWidth()/2||SwingUtilities.getDeepestComponentAt(view,point.x,point.y)!=locate)
      throw new AssertionError("the shared locate button is clickable at the top right");
     view.tabs.setSelectedIndex(1);
     setDiscZoom(view,145);
     playing.set(new FramingAlbumLibrary.Entry(second,List.of("b1")));
     cover.set(solidCover(Color.BLUE));
     if(find(view,JLabel.class,item->"当前专辑：专辑 A".equals(item.getText()))==null)
      throw new AssertionError("playback does not change the edit target until the button is pressed");
     view.tabs.setSelectedIndex(0);
     find(view,JRadioButton.class,item->"自动裁切对边".equals(item.getText())).setSelected(true);
     locate.doClick(0);
     view=find(workspace,CoverFramingDialog.FramingContent.class,item->true);
     if(view.tabs.getSelectedIndex()!=0||displayedCoverColor(view)!=Color.BLUE.getRGB()||
         !find(view,JRadioButton.class,item->"自动裁切对边".equals(item.getText())).isSelected())
      throw new AssertionError("locating from Large Cover keeps the tab and crop mode, and shows album B");

     view.tabs.setSelectedIndex(1);
     setDiscZoom(view,132);
     playing.set(new FramingAlbumLibrary.Entry(second,List.of("b2")));
     cover.set(solidCover(Color.GREEN));
     view.locatePlaying.doClick(0);
     view=find(workspace,CoverFramingDialog.FramingContent.class,item->true);
     if(view.tabs.getSelectedIndex()!=1||view.draft.get().cd().zoom()!=132)
      throw new AssertionError("same-album refresh keeps the disc tab and its draft");
     view.tabs.setSelectedIndex(0);
     if(displayedCoverColor(view)!=Color.GREEN.getRGB())
      throw new AssertionError("same-album refresh uses the new song's cover instead of the cached image");

     view.tabs.setSelectedIndex(1);
     playing.set(new FramingAlbumLibrary.Entry(first,List.of("a2")));
     cover.set(solidCover(Color.RED));
     view.locatePlaying.doClick(0);
     view=find(workspace,CoverFramingDialog.FramingContent.class,item->true);
     if(view.draft.get().cd().zoom()!=145)
      throw new AssertionError("locating album A restores its original draft");
     var picker=(FramingAlbumPicker)((BorderLayout)workspace.getLayout()).getLayoutComponent(BorderLayout.EAST);
     picker.setVisible(true);
     var playingEntry=playing.get();
     var anotherEntry=new FramingAlbumLibrary.Entry(second,List.of("b2"));
     clickAlbumRow(picker,playingEntry,anotherEntry);
     view=find(workspace,CoverFramingDialog.FramingContent.class,item->true);
     if(find(view,JLabel.class,item->"当前专辑：专辑 B".equals(item.getText()))==null||
         view.draft.get().cd().zoom()!=132)
      throw new AssertionError("clicking an album cover restores that album’s draft while A is playing");
     playing.set(null);
     view.locatePlaying.doClick(0);
     if(!"暂无可定位的播放曲目".equals(view.playbackHint.getText())||view.draft.get().cd().zoom()!=132)
      throw new AssertionError("no current song leaves the draft accessible and shows a hint");
    }finally{workspace.close();}
   });
  }
 }

 private static void checkAsyncAlbumSelection()throws Exception{
  var config=Files.createTempDirectory("spw-cover-switch-").resolve("config.json");
  var manager=new DiscSettingsTest.Manager(config,DiscSettingsTest.helper(config));
  var entries=List.of("A","B","C","D").stream().map(name->
      new FramingAlbumLibrary.Entry(new AlbumKey(name,null,2026,null),List.of(name))).toList();
  var first=entries.getFirst();
  var secondCover=new CompletableFuture<BufferedImage>();
  var thirdCover=new CompletableFuture<BufferedImage>();
  var missingCover=new CompletableFuture<BufferedImage>();
  var loads=Map.of("B",secondCover,"C",thirdCover,"D",missingCover);
  var playingEntry=new AtomicReference<>(first);
  var readCount=new java.util.concurrent.atomic.AtomicInteger();
  var workspaceRef=new AtomicReference<CoverFramingDialog.AlbumWorkspace>();
  try(var settings=new DiscSettings(manager,values->{});
      var dialog=new CoverFramingDialog(settings,playingEntry::get,(album,color)->
          album.equals(first.key())?new VinylLabelEditor.Sample(solidCover(Color.RED),0):null)){
   try{
    SwingUtilities.invokeAndWait(()->{
     var workspace=dialog.new AlbumWorkspace(null,entry->{readCount.incrementAndGet();return loads.get(entry.key().album());});
     workspaceRef.set(workspace);
     // Exercise displayability checks without exposing a native window in headless tests.
     workspace.setVisible(false);
     workspace.addNotify();
     var picker=(FramingAlbumPicker)((BorderLayout)workspace.getLayout()).getLayoutComponent(BorderLayout.EAST);
     picker.setVisible(true);
     var list=albumList(picker);
     ((DefaultListModel<FramingAlbumLibrary.Entry>)list.getModel()).addAll(entries);
     var view=find(workspace,CoverFramingDialog.FramingContent.class,item->true);
     view.tabs.setSelectedIndex(1);
     setDiscZoom(view,144);
     var hints=new ArrayList<String>();
     view.playbackHint.addPropertyChangeListener("text",event->hints.add((String)event.getNewValue()));
     playingEntry.set(entries.get(1));
     view.locatePlaying.doClick(0);
     view.locatePlaying.doClick(0);
     if(readCount.get()!=1)throw new AssertionError("repeated locate should share the in-flight cover read");
     clickAlbumRow(list,2);
     if(find(workspace,CoverFramingDialog.FramingContent.class,item->true)!=view||
         find(view,JLabel.class,item->"当前专辑：A".equals(item.getText()))==null||
         view.draft.get().cd().zoom()!=144)
      throw new AssertionError("loading another album preserves the current preview, edit target, and draft");
     if(!view.playbackHint.getText().isBlank()||!hints.isEmpty())
      throw new AssertionError("cover loading never flashes text beside the locate button");
    });
    thirdCover.complete(solidCover(Color.BLUE));
    secondCover.complete(solidCover(Color.GREEN));
    SwingUtilities.invokeAndWait(()->{
     var workspace=workspaceRef.get();
     var view=find(workspace,CoverFramingDialog.FramingContent.class,item->true);
     if(view.tabs.getSelectedIndex()!=1||find(view,JLabel.class,item->"当前专辑：C".equals(item.getText()))==null||
         view.draft.get().cd()!=null||!view.playbackHint.getText().isBlank())
      throw new AssertionError("only the latest selection installs its own editor and clears the loading hint");
     view.tabs.setSelectedIndex(0);
     if(displayedCoverColor(view)!=Color.BLUE.getRGB())
      throw new AssertionError("an older cover completion cannot replace the selected image");
     var list=albumList(find(workspace,FramingAlbumPicker.class,item->true));
     clickAlbumRow(list,0);
     view=find(workspace,CoverFramingDialog.FramingContent.class,item->true);
     if(view.draft.get().cd().zoom()!=144)
      throw new AssertionError("returning to the first album restores its separate draft");
     clickAlbumRow(list,3);
     if(find(workspace,CoverFramingDialog.FramingContent.class,item->true)!=view||
         find(view,JButton.class,item->"重新读取封面".equals(item.getText()))!=null)
      throw new AssertionError("a pending load does not install the unavailable-cover UI");
    });
    missingCover.complete(null);
    SwingUtilities.invokeAndWait(()->{
     var view=find(workspaceRef.get(),CoverFramingDialog.FramingContent.class,item->true);
     if(find(view,JLabel.class,item->"当前专辑：D".equals(item.getText()))==null||
         find(view,JButton.class,item->"重新读取封面".equals(item.getText()))==null||!view.playbackHint.getText().isBlank())
      throw new AssertionError("the unavailable-cover UI belongs to the album whose load finished empty");
    });
   }finally{
    var workspace=workspaceRef.get();
    if(workspace!=null)SwingUtilities.invokeAndWait(()->{
     workspace.close();
     workspace.removeNotify();
    });
   }
  }
 }

 @SuppressWarnings("unchecked")
 private static JList<FramingAlbumLibrary.Entry> albumList(FramingAlbumPicker picker){
  return (JList<FramingAlbumLibrary.Entry>)find(picker,JList.class,item->true);
 }

 private static void clickAlbumRow(FramingAlbumPicker picker,FramingAlbumLibrary.Entry first,
     FramingAlbumLibrary.Entry second){
  var list=albumList(picker);
  var entries=(DefaultListModel<FramingAlbumLibrary.Entry>)list.getModel();
  entries.addElement(first);
  entries.addElement(second);
  clickAlbumRow(list,1);
 }

 private static void clickAlbumRow(JList<?> list,int index){
  paint((FramingAlbumPicker)SwingUtilities.getAncestorOfClass(FramingAlbumPicker.class,list));
  var row=list.getCellBounds(index,index);
  list.dispatchEvent(new MouseEvent(list,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,
      row.x+12,row.y+row.height/2,1,false,MouseEvent.BUTTON1));
 }

 private static void setDiscZoom(CoverFramingDialog.FramingContent view,int zoom){
  find(view,JRadioButton.class,item->"单独调整".equals(item.getText())).doClick(0);
  find(view,JSlider.class,item->"盘面缩放".equals(item.getAccessibleContext().getAccessibleName())).setValue(zoom);
 }

 private static BufferedImage solidCover(Color color){
  var cover=new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB);
  var graphics=cover.createGraphics();
  graphics.setColor(color);
  graphics.fillRect(0,0,cover.getWidth(),cover.getHeight());
  graphics.dispose();
  return cover;
 }

 private static int displayedCoverColor(CoverFramingDialog.FramingContent view){
  var image=paint(view);
  var page=(JPanel)view.tabs.getSelectedComponent();
  var preview=((BorderLayout)page.getLayout()).getLayoutComponent(BorderLayout.CENTER);
  var point=SwingUtilities.convertPoint(preview,preview.getWidth()/2,preview.getHeight()/2,view);
  return image.getRGB(point.x,point.y);
 }

 private static void checkAlbumSearch()throws Exception{
  var mandopop=new FramingAlbumLibrary.Entry(new AlbumKey("18...","邓紫棋",2009,null),List.of());
  var zard=List.of("Don't you see!","Good-bye My Loneliness","心を開いて","負けないで","マイフレンド").stream()
      .map(title->new FramingAlbumLibrary.Entry(new AlbumKey(title,"ZARD",1997,null),List.of())).toList();
  var albums=new ArrayList<>(List.of(mandopop));
  albums.addAll(zard);
  var edited=new ArrayList<FramingAlbumLibrary.Entry>();
  var pickerRef=new AtomicReference<FramingAlbumPicker>();
  SwingUtilities.invokeAndWait(()->{
   String osName=System.getProperty("os.name");
   try{
    System.setProperty("os.name","Windows 11");
    pickerRef.set(new FramingAlbumPicker(mandopop,edited::add));
   }finally{System.setProperty("os.name",osName);}
   pickerRef.get().open(()->CompletableFuture.completedFuture(albums));
  });
  SwingUtilities.invokeAndWait(()->{
   var picker=pickerRef.get();
   var search=find(picker,JTextField.class,item->true);
   var scroll=find(picker,JScrollPane.class,item->true);
   var list=(JList<?>)scroll.getViewport().getView();
   class SearchInput extends InputContext {
    int textEvents,finished;
    @Override public void dispatchEvent(AWTEvent event){if(event instanceof InputMethodEvent)textEvents++;}
    @Override public void endComposition(){finished++;}
   }
   var input=new SearchInput();
   var parent=new JPanel(){@Override public InputContext getInputContext(){return input;}};
   parent.add(picker);
   try{
    if(scroll.getColumnHeader()!=null||find(picker,JButton.class,item->"自动裁切成对白边".equals(item.getText()))!=null)
     throw new AssertionError("the picker offers album navigation without bulk actions");
    search.addInputMethodListener(new InputMethodListener(){
     public void inputMethodTextChanged(InputMethodEvent event){}
     public void caretPositionChanged(InputMethodEvent event){}
    });
    String name="邓紫棋";
    search.dispatchEvent(new InputMethodEvent(search,InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
        new AttributedString(name).getIterator(),name.length(),null,null));
    if(input.textEvents!=1||!name.equals(search.getText()))
     throw new AssertionError("Windows search accepts committed IME text");
    parent.addNotify();
    try{
     picker.finishSearchInput();
     if(input.finished!=1)throw new AssertionError("finish search composition while the field is still showing");
    }finally{parent.removeNotify();}
    search.postActionEvent();
    if(list.getModel().getSize()!=1||!mandopop.equals(list.getModel().getElementAt(0)))
     throw new AssertionError("Chinese artist text matches the album");
    var edit=list.getActionForKeyStroke(KeyStroke.getKeyStroke("ENTER"));
    list.setSelectedIndex(0);
    edit.actionPerformed(null);
    search.setText("zard");
    search.postActionEvent();
    if(list.getModel().getSize()!=zard.size())throw new AssertionError("filter the library by artist");
    list.setSelectedIndex(1);
    edit.actionPerformed(null);
    if(!edited.equals(List.of(mandopop,zard.get(1))))
     throw new AssertionError("Enter opens only the selected album after changing the search");
    search.setText("没有匹配结果");
    search.postActionEvent();
    edit.actionPerformed(null);
    if(list.getModel().getSize()!=0||edited.size()!=2)
     throw new AssertionError("an empty search result cannot switch the album");
   }finally{picker.close();}
  });
 }

 private static void checkLargePreviewWithoutBorders(AlbumKey album){
  var panel=CoverFramingDialog.content(CoverWhiteBorders.Mode.PAIRED,album,AlbumFraming.FOLLOW,
      new VinylLabelEditor.Sample(solidCover(Color.BLUE),0),Style.CD,(mode,framing)->{},()->{});
  var page=(JPanel)panel.tabs.getSelectedComponent();
  if(displayedCoverColor(panel)!=Color.BLUE.getRGB()||
      find(page,JLabel.class,item->"未检测到成对白边，当前显示原图。".equals(item.getText()))==null)
   throw new AssertionError("an untrimmed cover still renders with an explicit explanation");
  var waiting=CoverFramingDialog.content(CoverWhiteBorders.Mode.PAIRED,album,AlbumFraming.FOLLOW,null,Style.CD,
      (mode,framing)->{},()->{});
  if(find(waiting,JLabel.class,item->item.getText().startsWith("暂时无法读取当前专辑封面"))==null)
   throw new AssertionError("a genuinely missing preview has its own retry message");
 }
 private static void checkDelayedPreview(AlbumKey album,BufferedImage cover,AlbumFraming saved){
  var saves=new ArrayList<Save>();
  var waiting=CoverFramingDialog.content(CoverWhiteBorders.Mode.OFF,album,saved,null,Style.CD,
      (mode,framing)->saves.add(new Save(mode,framing)),()->{});
  find(waiting,JRadioButton.class,item->"仅裁右侧白边".equals(item.getText())).setSelected(true);
  waiting.tabs.setSelectedIndex(1);
  find(waiting,JButton.class,item->"确认".equals(item.getText())).doClick(0);
  var draft=saves.getLast();
  if(!draft.equals(new Save(CoverWhiteBorders.Mode.RIGHT,saved)))
   throw new AssertionError("unavailable cover retains saved album framing and global draft");
  var panel=CoverFramingDialog.content(draft.mode(),album,draft.framing(),
      new VinylLabelEditor.Sample(cover,0xff449966),Style.CD,
      (mode,framing)->saves.add(new Save(mode,framing)),()->{});
  panel.tabs.setSelectedIndex(waiting.tabs.getSelectedIndex());
  if(panel.tabs.getSelectedIndex()!=1||find((Container)panel.tabs.getSelectedComponent(),JSlider.class,item->true)==null)
   throw new AssertionError("late preview restores the selected tab and enables its editor");
  paint(panel);
  find(panel,JButton.class,item->"确认".equals(item.getText())).doClick(0);
  if(!saves.getLast().equals(new Save(CoverWhiteBorders.Mode.RIGHT,saved)))
   throw new AssertionError("late preview preserves the settings draft and previously saved album framing");
 }
}
