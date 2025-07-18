import java.io.FileInputStream;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class StudentTransactions {    
    private Connection connection;
    
    public StudentTransactions() throws SQLException {
        try {
            //from config
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream("config.properties")) {
                props.load(fis);
            }

            String DB_URL = props.getProperty("DB_URL");
            String DB_USER = props.getProperty("DB_USER");
            String DB_PASSWORD = props.getProperty("DB_PASSWORD");
            Class.forName("oracle.jdbc.driver.OracleDriver");

            Properties connectionProps = new Properties();
            connectionProps.setProperty("user", DB_USER);
            connectionProps.setProperty("password", DB_PASSWORD);

            this.connection = DriverManager.getConnection(DB_URL, connectionProps);
            System.out.println("Connected to database");

        } catch (ClassNotFoundException | IOException e) {
            throw new SQLException("Error loading configuration or driver", e);
        }
    }
    
    // List courses for current quarter
    public void listCurrentCourses(String permNumber) {
        System.out.println("\n=== Current Courses for Student " + permNumber + " ===");
        
        String sql = "SELECT co.enrollment_code, co.course_number, c.title, co.quarter, co.year, " +
                    "co.prof_first_name, co.prof_last_name " +
                    "FROM Enrolls_in e " +
                    "JOIN Course_Offering co ON e.enrollment_code = co.enrollment_code " +
                    "JOIN Course c ON co.course_number = c.course_number " +
                    "WHERE e.perm_number = ? AND e.status = 'Current' " +
                    "ORDER BY co.course_number";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, permNumber);
            ResultSet rs = stmt.executeQuery();
            boolean foundCourses = false;
            while (rs.next()) {
                System.out.printf("%-8s %-20s %s %d (Prof: %s %s) [Code: %s]%n", 
                    rs.getString("course_number"),
                    rs.getString("title"),
                    rs.getString("quarter"),
                    rs.getInt("year"),
                    rs.getString("prof_first_name"),
                    rs.getString("prof_last_name"),
                    rs.getString("enrollment_code"));
                foundCourses = true;
            }
            if (!foundCourses) {
                System.out.println("No current courses found.");
            }
        } catch (SQLException e) {
            System.err.println("Error listing current courses: " + e.getMessage());
        }
    }
    
    // Add course for student in current quarter
    public boolean addCourse(String permNumber, String enrollmentCode) {
        System.out.println("\n=== Adding Course " + enrollmentCode + " for Student " + permNumber + " ===");
        if (!studentExists(permNumber)) {
            System.out.println("Failed to add course: Student " + permNumber + " not found.");
            return false;
        }
        if (!courseHasSpaceAndIsCurrent(enrollmentCode)) {
            System.out.println("Failed to add course.");
            return false;
        }
        if (isStudentEnrolled(permNumber, enrollmentCode)) {
            System.out.println("Failed to add course.");
            return false;
        }
        int currentCourseCount = getCurrentCourseCount(permNumber);
        if (currentCourseCount >= 5) {
            System.out.println("Failed to add course.");
            return false;
        }
        if (!hasPrerequisites(permNumber, enrollmentCode)) {
            System.out.println("Failed to add course.");
            return false;
        }
        
        String sql = "INSERT INTO Enrolls_in (perm_number, enrollment_code, year, quarter, status) " +
                    "SELECT ?, ?, co.year, co.quarter, 'Current' " +
                    "FROM Course_Offering co " +
                    "WHERE co.enrollment_code = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, permNumber);
            stmt.setString(2, enrollmentCode);
            stmt.setString(3, enrollmentCode);
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Successfully enrolled in course " + enrollmentCode);
                return true;
            } else {
                System.out.println("Failed to add course.");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Database error adding course: " + e.getMessage());
            System.out.println("Failed to add course.");
            return false;
        }
    }
    
    // Drop course with PIN verification for students
    public boolean dropCourse(String permNumber, String pin, String enrollmentCode) {
        System.out.println("\n=== Dropping Course " + enrollmentCode + " for Student " + permNumber + " ===");
        if (!verifyPin(permNumber, pin)) {
            return false;
        }
        return dropCourseInternal(permNumber, enrollmentCode);
    }
    
    // Drop course for registrar (no PIN required)
    public boolean dropCourse(String permNumber, String enrollmentCode) {
        System.out.println("\n=== [REGISTRAR] Dropping Course " + enrollmentCode + " for Student " + permNumber + " ===");
        return dropCourseInternal(permNumber, enrollmentCode);
    }
    
    // Internal method for dropping courses
    private boolean dropCourseInternal(String permNumber, String enrollmentCode) {
        // Check if student is currently enrolled
        String checkSql = "SELECT 1 FROM Enrolls_in WHERE perm_number = ? AND enrollment_code = ? AND status = 'Current'";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setString(1, permNumber);
            checkStmt.setString(2, enrollmentCode);
            if (!checkStmt.executeQuery().next()) {
                 System.out.println(" Error: Student " + permNumber + " is not currently enrolled in course offering " + enrollmentCode);
                 return false;
            }
        } catch (SQLException e) {
            System.err.println(" Database error checking enrollment before drop: " + e.getMessage());
            return false;
        }

        // Prevent dropping the only course
        int currentCourseCount = getCurrentCourseCount(permNumber);
        if (currentCourseCount <= 1) {
            System.out.println("Error: Cannot drop course, this is the student's only course");
            System.out.println("  (Students must stay enrolled in at least one course)");
            return false;
        }
        
        String sql = "DELETE FROM Enrolls_in " +
                    "WHERE perm_number = ? AND enrollment_code = ? AND status = 'Current'";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, permNumber);
            stmt.setString(2, enrollmentCode);
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Successfully dropped course " + enrollmentCode + " for student " + permNumber);
                return true;
            } else {
                System.out.println("Failed to drop course (no rows deleted).");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Database error dropping course: " + e.getMessage());
            return false;
        }
    }
    
    // Verify student PIN
    public boolean verifyPin(String permNumber, String pin) {
        String sql = "SELECT VerifyPin(?, ?) FROM DUAL";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, permNumber);
            stmt.setString(2, pin);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                boolean isValid = rs.getInt(1) == 1;
                if (!isValid) {
                    System.out.println("Invalid PIN for student " + permNumber);
                }
                return isValid;
            }
        } catch (SQLException e) {
            System.err.println("Error verifying PIN: " + e.getMessage());
        }
        return false;
    }
    
    // Change student PIN
    public boolean changePin(String permNumber, String oldPin, String newPin) {
        System.out.println("\n=== Changing PIN for Student " + permNumber + " ===");
        
        // Validate new PIN format
        if (newPin == null || newPin.length() != 5 || !newPin.matches("\\d{5}")) {
             System.out.println("Error: New PIN must be a five-digit number.");
             return false;
        }
        // Check if PIN is already in use
        if (isPinInUse(newPin, permNumber)) {
            System.out.println("Error: PIN " + newPin + " is already in use by another student.");
            return false;
        }
        
        String anonymousBlockSql = "BEGIN ? := SetPin(?, ?, ?); END;"; 
        try (CallableStatement cstmt = connection.prepareCall(anonymousBlockSql)) {
            cstmt.registerOutParameter(1, Types.INTEGER); 
            cstmt.setString(2, permNumber);
            cstmt.setString(3, oldPin);
            cstmt.setString(4, newPin);
            cstmt.execute();
            int successFlag = cstmt.getInt(1); 
            if (successFlag == 1) {
                System.out.println("PIN changed successfully for student " + permNumber);
                return true;
            } else {
                System.out.println("PIN change failed. This could be due to an incorrect old PIN or the student perm number not being found.");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Database error during PIN change: " + e.getMessage());
            return false;
        }
    }
    
    // List grades from a specific previous quarter
    public void listPreviousQuarterGrades(String permNumber, String quarter, int year) {
        System.out.println("\n=== Grades for Student " + permNumber + " - " + quarter + " " + year + " ===");
        
        String sql = "SELECT co.course_number, c.title, e.grade, e.grade_points, co.quarter, co.year " +
                    "FROM Enrolls_in e " +
                    "JOIN Course_Offering co ON e.enrollment_code = co.enrollment_code " +
                    "JOIN Course c ON co.course_number = c.course_number " +
                    "WHERE e.perm_number = ? AND e.status = 'Past' AND e.grade IS NOT NULL " +
                    "AND co.year = ? AND co.quarter = ? " +
                    "ORDER BY co.course_number";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, permNumber);
            stmt.setInt(2, year);
            stmt.setString(3, quarter);
            ResultSet rs = stmt.executeQuery();
            
            boolean foundGrades = false;
            while (rs.next()) {
                System.out.printf("%-8s %-20s Grade: %-3s (%.1f points) %s %d%n", 
                    rs.getString("course_number"),
                    rs.getString("title"),
                    rs.getString("grade"),
                    rs.getDouble("grade_points"),
                    rs.getString("quarter"),
                    rs.getInt("year"));
                foundGrades = true;
            }
            
            if (!foundGrades) {
                System.out.println("No grades found for " + quarter + " " + year + ".");
            }
        } catch (SQLException e) {
            System.err.println("Error listing grades: " + e.getMessage());
        }
    }
    
    // List all students enrolled in a course
    public void listStudentsInCourse(String enrollmentCode) {
        System.out.println("\n=== Students in Course " + enrollmentCode + " ===");
        
        String courseInfoSql = "SELECT co.course_number, c.title, co.quarter, co.year, co.enroll_limit, " +
                              "(SELECT COUNT(*) FROM Enrolls_in ei WHERE ei.enrollment_code = co.enrollment_code AND ei.status = 'Current') as enrolled_count " +
                              "FROM Course_Offering co " +
                              "JOIN Course c ON co.course_number = c.course_number " +
                              "WHERE co.enrollment_code = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(courseInfoSql)) {
            stmt.setString(1, enrollmentCode);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int enrolledCount = rs.getInt("enrolled_count");
                int enrollLimit = rs.getInt("enroll_limit");
                System.out.printf("Course: %s - %s (%s %d) [%d / %d]%n%n",
                    rs.getString("course_number"),
                    rs.getString("title"), 
                    rs.getString("quarter"),
                    rs.getInt("year"),
                    enrolledCount,
                    enrollLimit);
            } else {
                System.out.println("Course offering " + enrollmentCode + " not found.");
                return;
            }
        } catch (SQLException e) {
            System.err.println("Error getting course info: " + e.getMessage());
            return;
        }
        
        String studentsSql = "SELECT s.perm_number, s.name, s.major_name, s.department_name " +
                           "FROM Student s " +
                           "JOIN Enrolls_in e ON s.perm_number = e.perm_number " +
                           "WHERE e.enrollment_code = ? AND e.status = 'Current' " +
                           "ORDER BY s.name";
        
        try (PreparedStatement stmt = connection.prepareStatement(studentsSql)) {
            stmt.setString(1, enrollmentCode);
            ResultSet rs = stmt.executeQuery();
            boolean foundStudents = false;
            System.out.println("Enrolled Students:");
            System.out.println("Perm#  Name                      Major                Department");
            System.out.println("-----  ------------------------  -------------------  ----------");
            
            while (rs.next()) {
                System.out.printf("%-6s %-25s %-20s %s%n", 
                    rs.getString("perm_number"),
                    rs.getString("name"),
                    rs.getString("major_name"),
                    rs.getString("department_name"));
                foundStudents = true;
            }
            if (!foundStudents) {
                System.out.println("No students currently enrolled.");
            }
        } catch (SQLException e) {
            System.err.println("Error listing students: " + e.getMessage());
        }
    }
    
    // Generate transcript for student (with PIN verification)
    public void generateTranscript(String permNumber, String pin) {
        System.out.println("\n=== TRANSCRIPT for Student " + permNumber + " ===");
        
        if (!verifyPin(permNumber, pin)) {
            System.out.println("Error: Invalid PIN. Cannot generate transcript.");
            return;
        }
        generateTranscriptInternal(permNumber);
    }
    
    // Generate transcript for registrar (no PIN required)
    public void generateTranscript(String permNumber) {
        System.out.println("\n=== [REGISTRAR] TRANSCRIPT for Student " + permNumber + " ===");
        generateTranscriptInternal(permNumber);
    }
    
    // Internal method for transcript generation
    private void generateTranscriptInternal(String permNumber) {
        String studentInfoSql = "SELECT name, address, major_name, department_name " +
                            "FROM Student " +
                            "WHERE perm_number = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(studentInfoSql)) {
            stmt.setString(1, permNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                System.out.println("Student: " + rs.getString("name"));
                System.out.println("Perm #: " + permNumber);
                System.out.println("Address: " + rs.getString("address"));
                System.out.println("Major: " + rs.getString("major_name") + " (" + rs.getString("department_name") + ")");
                System.out.println();
            } else {
                System.out.println("Student " + permNumber + " not found.");
                return;
            }
        } catch (SQLException e) {
            System.err.println("Error getting student info: " + e.getMessage());
            return;
        }
        
        String transcriptSql = "SELECT co.course_number, c.title, e.grade, e.grade_points, co.quarter, co.year " +
                            "FROM Enrolls_in e " +
                            "JOIN Course_Offering co ON e.enrollment_code = co.enrollment_code " +
                            "JOIN Course c ON co.course_number = c.course_number " +
                            "WHERE e.perm_number = ? AND e.status = 'Past' AND e.grade IS NOT NULL " +
                            "ORDER BY co.year, " +
                            "CASE co.quarter " +
                            "  WHEN 'Winter' THEN 1 " +
                            "  WHEN 'Spring' THEN 2 " +
                            "  WHEN 'Fall' THEN 3 " +
                            "END, co.course_number";
        
        try (PreparedStatement stmt = connection.prepareStatement(transcriptSql)) {
            stmt.setString(1, permNumber);
            ResultSet rs = stmt.executeQuery();
            String currentTerm = "";
            double totalGradePointsSum = 0;
            int totalCoursesCount = 0;
            while (rs.next()) {
                String term = rs.getString("quarter") + " " + rs.getInt("year");
                if (!term.equals(currentTerm)) {
                    if (!currentTerm.isEmpty()) {
                        System.out.println();
                    }
                    System.out.println(term + ":");
                    currentTerm = term;
                }
                System.out.printf("  %-8s %-20s %s (%.1f points)%n", 
                    rs.getString("course_number"),
                    rs.getString("title"),
                    rs.getString("grade"),
                    rs.getDouble("grade_points"));
                
                if (rs.getString("grade") != null && !rs.getString("grade").trim().isEmpty()) {
                    totalGradePointsSum += rs.getDouble("grade_points");
                    totalCoursesCount++;
                }
            }
            
            if (totalCoursesCount > 0) {
                double gpa = totalGradePointsSum / totalCoursesCount;
                System.out.println("\n" + repeatString("=", 50));
                System.out.printf("Courses completed: %d%n", totalCoursesCount);
                System.out.printf("Cumulative GPA: %.2f%n", gpa);
            } else {
                System.out.println("\nNo completed courses with grades found for this student.");
            }
            
        } catch (SQLException e) {
            System.err.println("Error generating transcript: " + e.getMessage());
        }
    }

    // Enter grades for all students in a course
    public boolean enterGradesForCourse(String enrollmentCode, Map<String, String> studentGrades) {
    System.out.println("\n=== Entering Grades for Course " + enrollmentCode + " ===");
    
    if (!courseOfferingExists(enrollmentCode)) {
        System.out.println("Error: Course offering " + enrollmentCode + " not found");
        return false;
    }
    
    String sql = "UPDATE Enrolls_in " +
                "SET grade = ?, grade_points = ?, status = 'Past' " +
                "WHERE perm_number = ? AND enrollment_code = ? AND status = 'Current'";
    
    boolean anySuccess = false;
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        for (Map.Entry<String, String> entry : studentGrades.entrySet()) {
            String permNumber = entry.getKey();
            String grade = entry.getValue().toUpperCase();
            
            // Validate grade format first
            if (!isValidGrade(grade)) {
                System.out.println("Invalid grade '" + grade + "' for student " + permNumber + ". Skipping.");
                continue;
            }
            
            double gradePoints = calculateGradePoints(grade);
            
            // Check if student is currently enrolled
            String checkEnrollSql = "SELECT 1 FROM Enrolls_in WHERE perm_number = ? AND enrollment_code = ? AND status = 'Current'";
            boolean isEnrolledCurrently = false;
            try(PreparedStatement checkStmt = connection.prepareStatement(checkEnrollSql)){
                checkStmt.setString(1, permNumber);
                checkStmt.setString(2, enrollmentCode);
                isEnrolledCurrently = checkStmt.executeQuery().next();
            }

            if(!isEnrolledCurrently){
                System.out.println("Student " + permNumber + " is not currently enrolled in " + enrollmentCode + " or already graded. Skipping.");
                continue;
            }
            
            stmt.setString(1, grade);
            stmt.setDouble(2, gradePoints);
            stmt.setString(3, permNumber);
            stmt.setString(4, enrollmentCode);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Grade " + grade + " entered for student " + permNumber);
                anySuccess = true;
            } else {
                System.out.println("Failed to enter grade for student " + permNumber + ".");
            }
        }
        return anySuccess;
    } catch (SQLException e) {
        System.err.println("Error entering grades: " + e.getMessage());
        return false;
    }
    }   
    private boolean isValidGrade(String grade) {
    if (grade == null || grade.trim().isEmpty()) {
        return false;
    }
    String[] validGrades = {"A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D+", "D", "D-", "F", "F-"};
    for (String validGrade : validGrades) {
        if (grade.equals(validGrade)) {
            return true;
        }
    }
    return false;
}

    // Check if student meets graduation requirements
    public void checkGraduationRequirements(String permNumber) {
        System.out.println("\n=== Graduation Requirements Check for Student " + permNumber + " ===");
        
        String majorName = getStudentMajor(permNumber);
        if (majorName == null) {
            System.out.println("Student " + permNumber + " not found.");
            return;
        }
        MajorRequirements requirements = getMajorRequirements(majorName);
        if (requirements.requiredCourses.isEmpty() && requirements.electiveCourses.isEmpty() && requirements.electivesRequired == 0) {
            System.out.println("No requirements defined for major: " + majorName);
            return;
        }
        Set<String> completedCourses = getCompletedCourses(permNumber);
        List<String> missingRequired = new ArrayList<>();
        for (String course : requirements.requiredCourses) {
            if (!completedCourses.contains(course)) {
                missingRequired.add(course);
            }
        }
        int completedElectivesCount = 0;
        for (String course : requirements.electiveCourses) {
            if (completedCourses.contains(course)) {
                completedElectivesCount++;
            }
        }
        if (missingRequired.isEmpty() && completedElectivesCount >= requirements.electivesRequired) {
            System.out.println("YES - Student meets all graduation requirements!");
        } else {
            System.out.println("  Requirements not met:");
            if (!missingRequired.isEmpty()) {
                System.out.println("  Missing required courses: " + String.join(", ", missingRequired));
            }
            if (completedElectivesCount < requirements.electivesRequired) {
                System.out.println("  Need " + (requirements.electivesRequired - completedElectivesCount) + " more elective courses from the elective list.");
                List<String> availableElectivesNotTaken = new ArrayList<>();
                for(String elec : requirements.electiveCourses){
                    if(!completedCourses.contains(elec)){
                        availableElectivesNotTaken.add(elec);
                    }
                }
                if(!availableElectivesNotTaken.isEmpty()){
                     System.out.println("  Eligible electives not yet completed: " + String.join(", ", availableElectivesNotTaken));
                } else {
                     System.out.println("  No more eligible electives listed for the major that haven't been taken.");
                }
            }
        }
    }

    // Generate grade mailer for all students in a quarter
    public void generateGradeMailer(String quarter, int year) {
        System.out.println("\n=== Grade Mailer for " + quarter + " " + year + " ===");
        
        String sql = "SELECT s.perm_number, s.name, s.address, co.course_number, c.title, e.grade " +
                    "FROM Student s " +
                    "JOIN Enrolls_in e ON s.perm_number = e.perm_number " +
                    "JOIN Course_Offering co ON e.enrollment_code = co.enrollment_code " +
                    "JOIN Course c ON co.course_number = c.course_number " +
                    "WHERE co.quarter = ? AND co.year = ? AND e.status = 'Past' AND e.grade IS NOT NULL " +
                    "ORDER BY s.perm_number, co.course_number";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, quarter);
            stmt.setInt(2, year);
            ResultSet rs = stmt.executeQuery();

            String currentStudentPerm = "";
            StringBuilder mailerContent = new StringBuilder();
            boolean foundAnyGrades = false;
            while (rs.next()) {
                foundAnyGrades = true;
                String permNumber = rs.getString("perm_number");
                if (!permNumber.equals(currentStudentPerm)) {
                    if (!currentStudentPerm.isEmpty()) {
                        mailerContent.append("\nSincerely,\nThe Registrar\n");
                        System.out.println(mailerContent.toString());
                        System.out.println(repeatString("=", 50));
                    }
                    
                    currentStudentPerm = permNumber;
                    mailerContent = new StringBuilder();
                    mailerContent.append("TO: ").append(rs.getString("address")).append("\n");
                    mailerContent.append("SUBJECT: Grade Report for ").append(quarter).append(" ").append(year).append("\n\n");
                    mailerContent.append("Dear ").append(rs.getString("name")).append(" (").append(permNumber).append("),\n\n");
                    mailerContent.append("Your grades for ").append(quarter).append(" ").append(year).append(" are as follows:\n\n");
                }
                
                mailerContent.append("  ")
                             .append(rs.getString("course_number")).append(" - ")
                             .append(rs.getString("title")).append(": ")
                             .append(rs.getString("grade")).append("\n");
            }

            if (!currentStudentPerm.isEmpty()) {
                mailerContent.append("\nSincerely,\nThe Registrar\n");
                System.out.println(mailerContent.toString());
                 System.out.println(repeatString("=", 50));
            }
            if (!foundAnyGrades) {
                System.out.println("No grades found to mail for " + quarter + " " + year + ".");
            }
            
        } catch (SQLException e) {
            System.err.println("Error generating grade mailer: " + e.getMessage());
        }
    }
    
    // Helper Methods
    
    // Check if course has space and is offered in current quarter (Spring 2025)
    private boolean courseHasSpaceAndIsCurrent(String enrollmentCode) {
        String sql = "SELECT co.enroll_limit, co.year, co.quarter, co.course_number, " +
                    "(SELECT COUNT(*) FROM Enrolls_in ei WHERE ei.enrollment_code = co.enrollment_code AND ei.status = 'Current') as current_enrollment " +
                    "FROM Course_Offering co " +
                    "WHERE co.enrollment_code = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, enrollmentCode);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int enrollLimit = rs.getInt("enroll_limit");
                int currentEnrollment = rs.getInt("current_enrollment");
                int year = rs.getInt("year");
                String quarter = rs.getString("quarter");
                if (year != 2025 || !quarter.equals("Spring")) {
                    System.out.println("  Error: Enrollment only allowed for current quarter (Spring 2025). This offering is for " + quarter + " " + year + ".");
                    return false;
                }
                
                if (currentEnrollment >= enrollLimit) {
                    System.out.println("  Error: Course " + enrollmentCode + " is full (" + currentEnrollment + "/" + enrollLimit + ").");
                    return false;
                }
                return true;
            } else {
                System.out.println("  Error: Course offering " + enrollmentCode + " not found.");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error checking course space and currency: " + e.getMessage());
            return false;
        }
    }

    // Check if student has completed all prerequisites
    private boolean hasPrerequisites(String permNumber, String enrollmentCodeOffering) {
        String courseNumberToEnrollSql = "SELECT course_number FROM Course_Offering WHERE enrollment_code = ?";
        String courseNumberToEnroll = null;
        
        try (PreparedStatement stmt = connection.prepareStatement(courseNumberToEnrollSql)) {
            stmt.setString(1, enrollmentCodeOffering);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                courseNumberToEnroll = rs.getString("course_number");
            } else {
                System.out.println("  Error: Course offering " + enrollmentCodeOffering + " not found for prerequisite check.");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error getting course number for prerequisite check: " + e.getMessage());
            return false;
        }
        
        String prereqSql = "SELECT prerequisite_course_number FROM Prerequisite WHERE course_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(prereqSql)) {
            stmt.setString(1, courseNumberToEnroll);
            ResultSet rsPrereqs = stmt.executeQuery();
            
            while (rsPrereqs.next()) {
                String prereqCourseNumber = rsPrereqs.getString("prerequisite_course_number");
                
                // Check for passing grade (C or better)
                String completedSql = "SELECT 1 FROM Enrolls_in e " +
                                     "JOIN Course_Offering co ON e.enrollment_code = co.enrollment_code " +
                                     "WHERE e.perm_number = ? AND co.course_number = ? " +
                                     "AND e.status = 'Past' AND e.grade IS NOT NULL " +
                                     "AND e.grade IN ('A+', 'A', 'A-', 'B+', 'B', 'B-', 'C+', 'C')";
                
                try (PreparedStatement checkStmt = connection.prepareStatement(completedSql)) {
                    checkStmt.setString(1, permNumber);
                    checkStmt.setString(2, prereqCourseNumber);
                    if (!checkStmt.executeQuery().next()) {
                        System.out.println("  Error: Missing prerequisite " + prereqCourseNumber + " (must be completed with C or better).");
                        return false;
                    }
                }
            }
            return true;
        } catch (SQLException e) {
            System.err.println("Error checking prerequisites: " + e.getMessage());
            return false;
        }
    }
    
    private boolean studentExists(String permNumber) {
        String sql = "SELECT 1 FROM Student WHERE perm_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, permNumber);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("Error checking if student exists: " + e.getMessage());
            return false;
        }
    }
    
    private boolean isStudentEnrolled(String permNumber, String enrollmentCode) {
        String sql = "SELECT 1 FROM Enrolls_in WHERE perm_number = ? AND enrollment_code = ? AND status = 'Current'";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, permNumber);
            stmt.setString(2, enrollmentCode);
            boolean enrolled = stmt.executeQuery().next();
            if (enrolled) {
                System.out.println("  Error: Student " + permNumber + " is already enrolled in course offering " + enrollmentCode + ".");
            }
            return enrolled;
        } catch (SQLException e) {
            System.err.println("Error checking student enrollment: " + e.getMessage());
            return true;
        }
    }
    
    // Get current course count for student
    private int getCurrentCourseCount(String permNumber) {
        String sql = "SELECT COUNT(*) FROM Enrolls_in WHERE perm_number = ? AND status = 'Current'";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, permNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int count = rs.getInt(1);
                if (count >= 5) {
                    System.out.println("  Student " + permNumber + " is already enrolled in the maximum number of courses (5).");
                }
                return count;
            }
        } catch (SQLException e) {
            System.err.println("Error getting current course count: " + e.getMessage());
        }
        return 0;
    }
    
    private boolean courseOfferingExists(String enrollmentCode) {
        String sql = "SELECT 1 FROM Course_Offering WHERE enrollment_code = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, enrollmentCode);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("Error checking if course offering exists: " + e.getMessage());
            return false;
        }
    }
    
    // Calculate grade points based on letter grade
    private double calculateGradePoints(String grade) {
        if (grade == null) return 0.0;
        return switch (grade.toUpperCase()) {
            case "A+", "A" -> 4.0;
            case "A-" -> 3.7;
            case "B+" -> 3.3;
            case "B" -> 3.0;
            case "B-" -> 2.7;
            case "C+" -> 2.3;
            case "C" -> 2.0;
            case "C-" -> 1.7;
            case "D+" -> 1.3;
            case "D" -> 1.0;
            case "D-" -> 0.7;
            case "F" -> 0.0;
            default -> 0.0;
        };
    }

    private boolean isPinInUse(String pin, String excludePermNumber) {
        String sql = "SELECT 1 FROM Student WHERE PIN_HASH = ? AND perm_number != ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, "hash_of_" + pin);
            stmt.setString(2, excludePermNumber);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("Error checking PIN uniqueness: " + e.getMessage());
            return true;
        }
    }

    private String repeatString(String str, int count) {
        return str.repeat(Math.max(0, count));
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }

    public String getStudentNameForInterface(String permNumber) {
        String sql = "SELECT name FROM Student WHERE perm_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, permNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
             System.err.println("Error fetching student name: " + e.getMessage());
        }
        return "Student #" + permNumber;
    }

    // Generate optimal graduation plan for student
    public void makeGraduationPlan(String permNumber) {
        System.out.println("\n=== GRADUATION PLAN for Student " + permNumber + " ===");
    
        String majorName = getStudentMajor(permNumber);
        if (majorName == null) {
            System.out.println("Student " + permNumber + " not found or has no major.");
            return;
        }
    
        Set<String> completedCourses = getCompletedCourses(permNumber);
        Set<String> currentCourses = getCurrentlyEnrolledCourses(permNumber);
        MajorRequirements requirements = getMajorRequirements(majorName);

        if (requirements.requiredCourses.isEmpty() && requirements.electivesRequired == 0 && requirements.electiveCourses.isEmpty()) {
            System.out.println("No specific course requirements found for major: " + majorName + ". Cannot generate plan.");
            return;
        }

        List<String> initialRequiredStillNeeded = new ArrayList<>();
        for (String course : requirements.requiredCourses) {
            if (!completedCourses.contains(course) && !currentCourses.contains(course)) {
                initialRequiredStillNeeded.add(course);
            }
        }
    
        int completedElectiveUnitsCount = 0;
        for (String course : requirements.electiveCourses) {
            if (completedCourses.contains(course) || currentCourses.contains(course)) {
                completedElectiveUnitsCount++;
            }
        }
        int electivesStillToTakeCount = Math.max(0, requirements.electivesRequired - completedElectiveUnitsCount);
    
        List<String> availableElectiveCoursesNotTaken = new ArrayList<>();
        for (String course : requirements.electiveCourses) {
            if (!completedCourses.contains(course) && !currentCourses.contains(course)) {
                availableElectiveCoursesNotTaken.add(course);
            }
        }
    
        System.out.println("Major: " + majorName);
        System.out.println("Completed courses (passed with C or better): " + completedCourses.size());
        System.out.println("Currently enrolled (Spring 2025): " + currentCourses.size());
        System.out.println("Remaining required courses for major: " + initialRequiredStillNeeded.size());
        System.out.println("Remaining electives needed for major: " + electivesStillToTakeCount);
        System.out.println();
    
        if (initialRequiredStillNeeded.isEmpty() && electivesStillToTakeCount <= 0) {
            System.out.println("CONGRATULATIONS! You have completed all graduation requirements!");
            return;
        }
    
        List<Quarter> plan = generateOptimalPlan(
            new ArrayList<>(initialRequiredStillNeeded),
            new ArrayList<>(availableElectiveCoursesNotTaken),
            electivesStillToTakeCount,
            new HashSet<>(completedCourses),
            new HashSet<>(currentCourses)
        );
    
        if (plan.isEmpty() && (!initialRequiredStillNeeded.isEmpty() || electivesStillToTakeCount > 0)) {
            System.out.println("Unable to generate a full graduation plan with the current course offerings and prerequisites. Please consult with an advisor.");
            return;
        }
        if (plan.isEmpty() && initialRequiredStillNeeded.isEmpty() && electivesStillToTakeCount <= 0){
            System.out.println("All requirements appear to be met or in progress for the current quarter.");
            return;
        }

    
        System.out.println("EARLIEST GRADUATION PLAN:");
        if (!plan.isEmpty()) {
            System.out.println("Estimated graduation: " + plan.get(plan.size() - 1).getDisplayName());
        } else {
             System.out.println("Review current and past courses. All requirements might be met upon completion of current courses.");
        }
        System.out.println();
    
        for (Quarter quarter : plan) {
            System.out.println(quarter.getDisplayName() + ":");
            if (quarter.courses.isEmpty()){
                System.out.println("  No courses scheduled for this quarter (might indicate planning issue or requirements met).");
            }
            for (String course : quarter.courses) {
                String type = initialRequiredStillNeeded.contains(course) ? "(Required)" : "(Elective)";
                System.out.println("  • " + course + " " + type);
            }
            System.out.println();
        }
    }

    private String getStudentMajor(String permNumber) {
        String sql = "SELECT major_name FROM Student WHERE perm_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, permNumber);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("major_name") : null;
        } catch (SQLException e) {
            System.err.println("Error getting student major: " + e.getMessage());
            return null;
        }
    }

    private Set<String> getCompletedCourses(String permNumber) {
        Set<String> completed = new HashSet<>();
        String sql = "SELECT co.course_number FROM Enrolls_in e " +
                     "JOIN Course_Offering co ON e.enrollment_code = co.enrollment_code " +
                     "WHERE e.perm_number = ? AND e.status = 'Past' AND e.grade IS NOT NULL " +
                     "AND e.grade IN ('A+', 'A', 'A-', 'B+', 'B', 'B-', 'C+', 'C')";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, permNumber);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                completed.add(rs.getString("course_number"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting completed courses: " + e.getMessage());
        }
        return completed;
    }

    private Set<String> getCurrentlyEnrolledCourses(String permNumber) {
        Set<String> current = new HashSet<>();
        String sql = "SELECT co.course_number FROM Enrolls_in e " +
                     "JOIN Course_Offering co ON e.enrollment_code = co.enrollment_code " +
                     "WHERE e.perm_number = ? AND e.status = 'Current'";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, permNumber);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                current.add(rs.getString("course_number"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting current courses: " + e.getMessage());
        }
        return current;
    }

    private MajorRequirements getMajorRequirements(String majorName) {
        MajorRequirements requirements = new MajorRequirements();
        String sql = "SELECT m.elective_number, pom.course_number, pom.required " +
                     "FROM Major m LEFT JOIN Part_of_Major pom ON m.name = pom.major_name " +
                     "WHERE m.name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, majorName);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                requirements.electivesRequired = rs.getInt("elective_number");
                String courseNumber = rs.getString("course_number");
                if (courseNumber != null) {
                    if (rs.getInt("required") == 1) {
                        requirements.requiredCourses.add(courseNumber);
                    } else {
                        requirements.electiveCourses.add(courseNumber);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting major requirements for " + majorName + ": " + e.getMessage());
        }
        return requirements;
    }

    private List<Quarter> generateOptimalPlan(
            List<String> toScheduleRequired,
            List<String> availableElectives,
            int electivesStillNeededCount,
            Set<String> coursesAlreadyCompleted,
            Set<String> coursesCurrentlyEnrolled) {

        List<Quarter> plan = new ArrayList<>();

        Set<String> coursesConsideredTakenOrScheduled = new HashSet<>(coursesAlreadyCompleted);
        coursesConsideredTakenOrScheduled.addAll(coursesCurrentlyEnrolled);

        int currentYear = 2025;
        String currentQuarter = "Fall"; // Planning starts from Fall 2025

        int safetyBreakMaxQuarters = 20;

        while ((!toScheduleRequired.isEmpty() || electivesStillNeededCount > 0) && plan.size() < safetyBreakMaxQuarters) {
            Quarter quarterPlan = new Quarter(currentYear, currentQuarter);
            
            // Schedule required courses first
            Iterator<String> reqIter = toScheduleRequired.iterator();
            while (reqIter.hasNext() && quarterPlan.courses.size() < 5) {
                String course = reqIter.next();
                if (isCourseOfferedInQuarterType(course, currentQuarter) &&
                    canTakeCourse(course, coursesConsideredTakenOrScheduled)) {
                    quarterPlan.courses.add(course);
                    reqIter.remove();
                }
            }

            // Schedule elective courses
            Iterator<String> elecIter = availableElectives.iterator();
            while (elecIter.hasNext() && quarterPlan.courses.size() < 5 && electivesStillNeededCount > 0) {
                String course = elecIter.next();
                if (!quarterPlan.courses.contains(course) && 
                    isCourseOfferedInQuarterType(course, currentQuarter) &&
                    canTakeCourse(course, coursesConsideredTakenOrScheduled)) {
                    quarterPlan.courses.add(course);
                    elecIter.remove();
                    electivesStillNeededCount--;
                }
            }

            if (!quarterPlan.courses.isEmpty()) {
                plan.add(quarterPlan);
                coursesConsideredTakenOrScheduled.addAll(quarterPlan.courses);
            } else {
                if (!toScheduleRequired.isEmpty() || electivesStillNeededCount > 0) {
                     System.out.println(" Warning: Unable to find any valid courses to schedule for " +
                                     currentQuarter + " " + currentYear +
                                     ". Prerequisite issues or course availability constraints might exist. Please consult an advisor.");
                }
                break;
            }

            switch (currentQuarter) {
                case "Fall":
                    currentQuarter = "Winter";
                    currentYear++;
                    break;
                case "Winter":
                    currentQuarter = "Spring";
                    break;
                case "Spring":
                    currentQuarter = "Fall";
                    break;
            }
        }
         if (plan.size() >= safetyBreakMaxQuarters && (!toScheduleRequired.isEmpty() || electivesStillNeededCount > 0)) {
            System.out.println(" Warning: Plan exceeds reasonable timeframe (" + safetyBreakMaxQuarters/4.0 + " years). Please consult an advisor.");
        }

        return plan;
    }
    
    private boolean isCourseOfferedInQuarterType(String courseNumber, String quarterType) {
        String sql = "SELECT 1 FROM Course_Offering WHERE course_number = ? AND quarter = ? FETCH FIRST 1 ROWS ONLY";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, courseNumber);
            stmt.setString(2, quarterType);
            ResultSet rs = stmt.executeQuery();
            return rs.next(); 
        } catch (SQLException e) {
            System.err.println("Error checking course offering by quarter type for " + courseNumber + " in " + quarterType + ": " + e.getMessage());
            return false;
        }
    }
    
    private boolean canTakeCourse(String courseNumber, Set<String> coursesCompletedBeforeThisQuarter) {
        if (!courseExists(courseNumber)) {
            return false;
        }
        List<String> prerequisites = getPrerequisites(courseNumber);
        for (String prereq : prerequisites) {
            if (!coursesCompletedBeforeThisQuarter.contains(prereq)) {
                return false; 
            }
        }
        return true; 
    }

    private List<String> getPrerequisites(String courseNumber) {
        List<String> prerequisites = new ArrayList<>();
        String sql = "SELECT prerequisite_course_number FROM Prerequisite WHERE course_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, courseNumber);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                prerequisites.add(rs.getString("prerequisite_course_number"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting prerequisites for " + courseNumber + ": " + e.getMessage());
        }
        return prerequisites;
    }

    private boolean courseExists(String courseNumber) {
        String sql = "SELECT 1 FROM Course WHERE course_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, courseNumber);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("Error checking if course " + courseNumber + " exists: " + e.getMessage());
            return false;
        }
    }

    // Helper classes for graduation plan
    private static class MajorRequirements {
        List<String> requiredCourses = new ArrayList<>();
        List<String> electiveCourses = new ArrayList<>();
        int electivesRequired = 0;
    }

    private static class Quarter {
        int year;
        String quarter;
        List<String> courses = new ArrayList<>();
        
        Quarter(int year, String quarter) {
            this.year = year;
            this.quarter = quarter;
        }
        
        String getDisplayName() {
            return quarter + " " + year;
        }
    }
    
    public static void main(String[] args) {
        StudentTransactions st = null;
        Interfaces ui = null;
        
        try {
            st = new StudentTransactions();
            ui = new Interfaces(st);
            ui.start();
        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
            System.err.println("Please check your database configuration and try again.");
        } finally {
            if (ui != null) {
                ui.close();
            }
            if (st != null) {
                st.close();
            }
        }
    }
}