// src/main/java/org/stefanapetri/licenta/service/TranscriptionResult.java
package org.stefanapetri.licenta.service;

/**
 * A data record to hold the results from the transcription process,
 * including the transcribed text and the CPU time consumed by the Python process.
 */
public record TranscriptionResult(String transcription, long pythonCpuTimeMillis) {}