package com.PrimeCare.PrimeCare.shared.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerInputTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new InputController())
                                 .setControllerAdvice(new GlobalExceptionHandler())
                                 .setValidator(validator)
                                 .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                                 .build();
    }

    @Test
    void malformedJsonReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(post("/test/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
               .andExpect(jsonPath("$.message").value(not(containsString("JsonParseException"))));
    }

    @Test
    void jsonEnumMismatchReturnsSafeBadRequest() throws Exception {
        String payload = """
                {
                  "id": 1,
                  "visitDate": "2026-04-26",
                  "status": "BROKEN"
                }
                """;

        mockMvc.perform(post("/test/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
               .andExpect(jsonPath("$.message").value(not(containsString("InvalidFormatException"))))
               .andExpect(jsonPath("$.details.fields.status").exists());
    }

    @Test
    void missingRequestParameterReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(get("/test/param"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"))
               .andExpect(jsonPath("$.details.fields.id").exists());
    }

    @Test
    void requestParameterTypeMismatchReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(get("/test/param").param("id", "abc"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.success").value(false))
               .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
               .andExpect(jsonPath("$.details.fields.id").exists());
    }

    @RestController
    static class InputController {
        @PostMapping("/test/input")
        String input(@Valid @RequestBody InputRequest request) {
            return "ok";
        }

        @GetMapping("/test/param")
        String param(@RequestParam Long id,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
            return "ok";
        }
    }

    @Data
    static class InputRequest {
        @NotNull
        private Long id;
        @NotNull
        private LocalDate visitDate;
        @NotNull
        private TestStatus status;
    }

    enum TestStatus {
        OPEN,
        CLOSED
    }
}
