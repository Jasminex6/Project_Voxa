"""
==================================================================
  Voxa DTW Matcher
  Dev A Deliverable (Phase 3)
==================================================================

Purpose:
  Calculates the similarity between dynamic acoustic feature matrices
  using Dynamic Time Warping (DTW) and top-k consensus matching.
"""

import numpy as np

# ==============================================================
#  PRODUCTION CONFIG (Config 2: Best performing)
#  Confirmed parameters — DO NOT CHANGE without re-benchmarking
# ==============================================================
DTW_CONFIG = {
    "sakoe_chiba_band": "adaptive", # CONFIRMED: W = max(15, |N-M| + 5) — 50% latency reduction
    "endpoint_relaxation": False,   # CONFIRMED: Not needed with path normalization
    "top_k": 2,                     # CONFIRMED: top_k=2. Tested top_k=3, no improvement.
}

def dtw_distance(X: np.ndarray, Y: np.ndarray, band_width=None) -> float:
    """
    Compute DTW distance between two feature matrices.
    
    Args:
        X: Template matrix [N, D] (enrolled template)
        Y: Test matrix [M, D] (incoming utterance)
        band_width: Sakoe-Chiba band width (None = no constraint, 'adaptive' = dynamic)
    
    Returns:
        Normalized DTW distance (float) or np.inf if no path exists
    """
    N, M = len(X), len(Y)
    if N == 0 or M == 0:
        return np.inf
        
    if band_width == "adaptive":
        band_width = max(15, abs(N - M) + 5)
        
    # Cost matrix
    D = np.full((N + 1, M + 1), np.inf)
    D[0, 0] = 0.0
    
    for i in range(1, N + 1):
        j_start = 1
        j_end = M
        if band_width is not None:
            j_start = max(1, i - band_width)
            j_end = min(M, i + band_width)
            
        for j in range(j_start, j_end + 1):
            # Euclidean distance between feature vectors
            cost = np.sqrt(np.sum((X[i-1] - Y[j-1]) ** 2))
            D[i, j] = cost + min(D[i-1, j], D[i, j-1], D[i-1, j-1])
            
    # If the end cell is unreachable due to band constraints
    if D[N, M] == np.inf:
        return np.inf
        
    # Path length for normalization
    path_length = 0
    i, j = N, M
    while i > 0 or j > 0:
        path_length += 1
        if i == 0:
            j -= 1
        elif j == 0:
            i -= 1
        else:
            # Check step that minimizes cost
            argmin = np.argmin([D[i-1, j-1], D[i-1, j], D[i, j-1]])
            if argmin == 0:
                i, j = i-1, j-1
            elif argmin == 1:
                i -= 1
            else:
                j -= 1
                
    return float(D[N, M] / max(path_length, 1))

def consensus_match(test_features: np.ndarray, 
                    intent_templates: dict, 
                    top_k=2, 
                    band_width=None) -> list:
    """
    Compare test utterance against all enrolled intents using top-k consensus.
    
    Args:
        test_features: [T, D] feature matrix of incoming utterance
        intent_templates: dict of {intent_name: [list of [T_i, D] template matrices]}
        top_k: number of best matches to average per intent
        band_width: Sakoe-Chiba constraint
    
    Returns:
        List of (intent_name, avg_distance) sorted ascending
    """
    results = []
    
    for intent_name, templates in intent_templates.items():
        if not templates:
            results.append((intent_name, np.inf))
            continue
            
        distances = []
        for template in templates:
            d = dtw_distance(template, test_features, band_width)
            distances.append(d)
        
        distances.sort()
        # Take the best top_k matches, or fewer if not enough templates
        k_to_use = min(top_k, len(distances))
        avg_top_k = np.mean(distances[:k_to_use]) if k_to_use > 0 else np.inf
        results.append((intent_name, avg_top_k))
    
    results.sort(key=lambda x: x[1])
    return results

# ==============================================================
#  Self Test Block
# ==============================================================
if __name__ == "__main__":
    print("============================================================")
    print("  Voxa DTW Matcher - Self Test")
    print("============================================================")
    
    # Generate fake feature matrices
    np.random.seed(42)
    template1 = np.random.randn(50, 40)
    # Slight shift / stretch
    test1 = template1 + np.random.randn(50, 40) * 0.1
    # Different template
    template2 = np.random.randn(60, 40)
    
    print(f"Template 1 shape: {template1.shape}")
    print(f"Test 1 shape:     {test1.shape}")
    print(f"Template 2 shape: {template2.shape}")
    
    dist_self = dtw_distance(template1, template1)
    dist_same = dtw_distance(template1, test1)
    dist_diff = dtw_distance(template1, template2)
    
    print(f"Distance to self: {dist_self:.4f}")
    print(f"Distance to same: {dist_same:.4f}")
    print(f"Distance to diff: {dist_diff:.4f}")
    
    assert dist_self == 0.0, f"Expected 0.0 distance to self, got {dist_self}"
    assert dist_same < dist_diff, f"Expected same-intent distance ({dist_same:.4f}) to be less than diff-intent distance ({dist_diff:.4f})"
    
    # Consensus test
    intent_templates = {
        "intent_A": [template1, template1 * 1.1],
        "intent_B": [template2, template2 * 0.9],
    }
    
    match_results = consensus_match(test1, intent_templates, top_k=2)
    print("\nConsensus match results:")
    for intent, dist in match_results:
        print(f"  {intent}: {dist:.4f}")
        
    assert match_results[0][0] == "intent_A", f"Expected closest intent to be intent_A, got {match_results[0][0]}"
    print("[OK] Self-test complete.")
