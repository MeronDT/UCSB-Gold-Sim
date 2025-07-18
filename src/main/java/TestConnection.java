import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.pool.OracleDataSource;

public class TestConnection {
    public static void main(String args[]) throws SQLException {
        Properties configProps = new Properties();

        //from config file
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            configProps.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }

        String DB_URL = configProps.getProperty("DB_URL");
        String DB_USER = configProps.getProperty("DB_USER");
        String DB_PASSWORD = configProps.getProperty("DB_PASSWORD");

        Properties info = new Properties();
        System.out.println("Initializing connection properties...");
        info.put(OracleConnection.CONNECTION_PROPERTY_USER_NAME, DB_USER);
        info.put(OracleConnection.CONNECTION_PROPERTY_PASSWORD, DB_PASSWORD);
        info.put(OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH, "20");

        System.out.println("Creating OracleDataSource...");
        OracleDataSource ods = new OracleDataSource();
        System.out.println("Setting connection properties...");
        ods.setURL(DB_URL);
        ods.setConnectionProperties(info);

        try (OracleConnection connection = (OracleConnection) ods.getConnection()) {
            System.out.println("Connection established!");

            DatabaseMetaData dbmd = connection.getMetaData();
            System.out.println("Driver Name: " + dbmd.getDriverName());
            System.out.println("Driver Version: " + dbmd.getDriverVersion());
            System.out.println("Default Row Prefetch Value: " + connection.getDefaultRowPrefetch());
            System.out.println("Database username: " + connection.getUserName());
            System.out.println();

            insertTA(connection);
            printInstructors(connection);

        } catch (Exception e) {
            System.out.println("CONNECTION ERROR:");
            System.out.println(e);
        }
    }

    // Inserts another TA into the Instructors table.
    public static void insertTA(Connection connection) throws SQLException {
        System.out.println("Preparing to insert TA into Instructors table...");
        // Statement and ResultSet are AutoCloseable and closed automatically. 
        try (Statement statement = connection.createStatement()) {
            try (
                ResultSet resultSet = statement.executeQuery(
                    "INSERT INTO INSTRUCTORS VALUES (5, 'Tyler Yang', 'TA')"
                )
            ) {}
        } catch (Exception e) {
            System.out.println("ERROR: insertion failed.");
            System.out.println(e);
        }
    }

    // Displays data from Instructors table.
    public static void printInstructors(Connection connection) throws SQLException {
        // Statement and ResultSet are AutoCloseable and closed automatically. 
        try (Statement statement = connection.createStatement()) {
            try (
                ResultSet resultSet = statement.executeQuery(
                    "SELECT * FROM INSTRUCTORS"
                )
            ) {
                System.out.println("INSTRUCTORS:");
                System.out.println("I_ID\tI_NAME\t\tI_ROLE");
                while (resultSet.next()) {
                    System.out.println(
                        resultSet.getString("I_ID") + "\t"
                        + resultSet.getString("I_NAME") + "\t"
                        + resultSet.getString("I_ROLE")
                    );
                }
            }
        } catch (Exception e) {
            System.out.println("ERROR: selection failed.");
            System.out.println(e);
        }
    }
}
