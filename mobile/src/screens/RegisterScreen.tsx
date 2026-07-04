import React, { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../navigation/AppNavigator';
import { authApi } from '../api/journyApi';
import { useAppTheme } from '../theme/ThemeContext';
import { isStrongEnoughPassword, isValidEmail } from '../utils/validation';

type Props = NativeStackScreenProps<RootStackParamList, 'Register'>;

export default function RegisterScreen({ navigation }: Props) {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleRegister = async () => {
    if (!fullName.trim() || !email.trim() || !password.trim()) {
      Alert.alert('Missing information', 'Please fill in your name, email and password.');
      return;
    }
    if (!isValidEmail(email)) {
      Alert.alert('Invalid email', 'Please enter a valid email address.');
      return;
    }
    if (!isStrongEnoughPassword(password)) {
      Alert.alert('Password too short', 'Password must be at least 6 characters.');
      return;
    }

    if (password !== confirmPassword) {
      Alert.alert('Passwords do not match', 'Please confirm your password again.');
      return;
    }

    try {
      setLoading(true);
      await authApi.register(fullName.trim(), email.trim(), password);
      navigation.replace('TripSetup');
    } catch {
      Alert.alert('Account could not be created', 'Please try another email or make sure the backend is running.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />

      <View style={styles.content}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('Welcome'))}
        >
          <Ionicons name="arrow-back" size={21} color={colors.midnight} />
        </TouchableOpacity>

        <Text style={styles.title}>Create your travel profile.</Text>
        <Text style={styles.subtitle}>
          Journy learns your preferences to suggest better routes, places and local moments.
        </Text>

        <View style={styles.form}>
          <Input icon="person-outline" placeholder="Full name" value={fullName} onChangeText={setFullName} colors={colors} styles={styles} />
          <Input icon="mail-outline" placeholder="Email" value={email} onChangeText={setEmail} colors={colors} styles={styles} />
          <Input icon="lock-closed-outline" placeholder="Password" value={password} onChangeText={setPassword} secureTextEntry colors={colors} styles={styles} />
          <Input
            icon="shield-checkmark-outline"
            placeholder="Confirm password"
            value={confirmPassword}
            onChangeText={setConfirmPassword}
            secureTextEntry
            colors={colors}
            styles={styles}
          />

          <TouchableOpacity
            style={[styles.primaryButton, loading && styles.buttonDisabled]}
            activeOpacity={0.9}
            disabled={loading}
            onPress={handleRegister}
          >
            {loading ? (
              <ActivityIndicator color={colors.surface} />
            ) : (
              <>
                <Text style={styles.primaryButtonText}>Create account</Text>
                <Ionicons name="arrow-forward" size={19} color={colors.surface} />
              </>
            )}
          </TouchableOpacity>
        </View>

        <TouchableOpacity onPress={() => navigation.navigate('Login')}>
          <Text style={styles.bottomText}>
            Already have an account? <Text style={styles.link}>Sign in</Text>
          </Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

function Input({
  icon,
  placeholder,
  value,
  onChangeText,
  secureTextEntry,
  colors,
  styles,
}: {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  placeholder: string;
  value: string;
  onChangeText: (text: string) => void;
  secureTextEntry?: boolean;
  colors: Theme['colors'];
  styles: RegisterStyles;
}) {
  return (
    <View style={styles.inputRow}>
      <Ionicons name={icon} size={19} color={colors.teal} />
      <TextInput
        placeholder={placeholder}
        placeholderTextColor={colors.softMuted}
        value={value}
        onChangeText={onChangeText}
        secureTextEntry={secureTextEntry}
        style={styles.input}
      />
    </View>
  );
}

type Theme = ReturnType<typeof useAppTheme>['theme'];
type RegisterStyles = ReturnType<typeof createStyles>;

function createStyles({ colors, radius, spacing, typography }: Theme) {
  return StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  content: { flex: 1, padding: spacing.lg, justifyContent: 'center' },
  backButton: {
    width: 46,
    height: 46,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.mist,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.xl,
  },
  title: {
    color: colors.midnight,
    fontSize: typography.title,
    fontWeight: '900',
    lineHeight: 41,
  },
  subtitle: {
    color: colors.slate,
    fontSize: typography.body,
    lineHeight: 23,
    marginTop: spacing.sm,
  },
  form: { marginTop: spacing.xl, gap: spacing.md },
  inputRow: {
    minHeight: 56,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.mist,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.md,
  },
  input: {
    flex: 1,
    marginLeft: spacing.sm,
    color: colors.midnight,
    fontSize: typography.body,
    fontWeight: '700',
  },
  primaryButton: {
    minHeight: 58,
    borderRadius: radius.lg,
    backgroundColor: colors.teal,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.sm,
  },
  buttonDisabled: { opacity: 0.72 },
  primaryButtonText: {
    color: colors.surface,
    fontSize: typography.body,
    fontWeight: '900',
  },
  bottomText: {
    color: colors.slate,
    textAlign: 'center',
    marginTop: spacing.xl,
    fontSize: typography.small,
    fontWeight: '700',
  },
  link: { color: colors.teal, fontWeight: '900' },
});
}
