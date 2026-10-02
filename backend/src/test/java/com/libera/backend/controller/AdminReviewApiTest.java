package com.libera.backend.controller;

import com.libera.backend.domain.entity.Listing;
import com.libera.backend.domain.entity.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Revisión de publicaciones por un administrador, comprobantes y solicitudes de demo. */
class AdminReviewApiTest extends ApiTestBase {

    private static final byte[] PDF = "%PDF-1.4 comprobante de prueba".getBytes();

    private long publish() throws Exception {
        String body = """
                {"originalBookingId": %d, "listedTotalPrice": 1200, "allowsSplitBooking": false}
                """.formatted(integrationBooking.getId());
        postJson("/api/v1/listings", body, seller).andExpect(status().isCreated());
        return listingRepository.findAll().stream().mapToLong(Listing::getId).max().orElseThrow();
    }

    private ResultActions uploadVoucher(User as, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/api/v1/bookings/{id}/voucher", integrationBooking.getId()).file(file)
                .with(user(String.valueOf(as.getId())).roles(as.getRole().name())));
    }

    @Nested
    class Review {

        @Test
        void newListingWaitsForReviewAndIsHiddenFromThePublic() throws Exception {
            long id = publish();

            mvc.perform(get("/api/v1/listings")).andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(get("/api/v1/listings/{id}", id)).andExpect(status().isNotFound());
            getAs("/api/v1/listings/" + id, buyer).andExpect(status().isNotFound());
            getAs("/api/v1/listings/" + id, seller).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
            getAs("/api/v1/listings/" + id, admin).andExpect(status().isOk());

            // no se puede comprar mientras está en revisión
            postJson("/api/v1/purchases", """
                    {"listingId": %d, "checkIn": "%s", "checkOut": "%s"}
                    """.formatted(id, STAY_IN, STAY_OUT), buyer).andExpect(status().isConflict());
        }

        @Test
        void adminSeesQueueWithReviewData() throws Exception {
            publish();

            getAs("/api/v1/admin/listings", admin)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].listing.status").value("PENDING_REVIEW"))
                    .andExpect(jsonPath("$[0].sellerEmail").value("vendedor@libera.test"))
                    .andExpect(jsonPath("$[0].pmsConfirmationCode").value("RB-111"))
                    .andExpect(jsonPath("$[0].amountPaid").value(1500.00))
                    .andExpect(jsonPath("$[0].voucherFileName").doesNotExist());
        }

        @Test
        void approvedListingAppearsInCatalog() throws Exception {
            long id = publish();

            postEmpty("/api/v1/admin/listings/" + id + "/approve", admin)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.listing.status").value("ACTIVE"));
            mvc.perform(get("/api/v1/listings")).andExpect(jsonPath("$", hasSize(1)));
            // ya no está en revisión: no se puede aprobar de nuevo
            postEmpty("/api/v1/admin/listings/" + id + "/approve", admin).andExpect(status().isConflict());
        }

        @Test
        void rejectionNeedsReasonAndBlocksRelisting() throws Exception {
            long id = publish();

            postJson("/api/v1/admin/listings/" + id + "/reject", "{}", admin).andExpect(status().isBadRequest());
            postJson("/api/v1/admin/listings/" + id + "/reject", "{\"note\": \"No pudimos validar la reserva con el hotel\"}", admin)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.listing.status").value("REJECTED"))
                    .andExpect(jsonPath("$.listing.reviewNote").value("No pudimos validar la reserva con el hotel"));

            getAs("/api/v1/listings/mine", seller).andExpect(jsonPath("$[0].reviewNote").value("No pudimos validar la reserva con el hotel"));
            postJson("/api/v1/listings", """
                    {"originalBookingId": %d, "listedTotalPrice": 1000, "allowsSplitBooking": false}
                    """.formatted(integrationBooking.getId()), seller).andExpect(status().isConflict());
        }

        @Test
        void sellerCanCancelWhileUnderReview() throws Exception {
            long id = publish();

            postEmpty("/api/v1/listings/" + id + "/cancel", seller).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
            getAs("/api/v1/admin/listings", admin).andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void regularUsersCannotReview() throws Exception {
            long id = publish();

            postEmpty("/api/v1/admin/listings/" + id + "/approve", seller).andExpect(status().isForbidden());
            getAs("/api/v1/admin/listings", buyer).andExpect(status().isForbidden());
        }
    }

    @Nested
    class Vouchers {

        @Test
        void ownerUploadsAndAdminDownloads() throws Exception {
            uploadVoucher(seller, new MockMultipartFile("file", "reserva.pdf", "application/pdf", PDF))
                    .andExpect(status().isNoContent());
            publish();

            getAs("/api/v1/admin/listings", admin).andExpect(jsonPath("$[0].voucherFileName").value("reserva.pdf"));
            getAs("/api/v1/admin/bookings/" + integrationBooking.getId() + "/voucher", admin)
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(content().bytes(PDF));
            getAs("/api/v1/bookings/mine", seller)
                    .andExpect(jsonPath("$[?(@.id == " + integrationBooking.getId() + ")].hasVoucher").value(true))
                    .andExpect(jsonPath("$[?(@.id == " + integrationBooking.getId() + ")].listingStatus").value("PENDING_REVIEW"))
                    // la otra reserva nunca se publicó
                    .andExpect(jsonPath("$[?(@.id == " + convenioBooking.getId() + ")].listingStatus").value(contains(nullValue())));
        }

        @Test
        void uploadingAgainReplacesTheFile() throws Exception {
            uploadVoucher(seller, new MockMultipartFile("file", "viejo.pdf", "application/pdf", PDF)).andExpect(status().isNoContent());
            uploadVoucher(seller, new MockMultipartFile("file", "nuevo.png", "image/png", new byte[]{1, 2, 3})).andExpect(status().isNoContent());

            getAs("/api/v1/admin/bookings/" + integrationBooking.getId() + "/voucher", admin)
                    .andExpect(header().string("Content-Type", "image/png"))
                    .andExpect(content().bytes(new byte[]{1, 2, 3}));
        }

        @Test
        void rejectsOtherUsersAndInvalidFiles() throws Exception {
            uploadVoucher(buyer, new MockMultipartFile("file", "reserva.pdf", "application/pdf", PDF)).andExpect(status().isForbidden());
            uploadVoucher(seller, new MockMultipartFile("file", "virus.exe", "application/octet-stream", PDF)).andExpect(status().isBadRequest());
            uploadVoucher(seller, new MockMultipartFile("file", "vacio.pdf", "application/pdf", new byte[0])).andExpect(status().isBadRequest());
            uploadVoucher(seller, new MockMultipartFile("file", "grande.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void onlyAdminsCanDownload() throws Exception {
            uploadVoucher(seller, new MockMultipartFile("file", "reserva.pdf", "application/pdf", PDF)).andExpect(status().isNoContent());

            getAs("/api/v1/admin/bookings/" + integrationBooking.getId() + "/voucher", seller).andExpect(status().isForbidden());
            getAs("/api/v1/admin/bookings/" + convenioBooking.getId() + "/voucher", admin).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Contact {

        private static final String REQUEST = """
                {"name": "Laura Méndez", "hotel": "Hostería del Bosque", "email": "Laura@Hosteria.test",
                 "phone": "+54 294 400 0000", "rooms": "1 – 30", "message": "Queremos la tarifa revendible"}
                """;

        @Test
        void hotelsSendRequestsWithoutAccountAndAdminsSeeThem() throws Exception {
            mvc.perform(post("/api/v1/contact").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                    .andExpect(status().isCreated());

            getAs("/api/v1/admin/contact-requests", admin)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].hotelName").value("Hostería del Bosque"))
                    .andExpect(jsonPath("$[0].email").value("laura@hosteria.test"))
                    .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
        }

        @Test
        void validatesRequiredFields() throws Exception {
            mvc.perform(post("/api/v1/contact").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"\", \"email\": \"no-es-email\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void onlyAdminsCanList() throws Exception {
            getAs("/api/v1/admin/contact-requests", seller).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/admin/contact-requests")).andExpect(status().isUnauthorized());
        }
    }
}
