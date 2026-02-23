import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/services/auth_service.dart';
import '../../../core/services/firestore_service.dart';
import '../../../core/models/schedule.dart';
import '../../../core/widgets/gradient_card.dart';

class StudentScheduleScreen extends StatefulWidget {
  const StudentScheduleScreen({super.key});

  @override
  State<StudentScheduleScreen> createState() => _StudentScheduleScreenState();
}

class _StudentScheduleScreenState extends State<StudentScheduleScreen> {
  final List<String> _days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  int _selectedDayIndex = 0;
  List<Schedule> _schedules = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _selectedDayIndex = DateTime.now().weekday - 1;
    if (_selectedDayIndex > 5) _selectedDayIndex = 0;
    _loadSchedules();
  }

  Future<void> _loadSchedules() async {
    setState(() => _isLoading = true);
    final authService = context.read<AuthService>();
    final firestoreService = context.read<FirestoreService>();
    final user = authService.currentUser;
    if (user == null) return;

    final userData = await firestoreService.getUserData(user.uid);
    final section = userData?['section'] ?? '';

    final schedules = await firestoreService.getSchedulesForSection(
      section,
      _days[_selectedDayIndex],
    );

    if (mounted) {
      setState(() {
        _schedules = schedules..sort((a, b) => a.startTimeInMinutes.compareTo(b.startTimeInMinutes));
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildHeader(),
            _buildDaySelector(),
            Expanded(
              child: _isLoading
                  ? const Center(child: CircularProgressIndicator(color: AppColors.primary))
                  : _schedules.isEmpty
                      ? _buildEmptyState()
                      : _buildScheduleList(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Schedule',
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
          ),
          const SizedBox(height: 4),
          Text(
            DateFormat('EEEE, MMMM d').format(DateTime.now()),
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: AppColors.textSecondary,
                ),
          ),
        ],
      ),
    );
  }

  Widget _buildDaySelector() {
    return Container(
      height: 80,
      margin: const EdgeInsets.only(top: 16),
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        itemCount: _days.length,
        itemBuilder: (context, index) {
          final isSelected = _selectedDayIndex == index;
          final isToday = DateTime.now().weekday - 1 == index;
          return GestureDetector(
            onTap: () {
              setState(() => _selectedDayIndex = index);
              _loadSchedules();
            },
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 250),
              width: 65,
              margin: const EdgeInsets.symmetric(horizontal: 4),
              decoration: BoxDecoration(
                gradient: isSelected ? LinearGradient(colors: AppColors.primaryGradient) : null,
                color: isSelected ? null : AppColors.cardBackground,
                borderRadius: BorderRadius.circular(18),
                border: isToday && !isSelected
                    ? Border.all(color: AppColors.primary.withOpacity(0.5), width: 2)
                    : Border.all(color: isSelected ? Colors.transparent : AppColors.cardBorder),
                boxShadow: isSelected
                    ? [
                        BoxShadow(
                          color: AppColors.primary.withOpacity(0.3),
                          blurRadius: 10,
                          offset: const Offset(0, 4),
                        ),
                      ]
                    : null,
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    _days[index].substring(0, 3),
                    style: TextStyle(
                      color: isSelected ? Colors.white : AppColors.textSecondary,
                      fontSize: 13,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  const SizedBox(height: 4),
                  if (isToday)
                    Container(
                      width: 6,
                      height: 6,
                      decoration: BoxDecoration(
                        color: isSelected ? Colors.white : AppColors.primary,
                        shape: BoxShape.circle,
                      ),
                    ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: AppColors.surfaceVariant,
              shape: BoxShape.circle,
            ),
            child: Icon(
              Icons.event_busy_rounded,
              size: 48,
              color: AppColors.textHint,
            ),
          ),
          const SizedBox(height: 20),
          Text(
            'No classes',
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: AppColors.textSecondary,
                ),
          ),
          const SizedBox(height: 8),
          Text(
            'No classes scheduled for ${_days[_selectedDayIndex]}',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: AppColors.textHint,
                ),
          ),
        ],
      ),
    );
  }

  Widget _buildScheduleList() {
    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 100),
      itemCount: _schedules.length,
      itemBuilder: (context, index) {
        return _ScheduleCard(
          schedule: _schedules[index],
          index: index,
        );
      },
    );
  }
}

class _ScheduleCard extends StatelessWidget {
  final Schedule schedule;
  final int index;

  const _ScheduleCard({
    required this.schedule,
    required this.index,
  });

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final nowMinutes = now.hour * 60 + now.minute;
    final isActive = schedule.isCurrentlyActive(nowMinutes);
    final isPast = schedule.endTimeInMinutes < nowMinutes;

    final gradients = [
      AppColors.primaryGradient,
      AppColors.accentGradient,
      AppColors.warmGradient,
      [AppColors.secondary, AppColors.accent],
      AppColors.successGradient,
    ];

    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: Duration(milliseconds: 300 + (index * 80)),
      curve: Curves.easeOutCubic,
      builder: (context, value, child) {
        return Transform.translate(
          offset: Offset(30 * (1 - value), 0),
          child: Opacity(opacity: value, child: child),
        );
      },
      child: Container(
        margin: const EdgeInsets.only(bottom: 16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 60,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    schedule.formattedStartTime,
                    style: TextStyle(
                      color: isPast ? AppColors.textHint : AppColors.textPrimary,
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    schedule.formattedEndTime,
                    style: TextStyle(
                      color: AppColors.textHint,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 16),
            Column(
              children: [
                Container(
                  width: 14,
                  height: 14,
                  decoration: BoxDecoration(
                    gradient: isActive
                        ? LinearGradient(colors: gradients[index % gradients.length])
                        : null,
                    color: isActive ? null : (isPast ? AppColors.textHint : AppColors.cardBorder),
                    shape: BoxShape.circle,
                    boxShadow: isActive
                        ? [
                            BoxShadow(
                              color: gradients[index % gradients.length].first.withOpacity(0.5),
                              blurRadius: 8,
                            ),
                          ]
                        : null,
                  ),
                ),
                Container(
                  width: 2,
                  height: 90,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        isPast ? AppColors.textHint : AppColors.cardBorder,
                        AppColors.cardBorder.withOpacity(0.3),
                      ],
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  gradient: isActive
                      ? LinearGradient(
                          colors: gradients[index % gradients.length],
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                        )
                      : null,
                  color: isActive ? null : AppColors.cardBackground,
                  borderRadius: BorderRadius.circular(20),
                  border: isActive
                      ? null
                      : Border.all(
                          color: isPast ? AppColors.cardBorder.withOpacity(0.5) : AppColors.cardBorder,
                        ),
                  boxShadow: isActive
                      ? [
                          BoxShadow(
                            color: gradients[index % gradients.length].first.withOpacity(0.3),
                            blurRadius: 15,
                            offset: const Offset(0, 8),
                          ),
                        ]
                      : null,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (isActive)
                      Container(
                        margin: const EdgeInsets.only(bottom: 10),
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Text(
                          'NOW',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 1,
                          ),
                        ),
                      ),
                    Text(
                      schedule.subject,
                      style: TextStyle(
                        color: isActive ? Colors.white : (isPast ? AppColors.textSecondary : AppColors.textPrimary),
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Icon(
                          Icons.location_on_outlined,
                          size: 14,
                          color: isActive ? Colors.white.withOpacity(0.8) : AppColors.textHint,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          schedule.room,
                          style: TextStyle(
                            color: isActive ? Colors.white.withOpacity(0.8) : AppColors.textSecondary,
                            fontSize: 13,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
