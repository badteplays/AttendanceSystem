import 'package:flutter/material.dart';
import '../../../core/theme/app_colors.dart';
import 'teacher_dashboard_screen.dart';
import 'teacher_schedules_screen.dart';
import 'teacher_analytics_screen.dart';
import 'teacher_options_screen.dart';

class TeacherMainScreen extends StatefulWidget {
  const TeacherMainScreen({super.key});

  @override
  State<TeacherMainScreen> createState() => _TeacherMainScreenState();
}

class _TeacherMainScreenState extends State<TeacherMainScreen> {
  int _currentIndex = 0;

  final List<Widget> _screens = const [
    TeacherDashboardScreen(),
    TeacherSchedulesScreen(),
    TeacherAnalyticsScreen(),
    TeacherOptionsScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: IndexedStack(
        index: _currentIndex,
        children: _screens,
      ),
      bottomNavigationBar: _buildBottomNav(),
    );
  }

  Widget _buildBottomNav() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.background,
        border: Border(
          top: BorderSide(color: AppColors.divider.withOpacity(0.5), width: 1),
        ),
      ),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Container(
            padding: const EdgeInsets.all(6),
            decoration: BoxDecoration(
              color: AppColors.surfaceVariant,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _NavItem(
                  icon: Icons.home_rounded,
                  label: 'Home',
                  isSelected: _currentIndex == 0,
                  onTap: () => setState(() => _currentIndex = 0),
                  gradient: AppColors.primaryGradient,
                ),
                _NavItem(
                  icon: Icons.calendar_today_rounded,
                  label: 'Schedule',
                  isSelected: _currentIndex == 1,
                  onTap: () => setState(() => _currentIndex = 1),
                  gradient: AppColors.accentGradient,
                ),
                _NavItem(
                  icon: Icons.analytics_rounded,
                  label: 'Analytics',
                  isSelected: _currentIndex == 2,
                  onTap: () => setState(() => _currentIndex = 2),
                  gradient: AppColors.warmGradient,
                ),
                _NavItem(
                  icon: Icons.person_rounded,
                  label: 'Profile',
                  isSelected: _currentIndex == 3,
                  onTap: () => setState(() => _currentIndex = 3),
                  gradient: [AppColors.secondary, AppColors.accent],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool isSelected;
  final VoidCallback onTap;
  final List<Color> gradient;

  const _NavItem({
    required this.icon,
    required this.label,
    required this.isSelected,
    required this.onTap,
    required this.gradient,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeOutCubic,
        padding: EdgeInsets.symmetric(
          horizontal: isSelected ? 20 : 16,
          vertical: 10,
        ),
        decoration: BoxDecoration(
          gradient: isSelected ? LinearGradient(colors: gradient) : null,
          borderRadius: BorderRadius.circular(14),
          boxShadow: isSelected
              ? [
                  BoxShadow(
                    color: gradient.first.withOpacity(0.3),
                    blurRadius: 10,
                    offset: const Offset(0, 4),
                  ),
                ]
              : null,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              color: isSelected ? Colors.white : AppColors.textHint,
              size: 22,
            ),
            AnimatedSize(
              duration: const Duration(milliseconds: 200),
              child: isSelected
                  ? Padding(
                      padding: const EdgeInsets.only(left: 8),
                      child: Text(
                        label,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    )
                  : const SizedBox.shrink(),
            ),
          ],
        ),
      ),
    );
  }
}
