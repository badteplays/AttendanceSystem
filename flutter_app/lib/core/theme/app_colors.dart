import 'package:flutter/material.dart';

class AppColors {
  static const Color primary = Color(0xFF6366F1);
  static const Color primaryDark = Color(0xFF4F46E5);
  static const Color primaryLight = Color(0xFF818CF8);
  static const Color primaryContainer = Color(0xFF1E1B4B);
  
  static const Color secondary = Color(0xFFEC4899);
  static const Color secondaryDark = Color(0xFFDB2777);
  static const Color secondaryLight = Color(0xFFF472B6);
  static const Color secondaryContainer = Color(0xFF4A1942);

  static const Color accent = Color(0xFF06B6D4);
  static const Color accentDark = Color(0xFF0891B2);
  static const Color accentContainer = Color(0xFF164E63);

  static const Color background = Color(0xFF0A0A0F);
  static const Color backgroundSecondary = Color(0xFF12121A);
  static const Color surface = Color(0xFF16161F);
  static const Color surfaceVariant = Color(0xFF1E1E2A);
  static const Color surfaceContainer = Color(0xFF1A1A24);
  static const Color surfaceElevated = Color(0xFF222230);

  static const Color cardBackground = Color(0xFF14141C);
  static const Color cardBackgroundElevated = Color(0xFF1C1C28);
  static const Color cardBorder = Color(0xFF2A2A3C);
  static const Color dialogBackground = Color(0xFF18181F);

  static const Color textPrimary = Color(0xFFF8FAFC);
  static const Color textSecondary = Color(0xFF94A3B8);
  static const Color textHint = Color(0xFF64748B);
  static const Color textDisabled = Color(0xFF475569);

  static const Color divider = Color(0xFF1E293B);
  static const Color outline = Color(0xFF334155);
  static const Color outlineVariant = Color(0xFF1E293B);

  static const Color error = Color(0xFFF43F5E);
  static const Color errorDark = Color(0xFFE11D48);
  static const Color errorContainer = Color(0xFF4C0519);

  static const Color statusPresent = Color(0xFF10B981);
  static const Color statusPresentContainer = Color(0xFF064E3B);
  static const Color statusLate = Color(0xFFF59E0B);
  static const Color statusLateContainer = Color(0xFF78350F);
  static const Color statusAbsent = Color(0xFFF43F5E);
  static const Color statusAbsentContainer = Color(0xFF4C0519);
  static const Color statusExcused = Color(0xFF6366F1);
  static const Color statusExcusedContainer = Color(0xFF1E1B4B);
  static const Color statusCutting = Color(0xFFF97316);
  static const Color statusCuttingContainer = Color(0xFF7C2D12);

  static const Color success = Color(0xFF10B981);
  static const Color successContainer = Color(0xFF064E3B);
  static const Color warning = Color(0xFFF59E0B);
  static const Color warningContainer = Color(0xFF78350F);

  static const Color bottomNavBackground = Color(0xFF0A0A0F);
  static const Color bottomNavSelected = Color(0xFF6366F1);
  static const Color bottomNavUnselected = Color(0xFF64748B);
  static const Color bottomNavIndicator = Color(0xFF1E1B4B);

  static const Color shimmerBase = Color(0xFF1E1E2A);
  static const Color shimmerHighlight = Color(0xFF2A2A3C);

  static List<Color> get primaryGradient => [
    const Color(0xFF6366F1),
    const Color(0xFF8B5CF6),
    const Color(0xFFEC4899),
  ];

  static List<Color> get accentGradient => [
    const Color(0xFF06B6D4),
    const Color(0xFF6366F1),
  ];

  static List<Color> get successGradient => [
    const Color(0xFF10B981),
    const Color(0xFF06B6D4),
  ];

  static List<Color> get warmGradient => [
    const Color(0xFFF59E0B),
    const Color(0xFFF97316),
    const Color(0xFFEC4899),
  ];

  static Color getStatusColor(String status) {
    switch (status.toUpperCase()) {
      case 'PRESENT':
        return statusPresent;
      case 'LATE':
        return statusLate;
      case 'ABSENT':
        return statusAbsent;
      case 'EXCUSED':
        return statusExcused;
      case 'CUTTING':
        return statusCutting;
      default:
        return textSecondary;
    }
  }

  static Color getStatusContainerColor(String status) {
    switch (status.toUpperCase()) {
      case 'PRESENT':
        return statusPresentContainer;
      case 'LATE':
        return statusLateContainer;
      case 'ABSENT':
        return statusAbsentContainer;
      case 'EXCUSED':
        return statusExcusedContainer;
      case 'CUTTING':
        return statusCuttingContainer;
      default:
        return surfaceVariant;
    }
  }
}
