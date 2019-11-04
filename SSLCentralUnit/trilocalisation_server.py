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


def emit_and_record(sock, emission_offset, speakers):
    """
    Given a socket and an emission offset tell the device to start recording, emit the signal and gather the device's recording
    Return the recorded signal

    speakers = [(x,y,sock,signal),..]
    """
    time_now = int(time.time() * 1000)
    offset_recording = 2000
    start_recording = time_now + offset_recording
    data = {
        "start": start_recording,
        "duration": emission_offset + int(SIGNAL_DURATION * 1000) + 500,
    }
    sock.send(json.dumps(data))
    print(f"envoyé : {data}")
    for _, _, s_sock in speakers:
        tell_speaker_emit(speaker_socks, start_recording + emission_offset, signal)
    time.sleep((start_recording + emission_offset) / 1000 - time.time())
    play_sound()
    data = b""
    while True:
        part = sock.recv(990)  # 990=length android bluetooth message
        data += part
        if len(part) < 990:
            break
    json_data = data.decode("utf8")
    signal_record = json.loads(json_data)["data"]
    signal_record = [int(v) for v in signal_record]
    return signal_record


def get_time(signal_record, signal_emit, emission_offset):
    """
    Given the recorded and emited signal and the emission offset, return the travel time
    """
    time_receive = np.argmax(
        np.abs(np.convolve(signal_record, np.flip(signal_emit), mode="valid"))
    )
    return time_receive / SAMPLE_FREQUENCY  # - emission_offset / 1000 - SIGNAL_DURATION


def simulate_record(duration, emission_offset, distance, signal_emit):
    """
    Return an ideal recording
    """
    record = np.zeros(shape=(int(duration / 1000 * SAMPLE_FREQUENCY),))
    start = int((emission_offset / 1000 + distance / SOUND_CELERITY) * SAMPLE_FREQUENCY)
    record[start : start + int(SIGNAL_DURATION * SAMPLE_FREQUENCY)] = signal_emit
    return record


def set_speaker(sock, x, y):
    data = {"x": x, "y": y}
    sock.send(json.dumps(data))


def tell_speaker_emit(sock, start, signal):
    data = {"start": start, "sound": signal}
    sock.send(json.dumps(data))


def write_signal(signal):
    """
    Create the .wav file corresponding to the signal
    """
    common_path = Path("../common")
    wav.write(
        common_path / "signal.wav",
        rate=SAMPLE_FREQUENCY,
        data=np.asarray(signal, dtype=np.int16),
    )


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
    if SIMULATION:
        emission_offset = 500 + np.random.randint(500)
        record = simulate_record(emission_offset + 500, emission_offset, 0.5, signal)
        t1 = get_time(record, signal, emission_offset)
        d1 = t1 * SOUND_CELERITY
        response = {"accepted": False}
        if d1 < 1:
            response["accepted"] = True
        print(
            "test : {}\noffset: {}\ntime: {}\ndistance: {}".format(
                "ACCEPTED" if response["accepted"] else "REFUSED",
                emission_offset,
                t1,
                d1,
            )
        )
    else:
        cu_signal = get_signal_json()
        write_signal(cu_signal)
        sources = [Point(0, 0)]
        speakers = []
        # wait for 2 speakers
        for i in range(2):
            print("Waiting for speaker connection on RFCOMM channel %d" % port)
            client_sock, client_info = server_sock.accept()
            print(f"Accepted connection from speaker {i+1} ", client_info)
            try:
                x, y = np.random.random(2) * 3 - 1.5  # in a 3x3 square centred on CU
                set_speaker(client_sock, x, y)
                print(f"speaker ({x}, {y})")
                signal = get_signal_json()
                speakers.append((x, y, client_sock, signal))
                sources.append(Point(x, y))
            except IOError as e:
                print(e)
        # ready to localise
        while True:
            print("Waiting for device connection on RFCOMM channel %d" % port)
            client_sock, client_info = server_sock.accept()
            print("Accepted connection from ", client_info)
            try:
                emission_offset = 1000 + np.random.randint(1000)
                signal_record = emit_and_record(client_sock, emission_offset)
                time_offset = [get_time(signal_record, cu_signal, emission_offset)]
                for _, _, _, signal in speakers:
                    time_offset.append(get_time(signal_record, signal, emission_offset))
                coord_comp = get_coordinates(offsets_latency, sources)
                response = {"accepted": False}
                if coord_comp.norm2 < 1:
                    response["accepted"] = True
                bytes_send = client_sock.send(json.dumps(response))
                print(f"send : {bytes_send} bytes")
                print(
                    "{} : {}\noffset: {}\nx: {}\ny: {}".format(
                        client_info,
                        "ACCEPTED" if response["accepted"] else "REFUSED",
                        emission_offset,
                        coord_comp.x,
                        coord_comp.y,
                    )
                )
            except IOError as e:
                print(e)

        server_sock.close()
