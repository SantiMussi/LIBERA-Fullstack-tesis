/* =============================================================
   OFFERS DATA — contenido de marketing de los hoteles socios (fotos,
   descripción, régimen, amenities, rating), indexado por el slug del
   hotel en el backend. El precio, las fechas y la disponibilidad
   vienen siempre de la API (api.js): los campos price/original/
   checkin* de acá solo los usan las tarjetas fijas de index.html.
   ============================================================= */
(function (global) {
  "use strict";

  function img(photoId, w, h) {
    return "https://images.unsplash.com/photo-" + photoId + "?w=" + w + "&h=" + h + "&fit=crop&q=80&auto=format";
  }

  // Garantía de Traspaso (buyer fee) — tiered by the seller's own discount:
  // 55–80% off → 12.5% (max) · 35–54% off → 10% · 10–34% off → 7.5%.
  function feeForDiscount(discountPct) {
    if (discountPct >= 55) return 12.5;
    if (discountPct >= 35) return 10;
    return 7.5;
  }

  // Photo ids already proven live elsewhere on the site — reused here
  // (as hero + cross-hotel detail shots) so no gallery image is new/unverified.
  var PHOTO = {
    costaAzul: "1566073771259-6a8506099945",
    auroraBahia: "1520250497591-112f2f40a3f4",
    monteVerde: "1502672260266-1c1ef2d93688",
    palacioDelMar: "1571003123894-1f0594d2b5d9",
    selvaAlta: "1618773928121-c32242e63f39",
    rivieraBlu: "1582719478250-c89cae4dc85b",
    duneSands: "1611892440504-42a792e24d32",
    nordicFjord: "1590490360182-c33d57733427",
    aerialCoast: "1499793983690-e29da59ef1c2",
    infinityPool: "1613490493576-7fde63acd811"
  };

  global.OFFERS = {

    "costa-azul-resort-spa": {
      id: "costa-azul-resort-spa",
      summary: "Frente al mar, con piscina infinita y spa de autor. Ideal para desconectar en pareja.",
      hotel: "Costa Azul Resort & Spa",
      location: "Punta del Este, Uruguay",
      country: "Uruguay",
      checkin: "14 Sep 2026", checkout: "18 Sep 2026",
      checkinISO: "2026-09-14", checkoutISO: "2026-09-18",
      nights: 4, guests: 2, regimen: "Todo incluido",
      roomType: "Habitación Doble Superior",
      price: 950, original: 1290, feePct: feeForDiscount(26), rating: 4.8, discountPct: 26,
      badgeTag: "Últimas 2 habitaciones",
      description: "Frente al mar en Punta del Este, con piscina infinita, spa de autor y habitaciones luminosas pensadas para desconectar en pareja. Esta reserva fue publicada por su titular original, que no puede viajar en esas fechas — vos accedés al mismo hotel, la misma habitación y el mismo régimen todo incluido, a un precio que decidió el propio vendedor.",
      images: [
        { src: img(PHOTO.costaAzul, 1400, 1000), alt: "Piscina infinita y solárium del Costa Azul Resort & Spa" },
        { src: img(PHOTO.infinityPool, 700, 560), alt: "Piscina al atardecer" },
        { src: img(PHOTO.palacioDelMar, 700, 560), alt: "Habitación con vista al mar" },
        { src: img(PHOTO.aerialCoast, 700, 560), alt: "Vista aérea de la costa" }
      ],
      amenities: [
        { icon: "wifi", label: "Wifi gratis" },
        { icon: "pool", label: "Piscina infinita" },
        { icon: "breakfast", label: "Todo incluido" },
        { icon: "check", label: "Spa de autor" },
        { icon: "check", label: "Vista al mar" }
      ]
    },

    "aurora-bahia-suites": {
      id: "aurora-bahia-suites",
      summary: "Suites con balcón y vista directa al Caribe, a pasos de la Quinta Avenida.",
      hotel: "Aurora Bahía Suites",
      location: "Cancún, México",
      country: "México",
      checkin: "02 Oct 2026", checkout: "05 Oct 2026",
      checkinISO: "2026-10-02", checkoutISO: "2026-10-05",
      nights: 3, guests: 2, regimen: "Media pensión",
      roomType: "Suite con Balcón",
      price: 610, original: 840, feePct: feeForDiscount(27), rating: 4.6, discountPct: 27,
      description: "Suites con balcón y vista directa al Caribe, a pasos de la Quinta Avenida de Cancún. Ideal para quienes buscan playa, vida nocturna y buena gastronomía a un mismo paso de la puerta del hotel.",
      images: [
        { src: img(PHOTO.auroraBahia, 1400, 1000), alt: "Suite frente al mar en Aurora Bahía Suites" },
        { src: img(PHOTO.palacioDelMar, 700, 560), alt: "Habitación con vista al mar" },
        { src: img(PHOTO.infinityPool, 700, 560), alt: "Piscina al atardecer" },
        { src: img(PHOTO.costaAzul, 700, 560), alt: "Piscina y solárium" }
      ],
      amenities: [
        { icon: "wifi", label: "Wifi gratis" },
        { icon: "pool", label: "Piscina" },
        { icon: "breakfast", label: "Media pensión" },
        { icon: "check", label: "Balcón con vista al mar" },
        { icon: "check", label: "A pasos de la playa" }
      ]
    },

    "monte-verde-lodge": {
      id: "monte-verde-lodge",
      summary: "Cabañas de troncos sobre el lago Nahuel Huapi, con desayuno de producción propia.",
      hotel: "Monte Verde Lodge",
      location: "Bariloche, Argentina",
      country: "Argentina",
      checkin: "20 Ago 2026", checkout: "25 Ago 2026",
      checkinISO: "2026-08-20", checkoutISO: "2026-08-25",
      nights: 5, guests: 4, regimen: "Solo alojamiento",
      roomType: "Cabaña Familiar",
      price: 1180, original: 1650, feePct: feeForDiscount(28), rating: 4.9, discountPct: 28,
      badgeTag: "Favorito de viajeros",
      description: "Cabañas de troncos sobre el lago Nahuel Huapi, en Bariloche, con vistas a la cordillera desde cada ventana. Pensado para grupos familiares que buscan desconectar rodeados de montaña.",
      images: [
        { src: img(PHOTO.monteVerde, 1400, 1000), alt: "Lodge de montaña frente al lago en Bariloche" },
        { src: img(PHOTO.nordicFjord, 700, 560), alt: "Cabaña de diseño frente al agua" },
        { src: img(PHOTO.selvaAlta, 700, 560), alt: "Cabaña de madera entre la naturaleza" },
        { src: img(PHOTO.costaAzul, 700, 560), alt: "Piscina y solárium" }
      ],
      amenities: [
        { icon: "wifi", label: "Wifi gratis" },
        { icon: "check", label: "Vista al lago" },
        { icon: "check", label: "Cocina de producción propia" },
        { icon: "check", label: "Chimenea" },
        { icon: "check", label: "Pet-friendly" }
      ]
    },

    "palacio-del-mar": {
      id: "palacio-del-mar",
      summary: "Habitaciones minimalistas en el corazón de Ibiza, a cinco minutos de Dalt Vila.",
      hotel: "Palacio del Mar",
      location: "Ibiza, España",
      country: "España",
      checkin: "29 Ago 2026", checkout: "31 Ago 2026",
      checkinISO: "2026-08-29", checkoutISO: "2026-08-31",
      nights: 2, guests: 2, regimen: "Desayuno incluido",
      roomType: "Habitación Doble Minimalista",
      price: 720, original: 980, feePct: feeForDiscount(27), rating: 4.7, discountPct: 27,
      description: "Habitaciones minimalistas en el corazón de Ibiza, a cinco minutos caminando de Dalt Vila. Diseño cuidado, luz natural y la energía de la isla a un paso.",
      images: [
        { src: img(PHOTO.palacioDelMar, 1400, 1000), alt: "Habitación con vista al mar en Palacio del Mar, Ibiza" },
        { src: img(PHOTO.rivieraBlu, 700, 560), alt: "Terraza con vista al mar Mediterráneo" },
        { src: img(PHOTO.auroraBahia, 700, 560), alt: "Suite con balcón" },
        { src: img(PHOTO.infinityPool, 700, 560), alt: "Piscina al atardecer" }
      ],
      amenities: [
        { icon: "wifi", label: "Wifi gratis" },
        { icon: "breakfast", label: "Desayuno incluido" },
        { icon: "check", label: "Diseño minimalista" },
        { icon: "check", label: "A 5 min de Dalt Vila" },
        { icon: "check", label: "Aire acondicionado" }
      ]
    },

    "selva-alta-eco-resort": {
      id: "selva-alta-eco-resort",
      summary: "Cabañas elevadas entre la selva maya, con acceso privado a cenote.",
      hotel: "Selva Alta Eco Resort",
      location: "Tulum, México",
      country: "México",
      checkin: "10 Sep 2026", checkout: "14 Sep 2026",
      checkinISO: "2026-09-10", checkoutISO: "2026-09-14",
      nights: 4, guests: 2, regimen: "Todo incluido",
      roomType: "Cabaña Elevada",
      price: 990, original: 1410, feePct: feeForDiscount(30), rating: 4.8, discountPct: 30,
      badgeTag: "Nuevo",
      description: "Cabañas elevadas entre la selva maya de Tulum, con acceso privado a cenote y arquitectura eco-chic pensada para fundirse con el entorno.",
      images: [
        { src: img(PHOTO.selvaAlta, 1400, 1000), alt: "Cabaña eco-chic entre la selva en Tulum" },
        { src: img(PHOTO.monteVerde, 700, 560), alt: "Cabaña de madera entre la naturaleza" },
        { src: img(PHOTO.nordicFjord, 700, 560), alt: "Cabaña de diseño frente al agua" },
        { src: img(PHOTO.aerialCoast, 700, 560), alt: "Vista aérea de la costa" }
      ],
      amenities: [
        { icon: "wifi", label: "Wifi gratis" },
        { icon: "breakfast", label: "Todo incluido" },
        { icon: "check", label: "Acceso privado a cenote" },
        { icon: "check", label: "Cabañas elevadas" },
        { icon: "pool", label: "Piscina natural" }
      ]
    },

    "riviera-blu-hotel": {
      id: "riviera-blu-hotel",
      summary: "Terrazas colgantes sobre el acantilado, con vistas a la Costa Amalfitana.",
      hotel: "Riviera Blu Hotel",
      location: "Positano, Italia",
      country: "Italia",
      checkin: "05 Sep 2026", checkout: "08 Sep 2026",
      checkinISO: "2026-09-05", checkoutISO: "2026-09-08",
      nights: 3, guests: 2, regimen: "Media pensión",
      roomType: "Habitación con Terraza",
      price: 860, original: 1120, feePct: feeForDiscount(23), rating: 4.9, discountPct: 23,
      description: "Terrazas colgantes sobre el acantilado de Positano, con vistas privilegiadas a la Costa Amalfitana. Una base ideal para recorrer la costa sin resignar confort.",
      images: [
        { src: img(PHOTO.rivieraBlu, 1400, 1000), alt: "Terraza con vista al acantilado en Positano" },
        { src: img(PHOTO.palacioDelMar, 700, 560), alt: "Habitación con vista al mar" },
        { src: img(PHOTO.infinityPool, 700, 560), alt: "Piscina al atardecer" },
        { src: img(PHOTO.aerialCoast, 700, 560), alt: "Vista aérea de la costa" }
      ],
      amenities: [
        { icon: "wifi", label: "Wifi gratis" },
        { icon: "breakfast", label: "Media pensión" },
        { icon: "check", label: "Terraza privada" },
        { icon: "check", label: "Vista a la Costa Amalfitana" },
        { icon: "check", label: "Aire acondicionado" }
      ]
    },

    "dune-sands-retreat": {
      id: "dune-sands-retreat",
      summary: "Lujo desértico con piscinas infinitas y vistas al skyline de Dubái.",
      hotel: "Dune Sands Retreat",
      location: "Dubái, EAU",
      country: "EAU",
      checkin: "18 Oct 2026", checkout: "21 Oct 2026",
      checkinISO: "2026-10-18", checkoutISO: "2026-10-21",
      nights: 3, guests: 2, regimen: "Todo incluido",
      roomType: "Suite Desierto",
      price: 1540, original: 2050, feePct: feeForDiscount(25), rating: 4.9, discountPct: 25,
      badgeTag: "Alta demanda",
      description: "Lujo desértico en Dubái, con piscinas infinitas, spa y vistas al skyline de la ciudad desde cada suite. Pensado para quienes buscan una experiencia de alta gama sin escalas.",
      images: [
        { src: img(PHOTO.duneSands, 1400, 1000), alt: "Lobby de lujo del Dune Sands Retreat en Dubái" },
        { src: img(PHOTO.infinityPool, 700, 560), alt: "Piscina al atardecer" },
        { src: img(PHOTO.costaAzul, 700, 560), alt: "Piscina y solárium" },
        { src: img(PHOTO.palacioDelMar, 700, 560), alt: "Habitación con vista al mar" }
      ],
      amenities: [
        { icon: "wifi", label: "Wifi gratis" },
        { icon: "breakfast", label: "Todo incluido" },
        { icon: "pool", label: "Piscina infinita" },
        { icon: "check", label: "Spa" },
        { icon: "check", label: "Vista al skyline" }
      ]
    },

    "nordic-fjord-cabins": {
      id: "nordic-fjord-cabins",
      summary: "Cabañas de diseño escandinavo asomadas al fiordo, con sauna privada.",
      hotel: "Nordic Fjord Cabins",
      location: "Bergen, Noruega",
      country: "Noruega",
      checkin: "22 Sep 2026", checkout: "26 Sep 2026",
      checkinISO: "2026-09-22", checkoutISO: "2026-09-26",
      nights: 4, guests: 2, regimen: "Desayuno incluido",
      roomType: "Cabaña con Sauna",
      price: 990, original: 1340, feePct: feeForDiscount(26), rating: 4.7, discountPct: 26,
      description: "Cabañas de diseño escandinavo asomadas al fiordo de Bergen, con sauna privada y una calma nórdica difícil de encontrar en otro lado.",
      images: [
        { src: img(PHOTO.nordicFjord, 1400, 1000), alt: "Cabaña sobre el fiordo en Bergen, Noruega" },
        { src: img(PHOTO.monteVerde, 700, 560), alt: "Lodge de montaña frente al lago" },
        { src: img(PHOTO.selvaAlta, 700, 560), alt: "Cabaña de madera entre la naturaleza" },
        { src: img(PHOTO.aerialCoast, 700, 560), alt: "Vista aérea de la costa" }
      ],
      amenities: [
        { icon: "wifi", label: "Wifi gratis" },
        { icon: "breakfast", label: "Desayuno incluido" },
        { icon: "check", label: "Sauna privada" },
        { icon: "check", label: "Vista al fiordo" },
        { icon: "check", label: "Chimenea" }
      ]
    }

  };
})(window);
