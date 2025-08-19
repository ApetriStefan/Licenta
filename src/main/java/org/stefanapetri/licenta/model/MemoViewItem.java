package org.stefanapetri.licenta.model;

import java.sql.Timestamp;

/**
 * A data class used for displaying memo information in UI tables,
 * including the associated application's name.
 */
public record MemoViewItem(
        int memoId,
        int appId,
        String appName, // The name of the application associated with this memo
        String transcriptionText,
        String audioFilePath,
        Timestamp createdAt
) {}