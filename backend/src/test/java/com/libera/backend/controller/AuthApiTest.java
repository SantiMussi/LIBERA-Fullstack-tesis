package com.libera.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo real de autenticación: registro -> login -> llamadas con "Authorization: Bearer <token>". */
class AuthApiTest extends ApiTestBase {

    private static final String REGISTER = """
            {"email": "Nueva@Libera.test", "password": "secreta123", "firstName": "Lucía",
             "lastName": "Díaz", "documentNumber": "40111222"}
            """;

    private String token(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        return json.replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return token(result);
    }

    @Test
    void registerReturnsTokenAndNormalizesEmail() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.email").value("nueva@libera.test"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.identityVerified").value(false))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER.replace("Nueva@Libera.test", "nueva@libera.test")))
                .andExpect(status().isConflict());
    }

    @Test
    void registerValidatesInput() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"no-es-email\", \"password\": \"corta\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithTokenGivesAccessToProtectedEndpoints() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER));
        String token = login("nueva@libera.test", "secreta123");

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Lucía"));

        // el id del token es el que usan los servicios: el usuario puede cargar una reserva propia
        mvc.perform(post("/api/v1/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hotelId": %d, "pmsConfirmationCode": "RB-777", "checkIn": "%s", "checkOut": "%s",
                                 "roomType": "Suite", "totalAmountPaid": 900}
                                """.formatted(integrationHotel.getId(), LocalDate.now().plusDays(5), LocalDate.now().plusDays(7))))
                .andExpect(status().isCreated());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveSameError() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nueva@libera.test\", \"password\": \"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nadie@libera.test\", \"password\": \"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void invalidOrMissingTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer token.falso.123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsLocalFrontend() throws Exception {
        mvc.perform(options("/api/v1/listings")
                        .header(HttpHeaders.ORIGIN, "http://localhost:8765")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8765"));

        mvc.perform(options("/api/v1/listings")
                        .header(HttpHeaders.ORIGIN, "https://sitio-malicioso.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }
}
