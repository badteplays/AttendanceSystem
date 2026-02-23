import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:provider/provider.dart';
import 'package:vibration/vibration.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/services/auth_service.dart';
import '../../../core/services/firestore_service.dart';
import '../../../core/models/qr_code_data.dart';
import '../../../core/models/attendance.dart';

class QRScannerScreen extends StatefulWidget {
  const QRScannerScreen({super.key});

  @override
  State<QRScannerScreen> createState() => _QRScannerScreenState();
}

class _QRScannerScreenState extends State<QRScannerScreen> with SingleTickerProviderStateMixin {
  final MobileScannerController _controller = MobileScannerController(
    detectionSpeed: DetectionSpeed.normal,
    facing: CameraFacing.back,
    torchEnabled: false,
  );

  bool _isProcessing = false;
  String? _timerText;
  final Set<String> _scannedSessions = {};
  late AnimationController _pulseController;
  late Animation<double> _pulseAnimation;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    )..repeat(reverse: true);
    _pulseAnimation = Tween<double>(begin: 1.0, end: 1.1).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    _pulseController.dispose();
    super.dispose();
  }

  Future<void> _handleQRCode(String qrContent) async {
    if (_isProcessing) return;
    setState(() => _isProcessing = true);

    try {
      await Vibration.vibrate(duration: 200);
    } catch (_) {}

    try {
      final qrData = QRCodeData.fromJson(qrContent);

      if (qrData.isExpired) {
        _showMessage('This QR code has expired', isError: true);
        setState(() => _isProcessing = false);
        return;
      }

      if (_scannedSessions.contains(qrData.sessionId)) {
        _showMessage("You've already marked attendance!", isError: true);
        setState(() => _isProcessing = false);
        return;
      }

      final firestoreService = context.read<FirestoreService>();

      final sessionData = await firestoreService.validateSession(qrData.sessionId);
      if (sessionData == null) {
        _showMessage('Invalid or expired session', isError: true);
        setState(() => _isProcessing = false);
        return;
      }

      if (qrData.teacherId != sessionData['teacherId']) {
        _showMessage('Invalid QR code for this class', isError: true);
        setState(() => _isProcessing = false);
        return;
      }

      final expiresAt = sessionData['expiresAt'] as int? ?? 0;
      if (DateTime.now().millisecondsSinceEpoch > expiresAt) {
        _showMessage('Session has expired', isError: true);
        setState(() => _isProcessing = false);
        return;
      }

      final authService = context.read<AuthService>();
      final user = authService.currentUser;
      if (user == null) throw Exception('Not logged in');

      final userData = await firestoreService.getUserData(user.uid);
      final studentName = userData?['name'] ?? 'Unknown Student';

      final alreadyMarked = await firestoreService.hasAlreadyMarkedAttendance(
        user.uid,
        qrData.sessionId,
      );

      if (alreadyMarked) {
        _scannedSessions.add(qrData.sessionId);
        _showMessage("Already scanned!", isError: true);
        setState(() => _isProcessing = false);
        return;
      }

      await firestoreService.markAttendance(
        userId: user.uid,
        studentName: studentName,
        sessionId: qrData.sessionId,
        teacherId: qrData.teacherId,
        scheduleId: qrData.scheduleId,
        subject: qrData.subject,
        section: qrData.section,
      );

      _scannedSessions.add(qrData.sessionId);

      if (mounted) {
        _showSuccessDialog(qrData.subject);
      }
    } catch (e) {
      _showMessage(e.toString().replaceAll('Exception: ', ''), isError: true);
      setState(() => _isProcessing = false);
    }
  }

  void _showMessage(String message, {bool isError = false}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            Icon(
              isError ? Icons.error_outline_rounded : Icons.check_circle_rounded,
              color: isError ? AppColors.error : AppColors.statusPresent,
            ),
            const SizedBox(width: 12),
            Expanded(child: Text(message)),
          ],
        ),
        backgroundColor: AppColors.cardBackground,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
    );
  }

  void _showSuccessDialog(String subject) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => Dialog(
        backgroundColor: Colors.transparent,
        child: Container(
          padding: const EdgeInsets.all(32),
          decoration: BoxDecoration(
            color: AppColors.cardBackground,
            borderRadius: BorderRadius.circular(28),
            border: Border.all(color: AppColors.statusPresent.withOpacity(0.3)),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  gradient: LinearGradient(colors: AppColors.successGradient),
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: AppColors.statusPresent.withOpacity(0.4),
                      blurRadius: 20,
                      offset: const Offset(0, 10),
                    ),
                  ],
                ),
                child: const Icon(
                  Icons.check_rounded,
                  color: Colors.white,
                  size: 40,
                ),
              ),
              const SizedBox(height: 24),
              Text(
                'Attendance Marked!',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w700,
                      color: AppColors.textPrimary,
                    ),
              ),
              const SizedBox(height: 8),
              Text(
                subject,
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: AppColors.textSecondary,
                    ),
              ),
              const SizedBox(height: 28),
              SizedBox(
                width: double.infinity,
                child: Container(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(colors: AppColors.successGradient),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: ElevatedButton(
                    onPressed: () {
                      Navigator.pop(context);
                      Navigator.pop(context, {
                        'subject': subject,
                        'time': DateTime.now().millisecondsSinceEpoch,
                      });
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.transparent,
                      shadowColor: Colors.transparent,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                    ),
                    child: const Text(
                      'Done',
                      style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: GestureDetector(
          onTap: () => Navigator.pop(context),
          child: Container(
            margin: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: Colors.black.withOpacity(0.5),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(Icons.close_rounded, color: Colors.white),
          ),
        ),
        actions: [
          GestureDetector(
            onTap: () => _controller.toggleTorch(),
            child: Container(
              margin: const EdgeInsets.all(8),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Colors.black.withOpacity(0.5),
                borderRadius: BorderRadius.circular(12),
              ),
              child: ValueListenableBuilder(
                valueListenable: _controller.torchState,
                builder: (context, state, child) {
                  return Icon(
                    state == TorchState.on ? Icons.flash_on_rounded : Icons.flash_off_rounded,
                    color: state == TorchState.on ? AppColors.warning : Colors.white,
                  );
                },
              ),
            ),
          ),
        ],
      ),
      body: Stack(
        children: [
          MobileScanner(
            controller: _controller,
            onDetect: (capture) {
              final List<Barcode> barcodes = capture.barcodes;
              for (final barcode in barcodes) {
                if (barcode.rawValue != null && !_isProcessing) {
                  _handleQRCode(barcode.rawValue!);
                  break;
                }
              }
            },
          ),
          _buildScannerOverlay(),
          if (_isProcessing) _buildLoadingOverlay(),
          _buildInstructions(),
        ],
      ),
    );
  }

  Widget _buildScannerOverlay() {
    return Stack(
      children: [
        ColorFiltered(
          colorFilter: const ColorFilter.mode(
            Colors.black54,
            BlendMode.srcOut,
          ),
          child: Stack(
            children: [
              Container(
                decoration: const BoxDecoration(
                  color: Colors.black,
                  backgroundBlendMode: BlendMode.dstOut,
                ),
              ),
              Center(
                child: AnimatedBuilder(
                  animation: _pulseAnimation,
                  builder: (context, child) {
                    return Transform.scale(
                      scale: _pulseAnimation.value,
                      child: Container(
                        width: 280,
                        height: 280,
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(32),
                        ),
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
        Center(
          child: Container(
            width: 284,
            height: 284,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(32),
              border: Border.all(
                color: AppColors.primary,
                width: 3,
              ),
              boxShadow: [
                BoxShadow(
                  color: AppColors.primary.withOpacity(0.3),
                  blurRadius: 20,
                  spreadRadius: 5,
                ),
              ],
            ),
            child: Stack(
              children: [
                _buildCorner(Alignment.topLeft),
                _buildCorner(Alignment.topRight),
                _buildCorner(Alignment.bottomLeft),
                _buildCorner(Alignment.bottomRight),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildCorner(Alignment alignment) {
    final isTop = alignment == Alignment.topLeft || alignment == Alignment.topRight;
    final isLeft = alignment == Alignment.topLeft || alignment == Alignment.bottomLeft;

    return Positioned(
      top: isTop ? 0 : null,
      bottom: !isTop ? 0 : null,
      left: isLeft ? 0 : null,
      right: !isLeft ? 0 : null,
      child: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          border: Border(
            top: isTop ? BorderSide(color: AppColors.primary, width: 4) : BorderSide.none,
            bottom: !isTop ? BorderSide(color: AppColors.primary, width: 4) : BorderSide.none,
            left: isLeft ? BorderSide(color: AppColors.primary, width: 4) : BorderSide.none,
            right: !isLeft ? BorderSide(color: AppColors.primary, width: 4) : BorderSide.none,
          ),
          borderRadius: BorderRadius.only(
            topLeft: isTop && isLeft ? const Radius.circular(32) : Radius.zero,
            topRight: isTop && !isLeft ? const Radius.circular(32) : Radius.zero,
            bottomLeft: !isTop && isLeft ? const Radius.circular(32) : Radius.zero,
            bottomRight: !isTop && !isLeft ? const Radius.circular(32) : Radius.zero,
          ),
        ),
      ),
    );
  }

  Widget _buildLoadingOverlay() {
    return Container(
      color: Colors.black.withOpacity(0.7),
      child: Center(
        child: Container(
          padding: const EdgeInsets.all(32),
          decoration: BoxDecoration(
            color: AppColors.cardBackground,
            borderRadius: BorderRadius.circular(24),
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  gradient: LinearGradient(colors: AppColors.primaryGradient),
                  shape: BoxShape.circle,
                ),
                child: const SizedBox(
                  width: 32,
                  height: 32,
                  child: CircularProgressIndicator(
                    color: Colors.white,
                    strokeWidth: 3,
                  ),
                ),
              ),
              const SizedBox(height: 20),
              Text(
                'Marking attendance...',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: AppColors.textPrimary,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildInstructions() {
    return Positioned(
      bottom: 100,
      left: 0,
      right: 0,
      child: Center(
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 16),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                Colors.black.withOpacity(0.8),
                Colors.black.withOpacity(0.6),
              ],
            ),
            borderRadius: BorderRadius.circular(30),
            border: Border.all(color: Colors.white.withOpacity(0.1)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: AppColors.primary.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(
                  Icons.qr_code_scanner_rounded,
                  color: AppColors.primary,
                  size: 20,
                ),
              ),
              const SizedBox(width: 14),
              const Text(
                'Point camera at QR code',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 15,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
