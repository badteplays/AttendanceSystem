import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/services/auth_service.dart';
import '../../../core/services/firestore_service.dart';
import '../../student/screens/student_main_screen.dart';
import '../../teacher/screens/teacher_main_screen.dart';

class SignupScreen extends StatefulWidget {
  const SignupScreen({super.key});

  @override
  State<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends State<SignupScreen> with SingleTickerProviderStateMixin {
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  final _sectionController = TextEditingController();
  bool _isTeacher = false;
  bool _isLoading = false;
  bool _obscurePassword = true;
  bool _obscureConfirmPassword = true;
  int _currentStep = 0;
  late AnimationController _animController;
  late Animation<double> _fadeAnim;

  @override
  void initState() {
    super.initState();
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    );
    _fadeAnim = CurvedAnimation(parent: _animController, curve: Curves.easeOut);
    _animController.forward();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    _sectionController.dispose();
    _animController.dispose();
    super.dispose();
  }

  Future<void> _handleSignUp() async {
    if (_passwordController.text != _confirmPasswordController.text) {
      _showSnackBar('Passwords do not match', isError: true);
      return;
    }

    if (_nameController.text.trim().isEmpty ||
        _emailController.text.trim().isEmpty ||
        _passwordController.text.trim().isEmpty) {
      _showSnackBar('Please fill in all required fields', isError: true);
      return;
    }

    if (!_isTeacher && _sectionController.text.trim().isEmpty) {
      _showSnackBar('Please enter your section', isError: true);
      return;
    }

    setState(() => _isLoading = true);

    try {
      final authService = context.read<AuthService>();
      final firestoreService = context.read<FirestoreService>();

      final credential = await authService.signUp(
        _emailController.text.trim(),
        _passwordController.text.trim(),
      );

      await firestoreService.createUserData(
        uid: credential.user!.uid,
        name: _nameController.text.trim(),
        email: _emailController.text.trim(),
        isTeacher: _isTeacher,
        isStudent: !_isTeacher,
        section: _isTeacher ? null : _sectionController.text.trim().toUpperCase(),
      );

      if (!mounted) return;

      Navigator.pushAndRemoveUntil(
        context,
        MaterialPageRoute(
          builder: (_) => _isTeacher
              ? const TeacherMainScreen()
              : const StudentMainScreen(),
        ),
        (route) => false,
      );
    } catch (e) {
      _showSnackBar('Sign up failed: ${e.toString().replaceAll('Exception: ', '')}', isError: true);
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _showSnackBar(String message, {bool isError = false}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            Icon(
              isError ? Icons.error_outline_rounded : Icons.check_circle_rounded,
              color: isError ? AppColors.error : AppColors.statusPresent,
            ),
            const SizedBox(width: 12),
            Expanded(child: Text(message)),
          ],
        ),
        backgroundColor: AppColors.cardBackground,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: FadeTransition(
          opacity: _fadeAnim,
          child: Column(
            children: [
              _buildAppBar(),
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.symmetric(horizontal: 28),
                  child: Column(
                    children: [
                      const SizedBox(height: 20),
                      _buildTitle(),
                      const SizedBox(height: 36),
                      _buildStepper(),
                      const SizedBox(height: 36),
                      _buildCurrentStepContent(),
                      const SizedBox(height: 60),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildAppBar() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          GestureDetector(
            onTap: () {
              if (_currentStep > 0) {
                setState(() => _currentStep--);
              } else {
                Navigator.pop(context);
              }
            },
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.surfaceVariant,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppColors.cardBorder),
              ),
              child: const Icon(Icons.arrow_back_rounded, color: AppColors.textPrimary),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTitle() {
    return Column(
      children: [
        ShaderMask(
          shaderCallback: (bounds) => LinearGradient(
            colors: AppColors.primaryGradient,
          ).createShader(bounds),
          child: Text(
            'Create Account',
            style: Theme.of(context).textTheme.displaySmall?.copyWith(
                  color: Colors.white,
                  fontWeight: FontWeight.w700,
                ),
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'Join us and start tracking attendance',
          style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                color: AppColors.textSecondary,
              ),
        ),
      ],
    );
  }

  Widget _buildStepper() {
    return Row(
      children: [
        _buildStepIndicator(0, 'Role'),
        _buildStepLine(0),
        _buildStepIndicator(1, 'Info'),
        _buildStepLine(1),
        _buildStepIndicator(2, 'Password'),
      ],
    );
  }

  Widget _buildStepIndicator(int step, String label) {
    final isActive = _currentStep >= step;
    final isCurrent = _currentStep == step;
    return Column(
      children: [
        AnimatedContainer(
          duration: const Duration(milliseconds: 300),
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            gradient: isActive ? LinearGradient(colors: AppColors.primaryGradient) : null,
            color: isActive ? null : AppColors.surfaceVariant,
            shape: BoxShape.circle,
            border: !isActive ? Border.all(color: AppColors.cardBorder) : null,
            boxShadow: isCurrent
                ? [
                    BoxShadow(
                      color: AppColors.primary.withOpacity(0.4),
                      blurRadius: 12,
                    ),
                  ]
                : null,
          ),
          child: Center(
            child: isActive && _currentStep > step
                ? const Icon(Icons.check_rounded, color: Colors.white, size: 20)
                : Text(
                    '${step + 1}',
                    style: TextStyle(
                      color: isActive ? Colors.white : AppColors.textSecondary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
          ),
        ),
        const SizedBox(height: 8),
        Text(
          label,
          style: TextStyle(
            color: isActive ? AppColors.textPrimary : AppColors.textHint,
            fontSize: 12,
            fontWeight: isActive ? FontWeight.w600 : FontWeight.normal,
          ),
        ),
      ],
    );
  }

  Widget _buildStepLine(int afterStep) {
    final isActive = _currentStep > afterStep;
    return Expanded(
      child: Padding(
        padding: const EdgeInsets.only(bottom: 24),
        child: Container(
          height: 3,
          margin: const EdgeInsets.symmetric(horizontal: 8),
          decoration: BoxDecoration(
            gradient: isActive ? LinearGradient(colors: AppColors.primaryGradient) : null,
            color: isActive ? null : AppColors.surfaceVariant,
            borderRadius: BorderRadius.circular(2),
          ),
        ),
      ),
    );
  }

  Widget _buildCurrentStepContent() {
    switch (_currentStep) {
      case 0:
        return _buildRoleStep();
      case 1:
        return _buildInfoStep();
      case 2:
        return _buildPasswordStep();
      default:
        return const SizedBox();
    }
  }

  Widget _buildRoleStep() {
    return Column(
      children: [
        Text(
          'I am a...',
          style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w600,
              ),
        ),
        const SizedBox(height: 24),
        Row(
          children: [
            Expanded(child: _RoleCard(
              icon: Icons.person_rounded,
              title: 'Student',
              description: 'Track my attendance',
              isSelected: !_isTeacher,
              onTap: () => setState(() => _isTeacher = false),
            )),
            const SizedBox(width: 16),
            Expanded(child: _RoleCard(
              icon: Icons.school_rounded,
              title: 'Teacher',
              description: 'Manage classes',
              isSelected: _isTeacher,
              onTap: () => setState(() => _isTeacher = true),
            )),
          ],
        ),
        const SizedBox(height: 40),
        _buildNextButton(),
      ],
    );
  }

  Widget _buildInfoStep() {
    return Column(
      children: [
        _buildTextField(
          controller: _nameController,
          hintText: 'Full name',
          prefixIcon: Icons.person_outline_rounded,
        ),
        const SizedBox(height: 16),
        _buildTextField(
          controller: _emailController,
          hintText: 'Email address',
          prefixIcon: Icons.mail_outline_rounded,
          keyboardType: TextInputType.emailAddress,
        ),
        if (!_isTeacher) ...[
          const SizedBox(height: 16),
          _buildTextField(
            controller: _sectionController,
            hintText: 'Section (e.g., BSIT-3A)',
            prefixIcon: Icons.group_outlined,
          ),
        ],
        const SizedBox(height: 40),
        _buildNextButton(),
      ],
    );
  }

  Widget _buildPasswordStep() {
    return Column(
      children: [
        _buildTextField(
          controller: _passwordController,
          hintText: 'Password',
          prefixIcon: Icons.lock_outline_rounded,
          obscureText: _obscurePassword,
          suffixIcon: IconButton(
            icon: Icon(
              _obscurePassword ? Icons.visibility_off_rounded : Icons.visibility_rounded,
              color: AppColors.textHint,
            ),
            onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
          ),
        ),
        const SizedBox(height: 16),
        _buildTextField(
          controller: _confirmPasswordController,
          hintText: 'Confirm password',
          prefixIcon: Icons.lock_outline_rounded,
          obscureText: _obscureConfirmPassword,
          suffixIcon: IconButton(
            icon: Icon(
              _obscureConfirmPassword ? Icons.visibility_off_rounded : Icons.visibility_rounded,
              color: AppColors.textHint,
            ),
            onPressed: () => setState(() => _obscureConfirmPassword = !_obscureConfirmPassword),
          ),
        ),
        const SizedBox(height: 40),
        _buildSubmitButton(),
      ],
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String hintText,
    required IconData prefixIcon,
    TextInputType? keyboardType,
    bool obscureText = false,
    Widget? suffixIcon,
  }) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: TextField(
        controller: controller,
        keyboardType: keyboardType,
        obscureText: obscureText,
        style: const TextStyle(color: AppColors.textPrimary, fontSize: 16),
        decoration: InputDecoration(
          hintText: hintText,
          prefixIcon: Icon(prefixIcon, color: AppColors.textHint),
          suffixIcon: suffixIcon,
        ),
      ),
    );
  }

  Widget _buildNextButton() {
    return SizedBox(
      width: double.infinity,
      child: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(colors: AppColors.primaryGradient),
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: AppColors.primary.withOpacity(0.4),
              blurRadius: 20,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: ElevatedButton(
          onPressed: () => setState(() => _currentStep++),
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.transparent,
            shadowColor: Colors.transparent,
            padding: const EdgeInsets.symmetric(vertical: 18),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          ),
          child: const Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text('Continue', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
              SizedBox(width: 8),
              Icon(Icons.arrow_forward_rounded, size: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSubmitButton() {
    return SizedBox(
      width: double.infinity,
      child: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(colors: AppColors.successGradient),
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: AppColors.statusPresent.withOpacity(0.4),
              blurRadius: 20,
              offset: const Offset(0, 10),
            ),
          ],
        ),
        child: ElevatedButton(
          onPressed: _isLoading ? null : _handleSignUp,
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.transparent,
            shadowColor: Colors.transparent,
            padding: const EdgeInsets.symmetric(vertical: 18),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          ),
          child: _isLoading
              ? const SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                )
              : const Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.check_rounded, size: 20),
                    SizedBox(width: 8),
                    Text('Create Account', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
                  ],
                ),
        ),
      ),
    );
  }
}

class _RoleCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String description;
  final bool isSelected;
  final VoidCallback onTap;

  const _RoleCard({
    required this.icon,
    required this.title,
    required this.description,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 250),
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          gradient: isSelected ? LinearGradient(colors: AppColors.primaryGradient) : null,
          color: isSelected ? null : AppColors.cardBackground,
          borderRadius: BorderRadius.circular(24),
          border: Border.all(
            color: isSelected ? Colors.transparent : AppColors.cardBorder,
            width: 2,
          ),
          boxShadow: isSelected
              ? [
                  BoxShadow(
                    color: AppColors.primary.withOpacity(0.3),
                    blurRadius: 20,
                    offset: const Offset(0, 10),
                  ),
                ]
              : null,
        ),
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: isSelected ? Colors.white.withOpacity(0.2) : AppColors.surfaceVariant,
                borderRadius: BorderRadius.circular(18),
              ),
              child: Icon(
                icon,
                size: 32,
                color: isSelected ? Colors.white : AppColors.textSecondary,
              ),
            ),
            const SizedBox(height: 16),
            Text(
              title,
              style: TextStyle(
                color: isSelected ? Colors.white : AppColors.textPrimary,
                fontSize: 18,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              description,
              style: TextStyle(
                color: isSelected ? Colors.white.withOpacity(0.8) : AppColors.textSecondary,
                fontSize: 13,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
