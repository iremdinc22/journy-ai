import React from 'react';
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

type Props = NativeStackScreenProps<RootStackParamList, 'Register'>;

export default function RegisterScreen({ navigation }: Props) {
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

        <Text style={styles.title}>Create your travel profile.</Text>
        <Text style={styles.subtitle}>
          Journy learns your preferences to suggest better routes, places and local moments.
        </Text>

        <View style={styles.form}>
          <Input icon="person-outline" placeholder="Full name" />
          <Input icon="mail-outline" placeholder="Email" />
          <Input icon="lock-closed-outline" placeholder="Password" secureTextEntry />
          <Input icon="shield-checkmark-outline" placeholder="Confirm password" secureTextEntry />

          <TouchableOpacity
            style={styles.primaryButton}
            activeOpacity={0.9}
            onPress={() => navigation.replace('TripSetup')}
          >
            <Text style={styles.primaryButtonText}>Create account</Text>
            <Ionicons name="arrow-forward" size={19} color={colors.surface} />
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
  secureTextEntry,
}: {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  placeholder: string;
  secureTextEntry?: boolean;
}) {
  return (
    <View style={styles.inputRow}>
      <Ionicons name={icon} size={19} color={colors.teal} />
      <TextInput
        placeholder={placeholder}
        placeholderTextColor={colors.softMuted}
        secureTextEntry={secureTextEntry}
        style={styles.input}
      />
    </View>
  );
}

const styles = StyleSheet.create({
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
