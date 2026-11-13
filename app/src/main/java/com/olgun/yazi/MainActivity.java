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
  final int bg=Color.rgb(48,48,48), red=Color.rgb(155,20,20);
  EditText editor; LinearLayout root, items; android.content.SharedPreferences prefs;
  ArrayList<String> saved=new ArrayList<>(); int selected=-1; boolean playing=false;
  @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("texts",0);load();showEditor("");}
  void load(){saved.clear();try{JSONArray a=new JSONArray(prefs.getString("list","[]"));for(int i=0;i<a.length();i++)saved.add(a.getString(i));}catch(Exception ignored){}}
  void persist(){JSONArray a=new JSONArray();for(String s:saved)a.put(s);prefs.edit().putString("list",a.toString()).apply();}
  TextView button(String s,LinearLayout row,Runnable action){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(17);v.setGravity(17);v.setPadding(18,20,18,20);v.setBackgroundColor(red);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(4,4,4,4);row.addView(v,p);v.setOnClickListener(w->action.run());return v;}
  void showEditor(String value){playing=false;setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);getWindow().getDecorView().setSystemUiVisibility(0);
    ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(16,24,16,16);root.setBackgroundColor(bg);scroll.addView(root);setContentView(scroll);
    TextView heading=new TextView(this);heading.setText("Metin");heading.setTextSize(26);heading.setTextColor(Color.WHITE);heading.setTypeface(null,1);root.addView(heading);
    editor=new EditText(this);editor.setTextColor(Color.WHITE);editor.setHintTextColor(0xffbbbbbb);editor.setHint("Metin yazın veya yapıştırın");editor.setTextSize(20);editor.setGravity(Gravity.TOP);editor.setMinLines(5);editor.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);editor.setText(value);root.addView(editor,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout controls=new LinearLayout(this);root.addView(controls);button("Oynat",controls,()->{String t=editor.getText().toString();if(t.trim().isEmpty()){toast("Metin girin");return;}hideKeyboard();startPlay(t);});button("Kaydet",controls,()->{String t=editor.getText().toString().trim();if(t.isEmpty()){toast("Metin girin");return;}if(selected>=0&&selected<saved.size())saved.set(selected,t);else {saved.add(t);selected=saved.size()-1;}persist();showEditor(t);toast("Kaydedildi");});
    TextView title=new TextView(this);title.setText("Kayıtlı Metinler");title.setTextSize(21);title.setTextColor(Color.WHITE);title.setPadding(0,25,0,12);root.addView(title);
    items=new LinearLayout(this);items.setOrientation(1);root.addView(items);
    for(int i=0;i<saved.size();i++){final int idx=i;LinearLayout line=new LinearLayout(this);items.addView(line);TextView choose=button((i+1)+". "+saved.get(i).replace("\n"," "),line,()->{selected=idx;showEditor(saved.get(idx));});choose.setSingleLine(true);choose.setEllipsize(android.text.TextUtils.TruncateAt.END);button("Sil",line,()->confirmDelete(idx));}
  }
  void confirmDelete(int i){new AlertDialog.Builder(this).setTitle("Metni sil").setMessage("Bu kaydı silmek istiyor musunuz?").setNegativeButton("Vazgeç",null).setPositiveButton("Devam", (d,w)->new AlertDialog.Builder(this).setTitle("Son onay").setMessage("Bu kayıt kalıcı olarak silinecek. Onaylıyor musunuz?").setNegativeButton("İptal",null).setPositiveButton("Sil",(d2,w2)->{saved.remove(i);selected=-1;persist();showEditor("");}).show()).show();}
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
  void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0);}
  void startPlay(String t){playing=true;setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);setContentView(new Marquee(this,t));}
  class Marquee extends View {
    Paint paint=new Paint(3);String text;float x,speed=3f,lastY;long previous;boolean leftGesture=false;GestureDetector gestures;
    Marquee(Context c,String s){super(c);text=s.replace('\n',' ');setBackgroundColor(Color.BLACK);paint.setColor(red);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDoubleTap(MotionEvent e){runOnUiThread(()->showEditor(text));return true;}@Override public boolean onDown(MotionEvent e){return true;}});}
    @Override protected void onDraw(Canvas c){super.onDraw(c);paint.setTextSize(getHeight()*.68f);float width=paint.measureText(text);if(previous==0){x=getWidth();previous=System.nanoTime();}long now=System.nanoTime();float dt=Math.min((now-previous)/16666667f,3f);previous=now;x-=speed*dt;if(x+width<0)x=getWidth();float baseline=getHeight()/2f-(paint.ascent()+paint.descent())/2f;c.drawText(text,x,baseline,paint);postInvalidateDelayed(16);}
    @Override public boolean onTouchEvent(MotionEvent e){gestures.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastY=e.getY();leftGesture=e.getX()<getWidth()*.25f;return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&leftGesture){float dy=e.getY()-lastY;speed=Math.max(.3f,Math.min(40f,speed-dy*.018f));lastY=e.getY();}return true;}
  }
  @Override public void onBackPressed(){if(playing)showEditor("");else super.onBackPressed();}
}
