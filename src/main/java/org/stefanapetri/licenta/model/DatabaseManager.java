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
                        rs.getString("app_name"), // NEW: Get app_name from join
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

    // --- MODIFIED: Return type changed to List<MemoViewItem> ---
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
                        rs.getString("app_name"), // NEW: Get app_name from join
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

    // --- NEW METHOD: Search Memos ---
    /**
     * Searches memos by their transcription text and/or associated application name.
     * @param query The search string.
     * @return A list of MemoViewItem objects matching the query.
     */
    public List<MemoViewItem> searchMemos(String query) {
        List<MemoViewItem> results = new ArrayList<>();
        String searchQuery = "%" + query.toLowerCase() + "%"; // Case-insensitive search
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