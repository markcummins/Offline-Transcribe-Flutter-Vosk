# vosk

## Offline-Transcribe-Flutter-Vosk

Offline-Transcribe-Flutter-Vosk is a basic app that uses (Flutter)[https://flutter.dev/] and the (Vosk Speech to Text and Speaker Detection)[https://alphacephei.com/vosk/] models to detect speakers and automatically transcribe audio.

The purpose of the app was to see if it was possible to emulate the (Google Pixel Recorder)[https://recorder.google.com/about] app, which is only available on Pixel Phones.

The app is not very accurate in it's text or speaker detection, as the Vosk model is not accurate enough. If it had been more accurate, it would have been interesting to use the app to generate meeting summaries, and record audio. It would be possible to use other online methods to convert a recording to text, however the real-time offline transcription would not have been possible using those services. It would also have been more secure for transcription, as it is done on-device.

The main magic happens via the MainActivity.kt file, which handles the speaker recognition and transcription. There is a similar package on flutter called (vosk_flutter)[https://pub.dev/packages/vosk_flutter], however this does not handle speaker detection, which is something I wanted to try with this app.

## Installation

- To install the Flutter app, follow these steps:
- Make sure you have Flutter installed on your machine. If not, you can download and install it from the official Flutter website: https://flutter.dev/.
- Clone the repository containing the Flutter app to your local machine:
- Open a terminal and navigate to the project directory:
- Run the following command to fetch the dependencies:
- Connect your Android device or start an Android emulator.
- Run the app on your device or emulator using the following command:
- This will build and install the app on your device or emulator. Make sure you have USB debugging enabled on your device or emulator.
- Once the app is installed, you can launch it from your device's app drawer or by running the flutter run command again.
- That's it! You have successfully installed the Flutter app.