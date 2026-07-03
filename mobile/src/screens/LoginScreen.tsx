import React, { useState } from 'react';
import {
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

import { colors, radius, spacing, typography } from '../theme/colors';
import type { RootStackParamList } from '../navigation/AppNavigator';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

export default function LoginScreen({ navigation }: Props) {
  const [email, setEmail] = useState('admin@journy.app');
  const [password, setPassword] = useState('admin123');

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="dark-content" backgroundColor={colors.ivory} />

      <View style={styles.content}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('Welcome'))}
        >
          <Ionicons name="arrow-back" size={21} color={colors.midnight} />
        </TouchableOpacity>

        <View style={styles.logo}>
          <Ionicons name="sparkles" size={26} color={colors.surface} />
        </View>

        <Text style={styles.title}>Welcome back.</Text>
        <Text style={styles.subtitle}>
          Sign in to keep your trips, preferences and AI recommendations in sync.
        </Text>

        <View style={styles.form}>
          <Input icon="mail-outline" placeholder="Email" value={email} onChangeText={setEmail} />
          <Input
            icon="lock-closed-outline"
            placeholder="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
          />

          <TouchableOpacity
            style={styles.primaryButton}
            activeOpacity={0.9}
            onPress={() => navigation.replace('TripSetup')}
          >
            <Text style={styles.primaryButtonText}>Sign in</Text>
            <Ionicons name="arrow-forward" size={19} color={colors.surface} />
          </TouchableOpacity>

          <TouchableOpacity style={styles.googleButton} activeOpacity={0.85}>
            <Ionicons name="logo-google" size={18} color={colors.midnight} />
            <Text style={styles.googleText}>Continue with Google</Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity onPress={() => navigation.navigate('Register')}>
          <Text style={styles.bottomText}>
            New to Journy? <Text style={styles.link}>Create account</Text>
          </Text>
        </TouchableOpacity>

        <TouchableOpacity onPress={() => navigation.replace('TripSetup')}>
          <Text style={styles.guestText}>Continue as guest</Text>
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
}: {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  placeholder: string;
  value: string;
  onChangeText: (text: string) => void;
  secureTextEntry?: boolean;
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

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  content: { flex: 1, padding: spacing.lg, justifyContent: 'center' },
  logo: {
    width: 62,
    height: 62,
    borderRadius: radius.lg,
    backgroundColor: colors.teal,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.xl,
  },
  backButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.md,
    borderWidth: 1,
    height: 46,
    justifyContent: 'center',
    marginBottom: spacing.xl,
    width: 46,
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
  form: { marginTop: spacing.lg, gap: spacing.md },
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
  primaryButtonText: {
    color: colors.surface,
    fontSize: typography.body,
    fontWeight: '900',
  },
  googleButton: {
    minHeight: 56,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.mist,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: spacing.sm,
  },
  googleText: {
    color: colors.midnight,
    fontSize: typography.body,
    fontWeight: '800',
  },
  bottomText: {
    color: colors.slate,
    textAlign: 'center',
    marginTop: spacing.xl,
    fontSize: typography.small,
    fontWeight: '700',
  },
  link: { color: colors.teal, fontWeight: '900' },
  guestText: {
    color: colors.teal,
    textAlign: 'center',
    marginTop: spacing.md,
    fontSize: typography.small,
    fontWeight: '900',
  },
});
