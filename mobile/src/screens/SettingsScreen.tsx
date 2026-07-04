import React, { useMemo } from 'react';
import { SafeAreaView, ScrollView, StatusBar, StyleSheet, Switch, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../navigation/AppNavigator';
import { useAppTheme } from '../theme/ThemeContext';
import type { colors as lightColors } from '../theme/colors';
import { authApi } from '../api/journyApi';

type Props = NativeStackScreenProps<RootStackParamList, 'Settings'>;
type IconName = React.ComponentProps<typeof Ionicons>['name'];

const preferenceRows: Array<{ label: string; value: string; icon: IconName }> = [
  { label: 'Default pace', value: 'Balanced', icon: 'speedometer-outline' },
  { label: 'Budget mode', value: 'Mid range', icon: 'wallet-outline' },
  { label: 'Food discovery', value: 'Local-first', icon: 'restaurant-outline' },
];

const privacyRows: Array<{ label: string; value: string; icon: IconName }> = [
  { label: 'Location usage', value: 'While traveling', icon: 'location-outline' },
  { label: 'Saved taste profile', value: 'Enabled', icon: 'person-circle-outline' },
];

export default function SettingsScreen({ navigation }: Props) {
  const { isDark, setDarkMode, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;

  const signOut = async () => {
    await authApi.logout();
    navigation.reset({
      index: 0,
      routes: [{ name: 'Welcome' }],
    });
  };

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <TouchableOpacity
            style={styles.backButton}
            activeOpacity={0.86}
            onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('MainTabs'))}
          >
            <Ionicons name="arrow-back" size={21} color={colors.midnight} />
          </TouchableOpacity>
        </View>

        <Text style={styles.eyebrow}>Settings</Text>
        <Text style={styles.title}>App preferences</Text>
        <Text style={styles.subtitle}>
          Keep the assistant useful, quiet and aligned with how you like to move through a city.
        </Text>

        <Section title="Trip defaults" styles={styles} />
        <View style={styles.list}>
          {preferenceRows.map((item) => (
            <SettingRow key={item.label} item={item} colors={colors} styles={styles} />
          ))}
        </View>

        <Section title="Notifications" styles={styles} />
        <View style={styles.list}>
          <ToggleRow label="Plan changes" description="Route updates, closures and weather shifts." value colors={colors} styles={styles} />
          <ToggleRow label="Food windows" description="Timely lunch, coffee and dinner suggestions." value colors={colors} styles={styles} />
          <ToggleRow label="Marketing updates" description="Product news and occasional city guides." colors={colors} styles={styles} />
        </View>

        <Section title="Appearance" styles={styles} />
        <View style={styles.list}>
          <ToggleRow
            label="Dark mode"
            description="Use a calmer dark interface at night."
            value={isDark}
            onValueChange={setDarkMode}
            colors={colors}
            styles={styles}
          />
        </View>

        <Section title="Privacy" styles={styles} />
        <View style={styles.list}>
          {privacyRows.map((item) => (
            <SettingRow key={item.label} item={item} colors={colors} styles={styles} />
          ))}
        </View>

        <TouchableOpacity style={styles.signOutButton} activeOpacity={0.86} onPress={signOut}>
          <Ionicons name="log-out-outline" size={18} color={colors.teal} />
          <Text style={styles.signOutText}>Sign out</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

type Theme = ReturnType<typeof useAppTheme>['theme'];
type AppColors = typeof lightColors;
type SettingsStyles = ReturnType<typeof createStyles>;

function Section({ title, styles }: { title: string; styles: SettingsStyles }) {
  return <Text style={styles.sectionTitle}>{title}</Text>;
}

function SettingRow({
  item,
  colors,
  styles,
}: {
  item: { label: string; value: string; icon: IconName };
  colors: AppColors;
  styles: SettingsStyles;
}) {
  return (
    <TouchableOpacity style={styles.settingRow} activeOpacity={0.86}>
      <View style={styles.rowIcon}>
        <Ionicons name={item.icon} size={18} color={colors.teal} />
      </View>
      <Text style={styles.rowLabel}>{item.label}</Text>
      <Text style={styles.rowValue}>{item.value}</Text>
      <Ionicons name="chevron-forward" size={16} color={colors.softMuted} />
    </TouchableOpacity>
  );
}

function ToggleRow({
  label,
  description,
  value,
  onValueChange,
  colors,
  styles,
}: {
  label: string;
  description: string;
  value?: boolean;
  onValueChange?: (value: boolean) => void;
  colors: AppColors;
  styles: SettingsStyles;
}) {
  return (
    <View style={styles.toggleRow}>
      <View style={styles.toggleCopy}>
        <Text style={styles.toggleLabel}>{label}</Text>
        <Text style={styles.toggleDescription}>{description}</Text>
      </View>
      <Switch
        value={Boolean(value)}
        onValueChange={onValueChange}
        trackColor={{ false: colors.mist, true: colors.teal }}
        thumbColor={colors.surface}
        ios_backgroundColor={colors.mist}
        style={styles.switchControl}
      />
    </View>
  );
}

function createStyles({ colors, radius, spacing, typography }: Theme) {
  return StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  content: { padding: spacing.lg, paddingBottom: spacing.xxl },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    marginTop: spacing.md,
  },
  backButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.md,
    borderWidth: 1,
    height: 44,
    justifyContent: 'center',
    width: 44,
  },
  eyebrow: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    marginTop: spacing.lg,
    textTransform: 'uppercase',
  },
  title: {
    color: colors.midnight,
    fontSize: typography.h1,
    fontWeight: '900',
    lineHeight: 36,
    marginTop: spacing.xs,
  },
  subtitle: {
    color: colors.slate,
    fontSize: typography.body,
    fontWeight: '600',
    lineHeight: 23,
    marginTop: spacing.sm,
  },
  sectionTitle: {
    color: colors.midnight,
    fontSize: typography.h3,
    fontWeight: '900',
    marginTop: spacing.xl,
  },
  list: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    marginTop: spacing.md,
    overflow: 'hidden',
  },
  settingRow: {
    alignItems: 'center',
    borderBottomColor: colors.mist,
    borderBottomWidth: 1,
    flexDirection: 'row',
    minHeight: 62,
    paddingHorizontal: spacing.md,
  },
  rowIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    height: 38,
    justifyContent: 'center',
    marginRight: spacing.md,
    width: 38,
  },
  rowLabel: {
    color: colors.midnight,
    flex: 1,
    fontSize: typography.small,
    fontWeight: '900',
  },
  rowValue: {
    color: colors.slate,
    fontSize: typography.small,
    fontWeight: '800',
    marginRight: spacing.sm,
  },
  toggleRow: {
    alignItems: 'center',
    borderBottomColor: colors.mist,
    borderBottomWidth: 1,
    flexDirection: 'row',
    minHeight: 88,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
  },
  toggleCopy: { flex: 1, paddingRight: spacing.md },
  toggleLabel: {
    color: colors.midnight,
    fontSize: typography.small,
    fontWeight: '900',
  },
  toggleDescription: {
    color: colors.slate,
    fontSize: typography.tiny,
    fontWeight: '700',
    lineHeight: 17,
    marginTop: spacing.xs,
  },
  switchControl: {
    transform: [{ scaleX: 0.86 }, { scaleY: 0.86 }],
  },
  signOutButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    gap: spacing.sm,
    justifyContent: 'center',
    marginTop: spacing.lg,
    minHeight: 52,
  },
  signOutText: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
});
}
