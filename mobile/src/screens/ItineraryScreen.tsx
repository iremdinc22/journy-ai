import React, { useEffect, useState } from 'react';
import { SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { colors, radius, spacing, typography } from '../theme/colors';
import { tripApi } from '../api/journyApi';
import { session } from '../api/session';
import type { ItineraryResponse } from '../api/types';

const days = [
  {
    day: 'Day 1',
    city: 'Amsterdam',
    area: 'Canals & Museums',
    summary: 'A calm first day with a museum window, canal walk and a low-effort dinner area.',
    stats: '6.4 km - 4 stops',
    stops: ['Museumplein', 'Morning coffee', 'Canal loop', 'De Pijp dinner'],
  },
  {
    day: 'Day 2',
    city: 'Rome',
    area: 'Historic center',
    summary: 'Culture and food grouped tightly so the day feels rich without becoming exhausting.',
    stats: '4.8 km - 5 stops',
    stops: ['Morning piazza', 'Small gallery', 'Trattoria lunch', 'Aperitivo street'],
  },
  {
    day: 'Day 3',
    city: 'Barcelona',
    area: 'Design & coast',
    summary: 'A reusable city-day format for future destinations, not a single-city flow.',
    stats: '5.2 km - 4 stops',
    stops: ['Design district', 'Market lunch', 'Beach walk', 'Tapas bar'],
  },
];

export default function ItineraryScreen() {
  const navigation = useNavigation<any>();
  const [itinerary, setItinerary] = useState<ItineraryResponse | null>(null);

  useEffect(() => {
    let mounted = true;

    const load = async () => {
      try {
        const current = session.getCurrentTrip() ?? await tripApi.current();
        session.setCurrentTrip(current);
        const response = await tripApi.itinerary(current.id);
        if (mounted) {
          setItinerary(response);
        }
      } catch {
        // Keep fallback sample days available for offline UI preview.
      }
    };

    load();

    return () => {
      mounted = false;
    };
  }, []);

  const visibleDays = itinerary?.days.map((day) => ({
    day: `Day ${day.dayNumber}`,
    city: itinerary.destination,
    area: day.title,
    summary: day.summary,
    stats: `${day.walkKm.toFixed(1)} km - ${day.stopCount} stops`,
    stops: day.stops.map((stop) => stop.title),
  })) ?? days;

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="dark-content" backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <Text style={styles.eyebrow}>AI itinerary</Text>
        <Text style={styles.title}>Your days, grouped by distance and rhythm.</Text>
        <Text style={styles.subtitle}>
          Journy keeps each day realistic by clustering nearby places and leaving room for breaks.
        </Text>

        {visibleDays.map((item) => (
          <TouchableOpacity
            key={`${item.city}-${item.day}`}
            style={styles.dayCard}
            activeOpacity={0.88}
            onPress={() => navigation.navigate('DayRouteDetail', item)}
          >
            <View style={styles.dayHeader}>
              <View>
                <Text style={styles.day}>{item.day} - {item.city}</Text>
                <Text style={styles.area}>{item.area}</Text>
              </View>
              <View style={styles.badge}>
                <Ionicons name="walk-outline" size={14} color={colors.teal} />
                <Text style={styles.badgeText}>{item.stats}</Text>
              </View>
            </View>

            <Text style={styles.summary}>{item.summary}</Text>

            <View style={styles.timeline}>
              {item.stops.map((stop, index) => (
                <View key={stop} style={styles.stopRow}>
                  <View style={styles.stopNumber}>
                    <Text style={styles.stopNumberText}>{index + 1}</Text>
                  </View>
                  <View style={styles.stopLine} />
                  <Text style={styles.stopText}>{stop}</Text>
                </View>
              ))}
            </View>
            <View style={styles.openRouteRow}>
              <Text style={styles.openRouteText}>Open route map</Text>
              <Ionicons name="chevron-forward" size={16} color={colors.teal} />
            </View>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  content: { padding: spacing.lg, paddingBottom: 132 },
  eyebrow: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    marginTop: spacing.md,
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
    lineHeight: 23,
    marginBottom: spacing.lg,
    marginTop: spacing.sm,
  },
  dayCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    marginBottom: spacing.md,
    padding: spacing.lg,
  },
  dayHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: spacing.md,
  },
  day: { color: colors.teal, fontSize: typography.small, fontWeight: '900' },
  area: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900', marginTop: 3 },
  badge: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    flexDirection: 'row',
    gap: 4,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  badgeText: { color: colors.graphite, fontSize: typography.tiny, fontWeight: '900' },
  summary: {
    color: colors.slate,
    fontSize: typography.small,
    lineHeight: 20,
    marginTop: spacing.md,
  },
  timeline: { marginTop: spacing.md },
  stopRow: { alignItems: 'center', flexDirection: 'row', minHeight: 38 },
  stopNumber: {
    alignItems: 'center',
    backgroundColor: colors.midnight,
    borderRadius: radius.pill,
    height: 28,
    justifyContent: 'center',
    width: 28,
  },
  stopNumberText: { color: colors.surface, fontSize: typography.tiny, fontWeight: '900' },
  stopLine: {
    backgroundColor: colors.mist,
    height: 1,
    marginHorizontal: spacing.sm,
    width: 22,
  },
  stopText: { color: colors.midnight, flex: 1, fontSize: typography.small, fontWeight: '800' },
  openRouteRow: {
    alignItems: 'center',
    borderTopColor: colors.mist,
    borderTopWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing.md,
    paddingTop: spacing.md,
  },
  openRouteText: {
    color: colors.teal,
    fontSize: typography.small,
    fontWeight: '900',
  },
});
