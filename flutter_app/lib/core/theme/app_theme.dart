import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app_colors.dart';

class AppTheme {
  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: AppColors.background,
      primaryColor: AppColors.primary,
      colorScheme: const ColorScheme.dark(
        primary: AppColors.primary,
        secondary: AppColors.secondary,
        tertiary: AppColors.accent,
        surface: AppColors.surface,
        error: AppColors.error,
        onPrimary: Colors.white,
        onSecondary: Colors.white,
        onSurface: AppColors.textPrimary,
        onError: Colors.white,
      ),
      textTheme: GoogleFonts.spaceGroteskTextTheme(
        const TextTheme(
          displayLarge: TextStyle(fontSize: 36, fontWeight: FontWeight.w700, color: AppColors.textPrimary, letterSpacing: -1),
          displayMedium: TextStyle(fontSize: 30, fontWeight: FontWeight.w700, color: AppColors.textPrimary, letterSpacing: -0.5),
          displaySmall: TextStyle(fontSize: 26, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
          headlineMedium: TextStyle(fontSize: 22, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
          headlineSmall: TextStyle(fontSize: 20, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
          titleLarge: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
          titleMedium: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: AppColors.textPrimary),
          titleSmall: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, color: AppColors.textPrimary),
          bodyLarge: TextStyle(fontSize: 16, color: AppColors.textPrimary, height: 1.5),
          bodyMedium: TextStyle(fontSize: 14, color: AppColors.textPrimary, height: 1.5),
          bodySmall: TextStyle(fontSize: 12, color: AppColors.textSecondary, height: 1.4),
          labelLarge: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.textPrimary, letterSpacing: 0.5),
          labelMedium: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: AppColors.textSecondary),
          labelSmall: TextStyle(fontSize: 11, color: AppColors.textSecondary, letterSpacing: 0.5),
        ),
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        centerTitle: false,
        iconTheme: const IconThemeData(color: AppColors.textPrimary),
        titleTextStyle: GoogleFonts.spaceGrotesk(
          fontSize: 20,
          fontWeight: FontWeight.w600,
          color: AppColors.textPrimary,
        ),
      ),
      cardTheme: CardTheme(
        color: AppColors.cardBackground,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: const BorderSide(color: AppColors.cardBorder, width: 1),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.primary,
          foregroundColor: Colors.white,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 16),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          textStyle: GoogleFonts.spaceGrotesk(fontSize: 16, fontWeight: FontWeight.w600),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.textPrimary,
          side: const BorderSide(color: AppColors.outline),
          padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 16),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          textStyle: GoogleFonts.spaceGrotesk(fontSize: 16, fontWeight: FontWeight.w600),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.surfaceVariant,
        contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.cardBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.primary, width: 2),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: AppColors.error),
        ),
        hintStyle: GoogleFonts.spaceGrotesk(color: AppColors.textHint),
        labelStyle: GoogleFonts.spaceGrotesk(color: AppColors.textSecondary),
        prefixIconColor: AppColors.textHint,
        suffixIconColor: AppColors.textHint,
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: AppColors.bottomNavBackground,
        selectedItemColor: AppColors.primary,
        unselectedItemColor: AppColors.bottomNavUnselected,
        type: BottomNavigationBarType.fixed,
        elevation: 0,
      ),
      floatingActionButtonTheme: const FloatingActionButtonThemeData(
        backgroundColor: AppColors.primary,
        foregroundColor: Colors.white,
        elevation: 0,
      ),
      dividerTheme: const DividerThemeData(
        color: AppColors.divider,
        thickness: 1,
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: AppColors.surfaceElevated,
        contentTextStyle: GoogleFonts.spaceGrotesk(color: AppColors.textPrimary),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        behavior: SnackBarBehavior.floating,
      ),
      dialogTheme: DialogTheme(
        backgroundColor: AppColors.dialogBackground,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        titleTextStyle: GoogleFonts.spaceGrotesk(
          fontSize: 20,
          fontWeight: FontWeight.w600,
          color: AppColors.textPrimary,
        ),
      ),
    );
  }
}
