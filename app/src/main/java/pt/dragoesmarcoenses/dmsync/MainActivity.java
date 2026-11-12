package pt.dragoesmarcoenses.dmsync;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
public class MainActivity extends Activity {
 int baseline=712; TextView count, info; EditText value;
 int navy=Color.rgb(5,54,124);
 @Override public void onCreate(Bundle b){super.onCreate(b);
 baseline=getPreferences(0).getInt("baseline",712);
 LinearLayout root=new LinearLayout(this); root.setOrientation(1);root.setPadding(32,48,32,24);root.setBackgroundColor(Color.rgb(246,248,252));
 ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
 TextView title=new TextView(this);title.setText("DM SYNC");title.setTextColor(navy);title.setTextSize(30); title.setTypeface(null,1);root.addView(title);
 TextView subtitle=new TextView(this);subtitle.setText("Gestão assistida de inscrições");subtitle.setTextSize(16);root.addView(subtitle);
 count=new TextView(this);count.setTextSize(24);count.setTextColor(navy);root.addView(count);
 info=new TextView(this);info.setText("Inscrições no FC Porto requerem sempre validação expressa do administrador. Esta versão não efetua submissões automáticas."); info.setPadding(0,20,0,30);root.addView(info);
 value=new EditText(this);value.setInputType(2);value.setHint("Total atual de sócios");root.addView(value);
 Button compare=button("Comparar e preparar validação");root.addView(compare);
 compare.setOnClickListener(v->{String s=value.getText().toString().trim();if(s.isEmpty())return;int n;try{n=Integer.parseInt(s);}catch(Exception e){return;}int d=n-baseline;if(d>0){new AlertDialog.Builder(this).setTitle(d+" novos sócios detetados").setMessage("Total anterior: "+baseline+"\nTotal introduzido: "+n+"\n\nConfirme os novos registos no portal dos Dragões Marcoenses antes de os inscrever no FC Porto. Nenhum pedido será submetido automaticamente.").setPositiveButton("Confirmar novo total",(dialog,w)->{baseline=n;getPreferences(0).edit().putInt("baseline",n).apply();refresh();}).setNegativeButton("Cancelar",null).show();}else{new AlertDialog.Builder(this).setMessage(d==0?"Sem alteração no total.":"O total diminuiu. Verifique no portal antes de alterar a referência.").setPositiveButton("OK",null).show();}});
 Button source=button("Abrir gestão dos Dragões Marcoenses");root.addView(source);source.setOnClickListener(v->open("https://dragoesmarcoenses.pt/admin/members"));
 Button target=button("Abrir Comunidade FC Porto");root.addView(target);target.setOnClickListener(v->open("https://comunidade.fcporto.pt/DELEGACAO_371/settings"));
 TextView footer=new TextView(this);footer.setText("Versão 0.1.0 • Protótipo local. Sem recolha automática de dados, sem acesso a credenciais e sem submissões.");footer.setPadding(0,36,0,0);root.addView(footer);refresh();
 }
 Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
 void refresh(){count.setText("Referência atual: "+baseline+" sócios");}
 void open(String url){startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}
}