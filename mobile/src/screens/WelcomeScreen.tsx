import React, { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  ImageBackground,
  KeyboardAvoidingView,
  Platform,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../navigation/AppNavigator';
import { authApi } from '../api/journyApi';
import { useTranslation } from '../i18n/LanguageContext';
import { useAppTheme } from '../theme/ThemeContext';
import { authErrorMessage } from '../utils/apiErrors';
import { isStrongEnoughPassword, isValidEmail } from '../utils/validation';

type Props = NativeStackScreenProps<RootStackParamList, 'Welcome'>;
type AuthMode = 'login' | 'register';

export default function WelcomeScreen({ navigation }: Props) {
  const { isDark, theme } = useAppTheme();
  const t = useTranslation();
  const styles = useMemo(() => createStyles(theme, isDark), [theme, isDark]);
  const { colors } = theme;
  const [sheetMode, setSheetMode] = useState<AuthMode | null>(null);
  const [email, setEmail] = useState('admin@journy.app');
  const [password, setPassword] = useState('admin123');
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);

  const featurePills = [
    { title: t('welcome.routes'), text: t('welcome.routesText'), icon: 'navigate-outline' },
    { title: t('welcome.local'), text: t('welcome.localText'), icon: 'restaurant-outline' },
    { title: t('welcome.personal'), text: t('welcome.personalText'), icon: 'heart-outline' },
  ] as const;

  const closeSheet = () => setSheetMode(null);

  const submitAuth = async () => {
    if (!sheetMode) return;
    if (!isValidEmail(email)) {
      Alert.alert(t('auth.invalidEmailTitle'), t('auth.invalidEmailMessage'));
      return;
    }
    if (!isStrongEnoughPassword(password)) {
      Alert.alert(t('auth.passwordShortTitle'), t('auth.passwordShortMessage'));
      return;
    }
    if (sheetMode === 'register' && !name.trim()) {
      Alert.alert(t('auth.missingNameTitle'), t('auth.missingNameMessage'));
      return;
    }

    try {
      setLoading(true);
      if (sheetMode === 'login') {
        await authApi.login(email.trim(), password);
      } else {
        await authApi.register(name.trim() || 'Journy Traveler', email.trim(), password);
      }
      navigation.replace('TripSetup');
    } catch (error) {
      Alert.alert(t('auth.failedTitle'), authErrorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  const continueAsGuest = async () => {
    try {
      setLoading(true);
      await authApi.login('admin@journy.app', 'admin123');
      navigation.replace('TripSetup');
    } catch (error) {
      Alert.alert(t('auth.guestUnavailableTitle'), authErrorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <View style={styles.screen}>
        <ScrollView
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
          bounces={false}
        >
          <ImageBackground
            source={{
              uri: 'https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=1000&q=90',
            }}
            style={styles.hero}
            imageStyle={styles.heroImage}
          >
            <LinearGradient colors={['rgba(168,143,168,0.08)', 'rgba(80,72,80,0.54)']} style={styles.heroOverlay}>
              <View style={styles.aiPill}>
                <Ionicons name="sparkles-outline" size={13} color={colors.surface} />
                <Text style={styles.aiPillText}>{t('welcome.aiPlanner')}</Text>
              </View>

              <View>
                <Text style={styles.title}>{t('welcome.title')}</Text>
                <Text style={styles.subtitle}>{t('welcome.subtitle')}</Text>
              </View>
            </LinearGradient>
          </ImageBackground>

          <View style={styles.previewCard}>
            <Ionicons name="sparkles-outline" size={22} color={colors.teal} />
            <Text style={styles.previewTitle}>{t('welcome.previewTitle')}</Text>
            <Text style={styles.previewText}>{t('welcome.previewText')}</Text>

            <View style={styles.featureRow}>
              {featurePills.map((item, index) => (
                <View key={item.title} style={styles.featurePill}>
                  <View style={styles.featureIcon}>
                    <Ionicons name={item.icon} size={20} color={colors.teal} />
                  </View>
                  <Text style={styles.featureTitle}>{item.title}</Text>
                  <Text style={styles.featureText}>{item.text}</Text>
                  {index < featurePills.length - 1 ? <View style={styles.featureDivider} /> : null}
                </View>
              ))}
            </View>
          </View>

          <View style={styles.actions}>
            <TouchableOpacity
              style={styles.primaryButton}
              activeOpacity={0.9}
              onPress={() => setSheetMode('register')}
            >
              <Text style={styles.primaryButtonText}>{t('auth.createAccount')}</Text>
              <Ionicons name="arrow-forward" size={19} color={colors.surface} />
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.secondaryButton}
              activeOpacity={0.86}
              onPress={() => setSheetMode('login')}
            >
              <Text style={styles.secondaryButtonText}>{t('auth.signIn')}</Text>
            </TouchableOpacity>

            <TouchableOpacity style={styles.guestButton} activeOpacity={0.8} onPress={continueAsGuest}>
              <Text style={styles.guestText}>{t('auth.continueGuest')}</Text>
            </TouchableOpacity>
          </View>
        </ScrollView>

        {sheetMode ? (
          <KeyboardAvoidingView
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
            style={styles.sheetLayer}
          >
            <TouchableOpacity style={styles.sheetBackdrop} activeOpacity={1} onPress={closeSheet} />
            <View style={styles.sheet}>
              <View style={styles.sheetHandle} />
              <View style={styles.sheetHeader}>
                <View>
                  <Text style={styles.sheetTitle}>
                    {sheetMode === 'login' ? t('auth.welcomeBack') : t('auth.createProfile')}
                  </Text>
                  <Text style={styles.sheetSubtitle}>
                    {sheetMode === 'login'
                      ? t('auth.demoSubtitle')
                      : t('auth.saveRoutesSubtitle')}
                  </Text>
                </View>
                <TouchableOpacity style={styles.closeButton} activeOpacity={0.82} onPress={closeSheet}>
                  <Ionicons name="close" size={19} color={colors.midnight} />
                </TouchableOpacity>
              </View>

              <View style={styles.sheetForm}>
                {sheetMode === 'register' ? (
                  <AuthInput icon="person-outline" placeholder={t('auth.fullName')} value={name} onChangeText={setName} colors={colors} styles={styles} />
                ) : null}
                <AuthInput icon="mail-outline" placeholder={t('auth.email')} value={email} onChangeText={setEmail} colors={colors} styles={styles} />
                <AuthInput
                  icon="lock-closed-outline"
                  placeholder={t('auth.password')}
                  value={password}
                  onChangeText={setPassword}
                  secureTextEntry
                  colors={colors}
                  styles={styles}
                />

                <TouchableOpacity
                  style={[styles.sheetPrimaryButton, loading && styles.buttonDisabled]}
                  activeOpacity={0.9}
                  disabled={loading}
                  onPress={submitAuth}
                >
                  {loading ? (
                    <ActivityIndicator color={colors.surface} />
                  ) : (
                    <>
                      <Text style={styles.sheetPrimaryText}>
                        {sheetMode === 'login' ? t('auth.signIn') : t('auth.createAccount')}
                      </Text>
                      <Ionicons name="arrow-forward" size={18} color={colors.surface} />
                    </>
                  )}
                </TouchableOpacity>

                <TouchableOpacity style={styles.googleButton} activeOpacity={0.86}>
                  <Ionicons name="logo-google" size={17} color={colors.midnight} />
                  <Text style={styles.googleText}>
                    {sheetMode === 'login' ? t('auth.continueGoogle') : t('auth.signUpGoogle')}
                  </Text>
                </TouchableOpacity>
              </View>

              <TouchableOpacity
                activeOpacity={0.8}
                onPress={() => setSheetMode(sheetMode === 'login' ? 'register' : 'login')}
              >
                <Text style={styles.switchText}>
                  {sheetMode === 'login' ? `${t('auth.newToJourny')} ` : `${t('auth.alreadyAccount')} `}
                  <Text style={styles.switchLink}>{sheetMode === 'login' ? t('auth.createAccount') : t('auth.signIn')}</Text>
                </Text>
              </TouchableOpacity>

              <TouchableOpacity activeOpacity={0.8} onPress={continueAsGuest}>
                <Text style={styles.sheetGuestText}>{t('auth.continueGuest')}</Text>
              </TouchableOpacity>
            </View>
          </KeyboardAvoidingView>
        ) : null}
      </View>
    </SafeAreaView>
  );
}

function AuthInput({
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
  styles: WelcomeStyles;
}) {
  return (
    <View style={styles.authInputRow}>
      <Ionicons name={icon} size={18} color={colors.teal} />
      <TextInput
        placeholder={placeholder}
        placeholderTextColor={colors.softMuted}
        value={value}
        onChangeText={onChangeText}
        secureTextEntry={secureTextEntry}
        style={styles.authInput}
      />
    </View>
  );
}

type Theme = ReturnType<typeof useAppTheme>['theme'];
type WelcomeStyles = ReturnType<typeof createStyles>;

function createStyles({ colors, radius, spacing, typography }: Theme, isDark: boolean) {
  return StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  screen: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    padding: spacing.lg,
    paddingBottom: spacing.lg,
    paddingTop: spacing.xl,
  },
  hero: {
    height: 286,
  },
  heroImage: { borderRadius: radius.xl },
  heroOverlay: {
    borderRadius: radius.xl,
    flex: 1,
    justifyContent: 'space-between',
    padding: spacing.md,
  },
  aiPill: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(255,255,255,0.22)',
    borderColor: 'rgba(255,255,255,0.36)',
    borderRadius: radius.pill,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 5,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  aiPillText: { color: isDark ? colors.ink : colors.surface, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  title: {
    color: isDark ? colors.ink : colors.surface,
    fontSize: 32,
    fontWeight: '900',
    letterSpacing: 0,
    lineHeight: 37,
  },
  subtitle: {
    color: 'rgba(255,255,255,0.82)',
    fontSize: typography.body,
    fontWeight: '700',
    lineHeight: 23,
    marginTop: spacing.sm,
  },
  previewCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.sm,
    padding: spacing.md,
    shadowColor: colors.shadow,
    shadowOffset: { width: 0, height: 14 },
    shadowOpacity: 0.1,
    shadowRadius: 24,
  },
  previewTitle: {
    color: colors.midnight,
    fontSize: 23,
    fontWeight: '900',
    letterSpacing: 0,
    lineHeight: 28,
    marginTop: spacing.xs,
  },
  previewText: {
    color: colors.slate,
    fontSize: typography.small,
    fontWeight: '700',
    lineHeight: 20,
    marginTop: spacing.xs,
  },
  previewAccent: {
    color: colors.teal,
    fontWeight: '900',
  },
  featureRow: {
    flexDirection: 'row',
    marginTop: spacing.md,
  },
  featurePill: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: spacing.xs,
    position: 'relative',
  },
  featureIcon: {
    alignItems: 'center',
    backgroundColor: colors.lilac,
    borderRadius: radius.md,
    height: 40,
    justifyContent: 'center',
    marginBottom: spacing.xs,
    width: 40,
  },
  featureTitle: {
    color: colors.midnight,
    fontSize: typography.tiny,
    fontWeight: '900',
    textAlign: 'center',
  },
  featureText: {
    color: colors.slate,
    fontSize: typography.tiny,
    fontWeight: '700',
    lineHeight: 15,
    marginTop: 4,
    textAlign: 'center',
  },
  featureDivider: {
    backgroundColor: colors.mist,
    height: 66,
    position: 'absolute',
    right: 0,
    top: 6,
    width: 1,
  },
  actions: {
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  primaryButton: {
    alignItems: 'center',
    backgroundColor: colors.teal,
    borderRadius: radius.lg,
    flexDirection: 'row',
    gap: spacing.sm,
    justifyContent: 'center',
    minHeight: 54,
  },
  primaryButtonText: { color: isDark ? colors.ivory : colors.surface, fontSize: typography.body, fontWeight: '900' },
  secondaryButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    justifyContent: 'center',
    minHeight: 52,
  },
  secondaryButtonText: { color: colors.midnight, fontSize: typography.body, fontWeight: '900' },
  guestButton: { alignItems: 'center', justifyContent: 'center', minHeight: 34 },
  guestText: { color: colors.slate, fontSize: typography.small, fontWeight: '900' },
  sheetLayer: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'flex-end',
  },
  sheetBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: isDark ? 'rgba(0,0,0,0.44)' : 'rgba(78,95,99,0.22)',
  },
  sheet: {
    backgroundColor: colors.ivory,
    borderColor: colors.mist,
    borderTopLeftRadius: radius.xxl,
    borderTopRightRadius: radius.xxl,
    borderWidth: 1,
    minHeight: '68%',
    padding: spacing.lg,
    paddingBottom: spacing.lg,
    shadowColor: colors.shadow,
    shadowOffset: { width: 0, height: -16 },
    shadowOpacity: 0.18,
    shadowRadius: 28,
  },
  sheetHandle: {
    alignSelf: 'center',
    backgroundColor: colors.mist,
    borderRadius: radius.pill,
    height: 5,
    marginBottom: spacing.md,
    width: 42,
  },
  sheetHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  sheetTitle: {
    color: colors.midnight,
    fontSize: typography.h2,
    fontWeight: '900',
  },
  sheetSubtitle: {
    color: colors.slate,
    fontSize: typography.small,
    fontWeight: '700',
    lineHeight: 19,
    marginTop: 4,
    maxWidth: 260,
  },
  closeButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    height: 36,
    justifyContent: 'center',
    width: 36,
  },
  sheetForm: {
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  authInputRow: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    minHeight: 52,
    paddingHorizontal: spacing.md,
  },
  authInput: {
    color: colors.midnight,
    flex: 1,
    fontSize: typography.body,
    fontWeight: '700',
    marginLeft: spacing.sm,
  },
  sheetPrimaryButton: {
    alignItems: 'center',
    backgroundColor: colors.teal,
    borderRadius: radius.lg,
    flexDirection: 'row',
    gap: spacing.sm,
    justifyContent: 'center',
    marginTop: spacing.xs,
    minHeight: 52,
  },
  buttonDisabled: { opacity: 0.72 },
  sheetPrimaryText: {
    color: colors.surface,
    fontSize: typography.body,
    fontWeight: '900',
  },
  googleButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    gap: spacing.sm,
    justifyContent: 'center',
    minHeight: 50,
  },
  googleText: {
    color: colors.midnight,
    fontSize: typography.body,
    fontWeight: '800',
  },
  switchText: {
    color: colors.slate,
    fontSize: typography.small,
    fontWeight: '700',
    marginTop: spacing.md,
    textAlign: 'center',
  },
  switchLink: { color: colors.teal, fontWeight: '900' },
  sheetGuestText: {
    color: colors.teal,
    fontSize: typography.small,
    fontWeight: '900',
    marginTop: spacing.sm,
    textAlign: 'center',
  },
});
}
