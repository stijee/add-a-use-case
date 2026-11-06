package team.projectpulse.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer>, JpaSpecificationExecutor<Student> {

    // Find all students in a section
    List<Student> findBySectionSectionId(Integer sectionId);

    // The team's students whose account is active: the teammates a peer evaluation form lists (BR-evaluatee-active)
    List<Student> findByTeamTeamIdAndEnabledTrue(Integer teamId);

    // The student, found only for a user who may decide her account's lifecycle: an instructor assigned to her course
    // section, or the course admin of the course that course section belongs to (BR-student-lifecycle). For anyone
    // else the student is not found, so the scope is part of the load rather than a comparison made after it.
    @Query("""
            select s
            from Student s
            where s.id = :studentId
              and (s.section.course.courseAdmin.id = :userId
                or exists (select 1 from Section sec join sec.instructors i where sec = s.section and i.id = :userId))
            """)
    Optional<Student> findByIdWithinAuthorityOf(@Param("studentId") Integer studentId, @Param("userId") Integer userId);
}
