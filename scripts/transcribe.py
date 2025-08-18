import whisper
import sys
import os
import torch

# --- NEW: The modern and correct way to ensure UTF-8 output on all streams ---
# This tells Python to use UTF-8 for everything it prints.
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

def transcribe_audio(file_path):
    # Using 'cpu' explicitly can sometimes be more stable if CUDA setup is problematic.
    # Change back to 'cuda' if you are sure your GPU is configured correctly.
    device = 'cuda' if torch.cuda.is_available() else 'cpu'

    # It's more efficient to load the model only once.
    # We'll keep it here for simplicity, but for a production app,
    # you'd load it once at the start.
    model = whisper.load_model("small").to(device)

    result = model.transcribe(file_path)

    # Ensure the output is clean by stripping leading/trailing whitespace
    print(result["text"].strip())

if __name__ == "__main__":
    if len(sys.argv) > 1:
        audio_file_path = sys.argv[1]
        if os.path.exists(audio_file_path):
            try:
                transcribe_audio(audio_file_path)
            except Exception as e:
                # Explicitly convert the exception to a string to be safe
                print(f"Error during transcription: {str(e)}", file=sys.stderr)
        else:
            print(f"Error: File not found at {audio_file_path}", file=sys.stderr)
    else:
        print("Error: No audio file path provided.", file=sys.stderr)