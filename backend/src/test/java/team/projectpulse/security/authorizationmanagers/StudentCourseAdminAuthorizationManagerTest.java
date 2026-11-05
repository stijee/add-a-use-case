package team.projectpulse.security.authorizationmanagers;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import team.projectpulse.student.StudentSecurityService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StudentCourseAdminAuthorizationManagerTest {

    @Mock
    StudentSecurityService studentSecurityService;

    @InjectMocks
    StudentCourseAdminAuthorizationManager studentCourseAdminAuthorizationManager;


    private RequestAuthorizationContext contextWith(Map<String, String> variables) {
        return new RequestAuthorizationContext(mock(HttpServletRequest.class), variables);
    }

    @Test
    @DisplayName("Grants exactly what the course-admin check answers for the bound studentId")
    void delegatesTheBoundStudentId() {
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(7)).willReturn(true);
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(8)).willReturn(false);

        AuthorizationResult granted = this.studentCourseAdminAuthorizationManager.authorize(() -> null, contextWith(Map.of("studentId", "7")));
        AuthorizationResult denied = this.studentCourseAdminAuthorizationManager.authorize(() -> null, contextWith(Map.of("studentId", "8")));

        assertThat(granted).isNotNull();
        assertThat(granted.isGranted()).isTrue();
        assertThat(denied).isNotNull();
        assertThat(denied.isGranted()).isFalse();
    }

    @Test
    @DisplayName("Denies when the matched route bound no usable studentId")
    void deniesWhenTheVariableIsAbsentOrNotANumber() {
        AuthorizationResult absent = this.studentCourseAdminAuthorizationManager.authorize(() -> null, contextWith(Map.of()));
        AuthorizationResult notANumber = this.studentCourseAdminAuthorizationManager.authorize(() -> null, contextWith(Map.of("studentId", "x")));

        assertThat(absent.isGranted()).isFalse();
        assertThat(notANumber.isGranted()).isFalse();
        verifyNoInteractions(this.studentSecurityService);
    }

}
