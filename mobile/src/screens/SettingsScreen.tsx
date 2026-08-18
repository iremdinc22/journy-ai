import React, { useEffect, useMemo, useState } from 'react';
import { SafeAreaView, ScrollView, StatusBar, StyleSheet, Switch, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../navigation/AppNavigator';
import { useAppTheme } from '../theme/ThemeContext';
import { useLanguage, type LanguageCode } from '../i18n/LanguageContext';
import type { colors as lightColors } from '../theme/colors';
import { authApi, profileApi } from '../api/journyApi';
import type { UserPreferences } from '../api/types';

type Props = NativeStackScreenProps<RootStackParamList, 'Settings'>;
type IconName = React.ComponentProps<typeof Ionicons>['name'];

const defaultPreferences: UserPreferences = {
  defaultPace: 'BALANCED',
  defaultBudget: 'BALANCED',
  foodDiscovery: 'LOCAL_FIRST',
  planChangeNotifications: true,
  foodWindowNotifications: true,
};

export default function SettingsScreen({ navigation }: Props) {
  const { isDark, setDarkMode, theme } = useAppTheme();
  const { language, setLanguage, t } = useLanguage();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const [preferences, setPreferences] = useState<UserPreferences>(defaultPreferences);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let mounted = true;

    profileApi.me()
      .then((profile) => {
        if (mounted && profile.preferences) {
          setPreferences(profile.preferences);
        }
      })
      .catch(() => undefined);

    return () => {
      mounted = false;
    };
  }, []);

  const savePreferences = async (next: UserPreferences) => {
    setPreferences(next);
    setSaving(true);
    try {
      const updated = await profileApi.updatePreferences(next);
      setPreferences(updated.preferences ?? next);
    } catch {
      setPreferences(preferences);
    } finally {
      setSaving(false);
    }
  };

  const cyclePreference = (key: 'defaultPace' | 'defaultBudget' | 'foodDiscovery') => {
    const options = {
      defaultPace: ['RELAXED', 'BALANCED', 'FULL'],
      defaultBudget: ['LEAN', 'BALANCED', 'COMFORT'],
      foodDiscovery: ['LOCAL_FIRST', 'BEST_RATED', 'BUDGET_FRIENDLY'],
    } as const;
    const values = options[key];
    const currentIndex = values.findIndex((value) => value === preferences[key]);
    const nextValue = values[(currentIndex + 1) % values.length];
    savePreferences({ ...preferences, [key]: nextValue });
  };

  const updateToggle = (key: 'planChangeNotifications' | 'foodWindowNotifications', value: boolean) => {
    savePreferences({ ...preferences, [key]: value });
  };

  const cycleLanguage = () => {
    setLanguage(language === 'tr' ? 'en' : 'tr');
  };

  const preferenceRows: Array<{ label: string; value: string; icon: IconName; onPress: () => void }> = [
    { label: t('settings.defaultPace'), value: paceLabel(preferences.defaultPace, t), icon: 'speedometer-outline', onPress: () => cyclePreference('defaultPace') },
    { label: t('settings.budgetMode'), value: budgetLabel(preferences.defaultBudget, t), icon: 'wallet-outline', onPress: () => cyclePreference('defaultBudget') },
    { label: t('settings.foodDiscovery'), value: foodLabel(preferences.foodDiscovery, t), icon: 'restaurant-outline', onPress: () => cyclePreference('foodDiscovery') },
  ];

  const privacyRows: Array<{ label: string; value: string; icon: IconName }> = [
    { label: t('settings.locationUsage'), value: t('settings.locationUsageValue'), icon: 'location-outline' },
    { label: t('settings.savedTasteProfile'), value: t('settings.enabled'), icon: 'person-circle-outline' },
  ];

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

        <Text style={styles.eyebrow}>{t('settings.eyebrow')}</Text>
        <Text style={styles.title}>{t('settings.title')}</Text>
        <Text style={styles.subtitle}>{t('settings.subtitle')}</Text>

        <Section title={t('settings.tripDefaults')} styles={styles} />
        <View style={styles.list}>
          {preferenceRows.map((item) => (
            <SettingRow key={item.label} item={item} colors={colors} styles={styles} disabled={saving} />
          ))}
        </View>

        <Section title={t('settings.notifications')} styles={styles} />
        <View style={styles.list}>
          <ToggleRow
            label={t('settings.planChanges')}
            description={t('settings.planChangesDescription')}
            value={preferences.planChangeNotifications}
            onValueChange={(value) => updateToggle('planChangeNotifications', value)}
            colors={colors}
            styles={styles}
          />
          <ToggleRow
            label={t('settings.foodWindows')}
            description={t('settings.foodWindowsDescription')}
            value={preferences.foodWindowNotifications}
            onValueChange={(value) => updateToggle('foodWindowNotifications', value)}
            colors={colors}
            styles={styles}
          />
          <ToggleRow label={t('settings.marketingUpdates')} description={t('settings.marketingUpdatesDescription')} colors={colors} styles={styles} />
        </View>

        <Section title={t('settings.appearance')} styles={styles} />
        <View style={styles.list}>
          <SettingRow
            item={{
              label: t('common.language'),
              value: languageLabel(language, t),
              icon: 'language-outline',
              onPress: cycleLanguage,
            }}
            colors={colors}
            styles={styles}
          />
          <ToggleRow
            label={t('settings.darkMode')}
            description={t('settings.darkModeDescription')}
            value={isDark}
            onValueChange={setDarkMode}
            colors={colors}
            styles={styles}
          />
        </View>

        <Section title={t('settings.privacy')} styles={styles} />
        <View style={styles.list}>
          {privacyRows.map((item) => (
            <SettingRow key={item.label} item={item} colors={colors} styles={styles} />
          ))}
        </View>

        <TouchableOpacity style={styles.signOutButton} activeOpacity={0.86} onPress={signOut}>
          <Ionicons name="log-out-outline" size={18} color={colors.teal} />
          <Text style={styles.signOutText}>{t('common.signOut')}</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

type Theme = ReturnType<typeof useAppTheme>['theme'];
type AppColors = typeof lightColors;
type SettingsStyles = ReturnType<typeof createStyles>;
type Translate = ReturnType<typeof useLanguage>['t'];

function Section({ title, styles }: { title: string; styles: SettingsStyles }) {
  return <Text style={styles.sectionTitle}>{title}</Text>;
}

function SettingRow({
  item,
  colors,
  styles,
  disabled,
}: {
  item: { label: string; value: string; icon: IconName; onPress?: () => void };
  colors: AppColors;
  styles: SettingsStyles;
  disabled?: boolean;
}) {
  return (
    <TouchableOpacity style={styles.settingRow} activeOpacity={0.86} onPress={item.onPress} disabled={disabled}>
      <View style={styles.rowIcon}>
        <Ionicons name={item.icon} size={18} color={colors.teal} />
      </View>
      <Text style={styles.rowLabel}>{item.label}</Text>
      <Text style={styles.rowValue}>{item.value}</Text>
      <Ionicons name="chevron-forward" size={16} color={colors.softMuted} />
    </TouchableOpacity>
  );
}

function paceLabel(value: UserPreferences['defaultPace'], t: Translate) {
  return value === 'RELAXED' ? t('settings.relaxed') : value === 'FULL' ? t('settings.fullDay') : t('settings.balanced');
}

function budgetLabel(value: UserPreferences['defaultBudget'], t: Translate) {
  return value === 'LEAN' ? t('settings.lean') : value === 'COMFORT' ? t('settings.comfort') : t('settings.balanced');
}

function foodLabel(value: UserPreferences['foodDiscovery'], t: Translate) {
  if (value === 'BEST_RATED') return t('settings.bestRated');
  if (value === 'BUDGET_FRIENDLY') return t('settings.budgetFriendly');
  return t('settings.localFirst');
}

function languageLabel(value: LanguageCode, t: Translate) {
  return value === 'tr' ? t('common.turkish') : t('common.english');
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
        thumbColor={value ? colors.ivory : colors.surfaceWarm}
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
