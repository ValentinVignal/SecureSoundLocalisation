# SSL Central Unit

This is a Python Script to create the `.wav` file that the *Central Unit* should play in a speaker

For now, the produced sound is a sinus of frequence 440Hz (:musical_note:) for 0.25 seconds.
This python script creates 2 files:
- `../common/sin.wav`: File to play with the *Central Unit*
- `../common/sin.json`: The encoded sound

The goal would be to be able to open and integrate the file `sin.json` in build of the App (and not `sin.wav` because the data has been transformed to create the `.wav`) 


# Requirements

To install the requirements :
```
pip install -r requirements.txt
```