import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/services/auth_service.dart';
import '../../../core/widgets/gradient_card.dart';
import '../../../core/widgets/animated_counter.dart';

class TeacherAnalyticsScreen extends StatefulWidget {
  const TeacherAnalyticsScreen({super.key});

  @override
  State<TeacherAnalyticsScreen> createState() => _TeacherAnalyticsScreenState();
}

class _TeacherAnalyticsScreenState extends State<TeacherAnalyticsScreen> {
  int _totalPresent = 0;
  int _totalLate = 0;
  int _totalAbsent = 0;
  int _totalExcused = 0;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadStats();
  }

  Future<void> _loadStats() async {
    setState(() => _isLoading = true);
    final authService = context.read<AuthService>();
    final user = authService.currentUser;
    if (user == null) return;

    final now = DateTime.now();
    final monthStart = DateTime(now.year, now.month, 1);
    final monthStartTimestamp = Timestamp.fromDate(monthStart);

    final snapshot = await FirebaseFirestore.instance
        .collection('attendance')
        .where('teacherId', isEqualTo: user.uid)
        .where('timestamp', isGreaterThanOrEqualTo: monthStartTimestamp)
        .get();

    final archivedSnapshot = await FirebaseFirestore.instance
        .collection('archived_attendance')
        .where('teacherId', isEqualTo: user.uid)
        .get();

    final archivedFromMonth = archivedSnapshot.docs.where((doc) {
      final archivedAt = doc.data()['archivedAt'] as int? ?? 0;
      return archivedAt >= monthStart.millisecondsSinceEpoch;
    });

    int present = 0, late = 0, absent = 0, excused = 0;

    for (final doc in [...snapshot.docs, ...archivedFromMonth]) {
      final status = (doc.data() as Map<String, dynamic>)['status']?.toString().toUpperCase() ?? 'PRESENT';
      switch (status) {
        case 'PRESENT':
          present++;
          break;
        case 'LATE':
          late++;
          break;
        case 'ABSENT':
        case 'CUTTING':
          absent++;
          break;
        case 'EXCUSED':
          excused++;
          break;
      }
    }

    if (mounted) {
      setState(() {
        _totalPresent = present;
        _totalLate = late;
        _totalAbsent = absent;
        _totalExcused = excused;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final total = _totalPresent + _totalLate + _totalAbsent + _totalExcused;
    final attendanceRate = total > 0 ? ((_totalPresent + _totalExcused) / total * 100) : 0.0;

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: _isLoading
            ? const Center(child: CircularProgressIndicator(color: AppColors.primary))
            : RefreshIndicator(
                onRefresh: _loadStats,
                color: AppColors.primary,
                backgroundColor: AppColors.cardBackground,
                child: SingleChildScrollView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildHeader(),
                      const SizedBox(height: 24),
                      _buildOverviewCard(total, attendanceRate),
                      const SizedBox(height: 24),
                      _buildStatsSection(),
                    ],
                  ),
                ),
              ),
      ),
    );
  }

  Widget _buildHeader() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Analytics',
          style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                fontWeight: FontWeight.w700,
              ),
        ),
        const SizedBox(height: 4),
        Text(
          'Monthly attendance overview',
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: AppColors.textSecondary,
              ),
        ),
      ],
    );
  }

  Widget _buildOverviewCard(int total, double attendanceRate) {
    return GradientCard(
      gradient: AppColors.primaryGradient,
      padding: const EdgeInsets.all(0),
      child: Stack(
        children: [
          Positioned(
            right: -50,
            top: -50,
            child: Container(
              width: 180,
              height: 180,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withOpacity(0.1),
              ),
            ),
          ),
          Positioned(
            left: -30,
            bottom: -30,
            child: Container(
              width: 120,
              height: 120,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withOpacity(0.05),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.2),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(
                        Icons.insights_rounded,
                        color: Colors.white,
                        size: 22,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Text(
                      'This Month',
                      style: TextStyle(
                        color: Colors.white.withOpacity(0.8),
                        fontSize: 15,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 28),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        AnimatedPercentage(
                          value: attendanceRate,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 48,
                            fontWeight: FontWeight.w700,
                            height: 1,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Attendance Rate',
                          style: TextStyle(
                            color: Colors.white.withOpacity(0.7),
                            fontSize: 14,
                          ),
                        ),
                      ],
                    ),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        AnimatedCounter(
                          value: total,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 36,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Total Records',
                          style: TextStyle(
                            color: Colors.white.withOpacity(0.7),
                            fontSize: 14,
                          ),
                        ),
                      ],
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

  Widget _buildStatsSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Breakdown',
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w700,
              ),
        ),
        const SizedBox(height: 16),
        GridView.count(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          crossAxisCount: 2,
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 1.1,
          children: [
            _StatCard(
              count: _totalPresent,
              label: 'Present',
              icon: Icons.check_circle_rounded,
              gradient: AppColors.successGradient,
            ),
            _StatCard(
              count: _totalLate,
              label: 'Late',
              icon: Icons.schedule_rounded,
              gradient: AppColors.warmGradient,
            ),
            _StatCard(
              count: _totalAbsent,
              label: 'Absent',
              icon: Icons.cancel_rounded,
              gradient: [AppColors.error, AppColors.errorDark],
            ),
            _StatCard(
              count: _totalExcused,
              label: 'Excused',
              icon: Icons.event_available_rounded,
              gradient: AppColors.accentGradient,
            ),
          ],
        ),
      ],
    );
  }
}

class _StatCard extends StatelessWidget {
  final int count;
  final String label;
  final IconData icon;
  final List<Color> gradient;

  const _StatCard({
    required this.count,
    required this.label,
    required this.icon,
    required this.gradient,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.cardBackground,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: AppColors.cardBorder),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              gradient: LinearGradient(colors: gradient),
              borderRadius: BorderRadius.circular(14),
              boxShadow: [
                BoxShadow(
                  color: gradient.first.withOpacity(0.3),
                  blurRadius: 10,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Icon(icon, color: Colors.white, size: 24),
          ),
          const Spacer(),
          AnimatedCounter(
            value: count,
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: AppColors.textSecondary,
                ),
          ),
        ],
      ),
    );
  }
}
