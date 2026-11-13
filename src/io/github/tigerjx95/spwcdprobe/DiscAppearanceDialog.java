package io.github.tigerjx95.spwcdprobe;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.function.Predicate;
import javax.swing.*;
import static io.github.tigerjx95.spwcdprobe.DiscOrderDialog.*;

public final class DiscAppearanceDialog implements AutoCloseable {
 private static volatile DiscAppearanceDialog active;
 private final DiscSettings settings;
 private JDialog window;

 DiscAppearanceDialog(DiscSettings settings){
  this.settings=settings;
  active=this;
 }

 public static void open(){
  var editor=active;
  if(editor!=null)SwingUtilities.invokeLater(()->{
   if(active==editor&&!GraphicsEnvironment.isHeadless())editor.show();
  });
 }

 private void show(){
  if(window!=null){window.toFront();return;}
  var owner=KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
  var dialog=new JDialog(owner,"默认盘面样式",Dialog.ModalityType.MODELESS);
  window=dialog;
  dialog.setUndecorated(true);
  dialog.getAccessibleContext().setAccessibleName("默认盘面样式");
  dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
  dialog.addWindowListener(new WindowAdapter(){
   @Override public void windowClosed(WindowEvent event){if(window==dialog)window=null;}
  });
  dialog.setContentPane(content(settings.appearance(),settings::saveAppearance,dialog::dispose));
  dialog.pack();
  dialog.setLocationRelativeTo(owner);
  dialog.setVisible(true);
 }

 static JComponent content(DiscSettings.Appearance initial,Predicate<DiscSettings.Appearance> save,Runnable close){
  var panel=new JPanel(new BorderLayout(0,14));
  panel.setBackground(new Color(0x28343d));
  panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x47535c)),
      BorderFactory.createEmptyBorder(20,24,16,24)));
  panel.add(label("默认盘面样式",22,Font.BOLD,TEXT),BorderLayout.NORTH);

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
  colorSection.getAccessibleContext().setAccessibleName("彩胶颜色");
  Runnable refresh=()->{
   var style=DiscSettings.Style.valueOf(styles.getSelection().getActionCommand());
   boolean enabled=style==DiscSettings.Style.COLORED_VINYL;
   for(var button:colorButtons)button.setEnabled(enabled);
   status.setText("当前选择："+DiscSettings.VinylColor.valueOf(colors.getSelection().getActionCommand()).label);
   if(colorSection.isVisible()!=enabled){
    colorSection.setVisible(enabled);
    var dialog=SwingUtilities.getWindowAncestor(panel);
    if(dialog!=null)dialog.pack();
   }
  };
  var styleRow=new JPanel(new GridLayout(1,3,8,0));
  styleRow.setOpaque(false);
  for(var style:DiscSettings.Style.values()){
   var button=option(style.label,style.name(),styles,style==initial.style(),null);
   var size=button.getPreferredSize();
   var insets=button.getInsets();
   int textWidth=button.getFontMetrics(button.getFont()).stringWidth(style.label);
   button.setPreferredSize(new Dimension(Math.max(136,Math.max(size.width,textWidth+insets.left+insets.right+12)),size.height));
   button.addActionListener(event->refresh.run());
   styleRow.add(button);
  }
  fields.add(styleRow);
  var palette=new JPanel(new GridLayout(2,3,8,8));
  palette.setOpaque(false);
  for(var color:DiscSettings.VinylColor.values()){
   if(color==DiscSettings.VinylColor.BLACK)continue;
   var button=option(color.label,color.name(),colors,color==initial.color(),new Color(color.shade(85)));
   button.addActionListener(event->refresh.run());
   colorButtons.add(button);
   palette.add(button);
  }
  colorSection.add(palette,BorderLayout.CENTER);
  colorSection.add(status,BorderLayout.SOUTH);
  fields.add(colorSection);
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
   if(save.test(new DiscSettings.Appearance(style,color)))close.run();
   else JOptionPane.showMessageDialog(panel,"无法保存默认盘面样式。","保存失败",JOptionPane.ERROR_MESSAGE);
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
  group.add(button);
  button.setSelected(selected);
  return button;
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
