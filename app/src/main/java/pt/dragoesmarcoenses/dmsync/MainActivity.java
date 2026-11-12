package pt.dragoesmarcoenses.dmsync;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private static final String SOURCE = "https://dragoesmarcoenses.pt/admin/members";
    private static final String TARGET = "https://comunidade.fcporto.pt/DELEGACAO_371/settings";
    private static final int NAVY = Color.rgb(8, 29, 64);
    private static final int BLUE = Color.rgb(16, 86, 210);
    private static final String PREFS = "dm_sync_encrypted_data";
    private static final String KEY_ALIAS = "DM_SYNC_PRIVATE_KEY_02";
    private WebView ui, portal;
    private LinearLayout portalLayer, buttonBar;
    private TextView portalTitle, progressText;
    private Button importPageButton, importAllButton, importDetailsButton, prefillButton;
    private boolean portalVisible, isSource, importing, autoFillBusy, autoFillRequested;
    private String currentMemberId = "", lastPageKey = "";
    private int pageCount = 0, newCount = 0, autoFillSteps = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final String extractionScript = "(function(){"
        + "function clean(s){return String(s||'').replace(/\\s+/g,' ').trim()}"
        + "var table=[].slice.call(document.querySelectorAll('table')).find(t=>t.querySelectorAll('tbody tr').length>0);"
        + "if(!table)return JSON.stringify({items:[],next:false,error:'Não encontrei uma tabela de sócios nesta página.'});"
        + "var hs=[].slice.call(table.querySelectorAll('thead th')).map(x=>clean(x.innerText).toLowerCase());"
        + "function idx(reg,fallback){var n=hs.findIndex(h=>reg.test(h));return n<0?fallback:n}"
        + "var ni=idx(/nome/,1),ei=idx(/email|e-mail/,2),si=idx(/s[oó]cio/,3),fi=idx(/fc\\s*porto|n.\\s*fcp/,-1);"
        + "var items=[].slice.call(table.querySelectorAll('tbody tr')).map(tr=>{var cs=[].slice.call(tr.querySelectorAll('td')).map(c=>clean(c.innerText));"
        + "return {name:cs[ni]||'',email:cs[ei]||'',number:cs[si]||'',fcp:fi>=0?(cs[fi]||''):''}}).filter(m=>m.name&&m.number&&/\\d/.test(m.number));"
        + "var bs=[].slice.call(document.querySelectorAll('button'));var next=null;"
        + "for(var b of bs){var tag=clean((b.getAttribute('aria-label')||'')+' '+(b.getAttribute('title')||'')+' '+b.innerText).toLowerCase();"
        + "if(/pr[oó]xim[ao]|seguinte|next page|go to next|p[aá]gina seguinte/.test(tag)&&!b.disabled){next=b;break}}"
        + "if(!next){var svg=document.querySelector('button:not([disabled]) svg.lucide-chevron-right,button:not([disabled]) svg[data-lucide=chevron-right]');if(svg)next=svg.closest('button')}"
        + "var label=clean(document.body.innerText.match(/p[aá]gina\\s+\\d+\\s+de\\s+\\d+/i)?.[0]||'');"
        + "return JSON.stringify({items:items,next:!!next,label:label})"
        + "})()";
    private final String nextScript = "(function(){var btn=[].slice.call(document.querySelectorAll('button')).find(b=>"
        + "!b.disabled&&/pr[oó]xim[ao]|seguinte|next page|go to next|p[aá]gina seguinte/i.test((b.getAttribute('aria-label')||'')+' '+(b.getAttribute('title')||'')+' '+b.innerText));"
        + "if(!btn){var el=document.querySelector('button:not([disabled]) svg.lucide-chevron-right,button:not([disabled]) svg[data-lucide=chevron-right]');btn=el?el.closest('button'):null}"
        + "if(btn&&!btn.disabled){btn.click();return true}return false})()";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        getWindow().getDecorView().setSystemUiVisibility(0);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(244, 247, 252));
        setContentView(root);
        ui = new WebView(this);
        WebSettings us = ui.getSettings();
        us.setJavaScriptEnabled(true);
        us.setDomStorageEnabled(true);
        us.setAllowFileAccess(true);
        us.setAllowContentAccess(false);
        us.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ui.setBackgroundColor(Color.rgb(244,247,252));
        ui.addJavascriptInterface(new Bridge(), "Bridge");
        ui.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return !url.startsWith("file:///android_asset/");
            }
        });
        root.addView(ui, new FrameLayout.LayoutParams(-1,-1));
        ui.loadUrl("file:///android_asset/index.html");
        setupPortal(root);
    }

    private int dp(int x) { return (int)(x*getResources().getDisplayMetrics().density+0.5f); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);t.setTextColor(color);t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private Button smallButton(String label, int bg) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(12);
        b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bg));
        b.setPadding(dp(7),0,dp(7),0);
        return b;
    }
    private void setupPortal(FrameLayout root) {
        portalLayer = new LinearLayout(this);
        portalLayer.setOrientation(LinearLayout.VERTICAL);
        portalLayer.setBackgroundColor(Color.WHITE);
        portalLayer.setVisibility(View.GONE);
        root.addView(portalLayer, new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(9),dp(4),dp(12),dp(4));
        header.setBackgroundColor(NAVY);
        portalLayer.addView(header,new LinearLayout.LayoutParams(-1,dp(58)));
        Button close = smallButton("‹ Voltar",NAVY);
        header.addView(close,new LinearLayout.LayoutParams(dp(91),dp(47)));
        close.setOnClickListener(v->closePortal());
        portalTitle=text("Portal",15,Color.WHITE,true);
        header.addView(portalTitle,new LinearLayout.LayoutParams(0,-1,1f));
        Button refresh = smallButton("↻",NAVY);
        header.addView(refresh,new LinearLayout.LayoutParams(dp(46),dp(45)));
        refresh.setOnClickListener(v->portal.reload());
        portal = new WebView(this);
        portal.setBackgroundColor(Color.WHITE);
        WebSettings s = portal.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(portal,true);
        portal.setWebChromeClient(new WebChromeClient());
        portal.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v,String url) {
                progressText.setText(isSource?"Inicia sessão e importa os membros":"Portal FC Porto · confirmação final obrigatória");
                updateButtons();
                if (!isSource && autoFillRequested && !autoFillBusy) handler.postDelayed(()->{
                    if(portalVisible && !isSource && !autoFillBusy) prefillCurrent();
                },1250);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u=req.getUrl();
                if("https".equalsIgnoreCase(u.getScheme())||"about".equals(u.getScheme()))return false;
                return true;
            }
        });
        portalLayer.addView(portal,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(12),dp(7),dp(12),dp(9));
        bottom.setBackgroundColor(NAVY);
        portalLayer.addView(bottom,new LinearLayout.LayoutParams(-1,dp(95)));
        progressText = text("A iniciar...",11,Color.rgb(200,218,244),false);
        bottom.addView(progressText,new LinearLayout.LayoutParams(-1,dp(24)));
        buttonBar = new LinearLayout(this);
        buttonBar.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(buttonBar,new LinearLayout.LayoutParams(-1,dp(50)));
        importPageButton = smallButton("Importar página",BLUE);
        importAllButton = smallButton("Importar lista",Color.rgb(31,93,152));
        importDetailsButton = smallButton("Ler ficha",Color.rgb(28,71,127));
        // O registo final do FC Porto permanece dependente de confirmação humana.
        prefillButton = smallButton("Preencher dados",BLUE);
        buttonBar.addView(importPageButton,new LinearLayout.LayoutParams(0,dp(47),1f));
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(0,dp(47),1f);gap.leftMargin=dp(6);
        buttonBar.addView(importAllButton,gap);
        LinearLayout.LayoutParams third = new LinearLayout.LayoutParams(0,dp(47),1f);third.leftMargin=dp(6);
        buttonBar.addView(importDetailsButton,third);
        buttonBar.addView(prefillButton,new LinearLayout.LayoutParams(0,dp(47),1f));
        importPageButton.setOnClickListener(v->importPage(false));
        importAllButton.setOnClickListener(v->startImportAll());
        importDetailsButton.setOnClickListener(v->importDetails());
        prefillButton.setOnClickListener(v->prefillCurrent());
    }
    private void updateButtons(){
        importPageButton.setVisibility(isSource?View.VISIBLE:View.GONE);
        importAllButton.setVisibility(isSource?View.VISIBLE:View.GONE);
        importDetailsButton.setVisibility(isSource?View.VISIBLE:View.GONE);
        prefillButton.setVisibility(!isSource&&currentMemberId.length()>0?View.VISIBLE:View.GONE);
        importPageButton.setEnabled(!importing);importAllButton.setEnabled(!importing);importDetailsButton.setEnabled(!importing);
    }
    private boolean onRightHost(boolean source) {
        Uri u=Uri.parse(portal.getUrl()==null?"":portal.getUrl());
        String host=u.getHost()==null?"":u.getHost().toLowerCase(Locale.ROOT);
        return source?(host.equals("dragoesmarcoenses.pt")||host.endsWith(".dragoesmarcoenses.pt")):(host.equals("comunidade.fcporto.pt"));
    }
    private void openPortal(String kind,String memberId){
        isSource=!"fcporto".equals(kind);
        currentMemberId=memberId==null?"":memberId;
        if (!isSource && !currentMemberId.isEmpty()) {
            JSONObject x = findById(currentMemberId);
            if(x==null||!("aprovado".equals(x.optString("status"))||"aguarda_fc".equals(x.optString("status")))) {
                alert("Validação obrigatória","É necessário aprovar o registo antes de preparar a inscrição.");return;
            }
            // O estado aguarda_fc só pode ser atribuído após confirmação expressa da submissão.
        }
        portalVisible=true;portalLayer.setVisibility(View.VISIBLE);
        portalTitle.setText(isSource?"Dragões Marcoenses":"Comunidade FC Porto");
        importing=false;autoFillBusy=false;autoFillSteps=0;
        autoFillRequested=!isSource&&!currentMemberId.isEmpty();
        updateButtons();
        if(!isSource)portal.evaluateJavascript("window.__dmSyncFlowMode='';window.__dmSyncLookupAttempted=false;",null);
        String url=isSource?SOURCE:TARGET;
        Uri u=Uri.parse(portal.getUrl()==null?"":portal.getUrl());
        String host=u.getHost()==null?"":u.getHost();
        if(portal.getUrl()==null || !(isSource?host.contains("dragoesmarcoenses.pt"):host.equals("comunidade.fcporto.pt")))portal.loadUrl(url);
        else portal.reload();
        progressText.setText("A carregar sessão segura...");
    }
    private void closePortal() {
        importing=false;autoFillBusy=false;autoFillRequested=false;portalVisible=false;portalLayer.setVisibility(View.GONE);
        ui.evaluateJavascript("window.refreshAll && window.refreshAll()",null);
    }
    @Override public void onBackPressed() {
        if(portalVisible) {
            if(importing){importing=false;progressText.setText("Importação interrompida");updateButtons();return;}
            if(portal.canGoBack()) portal.goBack();else closePortal();
        }else{ui.evaluateJavascript("window.closeModal && window.closeModal()",null);}
    }
    private void alert(String title,String message){runOnUiThread(()->new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show());}
    private String jsonReply(boolean ok,String message){JSONObject o=new JSONObject();try{o.put("ok",ok);if(!ok)o.put("error",message);}catch(JSONException ignored){}return o.toString();}
    private String now(){return new SimpleDateFormat("dd/MM/yyyy HH:mm",new Locale("pt","PT")).format(new Date());}
    private synchronized JSONArray readList(){
        try {String encrypted=getSharedPreferences(PREFS,MODE_PRIVATE).getString("records",null);if(encrypted==null)return new JSONArray();return new JSONArray(decrypt(encrypted));}
        catch(Exception e){return new JSONArray();}
    }
    private synchronized void storeList(JSONArray list){
        try{String encrypted=encrypt(list.toString());getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("records",encrypted).apply();}
        catch(Exception e){throw new IllegalStateException("Erro de cifragem dos dados",e);}
    }
    private Key encryptionKey() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        Key k=ks.getKey(KEY_ALIAS,null);if(k!=null)return k;
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return gen.generateKey();
    }
    private String encrypt(String plain) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,encryptionKey());
        byte[] iv=c.getIV(),raw=c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv,Base64.NO_WRAP)+":"+Base64.encodeToString(raw,Base64.NO_WRAP);
    }
    private String decrypt(String raw) throws Exception {
        String[] p=raw.split(":",2);if(p.length!=2)throw new Exception("Formato inválido");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,encryptionKey(),new GCMParameterSpec(128,Base64.decode(p[0],Base64.DEFAULT)));
        return new String(c.doFinal(Base64.decode(p[1],Base64.DEFAULT)),StandardCharsets.UTF_8);
    }
    private synchronized JSONObject findById(String id){
        JSONArray data=readList();for(int i=0;i<data.length();i++){JSONObject x=data.optJSONObject(i);if(x!=null&&id.equals(x.optString("id")))return x;}return null;
    }
    private synchronized String save(JSONObject input,boolean imported){
        try {
            String name=input.optString("name").trim(),number=input.optString("number").trim();
            if(name.isEmpty()||number.isEmpty())return jsonReply(false,"O nome e o número da Casa são obrigatórios.");
            JSONArray data=readList();int index=-1;
            String id=input.optString("id","").trim();
            for(int i=0;i<data.length();i++){
                JSONObject x=data.optJSONObject(i);if(x==null)continue;
                if(!id.isEmpty()&&id.equals(x.optString("id"))){index=i;break;}
                if(number.equals(x.optString("number"))){index=i;id=x.optString("id");break;}
            }
            JSONObject rec=index<0?new JSONObject():data.getJSONObject(index);
            if(id.isEmpty())id=UUID.randomUUID().toString();
            rec.put("id",id);
            String[] attrs={"name","number","email","fcp","join","birth","ident","note"};
            for(String a:attrs){if(input.has(a)) {String v=input.optString(a).trim();if(!imported||!v.isEmpty()||index<0)rec.put(a,v);}}
            if(index<0){
                boolean baseReady=getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("baseline_initialized",false);
                rec.put("status",imported&&!baseReady?"referencia":"novo");
                rec.put("createdAt",now());
            }
            rec.put("updatedAt",now());
            if(index<0)data.put(rec);else data.put(index,rec);
            storeList(data);
            return jsonReply(true,"");
        } catch(Exception e){return jsonReply(false,"Não foi possível guardar o registo.");}
    }
    private synchronized String changeStatus(String id,String status){
        if(!java.util.Arrays.asList("novo","referencia","aprovado","aguarda_fc","concluido","rejeitado").contains(status))return jsonReply(false,"Estado inválido");
        try{
            JSONArray list=readList();
            for(int i=0;i<list.length();i++){
                JSONObject x=list.optJSONObject(i);
                if(x!=null&&id.equals(x.optString("id"))){
                    String old=x.optString("status","novo");
                    if("aguarda_fc".equals(status)&&!("aprovado".equals(old)||"aguarda_fc".equals(old)))return jsonReply(false,"Falta a aprovação.");
                    if("concluido".equals(status)&&!("aguarda_fc".equals(old)||"aprovado".equals(old)))return jsonReply(false,"O pedido ainda não foi aprovado.");
                    x.put("status",status);x.put("updatedAt",now());
                    if("aprovado".equals(status))x.put("approvedAt",now());
                    if("concluido".equals(status))x.put("completedAt",now());
                    storeList(list);return jsonReply(true,"");
                }
            }
        }catch(Exception ignored){}
        return jsonReply(false,"Registo não encontrado");
    }
    public final class Bridge {
        @JavascriptInterface public String listMembers(){return readList().toString();}
        @JavascriptInterface public String getSettings(){
            JSONObject j=new JSONObject();try {j.put("baseline",getSharedPreferences(PREFS,MODE_PRIVATE).getInt("baseline",712));}catch(Exception ignored){}return j.toString();
        }
        @JavascriptInterface public String updateBaseline(String s){
            try{int n=Integer.parseInt(s);if(n<0||n>1000000)return jsonReply(false,"Total fora do intervalo");
                getSharedPreferences(PREFS,MODE_PRIVATE).edit().putInt("baseline",n).apply();return jsonReply(true,"");}
            catch(Exception e){return jsonReply(false,"Total inválido");}
        }
        @JavascriptInterface public String upsert(String json){
            try{return save(new JSONObject(json),false);}catch(Exception e){return jsonReply(false,"Dados inválidos");}
        }
        @JavascriptInterface public String setStatus(String id,String status){return changeStatus(id,status);}
        @JavascriptInterface public String establishBaseline(){
            synchronized(MainActivity.this){
                JSONArray list=readList();
                try {
                    for(int i=0;i<list.length();i++){
                        JSONObject x=list.optJSONObject(i);
                        if(x!=null&&"novo".equals(x.optString("status","novo"))){
                            x.put("status","referencia"); x.put("updatedAt",now());
                        }
                    }
                    storeList(list);
                    getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("baseline_initialized",true).apply();
                    return jsonReply(true,"");
                }catch(Exception e){return jsonReply(false,"Não foi possível estabelecer a base.");}
            }
        }
        @JavascriptInterface public void clearMembers(){getSharedPreferences(PREFS,MODE_PRIVATE).edit().remove("records").apply();}
        @JavascriptInterface public void openPortal(String kind,String id){runOnUiThread(()->MainActivity.this.openPortal(kind,id));}
        @JavascriptInterface public String copyMember(String id){
            JSONObject m=findById(id);if(m==null)return jsonReply(false,"Sócio não encontrado");
            String content="DM Sync · Dados para inscrição\n"
                +"Nome: "+m.optString("name")+"\n"
                +"Número Casa: "+m.optString("number")+"\n"
                +"Nº FC Porto: "+m.optString("fcp")+"\n"
                +"Nº identificação: "+m.optString("ident")+"\n"
                +"Email: "+m.optString("email")+"\n"
                +"Data admissão na Casa: "+m.optString("join");
            runOnUiThread(()->{
                android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(cb!=null)cb.setPrimaryClip(android.content.ClipData.newPlainText("DM Sync",content));
                Toast.makeText(MainActivity.this,"Dados copiados",Toast.LENGTH_SHORT).show();
            });
            return jsonReply(true,"");
        }
        @JavascriptInterface public String getMemberForPortal(String id){
            JSONObject m=findById(id);if(m==null)return "{}";return m.toString();
        }
        @JavascriptInterface public void importFile(){runOnUiThread(()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/*","application/json","application/octet-stream"});
            startActivityForResult(i,24);
        });}
    }

    private String assetJS(String asset) {
        try (InputStream input=getAssets().open(asset);ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buf=new byte[4096];int n;
            while((n=input.read(buf))>=0)output.write(buf,0,n);
            return new String(output.toByteArray(),StandardCharsets.UTF_8);
        } catch(Exception error) { return "(function(){return JSON.stringify({error:'Código de integração não encontrado'})})()"; }
    }
    private JSONObject parseJsJson(String value) throws Exception {
        Object token=new JSONTokener(value).nextValue();
        return new JSONObject(String.valueOf(token));
    }
    private String keyOf(JSONArray arr){
        StringBuilder b=new StringBuilder();
        if(arr==null)return "";
        for(int i=0;i<arr.length();i++){
            JSONObject item=arr.optJSONObject(i);if(item!=null)b.append(item.optString("number")).append('|');
        }
        return b.toString();
    }
    private void waitPageChanged(String previous,int attempt){
        if(!importing||!portalVisible)return;
        handler.postDelayed(()->{
            if(!importing||!portalVisible)return;
            portal.evaluateJavascript("window.__dmPageOperation='scan';"+assetJS("page_scan.js"),value->{
                try{
                    JSONObject snapshot=parseJsJson(value);
                    JSONArray items=snapshot.optJSONArray("items");
                    String key=keyOf(items);
                    if(!key.isEmpty()&&!previous.equals(key)){importPage(true);return;}
                    if(attempt<28){waitPageChanged(previous,attempt+1);}
                    else stopAll("Não foi possível confirmar a mudança de página. Podes avançar manualmente e voltar a importar.");
                }catch(Exception e){stopAll("Erro ao verificar a página seguinte.");}
            });
        },450);
    }

    private void importPage(boolean all) {
        if(!onRightHost(true)){
            alert("Importação indisponível","Abre a gestão de membros no portal dos Dragões Marcoenses e inicia sessão.");
            importing=false;updateButtons();return;
        }
        portal.evaluateJavascript("window.__dmPageOperation='scan';"+assetJS("page_scan.js"),value->{
            try {
                JSONObject page=parseJsJson(value);
                JSONArray arr=page.optJSONArray("items");
                if(arr==null)arr=new JSONArray();
                String error=page.optString("error","");
                if(!error.isEmpty()){stopAll(error);return;}
                String key=keyOf(arr);
                if(all && key.equals(lastPageKey)){
                    stopAll("A página não mudou. Ajusta a paginação no site e importa novamente.");return;
                }
                lastPageKey=key;
                int before=readList().length(), processed=0;
                for(int i=0;i<arr.length();i++){
                    JSONObject obj=arr.optJSONObject(i);
                    if(obj==null)continue;
                    JSONObject result=new JSONObject(save(obj,true));
                    if(result.optBoolean("ok"))processed++;
                }
                int after=readList().length();
                newCount+=Math.max(0,after-before);
                pageCount++;
                int current=page.optInt("current",0),total=page.optInt("total",0);
                progressText.setText("Página "+(current>0?current:pageCount)+(total>0?" de "+total:"")+" · "+newCount+" novos · "+after+" locais");
                ui.evaluateJavascript("window.refreshAll && window.refreshAll()",null);
                if(all&&importing&&page.optBoolean("next")&&pageCount<200&&arr.length()>0){
                    portal.evaluateJavascript("window.__dmPageOperation='next';"+assetJS("page_scan.js"),clicked->{
                        try{
                            JSONObject next=parseJsJson(clicked);
                            if(next.optBoolean("clicked")){waitPageChanged(key,0);}
                            else stopAll("Navegação não reconhecida. Altera a página manualmente e volta a importar.");
                        }catch(Exception e){stopAll("Não foi possível avançar a paginação.");}
                    });
                }else if(all){
                    if(!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("baseline_initialized",false)){
                        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("baseline_initialized",true).apply();
                    }
                    stopAll("Importação terminada. "+after+" registos locais, "+newCount+" acrescentados.");
                    alert("Importação terminada","Foram lidas "+pageCount+" páginas.\n\nRegistos acrescentados: "+newCount+"\nTotal local: "+after+"\n\nOs registos iniciais ficam como base de referência. Só as entradas posteriores necessitam de validação.");
                }else {
                    new AlertDialog.Builder(this).setTitle("Página importada")
                        .setMessage("Registos identificados nesta página: "+arr.length()+"\nNovos registos: "+Math.max(0,after-before)+"\n\nPara importar todas as páginas, utiliza «Importar lista».")
                        .setPositiveButton("OK",null).show();
                }
            } catch(Exception e){stopAll("Não foi possível interpretar a listagem de membros.");}
        });
    }
    private void importDetails(){
        if(!onRightHost(true)){alert("Sem sessão","Abre a ficha de um sócio no portal dos Dragões Marcoenses.");return;}
        String script = "(function(){"
          + "var lines=(document.body.innerText||'').split(/\\n+/).map(s=>s.trim()).filter(Boolean);"
          + "function next(label){var i=lines.findIndex(s=>s.toLowerCase()===label.toLowerCase());return i>=0?(lines[i+1]||''):''}"
          + "function date(v){var m=(v||'').match(/(\\d\\d)\\/(\\d\\d)\\/(\\d{4})/);return m?(m[3]+'-'+m[2]+'-'+m[1]):''}"
          + "var txt=document.body.innerText||'';"
          + "var number=(txt.match(/S[oó]cio\\s*#\\s*(\\d+)/i)||[])[1]||'';"
          + "var name=next('Nome Completo'),fcp=next('Número de Sócio FC Porto');"
          + "var ident=next('Cartão de Cidadão'),birth=next('Data de Nascimento');"
          + "var join=next('Data de Registo'),email=next('Email');"
          + "if(!name||!number)return JSON.stringify({error:'Abre a ficha individual do sócio no portal antes de utilizar Ler ficha.'});"
          + "return JSON.stringify({name:name,number:number,email:email,fcp:(fcp.match(/\\d+/)||[])[0]||'',"
          + "ident:(ident.match(/[\\w-]+/)||[])[0]||'',birth:date(birth),join:date(join)})"
          + "})()";
        portal.evaluateJavascript(script,value->{
            try{
                JSONObject data=new JSONObject(String.valueOf(new JSONTokener(value).nextValue()));
                if(data.has("error")){alert("Ficha não encontrada",data.optString("error"));return;}
                JSONObject saved=new JSONObject(save(data,true));
                if(!saved.optBoolean("ok")){alert("Importação",saved.optString("error"));return;}
                ui.evaluateJavascript("window.refreshAll && window.refreshAll()",null);
                alert("Ficha importada","Os dados da ficha foram guardados localmente.\n\nVerifica o número FC Porto, a identificação e a data de admissão antes da validação.");
            }catch(Exception e){alert("Ficha não reconhecida","Esta página não apresenta os campos esperados. Podes editar os dados manualmente na área Sócios.");}
        });
    }
    private void startImportAll() {
        if(importing)return;
        if(!onRightHost(true)){alert("Inicia sessão","Abre primeiro a página de gestão de membros.");return;}
        new AlertDialog.Builder(this).setTitle("Importar a lista").setMessage("A app vai tentar percorrer as páginas de membros do portal. Apenas lê os dados visíveis e não modifica nenhum registo.\n\nSe a navegação do portal mudar, a importação pode parar antes do fim.")
          .setPositiveButton("Iniciar",(d,w)->{
              importing=true;pageCount=0;newCount=0;lastPageKey="";updateButtons();importPage(true);
          }).setNegativeButton("Cancelar",null).show();
    }
    private void stopAll(String message) {
        importing=false;progressText.setText(message);updateButtons();
        ui.evaluateJavascript("window.refreshAll && window.refreshAll()",null);
    }
    private String jsQuoted(String s){return JSONObject.quote(s==null?"":s);}
    private void prefillCurrent() {
        if(autoFillBusy)return;
        if(!onRightHost(false)){alert("Portal FC Porto","Abre a Comunidade FC Porto dentro da app.");return;}
        JSONObject member=findById(currentMemberId);
        if(member==null){alert("Sócio não encontrado","Escolhe um membro da base de sócios.");return;}
        String status=member.optString("status","novo");
        if(!("aprovado".equals(status)||"aguarda_fc".equals(status))){
            alert("Validação obrigatória","O membro precisa de aprovação antes de iniciar o preenchimento.");return;
        }
        if(member.optString("number").trim().isEmpty()){
            alert("Número em falta","Preenche o número da Casa na ficha antes de continuar.");return;
        }
        autoFillBusy=true;
        String script=assetJS("fc_autofill.js").replace("__DM_MEMBER__",member.toString());
        portal.evaluateJavascript(script,value->{
            autoFillBusy=false;
            try{
                JSONObject result=parseJsJson(value);
                String stage=result.optString("stage","manual");
                String message=result.optString("message","Confirma os dados no portal.");
                progressText.setText(message);
                if(("opened".equals(stage)||"tab".equals(stage)||"lookup".equals(stage))
                    && autoFillRequested&&autoFillSteps++<8){
                    handler.postDelayed(()->{
                        if(portalVisible&&!isSource)prefillCurrent();
                    },"lookup".equals(stage)?1650:650);
                }else if("ready".equals(stage)){
                    autoFillRequested=false;
                    new AlertDialog.Builder(this).setTitle("Dados preparados")
                        .setMessage(message+"\n\nVerifica a data de admissão e o número da Casa no formulário. A inscrição só é submetida quando carregares em «Adicionar» no portal.")
                        .setPositiveButton("Compreendido",null).show();
                }else if("missing".equals(stage)){
                    autoFillRequested=false;
                    alert("Dados em falta",message+"\n\nVolta à ficha do sócio, corrige e tenta novamente.");
                }else if("manual".equals(stage)){
                    autoFillRequested=false;
                    progressText.setText("Abre Adicionar Membros e carrega novamente em Preencher dados");
                }
            }catch(Exception e){
                autoFillRequested=false;
                progressText.setText("Não foi possível preencher este formulário. Usa o botão para tentar novamente.");
            }
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=24||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>2_000_000)throw new Exception("Ficheiro demasiado grande");out.write(b,0,n);}
            String contents=new String(out.toByteArray(),StandardCharsets.UTF_8).replace("\uFEFF","");
            int count=contents.trim().startsWith("[")||contents.trim().startsWith("{")?importJson(contents):importCsvText(contents);
            ui.evaluateJavascript("window.refreshAll && window.refreshAll()",null);
            alert("Importação concluída","Foram processados "+count+" registos.\n\nConfirma os novos dados antes de qualquer inscrição.");
        }catch(Exception e){alert("Erro na importação","Não foi possível ler o ficheiro CSV/JSON. "+e.getMessage());}
    }
    private int importJson(String raw)throws Exception {
        Object root=new JSONTokener(raw).nextValue();
        JSONArray arr=root instanceof JSONArray?(JSONArray)root:((JSONObject)root).optJSONArray("members");
        if(arr==null)throw new Exception("JSON sem lista de membros.");
        int n=0;for(int i=0;i<arr.length();i++){JSONObject src=arr.optJSONObject(i);if(src==null)continue;
            JSONObject r=new JSONObject();
            r.put("name",pick(src,"name","nome","Nome","nomeCompleto"));
            r.put("number",pick(src,"number","numeroSocio","numero_socio","Nº Sócio","nSocio"));
            r.put("fcp",pick(src,"fcp","numeroFcp","numero_fcp","socioFcPorto"));
            r.put("email",pick(src,"email","Email"));
            r.put("join",pick(src,"join","dataInscricao","data_admissao"));
            r.put("ident",pick(src,"ident","identificacaoFcp","identificacao"));
            if(new JSONObject(save(r,true)).optBoolean("ok"))n++;
        }return n;
    }
    private String pick(JSONObject j,String...keys){for(String k:keys){String x=j.optString(k,"").trim();if(!x.isEmpty())return x;}return "";}
    private List<String> parseCsvRow(String s,char sep){
        List<String> out=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;
        for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'){if(quoted&&i+1<s.length()&&s.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;}
          else if(c==sep&&!quoted){out.add(cell.toString().trim());cell.setLength(0);}else cell.append(c);}
        out.add(cell.toString().trim());return out;
    }
    private int importCsvText(String raw)throws Exception{
        String[] lines=raw.split("\\r?\\n");if(lines.length<2)throw new Exception("CSV sem linhas");
        char sep=lines[0].contains(";")?';':(lines[0].contains("\t")?'\t':',');
        List<String> heads=parseCsvRow(lines[0],sep);int count=0;
        for(int k=1;k<lines.length;k++){if(lines[k].trim().isEmpty())continue;
            List<String> vals=parseCsvRow(lines[k],sep);JSONObject obj=new JSONObject();
            for(int i=0;i<heads.size()&&i<vals.size();i++){
                String h=heads.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-záéíóúàãõç0-9]",""),v=vals.get(i);
                if(h.contains("email"))obj.put("email",v);
                else if(h.contains("nome"))obj.put("name",v);
                else if(h.contains("fcp")||h.contains("fcporto"))obj.put("fcp",v);
                else if(h.contains("socio")||h.contains("sócio")||h.contains("membro"))obj.put("number",v);
                else if(h.contains("admiss")||h.contains("inscri"))obj.put("join",v);
            }
            if(new JSONObject(save(obj,true)).optBoolean("ok"))count++;
        }return count;
    }
    @Override protected void onDestroy() {
        importing=false;
        if(portal!=null)portal.destroy();
        if(ui!=null)ui.destroy();
        super.onDestroy();
    }
}