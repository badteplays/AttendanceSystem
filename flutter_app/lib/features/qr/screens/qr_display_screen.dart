import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:uuid/uuid.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/services/auth_service.dart';
import '../../../core/services/firestore_service.dart';
import '../../../core/models/schedule.dart';
import '../../../core/models/qr_code_data.dart';
import '../../../core/widgets/gradient_card.dart';

class QRDisplayScreen extends StatefulWidget {
  final Schedule schedule;
  final bool forceNew;

  const QRDisplayScreen({
    super.key,
    required this.schedule,
    this.forceNew = false,
  });

  @override
  State<QRDisplayScreen> createState() => _QRDisplayScreenState();
}

class _QRDisplayScreenState extends State<QRDisplayScreen> with SingleTickerProviderStateMixin {
  String? _qrData;
  int _currentExpirationMinutes = 30;
  Timer? _countdownTimer;
  String _timerText = '';
  bool _isLoading = true;
  double _progress = 1.0;
  int _totalSeconds = 0;
  late AnimationController _glowController;

  @override
  void initState() {
    super.initState();
    _glowController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2000),
    )..repeat(reverse: true);
    _loadOrGenerateQR();
  }

  @override
  void dispose() {
    _countdownTimer?.cancel();
    _glowController.dispose();
    super.dispose();
  }

  Future<void> _loadOrGenerateQR() async {
    setState(() => _isLoading = true);

    final authService = context.read<AuthService>();
    final firestoreService = context.read<FirestoreService>();
    final user = authService.currentUser!;

    if (widget.forceNew) {
      await firestoreService.deleteOldSessions(widget.schedule.id, user.uid);
      await _generateQR();
    } else {
      final activeSession = await firestoreService.getActiveSession(
        widget.schedule.id,
        user.uid,
      );

      if (activeSession != null) {
        final createdAt = activeSession['createdAt'] as int? ?? 0;
        final expiresAt = activeSession['expiresAt'] as int? ?? 0;
        _currentExpirationMinutes = ((expiresAt - createdAt) / (60 * 1000)).toInt();

        final qrCodeData = QRCodeData(
          teacherId: user.uid,
          sessionId: activeSession['sessionId'] ?? '',
          userId: user.uid,
          timestamp: createdAt,
          scheduleId: widget.schedule.id,
          subject: widget.schedule.subject,
          section: widget.schedule.section,
          expirationMinutes: _currentExpirationMinutes,
        );

        setState(() {
          _qrData = qrCodeData.toJson();
          _isLoading = false;
        });

        _startCountdown(qrCodeData);
      } else {
        await _generateQR();
      }
    }
  }

  Future<void> _generateQR() async {
    final authService = context.read<AuthService>();
    final firestoreService = context.read<FirestoreService>();
    final user = authService.currentUser!;

    final sessionId = const Uuid().v4();

    await firestoreService.createSession(
      sessionId: sessionId,
      teacherId: user.uid,
      scheduleId: widget.schedule.id,
      subject: widget.schedule.subject,
      section: widget.schedule.section,
      expirationMinutes: _currentExpirationMinutes,
    );

    final qrCodeData = QRCodeData.createWithExpiration(
      teacherId: user.uid,
      sessionId: sessionId,
      userId: user.uid,
      scheduleId: widget.schedule.id,
      subject: widget.schedule.subject,
      section: widget.schedule.section,
      expirationMinutes: _currentExpirationMinutes,
    );

    setState(() {
      _qrData = qrCodeData.toJson();
      _isLoading = false;
    });

    _startCountdown(qrCodeData);

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            Icon(Icons.check_circle_rounded, color: AppColors.statusPresent),
            const SizedBox(width: 12),
            Text('QR Code ready! Expires in $_currentExpirationMinutes min'),
          ],
        ),
        backgroundColor: AppColors.cardBackground,
      ),
    );
  }

  void _startCountdown(QRCodeData qrData) {
    _countdownTimer?.cancel();
    _totalSeconds = qrData.remainingTimeInMillis ~/ 1000;

    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      final remaining = qrData.remainingTimeInMillis;
      if (remaining <= 0) {
        timer.cancel();
        setState(() {
          _timerText = 'Expired';
          _progress = 0;
        });
        _generateQR();
      } else {
        final seconds = remaining ~/ 1000;
        final minutes = seconds ~/ 60;
        final remainingSeconds = seconds % 60;
        setState(() {
          _timerText = '${minutes.toString().padLeft(2, '0')}:${remainingSeconds.toString().padLeft(2, '0')}';
          _progress = seconds / _totalSeconds;
        });
      }
    });
  }

  void _showExpirationDialog() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (context) => Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          color: AppColors.cardBackground,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AppColors.outline,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 24),
            Text(
              'Set Timer Duration',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
            ),
            const SizedBox(height: 20),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: [5, 10, 15, 30, 45, 60].map((minutes) {
                return GestureDetector(
                  onTap: () {
                    Navigator.pop(context);
                    setState(() => _currentExpirationMinutes = minutes);
                    _generateQR();
                  },
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
                    decoration: BoxDecoration(
                      gradient: _currentExpirationMinutes == minutes
                          ? LinearGradient(colors: AppColors.primaryGradient)
                          : null,
                      color: _currentExpirationMinutes != minutes ? AppColors.surfaceVariant : null,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(
                        color: _currentExpirationMinutes == minutes
                            ? Colors.transparent
                            : AppColors.cardBorder,
                      ),
                    ),
                    child: Text(
                      '$minutes min',
                      style: TextStyle(
                        color: _currentExpirationMinutes == minutes
                            ? Colors.white
                            : AppColors.textPrimary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Stack(
        children: [
          _buildBackground(),
          SafeArea(
            child: Column(
              children: [
                _buildAppBar(),
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      children: [
                        _buildClassInfo(),
                        const SizedBox(height: 32),
                        _buildQRCode(),
                        const SizedBox(height: 32),
                        _buildActions(),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBackground() {
    return Stack(
      children: [
        Positioned(
          top: -150,
          left: -100,
          child: Container(
            width: 400,
            height: 400,
            decoration: BoxDecoration(
              gradient: RadialGradient(
                colors: [
                  AppColors.primary.withOpacity(0.15),
                  AppColors.primary.withOpacity(0.0),
                ],
              ),
            ),
          ),
        ),
        Positioned(
          bottom: -100,
          right: -100,
          child: Container(
            width: 300,
            height: 300,
            decoration: BoxDecoration(
              gradient: RadialGradient(
                colors: [
                  AppColors.secondary.withOpacity(0.1),
                  AppColors.secondary.withOpacity(0.0),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildAppBar() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          GestureDetector(
            onTap: () => Navigator.pop(context),
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.surfaceVariant,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppColors.cardBorder),
              ),
              child: const Icon(Icons.arrow_back_rounded, color: AppColors.textPrimary),
            ),
          ),
          const Spacer(),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            decoration: BoxDecoration(
              gradient: LinearGradient(colors: AppColors.primaryGradient),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Row(
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: Colors.white,
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.white.withOpacity(0.5),
                        blurRadius: 6,
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 10),
                const Text(
                  'LIVE',
                  style: TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildClassInfo() {
    return GradientCard(
      gradient: [AppColors.secondary.withOpacity(0.8), AppColors.accent.withOpacity(0.8)],
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(0.2),
              borderRadius: BorderRadius.circular(14),
            ),
            child: const Icon(Icons.school_rounded, color: Colors.white, size: 28),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.schedule.subject,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 20,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  widget.schedule.section,
                  style: TextStyle(
                    color: Colors.white.withOpacity(0.8),
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildQRCode() {
    if (_isLoading || _qrData == null) {
      return Container(
        width: 320,
        height: 320,
        decoration: BoxDecoration(
          color: AppColors.cardBackground,
          borderRadius: BorderRadius.circular(32),
          border: Border.all(color: AppColors.cardBorder),
        ),
        child: const Center(
          child: CircularProgressIndicator(color: AppColors.primary),
        ),
      );
    }

    return Column(
      children: [
        AnimatedBuilder(
          animation: _glowController,
          builder: (context, child) {
            return Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(32),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.primary.withOpacity(0.2 + (_glowController.value * 0.15)),
                    blurRadius: 30 + (_glowController.value * 20),
                    spreadRadius: 5,
                  ),
                ],
              ),
              child: QrImageView(
                data: _qrData!,
                version: QrVersions.auto,
                size: 260,
                backgroundColor: Colors.white,
                errorCorrectionLevel: QrErrorCorrectLevel.H,
                gapless: true,
                eyeStyle: QrEyeStyle(
                  eyeShape: QrEyeShape.roundedRect,
                  color: AppColors.primary,
                ),
                dataModuleStyle: QrDataModuleStyle(
                  dataModuleShape: QrDataModuleShape.roundedRect,
                  color: AppColors.background,
                ),
              ),
            );
          },
        ),
        const SizedBox(height: 24),
        _buildTimerDisplay(),
      ],
    );
  }

  Widget _buildTimerDisplay() {
    final isExpired = _timerText == 'Expired';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      decoration: BoxDecoration(
        color: AppColors.cardBackground,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: isExpired ? AppColors.error.withOpacity(0.3) : AppColors.cardBorder,
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Stack(
            alignment: Alignment.center,
            children: [
              SizedBox(
                width: 44,
                height: 44,
                child: CircularProgressIndicator(
                  value: _progress,
                  strokeWidth: 4,
                  backgroundColor: AppColors.surfaceVariant,
                  valueColor: AlwaysStoppedAnimation(
                    isExpired ? AppColors.error : AppColors.primary,
                  ),
                ),
              ),
              Icon(
                isExpired ? Icons.refresh_rounded : Icons.timer_outlined,
                color: isExpired ? AppColors.error : AppColors.primary,
                size: 20,
              ),
            ],
          ),
          const SizedBox(width: 16),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                isExpired ? 'Expired' : 'Expires in',
                style: TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 12,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                _timerText.isEmpty ? '--:--' : _timerText,
                style: TextStyle(
                  color: isExpired ? AppColors.error : AppColors.textPrimary,
                  fontSize: 24,
                  fontWeight: FontWeight.w700,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildActions() {
    return Row(
      children: [
        Expanded(
          child: Container(
            decoration: BoxDecoration(
              gradient: LinearGradient(colors: AppColors.primaryGradient),
              borderRadius: BorderRadius.circular(16),
              boxShadow: [
                BoxShadow(
                  color: AppColors.primary.withOpacity(0.3),
                  blurRadius: 15,
                  offset: const Offset(0, 8),
                ),
              ],
            ),
            child: ElevatedButton.icon(
              onPressed: _generateQR,
              icon: const Icon(Icons.refresh_rounded),
              label: const Text('Regenerate'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.transparent,
                shadowColor: Colors.transparent,
                padding: const EdgeInsets.symmetric(vertical: 18),
              ),
            ),
          ),
        ),
        const SizedBox(width: 12),
        GestureDetector(
          onTap: _showExpirationDialog,
          child: Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: AppColors.surfaceVariant,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.cardBorder),
            ),
            child: const Icon(Icons.timer_outlined, color: AppColors.textPrimary),
          ),
        ),
      ],
    );
  }
}
