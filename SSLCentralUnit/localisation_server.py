import bluetooth as bt
import numpy as np
import json
from pathlib import Path
import scipy.io.wavfile as wav
import time
from playsound import playsound

SIGNAL_DURATION = 0.25  # s
SAMPLE_FREQUENCY = 8000  # Hz
SOUND_CELERITY = 340.29  # m/s
SIMULATION = True


def create_signal():
    """
    Create the .wav file corresponding to the signal
    """
    common_path = Path("../common")
    rate = SAMPLE_FREQUENCY  # Hertz         Encodage for wav file
    f = 440  # Hertz         Frequence of the sinus
    duration = SIGNAL_DURATION  # second    Duration of the sinus
    nb_points = rate * duration
    time = np.arange(nb_points) / rate
    sin = np.sin(time * 2 * np.pi * f)
    data = {"signal": list(sin)}
    common_path.mkdir(parents=True, exist_ok=True)

    with open(common_path / "signal.json", "w") as json_file:
        json.dump(data, json_file)

    sin_16bits = np.ceil(sin * 32767).astype(np.int16)  # To code in format 16-bitPCM

    wav.write(common_path / "signal.wav", rate=rate, data=sin_16bits)


def emit_and_record(sock, emission_offset):
    """
    Given a socket and an emission offset tell the device to start recording, emit the signal and gather the device's recording
    Return the recorded signal
    """
    time_now = int(time.time() * 1000)
    offset_recording = 1000
    start_recording = time_now + offset
    data = {
        "start": start_recording,
        "duration": emission_offset + int(SIGNAL_DURATION * 1000) + 250,
    }
    sock.send(json.dumps(data))
    time.sleep(start_recording / 1000 - time.time())
    playsound(Path("../common/signal.wav"))
    excepted_bytes = data["duration"] * SAMPLE_FREQUENCY / 1000 * 10
    data = sock.recv(excepted_bytes)
    json_data = data.decode("utf8")
    print(f"data received = {json_data}")
    signal_record = json.loads(json_data)["data"]
    return signal_record


def get_time(signal_record, signal_emit, emission_offset):
    """
    Given the recorded and emited signal and the emission offset, return the travel time
    """
    time_receive = np.argmax(np.abs(np.convolve(signal_record, signal_emit)))
    return time_receive / SAMPLE_FREQUENCY - emission_offset / 1000 - SIGNAL_DURATION


def simulate_record(duration, emission_offset, distance, signal_emit):
    """
    Return an ideal recording
    """
    record = np.zeros(shape=(int(duration / 1000 * SAMPLE_FREQUENCY),))
    start = int((emission_offset / 1000 + distance / SOUND_CELERITY) * SAMPLE_FREQUENCY)
    record[start : start + int(SIGNAL_DURATION * SAMPLE_FREQUENCY)] = signal_emit
    return record


if __name__ == "__main__":
    signal_json = Path("../common/signal.json")
    # signal_wav = Path("../common/signal.wav")
    # if not (signal_json.exists() and signal_wav.exists()) :
    create_signal()
    with open(signal_json, "r") as json_file:
        signal = json.loads(json_file.read())["signal"]

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
        # protocols = [ OBEX_UUID ]
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
        while True:
            print("Waiting for connection on RFCOMM channel %d" % port)
            client_sock, client_info = server_sock.accept()
            print("Accepted connection from ", client_info)

            try:
                emission_offset = 500 + np.random.randint(500)
                signal_record = emit_and_record(client_sock, emission_offset)
                t1 = get_time(signal_record, signal, emission_offset)
                d1 = t1 * SOUND_CELERITY
                response = {"accepted": False}
                if d1 < 1:
                    response["accepted"] = True
                client_sock.send(response)
                client_sock.close()
                print(
                    "{} : {}\noffset: {}\ntime: {}\ndistance: {}".format(
                        client_info,
                        "ACCEPTED" if response["accepted"] else "REFUSED",
                        emission_offset,
                        t1,
                        d1,
                    )
                )
            except IOError as e:
                print(e)

        server_sock.close()
