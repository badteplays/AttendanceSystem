import 'package:flutter/material.dart';
import '../../../core/theme/app_colors.dart';

class AuthTextField extends StatelessWidget {
  final TextEditingController controller;
  final String hintText;
  final bool obscureText;
  final TextInputType keyboardType;
  final IconData? prefixIcon;
  final Widget? suffixIcon;
  final TextCapitalization textCapitalization;

  const AuthTextField({
    super.key,
    required this.controller,
    required this.hintText,
    this.obscureText = false,
    this.keyboardType = TextInputType.text,
    this.prefixIcon,
    this.suffixIcon,
    this.textCapitalization = TextCapitalization.none,
  });

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      obscureText: obscureText,
      keyboardType: keyboardType,
      textCapitalization: textCapitalization,
      style: const TextStyle(
        color: AppColors.textPrimary,
        fontSize: 16,
      ),
      decoration: InputDecoration(
        hintText: hintText,
        prefixIcon: prefixIcon != null
            ? Icon(prefixIcon, color: AppColors.textHint)
            : null,
        suffixIcon: suffixIcon,
      ),
    );
  }
}
