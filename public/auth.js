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
    bind: function (formId, passId, errId) {
      if (this.ok()) {
        document.body.classList.add("in");
        return;
      }
      var f = document.getElementById(formId);
      if (!f) return;
      f.addEventListener("submit", function (ev) {
        ev.preventDefault();
        var p = document.getElementById(passId).value;
        if (tsAuth.login(p)) {
          document.body.classList.add("in");
        } else {
          document.getElementById(errId).textContent = "密码不对";
        }
      });
    }
  };
})();
