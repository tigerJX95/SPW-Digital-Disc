package io.github.tigerjx95.spwcdprobe;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.event.MouseInputAdapter;

/** Opens the four-item ordering editor from the host's preference button. */
public final class DiscOrderDialog implements AutoCloseable {
 private static final Color TEXT=new Color(0xeaf0f4);
 private static final Color MUTED=new Color(0xb9c5ce);
 private static final Color BLUE=new Color(0x49bdeb);
 private static final Font UI_FONT=chooseFont();
 private static volatile DiscOrderDialog active;
 private final DiscSettings settings;
 private final Supplier<String> artworkSource;
 private JDialog window;

 DiscOrderDialog(DiscSettings settings,Supplier<String> artworkSource){
  this.settings=settings;
  this.artworkSource=artworkSource;
  active=this;
 }

 // Called by the host preference schema through the plugin class loader.
 public static void open(){
  DiscOrderDialog editor=active;
  if(editor!=null)SwingUtilities.invokeLater(()->{
   if(active==editor&&!GraphicsEnvironment.isHeadless())editor.show();
  });
 }

 private void show(){
  if(window!=null){window.toFront();return;}
  var owner=KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
  var dialog=new JDialog(owner,"自定义盘面查找顺序",Dialog.ModalityType.MODELESS);
  window=dialog;
  dialog.setUndecorated(true);
  dialog.getAccessibleContext().setAccessibleName("自定义盘面查找顺序");
  dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
  dialog.addWindowListener(new WindowAdapter(){
   @Override public void windowClosed(WindowEvent event){if(window==dialog)window=null;}
  });

  dialog.setContentPane(content(settings.order(),settings::saveOrder,dialog::dispose,artworkSource));
  dialog.pack();
  dialog.getRootPane().setWindowDecorationStyle(JRootPane.NONE);
  dialog.setLocationRelativeTo(owner);
  var screen=dialog.getGraphicsConfiguration();
  var bounds=screen.getBounds();
  var insets=Toolkit.getDefaultToolkit().getScreenInsets(screen);
  int left=bounds.x+insets.left+12;
  int right=bounds.x+bounds.width-insets.right-12;
  int width=Math.min(dialog.getWidth(),Math.max(1,right-left));
  dialog.setBounds(Math.clamp(dialog.getX(),left,right-width),dialog.getY(),width,dialog.getHeight());
  dialog.setVisible(true);
 }

 static JComponent content(List<String> order,Predicate<List<String>> saveOrder,Runnable close,Supplier<String> artworkSource){
  var model=new DefaultListModel<String>();
  model.addAll(order);
  var list=new JList<>(model);
  list.setOpaque(false);
  list.setCellRenderer(new OrderRow());
  list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  list.setVisibleRowCount(LocalDiscService.DEFAULT_NAMES.size());
  list.setFixedCellHeight(64);
  list.setFixedCellWidth(480);
  list.getAccessibleContext().setAccessibleName("盘面文件优先级");
  var drag=new MouseInputAdapter(){
   private int row=-1;
   @Override public void mousePressed(MouseEvent event){
    row=-1;
    if(!SwingUtilities.isLeftMouseButton(event))return;
    int index=list.locationToIndex(event.getPoint());
    if(index>=0&&list.getCellBounds(index,index).contains(event.getPoint()))row=index;
   }
   @Override public void mouseDragged(MouseEvent event){
    if(row<0)return;
    int target=list.locationToIndex(event.getPoint());
    if(target>=0&&target!=row){
     move(list,model,row,target);
     row=target;
    }
   }
   @Override public void mouseReleased(MouseEvent event){row=-1;}
  };
  list.addMouseListener(drag);
  list.addMouseMotionListener(drag);

  list.getAccessibleContext().setAccessibleDescription("拖动文件名，或选中后按 Alt 加上下方向键排序。");
  list.registerKeyboardAction(event->move(list,model,list.getSelectedIndex(),list.getSelectedIndex()-1),
    KeyStroke.getKeyStroke("alt UP"),JComponent.WHEN_FOCUSED);
  list.registerKeyboardAction(event->move(list,model,list.getSelectedIndex(),list.getSelectedIndex()+1),
    KeyStroke.getKeyStroke("alt DOWN"),JComponent.WHEN_FOCUSED);
  var reset=new ActionButton("恢复默认",false);
  reset.addActionListener(event->{
   model.clear();
   model.addAll(LocalDiscService.DEFAULT_NAMES);
   list.clearSelection();
  });
  var cancel=new ActionButton("取消",false);
  cancel.addActionListener(event->close.run());
  var confirm=new ActionButton("确认",true);
  confirm.addActionListener(event->{
   if(saveOrder.test(Collections.list(model.elements())))close.run();
   else JOptionPane.showMessageDialog(list,"无法保存自定义盘面查找顺序。","保存失败",JOptionPane.ERROR_MESSAGE);
  });

  var heading=new JPanel(new BorderLayout(0,12));
  heading.setOpaque(false);
  heading.add(label("自定义盘面查找顺序",22,Font.BOLD,TEXT),BorderLayout.NORTH);
  var hint=new JPanel(new BorderLayout(12,0));
  hint.setOpaque(false);
  hint.setBorder(BorderFactory.createEmptyBorder(0,0,0,4));
  hint.add(label("拖动以重新排序",16,Font.BOLD,TEXT),BorderLayout.CENTER);
  hint.add(reset,BorderLayout.EAST);
  heading.add(hint,BorderLayout.SOUTH);

  var actions=new JPanel(new GridLayout(1,2,14,0));
  actions.setOpaque(false);
  actions.setBorder(BorderFactory.createEmptyBorder(12,0,0,0));
  actions.add(cancel);
  actions.add(confirm);

  var content=new JPanel(new BorderLayout(0,10)){
   @Override protected void paintComponent(Graphics graphics){
    var g=(Graphics2D)graphics.create();
    g.setPaint(new GradientPaint(0,0,new Color(0x252d34),0,getHeight(),new Color(0x2c414d)));
    g.fillRect(0,0,getWidth(),getHeight());
    g.setColor(new Color(0x47535c));
    g.drawRect(0,0,getWidth()-1,getHeight()-1);
    g.dispose();
   }
  };
  content.setBorder(BorderFactory.createEmptyBorder(16,24,14,24));
  content.add(heading,BorderLayout.NORTH);
  content.add(list,BorderLayout.CENTER);
  var status=label("当前曲目盘面："+artworkSource.get(),14,Font.PLAIN,MUTED);
  var footer=new JPanel(new BorderLayout());
  footer.setOpaque(false);
  footer.add(status,BorderLayout.NORTH);
  footer.add(actions,BorderLayout.SOUTH);
  content.add(footer,BorderLayout.SOUTH);
  var refresh=new Timer(500,event->status.setText("当前曲目盘面："+artworkSource.get()));
  content.addHierarchyListener(event->{
   if((event.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)==0)return;
   if(content.isShowing())refresh.start();else refresh.stop();
  });
  content.registerKeyboardAction(event->close.run(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
  content.addHierarchyListener(event->{
   JRootPane root=SwingUtilities.getRootPane(content);
   if(root!=null)root.setDefaultButton(confirm);
  });
  return content;
 }

 private static Font chooseFont(){
  for(String family:List.of("Microsoft YaHei UI","Microsoft YaHei","Noto Sans CJK SC","PingFang SC")){
   var font=new Font(family,Font.PLAIN,16);
   if(font.getFamily(java.util.Locale.ROOT).equalsIgnoreCase(family)&&font.canDisplayUpTo("自定义盘面查找顺序")==-1)return font;
  }
  return new Font(Font.SANS_SERIF,Font.PLAIN,16);
 }

 private static JLabel label(String text,int size,int style,Color color){
  var label=new JLabel(text);
  label.setFont(UI_FONT.deriveFont(style,(float)size));
  label.setForeground(color);
  return label;
 }

 private static final class OrderRow extends JPanel implements ListCellRenderer<String> {
  private final Font nameFont=UI_FONT.deriveFont(Font.BOLD,17f);
  private final Font rankFont=UI_FONT.deriveFont(Font.PLAIN,15f);
  private String name,rank;
  private boolean selected;

  @Override public Component getListCellRendererComponent(JList<? extends String> list,String value,int index,boolean selected,boolean focused){
   name=value;
   rank=Integer.toString(index+1);
   this.selected=selected;
   getAccessibleContext().setAccessibleName(value);
   return this;
  }

  @Override protected void paintComponent(Graphics graphics){
   var g=(Graphics2D)graphics.create();
   g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
   int height=getHeight()-12;
   g.setColor(selected?new Color(0x405563):new Color(255,255,255,14));
   g.fillRoundRect(0,0,getWidth()-1,height-1,14,14);
   g.setColor(selected?new Color(0x638497):new Color(255,255,255,32));
   g.drawRoundRect(0,0,getWidth()-1,height-1,14,14);
   g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
   g.setColor(TEXT);
   g.setFont(nameFont);
   var metrics=g.getFontMetrics();
   g.drawString(name,60,(height+metrics.getAscent()-metrics.getDescent())/2);
   g.setColor(MUTED);
   g.setFont(rankFont);
   metrics=g.getFontMetrics();
   g.drawString(rank,getWidth()-18-metrics.stringWidth(rank),(height+metrics.getAscent()-metrics.getDescent())/2);
   g.setColor(TEXT);
   for(int y=height/2-9;y<=height/2+7;y+=8){
    g.fillOval(25,y,3,3);
    g.fillOval(34,y,3,3);
   }
   g.dispose();
  }
 }

 private static final class ActionButton extends JButton {
  private final boolean primary;

  ActionButton(String text,boolean primary){
   super(text);
   this.primary=primary;
   setForeground(primary?new Color(0x142f40):TEXT);
   setFont(UI_FONT.deriveFont(Font.BOLD,text.equals("恢复默认")?14f:16f));
   setBorder(BorderFactory.createEmptyBorder(9,14,9,14));
   setContentAreaFilled(false);
   setOpaque(false);
   setFocusPainted(false);
   setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  @Override protected void paintComponent(Graphics graphics){
   var g=(Graphics2D)graphics.create();
   g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
   boolean hover=getModel().isRollover();
   g.setColor(primary?(hover?BLUE.brighter():BLUE):new Color(255,255,255,hover?30:16));
   g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
   g.setColor(hasFocus()?TEXT:new Color(255,255,255,32));
   g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
   g.dispose();
   super.paintComponent(graphics);
  }
 }

 private static void move(JList<String> list,DefaultListModel<String> model,int from,int to){
  if(from<0||from>=model.size()||to<0||to>=model.size()||from==to)return;
  model.add(to,model.remove(from));
  list.setSelectedIndex(to);
 }

 @Override public void close(){
  if(active==this)active=null;
  SwingUtilities.invokeLater(()->{if(window!=null)window.dispose();});
 }
}
