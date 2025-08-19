package org.stefanapetri.licenta.model;

import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class DatabaseManager {

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final String dbName;

    public DatabaseManager() {
        // Load database properties from the file
        try (InputStream input = DatabaseManager.class.getResourceAsStream("/org/stefanapetri/licenta/database.properties")) {
            if (input == null) {
                System.err.println("FATAL: Could not find database.properties file.");
                throw new RuntimeException("database.properties not found on the classpath");
            }
            Properties props = new Properties();
            props.load(input);

            this.dbUrl = props.getProperty("db.url");
            this.dbUser = props.getProperty("db.user");
            this.dbPassword = props.getProperty("db.password");

            // Extract database name from URL for creation logic
            this.dbName = this.dbUrl.substring(this.dbUrl.lastIndexOf("/") + 1);

            System.out.println("Database properties loaded successfully.");

            // NEW: Ensure the database and tables exist
            initializeDatabase();

        } catch (Exception e) {
            System.err.println("FATAL: Error during DatabaseManager initialization: " + e.getMessage());
            throw new RuntimeException("Could not initialize DatabaseManager.", e);
        }
    }

    private void initializeDatabase() throws SQLException {
        // Step 1: Check if the database exists and create it if it doesn't.
        try (Connection conn = connect()) {
            System.out.println("Successfully connected to existing database '" + dbName + "'.");
        } catch (SQLException e) {
            // SQL state "3D000" means the database does not exist in PostgreSQL.
            if (e.getSQLState().equals("3D000")) {
                System.out.println("Database '" + dbName + "' not found. Attempting to create it.");

                // Connect to the default 'postgres' database to create our new one.
                String maintenanceUrl = dbUrl.substring(0, dbUrl.lastIndexOf("/") + 1) + "postgres";
                try (Connection maintenanceConn = DriverManager.getConnection(maintenanceUrl, dbUser, dbPassword);
                     Statement stmt = maintenanceConn.createStatement()) {
                    stmt.executeUpdate("CREATE DATABASE " + dbName);
                    System.out.println("Database '" + dbName + "' created successfully.");
                }
            } else {
                // Another SQL error occurred (e.g., authentication failed), so we re-throw it.
                throw e;
            }
        }

        // Step 2: Now that the database exists, connect to it and create tables if they don't exist.
        try (Connection conn = connect()) {
            createTablesIfNotExists(conn);
        }
    }

    private void createTablesIfNotExists(Connection conn) throws SQLException {
        // SQL for creating the tracked_applications table
        String createAppTableSql = "CREATE TABLE IF NOT EXISTS tracked_applications (" +
                "app_id SERIAL PRIMARY KEY, " +
                "app_name VARCHAR(255) NOT NULL, " +
                "executable_path TEXT NOT NULL UNIQUE, " +
                "last_closed_at TIMESTAMP" +
                ");";

        // SQL for creating the memos table with a foreign key and ON DELETE CASCADE
        String createMemosTableSql = "CREATE TABLE IF NOT EXISTS memos (" +
                "memo_id SERIAL PRIMARY KEY, " +
                "app_id INTEGER NOT NULL, " +
                "transcription_text TEXT, " +
                "audio_file_path TEXT, " +
                "created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, " +
                "CONSTRAINT fk_app FOREIGN KEY(app_id) " +
                "REFERENCES tracked_applications(app_id) ON DELETE CASCADE" +
                ");";

        try (Statement stmt = conn.createStatement()) {
            System.out.println("Ensuring 'tracked_applications' table exists...");
            stmt.execute(createAppTableSql);
            System.out.println("Ensuring 'memos' table exists...");
            stmt.execute(createMemosTableSql);
            System.out.println("Database tables are ready.");
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    public List<TrackedApplication> getAllTrackedApplications() {
        List<TrackedApplication> apps = new ArrayList<>();
        String sql = "SELECT app_id, app_name, executable_path FROM tracked_applications ORDER BY app_name";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                apps.add(new TrackedApplication(
                        rs.getInt("app_id"),
                        rs.getString("app_name"),
                        rs.getString("executable_path")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching tracked applications: " + e.getMessage());
        }
        return apps;
    }

    public Optional<TrackedApplication> addTrackedApplication(String appName, String executablePath) {
        String sql = "INSERT INTO tracked_applications(app_name, executable_path) VALUES(?, ?) RETURNING app_id";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, appName);
            pstmt.setString(2, executablePath);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int newId = rs.getInt(1);
                System.out.println("Successfully added application: " + appName);
                return Optional.of(new TrackedApplication(newId, appName, executablePath));
            }
        } catch (SQLException e) {
            System.err.println("Error adding application: " + e.getMessage());
        }
        return Optional.empty();
    }

    // --- SIMPLIFIED due to ON DELETE CASCADE ---
    // The database now automatically handles deleting associated memos.
    public void removeTrackedApplication(int appId) {
        String deleteAppSql = "DELETE FROM tracked_applications WHERE app_id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(deleteAppSql)) {

            System.out.println("Deleting application ID: " + appId + ". Associated memos will be deleted by cascade.");
            pstmt.setInt(1, appId);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Application deleted successfully.");
            } else {
                System.out.println("No application found with ID: " + appId);
            }

        } catch (SQLException e) {
            System.err.println("Error removing application: " + e.getMessage());
        }
    }


    public void saveMemo(int appId, String transcription, String audioFilePath) {
        String sql = "INSERT INTO memos(app_id, transcription_text, audio_file_path) VALUES(?, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, appId);
            pstmt.setString(2, transcription);
            pstmt.setString(3, audioFilePath);
            pstmt.executeUpdate();
            System.out.println("Memo saved for app ID: " + appId);
        } catch (SQLException e) {
            System.err.println("Error saving memo: " + e.getMessage());
        }
    }

    public void updateMemoText(int memoId, String newText) {
        String sql = "UPDATE memos SET transcription_text = ? WHERE memo_id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newText);
            pstmt.setInt(2, memoId);
            pstmt.executeUpdate();
            System.out.println("Updated text for memo ID: " + memoId);

        } catch (SQLException e) {
            System.err.println("Error updating memo text: " + e.getMessage());
        }
    }
    public Optional<MemoViewItem> getLatestMemoForApp(int appId) {
        String sql = "SELECT m.*, ta.app_name FROM memos m " +
                "JOIN tracked_applications ta ON m.app_id = ta.app_id " +
                "WHERE m.app_id = ? ORDER BY m.created_at DESC LIMIT 1";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, appId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                MemoViewItem memo = new MemoViewItem(
                        rs.getInt("memo_id"),
                        rs.getInt("app_id"),
                        rs.getString("app_name"),
                        rs.getString("transcription_text"),
                        rs.getString("audio_file_path"),
                        rs.getTimestamp("created_at")
                );
                return Optional.of(memo);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching latest memo: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<MemoViewItem> getAllMemosForApp(int appId) {
        List<MemoViewItem> memos = new ArrayList<>();
        String sql = "SELECT m.*, ta.app_name FROM memos m " +
                "JOIN tracked_applications ta ON m.app_id = ta.app_id " +
                "WHERE m.app_id = ? ORDER BY m.created_at DESC";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, appId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                memos.add(new MemoViewItem(
                        rs.getInt("memo_id"),
                        rs.getInt("app_id"),
                        rs.getString("app_name"),
                        rs.getString("transcription_text"),
                        rs.getString("audio_file_path"),
                        rs.getTimestamp("created_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all memos for app: " + e.getMessage());
        }
        return memos;
    }

    public void deleteMemo(int memoId) {
        String sql = "DELETE FROM memos WHERE memo_id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, memoId);
            pstmt.executeUpdate();
            System.out.println("Memo with ID " + memoId + " deleted successfully.");
        } catch (SQLException e) {
            System.err.println("Error deleting memo: " + e.getMessage());
        }
    }

    public void updateApplicationPath(int appId, String newPath) {
        String sql = "UPDATE tracked_applications SET executable_path = ? WHERE app_id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newPath);
            pstmt.setInt(2, appId);
            pstmt.executeUpdate();
            System.out.println("Updated path for app ID: " + appId);

        } catch (SQLException e) {
            System.err.println("Error updating application path: " + e.getMessage());
        }
    }

    public void updateLastClosedTimestamp(int appId) {
        String sql = "UPDATE tracked_applications SET last_closed_at = CURRENT_TIMESTAMP WHERE app_id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, appId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating last closed timestamp: " + e.getMessage());
        }
    }

    public Optional<Timestamp> getLastClosedTimestamp(int appId) {
        String sql = "SELECT last_closed_at FROM tracked_applications WHERE app_id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, appId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.ofNullable(rs.getTimestamp("last_closed_at"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching last closed timestamp: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<MemoViewItem> searchMemos(String query) {
        List<MemoViewItem> results = new ArrayList<>();
        String searchQuery = "%" + query.toLowerCase() + "%";
        String sql = "SELECT m.*, ta.app_name FROM memos m " +
                "JOIN tracked_applications ta ON m.app_id = ta.app_id " +
                "WHERE LOWER(m.transcription_text) LIKE ? OR LOWER(ta.app_name) LIKE ? " +
                "ORDER BY m.created_at DESC";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, searchQuery);
            pstmt.setString(2, searchQuery);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                results.add(new MemoViewItem(
                        rs.getInt("memo_id"),
                        rs.getInt("app_id"),
                        rs.getString("app_name"),
                        rs.getString("transcription_text"),
                        rs.getString("audio_file_path"),
                        rs.getTimestamp("created_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error searching memos: " + e.getMessage());
        }
        return results;
    }
}