import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// ClosetOS warm bone & clay palette.
class AppColors {
  static const canvas = Color(0xFFF5F0E8);
  static const surface = Color(0xFFFCFAF5);
  static const greige = Color(0xFFE9E1D5);
  static const border = Color(0xFFDFD6C8);
  static const ink900 = Color(0xFF2C2723);
  static const ink600 = Color(0xFF6E655B);
  static const ink400 = Color(0xFF9C9285);
  static const clay500 = Color(0xFFB26A4E);
  static const clay700 = Color(0xFF914F37);
  static const clay100 = Color(0xFFEEDDD2);
  static const success = Color(0xFF6F8360);
  static const error = Color(0xFFA84A39);
  static const warning = Color(0xFFC08A3E);

  // Legacy aliases — mapped to ClosetOS tokens for gradual screen migration.
  static const black = ink900;
  static const white = surface;
  static const gray100 = greige;
  static const gray200 = border;
  static const gray400 = ink400;
  static const gray600 = ink600;
  static const gray800 = greige;
}

class AppTypography {
  static TextStyle display({
    double fontSize = 28,
    Color color = AppColors.surface,
    FontWeight fontWeight = FontWeight.w500,
    double height = 1.15,
  }) =>
      GoogleFonts.fraunces(
        fontSize: fontSize,
        fontWeight: fontWeight,
        height: height,
        color: color,
        letterSpacing: -0.3,
      );

  static TextStyle ui({
    double fontSize = 14,
    Color color = AppColors.ink900,
    FontWeight fontWeight = FontWeight.w400,
    double? letterSpacing,
    double? height,
  }) =>
      GoogleFonts.inter(
        fontSize: fontSize,
        fontWeight: fontWeight,
        color: color,
        letterSpacing: letterSpacing,
        height: height,
      );

  static TextStyle label({
    double fontSize = 11,
    Color color = AppColors.ink400,
    FontWeight fontWeight = FontWeight.w500,
    double letterSpacing = 1.2,
  }) =>
      GoogleFonts.inter(
        fontSize: fontSize,
        fontWeight: fontWeight,
        color: color,
        letterSpacing: letterSpacing,
      );
}

class AppTheme {
  static ThemeData get light {
    const scheme = ColorScheme.light(
      surface: AppColors.canvas,
      onSurface: AppColors.ink900,
      primary: AppColors.clay500,
      onPrimary: AppColors.surface,
      secondary: AppColors.ink600,
      onSecondary: AppColors.surface,
      outline: AppColors.border,
      surfaceContainerHighest: AppColors.surface,
      error: AppColors.error,
    );

    final baseText = GoogleFonts.interTextTheme();

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      colorScheme: scheme,
      scaffoldBackgroundColor: AppColors.canvas,
      textTheme: baseText.apply(
        bodyColor: AppColors.ink900,
        displayColor: AppColors.ink900,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: AppColors.canvas,
        foregroundColor: AppColors.ink900,
        elevation: 0,
        centerTitle: true,
        titleTextStyle: AppTypography.ui(
          fontSize: 15,
          fontWeight: FontWeight.w600,
          letterSpacing: 2,
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: AppColors.surface,
        indicatorColor: AppColors.clay100,
        height: 64,
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return AppTypography.ui(
            fontSize: 11,
            fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
            color: selected ? AppColors.clay500 : AppColors.ink400,
          );
        }),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return IconThemeData(
            color: selected ? AppColors.clay500 : AppColors.ink400,
            size: 22,
          );
        }),
      ),
      dividerTheme: const DividerThemeData(
        color: AppColors.border,
        thickness: 1,
      ),
      cardTheme: CardThemeData(
        color: AppColors.surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: const BorderSide(color: AppColors.border),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.surface,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(24),
          borderSide: const BorderSide(color: AppColors.border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(24),
          borderSide: const BorderSide(color: AppColors.border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(24),
          borderSide: const BorderSide(color: AppColors.clay500, width: 1.5),
        ),
        labelStyle: AppTypography.ui(fontSize: 13, color: AppColors.ink400),
        hintStyle: AppTypography.ui(fontSize: 13, color: AppColors.ink400),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.clay500,
          foregroundColor: AppColors.surface,
          elevation: 0,
          minimumSize: const Size(0, 48),
          padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 14),
          shape: const StadiumBorder(),
          textStyle: AppTypography.ui(
            fontSize: 15,
            fontWeight: FontWeight.w600,
            color: AppColors.surface,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.ink600,
          side: const BorderSide(color: AppColors.border),
          minimumSize: const Size(0, 48),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          shape: const StadiumBorder(),
          textStyle: AppTypography.ui(
            fontSize: 14,
            fontWeight: FontWeight.w500,
            color: AppColors.ink600,
          ),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(foregroundColor: AppColors.ink600),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: AppColors.greige,
        selectedColor: AppColors.clay100,
        labelStyle: AppTypography.ui(fontSize: 13),
        side: BorderSide.none,
        shape: const StadiumBorder(),
      ),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: AppColors.clay500,
        strokeWidth: 2,
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: AppColors.ink900,
        contentTextStyle: AppTypography.ui(color: AppColors.surface),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }

  /// Kept for reference — dark mode is a later pass.
  static ThemeData get dark => light;
}
