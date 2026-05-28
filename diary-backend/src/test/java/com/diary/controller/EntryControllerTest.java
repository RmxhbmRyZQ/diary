package com.diary.controller;

import com.diary.security.RateLimitFilter;
import com.diary.security.RateLimiterService;
import com.diary.security.SessionFilter;
import com.diary.service.EntryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = EntryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class EntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EntryService entryService;

    @MockBean
    private SessionFilter sessionFilter;

    @MockBean
    private RateLimitFilter rateLimitFilter;

    @MockBean
    private RateLimiterService rateLimiterService;

    @Test
    void should_return_400_when_missing_required_fields() throws Exception {
        mockMvc.perform(post("/api/v1/entries")
                        .contentType("application/json")
                        .content("{\"diaryDate\":\"2026-05-27\"}"))
                .andExpect(status().isBadRequest());
    }
}
