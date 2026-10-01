/* =============================================================
   LIBERA API — cliente del backend (Spring Boot) para las páginas HTML.
   Expone window.LiberaApi. Lo cargan todas las páginas antes de app.js.

   - URL del backend: si la página la sirve el propio backend, el mismo
     servidor (/api/v1); si no (tools/serve.ps1 o file://),
     http://localhost:8080/api/v1. Para apuntar a otro servidor, definir
     window.LIBERA_API_BASE antes de cargar este archivo.
   - La sesión (JWT + datos del usuario) se guarda en localStorage.
   - Los errores se lanzan como Error con .status y un .message ya
     traducido al español, listo para mostrar en un toast.
   ============================================================= */
(function (global) {
  "use strict";

  var servedByBackend = /^https?:$/.test(global.location.protocol) && global.location.port !== "8765";
  var BASE = (global.LIBERA_API_BASE || (servedByBackend ? "/api/v1" : "http://localhost:8080/api/v1")).replace(/\/$/, "");
  var TOKEN_KEY = "libera.token";
  var USER_KEY = "libera.user";
  var listeners = [];

  function storageGet(key) {
    try { return global.localStorage.getItem(key); } catch (e) { return null; }
  }
  function storageSet(key, value) {
    try {
      if (value === null) global.localStorage.removeItem(key);
      else global.localStorage.setItem(key, value);
    } catch (e) { /* modo privado / storage bloqueado: la sesión dura lo que dure la página */ }
  }

  var memory = { token: storageGet(TOKEN_KEY), user: null };
  try { memory.user = JSON.parse(storageGet(USER_KEY) || "null"); } catch (e) { memory.user = null; }

  function setSession(token, user) {
    memory.token = token;
    memory.user = user;
    storageSet(TOKEN_KEY, token);
    storageSet(USER_KEY, user ? JSON.stringify(user) : null);
    listeners.forEach(function (fn) { try { fn(user); } catch (e) { console.warn(e); } });
  }

  /* ---------- Mensajes de error en español ---------- */
  var MESSAGES = {
    "Invalid email or password.": "Email o contraseña incorrectos.",
    "An account with this email already exists.": "Ya existe una cuenta con ese email.",
    "Listing not found": "Esta publicación no existe.",
    "Listing is not available for purchase.": "Esta reserva ya no está disponible.",
    "Some of the requested nights have already been sold.": "Algunas de esas noches ya se vendieron. Elegí otras fechas.",
    "You cannot purchase your own listing.": "No podés comprar tu propia publicación.",
    "This listing does not allow split booking: the full stay must be purchased.": "Esta reserva se vende completa: no admite comprar noches sueltas.",
    "This booking already has a listing.": "Esta reserva ya está publicada.",
    "This booking has already been registered.": "Esa reserva ya fue cargada en LIBERA.",
    "Listed price cannot exceed the amount originally paid for the booking.": "El precio de venta no puede superar lo que pagaste.",
    "Cannot list a booking whose check-in date has already passed.": "No se puede publicar una reserva cuyo check-in ya pasó.",
    "Split bookings are only allowed for hotels with INTEGRATION partnership model.": "Este hotel no admite Split Booking.",
    "A listing with sold nights cannot be cancelled.": "No se puede cancelar una publicación que ya vendió noches.",
    "Listing is already cancelled.": "La publicación ya estaba cancelada.",
    "A dispute is already open for this purchase.": "Ya hay un reclamo abierto para esta compra.",
    "The purchase has already been liquidated and cannot be disputed.": "Esta compra ya se liquidó: no se puede abrir un reclamo."
  };
  var BY_STATUS = {
    0: "No pudimos conectarnos con el servidor de LIBERA. Probá de nuevo en unos minutos.",
    400: "Revisá los datos ingresados.",
    401: "Tu sesión venció. Iniciá sesión de nuevo.",
    403: "No tenés permiso para hacer esto.",
    404: "No encontramos lo que buscabas.",
    409: "La operación no se pudo completar porque los datos cambiaron. Actualizá la página.",
    500: "Hubo un error en el servidor. Probá de nuevo en unos minutos."
  };

  function apiError(status, serverMessage) {
    var msg = (serverMessage && MESSAGES[serverMessage]) || BY_STATUS[status] || BY_STATUS[500];
    var err = new Error(msg);
    err.status = status;
    err.serverMessage = serverMessage || "";
    return err;
  }

  /* ---------- Request genérico ---------- */
  function request(method, path, body) {
    var headers = { "Accept": "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (memory.token) headers["Authorization"] = "Bearer " + memory.token;

    return fetch(BASE + path, {
      method: method,
      headers: headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    }).then(function (res) {
      return res.text().then(function (text) {
        var data = null;
        if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }
        if (res.ok) return data;
        // Token vencido o inválido: se cierra la sesión local para no seguir mandándolo
        if (res.status === 401 && memory.token && path.indexOf("/auth/login") !== 0) setSession(null, null);
        throw apiError(res.status, data && data.message);
      });
    }, function () {
      throw apiError(0);
    });
  }

  function query(params) {
    var parts = [];
    Object.keys(params || {}).forEach(function (k) {
      var v = params[k];
      if (v !== undefined && v !== null && v !== "") parts.push(encodeURIComponent(k) + "=" + encodeURIComponent(v));
    });
    return parts.length ? "?" + parts.join("&") : "";
  }

  function onAuth(res) {
    setSession(res.accessToken, res.user);
    return res.user;
  }

  global.LiberaApi = {
    base: BASE,

    /* sesión */
    isLoggedIn: function () { return !!memory.token; },
    currentUser: function () { return memory.user; },
    onSessionChange: function (fn) { listeners.push(fn); },
    login: function (email, password) {
      return request("POST", "/auth/login", { email: email, password: password }).then(onAuth);
    },
    register: function (data) {
      return request("POST", "/auth/register", data).then(onAuth);
    },
    logout: function () { setSession(null, null); },
    me: function () { return request("GET", "/auth/me"); },

    /* catálogo */
    hotels: function (q) { return request("GET", "/hotels" + query({ q: q })); },
    listings: function (filters) { return request("GET", "/listings" + query(filters)); },
    listing: function (id) { return request("GET", "/listings/" + encodeURIComponent(id)); },

    /* vendedor */
    registerBooking: function (data) { return request("POST", "/bookings", data); },
    myBookings: function () { return request("GET", "/bookings/mine"); },
    createListing: function (data) { return request("POST", "/listings", data); },
    myListings: function () { return request("GET", "/listings/mine"); },
    cancelListing: function (id) { return request("POST", "/listings/" + id + "/cancel"); },
    mySales: function () { return request("GET", "/purchases/sales"); },

    /* comprador */
    purchase: function (listingId, checkIn, checkOut) {
      return request("POST", "/purchases", { listingId: listingId, checkIn: checkIn, checkOut: checkOut });
    },
    myPurchases: function () { return request("GET", "/purchases/mine"); },
    openDispute: function (id) { return request("POST", "/purchases/" + id + "/dispute"); }
  };
})(window);
