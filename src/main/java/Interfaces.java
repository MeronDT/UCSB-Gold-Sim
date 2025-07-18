import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Interfaces {
    private StudentTransactions st;
    private Scanner scanner;
    
    public Interfaces(StudentTransactions studentTransactions) {
        this.st = studentTransactions;
        this.scanner = new Scanner(System.in);
    }
    
    public void start() {
        while (true) {
            System.out.println("\n" + repeatString("=", 60));
            System.out.println("    UNIVERSITY COURSE MANAGEMENT SYSTEM");
            System.out.println(repeatString("=", 60));
            System.out.println("1. GOLD Interface");
            System.out.println("2. Registrar Office Interface");
            System.out.println("3. Exit");
            System.out.println(repeatString("=", 60));
            System.out.print("Select interface: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    goldInterface();
                    break;
                case "2":
                    registrarInterface();
                    break;
                case "3":
                    System.out.println("Thank you for using the University Course system.");
                    return;
                default:
                    System.out.println("Invalid choice. Please select 1, 2, or 3.");
            }
        }
    }
    
    // Student interface - requires PIN authentication
    private void goldInterface() {
        System.out.println("\nWelcome to GOLD");
        
        System.out.print("Enter your Perm Number: ");
        String permNumber = scanner.nextLine().trim();
        
        System.out.print("Enter your PIN: ");
        String pin = scanner.nextLine().trim();
        
        // Verify student credentials
        if (!st.verifyPin(permNumber, pin)) {
            System.out.println("Invalid credentials. Access denied.");
            return;
        }
        
        String studentName = getStudentName(permNumber);
        System.out.println("Welcome, " + studentName + "!");
        
        while (true) {
            System.out.println("\n" + repeatString("-", 50));
            System.out.println("           GOLD - STUDENT MENU");
            System.out.println(repeatString("-", 50));
            System.out.println("1. Add a Course");
            System.out.println("2. Drop a Course");
            System.out.println("3. List Courses Enrolled in Current Quarter");
            System.out.println("4. List Grades of a Previous Quarter");
            System.out.println("5. Requirements Check");
            System.out.println("6. Make a Plan");
            System.out.println("7. Change PIN");
            System.out.println("8. Return to Main Menu");
            System.out.println(repeatString("-", 50));
            System.out.print("Choose an option: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    studentAddCourse(permNumber);
                    break;
                case "2":
                    studentDropCourse(permNumber, pin);
                    break;
                case "3":
                    st.listCurrentCourses(permNumber);
                    break;
                case "4":
                    studentListPreviousGrades(permNumber);
                    break;
                case "5":
                    st.checkGraduationRequirements(permNumber);
                    break;
                case "6":
                    st.makeGraduationPlan(permNumber);
                    break;
                case "7":
                    String updatedPin = studentChangePin(permNumber);
                    if(updatedPin != null){
                        pin = updatedPin;
                    }
                    break;
                case "8":
                    return;
                default:
                    System.out.println("Invalid choice. Please select 1-8.");
            }
            
            System.out.println("\nPress Enter to continue...");
            scanner.nextLine();
        }
    }
    
    // Registrar interface - no PIN required, admin access
    private void registrarInterface() {
        System.out.println("\nWelcome to Registrar Office");
        System.out.println("Access granted to Registrar functions.");
        
        while (true) {
            System.out.println("\n" + repeatString("-", 50));
            System.out.println("       REGISTRAR OFFICE");
            System.out.println(repeatString("-", 50));
            System.out.println("1. Add a Student to a Course");
            System.out.println("2. Drop a Student from a Course");
            System.out.println("3. List Courses Taken by a Student");
            System.out.println("4. List Previous Quarter Grades for a Student");
            System.out.println("5. Generate Class List for a Course");
            System.out.println("6. Enter Grades for a Course (file or manual)");
            System.out.println("7. Request Transcript for a Student");
            System.out.println("8. Generate Grade Mailer for All Students");
            System.out.println("9. Return to Main Menu");
            System.out.println(repeatString("-", 50));
            System.out.print("Choose an option: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    adminAddStudentToCourse();
                    break;
                case "2":
                    adminDropStudentFromCourse();
                    break;
                case "3":
                    adminListStudentCourses();
                    break;
                case "4":
                    adminListStudentPreviousGrades();
                    break;
                case "5":
                    adminGenerateClassList();
                    break;
                case "6":
                    adminEnterGradesFromFile();
                    break;
                case "7":
                    adminRequestTranscript();
                    break;
                case "8":
                    adminGenerateGradeMailer();
                    break;
                case "9":
                    return;
                default:
                    System.out.println("Invalid choice. Please select 1-9.");
            }
            
            System.out.println("\nPress Enter to continue...");
            scanner.nextLine();
        }
    }
    
    // Student Interface Methods
    
    private void studentAddCourse(String permNumber) {
        System.out.println("\nADD A COURSE");
        System.out.println("Ask professor for enrollment code.");
        
        System.out.print("Enter enrollment code: ");
        String enrollmentCode = scanner.nextLine().trim();
        
        if (enrollmentCode.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        boolean success = st.addCourse(permNumber, enrollmentCode);
        if (success) {
            System.out.println("Course added successfully.");
        }
    }
    
    private void studentDropCourse(String permNumber, String pin) {
        System.out.println("\nDROP A COURSE");
        
        System.out.println("\nYour current courses:");
        st.listCurrentCourses(permNumber);
        
        System.out.print("\nEnter enrollment code of course to drop: ");
        String enrollmentCode = scanner.nextLine().trim();
        
        if (enrollmentCode.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        boolean success = st.dropCourse(permNumber, pin, enrollmentCode);
        if (success) {
            System.out.println("Course dropped successfully.");
        } else {
            System.out.println("Failed to drop course.");
        }
    }
    
    private void studentListPreviousGrades(String permNumber) {
        System.out.println("\nLIST GRADES OF A PREVIOUS QUARTER");
        
        System.out.print("Enter quarter (Winter/Spring/Fall): ");
        String quarter = scanner.nextLine().trim();
        
        if (quarter.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        // Validate quarter input
        if (!quarter.equalsIgnoreCase("Winter") && !quarter.equalsIgnoreCase("Spring") && !quarter.equalsIgnoreCase("Fall")) {
            System.out.println("Invalid quarter. Please enter Winter, Spring, or Fall.");
            return;
        }
        
        System.out.print("Enter year (e.g., 2024, 2025): ");
        String yearStr = scanner.nextLine().trim();
        
        if (yearStr.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        try {
            int year = Integer.parseInt(yearStr);
            quarter = quarter.substring(0, 1).toUpperCase() + quarter.substring(1).toLowerCase();
            st.listPreviousQuarterGrades(permNumber, quarter, year);
        } catch (NumberFormatException e) {
            System.out.println("Invalid year format. Please enter a valid year (e.g., 2024).");
        }
    }
    
    // Returns new PIN if successful, null otherwise
    private String studentChangePin(String permNumber) {
        System.out.println("\nCHANGE PIN");
        
        System.out.print("Enter your current PIN: ");
        String oldPin = scanner.nextLine().trim();
        
        System.out.print("Enter your new PIN: ");
        String newPin = scanner.nextLine().trim();
        
        if (oldPin.isEmpty() || newPin.isEmpty()) {
            System.out.println("Operation cancelled.");
            return null;
        }
        
        boolean success = st.changePin(permNumber, oldPin, newPin);
        if (success) {
            System.out.println("PIN changed successfully.");
            return newPin;
        } else {
            System.out.println("Failed to change PIN.");
            return null;
        }
    }
    
    // Registrar interface methods 
    
    private void adminAddStudentToCourse() {
        System.out.println("\nADD A STUDENT TO A COURSE");
        
        System.out.print("Enter student's perm number: ");
        String permNumber = scanner.nextLine().trim();
        
        System.out.print("Enter enrollment code: ");
        String enrollmentCode = scanner.nextLine().trim();
        
        if (permNumber.isEmpty() || enrollmentCode.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        boolean success = st.addCourse(permNumber, enrollmentCode);
        if (success) {
            System.out.println("Student added to course successfully.");
        } else {
            System.out.println("Failed to add student to course.");
        }
    }
    
    private void adminDropStudentFromCourse() {
        System.out.println("\nDROP A STUDENT FROM A COURSE");
        
        System.out.print("Enter student's perm number: ");
        String permNumber = scanner.nextLine().trim();
        
        System.out.print("Enter enrollment code: ");
        String enrollmentCode = scanner.nextLine().trim();
        
        if (permNumber.isEmpty() || enrollmentCode.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        boolean success = st.dropCourse(permNumber, enrollmentCode);
        if (success) {
            System.out.println("Student dropped from course successfully.");
        } else {
            System.out.println("Failed to drop student from course.");
        }
    }
    
    private void adminListStudentCourses() {
        System.out.println("\nLIST COURSES TAKEN BY A STUDENT");
        
        System.out.print("Enter student's perm number: ");
        String permNumber = scanner.nextLine().trim();
        
        if (!permNumber.isEmpty()) {
            st.listCurrentCourses(permNumber);
        } else {
            System.out.println("Operation cancelled.");
        }
    }
    
    private void adminListStudentPreviousGrades() {
        System.out.println("\nLIST PREVIOUS QUARTER GRADES FOR A STUDENT");
        
        System.out.print("Enter student's perm number: ");
        String permNumber = scanner.nextLine().trim();
        
        if (permNumber.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        System.out.print("Enter quarter (Winter/Spring/Fall): ");
        String quarter = scanner.nextLine().trim();
        
        if (quarter.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        // Validate quarter input
        if (!quarter.equalsIgnoreCase("Winter") && !quarter.equalsIgnoreCase("Spring") && !quarter.equalsIgnoreCase("Fall")) {
            System.out.println("Invalid quarter. Please enter Winter, Spring, or Fall.");
            return;
        }
        
        System.out.print("Enter year (e.g., 2024, 2025): ");
        String yearStr = scanner.nextLine().trim();
        
        if (yearStr.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        try {
            int year = Integer.parseInt(yearStr);
            quarter = quarter.substring(0, 1).toUpperCase() + quarter.substring(1).toLowerCase();
            st.listPreviousQuarterGrades(permNumber, quarter, year);
        } catch (NumberFormatException e) {
            System.out.println("Invalid year format. Please enter a valid year (e.g., 2024).");
        }
    }
    
    private void adminGenerateClassList() {
        System.out.println("\nGENERATE CLASS LIST FOR A COURSE");
        
        System.out.print("Enter enrollment code: ");
        String enrollmentCode = scanner.nextLine().trim();
        
        if (!enrollmentCode.isEmpty()) {
            st.listStudentsInCourse(enrollmentCode);
        } else {
            System.out.println("Operation cancelled.");
        }
    }
    
    // Handles both file and manual grade entry
    private void adminEnterGradesFromFile() {
        System.out.println("\nENTER GRADES FOR A COURSE (from file)");
        
        System.out.print("Enter enrollment code: ");
        String enrollmentCode = scanner.nextLine().trim();
        
        if (enrollmentCode.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        System.out.println("\nStudents in this course:");
        st.listStudentsInCourse(enrollmentCode);
        
        System.out.print("\nEnter filename (or 'manual' for manual entry): ");
        String filename = scanner.nextLine().trim();
        
        if (filename.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        Map<String, String> grades = new HashMap<>();
        
        if (filename.equalsIgnoreCase("manual")) {
            System.out.println("\nEnter grades manually (format: permNumber grade, or 'done' to finish):");
            System.out.println("Valid grades: A+, A, A-, B+, B, B-, C+, C, C-, D+, D, D-, F, F-");
            
            while (true) {
                System.out.print("Perm Number and Grade (e.g., '12345 A'): ");
                String input = scanner.nextLine().trim();
                
                if (input.equalsIgnoreCase("done")) break;
                
                String[] parts = input.split("\\s+");
                if (parts.length == 2) {
                    grades.put(parts[0], parts[1]);
                } else {
                    System.out.println("Invalid format. Use: permNumber grade");
                }
            }
        } else {
            // Read grades from file
            try {
                File file = new File(filename);
                Scanner fileScanner = new Scanner(file);
                
                System.out.println("Reading grades from file: " + filename);
                
                while (fileScanner.hasNextLine()) {
                    String line = fileScanner.nextLine().trim();
                    if (!line.isEmpty()) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            grades.put(parts[0], parts[1]);
                            System.out.println("Loaded grade " + parts[1] + " for student " + parts[0]);
                        }
                    }
                }
                fileScanner.close();
                
            } catch (FileNotFoundException e) {
                System.out.println("File not found: " + filename);
                System.out.println("Make sure the file exists in the current directory.");
                return;
            }
        }
        
        if (!grades.isEmpty()) {
            boolean success = st.enterGradesForCourse(enrollmentCode, grades);
            if (success) {
                System.out.println("Grades entered successfully.");
            }
        } else {
            System.out.println("No grades entered.");
        }
    }
    
    private void adminRequestTranscript() {
        System.out.println("\nREQUEST TRANSCRIPT FOR A STUDENT");
        
        System.out.print("Enter student's perm number: ");
        String permNumber = scanner.nextLine().trim();
        
        if (!permNumber.isEmpty()) {
            st.generateTranscript(permNumber);
        } else {
            System.out.println("Operation cancelled.");
        }
    }
    
    private void adminGenerateGradeMailer() {
        System.out.println("\nGENERATE GRADE MAILER FOR ALL STUDENTS");
        
        System.out.print("Enter quarter (Winter/Spring/Fall): ");
        String quarter = scanner.nextLine().trim();
        
        System.out.print("Enter year: ");
        String yearStr = scanner.nextLine().trim();
        
        if (quarter.isEmpty() || yearStr.isEmpty()) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        try {
            int year = Integer.parseInt(yearStr);
            st.generateGradeMailer(quarter, year);
        } catch (NumberFormatException e) {
            System.out.println("Invalid year format.");
        }
    }
    
    // Utility Methods
    
    private String getStudentName(String permNumber) {
        return st.getStudentNameForInterface(permNumber);
    }
    
    private String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    public void close() {
        if (scanner != null) {
            scanner.close();
        }
    }
}