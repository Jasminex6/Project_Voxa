"""
==================================================================
  Voxa MFCC Feature Extractor
  Dev A Deliverable (Phase 2)
==================================================================

Purpose:
  Extracts 40-dimensional feature frames from raw 16kHz PCM audio:
    - 13 MFCC coefficients
    - 13 Delta MFCCs
    - 13 Delta-Delta MFCCs
    - 1 Log RMS Energy contour
  Followed by per-utterance CMVN normalization.
"""

import numpy as np
from scipy.fftpack import dct

# ==============================================================
#  Confirmed Configuration Parameters
# ==============================================================
# ==============================================================
#  Confirmed Configuration Parameters (with Robustness Updates)
# ==============================================================
MFCC_CONFIG = {
    # --- PRODUCTION CONFIG (Config 2: Best performing) ---
    # Confirmed parameters — DO NOT CHANGE without re-benchmarking
    "sample_rate": 16000,
    "frame_size_ms": 25,       # 400 samples
    "hop_size_ms": 10,         # 160 samples
    "n_fft": 512,
    "n_mels": 40,
    "n_mfcc": 13,
    "f_min": 100,              # Hz
    "f_max": 8000,             # Hz
    "window": "hamming",
    "pre_emphasis": 0.97,      # Pre-emphasis coefficient
    "cmvn": "per_utterance",   # CONFIRMED: per_utterance beats cmn_only by +22% TAR
    "delta_window_K": 2,       # Delta window size
    
    # AGC: CONFIRMED enabled (improves volume invariance)
    "agc": True,               # Adaptive Gain Control (RMS normalization)
    "agc_target_rms": 0.05,    # Target RMS level
    
    # Denoising: CONFIRMED disabled (Wiener filter hurts TAR by -15-20%)
    "denoise_time_domain": False,
    "denoise_spectrogram": False,
    "denoise_frames": 8,
    "denoise_alpha": 1.5,
    "denoise_beta": 0.02,
    
    # V3 Feature-Level Enhancements: CONFIRMED disabled
    # Feature warping: -5.9% clean TAR, but helps at extreme noise (5dB)
    # RASTA: -19.8% clean TAR, not useful for short utterances
    "feature_warping": False,
    "feature_warping_window": 301,
    "rasta_filter": False,
}

def apply_agc(signal: np.ndarray, target_rms=0.05, max_gain=10.0) -> np.ndarray:
    """
    Apply Adaptive Gain Control (RMS normalization) to a float signal.
    """
    rms = np.sqrt(np.mean(signal ** 2) + 1e-10)
    if rms < 1e-5:
        return signal
    scale = target_rms / rms
    scale = min(scale, max_gain)
    scaled_signal = signal * scale
    return np.clip(scaled_signal, -1.0, 1.0)

def wiener_filter(pcm_int16: np.ndarray, sr=16000, n_fft=512, hop_len=128, noise_frames=8) -> np.ndarray:
    """
    Apply a time-domain Wiener filter denoising using overlap-add (OLA) reconstruction.
    
    Args:
        pcm_int16: raw 16-bit PCM array
        sr: sample rate (16kHz)
        n_fft: FFT size (512)
        hop_len: overlap-add hop size (128 for 75% overlap)
        noise_frames: initial frames to estimate noise floor
        
    Returns:
        denoised_pcm: denoised raw 16-bit PCM array
    """
    if len(pcm_int16) < n_fft:
        return pcm_int16
        
    signal = pcm_int16.astype(np.float64) / 32768.0
    
    frame_len = n_fft
    n_frames = 1 + (len(signal) - frame_len) // hop_len
    if n_frames <= 0:
        return pcm_int16
        
    # Manual Hann window to avoid deprecation warnings in NumPy
    window = 0.5 * (1.0 - np.cos(2.0 * np.pi * np.arange(frame_len) / (frame_len - 1)))
    
    frames = np.zeros((n_frames, frame_len))
    for i in range(n_frames):
        start = i * hop_len
        frames[i] = signal[start:start + frame_len] * window
        
    spec = np.fft.rfft(frames, n=n_fft)
    power = np.abs(spec) ** 2
    
    # Estimate noise floor from initial frames
    noise_est = np.mean(power[:min(noise_frames, n_frames)], axis=0)
    noise_est = np.maximum(noise_est, 1e-10)
    
    # Compute Wiener filter gain: G = (P_noisy - P_noise) / P_noisy
    beta = 0.02  # spectral floor
    gain = (power - noise_est) / (power + 1e-10)
    gain = np.clip(gain, beta, 1.0)
    
    # Apply gain
    clean_spec = spec * gain
    
    # Reconstruct frames via IFFT
    clean_frames = np.fft.irfft(clean_spec, n=n_fft)
    
    # Overlap-add
    recon_len = (n_frames - 1) * hop_len + frame_len
    recon_signal = np.zeros(recon_len)
    window_sum = np.zeros(recon_len)
    
    for i in range(n_frames):
        start = i * hop_len
        recon_signal[start:start + frame_len] += clean_frames[i]
        window_sum[start:start + frame_len] += window
        
    recon_signal = recon_signal / (window_sum + 1e-10)
    
    # Adjust size to match the original pcm_int16 length
    if len(recon_signal) < len(pcm_int16):
        padded = np.zeros(len(pcm_int16))
        padded[:len(recon_signal)] = recon_signal
        recon_signal = padded
    elif len(recon_signal) > len(pcm_int16):
        recon_signal = recon_signal[:len(pcm_int16)]
        
    recon_signal_int16 = np.clip(recon_signal * 32768.0, -32768, 32767)
    return recon_signal_int16.astype(np.int16)

def pre_emphasis(signal, coeff=0.97):
    """Apply pre-emphasis filter: y[n] = x[n] - coeff * x[n-1]"""
    return np.append(signal[0], signal[1:] - coeff * signal[:-1])

def mel_to_hz(mel):
    """Convert Mel scale to Hz"""
    return 700 * (10**(mel / 2595.0) - 1)

def hz_to_mel(hz):
    """Convert Hz to Mel scale"""
    return 2595 * np.log10(1 + hz / 700.0)

def create_mel_filterbank(n_filters, n_fft, sample_rate, f_min, f_max):
    """Create triangular Mel filterbank."""
    mel_min = hz_to_mel(f_min)
    mel_max = hz_to_mel(f_max)
    mel_points = np.linspace(mel_min, mel_max, n_filters + 2)
    hz_points = mel_to_hz(mel_points)
    bins = np.floor((n_fft + 1) * hz_points / sample_rate).astype(int)
    
    filterbank = np.zeros((n_filters, n_fft // 2 + 1))
    for i in range(n_filters):
        for j in range(bins[i], bins[i+1]):
            filterbank[i, j] = (j - bins[i]) / (bins[i+1] - bins[i])
        for j in range(bins[i+1], bins[i+2]):
            filterbank[i, j] = (bins[i+2] - j) / (bins[i+2] - bins[i+1])
    
    return filterbank

def compute_deltas(features, K=2):
    """Compute delta features using dynamic regression formula."""
    T, D = features.shape
    deltas = np.zeros_like(features)
    denominator = 2 * sum(n**2 for n in range(1, K + 1))
    
    for t in range(T):
        for n in range(1, K + 1):
            t_plus = min(t + n, T - 1)
            t_minus = max(t - n, 0)
            deltas[t] += n * (features[t_plus] - features[t_minus])
        deltas[t] /= denominator
    
    return deltas

def feature_warping(features: np.ndarray, window: int = 301) -> np.ndarray:
    """
    Apply feature warping for noise-robust normalization.
    
    Maps each feature value to a standard normal distribution based on its
    rank within a sliding window. This is widely used in speaker verification
    to handle additive noise and channel mismatch without destroying speaker
    characteristics (unlike variance normalization).
    
    Args:
        features: [T, D] feature matrix
        window: sliding window size (odd number, default 301 = ~3s at 10ms hop)
    
    Returns:
        warped: [T, D] feature matrix with warped values
    """
    T, D = features.shape
    if T < 3:
        return features
    
    warped = np.zeros_like(features)
    half_w = window // 2
    
    # Pre-compute inverse normal CDF values for common window sizes
    # Using a rational approximation instead of scipy.stats.norm.ppf
    def inv_normal_cdf(p):
        """Rational approximation of the inverse normal CDF (Beasley-Springer-Moro)."""
        p = np.clip(p, 1e-6, 1.0 - 1e-6)
        t = np.where(p < 0.5, p, 1.0 - p)
        t = np.sqrt(-2.0 * np.log(t))
        # Coefficients for rational approximation
        c0, c1, c2 = 2.515517, 0.802853, 0.010328
        d1, d2, d3 = 1.432788, 0.189269, 0.001308
        result = t - (c0 + c1 * t + c2 * t * t) / (1.0 + d1 * t + d2 * t * t + d3 * t * t * t)
        return np.where(p < 0.5, -result, result)
    
    for d in range(D):
        col = features[:, d]
        for t in range(T):
            start = max(0, t - half_w)
            end = min(T, t + half_w + 1)
            local = col[start:end]
            n_local = len(local)
            # Rank of current value in the local window
            rank = np.sum(local < col[t]) + 0.5 * np.sum(local == col[t])
            quantile = (rank + 0.5) / n_local
            warped[t, d] = inv_normal_cdf(quantile)
    
    return warped

def rasta_filter(features: np.ndarray) -> np.ndarray:
    """
    Apply RASTA (RelAtive SpecTrAl) bandpass filter on feature trajectories.
    
    Filters each feature dimension across time to suppress both very slow
    variations (channel/noise) and very fast variations (frame-level noise),
    keeping only speech-rate modulations (2-15 Hz).
    
    Transfer function: H(z) = 0.1 * z^4 * (2 + z^-1 - z^-3 - 2*z^-4) / (1 - 0.98*z^-1)
    
    Args:
        features: [T, D] feature matrix
    
    Returns:
        filtered: [T, D] RASTA-filtered feature matrix
    """
    T, D = features.shape
    if T < 5:
        return features
    
    # FIR numerator coefficients: [0.2, 0.1, 0, -0.1, -0.2]
    # These approximate the derivative-like bandpass
    numer = np.array([0.2, 0.1, 0.0, -0.1, -0.2])
    # IIR denominator: [1, -0.98]
    pole = 0.98
    
    filtered = np.zeros_like(features)
    
    for d in range(D):
        x = features[:, d]
        
        # Apply FIR part (convolution with numer)
        fir_out = np.convolve(x, numer, mode='same')
        
        # Apply IIR part (y[n] = fir_out[n] + pole * y[n-1])
        y = np.zeros(T)
        y[0] = fir_out[0]
        for t in range(1, T):
            y[t] = fir_out[t] + pole * y[t - 1]
        
        filtered[:, d] = y
    
    return filtered

def extract_mfcc(pcm_int16: np.ndarray, config=None) -> np.ndarray:
    """
    Extract 40-dimensional MFCC + Delta + Delta-Delta + Log-Energy features
    from raw 16-bit PCM audio, with options for AGC and denoising.
    
    Args:
        pcm_int16: numpy array of int16 PCM samples at 16kHz
        config: dict of parameters (uses MFCC_CONFIG if None)
    
    Returns:
        features: numpy array of shape [T, 40]
                  (13 MFCC + 13 Delta + 13 Delta-Delta + 1 Energy)
    """
    if config is None:
        config = MFCC_CONFIG
    
    # Optional time-domain Wiener filtering
    if config.get("denoise_time_domain", False):
        pcm_int16 = wiener_filter(pcm_int16, sr=config["sample_rate"], n_fft=config["n_fft"])
        
    sr = config["sample_rate"]
    frame_len = int(sr * config["frame_size_ms"] / 1000)   # 400
    hop_len = int(sr * config["hop_size_ms"] / 1000)        # 160
    n_fft = config["n_fft"]                                  # 512
    
    # 1. Normalize signal to float [-1.0, 1.0]
    signal = pcm_int16.astype(np.float64) / 32768.0
    
    # Optional Adaptive Gain Control
    if config.get("agc", False):
        signal = apply_agc(signal, target_rms=config.get("agc_target_rms", 0.05))
    
    # 2. Pre-emphasis
    signal = pre_emphasis(signal, config["pre_emphasis"])
    
    # 3. Framing
    n_frames = 1 + (len(signal) - frame_len) // hop_len
    if n_frames <= 0:
        return np.zeros((0, 40))
        
    frames = np.zeros((n_frames, frame_len))
    for i in range(n_frames):
        start = i * hop_len
        frames[i] = signal[start:start + frame_len]
    
    # 4. Windowing (Hamming)
    if config["window"] == "hamming":
        window = np.hamming(frame_len)
        frames *= window
    
    # 5. FFT + Power spectrum
    mag = np.abs(np.fft.rfft(frames, n=n_fft))
    power = mag ** 2 / n_fft
    
    # Optional spectrogram-level spectral subtraction
    if config.get("denoise_spectrogram", False) and len(power) > config.get("denoise_frames", 8):
        noise_frames = config.get("denoise_frames", 8)
        noise_est = np.mean(power[:noise_frames], axis=0)
        alpha = config.get("denoise_alpha", 1.5)
        beta = config.get("denoise_beta", 0.02)
        power_clean = power - alpha * noise_est
        power = np.where(power_clean > beta * power, power_clean, beta * power)
    
    # 6. Mel filterbank
    filterbank = create_mel_filterbank(
        config["n_mels"], n_fft, sr, config["f_min"], config["f_max"]
    )
    mel_spec = np.dot(power, filterbank.T)
    mel_spec = np.maximum(mel_spec, 1e-10)  # Avoid log(0)
    log_mel = np.log(mel_spec)
    
    # 7. Frame energy (before DCT)
    energy = np.log(np.sum(power, axis=1) + 1e-10).reshape(-1, 1)
    
    # 8. DCT -> 13 MFCCs
    mfcc = dct(log_mel, type=2, axis=1, norm='ortho')[:, :config["n_mfcc"]]
    
    # 9. Deltas and Delta-Deltas
    delta = compute_deltas(mfcc, K=config["delta_window_K"])
    delta_delta = compute_deltas(delta, K=config["delta_window_K"])
    
    # 10. Concatenate: [13 MFCC | 13 Delta | 13 Delta-Delta | 1 Energy] = 40 features
    features = np.hstack([mfcc, delta, delta_delta, energy])
    
    # 11. Normalization
    cmvn_mode = config.get("cmvn", "per_utterance")
    if cmvn_mode == "per_utterance":
        mean = np.mean(features, axis=0)
        std = np.std(features, axis=0)
        features = (features - mean) / (std + 1e-10)
    elif cmvn_mode == "cmn_only":
        # CMN: subtract mean only, preserve variance (keeps speaker identity)
        mean = np.mean(features, axis=0)
        features = features - mean
    
    # 12. Optional Feature Warping (applied after normalization)
    if config.get("feature_warping", False):
        warp_window = config.get("feature_warping_window", 301)
        features = feature_warping(features, window=warp_window)
    
    # 13. Optional RASTA Filtering (applied after normalization)
    if config.get("rasta_filter", False):
        features = rasta_filter(features)
    
    return features

# ==============================================================
#  Self Test Block
# ==============================================================
if __name__ == "__main__":
    print("============================================================")
    print("  Voxa MFCC Extractor - Self Test")
    print("============================================================")
    
    # Generate 1 second of synthetic audio (16000 samples)
    # Sine wave of 440 Hz + noise
    sr = 16000
    t = np.arange(sr) / sr
    audio = (0.5 * np.sin(2 * np.pi * 440 * t) + 0.1 * np.random.randn(sr)) * 32768
    audio = audio.astype(np.int16)
    
    print(f"Test audio duration: {len(audio)/sr:.1f}s ({len(audio)} samples)")
    print(f"Test audio range: [{audio.min()}, {audio.max()}]")
    
    # Extract features
    features = extract_mfcc(audio)
    
    print(f"Features shape: {features.shape}")
    print(f"Features range: [{features.min():.4f}, {features.max():.4f}]")
    print(f"Features mean (first 5 dims): {np.mean(features[:, :5], axis=0)}")
    print(f"Features std (first 5 dims): {np.std(features[:, :5], axis=0)}")
    
    # Verify dimensions
    assert features.shape[1] == 40, f"Expected 40 features, got {features.shape[1]}"
    print("[OK] Self-test complete.")
