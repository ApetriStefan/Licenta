import sys
import os
from faster_whisper import WhisperModel

# Ensure UTF-8 output on all streams
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

def transcribe_audio(file_path):
    compute_type = "int8"
    device = "cpu"

    model = WhisperModel("small", device=device, compute_type=compute_type)
    segments, info = model.transcribe(file_path, beam_size=5)

    # --- THIS IS THE KEY CHANGE ---
    # Print diagnostic information to stderr, so it doesn't pollute the final output.
    # The warnings from huggingface_hub also go to stderr by default.
    print(f"Detected language '{info.language}' with probability {info.language_probability}", file=sys.stderr)

    full_text = "".join(segment.text for segment in segments)

    # Print the final, clean transcription to stdout.
    print(full_text.strip())


if __name__ == "__main__":
    if len(sys.argv) > 1:
        audio_file_path = sys.argv[1]
        if os.path.exists(audio_file_path):
            try:
                transcribe_audio(audio_file_path)
            except Exception as e:
                print(f"Error during transcription: {str(e)}", file=sys.stderr)
        else:
            print(f"Error: File not found at {audio_file_path}", file=sys.stderr)
    else:
        print("Error: No audio file path provided.", file=sys.stderr)