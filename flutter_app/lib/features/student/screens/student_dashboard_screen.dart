import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/services/auth_service.dart';
import '../../../core/services/firestore_service.dart';
import '../../../core/widgets/gradient_card.dart';
import '../../../core/widgets/animated_counter.dart';
import '../../qr/screens/qr_scanner_screen.dart';
import 'student_history_screen.dart';

class StudentDashboardScreen extends StatefulWidget {
  final String? justMarkedSubject;
  final int? justMarkedTime;

  const StudentDashboardScreen({
    super.key,
    this.justMarkedSubject,
    this.justMarkedTime,
  });

  @override
  State<StudentDashboardScreen> createState() => _StudentDashboardScreenState();
}

class _StudentDashboardScreenState extends State<StudentDashboardScreen>
    with SingleTickerProviderStateMixin {
  String _studentName = 'Student';
  String _section = 'Section';
  String _todayStatus = 'Not marked yet';
  String _statusTime = '';
  Color _statusColor = AppColors.textHint;
  int _presentCount = 0;
  int _absentCount = 0;
  int _lateCount = 0;
  bool _isLoading = true;
  late AnimationController _animController;

  @override
  void initState() {
    super.initState();
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    );
    _loadData();
  }

  @override
  void dispose() {
    _animController.dispose();
    super.dispose();
  }

  Future<void> _loadData() async {
    await Future.wait([
      _loadUserData(),
      _loadAttendanceStats(),
      _loadTodayStatus(),
    ]);
    setState(() => _isLoading = false);
    _animController.forward();
  }

  Future<void> _loadUserData() async {
    final authService = context.read<AuthService>();
    final firestoreService = context.read<FirestoreService>();
    final user = authService.currentUser;
    if (user == null) return;

    final userData = await firestoreService.getUserData(user.uid);
    if (userData != null && mounted) {
      setState(() {
        _studentName = userData['name'] ?? 'Student';
        _section = (userData['section'] ?? 'Section').toUpperCase();
      });
    }
  }

  Future<void> _loadAttendanceStats() async {
    final authService = context.read<AuthService>();
    final firestoreService = context.read<FirestoreService>();
    final user = authService.currentUser;
    if (user == null) return;

    final stats = await firestoreService.getMonthlyAttendanceStats(user.uid);
    if (mounted) {
      setState(() {
        _presentCount = stats['present'] ?? 0;
        _absentCount = stats['absent'] ?? 0;
        _lateCount = stats['late'] ?? 0;
      });
    }
  }

  Future<void> _loadTodayStatus() async {
    if (widget.justMarkedSubject != null && widget.justMarkedTime != null) {
      final timeFormat = DateFormat('h:mm a');
      final time = DateTime.fromMillisecondsSinceEpoch(widget.justMarkedTime!);
      setState(() {
        _todayStatus = 'Present - ${widget.justMarkedSubject}';
        _statusTime = 'Marked at ${timeFormat.format(time)}';
        _statusColor = AppColors.statusPresent;
      });
      return;
    }
    setState(() {
      _todayStatus = 'Not marked yet';
      _statusTime = 'Scan QR to mark attendance';
      _statusColor = AppColors.textHint;
    });
  }

  String _getGreeting() {
    final hour = DateTime.now().hour;
    if (hour < 12) return 'Good Morning';
    if (hour < 17) return 'Good Afternoon';
    return 'Good Evening';
  }

  String _getInitials() {
    if (_studentName.isEmpty) return 'ST';
    final parts = _studentName.trim().split(' ');
    if (parts.length >= 2) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    }
    return _studentName.substring(0, _studentName.length > 1 ? 2 : 1).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Stack(
        children: [
          _buildBackground(),
          SafeArea(
            child: RefreshIndicator(
              onRefresh: _loadData,
              color: AppColors.primary,
              backgroundColor: AppColors.cardBackground,
              child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.fromLTRB(20, 20, 20, 120),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _buildHeader(),
                    const SizedBox(height: 28),
                    _buildQuickScanCard(),
                    const SizedBox(height: 20),
                    _buildTodayStatusCard(),
                    const SizedBox(height: 24),
                    _buildStatsSection(),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
      floatingActionButton: _buildFAB(),
    );
  }

  Widget _buildBackground() {
    return Positioned(
      top: -100,
      right: -100,
      child: Container(
        width: 300,
        height: 300,
        decoration: BoxDecoration(
          gradient: RadialGradient(
            colors: [
              AppColors.primary.withOpacity(0.15),
              AppColors.primary.withOpacity(0.0),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Row(
      children: [
        Container(
          width: 56,
          height: 56,
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: AppColors.primaryGradient,
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(18),
            boxShadow: [
              BoxShadow(
                color: AppColors.primary.withOpacity(0.3),
                blurRadius: 15,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: Center(
            child: Text(
              _getInitials(),
              style: const TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                _getGreeting(),
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: AppColors.textSecondary,
                    ),
              ),
              const SizedBox(height: 2),
              Text(
                _studentName,
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
              ),
            ],
          ),
        ),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                AppColors.accent.withOpacity(0.2),
                AppColors.primary.withOpacity(0.2),
              ],
            ),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.accent.withOpacity(0.3)),
          ),
          child: Text(
            _section,
            style: TextStyle(
              color: AppColors.accent,
              fontSize: 13,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.5,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildQuickScanCard() {
    return GradientCard(
      gradient: AppColors.primaryGradient,
      padding: const EdgeInsets.all(0),
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const QRScannerScreen()),
      ),
      child: Stack(
        children: [
          Positioned(
            right: -30,
            bottom: -30,
            child: Icon(
              Icons.qr_code_scanner_rounded,
              size: 150,
              color: Colors.white.withOpacity(0.1),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(24),
            child: Row(
              children: [
                Container(
                  width: 60,
                  height: 60,
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: const Icon(
                    Icons.qr_code_scanner_rounded,
                    color: Colors.white,
                    size: 32,
                  ),
                ),
                const SizedBox(width: 20),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Scan QR Code',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 20,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Tap to mark your attendance',
                        style: TextStyle(
                          color: Colors.white.withOpacity(0.8),
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(
                    Icons.arrow_forward_rounded,
                    color: Colors.white,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTodayStatusCard() {
    final isMarked = _statusColor == AppColors.statusPresent;
    return GlassCard(
      borderColor: isMarked ? AppColors.statusPresent.withOpacity(0.3) : null,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: _statusColor.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  isMarked ? Icons.check_circle_rounded : Icons.schedule_rounded,
                  color: _statusColor,
                  size: 24,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      "Today's Status",
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: AppColors.textSecondary,
                          ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      _todayStatus,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w600,
                            color: isMarked ? _statusColor : AppColors.textPrimary,
                          ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (_statusTime.isNotEmpty) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: AppColors.surfaceVariant,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.access_time_rounded,
                    size: 14,
                    color: AppColors.textSecondary,
                  ),
                  const SizedBox(width: 6),
                  Text(
                    _statusTime,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: AppColors.textSecondary,
                        ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildStatsSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'This Month',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
            ),
            GestureDetector(
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const StudentHistoryScreen()),
              ),
              child: Row(
                children: [
                  Text(
                    'View All',
                    style: TextStyle(
                      color: AppColors.primary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(width: 4),
                  Icon(
                    Icons.arrow_forward_rounded,
                    size: 16,
                    color: AppColors.primary,
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(child: _buildStatCard('Present', _presentCount, AppColors.statusPresent, Icons.check_circle_outline_rounded)),
            const SizedBox(width: 12),
            Expanded(child: _buildStatCard('Absent', _absentCount, AppColors.statusAbsent, Icons.cancel_outlined)),
            const SizedBox(width: 12),
            Expanded(child: _buildStatCard('Late', _lateCount, AppColors.statusLate, Icons.schedule_rounded)),
          ],
        ),
      ],
    );
  }

  Widget _buildStatCard(String label, int count, Color color, IconData icon) {
    return GlassCard(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: color.withOpacity(0.1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, color: color, size: 22),
          ),
          const SizedBox(height: 12),
          AnimatedCounter(
            value: count,
            style: TextStyle(
              color: color,
              fontSize: 28,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: AppColors.textSecondary,
                ),
          ),
        ],
      ),
    );
  }

  Widget _buildFAB() {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(colors: AppColors.primaryGradient),
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: AppColors.primary.withOpacity(0.4),
            blurRadius: 20,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: FloatingActionButton.extended(
        onPressed: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const QRScannerScreen()),
        ),
        backgroundColor: Colors.transparent,
        elevation: 0,
        icon: const Icon(Icons.qr_code_scanner_rounded),
        label: const Text('Scan', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
    );
  }
}
