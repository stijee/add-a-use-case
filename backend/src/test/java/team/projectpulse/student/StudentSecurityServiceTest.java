package team.projectpulse.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.projectpulse.course.Course;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.UserUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StudentSecurityServiceTest {

    @Mock
    StudentRepository studentRepository;
    @Mock
    UserUtils userUtils;

    @InjectMocks
    StudentSecurityService studentSecurityService;

    Student woody;

    @BeforeEach
    void setUp() {
        Instructor bingyang = new Instructor("b.wei@abc.edu", "Bingyang", "Wei", "b.wei@abc.edu", "123456", true, "admin instructor");
        bingyang.setId(1);

        Course course = new Course("COSC 40993 Senior Design", "Senior design project course");
        course.setCourseAdmin(bingyang);

        Section section = new Section("2023-2024", LocalDate.of(2023, 7, 31), LocalDate.of(2024, 10, 6), true, DayOfWeek.MONDAY, LocalTime.of(23, 59), DayOfWeek.TUESDAY, LocalTime.of(23, 59));
        course.addSection(section);

        this.woody = new Student("w.allen@abc.edu", "Woody", "Allen", "w.allen@abc.edu", "123456", true, "student");
        this.woody.setId(7);
        section.addStudent(this.woody);
    }

    @Test
    void testOwningCourseAdminIsAdminOfStudentCourse() {
        given(this.userUtils.getUserId()).willReturn(1);
        given(this.studentRepository.findById(7)).willReturn(Optional.of(this.woody));

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(7)).isTrue();
    }

    @Test
    void testAnotherCoursesAdminIsNotAdminOfStudentCourse() {
        given(this.userUtils.getUserId()).willReturn(3); // Tim Cook, admin of another course
        given(this.studentRepository.findById(7)).willReturn(Optional.of(this.woody));

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(7)).isFalse();
    }

    @Test
    void testMissingStudentIsDeniedNotThrown() {
        given(this.userUtils.getUserId()).willReturn(1);
        given(this.studentRepository.findById(999)).willReturn(Optional.empty());

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(999)).isFalse();
    }

}
