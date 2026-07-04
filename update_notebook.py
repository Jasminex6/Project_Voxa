import json

file_path = 'D:/programming/Ai-Nexus/Basma/Project_Voxa/notebooks/voxa_v4_prototypical_arch.ipynb'
with open(file_path, 'r', encoding='utf-8') as f:
    nb = json.load(f)

for cell in nb['cells']:
    if cell['cell_type'] == 'code':
        source = cell.get('source', [])
        if not source: continue
        text = ''.join(source)
        
        # Replace enroll_intent
        if 'def enroll_intent(wav_paths):' in text:
            text = text.replace('    if not vectors:\n        return []', '    if not vectors:\n        return [], 0.82')
            
            replacement = """    centroids = bifurcate_if_needed(valid_vectors)
    
    # Calculate dynamic OOD threshold based on child's acoustic variance
    if len(valid_vectors) > 0:
        similarities = []
        for v in valid_vectors:
            max_sim = max([1.0 - cosine(v, c) for c in centroids])
            similarities.append(max_sim)
        mean_sim = np.mean(similarities)
        std_sim = np.std(similarities)
        ood_threshold = max(0.82, mean_sim - 2 * std_sim)
    else:
        ood_threshold = 0.82
        
    return centroids, ood_threshold"""
            text = text.replace('    centroids = bifurcate_if_needed(valid_vectors)\n    return centroids', replacement)
            
        # Replace PrototypicalMatcher
        if 'class PrototypicalMatcher:' in text:
            text = text.replace(
                'class PrototypicalMatcher:\n    def __init__(self, ccp_penalty=0.005, ood_threshold=0.2, margin_threshold=0.0):\n        self.enrolled_intents = {} # { intent_name: [centroid1, centroid2...] }\n        self.ccp_penalty = ccp_penalty\n        self.ood_threshold = ood_threshold\n        self.margin_threshold = margin_threshold', 
                "class PrototypicalMatcher:\n    def __init__(self, ccp_penalty=0.005, margin_threshold=0.04):\n        self.enrolled_intents = {} # { intent_name: {'centroids': [c1, c2], 'ood_threshold': 0.85} }\n        self.ccp_penalty = ccp_penalty\n        self.margin_threshold = margin_threshold"
            )
            
            text = text.replace(
                '    def add_intent(self, intent_name, centroids):\n        self.enrolled_intents[intent_name] = centroids',
                "    def add_intent(self, intent_name, centroids, ood_threshold):\n        self.enrolled_intents[intent_name] = {\n            'centroids': centroids,\n            'ood_threshold': ood_threshold\n        }"
            )
            
            text = text.replace(
                '        for intent_name, centroids in self.enrolled_intents.items():',
                "        thresholds = {}\n        for intent_name, intent_data in self.enrolled_intents.items():\n            centroids = intent_data['centroids']\n            ood_threshold = intent_data['ood_threshold']"
            )
            
            text = text.replace(
                '            scores[intent_name] = effective_sim', 
                '            scores[intent_name] = effective_sim\n            thresholds[intent_name] = ood_threshold'
            )
            
            text = text.replace(
                '        best_intent, best_score = ranked[0]\n        \n        # OOD Gate Check\n        if best_score < self.ood_threshold:\n            return "Reject_OOD", f"Best match ({best_intent}) score {best_score:.4f} is below OOD threshold {self.ood_threshold}"',
                '        best_intent, best_score = ranked[0]\n        best_threshold = thresholds[best_intent]\n        \n        # OOD Gate Check\n        if best_score < best_threshold:\n            return "Reject_OOD", f"Best match ({best_intent}) score {best_score:.4f} is below OOD threshold {best_threshold:.4f}"'
            )
            
            text = text.replace(
                '            if margin < self.margin_threshold:\n                return "Reject_Margin", f"Ambiguous match. Margin {margin:.4f} below threshold. Best: {best_intent}, Second: {second_intent}"',
                '            if margin < self.margin_threshold:\n                return "Reject_Margin", f"Ambiguous match. Margin {margin:.4f} below threshold {self.margin_threshold:.4f}. Best: {best_intent}, Second: {second_intent}"'
            )
            
        # Replace loop usage
        if 'centroids = enroll_intent(wavs)' in text:
            text = text.replace(
                'centroids = enroll_intent(wavs)\n        if centroids:\n            matcher.add_intent(name, centroids)', 
                'centroids, ood_threshold = enroll_intent(wavs)\n        if centroids:\n            matcher.add_intent(name, centroids, ood_threshold)'
            )

        # Re-split source back to list of strings with newlines
        lines = []
        split_text = text.split('\n')
        for i, line in enumerate(split_text):
            if i < len(split_text) - 1:
                lines.append(line + '\n')
            else:
                if line: lines.append(line)
        cell['source'] = lines

with open(file_path, 'w', encoding='utf-8') as f:
    json.dump(nb, f, indent=1)
print('Notebook updated successfully.')
