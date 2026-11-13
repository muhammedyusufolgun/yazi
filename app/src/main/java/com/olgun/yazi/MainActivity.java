package com.olgun.yazi;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
  final int bg=Color.WHITE, red=Color.rgb(155,20,20);
  EditText editor; LinearLayout root, items; LanLink lan; Marquee activeMarquee; android.content.SharedPreferences prefs;
  ArrayList<String> saved=new ArrayList<>(); int selected=-1; boolean playing=false;
  @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("texts",0);load();lan=new LanLink(this);showEditor("");}
  void load(){saved.clear();try{JSONArray a=new JSONArray(prefs.getString("list","[]"));for(int i=0;i<a.length();i++)saved.add(a.getString(i));}catch(Exception ignored){}}
  void persist(){JSONArray a=new JSONArray();for(String s:saved)a.put(s);prefs.edit().putString("list",a.toString()).apply();}
  TextView button(String s,LinearLayout row,Runnable action){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(17);v.setGravity(17);v.setPadding(18,20,18,20);v.setBackgroundColor(red);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(4,4,4,4);row.addView(v,p);v.setOnClickListener(w->action.run());return v;}
  void showEditor(String value){
    playing=false;setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    getWindow().getDecorView().setSystemUiVisibility(0);
    LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setBackgroundColor(bg);
    page.setPadding(16,0,16,0);setContentView(page);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
    page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    root=new LinearLayout(this);root.setOrientation(1);root.setPadding(0,120,0,0);scroll.addView(root);
    TextView heading=new TextView(this);heading.setText("Kayan Yazı");heading.setTextSize(26);
    heading.setTextColor(0xffd32f2f);heading.setGravity(Gravity.CENTER);heading.setTypeface(null,Typeface.BOLD);heading.setBackgroundColor(Color.BLACK);heading.setPadding(8,16,8,16);root.addView(heading,new LinearLayout.LayoutParams(-1,-2));heading.setOnClickListener(v->{editor.setText("");selected=-1;editor.clearFocus();});
    editor=new EditText(this);editor.setTextColor(Color.BLACK);editor.setHintTextColor(0xff777777);
    editor.setHint("Metin yazın veya yapıştırın");editor.setTextSize(20);editor.setGravity(Gravity.TOP);
    editor.setMinLines(5);editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
      android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    editor.setText(value);root.addView(editor,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout controls=new LinearLayout(this);root.addView(controls,new LinearLayout.LayoutParams(-1,-2));
    button("Kaydet",controls,()->{
      String t=editor.getText().toString().trim();if(t.isEmpty()){toast("Metin girin");return;}
      saved.add(t);selected=saved.size()-1;persist();showEditor(t);toast("Kaydedildi");
    }).setBackgroundColor(0xff03a9f4);
    button("Oynat",controls,()->{
      String t=editor.getText().toString();if(t.trim().isEmpty()){toast("Metin girin");return;}
      hideKeyboard();startPlay(t);
    }).setBackgroundColor(0xff2e7d32);
    TextView del=button("Sil",controls,()->{
      if(selected<0||selected>=saved.size()){toast("Listeden yazı seçin");return;}
      confirmDelete(selected);
    });del.setBackgroundColor(0xffef9a9a);del.setTextColor(Color.BLACK);
    LinearLayout remote=new LinearLayout(this);root.addView(remote,new LinearLayout.LayoutParams(-1,-2));
    button("Cihaz",remote,()->lan.choose()).setBackgroundColor(0xff546e7a);
    button("Gönder ve Oynat",remote,()->{
      String t=editor.getText().toString();
      if(t.trim().isEmpty()){toast("Metin girin");return;}
      String peer=lan.chosen();if(peer.isEmpty()){toast("Önce cihaz eşleştirin");lan.choose();return;}
      lan.send(peer,"play",t,this::toast);
    }).setBackgroundColor(0xff81c784);
    button("Durdur",remote,()->{
      String peer=lan.chosen();if(peer.isEmpty()){toast("Cihaz seçin");return;}
      lan.send(peer,"stop","",this::toast);
    }).setBackgroundColor(0xffc62828);
    LinearLayout speedRow=new LinearLayout(this);speedRow.setOrientation(LinearLayout.HORIZONTAL);
    speedRow.setGravity(Gravity.CENTER_VERTICAL);speedRow.setPadding(4,12,4,12);
    root.addView(speedRow,new LinearLayout.LayoutParams(-1,-2));
    TextView slowLabel=new TextView(this);slowLabel.setText("Yavaş");slowLabel.setTextColor(0xffc62828);
    slowLabel.setTextSize(16);slowLabel.setTypeface(null,Typeface.BOLD);
    speedRow.addView(slowLabel);
    SeekBar speedSlider=new SeekBar(this);speedSlider.setMax(100);speedSlider.setProgress(50);
    speedSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff333333));
    speedSlider.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff333333));
    speedSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(0xff222222));
    speedSlider.setElevation(5f);
    LinearLayout.LayoutParams sliderParams=new LinearLayout.LayoutParams(0,-2,1);
    sliderParams.setMargins(12,0,12,0);speedRow.addView(speedSlider,sliderParams);
    TextView fastLabel=new TextView(this);fastLabel.setText("Hızlı");fastLabel.setTextColor(0xffc62828);
    fastLabel.setTextSize(16);fastLabel.setTypeface(null,Typeface.BOLD);speedRow.addView(fastLabel);
    speedSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      long lastSent=0;
      public void onStartTrackingTouch(SeekBar bar){}
      public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
        if(!fromUser)return;
        remoteSpeedValue=0.3f+(progress/100f)*39.7f;
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-lastSent>=120){lastSent=now;sendRemoteSpeed(false);}
      }
      public void onStopTrackingTouch(SeekBar bar){sendRemoteSpeed(true);}
    });
    TextView title=new TextView(this);title.setText("Kaydedilen Yazılar");title.setTextSize(21);
    title.setTextColor(Color.BLACK);title.setPadding(0,24,0,12);root.addView(title);
    items=new LinearLayout(this);items.setOrientation(1);root.addView(items);
    for(int i=0;i<saved.size();i++){
      final int idx=i;TextView entry=new TextView(this);
      entry.setText((i+1)+". "+saved.get(i).replace("\\n"," "));
      entry.setTextColor(Color.BLACK);entry.setTextSize(17);entry.setPadding(14,16,14,16);
      entry.setSingleLine(true);entry.setEllipsize(android.text.TextUtils.TruncateAt.END);
      entry.setBackgroundColor(selected==i?0xffb3e5fc:0xffeeeeee);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,2,0,2);
      items.addView(entry,lp);entry.setOnClickListener(v->{selected=idx;showEditor(saved.get(idx));});
    }
    TextView footer=new TextView(this);footer.setText("Muhammed Yusuf OLGUN");
    footer.setTextColor(Color.BLACK);footer.setTextSize(14);footer.setGravity(Gravity.CENTER);
    footer.setTypeface(null,Typeface.ITALIC);footer.setPadding(0,4,0,144);
    page.addView(footer,new LinearLayout.LayoutParams(-1,-2));
  }
  float remoteSpeedValue=3f;
  void sendRemoteSpeed(boolean showFeedback){String peer=lan.chosen();if(peer.isEmpty()){if(showFeedback)toast("Cihaz seçin");return;}lan.send(peer,"speed",String.valueOf(remoteSpeedValue),result->{if(showFeedback)toast(result);});}
  void remoteSpeed(int delta){String peer=lan.chosen();if(peer.isEmpty()){toast("Cihaz seçin");return;}remoteSpeedValue=Math.max(.3f,Math.min(40f,remoteSpeedValue+delta));lan.send(peer,"speed",String.valueOf(remoteSpeedValue),this::toast);}
  void setRemoteSpeed(float speed){if(activeMarquee!=null)activeMarquee.speed=Math.max(.3f,Math.min(40f,speed));}
  @Override protected void onDestroy(){if(lan!=null)lan.shutdown();super.onDestroy();}
  void confirmDelete(int i){new AlertDialog.Builder(this).setTitle("Metni sil").setMessage("Bu kaydı silmek istiyor musunuz?").setNegativeButton("Vazgeç",null).setPositiveButton("Devam", (d,w)->new AlertDialog.Builder(this).setTitle("Son onay").setMessage("Bu kayıt kalıcı olarak silinecek. Onaylıyor musunuz?").setNegativeButton("İptal",null).setPositiveButton("Sil",(d2,w2)->{saved.remove(i);selected=-1;persist();showEditor("");}).show()).show();}
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
  void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0);}
  void startPlay(String t){playing=true;setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);activeMarquee=new Marquee(this,t);setContentView(activeMarquee);}
  class Marquee extends View {
    Paint paint=new Paint(3);String text;float x,speed=3f,lastY;long previous;boolean leftGesture=false;GestureDetector gestures;
    Marquee(Context c,String s){super(c);text=s.replace('\n',' ');setBackgroundColor(Color.BLACK);paint.setColor(red);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDoubleTap(MotionEvent e){runOnUiThread(()->showEditor(text));return true;}@Override public boolean onDown(MotionEvent e){return true;}});}
    @Override protected void onDraw(Canvas c){super.onDraw(c);paint.setTextSize(getHeight()*.68f);float width=paint.measureText(text);if(previous==0){x=getWidth();previous=System.nanoTime();}long now=System.nanoTime();float dt=Math.min((now-previous)/16666667f,3f);previous=now;x-=speed*dt;if(x+width<0)x=getWidth();float baseline=getHeight()/2f-(paint.ascent()+paint.descent())/2f;c.drawText(text,x,baseline,paint);postInvalidateDelayed(16);}
    @Override public boolean onTouchEvent(MotionEvent e){gestures.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastY=e.getY();leftGesture=e.getX()<getWidth()*.25f;return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&leftGesture){float dy=e.getY()-lastY;speed=Math.max(.3f,Math.min(40f,speed-dy*.018f));lastY=e.getY();}return true;}
  }
  @Override public void onBackPressed(){if(playing)showEditor("");else super.onBackPressed();}
}
