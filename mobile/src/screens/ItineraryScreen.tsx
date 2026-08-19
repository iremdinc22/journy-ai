import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Modal, Pressable, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { agentApi, tripApi } from '../api/journyApi';
import { session } from '../api/session';
import type { ItineraryDay, ItineraryResponse, ItineraryTimelineItem, WeatherAdjustmentResponse } from '../api/types';
import { useLanguage, useTranslation } from '../i18n/LanguageContext';
import { useAppTheme } from '../theme/ThemeContext';
import { InlineError, InlineLoading } from '../components/StateViews';
import { cityCoordinates } from '../utils/destinationVisuals';
import { localizeDynamicList, localizeDynamicText } from '../utils/localizedDynamicText';

export default function ItineraryScreen() {
  const { isDark, theme } = useAppTheme();
  const { language } = useLanguage();
  const t = useTranslation();
  const styles = useMemo(() => createStyles(theme, isDark), [isDark, theme]);
  const { colors } = theme;
  const navigation = useNavigation<any>();
  const [itinerary, setItinerary] = useState<ItineraryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [selectedStop, setSelectedStop] = useState<{ day: ItineraryDay; stop: ItineraryDay['stops'][number] } | null>(null);
  const [weatherPreviewOpen, setWeatherPreviewOpen] = useState(false);
  const [weatherApplying, setWeatherApplying] = useState(false);
  const [weatherApplied, setWeatherApplied] = useState(false);
  const [weatherSignal, setWeatherSignal] = useState<WeatherAdjustmentResponse | null>(null);

  const loadItinerary = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const current = session.getCurrentTrip() ?? await tripApi.current();
      session.setCurrentTrip(current);
      const response = await tripApi.itinerary(current.id);
      const weather = await tripApi.weatherAdjustment(current.id).catch(() => null);
      setItinerary(response);
      setWeatherSignal(weather?.available ? weather : null);
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
        const weather = await tripApi.weatherAdjustment(current.id).catch(() => null);
        if (mounted) {
          setItinerary(response);
          setWeatherSignal(weather?.available ? weather : null);
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

  const fallbackDestination = session.getCurrentTrip()?.destination ?? t('home.yourTrip');
  const destination = itinerary?.destination ?? fallbackDestination;
  const visibleDays = itinerary?.days ?? previewDays(destination, session.getCurrentTrip()?.days ?? 1);
  const tripId = itinerary?.tripId ?? session.getCurrentTrip()?.id ?? 'preview-trip';
  const totalWalk = visibleDays.reduce((sum, day) => sum + day.walkKm, 0);
  const totalStops = visibleDays.reduce((sum, day) => sum + day.stopCount, 0);
  const weatherTargetDay = weatherSignal
    ? visibleDays.find((day) => day.dayNumber === weatherSignal.dayNumber)
    : undefined;

  const updateDay = (updatedDay: ItineraryDay) => {
    setItinerary((current) => current
      ? { ...current, days: current.days.map((day) => day.dayNumber === updatedDay.dayNumber ? updatedDay : day) }
      : current);
  };

  const applyWeatherAdjustment = async () => {
    if (!weatherTargetDay || tripId === 'preview-trip') {
      Alert.alert(t('itinerary.livePlanRequiredTitle'), t('itinerary.livePlanWeather'));
      return;
    }
    setWeatherApplying(true);
    try {
      const updatedDay = await agentApi.apply(tripId, weatherTargetDay.dayNumber, 'RAIN_REPLAN', language);
      updateDay(updatedDay);
      setWeatherApplied(true);
      setWeatherSignal((current) => current ? { ...current, available: false } : current);
      setWeatherPreviewOpen(false);
    } catch {
      Alert.alert(t('itinerary.weatherUpdateErrorTitle'), t('itinerary.backendRetry'));
    } finally {
      setWeatherApplying(false);
    }
  };

  const ensureLivePlan = () => {
    if (!itinerary) {
      Alert.alert(t('itinerary.livePlanRequiredTitle'), t('itinerary.livePlanEdit'));
      return false;
    }
    return true;
  };

  const closeStopActions = () => setSelectedStop(null);

  const removeStop = (day: ItineraryDay, stop: ItineraryDay['stops'][number]) => {
    closeStopActions();
    Alert.alert(
      t('itinerary.removeStopTitle'),
      t('itinerary.removeStopMessage', { stop: stop.title, day: day.dayNumber }),
      [
        { text: t('common.cancel'), style: 'cancel' },
        {
          text: t('itinerary.remove'),
          style: 'destructive',
          onPress: async () => {
            if (!ensureLivePlan()) return;
            try {
              updateDay(await tripApi.removeStop(tripId, day.dayNumber, stop.id));
            } catch {
              Alert.alert(t('itinerary.removeErrorTitle'), t('itinerary.backendRetry'));
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
      Alert.alert(t('itinerary.updateErrorTitle'), t('itinerary.backendRetry'));
    }
  };

  const updateStopStatus = async (
    day: ItineraryDay,
    stop: ItineraryDay['stops'][number],
    status: 'PLANNED' | 'ARRIVED' | 'DONE' | 'SKIPPED',
  ) => {
    if (tripId === 'preview-trip') {
      closeStopActions();
      return;
    }
    try {
      const updatedDay = await tripApi.updateStopStatus(tripId, day.dayNumber, stop.id, status);
      updateDay(updatedDay);
    } catch {
      Alert.alert(t('itinerary.updateErrorTitle'), t('itinerary.backendRetry'));
    } finally {
      closeStopActions();
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
      Alert.alert(t('itinerary.reorderErrorTitle'), t('itinerary.backendRetry'));
    }
  };

  const moveStop = async (day: ItineraryDay, stop: ItineraryDay['stops'][number], targetDayNumber: number) => {
    if (!ensureLivePlan()) return;
    closeStopActions();
    try {
      setItinerary(await tripApi.moveStop(tripId, day.dayNumber, stop.id, targetDayNumber));
    } catch {
      Alert.alert(t('itinerary.moveErrorTitle'), t('itinerary.backendRetry'));
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
            <Text style={styles.eyebrow}>{t('itinerary.aiItinerary')}</Text>
            <Text style={styles.title}>{destination}</Text>
          </View>
          <View style={styles.headerBadge}>
            <Ionicons name="sparkles-outline" size={14} color={colors.teal} />
            <Text style={styles.headerBadgeText}>{t('itinerary.optimized')}</Text>
          </View>
        </View>

        <View style={styles.overviewCard}>
          <View style={styles.overviewTop}>
            <View>
              <Text style={styles.overviewLabel}>{t('itinerary.tripRhythm')}</Text>
              <Text style={styles.overviewTitle}>{t('itinerary.daysPlanned', { count: visibleDays.length })}</Text>
            </View>
            <Ionicons name="map-outline" size={24} color={colors.teal} />
          </View>
          <View style={styles.overviewStats}>
            <OverviewStat label={t('itinerary.days')} value={`${visibleDays.length}`} styles={styles} />
            <OverviewStat label={t('itinerary.stops')} value={`${totalStops}`} styles={styles} />
            <OverviewStat label={t('itinerary.walk')} value={`${totalWalk.toFixed(1)} km`} styles={styles} />
          </View>
        </View>

        {loading ? <InlineLoading label={t('itinerary.loading')} /> : null}
        {error ? (
          <InlineError
            title={t('itinerary.refreshErrorTitle')}
            description={t('itinerary.refreshErrorDescription')}
            onRetry={loadItinerary}
          />
        ) : null}

        {weatherSignal?.available && weatherTargetDay ? (
          <View style={styles.weatherCard}>
            <View style={styles.weatherTop}>
              <View style={styles.weatherIcon}>
                <Ionicons name="rainy-outline" size={20} color={colors.teal} />
              </View>
              <View style={styles.weatherCopy}>
                <Text style={styles.weatherKicker}>{t('itinerary.weatherAvailable')}</Text>
                <Text style={styles.weatherTitle}>{localizeDynamicText(weatherSignal.title, language)}</Text>
                <Text style={styles.weatherText}>{localizeDynamicText(weatherSignal.message, language)}</Text>
              </View>
            </View>

            {weatherPreviewOpen ? (
              <View style={styles.weatherPreview}>
                <WeatherPreviewMetric label={t('itinerary.before')} value={`${weatherSignal.beforeStopCount} ${t('itinerary.stops').toLowerCase()} - ${weatherSignal.beforeWalkKm.toFixed(1)} km`} styles={styles} />
                <WeatherPreviewMetric label={t('itinerary.after')} value={`${weatherSignal.afterStopCount} ${t('itinerary.stops').toLowerCase()} - ${weatherSignal.afterWalkKm.toFixed(1)} km`} styles={styles} />
                <View style={styles.weatherChangeList}>
                  {localizeDynamicList(weatherSignal.changes, language).slice(0, 3).map((change) => (
                    <Text key={change} style={styles.weatherChange}>{change}</Text>
                  ))}
                </View>
              </View>
            ) : null}

            {weatherApplied ? <Text style={styles.weatherSuccess}>{t('itinerary.weatherApplied', { day: weatherTargetDay.dayNumber })}</Text> : null}

            <View style={styles.weatherActions}>
              <TouchableOpacity style={styles.weatherSecondaryButton} activeOpacity={0.86} onPress={() => setWeatherPreviewOpen((open) => !open)}>
                <Text style={styles.weatherSecondaryText}>{weatherPreviewOpen ? t('itinerary.hidePreview') : t('itinerary.previewChanges')}</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.weatherPrimaryButton, weatherApplying && styles.weatherButtonDisabled]}
                activeOpacity={0.86}
                onPress={applyWeatherAdjustment}
                disabled={weatherApplying}
              >
                <Text style={styles.weatherPrimaryText}>{weatherApplying ? t('itinerary.applying') : t('itinerary.apply')}</Text>
              </TouchableOpacity>
            </View>
          </View>
        ) : null}

        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.dayRail}>
          {visibleDays.map((item) => (
            <TouchableOpacity
              key={`chip-${destination}-${item.dayNumber}`}
              style={styles.dayChip}
              activeOpacity={0.86}
              onPress={() => navigation.navigate('DayRouteDetail', { tripId, destination, day: item })}
            >
              <Text style={styles.dayChipLabel}>{t('itinerary.day', { day: item.dayNumber })}</Text>
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
                <Text style={styles.day}>{t('itinerary.dayDestination', { day: item.dayNumber, destination })}</Text>
                <Text style={styles.area}>{localizeDynamicText(item.title, language)}</Text>
              </View>
              <View style={styles.badge}>
                <Ionicons name="walk-outline" size={14} color={colors.teal} />
                <Text style={styles.badgeText}>{item.walkKm.toFixed(1)} km - {item.stopCount} {t('itinerary.stops').toLowerCase()}</Text>
              </View>
            </View>

            <Text style={styles.summary}>{localizeDynamicText(item.summary, language)}</Text>

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
              {timelineForDay(item).slice(0, 5).map((timelineItem, index, visibleTimeline) => (
                timelineItem.type === 'TRAVEL' ? (
                  <View key={timelineItem.id} style={styles.travelTimelineRow}>
                    <View style={styles.travelTimelineIcon}>
                      <Ionicons name="walk-outline" size={12} color={colors.teal} />
                    </View>
                    <View style={styles.stopLine} />
                    <View style={styles.stopCopy}>
                      <Text style={styles.travelTimelineText}>{timelineItem.startTime} - {timelineItem.endTime} · {localizeDynamicText(timelineItem.title, language)}</Text>
                    </View>
                  </View>
                ) : (
                  <View key={timelineItem.id} style={styles.stopRow}>
                    <View style={styles.stopNumber}>
                      <Text style={styles.stopNumberText}>{stopNumberForTimelineItem(visibleTimeline, index)}</Text>
                    </View>
                    <View style={styles.stopLine} />
                    <View style={styles.stopCopy}>
                      <Text style={styles.stopText}>{localizeDynamicText(timelineItem.title, language)}</Text>
                      <Text style={styles.stopTime}>{timelineItem.startTime} - {timelineItem.endTime}</Text>
                    </View>
                    <TouchableOpacity
                      style={styles.stopMenuButton}
                      activeOpacity={0.78}
                      onPress={() => {
                        const stop = item.stops.find((candidate) => candidate.id === timelineItem.id);
                        if (stop) openStopActions(item, stop);
                      }}
                    >
                      <Ionicons name="ellipsis-horizontal" size={18} color={colors.teal} />
                    </TouchableOpacity>
                  </View>
                )
              ))}
              {timelineForDay(item).length > 5 ? <Text style={styles.moreStops}>{t('itinerary.moreItems', { count: timelineForDay(item).length - 5 })}</Text> : null}
            </View>
            <View style={styles.openRouteRow}>
              <Text style={styles.openRouteText}>{t('itinerary.openRouteMap')}</Text>
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
                      {t('itinerary.day', { day: selectedStop.day.dayNumber })} - {selectedStop.stop.timeWindow}
                      {selectedStop.stop.optional ? ` - ${t('itinerary.optionalStop')}` : ''}
                      {selectedStop.stop.status && selectedStop.stop.status !== 'PLANNED' ? ` - ${statusLabel(selectedStop.stop.status, t)}` : ''}
                    </Text>
                  </View>
                  <TouchableOpacity style={styles.actionClose} activeOpacity={0.75} onPress={closeStopActions}>
                    <Ionicons name="close" size={18} color={colors.slate} />
                  </TouchableOpacity>
                </View>

                <View style={styles.actionList}>
                  <StopActionRow
                    icon="navigate-outline"
                    title={t('itinerary.imHere')}
                    description={t('itinerary.imHereDescription')}
                    disabled={selectedStop.stop.status === 'ARRIVED' || selectedStop.stop.status === 'DONE' || selectedStop.stop.status === 'SKIPPED'}
                    onPress={() => updateStopStatus(selectedStop.day, selectedStop.stop, 'ARRIVED')}
                    styles={styles}
                  />
                  <StopActionRow
                    icon="checkmark-circle-outline"
                    title={t('itinerary.done')}
                    description={t('itinerary.doneDescription')}
                    disabled={selectedStop.stop.status === 'DONE'}
                    onPress={() => updateStopStatus(selectedStop.day, selectedStop.stop, 'DONE')}
                    styles={styles}
                  />
                  <StopActionRow
                    icon="play-skip-forward-outline"
                    title={t('itinerary.skip')}
                    description={t('itinerary.skipDescription')}
                    disabled={selectedStop.stop.status === 'SKIPPED'}
                    onPress={() => updateStopStatus(selectedStop.day, selectedStop.stop, 'SKIPPED')}
                    styles={styles}
                  />
                  <StopActionRow
                    icon={selectedStop.stop.optional ? 'star-outline' : 'bookmark-outline'}
                    title={selectedStop.stop.optional ? t('itinerary.keepMain') : t('itinerary.makeOptional')}
                    description={selectedStop.stop.optional ? t('itinerary.keepMainDescription') : t('itinerary.makeOptionalDescription')}
                    onPress={() => toggleOptional(selectedStop.day, selectedStop.stop)}
                    styles={styles}
                  />
                  <StopActionRow
                    icon="arrow-up-outline"
                    title={t('itinerary.moveEarlier')}
                    description={t('itinerary.moveEarlierDescription')}
                    disabled={selectedStop.stop.order <= 1}
                    onPress={() => reorderStop(selectedStop.day, selectedStop.stop, -1)}
                    styles={styles}
                  />
                  <StopActionRow
                    icon="arrow-down-outline"
                    title={t('itinerary.moveLater')}
                    description={t('itinerary.moveLaterDescription')}
                    disabled={selectedStop.stop.order >= selectedStop.day.stops.length}
                    onPress={() => reorderStop(selectedStop.day, selectedStop.stop, 1)}
                    styles={styles}
                  />
                  {targetDay ? (
                    <StopActionRow
                      icon="calendar-outline"
                      title={t('itinerary.moveToDay', { day: targetDay.dayNumber })}
                      description={t('itinerary.moveToDayDescription')}
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
                    <Text style={styles.removeTitle}>{t('itinerary.removeFromPlan')}</Text>
                    <Text style={styles.removeDescription}>{t('itinerary.deleteFromDay', { day: selectedStop.day.dayNumber })}</Text>
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
type Translate = ReturnType<typeof useTranslation>;

function previewDays(destination: string, dayCount: number): ItineraryDay[] {
  const base = cityCoordinates(destination);
  const count = Math.max(1, Math.min(dayCount || 1, 5));
  const themes = [
    { title: 'City Core & Coffee Loop', categories: ['WALKING', 'COFFEE', 'CULTURE', 'FOOD'] },
    { title: 'Culture Morning, Local Dinner', categories: ['CULTURE', 'WALKING', 'COFFEE', 'FOOD'] },
    { title: 'Food Streets & Local Corners', categories: ['FOOD', 'WALKING', 'CULTURE', 'COFFEE'] },
    { title: 'Neighborhood Walk & Dinner', categories: ['WALKING', 'CULTURE', 'COFFEE', 'FOOD'] },
    { title: 'Slow Design & Market Route', categories: ['CULTURE', 'COFFEE', 'WALKING', 'FOOD'] },
  ];

  return Array.from({ length: count }, (_, index) => {
    const dayNumber = index + 1;
    const theme = themes[index % themes.length];
    const stops = theme.categories.map((category, stopIndex) => ({
      id: `preview-${destination}-${dayNumber}-${stopIndex + 1}`.toLowerCase().replace(/[^a-z0-9]+/g, '-'),
      order: stopIndex + 1,
      title: previewStopTitle(destination, category, stopIndex),
      category,
      timeWindow: ['Morning', 'Late morning', 'Afternoon', 'Evening'][stopIndex] ?? 'Flexible',
      note: `${destination} preview stop shaped around your selected city until the live itinerary loads.`,
      optional: false,
      latitude: base.latitude + (dayNumber * 0.004) + (stopIndex * 0.003),
      longitude: base.longitude + (dayNumber * 0.004) - (stopIndex * 0.003),
    }));

    return {
      dayNumber,
      title: theme.title,
      summary: `A city-aware preview for ${destination} with local breaks and compact walking until Journy loads the live plan.`,
      walkKm: 4.4 + (index % 3) * 0.6,
      stopCount: stops.length,
      stops,
    };
  });
}

function previewStopTitle(destination: string, category: string, index: number) {
  if (category === 'COFFEE') return `${destination} coffee pause`;
  if (category === 'FOOD') return `${destination} local food stop`;
  if (category === 'CULTURE') return `${destination} culture window`;
  return index === 0 ? `${destination} first walk` : `${destination} neighborhood walk`;
}

function OverviewStat({ label, value, styles }: { label: string; value: string; styles: ItineraryStyles }) {
  return (
    <View style={styles.overviewStat}>
      <Text style={styles.overviewStatValue}>{value}</Text>
      <Text style={styles.overviewStatLabel}>{label}</Text>
    </View>
  );
}

function WeatherPreviewMetric({ label, value, styles }: { label: string; value: string; styles: ItineraryStyles }) {
  return (
    <View style={styles.weatherPreviewMetric}>
      <Text style={styles.weatherPreviewLabel}>{label}</Text>
      <Text style={styles.weatherPreviewValue}>{value}</Text>
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

function timelineForDay(day: ItineraryDay): ItineraryTimelineItem[] {
  if (day.timeline?.length) {
    return day.timeline;
  }
  return day.stops.map((stop) => ({
    id: stop.id,
    type: 'STOP',
    title: stop.title,
    startTime: stop.timeWindow,
    endTime: stop.timeWindow,
    durationMinutes: 0,
    category: stop.category,
    note: stop.note,
    constraintStatus: 'OK',
  }));
}

function stopNumberForTimelineItem(items: ItineraryTimelineItem[], index: number) {
  return items.slice(0, index + 1).filter((item) => item.type === 'STOP').length;
}

function statusLabel(status: NonNullable<ItineraryDay['stops'][number]['status']>, t: Translate) {
  if (status === 'ARRIVED') return t('itinerary.arrived');
  if (status === 'DONE') return t('itinerary.completed');
  if (status === 'SKIPPED') return t('itinerary.skipped');
  return t('itinerary.planned');
}

function hasWeatherSensitiveStop(day: ItineraryDay) {
  return day.stops.some((stop) => isWeatherSensitiveCategory(stop.category));
}

function isWeatherSensitiveCategory(category: string) {
  const normalized = category.toUpperCase();
  return normalized.includes('WALK') || normalized.includes('FREE') || normalized.includes('OUTDOOR');
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
  weatherCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.md,
    padding: spacing.md,
  },
  weatherTop: { alignItems: 'flex-start', flexDirection: 'row', gap: spacing.sm },
  weatherIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    height: 42,
    justifyContent: 'center',
    width: 42,
  },
  weatherCopy: { flex: 1, minWidth: 0 },
  weatherKicker: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  weatherTitle: { color: colors.midnight, fontSize: typography.small, fontWeight: '900', lineHeight: 20, marginTop: 3 },
  weatherText: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', lineHeight: 17, marginTop: spacing.xs },
  weatherPreview: {
    backgroundColor: colors.surfaceWarm,
    borderRadius: radius.lg,
    marginTop: spacing.md,
    padding: spacing.sm,
  },
  weatherPreviewMetric: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    minHeight: 28,
  },
  weatherPreviewLabel: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  weatherPreviewValue: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900' },
  weatherChangeList: { borderColor: colors.mist, borderTopWidth: 1, gap: 5, marginTop: spacing.xs, paddingTop: spacing.xs },
  weatherChange: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', lineHeight: 16 },
  weatherSuccess: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', marginTop: spacing.sm },
  weatherActions: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.md },
  weatherSecondaryButton: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    flex: 1,
    justifyContent: 'center',
    minHeight: 44,
  },
  weatherSecondaryText: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900' },
  weatherPrimaryButton: {
    alignItems: 'center',
    backgroundColor: isDark ? colors.teal : colors.midnight,
    borderRadius: radius.pill,
    flex: 1,
    justifyContent: 'center',
    minHeight: 44,
  },
  weatherButtonDisabled: { opacity: 0.55 },
  weatherPrimaryText: { color: isDark ? colors.ivory : colors.surface, fontSize: typography.tiny, fontWeight: '900' },
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
    backgroundColor: isDark ? colors.teal : colors.midnight,
    borderRadius: radius.pill,
    height: 26,
    justifyContent: 'center',
    width: 26,
  },
  routeDotText: { color: isDark ? colors.ivory : colors.surface, fontSize: typography.tiny, fontWeight: '900' },
  routeLine: { backgroundColor: colors.mist, flex: 1, height: 2, marginHorizontal: spacing.xs },
  timeline: { marginTop: spacing.md },
  stopRow: { alignItems: 'center', flexDirection: 'row', minHeight: 38 },
  travelTimelineRow: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 30,
    paddingLeft: 2,
  },
  travelTimelineIcon: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderRadius: radius.pill,
    height: 24,
    justifyContent: 'center',
    width: 24,
  },
  travelTimelineText: {
    color: colors.slate,
    fontSize: typography.tiny,
    fontWeight: '900',
  },
  stopNumber: {
    alignItems: 'center',
    backgroundColor: isDark ? colors.teal : colors.midnight,
    borderRadius: radius.pill,
    height: 28,
    justifyContent: 'center',
    width: 28,
  },
  stopNumberText: { color: isDark ? colors.ivory : colors.surface, fontSize: typography.tiny, fontWeight: '900' },
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
    shadowOpacity: isDark ? 0.34 : 0.16,
    shadowRadius: isDark ? 18 : 24,
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
