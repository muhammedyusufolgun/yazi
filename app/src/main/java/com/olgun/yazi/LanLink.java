package com.olgun.yazi;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public class LanLink {
  static final int UDP=37655,TCP=37656;
  final MainActivity activity;
  final android.content.SharedPreferences prefs;
  final String id;
  final Handler ui=new Handler(Looper.getMainLooper());
  final ExecutorService workers=Executors.newCachedThreadPool();
  final ConcurrentHashMap<String,String> hosts=new ConcurrentHashMap<>();
  volatile boolean running=true;
  DatagramSocket udpSocket;
  ServerSocket server;
  String selectedId="";
  interface Reply {void done(String value);}
  LanLink(MainActivity a){
    activity=a;prefs=a.getSharedPreferences("lan",0);
    String stored=prefs.getString("id","");
    if(stored.isEmpty()){stored=UUID.randomUUID().toString();prefs.edit().putString("id",stored).apply();}
    id=stored;
    workers.execute(this::listenUdp);
    workers.execute(this::listenTcp);
    workers.execute(()->{while(running){announce();try{Thread.sleep(5000);}catch(Exception ignored){}}});
  }
  void announce(){
    workers.execute(()->{
      try(DatagramSocket s=new DatagramSocket()){
        s.setBroadcast(true);
        byte[] data=("YAZI|"+id+"|"+android.os.Build.MODEL).getBytes("UTF-8");
        for(InetAddress dest:new InetAddress[]{InetAddress.getByName("255.255.255.255")}){
          s.send(new DatagramPacket(data,data.length,dest,UDP));
        }
        Enumeration<NetworkInterface> it=NetworkInterface.getNetworkInterfaces();
        while(it.hasMoreElements()){
          NetworkInterface ni=it.nextElement();
          if(!ni.isUp()||ni.isLoopback())continue;
          for(InterfaceAddress addr:ni.getInterfaceAddresses()){
            InetAddress b=addr.getBroadcast();
            if(b!=null)try{s.send(new DatagramPacket(data,data.length,b,UDP));}catch(Exception ignored){}
          }
        }
      }catch(Exception ignored){}
    });
  }
  void listenUdp(){
    try{
      udpSocket=new DatagramSocket(null);
      udpSocket.setReuseAddress(true);
      udpSocket.bind(new InetSocketAddress(UDP));
      while(running){
        byte[] buf=new byte[1024];DatagramPacket p=new DatagramPacket(buf,buf.length);
        udpSocket.receive(p);
        String msg=new String(p.getData(),0,p.getLength(),"UTF-8");
        String[] parts=msg.split("\\|",3);
        if(parts.length==3&&parts[0].equals("YAZI")&&!parts[1].equals(id)){
          hosts.put(parts[1],p.getAddress().getHostAddress());
          prefs.edit().putString("host_"+parts[1],p.getAddress().getHostAddress()).apply();
        }
      }
    }catch(Exception ignored){}
  }
  void listenTcp(){
    try{
      server=new ServerSocket(TCP);
      while(running){Socket socket=server.accept();workers.execute(()->receive(socket));}
    }catch(Exception ignored){}
  }
  void receive(Socket socket){
    try(Socket s=socket){
      s.setSoTimeout(12000);
      DataInputStream in=new DataInputStream(s.getInputStream());
      DataOutputStream out=new DataOutputStream(s.getOutputStream());
      JSONObject j=new JSONObject(in.readUTF());
      String type=j.optString("type"),sender=j.optString("sender");
      if(type.equals("pair")){
        if(sender.isEmpty()||sender.equals(id))return;
        String ip=s.getInetAddress().getHostAddress();
        ui.post(()->new AlertDialog.Builder(activity).setTitle("Cihaz eşleştirme")
          .setMessage(ip+" adresindeki cihaz eşleşmek istiyor. İzin verilsin mi?")
          .setNegativeButton("Reddet",(d,w)->workers.execute(()->reply(out,new JSONObject())))
          .setPositiveButton("Eşleştir",(d,w)->{
            String token=UUID.randomUUID().toString()+UUID.randomUUID().toString();
            prefs.edit().putString("allow_"+sender,token).putString("host_"+sender,ip).apply();
            JSONObject r=new JSONObject();
            try{r.put("token",token);r.put("id",id);}catch(Exception ignored){}
            workers.execute(()->reply(out,r));
          }).show());
        // Wait for the UI approval callback to write the response before socket closes.
        try{Thread.sleep(25000);}catch(Exception ignored){}
        return;
      }
      if(!prefs.getString("allow_"+sender,"!").equals(j.optString("token","")))return;
      String text=j.optString("text");
      ui.post(()->{
        if(type.equals("play"))activity.startPlay(text);
        else if(type.equals("stop"))activity.showEditor("");
        else if(type.equals("speed"))activity.setRemoteSpeed((float)j.optDouble("speed",3));
      });
      out.writeUTF("OK");out.flush();
    }catch(Exception ignored){}
  }
  void reply(DataOutputStream out,JSONObject obj){try{out.writeUTF(obj.toString());out.flush();}catch(Exception ignored){}}
  void send(String target,String type,String value,Reply cb){
    workers.execute(()->{
      String host=hosts.get(target);
      if(host==null)host=prefs.getString("host_"+target,"");
      String token=prefs.getString("token_"+target,"");
      if(host.isEmpty()||token.isEmpty()){ui.post(()->cb.done("Cihaz bulunamadı veya eşleşmedi"));return;}
      try(Socket s=new Socket()){
        s.connect(new InetSocketAddress(host,TCP),3500);s.setSoTimeout(5000);
        JSONObject j=new JSONObject();j.put("type",type);j.put("sender",id);j.put("token",token);
        j.put("text",value);if(type.equals("speed"))j.put("speed",Double.parseDouble(value));
        DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeUTF(j.toString());out.flush();
        String result=new DataInputStream(s.getInputStream()).readUTF();
        ui.post(()->cb.done(result.equals("OK")?"Gönderildi":"Komut reddedildi"));
      }catch(Exception e){ui.post(()->cb.done("Bağlantı kurulamadı: "+e.getClass().getSimpleName()));}
    });
  }
  void pair(String target,Reply cb){
    workers.execute(()->{
      String host=hosts.get(target);
      if(host==null){ui.post(()->cb.done("Cihaz bulunamadı"));return;}
      try(Socket s=new Socket()){
        s.connect(new InetSocketAddress(host,TCP),3500);s.setSoTimeout(35000);
        JSONObject j=new JSONObject();j.put("type","pair");j.put("sender",id);
        DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeUTF(j.toString());out.flush();
        JSONObject response=new JSONObject(new DataInputStream(s.getInputStream()).readUTF());
        String token=response.optString("token");
        if(token.isEmpty()){ui.post(()->cb.done("Eşleştirme reddedildi"));return;}
        prefs.edit().putString("token_"+target,token).putString("host_"+target,host).apply();
        selectedId=target;ui.post(()->cb.done("Eşleşme kaydedildi"));
      }catch(Exception e){ui.post(()->cb.done("Eşleştirme başarısız: "+e.getClass().getSimpleName()));}
    });
  }
  void choose(){
    announce();
    ui.postDelayed(()->{
      LinkedHashSet<String> ids=new LinkedHashSet<>(hosts.keySet());
      for(String key:prefs.getAll().keySet())if(key.startsWith("token_"))ids.add(key.substring(6));
      if(ids.isEmpty()){Toast.makeText(activity,"Aynı Wi-Fi ağına bağlanın ve yeniden deneyin",0).show();return;}
      ArrayList<String> keys=new ArrayList<>(ids);
      String[] labels=new String[keys.size()];
      for(int i=0;i<keys.size();i++){
        String key=keys.get(i);
        labels[i]=(prefs.contains("token_"+key)?"Eşleşmiş • ":"Yeni • ")+
          (hosts.containsKey(key)?"Çevrimiçi":"Son bilinen cihaz")+" • "+key.substring(0,8);
      }
      new AlertDialog.Builder(activity).setTitle("Cihaz seç / eşleştir").setItems(labels,(d,which)->{
        String key=keys.get(which);
        if(prefs.contains("token_"+key)){selectedId=key;Toast.makeText(activity,"Cihaz seçildi",0).show();}
        else pair(key,result->Toast.makeText(activity,result,1).show());
      }).setNegativeButton("Kapat",null).show();
    },1600);
  }
  String chosen(){
    if(!selectedId.isEmpty())return selectedId;
    for(String key:prefs.getAll().keySet())if(key.startsWith("token_"))return key.substring(6);
    return "";
  }
  void shutdown(){running=false;try{udpSocket.close();}catch(Exception ignored){}try{server.close();}catch(Exception ignored){}workers.shutdownNow();}
}
