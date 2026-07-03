import React, { useEffect, useState } from 'react';
import { Alert, SafeAreaView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { colors, radius, spacing, typography } from '../theme/colors';
import type { RootStackParamList } from '../navigation/AppNavigator';
import { tripApi } from '../api/journyApi';

type Props = NativeStackScreenProps<RootStackParamList, 'LoadingPlan'>;

const steps = [
  'Reading your travel style',
  'Clustering nearby places',
  'Selecting local food stops',
  'Optimizing the daily rhythm',
];

export default function LoadingPlanScreen({ navigation, route }: Props) {
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;

    const createPlan = async () => {
      try {
        const trip = await tripApi.create(route.params.tripDraft);
        await tripApi.generate(trip.id);

        if (!cancelled) {
          navigation.replace('MainTabs', { screen: 'Itinerary' });
        }
      } catch {
        if (!cancelled) {
          setError(true);
          Alert.alert('Plan could not be created', 'Please make sure you are signed in and the backend is running.');
        }
      }
    };

    createPlan();

    return () => {
      cancelled = true;
    };
  }, [navigation, route.params.tripDraft]);

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="dark-content" backgroundColor={colors.ivory} />

      <View style={styles.content}>
        <View style={styles.aiCircle}>
          <Ionicons name="sparkles" size={36} color={colors.surface} />
        </View>

        <Text style={styles.title}>Building your AI plan.</Text>
        <Text style={styles.subtitle}>
          Journy is combining route logic, pace, local experiences and food picks for your city.
        </Text>

        <View style={styles.card}>
          {steps.map((step, index) => (
            <View key={step} style={styles.stepRow}>
              <View style={[styles.stepIcon, index === 0 && styles.stepIconActive]}>
                <Ionicons
                  name={index === 0 ? 'sparkles' : 'checkmark'}
                  size={15}
                  color={index === 0 ? colors.surface : colors.teal}
                />
              </View>
              <Text style={styles.stepText}>{step}</Text>
            </View>
          ))}
        </View>

        <View style={styles.progressTrack}>
          <View style={styles.progressFill} />
        </View>

        {error ? (
          <TouchableOpacity style={styles.retryButton} activeOpacity={0.88} onPress={() => navigation.replace('TripSetup')}>
            <Text style={styles.retryText}>Back to setup</Text>
          </TouchableOpacity>
        ) : null}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  content: {
    flex: 1,
    padding: spacing.lg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  aiCircle: {
    width: 96,
    height: 96,
    borderRadius: radius.pill,
    backgroundColor: colors.midnight,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.xl,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 18 },
    shadowOpacity: 0.28,
    shadowRadius: 26,
  },
  title: {
    color: colors.midnight,
    fontSize: typography.h1,
    fontWeight: '900',
    textAlign: 'center',
  },
  subtitle: {
    color: colors.slate,
    fontSize: typography.body,
    lineHeight: 23,
    textAlign: 'center',
    marginTop: spacing.sm,
  },
  card: {
    width: '100%',
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.mist,
    padding: spacing.lg,
    marginTop: spacing.xl,
    gap: spacing.md,
  },
  stepRow: { flexDirection: 'row', alignItems: 'center' },
  stepIcon: {
    width: 30,
    height: 30,
    borderRadius: radius.pill,
    backgroundColor: colors.fog,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.sm,
  },
  stepIconActive: { backgroundColor: colors.teal },
  stepText: {
    color: colors.midnight,
    fontSize: typography.small,
    fontWeight: '800',
    flex: 1,
  },
  progressTrack: {
    width: '74%',
    height: 8,
    borderRadius: radius.pill,
    backgroundColor: colors.mist,
    marginTop: spacing.xl,
    overflow: 'hidden',
  },
  progressFill: {
    width: '68%',
    height: '100%',
    borderRadius: radius.pill,
    backgroundColor: colors.teal,
  },
  retryButton: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    marginTop: spacing.lg,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  retryText: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
});
