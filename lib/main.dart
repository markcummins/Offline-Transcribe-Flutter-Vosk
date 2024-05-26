import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const VoskApp());
}

class TranscriptionItem {
  String text;
  String speaker;
  String time;

  TranscriptionItem(this.text, this.speaker, this.time);
}

class VoskApp extends StatefulWidget {
  const VoskApp({super.key});

  @override
  VoskAppState createState() => VoskAppState();
}

class VoskAppState extends State<VoskApp> {
  static const MethodChannel _methodChannel =
      MethodChannel('com.example.speaker_recognition/recognize');
  static const EventChannel _eventChannel =
      EventChannel('com.example.speaker_recognition/events');

  String _errorMsg = '';
  String _partialText = 'Partial result will appear here';
  final List<TranscriptionItem> _finalTexts = [];
  bool _isListening = false;
  final ScrollController _scrollController = ScrollController();

  final Stopwatch _stopwatch = Stopwatch();
  late Timer _timer;
  String _elapsedTime = '';

  @override
  void initState() {
    super.initState();
    _eventChannel.receiveBroadcastStream().listen(_onEvent, onError: _onError);
  }

  void _onEvent(dynamic event) {
    setState(() {
      if (event['type'] == 'partial') {
        _partialText = event['result'];
      } else if (event['type'] == 'final') {
        if (event['result'].trim().length > 0) {
          _finalTexts.add(TranscriptionItem(
              event['result'], event['speaker'], _elapsedTime));
          _scrollToBottom();
        }
      }
    });
  }

  void _onError(dynamic error) {
    setState(() {
      _errorMsg = 'Recognition error: $error';
      _scrollToBottom();
    });
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> _startRecognition() async {
    try {
      await _methodChannel.invokeMethod('startRecognition');

      setState(() {
        _finalTexts.clear();
        _isListening = true;
        _startStopwatch();
      });
    } on PlatformException catch (e) {
      setState(() {
        _errorMsg = 'Failed to start recognition: ${e.message}';
        _scrollToBottom();
      });
    }
  }

  Future<void> _stopRecognition() async {
    try {
      await _methodChannel.invokeMethod('stopRecognition');
      setState(() {
        _isListening = false;
        _elapsedTime = '';

        _scrollToBottom();
        _stopStopwatch();
      });
    } on PlatformException catch (e) {
      setState(() {
        _errorMsg = 'Failed to stop recognition: ${e.message}';
        _scrollToBottom();
      });
    }
  }

  void _startStopwatch() {
    _stopwatch.reset();
    _stopwatch.start();

    _timer = Timer.periodic(const Duration(milliseconds: 100), (timer) {
      setState(() {
        _elapsedTime = _formatElapsedTime(_stopwatch.elapsed);
      });
    });
  }

  void _stopStopwatch() {
    _stopwatch.stop();
    _timer.cancel();
  }

  String _formatElapsedTime(Duration elapsed) {
    String twoDigits(int n) => n.toString().padLeft(2, '0');

    String twoDigitHours =
        elapsed.inHours == 0 ? '' : "${twoDigits(elapsed.inHours)}:";
    String twoDigitMinutes = twoDigits(elapsed.inMinutes.remainder(60));
    String twoDigitSeconds = twoDigits(elapsed.inSeconds.remainder(60));
    String oneDigitTenthOfSeconds =
        ((elapsed.inMilliseconds.remainder(1000) / 100).round() % 10)
            .toString();

    return "$twoDigitHours$twoDigitMinutes:$twoDigitSeconds:$oneDigitTenthOfSeconds";
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
            title: const Center(
              child: Text(
                'Vosk',
                style: TextStyle(
                    fontStyle: FontStyle.italic,
                    fontWeight: FontWeight.w500,
                    color: Color(0xFFD6C583)),
              ),
            ),
            backgroundColor: const Color(0xFF1B1607)),
        body: Container(
          color: const Color(0xFF1B1607), // Set the background color here
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: <Widget>[
                  _errorMsg.isNotEmpty
                      ? Text(
                          _errorMsg,
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                              fontSize: 18, color: Color(0xFFEE675C)),
                        )
                      : const SizedBox(height: 0),
                  const SizedBox(height: 20),
                  Expanded(
                    child: _finalTexts.isEmpty
                        ? Container(
                            alignment: Alignment.center,
                            child: const Icon(
                              Icons.graphic_eq,
                              color: Color(0xFF2F2A17),
                              size: 340.0,
                            ),
                          )
                        : Container(
                            decoration: BoxDecoration(
                              color: const Color(0xFF241F0E),
                              borderRadius: BorderRadius.circular(20.0),
                            ),
                            child: ListView.builder(
                              controller: _scrollController,
                              itemCount: _finalTexts.length,
                              itemBuilder: (context, index) {
                                return Container(
                                  margin:
                                      const EdgeInsets.symmetric(vertical: 4.0),
                                  padding: const EdgeInsets.all(12.0),
                                  decoration: BoxDecoration(
                                    borderRadius: BorderRadius.circular(8.0),
                                  ),
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Row(
                                        children: [
                                          const Icon(
                                              Icons.account_circle_rounded,
                                              color: Color(0xFFD6C583)),
                                          const SizedBox(width: 4),
                                          SelectableText(
                                            _finalTexts[index].speaker,
                                            textAlign: TextAlign.left,
                                            style: const TextStyle(
                                                fontWeight: FontWeight.bold,
                                                fontSize: 14,
                                                color: Color(0xFFD6C583)),
                                          ),
                                          const SizedBox(width: 10),
                                          SelectableText(
                                            _finalTexts[index].time,
                                            textAlign: TextAlign.left,
                                            style: const TextStyle(
                                                fontSize: 14,
                                                color: Color(0xFFD0C6AB)),
                                          ),
                                        ],
                                      ),
                                      const SizedBox(height: 10),
                                      SelectableText(
                                        _finalTexts[index].text,
                                        style: const TextStyle(
                                            height: 1.45,
                                            fontSize: 18,
                                            color: Color(0xFFEAE2CB)),
                                      ),
                                    ],
                                  ),
                                );
                              },
                            ),
                          ),
                  ),
                  const SizedBox(height: 20),
                  _isListening
                      ? Container(
                          width: double.infinity, // Set the width to full width
                          padding: const EdgeInsets.all(16.0),
                          decoration: BoxDecoration(
                            color: const Color(0xFF241F0E),
                            borderRadius: BorderRadius.circular(20.0),
                          ),
                          child: Text(
                            _partialText,
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                                fontSize: 18, color: Color(0xFFEAE2CB)),
                          ),
                        )
                      : const SizedBox(height: 0),
                  const SizedBox(height: 20),
                  Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      _elapsedTime.isNotEmpty
                          ? Column(
                              children: [
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    const Icon(
                                      Icons.circle,
                                      color: Color(0xFFEE675C),
                                      size: 16.0,
                                    ),
                                    const SizedBox(width: 8),
                                    Text(
                                      _elapsedTime,
                                      textAlign: TextAlign.center,
                                      style: const TextStyle(
                                          fontWeight: FontWeight.w400,
                                          fontSize: 32,
                                          color: Color(0xFFEAE2CB)),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 20),
                              ],
                            )
                          : const SizedBox(height: 0),
                      _isListening
                          ? BigFrickinButton(
                              icon: const Icon(
                                Icons.stop,
                                color: Color(0xff60130F),
                                size: 48.0,
                              ),
                              onPressed: _stopRecognition,
                            )
                          : BigFrickinButton(
                              icon: const Icon(
                                Icons.circle,
                                color: Color(0xff60130F),
                                size: 32.0,
                              ),
                              onPressed: _startRecognition,
                            ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class BigFrickinButton extends StatelessWidget {
  final VoidCallback onPressed;
  final Icon icon;

  const BigFrickinButton(
      {Key? key, required this.onPressed, required this.icon})
      : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 100.0, // Set the width of the circle
      height: 100.0, // Set the height of the circle
      decoration: const BoxDecoration(
        shape: BoxShape.circle,
        color: Color(0xFFEE675C), // Set the background color of the circle
      ),
      child: IconButton(
        icon: icon,
        onPressed: onPressed,
      ),
    );
  }
}
