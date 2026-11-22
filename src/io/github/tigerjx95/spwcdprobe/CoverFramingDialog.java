package io.github.tigerjx95.spwcdprobe;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.*;

import static io.github.tigerjx95.spwcdprobe.DiscSettings.*;
import static io.github.tigerjx95.spwcdprobe.DiscOrderDialog.*;

public final class CoverFramingDialog implements AutoCloseable {
 private static final String TITLE="封面裁切与盘面取景";
 private static final String FRAMING_METHOD="取景方式";
 private static final Color BACKGROUND=new Color(0x28343d);
 private static final Color PREVIEW_BACKGROUND=new Color(0x18232c);
 private static final Color REMINDER=new Color(0xff999f);
 private static volatile CoverFramingDialog active;
 private final DiscSettings settings;
 private final Supplier<FramingAlbumLibrary.Entry> playingEntry;
 private final BiFunction<DiscSettings.AlbumKey,DiscSettings.VinylColor,VinylLabelEditor.Sample> sampleFor;
 private AlbumWorkspace workspace;
 private boolean saving;

 CoverFramingDialog(DiscSettings settings,Supplier<FramingAlbumLibrary.Entry> playingEntry,
     BiFunction<DiscSettings.AlbumKey,DiscSettings.VinylColor,VinylLabelEditor.Sample> sampleFor){
  this.settings=settings;
  this.playingEntry=playingEntry;
  this.sampleFor=sampleFor;
  active=this;
 }

 public static void open(){
  var dialog=active;
  if(dialog!=null)SwingUtilities.invokeLater(()->{
   if(active==dialog&&!GraphicsEnvironment.isHeadless())dialog.show();
  });
 }

 private void show(){
  if(workspace!=null&&workspace.dialog.isDisplayable()){workspace.dialog.toFront();return;}
  var owner=KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
  var dialog=new JDialog(owner,TITLE,Dialog.ModalityType.MODELESS);
  dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
  var workspace=new AlbumWorkspace(dialog,entry->FramingAlbumLibrary.cover(entry,480));
  this.workspace=workspace;
  dialog.addWindowListener(new WindowAdapter(){
   @Override public void windowClosing(WindowEvent event){if(!saving)workspace.requestClose();}
   @Override public void windowClosed(WindowEvent event){
    workspace.close();
    if(CoverFramingDialog.this.workspace==workspace)CoverFramingDialog.this.workspace=null;
   }
  });
  dialog.setContentPane(workspace);
  dialog.pack();
  dialog.setLocationRelativeTo(owner);
  dialog.setVisible(true);
 }

 private FramingAlbumLibrary.Entry readPlayingEntry(){
  try{return playingEntry.get();}
  catch(RuntimeException|LinkageError unavailable){return null;}
 }

 private void save(AlbumWorkspace workspace,CoverWhiteBorders.Mode mode,Map<AlbumKey,AlbumFraming> albums,
     Map<String,FramingChoice> presets){
  if(saving)return;
  saving=true;
  var dialog=workspace.dialog;
  workspace.cancelCoverRequest();
  workspace.picker.finishSearchInput();
  dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
  DiscAppearanceDialog.runSave(()->settings.saveCoverFraming(mode,albums,presets),saved->{
   saving=false;
   if(active!=this||workspace.closed||!dialog.isDisplayable())return;
   if(saved){workspace.requestClose();return;}
   dialog.setCursor(Cursor.getDefaultCursor());
   workspace.picker.finishSearchInput();
   JOptionPane.showMessageDialog(dialog,"无法保存封面裁切与盘面取景。","保存失败",JOptionPane.ERROR_MESSAGE);
  });
 }

 final class AlbumWorkspace extends JPanel {
  private record CoverRequest(FramingAlbumLibrary.Entry entry,boolean openFraming){}

  private final JDialog dialog;
  private final Function<FramingAlbumLibrary.Entry,CompletionStage<BufferedImage>> loadCover;
  private final CoverFraming original=settings.coverFraming();
  private final Map<AlbumKey,AlbumFraming> drafts=new LinkedHashMap<>();
  private final LinkedHashMap<AlbumKey,VinylLabelEditor.Sample> covers=new LinkedHashMap<>(12,.75f,true);
  private final FramingAlbumPicker picker;
  private final Timer retry=new Timer(350,event->retryCurrent());
  private AlbumKey selectedAlbum;
  private FramingAlbumLibrary.Entry selectedEntry;
  private FramingContent view;
  private CoverRequest pendingCover;
  private int attempts;
  private boolean hasCover,readingCover,closed;

  AlbumWorkspace(JDialog dialog,Function<FramingAlbumLibrary.Entry,CompletionStage<BufferedImage>> loadCover){
   super(new BorderLayout());
   this.dialog=dialog;
   this.loadCover=loadCover;
   selectedEntry=readPlayingEntry();
   selectedAlbum=selectedEntry==null?null:selectedEntry.key();
   picker=new FramingAlbumPicker(selectedEntry,this::editAlbum);
   picker.setVisible(false);
   add(picker,BorderLayout.EAST);
   var preview=sample(selectedAlbum);
   rebuild(original.mode(),0,null,preview);
   if(preview==null&&selectedEntry!=null)selectAlbum(selectedEntry,false);
  }

  private Appearance appearance(AlbumKey album){
   var saved=album==null?null:settings.albumAppearance(album);
   return saved==null?settings.appearance():saved;
  }

  private VinylLabelEditor.Sample sample(AlbumKey album){
   if(album==null)return null;
   var cached=covers.get(album);
   if(cached!=null)return cached;
   var result=sampleFor.apply(album,appearance(album).color());
   if(result!=null)cache(album,result);
   return result;
  }

  private void cache(AlbumKey album,VinylLabelEditor.Sample value){
   covers.put(album,value);
   if(covers.size()>12)covers.pollFirstEntry();
  }

  private void rebuild(CoverWhiteBorders.Mode mode,int tab,Style selectedStyle,VinylLabelEditor.Sample sample){
   if(view!=null)remove(view);
   hasCover=sample!=null;
   AlbumKey album=selectedAlbum!=null&&selectedAlbum.album()!=null?selectedAlbum:null;
   view=content(mode,album,framing(album),sample,appearance(selectedAlbum).style(),
       (next,current)->{
        if(closed)return;
        remember(album,current);
        save(this,next,Map.copyOf(drafts),original.presets());
       },()->{if(!saving)requestClose();});
   var choose=new ActionButton("选择专辑",false);
   choose.addActionListener(event->togglePicker());
   view.albumActions.add(choose,BorderLayout.LINE_END);
   view.locatePlaying.addActionListener(event->locatePlayingAlbum());
   view.tabs.setSelectedIndex(tab);
   if(selectedStyle!=null)view.selectStyle(selectedStyle);
   if(sample==null&&selectedAlbum!=null){
    var reload=new ActionButton("重新读取封面",false);
    reload.addActionListener(event->selectAlbum(selectedEntry,false));
    view.actions.add(reload,1);
   }
   add(view,BorderLayout.CENTER);
   revalidate();
   repaint();
  }

  private void togglePicker(){
   if(saving||closed)return;
   if(!picker.isVisible())setPickerVisible(true);
   else{
    picker.finishSearchInput();
    SwingUtilities.invokeLater(()->{if(!closed)setPickerVisible(false);});
   }
  }

  private void setPickerVisible(boolean visible){
   picker.setVisible(visible);
   if(visible)picker.open(FramingAlbumLibrary::albums);
   dialog.pack();
   dialog.setLocationRelativeTo(dialog.getOwner());
  }

  private void editAlbum(FramingAlbumLibrary.Entry entry){
   if(saving||closed)return;
   if(pendingCover!=null&&pendingCover.entry().key().equals(entry.key()))return;
   boolean sameAlbum=Objects.equals(selectedAlbum,entry.key());
   if(sameAlbum&&hasCover){
    cancelCoverRequest();
    selectedEntry=entry;
    picker.setEditingAlbum(entry);
    return;
   }
   selectAlbum(entry,!sameAlbum);
  }

  private void locatePlayingAlbum(){
   if(saving||closed)return;
   var entry=readPlayingEntry();
   if(entry==null){
    cancelCoverRequest();
    view.playbackHint.setText("暂无可定位的播放曲目");
    return;
   }
   covers.remove(entry.key());
   selectAlbum(entry,false);
  }

  private void selectAlbum(FramingAlbumLibrary.Entry entry,boolean openFraming){
   if(saving||closed)return;
   cancelCoverRequest();
   var request=new CoverRequest(entry,openFraming);
   var ready=sample(entry.key());
   if(ready!=null){showAlbum(request,ready);return;}

   // Keep the preview and its edit target together while the next cover is loading.
   pendingCover=request;
   var playing=readPlayingEntry();
   if(playing!=null&&entry.key().equals(playing.key())){
    attempts=0;
    retry.start();
    if(entry.trackIds().isEmpty())entry=playing;
   }
   readingCover=true;
   try{
    loadCover.apply(entry).whenComplete((image,error)->
        SwingUtilities.invokeLater(()->coverLoaded(request,error==null?image:null)));
   }catch(RuntimeException|LinkageError unavailable){
    SwingUtilities.invokeLater(()->coverLoaded(request,null));
   }
  }

  private void coverLoaded(CoverRequest request,BufferedImage image){
   if(closed||saving||pendingCover!=request||!isDisplayable())return;
   readingCover=false;
   if(image==null){
    if(!retry.isRunning())showAlbum(request,null);
    return;
   }
   var album=request.entry().key();
   var color=appearance(album).color().resolve(album.album(),album.artist(),album.artist(),album.year(),null,0);
   var preview=new VinylLabelEditor.Sample(image,color.shade(65));
   cache(album,preview);
   showAlbum(request,preview);
  }

  private void retryCurrent(){
   var request=pendingCover;
   if(request==null||closed||saving||!isDisplayable()){retry.stop();return;}
   var ready=sample(request.entry().key());
   if(ready!=null)showAlbum(request,ready);
   else if(++attempts>=12){
    retry.stop();
    if(!readingCover)showAlbum(request,null);
   }
  }

  private void cancelCoverRequest(){
   pendingCover=null;
   readingCover=false;
   retry.stop();
   view.playbackHint.setText("");
  }

  private void showAlbum(CoverRequest request,VinylLabelEditor.Sample preview){
   cancelCoverRequest();
   remember(selectedAlbum,view.draft.get());
   selectedEntry=request.entry();
   selectedAlbum=selectedEntry.key();
   picker.setEditingAlbum(selectedEntry);
   boolean restoreEditorSize=!hasCover&&preview!=null;
   int tab=request.openFraming()?1:view.tabs.getSelectedIndex();
   rebuild(selected(view.modes),tab,view.selectedStyle(),preview);
   if(restoreEditorSize&&dialog!=null){
    var preferred=dialog.getPreferredSize();
    if(dialog.getWidth()<preferred.width||dialog.getHeight()<preferred.height)
     dialog.setSize(Math.max(dialog.getWidth(),preferred.width),Math.max(dialog.getHeight(),preferred.height));
   }
  }

  private AlbumFraming framing(AlbumKey album){
   return album==null?AlbumFraming.FOLLOW:drafts.getOrDefault(album,original.forAlbum(album));
  }

  private void remember(AlbumKey album,AlbumFraming value){
   if(album==null||album.album()==null)return;
   if(value.equals(original.forAlbum(album)))drafts.remove(album);
   else drafts.put(album,value);
  }

  private void requestClose(){
   if(closed)return;
   picker.finishSearchInput();
   close();
   // Let queued input-method callbacks run while the native window still exists.
   SwingUtilities.invokeLater(dialog::dispose);
  }

  void close(){
   if(closed)return;
   closed=true;
   cancelCoverRequest();
   picker.close();
   covers.clear();
  }
 }

 static final class FramingContent extends JPanel {
  final JTabbedPane tabs=FramingControls.tabs();
  final ButtonGroup modes=new ButtonGroup();
  final JPanel actions=new JPanel(new GridLayout(1,0,10,0));
  final JPanel albumActions=new JPanel(new BorderLayout(12,0));
  final ActionButton locatePlaying=new ActionButton("定位正在播放的专辑",false);
  final JLabel playbackHint=label("",13,Font.PLAIN,MUTED);
  final ButtonGroup styles=new ButtonGroup();
  Supplier<AlbumFraming> draft;
  Style initialStyle=Style.CD;
  Style selectedStyle(){return styles.getSelection()==null?initialStyle:Style.valueOf(styles.getSelection().getActionCommand());}
  void selectStyle(Style style){
   initialStyle=style;
   for(var option:Collections.list(styles.getElements())){
    if(option.getActionCommand().equals(style.name())){option.setSelected(true);return;}
   }
  }
  FramingContent(){super(new BorderLayout(0,12));}
 }

 static FramingContent content(CoverWhiteBorders.Mode mode,AlbumKey album,AlbumFraming initial,
     VinylLabelEditor.Sample sample,Style currentStyle,BiConsumer<CoverWhiteBorders.Mode,AlbumFraming> save,Runnable cancel){
  var panel=new FramingContent();
  panel.setBackground(BACKGROUND);
  panel.setBorder(BorderFactory.createEmptyBorder(14,18,14,18));
  var tabs=panel.tabs;
  var modes=panel.modes;
  var large=new JPanel(new BorderLayout(0,8));
  large.setBackground(FramingControls.PANEL);
  large.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));
  var controls=new JPanel(new FlowLayout(FlowLayout.LEFT,10,6));
  controls.setOpaque(false);
  controls.add(label("白边裁切",15,Font.BOLD,TEXT));
  String[] modeLabels={"关闭","自动裁切对边","仅裁右侧白边"};
  for(var choice:CoverWhiteBorders.Mode.values()){
   var option=FramingControls.radio(modeLabels[choice.ordinal()],choice==mode);
   option.setActionCommand(choice.name());
   modes.add(option);
   controls.add(option);
  }
  large.add(controls,BorderLayout.NORTH);
  var cropHint=label(" ",14,Font.PLAIN,MUTED);
  large.add(cropHint,BorderLayout.SOUTH);

  Supplier<CoverWhiteBorders.Bounds> currentBounds=()->null;
  if(sample==null){
   large.add(placeholder(album==null?"请播放一首有封面的曲目。":"暂时无法读取当前专辑封面，可点「重新读取封面」再试。"));
   cropHint.setText("取得封面后才能预览裁切结果。裁切设置用于二级展示页。");
  }else{
   var preview=new LargePreview(sample);
   var bounds=new EnumMap<CoverWhiteBorders.Mode,CoverWhiteBorders.Bounds>(CoverWhiteBorders.Mode.class);
   var image=sample.image();
   Supplier<CoverWhiteBorders.Bounds> crop=()->bounds.computeIfAbsent(selected(modes),
       key->CoverWhiteBorders.find(image.getWidth(),image.getHeight(),image::getRGB,key));
   currentBounds=crop;
   large.add(preview,BorderLayout.CENTER);
   Runnable refresh=()->{
    var area=crop.get();
    preview.setCrop(area);
    boolean trimmed=area.trimmedFrom(image.getWidth(),image.getHeight());
    cropHint.setText(switch(selected(modes)){
     case OFF -> "白边裁切已关闭，当前显示原图。";
     case PAIRED -> trimmed?"已裁切成对白边。":"未检测到成对白边，当前显示原图。";
     case RIGHT -> trimmed?"已裁切右侧白边。":"需要左右两侧均有连续白边，当前显示原图。";
    });
   };
   refresh.run();
   onSelection(modes,refresh);
  }
  panel.initialStyle=currentStyle.vinyl()?Style.COLORED_VINYL:currentStyle.reel()?Style.REEL_COVER:Style.CD;
  var framing=framingPage(panel,album,initial,sample,currentStyle,currentBounds,modes);
  panel.draft=framing.value();
  tabs.addTab("大封面",large);
  tabs.addTab("盘面取景",framing.panel());
  var pages=new JLayeredPane();
  pages.setLayout(new GridBagLayout());
  var pageBounds=new GridBagConstraints();
  pageBounds.gridx=0;
  pageBounds.gridy=0;
  pageBounds.weightx=1;
  pageBounds.weighty=1;
  pageBounds.fill=GridBagConstraints.BOTH;
  pages.add(tabs,pageBounds);
  var header=new JPanel(new BorderLayout(8,0));
  header.setOpaque(false);
  panel.locatePlaying.setFont(UI_FONT.deriveFont(Font.BOLD,14f));
  panel.locatePlaying.setBorder(BorderFactory.createEmptyBorder(6,12,6,12));
  panel.locatePlaying.setToolTipText("显示正在播放的专辑和封面");
  header.add(panel.playbackHint,BorderLayout.CENTER);
  header.add(panel.locatePlaying,BorderLayout.LINE_END);
  var headerBounds=new GridBagConstraints();
  headerBounds.gridx=0;
  headerBounds.gridy=0;
  headerBounds.anchor=GridBagConstraints.NORTHEAST;
  pages.setLayer(header,JLayeredPane.PALETTE_LAYER);
  pages.add(header,headerBounds);
  panel.add(pages,BorderLayout.CENTER);

  var actions=panel.actions;
  actions.setOpaque(false);
  var cancelButton=new ActionButton("取消",false);
  var confirm=new ActionButton("确认",true);
  cancelButton.addActionListener(event->cancel.run());
  confirm.addActionListener(event->save.accept(selected(modes),framing.value().get()));
  actions.add(cancelButton);
  actions.add(confirm);
  panel.add(actions,BorderLayout.SOUTH);
  panel.registerKeyboardAction(event->cancel.run(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
  return panel;
 }

 private record FramingPage(JPanel panel,Supplier<AlbumFraming> value){}
 private record Editor(JPanel panel,Supplier<FramingChoice> value){
  VinylLabelFraming faceFraming(){
   var draft=value.get();
   return draft==null?null:draft.framing();
  }
 }

 private static FramingPage framingPage(FramingContent controls,AlbumKey album,AlbumFraming initial,VinylLabelEditor.Sample sample,Style currentStyle,
     Supplier<CoverWhiteBorders.Bounds> crop,ButtonGroup modes){
  var panel=new JPanel(new BorderLayout(0,8));
  panel.setBackground(FramingControls.PANEL);
  panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));
  var heading=new JPanel();
  heading.setLayout(new BoxLayout(heading,BoxLayout.Y_AXIS));
  heading.setOpaque(false);
  controls.albumActions.setOpaque(false);
  controls.albumActions.setAlignmentX(Component.LEFT_ALIGNMENT);
  if(album!=null){
   String description="当前专辑："+album.album()+(album.artist()==null?"":" · "+album.artist());
   var identity=DiscAppearanceDialog.plainAlbumLabel(description);
   identity.setFont(identity.getFont().deriveFont(Font.BOLD));
   identity.setToolTipText(description);
   controls.albumActions.add(identity,BorderLayout.CENTER);
  }
  heading.add(controls.albumActions);
  panel.add(heading,BorderLayout.NORTH);
  if(sample==null){
   panel.add(placeholder(album==null?"请先播放带有专辑信息的曲目，或选择专辑。":"当前专辑的封面预览暂不可用。"));
   return new FramingPage(panel,()->initial);
  }

  var previewRow=optionRow();
  var previewLabel=label("盘面",14,Font.BOLD,TEXT);
  var labelSize=previewLabel.getPreferredSize();
  labelSize.width=previewLabel.getFontMetrics(previewLabel.getFont()).stringWidth(FRAMING_METHOD);
  previewLabel.setPreferredSize(labelSize);
  previewRow.add(previewLabel);
  var previews=controls.styles;
  var defaultStyle=controls.initialStyle;
  option(previewRow,previews,"CD",Style.CD.name(),defaultStyle==Style.CD);
  option(previewRow,previews,"黑胶／彩胶",Style.COLORED_VINYL.name(),defaultStyle==Style.COLORED_VINYL);
  option(previewRow,previews,"封面磁带",Style.REEL_COVER.name(),defaultStyle==Style.REEL_COVER);
  heading.add(previewRow);

  var cd=editor(album,faceChoice(FramingTarget.CD,initial.cd()),sample,Style.CD,crop,modes);
  var vinyl=editor(album,initial.label(),sample,currentStyle.vinyl()?currentStyle:Style.COLORED_VINYL,crop,modes);
  var reel=editor(album,faceChoice(FramingTarget.REEL_COVER,initial.reel()),sample,Style.REEL_COVER,crop,modes);
  var cards=new CardLayout();
  var body=new JPanel(cards);
  body.setOpaque(false);
  body.add(cd.panel(),Style.CD.name());
  body.add(vinyl.panel(),Style.COLORED_VINYL.name());
  body.add(reel.panel(),Style.REEL_COVER.name());
  Runnable showPreview=()->cards.show(body,previews.getSelection().getActionCommand());
  onSelection(previews,showPreview);
  showPreview.run();
  panel.add(body,BorderLayout.CENTER);
  String hint=album==null?"当前曲目没有专辑信息，盘面取景跟随大封面。":"取景按专辑和盘面保存，自定义盘面优先。";
  var footer=new JPanel(new BorderLayout(8,0));
  footer.setOpaque(false);
  footer.add(label(hint,14,Font.PLAIN,REMINDER),BorderLayout.CENTER);
  panel.add(footer,BorderLayout.SOUTH);
  return new FramingPage(panel,()->new AlbumFraming(vinyl.value().get(),cd.faceFraming(),reel.faceFraming()));
 }

 private static FramingChoice faceChoice(FramingTarget target,VinylLabelFraming framing){
  return framing==null?null:new FramingChoice(target,framing);
 }

 private static Editor editor(AlbumKey album,FramingChoice initial,VinylLabelEditor.Sample sample,Style style,
     Supplier<CoverWhiteBorders.Bounds> crop,ButtonGroup modes){
  var panel=new JPanel(new BorderLayout(0,8));
  panel.setOpaque(false);
  var heading=new JPanel();
  heading.setLayout(new BoxLayout(heading,BoxLayout.Y_AXIS));
  heading.setOpaque(false);
  panel.add(heading,BorderLayout.NORTH);

  boolean vinyl=style.vinyl();
  var targets=new ButtonGroup();
  Supplier<Style> previewStyle=()->{
   if(!vinyl||targets.getSelection()==null)return style;
   var target=FramingTarget.valueOf(targets.getSelection().getActionCommand());
   return target==FramingTarget.BOTH_VINYL?style:
       target==FramingTarget.BLACK_VINYL?Style.BLACK_VINYL:Style.COLORED_VINYL;
  };
  var follower=new VinylLabelEditor(sample,!vinyl,previewStyle);
  var round=follower.followingPreview();
  Runnable update=()->{follower.follow(crop.get());round.repaint();};
  onSelection(modes,update);
  update.run();
  if(album==null){
   panel.add(round,BorderLayout.CENTER);
   return new Editor(panel,()->initial);
  }

  var controls=optionRow();
  controls.add(label(FRAMING_METHOD,14,Font.BOLD,TEXT));
  var overrides=new ButtonGroup();
  var follow=option(controls,overrides,"跟随大封面","follow",initial==null);
  var independent=option(controls,overrides,"单独调整","independent",initial!=null);
  heading.add(controls);
  var scopeLayout=new CardLayout();
  var scope=new JPanel(scopeLayout);
  scope.setOpaque(false);
  scope.setAlignmentX(Component.LEFT_ALIGNMENT);
  if(vinyl){
   var scopeRow=optionRow();
   scopeRow.add(label("应用到",14,Font.BOLD,TEXT));
   var selected=initial==null?FramingTarget.BOTH_VINYL:initial.target();
   for(var target:new FramingTarget[]{FramingTarget.BLACK_VINYL,FramingTarget.COLORED_VINYL,FramingTarget.BOTH_VINYL})
    option(scopeRow,targets,target.label,target.name(),target==selected);
   scope.add(scopeRow,"independent");
   var emptyScope=new JPanel();
   emptyScope.setOpaque(false);
   scope.add(emptyScope,"follow");
   controls.add(scope);
  }

  var cards=new CardLayout();
  var body=new JPanel(cards);
  body.setOpaque(false);
  body.add(round,"follow");
  var manual=new VinylLabelEditor(sample,!vinyl,previewStyle);
  var editor=manual.content(initial==null?VinylLabelFraming.DEFAULT:initial.framing());
  body.add(editor,"independent");
  onSelection(targets,()->{round.repaint();editor.repaint();});
  boolean[] seeded={initial!=null};
  Runnable showControls=()->{
   if(independent.isSelected()&&!seeded[0]){
    var image=sample.image();
    var initialCrop=VinylLabelFraming.Crop.fromPixels(crop.get(),image.getWidth(),image.getHeight());
    manual.load(new VinylLabelFraming(initialCrop,false,VinylLabelFraming.MIN_ZOOM,0,0));
    seeded[0]=true;
   }
   String choice=overrides.getSelection().getActionCommand();
   if(vinyl)scopeLayout.show(scope,choice);
   cards.show(body,choice);
  };
  onSelection(overrides,showControls);
  showControls.run();
  panel.add(body,BorderLayout.CENTER);

  return new Editor(panel,()->{
   if(follow.isSelected())return null;
   var target=vinyl?FramingTarget.valueOf(targets.getSelection().getActionCommand()):
       style==Style.CD?FramingTarget.CD:FramingTarget.REEL_COVER;
   return new FramingChoice(target,manual.value());
  });
 }

 private static JPanel optionRow(){
  var row=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
  row.setAlignmentX(Component.LEFT_ALIGNMENT);
  row.setOpaque(false);
  return row;
 }

 private static JRadioButton option(JPanel row,ButtonGroup group,String text,String command,boolean selected){
  var button=FramingControls.radio(text,selected);
  button.setActionCommand(command);
  group.add(button);
  row.add(button);
  return button;
 }

 private static void onSelection(ButtonGroup group,Runnable update){
  for(var option:Collections.list(group.getElements()))
   option.addItemListener(event->{if(event.getStateChange()==ItemEvent.SELECTED)update.run();});
 }

 private static CoverWhiteBorders.Mode selected(ButtonGroup modes){
  return CoverWhiteBorders.Mode.valueOf(modes.getSelection().getActionCommand());
 }

 private static JLabel placeholder(String message){
  var placeholder=label(message,14,Font.PLAIN,MUTED);
  placeholder.setHorizontalAlignment(SwingConstants.CENTER);
  placeholder.setPreferredSize(new Dimension(570,280));
  return placeholder;
 }

 private static final class LargePreview extends JComponent {
  private final VinylLabelEditor.Sample sample;
  private CoverWhiteBorders.Bounds crop;
  LargePreview(VinylLabelEditor.Sample sample){
   this.sample=sample;
   setPreferredSize(new Dimension(570,268));
  }
  void setCrop(CoverWhiteBorders.Bounds crop){this.crop=crop;repaint();}
  @Override protected void paintComponent(Graphics graphics){
   var g=(Graphics2D)graphics.create();
   try{
    g.setColor(PREVIEW_BACKGROUND);
    g.fillRect(0,0,getWidth(),getHeight());
    if(crop==null)return;
    var image=sample.image();
    float aspect=crop.trimmedFrom(image.getWidth(),image.getHeight())?crop.displayAspect():(float)crop.width()/crop.height();
    int h=Math.max(1,Math.round(Math.min(getHeight()-24,(getWidth()-24)/aspect)));
    int w=Math.max(1,Math.round(h*aspect));
    int x=(getWidth()-w)/2,y=(getHeight()-h)/2;
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(image,x,y,x+w,y+h,crop.x(),crop.y(),crop.x()+crop.width(),crop.y()+crop.height(),null);
   }finally{g.dispose();}
  }
 }

 @Override public void close(){
  if(active==this)active=null;
  SwingUtilities.invokeLater(()->{if(workspace!=null)workspace.requestClose();});
 }
}
