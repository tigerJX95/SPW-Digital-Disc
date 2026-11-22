package io.github.tigerjx95.spwcdprobe;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.*;
import static io.github.tigerjx95.spwcdprobe.DiscOrderDialog.*;

public final class DiscAppearanceDialog implements AutoCloseable {
 private static volatile DiscAppearanceDialog active;
 private final DiscSettings settings;
 private final Supplier<DiscSettings.AlbumKey> playingAlbum;
 private final Supplier<DiscSettings.ArtworkSource> artworkSource;
 private final Consumer<DiscSettings.Appearance> prepareDefault;
 private JDialog window;
 private boolean saving;
 private DiscSettings.AlbumKey windowAlbum;

 DiscAppearanceDialog(DiscSettings settings,Supplier<DiscSettings.AlbumKey> playingAlbum,Supplier<DiscSettings.ArtworkSource> artworkSource,
     Consumer<DiscSettings.Appearance> prepareDefault){
  this.settings=settings;
  this.playingAlbum=playingAlbum;
  this.artworkSource=artworkSource;
  this.prepareDefault=prepareDefault;
  active=this;
 }

 public static void open(){open(false);}
 public static void openAlbum(){open(true);}
 private static void open(boolean albumScope){
  var editor=active;
  if(editor!=null)SwingUtilities.invokeLater(()->{
   if(active==editor&&!GraphicsEnvironment.isHeadless())editor.show(albumScope);
  });
 }

 private void show(boolean albumScope){
  if(saving&&window!=null){window.toFront();return;}
  DiscSettings.AlbumKey album=null;
  if(albumScope){
   try{album=playingAlbum.get();}
   catch(RuntimeException|LinkageError unavailable){ /* Playback is not available. */ }
   if(album==null||album.album()==null){
    var owner=KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    JOptionPane.showMessageDialog(owner,"请先播放带有专辑信息的曲目。","当前专辑盘面样式",JOptionPane.INFORMATION_MESSAGE);
    return;
   }
  }
  if(window!=null){
   if(Objects.equals(windowAlbum,album)){window.toFront();return;}
   window.dispose();
  }
  var owner=KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
  String title=albumScope?"当前专辑盘面样式":"默认盘面样式";
  var dialog=new JDialog(owner,title,Dialog.ModalityType.MODELESS);
  window=dialog;
  windowAlbum=album;
  dialog.setUndecorated(true);
  dialog.getAccessibleContext().setAccessibleName(title);
  dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
  dialog.addWindowListener(new WindowAdapter(){
   @Override public void windowClosed(WindowEvent event){if(window==dialog)window=null;}
  });
  dialog.setContentPane(albumScope?albumContent(dialog,album):
      content(settings.appearance(),value->saveInBackground(dialog,()->saveDefault(value),"无法保存盘面样式。"),
          ()->{if(!saving)dialog.dispose();},"默认盘面样式",null,null,null));
  dialog.pack();
  dialog.setLocationRelativeTo(owner);
  dialog.setVisible(true);
 }

 boolean saveDefault(DiscSettings.Appearance appearance){
  if(!appearance.equals(settings.appearance()))prepareDefault.accept(appearance);
  return settings.saveAppearance(appearance);
 }

 private JComponent albumContent(JDialog dialog,DiscSettings.AlbumKey album){
  var saved=settings.albumAppearance(album);
  var reset=new ActionButton("恢复默认",false);
  reset.setEnabled(saved!=null);
  reset.addActionListener(event->saveInBackground(dialog,()->settings.saveAlbumAppearance(album,null),"无法清除专辑设置。"));
  String description=album.album()+(album.artist()==null?"":" · "+album.artist());
  return content(saved==null?settings.appearance():saved,
      value->saveInBackground(dialog,()->settings.saveAlbumAppearance(album,value),"无法保存盘面样式。"),
      ()->{if(!saving)dialog.dispose();},"当前专辑盘面样式",description,artworkSource.get(),reset);
 }

 private void saveInBackground(JDialog dialog,Supplier<Boolean> save,String failureMessage){
  if(saving)return;
  saving=true;
  dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
  var controls=new ArrayList<AbstractButton>();
  disableButtons(dialog.getContentPane(),controls);
  dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
  runSave(save,saved->{
   saving=false;
   if(active!=this||!dialog.isDisplayable())return;
   if(saved){dialog.dispose();return;}
   dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
   controls.forEach(button->button.setEnabled(true));
   dialog.setCursor(Cursor.getDefaultCursor());
   JOptionPane.showMessageDialog(dialog,failureMessage,"保存失败",JOptionPane.ERROR_MESSAGE);
  });
 }

 private static void disableButtons(Container parent,ArrayList<AbstractButton> controls){
  for(var child:parent.getComponents()){
   if(child instanceof AbstractButton button&&button.isEnabled()){
    controls.add(button);
    button.setEnabled(false);
   }else if(child instanceof Container container)disableButtons(container,controls);
  }
 }

 static void runSave(Supplier<Boolean> save,Consumer<Boolean> completion){
  new SwingWorker<Boolean,Void>(){
   @Override protected Boolean doInBackground(){return save.get();}
   @Override protected void done(){
    boolean saved=false;
    try{saved=get();}
    catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    catch(ExecutionException failed){ /* Report failure on the EDT. */ }
    completion.accept(saved);
   }
  }.execute();
 }

 static JLabel plainAlbumLabel(String description){
  var label=label("",14,Font.PLAIN,new Color(0xff999f));
  label.putClientProperty("html.disable",Boolean.TRUE);
  int length=description.codePointCount(0,description.length());
  label.setText(length>48?description.substring(0,description.offsetByCodePoints(0,48))+"…":description);
  return label;
 }

 static JComponent content(DiscSettings.Appearance initial,Predicate<DiscSettings.Appearance> save,Runnable close){
  return content(initial,value->{
   if(save.test(value))close.run();
  },close,"默认盘面样式",null,null,null);
 }
 private static JComponent content(DiscSettings.Appearance initial,Consumer<DiscSettings.Appearance> save,Runnable close,
      String title,String subtitle,DiscSettings.ArtworkSource artwork,JButton scopeAction){
  var panel=new JPanel(new BorderLayout(0,14));
  panel.setBackground(new Color(0x28343d));
  panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x47535c)),
      BorderFactory.createEmptyBorder(20,24,16,24)));
  var header=new JPanel(new BorderLayout(12,4));
  header.setOpaque(false);
  header.add(label(title,22,Font.BOLD,TEXT),BorderLayout.CENTER);
  if(scopeAction!=null)header.add(scopeAction,BorderLayout.EAST);
  if(subtitle!=null){
   var albumLabel=plainAlbumLabel(subtitle);
   albumLabel.setToolTipText("<html>"+subtitle.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+"</html>");
   var details=new JPanel(new GridLayout(0,1,0,4));
   details.setOpaque(false);
   details.add(albumLabel);
   String source="打开时的盘面："+artwork.description();
   var sourceLabel=label(source.length()>52?source.substring(0,52)+"…":source,14,Font.PLAIN,MUTED);
   sourceLabel.setToolTipText(source);
   details.add(sourceLabel);
   if(artwork.customImage()){
    details.add(label("自定义图片优先。没有可用的自定义图片时，才会显示所设样式。",14,Font.PLAIN,new Color(0xffcf87)));
   }
   header.add(details,BorderLayout.SOUTH);
  }
  panel.add(header,BorderLayout.NORTH);

  var fields=new JPanel();
  fields.setOpaque(false);
  fields.setLayout(new BoxLayout(fields,BoxLayout.Y_AXIS));
  var styles=new ButtonGroup();
  var colors=new ButtonGroup();
  var colorButtons=new ArrayList<JToggleButton>();
  var status=label("",14,Font.PLAIN,MUTED);
  var colorSection=new JPanel(new BorderLayout(0,8));
  colorSection.setOpaque(false);
  colorSection.setBorder(BorderFactory.createEmptyBorder(14,0,0,0));
  colorSection.getAccessibleContext().setAccessibleName("盘面颜色");
  var reelOptions=new ButtonGroup();
  var reelColors=new ButtonGroup();
  var reelPalette=new JPanel(new GridLayout(0,3,8,8));
  reelPalette.setOpaque(false);
  reelPalette.setBorder(BorderFactory.createEmptyBorder(8,0,0,0));
  reelPalette.getAccessibleContext().setAccessibleName("卷盘颜色");
  for(var color:DiscSettings.ReelColor.values()){
   var button=option(color.label,color.name(),reelColors,color==initial.reelColor(),
     color==DiscSettings.ReelColor.RANDOM?null:new Color(color.rgb));
   if(color==DiscSettings.ReelColor.RANDOM){
    var icon=new RandomSwatch(DiscSettings.ReelColor.PALETTE.stream().mapToInt(entry->entry.rgb).toArray());
    button.setIcon(icon);
    button.setDisabledIcon(icon);
   }
   reelPalette.add(button);
  }
  var reelSection=new JPanel(new GridLayout(1,2,8,0));
  reelSection.setOpaque(false);
  reelSection.setBorder(BorderFactory.createEmptyBorder(14,0,0,0));
  reelSection.getAccessibleContext().setAccessibleName("卷盘外观");
  reelSection.add(option("纯色",DiscSettings.Style.REEL.name(),reelOptions,
      initial.style()!=DiscSettings.Style.REEL_COVER,null));
  reelSection.add(option("专辑封面",DiscSettings.Style.REEL_COVER.name(),reelOptions,
      initial.style()==DiscSettings.Style.REEL_COVER,null));
  Runnable refresh=()->{
   var style=DiscSettings.Style.valueOf(styles.getSelection().getActionCommand());
   boolean enabled=style==DiscSettings.Style.COLORED_VINYL;
   for(var button:colorButtons)button.setEnabled(enabled);
   status.setText("当前选择："+DiscSettings.VinylColor.valueOf(colors.getSelection().getActionCommand()).label);
   boolean showReelPalette=style.reel()&&reelOptions.getSelection().getActionCommand().equals(DiscSettings.Style.REEL.name());
   if(colorSection.isVisible()!=enabled||reelSection.isVisible()!=style.reel()||reelPalette.isVisible()!=showReelPalette){
    reelPalette.setVisible(showReelPalette);
    reelSection.setVisible(style.reel());
    colorSection.setVisible(enabled);
    var dialog=SwingUtilities.getWindowAncestor(panel);
    if(dialog!=null)dialog.pack();
   }
  };
  var styleRow=new JPanel(new GridLayout(1,4,8,0));
  styleRow.setOpaque(false);
  for(var style:DiscSettings.Style.values()){
   if(style==DiscSettings.Style.REEL_COVER)continue;
   var button=option(style.label,style.name(),styles,style==initial.style()||style.reel()&&initial.style().reel(),null);
   var size=button.getPreferredSize();
   var insets=button.getInsets();
   int textWidth=button.getFontMetrics(button.getFont()).stringWidth(style.label);
   button.setPreferredSize(new Dimension(Math.max(136,Math.max(size.width,textWidth+insets.left+insets.right+12)),size.height));
   button.addActionListener(event->refresh.run());
   styleRow.add(button);
  }
  fields.add(styleRow);
  var palette=new JPanel(new GridLayout(0,3,8,8));
  palette.setOpaque(false);
  for(var color:DiscSettings.VinylColor.values()){
   if(color==DiscSettings.VinylColor.BLACK)continue;
   var button=option(color.label,color.name(),colors,color==initial.color(),color==DiscSettings.VinylColor.RANDOM?null:new Color(color.shade(85)));
   button.addActionListener(event->refresh.run());
   colorButtons.add(button);
   palette.add(button);
  }
  colorSection.add(palette,BorderLayout.CENTER);
  var colorActions=new JPanel(new BorderLayout(8,0));
  colorActions.setOpaque(false);
  colorActions.add(status,BorderLayout.CENTER);
  colorSection.add(colorActions,BorderLayout.SOUTH);
  fields.add(colorSection);
  fields.add(reelSection);
  fields.add(reelPalette);
  for(var button:reelSection.getComponents())((JToggleButton)button).addActionListener(event->refresh.run());
  panel.add(fields,BorderLayout.CENTER);
  refresh.run();

  var actions=new JPanel(new GridLayout(1,2,14,0));
  actions.setOpaque(false);
  var cancel=new ActionButton("取消",false);
  cancel.addActionListener(event->close.run());
  var confirm=new ActionButton("确认",true);
  confirm.addActionListener(event->{
   var style=DiscSettings.Style.valueOf(styles.getSelection().getActionCommand());
   var color=DiscSettings.VinylColor.valueOf(colors.getSelection().getActionCommand());
   if(style.reel())style=DiscSettings.Style.valueOf(reelOptions.getSelection().getActionCommand());
   var reelColor=DiscSettings.ReelColor.valueOf(reelColors.getSelection().getActionCommand());
   save.accept(new DiscSettings.Appearance(style,color,reelColor));
  });
  actions.add(cancel);
  actions.add(confirm);
  panel.add(actions,BorderLayout.SOUTH);
  panel.registerKeyboardAction(event->close.run(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
  return panel;
 }

 private static JToggleButton option(String text,String command,ButtonGroup group,boolean selected,Color swatch){
  var button=new JToggleButton(text){
   @Override protected void paintComponent(Graphics graphics){
    var g=(Graphics2D)graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    boolean highlighted=isEnabled()&&isSelected();
    g.setColor(highlighted?new Color(0x375669):new Color(255,255,255,isEnabled()?18:9));
    g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
    g.setColor(highlighted||hasFocus()?BLUE:new Color(255,255,255,32));
    g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
    g.dispose();
    super.paintComponent(graphics);
   }
  };
  button.setFont(UI_FONT.deriveFont(Font.PLAIN,15f));
  button.setForeground(TEXT);
  button.setBorder(BorderFactory.createEmptyBorder(10,12,10,12));
  button.setContentAreaFilled(false);
  button.setFocusPainted(false);
  button.setActionCommand(command);
  if(swatch!=null){
   button.setIcon(new Swatch(swatch));
   button.setDisabledIcon(new Swatch(swatch.darker().darker()));
   button.setIconTextGap(8);
  }
  if(command.equals(DiscSettings.VinylColor.RANDOM.name())){
   var icon=new RandomSwatch(java.util.Arrays.stream(DiscSettings.VinylColor.values())
     .filter(color->color!=DiscSettings.VinylColor.BLACK&&color!=DiscSettings.VinylColor.RANDOM)
     .mapToInt(color->color.shade(85)).toArray());
   button.setIcon(icon);
   button.setDisabledIcon(icon);
   button.setIconTextGap(8);
  }
  group.add(button);
  button.setSelected(selected);
  return button;
 }

 private record RandomSwatch(int[] colors) implements Icon {
  @Override public int getIconWidth(){return 20;}
  @Override public int getIconHeight(){return 20;}
  @Override public void paintIcon(Component component,Graphics graphics,int x,int y){
   var g=(Graphics2D)graphics.create();
   g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
   for(int i=0;i<colors.length;i++){
    var color=new Color(colors[i]);
    g.setColor(component.isEnabled()?color:color.darker().darker());
    g.fillArc(x,y,19,19,90+i*60,60);
   }
   g.setColor(new Color(255,255,255,65));
   g.drawOval(x,y,19,19);
   g.dispose();
  }
 }

 private record Swatch(Color color) implements Icon {
  @Override public int getIconWidth(){return 20;}
  @Override public int getIconHeight(){return 20;}
  @Override public void paintIcon(Component component,Graphics graphics,int x,int y){
   var g=(Graphics2D)graphics.create();
   g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
   g.setColor(color);
   g.fillOval(x,y,19,19);
   g.setColor(new Color(255,255,255,65));
   g.drawOval(x,y,19,19);
   g.dispose();
  }
 }

 @Override public void close(){
  if(active==this)active=null;
  SwingUtilities.invokeLater(()->{if(window!=null)window.dispose();});
 }
}
