package com.libera.backend.controller;

import com.libera.backend.domain.entity.Hotel;
import com.libera.backend.domain.entity.Listing;
import com.libera.backend.domain.entity.OriginalBooking;
import com.libera.backend.domain.entity.ResalePurchase;
import com.libera.backend.domain.entity.User;
import com.libera.backend.domain.enums.ListingStatus;
import com.libera.backend.domain.enums.PartnershipModel;
import com.libera.backend.domain.enums.ResalePurchaseStatus;
import com.libera.backend.domain.enums.UserRole;
import com.libera.backend.repository.HotelRepository;
import com.libera.backend.repository.ListingRepository;
import com.libera.backend.repository.OriginalBookingRepository;
import com.libera.backend.repository.ResalePurchaseRepository;
import com.libera.backend.repository.TransactionRepository;
import com.libera.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de los tests de API de punta a punta: HTTP -> seguridad -> validación -> servicio -> JPA (H2 en memoria).
 * El usuario autenticado se simula con {@code user("<id>")}, que es lo que el backend lee en Principal.getName().
 * El login real con JWT se prueba aparte en {@link AuthApiTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestBase {

    protected static final String API_KEY = "libera-pms-secure-key-2026";
    /** Estadía de las reservas de prueba: 4 noches. */
    protected static final LocalDate STAY_IN = LocalDate.now().plusDays(10);
    protected static final LocalDate STAY_OUT = LocalDate.now().plusDays(14);

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected HotelRepository hotelRepository;
    @Autowired
    protected OriginalBookingRepository bookingRepository;
    @Autowired
    protected ListingRepository listingRepository;
    @Autowired
    protected ResalePurchaseRepository purchaseRepository;
    @Autowired
    protected TransactionRepository transactionRepository;

    protected User seller;
    protected User buyer;
    protected User admin;
    protected Hotel integrationHotel;
    protected Hotel convenioHotel;
    protected OriginalBooking integrationBooking;
    protected OriginalBooking convenioBooking;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        purchaseRepository.deleteAll();
        listingRepository.deleteAll();
        bookingRepository.deleteAll();
        hotelRepository.deleteAll();
        userRepository.deleteAll();

        seller = userRepository.save(newUser("vendedor@libera.test", "Ana", "Pérez", "20111222", UserRole.USER));
        buyer = userRepository.save(newUser("comprador@libera.test", "Juan", "Gómez", "30111222", UserRole.USER));
        admin = userRepository.save(newUser("admin@libera.test", "Admin", "Libera", "1", UserRole.ADMIN));

        integrationHotel = hotelRepository.save(Hotel.builder().slug("hotel-boutique").name("Hotel Boutique")
                .legalName("Boutique SA").taxId("30-1").city("Bariloche").country("Argentina")
                .partnershipModel(PartnershipModel.INTEGRATION).pmsProvider("ROIBACK")
                .markupFee(new BigDecimal("20.00")).build());
        convenioHotel = hotelRepository.save(Hotel.builder().slug("gran-cadena").name("Gran Cadena")
                .legalName("Cadena SA").taxId("30-2").city("Mendoza").country("Argentina")
                .partnershipModel(PartnershipModel.CONVENIO).pmsProvider("WINPAX").build());

        integrationBooking = bookingRepository.save(newBooking(integrationHotel, "RB-111"));
        convenioBooking = bookingRepository.save(newBooking(convenioHotel, "WP-222"));
    }

    private static User newUser(String email, String first, String last, String doc, UserRole role) {
        return User.builder().email(email).passwordHash("x").firstName(first).lastName(last)
                .documentNumber(doc).identityVerified(true).role(role).build();
    }

    private OriginalBooking newBooking(Hotel hotel, String code) {
        return OriginalBooking.builder().hotel(hotel).originalGuest(seller).pmsConfirmationCode(code)
                .checkIn(STAY_IN).checkOut(STAY_OUT).roomType("Doble")
                .totalAmountPaid(new BigDecimal("1500.00")).isLiberaRate(true).build();
    }

    protected Listing saveListing(OriginalBooking booking, String price, boolean split) {
        return listingRepository.save(Listing.builder().originalBooking(booking).seller(seller)
                .listedTotalPrice(new BigDecimal(price)).allowsSplitBooking(split).status(ListingStatus.ACTIVE).build());
    }

    protected ResalePurchase savePurchase(Listing listing, ResalePurchaseStatus status) {
        return purchaseRepository.save(ResalePurchase.builder().listing(listing).buyer(buyer)
                .checkIn(listing.getOriginalBooking().getCheckIn()).checkOut(listing.getOriginalBooking().getCheckOut())
                .totalPrice(listing.getListedTotalPrice()).status(status).build());
    }

    protected ResultActions postJson(String url, String body, User as) throws Exception {
        return mvc.perform(authenticated(post(url).contentType(MediaType.APPLICATION_JSON).content(body), as));
    }

    protected ResultActions postEmpty(String url, User as) throws Exception {
        return mvc.perform(authenticated(post(url), as));
    }

    protected ResultActions getAs(String url, User as) throws Exception {
        return mvc.perform(authenticated(get(url), as));
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder req, User as) {
        if (as != null) {
            req.with(user(String.valueOf(as.getId())).roles(as.getRole().name()));
        }
        return req;
    }
}
