package team.projectpulse.system.exception;

/**
 * Thrown when a student whose account is already inactive is deactivated again (UC-STU-deactivate-student 3a2).
 * Mapped to {@code 409 CONFLICT}.
 */
public class StudentAlreadyDeactivatedException extends RuntimeException {

    public StudentAlreadyDeactivatedException() {
        super("The student is already deactivated.");
    }

}
