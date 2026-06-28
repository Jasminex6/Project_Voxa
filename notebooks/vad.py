"""
══════════════════════════════════════════════════════════════════
  Voxa VAD — WebRTC Voice Activity Detection State Machine
  Dev A Deliverable (Phase 1)
══════════════════════════════════════════════════════════════════

Purpose:
  First filter in the Voxa pipeline. Segments raw 16kHz PCM into
  valid speech chunks (400ms–2000ms), filtering silence, breaths,
  and background noise.

State Machine:
  SILENCE ──[M speech frames]──▶ SPEECH_TRIGGERED
  SPEECH_TRIGGERED ──▶ SPEECH_COLLECTING
  SPEECH_COLLECTING ──[N silent frames]──▶ SEGMENT_COMPLETE
  SEGMENT_COMPLETE ──[length OK]──▶ PASS
                   ──[length bad]──▶ DISCARD → SILENCE

Usage:
  from vad import VoxaVAD
  vad = VoxaVAD()
  segments = vad.process_audio(pcm_int16_array)

Dependencies:
  pip install webrtcvad numpy
"""

import numpy as np
from dataclasses import dataclass, field
from typing import List, Optional, Tuple
import struct

# ── Try importing webrtcvad; provide fallback info if missing ──
try:
    import webrtcvad
    HAS_WEBRTCVAD = True
except ImportError:
    HAS_WEBRTCVAD = False
    print("[WARNING] webrtcvad not installed. Install with: pip install webrtcvad")
    print("          Using energy-based fallback VAD instead.")


# ══════════════════════════════════════════════════════════════
#  Configuration
# ══════════════════════════════════════════════════════════════

@dataclass
class VADConfig:
    """All VAD parameters — matches voxa_full_project_plan.md §1.1"""
    sample_rate: int = 16000
    frame_ms: int = 20               # 20ms frames (320 samples) — WebRTC requires 10/20/30ms
    aggressiveness: int = 2           # 0=least aggressive, 3=most aggressive
    speech_trigger_frames: int = 8    # M = 8 consecutive speech frames (~160ms) to trigger
    silence_boundary_frames: int = 15 # N = 15 consecutive silent frames (~300ms) to end
    min_segment_ms: int = 400         # Discard segments shorter than this
    max_segment_ms: int = 2000        # Discard segments longer than this
    energy_threshold: float = 500.0   # RMS threshold for fallback energy-based VAD

    @property
    def frame_size(self) -> int:
        """Number of samples per frame"""
        return int(self.sample_rate * self.frame_ms / 1000)

    @property
    def frame_bytes(self) -> int:
        """Number of bytes per frame (16-bit = 2 bytes/sample)"""
        return self.frame_size * 2

    @property
    def min_samples(self) -> int:
        return int(self.sample_rate * self.min_segment_ms / 1000)

    @property
    def max_samples(self) -> int:
        return int(self.sample_rate * self.max_segment_ms / 1000)


# ══════════════════════════════════════════════════════════════
#  VAD State Machine
# ══════════════════════════════════════════════════════════════

class VoxaVAD:
    """
    WebRTC-based Voice Activity Detector with state machine.

    Processes 16kHz 16-bit mono PCM audio and extracts valid speech
    segments between 400ms and 2000ms in length.

    Parameters
    ----------
    config : VADConfig
        Configuration object. Uses defaults if not provided.

    Example
    -------
    >>> vad = VoxaVAD()
    >>> segments = vad.process_audio(pcm_int16_array)
    >>> for seg in segments:
    ...     print(f"Segment: {len(seg)} samples ({len(seg)/16000*1000:.0f}ms)")
    """

    # State constants
    SILENCE = "SILENCE"
    SPEECH_TRIGGERED = "SPEECH_TRIGGERED"
    SPEECH_COLLECTING = "SPEECH_COLLECTING"
    SEGMENT_COMPLETE = "SEGMENT_COMPLETE"
    DISCARDED = "DISCARDED"

    def __init__(self, config: Optional[VADConfig] = None):
        self.config = config or VADConfig()

        # Initialize WebRTC VAD or fallback
        if HAS_WEBRTCVAD:
            self._vad = webrtcvad.Vad(self.config.aggressiveness)
            self._is_speech_fn = self._webrtc_is_speech
        else:
            self._vad = None
            self._is_speech_fn = self._energy_is_speech

        self.reset()

    def reset(self):
        """Reset state machine to initial state."""
        self.state = self.SILENCE
        self._speech_frame_count = 0
        self._silence_frame_count = 0
        self._segment_buffer: List[np.ndarray] = []
        self._segment_sample_count = 0

    # ── Speech detection backends ──

    def _webrtc_is_speech(self, frame_bytes: bytes) -> bool:
        """Use WebRTC VAD to detect speech in a single frame."""
        return self._vad.is_speech(frame_bytes, self.config.sample_rate)

    def _energy_is_speech(self, frame_bytes: bytes) -> bool:
        """Fallback: simple RMS energy threshold."""
        samples = np.frombuffer(frame_bytes, dtype=np.int16).astype(np.float64)
        rms = np.sqrt(np.mean(samples ** 2))
        return rms > self.config.energy_threshold

    # ── Core processing ──

    def process_frame(self, frame_bytes: bytes) -> Tuple[str, Optional[np.ndarray]]:
        """
        Process a single audio frame through the state machine.

        Parameters
        ----------
        frame_bytes : bytes
            Raw PCM bytes for one frame (frame_ms of 16-bit samples).
            For 20ms at 16kHz: 640 bytes (320 samples × 2 bytes).

        Returns
        -------
        (state, segment_or_None)
            state: current state name after processing this frame
            segment: numpy int16 array if a valid segment was completed, else None
        """
        is_speech = self._is_speech_fn(frame_bytes)
        frame_samples = np.frombuffer(frame_bytes, dtype=np.int16).copy()
        frame_len = len(frame_samples)

        if self.state == self.SILENCE:
            if is_speech:
                self._speech_frame_count += 1
                self._segment_buffer.append(frame_samples)
                self._segment_sample_count += frame_len
                if self._speech_frame_count >= self.config.speech_trigger_frames:
                    self.state = self.SPEECH_COLLECTING
                    self._silence_frame_count = 0
            else:
                # Reset partial trigger
                self._speech_frame_count = 0
                self._segment_buffer = []
                self._segment_sample_count = 0

        elif self.state == self.SPEECH_COLLECTING:
            self._segment_buffer.append(frame_samples)
            self._segment_sample_count += frame_len

            if not is_speech:
                self._silence_frame_count += 1
                if self._silence_frame_count >= self.config.silence_boundary_frames:
                    # Segment ended — validate length
                    segment = np.concatenate(self._segment_buffer)
                    self.reset()

                    if self.config.min_samples <= len(segment) <= self.config.max_samples:
                        return (self.SEGMENT_COMPLETE, segment)
                    else:
                        return (self.DISCARDED, None)
            else:
                self._silence_frame_count = 0

            # Safety: discard if segment is growing too long
            if self._segment_sample_count > self.config.max_samples:
                self.reset()
                return (self.DISCARDED, None)

        return (self.state, None)

    def process_audio(self, pcm_int16: np.ndarray) -> List[np.ndarray]:
        """
        Process a complete audio array through the VAD.

        Parameters
        ----------
        pcm_int16 : np.ndarray
            Full audio signal as int16 samples at 16kHz.

        Returns
        -------
        List of valid speech segments as int16 numpy arrays.
        """
        self.reset()
        segments = []

        frame_size = self.config.frame_size
        total_samples = len(pcm_int16)

        for start in range(0, total_samples - frame_size + 1, frame_size):
            frame = pcm_int16[start:start + frame_size]
            frame_bytes = frame.astype(np.int16).tobytes()

            state, segment = self.process_frame(frame_bytes)
            if segment is not None:
                segments.append(segment)

        return segments

    def process_audio_detailed(self, pcm_int16: np.ndarray) -> List[dict]:
        """
        Process audio and return detailed info about each detected segment.

        Returns list of dicts with:
          - 'segment': int16 numpy array
          - 'start_sample': start index in original audio
          - 'end_sample': end index in original audio
          - 'duration_ms': duration in milliseconds
        """
        self.reset()
        results = []

        frame_size = self.config.frame_size
        total_samples = len(pcm_int16)
        current_segment_start = 0

        for start in range(0, total_samples - frame_size + 1, frame_size):
            frame = pcm_int16[start:start + frame_size]
            frame_bytes = frame.astype(np.int16).tobytes()

            # Track when we enter speech
            prev_state = self.state
            state, segment = self.process_frame(frame_bytes)

            if prev_state == self.SILENCE and state in (self.SPEECH_COLLECTING, self.SILENCE):
                if state == self.SPEECH_COLLECTING:
                    # We just transitioned — segment started M frames ago
                    current_segment_start = max(0, start - (self.config.speech_trigger_frames - 1) * frame_size)

            if segment is not None:
                duration_ms = len(segment) / self.config.sample_rate * 1000
                results.append({
                    'segment': segment,
                    'start_sample': current_segment_start,
                    'end_sample': current_segment_start + len(segment),
                    'duration_ms': duration_ms,
                })

        return results


# ══════════════════════════════════════════════════════════════
#  Utility functions
# ══════════════════════════════════════════════════════════════

def load_audio_file(filepath: str, target_sr: int = 16000) -> np.ndarray:
    """
    Load an audio file and return as int16 numpy array at target sample rate.

    Supports .wav, .pcm, and any format soundfile supports.
    """
    if filepath.endswith('.pcm'):
        # Raw PCM — assume 16kHz 16-bit mono
        audio = np.fromfile(filepath, dtype=np.int16)
        return audio

    try:
        import soundfile as sf
        audio, sr = sf.read(filepath, dtype='int16')
        if sr != target_sr:
            # Simple resampling (for production, use librosa or scipy)
            import scipy.signal
            num_samples = int(len(audio) * target_sr / sr)
            audio = scipy.signal.resample(audio, num_samples).astype(np.int16)
        return audio
    except ImportError:
        # Fallback: try wave module (wav only)
        import wave
        with wave.open(filepath, 'rb') as wf:
            assert wf.getsampwidth() == 2, "Expected 16-bit audio"
            assert wf.getnchannels() == 1, "Expected mono audio"
            raw = wf.readframes(wf.getnframes())
            audio = np.frombuffer(raw, dtype=np.int16)
            sr = wf.getframerate()
            if sr != target_sr:
                raise ValueError(f"Sample rate {sr} != {target_sr}. Install soundfile for resampling.")
            return audio


def generate_test_audio(duration_ms: int = 3000, 
                        speech_start_ms: int = 500, 
                        speech_end_ms: int = 2000,
                        sample_rate: int = 16000,
                        speech_freq: float = 300.0,
                        speech_amplitude: int = 8000,
                        noise_amplitude: int = 200) -> np.ndarray:
    """
    Generate synthetic test audio with a speech-like segment embedded in silence/noise.

    Parameters
    ----------
    duration_ms : total audio length in ms
    speech_start_ms : where the speech segment begins
    speech_end_ms : where the speech segment ends
    speech_freq : frequency of the synthetic speech tone (Hz)
    speech_amplitude : peak amplitude of speech
    noise_amplitude : peak amplitude of background noise

    Returns
    -------
    int16 numpy array
    """
    n_samples = int(sample_rate * duration_ms / 1000)
    t = np.arange(n_samples) / sample_rate

    # Background noise
    audio = (np.random.randn(n_samples) * noise_amplitude).astype(np.float64)

    # Speech-like signal (simple tone with harmonics + amplitude modulation)
    speech_start = int(sample_rate * speech_start_ms / 1000)
    speech_end = int(sample_rate * speech_end_ms / 1000)
    speech_len = speech_end - speech_start
    speech_t = np.arange(speech_len) / sample_rate

    # Fundamental + harmonics
    speech = (
        np.sin(2 * np.pi * speech_freq * speech_t) * 1.0 +
        np.sin(2 * np.pi * speech_freq * 2 * speech_t) * 0.5 +
        np.sin(2 * np.pi * speech_freq * 3 * speech_t) * 0.25
    )
    # Amplitude modulation (makes it more speech-like)
    envelope = np.sin(np.pi * np.arange(speech_len) / speech_len) ** 0.3
    speech = speech * envelope * speech_amplitude

    audio[speech_start:speech_end] += speech

    return np.clip(audio, -32768, 32767).astype(np.int16)


# ══════════════════════════════════════════════════════════════
#  Quick self-test
# ══════════════════════════════════════════════════════════════

if __name__ == "__main__":
    print("=" * 60)
    print("  Voxa VAD — Self Test")
    print("=" * 60)
    print()

    config = VADConfig()
    print(f"Configuration:")
    print(f"  Sample rate:      {config.sample_rate} Hz")
    print(f"  Frame size:       {config.frame_ms}ms ({config.frame_size} samples)")
    print(f"  Aggressiveness:   {config.aggressiveness}")
    print(f"  Speech trigger:   {config.speech_trigger_frames} frames ({config.speech_trigger_frames * config.frame_ms}ms)")
    print(f"  Silence boundary: {config.silence_boundary_frames} frames ({config.silence_boundary_frames * config.frame_ms}ms)")
    print(f"  Min segment:      {config.min_segment_ms}ms ({config.min_samples} samples)")
    print(f"  Max segment:      {config.max_samples}ms ({config.max_samples} samples)")
    print(f"  Backend:          {'WebRTC' if HAS_WEBRTCVAD else 'Energy-based fallback'}")
    print()

    # Generate test audio: 3 seconds, speech from 500ms to 2000ms
    print("Generating synthetic test audio (3s, speech 500-2000ms)...")
    test_audio = generate_test_audio(
        duration_ms=3000,
        speech_start_ms=500,
        speech_end_ms=2000,
        speech_amplitude=10000,
        noise_amplitude=150,
    )
    print(f"  Audio length: {len(test_audio)} samples ({len(test_audio)/16000*1000:.0f}ms)")
    print(f"  Audio range:  [{test_audio.min()}, {test_audio.max()}]")
    print()

    # Run VAD
    vad = VoxaVAD(config)
    results = vad.process_audio_detailed(test_audio)
    print(f"Detected segments: {len(results)}")
    for i, r in enumerate(results):
        print(f"  Segment {i+1}: {r['duration_ms']:.0f}ms "
              f"(samples {r['start_sample']}–{r['end_sample']})")
    print()

    # Test edge cases
    print("Edge case tests:")

    # Too short
    short_audio = generate_test_audio(duration_ms=1000, speech_start_ms=200, speech_end_ms=500)
    vad2 = VoxaVAD(config)
    short_results = vad2.process_audio(short_audio)
    print(f"  Short speech (300ms): {len(short_results)} segments detected "
          f"({'PASS: correctly rejected' if len(short_results) == 0 else 'FAIL: should reject'})")

    # Silence only
    silence = np.zeros(32000, dtype=np.int16)
    vad3 = VoxaVAD(config)
    silence_results = vad3.process_audio(silence)
    print(f"  Pure silence: {len(silence_results)} segments detected "
          f"({'PASS: correctly rejected' if len(silence_results) == 0 else 'FAIL: should reject'})")

    # Valid speech
    valid_audio = generate_test_audio(duration_ms=3000, speech_start_ms=300, speech_end_ms=1500)
    vad4 = VoxaVAD(config)
    valid_results = vad4.process_audio(valid_audio)
    print(f"  Valid speech (1200ms): {len(valid_results)} segments detected "
          f"({'PASS: correctly detected' if len(valid_results) >= 1 else 'FAIL: should detect'})")

    print()
    print("[OK] Self-test complete.")
