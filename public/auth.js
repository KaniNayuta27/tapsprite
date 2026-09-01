(function () {
  var KEY = "ts_ok";
  var PASS = "941227";
  window.tsAuth = {
    ok: function () { return sessionStorage.getItem(KEY) === "1"; },
    login: function (p) {
      if (String(p || "") === PASS) {
        sessionStorage.setItem(KEY, "1");
        return true;
      }
      return false;
    },
    require: function () {
      if (this.ok()) return;
      document.open();
      document.write("<!DOCTYPE html><html lang='zh-CN'><head><meta charset='utf-8'/>" +
        "<meta name='viewport' content='width=device-width,initial-scale=1'/>" +
        "<title>触控精灵</title><style>" +
        "body{margin:0;min-height:100vh;display:grid;place-items:center;background:#f3f0fa;color:#1a1430;" +
        "font:16px/1.5 'Segoe UI','Microsoft YaHei UI',sans-serif}" +
        ".box{width:min(360px,92vw);background:#fff;padding:28px 24px;border-radius:20px;" +
        "box-shadow:0 0 0 1px rgba(26,20,48,.08),0 8px 24px rgba(40,30,80,.06)}" +
        "h1{margin:0 0 6px;font-size:22px}p{margin:0 0 16px;color:#746d8c;font-size:14px}" +
        "input{width:100%;box-sizing:border-box;padding:12px 14px;border:1px solid #e4dff2;border-radius:12px;font:16px inherit;outline:none}" +
        "input:focus{border-color:#6d5ef5}" +
        "button{margin-top:12px;width:100%;padding:12px;border:0;border-radius:12px;background:#6d5ef5;color:#fff;font:650 15px inherit;cursor:pointer}" +
        ".err{color:#c45c4a;font-size:13px;min-height:1.2em;margin-top:8px}</style></head><body>" +
        "<form class='box' id='f'><h1>触控精灵</h1><p>输入密码后查看下载和文档。</p>" +
        "<input id='p' type='password' autofocus placeholder='密码' autocomplete='current-password'/>" +
        "<button type='submit'>进入</button><div class='err' id='e'></div></form>" +
        "<script>document.getElementById('f').onsubmit=function(ev){ev.preventDefault();" +
        "if(document.getElementById('p').value==='941227'){sessionStorage.setItem('ts_ok','1');location.reload();}" +
        "else{document.getElementById('e').textContent='密码不对';}};" +
        "document.getElementById('p').focus();<\/script></body></html>");
      document.close();
    }
  };
})();
