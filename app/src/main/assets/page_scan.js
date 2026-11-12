(function(){
  function norm(s){return String(s||'').replace(/\s+/g,' ').trim()}
  function visible(e){if(!e||e.disabled)return false;var r=e.getBoundingClientRect();var st=getComputedStyle(e);return r.width>0&&r.height>0&&st.visibility!=='hidden'&&st.display!=='none'}
  function meta(){
    var body=norm(document.body.innerText||'');
    var m=body.match(/p[aá]gina\s*(\d+)\s*(?:de|\/)\s*(\d+)/i);
    return m?{current:+m[1],total:+m[2]}:{current:0,total:0};
  }
  function pagination(){
    var candidates=[].slice.call(document.querySelectorAll('button')).filter(visible);
    var explicit=candidates.find(function(b){
      var s=norm((b.getAttribute('aria-label')||'')+' '+(b.title||'')+' '+(b.getAttribute('data-testid')||'')).toLowerCase();
      return /(^|\s)(next|pr[oó]xima|seguinte|avan[cç]ar)(\s|$)/i.test(s)&&!/(last|ultima|última)/i.test(s)
    });
    if(explicit)return explicit;
    var icons=candidates.filter(function(b){
      var svg=b.querySelector('svg');
      if(!svg)return false;
      var data=norm(svg.outerHTML).toLowerCase();
      return /chevron-right|arrow-right|angle-right|next-page|lucide-chevron-right/.test(data)||/points=["']9[ ,]+18[ ,]+15[ ,]+12[ ,]+9[ ,]+6/.test(data)||/m9[, ]+18[, ]+6-6-6-6/.test(data)
    });
    if(icons.length)return icons[icons.length-1];
    var nodes=[].slice.call(document.querySelectorAll('body *')).filter(function(el){
      return el.children.length===0&&/p[aá]gina\s*\d+\s*(?:de|\/)\s*\d+/i.test(norm(el.innerText||''))
    });
    for(var node of nodes){
      var parent=node;
      for(var depth=0;depth<6&&parent;depth++,parent=parent.parentElement){
        var buttons=[].slice.call(parent.querySelectorAll('button')).filter(visible);
        if(buttons.length>=4&&buttons.length<=10){
          // Navegação típica: primeiro, anterior, seguinte, último
          var nav=buttons.slice(-4);
          if(visible(nav[2]))return nav[2];
        }
      }
    }
    var navs=[].slice.call(document.querySelectorAll('nav,[role=navigation],[class*=pagination],[class*=Pagination]'));
    for(var el of navs){var bs=[].slice.call(el.querySelectorAll('button')).filter(visible);if(bs.length>=4)return bs[bs.length-2]}
    return null;
  }
  function rows(){
    var tables=[].slice.call(document.querySelectorAll('table')).filter(visible);
    var table=tables.sort(function(a,b){return b.querySelectorAll('tbody tr').length-a.querySelectorAll('tbody tr').length})[0];
    if(!table)return [];
    var headers=[].slice.call(table.querySelectorAll('thead th')).map(function(c){return norm(c.innerText||'').toLowerCase()});
    function idx(rx,fallback){var v=headers.findIndex(function(h){return rx.test(h)});return v<0?fallback:v}
    var n=idx(/nome/,1), e=idx(/e-?mail/,2), s=idx(/n[oº°]?\s*s[oó]cio|n[uú]mero.*s[oó]cio/,3);
    return [].slice.call(table.querySelectorAll('tbody tr')).map(function(row){
      var c=[].slice.call(row.querySelectorAll('td')).map(function(el){return norm(el.innerText)});
      return {name:c[n]||'',email:c[e]||'',number:(c[s]||'').replace(/\s+/g,''),fcp:''}
    }).filter(function(m){return m.name&&/^\d+$/.test(m.number)})
  }
  var page=meta(),items=rows(),next=pagination(),canNext=page.total>0?page.current<page.total:!!next;
  if(window.__dmPageOperation==='next'){
    if(!canNext||!next)return JSON.stringify({clicked:false,reason:canNext?'Não encontrei o botão da página seguinte.':'Última página.'});
    next.click();
    return JSON.stringify({clicked:true,page:page.current,total:page.total});
  }
  return JSON.stringify({items:items,next:canNext,current:page.current,total:page.total,
    detectedButton:!!next,error:items.length?'':'Sem sócios nesta página. Abre a listagem de membros e confirma que está carregada.'});
})()