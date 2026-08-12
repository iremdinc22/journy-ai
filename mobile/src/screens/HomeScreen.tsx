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
import { useLanguage, useTranslation } from '../i18n/LanguageContext';
import { useAppTheme } from '../theme/ThemeContext';
import { InlineError, InlineLoading } from '../components/StateViews';
import { cityImage, placeImage } from '../utils/destinationVisuals';
import { localizeDynamicText } from '../utils/localizedDynamicText';

const journyLogo = require('../../assets/images/journy-logo.png');

export default function HomeScreen() {
  const { isDark, theme } = useAppTheme();
  const { language } = useLanguage();
  const t = useTranslation();
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

  const lifecycle = tripLifecycle(trip);
  const todayDay = itinerary?.days.find((day) => day.dayNumber === lifecycle.dayNumber) ?? itinerary?.days[0];
  const firstDay = lifecycle.status === 'ACTIVE' ? todayDay : itinerary?.days[0];
  const firstStops = firstDay?.stops.slice(0, 3);
  const destination = trip?.destination ?? session.getCurrentTrip()?.destination ?? t('home.yourTrip');
  const heroImage = cityImage(destination);
  const dayTitle = localizeDynamicText(firstDay?.title ?? `${destination} day route`, language);
  const walkKm = firstDay?.walkKm ?? trip?.stats.averageWalkKm ?? 6.4;
  const stopCount = firstDay?.stopCount ?? trip?.stats.stops ?? 4;
  const nextStop = firstDay?.stops[0];

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View style={styles.brandBlock}>
            <Image source={journyLogo} style={styles.logo} resizeMode="contain" />
            <View style={styles.headerCopy}>
              <Text style={styles.headerLabel}>{t('home.currentTrip')}</Text>
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
            uri: heroImage,
          }}
          style={styles.hero}
          imageStyle={styles.heroImage}
        >
          <LinearGradient colors={['rgba(34,42,45,0.08)', 'rgba(34,42,45,0.72)']} style={styles.heroOverlay}>
            <View style={styles.heroTop}>
              <View style={styles.pill}>
                <Ionicons name="partly-sunny-outline" size={14} color={colors.surface} />
                <Text style={styles.pillText}>{t('home.mildWeather')}</Text>
              </View>
            </View>
            <View>
              <Text style={styles.heroKicker}>{heroKicker(lifecycle, destination, t)}</Text>
              <Text style={styles.heroTitle}>{heroTitle(lifecycle, destination, dayTitle, t)}</Text>
              <Text style={styles.heroMeta}>{stopCount} {t('home.stops')} - {walkKm.toFixed(1)} km - {formatEnum(trip?.pace ?? 'easy', language)} {t('home.pace')}</Text>
            </View>
          </LinearGradient>
        </ImageBackground>

        {lifecycle.status === 'ACTIVE' && firstDay ? (
          <View style={styles.todayCard}>
            <View style={styles.todayTop}>
              <View>
                <Text style={styles.todayKicker}>{t('home.todayMode')}</Text>
                <Text style={styles.todayTitle}>{t('home.todayIn', { destination })}</Text>
              </View>
              <View style={styles.nextPill}>
                <Ionicons name="time-outline" size={14} color={colors.teal} />
                <Text style={styles.nextPillText}>{t('home.nextStop')}</Text>
              </View>
            </View>
            {nextStop ? (
              <View style={styles.nextStopRow}>
                <Text style={styles.nextStopTime}>{nextStop.timeWindow}</Text>
                <View style={styles.nextStopCopy}>
                  <Text style={styles.nextStopTitle}>{localizeDynamicText(nextStop.title, language)}</Text>
                  <Text style={styles.nextStopMeta}>{localizeDynamicText(nextStop.category.toLowerCase(), language)} - {nextStop.optional ? t('home.optional') : t('home.mainRoute')}</Text>
                </View>
                <TouchableOpacity style={styles.directionsButton} activeOpacity={0.86}>
                  <Ionicons name="navigate-outline" size={17} color={colors.surface} />
                </TouchableOpacity>
              </View>
            ) : null}
            <View style={styles.todayTimeline}>
              {firstDay.stops.slice(0, 4).map((stop) => (
                <View key={stop.id} style={styles.todayTimelineRow}>
                  <Text style={styles.todayTime}>{stop.timeWindow}</Text>
                  <Text style={styles.todayStop} numberOfLines={1}>{localizeDynamicText(stop.title, language)}</Text>
                </View>
              ))}
            </View>
            <TouchableOpacity style={styles.rebuildButton} activeOpacity={0.86} onPress={() => navigation.navigate('Assistant')}>
              <Ionicons name="sparkles-outline" size={15} color={colors.teal} />
              <Text style={styles.rebuildText}>{t('home.optimizeToday')}</Text>
            </TouchableOpacity>
          </View>
        ) : null}

        <View style={styles.routeSummary}>
          <SummaryItem icon="walk-outline" value={`${walkKm.toFixed(1)} km`} label={t('home.walk')} colors={colors} styles={styles} />
          <SummaryItem icon="location-outline" value={`${stopCount}`} label={t('home.stops')} colors={colors} styles={styles} />
          <SummaryItem icon="time-outline" value={formatEnum(trip?.pace ?? 'Easy', language)} label={t('home.pace')} colors={colors} styles={styles} />
        </View>

        {loading ? <InlineLoading label={t('home.loading')} /> : null}
        {error ? (
          <InlineError
            title={t('home.errorTitle')}
            description={t('home.errorDescription')}
            onRetry={loadHome}
          />
        ) : null}

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>{t('home.nextRoute')}</Text>
          <Text style={styles.sectionAction}>{t('home.edit')}</Text>
        </View>

        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.visualList}>
          {(firstStops?.length ? firstStops.map((stop, index) => ({
            title: localizeDynamicText(stop.title, language),
            meta: `${localizeDynamicText(stop.category, language)} - ${localizeDynamicText(stop.timeWindow, language)}`,
            image: imageForStop(destination, stop.category, stop.title, index),
          })) : fallbackVisualPicks(destination)).map((item) => (
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
          <Text style={styles.primaryActionText}>{lifecycle.status === 'ACTIVE' ? t('home.startTodayRoute') : t('home.openTripPlan')}</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

function formatEnum(value: string, language: 'en' | 'tr' = 'en') {
  const formatted = value
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/^\w/, (letter) => letter.toUpperCase());
  return localizeDynamicText(formatted, language);
}

function tripLifecycle(trip?: TripResponse) {
  if (!trip) {
    return { status: 'UPCOMING' as const, dayNumber: 1, daysUntil: 0 };
  }
  const today = startOfDay(new Date());
  const start = startOfDay(new Date(trip.startDate));
  const end = startOfDay(new Date(trip.endDate));
  const daysUntil = Math.ceil((start.getTime() - today.getTime()) / 86400000);
  if (today < start) {
    return { status: 'UPCOMING' as const, dayNumber: 1, daysUntil };
  }
  if (today >= end) {
    return { status: 'COMPLETED' as const, dayNumber: trip.days, daysUntil: 0 };
  }
  const dayNumber = Math.min(trip.days, Math.max(1, Math.floor((today.getTime() - start.getTime()) / 86400000) + 1));
  return { status: 'ACTIVE' as const, dayNumber, daysUntil: 0 };
}

function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

type Translate = ReturnType<typeof useTranslation>;

function heroKicker(lifecycle: ReturnType<typeof tripLifecycle>, destination: string, t: Translate) {
  if (lifecycle.status === 'ACTIVE') return t('itinerary.day', { day: lifecycle.dayNumber });
  if (lifecycle.status === 'COMPLETED') return t('home.tripComplete');
  return lifecycle.daysUntil > 0 ? t('home.inDays', { destination, count: lifecycle.daysUntil }) : t('home.upcomingTrip');
}

function heroTitle(lifecycle: ReturnType<typeof tripLifecycle>, destination: string, dayTitle: string, t: Translate) {
  if (lifecycle.status === 'ACTIVE') return t('home.goodMorning', { destination });
  if (lifecycle.status === 'COMPLETED') return t('home.completeTitle', { destination });
  return dayTitle;
}

function imageForStop(destination: string, category: string, title: string, index: number) {
  return placeImage(destination, category, `${title}-${index}`);
}

function fallbackVisualPicks(destination: string) {
  return [
    {
      title: `${destination} coffee break`,
      meta: 'Coffee - route friendly',
      image: placeImage(destination, 'COFFEE', `${destination}-coffee`),
    },
    {
      title: `${destination} culture window`,
      meta: 'Culture - flexible stop',
      image: placeImage(destination, 'CULTURE', `${destination}-culture`),
    },
    {
      title: `${destination} city walk`,
      meta: 'Walk - local area',
      image: placeImage(destination, 'WALKING', `${destination}-walk`),
    },
  ];
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
  todayCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.md,
    padding: spacing.md,
  },
  todayTop: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  todayKicker: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  todayTitle: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900', marginTop: 3 },
  nextPill: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    flexDirection: 'row',
    gap: 4,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  nextPillText: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900' },
  nextStopRow: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderRadius: radius.lg,
    flexDirection: 'row',
    marginTop: spacing.md,
    padding: spacing.sm,
  },
  nextStopTime: { color: colors.teal, fontSize: typography.small, fontWeight: '900', width: 54 },
  nextStopCopy: { flex: 1, minWidth: 0 },
  nextStopTitle: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  nextStopMeta: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 2, textTransform: 'capitalize' },
  directionsButton: {
    alignItems: 'center',
    backgroundColor: colors.midnight,
    borderRadius: radius.pill,
    height: 36,
    justifyContent: 'center',
    marginLeft: spacing.sm,
    width: 36,
  },
  todayTimeline: { marginTop: spacing.md },
  todayTimelineRow: {
    alignItems: 'center',
    borderTopColor: colors.mist,
    borderTopWidth: 1,
    flexDirection: 'row',
    minHeight: 38,
  },
  todayTime: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', width: 58 },
  todayStop: { color: colors.midnight, flex: 1, fontSize: typography.small, fontWeight: '800' },
  rebuildButton: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    flexDirection: 'row',
    gap: 5,
    marginTop: spacing.md,
    minHeight: 34,
    paddingHorizontal: spacing.sm,
  },
  rebuildText: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900' },
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
