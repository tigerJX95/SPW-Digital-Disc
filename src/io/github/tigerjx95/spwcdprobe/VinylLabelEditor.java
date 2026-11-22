package io.github.tigerjx95.spwcdprobe;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.event.MouseInputAdapter;

import static io.github.tigerjx95.spwcdprobe.DiscOrderDialog.*;

final class VinylLabelEditor {
 record Sample(BufferedImage image,int tint){}

 private static final Color PREVIEW_BACKGROUND=new Color(0x18232c);
 private static final int PREVIEW_SIZE=268;
 private static final String[] EDGES={"左侧","上方","右侧","下方"};
 private final Sample sample;
 private final boolean fullFace;
 private final Supplier<DiscSettings.Style> previewStyle;
 private final JSlider[] margins=new JSlider[4];
 private final JLabel[] marginLabels=new JLabel[4];
 private final JSlider zoom=FramingControls.slider(VinylLabelFraming.MIN_ZOOM,VinylLabelFraming.MAX_ZOOM);
 private final JLabel zoomLabel=label("",14,Font.PLAIN,TEXT);
 private final JLabel status=label("",14,Font.PLAIN,MUTED);
 private final JRadioButton fit=FramingControls.radio("保留构图",false);
 private final JRadioButton fill;
 private final Preview artwork;
 private final Preview round;
 private float panX,panY;
 private boolean loading;
 private CoverWhiteBorders.Bounds followBounds;

 VinylLabelEditor(Sample sample,boolean fullFace,Supplier<DiscSettings.Style> previewStyle){
  this.sample=sample;
  this.fullFace=fullFace;
  this.previewStyle=previewStyle;
  fill=FramingControls.radio(fullFace?"铺满盘面":"铺满圆形",false);
  artwork=new Preview(false,false);
  round=new Preview(true,true);
 }

 JComponent followingPreview(){return new Preview(true,false);}

 void follow(CoverWhiteBorders.Bounds bounds){followBounds=bounds;}

 JComponent content(VinylLabelFraming initial){
  var panel=new JPanel(new BorderLayout());
  panel.setOpaque(false);

  var left=column("封面裁边",artwork);
  for(int i=0;i<margins.length;i++){
   final int edge=i;
   margins[i]=FramingControls.slider(0,VinylLabelFraming.Crop.MAX_EDGE);
   margins[i].getAccessibleContext().setAccessibleName("裁切"+EDGES[i]);
   marginLabels[i]=label(EDGES[i],14,Font.PLAIN,TEXT);
   marginLabels[i].setLabelFor(margins[i]);
   margins[i].addChangeListener(event->{
    if(loading)return;
    int limit=VinylLabelFraming.Crop.MAX_EDGE-margins[(edge+2)%4].getValue();
    if(margins[edge].getValue()>limit)margins[edge].setValue(limit);
    status.setText(" ");
    refresh();
   });
   left.add(row(marginLabels[i],margins[i]));
  }
  var white=new ActionButton("去成对白边",false);
  white.addActionListener(event->trimWhite());
  left.add(status);

  var right=column(fullFace?"整盘预览":"圆形预览",round);
  var modes=new JPanel(new GridLayout(1,2,8,0));
  modes.setOpaque(false);
  var modeGroup=new ButtonGroup();
  for(var button:new JRadioButton[]{fill,fit}){
   modeGroup.add(button);
   button.addItemListener(event->{if(!loading&&button.isSelected())refresh();});
   modes.add(button);
  }
  right.add(modes);
  zoomLabel.setLabelFor(zoom);
  zoom.getAccessibleContext().setAccessibleName(fullFace?"盘面缩放":"标签缩放");
  zoom.addChangeListener(event->{if(!loading)refresh();});
  right.add(row(zoomLabel,zoom));
  var hint=label("在圆内拖动定位，滚轮缩放。",14,Font.PLAIN,MUTED);
  right.add(hint);
  for(var column:new JPanel[]{left,right})
   for(var child:column.getComponents())if(child instanceof JComponent item)item.setAlignmentX(Component.LEFT_ALIGNMENT);
  var columns=new JPanel(new GridLayout(1,2,18,0));
  columns.setOpaque(false);
  var reset=new ActionButton("重置取景",false);
  reset.addActionListener(event->load(VinylLabelFraming.DEFAULT));
  columns.add(withColumnAction(left,white));
  columns.add(withColumnAction(right,reset));
  panel.add(columns,BorderLayout.CENTER);
  load(initial);
  return panel;
 }

 private static JPanel withColumnAction(JPanel controls,ActionButton action){
  var column=new JPanel(new BorderLayout(0,6));
  column.setBackground(new Color(0x25313a));
  column.setBorder(BorderFactory.createEmptyBorder(9,10,9,10));
  var row=new JPanel(new FlowLayout(FlowLayout.CENTER,0,0));
  row.setOpaque(false);
  row.add(action);
  column.add(controls,BorderLayout.CENTER);
  column.add(row,BorderLayout.SOUTH);
  return column;
 }

 private static JPanel column(String title,JComponent preview){
  var column=new JPanel();
  column.setOpaque(false);
  column.setLayout(new BoxLayout(column,BoxLayout.Y_AXIS));
  column.add(label(title,15,Font.BOLD,TEXT));
  column.add(Box.createVerticalStrut(7));
  preview.setAlignmentX(Component.LEFT_ALIGNMENT);
  column.add(preview);
  column.add(Box.createVerticalStrut(8));
  return column;
 }

 private static JPanel row(JLabel name,JSlider slider){
  var row=new JPanel(new BorderLayout(8,0));
  row.setOpaque(false);
  row.setAlignmentX(Component.LEFT_ALIGNMENT);
  name.setPreferredSize(new Dimension(104,22));
  row.add(name,BorderLayout.WEST);
  row.add(slider,BorderLayout.CENTER);
  return row;
 }

 void load(VinylLabelFraming value){
  setMargins(value.crop());
  loading=true;
  fit.setSelected(value.fit());
  fill.setSelected(!value.fit());
  zoom.setValue(value.zoom());
  panX=value.panX();
  panY=value.panY();
  loading=false;
  status.setText(" ");
  refresh();
 }

 private void setMargins(VinylLabelFraming.Crop crop){
  loading=true;
  int[] values={crop.left(),crop.top(),crop.right(),crop.bottom()};
  for(int i=0;i<margins.length;i++)margins[i].setValue(values[i]);
  loading=false;
 }

 VinylLabelFraming value(){
  var crop=new VinylLabelFraming.Crop(margins[0].getValue(),margins[1].getValue(),margins[2].getValue(),margins[3].getValue());
  // In fit mode the centered image remains fully inside the circle.
  if(fit.isSelected())return new VinylLabelFraming(crop,true,VinylLabelFraming.MIN_ZOOM,0,0);
  return new VinylLabelFraming(crop,false,zoom.getValue(),panX,panY);
 }

 private void trimWhite(){
  var crop=FramingAlbumLibrary.pairedCrop(sample.image());
  if(crop==null){
   status.setText("没有识别到成对的白边。");
   return;
  }
  setMargins(crop);
  status.setText("已裁去成对白边，可继续调整。");
  refresh();
 }

 private void refresh(){
  for(int i=0;i<margins.length;i++){
   int value=margins[i].getValue();
   marginLabels[i].setText(String.format(Locale.ROOT,"%s %.1f%%",EDGES[i],value/10.0));
  }
  zoom.setEnabled(!fit.isSelected());
  zoomLabel.setText("缩放  "+(fit.isSelected()?"适应圆形":zoom.getValue()+"%"));
  artwork.repaint();
  round.repaint();
 }

 private final class Preview extends JComponent {
  private final boolean circular;
  private Point last;

  Preview(boolean circular,boolean interactive){
   this.circular=circular;
   setPreferredSize(new Dimension(PREVIEW_SIZE,PREVIEW_SIZE));
   setMaximumSize(new Dimension(Integer.MAX_VALUE,PREVIEW_SIZE));
   setToolTipText(circular?(fullFace?"整盘封面预览":"唱片标签预览"):"原封面和裁切框");
   if(!interactive)return;
   var drag=new MouseInputAdapter(){
    @Override public void mousePressed(MouseEvent event){
     if(SwingUtilities.isLeftMouseButton(event)&&!fit.isSelected())last=event.getPoint();
    }
    @Override public void mouseReleased(MouseEvent event){last=null;}
    @Override public void mouseDragged(MouseEvent event){
     if(last==null||fit.isSelected())return;
     var source=sourceBounds();
     float size=diameter();
     var box=value().place((float)source.width()/source.height(),size);
     panX=pan(panX,event.getX()-last.x,VinylLabelFraming.panRoom(box.width(),size));
     panY=pan(panY,event.getY()-last.y,VinylLabelFraming.panRoom(box.height(),size));
     last=event.getPoint();
     repaint();
    }
   };
   addMouseListener(drag);
   addMouseMotionListener(drag);
   addMouseWheelListener(event->{if(zoom.isEnabled())zoom.setValue(zoom.getValue()-event.getWheelRotation()*5);});
   setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
  }

  private float pan(float current,int distance,float room){
   return room==0?0:Math.clamp(current+distance/room,-1,1);
  }

  private float diameter(){return Math.max(1,Math.min(getWidth(),getHeight())-34);}

  private CoverWhiteBorders.Bounds sourceBounds(){
   return followBounds==null?value().crop().pixels(sample.image().getWidth(),sample.image().getHeight()):followBounds;
  }

  @Override protected void paintComponent(Graphics graphics){
   var g=(Graphics2D)graphics.create();
   try{
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setColor(PREVIEW_BACKGROUND);
    g.fillRect(0,0,getWidth(),getHeight());
    if(circular)paintCircle(g);else paintCrop(g);
   }finally{g.dispose();}
  }

  private void paintCrop(Graphics2D g){
   var image=sample.image();
   int w=image.getWidth(),h=image.getHeight();
   float scale=Math.min((getWidth()-22f)/w,(getHeight()-22f)/h);
   int x=Math.round((getWidth()-w*scale)*.5f),y=Math.round((getHeight()-h*scale)*.5f);
   int drawW=Math.round(w*scale),drawH=Math.round(h*scale);
   g.drawImage(image,x,y,drawW,drawH,null);
   var crop=sourceBounds();
   var outline=new Rectangle2D.Float(x+crop.x()*scale,y+crop.y()*scale,crop.width()*scale,crop.height()*scale);
   var shade=new Area(new Rectangle2D.Float(x,y,drawW,drawH));
   shade.subtract(new Area(outline));
   g.setColor(new Color(0xa9000000,true));
   g.fill(shade);
   g.setColor(BLUE);
   g.draw(outline);
  }

  private void paintCircle(Graphics2D g){
   float size=diameter(),cx=getWidth()*.5f,cy=getHeight()*.5f;
   float r=size*.5f;
   var style=previewStyle.get();
   boolean reel=style==DiscSettings.Style.REEL_COVER;
   var bounds=sourceBounds();
   var image=sample.image();
   boolean trimmed=bounds.trimmedFrom(image.getWidth(),image.getHeight());
   float aspect=followBounds!=null&&trimmed?bounds.displayAspect():(float)bounds.width()/bounds.height();
   var framing=followBounds==null?value():VinylLabelFraming.DEFAULT;
   var box=framing.place(aspect,size);
   g.translate(cx,cy);
   int x=Math.round(box.x()),y=Math.round(box.y());
   int right=Math.round(box.x()+box.width()),bottom=Math.round(box.y()+box.height());
   var circle=circle(r);
   var windows=reel?reelWindows(r):null;
   float hole=style==DiscSettings.Style.CD?r*RootRenderer.CD_HOLE_RATIO:
       reel?r*ReelGeometry.SPINDLE_RADIUS:r*.029f/.375f;
   var face=new Area(reel?circle(r*ReelGeometry.FACE_RADIUS):circle);
   face.subtract(new Area(circle(hole)));
   if(windows!=null)face.subtract(new Area(windows));
   var clip=g.getClip();
   g.setComposite(AlphaComposite.SrcOver.derive(.27f));
   drawImage(g,bounds,x,y,right,bottom);
   g.setComposite(AlphaComposite.SrcOver);
   g.clip(face);
   int tint=switch(style){
    case CD -> RootRenderer.CD_FACE_COLOR;
    case REEL_COVER -> framing.fit()?RootRenderer.REEL_FIT_COLOR:0xffff4208;
    case BLACK_VINYL -> RootRenderer.BLACK_LABEL_COLOR;
    default -> sample.tint();
   };
   g.setColor(new Color(tint));
   g.fill(circle);
   drawImage(g,bounds,x,y,right,bottom);
   g.setClip(clip);
   g.setColor(BLUE);
   g.draw(circle);
   paintDiscDetails(g,style,r,hole,windows);
  }

  private void paintDiscDetails(Graphics2D g,DiscSettings.Style style,float radius,float hole,Shape windows){
   if(windows!=null){
    g.setColor(new Color(0x524035));
    g.fill(windows);
    g.setColor(new Color(0x211b17));
    g.draw(windows);
    g.setColor(new Color(0x6477818b,true));
    g.fill(circle(radius*.338f));
    g.setColor(new Color(0x45413c));
    g.draw(circle(radius*.338f));
    g.setColor(new Color(0x22262a));
    g.fill(circle(radius*ReelGeometry.HUB_RADIUS));
   }else if(style==DiscSettings.Style.CD){
    var hub=new Area(circle(radius*.22f));
    hub.subtract(new Area(circle(hole)));
    g.setColor(new Color(0x78d5d9dc,true));
    g.fill(hub);
   }else{
    g.setColor(new Color(0xdedede));
    g.fill(circle(radius*.042f/.375f));
   }
   g.setColor(PREVIEW_BACKGROUND);
   g.fill(circle(hole));
  }

  private Shape reelWindows(float radius){
   var path=new Path2D.Float();
   ReelGeometry.windows(radius,new ReelGeometry.WindowPath(){
    public void moveTo(float x,float y){path.moveTo(x,y);}
    public void lineTo(float x,float y){path.lineTo(x,y);}
    public void corner(float x,float y,float nextX,float nextY){path.quadTo(x,y,nextX,nextY);}
    public void arc(float r,float start,float sweep){path.append(new Arc2D.Float(-r,-r,r*2,r*2,-start,-sweep,Arc2D.OPEN),true);}
    public void close(){path.closePath();}
   });
   return path;
  }

  private Ellipse2D.Float circle(float radius){
   return new Ellipse2D.Float(-radius,-radius,radius*2,radius*2);
  }

  private void drawImage(Graphics2D g,CoverWhiteBorders.Bounds bounds,int x,int y,int right,int bottom){
   g.drawImage(sample.image(),x,y,right,bottom,bounds.x(),bounds.y(),
       bounds.x()+bounds.width(),bounds.y()+bounds.height(),null);
  }
 }
}
