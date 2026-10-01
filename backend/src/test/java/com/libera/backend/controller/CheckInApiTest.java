package com.libera.backend.controller;

import com.libera.backend.domain.entity.ResalePurchase;
import com.libera.backend.domain.entity.Transaction;
import com.libera.backend.domain.enums.ResalePurchaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckInApiTest extends ApiTestBase {

    @Autowired
    private TransactionTemplate tx;

    private ResalePurchase purchase;

    @BeforeEach
    void createPurchase() {
        purchase = savePurchase(saveListing(integrationBooking, "1050.00", false), ResalePurchaseStatus.NAME_CHANGED);
    }

    private ResalePurchaseStatus purchaseStatus() {
        return purchaseRepository.findById(purchase.getId()).orElseThrow().getStatus();
    }

    @Nested
    class PmsWebhook {

        private ResultActions sendWebhook(String apiKey, String code) throws Exception {
            var req = post("/api/v1/webhooks/pms/checkin").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"resalePurchaseId": %d, "pmsConfirmationCode": "%s"}
                            """.formatted(purchase.getId(), code));
            if (apiKey != null) {
                req.header("X-API-KEY", apiKey);
            }
            return mvc.perform(req);
        }

        @Test
        void liquidatesPurchaseAndRecordsTransaction() throws Exception {
            sendWebhook(API_KEY, "RB-111").andExpect(status().isOk());

            assertThat(purchaseStatus()).isEqualTo(ResalePurchaseStatus.LIQUIDATED);

            List<Transaction> txs = transactionRepository.findAll();
            assertThat(txs).hasSize(1);
            Transaction t = txs.getFirst();
            assertThat(t.getTotalPaidByBuyer()).isEqualByComparingTo("1050.00");
            assertThat(t.getSellerPayoutAmount()).isEqualByComparingTo("850.00");
            assertThat(t.getHotelRevenueShareAmount()).isEqualByComparingTo("40.00");
            assertThat(t.getLiberaNetRevenue()).isEqualByComparingTo("160.00");
            Long linkedPurchaseId = tx.execute(s -> transactionRepository.findById(t.getId()).orElseThrow().getResalePurchase().getId());
            assertThat(linkedPurchaseId).isEqualTo(purchase.getId());
        }

        @Test
        void isPublicButRequiresApiKey() throws Exception {
            sendWebhook(null, "RB-111").andExpect(status().isUnauthorized());
            sendWebhook("clave-incorrecta", "RB-111").andExpect(status().isUnauthorized());
            assertThat(transactionRepository.count()).isZero();
        }

        @Test
        void rejectsWrongConfirmationCode() throws Exception {
            sendWebhook(API_KEY, "RB-FALSO").andExpect(status().isForbidden());

            assertThat(purchaseStatus()).isEqualTo(ResalePurchaseStatus.NAME_CHANGED);
            assertThat(transactionRepository.count()).isZero();
        }

        @Test
        void validatesBody() throws Exception {
            mvc.perform(post("/api/v1/webhooks/pms/checkin").header("X-API-KEY", API_KEY)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void secondWebhookForSamePurchaseIsIdempotent() throws Exception {
            sendWebhook(API_KEY, "RB-111").andExpect(status().isOk());
            sendWebhook(API_KEY, "RB-111").andExpect(status().isOk());

            assertThat(transactionRepository.count()).isEqualTo(1);
            assertThat(purchaseStatus()).isEqualTo(ResalePurchaseStatus.LIQUIDATED);
        }
    }

    @Nested
    class Admin {

        @Test
        void adminConfirmsCheckInManually() throws Exception {
            postEmpty("/api/v1/admin/purchases/" + purchase.getId() + "/check-in", admin)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("LIQUIDATED"));
            assertThat(transactionRepository.count()).isEqualTo(1);
        }

        @Test
        void adminListsPurchasesByStatus() throws Exception {
            getAs("/api/v1/admin/purchases", admin).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
            getAs("/api/v1/admin/purchases?status=NAME_CHANGED", admin).andExpect(jsonPath("$", hasSize(1)));
            getAs("/api/v1/admin/purchases?status=DISPUTED", admin).andExpect(jsonPath("$", hasSize(0)));
            getAs("/api/v1/admin/purchases?status=NO_EXISTE", admin).andExpect(status().isBadRequest());
        }

        @Test
        void regularUsersCannotUseAdminEndpoints() throws Exception {
            postEmpty("/api/v1/admin/purchases/" + purchase.getId() + "/check-in", seller).andExpect(status().isForbidden());
            getAs("/api/v1/admin/purchases", buyer).andExpect(status().isForbidden());
            getAs("/api/v1/admin/purchases", null).andExpect(status().isUnauthorized());
            assertThat(transactionRepository.count()).isZero();
        }
    }
}
