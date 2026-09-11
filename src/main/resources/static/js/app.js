/* MailDesk Pro - JavaScript mínimo (sin dependencias) */
(function () {
    "use strict";

    // ---- Tema claro/oscuro (preferencia guardada en el navegador) ----
    function applyTheme(theme) {
        if (theme === "dark" || theme === "light") {
            document.documentElement.setAttribute("data-theme", theme);
        } else {
            document.documentElement.removeAttribute("data-theme");
        }
    }
    try { applyTheme(localStorage.getItem("maildesk-theme")); } catch (e) { /* almacenamiento no disponible */ }

    function csrf() {
        var token = document.querySelector("meta[name='_csrf']");
        var header = document.querySelector("meta[name='_csrf_header']");
        return { token: token ? token.content : "", header: header ? header.content : "X-CSRF-TOKEN" };
    }

    document.addEventListener("DOMContentLoaded", function () {
        // Tema
        document.querySelectorAll("[data-toggle='theme']").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var current = document.documentElement.getAttribute("data-theme");
                var prefersDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
                var effective = current || (prefersDark ? "dark" : "light");
                var next = effective === "dark" ? "light" : "dark";
                applyTheme(next);
                try { localStorage.setItem("maildesk-theme", next); } catch (e) { /* ignorar */ }
            });
        });

        // Menú lateral en móvil
        document.querySelectorAll("[data-toggle='sidebar']").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var sidebar = document.getElementById("sidebar");
                if (!sidebar) { return; }
                var open = sidebar.classList.toggle("open");
                btn.setAttribute("aria-expanded", open ? "true" : "false");
            });
        });

        // Confirmaciones
        document.querySelectorAll("form[data-confirm]").forEach(function (form) {
            form.addEventListener("submit", function (ev) {
                if (!window.confirm(form.getAttribute("data-confirm"))) { ev.preventDefault(); }
            });
        });

        // Protección contra doble clic: deshabilita botones al enviar
        document.querySelectorAll("form").forEach(function (form) {
            form.addEventListener("submit", function () {
                if (form.dataset.submitted === "1") { return; }
                form.dataset.submitted = "1";
                form.querySelectorAll("button[type='submit']").forEach(function (b) {
                    b.classList.add("loading");
                    b.setAttribute("aria-disabled", "true");
                    setTimeout(function () { b.disabled = true; }, 0);
                });
            });
        });

        // Cuenta regresiva para reenviar código
        var resend = document.querySelector("[data-resend-wait]");
        if (resend) {
            var wait = parseInt(resend.getAttribute("data-resend-wait"), 10) || 0;
            var button = resend.querySelector("button");
            var label = resend.querySelector("[data-countdown]");
            if (wait > 0 && button) {
                button.disabled = true;
                var timer = setInterval(function () {
                    wait -= 1;
                    if (label) { label.textContent = wait > 0 ? "Podrás reenviar en " + wait + " s" : ""; }
                    if (wait <= 0) { clearInterval(timer); button.disabled = false; }
                }, 1000);
            }
        }

        initEditor();
        initAutosave();
        initSuggest();
        initTemplatePreview();
    });

    // ---- Editor HTML sencillo ----
    function initEditor() {
        var editor = document.getElementById("editor");
        var hidden = document.getElementById("body");
        var textArea = document.getElementById("bodyText");
        var toolbar = document.querySelector(".editor-toolbar");
        if (!editor || !hidden) { return; }
        var modeInputs = document.querySelectorAll("input[name='contentType']");

        function syncMode() {
            var html = true;
            modeInputs.forEach(function (i) { if (i.checked) { html = i.value === "HTML"; } });
            editor.hidden = !html;
            if (toolbar) { toolbar.hidden = !html; }
            if (textArea) { textArea.hidden = html; }
        }
        modeInputs.forEach(function (i) { i.addEventListener("change", function () { syncMode(); sync(); }); });
        syncMode();

        function sync() {
            var html = !editor.hidden;
            hidden.value = html ? editor.innerHTML : (textArea ? textArea.value : "");
        }
        editor.addEventListener("input", sync);
        if (textArea) { textArea.addEventListener("input", sync); }
        var form = editor.closest("form");
        if (form) { form.addEventListener("submit", sync); }
        sync();

        if (toolbar) {
            toolbar.querySelectorAll("button[data-cmd]").forEach(function (btn) {
                btn.addEventListener("click", function (ev) {
                    ev.preventDefault();
                    editor.focus();
                    var cmd = btn.getAttribute("data-cmd");
                    if (cmd === "createLink") {
                        var url = window.prompt("Dirección del enlace (https://...)");
                        if (url && /^https?:\/\//i.test(url)) { document.execCommand("createLink", false, url); }
                        return;
                    }
                    document.execCommand(cmd, false, btn.getAttribute("data-value") || null);
                    sync();
                });
            });
        }
        editor.addEventListener("paste", function (ev) {
            // Pegar como texto plano evita HTML inesperado; el servidor sanea de todos modos.
            ev.preventDefault();
            var text = (ev.clipboardData || window.clipboardData).getData("text/plain");
            document.execCommand("insertText", false, text);
        });
    }

    // ---- Autoguardado de borradores ----
    function initAutosave() {
        var form = document.querySelector("form[data-autosave]");
        if (!form) { return; }
        var url = form.getAttribute("data-autosave");
        var status = document.getElementById("autosave-status");
        var timer = null;
        var lastPayload = null;

        function payload() {
            var body = form.querySelector("#body");
            var contentType = "HTML";
            form.querySelectorAll("input[name='contentType']").forEach(function (i) { if (i.checked) { contentType = i.value; } });
            var includeSignature = form.querySelector("input[name='includeSignature']");
            return {
                to: (form.querySelector("[name='to']") || {}).value || "",
                cc: (form.querySelector("[name='cc']") || {}).value || "",
                bcc: (form.querySelector("[name='bcc']") || {}).value || "",
                subject: (form.querySelector("[name='subject']") || {}).value || "",
                body: body ? body.value : "",
                contentType: contentType,
                includeSignature: includeSignature ? includeSignature.checked : true
            };
        }

        function save() {
            var data = payload();
            var json = JSON.stringify(data);
            if (json === lastPayload) { return; }
            if (status) { status.textContent = "Guardando…"; }
            var c = csrf();
            var headers = { "Content-Type": "application/json" };
            headers[c.header] = c.token;
            fetch(url, { method: "PUT", headers: headers, body: json, credentials: "same-origin" })
                .then(function (r) {
                    if (r.status === 401 || r.status === 403) { throw new Error("Sesión expirada. Vuelve a iniciar sesión."); }
                    if (!r.ok) { return r.json().then(function (j) { throw new Error(j.error || "No se pudo guardar"); }); }
                    return r.json();
                })
                .then(function () {
                    lastPayload = json;
                    if (status) { status.textContent = "Borrador guardado " + new Date().toLocaleTimeString(); }
                })
                .catch(function (err) {
                    if (status) { status.textContent = "Autoguardado fallido: " + err.message; }
                });
        }

        function schedule() {
            clearTimeout(timer);
            timer = setTimeout(save, 1500);
            if (status) { status.textContent = "Cambios sin guardar…"; }
        }
        form.querySelectorAll("input, textarea, [contenteditable]").forEach(function (el) {
            el.addEventListener("input", schedule);
            el.addEventListener("change", schedule);
        });
        lastPayload = JSON.stringify(payload());
        form.addEventListener("submit", function () { clearTimeout(timer); });
    }

    // ---- Sugerencia de contactos en Para/CC/CCO ----
    function initSuggest() {
        document.querySelectorAll("input[data-suggest]").forEach(function (input) {
            var wrap = input.parentElement;
            var list = document.createElement("ul");
            list.className = "suggest-list";
            list.hidden = true;
            list.setAttribute("role", "listbox");
            wrap.appendChild(list);
            var timer = null;

            function currentTerm() {
                var parts = input.value.split(/[,;]/);
                return parts[parts.length - 1].trim();
            }
            function replaceLast(email) {
                var parts = input.value.split(/[,;]/);
                parts[parts.length - 1] = " " + email;
                input.value = parts.map(function (p) { return p.trim(); }).filter(Boolean).join(", ") + ", ";
                input.dispatchEvent(new Event("input", { bubbles: true }));
            }
            function render(items) {
                list.innerHTML = "";
                if (!items.length) { list.hidden = true; return; }
                items.forEach(function (c) {
                    var li = document.createElement("li");
                    li.setAttribute("role", "option");
                    li.textContent = c.name;
                    var small = document.createElement("small");
                    small.textContent = c.email;
                    li.appendChild(small);
                    li.addEventListener("mousedown", function (ev) { ev.preventDefault(); replaceLast(c.email); list.hidden = true; });
                    list.appendChild(li);
                });
                list.hidden = false;
            }
            input.addEventListener("input", function () {
                clearTimeout(timer);
                var term = currentTerm();
                if (term.length < 2) { list.hidden = true; return; }
                timer = setTimeout(function () {
                    fetch("/api/contacts/suggest?q=" + encodeURIComponent(term), { credentials: "same-origin" })
                        .then(function (r) { return r.ok ? r.json() : []; })
                        .then(render)
                        .catch(function () { list.hidden = true; });
                }, 250);
            });
            input.addEventListener("blur", function () { setTimeout(function () { list.hidden = true; }, 150); });
            input.addEventListener("keydown", function (ev) { if (ev.key === "Escape") { list.hidden = true; } });
        });
    }

    // ---- Vista previa de plantillas (solo texto del formulario, sin evaluar nada) ----
    function initTemplatePreview() {
        var editor = document.getElementById("editor");
        var frame = document.getElementById("template-preview");
        if (!editor || !frame) { return; }
        function update() { frame.srcdoc = "<!doctype html><meta charset='utf-8'><body style='font-family:sans-serif'>" + editor.innerHTML + "</body>"; }
        editor.addEventListener("input", update);
        update();
    }
})();
