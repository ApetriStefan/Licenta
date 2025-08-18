package org.stefanapetri.licenta.model;

import java.sql.Timestamp;

public record Memo(
        int memoId,
        int appId,
        String transcriptionText,
        String audioFilePath,
        Timestamp createdAt
) {}