# src\main\resources\org\stefanapetri\licenta\scripts\transcribe.py
import sys
import os
import time # NEW
import json # NEW
import psutil # NEW

from faster_whisper import WhisperModel
import google.generativeai as genai

# Ensure UTF-8 output on all streams
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

METRICS_DELIMITER = "---METRICS_JSON_START---" # Must match Java

def get_process_memory_usage(process):
    # Returns RSS (Resident Set Size) in bytes
    return process.memory_info().rss

def transcribe_audio(file_path, whisper_model_size, enable_gemini, gemini_model_name, gemini_api_key):
    process = psutil.Process(os.getpid())
    metrics = {
        "total_execution_time_ms": 0.0,
        "whisper_transcription_time_ms": 0.0,
        "gemini_processing_time_ms": 0.0,
        "peak_memory_mb": 0.0,
        "cpu_user_time_s": 0.0,
        "cpu_system_time_s": 0.0,
        "whisper_model_used": whisper_model_size,
        "gemini_enabled": enable_gemini,
        "gemini_model_used": gemini_model_name if enable_gemini else "N/A"
    }

    start_total_time = time.time()
    initial_memory_rss = get_process_memory_usage(process)
    peak_memory_rss = initial_memory_rss

    compute_type = "int8" # Generally recommended for CPU for faster inference
    device = "cpu"

    # Load Whisper model
    print(f"Loading Whisper model: {whisper_model_size}", file=sys.stderr)
    start_whisper_load_time = time.time()
    whisper_model = WhisperModel(whisper_model_size, device=device, compute_type=compute_type)
    metrics["whisper_load_time_ms"] = (time.time() - start_whisper_load_time) * 1000
    peak_memory_rss = max(peak_memory_rss, get_process_memory_usage(process))


    # Transcribe the audio
    print(f"Transcribing audio from: {file_path}", file=sys.stderr)
    start_whisper_transcribe_time = time.time()
    segments, info = whisper_model.transcribe(file_path, beam_size=5)
    transcription_text = "".join(segment.text for segment in segments)
    metrics["whisper_transcription_time_ms"] = (time.time() - start_whisper_transcribe_time) * 1000
    peak_memory_rss = max(peak_memory_rss, get_process_memory_usage(process))


    print(f"Detected language '{info.language}' with probability {info.language_probability}", file=sys.stderr)
    print(f"Raw Transcription: {transcription_text}", file=sys.stderr) # For debugging in stderr

    final_transcription = transcription_text.strip()

    # --- Conditional Gemini API Call and model selection ---
    if enable_gemini:
        if not gemini_api_key:
            print("Warning: Gemini API processing enabled but no API key provided. Falling back to raw transcription.", file=sys.stderr)
        else:
            try:
                genai.configure(api_key=gemini_api_key) # Use the provided API key
                print(f"Using Gemini model: {gemini_model_name}", file=sys.stderr)
                model = genai.GenerativeModel(gemini_model_name) # Use gemini_model_name

                prompt = f"""
                You are an AI assistant designed to help users recall their work sessions.
                Given a voice memo transcription about what the user was last doing in an application,
                please process it and provide a concise, structured summary. The summary can be in any language,
                do not translate it. If there are parts of the transcription in another language than the majority of the text, use them as-is and provide a translation in (parantheses).

                Your task involves the following:
                1.  **Resume/Identify Activities:** Extract the core activities, tasks, decisions, problems encountered, or progress made. Focus on "what was done" and "what needs to be done next".
                2.  **Summarize:** Condense the key information into a brief, easy-to-read summary.
                3.  **Spellcheck & Clarity:** Ensure the language is grammatically correct, professional, and clear, fixing any obvious transcription errors.
                4.  **Analysis (Implicit Actions):** Identify any explicit or implied action items or next steps.
                5.  **Formatting:** Present your findings clearly using bullet points. Start with a main summary point if applicable, then detail specific activities/tasks.

                Example Output Format:
                - Brief summary of the session.
                - Completed:
                    - [Task 1 completed]
                    - [Task 2 completed]
                - In Progress:
                    - [Task 1 in progress]
                - Next Steps/Action Items:
                    - [Action item 1]
                    - [Action item 2]
                - Notes/Decisions:
                    - [Important note or decision]

                ---
                Here is the transcription from the user's voice memo:
                "{transcription_text}"
                """
                print("Sending prompt to Gemini API...", file=sys.stderr)
                start_gemini_time = time.time()
                gemini_response = model.generate_content(prompt)
                metrics["gemini_processing_time_ms"] = (time.time() - start_gemini_time) * 1000
                peak_memory_rss = max(peak_memory_rss, get_process_memory_usage(process))
                print("Received response from Gemini API.", file=sys.stderr)

                if gemini_response.candidates:
                    final_transcription = gemini_response.candidates[0].content.parts[0].text.strip()
                else:
                    print("Error: Gemini response had no candidates. Falling back to raw transcription.", file=sys.stderr)

            except Exception as e:
                print(f"Error communicating with Gemini API: {str(e)}. Falling back to raw transcription.", file=sys.stderr)

    # Finalize metrics
    metrics["total_execution_time_ms"] = (time.time() - start_total_time) * 1000
    metrics["peak_memory_mb"] = peak_memory_rss / (1024 * 1024) # Convert bytes to MB

    cpu_times = process.cpu_times()
    metrics["cpu_user_time_s"] = cpu_times.user
    metrics["cpu_system_time_s"] = cpu_times.system

    # Print the final transcription first
    print(final_transcription)

    # Then print the delimiter and JSON metrics
    print(METRICS_DELIMITER)
    print(json.dumps(metrics))


# --- Main execution block ---
if __name__ == "__main__":
    audio_file_path = None
    whisper_model_size = "small" # Default Whisper model size
    enable_gemini = False
    gemini_model_name = "gemini-2.5-flash" # Default Gemini model
    gemini_api_key = ""

    # Parse command-line arguments
    for i, arg in enumerate(sys.argv):
        if i == 1: # First argument is always audio file path
            audio_file_path = arg
        elif arg.startswith("--whisper-model="):
            whisper_model_size = arg.split("=")[1]
        elif arg.startswith("--enable-gemini="):
            enable_gemini = arg.split("=")[1].lower() == "true"
        elif arg.startswith("--gemini-model="):
            gemini_model_name = arg.split("=")[1]
        elif arg.startswith("--gemini-api-key="):
            gemini_api_key = arg.split("=")[1]

    if audio_file_path and os.path.exists(audio_file_path):
        try:
            transcribe_audio(audio_file_path, whisper_model_size, enable_gemini, gemini_model_name, gemini_api_key)
        except Exception as e:
            print(f"Critical error in transcribe_audio: {str(e)}", file=sys.stderr)
            # Ensure some output even on critical failure
            print("Error: Transcription or processing failed.", file=sys.stdout)
    else:
        print(f"Error: Audio file not found or path not provided: {audio_file_path}", file=sys.stderr)
        print("Error: No audio input or file not found.", file=sys.stdout)