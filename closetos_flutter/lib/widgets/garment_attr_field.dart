import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

class GarmentAttrField extends StatelessWidget {
  const GarmentAttrField({
    super.key,
    required this.label,
    required this.controller,
    required this.hint,
  });

  final String label;
  final TextEditingController controller;
  final String hint;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: AppTypography.ui(
            fontSize: 11,
            fontWeight: FontWeight.w500,
            color: AppColors.ink400,
          ),
        ),
        const SizedBox(height: 4),
        TextField(
          controller: controller,
          style: AppTypography.ui(fontSize: 13, color: AppColors.ink900),
          decoration: InputDecoration(
            hintText: hint,
            hintStyle: AppTypography.ui(fontSize: 13, color: AppColors.ink400),
            isDense: true,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 12,
              vertical: 10,
            ),
            filled: true,
            fillColor: AppColors.greige,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide.none,
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide.none,
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide(
                color: AppColors.clay500.withValues(alpha: 0.5),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
