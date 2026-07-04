import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { tripApi } from '../api/journyApi';
import { session } from '../api/session';
import type { ItineraryDay, ItineraryResponse } from '../api/types';
import { useAppTheme } from '../theme/ThemeContext';
import { InlineError, InlineLoading } from '../components/StateViews';

const days: ItineraryDay[] = [
  {
    dayNumber: 1,
    title: 'Canals & Museums',
    summary: 'A calm first day with a museum window, canal walk and a low-effort dinner area.',
    walkKm: 6.4,
    stopCount: 4,
    stops: [
      { order: 1, title: 'Museumplein', category: 'CULTURE', timeWindow: 'Morning', note: 'Start with the strongest culture anchor.', latitude: 52.3584, longitude: 4.8811 },
      { order: 2, title: 'Morning coffee', category: 'COFFEE', timeWindow: 'Late morning', note: 'A soft break before the canal loop.', latitude: 52.3631, longitude: 4.8858 },
      { order: 3, title: 'Canal loop', category: 'WALKING', timeWindow: 'Afternoon', note: 'Walkable streets with flexible photo stops.', latitude: 52.3702, longitude: 4.8952 },
      { order: 4, title: 'De Pijp dinner', category: 'FOOD', timeWindow: 'Evening', note: 'End near a lively local dinner area.', latitude: 52.3542, longitude: 4.8907 },
    ],
  },
  {
    dayNumber: 2,
    title: 'Historic center',
    summary: 'Culture and food grouped tightly so the day feels rich without becoming exhausting.',
    walkKm: 4.8,
    stopCount: 4,
    stops: [
      { order: 1, title: 'Morning piazza', category: 'WALKING', timeWindow: 'Morning', note: 'Ease into the center with a short walk.', latitude: 41.8986, longitude: 12.4769 },
      { order: 2, title: 'Small gallery', category: 'CULTURE', timeWindow: 'Late morning', note: 'A compact culture stop.', latitude: 41.9007, longitude: 12.4781 },
      { order: 3, title: 'Trattoria lunch', category: 'FOOD', timeWindow: 'Lunch', note: 'Food-first stop without crossing town.', latitude: 41.8951, longitude: 12.4722 },
      { order: 4, title: 'Aperitivo street', category: 'FOOD', timeWindow: 'Evening', note: 'A flexible final area for dinner or drinks.', latitude: 41.8916, longitude: 12.4679 },
    ],
  },
  {
    dayNumber: 3,
    title: 'Design & coast',
    summary: 'A reusable city-day format for future destinations, not a single-city flow.',
    walkKm: 5.2,
    stopCount: 4,
    stops: [
      { order: 1, title: 'Design district', category: 'CULTURE', timeWindow: 'Morning', note: 'Start with galleries and small shops.', latitude: 41.3851, longitude: 2.1734 },
      { order: 2, title: 'Market lunch', category: 'FOOD', timeWindow: 'Lunch', note: 'Local food break near the route.', latitude: 41.3818, longitude: 2.1716 },
      { order: 3, title: 'Beach walk', category: 'WALKING', timeWindow: 'Afternoon', note: 'Open-air pacing after lunch.', latitude: 41.3762, longitude: 2.1894 },
      { order: 4, title: 'Tapas bar', category: 'FOOD', timeWindow: 'Evening', note: 'End with a low-effort dinner zone.', latitude: 41.3837, longitude: 2.1819 },
    ],
  },
];

export default function ItineraryScreen() {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const navigation = useNavigation<any>();
  const [itinerary, setItinerary] = useState<ItineraryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const loadItinerary = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const current = session.getCurrentTrip() ?? await tripApi.current();
      session.setCurrentTrip(current);
      const response = await tripApi.itinerary(current.id);
      setItinerary(response);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

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
        if (mounted) {
          setError(true);
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    load();

    return () => {
      mounted = false;
    };
  }, []);

  const visibleDays = itinerary?.days ?? days;
  const destination = itinerary?.destination ?? 'Amsterdam';
  const tripId = itinerary?.tripId ?? session.getCurrentTrip()?.id ?? 'preview-trip';
  const totalWalk = visibleDays.reduce((sum, day) => sum + day.walkKm, 0);
  const totalStops = visibleDays.reduce((sum, day) => sum + day.stopCount, 0);

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View>
            <Text style={styles.eyebrow}>AI itinerary</Text>
            <Text style={styles.title}>{destination}</Text>
          </View>
          <View style={styles.headerBadge}>
            <Ionicons name="sparkles-outline" size={14} color={colors.teal} />
            <Text style={styles.headerBadgeText}>Optimized</Text>
          </View>
        </View>

        <View style={styles.overviewCard}>
          <View style={styles.overviewTop}>
            <View>
              <Text style={styles.overviewLabel}>Trip rhythm</Text>
              <Text style={styles.overviewTitle}>{visibleDays.length} days planned around distance</Text>
            </View>
            <Ionicons name="map-outline" size={24} color={colors.teal} />
          </View>
          <View style={styles.overviewStats}>
            <OverviewStat label="Days" value={`${visibleDays.length}`} styles={styles} />
            <OverviewStat label="Stops" value={`${totalStops}`} styles={styles} />
            <OverviewStat label="Walk" value={`${totalWalk.toFixed(1)} km`} styles={styles} />
          </View>
        </View>

        {loading ? <InlineLoading label="Building your itinerary..." /> : null}
        {error ? (
          <InlineError
            title="Could not refresh the itinerary"
            description="Showing the preview plan until the backend responds."
            onRetry={loadItinerary}
          />
        ) : null}

        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.dayRail}>
          {visibleDays.map((item) => (
            <TouchableOpacity
              key={`chip-${destination}-${item.dayNumber}`}
              style={styles.dayChip}
              activeOpacity={0.86}
              onPress={() => navigation.navigate('DayRouteDetail', { tripId, destination, day: item })}
            >
              <Text style={styles.dayChipLabel}>Day {item.dayNumber}</Text>
              <Text style={styles.dayChipValue}>{item.walkKm.toFixed(1)} km</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        {visibleDays.map((item) => (
          <TouchableOpacity
            key={`${destination}-${item.dayNumber}`}
            style={styles.dayCard}
            activeOpacity={0.88}
            onPress={() => navigation.navigate('DayRouteDetail', { tripId, destination, day: item })}
          >
            <View style={styles.dayHeader}>
              <View style={styles.dayTitleBlock}>
                <Text style={styles.day}>Day {item.dayNumber} - {destination}</Text>
                <Text style={styles.area}>{item.title}</Text>
              </View>
              <View style={styles.badge}>
                <Ionicons name="walk-outline" size={14} color={colors.teal} />
                <Text style={styles.badgeText}>{item.walkKm.toFixed(1)} km - {item.stopCount} stops</Text>
              </View>
            </View>

            <Text style={styles.summary}>{item.summary}</Text>

            <View style={styles.routeStrip}>
              {item.stops.slice(0, 5).map((stop, index) => (
                <React.Fragment key={`${stop.order}-${stop.title}-strip`}>
                  <View style={styles.routeDot}>
                    <Text style={styles.routeDotText}>{index + 1}</Text>
                  </View>
                  {index !== Math.min(item.stops.length, 5) - 1 ? <View style={styles.routeLine} /> : null}
                </React.Fragment>
              ))}
            </View>

            <View style={styles.timeline}>
              {item.stops.slice(0, 3).map((stop, index) => (
                <View key={`${stop.order}-${stop.title}`} style={styles.stopRow}>
                  <View style={styles.stopNumber}>
                    <Text style={styles.stopNumberText}>{index + 1}</Text>
                  </View>
                  <View style={styles.stopLine} />
                  <Text style={styles.stopText}>{stop.title}</Text>
                </View>
              ))}
              {item.stops.length > 3 ? <Text style={styles.moreStops}>+{item.stops.length - 3} more stops in detail</Text> : null}
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

type Theme = ReturnType<typeof useAppTheme>['theme'];
type ItineraryStyles = ReturnType<typeof createStyles>;

function OverviewStat({ label, value, styles }: { label: string; value: string; styles: ItineraryStyles }) {
  return (
    <View style={styles.overviewStat}>
      <Text style={styles.overviewStatValue}>{value}</Text>
      <Text style={styles.overviewStatLabel}>{label}</Text>
    </View>
  );
}

function createStyles({ colors, radius, spacing, typography }: Theme) {
  return StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  content: { padding: spacing.lg, paddingBottom: 132 },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing.md,
  },
  eyebrow: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    marginTop: spacing.md,
    textTransform: 'uppercase',
  },
  title: {
    color: colors.midnight,
    fontSize: typography.h2,
    fontWeight: '900',
    lineHeight: 29,
    marginTop: spacing.xs,
  },
  headerBadge: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 5,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  headerBadgeText: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  overviewCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.md,
    padding: spacing.md,
  },
  overviewTop: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  overviewLabel: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  overviewTitle: {
    color: colors.midnight,
    fontSize: typography.h3,
    fontWeight: '900',
    lineHeight: 23,
    marginTop: 4,
    maxWidth: 250,
  },
  overviewStats: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.md },
  overviewStat: {
    backgroundColor: colors.surfaceWarm,
    borderRadius: radius.lg,
    flex: 1,
    padding: spacing.sm,
  },
  overviewStatValue: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
  overviewStatLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900', marginTop: 2 },
  dayRail: { gap: spacing.sm, paddingVertical: spacing.md },
  dayChip: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    minWidth: 92,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  dayChipLabel: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  dayChipValue: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900', marginTop: 2 },
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
    borderRadius: radius.xl,
    borderWidth: 1,
    marginBottom: spacing.md,
    padding: spacing.md,
  },
  dayHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: spacing.md,
  },
  dayTitleBlock: { flex: 1 },
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
  routeStrip: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderRadius: radius.lg,
    flexDirection: 'row',
    marginTop: spacing.md,
    padding: spacing.sm,
  },
  routeDot: {
    alignItems: 'center',
    backgroundColor: colors.midnight,
    borderRadius: radius.pill,
    height: 26,
    justifyContent: 'center',
    width: 26,
  },
  routeDotText: { color: colors.surface, fontSize: typography.tiny, fontWeight: '900' },
  routeLine: { backgroundColor: colors.mist, flex: 1, height: 2, marginHorizontal: spacing.xs },
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
  moreStops: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    marginTop: spacing.xs,
  },
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
}
