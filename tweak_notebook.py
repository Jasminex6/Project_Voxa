import json

file_path = 'D:/programming/Ai-Nexus/Basma/Project_Voxa/notebooks/voxa_v4_prototypical_arch.ipynb'
with open(file_path, 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('ood_threshold = max(0.82, mean_sim - 2 * std_sim)', 'ood_threshold = max(0.82, mean_sim - 1 * std_sim)')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(text)

print('Notebook updated successfully.')
