(function () {
  "use strict";

  var $ = function (sel, scope) { return (scope || document).querySelector(sel); };
  var $$ = function (sel, scope) { return Array.prototype.slice.call((scope || document).querySelectorAll(sel)); };
  var fineHover = matchMedia("(hover: hover) and (pointer: fine)").matches;
  // Shared between initOtaSearch (writes, as the user picks) and initCatalog
  // (reads, only when "Buscar" is actually pressed — see initCatalog).
  var otaState = { country: "", month: "Ago", adults: 2, rooms: 1 };

  function safe(fn, name) {
    try { fn(); } catch (e) { console.warn("[" + name + "] failed:", e); }
  }

  // requestAnimationFrame is throttled/suspended on hidden or non-composited tabs
  // (backgrounded windows, some low-power/RDP setups) — races it against a timeout
  // so one-shot "force a reflow, then add the transition class" calls never get stuck.
  function nextFrame(fn) {
    var done = false;
    var run = function () { if (done) return; done = true; fn(); };
    requestAnimationFrame(run);
    setTimeout(run, 60);
  }

  function formatMoney(n) {
    return "$" + Math.round(n).toLocaleString("es-AR");
  }

  // Shared seller-side math: used by the "Elegí tu precio" step of the seller wizard (initSellerWizard).
  var SELLER_FEE = 0.075;
  function computeSaleBreakdown(original, discountPct) {
    var sale = original * (1 - discountPct / 100);
    var fee = sale * SELLER_FEE;
    var net = sale - fee;
    return { sale: sale, fee: fee, net: net };
  }

  function animateCount(el, to, opts) {
    opts = opts || {};
    var duration = opts.duration || 600;
    var prefix = opts.prefix || "$";
    var from = parseFloat(el.dataset.currentVal || "0");
    if (isNaN(from)) from = 0;
    var start = performance.now();
    var finished = false;
    // Safety net: if rAF is throttled (backgrounded tab, battery saver), the
    // displayed number must still land on the correct value, even without the animation.
    function finalize() {
      if (finished) return;
      finished = true;
      el.textContent = prefix + Math.round(to).toLocaleString("es-AR");
      el.dataset.currentVal = String(to);
    }
    function tick(now) {
      if (finished) return;
      var p = Math.min(1, (now - start) / duration);
      var eased = 1 - Math.pow(1 - p, 3);
      var val = from + (to - from) * eased;
      el.textContent = prefix + Math.round(val).toLocaleString("es-AR");
      if (p < 1) requestAnimationFrame(tick);
      else finalize();
    }
    requestAnimationFrame(tick);
    setTimeout(finalize, duration + 120);
  }

  var toastTimer = null;
  function showToast(msg) {
    var toast = $("[data-toast]");
    if (!toast) return;
    toast.textContent = msg;
    toast.hidden = false;
    nextFrame(function () { toast.classList.add("is-visible"); });
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      toast.classList.remove("is-visible");
      setTimeout(function () { toast.hidden = true; }, 400);
    }, 2600);
  }

  /* ---------------------------------------------------------------
     DATOS DEL BACKEND — helpers compartidos (catálogo, oferta, wizard, mi cuenta)
  --------------------------------------------------------------- */
  var MESES = ["Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"];
  var MESES_ABBR = ["Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"];
  var api = window.LiberaApi || null;
  // Foto genérica para hoteles sin contenido propio en offers-data.js
  var PLACEHOLDER_IMG = "https://images.unsplash.com/photo-1499793983690-e29da59ef1c2?w=1400&h=1000&fit=crop&q=80&auto=format";

  function pad2(n) { return String(n).padStart(2, "0"); }
  function parseISODate(s) { return new Date(s + "T00:00:00"); }
  function isoOfDate(d) { return d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate()); }
  function fmtDateLong(iso) { var d = parseISODate(iso); return pad2(d.getDate()) + " " + MESES_ABBR[d.getMonth()] + " " + d.getFullYear(); }
  function fmtRangeShort(inIso, outIso) {
    var a = parseISODate(inIso), b = parseISODate(outIso);
    if (a.getMonth() === b.getMonth()) return a.getDate() + "–" + b.getDate() + " " + MESES_ABBR[b.getMonth()];
    return a.getDate() + " " + MESES_ABBR[a.getMonth()] + " – " + b.getDate() + " " + MESES_ABBR[b.getMonth()];
  }
  function plural(n, one, many) { return n + " " + (n === 1 ? one : many); }

  // Garantía de Traspaso (fee del comprador) por tramos según el descuento del vendedor
  function feeForDiscount(discountPct) {
    if (discountPct >= 55) return 12.5;
    if (discountPct >= 35) return 10;
    return 7.5;
  }

  // Une una publicación de la API (precio, fechas, disponibilidad) con el contenido de
  // marketing del hotel en offers-data.js (fotos, descripción, régimen), si existe.
  function listingView(l) {
    var offer = (window.OFFERS || {})[l.hotelSlug] || null;
    var discountPct = Math.round(Number(l.discountPercentage) || 0);
    return {
      listing: l,
      id: l.id,
      hotel: l.hotelName,
      location: [l.hotelCity, l.hotelCountry].filter(Boolean).join(", "),
      country: l.hotelCountry || "",
      checkinISO: l.checkIn,
      checkoutISO: l.checkOut,
      checkin: fmtDateLong(l.checkIn),
      checkout: fmtDateLong(l.checkOut),
      checkinMonth: MESES_ABBR[parseISODate(l.checkIn).getMonth()],
      nights: l.nights,
      guests: offer ? offer.guests : null,
      regimen: offer ? offer.regimen : "",
      roomType: l.roomType,
      price: Number(l.listedTotalPrice),
      original: Number(l.originalPrice),
      discountPct: discountPct,
      feePct: feeForDiscount(discountPct),
      rating: offer ? offer.rating : null,
      summary: offer ? offer.summary : "Reserva publicada por su titular original en " + l.hotelName + ".",
      description: offer ? offer.description
        : "Esta reserva fue publicada por su titular original, que no puede viajar en esas fechas. Accedés al mismo hotel, la misma habitación y las mismas fechas, a un precio que decidió el propio vendedor.",
      amenities: offer ? offer.amenities : [],
      images: offer ? offer.images : [{ src: PLACEHOLDER_IMG, alt: "Foto de referencia — " + l.hotelName }],
      soldRanges: l.soldRanges || [],
      isOwn: !!(api && api.currentUser() && api.currentUser().id === l.sellerId)
    };
  }

  // La asigna initAuthModal; las páginas la usan para pedir login antes de comprar o publicar
  var openAuthModal = function () { location.href = "index.html"; };
  function requireLogin(message) {
    if (api && api.isLoggedIn()) return true;
    if (message) showToast(message);
    openAuthModal("login");
    return false;
  }

  /* ---------------------------------------------------------------
     SCROLL SUAVIZADO (Lenis, lib/lenis.min.js)
     La rueda del mouse deja de avanzar "a saltos": Lenis interpola el
     scroll y lo sincroniza con el reloj de GSAP, así el parallax se
     mueve en el mismo cuadro que la página. En pantallas táctiles queda
     el scroll nativo, y no se activa si el sistema pide reducir movimiento.
     Se carga desde acá para no repetir la etiqueta <script> en cada página.
  --------------------------------------------------------------- */
  var lenis = null;

  function navOffset() {
    var nav = $("[data-nav]");
    return -((nav ? nav.offsetHeight : 0) + 16);
  }

  // Scroll programático: pasa por Lenis si está activo, si no usa el nativo
  function scrollToTarget(target) {
    if (lenis) { lenis.scrollTo(target, { offset: typeof target === "number" ? 0 : navOffset() }); return; }
    if (typeof target === "number") window.scrollTo({ top: target, behavior: "smooth" });
    else target.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  // Bloquea el scroll de la página (drawer abierto)
  function setPageScrollLocked(locked) {
    document.documentElement.style.overflow = locked ? "hidden" : "";
    if (lenis) { if (locked) lenis.stop(); else lenis.start(); }
  }

  function initSmoothScroll() {
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    var script = document.createElement("script");
    script.src = "lib/lenis.min.js?v=1.3.26";
    script.onload = function () {
      if (!window.Lenis) return;
      var withGsap = !!(window.gsap && window.ScrollTrigger);
      lenis = new window.Lenis({
        lerp: 0.1,               // cuánto "flota": más bajo = más suave y lento
        smoothWheel: true,
        syncTouch: false,        // táctil: scroll nativo del celular
        allowNestedScroll: true, // drawer, modal y listas con scroll propio siguen andando
        anchors: { offset: navOffset() },
        autoRaf: !withGsap
      });
      if (withGsap) {
        lenis.on("scroll", window.ScrollTrigger.update);
        window.gsap.ticker.add(function (time) { lenis.raf(time * 1000); });
        window.gsap.ticker.lagSmoothing(0);
      }
    };
    document.head.appendChild(script);
  }

  /* ---------------------------------------------------------------
     SPLASH
  --------------------------------------------------------------- */
  function initSplash() {
    var splash = $("[data-splash]");
    if (!splash) return;
    var hide = function () { splash.classList.add("is-out"); setTimeout(function () { splash.hidden = true; }, 750); };
    if (document.readyState === "complete") setTimeout(hide, 500);
    else window.addEventListener("load", function () { setTimeout(hide, 350); });
    setTimeout(hide, 2200);
  }

  // catalogo.html's loading screen: unlike the splash above (hide as soon as
  // assets are ready), this deliberately holds for a minimum stretch to sell
  // "buscando en el mercado secundario" before revealing the already-loaded grid.
  // Lo completa initCatalog cuando terminan de llegar las ofertas de la API
  var catalogReady = null;

  function initCatalogLoading() {
    var loading = $("[data-catalog-loading]");
    if (!loading) return;
    var MIN_MS = 1700;
    var start = performance.now();
    var done = false;
    var hide = function () {
      if (done) return;
      done = true;
      loading.classList.add("is-out");
      setTimeout(function () { loading.hidden = true; }, 850);
    };
    var reveal = function () {
      Promise.resolve(catalogReady).then(function () {
        var elapsed = performance.now() - start;
        setTimeout(hide, Math.max(0, MIN_MS - elapsed));
      });
    };
    if (document.readyState === "complete") reveal();
    else window.addEventListener("load", reveal);
    setTimeout(hide, 4500); // safety net if "load" never fires
  }

  /* ---------------------------------------------------------------
     NAV SCROLL STATE
  --------------------------------------------------------------- */
  function initNavScroll() {
    var nav = $("[data-nav]");
    if (!nav) return;
    var onScroll = function () { nav.classList.toggle("is-scrolled", window.scrollY > 40); };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
  }

  /* ---------------------------------------------------------------
     MENÚ MÓVIL — en pantallas chicas (≤1024px, ver style.css) los links
     y botones de la nav pasan a un panel que abre el botón hamburguesa.
     El botón se inserta acá para no repetir el markup en cada página.
  --------------------------------------------------------------- */
  function initMobileNav() {
    var nav = $("[data-nav]");
    var inner = nav && $(".nav-inner", nav);
    if (!inner) return;

    var toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "nav-toggle";
    toggle.setAttribute("aria-label", "Abrir menú");
    toggle.setAttribute("aria-expanded", "false");
    toggle.innerHTML = "<span></span><span></span><span></span>";
    var logo = $(".logo", inner);
    inner.insertBefore(toggle, logo ? logo.nextSibling : inner.firstChild);

    function setOpen(open) {
      nav.classList.toggle("is-menu-open", open);
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
      toggle.setAttribute("aria-label", open ? "Cerrar menú" : "Abrir menú");
    }
    toggle.addEventListener("click", function () { setOpen(!nav.classList.contains("is-menu-open")); });
    // Elegir un link o un botón del panel lo cierra
    inner.addEventListener("click", function (e) {
      if (e.target.closest(".nav-links a, .nav-actions a, .nav-actions button")) setOpen(false);
    });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") setOpen(false); });
    window.addEventListener("resize", function () { if (window.innerWidth > 1024) setOpen(false); });
  }

  function initScrollTopLinks() {
    $$("[data-scroll-top]").forEach(function (link) {
      link.addEventListener("click", function (e) {
        e.preventDefault();
        scrollToTarget(0);
      });
    });
  }

  /* ---------------------------------------------------------------
     VIEW TOGGLE (B2C <-> B2B)
  --------------------------------------------------------------- */
  function initViewToggle() {
    var body = document.body;
    var toggleBtn = $("[data-view-toggle]");
    var label = $("[data-view-toggle-label]");
    if (!toggleBtn) return;

    var panelB2c = $('[data-view-panel="b2c"]');
    var panelB2b = $('[data-view-panel="b2b"]');
    var navB2c = $('[data-nav-links="b2c"]');
    var navB2b = $('[data-nav-links="b2b"]');
    var ctaB2c = $("[data-nav-cta-b2c]");
    var ctaB2b = $("[data-nav-cta-b2b]");

    function applyView(next, silent) {
      body.dataset.view = next;
      if (panelB2c) panelB2c.hidden = next !== "b2c";
      if (panelB2b) panelB2b.hidden = next !== "b2b";
      if (navB2c) navB2c.hidden = next !== "b2c";
      if (navB2b) navB2b.hidden = next !== "b2b";
      if (ctaB2c) ctaB2c.hidden = next !== "b2c";
      if (ctaB2b) ctaB2b.hidden = next !== "b2b";
      label.textContent = next === "b2c" ? toggleBtn.dataset.labelB2c : toggleBtn.dataset.labelB2b;
      if (!silent) showToast(next === "b2b" ? "Modo Hoteles activado" : "Modo Viajeros activado");
    }

    // Arriving from catalogo.html's "Para Hoteles" link (index.html?view=b2b)
    // or a footer/nav link that targets a B2B anchor directly.
    if (new URLSearchParams(location.search).get("view") === "b2b") {
      applyView("b2b", true);
    }

    function swap() {
      var next = body.dataset.view === "b2c" ? "b2b" : "b2c";
      if (document.startViewTransition) {
        var t;
        try { t = document.startViewTransition(function () { applyView(next); }); }
        catch (err) { applyView(next); t = null; }
        // The transition's own animation can abort (tab loses visibility mid-click,
        // reduced motion, etc.) — the DOM swap above already happened either way,
        // so silence rejections instead of leaving an uncaught promise error.
        var noop = function () {};
        if (t) {
          if (t.ready) t.ready.catch(noop);
          if (t.updateCallbackDone) t.updateCallbackDone.catch(noop);
          if (t.finished) t.finished.catch(noop);
        }
      } else {
        applyView(next);
      }
    }

    toggleBtn.addEventListener("click", function () {
      if (window.scrollY < 4) { swap(); return; }
      var settled = false;
      var finish = function () {
        if (settled) return;
        settled = true;
        window.removeEventListener("scroll", onScroll);
        swap();
      };
      var onScroll = function () { if (window.scrollY <= 2) finish(); };
      window.addEventListener("scroll", onScroll, { passive: true });
      scrollToTarget(0);
      setTimeout(finish, 900);
    });
  }

  /* ---------------------------------------------------------------
     SPLIT TEXT (preserves <br> / inline tags)
  --------------------------------------------------------------- */
  function splitWordsPreserve(el) {
    if (el.dataset.splitDone) return;
    el.dataset.splitDone = "1";
    var i = 0;
    function walk(node) {
      if (node.nodeType === 3) {
        var words = node.textContent.split(/(\s+)/);
        var frag = document.createDocumentFragment();
        words.forEach(function (w) {
          if (w === "" ) return;
          if (/^\s+$/.test(w)) { frag.appendChild(document.createTextNode(w)); return; }
          var span = document.createElement("span");
          span.className = "split-word";
          span.style.transitionDelay = (i * 0.045) + "s";
          i++;
          var inner = document.createElement("span");
          inner.textContent = w;
          span.appendChild(inner);
          frag.appendChild(span);
        });
        node.parentNode.replaceChild(frag, node);
      } else if (node.nodeType === 1 && node.tagName !== "BR") {
        Array.prototype.slice.call(node.childNodes).forEach(walk);
      }
    }
    Array.prototype.slice.call(el.childNodes).forEach(walk);
  }

  function splitLines(el) {
    if (el.dataset.splitDone) return;
    el.dataset.splitDone = "1";
    var html = el.innerHTML;
    var parts = html.split(/<br\s*\/?>/i);
    el.innerHTML = parts.map(function (part, i) {
      return '<span class="split-line" style="transition-delay:' + (i * 0.1) + 's"><span>' + part + "</span></span>";
    }).join("<br>");
  }

  function initSplitText() {
    $$('[data-split="words"]').forEach(splitWordsPreserve);
    $$('[data-split="lines"]').forEach(splitLines);
  }

  /* ---------------------------------------------------------------
     SCROLL REVEAL
  --------------------------------------------------------------- */
  function initReveals() {
    var groups = $$(".catalog-list, .destacadas-grid, .about-grid, .benefits-grid, .testimonial-grid, .manifesto-steps");
    groups.forEach(function (group) {
      $$(":scope > *", group).forEach(function (child, i) {
        if (child.classList.contains("reveal")) child.style.transitionDelay = Math.min(i * 0.08, 0.4) + "s";
      });
    });

    var els = $$(".reveal");
    var reveal = function (el) {
      el.classList.add("is-visible");
      $$(".split-line, .split-word", el).forEach(function (s) { s.classList.add("is-visible"); });
      var counter = el.hasAttribute("data-count-to") ? el : el.querySelector("[data-count-to]");
      if (counter) startCount(counter);
    };

    if (!("IntersectionObserver" in window)) { els.forEach(reveal); return; }

    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) { reveal(entry.target); io.unobserve(entry.target); }
      });
    }, { threshold: 0.01, rootMargin: "0px 0px -2% 0px" });
    els.forEach(function (el) { io.observe(el); });

    // Safety net: on some setups the observer can miss a callback (GPU/compositing
    // quirks, throttled background tabs). Re-sweep on scroll so nothing stays stuck
    // at opacity:0 once it has visibly entered the viewport.
    var sweep = function () {
      $$(".reveal:not(.is-visible)").forEach(function (el) {
        var r = el.getBoundingClientRect();
        if (r.top < window.innerHeight && r.bottom > 0) reveal(el);
      });
    };
    var sweepTimer = null;
    var scheduleSweep = function () { clearTimeout(sweepTimer); sweepTimer = setTimeout(sweep, 150); };
    window.addEventListener("scroll", scheduleSweep, { passive: true });
    window.addEventListener("resize", scheduleSweep);
    setTimeout(sweep, 1200);
  }

  function startCount(el) {
    var to = parseFloat(el.dataset.countTo);
    if (isNaN(to)) return;
    var prefix = el.dataset.prefix || "";
    var suffix = el.dataset.suffix || "";
    var decimals = (String(el.dataset.countTo).split(".")[1] || "").length;
    var start = performance.now();
    var duration = 1400;
    var finished = false;
    function finalize() {
      if (finished) return;
      finished = true;
      el.textContent = prefix + to.toFixed(decimals).replace(".", ",") + suffix;
    }
    function tick(now) {
      if (finished) return;
      var p = Math.min(1, (now - start) / duration);
      var eased = 1 - Math.pow(1 - p, 3);
      var val = to * eased;
      el.textContent = prefix + val.toFixed(decimals).replace(".", ",") + suffix;
      if (p < 1) requestAnimationFrame(tick);
      else finalize();
    }
    setTimeout(finalize, duration + 150);
    requestAnimationFrame(tick);
  }

  /* ---------------------------------------------------------------
     TILT 3D + MAGNETIC
  --------------------------------------------------------------- */
  function initTilt() {
    if (!fineHover) return;
    $$("[data-tilt]").forEach(function (card) {
      var raf = null, rx = 0, ry = 0;
      card.addEventListener("mousemove", function (e) {
        var rect = card.getBoundingClientRect();
        var px = (e.clientX - rect.left) / rect.width;
        var py = (e.clientY - rect.top) / rect.height;
        rx = (py - 0.5) * -8;
        ry = (px - 0.5) * 8;
        card.style.setProperty("--mx", (px * 100) + "%");
        card.style.setProperty("--my", (py * 100) + "%");
        if (raf) return;
        raf = requestAnimationFrame(function () {
          card.style.transform = "perspective(1200px) rotateX(" + rx + "deg) rotateY(" + ry + "deg) translateY(-6px)";
          raf = null;
        });
      });
      card.addEventListener("mouseout", function (e) {
        if (card.contains(e.relatedTarget)) return;
        card.style.transform = "";
      });
    });
  }

  function initMagnetic() {
    if (!fineHover) return;
    $$("[data-magnetic]").forEach(function (btn) {
      btn.addEventListener("mousemove", function (e) {
        var rect = btn.getBoundingClientRect();
        var mx = (e.clientX - rect.left - rect.width / 2) * 0.22;
        var my = (e.clientY - rect.top - rect.height / 2) * 0.35;
        btn.style.transform = "translate(" + mx + "px," + my + "px)";
      });
      btn.addEventListener("mouseout", function (e) {
        if (btn.contains(e.relatedTarget)) return;
        btn.style.transform = "";
      });
    });
  }

  /* ---------------------------------------------------------------
     CHECKOUT DRAWER
  --------------------------------------------------------------- */
  function initCheckout() {
    var drawer = $("[data-checkout-drawer]");
    var backdrop = $("[data-drawer-backdrop]");
    if (!drawer || !backdrop) return;

    var FEE_CAP = 12.5;
    var currentCard = null;

    function open(card) {
      currentCard = card;
      var img = card.querySelector(".offer-media img");
      var coImg = $("[data-co-image]", drawer);
      coImg.src = img.src;
      coImg.alt = img.alt;
      $("[data-co-hotel]", drawer).textContent = card.dataset.hotel;
      $("[data-co-location]", drawer).textContent = card.dataset.location;
      $("[data-co-dates]", drawer).textContent = card.dataset.checkin + " – " + card.dataset.checkout;
      $("[data-co-nights]", drawer).textContent = card.dataset.nights + " noches";
      var guestsEl = $("[data-co-guests]", drawer);
      guestsEl.textContent = card.dataset.guests ? card.dataset.guests + " huéspedes" : "";
      guestsEl.hidden = !card.dataset.guests;
      var regimenEl = $("[data-co-regimen]", drawer);
      regimenEl.textContent = card.dataset.regimen || "";
      regimenEl.hidden = !card.dataset.regimen;

      var base = Number(card.dataset.price);
      var feePct = Math.min(FEE_CAP, Number(card.dataset.feePct));
      var guarantee = base * (feePct / 100);
      var total = base + guarantee;
      var gaugePct = Math.min(100, (feePct / FEE_CAP) * 100);
      var feeLabel = feePct.toString().replace(".", ",") + "%";

      $("[data-co-base]", drawer).textContent = formatMoney(base);
      $("[data-co-fee-pct]", drawer).textContent = feeLabel;
      $("[data-co-fee-pct-2]", drawer).textContent = feeLabel;
      $("[data-co-guarantee]", drawer).textContent = "+" + formatMoney(guarantee);
      $("[data-co-total]", drawer).textContent = formatMoney(total);
      $("[data-co-gauge]", drawer).style.width = "0%";
      nextFrame(function () {
        nextFrame(function () { $("[data-co-gauge]", drawer).style.width = gaugePct + "%"; });
      });

      var confirmBtn = $("[data-co-confirm]", drawer);
      confirmBtn.disabled = false;
      confirmBtn.querySelector("span").textContent = "Confirmar reserva";
      var success = $("[data-co-success]", drawer);
      success.hidden = true;
      success.classList.remove("is-visible");

      drawer.hidden = false;
      backdrop.hidden = false;
      nextFrame(function () {
        drawer.classList.add("is-open");
        backdrop.classList.add("is-open");
      });
      setPageScrollLocked(true);
    }

    function close() {
      drawer.classList.remove("is-open");
      backdrop.classList.remove("is-open");
      setPageScrollLocked(false);
      setTimeout(function () { drawer.hidden = true; backdrop.hidden = true; }, 560);
    }

    $$("[data-reserve]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var card = btn.closest(".offer-card, .destacada-card, .detail-summary");
        if (card) open(card);
      });
    });

    var closeBtn = $("[data-drawer-close]");
    if (closeBtn) closeBtn.addEventListener("click", close);
    backdrop.addEventListener("click", close);
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && drawer.classList.contains("is-open")) close();
    });

    var confirmBtn = $("[data-co-confirm]");
    if (confirmBtn) {
      confirmBtn.addEventListener("click", function () {
        if (!currentCard || !api) return;
        if (!api.isLoggedIn()) {
          close();
          requireLogin("Iniciá sesión para confirmar tu compra");
          return;
        }
        var label = confirmBtn.querySelector("span");
        confirmBtn.disabled = true;
        label.textContent = "Confirmando…";
        var d = currentCard.dataset;
        api.purchase(Number(d.listingId), d.checkinIso, d.checkoutIso).then(function (purchase) {
          label.textContent = "Reserva confirmada";
          var success = $("[data-co-success]");
          success.hidden = false;
          nextFrame(function () { success.classList.add("is-visible"); });
          showToast("Compra confirmada — el pago queda retenido hasta tu check-in");
          document.dispatchEvent(new CustomEvent("libera:purchased", { detail: purchase }));
        }).catch(function (err) {
          confirmBtn.disabled = false;
          label.textContent = "Confirmar reserva";
          showToast(err.message);
        });
      });
    }
  }

  /* ---------------------------------------------------------------
     OFFER DETAIL PAGE (oferta.html) — carga la publicación desde la API.
     ?id=<número> es el id de la publicación; ?id=<slug> (links de las
     tarjetas fijas de index.html) busca la publicación activa de ese hotel.
  --------------------------------------------------------------- */
  function loadListingFromQuery() {
    var id = new URLSearchParams(location.search).get("id");
    if (!id || !api) return Promise.reject(new Error("Esta publicación no existe."));
    if (/^\d+$/.test(id)) return api.listing(id);
    return api.listings().then(function (all) {
      var match = all.filter(function (l) { return l.hotelSlug === id; })[0];
      if (!match) throw new Error("Esta reserva ya no está disponible.");
      return match;
    });
  }

  function initOfferDetail() {
    var section = $(".offer-detail");
    if (!section) return;

    var split = null;
    var summary = $("[data-summary-card]");

    function showUnavailable(message) {
      section.innerHTML =
        '<div class="detail-unavailable">' +
          '<h1>Publicación no disponible</h1><p></p>' +
          '<a href="catalogo.html" class="btn btn-primary">Ver otras ofertas</a>' +
        '</div>';
      section.querySelector("p").textContent = message;
    }

    function render(l) {
      var v = listingView(l);
      document.title = v.hotel + " — LIBERA";

      var heroImg = $("[data-detail-hero-img]");
      heroImg.src = v.images[0].src;
      heroImg.alt = v.images[0].alt;
      var discountFloat = $("[data-detail-discount-floating]");
      if (discountFloat) discountFloat.textContent = "-" + v.discountPct + "%";

      var thumbsWrap = $("[data-detail-thumbs]");
      thumbsWrap.innerHTML = "";
      v.images.slice(1).forEach(function (im) {
        var thumb = document.createElement("img");
        thumb.src = im.src; thumb.alt = im.alt; thumb.loading = "lazy"; thumb.decoding = "async";
        thumbsWrap.appendChild(thumb);
      });

      $("[data-detail-hotel]").textContent = v.hotel;
      var ratingEl = $("[data-detail-rating]");
      ratingEl.hidden = v.rating == null;
      if (v.rating != null) ratingEl.textContent = "★ " + v.rating.toFixed(1);
      $("[data-detail-location]").textContent = v.location;

      var tagsWrap = $("[data-detail-tags]");
      tagsWrap.innerHTML = "";
      [v.regimen, v.roomType, l.allowsSplitBooking ? "Admite noches sueltas" : ""].filter(Boolean).forEach(function (t) {
        var li = document.createElement("li");
        li.textContent = t;
        tagsWrap.appendChild(li);
      });

      var amenitiesWrap = $("[data-detail-amenities]");
      amenitiesWrap.innerHTML = "";
      amenitiesWrap.closest(".detail-section").hidden = !v.amenities.length;
      v.amenities.forEach(function (a) {
        var row = document.createElement("div");
        row.className = "detail-amenity";
        row.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><use href="#icon-' + a.icon + '"></use></svg><span></span>';
        row.querySelector("span").textContent = a.label;
        amenitiesWrap.appendChild(row);
      });

      $("[data-detail-description]").textContent = v.description;

      $("[data-stay-checkin]").textContent = v.checkin;
      $("[data-stay-checkout]").textContent = v.checkout;
      $("[data-stay-nights]").textContent = plural(v.nights, "noche", "noches");
      var guestsEl = $("[data-stay-guests]");
      guestsEl.closest(".stay-detail").hidden = !v.guests;
      if (v.guests) guestsEl.textContent = plural(v.guests, "huésped", "huéspedes");
      var regimenEl = $("[data-stay-regimen]");
      regimenEl.closest(".stay-detail").hidden = !v.regimen;
      regimenEl.textContent = v.regimen;

      // La tarjeta de resumen lleva el mismo dataset que leía el drawer de checkout (initCheckout)
      var ds = summary.dataset;
      ds.listingId = l.id;
      ds.checkinIso = v.checkinISO;
      ds.checkoutIso = v.checkoutISO;
      ds.hotel = v.hotel;
      ds.location = v.location;
      ds.checkin = v.checkin;
      ds.checkout = v.checkout;
      ds.nights = v.nights;
      ds.guests = v.guests || "";
      ds.regimen = v.regimen;
      ds.price = v.price;
      ds.feePct = v.feePct;
      var summaryImg = summary.querySelector(".offer-media img");
      summaryImg.src = v.images[0].src;
      summaryImg.alt = v.images[0].alt;

      $("[data-summary-was]").textContent = formatMoney(v.original);
      $("[data-summary-now]").textContent = formatMoney(v.price);
      $("[data-summary-discount]").textContent = "-" + v.discountPct + "%";

      var FEE_CAP = 12.5;
      var feePct = Math.min(FEE_CAP, v.feePct);
      var guarantee = v.price * (feePct / 100);
      var gaugePct = Math.min(100, (feePct / FEE_CAP) * 100);
      var feeLabel = feePct.toString().replace(".", ",") + "%";
      $("[data-summary-fee-pct]").textContent = feeLabel;
      $("[data-summary-fee-pct-2]").textContent = feeLabel;
      $("[data-summary-base]").textContent = formatMoney(v.price);
      $("[data-summary-guarantee]").textContent = "+" + formatMoney(guarantee);
      $("[data-summary-total]").textContent = formatMoney(v.price + guarantee);
      nextFrame(function () {
        nextFrame(function () { $("[data-summary-gauge]").style.width = gaugePct + "%"; });
      });

      renderAvailability(v);
    }

    // Habilita o bloquea "Reservar completa" y la compra por noches según el estado real
    function renderAvailability(v) {
      var l = v.listing;
      var reserveBtn = $("[data-reserve]", summary);
      var reserveLabel = reserveBtn.querySelector("span");
      var forSale = l.status === "ACTIVE" || l.status === "PARTIALLY_SOLD";
      var blocked = null;
      if (v.isOwn) blocked = "Esta es tu publicación";
      else if (l.status === "SOLD_OUT") blocked = "Reserva vendida";
      else if (!forSale) blocked = "Publicación no disponible";
      else if (v.soldRanges.length) blocked = "Algunas noches ya se vendieron";

      reserveBtn.disabled = !!blocked;
      reserveLabel.textContent = blocked || "Reservar esta estadía completa";

      var splitForm = $("[data-split-form]");
      var splitPossible = forSale && !v.isOwn && l.allowsSplitBooking;
      if (splitForm) {
        splitForm.hidden = !splitPossible;
        if (splitPossible) {
          if (!split) split = initSplitBooking(splitForm);
          split.update(v);
        }
      }
    }

    function reload() {
      return loadListingFromQuery().then(render);
    }

    reload().catch(function (err) { showUnavailable(err.message); });

    // Después de una compra (completa o por noches) se recarga la disponibilidad real
    document.addEventListener("libera:purchased", function () {
      reload().catch(function () { /* el detalle ya mostrado sigue siendo válido */ });
    });
    if (api) api.onSessionChange(function () { reload().catch(function () {}); });
  }

  /* ---------------------------------------------------------------
     SPLIT BOOKING (Reserva Fraccionada) — oferta.html only.
     El comprador elige un sub-rango de noches dentro de la estadía y
     las compra a un precio proporcional al publicado. Las noches ya
     vendidas aparecen tachadas y no se pueden elegir.
  --------------------------------------------------------------- */
  function initSplitBooking(form) {
    function nightsBetween(a, b) { return Math.round((b - a) / 86400000); }
    function fmtShort(d) { return d.getDate() + " " + MESES_ABBR[d.getMonth()]; }
    function addDays(d, n) { var x = new Date(d); x.setDate(x.getDate() + n); return x; }

    var trigger = $("[data-split-date-trigger]", form);
    var panel = $("[data-split-calendar-panel]", form);
    var grid = $("[data-split-cal-grid]", form);
    var monthLabel = $("[data-split-cal-month-label]", form);
    var rangeLabel = $("[data-split-cal-range-label]", form);
    var applyBtn = $("[data-split-cal-apply]", form);
    var cancelBtn = $("[data-split-cal-cancel]", form);
    var dateLabel = $("[data-split-date-label]", form);
    var nightsLabel = $("[data-split-nights-label]", form);
    var priceInput = $("[data-split-price-input]", form);
    var roomLine = $("[data-split-room-line]", form);
    var submitBtn = $("[data-split-submit]", form);

    var view = null;
    var checkinDate = null, checkoutDate = null;
    var sold = []; // [{start, end}] en Date
    var dayButtons = {};
    // committed = selección confirmada (maneja el campo y el precio); pending = lo que se elige con el panel abierto
    var committed = { start: null, end: null };
    var pending = { start: null, end: null };

    function nightIsSold(d) {
      return sold.some(function (r) { return d >= r.start && d < r.end; });
    }
    function rangeIsFree(start, end) {
      for (var d = new Date(start); d < end; d = addDays(d, 1)) { if (nightIsSold(d)) return false; }
      return true;
    }
    // Primer bloque continuo de noches libres: es la selección inicial
    function firstFreeRun() {
      var start = null;
      for (var d = new Date(checkinDate); d < checkoutDate; d = addDays(d, 1)) {
        if (!nightIsSold(d)) { if (!start) start = new Date(d); }
        else if (start) return { start: start, end: new Date(d) };
      }
      return start ? { start: start, end: new Date(checkoutDate) } : null;
    }

    // La estadía puede cruzar de mes: se dibujan todos los meses en la misma grilla
    function buildGrid() {
      grid.innerHTML = "";
      dayButtons = {};
      var first = new Date(checkinDate.getFullYear(), checkinDate.getMonth(), 1);
      var last = new Date(checkoutDate.getFullYear(), checkoutDate.getMonth(), 1);
      monthLabel.textContent = first.getTime() === last.getTime()
        ? MESES[first.getMonth()] + " " + first.getFullYear()
        : MESES[first.getMonth()] + " – " + MESES[last.getMonth()] + " " + last.getFullYear();

      for (var m = new Date(first); m <= last; m = new Date(m.getFullYear(), m.getMonth() + 1, 1)) {
        if (m > first) {
          var sep = document.createElement("div");
          sep.className = "cal-month-sep";
          sep.textContent = MESES[m.getMonth()] + " " + m.getFullYear();
          grid.appendChild(sep);
        }
        var leading = (m.getDay() + 6) % 7; // lunes primero
        for (var i = 0; i < leading; i++) grid.appendChild(document.createElement("span"));
        var daysInMonth = new Date(m.getFullYear(), m.getMonth() + 1, 0).getDate();
        for (var d = 1; d <= daysInMonth; d++) {
          var date = new Date(m.getFullYear(), m.getMonth(), d);
          var btn = document.createElement("button");
          btn.type = "button";
          btn.className = "cal-day";
          btn.textContent = String(d);
          var inStay = date >= checkinDate && date <= checkoutDate;
          var isSoldNight = date < checkoutDate && nightIsSold(date);
          if (isSoldNight) { btn.classList.add("is-sold"); btn.title = "Noche ya vendida"; }
          if (!inStay) btn.disabled = true;
          else {
            btn.dataset.date = isoOfDate(date);
            btn.addEventListener("click", function () { onDayClick(parseISODate(this.dataset.date)); });
            dayButtons[btn.dataset.date] = btn;
          }
          grid.appendChild(btn);
        }
      }
    }

    function renderHighlight() {
      Object.keys(dayButtons).forEach(function (iso) {
        var btn = dayButtons[iso];
        var date = parseISODate(iso);
        var isEdge = (pending.start && date.getTime() === pending.start.getTime()) || (pending.end && date.getTime() === pending.end.getTime());
        var inRange = pending.start && pending.end && date > pending.start && date < pending.end;
        btn.classList.toggle("is-selected", !!isEdge);
        btn.classList.toggle("is-in-range", !!inRange);
      });
    }

    function updateRangeLabel() {
      if (pending.start && pending.end) {
        var free = rangeIsFree(pending.start, pending.end);
        rangeLabel.textContent = free
          ? fmtShort(pending.start) + " – " + fmtShort(pending.end) + " · " + plural(nightsBetween(pending.start, pending.end), "noche", "noches")
          : "Ese rango incluye noches ya vendidas";
        applyBtn.disabled = !free;
      } else {
        rangeLabel.textContent = (pending.start ? fmtShort(pending.start) + " · " : "") + "elegí el checkout";
        applyBtn.disabled = true;
      }
    }

    function onDayClick(date) {
      if (!pending.start || (pending.start && pending.end) || date <= pending.start) {
        // el día de entrada tiene que ser una noche libre de la estadía
        if (date >= checkoutDate || nightIsSold(date)) return;
        pending.start = date; pending.end = null;
      } else {
        pending.end = date;
      }
      renderHighlight();
      updateRangeLabel();
    }

    function updateFieldDisplay() {
      if (!committed.start) {
        dateLabel.textContent = "Sin noches disponibles";
        nightsLabel.textContent = "";
        priceInput.value = "";
        submitBtn.disabled = true;
        return;
      }
      var n = nightsBetween(committed.start, committed.end);
      dateLabel.textContent = fmtShort(committed.start) + " – " + fmtShort(committed.end);
      nightsLabel.textContent = plural(n, "noche", "noches");
      // Mismo cálculo que el backend: proporcional a las noches sobre el precio publicado
      priceInput.value = Math.round((view.price * n / view.nights) * 100) / 100;
      submitBtn.disabled = false;
    }

    function openPanel() {
      pending.start = committed.start; pending.end = committed.end;
      renderHighlight();
      updateRangeLabel();
      panel.hidden = false;
      trigger.classList.add("is-open");
    }
    function closePanel() {
      panel.hidden = true;
      trigger.classList.remove("is-open");
    }

    trigger.addEventListener("click", function (e) {
      e.stopPropagation();
      if (panel.hidden) openPanel(); else closePanel();
    });
    panel.addEventListener("click", function (e) { e.stopPropagation(); });
    document.addEventListener("click", function () { if (!panel.hidden) closePanel(); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape" && !panel.hidden) closePanel(); });

    cancelBtn.addEventListener("click", function () { closePanel(); });
    applyBtn.addEventListener("click", function () {
      if (!pending.start || !pending.end || !rangeIsFree(pending.start, pending.end)) return;
      committed.start = pending.start; committed.end = pending.end;
      closePanel();
      updateFieldDisplay();
    });

    form.addEventListener("submit", function (e) {
      e.preventDefault();
      if (!committed.start) return;
      if (!requireLogin("Iniciá sesión para comprar estas noches")) return;
      var label = submitBtn.querySelector("span");
      var original = label.textContent;
      submitBtn.disabled = true;
      label.textContent = "Comprando…";
      api.purchase(view.id, isoOfDate(committed.start), isoOfDate(committed.end)).then(function (purchase) {
        label.textContent = original;
        var success = $("[data-split-success]", form);
        success.hidden = false;
        nextFrame(function () { success.classList.add("is-visible"); });
        showToast("Compraste " + plural(purchase.nights, "noche", "noches") + " — el pago queda retenido hasta tu check-in");
        document.dispatchEvent(new CustomEvent("libera:purchased", { detail: purchase }));
      }).catch(function (err) {
        label.textContent = original;
        submitBtn.disabled = false;
        showToast(err.message);
      });
    });

    return {
      update: function (v) {
        view = v;
        checkinDate = parseISODate(v.checkinISO);
        checkoutDate = parseISODate(v.checkoutISO);
        sold = v.soldRanges.map(function (r) { return { start: parseISODate(r.checkIn), end: parseISODate(r.checkOut) }; });
        roomLine.textContent = [v.roomType, v.guests ? plural(v.guests, "adulto", "adultos") : "", v.regimen].filter(Boolean).join(" · ");
        buildGrid();
        // si lo elegido ya no está libre (otra compra), se vuelve al primer bloque libre
        if (!committed.start || !rangeIsFree(committed.start, committed.end)) {
          var run = firstFreeRun();
          committed.start = run ? run.start : null;
          committed.end = run ? run.end : null;
        }
        updateFieldDisplay();
      }
    };
  }

  /* ---------------------------------------------------------------
     CATALOG (search + filters + sort)
  --------------------------------------------------------------- */
  // Tarjeta del catálogo a partir de una publicación de la API. Los data-* son los
  // mismos que tenían las tarjetas fijas, así los filtros y el orden no cambian.
  function buildOfferCard(v) {
    var l = v.listing;
    var card = document.createElement("article");
    card.className = "offer-card";
    var ds = card.dataset;
    ds.id = l.id;
    ds.hotel = v.hotel;
    ds.location = v.location;
    ds.country = v.country;
    ds.checkinMonth = v.checkinMonth;
    ds.checkin = v.checkin;
    ds.checkout = v.checkout;
    ds.nights = v.nights;
    ds.guests = v.guests || "";
    ds.regimen = v.regimen;
    ds.price = v.price;
    ds.original = v.original;
    ds.feePct = v.feePct;
    ds.rating = v.rating || 0;

    var tag = l.status === "PARTIALLY_SOLD" ? "Quedan noches sueltas" : (l.allowsSplitBooking ? "Admite noches sueltas" : "");
    card.innerHTML =
      '<div class="offer-media">' +
        '<img loading="lazy" decoding="async" alt="">' +
        '<span class="offer-badge offer-badge-discount"></span>' +
        (tag ? '<span class="offer-badge offer-badge-tag"></span>' : "") +
      '</div>' +
      '<div class="offer-body">' +
        '<div class="offer-top"><h3></h3>' + (v.rating ? '<span class="offer-rating"></span>' : "") + '</div>' +
        '<p class="offer-location"></p>' +
        '<p class="offer-desc"></p>' +
        '<ul class="offer-meta"></ul>' +
        '<div class="offer-foot">' +
          '<div class="offer-price"><span class="was"></span><span class="now"></span></div>' +
          '<a class="btn btn-primary btn-sm">Reservar</a>' +
        '</div>' +
      '</div>';
    var img = $(".offer-media img", card);
    img.src = v.images[0].src;
    img.alt = v.images[0].alt;
    $(".offer-badge-discount", card).textContent = "-" + v.discountPct + "%";
    if (tag) $(".offer-badge-tag", card).textContent = tag;
    $("h3", card).textContent = v.hotel;
    if (v.rating) $(".offer-rating", card).textContent = "★ " + v.rating.toFixed(1);
    $(".offer-location", card).textContent = v.location;
    $(".offer-desc", card).textContent = v.summary;
    var meta = $(".offer-meta", card);
    [fmtRangeShort(v.checkinISO, v.checkoutISO), plural(v.nights, "noche", "noches"),
      v.guests ? plural(v.guests, "huésped", "huéspedes") : "", v.regimen].filter(Boolean).forEach(function (t) {
      var li = document.createElement("li");
      li.textContent = t;
      meta.appendChild(li);
    });
    $(".offer-price .was", card).textContent = formatMoney(v.original);
    $(".offer-price .now", card).textContent = formatMoney(v.price);
    $(".offer-foot a", card).href = "oferta.html?id=" + l.id;
    return card;
  }

  function initCatalog() {
    var section = $("#marketplace");
    var list = $("[data-catalog-list]");
    if (!section || !list) return;

    // En pantallas chicas los filtros arrancan plegados (style.css) y este botón los abre
    var filtersBox = $(".catalog-filters");
    var filtersHead = filtersBox && $(".catalog-filters-head", filtersBox);
    if (filtersHead) {
      var filtersToggle = document.createElement("button");
      filtersToggle.type = "button";
      filtersToggle.className = "catalog-filters-toggle";
      filtersToggle.setAttribute("aria-expanded", "false");
      filtersToggle.textContent = "Mostrar filtros";
      filtersHead.appendChild(filtersToggle);
      filtersToggle.addEventListener("click", function () {
        var open = filtersBox.classList.toggle("is-open");
        filtersToggle.setAttribute("aria-expanded", open ? "true" : "false");
        filtersToggle.textContent = open ? "Ocultar filtros" : "Mostrar filtros";
      });
    }
    var cards = [];
    var countEl = $("[data-catalog-count]");
    var emptyEl = $("[data-catalog-empty]");
    var sortSelect = $("[data-catalog-sort]");
    var clearBtn = $("[data-catalog-clear]");

    // Populated only when "Buscar" (the OTA bar) is actually pressed — merely
    // opening a dropdown or picking a month shouldn't retroactively filter
    // a catalog the user hasn't asked to search yet.
    var activeSearch = { country: "", month: "", minGuests: 0 };
    var defaultEmptyText = emptyEl ? emptyEl.textContent : "";

    function checkedValues(attr) {
      return $$("[" + attr + "]:checked").map(function (i) { return i.value; });
    }
    function activePriceRange() {
      var checked = document.querySelector("[data-filter-price]:checked");
      if (!checked || !checked.value) return null;
      var parts = checked.value.split("-").map(Number);
      return { min: parts[0], max: parts[1] };
    }

    function applyFilters() {
      var destinos = checkedValues("data-filter-destino");
      var regimenes = checkedValues("data-filter-regimen");
      var priceRange = activePriceRange();

      var visible = 0;
      cards.forEach(function (card) {
        var ok = true;
        if (destinos.length && destinos.indexOf(card.dataset.country) === -1) ok = false;
        if (regimenes.length && regimenes.indexOf(card.dataset.regimen) === -1) ok = false;
        if (priceRange) {
          var price = Number(card.dataset.price);
          if (price < priceRange.min || price > priceRange.max) ok = false;
        }
        if (activeSearch.country && card.dataset.country !== activeSearch.country) ok = false;
        if (activeSearch.month && card.dataset.checkinMonth !== activeSearch.month) ok = false;
        if (activeSearch.minGuests && Number(card.dataset.guests) < activeSearch.minGuests) ok = false;

        card.hidden = !ok;
        if (ok) visible++;
      });

      if (countEl) countEl.textContent = String(visible);
      if (emptyEl) { emptyEl.hidden = visible > 0; emptyEl.textContent = defaultEmptyText; }
      list.hidden = visible === 0;
    }

    // Los contadores de cada filtro (<em>) reflejan las ofertas reales
    function updateFilterCounts() {
      $$("[data-filter-destino]").forEach(function (input) {
        var n = cards.filter(function (c) { return c.dataset.country === input.value; }).length;
        var em = input.parentElement.querySelector("em");
        if (em) em.textContent = String(n);
      });
      $$("[data-filter-regimen]").forEach(function (input) {
        var n = cards.filter(function (c) { return c.dataset.regimen === input.value; }).length;
        var em = input.parentElement.querySelector("em");
        if (em) em.textContent = String(n);
      });
    }

    function applySort() {
      var val = sortSelect.value;
      var sorted = cards.slice().sort(function (a, b) {
        if (val === "price-asc") return Number(a.dataset.price) - Number(b.dataset.price);
        if (val === "price-desc") return Number(b.dataset.price) - Number(a.dataset.price);
        if (val === "rating") return Number(b.dataset.rating) - Number(a.dataset.rating);
        return 0;
      });
      sorted.forEach(function (card) { list.appendChild(card); });
    }

    function revealCatalog(scroll) {
      if (section.hidden) section.hidden = false;
      if (scroll !== false) {
        nextFrame(function () { scrollToTarget(section); });
      }
    }

    // Any link that points at the catalog (nav "Ofertas", hero "Comprar
    // reservas", the "Ver el catálogo completo" CTA) just reveals + scrolls,
    // without touching whatever filters are already active.
    $$('a[href="#marketplace"]').forEach(function (a) {
      a.addEventListener("click", function (e) {
        e.preventDefault();
        revealCatalog();
      });
    });

    var otaBtn = $("[data-ota-search-btn]");
    if (otaBtn) {
      otaBtn.addEventListener("click", function () {
        activeSearch.country = otaState.country;
        activeSearch.month = otaState.month;
        activeSearch.minGuests = otaState.adults;
        $$("[data-filter-destino]").forEach(function (cb) {
          cb.checked = !!otaState.country && cb.value === otaState.country;
        });
        applyFilters();
        revealCatalog();
      });
    }

    $$("[data-filter-destino], [data-filter-regimen], [data-filter-price]").forEach(function (input) {
      input.addEventListener("change", applyFilters);
    });
    if (sortSelect) sortSelect.addEventListener("change", applySort);
    if (clearBtn) {
      clearBtn.addEventListener("click", function () {
        $$("[data-filter-destino], [data-filter-regimen]").forEach(function (i) { i.checked = false; });
        var anyPrice = document.querySelector('[data-filter-price][value=""]');
        if (anyPrice) anyPrice.checked = true;
        activeSearch.country = "";
        activeSearch.month = "";
        activeSearch.minGuests = 0;

        // Keep the OTA bar's own displayed state (and otaState, which a later
        // "Buscar" click reads from) in sync — otherwise a stale month/destino
        // picked before this Limpiar silently re-applies on the next search.
        otaState.country = "";
        otaState.month = "";
        otaState.adults = 2;
        otaState.rooms = 1;
        $$(".ota-month").forEach(function (b) { b.classList.remove("is-active"); });
        var destinoValueEl = $('[data-ota-value="destino"]');
        if (destinoValueEl) destinoValueEl.textContent = "Buscar por destino, ciudad u hotel";
        var fechasValueEl = $('[data-ota-value="fechas"]');
        if (fechasValueEl) fechasValueEl.textContent = "Elegí un mes";
        var huespedesValueEl = $('[data-ota-value="huespedes"]');
        if (huespedesValueEl) huespedesValueEl.textContent = "2 adultos · 1 habitación";
        var adultsValueEl = $('[data-ota-stepper-value="adults"]');
        if (adultsValueEl) adultsValueEl.textContent = "2";
        var roomsValueEl = $('[data-ota-stepper-value="rooms"]');
        if (roomsValueEl) roomsValueEl.textContent = "1";

        applyFilters();
      });
    }

    // Arriving from index.html's "Buscar" (new tab, ?destino=&mes=&adultos=):
    // seed both the sidebar checkboxes and the OTA bar's own field labels so
    // the page looks like the search already ran, not just the results.
    (function seedFromQuery() {
      var qs = new URLSearchParams(location.search);
      var destino = qs.get("destino");
      var mes = qs.get("mes");
      var adultos = Number(qs.get("adultos") || 0);
      if (!destino && !mes && !adultos) return;

      if (destino) {
        activeSearch.country = destino;
        otaState.country = destino;
        $$("[data-filter-destino]").forEach(function (cb) { cb.checked = cb.value === destino; });
        var li = $$("[data-ota-destino-list] li").filter(function (i) { return i.dataset.country === destino; })[0];
        var destinoValueEl = $('[data-ota-value="destino"]');
        if (li && destinoValueEl) destinoValueEl.textContent = li.dataset.label;
      }
      if (mes) {
        activeSearch.month = mes;
        otaState.month = mes;
        var monthBtns = $$(".ota-month");
        monthBtns.forEach(function (b) { b.classList.toggle("is-active", b.dataset.month === mes); });
        var active = monthBtns.filter(function (b) { return b.dataset.month === mes; })[0];
        var fechasValueEl = $('[data-ota-value="fechas"]');
        if (active && fechasValueEl) {
          var name = $(".ota-month-name", active).textContent;
          var year = $(".ota-month-year", active).textContent;
          fechasValueEl.textContent = name.charAt(0).toUpperCase() + name.slice(1) + " " + year;
        }
      }
      if (adultos) {
        activeSearch.minGuests = adultos;
        otaState.adults = adultos;
        var adultsValueEl = $('[data-ota-stepper-value="adults"]');
        if (adultsValueEl) adultsValueEl.textContent = adultos;
        var huespedesValueEl = $('[data-ota-value="huespedes"]');
        if (huespedesValueEl) {
          huespedesValueEl.textContent = adultos + (adultos === 1 ? " adulto · " : " adultos · ") +
            otaState.rooms + (otaState.rooms === 1 ? " habitación" : " habitaciones");
        }
      }
    })();

    applyFilters();

    catalogReady = (api ? api.listings() : Promise.reject(new Error("api.js no está cargado"))).then(function (listings) {
      list.innerHTML = "";
      cards = listings.map(function (l) { return buildOfferCard(listingView(l)); });
      cards.forEach(function (card) { list.appendChild(card); });
      updateFilterCounts();
      if (sortSelect) applySort();
      applyFilters();
    }).catch(function (err) {
      cards = [];
      applyFilters();
      if (countEl) countEl.textContent = "0";
      if (emptyEl) { emptyEl.hidden = false; emptyEl.textContent = err.message; }
    });
  }

  /* ---------------------------------------------------------------
     OTA SEARCH BAR — "Buscar" opens catalogo.html in a new tab
     (only relevant on index.html, which has no #marketplace of its own;
     on catalogo.html itself, initCatalog() wires the same button to
     filter in place instead — see the guard below).
  --------------------------------------------------------------- */
  function initOtaSearchRedirect() {
    var otaBtn = $("[data-ota-search-btn]");
    if (!otaBtn) return;
    if ($("#marketplace")) return;
    otaBtn.addEventListener("click", function () {
      var params = [];
      if (otaState.country) params.push("destino=" + encodeURIComponent(otaState.country));
      if (otaState.month) params.push("mes=" + encodeURIComponent(otaState.month));
      if (otaState.adults) params.push("adultos=" + encodeURIComponent(otaState.adults));
      var url = "catalogo.html" + (params.length ? "?" + params.join("&") : "");
      window.open(url, "_blank", "noopener");
    });
  }

  /* ---------------------------------------------------------------
     OTA SEARCH BAR (Dónde / Cuándo / Cuántos)
  --------------------------------------------------------------- */
  function initOtaSearch() {
    var wrap = $("[data-ota-search]");
    if (!wrap) return;
    var triggers = $$("[data-ota-trigger]", wrap);

    function panelFor(name) { return $('[data-ota-panel="' + name + '"]', wrap); }
    function closeAll() {
      triggers.forEach(function (t) {
        t.classList.remove("is-open");
        var p = panelFor(t.dataset.otaTrigger);
        if (p) p.hidden = true;
      });
    }

    triggers.forEach(function (t) {
      t.addEventListener("click", function (e) {
        e.stopPropagation();
        var panel = panelFor(t.dataset.otaTrigger);
        var wasOpen = t.classList.contains("is-open");
        closeAll();
        if (!wasOpen && panel) { panel.hidden = false; t.classList.add("is-open"); }
      });
    });
    wrap.addEventListener("click", function (e) { e.stopPropagation(); });
    document.addEventListener("click", closeAll);
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") closeAll(); });

    var destinoValueEl = $('[data-ota-value="destino"]', wrap);
    var destinoInput = $("[data-ota-destino-input]", wrap);
    var destinoItems = $$("[data-ota-destino-list] li", wrap);
    destinoItems.forEach(function (li) {
      li.addEventListener("click", function () {
        otaState.country = li.dataset.country;
        destinoValueEl.textContent = li.dataset.label;
        closeAll();
      });
    });
    if (destinoInput) {
      destinoInput.addEventListener("input", function () {
        var q = destinoInput.value.trim().toLowerCase();
        destinoItems.forEach(function (li) {
          li.hidden = q.length > 0 && li.textContent.toLowerCase().indexOf(q) === -1;
        });
      });
    }

    var fechasValueEl = $('[data-ota-value="fechas"]', wrap);
    var monthButtons = $$(".ota-month", wrap);
    monthButtons.forEach(function (btn) {
      btn.addEventListener("click", function () {
        monthButtons.forEach(function (b) { b.classList.remove("is-active"); });
        btn.classList.add("is-active");
        otaState.month = btn.dataset.month;
        var name = $(".ota-month-name", btn).textContent;
        var year = $(".ota-month-year", btn).textContent;
        fechasValueEl.textContent = name.charAt(0).toUpperCase() + name.slice(1) + " " + year;
        closeAll();
      });
    });
    var monthsAll = $("[data-ota-months-all]", wrap);
    if (monthsAll) {
      monthsAll.addEventListener("click", function (e) {
        e.preventDefault();
        monthButtons.forEach(function (b) { b.classList.remove("is-active"); });
        otaState.month = "";
        fechasValueEl.textContent = "Cualquier mes";
        closeAll();
      });
    }

    var huespedesValueEl = $('[data-ota-value="huespedes"]', wrap);
    function updateHuespedesLabel() {
      huespedesValueEl.textContent =
        otaState.adults + (otaState.adults === 1 ? " adulto · " : " adultos · ") +
        otaState.rooms + (otaState.rooms === 1 ? " habitación" : " habitaciones");
    }
    $$("[data-ota-stepper-plus]", wrap).forEach(function (btn) {
      btn.addEventListener("click", function () {
        var key = btn.dataset.otaStepperPlus;
        var max = key === "adults" ? 8 : 4;
        otaState[key] = Math.min(max, otaState[key] + 1);
        $('[data-ota-stepper-value="' + key + '"]', wrap).textContent = otaState[key];
        updateHuespedesLabel();
      });
    });
    $$("[data-ota-stepper-minus]", wrap).forEach(function (btn) {
      btn.addEventListener("click", function () {
        var key = btn.dataset.otaStepperMinus;
        otaState[key] = Math.max(1, otaState[key] - 1);
        $('[data-ota-stepper-value="' + key + '"]', wrap).textContent = otaState[key];
        updateHuespedesLabel();
      });
    });
  }

  /* ---------------------------------------------------------------
     SELLER WIZARD (vender.html) — guided "publicar mi reserva" flow.
     El hotel se busca en la API; al publicar se registra la reserva
     original (POST /bookings) y se crea la publicación (POST /listings).
     La verificación con el hotel (paso 3) y el comprobante (paso 4)
     siguen siendo solo de interfaz: el backend todavía no los procesa.
  --------------------------------------------------------------- */
  function initSellerWizard() {
    var shell = $(".wizard-shell");
    if (!shell || !api) return;

    function isoOf(d) { return d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate()); }
    function parseISO(s) { return new Date(s + "T00:00:00"); }
    function fmtShort(d) { return d.getDate() + " " + MESES_ABBR[d.getMonth()]; }
    function nightsBetween(a, b) { return Math.round((b - a) / 86400000); }

    // state.hotel is built across paso 1-2 instead of pointing at a
    // pre-existing OFFERS entry — a real seller could have booked
    // anywhere, through any channel.
    var state = {
      step: 1, hotel: null, checkin: null, checkout: null,
      guests: 2, roomType: "Doble Estándar", provider: "Directo con el hotel", bookingRef: "", reason: "",
      originalPrice: null, discount: 30, verified: false, uploaded: false,
      allowOffers: true, allowSplitBooking: true,
      bookingId: null // reserva ya registrada en el backend (para reintentar sin duplicarla)
    };

    var stepIndicators = $$("[data-wizard-step-indicator]", shell);
    var stepLines = $$(".wizard-step-line", shell);
    var panels = $$("[data-wizard-panel]", shell);
    var backBtn = $("[data-wizard-back]", shell);
    var nextBtn = $("[data-wizard-next]", shell);
    var nextLabel = nextBtn.querySelector("span");

    function isIdentityValid() {
      var name = $("[data-wizard-name]", shell).value.trim();
      var email = $("[data-wizard-email]", shell).value.trim();
      var phone = $("[data-wizard-phone]", shell).value.trim();
      var consent = $("[data-wizard-consent]", shell).checked;
      return name.length > 1 && /\S+@\S+\.\S+/.test(email) && phone.length > 3 && consent;
    }

    function canAdvance() {
      if (state.step === 1) return !!state.hotel;
      if (state.step === 2) return !!state.checkin && !!state.checkout && !!state.bookingRef.trim();
      if (state.step === 3) return state.verified;
      if (state.step === 4) return state.uploaded;
      if (state.step === 5) return Number(state.originalPrice) > 0;
      if (state.step === 6) return isIdentityValid();
      return true; // paso 7
    }

    function renderStepper() {
      var doneThreshold = state.step === "success" ? 8 : state.step;
      stepIndicators.forEach(function (el) {
        var n = Number(el.dataset.wizardStepIndicator);
        el.classList.toggle("is-active", n === doneThreshold);
        el.classList.toggle("is-done", n < doneThreshold);
      });
      stepLines.forEach(function (line, i) {
        line.classList.toggle("is-done", (i + 1) < doneThreshold);
      });
    }

    function updateNav() {
      backBtn.hidden = state.step === 1 || state.step === "success";
      nextBtn.hidden = state.step === "success";
      nextBtn.disabled = !canAdvance();
      nextLabel.textContent = state.step === 7 ? "Publicar mi reserva" : "Continuar";
    }

    function showStep(step) {
      panels.forEach(function (p) { p.hidden = p.dataset.wizardPanel !== String(step); });
      state.step = step;
      if (step === 3) runVerification();
      if (step === 5) { syncSplitOption(); updateWizardPrice(false); }
      if (step === 6) prefillIdentity();
      renderStepper();
      renderSummarySidebar();
      updateNav();
    }

    /* ---------- Paso 1 — buscar y confirmar el hotel ---------- */
    var searchInput = $("[data-hotel-search-input]", shell);
    var resultsWrap = $("[data-hotel-search-results]", shell);
    var confirmWrap = $("[data-hotel-confirm]", shell);
    var manualToggle = $("[data-hotel-manual-toggle]", shell);
    var manualForm = $("[data-hotel-manual-form]", shell);

    // Hotel de la API -> forma que usa el wizard (offerId: contenido de marketing en offers-data.js)
    function toWizardHotel(h) {
      return {
        id: h.id, name: h.name, city: h.city || "", country: h.country || "",
        partner: !!h.liberaPartner, splitAvailable: !!h.splitBookingAvailable,
        offerId: (window.OFFERS || {})[h.slug] ? h.slug : null
      };
    }

    var searchSeq = 0;
    var searchTimer = null;
    function renderSearchResults(query) {
      var q = query.trim();
      clearTimeout(searchTimer);
      if (!q) { resultsWrap.hidden = true; return; }
      searchTimer = setTimeout(function () {
        var seq = ++searchSeq;
        api.hotels(q).then(function (hotels) {
          if (seq !== searchSeq) return; // llegó una respuesta vieja
          showSearchResults(query, hotels.slice(0, 8).map(toWizardHotel));
        }).catch(function (err) {
          if (seq !== searchSeq) return;
          showSearchResults(query, [], err.message);
        });
      }, 200);
    }

    function showSearchResults(query, matches, errorMsg) {
      resultsWrap.innerHTML = "";
      if (!matches.length) {
        var empty = document.createElement("p");
        empty.className = "hotel-search-empty";
        empty.textContent = errorMsg || "No encontramos resultados para “" + query + "”.";
        resultsWrap.appendChild(empty);
      } else {
        matches.forEach(function (h) {
          var row = document.createElement("button");
          row.type = "button";
          row.className = "hotel-result-row";
          row.innerHTML =
            '<span><span class="hotel-result-name"></span><span class="hotel-result-location"></span></span>' +
            (h.partner ? '<span class="hotel-result-partner-pin"><svg viewBox="0 0 24 24" aria-hidden="true"><use href="#icon-check"></use></svg>Hotel asociado</span>' : "");
          row.querySelector(".hotel-result-name").textContent = h.name;
          row.querySelector(".hotel-result-location").textContent = h.city + ", " + h.country;
          row.addEventListener("click", function () { selectHotel(h); });
          resultsWrap.appendChild(row);
        });
      }
      resultsWrap.hidden = false;
    }

    function selectHotel(h) {
      state.hotel = h;
      state.bookingId = null;
      resultsWrap.hidden = true;
      searchInput.value = h.name;
      manualForm.hidden = true;
      renderHotelConfirm();
      syncSplitOption();
      renderSummarySidebar();
      updateNav();
    }

    function renderHotelConfirm() {
      var h = state.hotel;
      confirmWrap.hidden = false;
      var offer = h.offerId ? window.OFFERS[h.offerId] : null;

      if (offer) {
        confirmWrap.innerHTML =
          '<div class="hotel-confirm-rich">' +
            '<div class="hotel-confirm-header"><img alt=""><div><h4></h4><p></p></div></div>' +
            '<div class="partner-banner"><svg viewBox="0 0 24 24" aria-hidden="true"><use href="#icon-shield"></use></svg>' +
              '<div><strong>Hotel asociado a LIBERA</strong><span>Vamos a confirmar tu reserva directo con el hotel — publicación más rápida y con seguimiento garantizado.</span></div>' +
            '</div>' +
            '<div class="hotel-confirm-amenities" data-hotel-confirm-amenities></div>' +
            '<button type="button" class="hotel-confirm-change" data-hotel-change>Buscar otro hotel</button>' +
          '</div>';
        var img = confirmWrap.querySelector("img");
        img.src = offer.images[0].src; img.alt = offer.images[0].alt;
        confirmWrap.querySelector("h4").textContent = offer.hotel;
        confirmWrap.querySelector("p").textContent = offer.location;
        var amenitiesWrap = confirmWrap.querySelector("[data-hotel-confirm-amenities]");
        offer.amenities.slice(0, 4).forEach(function (a) {
          var span = document.createElement("span"); span.textContent = a.label; amenitiesWrap.appendChild(span);
        });
      } else {
        confirmWrap.innerHTML =
          '<div class="hotel-confirm-simple">' +
            '<div class="hotel-confirm-header"><div><h4></h4><p></p></div></div>' +
            '<p class="hotel-confirm-simple-banner"></p>' +
            '<button type="button" class="hotel-confirm-change" data-hotel-change>Buscar otro hotel</button>' +
          '</div>';
        confirmWrap.querySelector("h4").textContent = h.name;
        confirmWrap.querySelector("p").textContent = h.city + ", " + h.country;
        confirmWrap.querySelector(".hotel-confirm-simple-banner").textContent = h.partner
          ? "Hotel asociado a LIBERA: el cambio de nombre se reconoce en recepción. En este hotel la reserva se vende completa (sin noches sueltas)."
          : "Este hotel todavía no es socio de LIBERA — vamos a validar tu reserva con el comprobante que subas más adelante.";
      }
      confirmWrap.querySelector("[data-hotel-change]").addEventListener("click", function () {
        state.hotel = null;
        state.verified = false;
        confirmWrap.hidden = true;
        confirmWrap.innerHTML = "";
        searchInput.value = "";
        searchInput.focus();
        renderSummarySidebar();
        updateNav();
      });
    }

    searchInput.addEventListener("input", function () { renderSearchResults(searchInput.value); });
    searchInput.addEventListener("focus", function () { if (searchInput.value.trim()) renderSearchResults(searchInput.value); });
    resultsWrap.addEventListener("click", function (e) { e.stopPropagation(); });
    document.addEventListener("click", function () { resultsWrap.hidden = true; });

    manualToggle.addEventListener("click", function () { manualForm.hidden = !manualForm.hidden; });

    /* ---------- Paso 2 — fechas, huéspedes y detalles ---------- */
    var calTrigger = $("[data-wizard-date-trigger]", shell);
    var calPanel = $("[data-wizard-calendar-panel]", shell);
    var calMonthsWrap = $("[data-wizard-calendar-months]", shell);
    var calRangeLabel = $("[data-wizard-cal-range-label]", shell);
    var calApply = $("[data-wizard-cal-apply]", shell);
    var calCancel = $("[data-wizard-cal-cancel]", shell);
    var calPrevBtn = $("[data-wizard-cal-prev]", shell);
    var calNextBtn = $("[data-wizard-cal-next]", shell);
    var dateLabel = $("[data-wizard-date-label]", shell);

    var today = new Date(); today.setHours(0, 0, 0, 0);
    var pendingRange = { start: null, end: null };
    var dayButtons = {};
    var calMonthOffset = 0;
    var MAX_MONTH_OFFSET = 24; // ~2 años hacia adelante — cualquier reserva real cae bien dentro de esto

    function buildCalendar() {
      calMonthsWrap.innerHTML = "";
      dayButtons = {};
      for (var m = 0; m < 2; m++) {
        var base = new Date(today.getFullYear(), today.getMonth() + calMonthOffset + m, 1);
        var year = base.getFullYear(), month = base.getMonth();
        var monthWrap = document.createElement("div");
        var label = document.createElement("div");
        label.className = "wizard-cal-month-label";
        label.textContent = MESES[month] + " " + year;
        monthWrap.appendChild(label);
        var weekdays = document.createElement("div");
        weekdays.className = "cal-weekdays";
        ["Lu", "Ma", "Mi", "Ju", "Vi", "Sa", "Do"].forEach(function (d) { var s = document.createElement("span"); s.textContent = d; weekdays.appendChild(s); });
        monthWrap.appendChild(weekdays);
        var grid = document.createElement("div");
        grid.className = "cal-grid";
        var daysInMonth = new Date(year, month + 1, 0).getDate();
        var leading = (new Date(year, month, 1).getDay() + 6) % 7;
        for (var i = 0; i < leading; i++) grid.appendChild(document.createElement("span"));
        for (var d = 1; d <= daysInMonth; d++) {
          var date = new Date(year, month, d);
          var btn = document.createElement("button");
          btn.type = "button"; btn.className = "cal-day"; btn.textContent = String(d);
          if (date < today) { btn.disabled = true; }
          else {
            btn.dataset.date = isoOf(date);
            btn.addEventListener("click", function () { onDayClick(parseISO(this.dataset.date)); });
            dayButtons[isoOf(date)] = btn;
          }
          grid.appendChild(btn);
        }
        monthWrap.appendChild(grid);
        calMonthsWrap.appendChild(monthWrap);
      }
      if (calPrevBtn) calPrevBtn.disabled = calMonthOffset === 0;
      if (calNextBtn) calNextBtn.disabled = calMonthOffset >= MAX_MONTH_OFFSET;
      renderCalHighlight();
    }

    function renderCalHighlight() {
      Object.keys(dayButtons).forEach(function (iso) {
        var btn = dayButtons[iso];
        var date = parseISO(iso);
        var isEdge = (pendingRange.start && date.getTime() === pendingRange.start.getTime()) || (pendingRange.end && date.getTime() === pendingRange.end.getTime());
        var inRange = pendingRange.start && pendingRange.end && date > pendingRange.start && date < pendingRange.end;
        btn.classList.toggle("is-selected", !!isEdge);
        btn.classList.toggle("is-in-range", !!inRange);
      });
    }

    function updateCalRangeLabel() {
      if (pendingRange.start && pendingRange.end) {
        calRangeLabel.textContent = fmtShort(pendingRange.start) + " – " + fmtShort(pendingRange.end) + " · " + nightsBetween(pendingRange.start, pendingRange.end) + " noches";
        calApply.disabled = false;
      } else if (pendingRange.start) {
        calRangeLabel.textContent = fmtShort(pendingRange.start) + " · elegí el checkout";
        calApply.disabled = true;
      } else {
        calRangeLabel.textContent = "Elegí tus fechas";
        calApply.disabled = true;
      }
    }

    function onDayClick(date) {
      if (!pendingRange.start || (pendingRange.start && pendingRange.end)) { pendingRange.start = date; pendingRange.end = null; }
      else if (date <= pendingRange.start) { pendingRange.start = date; pendingRange.end = null; }
      else { pendingRange.end = date; }
      renderCalHighlight();
      updateCalRangeLabel();
    }

    function openCal() {
      pendingRange.start = state.checkin; pendingRange.end = state.checkout;
      calMonthOffset = state.checkin
        ? Math.max(0, (state.checkin.getFullYear() - today.getFullYear()) * 12 + (state.checkin.getMonth() - today.getMonth()))
        : 0;
      buildCalendar();
      updateCalRangeLabel();
      calPanel.hidden = false;
      calTrigger.classList.add("is-open");
    }
    function closeCal() { calPanel.hidden = true; calTrigger.classList.remove("is-open"); }

    calTrigger.addEventListener("click", function (e) { e.stopPropagation(); if (calPanel.hidden) openCal(); else closeCal(); });
    calPanel.addEventListener("click", function (e) { e.stopPropagation(); });
    document.addEventListener("click", function () { if (!calPanel.hidden) closeCal(); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape" && !calPanel.hidden) closeCal(); });
    calCancel.addEventListener("click", closeCal);
    if (calPrevBtn) calPrevBtn.addEventListener("click", function () { if (calMonthOffset > 0) { calMonthOffset--; buildCalendar(); } });
    if (calNextBtn) calNextBtn.addEventListener("click", function () { if (calMonthOffset < MAX_MONTH_OFFSET) { calMonthOffset++; buildCalendar(); } });
    calApply.addEventListener("click", function () {
      if (!pendingRange.start || !pendingRange.end) return;
      state.checkin = pendingRange.start; state.checkout = pendingRange.end;
      state.bookingId = null;
      closeCal();
      dateLabel.textContent = fmtShort(state.checkin) + " – " + fmtShort(state.checkout) + " · " + nightsBetween(state.checkin, state.checkout) + " noches";
      renderSummarySidebar();
      updateNav();
    });
    buildCalendar();

    var guestsValueEl = $("[data-wizard-guests-value]", shell);
    $("[data-wizard-guests-plus]", shell).addEventListener("click", function () {
      state.guests = Math.min(10, state.guests + 1);
      guestsValueEl.textContent = state.guests;
      renderSummarySidebar();
    });
    $("[data-wizard-guests-minus]", shell).addEventListener("click", function () {
      state.guests = Math.max(1, state.guests - 1);
      guestsValueEl.textContent = state.guests;
      renderSummarySidebar();
    });
    $("[data-wizard-room-type]", shell).addEventListener("change", function (e) { state.roomType = e.target.value; renderSummarySidebar(); });
    $("[data-wizard-provider]", shell).addEventListener("change", function (e) { state.provider = e.target.value; });
    $("[data-wizard-booking-ref]", shell).addEventListener("input", function (e) {
      state.bookingRef = e.target.value;
      state.bookingId = null; // cambió el código: hay que registrar la reserva de nuevo
      updateNav();
    });
    $("[data-wizard-reason]", shell).addEventListener("change", function (e) { state.reason = e.target.value; });

    /* ---------- Paso 3 — verificación (condicional según socio) ---------- */
    function runVerification() {
      if (state.verified) return;
      var statusEl = $(".verify-status", shell);
      var textEl = $("[data-verify-text]", shell);
      if (state.hotel.partner) {
        statusEl.dataset.verifyStatus = "pending";
        textEl.textContent = "Verificando tu reserva con el hotel…";
        updateNav();
        setTimeout(function () {
          state.verified = true;
          statusEl.dataset.verifyStatus = "done";
          textEl.textContent = "Reserva verificada con el hotel ✓";
          updateNav();
        }, 1300);
      } else {
        statusEl.dataset.verifyStatus = "done";
        textEl.textContent = "Como este hotel todavía no es socio de LIBERA, vamos a validar tu reserva con el comprobante que subas a continuación.";
        state.verified = true;
        updateNav();
      }
    }

    /* ---------- Paso 4 — comprobante ---------- */
    var dropzone = $("[data-upload-dropzone]", shell);
    var fileInput = $("[data-upload-input]", shell);
    var uploadConfirmed = $("[data-upload-confirmed]", shell);

    function handleFile(file) {
      if (!file) return;
      state.uploaded = true;
      $("[data-upload-filename]", shell).textContent = file.name;
      uploadConfirmed.hidden = false;
      $("[data-upload-label]", shell).textContent = "Cambiar archivo";
      updateNav();
    }
    fileInput.addEventListener("change", function () { handleFile(fileInput.files[0]); });
    dropzone.addEventListener("dragover", function (e) { e.preventDefault(); dropzone.classList.add("is-dragover"); });
    dropzone.addEventListener("dragleave", function () { dropzone.classList.remove("is-dragover"); });
    dropzone.addEventListener("drop", function (e) {
      e.preventDefault();
      dropzone.classList.remove("is-dragover");
      var file = e.dataTransfer.files && e.dataTransfer.files[0];
      if (file) { fileInput.files = e.dataTransfer.files; handleFile(file); }
    });

    /* ---------- Paso 5 — precio ---------- */
    var originalPriceInput = $("[data-wizard-original-price]", shell);
    function updateWizardPrice(animate) {
      var original = Number(originalPriceInput.value) || 0;
      state.originalPrice = original;
      var range = $("[data-wizard-discount-range]", shell);
      var discount = Number(range.value);
      state.discount = discount;
      var b = computeSaleBreakdown(original, discount);

      $("[data-wizard-discount-readout]", shell).textContent = discount + "%";
      range.style.setProperty("--range-progress", ((discount - 10) / (80 - 10)) * 100 + "%");

      var originalOut = $('[data-wizard-out="original"]', shell);
      var saleOut = $('[data-wizard-out="sale"]', shell);
      var feeOut = $('[data-wizard-out="fee"]', shell);
      var netOut = $('[data-wizard-out="net"]', shell);

      if (animate) {
        animateCount(originalOut, original, { duration: 400 });
        animateCount(saleOut, b.sale, { duration: 450 });
        animateCount(netOut, b.net, { duration: 550 });
      } else {
        originalOut.textContent = formatMoney(original); originalOut.dataset.currentVal = String(original);
        saleOut.textContent = formatMoney(b.sale); saleOut.dataset.currentVal = String(b.sale);
        netOut.textContent = formatMoney(b.net); netOut.dataset.currentVal = String(b.net);
      }
      feeOut.textContent = "−" + formatMoney(b.fee);
      renderSummarySidebar();
    }
    originalPriceInput.addEventListener("input", function () { state.bookingId = null; updateWizardPrice(true); updateNav(); });
    $("[data-wizard-discount-range]", shell).addEventListener("input", function () { updateWizardPrice(true); });

    var allowOffersInput = $("[data-wizard-allow-offers]", shell);
    var allowSplitInput = $("[data-wizard-allow-split]", shell);
    var splitRow = $("[data-wizard-split-row]", shell);
    var splitWarning = $("[data-wizard-split-warning]", shell);
    var splitUnavailable = $("[data-wizard-split-unavailable]", shell);

    function syncSplitOption() {
      // Split Booking solo en hoteles con integración (no en todos los socios)
      var isPartner = !!(state.hotel && state.hotel.splitAvailable);
      if (splitRow) splitRow.hidden = !isPartner;
      if (splitWarning) splitWarning.hidden = !isPartner;
      if (splitUnavailable) splitUnavailable.hidden = isPartner;
      if (!isPartner) {
        state.allowSplitBooking = false;
        if (allowSplitInput) allowSplitInput.checked = false;
      }
    }
    allowOffersInput.addEventListener("change", function () { state.allowOffers = allowOffersInput.checked; renderSummarySidebar(); });
    allowSplitInput.addEventListener("change", function () { state.allowSplitBooking = allowSplitInput.checked; renderSummarySidebar(); });

    /* ---------- Paso 6 — identidad ---------- */
    $$("[data-wizard-name], [data-wizard-email], [data-wizard-phone]", shell).forEach(function (el) {
      el.addEventListener("input", updateNav);
    });
    $("[data-wizard-consent]", shell).addEventListener("change", updateNav);

    function prefillIdentity() {
      var user = api.currentUser();
      if (!user) return;
      var nameInput = $("[data-wizard-name]", shell);
      var emailInput = $("[data-wizard-email]", shell);
      if (!nameInput.value.trim()) nameInput.value = user.firstName + " " + user.lastName;
      if (!emailInput.value.trim()) emailInput.value = user.email;
      updateNav();
    }

    /* ---------- Panel de resumen en vivo (columna derecha, todos los pasos) ---------- */
    var summaryBody = $("[data-wizard-summary-body]", shell);

    function renderSummarySidebar() {
      if (!summaryBody) return;
      if (!state.hotel) {
        summaryBody.innerHTML = '<p class="wizard-summary-empty" data-wizard-summary-empty>Elegí un hotel para empezar a armar tu publicación.</p>';
        return;
      }
      var h = state.hotel;
      var offer = h.offerId ? window.OFFERS[h.offerId] : null;

      var html = '<div class="wizard-summary-hotel">' +
        (offer ? '<img alt="">' : '<div class="wizard-summary-hotel-photo-placeholder"></div>') +
        '<div><h4></h4><p></p>' +
        (h.partner ? '<span class="wizard-summary-partner-pin"><svg viewBox="0 0 24 24" aria-hidden="true"><use href="#icon-check"></use></svg>Hotel asociado</span>' : '') +
        '</div></div>';
      summaryBody.innerHTML = html;

      if (offer) {
        var img = summaryBody.querySelector("img");
        img.src = offer.images[0].src; img.alt = offer.images[0].alt;
      } else {
        summaryBody.querySelector(".wizard-summary-hotel-photo-placeholder").textContent = h.name.charAt(0).toUpperCase();
      }
      summaryBody.querySelector("h4").textContent = h.name;
      summaryBody.querySelector(".wizard-summary-hotel p").textContent = h.city + ", " + h.country;

      if (state.checkin && state.checkout) {
        var rowsWrap = document.createElement("div");
        [
          [fmtShort(state.checkin) + " – " + fmtShort(state.checkout), nightsBetween(state.checkin, state.checkout) + " noches"],
          ["Huéspedes", state.guests],
          ["Habitación", state.roomType]
        ].forEach(function (r) {
          var row = document.createElement("div");
          row.className = "wizard-summary-row";
          row.innerHTML = "<span></span><span></span>";
          row.children[0].textContent = r[0];
          row.children[1].textContent = r[1];
          rowsWrap.appendChild(row);
        });
        summaryBody.appendChild(rowsWrap);
      }

      if (Number(state.originalPrice) > 0) {
        var b = computeSaleBreakdown(state.originalPrice, state.discount);
        var priceWrap = document.createElement("div");
        priceWrap.className = "wizard-summary-price";
        priceWrap.innerHTML =
          '<div class="wizard-summary-price-row"><span class="wizard-summary-was"></span><span class="wizard-summary-now"></span></div>' +
          '<p class="wizard-summary-net">Neto para vos: <strong></strong></p>';
        priceWrap.querySelector(".wizard-summary-was").textContent = formatMoney(state.originalPrice);
        priceWrap.querySelector(".wizard-summary-now").textContent = formatMoney(b.sale);
        priceWrap.querySelector(".wizard-summary-net strong").textContent = formatMoney(b.net);
        summaryBody.appendChild(priceWrap);

        var flags = [];
        if (state.allowOffers) flags.push("Acepta ofertas");
        if (h.splitAvailable && state.allowSplitBooking) flags.push("Split Booking");
        if (flags.length) {
          var flagsWrap = document.createElement("div");
          flagsWrap.className = "wizard-summary-flags";
          flags.forEach(function (f) {
            var span = document.createElement("span");
            span.className = "wizard-summary-flag";
            span.textContent = f;
            flagsWrap.appendChild(span);
          });
          summaryBody.appendChild(flagsWrap);
        }
      }

      var recapVerify = $("[data-wizard-recap-verify]", shell);
      if (recapVerify) {
        recapVerify.textContent = h.partner
          ? "Reserva confirmada directamente con el hotel asociado"
          : "Reserva validada con tu comprobante";
      }
    }

    function round2(n) { return Math.round(n * 100) / 100; }

    // Registra la reserva original. Si ya estaba cargada por este mismo usuario (p. ej. un
    // reintento después de un error al publicar), reutiliza esa en lugar de fallar.
    function ensureBooking() {
      if (state.bookingId) return Promise.resolve(state.bookingId);
      var code = state.bookingRef.trim();
      return api.registerBooking({
        hotelId: state.hotel.id,
        pmsConfirmationCode: code,
        checkIn: isoOf(state.checkin),
        checkOut: isoOf(state.checkout),
        roomType: state.roomType,
        totalAmountPaid: round2(state.originalPrice)
      }).then(function (booking) {
        state.bookingId = booking.id;
        return booking.id;
      }).catch(function (err) {
        if (err.status !== 409) throw err;
        return api.myBookings().then(function (mine) {
          var same = mine.filter(function (b) { return b.hotelId === state.hotel.id && b.pmsConfirmationCode === code; })[0];
          if (!same) throw err; // la cargó otra cuenta
          state.bookingId = same.id;
          return same.id;
        });
      });
    }

    function publish() {
      if (!requireLogin("Iniciá sesión para publicar tu reserva")) return;
      nextBtn.disabled = true;
      nextLabel.textContent = "Publicando…";
      var sale = computeSaleBreakdown(state.originalPrice, state.discount).sale;
      ensureBooking().then(function (bookingId) {
        return api.createListing({
          originalBookingId: bookingId,
          listedTotalPrice: round2(sale),
          allowsSplitBooking: !!(state.hotel.splitAvailable && state.allowSplitBooking)
        });
      }).then(function (listing) {
        var link = $("[data-wizard-view-listing]", shell);
        if (link) link.href = "oferta.html?id=" + listing.id;
        showStep("success");
        showToast("¡Reserva publicada con éxito!");
      }).catch(function (err) {
        nextBtn.disabled = false;
        nextLabel.textContent = "Publicar mi reserva";
        showToast(err.message);
      });
    }

    // Navegación
    backBtn.addEventListener("click", function () {
      if (typeof state.step === "number" && state.step > 1) showStep(state.step - 1);
    });
    nextBtn.addEventListener("click", function () {
      if (!canAdvance()) return;
      // Para publicar hace falta una cuenta: se pide al salir del paso 1
      if (!requireLogin("Iniciá sesión para publicar tu reserva")) return;
      if (state.step === 7) { publish(); return; }
      showStep(state.step + 1);
    });

    showStep(1);
  }

  /* ---------------------------------------------------------------
     AUTH MODAL (login / register) — contra /api/v1/auth
  --------------------------------------------------------------- */
  function initAuthModal() {
    var modal = $("[data-auth-modal]");
    if (!modal || typeof modal.showModal !== "function") return;
    var errorEl = $("[data-auth-error]", modal);

    function showError(msg) {
      if (!errorEl) { showToast(msg); return; }
      errorEl.textContent = msg || "";
      errorEl.hidden = !msg;
    }

    function showTab(name) {
      $$("[data-auth-tab]", modal).forEach(function (t) {
        var active = t.dataset.authTab === name;
        t.classList.toggle("is-active", active);
        t.setAttribute("aria-selected", active ? "true" : "false");
      });
      $$("[data-auth-form]", modal).forEach(function (f) { f.hidden = f.dataset.authForm !== name; });
      var success = $("[data-auth-success]", modal);
      success.hidden = true;
      success.classList.remove("is-visible");
      showError("");
    }

    openAuthModal = function (tab) {
      showTab(tab === "register" ? "register" : "login");
      if (!modal.open) modal.showModal();
    };

    $$("[data-auth-open]").forEach(function (btn) {
      btn.addEventListener("click", function () { openAuthModal(btn.dataset.authOpen); });
    });

    $$("[data-auth-tab]", modal).forEach(function (tab) {
      tab.addEventListener("click", function () { showTab(tab.dataset.authTab); });
    });

    var closeBtn = $("[data-auth-close]", modal);
    if (closeBtn) closeBtn.addEventListener("click", function () { modal.close(); });

    // Click on the ::backdrop (native <dialog> click lands on the dialog element itself
    // when it's outside the padding box of its content).
    modal.addEventListener("click", function (e) {
      if (e.target === modal) modal.close();
    });

    $$("[data-auth-form]", modal).forEach(function (form) {
      form.addEventListener("submit", function (e) {
        e.preventDefault();
        if (!form.reportValidity()) return;
        if (!api) { showError("No se pudo cargar la conexión con el servidor."); return; }
        var btn = form.querySelector('button[type="submit"]');
        var label = btn.querySelector("span");
        var original = label.textContent;
        var isRegister = form.dataset.authForm === "register";
        var data = {};
        $$("input[name]", form).forEach(function (input) { data[input.name] = input.value.trim(); });
        data.password = form.querySelector('input[name="password"]').value; // la contraseña va sin recortar

        btn.disabled = true;
        label.textContent = isRegister ? "Creando cuenta…" : "Entrando…";
        showError("");
        (isRegister ? api.register(data) : api.login(data.email, data.password)).then(function (user) {
          label.textContent = original;
          btn.disabled = false;
          form.hidden = true;
          var success = $("[data-auth-success]", modal);
          $("[data-auth-success-text]", modal).textContent = isRegister
            ? "¡Cuenta creada! Hola, " + user.firstName + "."
            : "¡Hola de nuevo, " + user.firstName + "!";
          success.hidden = false;
          nextFrame(function () { success.classList.add("is-visible"); });
          showToast(isRegister ? "Cuenta creada" : "Sesión iniciada");
          setTimeout(function () { modal.close(); form.reset(); form.hidden = false; }, 1200);
        }).catch(function (err) {
          label.textContent = original;
          btn.disabled = false;
          showError(err.message);
        });
      });
    });
  }

  /* ---------------------------------------------------------------
     SESIÓN EN LA NAV — "Hola, Ana" + Mi cuenta / Cerrar sesión
  --------------------------------------------------------------- */
  function initSessionNav() {
    if (!api) return;
    var navs = $$(".nav-auth");
    if (!navs.length) return;

    function render() {
      var user = api.currentUser();
      navs.forEach(function (nav) {
        $$("[data-auth-open]", nav).forEach(function (b) { b.hidden = !!user; });
        var box = $("[data-session-nav]", nav);
        if (!user) { if (box) box.remove(); return; }
        if (!box) {
          box = document.createElement("div");
          box.className = "nav-session";
          box.dataset.sessionNav = "";
          box.innerHTML =
            '<a class="btn btn-ghost-nav" href="mi-cuenta.html" data-session-name></a>' +
            '<button type="button" class="btn btn-primary nav-cta" data-logout>Cerrar sesión</button>';
          $("[data-logout]", box).addEventListener("click", function () {
            api.logout();
            showToast("Cerraste sesión");
          });
          nav.appendChild(box);
        }
        $("[data-session-name]", box).textContent = "Hola, " + user.firstName;
      });
    }

    api.onSessionChange(render);
    render();
  }

  /* ---------------------------------------------------------------
     MI CUENTA (mi-cuenta.html) — compras, publicaciones y ventas
  --------------------------------------------------------------- */
  var PURCHASE_STATUS = {
    PAYMENT_HELD: { label: "Pago retenido", tone: "pending", buyer: "Estamos gestionando el cambio de nombre con el hotel. Tu pago está protegido.", seller: "El comprador ya pagó. Cobrás después de su check-in." },
    NAME_CHANGED: { label: "A nombre del comprador", buyerLabel: "A tu nombre", tone: "pending", buyer: "La reserva ya está a tu nombre. El pago se libera al vendedor recién después de tu check-in.", seller: "La reserva ya está a nombre del comprador. Cobrás después de su check-in." },
    CHECKED_IN: { label: "Check-in confirmado", tone: "ok", buyer: "El hotel confirmó tu llegada.", seller: "El hotel confirmó la llegada del comprador." },
    LIQUIDATED: { label: "Finalizada", tone: "ok", buyer: "Check-in confirmado y pago liberado al vendedor.", seller: "Operación cobrada." },
    DISPUTED: { label: "En reclamo", tone: "alert", buyer: "Nuestro equipo está revisando el caso. El pago sigue retenido.", seller: "El comprador abrió un reclamo. El pago sigue retenido mientras lo revisamos." }
  };
  var LISTING_STATUS = {
    ACTIVE: { label: "Publicada", tone: "ok" },
    PARTIALLY_SOLD: { label: "Vendida en parte", tone: "pending" },
    SOLD_OUT: { label: "Vendida", tone: "ok" },
    CANCELLED: { label: "Cancelada", tone: "muted" }
  };

  function initAccountPage() {
    var root = $("[data-account]");
    if (!root || !api) return;
    var guest = $("[data-account-guest]", root);
    var panels = $("[data-account-panels]", root);
    var greeting = $("[data-account-greeting]");
    var defaultGreeting = greeting ? greeting.textContent : "";

    $$("[data-account-tab]", root).forEach(function (tab) {
      tab.addEventListener("click", function () {
        $$("[data-account-tab]", root).forEach(function (t) {
          var active = t === tab;
          t.classList.toggle("is-active", active);
          t.setAttribute("aria-selected", active ? "true" : "false");
        });
        $$("[data-account-panel]", root).forEach(function (p) { p.hidden = p.dataset.accountPanel !== tab.dataset.accountTab; });
      });
    });

    function item(opts) {
      var el = document.createElement("article");
      el.className = "account-item";
      el.innerHTML =
        '<div class="account-item-main"><h3></h3><p class="account-item-meta"></p><p class="account-item-hint"></p></div>' +
        '<div class="account-item-side"><span class="status-pill"></span><strong class="account-item-price"></strong><div class="account-item-actions"></div></div>';
      $("h3", el).textContent = opts.title;
      $(".account-item-meta", el).textContent = opts.meta;
      var hint = $(".account-item-hint", el);
      hint.textContent = opts.hint || "";
      hint.hidden = !opts.hint;
      var pill = $(".status-pill", el);
      pill.textContent = opts.status.label;
      pill.dataset.tone = opts.status.tone;
      $(".account-item-price", el).textContent = opts.price;
      var actions = $(".account-item-actions", el);
      (opts.actions || []).forEach(function (a) {
        var btn = document.createElement(a.href ? "a" : "button");
        btn.className = "btn btn-sm " + (a.primary ? "btn-primary" : "btn-outline");
        btn.textContent = a.label;
        if (a.href) btn.href = a.href;
        else { btn.type = "button"; btn.addEventListener("click", function () { a.run(btn); }); }
        actions.appendChild(btn);
      });
      return el;
    }

    function fill(panelName, items, emptyHtml) {
      var panel = $('[data-account-panel="' + panelName + '"]', root);
      panel.innerHTML = "";
      if (!items.length) {
        var empty = document.createElement("p");
        empty.className = "account-empty";
        empty.innerHTML = emptyHtml;
        panel.appendChild(empty);
        return;
      }
      items.forEach(function (el) { panel.appendChild(el); });
    }

    function stayText(x) {
      return fmtRangeShort(x.checkIn, x.checkOut) + " " + parseISODate(x.checkOut).getFullYear() + " · " + plural(x.nights, "noche", "noches");
    }

    // Corre una acción con confirmación y recarga todo al terminar
    function action(question, call, okMessage) {
      return function (btn) {
        if (!window.confirm(question)) return;
        btn.disabled = true;
        call().then(function () { showToast(okMessage); load(); })
          .catch(function (err) { btn.disabled = false; showToast(err.message); });
      };
    }

    function purchaseItem(p) {
      var st = PURCHASE_STATUS[p.status] || { label: p.status, tone: "muted" };
      var canDispute = p.status === "PAYMENT_HELD" || p.status === "NAME_CHANGED";
      return item({
        title: p.hotelName,
        meta: p.roomType + " · " + stayText(p),
        hint: st.buyer,
        status: { label: st.buyerLabel || st.label, tone: st.tone },
        price: formatMoney(Number(p.totalPrice)),
        actions: canDispute ? [{
          label: "Tengo un problema",
          run: action("¿Querés abrir un reclamo por esta compra? El pago va a quedar retenido mientras lo revisamos.",
            function () { return api.openDispute(p.id); }, "Abrimos tu reclamo. Te vamos a contactar.")
        }] : []
      });
    }

    function listingItem(l) {
      var st = LISTING_STATUS[l.status] || { label: l.status, tone: "muted" };
      var soldNights = (l.soldRanges || []).reduce(function (n, r) {
        return n + Math.round((parseISODate(r.checkOut) - parseISODate(r.checkIn)) / 86400000);
      }, 0);
      var actions = [{ label: "Ver publicación", href: "oferta.html?id=" + l.id }];
      if (l.status === "ACTIVE") {
        actions.push({
          label: "Cancelar",
          run: action("¿Seguro que querés cancelar esta publicación? Va a dejar de aparecer en el catálogo.",
            function () { return api.cancelListing(l.id); }, "Publicación cancelada")
        });
      }
      return item({
        title: l.hotelName,
        meta: l.roomType + " · " + stayText(l),
        hint: soldNights ? "Vendiste " + plural(soldNights, "noche", "noches") + " de " + l.nights + "." : (l.allowsSplitBooking ? "Admite noches sueltas." : ""),
        status: st,
        price: formatMoney(Number(l.listedTotalPrice)),
        actions: actions
      });
    }

    function saleItem(p) {
      var st = PURCHASE_STATUS[p.status] || { label: p.status, tone: "muted" };
      return item({
        title: p.hotelName,
        meta: p.roomType + " · " + stayText(p),
        hint: st.seller,
        status: st,
        price: formatMoney(Number(p.totalPrice))
      });
    }

    function load() {
      var user = api.currentUser();
      guest.hidden = !!user;
      panels.hidden = !user;
      if (greeting) greeting.textContent = user ? "Hola, " + user.firstName + ". " + defaultGreeting : defaultGreeting;
      if (!user) return;

      $$("[data-account-panel]", root).forEach(function (p) { p.innerHTML = '<p class="account-empty">Cargando…</p>'; });
      Promise.all([api.myPurchases(), api.myListings(), api.mySales()]).then(function (res) {
        fill("purchases", res[0].map(purchaseItem), 'Todavía no compraste ninguna reserva. <a href="catalogo.html">Ver ofertas</a>');
        fill("listings", res[1].map(listingItem), 'Todavía no publicaste ninguna reserva. <a href="vender.html">Vender mi reserva</a>');
        fill("sales", res[2].map(saleItem), "Todavía no vendiste noches de tus publicaciones.");
      }).catch(function (err) {
        $$("[data-account-panel]", root).forEach(function (p) {
          p.innerHTML = '<p class="account-empty"></p>';
          p.firstChild.textContent = err.message;
        });
      });
    }

    api.onSessionChange(load);
    load();
  }

  /* ---------------------------------------------------------------
     CONTACT FORM
  --------------------------------------------------------------- */
  function initContactForm() {
    var form = $("[data-contact-form]");
    if (!form) return;
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      if (!form.reportValidity()) return;
      var btn = form.querySelector('button[type="submit"]');
      var original = btn.innerHTML;
      btn.disabled = true;
      btn.querySelector("span").textContent = "Enviando…";
      setTimeout(function () {
        btn.innerHTML = original;
        btn.disabled = false;
        form.reset();
        var success = $("[data-contact-success]", form);
        success.hidden = false;
        nextFrame(function () { success.classList.add("is-visible"); });
        showToast("Solicitud enviada correctamente");
      }, 700);
    });
  }

  /* ---------------------------------------------------------------
     HERO PHOTO CAROUSEL (crossfade, no rAF dependency)
  --------------------------------------------------------------- */
  function initHeroCarousel() {
    $$("[data-hero-carousel]").forEach(function (carousel) {
      var imgs = $$("img", carousel);
      if (imgs.length < 2) return;
      var i = 0;
      setInterval(function () {
        imgs[i].classList.remove("is-active");
        i = (i + 1) % imgs.length;
        imgs[i].classList.add("is-active");
      }, 6500);
    });
  }

  /* ---------------------------------------------------------------
     CARD CAROUSELS (prev/next scroll, e.g. destacadas)
  --------------------------------------------------------------- */
  function initCarousels() {
    $$("[data-carousel-track]").forEach(function (track) {
      var wrap = track.closest(".destacadas-carousel") || track.parentElement;
      var prev = $("[data-carousel-prev]", wrap);
      var next = $("[data-carousel-next]", wrap);
      if (!prev || !next) return;
      function scrollByCard(dir) {
        var card = track.querySelector(".destacada-card");
        var gap = 22;
        var amount = card ? card.getBoundingClientRect().width + gap : 300;
        track.scrollBy({ left: dir * amount, behavior: "smooth" });
      }
      prev.addEventListener("click", function () { scrollByCard(-1); });
      next.addEventListener("click", function () { scrollByCard(1); });
    });
  }

  /* ---------------------------------------------------------------
     GSAP PARALLAX
  --------------------------------------------------------------- */
  function initParallax() {
    if (!(window.gsap && window.ScrollTrigger)) return;
    gsap.registerPlugin(ScrollTrigger);
    $$(".hero").forEach(function (hero) {
      var bgImgs = $$("[data-parallax-bg] img", hero);
      if (bgImgs.length) {
        gsap.to(bgImgs, { yPercent: 12, ease: "none", scrollTrigger: { trigger: hero, start: "top top", end: "bottom top", scrub: 0.6 } });
      }
      $$("[data-parallax-el]", hero).forEach(function (el) {
        var depth = parseFloat(el.dataset.depth || "0.1");
        // fromTo desde 0: con gsap.to tomaba como base el translateY(36px) inicial de .reveal
        // y el texto quedaba corrido 36px para siempre (la etiqueta pisaba al título)
        gsap.fromTo(el, { y: 0 }, { y: depth * 260, ease: "none", scrollTrigger: { trigger: hero, start: "top top", end: "bottom top", scrub: 0.6 } });
      });
    });
  }

  /* ---------------------------------------------------------------
     BOOT
  --------------------------------------------------------------- */
  function boot() {
    safe(initSplash, "initSplash");
    safe(initCatalogLoading, "initCatalogLoading");
    safe(initSmoothScroll, "initSmoothScroll");
    safe(initNavScroll, "initNavScroll");
    safe(initMobileNav, "initMobileNav");
    safe(initScrollTopLinks, "initScrollTopLinks");
    safe(initViewToggle, "initViewToggle");
    safe(initSplitText, "initSplitText");
    safe(initReveals, "initReveals");
    safe(initTilt, "initTilt");
    safe(initMagnetic, "initMagnetic");
    safe(initCheckout, "initCheckout");
    safe(initOfferDetail, "initOfferDetail");
    safe(initAuthModal, "initAuthModal");
    safe(initSessionNav, "initSessionNav");
    safe(initOtaSearch, "initOtaSearch");
    safe(initOtaSearchRedirect, "initOtaSearchRedirect");
    safe(initCatalog, "initCatalog");
    safe(initSellerWizard, "initSellerWizard");
    safe(initAccountPage, "initAccountPage");
    safe(initContactForm, "initContactForm");
    safe(initHeroCarousel, "initHeroCarousel");
    safe(initCarousels, "initCarousels");
    safe(initParallax, "initParallax");
    document.documentElement.classList.add("is-ready");
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
