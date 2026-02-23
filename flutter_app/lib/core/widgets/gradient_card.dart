import 'package:flutter/material.dart';
import '../theme/app_colors.dart';

class GradientCard extends StatelessWidget {
  final Widget child;
  final List<Color>? gradient;
  final EdgeInsets? padding;
  final double borderRadius;
  final VoidCallback? onTap;

  const GradientCard({
    super.key,
    required this.child,
    this.gradient,
    this.padding,
    this.borderRadius = 24,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: padding ?? const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: gradient ?? AppColors.primaryGradient,
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(borderRadius),
          boxShadow: [
            BoxShadow(
              color: (gradient?.first ?? AppColors.primary).withOpacity(0.3),
              blurRadius: 20,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: child,
      ),
    );
  }
}

class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsets? padding;
  final double borderRadius;
  final VoidCallback? onTap;
  final Color? borderColor;

  const GlassCard({
    super.key,
    required this.child,
    this.padding,
    this.borderRadius = 20,
    this.onTap,
    this.borderColor,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: padding ?? const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: AppColors.cardBackground,
          borderRadius: BorderRadius.circular(borderRadius),
          border: Border.all(
            color: borderColor ?? AppColors.cardBorder,
            width: 1,
          ),
        ),
        child: child,
      ),
    );
  }
}

class AnimatedGlassCard extends StatefulWidget {
  final Widget child;
  final EdgeInsets? padding;
  final double borderRadius;
  final VoidCallback? onTap;

  const AnimatedGlassCard({
    super.key,
    required this.child,
    this.padding,
    this.borderRadius = 20,
    this.onTap,
  });

  @override
  State<AnimatedGlassCard> createState() => _AnimatedGlassCardState();
}

class _AnimatedGlassCardState extends State<AnimatedGlassCard> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => setState(() => _isPressed = true),
      onTapUp: (_) => setState(() => _isPressed = false),
      onTapCancel: () => setState(() => _isPressed = false),
      onTap: widget.onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: widget.padding ?? const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: _isPressed ? AppColors.surfaceVariant : AppColors.cardBackground,
          borderRadius: BorderRadius.circular(widget.borderRadius),
          border: Border.all(
            color: _isPressed ? AppColors.primary.withOpacity(0.5) : AppColors.cardBorder,
            width: 1,
          ),
        ),
        transform: Matrix4.identity()..scale(_isPressed ? 0.98 : 1.0),
        child: widget.child,
      ),
    );
  }
}
