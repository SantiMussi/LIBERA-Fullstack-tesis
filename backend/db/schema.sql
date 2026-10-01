-- =====================================================================
-- LIBERA — esquema de base de datos (MySQL 8+)
--
-- Uso (MySQL Workbench o consola):  ejecutar este archivo completo y
-- después, si querés datos de prueba, db/seed.sql.
-- ATENCIÓN: borra y vuelve a crear todas las tablas.
--
-- Spring arranca con ddl-auto=validate: si cambiás una entidad JPA,
-- actualizá también este archivo.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS libera_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE libera_db;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS resale_purchases;
DROP TABLE IF EXISTS listings;
DROP TABLE IF EXISTS original_bookings;
DROP TABLE IF EXISTS hotels;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    document_number   VARCHAR(50)  NULL,
    identity_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    role              ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
    created_at        TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE hotels (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    slug              VARCHAR(120) NULL,
    name              VARCHAR(255) NOT NULL,
    legal_name        VARCHAR(255) NOT NULL,
    tax_id            VARCHAR(50)  NOT NULL,
    city              VARCHAR(120) NULL,
    country           VARCHAR(120) NULL,
    partnership_model ENUM('INTEGRATION','CONVENIO','NONE') NOT NULL DEFAULT 'NONE',
    pms_provider      VARCHAR(100) NULL,
    markup_fee        DECIMAL(5,2) NULL DEFAULT 0.00,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hotels_slug (slug),
    KEY idx_hotels_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE original_bookings (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    hotel_id              BIGINT        NOT NULL,
    original_guest_id     BIGINT        NOT NULL,
    pms_confirmation_code VARCHAR(100)  NOT NULL,
    check_in              DATE          NOT NULL,
    check_out             DATE          NOT NULL,
    room_type             VARCHAR(100)  NOT NULL,
    total_amount_paid     DECIMAL(10,2) NOT NULL,
    is_libera_rate        BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    -- un mismo código de confirmación no se puede cargar dos veces para el mismo hotel
    UNIQUE KEY uk_bookings_hotel_code (hotel_id, pms_confirmation_code),
    KEY idx_bookings_guest (original_guest_id),
    CONSTRAINT fk_bookings_hotel FOREIGN KEY (hotel_id) REFERENCES hotels (id),
    CONSTRAINT fk_bookings_guest FOREIGN KEY (original_guest_id) REFERENCES users (id),
    CONSTRAINT ck_bookings_dates CHECK (check_out > check_in)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE listings (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    original_booking_id  BIGINT        NOT NULL,
    seller_id            BIGINT        NOT NULL,
    listed_total_price   DECIMAL(10,2) NOT NULL,
    discount_percentage  DECIMAL(5,2)  NULL,
    allows_split_booking BOOLEAN       NOT NULL DEFAULT FALSE,
    status               ENUM('ACTIVE','PARTIALLY_SOLD','SOLD_OUT','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    KEY idx_listings_booking (original_booking_id),
    KEY idx_listings_seller (seller_id),
    KEY idx_listings_status (status),
    CONSTRAINT fk_listings_booking FOREIGN KEY (original_booking_id) REFERENCES original_bookings (id),
    CONSTRAINT fk_listings_seller FOREIGN KEY (seller_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resale_purchases (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    listing_id  BIGINT        NOT NULL,
    buyer_id    BIGINT        NOT NULL,
    check_in    DATE          NOT NULL,
    check_out   DATE          NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    status      ENUM('PAYMENT_HELD','NAME_CHANGED','CHECKED_IN','LIQUIDATED','DISPUTED') NOT NULL DEFAULT 'PAYMENT_HELD',
    PRIMARY KEY (id),
    KEY idx_purchases_listing (listing_id),
    KEY idx_purchases_buyer (buyer_id),
    KEY idx_purchases_status (status),
    CONSTRAINT fk_purchases_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_purchases_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT ck_purchases_dates CHECK (check_out > check_in)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE transactions (
    id                         BIGINT        NOT NULL AUTO_INCREMENT,
    resale_purchase_id         BIGINT        NOT NULL,
    total_paid_by_buyer        DECIMAL(10,2) NOT NULL,
    buyer_fee_amount           DECIMAL(10,2) NOT NULL,
    seller_fee_amount          DECIMAL(10,2) NOT NULL,
    seller_payout_amount       DECIMAL(10,2) NOT NULL,
    hotel_revenue_share_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    libera_net_revenue         DECIMAL(10,2) NOT NULL,
    payment_gateway_reference  VARCHAR(255)  NULL,
    PRIMARY KEY (id),
    -- una compra se liquida una sola vez
    UNIQUE KEY uk_transactions_purchase (resale_purchase_id),
    CONSTRAINT fk_transactions_purchase FOREIGN KEY (resale_purchase_id) REFERENCES resale_purchases (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
