import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/services/auth_service.dart';
import '../../../core/services/firestore_service.dart';
import '../../student/screens/student_main_screen.dart';
import '../../teacher/screens/teacher_main_screen.dart';
import 'signup_screen.dart';
import 'role_selection_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> with SingleTickerProviderStateMixin {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isTeacher = false;
  bool _isLoading = false;
  bool _obscurePassword = true;
  late AnimationController _animController;
  late Animation<double> _fadeAnim;

  @override
  void initState() {
    super.initState();
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    );
    _fadeAnim = CurvedAnimation(parent: _animController, curve: Curves.easeOut);
    _animController.forward();
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _animController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    final email = _emailController.text.trim();
    final password = _passwordController.text.trim();

    if (email.isEmpty || password.isEmpty) {
      _showSnackBar('Please fill in all fields');
      return;
    }

    setState(() => _isLoading = true);

    try {
      final authService = context.read<AuthService>();
      final firestoreService = context.read<FirestoreService>();

      final credential = await authService.signIn(email, password);
      final userData = await firestoreService.getUserData(credential.user!.uid);

      if (userData == null) {
        await authService.signOut();
        _showSnackBar('Account not found. Please sign up.');
        return;
      }

      final userIsTeacher = userData['isTeacher'] ?? false;
      if (userIsTeacher != _isTeacher) {
        await authService.signOut();
        _showSnackBar('Incorrect role selected for this account');
        return;
      }

      if (!mounted) return;

      final isStudent = userData['isStudent'] ?? true;
      if (userIsTeacher && isStudent) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (_) => const RoleSelectionScreen()),
        );
      } else {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (_) => userIsTeacher
                ? const TeacherMainScreen()
                : const StudentMainScreen(),
          ),
        );
      }
    } catch (e) {
      _showSnackBar('Login failed: ${e.toString().replaceAll('Exception: ', '')}');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: BoxDecoration(
          color: AppColors.background,
        ),
        child: SafeArea(
          child: FadeTransition(
            opacity: _fadeAnim,
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 28),
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  minHeight: MediaQuery.of(context).size.height -
                      MediaQuery.of(context).padding.top -
                      MediaQuery.of(context).padding.bottom,
                ),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const SizedBox(height: 60),
                    _buildLogo(),
                    const SizedBox(height: 48),
                    _buildTitle(),
                    const SizedBox(height: 48),
                    _buildForm(),
                    const SizedBox(height: 60),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLogo() {
    return Container(
      width: 100,
      height: 100,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: AppColors.primaryGradient,
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(28),
        boxShadow: [
          BoxShadow(
            color: AppColors.primary.withOpacity(0.4),
            blurRadius: 30,
            offset: const Offset(0, 15),
          ),
        ],
      ),
      child: const Icon(
        Icons.fingerprint,
        size: 48,
        color: Colors.white,
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
            'Welcome Back',
            style: Theme.of(context).textTheme.displayMedium?.copyWith(
                  color: Colors.white,
                  fontWeight: FontWeight.w700,
                ),
          ),
        ),
        const SizedBox(height: 12),
        Text(
          'Sign in to continue tracking attendance',
          style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                color: AppColors.textSecondary,
              ),
          textAlign: TextAlign.center,
        ),
      ],
    );
  }

  Widget _buildForm() {
    return Column(
      children: [
        _buildTextField(
          controller: _emailController,
          hintText: 'Email address',
          prefixIcon: Icons.mail_outline_rounded,
          keyboardType: TextInputType.emailAddress,
        ),
        const SizedBox(height: 16),
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
        const SizedBox(height: 28),
        _buildRoleSelector(),
        const SizedBox(height: 32),
        _buildLoginButton(),
        const SizedBox(height: 28),
        _buildSignUpLink(),
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

  Widget _buildRoleSelector() {
    return Container(
      padding: const EdgeInsets.all(6),
      decoration: BoxDecoration(
        color: AppColors.surfaceVariant,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.cardBorder),
      ),
      child: Row(
        children: [
          Expanded(child: _buildRoleOption('Student', false)),
          Expanded(child: _buildRoleOption('Teacher', true)),
        ],
      ),
    );
  }

  Widget _buildRoleOption(String label, bool isTeacherOption) {
    final isSelected = _isTeacher == isTeacherOption;
    return GestureDetector(
      onTap: () => setState(() => _isTeacher = isTeacherOption),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          gradient: isSelected
              ? LinearGradient(colors: AppColors.primaryGradient)
              : null,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              isTeacherOption ? Icons.school_rounded : Icons.person_rounded,
              size: 20,
              color: isSelected ? Colors.white : AppColors.textSecondary,
            ),
            const SizedBox(width: 8),
            Text(
              label,
              style: TextStyle(
                color: isSelected ? Colors.white : AppColors.textSecondary,
                fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLoginButton() {
    return SizedBox(
      width: double.infinity,
      height: 58,
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
          onPressed: _isLoading ? null : _handleLogin,
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.transparent,
            shadowColor: Colors.transparent,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          ),
          child: _isLoading
              ? const SizedBox(
                  width: 24,
                  height: 24,
                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                )
              : const Text(
                  'Sign In',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
                ),
        ),
      ),
    );
  }

  Widget _buildSignUpLink() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text(
          "Don't have an account? ",
          style: TextStyle(color: AppColors.textSecondary),
        ),
        GestureDetector(
          onTap: () => Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const SignupScreen()),
          ),
          child: ShaderMask(
            shaderCallback: (bounds) => LinearGradient(
              colors: AppColors.primaryGradient,
            ).createShader(bounds),
            child: const Text(
              'Sign Up',
              style: TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ),
      ],
    );
  }
}
