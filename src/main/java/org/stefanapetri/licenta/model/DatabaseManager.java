package org.stefanapetri.licenta.model;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseManager {

    // !!! IMPORTANT: CHANGE THESE TO MATCH YOUR POSTGRESQL CONFIGURATION !!!
    private final String dbUrl = "jdbc:postgresql://localhost:5432/app_tracker";
    private final String dbUser = "postgres"; // or your username
    private final String dbPassword = "postgres"; // your password

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
            // In a real app, you'd show an error dialog to the user.
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
                return Optional.of(new TrackedApplication(newId, appName, executablePath));
            }
        } catch (SQLException e) {
            System.err.println("Error adding application: " + e.getMessage());
        }
        return Optional.empty();
    }

    public void removeTrackedApplication(int appId) {
        String sql = "DELETE FROM tracked_applications WHERE app_id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, appId);
            pstmt.executeUpdate();
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
            pstmt.setString(3, audioFilePath); // Can be null if you don't store the file path
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving memo: " + e.getMessage());
        }
    }
    /**
     * Updates the text of a specific memo in the database.
     * @param memoId The ID of the memo to update.
     * @param newText The new transcription text to save.
     */
    public void updateMemoText(int memoId, String newText) {
        String sql = "UPDATE memos SET transcription_text = ? WHERE memo_id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newText);
            pstmt.setInt(2, memoId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error updating memo text: " + e.getMessage());
            // In a real application, you might show an error dialog here.
        }
    }
    public Optional<Memo> getLatestMemoForApp(int appId) {
        String sql = "SELECT * FROM memos WHERE app_id = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, appId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Memo memo = new Memo(
                        rs.getInt("memo_id"),
                        rs.getInt("app_id"),
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


}