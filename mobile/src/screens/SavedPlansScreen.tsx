import React, { useCallback, useMemo, useState } from 'react';
import { Alert, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';

import { tripApi } from '../api/journyApi';
import type { TripResponse } from '../api/types';
import type { RootStackParamList } from '../navigation/AppNavigator';
import { InlineEmpty, InlineError, InlineLoading } from '../components/StateViews';
import { useAppTheme } from '../theme/ThemeContext';

type Props = NativeStackScreenProps<RootStackParamList, 'SavedPlans'>;

export default function SavedPlansScreen({ navigation }: Props) {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const [plans, setPlans] = useState<TripResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [busyPlanId, setBusyPlanId] = useState<string | null>(null);

  const loadPlans = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const response = await tripApi.list();
      setPlans(response);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(useCallback(() => {
    loadPlans();
  }, [loadPlans]));

  const openPlan = async (plan: TripResponse) => {
    setBusyPlanId(plan.id);
    try {
      await tripApi.makeCurrent(plan.id);
      navigation.navigate('MainTabs', { screen: 'Itinerary' });
    } catch {
      Alert.alert('Could not open plan', 'Please check the backend connection and try again.');
    } finally {
      setBusyPlanId(null);
    }
  };

  const confirmDelete = (plan: TripResponse) => {
    Alert.alert(
      'Delete saved plan?',
      `${plan.destination} will be removed from your saved plans.`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Delete', style: 'destructive', onPress: () => deletePlan(plan) },
      ],
    );
  };

  const deletePlan = async (plan: TripResponse) => {
    setBusyPlanId(plan.id);
    try {
      await tripApi.delete(plan.id);
      setPlans((current) => current.filter((item) => item.id !== plan.id));
    } catch {
      Alert.alert('Could not delete plan', 'Please try again in a moment.');
    } finally {
      setBusyPlanId(null);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <TouchableOpacity
            style={styles.backButton}
            activeOpacity={0.86}
            onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('MainTabs', { screen: 'Profile' }))}
          >
            <Ionicons name="arrow-back" size={21} color={colors.midnight} />
          </TouchableOpacity>
          <TouchableOpacity style={styles.newButton} activeOpacity={0.86} onPress={() => navigation.navigate('TripSetup')}>
            <Ionicons name="add" size={18} color={colors.surface} />
            <Text style={styles.newButtonText}>New plan</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.hero}>
          <Text style={styles.eyebrow}>Saved plans</Text>
          <Text style={styles.title}>Keep every city plan ready.</Text>
          <Text style={styles.subtitle}>Open a previous itinerary, make it current, or clear plans you no longer need.</Text>
        </View>

        {loading ? <InlineLoading label="Loading saved plans..." /> : null}
        {error ? (
          <InlineError
            title="Saved plans could not load"
            description="Retry after the API connection is available."
            onRetry={loadPlans}
          />
        ) : null}
        {!loading && !error && !plans.length ? (
          <InlineEmpty
            title="No saved plans yet"
            description="Create a trip plan and Journy will keep it here for later."
          />
        ) : null}

        <View style={styles.planList}>
          {plans.map((plan) => {
            const busy = busyPlanId === plan.id;
            return (
              <View key={plan.id} style={styles.planCard}>
                <View style={styles.planTop}>
                  <View style={styles.destinationIcon}>
                    <Ionicons name="map-outline" size={21} color={colors.teal} />
                  </View>
                  <View style={styles.planCopy}>
                    <Text style={styles.planCity}>{plan.destination}</Text>
                    <Text style={styles.planMeta}>{plan.days} days - {formatPace(plan.pace)} pace - {formatBudget(plan.budget)}</Text>
                  </View>
                </View>

                <View style={styles.statsRow}>
                  <PlanStat icon="location-outline" value={`${plan.stats.stops}`} label="Stops" styles={styles} />
                  <PlanStat icon="restaurant-outline" value={`${plan.stats.foodPicks}`} label="Food" styles={styles} />
                  <PlanStat icon="walk-outline" value={`${plan.stats.averageWalkKm.toFixed(1)} km`} label="Avg walk" styles={styles} />
                </View>

                <View style={styles.actionRow}>
                  <TouchableOpacity style={[styles.openButton, busy && styles.disabledButton]} activeOpacity={0.86} onPress={() => openPlan(plan)}>
                    <Text style={styles.openButtonText}>{busy ? 'Opening...' : 'Open itinerary'}</Text>
                    <Ionicons name="chevron-forward" size={16} color={colors.surface} />
                  </TouchableOpacity>
                  <TouchableOpacity style={styles.deleteButton} activeOpacity={0.86} onPress={() => confirmDelete(plan)} disabled={busy}>
                    <Ionicons name="trash-outline" size={18} color={colors.teal} />
                  </TouchableOpacity>
                </View>
              </View>
            );
          })}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

type Theme = ReturnType<typeof useAppTheme>['theme'];
type SavedPlansStyles = ReturnType<typeof createStyles>;

function PlanStat({
  icon,
  value,
  label,
  styles,
}: {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  value: string;
  label: string;
  styles: SavedPlansStyles;
}) {
  return (
    <View style={styles.stat}>
      <Ionicons name={icon} size={16} style={styles.statIcon} />
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

function formatPace(value: string) {
  return value.toLowerCase().replace(/^\w/, (letter) => letter.toUpperCase());
}

function formatBudget(value: string) {
  if (value === 'LEAN') return 'Lean';
  if (value === 'COMFORT') return 'Comfort';
  return 'Balanced';
}

function createStyles({ colors, radius, spacing, typography }: Theme) {
  return StyleSheet.create({
    safe: { flex: 1, backgroundColor: colors.ivory },
    content: { padding: spacing.lg, paddingBottom: 56 },
    header: {
      alignItems: 'center',
      flexDirection: 'row',
      justifyContent: 'space-between',
      marginTop: spacing.md,
    },
    backButton: {
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.md,
      borderWidth: 1,
      height: 46,
      justifyContent: 'center',
      width: 46,
    },
    newButton: {
      alignItems: 'center',
      backgroundColor: colors.midnight,
      borderRadius: radius.pill,
      flexDirection: 'row',
      gap: 6,
      minHeight: 42,
      paddingHorizontal: spacing.md,
    },
    newButtonText: { color: colors.surface, fontSize: typography.small, fontWeight: '900' },
    hero: {
      marginTop: spacing.xl,
    },
    eyebrow: {
      color: colors.teal,
      fontSize: typography.tiny,
      fontWeight: '900',
      textTransform: 'uppercase',
    },
    title: {
      color: colors.midnight,
      fontSize: typography.h1,
      fontWeight: '900',
      lineHeight: 38,
      marginTop: spacing.xs,
    },
    subtitle: {
      color: colors.slate,
      fontSize: typography.body,
      fontWeight: '700',
      lineHeight: 23,
      marginTop: spacing.sm,
    },
    planList: {
      gap: spacing.md,
      marginTop: spacing.lg,
    },
    planCard: {
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.xl,
      borderWidth: 1,
      padding: spacing.md,
      shadowColor: colors.shadow,
      shadowOffset: { width: 0, height: 14 },
      shadowOpacity: 0.08,
      shadowRadius: 22,
    },
    planTop: {
      alignItems: 'center',
      flexDirection: 'row',
      gap: spacing.sm,
    },
    destinationIcon: {
      alignItems: 'center',
      backgroundColor: colors.fog,
      borderRadius: radius.md,
      height: 48,
      justifyContent: 'center',
      width: 48,
    },
    planCopy: { flex: 1, minWidth: 0 },
    planCity: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
    planMeta: { color: colors.slate, fontSize: typography.small, fontWeight: '800', marginTop: 4 },
    statsRow: {
      flexDirection: 'row',
      gap: spacing.sm,
      marginTop: spacing.md,
    },
    stat: {
      alignItems: 'center',
      backgroundColor: colors.surfaceWarm,
      borderRadius: radius.lg,
      flex: 1,
      minHeight: 76,
      justifyContent: 'center',
      padding: spacing.xs,
    },
    statIcon: { color: colors.teal },
    statValue: { color: colors.midnight, fontSize: typography.body, fontWeight: '900', marginTop: 4 },
    statLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 2 },
    actionRow: {
      flexDirection: 'row',
      gap: spacing.sm,
      marginTop: spacing.md,
    },
    openButton: {
      alignItems: 'center',
      backgroundColor: colors.midnight,
      borderRadius: radius.pill,
      flex: 1,
      flexDirection: 'row',
      gap: 6,
      justifyContent: 'center',
      minHeight: 48,
    },
    openButtonText: { color: colors.surface, fontSize: typography.small, fontWeight: '900' },
    deleteButton: {
      alignItems: 'center',
      backgroundColor: colors.surfaceWarm,
      borderColor: colors.mist,
      borderRadius: radius.pill,
      borderWidth: 1,
      height: 48,
      justifyContent: 'center',
      width: 52,
    },
    disabledButton: {
      opacity: 0.6,
    },
  });
}
