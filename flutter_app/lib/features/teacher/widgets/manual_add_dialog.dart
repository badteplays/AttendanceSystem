import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/services/auth_service.dart';
import '../../../core/services/firestore_service.dart';
import '../../../core/models/schedule.dart';

class ManualAddDialog extends StatefulWidget {
  final Schedule schedule;

  const ManualAddDialog({super.key, required this.schedule});

  @override
  State<ManualAddDialog> createState() => _ManualAddDialogState();
}

class _ManualAddDialogState extends State<ManualAddDialog> {
  final _studentIdController = TextEditingController();
  final _studentNameController = TextEditingController();
  bool _isLoading = false;
  String? _errorMessage;

  @override
  void dispose() {
    _studentIdController.dispose();
    _studentNameController.dispose();
    super.dispose();
  }

  Future<void> _addStudent() async {
    if (_studentNameController.text.trim().isEmpty) {
      setState(() => _errorMessage = 'Please enter student name');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final authService = context.read<AuthService>();
      final firestoreService = context.read<FirestoreService>();
      final teacher = authService.currentUser;
      if (teacher == null) throw Exception('Not authenticated');

      await firestoreService.addManualAttendance(
        teacherId: teacher.uid,
        studentName: _studentNameController.text.trim(),
        studentId: _studentIdController.text.trim(),
        scheduleId: widget.schedule.id,
        subject: widget.schedule.subject,
        section: widget.schedule.section,
      );

      if (mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: [
                Icon(Icons.check_circle_rounded, color: AppColors.statusPresent),
                const SizedBox(width: 12),
                Text('${_studentNameController.text.trim()} added successfully'),
              ],
            ),
            backgroundColor: AppColors.cardBackground,
          ),
        );
      }
    } catch (e) {
      setState(() {
        _isLoading = false;
        _errorMessage = e.toString().replaceAll('Exception: ', '');
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: AppColors.cardBackground,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    gradient: LinearGradient(colors: AppColors.accentGradient),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: const Icon(
                    Icons.person_add_rounded,
                    color: Colors.white,
                    size: 24,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Add Student',
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                              fontWeight: FontWeight.w700,
                            ),
                      ),
                      Text(
                        widget.schedule.subject,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: AppColors.textSecondary,
                            ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 28),
            TextField(
              controller: _studentNameController,
              style: const TextStyle(color: AppColors.textPrimary),
              decoration: InputDecoration(
                labelText: 'Student Name',
                prefixIcon: Icon(Icons.person_outline_rounded, color: AppColors.textHint),
                errorText: _errorMessage,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _studentIdController,
              style: const TextStyle(color: AppColors.textPrimary),
              decoration: InputDecoration(
                labelText: 'Student ID (Optional)',
                prefixIcon: Icon(Icons.badge_outlined, color: AppColors.textHint),
              ),
            ),
            const SizedBox(height: 28),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.pop(context),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    child: const Text('Cancel'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(colors: AppColors.successGradient),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: ElevatedButton(
                      onPressed: _isLoading ? null : _addStudent,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.transparent,
                        shadowColor: Colors.transparent,
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      child: _isLoading
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Text('Add'),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
