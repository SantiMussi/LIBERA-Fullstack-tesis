-- =====================================================================
-- LIBERA — datos de prueba para desarrollo local
-- Con la base H2 por defecto se carga sola al arrancar el backend.
-- Con MySQL: ejecutar DESPUÉS de db/schema.sql (asume las tablas vacías).
--
-- Usuarios demo (contraseña de los tres: Libera2026!)
--   vendedor@libera.test  -> titular de las reservas publicadas
--   comprador@libera.test -> para probar compras
--   admin@libera.test     -> rol ADMIN (panel de administración: revisión de publicaciones,
--                            confirmación de check-in y solicitudes de demo)
--
-- Los hoteles socios usan los mismos slugs que offers-data.js del front.
-- Las fechas son relativas a hoy, así el catálogo nunca queda vencido.
-- =====================================================================

USE libera_db;

INSERT INTO users (id, email, password_hash, first_name, last_name, document_number, identity_verified, role) VALUES
(1, 'vendedor@libera.test',  '$2a$10$Pxxpg93dfEQFKUgGC7MKXeiZgyY9MLYh8ofcQwm69fPQQY0VwsJgC', 'Ana',   'Pérez',  '20111222', TRUE, 'USER'),
(2, 'comprador@libera.test', '$2a$10$Pxxpg93dfEQFKUgGC7MKXeiZgyY9MLYh8ofcQwm69fPQQY0VwsJgC', 'Juan',  'Gómez',  '30111222', TRUE, 'USER'),
(3, 'admin@libera.test',     '$2a$10$Pxxpg93dfEQFKUgGC7MKXeiZgyY9MLYh8ofcQwm69fPQQY0VwsJgC', 'Admin', 'LIBERA', '00000000', TRUE, 'ADMIN');

INSERT INTO hotels (id, slug, name, legal_name, tax_id, city, country, partnership_model, pms_provider, markup_fee) VALUES
(1,  'costa-azul-resort-spa', 'Costa Azul Resort & Spa', 'Costa Azul Resort S.A.',   'UY-2100001', 'Punta del Este', 'Uruguay',   'INTEGRATION', 'ROIBACK', 20.00),
(2,  'aurora-bahia-suites',   'Aurora Bahía Suites',     'Aurora Bahía S.A. de C.V.', 'MX-3100002', 'Cancún',         'México',    'INTEGRATION', 'WINPAX',  15.00),
(3,  'monte-verde-lodge',     'Monte Verde Lodge',       'Monte Verde S.R.L.',        '30-71000003-1', 'Bariloche',   'Argentina', 'INTEGRATION', 'ROIBACK', 20.00),
(4,  'palacio-del-mar',       'Palacio del Mar',         'Palacio del Mar S.L.',      'ES-B0000004', 'Ibiza',         'España',    'INTEGRATION', 'WINPAX',  10.00),
(5,  'selva-alta-eco-resort', 'Selva Alta Eco Resort',   'Selva Alta S.A. de C.V.',   'MX-3100005', 'Tulum',          'México',    'INTEGRATION', 'ROIBACK', 20.00),
(6,  'riviera-blu-hotel',     'Riviera Blu Hotel',       'Riviera Blu S.r.l.',        'IT-0000006', 'Positano',       'Italia',    'INTEGRATION', 'ROIBACK', 12.50),
(7,  'dune-sands-retreat',    'Dune Sands Retreat',      'Dune Sands LLC',            'AE-0000007', 'Dubái',          'EAU',       'INTEGRATION', 'WINPAX',  15.00),
(8,  'nordic-fjord-cabins',   'Nordic Fjord Cabins',     'Nordic Fjord AS',           'NO-0000008', 'Bergen',         'Noruega',   'INTEGRATION', 'ROIBACK', 10.00),
-- cadena en Modelo Convenio (sin Split Booking) y un hotel sin convenio
(9,  'gran-hotel-andino',     'Gran Hotel Andino',       'Hoteles Andinos S.A.',      '30-71000009-1', 'Mendoza',     'Argentina', 'CONVENIO',    NULL,      NULL),
(10, 'hotel-plaza-centro',    'Hotel Plaza Centro',      'Plaza Centro S.A.',         '30-71000010-1', 'Buenos Aires','Argentina', 'NONE',        NULL,      NULL);

-- Reservas originales del vendedor (los montos "original" de offers-data.js)
INSERT INTO original_bookings (id, hotel_id, original_guest_id, pms_confirmation_code, check_in, check_out, room_type, total_amount_paid, is_libera_rate) VALUES
(1,  1, 1, 'RB-CA-10001', CURRENT_DATE + INTERVAL '20' DAY, CURRENT_DATE + INTERVAL '24' DAY, 'Habitación Doble Superior',   1290.00, TRUE),
(2,  2, 1, 'WP-AB-10002', CURRENT_DATE + INTERVAL '25' DAY, CURRENT_DATE + INTERVAL '28' DAY, 'Suite con Balcón',             840.00, TRUE),
(3,  3, 1, 'RB-MV-10003', CURRENT_DATE + INTERVAL '30' DAY, CURRENT_DATE + INTERVAL '35' DAY, 'Cabaña Familiar',             1650.00, TRUE),
(4,  4, 1, 'WP-PM-10004', CURRENT_DATE + INTERVAL '15' DAY, CURRENT_DATE + INTERVAL '17' DAY, 'Habitación Doble Minimalista', 980.00, TRUE),
(5,  5, 1, 'RB-SA-10005', CURRENT_DATE + INTERVAL '40' DAY, CURRENT_DATE + INTERVAL '44' DAY, 'Cabaña Elevada',              1410.00, TRUE),
(6,  6, 1, 'RB-RB-10006', CURRENT_DATE + INTERVAL '45' DAY, CURRENT_DATE + INTERVAL '48' DAY, 'Habitación con Terraza',      1120.00, TRUE),
(7,  7, 1, 'WP-DS-10007', CURRENT_DATE + INTERVAL '50' DAY, CURRENT_DATE + INTERVAL '53' DAY, 'Suite Desierto',              2050.00, TRUE),
(8,  8, 1, 'RB-NF-10008', CURRENT_DATE + INTERVAL '60' DAY, CURRENT_DATE + INTERVAL '64' DAY, 'Cabaña con Sauna',            1340.00, TRUE),
(9,  9, 1, 'GHA-10009',   CURRENT_DATE + INTERVAL '12' DAY, CURRENT_DATE + INTERVAL '15' DAY, 'Doble Estándar',               600.00, FALSE),
-- reserva cargada pero todavía sin publicar (para probar el flujo de publicación)
(10, 3, 1, 'RB-MV-10010', CURRENT_DATE + INTERVAL '70' DAY, CURRENT_DATE + INTERVAL '73' DAY, 'Habitación Doble',             900.00, TRUE),
-- reserva en un hotel sin convenio, publicada y esperando la revisión de un administrador
(11, 10, 1, 'HPC-20011',  CURRENT_DATE + INTERVAL '35' DAY, CURRENT_DATE + INTERVAL '38' DAY, 'Doble Superior',               750.00, FALSE);

-- Publicaciones (los "price" de offers-data.js)
INSERT INTO listings (id, original_booking_id, seller_id, listed_total_price, discount_percentage, allows_split_booking, status) VALUES
(1, 1, 1,  950.00, 26.36, TRUE,  'ACTIVE'),
(2, 2, 1,  610.00, 27.38, FALSE, 'ACTIVE'),
(3, 3, 1, 1180.00, 28.48, TRUE,  'ACTIVE'),
(4, 4, 1,  720.00, 26.53, FALSE, 'SOLD_OUT'),
(5, 5, 1,  990.00, 29.79, TRUE,  'ACTIVE'),
(6, 6, 1,  860.00, 23.21, FALSE, 'ACTIVE'),
(7, 7, 1, 1540.00, 24.88, FALSE, 'ACTIVE'),
(8, 8, 1,  990.00, 26.12, TRUE,  'ACTIVE'),
(9, 9, 1,  480.00, 20.00, FALSE, 'ACTIVE'),
(10, 11, 1, 600.00, 20.00, FALSE, 'PENDING_REVIEW');

-- Una compra con el pago retenido (el comprador ya figura en el hotel; falta confirmar el check-in).
-- buyer_fee = Garantía de Traspaso: 7,5% porque el descuento es menor a 35%.
INSERT INTO resale_purchases (id, listing_id, buyer_id, check_in, check_out, total_price, buyer_fee, status) VALUES
(1, 4, 2, CURRENT_DATE + INTERVAL '15' DAY, CURRENT_DATE + INTERVAL '17' DAY, 720.00, 54.00, 'NAME_CHANGED');

-- Una solicitud de demo de un hotel (formulario de la vista Hoteles)
INSERT INTO contact_requests (id, name, hotel_name, email, phone, rooms, message, created_at) VALUES
(1, 'Laura Méndez', 'Hostería del Bosque', 'laura@hosteriadelbosque.test', '+54 294 400 0000', '1 – 30',
 'Queremos ofrecer la tarifa revendible en temporada alta.', CURRENT_TIMESTAMP);

-- Hoteles sin convenio con LIBERA (mismo directorio que hotels-directory.js del front):
-- aparecen en el buscador del wizard de venta, sin Split Booking.
INSERT INTO hotels (slug, name, legal_name, tax_id, city, country, partnership_model) VALUES
('nh-collection-buenos-aires-provincial-buenos-aires', 'NH Collection Buenos Aires Provincial', 'NH Collection Buenos Aires Provincial', 'PENDIENTE', 'Buenos Aires', 'Argentina', 'NONE'),
('alvear-icon-hotel-buenos-aires', 'Alvear Icon Hotel', 'Alvear Icon Hotel', 'PENDIENTE', 'Buenos Aires', 'Argentina', 'NONE'),
('hotel-emperador-buenos-aires', 'Hotel Emperador', 'Hotel Emperador', 'PENDIENTE', 'Buenos Aires', 'Argentina', 'NONE'),
('fierro-hotel-buenos-aires-buenos-aires', 'Fierro Hotel Buenos Aires', 'Fierro Hotel Buenos Aires', 'PENDIENTE', 'Buenos Aires', 'Argentina', 'NONE'),
('sheraton-mendoza-hotel-mendoza', 'Sheraton Mendoza Hotel', 'Sheraton Mendoza Hotel', 'PENDIENTE', 'Mendoza', 'Argentina', 'NONE'),
('diplomatic-hotel-mendoza-mendoza', 'Diplomatic Hotel Mendoza', 'Diplomatic Hotel Mendoza', 'PENDIENTE', 'Mendoza', 'Argentina', 'NONE'),
('hilton-garden-inn-rosario-rosario', 'Hilton Garden Inn Rosario', 'Hilton Garden Inn Rosario', 'PENDIENTE', 'Rosario', 'Argentina', 'NONE'),
('howard-johnson-cordoba-cordoba', 'Howard Johnson Córdoba', 'Howard Johnson Córdoba', 'PENDIENTE', 'Córdoba', 'Argentina', 'NONE'),
('sheraton-salta-salta', 'Sheraton Salta', 'Sheraton Salta', 'PENDIENTE', 'Salta', 'Argentina', 'NONE'),
('sheraton-iguazu-resort-puerto-iguazu', 'Sheraton Iguazú Resort', 'Sheraton Iguazú Resort', 'PENDIENTE', 'Puerto Iguazú', 'Argentina', 'NONE'),
('llao-llao-resort-bariloche', 'Llao Llao Resort', 'Llao Llao Resort', 'PENDIENTE', 'Bariloche', 'Argentina', 'NONE'),
('design-suites-bariloche-bariloche', 'Design Suites Bariloche', 'Design Suites Bariloche', 'PENDIENTE', 'Bariloche', 'Argentina', 'NONE'),
('hotel-provincial-mar-del-plata-mar-del-plata', 'Hotel Provincial Mar del Plata', 'Hotel Provincial Mar del Plata', 'PENDIENTE', 'Mar del Plata', 'Argentina', 'NONE'),
('sofitel-montevideo-casino-carrasco-montevideo', 'Sofitel Montevideo Casino Carrasco', 'Sofitel Montevideo Casino Carrasco', 'PENDIENTE', 'Montevideo', 'Uruguay', 'NONE'),
('enjoy-punta-del-este-punta-del-este', 'Enjoy Punta del Este', 'Enjoy Punta del Este', 'PENDIENTE', 'Punta del Este', 'Uruguay', 'NONE'),
('conrad-punta-del-este-punta-del-este', 'Conrad Punta del Este', 'Conrad Punta del Este', 'PENDIENTE', 'Punta del Este', 'Uruguay', 'NONE'),
('hyatt-centric-las-condes-santiago', 'Hyatt Centric Las Condes', 'Hyatt Centric Las Condes', 'PENDIENTE', 'Santiago', 'Chile', 'NONE'),
('nh-ciudad-de-santiago-santiago', 'NH Ciudad de Santiago', 'NH Ciudad de Santiago', 'PENDIENTE', 'Santiago', 'Chile', 'NONE'),
('sheraton-miramar-hotel-convention-center-vina-del-mar', 'Sheraton Miramar Hotel & Convention Center', 'Sheraton Miramar Hotel & Convention Center', 'PENDIENTE', 'Viña del Mar', 'Chile', 'NONE'),
('copacabana-palace-rio-de-janeiro', 'Copacabana Palace', 'Copacabana Palace', 'PENDIENTE', 'Río de Janeiro', 'Brasil', 'NONE'),
('windsor-atlantica-rio-de-janeiro', 'Windsor Atlantica', 'Windsor Atlantica', 'PENDIENTE', 'Río de Janeiro', 'Brasil', 'NONE'),
('grand-hyatt-sao-paulo-sao-paulo', 'Grand Hyatt São Paulo', 'Grand Hyatt São Paulo', 'PENDIENTE', 'São Paulo', 'Brasil', 'NONE'),
('fasano-salvador-salvador', 'Fasano Salvador', 'Fasano Salvador', 'PENDIENTE', 'Salvador', 'Brasil', 'NONE'),
('four-seasons-mexico-city-ciudad-de-mexico', 'Four Seasons Mexico City', 'Four Seasons Mexico City', 'PENDIENTE', 'Ciudad de México', 'México', 'NONE'),
('grand-velas-riviera-maya-riviera-maya', 'Grand Velas Riviera Maya', 'Grand Velas Riviera Maya', 'PENDIENTE', 'Riviera Maya', 'México', 'NONE'),
('fiesta-americana-condesa-cancun-cancun', 'Fiesta Americana Condesa Cancún', 'Fiesta Americana Condesa Cancún', 'PENDIENTE', 'Cancún', 'México', 'NONE'),
('live-aqua-beach-resort-playa-del-carmen', 'Live Aqua Beach Resort', 'Live Aqua Beach Resort', 'PENDIENTE', 'Playa del Carmen', 'México', 'NONE'),
('hotel-casa-san-agustin-cartagena', 'Hotel Casa San Agustín', 'Hotel Casa San Agustín', 'PENDIENTE', 'Cartagena', 'Colombia', 'NONE'),
('sofitel-legend-santa-clara-cartagena', 'Sofitel Legend Santa Clara', 'Sofitel Legend Santa Clara', 'PENDIENTE', 'Cartagena', 'Colombia', 'NONE'),
('nh-collection-bogota-teleport-bogota', 'NH Collection Bogotá Teleport', 'NH Collection Bogotá Teleport', 'PENDIENTE', 'Bogotá', 'Colombia', 'NONE'),
('belmond-hotel-monasterio-cusco', 'Belmond Hotel Monasterio', 'Belmond Hotel Monasterio', 'PENDIENTE', 'Cusco', 'Perú', 'NONE'),
('jw-marriott-lima-lima', 'JW Marriott Lima', 'JW Marriott Lima', 'PENDIENTE', 'Lima', 'Perú', 'NONE'),
('libertador-lago-titicaca-puno', 'Libertador Lago Titicaca', 'Libertador Lago Titicaca', 'PENDIENTE', 'Puno', 'Perú', 'NONE'),
('yacht-y-golf-club-paraguayo-asuncion', 'Yacht y Golf Club Paraguayo', 'Yacht y Golf Club Paraguayo', 'PENDIENTE', 'Asunción', 'Paraguay', 'NONE'),
('los-tajibos-hotel-convention-center-santa-cruz-de-la-sierra', 'Los Tajibos Hotel & Convention Center', 'Los Tajibos Hotel & Convention Center', 'PENDIENTE', 'Santa Cruz de la Sierra', 'Bolivia', 'NONE'),
('hotel-presidente-intercontinental-guayaquil', 'Hotel Presidente Intercontinental', 'Hotel Presidente Intercontinental', 'PENDIENTE', 'Guayaquil', 'Ecuador', 'NONE'),
('jw-marriott-panama-ciudad-de-panama', 'JW Marriott Panama', 'JW Marriott Panama', 'PENDIENTE', 'Ciudad de Panamá', 'Panamá', 'NONE'),
('barcelo-san-jose-san-jose', 'Barceló San José', 'Barceló San José', 'PENDIENTE', 'San José', 'Costa Rica', 'NONE'),
('melia-punta-cana-beach-punta-cana', 'Meliá Punta Cana Beach', 'Meliá Punta Cana Beach', 'PENDIENTE', 'Punta Cana', 'República Dominicana', 'NONE'),
('hard-rock-hotel-punta-cana-punta-cana', 'Hard Rock Hotel Punta Cana', 'Hard Rock Hotel Punta Cana', 'PENDIENTE', 'Punta Cana', 'República Dominicana', 'NONE'),
('nh-collection-madrid-eurobuilding-madrid', 'NH Collection Madrid Eurobuilding', 'NH Collection Madrid Eurobuilding', 'PENDIENTE', 'Madrid', 'España', 'NONE'),
('melia-barcelona-sky-barcelona', 'Meliá Barcelona Sky', 'Meliá Barcelona Sky', 'PENDIENTE', 'Barcelona', 'España', 'NONE'),
('hotel-alfonso-xiii-sevilla', 'Hotel Alfonso XIII', 'Hotel Alfonso XIII', 'PENDIENTE', 'Sevilla', 'España', 'NONE'),
('iberostar-grand-hotel-portals-nous-mallorca', 'Iberostar Grand Hotel Portals Nous', 'Iberostar Grand Hotel Portals Nous', 'PENDIENTE', 'Mallorca', 'España', 'NONE'),
('hotel-danieli-venecia', 'Hotel Danieli', 'Hotel Danieli', 'PENDIENTE', 'Venecia', 'Italia', 'NONE'),
('bulgari-hotel-milano-milan', 'Bulgari Hotel Milano', 'Bulgari Hotel Milano', 'PENDIENTE', 'Milán', 'Italia', 'NONE'),
('hotel-plaza-athenee-paris', 'Hôtel Plaza Athénée', 'Hôtel Plaza Athénée', 'PENDIENTE', 'París', 'Francia', 'NONE'),
('le-negresco-niza', 'Le Negresco', 'Le Negresco', 'PENDIENTE', 'Niza', 'Francia', 'NONE'),
('pestana-palace-lisboa-lisboa', 'Pestana Palace Lisboa', 'Pestana Palace Lisboa', 'PENDIENTE', 'Lisboa', 'Portugal', 'NONE'),
('the-ritz-carlton-miami-beach-miami', 'The Ritz-Carlton Miami Beach', 'The Ritz-Carlton Miami Beach', 'PENDIENTE', 'Miami', 'Estados Unidos', 'NONE'),
('fontainebleau-miami-beach-miami', 'Fontainebleau Miami Beach', 'Fontainebleau Miami Beach', 'PENDIENTE', 'Miami', 'Estados Unidos', 'NONE'),
('marriott-marquis-times-square-nueva-york', 'Marriott Marquis Times Square', 'Marriott Marquis Times Square', 'PENDIENTE', 'Nueva York', 'Estados Unidos', 'NONE'),
('the-beverly-hills-hotel-los-angeles', 'The Beverly Hills Hotel', 'The Beverly Hills Hotel', 'PENDIENTE', 'Los Ángeles', 'Estados Unidos', 'NONE'),
('atlantis-the-palm-dubai', 'Atlantis The Palm', 'Atlantis The Palm', 'PENDIENTE', 'Dubái', 'EAU', 'NONE'),
('marina-bay-sands-singapur', 'Marina Bay Sands', 'Marina Bay Sands', 'PENDIENTE', 'Singapur', 'Singapur', 'NONE');
