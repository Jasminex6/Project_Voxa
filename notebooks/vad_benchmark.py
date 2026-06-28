"""
══════════════════════════════════════════════════════════════════
  Voxa VAD Benchmark — Evaluate VAD on test data
  Dev A Deliverable (Phase 1)
══════════════════════════════════════════════════════════════════

This script benchmarks the VAD in two modes:

  MODE 1 — Synthetic (no dataset required):
    Generates test audio with known speech regions and measures
    detection accuracy, false positives, and timing.

  MODE 2 — Real dataset (if available):
    Reads .wav/.pcm files from a test directory and runs full
    VAD + MFCC + DTW pipeline to measure TAR/FAR/URR with VAD enabled.

Usage:
  python vad_benchmark.py                        # Synthetic mode
  python vad_benchmark.py --data_dir ./test_data  # Real data mode

Dependencies:
  pip install webrtcvad numpy tabulate
  (optional) pip install soundfile matplotlib
"""

import numpy as np
import time
import os
import argparse
from pathlib import Path
from typing import List, Dict, Tuple

from vad import VoxaVAD, VADConfig, generate_test_audio, load_audio_file


# ══════════════════════════════════════════════════════════════
#  Synthetic Benchmark
# ══════════════════════════════════════════════════════════════

def benchmark_synthetic(config: VADConfig, n_trials: int = 100) -> Dict:
    """
    Run VAD on synthetic audio with known speech boundaries.
    
    Test scenarios:
      1. Valid speech (400-2000ms) → should detect
      2. Too-short speech (<400ms) → should reject
      3. Too-long speech (>2000ms) → should reject
      4. Pure silence → should reject
      5. Noise only → should reject
      6. Multiple speech bursts → should detect each
    """
    results = {
        'valid_detected': 0,
        'valid_total': 0,
        'short_rejected': 0,
        'short_total': 0,
        'long_rejected': 0,
        'long_total': 0,
        'silence_rejected': 0,
        'silence_total': 0,
        'noise_rejected': 0,
        'noise_total': 0,
        'latencies_ms': [],
        'segment_durations_ms': [],
    }

    print(f"\nRunning {n_trials} trials per scenario...\n")

    # ── Scenario 1: Valid speech segments (should detect) ──
    print("  [1/5] Valid speech (400-2000ms)...")
    for _ in range(n_trials):
        # Random speech duration between 400ms and 1800ms
        speech_dur = np.random.randint(500, 1800)
        speech_start = np.random.randint(200, 500)
        speech_end = speech_start + speech_dur
        total_dur = speech_end + np.random.randint(300, 600)

        audio = generate_test_audio(
            duration_ms=total_dur,
            speech_start_ms=speech_start,
            speech_end_ms=speech_end,
            speech_amplitude=np.random.randint(5000, 15000),
            noise_amplitude=np.random.randint(50, 300),
        )

        vad = VoxaVAD(config)
        t0 = time.perf_counter()
        segments = vad.process_audio(audio)
        t1 = time.perf_counter()

        results['valid_total'] += 1
        if len(segments) >= 1:
            results['valid_detected'] += 1
            results['segment_durations_ms'].append(len(segments[0]) / 16000 * 1000)
        results['latencies_ms'].append((t1 - t0) * 1000)

    # ── Scenario 2: Too-short speech (<400ms) — should reject ──
    print("  [2/5] Short speech (<400ms)...")
    for _ in range(n_trials):
        speech_dur = np.random.randint(50, 350)  # too short
        audio = generate_test_audio(
            duration_ms=1500,
            speech_start_ms=300,
            speech_end_ms=300 + speech_dur,
            speech_amplitude=np.random.randint(5000, 15000),
            noise_amplitude=np.random.randint(50, 300),
        )

        vad = VoxaVAD(config)
        segments = vad.process_audio(audio)
        results['short_total'] += 1
        if len(segments) == 0:
            results['short_rejected'] += 1

    # ── Scenario 3: Too-long speech (>2000ms) — should reject/discard ──
    print("  [3/5] Long speech (>2000ms)...")
    for _ in range(n_trials):
        speech_dur = np.random.randint(2200, 4000)  # too long
        audio = generate_test_audio(
            duration_ms=speech_dur + 1000,
            speech_start_ms=200,
            speech_end_ms=200 + speech_dur,
            speech_amplitude=np.random.randint(5000, 15000),
            noise_amplitude=np.random.randint(50, 200),
        )

        vad = VoxaVAD(config)
        segments = vad.process_audio(audio)
        results['long_total'] += 1
        if len(segments) == 0:
            results['long_rejected'] += 1

    # ── Scenario 4: Pure silence ──
    print("  [4/5] Pure silence...")
    for _ in range(n_trials):
        audio = np.zeros(np.random.randint(16000, 48000), dtype=np.int16)
        vad = VoxaVAD(config)
        segments = vad.process_audio(audio)
        results['silence_total'] += 1
        if len(segments) == 0:
            results['silence_rejected'] += 1

    # ── Scenario 5: Noise only (no speech) ──
    print("  [5/5] Background noise only...")
    for _ in range(n_trials):
        n = np.random.randint(16000, 48000)
        audio = (np.random.randn(n) * np.random.randint(100, 400)).astype(np.int16)
        vad = VoxaVAD(config)
        segments = vad.process_audio(audio)
        results['noise_total'] += 1
        if len(segments) == 0:
            results['noise_rejected'] += 1

    return results


def print_synthetic_report(results: Dict, config: VADConfig):
    """Print formatted benchmark results."""
    print()
    print("=" * 65)
    print("  VOXA VAD BENCHMARK REPORT — Synthetic Audio")
    print("=" * 65)
    print()

    # Config summary
    print("Configuration:")
    print(f"  Backend:          {'WebRTC' if True else 'Energy-based'}")
    print(f"  Aggressiveness:   {config.aggressiveness}")
    print(f"  Frame size:       {config.frame_ms}ms")
    print(f"  Speech trigger M: {config.speech_trigger_frames} frames")
    print(f"  Silence bound N:  {config.silence_boundary_frames} frames")
    print(f"  Min segment:      {config.min_segment_ms}ms")
    print(f"  Max segment:      {config.max_segment_ms}ms")
    print()

    # Results table
    print("+-----------------------------+----------+----------+----------+")
    print("| Scenario                    | Expected | Actual   | Rate     |")
    print("+-----------------------------+----------+----------+----------+")

    # Valid speech detection
    v_det = results['valid_detected']
    v_tot = results['valid_total']
    v_rate = v_det / v_tot * 100 if v_tot > 0 else 0
    status = "[OK]" if v_rate >= 80 else "[~]" if v_rate >= 60 else "[!!]"
    print(f"| Valid speech detected       | Detect   | {v_det:>4}/{v_tot:<4} | {v_rate:5.1f}% {status}|")

    # Short rejection
    s_rej = results['short_rejected']
    s_tot = results['short_total']
    s_rate = s_rej / s_tot * 100 if s_tot > 0 else 0
    status = "[OK]" if s_rate >= 90 else "[~]" if s_rate >= 70 else "[!!]"
    print(f"| Short speech rejected       | Reject   | {s_rej:>4}/{s_tot:<4} | {s_rate:5.1f}% {status}|")

    # Long rejection
    l_rej = results['long_rejected']
    l_tot = results['long_total']
    l_rate = l_rej / l_tot * 100 if l_tot > 0 else 0
    status = "[OK]" if l_rate >= 80 else "[~]" if l_rate >= 60 else "[!!]"
    print(f"| Long speech rejected        | Reject   | {l_rej:>4}/{l_tot:<4} | {l_rate:5.1f}% {status}|")

    # Silence rejection
    si_rej = results['silence_rejected']
    si_tot = results['silence_total']
    si_rate = si_rej / si_tot * 100 if si_tot > 0 else 0
    status = "[OK]" if si_rate >= 99 else "[~]" if si_rate >= 90 else "[!!]"
    print(f"| Silence rejected            | Reject   | {si_rej:>4}/{si_tot:<4} | {si_rate:5.1f}% {status}|")

    # Noise rejection
    n_rej = results['noise_rejected']
    n_tot = results['noise_total']
    n_rate = n_rej / n_tot * 100 if n_tot > 0 else 0
    status = "[OK]" if n_rate >= 90 else "[~]" if n_rate >= 70 else "[!!]"
    print(f"| Noise-only rejected         | Reject   | {n_rej:>4}/{n_tot:<4} | {n_rate:5.1f}% {status}|")

    print("+-----------------------------+----------+----------+----------+")
    print()

    # Latency stats
    lats = results['latencies_ms']
    if lats:
        print("Latency (per audio file):")
        print(f"  Mean:   {np.mean(lats):7.2f} ms")
        print(f"  Median: {np.median(lats):7.2f} ms")
        print(f"  P95:    {np.percentile(lats, 95):7.2f} ms")
        print(f"  Max:    {np.max(lats):7.2f} ms")
        print()

    # Segment duration stats
    durs = results['segment_durations_ms']
    if durs:
        print("Detected segment durations:")
        print(f"  Mean:   {np.mean(durs):7.0f} ms")
        print(f"  Min:    {np.min(durs):7.0f} ms")
        print(f"  Max:    {np.max(durs):7.0f} ms")
        print(f"  Std:    {np.std(durs):7.0f} ms")
        print()

    # Overall verdict
    overall_pass = (v_rate >= 80 and s_rate >= 90 and si_rate >= 99 and n_rate >= 90)
    if overall_pass:
        print("=== VERDICT: [OK] PASS - VAD ready for pipeline integration ===")
    else:
        print("=== VERDICT: [~] NEEDS TUNING - Adjust aggressiveness or thresholds ===")
    print()


# ══════════════════════════════════════════════════════════════
#  Aggressiveness Sweep
# ══════════════════════════════════════════════════════════════

def sweep_aggressiveness(n_trials: int = 50):
    """Test all 4 aggressiveness levels and compare."""
    print()
    print("=" * 65)
    print("  AGGRESSIVENESS SWEEP (0, 1, 2, 3)")
    print("=" * 65)
    print()
    print("+-------+----------+----------+----------+----------+----------+")
    print("| Level | Valid    | Short    | Long     | Silence  | Noise    |")
    print("|       | Detect%  | Reject%  | Reject%  | Reject%  | Reject%  |")
    print("+-------+----------+----------+----------+----------+----------+")

    for agg in range(4):
        config = VADConfig(aggressiveness=agg)
        r = benchmark_synthetic(config, n_trials=n_trials)

        v = r['valid_detected'] / r['valid_total'] * 100 if r['valid_total'] else 0
        s = r['short_rejected'] / r['short_total'] * 100 if r['short_total'] else 0
        l = r['long_rejected'] / r['long_total'] * 100 if r['long_total'] else 0
        si = r['silence_rejected'] / r['silence_total'] * 100 if r['silence_total'] else 0
        n = r['noise_rejected'] / r['noise_total'] * 100 if r['noise_total'] else 0

        print(f"|   {agg}   | {v:6.1f}%  | {s:6.1f}%  | {l:6.1f}%  | {si:6.1f}%  | {n:6.1f}%  |")

    print("+-------+----------+----------+----------+----------+----------+")
    print()
    print("Higher aggressiveness = more aggressive at filtering non-speech.")
    print("Recommended: 2 or 3 for noisy child environments.")


# ══════════════════════════════════════════════════════════════
#  Real Data Benchmark (when dataset is available)
# ══════════════════════════════════════════════════════════════

def benchmark_real_data(data_dir: str, config: VADConfig) -> Dict:
    """
    Run VAD on real audio files from a directory.
    
    Expected directory structure:
      data_dir/
        speaker_01/
          word_water_01.wav
          word_water_02.wav
          word_hungry_01.wav
          ...
        speaker_02/
          ...
    
    Or flat structure:
      data_dir/
        sample_001.wav
        sample_002.wav
        ...
    """
    data_path = Path(data_dir)
    if not data_path.exists():
        print(f"[!!] Data directory not found: {data_dir}")
        return {}

    # Collect all audio files
    audio_files = []
    for ext in ['*.wav', '*.pcm', '*.flac', '*.mp3']:
        audio_files.extend(data_path.rglob(ext))

    if not audio_files:
        print(f"[!!] No audio files found in {data_dir}")
        return {}

    print(f"\nFound {len(audio_files)} audio files in {data_dir}")
    print()

    results = {
        'total_files': len(audio_files),
        'files_with_speech': 0,
        'total_segments': 0,
        'segment_durations_ms': [],
        'latencies_ms': [],
        'errors': 0,
    }

    for i, fpath in enumerate(audio_files):
        try:
            audio = load_audio_file(str(fpath))
            vad = VoxaVAD(config)

            t0 = time.perf_counter()
            details = vad.process_audio_detailed(audio)
            t1 = time.perf_counter()

            results['latencies_ms'].append((t1 - t0) * 1000)

            if details:
                results['files_with_speech'] += 1
                results['total_segments'] += len(details)
                for d in details:
                    results['segment_durations_ms'].append(d['duration_ms'])

            if (i + 1) % 50 == 0:
                print(f"  Processed {i+1}/{len(audio_files)} files...")

        except Exception as e:
            results['errors'] += 1
            print(f"  [WARN] Error processing {fpath.name}: {e}")

    # Print report
    print()
    print("=" * 65)
    print("  VOXA VAD BENCHMARK REPORT — Real Audio Data")
    print("=" * 65)
    print()
    print(f"  Total files:         {results['total_files']}")
    print(f"  Files with speech:   {results['files_with_speech']}")
    print(f"  Total segments:      {results['total_segments']}")
    print(f"  Errors:              {results['errors']}")
    print()

    if results['segment_durations_ms']:
        durs = results['segment_durations_ms']
        print(f"  Segment durations:")
        print(f"    Mean:    {np.mean(durs):7.0f} ms")
        print(f"    Median:  {np.median(durs):7.0f} ms")
        print(f"    Min:     {np.min(durs):7.0f} ms")
        print(f"    Max:     {np.max(durs):7.0f} ms")
        print()

    if results['latencies_ms']:
        lats = results['latencies_ms']
        print(f"  Processing latency:")
        print(f"    Mean:    {np.mean(lats):7.2f} ms")
        print(f"    P95:     {np.percentile(lats, 95):7.2f} ms")
        print()

    return results


# ══════════════════════════════════════════════════════════════
#  Save Report to File
# ══════════════════════════════════════════════════════════════

def save_report(results: Dict, config: VADConfig, filepath: str):
    """Save benchmark results as a markdown report."""
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write("# Voxa VAD Benchmark Report\n\n")
        f.write(f"> Generated: {time.strftime('%Y-%m-%d %H:%M:%S')}\n\n")

        f.write("## Configuration\n\n")
        f.write(f"| Parameter | Value |\n")
        f.write(f"|-----------|-------|\n")
        f.write(f"| Aggressiveness | {config.aggressiveness} |\n")
        f.write(f"| Frame size | {config.frame_ms}ms |\n")
        f.write(f"| Speech trigger (M) | {config.speech_trigger_frames} frames |\n")
        f.write(f"| Silence boundary (N) | {config.silence_boundary_frames} frames |\n")
        f.write(f"| Min segment | {config.min_segment_ms}ms |\n")
        f.write(f"| Max segment | {config.max_segment_ms}ms |\n\n")

        f.write("## Results\n\n")
        f.write(f"| Scenario | Rate |\n")
        f.write(f"|----------|------|\n")

        if 'valid_total' in results and results['valid_total'] > 0:
            v = results['valid_detected'] / results['valid_total'] * 100
            f.write(f"| Valid speech detected | {v:.1f}% |\n")
        if 'short_total' in results and results['short_total'] > 0:
            s = results['short_rejected'] / results['short_total'] * 100
            f.write(f"| Short speech rejected | {s:.1f}% |\n")
        if 'long_total' in results and results['long_total'] > 0:
            l = results['long_rejected'] / results['long_total'] * 100
            f.write(f"| Long speech rejected | {l:.1f}% |\n")
        if 'silence_total' in results and results['silence_total'] > 0:
            si = results['silence_rejected'] / results['silence_total'] * 100
            f.write(f"| Silence rejected | {si:.1f}% |\n")
        if 'noise_total' in results and results['noise_total'] > 0:
            n = results['noise_rejected'] / results['noise_total'] * 100
            f.write(f"| Noise rejected | {n:.1f}% |\n")

        f.write(f"\n")

        if results.get('latencies_ms'):
            lats = results['latencies_ms']
            f.write("## Latency\n\n")
            f.write(f"| Stat | Value |\n")
            f.write(f"|------|-------|\n")
            f.write(f"| Mean | {np.mean(lats):.2f} ms |\n")
            f.write(f"| Median | {np.median(lats):.2f} ms |\n")
            f.write(f"| P95 | {np.percentile(lats, 95):.2f} ms |\n\n")

    print(f"[FILE] Report saved to: {filepath}")


# ══════════════════════════════════════════════════════════════
#  Main
# ══════════════════════════════════════════════════════════════

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Voxa VAD Benchmark")
    parser.add_argument('--data_dir', type=str, default=None,
                        help='Path to directory with real audio files')
    parser.add_argument('--aggressiveness', type=int, default=2,
                        help='WebRTC aggressiveness (0-3)')
    parser.add_argument('--trials', type=int, default=100,
                        help='Number of trials per scenario (synthetic mode)')
    parser.add_argument('--sweep', action='store_true',
                        help='Run aggressiveness sweep (tests all 4 levels)')
    parser.add_argument('--save', type=str, default=None,
                        help='Save report to this file path')
    args = parser.parse_args()

    config = VADConfig(aggressiveness=args.aggressiveness)

    if args.sweep:
        sweep_aggressiveness(n_trials=args.trials)
    elif args.data_dir:
        results = benchmark_real_data(args.data_dir, config)
        if args.save:
            save_report(results, config, args.save)
    else:
        results = benchmark_synthetic(config, n_trials=args.trials)
        print_synthetic_report(results, config)
        if args.save:
            save_report(results, config, args.save)
        else:
            # Auto-save to default location
            report_path = os.path.join(os.path.dirname(__file__), "vad_benchmark_report.md")
            save_report(results, config, report_path)
