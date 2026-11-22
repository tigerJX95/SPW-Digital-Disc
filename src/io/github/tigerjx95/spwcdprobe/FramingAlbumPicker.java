package io.github.tigerjx95.spwcdprobe;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import static io.github.tigerjx95.spwcdprobe.DiscOrderDialog.*;
import static io.github.tigerjx95.spwcdprobe.DiscSettings.AlbumKey;
import static io.github.tigerjx95.spwcdprobe.FramingAlbumLibrary.Entry;

final class FramingAlbumPicker extends JPanel {
 private final DefaultListModel<Entry> results=new DefaultListModel<>();
 private final JList<Entry> list=new JList<>(results);
 private final JTextField search=new JTextField();
 private final JLabel status=label("正在读取曲库…",14,Font.PLAIN,MUTED);
 private final LinkedHashMap<AlbumKey,Icon> thumbnails=new LinkedHashMap<>(16,.75f,true);
 private final Set<AlbumKey> missing=new HashSet<>();
 private final JScrollPane scroll=new JScrollPane(list);
 private List<Entry> catalog=List.of();
 private Entry editing;
 private boolean reading,loaded,thumbnailPending,closed;
 private final javax.swing.Timer filterDelay=new javax.swing.Timer(120,event->filter());

 FramingAlbumPicker(Entry current,Consumer<Entry> edit){
  super(new BorderLayout(0,8));
  editing=current;
  setBackground(new Color(0x25313a));
  setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
  setPreferredSize(new Dimension(340,300));
  var heading=new JPanel(new GridLayout(0,1,0,6));
  heading.setOpaque(false);
  heading.add(label("选择专辑",17,Font.BOLD,TEXT));
  search.setToolTipText("搜索专辑名或艺人");
  search.getAccessibleContext().setAccessibleName("搜索专辑名或艺人");
  search.setFont(UI_FONT.deriveFont(14f));
  search.setBackground(new Color(0x18232c));
  search.setForeground(TEXT);
  search.setCaretColor(TEXT);
  search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x47535c)),
      BorderFactory.createEmptyBorder(6,8,6,8)));
  heading.add(search);
  heading.add(status);
  add(heading,BorderLayout.NORTH);

  list.setFixedCellHeight(58);
  list.setBackground(new Color(0x1d2831));
  list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  list.setCellRenderer(new AlbumRow());
  scroll.setBorder(BorderFactory.createEmptyBorder());
  scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
  add(scroll,BorderLayout.CENTER);
  scroll.getViewport().addChangeListener(event->loadThumbnail());
  list.addMouseListener(new MouseAdapter(){
   @Override public void mouseClicked(MouseEvent event){
    if(!SwingUtilities.isLeftMouseButton(event)||event.getClickCount()!=1)return;
    int index=list.locationToIndex(event.getPoint());
    if(index<0||!list.getCellBounds(index,index).contains(event.getPoint()))return;
    edit.accept(results.get(index));
   }
  });
  list.registerKeyboardAction(event->{if(list.getSelectedValue()!=null)edit.accept(list.getSelectedValue());},
      KeyStroke.getKeyStroke("ENTER"),WHEN_FOCUSED);
  filterDelay.setRepeats(false);
  search.getDocument().addDocumentListener(new DocumentListener(){
   public void insertUpdate(DocumentEvent event){scheduleFilter();}
   public void removeUpdate(DocumentEvent event){scheduleFilter();}
   public void changedUpdate(DocumentEvent event){scheduleFilter();}
  });
  search.addActionListener(event->filter());
 }

 void setEditingAlbum(Entry entry){
  editing=entry;
  list.repaint();
 }

 void open(Supplier<CompletionStage<List<Entry>>> loadAlbums){
  SwingUtilities.invokeLater(this::loadThumbnail);
  if(reading||loaded||closed)return;
  reading=true;
  status.setText("正在读取曲库…");
  try{
   loadAlbums.get().whenComplete((albums,error)->SwingUtilities.invokeLater(()->{
    if(closed)return;
    reading=false;
    if(error!=null){status.setText("暂时无法读取曲库");return;}
    var entries=new ArrayList<>(albums);
    int index=0;
    while(index<entries.size()&&!isEditing(entries.get(index)))index++;
    if(index<entries.size())entries.addFirst(entries.remove(index));
    else if(hasEditingAlbum())entries.addFirst(editing);
    catalog=List.copyOf(entries);
    loaded=true;
    filter();
   }));
  }catch(RuntimeException|LinkageError unavailable){
   reading=false;
   status.setText("暂时无法读取曲库");
  }
 }

 private void scheduleFilter(){
  if(!closed)filterDelay.restart();
 }

 private void filter(){
  if(!loaded||closed)return;
  filterDelay.stop();
  String query=search.getText().strip().toLowerCase(Locale.ROOT);
  results.clear();
  results.addAll(catalog.stream().filter(entry->entry.searchText().contains(query)).toList());
  status.setText(results.isEmpty()?"没有匹配的专辑":"点击专辑编辑取景");
  loadThumbnail();
 }

 private static String artistAndYear(Entry entry){
  String details=entry.artist();
  if(entry.key().year()>0)details+=(details.isEmpty()?"":" · ")+entry.key().year();
  return details;
 }

 private boolean hasEditingAlbum(){
  return editing!=null&&editing.key().album()!=null;
 }

 private boolean isEditing(Entry entry){
  return editing!=null&&editing.key().equals(entry.key());
 }

 private void loadThumbnail(){
  if(thumbnailPending||closed||!isShowing())return;
  int first=Math.max(0,list.getFirstVisibleIndex());
  int last=Math.min(results.size()-1,list.getLastVisibleIndex());
  for(int i=first;i<=last;i++){
   Entry entry=results.get(i);
   if(entry.trackIds().isEmpty()||thumbnails.containsKey(entry.key())||missing.contains(entry.key()))continue;
   thumbnailPending=true;
   try{
    FramingAlbumLibrary.cover(entry,42).whenComplete((image,error)->SwingUtilities.invokeLater(()->{
     if(closed)return;
     if(error!=null||image==null)missing.add(entry.key());
     else{
      thumbnails.put(entry.key(),new ImageIcon(image));
      if(thumbnails.size()>96)thumbnails.pollFirstEntry();
     }
     thumbnailPending=false;
     list.repaint();
     loadThumbnail();
    }));
   }catch(RuntimeException|LinkageError unavailable){
    missing.add(entry.key());
    thumbnailPending=false;
    continue;
   }
   break;
  }
 }

 void finishSearchInput(){
  if(!search.isShowing())return;
  var input=search.getInputContext();
  if(input!=null)input.endComposition();
  if(search.isFocusOwner())search.transferFocus();
 }

 void close(){
  closed=true;
  filterDelay.stop();
  thumbnails.clear();
 }

 private final class AlbumRow extends JPanel implements ListCellRenderer<Entry>{
  private final JLabel image=new JLabel("♪",SwingConstants.CENTER);
  private final JLabel title=label("",14,Font.BOLD,TEXT);
  private final JLabel artist=label("",13,Font.PLAIN,MUTED);

  AlbumRow(){
   super(new BorderLayout(7,0));
   setBorder(BorderFactory.createEmptyBorder(3,6,3,6));
   image.setPreferredSize(new Dimension(42,42));
   image.setForeground(MUTED);
   title.putClientProperty("html.disable",true);
   artist.putClientProperty("html.disable",true);
   add(image,BorderLayout.WEST);
   var labels=new JPanel(new GridLayout(2,1));
   labels.setOpaque(false);
   labels.add(title);
   labels.add(artist);
   add(labels,BorderLayout.CENTER);
  }

  @Override public Component getListCellRendererComponent(JList<? extends Entry> source,Entry entry,int index,boolean selected,boolean focus){
   setBackground(isEditing(entry)?new Color(0x315363):selected?new Color(0x35434e):new Color(0x1d2831));
   Icon thumbnail=thumbnails.get(entry.key());
   image.setIcon(thumbnail);
   image.setText(thumbnail==null?"♪":"");
   title.setText(entry.key().album());
   artist.setText(artistAndYear(entry));
   return this;
  }
 }
}
