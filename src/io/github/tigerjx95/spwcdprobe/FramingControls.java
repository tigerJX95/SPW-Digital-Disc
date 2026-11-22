package io.github.tigerjx95.spwcdprobe;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import javax.swing.*;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

import static io.github.tigerjx95.spwcdprobe.DiscOrderDialog.*;

final class FramingControls {
 static final Color PANEL=new Color(0x2d3942);
 private static final Color CONTROL=new Color(0x36434d);
 private static final Color BORDER=new Color(0x4b5963);
 private static final Color SELECTED=new Color(0x355462);
 private static final Color TRACK=new Color(0x4f5d67);
 private static final Color DIMMED=new Color(0x778791);

 private FramingControls(){}

 static JRadioButton radio(String text,boolean selected){
  var button=new JRadioButton(text,selected);
  button.setUI(new BasicRadioButtonUI(){
   @Override protected void paintFocus(Graphics graphics,Rectangle text,Dimension size){
    graphics.setColor(MUTED);
    graphics.drawLine(text.x,text.y+text.height,text.x+text.width-1,text.y+text.height);
   }
  });
  button.setOpaque(false);
  button.setContentAreaFilled(false);
  button.setFont(UI_FONT.deriveFont(14f));
  button.setForeground(TEXT);
  button.setBorder(BorderFactory.createEmptyBorder(5,6,5,8));
  button.setIconTextGap(7);
  button.setIcon(new RadioMark());
  button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  return button;
 }

 static JSlider slider(int min,int max){
  var slider=new JSlider(min,max);
  slider.setUI(new SliderUI(slider));
  slider.setOpaque(false);
  slider.setPreferredSize(new Dimension(178,26));
  slider.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  return slider;
 }

 static JTabbedPane tabs(){
  var tabs=new JTabbedPane();
  tabs.setUI(new TabsUI());
  tabs.setFont(UI_FONT.deriveFont(Font.BOLD,15f));
  tabs.setForeground(TEXT);
  tabs.setBackground(PANEL);
  tabs.setOpaque(false);
  return tabs;
 }

 private static Graphics2D antialias(Graphics graphics){
  var g=(Graphics2D)graphics.create();
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
  return g;
 }

 private static final class RadioMark implements Icon {
  @Override public int getIconWidth(){return 18;}
  @Override public int getIconHeight(){return 18;}
  @Override public void paintIcon(Component component,Graphics graphics,int x,int y){
   var button=(JRadioButton)component;
   var g=antialias(graphics);
   try{
    g.setColor(!button.isEnabled()?DIMMED:button.isSelected()?BLUE:Color.WHITE);
    g.setStroke(new BasicStroke(4f));
    g.draw(new Ellipse2D.Float(x+3.5f,y+3.5f,11,11));
   }finally{g.dispose();}
  }
 }

 private static final class SliderUI extends BasicSliderUI {
  private static final int TRACK_HEIGHT=16;
  private static final int THUMB_SIZE=18;

  SliderUI(JSlider slider){super(slider);}
  @Override protected Dimension getThumbSize(){return new Dimension(THUMB_SIZE,THUMB_SIZE);}

  @Override public void setThumbLocation(int x,int y){
   // Redraw the whole track so partial, scaled repaint clips cannot leave old thumb edges behind.
   thumbRect.setLocation(x,y);
   slider.repaint();
  }

  @Override public void paintTrack(Graphics graphics){
   var g=antialias(graphics);
   try{
    int x=trackRect.x-TRACK_HEIGHT/2;
    int y=trackRect.y+(trackRect.height-TRACK_HEIGHT)/2;
    int width=Math.max(0,trackRect.width-1)+TRACK_HEIGHT;
    int thumb=Math.clamp(thumbRect.x+thumbRect.width/2,x,x+width);
    g.setColor(TRACK);
    g.fillRoundRect(x,y,width,TRACK_HEIGHT,TRACK_HEIGHT,TRACK_HEIGHT);
    if(drawInverted())g.clipRect(thumb,y,x+width-thumb,TRACK_HEIGHT);
    else g.clipRect(x,y,thumb-x,TRACK_HEIGHT);
    g.setColor(slider.isEnabled()?BLUE:DIMMED);
    g.fillRoundRect(x,y,width,TRACK_HEIGHT,TRACK_HEIGHT,TRACK_HEIGHT);
   }finally{g.dispose();}
  }
  @Override public void paintThumb(Graphics graphics){
   var g=antialias(graphics);
   try{
    g.setColor(slider.isEnabled()?Color.WHITE:DIMMED);
    g.fillOval(thumbRect.x,thumbRect.y,THUMB_SIZE,THUMB_SIZE);
    if(slider.hasFocus()&&!slider.getValueIsAdjusting()){
     g.setColor(TRACK);
     g.drawOval(thumbRect.x+2,thumbRect.y+2,THUMB_SIZE-5,THUMB_SIZE-5);
    }
   }finally{g.dispose();}
  }
  @Override public void paintFocus(Graphics graphics){/* Focus is drawn inside the thumb. */}
 }

 private static final class TabsUI extends BasicTabbedPaneUI {
  @Override protected void installDefaults(){
   super.installDefaults();
   tabInsets=new Insets(7,16,7,16);
   selectedTabPadInsets=new Insets(0,0,0,0);
   tabAreaInsets=new Insets(0,0,7,0);
   contentBorderInsets=new Insets(0,0,0,0);
  }

  @Override protected void paintTabBackground(Graphics graphics,int placement,int index,
      int x,int y,int width,int height,boolean selected){
   var g=antialias(graphics);
   try{
    g.setColor(selected?SELECTED:CONTROL);
    g.fillRoundRect(x,y,width-4,height-1,12,12);
   }finally{g.dispose();}
  }

  @Override protected void paintTabBorder(Graphics graphics,int placement,int index,
      int x,int y,int width,int height,boolean selected){
   var g=antialias(graphics);
   try{
    g.setColor(selected?BLUE:BORDER);
    g.drawRoundRect(x,y,width-4,height-1,12,12);
   }finally{g.dispose();}
  }

  @Override protected void paintFocusIndicator(Graphics graphics,int placement,Rectangle[] rectangles,
      int index,Rectangle icon,Rectangle text,boolean selected){
   if(!selected||!tabPane.hasFocus())return;
   var bounds=rectangles[index];
   var g=antialias(graphics);
   try{
    g.setColor(TEXT);
    g.drawRoundRect(bounds.x+3,bounds.y+3,bounds.width-10,bounds.height-7,9,9);
   }finally{g.dispose();}
  }

  @Override protected void paintContentBorder(Graphics graphics,int placement,int index){}
 }
}
