echo "ANDROID_HOME=$ANDROID_HOME"
echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
which sdkmanager
ls -la /usr/lib/android-sdk 2>/dev/null
from pathlib import Path
import re
import shutil
import sys

# Execute na pasta do projeto, apontando para app/src/main/assets/index.html
target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("app/src/main/assets/index.html")
if not target.exists():
    raise SystemExit(f"Não encontrei: {target}\nPasse o caminho do index.html como argumento.")

src = target.read_text(encoding="utf-8")
original = src

# Botão de compartilhar em cada operação salva
old = '<button class="btn secondary" data-act="edit" data-id="${o.id}">Editar</button>'
new = old + '<button class="btn secondary" data-act="share" data-id="${o.id}">Compartilhar</button>'
if old in src and 'data-act="share"' not in src:
    src = src.replace(old, new, 1)

# Função de compartilhamento: menu nativo do Android; WhatsApp como alternativa.
anchor = "function askConfirm(msg,label){"
share_fn = r"""
async function shareOperation(o){
 if(!o)return;
 const lines=['CONTROLE DE CHEQUES',o.client?'Cliente: '+o.client:'', 'Data: '+(o.date?br(o.date):''),
  'Total dos cheques: '+money(o.face),'Descontos: '+money(o.discounts),'Valor líquido: '+money(o.net),'',
  'CHEQUES:',...o.cheques.map((c,i)=>(i+1)+'. '+(c.due?br(c.due):'Sem data')+' — '+money(c.amount)+(c.ref?' — '+c.ref:''))];
 const message=lines.filter(x=>x!==null&&x!==undefined).join('\n');
 try{
  if(navigator.share){await navigator.share({title:'Controle de Cheques',text:message});return;}
 }catch(e){if(e&&e.name==='AbortError')return;}
 const url='https://wa.me/?text='+encodeURIComponent(message);
 if(window.Android&&typeof Android.shareText==='function'){Android.shareText(message);}
 else{window.open(url,'_blank');}
}
"""
if anchor in src and "async function shareOperation(o)" not in src:
    src = src.replace(anchor, share_fn + "\n" + anchor, 1)

# Evento do botão compartilhar
old_handler = "if(b.dataset.act==='tg')$('d'+id).classList.toggle('hidden');else if(b.dataset.act==='dup')dup(o);else if(b.dataset.act==='edit')editSavedCheque(o);"
new_handler = "if(b.dataset.act==='tg')$('d'+id).classList.toggle('hidden');else if(b.dataset.act==='dup')dup(o);else if(b.dataset.act==='edit')editSavedCheque(o);else if(b.dataset.act==='share'){shareOperation(o);return;}"
if old_handler in src:
    src = src.replace(old_handler, new_handler, 1)

# Botão de apagar histórico pequeno e alinhado ao canto inferior direito
css_anchor = ".foot{text-align:center;"
css_rule = ".clear-history-wrap{display:flex;justify-content:flex-end;margin-top:14px}.btn.clear-history{width:auto;margin:0;padding:7px 10px;font-size:11px;border-radius:9px;opacity:.82}\n"
if css_anchor in src and ".btn.clear-history" not in src:
    src = src.replace(css_anchor, css_rule + css_anchor, 1)

old_clear = '<button class="btn danger" id="clearHistory">Apagar todo o histórico</button>'
new_clear = '<div class="clear-history-wrap"><button class="btn danger clear-history" id="clearHistory">Apagar todo o histórico</button></div>'
if old_clear in src:
    src = src.replace(old_clear, new_clear, 1)

if src == original:
    raise SystemExit("Nenhuma alteração aplicada: o arquivo não corresponde à versão esperada ou já está atualizado.")

backup = target.with_suffix(target.suffix + ".backup")
shutil.copy2(target, backup)
target.write_text(src, encoding="utf-8")
print("Atualização aplicada com sucesso.")
print(f"Arquivo atualizado: {target}")
print(f"Cópia de segurança: {backup}")
print("Incluído: compartilhar operação e botão compacto para apagar histórico.")
print("A edição de operações existente foi preservada.")
