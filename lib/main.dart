import 'dart:io';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

// Current local build version
const int currentBuildNo = 202607090300;
const String currentVersion = "1.0.0";

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CI-Deploy',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        primarySwatch: Colors.blue,
      ),
      home: const WebViewScreen(),
    );
  }
}

class WebViewScreen extends StatefulWidget {
  const WebViewScreen({super.key});

  @override
  State<WebViewScreen> createState() => _WebViewScreenState();
}

class _WebViewScreenState extends State<WebViewScreen> {
  late final WebViewController _controller;
  bool _checkedUpdate = false;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..loadRequest(Uri.parse('http://172.16.100.26:8080'));

    // Check for updates on startup
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_checkedUpdate) {
        _checkedUpdate = true;
        _checkForUpdates();
      }
    });
  }

  Future<void> _checkForUpdates() async {
    try {
      final response = await http.get(Uri.parse('http://172.16.100.26:8080/apps/ci-deploy/ci-deploy-version.json'));
      if (response.statusCode == 200) {
        final data = json.decode(utf8.decode(response.bodyBytes));
        final int remoteBuildNo = data['buildNo'] ?? 0;
        final String remoteVersion = data['version'] ?? '1.0.0';
        final String buildNote = data['buildNote'] ?? '';
        final String downloadUrl = data['url'] ?? '';

        if (remoteBuildNo > currentBuildNo) {
          if (mounted) {
            _showUpgradeDialog(remoteVersion, buildNote, downloadUrl);
          }
        }
      }
    } catch (e) {
      print("Error checking update: $e");
    }
  }

  void _showUpgradeDialog(String version, String note, String url) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('New Upgrade'),
          content: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('Version: $version'),
                const SizedBox(height: 10),
                const Text('What\'s new:', style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 5),
                Text(note),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('No'),
            ),
            ElevatedButton(
              onPressed: () {
                Navigator.of(context).pop();
                _startDownload(url);
              },
              child: const Text('Yes'),
            ),
          ],
        );
      },
    );
  }

  void _startDownload(String url) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => DownloadDialog(url: url),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: WebViewWidget(controller: _controller),
      ),
    );
  }
}

class DownloadDialog extends StatefulWidget {
  final String url;
  const DownloadDialog({super.key, required this.url});

  @override
  State<DownloadDialog> createState() => _DownloadDialogState();
}

class _DownloadDialogState extends State<DownloadDialog> {
  static const _channel = MethodChannel('hdisoft.app.cideploy/install');

  double _progress = 0.0;
  String _status = "Initializing download...";
  bool _isDownloading = true;
  bool _isCompleted = false;
  String? _localPath;
  http.Client? _client;

  @override
  void initState() {
    super.initState();
    _downloadFile();
  }

  Future<void> _downloadFile() async {
    try {
      _client = http.Client();
      final request = http.Request('GET', Uri.parse(widget.url));
      final response = await _client!.send(request);

      if (response.statusCode != 200) {
        setState(() {
          _status = "Error: Server returned status code ${response.statusCode}";
          _isDownloading = false;
        });
        return;
      }

      final contentLength = response.contentLength ?? 0;
      final tempDir = await getTemporaryDirectory();
      final filePath = "${tempDir.path}/CI-Deploy_upgrade.apk";
      _localPath = filePath;

      final file = File(filePath);
      final fileSink = file.openWrite();

      int downloaded = 0;

      await response.stream.listen(
        (List<int> chunk) {
          if (!_isDownloading) {
            // Cancelled
            fileSink.close();
            return;
          }
          fileSink.add(chunk);
          downloaded += chunk.length;
          setState(() {
            if (contentLength > 0) {
              _progress = downloaded / contentLength;
              _status = "Downloading: ${(downloaded / (1024 * 1024)).toStringAsFixed(1)} MB / ${(contentLength / (1024 * 1024)).toStringAsFixed(1)} MB (${(_progress * 100).toStringAsFixed(0)}%)";
            } else {
              _progress = 0.0;
              _status = "Downloading: ${(downloaded / (1024 * 1024)).toStringAsFixed(1)} MB";
            }
          });
        },
        onDone: () async {
          await fileSink.close();
          if (_isDownloading) {
            setState(() {
              _isDownloading = false;
              _isCompleted = true;
              _progress = 1.0;
              _status = "Download completed! Press Install to continue.";
            });
          }
        },
        onError: (error) {
          fileSink.close();
          setState(() {
            _status = "Error occurred: $error";
            _isDownloading = false;
          });
        },
        cancelOnError: true,
      ).asFuture();

    } catch (e) {
      setState(() {
        _status = "Error: $e";
        _isDownloading = false;
      });
    }
  }

  void _cancelDownload() {
    _isDownloading = false;
    _client?.close();
    setState(() {
      _status = "Download cancelled.";
    });
    Navigator.of(context).pop();
  }

  Future<void> _installApk() async {
    if (_localPath != null) {
      try {
        final bool success = await _channel.invokeMethod('installApk', {'filePath': _localPath});
        if (!success) {
          setState(() {
            _status = "Installation failed: File not found or could not open.";
          });
        }
      } catch (e) {
        setState(() {
          _status = "Error installing: $e";
        });
      }
    }
  }

  @override
  void dispose() {
    _client?.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Downloading Update'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(_status, style: const TextStyle(fontSize: 14)),
          const SizedBox(height: 15),
          if (_isDownloading || _isCompleted)
            LinearProgressIndicator(
              value: _progress > 0 ? _progress : null,
              backgroundColor: Colors.grey[200],
              valueColor: const AlwaysStoppedAnimation<Color>(Colors.blue),
            ),
        ],
      ),
      actions: [
        if (_isDownloading)
          TextButton(
            onPressed: _cancelDownload,
            child: const Text('Cancel'),
          ),
        if (!_isDownloading && !_isCompleted)
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Close'),
          ),
        if (_isCompleted) ...[
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Later'),
          ),
          ElevatedButton(
            onPressed: _installApk,
            child: const Text('Install'),
          ),
        ],
      ],
    );
  }
}
