import numpy as np
import json
from pathlib import Path
import scipy.io.wavfile as wav
import matplotlib.pyplot as plt

common_path = Path('../common')

rate = 8000     # Hertz         Encodage for wav file
f = 440         # Hertz         Frequence of the sinus
duration = 0.25        # second    Duration of the sinus
nb_points = rate * duration
time = np.arange(nb_points) / rate
sin = np.sin(time * 2 * np.pi * f)
data = {
    'sin': list(sin)
}
common_path.mkdir(parents=True, exist_ok=True)

with open(common_path / 'sin.json', 'w') as json_file:
    json.dump(data, json_file)

sin_16bits = np.ceil(sin * 32767).astype(np.int16)      # To code in format 16-bitPCM

# plt.plot(time, sin_16bits)
# plt.show()

wav.write(common_path / 'sin.wav', rate=rate, data=sin_16bits)

