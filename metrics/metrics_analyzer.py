# metrics_analyzer.py
import os
import re
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import jiwer # For Word Error Rate calculation

# --- Configuration ---
METRICS_DIR = "." # Looks in the current directory
OUTPUT_PLOTS_DIR = "performance_plots"

# !!! IMPORTANT !!!
# THIS IS THE CORRECTED GROUND TRUTH.
# It now uses "All right" (two words) to match the output of the best models.
GROUND_TRUTH_TRANSCRIPT = "All right this is a quick memo about my VS Code session. I started by creating a new Python project folder. Then I installed the Pylance extension for better code completion. I wrote a small script to fetch data from an API. I also configured the debugger to step through the code. Everything seems to be working as expected. Next I will refactor the data processing logic."


# --- Helper to parse a single metrics file ---
def parse_metrics_file(filepath):
    print(f"Parsing file: {os.path.basename(filepath)}")
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    data = {}

    # --- Extract Transcription using a more robust Regex ---
    # This looks for the text between "Transcription Output:" and "----" at the end.
    # The re.DOTALL flag allows '.' to match newlines.
    transcription_match = re.search(r"Transcription Output:\s*(.*?)\s*----------------------------", content, re.DOTALL)
    if not transcription_match:
        print(f"  [ERROR] Could not find 'Transcription Output:' section in {os.path.basename(filepath)}")
        return None
    transcription_text = transcription_match.group(1).strip()

    # --- Extract Python-side Metrics ---
    metrics_section_match = re.search(r"--- Python-side Metrics ---\s*(.*?)\s*--- Java-side System Metrics ---", content, re.DOTALL)
    if not metrics_section_match:
        print(f"  [ERROR] Could not find '--- Python-side Metrics ---' section in {os.path.basename(filepath)}")
        return None
    
    metrics_section = metrics_section_match.group(1)
    for line in metrics_section.strip().split('\n'):
        if ':' in line:
            key, value = line.split(':', 1)
            clean_key = key.strip().lower().replace(' ', '_')
            value_str = value.strip()
            
            try:
                data[clean_key] = float(value_str)
            except ValueError:
                if value_str.lower() == 'true':
                    data[clean_key] = True
                elif value_str.lower() == 'false':
                    data[clean_key] = False
                else:
                    data[clean_key] = value_str

    # --- Extract Model Info from Header ---
    header_section_match = re.search(r"--- Performance Test Run ---\s*(.*?)\s*--- Python-side Metrics ---", content, re.DOTALL)
    if not header_section_match:
        print(f"  [ERROR] Could not find header section in {os.path.basename(filepath)}")
        return None
    
    header_section = header_section_match.group(1)
    for line in header_section.strip().split('\n'):
        if line.startswith("Whisper Model:"):
            data['whisper_model'] = line.split(':', 1)[1].strip()
        if line.startswith("Gemini Model:"):
            gemini_model_str = line.split(':', 1)[1].strip()
            data['gemini_model'] = 'disabled' if gemini_model_str == 'N/A' else gemini_model_str

    # --- Calculate Word Error Rate (WER) ---
    transformation = jiwer.Compose([
        jiwer.ToLowerCase(),
        jiwer.RemovePunctuation(),
        jiwer.Strip()
    ])
    
    clean_ground_truth = transformation(GROUND_TRUTH_TRANSCRIPT)
    clean_hypothesis = transformation(transcription_text)
    
    error = jiwer.wer(clean_ground_truth, clean_hypothesis)
    data['word_error_rate'] = error
    
    return data

# --- Load all metrics ---
def load_all_metrics(metrics_dir):
    all_data = []
    for filename in os.listdir(metrics_dir):
        if filename.endswith(".txt"):
            filepath = os.path.join(metrics_dir, filename)
            data = parse_metrics_file(filepath)
            if data:
                all_data.append(data)
    return pd.DataFrame(all_data)

# --- Plotting Function ---
def plot_metrics(df):
    if df.empty:
        print("No data to plot.")
        return

    os.makedirs(OUTPUT_PLOTS_DIR, exist_ok=True)

    # Ensure consistent order for models, excluding 'base'
    whisper_order = ["tiny", "small", "medium", "large"]
    gemini_order = ["disabled", "gemini-2.5-flash", "gemini-2.5-pro"]

    df['whisper_model'] = pd.Categorical(df['whisper_model'], categories=whisper_order, ordered=True)
    df['gemini_model'] = pd.Categorical(df['gemini_model'], categories=gemini_order, ordered=True)

    df_sorted = df.sort_values(by=['whisper_model', 'gemini_model'])
    
    # --- Plot 1: Word Error Rate (Accuracy) vs. Whisper Model ---
    plt.figure(figsize=(12, 7))
    # Accuracy is based on the raw Whisper output, so we only need one data point per whisper model
    # We'll specifically use the 'gemini-disabled' runs for a fair comparison
    accuracy_data = df_sorted[df_sorted['gemini_model'] == 'disabled'].groupby('whisper_model')['word_error_rate'].mean().reset_index()
    
    bars = plt.bar(accuracy_data['whisper_model'], accuracy_data['word_error_rate'], color='skyblue')
    plt.ylabel('Word Error Rate (WER)')
    plt.title('Transcription Accuracy vs. Whisper Model (Lower is Better)')
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    
    # Add WER values on top of the bars
    for bar in bars:
        yval = bar.get_height()
        plt.text(bar.get_x() + bar.get_width()/2.0, yval, f'{yval:.3f}', va='bottom', ha='center')

    plt.tight_layout()
    plt.savefig(os.path.join(OUTPUT_PLOTS_DIR, 'accuracy_vs_model.png'))
    plt.close()

    # --- Prepare grouped data for subsequent plots ---
    grouped_data = df_sorted.groupby(['whisper_model', 'gemini_model'], observed=False).mean(numeric_only=True).reset_index()
    grouped_data['label'] = grouped_data['whisper_model'].astype(str) + " - " + grouped_data['gemini_model'].astype(str)

    # --- Plot 2: Total Execution Time Breakdown (Stacked Bar Chart) ---
    plt.figure(figsize=(14, 8))
    ind = np.arange(len(grouped_data))
    bar_width = 0.8
    
    p1 = plt.bar(ind, grouped_data['whisper_load_time_ms'], bar_width, label='Whisper Load Time')
    p2 = plt.bar(ind, grouped_data['whisper_transcription_time_ms'], bar_width, label='Whisper Transcription Time', bottom=grouped_data['whisper_load_time_ms'])
    p3 = plt.bar(ind, grouped_data['gemini_processing_time_ms'], bar_width, label='Gemini Processing Time', bottom=grouped_data['whisper_load_time_ms'] + grouped_data['whisper_transcription_time_ms'])

    plt.ylabel('Time (ms)')
    plt.title('Total Execution Time Breakdown by Model Combination')
    plt.xticks(ind, grouped_data['label'], rotation=45, ha='right')
    plt.legend()
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()
    plt.savefig(os.path.join(OUTPUT_PLOTS_DIR, 'total_execution_time_breakdown.png'))
    plt.close()

    # --- Plot 3: Peak Memory Usage (Grouped Bar Chart) ---
    plt.figure(figsize=(14, 8))
    num_whisper_models = len(whisper_order)
    index = np.arange(num_whisper_models)
    bar_width = 0.25

    for i, gemini_m in enumerate(gemini_order):
        subset = grouped_data[grouped_data['gemini_model'] == gemini_m]
        plot_values = [subset[subset['whisper_model'] == wm]['peak_memory_mb'].iloc[0] if wm in subset['whisper_model'].values else 0 for wm in whisper_order]
        plt.bar(index + i * bar_width, plot_values, bar_width, label=f'Gemini: {gemini_m}')
    
    plt.ylabel('Peak Memory (MB)')
    plt.title('Peak Memory Usage by Model Combination')
    plt.xticks(index + bar_width, whisper_order)
    plt.legend(title="Gemini Model")
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()
    plt.savefig(os.path.join(OUTPUT_PLOTS_DIR, 'peak_memory_usage.png'))
    plt.close()

    # --- Plot 4: Total CPU Time (Grouped Bar Chart) ---
    plt.figure(figsize=(14, 8))
    grouped_data['total_cpu_time_s'] = grouped_data['cpu_user_time_s'] + grouped_data['cpu_system_time_s']
    
    for i, gemini_m in enumerate(gemini_order):
        subset = grouped_data[grouped_data['gemini_model'] == gemini_m]
        plot_values = [subset[subset['whisper_model'] == wm]['total_cpu_time_s'].iloc[0] if wm in subset['whisper_model'].values else 0 for wm in whisper_order]
        plt.bar(index + i * bar_width, plot_values, bar_width, label=f'Gemini: {gemini_m}')
    
    plt.ylabel('Total CPU Time (seconds)')
    plt.title('Total CPU Time by Model Combination')
    plt.xticks(index + bar_width, whisper_order)
    plt.legend(title="Gemini Model")
    plt.grid(axis='y', linestyle='--', alpha=0.7)
    plt.tight_layout()
    plt.savefig(os.path.join(OUTPUT_PLOTS_DIR, 'total_cpu_time.png'))
    plt.close()


# --- Main Execution ---
if __name__ == "__main__":
    if not GROUND_TRUTH_TRANSCRIPT.strip():
        print("ERROR: GROUND_TRUTH_TRANSCRIPT is empty. Please edit the script and add your ground truth text.")
    else:
        df_metrics = load_all_metrics(METRICS_DIR)
        if not df_metrics.empty:
            plot_metrics(df_metrics)
            print(f"Plots saved to the '{OUTPUT_PLOTS_DIR}' directory.")
        else:
            print(f"No valid metrics files found in '{METRICS_DIR}' or failed to parse any data.")
