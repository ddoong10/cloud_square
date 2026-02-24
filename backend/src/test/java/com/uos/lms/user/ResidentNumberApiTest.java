package com.uos.lms.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResidentNumberApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "resident-check@lms.local";
    private static final String PASSWORD = "test1234!";
    private static final String RESIDENT_NUMBER = "9010101234567";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .residentNumberEncrypted(RESIDENT_NUMBER)
                .residentNumberHash("dummy")
                .role(UserRole.STUDENT)
                .build());
    }

    @Test
    void returnsDecryptedResidentNumberForCurrentUser() throws Exception {
        String accessToken = loginAndGetToken(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/me/resident-number")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.encryptedAtRest").value(false))
                .andExpect(jsonPath("$.residentNumber").value("901010-1******"));
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String payload = objectMapper.writeValueAsString(new LoginPayload(email, password));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return jsonNode.get("accessToken").asText();
    }

    private record LoginPayload(String email, String password) {
    }
}
