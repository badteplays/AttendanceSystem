import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:intl/intl.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/services/auth_service.dart';
import '../../../core/services/firestore_service.dart';
import '../../../core/models/schedule.dart';
import '../../../core/models/attendance.dart';
import '../../../core/widgets/gradient_card.dart';
import '../../qr/screens/qr_display_screen.dart';
import '../widgets/manual_add_dialog.dart';

class TeacherDashboardScreen extends StatefulWidget {
  const TeacherDashboardScreen({super.key});

  @override
  State<TeacherDashboardScreen> createState() => _TeacherDashboardScreenState();
}

class _TeacherDashboardScreenState extends State<TeacherDashboardScreen> {
  String _teacherName = 'Teacher';
  String _department = 'Department';
  String _qrStatus = 'No active QR code';
  Schedule? _currentSchedule;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    await _loadUserData();
    await _loadCurrentSchedule();
    setState(() => _isLoading = false);
  }

  Future<void> _loadUserData() async {
    final authService = context.read<AuthService>();
    final firestoreService = context.read<FirestoreService>();
    final user = authService.currentUser;
    if (user == null) return;

    final userData = await firestoreService.getUserData(user.uid);
    if (userData != null && mounted) {
      setState(() {
        _teacherName = userData['name'] ?? 'Teacher';
        _department = userData['department'] ?? 'Department';
      });
    }
  }

  Future<void> _loadCurrentSchedule() async {
    final authService = context.read<AuthService>();
    final firestoreService = context.read<FirestoreService>();
    final user = authService.currentUser;
    if (user == null) return;

    final dayFormat = DateFormat('EEEE');
    final currentDay = dayFormat.format(DateTime.now());
    final now = DateTime.now();
    final nowMinutes = now.hour * 60 + now.minute;

    final schedules = await firestoreService.getTodaySchedulesForTeacher(user.uid, currentDay);

    Schedule? currentSchedule;
    Schedule? nextSchedule;

    for (final schedule in schedules) {
      if (schedule.isCurrentlyActive(nowMinutes)) {
        currentSchedule = schedule;
        break;
      }
      if (schedule.startTimeInMinutes > nowMinutes) {
        if (nextSchedule == null || schedule.startTimeInMinutes < nextSchedule.startTimeInMinutes) {
          nextSchedule = schedule;
        }
      }
    }

    if (mounted) {
      setState(() {
        _currentSchedule = currentSchedule;
        if (currentSchedule != null) {
          _qrStatus = '${currentSchedule.subject} • ${currentSchedule.section}';
        } else if (nextSchedule != null) {
          _qrStatus = 'Next: ${nextSchedule.subject} at ${nextSchedule.formattedStartTime}';
        } else if (schedules.isEmpty) {
          _qrStatus = 'No classes scheduled today';
        } else {
          _qrStatus = 'No more classes today';
        }
      });
    }
  }

  String _getInitials() {
    if (_teacherName.isEmpty) return 'TC';
    final parts = _teacherName.trim().split(' ');
    if (parts.length >= 2) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    }
    return _teacherName.substring(0, _teacherName.length > 1 ? 2 : 1).toUpperCase();
  }

  void _showQRCode({bool forceNew = false}) {
    if (_currentSchedule == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_qrStatus)),
      );
      return;
    }

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => QRDisplayScreen(
          schedule: _currentSchedule!,
          forceNew: forceNew,
        ),
      ),
    );
  }

  void _showManualAddDialog() {
    if (_currentSchedule == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No current class')),
      );
      return;
    }

    showDialog(
      context: context,
      builder: (context) => ManualAddDialog(schedule: _currentSchedule!),
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
                    _buildQRCard(),
                    const SizedBox(height: 20),
                    _buildLiveAttendanceSection(),
                  ],
                ),
              ),
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
          top: -80,
          left: -80,
          child: Container(
            width: 250,
            height: 250,
            decoration: BoxDecoration(
              gradient: RadialGradient(
                colors: [
                  AppColors.secondary.withOpacity(0.12),
                  AppColors.secondary.withOpacity(0.0),
                ],
              ),
            ),
          ),
        ),
        Positioned(
          top: 200,
          right: -100,
          child: Container(
            width: 200,
            height: 200,
            decoration: BoxDecoration(
              gradient: RadialGradient(
                colors: [
                  AppColors.accent.withOpacity(0.08),
                  AppColors.accent.withOpacity(0.0),
                ],
              ),
            ),
          ),
        ),
      ],
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
              colors: [AppColors.secondary, AppColors.accent],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(18),
            boxShadow: [
              BoxShadow(
                color: AppColors.secondary.withOpacity(0.3),
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
                'Welcome back',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: AppColors.textSecondary,
                    ),
              ),
              const SizedBox(height: 2),
              Text(
                _teacherName,
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
                AppColors.secondary.withOpacity(0.2),
                AppColors.accent.withOpacity(0.2),
              ],
            ),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.secondary.withOpacity(0.3)),
          ),
          child: Text(
            _department,
            style: TextStyle(
              color: AppColors.secondary,
              fontSize: 13,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildQRCard() {
    final hasClass = _currentSchedule != null;
    return GradientCard(
      gradient: hasClass ? AppColors.primaryGradient : [AppColors.surfaceVariant, AppColors.surface],
      padding: const EdgeInsets.all(0),
      child: Stack(
        children: [
          if (hasClass)
            Positioned(
              right: -40,
              bottom: -40,
              child: Icon(
                Icons.qr_code_2_rounded,
                size: 180,
                color: Colors.white.withOpacity(0.08),
              ),
            ),
          Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(hasClass ? 0.2 : 0.1),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Icon(
                        Icons.qr_code_2_rounded,
                        color: hasClass ? Colors.white : AppColors.textSecondary,
                        size: 28,
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            hasClass ? 'Current Class' : 'QR Code',
                            style: TextStyle(
                              color: hasClass ? Colors.white.withOpacity(0.7) : AppColors.textSecondary,
                              fontSize: 13,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            _qrStatus,
                            style: TextStyle(
                              color: hasClass ? Colors.white : AppColors.textPrimary,
                              fontSize: 18,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),
                Row(
                  children: [
                    Expanded(
                      child: _QRActionButton(
                        icon: Icons.qr_code_rounded,
                        label: 'Show QR',
                        isPrimary: true,
                        isLight: hasClass,
                        onTap: () => _showQRCode(),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _QRActionButton(
                        icon: Icons.refresh_rounded,
                        label: 'Renew',
                        isPrimary: false,
                        isLight: hasClass,
                        onTap: () => _showQRCode(forceNew: true),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLiveAttendanceSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Text(
                  'Live Attendance',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                ),
                const SizedBox(width: 10),
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: AppColors.statusPresent,
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.statusPresent.withOpacity(0.5),
                        blurRadius: 6,
                      ),
                    ],
                  ),
                ),
              ],
            ),
            Row(
              children: [
                _MiniButton(
                  icon: Icons.refresh_rounded,
                  onTap: _loadData,
                ),
                const SizedBox(width: 8),
                _MiniButton(
                  icon: Icons.person_add_rounded,
                  onTap: _showManualAddDialog,
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 16),
        if (_currentSchedule != null)
          _AttendanceListView(schedule: _currentSchedule!)
        else
          GlassCard(
            child: Center(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: Column(
                  children: [
                    Icon(
                      Icons.event_busy_rounded,
                      size: 48,
                      color: AppColors.textHint,
                    ),
                    const SizedBox(height: 16),
                    Text(
                      'No active class',
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            color: AppColors.textSecondary,
                          ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Start a class to see live attendance',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: AppColors.textHint,
                          ),
                    ),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _QRActionButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool isPrimary;
  final bool isLight;
  final VoidCallback onTap;

  const _QRActionButton({
    required this.icon,
    required this.label,
    required this.isPrimary,
    required this.isLight,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          color: isPrimary
              ? (isLight ? Colors.white : AppColors.primary)
              : (isLight ? Colors.white.withOpacity(0.15) : AppColors.surfaceVariant),
          borderRadius: BorderRadius.circular(14),
          border: isPrimary ? null : Border.all(
            color: isLight ? Colors.white.withOpacity(0.3) : AppColors.cardBorder,
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              icon,
              size: 20,
              color: isPrimary
                  ? (isLight ? AppColors.primary : Colors.white)
                  : (isLight ? Colors.white : AppColors.textPrimary),
            ),
            const SizedBox(width: 8),
            Text(
              label,
              style: TextStyle(
                color: isPrimary
                    ? (isLight ? AppColors.primary : Colors.white)
                    : (isLight ? Colors.white : AppColors.textPrimary),
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MiniButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;

  const _MiniButton({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: AppColors.surfaceVariant,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppColors.cardBorder),
        ),
        child: Icon(icon, size: 20, color: AppColors.textSecondary),
      ),
    );
  }
}

class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsets? padding;

  const GlassCard({super.key, required this.child, this.padding});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: padding ?? const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.cardBackground,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppColors.cardBorder),
      ),
      child: child,
    );
  }
}

class _AttendanceListView extends StatelessWidget {
  final Schedule schedule;

  const _AttendanceListView({required this.schedule});

  @override
  Widget build(BuildContext context) {
    final authService = context.read<AuthService>();
    final user = authService.currentUser;
    if (user == null) return const SizedBox();

    final now = DateTime.now();
    final startTime = schedule.startTime.split(':');
    final classStart = DateTime(
      now.year,
      now.month,
      now.day,
      int.parse(startTime[0]),
      int.parse(startTime[1]),
    );
    final classStartTimestamp = Timestamp.fromDate(classStart);

    return StreamBuilder<QuerySnapshot>(
      stream: FirebaseFirestore.instance
          .collection('attendance')
          .where('teacherId', isEqualTo: user.uid)
          .where('scheduleId', isEqualTo: schedule.id)
          .where('subject', isEqualTo: schedule.subject)
          .where('timestamp', isGreaterThanOrEqualTo: classStartTimestamp)
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator(color: AppColors.primary));
        }

        if (!snapshot.hasData || snapshot.data!.docs.isEmpty) {
          return GlassCard(
            child: Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  children: [
                    Icon(Icons.hourglass_empty_rounded, size: 40, color: AppColors.textHint),
                    const SizedBox(height: 12),
                    Text(
                      'Waiting for students...',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            color: AppColors.textSecondary,
                          ),
                    ),
                  ],
                ),
              ),
            ),
          );
        }

        final attendanceList = snapshot.data!.docs
            .map((doc) => Attendance.fromFirestore(doc))
            .toList()
          ..sort((a, b) => b.timestamp.compareTo(a.timestamp));

        return GlassCard(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    '${attendanceList.length} students present',
                    style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          color: AppColors.statusPresent,
                          fontWeight: FontWeight.w600,
                        ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppColors.statusPresentContainer,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      'LIVE',
                      style: TextStyle(
                        color: AppColors.statusPresent,
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              ...attendanceList.take(10).map((attendance) => _AttendanceItem(attendance: attendance)),
              if (attendanceList.length > 10)
                Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: Text(
                    '+${attendanceList.length - 10} more students',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: AppColors.textSecondary,
                        ),
                  ),
                ),
            ],
          ),
        );
      },
    );
  }
}

class _AttendanceItem extends StatelessWidget {
  final Attendance attendance;

  const _AttendanceItem({required this.attendance});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surfaceVariant,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              gradient: LinearGradient(colors: AppColors.accentGradient),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Center(
              child: Text(
                attendance.studentName.isNotEmpty
                    ? attendance.studentName[0].toUpperCase()
                    : 'S',
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.bold,
                  fontSize: 16,
                ),
              ),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  attendance.studentName,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                ),
                const SizedBox(height: 2),
                Text(
                  attendance.formattedTime,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: AppColors.textSecondary,
                      ),
                ),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: AppColors.getStatusContainerColor(attendance.status.name),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(
              attendance.status.name.toUpperCase(),
              style: TextStyle(
                color: AppColors.getStatusColor(attendance.status.name),
                fontSize: 10,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
