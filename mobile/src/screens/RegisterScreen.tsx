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
import { useTranslation } from '../i18n/LanguageContext';
import { useAppTheme } from '../theme/ThemeContext';
import { isStrongEnoughPassword, isValidEmail } from '../utils/validation';

type Props = NativeStackScreenProps<RootStackParamList, 'Register'>;

export default function RegisterScreen({ navigation }: Props) {
  const { isDark, theme } = useAppTheme();
  const t = useTranslation();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleRegister = async () => {
    if (!fullName.trim() || !email.trim() || !password.trim()) {
      Alert.alert(t('auth.missingInfoTitle'), t('auth.missingRegisterMessage'));
      return;
    }
    if (!isValidEmail(email)) {
      Alert.alert(t('auth.invalidEmailTitle'), t('auth.invalidEmailMessage'));
      return;
    }
    if (!isStrongEnoughPassword(password)) {
      Alert.alert(t('auth.passwordShortTitle'), t('auth.passwordShortMessage'));
      return;
    }

    if (password !== confirmPassword) {
      Alert.alert(t('auth.passwordMismatchTitle'), t('auth.passwordMismatchMessage'));
      return;
    }

    try {
      setLoading(true);
      await authApi.register(fullName.trim(), email.trim(), password);
      navigation.replace('TripSetup');
    } catch {
      Alert.alert(t('auth.accountCreateFailedTitle'), t('auth.accountCreateFailedMessage'));
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

        <Text style={styles.title}>{t('auth.createProfile')}</Text>
        <Text style={styles.subtitle}>{t('auth.registerSubtitle')}</Text>

        <View style={styles.form}>
          <Input icon="person-outline" placeholder={t('auth.fullName')} value={fullName} onChangeText={setFullName} colors={colors} styles={styles} />
          <Input icon="mail-outline" placeholder={t('auth.email')} value={email} onChangeText={setEmail} colors={colors} styles={styles} />
          <Input icon="lock-closed-outline" placeholder={t('auth.password')} value={password} onChangeText={setPassword} secureTextEntry colors={colors} styles={styles} />
          <Input
            icon="shield-checkmark-outline"
            placeholder={t('auth.confirmPassword')}
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
                <Text style={styles.primaryButtonText}>{t('auth.createAccount')}</Text>
                <Ionicons name="arrow-forward" size={19} color={colors.surface} />
              </>
            )}
          </TouchableOpacity>
        </View>

        <TouchableOpacity onPress={() => navigation.navigate('Login')}>
          <Text style={styles.bottomText}>
            {t('auth.alreadyAccount')} <Text style={styles.link}>{t('auth.signIn')}</Text>
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
