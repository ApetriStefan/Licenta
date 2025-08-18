import whisper
import sys
import os
import torch


def transcribe_audio(file_path):
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    model = whisper.load_model("small").to(device)
    result = model.transcribe(file_path)
    print(result["text"])

if __name__ == "__main__":
    if len(sys.argv) > 1:
        audio_file_path = sys.argv[1]
        if os.path.exists(audio_file_path):
            try:
                transcribe_audio(audio_file_path)
            except Exception as e:
                print(f"Error during transcription: {e}", file=sys.stderr)
        else:
            print(f"Error: File not found at {audio_file_path}", file=sys.stderr)
    else:
        print("Error: No audio file path provided.", file=sys.stderr)
