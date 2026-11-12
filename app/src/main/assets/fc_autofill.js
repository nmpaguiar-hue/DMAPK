(function(member){
"use strict";
function norm(s){return String(s||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase().replace(/\s+/g," ").trim();}
function visible(el){if(!el)return false;var cs=getComputedStyle(el),r=el.getBoundingClientRect();return r.width>1&&r.height>1&&cs.visibility!=="hidden"&&cs.display!=="none"&&!el.disabled;}
function controls(){return [].slice.call(document.querySelectorAll("button,[role=button],[role=tab]")).filter(visible);}
function words(el){return norm([el.innerText,el.getAttribute("aria-label"),el.getAttribute("title")].filter(Boolean).join(" "));}
function result(stage,message,extra){return JSON.stringify(Object.assign({stage:stage,message:message},extra||{}));}
function fill(el,value){if(!el||!value)return false;var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,"value").set;setter.call(el,value);el.dispatchEvent(new Event("input",{bubbles:true}));el.dispatchEvent(new Event("change",{bubbles:true}));el.dispatchEvent(new Event("blur",{bubbles:true}));return true;}
function inputs(){return [].slice.call(document.querySelectorAll("input")).filter(function(x){return visible(x)&&x.type!=="hidden"&&x.type!=="checkbox"&&x.type!=="radio"});}
function label(input){
 var ss=[input.getAttribute("placeholder"),input.getAttribute("aria-label"),input.name,input.id].filter(Boolean);
 if(input.labels)for(var l of input.labels)ss.push(l.innerText);
 var parent=input.parentElement;
 for(var n=0;parent&&n<3;n++,parent=parent.parentElement){
   var ls=[].slice.call(parent.querySelectorAll("label")).filter(visible);
   for(var v of ls){if(v.contains(input)||parent.querySelectorAll("input").length===1)ss.push(v.innerText);}
   if(parent.querySelectorAll("input").length===1)ss.push((parent.innerText||"").slice(0,170));
 }
 return norm(ss.join(" "));
}
function findInput(re){var inp=inputs();return inp.find(function(i){return re.test(label(i));})||null;}
function nativeDate(iso,input){
 if(!iso)return "";
 if(input.type==="date")return /^\d{4}-\d\d-\d\d$/.test(iso)?iso:"";
 var p=iso.match(/^(\d{4})-(\d\d)-(\d\d)$/);return p?p[3]+"/"+p[2]+"/"+p[1]:iso;
}
function closestAction(input,rx){
 var list=controls().filter(function(b){return rx.test(words(b));});
 if(!list.length)return null;
 if(!input)return list[0];
 var rect=input.getBoundingClientRect();
 list.sort(function(a,b){return Math.abs(a.getBoundingClientRect().top-rect.top)-Math.abs(b.getBoundingClientRect().top-rect.top)});
 return list[0];
}
var isSocio=!!String(member.fcp||"").trim(), type=isSocio?"socio":"adepto";
var totalCount=inputs().length;
var communityDate=findInput(/data de admissao na comunidade/);
var communityNumber=findInput(/numero de (socio|adepto) na comunidade/);
if(communityDate&&communityNumber){
 var updated=0, missing=[];
 if(fill(communityNumber,String(member.number||"")))updated++;else missing.push("número da Casa");
 var d=nativeDate(String(member.join||""),communityDate);
 if(d&&fill(communityDate,d))updated++;else missing.push("data de admissão na Casa");
 return result("ready",updated+" campos preenchidos. Confirma os dados e clica em Adicionar no portal para submeter.",{count:updated,missing:missing,number:member.number,kind:type});
}
if(window.__dmSyncFlowMode!==type){
 window.__dmSyncFlowMode=type;window.__dmSyncLookupAttempted=false;
 var tab=controls().find(function(b){return type==="socio"?/^adicionar socio$/.test(words(b)):/^adicionar adepto$/.test(words(b));});
 if(tab){tab.click();return result("tab","Selecionado Adicionar "+(type==="socio"?"Sócio":"Adepto")+".",{kind:type});}
}
var numberInput=findInput(/(introduza.*numero de socio|numero de socio\s*\*?$)/);
var idInput=findInput(/(numero de identificacao|introduza.*identificacao)/);
var emailInput=findInput(/(introduza.*email|email\s*\*?$)/);
var firstStage=isSocio?(numberInput&&idInput):!!emailInput;
if(!firstStage){
 var open=controls().find(function(b){return /^(adicionar membros|adicionar membro|novo membro|novo socio|adicionar sócio|adicionar socio)$/.test(words(b));});
 if(open){open.click();window.__dmSyncFlowMode="";return result("opened","Abri o formulário de novo membro.");}
 return result("manual","Abre a listagem de Membros e o formulário Adicionar Sócio ou Adicionar Adepto. Depois carrega em Preencher na aplicação.",{inputs:totalCount});
}
if(isSocio&&(!member.fcp||!member.ident))return result("missing","Este sócio necessita do número FC Porto e do número de identificação na ficha antes da pesquisa.",{kind:type});
if(!isSocio&&!member.email)return result("missing","Este adepto necessita de um email válido antes da pesquisa.",{kind:type});
var lookupInput=isSocio?numberInput:emailInput,count=0;
if(isSocio){if(fill(numberInput,String(member.fcp)))count++;if(fill(idInput,String(member.ident)))count++;}
else if(fill(emailInput,String(member.email)))count++;
var add=closestAction(lookupInput,/^adicionar$/);
if(!add)return result("filled","Dados de identificação preenchidos. Carrega em Adicionar no formulário para pesquisar o membro no FC Porto.",{count:count});
if(window.__dmSyncLookupAttempted)return result("filled","Pesquisa já iniciada. Confirma se o portal devolveu a ficha e carrega novamente em Preencher.",{count:count});
window.__dmSyncLookupAttempted=true;
add.click();
return result("lookup","Pesquisa de "+(isSocio?"sócio":"adepto")+" iniciada no FC Porto. A inscrição final NÃO foi submetida.",{count:count});
})(__DM_MEMBER__)