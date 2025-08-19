import sys
import os
from faster_whisper import WhisperModel
import google.generativeai as genai

# Ensure UTF-8 output on all streams
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

def transcribe_audio(file_path, enable_gemini, gemini_api_key):
    compute_type = "int8"
    device = "cpu"

    # Load Whisper model (still necessary for transcription)
    # This will download the model on the first run.
    whisper_model = WhisperModel("small", device=device, compute_type=compute_type)

    # Transcribe the audio
    segments, info = whisper_model.transcribe(file_path, beam_size=5)
    transcription_text = "".join(segment.text for segment in segments)

    print(f"Detected language '{info.language}' with probability {info.language_probability}", file=sys.stderr)
    print(f"Raw Transcription: {transcription_text}", file=sys.stderr) # For debugging in stderr

    # --- MODIFIED: Conditional Gemini API Call ---
    if enable_gemini:
        if not gemini_api_key:
            print("Warning: Gemini API processing enabled but no API key provided. Falling back to raw transcription.", file=sys.stderr)
            print(transcription_text.strip()) # Fallback to raw transcription
            return

        try:
            genai.configure(api_key=gemini_api_key) # Use the provided API key
            model = genai.GenerativeModel('gemini-2.5-flash')

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

            gemini_response = model.generate_content(prompt)

            if gemini_response.candidates:
                print(gemini_response.candidates[0].content.parts[0].text.strip())
            else:
                print("Error: Gemini response had no candidates. Falling back to raw transcription.", file=sys.stderr)
                print(transcription_text.strip()) # Fallback if Gemini fails to provide candidates

        except Exception as e:
            print(f"Error communicating with Gemini API: {str(e)}. Falling back to raw transcription.", file=sys.stderr)
            print(transcription_text.strip()) # Fallback to raw transcription
    else:
        # If Gemini processing is disabled, just print the raw transcription
        print(transcription_text.strip())

# --- Main execution block ---
if __name__ == "__main__":
    audio_file_path = None
    enable_gemini = False
    gemini_api_key = ""

    # Parse command-line arguments
    for i, arg in enumerate(sys.argv):
        if i == 1: # First argument is always audio file path
            audio_file_path = arg
        elif arg.startswith("--enable-gemini="):
            enable_gemini = arg.split("=")[1].lower() == "true"
        elif arg.startswith("--gemini-api-key="):
            gemini_api_key = arg.split("=")[1]

    if audio_file_path and os.path.exists(audio_file_path):
        try:
            transcribe_audio(audio_file_path, enable_gemini, gemini_api_key)
        except Exception as e:
            print(f"Critical error in transcribe_audio: {str(e)}", file=sys.stderr)
            print("Error: Transcription or processing failed.", file=sys.stdout)
    else:
        print(f"Error: Audio file not found or path not provided: {audio_file_path}", file=sys.stderr)
        print("Error: No audio input or file not found.", file=sys.stdout)