/* APK Forge shim: descargas (blob/data), compartir y utilidades dentro de la app. */
(function () {
  if (window.__forgeShim) return;
  window.__forgeShim = true;
  var N = window.ForgeNative;
  if (!N) return;
  var O = {};
  try { O = JSON.parse(N.options() || '{}'); } catch (e) { }
  var css = '';
  if (O.selectText === false) css += '*{-webkit-user-select:none;user-select:none;-webkit-touch-callout:none}input,textarea,[contenteditable],[contenteditable] *{-webkit-user-select:text;user-select:text}';
  if (css) { var st = document.createElement('style'); st.textContent = css; (document.head || document.documentElement).appendChild(st); }
  if (O.theme === 'dark' || O.theme === 'light') document.documentElement.style.colorScheme = O.theme;
  var CH = 3 * 131072; // múltiplo de 3 -> cada trozo base64 se decodifica por separado

  function extFromType(t) {
    var m = { 'image/png': 'png', 'image/jpeg': 'jpg', 'image/webp': 'webp', 'image/gif': 'gif', 'video/webm': 'webm', 'video/mp4': 'mp4',
      'audio/wav': 'wav', 'audio/mpeg': 'mp3', 'audio/ogg': 'ogg', 'application/json': 'json', 'text/plain': 'txt', 'application/zip': 'zip' };
    return m[(t || '').split(';')[0]] || '';
  }

  function saveBlob(blob, name, share) {
    return new Promise(function (resolve, reject) {
      try {
        if (!name) { var e = extFromType(blob.type); name = 'descarga' + (e ? '.' + e : ''); }
        var id = N.begin(String(name), blob.type || '');
        if (!id) { reject(new Error('no se pudo iniciar la descarga')); return; }
        var off = 0;
        (function next() {
          if (off >= blob.size) { resolve(N.end(id, !!share)); return; }
          var rd = new FileReader();
          rd.onload = function () {
            var s = String(rd.result), i = s.indexOf(',');
            if (!N.chunk(id, s.substring(i + 1))) { reject(new Error('error al guardar')); return; }
            off += CH;
            next();
          };
          rd.onerror = function () { N.abort(id); reject(rd.error); };
          rd.readAsDataURL(blob.slice(off, off + CH));
        })();
      } catch (err) { reject(err); }
    });
  }

  function nameFromUrl(u) {
    try {
      if (/^(blob|data):/.test(u)) return '';
      var p = decodeURIComponent(new URL(u).pathname.split('/').pop() || '');
      return p;
    } catch (e) { return ''; }
  }

  function saveUrl(url, name, share) {
    return fetch(url).then(function (r) { return r.blob(); }).then(function (b) {
      return saveBlob(b, name || nameFromUrl(url), share);
    });
  }
  window.__forgeSaveUrl = saveUrl;

  function handleAnchor(a) {
    if (!a || !a.hasAttribute || !a.hasAttribute('download')) return false;
    var href = a.href;
    if (!href) return false;
    if (/^(blob:|data:)/.test(href) || href.indexOf(location.origin) === 0) {
      saveUrl(href, a.getAttribute('download') || '').catch(function (e) { N.toast('Error al descargar'); });
      return true;
    }
    return false;
  }

  var oc = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (handleAnchor(this)) return;
    return oc.apply(this, arguments);
  };
  var od = EventTarget.prototype.dispatchEvent;
  EventTarget.prototype.dispatchEvent = function (ev) {
    if (this instanceof HTMLAnchorElement && ev && ev.type === 'click' && handleAnchor(this)) return true;
    return od.apply(this, arguments);
  };
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
    if (a && handleAnchor(a)) { e.preventDefault(); e.stopPropagation(); }
  }, true);

  // Web Share API (no existe en WebView)
  navigator.canShare = function () { return true; };
  navigator.share = function (d) {
    d = d || {};
    if (d.files && d.files.length) {
      var p = [];
      for (var i = 0; i < d.files.length; i++) p.push(saveBlob(d.files[i], d.files[i].name, i === 0));
      return Promise.all(p).then(function () { });
    }
    var t = (d.title ? d.title + '\n' : '') + (d.text || '') + (d.url ? '\n' + d.url : '');
    N.shareText(t);
    return Promise.resolve();
  };

  window.ForgeApp = { isApp: true, name: N.appName(), theme: O.theme || 'system', options: O, save: saveBlob, saveUrl: saveUrl, share: function (b, n) { return saveBlob(b, n, true); } };
})();
