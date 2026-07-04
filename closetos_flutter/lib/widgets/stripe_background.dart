import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// Subtle diagonal stripe texture used on canvas and card surfaces.
class StripeBackground extends StatelessWidget {
  const StripeBackground({
    super.key,
    this.child,
    this.baseColor = AppColors.canvas,
    this.stripeColor,
    this.opacity = 0.55,
  });

  final Widget? child;
  final Color baseColor;
  final Color? stripeColor;
  final double opacity;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _StripePainter(
        baseColor: baseColor,
        stripeColor: stripeColor ?? AppColors.border,
        opacity: opacity,
      ),
      child: child,
    );
  }
}

class _StripePainter extends CustomPainter {
  _StripePainter({
    required this.baseColor,
    required this.stripeColor,
    required this.opacity,
  });

  final Color baseColor;
  final Color stripeColor;
  final double opacity;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Offset.zero & size, Paint()..color = baseColor);

    final paint = Paint()
      ..color = stripeColor.withValues(alpha: opacity)
      ..strokeWidth = 1;

    const spacing = 14.0;
    final diagonal = size.width + size.height;

    for (var i = -diagonal; i < diagonal; i += spacing) {
      canvas.drawLine(
        Offset(i, size.height),
        Offset(i + size.height, 0),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _StripePainter oldDelegate) =>
      oldDelegate.baseColor != baseColor ||
      oldDelegate.stripeColor != stripeColor ||
      oldDelegate.opacity != opacity;
}
