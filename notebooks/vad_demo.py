"""
══════════════════════════════════════════════════════════════════
  Voxa VAD — Interactive Demo & Visualization
  Dev A Deliverable (Phase 1)
══════════════════════════════════════════════════════════════════

This script demonstrates the VAD visually:
  1. Generates synthetic audio (or loads a real file)
  2. Runs the VAD state machine
  3. Plots the waveform with detected segments highlighted
  4. Prints frame-by-frame state transitions

Usage:
  python vad_demo.py                           # Synthetic demo
  python vad_demo.py --audio path/to/file.wav  # Real audio demo
  python vad_demo.py --no-plot                  # Text-only mode

Dependencies:
  pip install webrtcvad numpy
  (optional) pip install matplotlib soundfile
"""

import numpy as np
import os
import argparse
from vad import VoxaVAD, VADConfig, generate_test_audio, load_audio_file

HAS_MATPLOTLIB = False
try:
    import matplotlib
    matplotlib.use('Agg')  # Non-interactive backend
    import matplotlib.pyplot as plt
    import matplotlib.patches as mpatches
    HAS_MATPLOTLIB = True
except ImportError:
    pass


def plot_vad_results(audio: np.ndarray, 
                     segments: list, 
                     config: VADConfig,
                     title: str = "Voxa VAD Detection",
                     save_path: str = None):
    """
    Plot waveform with detected speech segments highlighted.
    """
    if not HAS_MATPLOTLIB:
        print("[WARN]  matplotlib not installed. Install with: pip install matplotlib")
        print("    Skipping plot generation.")
        return

    sr = config.sample_rate
    t = np.arange(len(audio)) / sr

    fig, axes = plt.subplots(2, 1, figsize=(14, 6), sharex=True,
                             gridspec_kw={'height_ratios': [3, 1]})

    # ── Plot 1: Waveform with segment highlights ──
    ax = axes[0]
    ax.plot(t, audio, color='#4a90d9', linewidth=0.3, alpha=0.7)
    ax.set_ylabel('Amplitude', fontsize=10)
    ax.set_title(title, fontsize=12, fontweight='bold')

    colors = ['#2ecc71', '#e74c3c', '#f39c12', '#9b59b6', '#1abc9c']
    for i, seg_info in enumerate(segments):
        start_s = seg_info['start_sample'] / sr
        end_s = seg_info['end_sample'] / sr
        color = colors[i % len(colors)]
        ax.axvspan(start_s, end_s, alpha=0.25, color=color, 
                   label=f"Seg {i+1}: {seg_info['duration_ms']:.0f}ms")

    ax.legend(loc='upper right', fontsize=8)
    ax.grid(True, alpha=0.2)

    # ── Plot 2: Energy envelope ──
    ax2 = axes[1]
    frame_size = config.frame_size
    energies = []
    for start in range(0, len(audio) - frame_size + 1, frame_size):
        frame = audio[start:start + frame_size].astype(np.float64)
        rms = np.sqrt(np.mean(frame ** 2))
        energies.append(rms)

    energy_t = np.arange(len(energies)) * config.frame_ms / 1000
    ax2.fill_between(energy_t, energies, alpha=0.5, color='#e67e22')
    ax2.set_ylabel('RMS Energy', fontsize=10)
    ax2.set_xlabel('Time (seconds)', fontsize=10)
    ax2.grid(True, alpha=0.2)

    plt.tight_layout()

    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches='tight')
        print(f"[CHART] Plot saved to: {save_path}")
    else:
        default_path = os.path.join(os.path.dirname(__file__), "vad_demo_output.png")
        plt.savefig(default_path, dpi=150, bbox_inches='tight')
        print(f"[CHART] Plot saved to: {default_path}")

    plt.close()


def demo_state_transitions(audio: np.ndarray, config: VADConfig):
    """Print frame-by-frame state transitions (first 200 frames)."""
    vad = VoxaVAD(config)
    frame_size = config.frame_size

    print("\nFrame-by-frame state transitions (first 200 frames):")
    print("-" * 60)
    print(f"{'Frame':>5} {'Time':>8} {'Speech?':>8} {'State':>20}")
    print("-" * 60)

    prev_state = "SILENCE"
    frame_idx = 0

    for start in range(0, len(audio) - frame_size + 1, frame_size):
        if frame_idx >= 200:
            print(f"  ... ({len(audio) // frame_size - 200} more frames)")
            break

        frame = audio[start:start + frame_size]
        frame_bytes = frame.astype(np.int16).tobytes()

        # Peek at speech detection
        is_speech = vad._is_speech_fn(frame_bytes)
        state, segment = vad.process_frame(frame_bytes)

        # Only print on state changes or first/last frames
        time_ms = frame_idx * config.frame_ms
        if state != prev_state or frame_idx < 5 or segment is not None:
            marker = " <-- SEGMENT!" if segment is not None else ""
            marker = " <-- DISCARDED" if state == "DISCARDED" else marker
            if state != prev_state and state not in ("DISCARDED",):
                marker = f" <-- {prev_state} -> {state}" if not marker else marker
            print(f"{frame_idx:>5} {time_ms:>6}ms {'YES' if is_speech else 'no':>8} {state:>20}{marker}")

        prev_state = state if state not in ("SEGMENT_COMPLETE", "DISCARDED") else "SILENCE"
        frame_idx += 1

    print()


def run_demo(audio: np.ndarray, config: VADConfig, label: str, plot: bool = True):
    """Run a single demo scenario."""
    print(f"\n{'-' * 60}")
    print(f"  {label}")
    print(f"{'-' * 60}")
    print(f"  Audio: {len(audio)} samples ({len(audio)/16000*1000:.0f}ms)")
    print(f"  Range: [{audio.min()}, {audio.max()}]")

    vad = VoxaVAD(config)
    results = vad.process_audio_detailed(audio)

    print(f"\n  Detected segments: {len(results)}")
    for i, r in enumerate(results):
        print(f"    Segment {i+1}: {r['duration_ms']:.0f}ms "
              f"(samples {r['start_sample']}-{r['end_sample']})")

    if plot:
        save_name = label.lower().replace(" ", "_").replace(":", "").replace("—", "")[:30]
        save_path = os.path.join(os.path.dirname(__file__), f"vad_demo_{save_name}.png")
        plot_vad_results(audio, results, config, title=f"VAD: {label}", save_path=save_path)

    return results


# ══════════════════════════════════════════════════════════════
#  Main
# ══════════════════════════════════════════════════════════════

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Voxa VAD Interactive Demo")
    parser.add_argument('--audio', type=str, default=None,
                        help='Path to an audio file to analyze')
    parser.add_argument('--no-plot', action='store_true',
                        help='Skip plot generation')
    parser.add_argument('--aggressiveness', type=int, default=2,
                        help='VAD aggressiveness (0-3)')
    args = parser.parse_args()

    config = VADConfig(aggressiveness=args.aggressiveness)
    do_plot = not args.no_plot and HAS_MATPLOTLIB

    print("=" * 60)
    print("  Voxa VAD - Interactive Demo")
    print("=" * 60)
    print(f"  Backend: {'WebRTC' if True else 'Energy-based'}")
    print(f"  Aggressiveness: {config.aggressiveness}")
    print(f"  Plotting: {'ON' if do_plot else 'OFF'}")

    if args.audio:
        # ── Real audio mode ──
        print(f"\n  Loading: {args.audio}")
        audio = load_audio_file(args.audio)
        results = run_demo(audio, config, f"Real Audio: {os.path.basename(args.audio)}", plot=do_plot)
        demo_state_transitions(audio, config)

    else:
        # ── Synthetic demo mode — run multiple scenarios ──

        # Scenario 1: Clean speech in the middle
        audio1 = generate_test_audio(
            duration_ms=3000, speech_start_ms=500, speech_end_ms=2000,
            speech_amplitude=10000, noise_amplitude=150
        )
        run_demo(audio1, config, "Scenario 1 - Valid 1500ms speech", plot=do_plot)

        # Scenario 2: Very short utterance
        audio2 = generate_test_audio(
            duration_ms=2000, speech_start_ms=500, speech_end_ms=800,
            speech_amplitude=10000, noise_amplitude=150
        )
        run_demo(audio2, config, "Scenario 2 - Short 300ms (should reject)", plot=do_plot)

        # Scenario 3: Two separate speech bursts
        audio3a = generate_test_audio(
            duration_ms=5000, speech_start_ms=300, speech_end_ms=1200,
            speech_amplitude=10000, noise_amplitude=100
        )
        audio3b = generate_test_audio(
            duration_ms=5000, speech_start_ms=2500, speech_end_ms=3700,
            speech_amplitude=8000, noise_amplitude=100
        )
        # Combine: take speech from each
        audio3 = np.clip(audio3a.astype(np.int32) + audio3b.astype(np.int32), -32768, 32767).astype(np.int16)
        run_demo(audio3, config, "Scenario 3 - Two speech bursts", plot=do_plot)

        # Scenario 4: Pure silence
        audio4 = np.zeros(32000, dtype=np.int16)
        run_demo(audio4, config, "Scenario 4 - Pure silence (should reject)", plot=do_plot)

        # Scenario 5: Noisy background, no speech
        audio5 = (np.random.randn(48000) * 300).astype(np.int16)
        run_demo(audio5, config, "Scenario 5 - Noise only (should reject)", plot=do_plot)

        # Show state transitions for scenario 1
        print("\n" + "=" * 60)
        print("  State Transition Log (Scenario 1)")
        print("=" * 60)
        demo_state_transitions(audio1, config)

    print("\n[OK] Demo complete.")
    if do_plot:
        print(f"[CHART] Plots saved in: {os.path.dirname(os.path.abspath(__file__))}")
