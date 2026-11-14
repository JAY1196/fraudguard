package com.fraudguard.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new ExceptionThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void dedicatedRequestParameterExceptionShouldReturn400() throws Exception {
        mockMvc.perform(get("/test/invalid-parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("INVALID_REQUEST_PARAMETER")));
    }

    @Test
    void unexpectedIllegalArgumentExceptionShouldReturn500() throws Exception {
        mockMvc.perform(get("/test/unexpected-argument"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.code", is("INTERNAL_SERVER_ERROR")));
    }

    @RestController
    static class ExceptionThrowingController {

        @GetMapping("/test/invalid-parameter")
        void invalidParameter() {
            throw new InvalidRequestParameterException("Invalid pagination limit");
        }

        @GetMapping("/test/unexpected-argument")
        void unexpectedArgument() {
            throw new IllegalArgumentException("Unexpected internal argument error");
        }
    }
}