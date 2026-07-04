import React, { useMemo, useState } from 'react';
import { SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../navigation/AppNavigator';
import type { ItineraryStop, PlaceResponse } from '../api/types';
import { useAppTheme } from '../theme/ThemeContext';

type Props = NativeStackScreenProps<RootStackParamList, 'DayRouteDetail'>;
type ActionKey = 'lighter' | 'food' | 'replace';

const categoryImages: Record<string, string> = {
  FOOD: 'https://images.unsplash.com/photo-1533777857889-4be7c70b33f7?auto=format&fit=crop&w=900&q=85',
  COFFEE: 'https://images.unsplash.com/photo-1511920170033-f8396924c348?auto=format&fit=crop&w=900&q=85',
  CULTURE: 'https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=900&q=85',
  WALKING: 'https://images.unsplash.com/photo-1584003564911-a7a321c84e1c?auto=format&fit=crop&w=900&q=85',
  FREE: 'https://images.unsplash.com/photo-1499856871958-5b9627545d1a?auto=format&fit=crop&w=900&q=85',
};

export default function DayRouteDetailScreen({ navigation, route }: Props) {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme, isDark), [theme, isDark]);
  const { colors } = theme;
  const { destination, day } = route.params;
  const [selectedAction, setSelectedAction] = useState<ActionKey>('lighter');

  const paceLabel = day.walkKm <= 4.5 ? 'Relaxed' : day.walkKm >= 7 ? 'Full' : 'Balanced';
  const focusLabel = day.stops.some((stop) => stop.category === 'FOOD' || stop.category === 'COFFEE')
    ? 'Food breaks included'
    : 'Culture-first flow';
  const actionMessage = {
    lighter: 'Journy would remove the longest transfer and keep the strongest stops.',
    food: 'Journy would add a nearby food or coffee stop without stretching the route.',
    replace: 'Journy would swap one stop for a better fit in the same area.',
  }[selectedAction];

  const openStop = (stop: ItineraryStop) => {
    navigation.navigate('PlaceDetail', { place: toPlaceDetail(stop, destination) });
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
          <TouchableOpacity style={styles.startButton} activeOpacity={0.88}>
            <Ionicons name="navigate-outline" size={16} color={colors.surface} />
            <Text style={styles.startButtonText}>Start route</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.hero}>
          <Text style={styles.eyebrow}>Day {day.dayNumber} - {destination}</Text>
          <Text style={styles.title}>{day.title}</Text>
          <Text style={styles.subtitle}>{day.summary}</Text>
          <View style={styles.heroMetaRow}>
            <HeroMetric icon="walk-outline" value={`${day.walkKm.toFixed(1)} km`} label="Walking" colors={colors} styles={styles} />
            <HeroMetric icon="location-outline" value={`${day.stopCount}`} label="Stops" colors={colors} styles={styles} />
            <HeroMetric icon="speedometer-outline" value={paceLabel} label="Pace" colors={colors} styles={styles} />
          </View>
        </View>

        <View style={styles.routeCard}>
          <View style={styles.mapHeader}>
            <View>
              <Text style={styles.mapTitle}>Route preview</Text>
              <Text style={styles.mapSubtitle}>{focusLabel}</Text>
            </View>
            <View style={styles.routePill}>
              <Ionicons name="sparkles-outline" size={13} color={colors.teal} />
              <Text style={styles.routePillText}>AI grouped</Text>
            </View>
          </View>

          <View style={styles.mapCanvas}>
            <View style={styles.mapWater} />
            <View style={[styles.mapRoad, styles.roadOne]} />
            <View style={[styles.mapRoad, styles.roadTwo]} />
            <View style={[styles.mapRoad, styles.roadThree]} />
            <View style={styles.routeLineOne} />
            <View style={styles.routeLineTwo} />
            <View style={styles.routeLineThree} />
            {day.stops.slice(0, 5).map((stop, index) => (
              <TouchableOpacity
                key={`${stop.order}-${stop.title}`}
                style={[
                  styles.mapPin,
                  index === 0 && styles.pinOne,
                  index === 1 && styles.pinTwo,
                  index === 2 && styles.pinThree,
                  index === 3 && styles.pinFour,
                  index >= 4 && styles.pinFive,
                ]}
                activeOpacity={0.86}
                onPress={() => openStop(stop)}
              >
                <Text style={styles.mapPinText}>{index + 1}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        <View style={styles.actionRow}>
          <ActionChip label="Make lighter" icon="leaf-outline" active={selectedAction === 'lighter'} onPress={() => setSelectedAction('lighter')} styles={styles} />
          <ActionChip label="Add food stop" icon="restaurant-outline" active={selectedAction === 'food'} onPress={() => setSelectedAction('food')} styles={styles} />
          <ActionChip label="Replace stop" icon="swap-horizontal-outline" active={selectedAction === 'replace'} onPress={() => setSelectedAction('replace')} styles={styles} />
        </View>
        <View style={styles.aiNote}>
          <Ionicons name="sparkles-outline" size={18} color={colors.teal} />
          <View style={styles.aiNoteCopy}>
            <Text style={styles.aiNoteLabel}>Suggested adjustment</Text>
            <Text style={styles.aiNoteText}>{actionMessage}</Text>
          </View>
          <TouchableOpacity style={styles.applyButton} activeOpacity={0.86}>
            <Text style={styles.applyButtonText}>Apply</Text>
          </TouchableOpacity>
        </View>

        <Text style={styles.sectionTitle}>Stop timeline</Text>
        <View style={styles.stopList}>
          {day.stops.map((stop, index) => (
            <TouchableOpacity
              key={`${stop.order}-${stop.title}`}
              style={styles.stopRow}
              activeOpacity={0.86}
              onPress={() => openStop(stop)}
            >
              <View style={styles.stopRail}>
                <View style={styles.stopNumber}>
                  <Text style={styles.stopNumberText}>{index + 1}</Text>
                </View>
                {index !== day.stops.length - 1 ? <View style={styles.verticalLine} /> : null}
              </View>
              <View style={styles.stopCopy}>
                <View style={styles.stopTopLine}>
                  <Text style={styles.stopWindow}>{stop.timeWindow}</Text>
                  <Text style={styles.stopCategory}>{formatCategory(stop.category)}</Text>
                </View>
                <Text style={styles.stopTitle}>{stop.title}</Text>
                <Text style={styles.stopNote}>{stop.note}</Text>
              </View>
              <Ionicons name="chevron-forward" size={18} color={colors.softMuted} />
            </TouchableOpacity>
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

type Theme = ReturnType<typeof useAppTheme>['theme'];
type DetailStyles = ReturnType<typeof createStyles>;

function HeroMetric({
  icon,
  value,
  label,
  colors,
  styles,
}: {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  value: string;
  label: string;
  colors: Theme['colors'];
  styles: DetailStyles;
}) {
  return (
    <View style={styles.heroMetric}>
      <Ionicons name={icon} size={18} color={colors.teal} />
      <Text style={styles.heroMetricValue}>{value}</Text>
      <Text style={styles.heroMetricLabel}>{label}</Text>
    </View>
  );
}

function ActionChip({
  label,
  icon,
  active,
  onPress,
  styles,
}: {
  label: string;
  icon: React.ComponentProps<typeof Ionicons>['name'];
  active: boolean;
  onPress: () => void;
  styles: DetailStyles;
}) {
  return (
    <TouchableOpacity style={[styles.actionChip, active && styles.actionChipActive]} activeOpacity={0.86} onPress={onPress}>
      <Ionicons name={icon} size={15} style={active ? styles.actionIconActive : styles.actionIcon} />
      <Text style={[styles.actionText, active && styles.actionTextActive]}>{label}</Text>
    </TouchableOpacity>
  );
}

function toPlaceDetail(stop: ItineraryStop, destination: string): PlaceResponse {
  const category = stop.category || 'WALKING';

  return {
    id: `stop-${destination}-${stop.order}-${stop.title}`.toLowerCase().replace(/[^a-z0-9]+/g, '-'),
    name: stop.title,
    city: destination,
    category,
    description: stop.note,
    priceLevel: 'Mid',
    rating: 4.7,
    imageUrl: categoryImages[category] ?? categoryImages.WALKING,
    address: `${destination} city center`,
    latitude: stop.latitude,
    longitude: stop.longitude,
    openingHours: stop.timeWindow,
    estimatedVisitMinutes: category === 'FOOD' || category === 'COFFEE' ? 45 : 60,
    tags: `${formatCategory(category)}, Walkable`,
  };
}

function formatCategory(category: string) {
  return category.toLowerCase().replace(/_/g, ' ');
}

function createStyles({ colors, radius, spacing, typography }: Theme, isDark: boolean) {
  return StyleSheet.create({
    safe: { flex: 1, backgroundColor: colors.ivory },
    content: { padding: spacing.lg, paddingBottom: 132 },
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
    startButton: {
      alignItems: 'center',
      backgroundColor: colors.midnight,
      borderRadius: radius.pill,
      flexDirection: 'row',
      gap: 6,
      minHeight: 42,
      paddingHorizontal: spacing.md,
    },
    startButtonText: { color: colors.surface, fontSize: typography.small, fontWeight: '900' },
    hero: {
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.xl,
      borderWidth: 1,
      marginTop: spacing.lg,
      padding: spacing.lg,
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
      lineHeight: 36,
      marginTop: spacing.xs,
    },
    subtitle: {
      color: colors.slate,
      fontSize: typography.body,
      fontWeight: '700',
      lineHeight: 23,
      marginTop: spacing.sm,
    },
    heroMetaRow: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.lg },
    heroMetric: {
      alignItems: 'center',
      backgroundColor: colors.surfaceWarm,
      borderColor: colors.mist,
      borderRadius: radius.lg,
      borderWidth: 1,
      flex: 1,
      minHeight: 88,
      padding: spacing.sm,
    },
    heroMetricValue: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900', marginTop: spacing.xs },
    heroMetricLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 2 },
    routeCard: {
      backgroundColor: colors.surfaceWarm,
      borderColor: colors.mist,
      borderRadius: radius.xl,
      borderWidth: 1,
      marginTop: spacing.md,
      overflow: 'hidden',
      padding: spacing.md,
    },
    mapHeader: {
      alignItems: 'center',
      flexDirection: 'row',
      justifyContent: 'space-between',
      marginBottom: spacing.md,
    },
    mapTitle: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
    mapSubtitle: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 3 },
    routePill: {
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderRadius: radius.pill,
      flexDirection: 'row',
      gap: 4,
      paddingHorizontal: spacing.sm,
      paddingVertical: spacing.xs,
    },
    routePillText: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
    mapCanvas: {
      backgroundColor: isDark ? colors.canvas : colors.fog,
      borderRadius: radius.lg,
      height: 250,
      overflow: 'hidden',
      position: 'relative',
    },
    mapWater: {
      backgroundColor: isDark ? 'rgba(169,185,167,0.34)' : 'rgba(169,185,167,0.42)',
      borderRadius: 140,
      height: 178,
      left: -46,
      position: 'absolute',
      top: 84,
      transform: [{ rotate: '-14deg' }],
      width: 430,
    },
    mapRoad: {
      backgroundColor: isDark ? 'rgba(255,253,248,0.34)' : 'rgba(255,255,255,0.86)',
      borderRadius: radius.pill,
      height: 12,
      position: 'absolute',
    },
    roadOne: { left: -10, top: 58, transform: [{ rotate: '18deg' }], width: 330 },
    roadTwo: { left: 70, top: 132, transform: [{ rotate: '-30deg' }], width: 320 },
    roadThree: { left: -2, top: 206, transform: [{ rotate: '24deg' }], width: 260 },
    routeLineOne: {
      backgroundColor: colors.teal,
      borderRadius: radius.pill,
      height: 5,
      left: 68,
      position: 'absolute',
      top: 118,
      transform: [{ rotate: '28deg' }],
      width: 128,
    },
    routeLineTwo: {
      backgroundColor: colors.midnight,
      borderRadius: radius.pill,
      height: 5,
      left: 156,
      position: 'absolute',
      top: 160,
      transform: [{ rotate: '-24deg' }],
      width: 112,
    },
    routeLineThree: {
      backgroundColor: colors.teal,
      borderRadius: radius.pill,
      height: 5,
      left: 205,
      opacity: 0.85,
      position: 'absolute',
      top: 192,
      transform: [{ rotate: '32deg' }],
      width: 82,
    },
    mapPin: {
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderColor: colors.teal,
      borderRadius: radius.pill,
      borderWidth: 3,
      height: 40,
      justifyContent: 'center',
      position: 'absolute',
      width: 40,
    },
    pinOne: { left: 48, top: 84 },
    pinTwo: { left: 144, top: 128 },
    pinThree: { left: 234, top: 96 },
    pinFour: { left: 194, top: 196 },
    pinFive: { left: 274, top: 178 },
    mapPinText: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900' },
    actionRow: {
      flexDirection: 'row',
      flexWrap: 'wrap',
      gap: spacing.sm,
      marginTop: spacing.md,
    },
    actionChip: {
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.pill,
      borderWidth: 1,
      flexDirection: 'row',
      gap: 5,
      paddingHorizontal: spacing.md,
      paddingVertical: spacing.sm,
    },
    actionChipActive: { backgroundColor: colors.midnight, borderColor: colors.midnight },
    actionIcon: { color: colors.teal },
    actionIconActive: { color: colors.surface },
    actionText: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900' },
    actionTextActive: { color: colors.surface },
    aiNote: {
      alignItems: 'center',
      backgroundColor: colors.lilac,
      borderColor: colors.mist,
      borderRadius: radius.lg,
      borderWidth: 1,
      flexDirection: 'row',
      gap: spacing.sm,
      marginTop: spacing.sm,
      padding: spacing.md,
    },
    aiNoteCopy: { flex: 1 },
    aiNoteLabel: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
    aiNoteText: { color: colors.midnight, fontSize: typography.small, fontWeight: '800', lineHeight: 19, marginTop: 3 },
    applyButton: {
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.pill,
      borderWidth: 1,
      paddingHorizontal: spacing.md,
      paddingVertical: spacing.sm,
    },
    applyButtonText: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900' },
    sectionTitle: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900', marginTop: spacing.xl },
    stopList: {
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.xl,
      borderWidth: 1,
      marginTop: spacing.md,
      overflow: 'hidden',
    },
    stopRow: {
      alignItems: 'center',
      flexDirection: 'row',
      minHeight: 96,
      paddingHorizontal: spacing.md,
      paddingVertical: spacing.sm,
    },
    stopRail: { alignItems: 'center', alignSelf: 'stretch', width: 38 },
    stopNumber: {
      alignItems: 'center',
      backgroundColor: colors.fog,
      borderRadius: radius.pill,
      height: 34,
      justifyContent: 'center',
      width: 34,
    },
    stopNumberText: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900' },
    verticalLine: { backgroundColor: colors.mist, flex: 1, marginTop: 6, width: 1 },
    stopCopy: { flex: 1, marginLeft: spacing.md, marginRight: spacing.sm },
    stopTopLine: { alignItems: 'center', flexDirection: 'row', gap: spacing.sm, marginBottom: 4 },
    stopWindow: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
    stopCategory: {
      backgroundColor: colors.fog,
      borderRadius: radius.pill,
      color: colors.slate,
      fontSize: typography.tiny,
      fontWeight: '900',
      overflow: 'hidden',
      paddingHorizontal: spacing.xs,
      paddingVertical: 2,
      textTransform: 'capitalize',
    },
    stopTitle: { color: colors.midnight, fontSize: typography.body, fontWeight: '900' },
    stopNote: { color: colors.slate, fontSize: typography.small, fontWeight: '700', lineHeight: 18, marginTop: 4 },
  });
}
