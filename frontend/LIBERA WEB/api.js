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

  // Solo la página que sirve el propio backend (puerto 8080, o sin puerto en producción) usa rutas
  // relativas; cualquier otro servidor de desarrollo (Go Live :5500, serve.ps1 :8765) va a localhost:8080
  var port = global.location.port;
  var servedByBackend = /^https?:$/.test(global.location.protocol) && (port === "8080" || port === "");
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
    "The purchase has already been liquidated and cannot be disputed.": "Esta compra ya se liquidó: no se puede abrir un reclamo.",
    "Only listings under review can be approved or rejected.": "Esta publicación ya fue revisada.",
    "The voucher must be a PDF, JPG, PNG or WEBP file.": "El comprobante tiene que ser un PDF, JPG, PNG o WEBP.",
    "The voucher file must be 5 MB or smaller.": "El comprobante puede pesar hasta 5 MB.",
    "The voucher file is empty.": "El archivo del comprobante está vacío.",
    "This booking has no voucher": "Esta reserva no tiene comprobante cargado.",
    "You can only upload the voucher of your own booking.": "Solo podés subir el comprobante de una reserva tuya.",
    "Resale purchase not found": "Esta compra no existe.",
    "You do not have permission to view this purchase.": "No tenés acceso a esta compra."
  };
  var BY_STATUS = {
    0: "No pudimos conectarnos con el servidor de LIBERA. Probá de nuevo en unos minutos.",
    400: "Revisá los datos ingresados.",
    401: "Tu sesión venció. Iniciá sesión de nuevo.",
    403: "No tenés permiso para hacer esto.",
    404: "No encontramos lo que buscabas.",
    409: "La operación no se pudo completar porque los datos cambiaron. Actualizá la página.",
    413: "El archivo es demasiado grande (máximo 5 MB).",
    500: "Hubo un error en el servidor. Probá de nuevo en unos minutos."
  };

  function apiError(status, serverMessage) {
    var msg = (serverMessage && MESSAGES[serverMessage]) || BY_STATUS[status] || BY_STATUS[500];
    var err = new Error(msg);
    err.status = status;
    err.serverMessage = serverMessage || "";
    return err;
  }

  /* ---------- Request genérico ----------
     body: objeto (va como JSON) o FormData (archivos: el navegador arma el multipart).
     asBlob: devuelve el cuerpo como archivo (Blob) en lugar de JSON. */
  function request(method, path, body, asBlob) {
    var headers = { "Accept": asBlob ? "*/*" : "application/json" };
    var payload;
    if (typeof FormData !== "undefined" && body instanceof FormData) payload = body;
    else if (body !== undefined) { headers["Content-Type"] = "application/json"; payload = JSON.stringify(body); }
    if (memory.token) headers["Authorization"] = "Bearer " + memory.token;

    return fetch(BASE + path, {
      method: method,
      headers: headers,
      body: payload
    }).then(function (res) {
      if (res.ok && asBlob) return res.blob();
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
    isAdmin: function () { return !!(memory.user && memory.user.role === "ADMIN"); },
    // Trae los datos actuales del usuario (por ejemplo, si le cambiaron el rol) y avisa solo si cambiaron
    refreshSession: function () {
      if (!memory.token) return Promise.resolve(null);
      return request("GET", "/auth/me").then(function (user) {
        if (JSON.stringify(user) !== JSON.stringify(memory.user)) setSession(memory.token, user);
        return user;
      });
    },

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
    uploadVoucher: function (bookingId, file) {
      var form = new FormData();
      form.append("file", file);
      return request("POST", "/bookings/" + bookingId + "/voucher", form);
    },

    /* comprador */
    purchase: function (listingId, checkIn, checkOut) {
      return request("POST", "/purchases", { listingId: listingId, checkIn: checkIn, checkOut: checkOut });
    },
    myPurchases: function () { return request("GET", "/purchases/mine"); },
    purchaseDetail: function (id) { return request("GET", "/purchases/" + encodeURIComponent(id)); },
    openDispute: function (id) { return request("POST", "/purchases/" + id + "/dispute"); },

    /* hoteles: formulario "Agendar demo" */
    sendContact: function (data) { return request("POST", "/contact", data); },

    /* administración (rol ADMIN) */
    admin: {
      listings: function (status) { return request("GET", "/admin/listings" + query({ status: status })); },
      approveListing: function (id) { return request("POST", "/admin/listings/" + id + "/approve"); },
      rejectListing: function (id, note) { return request("POST", "/admin/listings/" + id + "/reject", { note: note }); },
      voucherFile: function (bookingId) { return request("GET", "/admin/bookings/" + bookingId + "/voucher", undefined, true); },
      purchases: function (status) { return request("GET", "/admin/purchases" + query({ status: status })); },
      confirmCheckIn: function (id) { return request("POST", "/admin/purchases/" + id + "/check-in"); },
      contactRequests: function () { return request("GET", "/admin/contact-requests"); }
    }
  };
})(window);
