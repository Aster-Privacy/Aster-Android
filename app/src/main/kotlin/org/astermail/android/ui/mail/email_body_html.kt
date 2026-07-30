//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.mail

import org.astermail.android.mail.body_starts_with

internal fun build_email_html(
    body: String,
    is_dark: Boolean,
    fg_hex: String,
    link_hex: String,
    forwarded_label: String,
    image_failed_label: String,
    force_dark_emails: Boolean,
    dyslexia_font: Boolean,
    translate_mode: String,
): String {
    val is_html_body = body_starts_with(body, "<")
    val has_table = is_html_body && body.contains(Regex("<table", RegexOption.IGNORE_CASE))
    val has_newsletter_layout = has_table && (
        body.contains(Regex("style\\s*=\\s*[\"'][^\"']*width\\s*:\\s*[456789]\\d{2}px", RegexOption.IGNORE_CASE)) ||
        body.contains(Regex("<table[^>]*(?:width|bgcolor|background)\\s*=", RegexOption.IGNORE_CASE)) ||
        (body.split(Regex("<table\\b", RegexOption.IGNORE_CASE)).size - 1) > 2
    )
    val render_body = if (has_newsletter_layout) {
        body
            .replace(Regex("""(?<!\(\s{0,8})\bmin-width\s*:\s*([1-9]\d{2,3})px""", RegexOption.IGNORE_CASE), "min-width:$1px;min-width:min($1px,100%)")
            .replace(Regex("""(?<!\(\s{0,8})(?<![a-z-])width\s*:\s*[4-9]\d{2,3}px""", RegexOption.IGNORE_CASE), "width:100%")
            .replace(Regex("""\bwidth\s*=\s*["']?[4-9]\d{2,3}["']?""", RegexOption.IGNORE_CASE), "width=\"100%\"")
    } else {
        body
    }
    val declares_light = body.contains(Regex("color-scheme\\s*:\\s*light\\s+only", RegexOption.IGNORE_CASE))
    val declares_light_bg = is_html_body && body.contains(
        Regex(
            "(?:background(?:-color)?\\s*:\\s*(?:#fff(?:fff)?|white|rgb\\(\\s*25[0-5])|bgcolor\\s*=\\s*[\"']?(?:#fff(?:fff)?|white))",
            RegexOption.IGNORE_CASE,
        ),
    )
    val designed_light = declares_light || declares_light_bg || has_newsletter_layout
    val white_page = is_dark && is_html_body && designed_light && !force_dark_emails
    val simple_dark = is_dark && is_html_body && !white_page
    val force_light = is_html_body && !simple_dark
    val chip_dark = is_dark && !white_page

    val sys_font = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif"
    val body_style = when {
        is_html_body && !has_newsletter_layout ->
            "background-color:transparent;color:${if (simple_dark) "#e5e5e5" else "#111827"};margin:0;padding:4px 16px 0 16px;font-family:$sys_font;font-size:14px;line-height:1.6;word-wrap:break-word"
        is_html_body ->
            "background-color:transparent;margin:0;padding:4px 16px 0 16px"
        else ->
            "background-color:transparent;color:$fg_hex;margin:0;padding:4px 16px 6px 16px;font-family:$sys_font;font-size:16px;line-height:1.55;word-wrap:break-word"
    }

    val dark_css = when {
        simple_dark -> """
html{color-scheme:dark}
html,body{background-color:transparent!important;color:#e8e8e8!important}
"""
        white_page -> """
html,body{background-color:#ffffff!important}
"""
        else -> ""
    }

    val dyslexia_css = if (dyslexia_font) {
        "@font-face{font-family:'AsterDyslexic';font-style:normal;font-weight:400;font-display:swap;src:url('$EMAIL_FONT_PATH') format('opentype')}" +
            "body,body *:not(code):not(pre):not(kbd):not(samp):not(font){font-family:'AsterDyslexic',$sys_font!important}"
    } else {
        ""
    }

    val table_css = if (has_newsletter_layout) {
        "#m{max-width:100%!important;overflow-x:hidden!important;box-sizing:border-box!important}#m table{max-width:100%!important;box-sizing:border-box!important}#m img{max-width:100%!important;height:auto!important}#m div,#m p,#m blockquote,#m section,#m article{box-sizing:border-box!important;max-width:100%!important}td,th{box-sizing:border-box!important;max-width:100%!important}#m,#m *{word-break:normal!important;overflow-wrap:break-word!important;word-wrap:break-word!important}#m a{overflow-wrap:anywhere!important}"
    } else {
        "table{max-width:100%!important;border-collapse:collapse;width:100%!important}td,th{overflow-wrap:break-word}"
    }
    val viewport_meta =
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes\">"
    val color_scheme_meta = if (force_light) "<meta name=\"color-scheme\" content=\"light only\">" else ""

    val bq_border = if (simple_dark) "#4b5563" else "#dadce0"
    val bq_color = if (simple_dark) "#9ca3af" else "#5f6368"
    val bq_border2 = if (simple_dark) "#444" else "#dadce0"
    val bq_border3 = if (simple_dark) "#555" else "#c4c7cc"
    val detail_border = if (simple_dark) "#374151" else "#e5e7eb"
    val detail_color = if (simple_dark) "#9ca3af" else "#6b7280"

    val csp_nonce = run {
        val nonce_bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(nonce_bytes)
        android.util.Base64.encodeToString(nonce_bytes, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }
    val script_src = if (translate_mode != "off") {
        "script-src 'nonce-$csp_nonce' 'wasm-unsafe-eval' https://mail-content.invalid; worker-src https://mail-content.invalid; connect-src https://mail-content.invalid"
    } else {
        "script-src 'nonce-$csp_nonce'"
    }
    val csp_meta = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src https://app.astermail.org data:; style-src 'unsafe-inline'; font-src https://app.astermail.org https://mail-content.invalid data:; $script_src; base-uri 'none'; form-action 'none'; frame-src 'none'; object-src 'none'\">"

    return """<!DOCTYPE html><html${if (has_newsletter_layout) " data-nl=\"1\"" else ""}${if (white_page) " data-white=\"1\"" else ""}${if (simple_dark) " data-dark=\"1\"" else ""}${if (force_dark_emails) " data-dark-force=\"1\"" else ""}${if (is_html_body && !simple_dark) " style=\"background-color:transparent\"" else ""}><head>
$csp_meta
<meta charset="utf-8">
$viewport_meta
$color_scheme_meta
<style>
html{height:auto!important;min-height:0!important;background-color:transparent;-webkit-text-size-adjust:100%;text-size-adjust:100%}
body{height:auto!important;min-height:0!important;margin:0;overflow-x:hidden;overflow-y:hidden}
*{box-sizing:border-box}
img{max-width:100%!important;height:auto!important}
img:not([data-blocked='true']):not(.blocked-image){cursor:zoom-in;-webkit-tap-highlight-color:rgba(128,128,128,0.22)}
a img{cursor:pointer}
a{color:$link_hex;text-decoration:underline}
pre,code{overflow-x:auto;max-width:100%}
.blocked-image{display:inline-block;padding:4px 8px;border-radius:4px;font-size:12px;background-color:${if (simple_dark) "#1f1f1f" else "#f3f4f6"};color:#9ca3af${if (simple_dark) "!important" else ""};border:1px dashed ${if (simple_dark) "#374151" else "#e5e7eb"}}
$table_css
.aster_quote,.gmail_quote,.protonmail_quote,.yahoo_quoted,.moz-cite-prefix{display:none}
.aster-quoted-content .aster_quote,.aster-quoted-content .gmail_quote,.aster-quoted-content .protonmail_quote,.aster-quoted-content .yahoo_quoted,.aster-quoted-content .moz-cite-prefix,.aster-forwarded-content .aster_quote,.aster-forwarded-content .gmail_quote,.aster-forwarded-content .protonmail_quote{display:block;margin:0;padding:0}
blockquote{margin:8px 0;padding-left:12px;border-left:2px solid $bq_border;color:$bq_color}
.aster-quoted-wrapper{margin-top:2px}
.aster-quote-toggle{display:inline-flex;align-items:center;justify-content:center;min-height:26px;padding:0 14px;margin:0;border-radius:13px;border:none;outline:none;background:${if (chip_dark) "rgba(255,255,255,0.12)" else "rgba(0,0,0,0.08)"};color:${if (chip_dark) "rgba(255,255,255,0.65)" else "rgba(0,0,0,0.55)"};cursor:pointer;font-size:15px;letter-spacing:2px;line-height:1;vertical-align:middle;user-select:none;-webkit-tap-highlight-color:transparent}
.aster-quote-toggle:active,.aster-quote-toggle.aster-quote-expanded{background:${if (chip_dark) "rgba(255,255,255,0.2)" else "rgba(0,0,0,0.16)"}}
.aster-quoted-content{margin-top:8px;color:$bq_color;font-size:15px;line-height:21px}
.aster-quoted-content .aster_quote_attr,.aster-quoted-content .gmail_attr{color:$bq_color;font-size:12px;margin-bottom:4px}
.aster-quoted-content blockquote{margin:0;padding:0 0 0 12px;border-left:2px solid $bq_border2;color:$bq_color}
.aster-quoted-content blockquote blockquote{border-left-color:$bq_border3}
details.aster-forwarded-collapse{margin-top:12px;border-top:1px solid $detail_border;padding-top:4px}
details.aster-forwarded-collapse>summary{cursor:pointer;color:$detail_color;font-size:13px;padding:6px 0;user-select:none;list-style:none}
details.aster-forwarded-collapse>summary::-webkit-details-marker{display:none}
details.aster-forwarded-collapse>summary::before{content:'\25B6';display:inline-block;font-size:8px;margin-right:6px;transition:transform 0.15s ease}
details[open].aster-forwarded-collapse>summary::before{transform:rotate(90deg)}
details.aster-forwarded-collapse>.aster-forwarded-content{padding-top:8px}
$dyslexia_css
$dark_css
</style>
</head><body style="$body_style"><div id="m">$render_body</div>
<script nonce="$csp_nonce">
(function(){
  var body=document.body;
  if(!body)return;
  var ad_root=document.documentElement;
  var ad_seed=document.getElementById('m');
  if(ad_seed){
try{
  var seed_cur=window.getComputedStyle(ad_seed).backgroundColor;
  if(!seed_cur||seed_cur==='transparent'||seed_cur==='rgba(0, 0, 0, 0)'){
    var seed_first=ad_seed.firstElementChild;
    if(seed_first){
      var seed_bg=seed_first.getAttribute('bgcolor')||seed_first.style.backgroundColor;
      if(!seed_bg||seed_bg==='transparent'||seed_bg==='rgba(0, 0, 0, 0)'){
        seed_bg=window.getComputedStyle(seed_first).backgroundColor;
      }
      if(seed_bg&&seed_bg!=='transparent'&&seed_bg!=='rgba(0, 0, 0, 0)'){
        ad_seed.style.backgroundColor=seed_bg;
      }
    }
  }
  if(ad_root.getAttribute('data-nl')){
    var seed_after=window.getComputedStyle(ad_seed).backgroundColor;
    if(!seed_after||seed_after==='transparent'||seed_after==='rgba(0, 0, 0, 0)'){
      ad_seed.style.backgroundColor='#ffffff';
    }
  }
  var seed_final=window.getComputedStyle(ad_seed).backgroundColor;
  var seed_match=/rgba?\(([^)]+)\)/.exec(seed_final||'');
  if(seed_match){
    var seed_parts=seed_match[1].split(',');
    var seed_alpha=seed_parts.length>3?parseFloat(seed_parts[3]):1;
    var seed_r=parseInt(seed_parts[0],10),seed_g=parseInt(seed_parts[1],10),seed_b=parseInt(seed_parts[2],10);
    if(!isNaN(seed_r)&&!isNaN(seed_g)&&!isNaN(seed_b)&&seed_alpha>0.5){
      var seed_lum=(0.2126*seed_r+0.7152*seed_g+0.0722*seed_b)/255;
      if(seed_lum>0.55){
        ad_root.removeAttribute('data-dark');
        ad_root.setAttribute('data-white','1');
        body.style.setProperty('color','#111827','important');
        ad_seed.style.setProperty('color','#111827','important');
      }
    }
  }
}catch(_){}
  }
  if(ad_root.getAttribute('data-dark')){
var ad_force=!!ad_root.getAttribute('data-dark-force');
var ad_parse=function(c){
  if(!c)return null;
  var m=/rgba?\(([^)]+)\)/.exec(c);
  if(!m)return null;
  var p=m[1].split(',');
  var a=p.length>3?parseFloat(p[3]):1;
  if(!(a>0.08))return null;
  var r=parseInt(p[0],10),g=parseInt(p[1],10),b=parseInt(p[2],10);
  if(isNaN(r)||isNaN(g)||isNaN(b))return null;
  return {r:r,g:g,b:b,a:a,l:(0.2126*r+0.7152*g+0.0722*b)/255};
};
var ad_darken=function(c){
  var mx=Math.max(c.r,c.g,c.b)/255,mn=Math.min(c.r,c.g,c.b)/255;
  var l=(mx+mn)/2,d=mx-mn,h=0,sat=0;
  if(d>0){
    sat=l>0.5?d/(2-mx-mn):d/(mx+mn);
    var rr=c.r/255,gg=c.g/255,bb=c.b/255;
    if(mx===rr)h=((gg-bb)/d)%6;else if(mx===gg)h=(bb-rr)/d+2;else h=(rr-gg)/d+4;
    h=h*60;if(h<0)h+=360;
  }
  var nl=Math.min(0.26,Math.max(0.07,1-l));
  sat=Math.min(sat,0.45);
  var cc=(1-Math.abs(2*nl-1))*sat,x=cc*(1-Math.abs((h/60)%2-1)),mm=nl-cc/2;
  var t=[0,0,0];
  if(h<60)t=[cc,x,0];else if(h<120)t=[x,cc,0];else if(h<180)t=[0,cc,x];
  else if(h<240)t=[0,x,cc];else if(h<300)t=[x,0,cc];else t=[cc,0,x];
  return 'rgb('+Math.round((t[0]+mm)*255)+','+Math.round((t[1]+mm)*255)+','+Math.round((t[2]+mm)*255)+')';
};
var ad_lighten=function(c){
  var mx=Math.max(c.r,c.g,c.b)/255,mn=Math.min(c.r,c.g,c.b)/255;
  var l=(mx+mn)/2,d=mx-mn,h=0,sat=0;
  if(d>0){
    sat=l>0.5?d/(2-mx-mn):d/(mx+mn);
    var rr=c.r/255,gg=c.g/255,bb=c.b/255;
    if(mx===rr)h=((gg-bb)/d)%6;else if(mx===gg)h=(bb-rr)/d+2;else h=(rr-gg)/d+4;
    h=h*60;if(h<0)h+=360;
  }
  if(sat<0.12)return '#e8e8e8';
  var nl=Math.max(0.72,l);
  sat=Math.min(sat,0.62);
  var cc=(1-Math.abs(2*nl-1))*sat,x=cc*(1-Math.abs((h/60)%2-1)),mm=nl-cc/2;
  var t=[0,0,0];
  if(h<60)t=[cc,x,0];else if(h<120)t=[x,cc,0];else if(h<180)t=[0,cc,x];
  else if(h<240)t=[0,x,cc];else if(h<300)t=[x,0,cc];else t=[cc,0,x];
  return 'rgb('+Math.round((t[0]+mm)*255)+','+Math.round((t[1]+mm)*255)+','+Math.round((t[2]+mm)*255)+')';
};
var ad_walk=function(el,lit){
  var cs=window.getComputedStyle(el);
  var bg=ad_parse(cs.backgroundColor);
  var has_bg_image=cs.backgroundImage&&cs.backgroundImage!=='none';
  var next_lit=lit;
  if(bg&&bg.l>0.5){
    if(ad_force){el.style.setProperty('background-color',ad_darken(bg),'important');}
    else{next_lit=true;}
  }else if(has_bg_image&&!lit){
    next_lit=true;
  }
  if(!next_lit){
    var fg=ad_parse(cs.color);
    if(fg&&fg.l<0.62){
      var lifted=ad_lighten(fg);
      if(el.tagName==='A'&&lifted==='#e8e8e8')lifted='#7cb0ff';
      el.style.setProperty('color',lifted,'important');
    }
    var sides=['Top','Right','Bottom','Left'];
    for(var s=0;s<sides.length;s++){
      if(parseFloat(cs['border'+sides[s]+'Width'])>0){
        var bc=ad_parse(cs['border'+sides[s]+'Color']);
        if(bc&&bc.l<0.32){
          el.style.setProperty('border-'+sides[s].toLowerCase()+'-color','#4a4a4a','important');
        }
      }
    }
  }
  var kids=el.children;
  for(var i=0;i<kids.length;i++){
    var t=kids[i].tagName;
    if(t==='IMG'||t==='PICTURE'||t==='VIDEO'||t==='CANVAS'||t==='IFRAME')continue;
    ad_walk(kids[i],next_lit);
  }
};
var ad_host=document.getElementById('m');
if(ad_host){try{ad_walk(ad_host,false);}catch(_){}}
  }
  var is_nl=document.documentElement.getAttribute('data-nl');
  if(is_nl){
var m_nr=document.getElementById('m');
if(m_nr){
  var nw_limit=m_nr.clientWidth||document.documentElement.clientWidth||0;
  var nw_els=m_nr.querySelectorAll('td,th,p,h1,h2,h3,h4,h5,h6,div,span');
  for(var k=0;k<nw_els.length;k++){
    var cs_nw=window.getComputedStyle(nw_els[k]);
    if(cs_nw.whiteSpace!=='nowrap'&&cs_nw.whiteSpace!=='pre')continue;
    if(nw_limit>0&&nw_els[k].scrollWidth<=nw_limit)continue;
    nw_els[k].style.whiteSpace='normal';
    nw_els[k].style.overflowWrap='break-word';
  }
  var tw=document.createTreeWalker(m_nr,NodeFilter.SHOW_TEXT,null),tn,long_hosts=[];
  while((tn=tw.nextNode())){
    var tv=tn.nodeValue||'';
    if(tv.length<28)continue;
    var longest=0,run=0;
    for(var c=0;c<tv.length;c++){
      if(tv.charCodeAt(c)<=32){run=0;}else{run++;if(run>longest)longest=run;}
    }
    if(longest>=28&&tn.parentElement)long_hosts.push(tn.parentElement);
  }
  for(var q=0;q<long_hosts.length;q++){
    long_hosts[q].style.setProperty('overflow-wrap','anywhere','important');
  }
  var fh_els=m_nr.querySelectorAll('td,th,div,p,section,article,span,a,h1,h2,h3,h4,h5,h6');
  for(var f=0;f<fh_els.length;f++){
    var fe=fh_els[f];
    if(fe.id==='m')continue;
    var fs=window.getComputedStyle(fe);
    if(fs.position==='absolute'||fs.position==='fixed')continue;
    var fh_declared=(fe.style&&fe.style.height)||fe.getAttribute('height')||'';
    var fh_capped=fs.maxHeight&&fs.maxHeight!=='none';
    var fh_client=fe.clientHeight||0;
    if(fh_client>0&&fe.scrollHeight>fh_client+1&&(fh_declared||fh_capped)){
      fe.style.setProperty('height','auto','important');
      fe.style.setProperty('max-height','none','important');
      fe.style.setProperty('min-height','0','important');
    }
    var fh_font=parseFloat(fs.fontSize)||0;
    var fh_line=parseFloat(fs.lineHeight);
    if(fh_font>0&&fh_line>0&&fh_line<fh_font*0.9){
      var fh_text='';
      for(var fc=0;fc<fe.childNodes.length;fc++){
        var fn=fe.childNodes[fc];
        if(fn.nodeType===3&&fn.nodeValue)fh_text+=fn.nodeValue;
      }
      if(fh_text.trim().length>0)fe.style.setProperty('line-height','normal','important');
    }
  }
}
  }
  (function(){
var bh=document.getElementById('m');
if(!bh)return;
var bl=bh.clientWidth||document.documentElement.clientWidth||0;
var bc=bh.querySelectorAll('a,span,td,th,div,strong,b,em,small,p');
for(var bi=0;bi<bc.length;bi++){
  var be=bc[bi];
  if(be.children.length>0)continue;
  var bt=(be.textContent||'').trim();
  if(!bt||bt.length>24)continue;
  var bs=window.getComputedStyle(be);
  var br=parseFloat(bs.borderTopLeftRadius)||0;
  var bg=bs.backgroundColor||'';
  var bb=bg.length>0&&bg!=='transparent'&&bg.indexOf('rgba(0, 0, 0, 0)')<0;
  var bp=(parseFloat(bs.paddingLeft)||0)>=6;
  var bd=bs.borderTopStyle&&bs.borderTopStyle!=='none';
  var pill=(br>=6&&(bb||bd))||(bb&&bp);
  if(!pill)continue;
  if(bl>0&&be.scrollWidth>bl*0.9)continue;
  be.style.setProperty('white-space','nowrap','important');
  be.style.setProperty('overflow-wrap','normal','important');
  be.style.setProperty('word-break','keep-all','important');
}
  })();
  function measure_h(){
var m=document.getElementById('m');
if(!m)return 0;
var scroll_y=window.pageYOffset||document.documentElement.scrollTop||0;
var top_offset=Math.max(0,m.getBoundingClientRect().top+scroll_y);
if(document.documentElement.getAttribute('data-nl'))return Math.ceil(aster_content_height()+top_offset);
var pb=parseFloat(window.getComputedStyle(document.body).paddingBottom)||0;
var raw=Math.ceil(m.getBoundingClientRect().bottom+scroll_y+pb)+4;
var trimmed=aster_content_height();
if(trimmed>0&&trimmed+top_offset<raw)return Math.ceil(trimmed+top_offset+pb)+4;
return raw;
  }
  function report_h(){if(document.getElementById('m'))console.log('ASTER_HEIGHT:'+measure_h())}
  function report_h_exact(){if(document.getElementById('m'))console.log('ASTER_HEIGHT_EXACT:'+measure_h())}
  function schedule_h(){
report_h_exact();
requestAnimationFrame(function(){report_h_exact();requestAnimationFrame(report_h_exact)});
setTimeout(report_h_exact,120);
setTimeout(report_h_exact,400);
setTimeout(report_h_exact,1200);
  }
  function watch_media(root){
try{
  var im=root.querySelectorAll('img');
  for(var i=0;i<im.length;i++){
    var g=im[i];
    if(g.complete&&g.naturalWidth>0)continue;
    g.addEventListener('load',schedule_h,{once:true});
    g.addEventListener('error',schedule_h,{once:true});
  }
}catch(_){}
  }
  function aster_content_height(){
var m=document.getElementById('m');
if(!m)return 0;
var full=m.offsetHeight;
if(full<=0)return full;
var m_top=m.getBoundingClientRect().top;
var wall=window.getComputedStyle(m).backgroundColor;
var content=0;
function consider(bottom){var v=bottom-m_top;if(v>content)content=v;}
try{
  var tw=document.createTreeWalker(m,NodeFilter.SHOW_TEXT,null);
  var rng=document.createRange();
  while(tw.nextNode()){
    var tn=tw.currentNode;
    if(!tn.nodeValue||tn.nodeValue.trim().length===0)continue;
    rng.selectNodeContents(tn);
    var rects=rng.getClientRects();
    for(var k=0;k<rects.length;k++){
      var rr=rects[k];
      if(rr.width<=0||rr.height<=0)continue;
      consider(rr.bottom);
    }
  }
}catch(_){}
var all=m.querySelectorAll('*');
for(var i=0;i<all.length;i++){
  var e=all[i];
  var r=e.getBoundingClientRect();
  if(r.height<=0||r.width<=0)continue;
  var tag=e.tagName;
  var vis=false;
  if(tag==='IMG'){if(r.height>=4&&r.width>=4&&e.naturalWidth>1)vis=true;}
  else if(tag==='HR'||tag==='VIDEO'||tag==='CANVAS'||tag==='SVG'||tag==='IFRAME')vis=true;
  if(!vis){
    var cs=window.getComputedStyle(e);
    if(r.height<full*0.9){
      var bg=cs.backgroundColor;
      if(bg&&bg!=='transparent'&&bg!=='rgba(0, 0, 0, 0)'&&bg!==wall)vis=true;
      if(!vis&&cs.backgroundImage&&cs.backgroundImage!=='none')vis=true;
      if(!vis&&parseFloat(cs.borderBottomWidth||0)>0)vis=true;
    }
  }
  if(vis){consider(r.bottom);continue;}
  for(var c=0;c<e.childNodes.length;c++){
    var n=e.childNodes[c];
    if(n.nodeType===3&&n.nodeValue&&n.nodeValue.trim().length>0){consider(r.bottom);break;}
  }
}
try{
  var caps=m.querySelectorAll('.aster-quote-toggle,details.aster-forwarded-collapse>summary');
  for(var q=0;q<caps.length;q++){
    var cr=caps[q].getBoundingClientRect();
    if(cr.height>0&&cr.width>0)consider(cr.bottom);
  }
}catch(_){}
if(content<=0)return full;
var trailing=full-content;
if(trailing<=48)return full;
return Math.min(full,Math.ceil(content)+24);
  }
  function linkify_text_nodes(root){
var url_re=/((?:https?:\/\/|www\.)[^\s<>"']+[^\s<>"'.,;:!?)\]}])|([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})/g;
var skip_tags={A:1,SCRIPT:1,STYLE:1,TEXTAREA:1,CODE:1,PRE:1,BUTTON:1};
var to_process=[];
var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode:function(n){
  var p=n.parentNode;while(p&&p!==root){if(p.nodeType===1&&skip_tags[p.tagName])return NodeFilter.FILTER_REJECT;p=p.parentNode}
  return n.nodeValue&&url_re.test(n.nodeValue)?NodeFilter.FILTER_ACCEPT:NodeFilter.FILTER_REJECT;
}});
while(w.nextNode())to_process.push(w.currentNode);
to_process.forEach(function(n){
  var s=n.nodeValue;url_re.lastIndex=0;
  var frag=document.createDocumentFragment();var last=0;var m;
  while((m=url_re.exec(s))!==null){
    if(m.index>last)frag.appendChild(document.createTextNode(s.substring(last,m.index)));
    var a=document.createElement('a');
    if(m[1]){var href=m[1];if(/^www\./i.test(href))href='http://'+href;a.href=href;a.textContent=m[1];}
    else{a.href='mailto:'+m[2];a.textContent=m[2];}
    frag.appendChild(a);last=m.index+m[0].length;
  }
  if(last<s.length)frag.appendChild(document.createTextNode(s.substring(last)));
  n.parentNode.replaceChild(frag,n);
});
  }
  try{linkify_text_nodes(body)}catch(_){}
  function aster_lum(c){
if(!c)return null;
var q=/rgba?\(([^)]+)\)/.exec(c);
if(!q)return null;
var p=q[1].split(',');
var a=p.length>3?parseFloat(p[3]):1;
if(a<0.55)return null;
var r=parseInt(p[0],10),g=parseInt(p[1],10),b=parseInt(p[2],10);
if(isNaN(r)||isNaN(g)||isNaN(b))return null;
function ch(v){v=v/255;return v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4)}
return 0.2126*ch(r)+0.7152*ch(g)+0.0722*ch(b);
  }
  function aster_ratio(a,b){var hi=Math.max(a,b),lo=Math.min(a,b);return (hi+0.05)/(lo+0.05)}
  function aster_surface_lum(el){
var n=el;
while(n&&n.nodeType===1){
  var cs=window.getComputedStyle(n);
  if(cs.backgroundImage&&cs.backgroundImage!=='none')return null;
  var l=aster_lum(cs.backgroundColor);
  if(l!==null)return l;
  n=n.parentElement;
}
return null;
  }
  function aster_contrast_repair(){
var m=document.getElementById('m');
if(!m)return;
var els=m.querySelectorAll('*');
for(var i=0;i<els.length;i++){
  var e=els[i];
  if(e.tagName==='SCRIPT'||e.tagName==='STYLE')continue;
  var own='';
  for(var c=0;c<e.childNodes.length;c++){
    var n=e.childNodes[c];
    if(n.nodeType===3&&n.nodeValue)own+=n.nodeValue;
  }
  if(own.replace(/\s+/g,'').length<1)continue;
  var r=e.getBoundingClientRect();
  if(r.width<=0||r.height<=0)continue;
  var bl=aster_surface_lum(e);
  if(bl===null)continue;
  var cs2=window.getComputedStyle(e);
  var tl=aster_lum(cs2.color);
  if(tl===null)continue;
  if(aster_ratio(tl,bl)>=3.2)continue;
  var dark_text=aster_lum('rgb(17,24,39)');
  var light_text=aster_lum('rgb(232,232,232)');
  var pick=aster_ratio(dark_text,bl)>=aster_ratio(light_text,bl)?'#111827':'#e8e8e8';
  e.style.setProperty('color',pick,'important');
}
  }
  window.__aster_contrast=aster_contrast_repair;
  try{aster_contrast_repair()}catch(_){}
  function aster_pad_loose_blocks(){
var m=document.getElementById('m');
if(!m)return;
if(!document.documentElement.getAttribute('data-nl'))return;
for(var i=0;i<m.children.length;i++){
  var e=m.children[i];
  if(e.getAttribute('data-aster-pad')==='1')continue;
  if(e.tagName==='TABLE'||e.querySelector('table'))continue;
  var txt=(e.textContent||'').replace(/\s+/g,'');
  if(txt.length<1&&!e.querySelector('img'))continue;
  var cs=window.getComputedStyle(e);
  if((parseFloat(cs.marginLeft)||0)<0)e.style.setProperty('margin-left','0','important');
  if((parseFloat(cs.marginRight)||0)<0)e.style.setProperty('margin-right','0','important');
  e.setAttribute('data-aster-pad','1');
}
  }
  window.__aster_pad=aster_pad_loose_blocks;
  try{aster_pad_loose_blocks()}catch(_){}
  function aster_relax_fixed_heights(){
var m=document.getElementById('m');
if(!m)return;
var els=m.querySelectorAll('td,th,tr,div,p,span,a,table');
for(var i=0;i<els.length;i++){
  var e=els[i];
  if(e.getAttribute('data-aster-h')==='1')continue;
  var txt=(e.textContent||'').replace(/\s+/g,'');
  if(txt.length<1){e.setAttribute('data-aster-h','1');continue;}
  var cs=window.getComputedStyle(e);
  if(cs.position==='absolute'||cs.position==='fixed'){e.setAttribute('data-aster-h','1');continue;}
  var box=parseFloat(cs.height)||0;
  if(box<=0){e.setAttribute('data-aster-h','1');continue;}
  if(e.scrollHeight>box+2){
    e.style.setProperty('height','auto','important');
    e.style.setProperty('min-height',Math.round(box)+'px','important');
    if(cs.overflowY==='hidden')e.style.setProperty('overflow-y','visible','important');
  }
  e.setAttribute('data-aster-h','1');
}
  }
  window.__aster_relax=aster_relax_fixed_heights;
  try{aster_relax_fixed_heights()}catch(_){}
  function aster_mark_failed_images(){
var m=document.getElementById('m');
if(!m)return;
var imgs=m.querySelectorAll('img');
for(var i=0;i<imgs.length;i++){
  var im=imgs[i];
  if(!im.complete)continue;
  if(im.getAttribute('data-aster-failed')==='1')continue;
  var placeholder=im.naturalWidth<=1&&im.naturalHeight<=1;
  var broken=im.naturalWidth===0&&im.naturalHeight===0;
  if(!placeholder&&!broken)continue;
  var r=im.getBoundingClientRect();
  if(r.width<=1&&r.height<=1)continue;
  var note=document.createElement('span');
  note.className='blocked-image';
  note.setAttribute('data-aster-failed','1');
  note.textContent=${org.json.JSONObject.quote(image_failed_label)};
  im.parentNode.replaceChild(note,im);
}
  }
  window.__aster_collapse_images=aster_mark_failed_images;
  try{aster_mark_failed_images()}catch(_){}
  var aster_fit_scale=1.0;
  var aster_base_vw=0;
  var aster_applied_w=0;
  var aster_fitting=false;
  function aster_fit(){
if(aster_fitting)return;
aster_fitting=true;
try{aster_fit_inner()}finally{
  setTimeout(function(){aster_fitting=false},0);
}
  }
  function aster_fit_inner(){
var m=document.getElementById('m');
if(!m)return;
var nl=document.documentElement.getAttribute('data-nl');
var vw=window.innerWidth;
if(vw<=100)return;
if(!aster_base_vw)aster_base_vw=vw;
vw=aster_base_vw;
var meta=document.querySelector('meta[name=viewport]');
if(!meta)return;
var doc_max=m.scrollWidth||0;
try{
  var doc_w=Math.max(
    document.documentElement.scrollWidth||0,
    document.body?document.body.scrollWidth||0:0
  );
  if(doc_w>doc_max)doc_max=doc_w;
}catch(_){}
var el_max=0;
try{
  var els=m.querySelectorAll('*');
  for(var i=0;i<els.length;i++){
    var el=els[i];
    var ecs=window.getComputedStyle(el);
    if(ecs.position==='fixed')continue;
    if(ecs.display==='none'||ecs.visibility==='hidden')continue;
    var r=el.getBoundingClientRect();
    if(r.width<=0||r.height<=0)continue;
    if(r.right>el_max)el_max=r.right;
    var sw=el.scrollWidth||0;
    if(sw>r.width+1){
      var own=r.left+sw;
      if(own>el_max)el_max=own;
    }
  }
}catch(_){}
try{
  if(!aster_applied_w&&el_max>0){
    var body_pr=parseFloat(window.getComputedStyle(document.body).paddingRight)||0;
    el_max=el_max+body_pr;
  }
}catch(_){}
var max=Math.max(doc_max,el_max);
var widest2=Math.ceil(max);
if(aster_applied_w&&widest2<=aster_applied_w+2)widest2=aster_applied_w;
var scale2=1.0;
if(widest2>vw+4){
  scale2=Math.max(0.25,vw/widest2);
  aster_applied_w=widest2;
  var content='width='+widest2+',initial-scale='+scale2+',maximum-scale=5,user-scalable=yes';
  if(meta.getAttribute('content')!==content){
    meta.setAttribute('content',content);
    document.documentElement.style.overflowX='auto';
    document.body.style.overflowX='auto';
    if(nl){
      m.style.setProperty('overflow-x','visible','important');
      m.style.setProperty('max-width','none','important');
    }
  }
}
aster_fit_scale=scale2;
window.__aster_fit_scale=scale2;
var fit_h=nl?aster_content_height():measure_h();
console.log('ASTER_HEIGHT_EXACT:'+(Math.round(fit_h*scale2)));
  }
  window.__aster_fit=aster_fit;
  try{
var all_imgs=body.querySelectorAll('img');
for(var ii=0;ii<all_imgs.length;ii++){
  (function(im){
    if(im.complete)return;
    im.addEventListener('load',function(){aster_mark_failed_images();aster_fit()});
    im.addEventListener('error',function(){aster_mark_failed_images();aster_fit()});
  })(all_imgs[ii]);
}
  }catch(_){}
  try{
var mo=new MutationObserver(function(){aster_fit()});
mo.observe(body,{childList:true,subtree:true,attributes:true,attributeFilter:['src','style','width']});
setTimeout(function(){try{mo.disconnect()}catch(_){}},5000);
  }catch(_){}
  setTimeout(aster_fit,0);
  setTimeout(aster_fit,300);
  setTimeout(aster_fit,1000);
  setTimeout(aster_fit,2500);
  function is_blank_spacer(n){
if(!n)return false;
if(n.nodeType===3)return !(n.nodeValue||'').trim().length;
if(n.nodeType!==1)return false;
if(n.tagName==='BR')return true;
if(['DIV','P','SPAN'].indexOf(n.tagName)<0)return false;
if((n.textContent||'').trim().length)return false;
return !n.querySelector('img,hr,table,video,audio,iframe,object');
  }
  function trim_trailing_gap(node){
if(!node)return;
var prev=node.previousSibling;
while(is_blank_spacer(prev)){
  var rm=prev;prev=prev.previousSibling;
  if(rm.parentNode)rm.parentNode.removeChild(rm);
}
var next=node.nextSibling;
while(is_blank_spacer(next)){
  var rn=next;next=next.nextSibling;
  if(rn.parentNode)rn.parentNode.removeChild(rn);
}
  }
  function make_toggle(content_el){
var wrapper=document.createElement('div');wrapper.className='aster-quoted-wrapper';
var btn=document.createElement('button');btn.className='aster-quote-toggle';btn.type='button';btn.textContent='•••';
var cdiv=document.createElement('div');cdiv.className='aster-quoted-content';cdiv.style.display='none';
content_el.parentNode.insertBefore(wrapper,content_el);cdiv.appendChild(content_el);
btn.addEventListener('click',function(){var h=cdiv.style.display==='none';cdiv.style.display=h?'':'none';btn.classList.toggle('aster-quote-expanded',h);if(h)watch_media(cdiv);schedule_h()});
wrapper.appendChild(btn);wrapper.appendChild(cdiv);
trim_trailing_gap(wrapper);
  }
  var proton=body.querySelector('div.protonmail_quote');
  if(proton){
var cbq=proton.querySelector(':scope > blockquote');
if(cbq){
  var meta=[];var prev=proton.previousSibling;
  while(prev){var pel=prev.nodeType===1?prev:null;var ptxt=(prev.textContent||'').trim();var is_sig=pel&&pel.classList&&pel.classList.contains('protonmail_signature_block');if(is_sig||!ptxt){meta.unshift(prev);prev=prev.previousSibling}else break}
  var par=proton.parentNode;while(cbq.firstChild)par.insertBefore(cbq.firstChild,proton);
  meta.push(proton);
  var det=document.createElement('details');det.className='aster-forwarded-collapse';
  var sum=document.createElement('summary');sum.textContent=${org.json.JSONObject.quote(forwarded_label)};det.appendChild(sum);
  var cdiv2=document.createElement('div');cdiv2.className='aster-forwarded-content';
  meta.forEach(function(n){cdiv2.appendChild(n)});det.appendChild(cdiv2);body.appendChild(det);
}
  }
  try{
if(!body.querySelector('div.aster_quote,div.gmail_quote')){
  var legacy=body.querySelector('details:not(.aster-forwarded-collapse)');
  var legacy_bq=legacy?legacy.querySelector('blockquote'):null;
  if(legacy_bq){
    var legacy_wrap=document.createElement('div');legacy_wrap.className='aster_quote';
    while(legacy_bq.firstChild)legacy_wrap.appendChild(legacy_bq.firstChild);
    legacy.parentNode.replaceChild(legacy_wrap,legacy);
  }
}
  }catch(_){}
  if(!body.querySelector('details.aster-forwarded-collapse')){
var gq=body.querySelector('div.aster_quote,div.gmail_quote');
if(gq)make_toggle(gq);
  }
  if(!body.querySelector('.aster-quoted-wrapper')&&!body.querySelector('details.aster-forwarded-collapse')){
var wrote_re=/(^|[\s> ])(On\s[^\n]{1,200}?\bwrote\s*:)/i;
var wm_re=/(^|[\s> ])(Secured by Aster Mail)/i;
var walker=document.createTreeWalker(body,NodeFilter.SHOW_TEXT);
var marker=null;
var wrote_node=null;var wrote_idx=-1;
while(walker.nextNode()){
  var nd=walker.currentNode;var txt=nd.nodeValue||'';
  var wm=wm_re.exec(txt);
  if(wm){
    var wm_idx=wm.index+(wm[1]?wm[1].length:0);
    marker=(wm_idx>0)?nd.splitText(wm_idx):nd;
    break;
  }
  if(wrote_node===null){
    var mm=wrote_re.exec(txt);
    if(mm){wrote_node=nd;wrote_idx=mm.index+(mm[1]?mm[1].length:0);}
  }
}
if(!marker&&wrote_node){
  marker=(wrote_idx>0)?wrote_node.splitText(wrote_idx):wrote_node;
}
if(marker){
  var to_col=[];
  var cur=marker;
  while(cur){var nx=cur.nextSibling;to_col.push(cur);cur=nx}
  var anc=marker.parentNode;
  while(anc&&anc!==body){
    var ns=anc.nextSibling;
    while(ns){var nxn=ns.nextSibling;to_col.push(ns);ns=nxn}
    anc=anc.parentNode;
  }
  var col_text='';
  for(var ct=0;ct<to_col.length;ct++)col_text+=(to_col[ct].textContent||to_col[ct].nodeValue||'');
  var has_quote_sig=/wrote\s*:|-{3,}\s*(?:Original|Forwarded)|Forwarded message|^\s*>/i.test(col_text);
  if(to_col.length>0&&(has_quote_sig||col_text.replace(/\s+/g,' ').trim().length>60)){
    var w2=document.createElement('div');w2.className='aster-quoted-wrapper';
    var b2=document.createElement('button');b2.className='aster-quote-toggle';b2.type='button';b2.textContent='•••';
    var c2=document.createElement('div');c2.className='aster-quoted-content';c2.style.display='none';
    to_col.forEach(function(node){c2.appendChild(node)});
    b2.addEventListener('click',function(){var h=c2.style.display==='none';c2.style.display=h?'':'none';b2.classList.toggle('aster-quote-expanded',h);if(h)watch_media(c2);schedule_h()});
    w2.appendChild(b2);w2.appendChild(c2);(document.getElementById('m')||body).appendChild(w2);trim_trailing_gap(w2);
  }
}
  }
  try{
body.querySelectorAll('.protonmail_signature_block-empty').forEach(function(el){el.remove()});
body.querySelectorAll('.protonmail_signature_block').forEach(function(sig){
  if(!((sig.textContent||'').trim().length)){sig.remove();return}
  var sprev=sig.previousSibling;
  while(sprev){
    var sel=sprev.nodeType===1?sprev:null;
    var stx=(sprev.textContent||'').trim();
    var empty_block=sel&&['DIV','P','BR'].indexOf(sel.tagName)>=0&&!stx.length&&!sel.querySelector('img,hr,table');
    if(empty_block||(!sel&&!stx.length)){var rm=sprev;sprev=sprev.previousSibling;if(rm.parentNode)rm.parentNode.removeChild(rm)}
    else break;
  }
});
  }catch(_){}
  try{
var rich=body.querySelector('table[width],table[bgcolor],table[background],center,[class]:not(img)')!==null||(body.querySelector('table')!==null&&body.querySelectorAll('table').length>1);
if(!rich){
  var removable=function(node){
    if(node.nodeType===3)return !((node.textContent||'').trim());
    if(node.nodeType===8)return true;
    if(node.nodeType!==1)return false;
    if(node.tagName==='BR')return true;
    if(['DIV','P','SECTION','SPAN'].indexOf(node.tagName)<0)return false;
    if((node.textContent||'').trim())return false;
    if(node.querySelector('img,hr,table,iframe,svg,video,object,embed,input,button'))return false;
    return !/background|height|border|padding/i.test(node.getAttribute('style')||'');
  };
  var container=body;
  for(;;){
    var last=container.lastChild;
    for(;;){
      while(last&&removable(last)){var rmv=last;last=last.previousSibling;if(rmv.parentNode)rmv.parentNode.removeChild(rmv)}
      if(last&&last.nodeType===1&&last.matches('.aster-quoted-wrapper, details.aster-forwarded-collapse')){last=last.previousSibling;continue}
      break;
    }
    if(last&&last.nodeType===1&&['DIV','P'].indexOf(last.tagName)>=0&&!last.matches("[class*='quote'], [class*='cite']")){container=last;continue}
    break;
  }
}
  }catch(_){}
  document.addEventListener('click',function(ev){
var t=ev.target;
if(!t||t.tagName!=='IMG')return;
if(t.closest&&t.closest('a'))return;
if(t.getAttribute('data-blocked')==='true')return;
if(t.classList&&t.classList.contains('blocked-image'))return;
if((t.naturalWidth||0)<24||(t.naturalHeight||0)<24)return;
var isrc=t.currentSrc||t.src||'';
if(!isrc)return;
ev.preventDefault();
ev.stopPropagation();
window.location.href='asterimg:'+encodeURIComponent(isrc);
  },true);
  try{
var dts=document.querySelectorAll('details.aster-forwarded-collapse');
for(var d=0;d<dts.length;d++){
  (function(el){el.addEventListener('toggle',function(){if(el.open)watch_media(el);schedule_h()})})(dts[d]);
}
  }catch(_){}
  report_h_exact();
})();
</script></body></html>"""
}
