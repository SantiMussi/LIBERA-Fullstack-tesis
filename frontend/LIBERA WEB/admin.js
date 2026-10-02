/* =============================================================
   PANEL DE ADMINISTRACIÓN (admin.html) — solo rol ADMIN.
   Revisión de publicaciones (con el comprobante), compras con el pago
   retenido (confirmar check-in) y solicitudes de demo de hoteles.
   Usa window.LiberaApi (api.js) y los helpers de window.LiberaUI (app.js).
   ============================================================= */
(function () {
  "use strict";

  var api = window.LiberaApi;
  var ui = window.LiberaUI;
  var root = document.querySelector("[data-admin]");
  if (!root || !api || !ui) return;

  var $ = ui.$, $$ = ui.$$;
  var guest = $("[data-account-guest]", root);
  var denied = $("[data-admin-denied]", root);
  var panels = $("[data-admin-panels]", root);
  var statusFilter = $("[data-admin-purchase-status]", root);

  /* ---------- pestañas ---------- */
  $$("[data-admin-tab]", root).forEach(function (tab) {
    tab.addEventListener("click", function () {
      $$("[data-admin-tab]", root).forEach(function (t) {
        var active = t === tab;
        t.classList.toggle("is-active", active);
        t.setAttribute("aria-selected", active ? "true" : "false");
      });
      $$("[data-admin-panel]", root).forEach(function (p) { p.hidden = p.dataset.adminPanel !== tab.dataset.adminTab; });
    });
  });

  function list(name) { return $('[data-admin-list="' + name + '"]', root); }

  function fill(name, items, emptyText) {
    var el = list(name);
    el.innerHTML = "";
    if (!items.length) {
      var p = document.createElement("p");
      p.className = "account-empty";
      p.textContent = emptyText;
      el.appendChild(p);
      return;
    }
    items.forEach(function (i) { el.appendChild(i); });
  }

  function setCount(name, n) {
    var badge = $('[data-admin-count="' + name + '"]', root);
    if (badge) { badge.textContent = String(n); badge.hidden = n === 0; }
  }

  function showError(name, err) {
    var el = list(name);
    el.innerHTML = '<p class="account-empty"></p>';
    el.firstChild.textContent = err.message;
  }

  // Acción sobre un ítem: deshabilita el botón mientras corre y recarga la lista al terminar
  function run(btn, call, okMessage, reload) {
    btn.disabled = true;
    call().then(function () { ui.showToast(okMessage); reload(); })
      .catch(function (err) { btn.disabled = false; ui.showToast(err.message); });
  }

  /* ---------- publicaciones en revisión ---------- */
  function openVoucher(bookingId) {
    // la ventana se abre ya (dentro del click) para que el navegador no la bloquee; el archivo llega después
    var win = window.open("", "_blank");
    api.admin.voucherFile(bookingId).then(function (blob) {
      var url = URL.createObjectURL(blob);
      if (win) win.location.href = url; else window.location.href = url;
      setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
    }).catch(function (err) {
      if (win) win.close();
      ui.showToast(err.message);
    });
  }

  function reviewItem(a) {
    var l = a.listing;
    var actions = [];
    if (a.voucherFileName) actions.push({ label: "Ver comprobante", run: function () { openVoucher(l.originalBookingId); } });
    actions.push({ label: "Ver publicación", href: "oferta.html?id=" + l.id });
    actions.push({
      label: "Rechazar",
      run: function (btn) {
        var note = window.prompt("Motivo del rechazo (lo va a ver el vendedor):", "No pudimos validar la reserva con el hotel.");
        if (note === null) return;
        if (!note.trim()) { ui.showToast("Escribí un motivo para rechazar la publicación."); return; }
        run(btn, function () { return api.admin.rejectListing(l.id, note.trim()); }, "Publicación rechazada", loadReview);
      }
    });
    actions.push({
      label: "Aprobar", primary: true,
      run: function (btn) { run(btn, function () { return api.admin.approveListing(l.id); }, "Publicación aprobada: ya está en el catálogo", loadReview); }
    });
    return ui.accountItem({
      title: l.hotelName + (l.hotelCity ? " · " + l.hotelCity : ""),
      meta: l.roomType + " · " + ui.stayText(l) + " · Código " + a.pmsConfirmationCode,
      hint: "Vendedor: " + a.sellerName + " (" + a.sellerEmail + "). Pagó " + ui.formatMoney(Number(a.amountPaid)) +
        " y publica a " + ui.formatMoney(Number(l.listedTotalPrice)) + ". " +
        (a.voucherFileName ? "Comprobante: " + a.voucherFileName + "." : "No subió comprobante."),
      status: ui.listingStatus(l.status),
      price: ui.formatMoney(Number(l.listedTotalPrice)),
      actions: actions
    });
  }

  function loadReview() {
    api.admin.listings("PENDING_REVIEW").then(function (items) {
      setCount("review", items.length);
      fill("review", items.map(reviewItem), "No hay publicaciones esperando revisión.");
    }).catch(function (err) { showError("review", err); });
  }

  /* ---------- compras ---------- */
  function purchaseItem(a) {
    var p = a.purchase;
    var st = ui.purchaseStatus(p.status) || { label: p.status, tone: "muted" };
    var t = a.transaction;
    var hint = "Comprador: " + a.buyerName + " (" + a.buyerEmail + ") · Vendedor: " + a.sellerName + " (" + a.sellerEmail + ").";
    if (t) {
      hint += " Reparto: vendedor " + ui.formatAmount(t.sellerPayoutAmount) +
        " · hotel " + ui.formatAmount(t.hotelRevenueShareAmount) +
        " · LIBERA " + ui.formatAmount(t.liberaNetRevenue) + ".";
    }
    var actions = [{ label: "Ver detalle", href: "compra.html?id=" + p.id }];
    if (p.status !== "LIQUIDATED") {
      actions.push({
        label: "Confirmar check-in", primary: true,
        run: function (btn) {
          if (!window.confirm("¿Confirmás que el huésped hizo el check-in? Se libera el pago al vendedor y no se puede deshacer.")) return;
          run(btn, function () { return api.admin.confirmCheckIn(p.id); }, "Check-in confirmado: pago liberado al vendedor", loadPurchases);
        }
      });
    }
    return ui.accountItem({
      title: p.hotelName,
      meta: p.roomType + " · " + ui.stayText(p) + " · Compra #" + p.id,
      hint: hint,
      status: st,
      price: ui.formatAmount(p.totalPaid != null ? p.totalPaid : p.totalPrice),
      actions: actions
    });
  }

  function loadPurchases() {
    api.admin.purchases(statusFilter.value).then(function (items) {
      fill("purchases", items.map(purchaseItem), "No hay compras con ese estado.");
    }).catch(function (err) { showError("purchases", err); });
    // el contador de la pestaña muestra las que esperan confirmación de check-in
    api.admin.purchases().then(function (all) {
      setCount("purchases", all.filter(function (a) { return a.purchase.status !== "LIQUIDATED"; }).length);
    }).catch(function () {});
  }
  statusFilter.addEventListener("change", loadPurchases);

  /* ---------- solicitudes de demo ---------- */
  function contactItem(c) {
    var date = new Date(c.createdAt);
    var item = ui.accountItem({
      title: c.hotelName,
      meta: [c.name, c.email, c.phone, c.rooms ? c.rooms + " habitaciones" : ""].filter(Boolean).join(" · "),
      hint: c.message || "Sin mensaje.",
      price: isNaN(date) ? "" : date.toLocaleDateString("es-AR", { day: "2-digit", month: "short", year: "numeric" }),
      actions: [{ label: "Responder por email", href: "mailto:" + c.email + "?subject=" + encodeURIComponent("LIBERA — demo para " + c.hotelName) }]
    });
    return item;
  }

  function loadContacts() {
    api.admin.contactRequests().then(function (items) {
      setCount("contacts", items.length);
      fill("contacts", items.map(contactItem), "Todavía no hay solicitudes de demo.");
    }).catch(function (err) { showError("contacts", err); });
  }

  /* ---------- acceso ---------- */
  function load() {
    var user = api.currentUser();
    guest.hidden = !!user;
    denied.hidden = !user || user.role === "ADMIN";
    panels.hidden = !user || user.role !== "ADMIN";
    if (!user || user.role !== "ADMIN") return;
    loadReview();
    loadPurchases();
    loadContacts();
  }

  api.onSessionChange(load);
  load();
})();
