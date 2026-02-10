package com.uos.lms.lecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uos.lms.user.User;
import com.uos.lms.user.UserRepository;
import com.uos.lms.user.UserRole;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LectureDeleteApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL = "admin-delete-test@lms.local";
    private static final String USER_EMAIL = "user-delete-test@lms.local";
    private static final String PASSWORD = "test1234!";

    @BeforeEach
    void setUp() {
        lectureRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(UserRole.ADMIN)
                .build());

        userRepository.save(User.builder()
                .email(USER_EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(UserRole.USER)
                .build());
    }

    @Test
    void adminCanDeleteLecture() throws Exception {
        Lecture lecture = lectureRepository.save(Lecture.builder()
                .title("Delete target")
                .videoUrl("https://static.uoscholar-server.store/uploads/test/video.mp4")
                .build());

        String accessToken = loginAndGetToken(ADMIN_EMAIL, PASSWORD);

        mockMvc.perform(delete("/api/lectures/{lectureId}", lecture.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lectureId").value(lecture.getId()))
                .andExpect(jsonPath("$.objectDeleteAttempted").value(false))
                .andExpect(jsonPath("$.objectDeleted").value(false));

        assertThat(lectureRepository.existsById(lecture.getId())).isFalse();
    }

    @Test
    void userCannotDeleteLecture() throws Exception {
        Lecture lecture = lectureRepository.save(Lecture.builder()
                .title("Protected lecture")
                .videoUrl("https://static.uoscholar-server.store/uploads/test/video.mp4")
                .build());

        String accessToken = loginAndGetToken(USER_EMAIL, PASSWORD);

        mockMvc.perform(delete("/api/lectures/{lectureId}", lecture.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        assertThat(lectureRepository.existsById(lecture.getId())).isTrue();
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
