package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.hamcrest.Matchers;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.activity.ActivityRepository;
import team.projectpulse.evaluation.PeerEvaluationRepository;
import team.projectpulse.evaluation.dto.PeerEvaluationDto;
import team.projectpulse.evaluation.dto.RatingDto;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.instructor.InstructorRepository;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.StatusCode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student, end to end through the API: the main flow for both permitted actors, 3a2, 3b, and the
 * three steps of 6a (6a1: the peer evaluation form and a submission; 6a2: the course section's report; 6a3: he stays
 * on his team, asserted in the owning course admin's main flow). 3a1 and 5a are UI behavior and are covered by
 * {@code cypress/e2e/deactivate-student.spec.cy.js}.
 *
 * <p>Fixtures, from the dev seed: Woody Allen (id 7) is on Team2 (id 2) in course section 2023-2024 (id 2) with
 * Amanda (8), Cora (9), and Agustin (10). Peer evaluations of him exist for 2023-W31 only, and the dev clock makes
 * 2023-W32 the open previous week. Bingyang Wei owns the course and is assigned to the course section; Bill Gates is
 * assigned to it; Tim Cook owns another course and is assigned to no course section of this one.
 */
@DisplayName("Integration tests for UC-STU-deactivate-student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    private static final int WOODY_ID = 7;
    private static final int AMANDA_ID = 8;
    private static final int CORA_ID = 9;
    private static final int TEAM2_ID = 2;
    private static final int SECTION2_ID = 2;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    SectionRepository sectionRepository;

    @Autowired
    InstructorRepository instructorRepository;

    @Autowired
    ActivityRepository activityRepository;

    @Autowired
    PeerEvaluationRepository evaluationRepository;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String adminBingyangToken;
    String instructorBillToken;
    String adminTimToken;
    String studentWoodyToken;
    String studentJohnToken;
    String studentAmandaToken;


    @BeforeEach
    void setUp() throws Exception {
        this.adminBingyangToken = login("b.wei@abc.edu");
        this.instructorBillToken = login("b.gates@abc.edu");
        this.adminTimToken = login("t.cook@abc.edu");
        this.studentWoodyToken = login("w.allen@abc.edu");
        this.studentJohnToken = login("j.smith@abc.edu");
        this.studentAmandaToken = login("a.wagner@abc.edu");
    }

    private String login(String email) throws Exception {
        String body = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456")))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + new JSONObject(body).getJSONObject("data").getString("token");
    }

    private ResultActions deactivate(int studentId, String token) throws Exception {
        return this.mockMvc.perform(patch(this.baseUrl + "/students/" + studentId + "/deactivate")
                .accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token));
    }

    private void deactivateWoody() throws Exception {
        deactivate(WOODY_ID, this.instructorBillToken).andExpect(status().isOk());
    }

    private String evaluationJson(int evaluatorId, int evaluateeId, String week) {
        List<RatingDto> ratings = List.of(
                new RatingDto(null, 1, 8.0), new RatingDto(null, 2, 8.0), new RatingDto(null, 3, 8.0),
                new RatingDto(null, 4, 8.0), new RatingDto(null, 5, 8.0), new RatingDto(null, 6, 8.0));
        return this.jsonMapper.writeValueAsString(new PeerEvaluationDto(null, week, evaluatorId, null, evaluateeId, null, ratings, 48.0, "Public", "Private", null, null));
    }

    @Test
    @DisplayName("Main flow and 6a3: the owning course admin, not assigned to the course section, deactivates a student, who stays on his team")
    void owningCourseAdminDeactivatesStudent() throws Exception {
        // Given: Bingyang is removed from the course section's instructors, so only the course-admin check can admit him
        Section section2 = this.sectionRepository.findById(SECTION2_ID).orElseThrow();
        Instructor bingyang = this.instructorRepository.findById(1).orElseThrow();
        assertThat(bingyang.getEmail()).isEqualTo("b.wei@abc.edu");
        section2.removeInstructor(bingyang);
        this.entityManager.flush();

        // When and then
        deactivate(WOODY_ID, this.adminBingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.id").value(WOODY_ID))
                .andExpect(jsonPath("$.data.enabled").value(false));

        // Her next login fails (FR-SEC-active-account)
        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("w.allen@abc.edu", "123456")))
                .andExpect(status().isUnauthorized());

        // Her submitted work, team, and course section are kept (BR-student-lifecycle)
        assertThat(this.activityRepository.findAll()).anyMatch(activity -> activity.getStudent().getId().equals(WOODY_ID));
        assertThat(this.evaluationRepository.findByWeekAndEvaluateeId("2023-W31", WOODY_ID)).isNotEmpty();
        Student woody = this.studentRepository.findById(WOODY_ID).orElseThrow();
        assertThat(woody.getTeam().getTeamId()).isEqualTo(TEAM2_ID);
        assertThat(woody.getSection().getSectionId()).isEqualTo(SECTION2_ID);

        // The team roster (POST /students/search) still lists him on Team2
        this.mockMvc.perform(post(this.baseUrl + "/students/search").contentType(MediaType.APPLICATION_JSON)
                        .content(this.jsonMapper.writeValueAsString(Map.of("teamName", "Team2")))
                        .header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(jsonPath("$.data.content[*].id", Matchers.hasItem(WOODY_ID)));
    }

    @Test
    @DisplayName("Main flow: an instructor assigned to the course section deactivates a student")
    void assignedInstructorDeactivatesStudent() throws Exception {
        deactivate(WOODY_ID, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.data.enabled").value(false));

        assertThat(this.studentRepository.findById(WOODY_ID).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("3a2: deactivating a student who is already deactivated is a 409 and changes nothing")
    void deactivatingAnInactiveStudentIsAConflict() throws Exception {
        deactivateWoody();

        deactivate(WOODY_ID, this.adminBingyangToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT))
                .andExpect(jsonPath("$.message").value("The student is already deactivated."));

        Student woody = this.studentRepository.findById(WOODY_ID).orElseThrow();
        assertThat(woody.isEnabled()).isFalse();
        assertThat(woody.getTeam().getTeamId()).isEqualTo(TEAM2_ID);
    }

    @Test
    @DisplayName("3b: the student herself, another student, an unassigned instructor who owns another course, and a missing id are refused")
    void unpermittedCallersAreRefused() throws Exception {
        for (String token : List.of(this.studentWoodyToken, this.studentJohnToken, this.adminTimToken)) {
            deactivate(WOODY_ID, token)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.flag").value(false))
                    .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN))
                    .andExpect(jsonPath("$.message").value("No permission."));
        }
        deactivate(999, this.adminBingyangToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        assertThat(this.studentRepository.findById(WOODY_ID).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("6a1: the peer evaluation form no longer lists the deactivated teammate")
    void teamListingOmitsDeactivatedTeammate() throws Exception {
        deactivateWoody();

        this.mockMvc.perform(get(this.baseUrl + "/students/teams/" + TEAM2_ID).accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, this.studentAmandaToken))
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data", Matchers.hasSize(3)))
                .andExpect(jsonPath("$.data[*].firstName", Matchers.containsInAnyOrder("Amanda", "Cora", "Agustin")));
    }

    @Test
    @DisplayName("6a1: a peer evaluation of the deactivated teammate is refused; one of an active teammate still saves")
    void evaluationOfDeactivatedTeammateIsRefused() throws Exception {
        deactivateWoody();

        this.mockMvc.perform(post(this.baseUrl + "/evaluations").contentType(MediaType.APPLICATION_JSON)
                        .content(evaluationJson(AMANDA_ID, WOODY_ID, "2023-W32"))
                        .header(HttpHeaders.AUTHORIZATION, this.studentAmandaToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.INVALID_ARGUMENT))
                .andExpect(jsonPath("$.message").value("You can evaluate only a teammate whose account is active."));
        assertThat(this.evaluationRepository.findByWeekAndEvaluatorIdAndEvaluateeId("2023-W32", AMANDA_ID, WOODY_ID)).isEmpty();

        this.mockMvc.perform(post(this.baseUrl + "/evaluations").contentType(MediaType.APPLICATION_JSON)
                        .content(evaluationJson(AMANDA_ID, CORA_ID, "2023-W32"))
                        .header(HttpHeaders.AUTHORIZATION, this.studentAmandaToken))
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS));
    }

    @Test
    @DisplayName("6a2: the course section's report lists the deactivated student only for a week with evaluations of him")
    void reportListsDeactivatedStudentOnlyWhenEvaluated() throws Exception {
        deactivateWoody();

        this.mockMvc.perform(get(this.baseUrl + "/evaluations/sections/" + SECTION2_ID + "/week/2023-W32").accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.peerEvaluationAverages[*].studentId", Matchers.not(Matchers.hasItem(WOODY_ID))))
                .andExpect(jsonPath("$.data.peerEvaluationAverages[*].studentId", Matchers.hasItem(AMANDA_ID)));

        this.mockMvc.perform(get(this.baseUrl + "/evaluations/sections/" + SECTION2_ID + "/week/2023-W31").accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.peerEvaluationAverages[*].studentId", Matchers.hasItem(WOODY_ID)))
                .andExpect(jsonPath("$.data.peerEvaluationAverages[*].studentId", Matchers.hasItem(AMANDA_ID)));
    }

}
