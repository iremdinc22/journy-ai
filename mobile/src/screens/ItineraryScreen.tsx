import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Modal, Pressable, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
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
      { id: 'preview-1-1', order: 1, title: 'Museumplein', category: 'CULTURE', timeWindow: 'Morning', note: 'Start with the strongest culture anchor.', optional: false, latitude: 52.3584, longitude: 4.8811 },
      { id: 'preview-1-2', order: 2, title: 'Morning coffee', category: 'COFFEE', timeWindow: 'Late morning', note: 'A soft break before the canal loop.', optional: false, latitude: 52.3631, longitude: 4.8858 },
      { id: 'preview-1-3', order: 3, title: 'Canal loop', category: 'WALKING', timeWindow: 'Afternoon', note: 'Walkable streets with flexible photo stops.', optional: false, latitude: 52.3702, longitude: 4.8952 },
      { id: 'preview-1-4', order: 4, title: 'De Pijp dinner', category: 'FOOD', timeWindow: 'Evening', note: 'End near a lively local dinner area.', optional: false, latitude: 52.3542, longitude: 4.8907 },
    ],
  },
  {
    dayNumber: 2,
    title: 'Historic center',
    summary: 'Culture and food grouped tightly so the day feels rich without becoming exhausting.',
    walkKm: 4.8,
    stopCount: 4,
    stops: [
      { id: 'preview-2-1', order: 1, title: 'Morning piazza', category: 'WALKING', timeWindow: 'Morning', note: 'Ease into the center with a short walk.', optional: false, latitude: 41.8986, longitude: 12.4769 },
      { id: 'preview-2-2', order: 2, title: 'Small gallery', category: 'CULTURE', timeWindow: 'Late morning', note: 'A compact culture stop.', optional: false, latitude: 41.9007, longitude: 12.4781 },
      { id: 'preview-2-3', order: 3, title: 'Trattoria lunch', category: 'FOOD', timeWindow: 'Lunch', note: 'Food-first stop without crossing town.', optional: false, latitude: 41.8951, longitude: 12.4722 },
      { id: 'preview-2-4', order: 4, title: 'Aperitivo street', category: 'FOOD', timeWindow: 'Evening', note: 'A flexible final area for dinner or drinks.', optional: false, latitude: 41.8916, longitude: 12.4679 },
    ],
  },
  {
    dayNumber: 3,
    title: 'Design & coast',
    summary: 'A reusable city-day format for future destinations, not a single-city flow.',
    walkKm: 5.2,
    stopCount: 4,
    stops: [
      { id: 'preview-3-1', order: 1, title: 'Design district', category: 'CULTURE', timeWindow: 'Morning', note: 'Start with galleries and small shops.', optional: false, latitude: 41.3851, longitude: 2.1734 },
      { id: 'preview-3-2', order: 2, title: 'Market lunch', category: 'FOOD', timeWindow: 'Lunch', note: 'Local food break near the route.', optional: false, latitude: 41.3818, longitude: 2.1716 },
      { id: 'preview-3-3', order: 3, title: 'Beach walk', category: 'WALKING', timeWindow: 'Afternoon', note: 'Open-air pacing after lunch.', optional: false, latitude: 41.3762, longitude: 2.1894 },
      { id: 'preview-3-4', order: 4, title: 'Tapas bar', category: 'FOOD', timeWindow: 'Evening', note: 'End with a low-effort dinner zone.', optional: false, latitude: 41.3837, longitude: 2.1819 },
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
  const [selectedStop, setSelectedStop] = useState<{ day: ItineraryDay; stop: ItineraryDay['stops'][number] } | null>(null);

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

  const updateDay = (updatedDay: ItineraryDay) => {
    setItinerary((current) => current
      ? { ...current, days: current.days.map((day) => day.dayNumber === updatedDay.dayNumber ? updatedDay : day) }
      : current);
  };

  const ensureLivePlan = () => {
    if (!itinerary) {
      Alert.alert('Live plan required', 'Refresh the itinerary before editing stops.');
      return false;
    }
    return true;
  };

  const closeStopActions = () => setSelectedStop(null);

  const removeStop = (day: ItineraryDay, stop: ItineraryDay['stops'][number]) => {
    closeStopActions();
    Alert.alert(
      'Remove stop?',
      `${stop.title} will be removed from Day ${day.dayNumber}.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: async () => {
            if (!ensureLivePlan()) return;
            try {
              updateDay(await tripApi.removeStop(tripId, day.dayNumber, stop.id));
            } catch {
              Alert.alert('Could not remove stop', 'Please check the backend connection and try again.');
            }
          },
        },
      ],
    );
  };

  const toggleOptional = async (day: ItineraryDay, stop: ItineraryDay['stops'][number]) => {
    if (!ensureLivePlan()) return;
    closeStopActions();
    try {
      updateDay(await tripApi.toggleStopOptional(tripId, day.dayNumber, stop.id));
    } catch {
      Alert.alert('Could not update stop', 'Please check the backend connection and try again.');
    }
  };

  const reorderStop = async (day: ItineraryDay, stop: ItineraryDay['stops'][number], direction: -1 | 1) => {
    if (!ensureLivePlan()) return;
    const targetOrder = stop.order + direction;
    if (targetOrder < 1 || targetOrder > day.stops.length) return;
    closeStopActions();
    try {
      updateDay(await tripApi.reorderStop(tripId, day.dayNumber, stop.id, targetOrder));
    } catch {
      Alert.alert('Could not reorder stop', 'Please check the backend connection and try again.');
    }
  };

  const moveStop = async (day: ItineraryDay, stop: ItineraryDay['stops'][number], targetDayNumber: number) => {
    if (!ensureLivePlan()) return;
    closeStopActions();
    try {
      setItinerary(await tripApi.moveStop(tripId, day.dayNumber, stop.id, targetDayNumber));
    } catch {
      Alert.alert('Could not move stop', 'Please check the backend connection and try again.');
    }
  };

  const openStopActions = (day: ItineraryDay, stop: ItineraryDay['stops'][number]) => {
    setSelectedStop({ day, stop });
  };

  const targetDay = selectedStop
    ? visibleDays.find((item) => item.dayNumber !== selectedStop.day.dayNumber)
    : undefined;

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
                  <View style={styles.stopCopy}>
                    <Text style={[styles.stopText, stop.optional && styles.stopOptional]}>{stop.title}</Text>
                    <Text style={styles.stopTime}>{stop.timeWindow}{stop.optional ? ' - Optional' : ''}</Text>
                  </View>
                  <TouchableOpacity style={styles.stopMenuButton} activeOpacity={0.78} onPress={() => openStopActions(item, stop)}>
                    <Ionicons name="ellipsis-horizontal" size={18} color={colors.teal} />
                  </TouchableOpacity>
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
      <Modal visible={!!selectedStop} transparent animationType="fade" onRequestClose={closeStopActions}>
        <Pressable style={styles.actionOverlay} onPress={closeStopActions}>
          <Pressable style={styles.actionSheet}>
            {selectedStop ? (
              <>
                <View style={styles.actionHandle} />
                <View style={styles.actionHeader}>
                  <View style={styles.actionIcon}>
                    <Ionicons name={iconForCategory(selectedStop.stop.category)} size={20} color={colors.teal} />
                  </View>
                  <View style={styles.actionTitleBlock}>
                    <Text style={styles.actionTitle}>{selectedStop.stop.title}</Text>
                    <Text style={styles.actionMeta}>
                      Day {selectedStop.day.dayNumber} - {selectedStop.stop.timeWindow}
                      {selectedStop.stop.optional ? ' - Optional stop' : ''}
                    </Text>
                  </View>
                  <TouchableOpacity style={styles.actionClose} activeOpacity={0.75} onPress={closeStopActions}>
                    <Ionicons name="close" size={18} color={colors.slate} />
                  </TouchableOpacity>
                </View>

                <View style={styles.actionList}>
                  <StopActionRow
                    icon={selectedStop.stop.optional ? 'star-outline' : 'bookmark-outline'}
                    title={selectedStop.stop.optional ? 'Keep as main stop' : 'Make optional'}
                    description={selectedStop.stop.optional ? 'Bring it back into the core route.' : 'Keep it in the day, but lower the pressure.'}
                    onPress={() => toggleOptional(selectedStop.day, selectedStop.stop)}
                    styles={styles}
                  />
                  <StopActionRow
                    icon="arrow-up-outline"
                    title="Move earlier"
                    description="Place this stop one step earlier in the route."
                    disabled={selectedStop.stop.order <= 1}
                    onPress={() => reorderStop(selectedStop.day, selectedStop.stop, -1)}
                    styles={styles}
                  />
                  <StopActionRow
                    icon="arrow-down-outline"
                    title="Move later"
                    description="Place this stop one step later in the route."
                    disabled={selectedStop.stop.order >= selectedStop.day.stops.length}
                    onPress={() => reorderStop(selectedStop.day, selectedStop.stop, 1)}
                    styles={styles}
                  />
                  {targetDay ? (
                    <StopActionRow
                      icon="calendar-outline"
                      title={`Move to Day ${targetDay.dayNumber}`}
                      description="Shift it into another day and rebalance both days."
                      onPress={() => moveStop(selectedStop.day, selectedStop.stop, targetDay.dayNumber)}
                      styles={styles}
                    />
                  ) : null}
                </View>

                <TouchableOpacity
                  style={styles.removeAction}
                  activeOpacity={0.82}
                  onPress={() => removeStop(selectedStop.day, selectedStop.stop)}
                >
                  <View style={styles.removeIcon}>
                    <Ionicons name="trash-outline" size={18} color="#C65353" />
                  </View>
                  <View style={styles.removeCopy}>
                    <Text style={styles.removeTitle}>Remove from plan</Text>
                    <Text style={styles.removeDescription}>Delete this stop from Day {selectedStop.day.dayNumber}.</Text>
                  </View>
                </TouchableOpacity>
              </>
            ) : null}
          </Pressable>
        </Pressable>
      </Modal>
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

function StopActionRow({
  icon,
  title,
  description,
  disabled,
  onPress,
  styles,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  description: string;
  disabled?: boolean;
  onPress: () => void;
  styles: ItineraryStyles;
}) {
  return (
    <TouchableOpacity
      style={[styles.actionRow, disabled && styles.actionRowDisabled]}
      activeOpacity={0.82}
      disabled={disabled}
      onPress={onPress}
    >
      <View style={styles.actionRowIcon}>
        <Ionicons name={icon} size={18} color={disabled ? '#B9AAA4' : '#A989AA'} />
      </View>
      <View style={styles.actionRowCopy}>
        <Text style={[styles.actionRowTitle, disabled && styles.actionRowTitleDisabled]}>{title}</Text>
        <Text style={styles.actionRowDescription}>{description}</Text>
      </View>
      <Ionicons name="chevron-forward" size={16} color="#B9AAA4" />
    </TouchableOpacity>
  );
}

function iconForCategory(category: string): keyof typeof Ionicons.glyphMap {
  const normalized = category.toUpperCase();
  if (normalized.includes('COFFEE')) return 'cafe-outline';
  if (normalized.includes('FOOD')) return 'restaurant-outline';
  if (normalized.includes('CULTURE')) return 'color-palette-outline';
  if (normalized.includes('WALKING')) return 'walk-outline';
  if (normalized.includes('FREE')) return 'leaf-outline';
  return 'location-outline';
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
  stopCopy: { flex: 1, minWidth: 0 },
  stopText: { color: colors.midnight, fontSize: typography.small, fontWeight: '800' },
  stopOptional: { color: colors.slate },
  stopTime: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 2 },
  stopMenuButton: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    height: 32,
    justifyContent: 'center',
    marginLeft: spacing.sm,
    width: 32,
  },
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
  actionOverlay: {
    backgroundColor: 'rgba(39, 35, 33, 0.34)',
    flex: 1,
    justifyContent: 'flex-end',
    padding: spacing.md,
  },
  actionSheet: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    padding: spacing.md,
    shadowColor: colors.shadow,
    shadowOffset: { width: 0, height: -8 },
    shadowOpacity: 0.16,
    shadowRadius: 24,
  },
  actionHandle: {
    alignSelf: 'center',
    backgroundColor: colors.mist,
    borderRadius: radius.pill,
    height: 4,
    marginBottom: spacing.md,
    width: 42,
  },
  actionHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing.sm,
  },
  actionIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.lg,
    height: 44,
    justifyContent: 'center',
    width: 44,
  },
  actionTitleBlock: { flex: 1, minWidth: 0 },
  actionTitle: {
    color: colors.midnight,
    fontSize: typography.h3,
    fontWeight: '900',
  },
  actionMeta: {
    color: colors.slate,
    fontSize: typography.tiny,
    fontWeight: '800',
    marginTop: 3,
  },
  actionClose: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderRadius: radius.pill,
    height: 34,
    justifyContent: 'center',
    width: 34,
  },
  actionList: {
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    marginTop: spacing.md,
    overflow: 'hidden',
  },
  actionRow: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderBottomColor: colors.mist,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: spacing.sm,
    minHeight: 66,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.sm,
  },
  actionRowDisabled: { opacity: 0.46 },
  actionRowIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    height: 36,
    justifyContent: 'center',
    width: 36,
  },
  actionRowCopy: { flex: 1, minWidth: 0 },
  actionRowTitle: {
    color: colors.midnight,
    fontSize: typography.small,
    fontWeight: '900',
  },
  actionRowTitleDisabled: { color: colors.slate },
  actionRowDescription: {
    color: colors.slate,
    fontSize: typography.tiny,
    fontWeight: '700',
    lineHeight: 16,
    marginTop: 2,
  },
  removeAction: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderRadius: radius.lg,
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.md,
    padding: spacing.sm,
  },
  removeIcon: {
    alignItems: 'center',
    backgroundColor: '#F7E7E3',
    borderRadius: radius.md,
    height: 36,
    justifyContent: 'center',
    width: 36,
  },
  removeCopy: { flex: 1, minWidth: 0 },
  removeTitle: {
    color: '#C65353',
    fontSize: typography.small,
    fontWeight: '900',
  },
  removeDescription: {
    color: colors.slate,
    fontSize: typography.tiny,
    fontWeight: '700',
    marginTop: 2,
  },
});
}
