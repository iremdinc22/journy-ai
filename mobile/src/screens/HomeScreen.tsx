import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Image,
  ImageBackground,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';

import { tripApi } from '../api/journyApi';
import { session } from '../api/session';
import type { ItineraryResponse, TripResponse } from '../api/types';
import { useAppTheme } from '../theme/ThemeContext';
import { InlineError, InlineLoading } from '../components/StateViews';

const journyLogo = require('../../assets/images/journy-logo.png');

const visualPicks = [
  {
    title: 'Ten Belles',
    meta: 'Coffee - 9 min walk',
    image:
      'https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=700&q=85',
  },
  {
    title: 'Museumplein',
    meta: 'Culture - low crowd',
    image:
      'https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=700&q=85',
  },
  {
    title: 'Canal loop',
    meta: 'Walk - golden hour',
    image:
      'https://images.unsplash.com/photo-1584003564911-a7a321c84e1c?auto=format&fit=crop&w=700&q=85',
  },
];

export default function HomeScreen() {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const navigation = useNavigation<any>();
  const [trip, setTrip] = useState<TripResponse | undefined>(() => session.getCurrentTrip());
  const [itinerary, setItinerary] = useState<ItineraryResponse | null>(null);
  const [loading, setLoading] = useState(!session.getCurrentTrip());
  const [error, setError] = useState(false);

  const loadHome = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const current = await tripApi.current();
      session.setCurrentTrip(current);
      const route = await tripApi.itinerary(current.id);
      setTrip(current);
      setItinerary(route);
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
        const current = await tripApi.current();
        session.setCurrentTrip(current);
        const route = await tripApi.itinerary(current.id);
        if (mounted) {
          setTrip(current);
          setItinerary(route);
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

  const firstDay = itinerary?.days[0];
  const firstStops = firstDay?.stops.slice(0, 3);
  const destination = trip?.destination ?? 'Amsterdam';
  const dayTitle = firstDay?.title ?? 'Canals & Museums';
  const walkKm = firstDay?.walkKm ?? trip?.stats.averageWalkKm ?? 6.4;
  const stopCount = firstDay?.stopCount ?? trip?.stats.stops ?? 4;

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View style={styles.brandBlock}>
            <Image source={journyLogo} style={styles.logo} resizeMode="contain" />
            <View style={styles.headerCopy}>
              <Text style={styles.headerLabel}>Current trip</Text>
              <Text style={styles.location}>{destination}</Text>
            </View>
          </View>
          <TouchableOpacity
            style={styles.iconButton}
            activeOpacity={0.85}
            onPress={() => navigation.navigate('Notifications')}
          >
            <Ionicons name="notifications-outline" size={21} color={colors.midnight} />
          </TouchableOpacity>
        </View>

        <ImageBackground
          source={{
            uri: 'https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=900&q=90',
          }}
          style={styles.hero}
          imageStyle={styles.heroImage}
        >
          <LinearGradient colors={['rgba(34,42,45,0.08)', 'rgba(34,42,45,0.72)']} style={styles.heroOverlay}>
            <View style={styles.heroTop}>
              <View style={styles.pill}>
                <Ionicons name="partly-sunny-outline" size={14} color={colors.surface} />
                <Text style={styles.pillText}>Mild weather</Text>
              </View>
            </View>
            <View>
              <Text style={styles.heroKicker}>Day 1</Text>
              <Text style={styles.heroTitle}>{dayTitle}</Text>
              <Text style={styles.heroMeta}>{stopCount} stops - {walkKm.toFixed(1)} km - {formatEnum(trip?.pace ?? 'easy')} pace</Text>
            </View>
          </LinearGradient>
        </ImageBackground>

        <View style={styles.routeSummary}>
          <SummaryItem icon="walk-outline" value={`${walkKm.toFixed(1)} km`} label="walk" colors={colors} styles={styles} />
          <SummaryItem icon="location-outline" value={`${stopCount}`} label="stops" colors={colors} styles={styles} />
          <SummaryItem icon="time-outline" value={formatEnum(trip?.pace ?? 'Easy')} label="pace" colors={colors} styles={styles} />
        </View>

        {loading ? <InlineLoading label="Loading your current trip..." /> : null}
        {error ? (
          <InlineError
            title="Backend connection needs a retry"
            description="Showing the local preview for now. Retry when the API is running."
            onRetry={loadHome}
          />
        ) : null}

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Next in your route</Text>
          <Text style={styles.sectionAction}>Edit</Text>
        </View>

        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.visualList}>
          {(firstStops?.length ? firstStops.map((stop, index) => ({
            title: stop.title,
            meta: `${stop.category} - ${stop.timeWindow}`,
            image: visualPicks[index % visualPicks.length].image,
          })) : visualPicks).map((item) => (
            <TouchableOpacity key={item.title} style={styles.visualCard} activeOpacity={0.88}>
              <Image source={{ uri: item.image }} style={styles.visualImage} />
              <View style={styles.visualBody}>
                <Text style={styles.visualTitle}>{item.title}</Text>
                <Text style={styles.visualMeta}>{item.meta}</Text>
              </View>
            </TouchableOpacity>
          ))}
        </ScrollView>

        <TouchableOpacity style={styles.primaryAction} activeOpacity={0.9}>
          <Ionicons name="navigate" size={17} color={colors.surface} />
          <Text style={styles.primaryActionText}>Start today route</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

function formatEnum(value: string) {
  return value
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/^\w/, (letter) => letter.toUpperCase());
}

function SummaryItem({
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
  styles: HomeStyles;
}) {
  return (
    <View style={styles.summaryItem}>
      <Ionicons name={icon} size={16} color={colors.teal} />
      <Text style={styles.summaryValue}>{value}</Text>
      <Text style={styles.summaryLabel}>{label}</Text>
    </View>
  );
}

type Theme = ReturnType<typeof useAppTheme>['theme'];
type HomeStyles = ReturnType<typeof createStyles>;

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
  brandBlock: { alignItems: 'center', flex: 1, flexDirection: 'row' },
  logo: {
    height: 48,
    width: 118,
  },
  headerCopy: {
    borderLeftColor: colors.mist,
    borderLeftWidth: 1,
    marginLeft: spacing.sm,
    paddingLeft: spacing.sm,
  },
  headerLabel: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  location: { color: colors.midnight, fontSize: typography.small, fontWeight: '900', marginTop: 2 },
  iconButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.md,
    borderWidth: 1,
    height: 44,
    justifyContent: 'center',
    width: 44,
  },
  hero: {
    height: 285,
    marginTop: spacing.lg,
  },
  heroImage: {
    borderRadius: radius.xl,
  },
  heroOverlay: {
    borderRadius: radius.xl,
    flex: 1,
    justifyContent: 'space-between',
    padding: spacing.lg,
  },
  heroTop: { alignItems: 'flex-start' },
  pill: {
    alignItems: 'center',
    backgroundColor: 'rgba(255,255,255,0.18)',
    borderColor: 'rgba(255,255,255,0.28)',
    borderRadius: radius.pill,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 5,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  pillText: { color: colors.surface, fontSize: typography.tiny, fontWeight: '900' },
  heroKicker: { color: 'rgba(255,255,255,0.76)', fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  heroTitle: { color: colors.surface, fontSize: 34, fontWeight: '900', lineHeight: 38, marginTop: spacing.xs },
  heroMeta: { color: 'rgba(255,255,255,0.78)', fontSize: typography.small, fontWeight: '800', marginTop: spacing.xs },
  routeSummary: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.md,
    padding: spacing.md,
  },
  summaryItem: { alignItems: 'center', flex: 1 },
  summaryValue: { color: colors.midnight, fontSize: typography.small, fontWeight: '900', marginTop: 3 },
  summaryLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 2 },
  sectionHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing.xl,
  },
  sectionTitle: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
  sectionAction: { color: colors.teal, fontSize: typography.small, fontWeight: '900' },
  visualList: { gap: spacing.md, paddingVertical: spacing.md },
  visualCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    overflow: 'hidden',
    width: 190,
  },
  visualImage: { height: 118, width: '100%' },
  visualBody: { padding: spacing.md },
  visualTitle: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  visualMeta: { color: colors.slate, fontSize: typography.tiny, fontWeight: '700', marginTop: 4 },
  primaryAction: {
    alignItems: 'center',
    backgroundColor: colors.midnight,
    borderRadius: radius.lg,
    flexDirection: 'row',
    gap: spacing.sm,
    justifyContent: 'center',
    marginTop: spacing.sm,
    minHeight: 56,
  },
  primaryActionText: { color: colors.surface, fontSize: typography.body, fontWeight: '900' },
});
}
