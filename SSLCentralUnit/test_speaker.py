"""wait for a speaker to connect and send it a sound to play"""
import bluetooth as bt
import numpy as np
import json
from pathlib import Path
import scipy.io.wavfile as wav
import time
import sys
from localise_hyperbole import *

if sys.platform.startswith("win"):
    import winsound

    def play_sound(path):
        winsound.PlaySound(path, winsound.SND_FILENAME)


elif sys.platform.startswith("linux"):
    from playsound import playsound

    def play_sound(path):
        playsound(path)


SIGNAL_DURATION = 0.25  # s
SAMPLE_FREQUENCY = 8000  # Hz
SOUND_CELERITY = 340.29  # m/s
SIMULATION = False


def get_signal_json():
    """
    Create the the signal
    """
    rate = SAMPLE_FREQUENCY  # Hertz         Encodage for wav file
    f = np.random.randint(220, 880)  # Hertz         Frequence of the sinus
    duration = SIGNAL_DURATION  # second    Duration of the sinus
    nb_points = rate * duration
    time = np.arange(nb_points) / rate
    sin = np.sin(time * 2 * np.pi * f)
    sin_16bits = np.ceil(sin * 32767)  # To code in format 16-bitPCM
    return [int(x) for x in sin_16bits]


def set_speaker(sock, x, y):
    data = {"x": x, "y": y}
    sock.send(json.dumps(data))


def tell_speaker_emit(sock, start, signal):
    data = {"start": start, "sound": signal}
    sock.send(json.dumps(data))


if __name__ == "__main__":
    server_sock = bt.BluetoothSocket(bt.RFCOMM)
    server_sock.bind(("", bt.PORT_ANY))
    server_sock.listen(1)
    port = server_sock.getsockname()[1]
    uuid = "ae465fd9-2d3b-a4c6-4385-ea69b4c1e23c"
    bt.advertise_service(
        server_sock,
        "LocalisationServer",
        service_id=uuid,
        service_classes=[uuid, bt.SERIAL_PORT_CLASS],
        profiles=[bt.SERIAL_PORT_PROFILE],
    )
    # wait for speakers for ever
    while True:
        print("Waiting for speaker connection on RFCOMM channel %d" % port)
        client_sock, client_info = server_sock.accept()
        print(f"Accepted connection from speaker {i+1} ", client_info)
        try:
            x, y = np.random.random(2) * 3 - 1.5  # in a 3x3 square centred on CU
            byte_send = set_speaker(client_sock, x, y)
            print(f"told speaker to go to ({x}, {y}), bytes send : {byte_send}")
            signal = get_signal_json()
            time.sleep(5)
            byte_send = tell_speaker_emit(
                client_sock, int(time.time() * 1000) + 3000, signal
            )
            print(f"told speaker to emit the sound in 3 sec, bytes send : {byte_send}")
        except IOError as e:
            print(e)
    server_sock.close()
