# UCSB GOLD Simulator

A command-line based simulation of UCSB's enrollment website GOLD, built using Java, Oracle SQL, and JDBC. This application models key student and registrar operations for course enrollment, grade management, and academic planning.

Upon starting the application the user is shown a menu with three options:
- `Student Interface (GOLD)`
- `Registrar Interface`
- `Exit`

## Student Interface
To access the student interface the user must input a valid perm number and PIN combo, then they are able to:
- Add or drop a course
- View current or past courses and grades
- Change their PIN
- Check their graduation requirements within their major
- Plan courses to graduate at the earliest time

## Registrar Interface
The Registrar interface does not use the PIN at all but some actions require entering a student's perm number. The possible actions are:
- Add or drop student from a course
- View student's course history
- Enter grades (individually or via a file)
- Generate transcripts
- Generate grade mailers for a quarter

## Running the application
To actually run the application you must configure the config.properties file with your own oracle DB connection and wallet:
- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`


**This project is for educational purposes only and is not affliated with the official UCSB Gold System.**
