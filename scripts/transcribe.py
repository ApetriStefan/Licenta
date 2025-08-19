import sys
import os
from faster_whisper import WhisperModel
import google.generativeai as genai # NEW: Import Gemini library


# Ensure UTF-8 output on all streams
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

# --- NEW: Configure Gemini API ---
# This will try to get the API key from the GOOGLE_API_KEY environment variable.
# DO NOT hardcode your API key here!
API_KEY = os.getenv("GOOGLE_API_KEY")
if not API_KEY:
    print("Error: GOOGLE_API_KEY environment variable not set.", file=sys.stderr)
    sys.exit(1) # Exit if API key is missing

genai.configure(api_key=API_KEY)

def transcribe_audio(file_path):
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

    # --- NEW: Call Gemini API ---
    try:
        model = genai.GenerativeModel('gemini-2.5-flash') # Use the gemini-pro model for text

        # Craft a detailed prompt for Gemini
        prompt = f"""
        You are an AI assistant designed to help users recall their work sessions. 
        Given a voice memo transcription about what the user was doing in an application, 
        please process it and provide a concise, structured summary. The summary can be in any language in which the transcription is provided, do not translate it. If there are small parts of the transcription in another language than the majority, use them as-is and provide a translation in (parantheses).

        Your task involves the following:
        1.  **Resume/Identify Activities:** Extract the core activities, tasks, decisions, problems encountered, or progress made. Focus on "what was done" and "what needs to be done next".
        2.  **Summarize:** Condense the key information into a brief, easy-to-read summary.
        3.  **Spellcheck & Clarity:** Ensure the language is grammatically correct, professional, and clear, fixing any obvious transcription errors.
        4.  **Analysis (Implicit Actions):** Identify any explicit or implied action items or next steps.
        5.  **Formatting:** Present your findings clearly using bullet points. Start with a main summary point if applicable, then detail specific activities/tasks.

        Example Output Format (-titles can be in any language, as specified before):
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

        # Send the prompt to Gemini
        gemini_response = model.generate_content(prompt)

        # Print Gemini's formatted text to standard output
        # This is what the Java application will read.
        if gemini_response.candidates:
            # Access the text from the first part of the first candidate
            print(gemini_response.candidates[0].content.parts[0].text.strip())
        else:
            print("Error: Gemini response had no candidates.", file=sys.stderr)
            # You might want to print the raw transcription as a fallback if Gemini fails.
            # print(transcription_text.strip(), file=sys.stderr)

    except Exception as e:
        print(f"Error communicating with Gemini API: {str(e)}", file=sys.stderr)
        # As a fallback, if Gemini fails, print the raw Whisper transcription
        print(transcription_text.strip()) # Print raw transcription to stdout as fallback

# --- Main execution block (unchanged) ---
if __name__ == "__main__":
    if len(sys.argv) > 1:
        audio_file_path = sys.argv[1]
        if os.path.exists(audio_file_path):
            try:
                transcribe_audio(audio_file_path)
            except Exception as e:
                print(f"Critical error in transcribe_audio: {str(e)}", file=sys.stderr)
                # If a critical error occurs even before Gemini, print a generic error to stdout
                print("Error: Transcription or processing failed.", file=sys.stdout)
        else:
            print(f"Error: File not found at {audio_file_path}", file=sys.stderr)
            print("Error: Audio file not found.", file=sys.stdout)
    else:
        print("Error: No audio file path provided.", file=sys.stderr)
        print("Error: No audio input.", file=sys.stdout)