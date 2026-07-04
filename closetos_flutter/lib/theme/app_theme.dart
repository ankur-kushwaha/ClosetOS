import 'package:flutter/material.dart';

/// Strict black-and-white professional theme.
class AppColors {
  static const black = Color(0xFF000000);
  static const white = Color(0xFFFFFFFF);
  static const gray100 = Color(0xFFF5F5F5);
  static const gray200 = Color(0xFFE5E5E5);
  static const gray400 = Color(0xFF9CA3AF);
  static const gray600 = Color(0xFF4B5563);
  static const gray800 = Color(0xFF1F1F1F);
}

class AppTheme {
  static ThemeData get dark {
    const scheme = ColorScheme.dark(
      surface: AppColors.black,
      onSurface: AppColors.white,
      primary: AppColors.white,
      onPrimary: AppColors.black,
      secondary: AppColors.gray400,
      onSecondary: AppColors.black,
      outline: AppColors.gray600,
      surfaceContainerHighest: AppColors.gray800,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: scheme,
      scaffoldBackgroundColor: AppColors.black,
      fontFamily: 'Helvetica Neue',
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.black,
        foregroundColor: AppColors.white,
        elevation: 0,
        centerTitle: true,
        titleTextStyle: TextStyle(
          color: AppColors.white,
          fontSize: 17,
          fontWeight: FontWeight.w600,
          letterSpacing: 1.2,
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: AppColors.black,
        indicatorColor: AppColors.white,
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return TextStyle(
            fontSize: 11,
            fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
            color: selected ? AppColors.white : AppColors.gray400,
          );
        }),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return IconThemeData(
            color: selected ? AppColors.black : AppColors.gray400,
            size: 22,
          );
        }),
      ),
      dividerTheme: const DividerThemeData(
        color: AppColors.gray800,
        thickness: 1,
      ),
      cardTheme: CardThemeData(
        color: AppColors.gray800,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(2),
          side: const BorderSide(color: AppColors.gray600, width: 0.5),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.gray800,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(2),
          borderSide: const BorderSide(color: AppColors.gray600),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(2),
          borderSide: const BorderSide(color: AppColors.gray600),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(2),
          borderSide: const BorderSide(color: AppColors.white),
        ),
        labelStyle: const TextStyle(color: AppColors.gray400),
        hintStyle: const TextStyle(color: AppColors.gray600),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.white,
          foregroundColor: AppColors.black,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2)),
          textStyle: const TextStyle(
            fontWeight: FontWeight.w600,
            letterSpacing: 0.5,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.white,
          side: const BorderSide(color: AppColors.gray600),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2)),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(foregroundColor: AppColors.gray400),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: AppColors.gray800,
        selectedColor: AppColors.white,
        labelStyle: const TextStyle(fontSize: 12),
        side: const BorderSide(color: AppColors.gray600),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2)),
      ),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: AppColors.white,
      ),
      snackBarTheme: const SnackBarThemeData(
        backgroundColor: AppColors.gray800,
        contentTextStyle: TextStyle(color: AppColors.white),
      ),
    );
  }
}
