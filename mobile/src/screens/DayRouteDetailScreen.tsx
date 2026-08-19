import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Linking, Platform, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import MapView, { Marker, Polyline, type LatLng } from 'react-native-maps';

import type { RootStackParamList } from '../navigation/AppNavigator';
import { aiApi, tripApi } from '../api/journyApi';
import type { AiItinerarySuggestionResponse, ItineraryDay, ItineraryStop, ItineraryTimelineItem, PlaceResponse } from '../api/types';
import { useLanguage, useTranslation } from '../i18n/LanguageContext';
import { useAppTheme } from '../theme/ThemeContext';
import { placeImage } from '../utils/destinationVisuals';
import { localizeDynamicText } from '../utils/localizedDynamicText';

type Props = NativeStackScreenProps<RootStackParamList, 'DayRouteDetail'>;
type ActionKey = 'lighter' | 'food' | 'replace';
type MapMode = 'Route' | 'Places';

export default function DayRouteDetailScreen({ navigation, route }: Props) {
  const { isDark, theme } = useAppTheme();
  const { language } = useLanguage();
  const t = useTranslation();
  const styles = useMemo(() => createStyles(theme, isDark), [theme, isDark]);
  const { colors } = theme;
  const { tripId, destination, day } = route.params;
  const [currentDay, setCurrentDay] = useState<ItineraryDay>(day);
  const [selectedAction, setSelectedAction] = useState<ActionKey>('lighter');
  const [suggestion, setSuggestion] = useState<AiItinerarySuggestionResponse | null>(null);
  const [suggestionLoading, setSuggestionLoading] = useState(false);
  const [suggestionError, setSuggestionError] = useState(false);
  const [applyLoading, setApplyLoading] = useState(false);
  const [applySuccess, setApplySuccess] = useState(false);
  const [mapMode, setMapMode] = useState<MapMode>('Route');
  const [selectedStop, setSelectedStop] = useState<ItineraryStop | null>(currentDay.stops[0] ?? null);

  const displayTitle = localizeDynamicText(cleanRepeatedPrefix(currentDay.title, 'Lighter'), language);
  const displaySummary = localizeDynamicText(compactRepeatedSentences(currentDay.summary), language);
  const paceLabel = currentDay.walkKm <= 4.5 ? t('setup.relaxed') : currentDay.walkKm >= 7 ? t('setup.full') : t('setup.balanced');
  const focusLabel = currentDay.stops.some((stop) => stop.category === 'FOOD' || stop.category === 'COFFEE')
    ? t('dayRoute.foodBreaksIncluded')
    : t('dayRoute.cultureFirstFlow');
  const mapRef = useRef<MapView | null>(null);
  const timelineItems = useMemo(() => timelineForDay(currentDay), [currentDay]);
  const routeCoordinates = useMemo(() => stopsToCoordinates(currentDay.stops), [currentDay.stops]);
  const mapRegion = useMemo(() => regionForCoordinates(routeCoordinates), [routeCoordinates]);
  const mappedWalkKm = useMemo(() => estimateRouteDistanceKm(currentDay.stops), [currentDay.stops]);
  const fallbackActionMessage = {
    lighter: t('dayRoute.fallbackLighter'),
    food: t('dayRoute.fallbackFood'),
    replace: t('dayRoute.fallbackReplace'),
  }[selectedAction];

  const loadSuggestion = useCallback(async (action: ActionKey) => {
    if (tripId === 'preview-trip') {
      return;
    }
    setSuggestionLoading(true);
    setSuggestionError(false);
    try {
      const response = await aiApi.itinerarySuggestion(tripId, currentDay.dayNumber, action, language);
      setSuggestion(response);
    } catch {
      setSuggestionError(true);
      setSuggestion(null);
    } finally {
      setSuggestionLoading(false);
    }
  }, [currentDay.dayNumber, language, tripId]);

  useEffect(() => {
    loadSuggestion(selectedAction);
  }, [loadSuggestion, selectedAction]);

  const chooseAction = (action: ActionKey) => {
    setSelectedAction(action);
    setApplySuccess(false);
  };

  const applySuggestion = async () => {
    if (tripId === 'preview-trip' || applyLoading) {
      return;
    }
    setApplyLoading(true);
    setApplySuccess(false);
    try {
      const updatedDay = await aiApi.applyItinerarySuggestion(tripId, currentDay.dayNumber, selectedAction, language);
      setCurrentDay(updatedDay);
      setApplySuccess(true);
      await loadSuggestion(selectedAction);
    } catch {
      setSuggestionError(true);
    } finally {
      setApplyLoading(false);
    }
  };

  const openStop = (stop: ItineraryStop) => {
    navigation.navigate('PlaceDetail', { place: toPlaceDetail(stop, destination) });
  };

  const startRoute = () => {
    openRouteInMaps(routeCoordinates);
  };

  const updateStopStatus = async (stop: ItineraryStop, status: 'PLANNED' | 'ARRIVED' | 'DONE' | 'SKIPPED') => {
    if (tripId === 'preview-trip') {
      return;
    }
    try {
      const updatedDay = await tripApi.updateStopStatus(tripId, currentDay.dayNumber, stop.id, status);
      setCurrentDay(updatedDay);
      const updatedStop = updatedDay.stops.find((item) => item.id === stop.id);
      if (updatedStop) {
        setSelectedStop(updatedStop);
      }
    } catch {
      setSuggestionError(true);
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
            onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('MainTabs'))}
          >
            <Ionicons name="arrow-back" size={21} color={colors.midnight} />
          </TouchableOpacity>
          <TouchableOpacity style={styles.startButton} activeOpacity={0.88} onPress={startRoute}>
            <Ionicons name="navigate-outline" size={16} color={colors.surface} />
            <Text style={styles.startButtonText}>{t('dayRoute.startRoute')}</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.hero}>
          <Text style={styles.eyebrow}>{t('itinerary.dayDestination', { day: currentDay.dayNumber, destination })}</Text>
          <Text style={styles.title}>{displayTitle}</Text>
          <Text style={styles.subtitle}>{displaySummary}</Text>
          <View style={styles.heroMetaRow}>
            <HeroMetric icon="walk-outline" value={`${currentDay.walkKm.toFixed(1)} km`} label={t('dayRoute.walking')} colors={colors} styles={styles} />
            <HeroMetric icon="location-outline" value={`${currentDay.stopCount}`} label={t('dayRoute.stops')} colors={colors} styles={styles} />
            <HeroMetric icon="speedometer-outline" value={paceLabel} label={t('dayRoute.pace')} colors={colors} styles={styles} />
          </View>
        </View>

        <View style={styles.routeCard}>
          <View style={styles.mapHeader}>
            <View>
              <Text style={styles.mapTitle}>{t('dayRoute.routePreview')}</Text>
              <Text style={styles.mapSubtitle}>{focusLabel}</Text>
            </View>
            <View style={styles.mapModeToggle}>
              {(['Route', 'Places'] as MapMode[]).map((mode) => (
                <TouchableOpacity
                  key={mode}
                  style={[styles.mapModeButton, mapMode === mode && styles.mapModeButtonActive]}
                  activeOpacity={0.86}
                  onPress={() => setMapMode(mode)}
                >
                  <Text style={[styles.mapModeText, mapMode === mode && styles.mapModeTextActive]}>{mode === 'Route' ? t('dayRoute.route') : t('dayRoute.places')}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>

          <View style={styles.mapCanvas}>
            <MapView
              ref={mapRef}
              style={styles.nativeMap}
              initialRegion={mapRegion}
              onMapReady={() => {
                if (routeCoordinates.length > 1) {
                  mapRef.current?.fitToCoordinates(routeCoordinates, {
                    edgePadding: { top: 56, right: 46, bottom: 58, left: 46 },
                    animated: true,
                  });
                }
              }}
              showsCompass={false}
              showsPointsOfInterest
              showsUserLocation={false}
              toolbarEnabled={false}
            >
              {mapMode === 'Route' && routeCoordinates.length > 1 ? (
                <Polyline
                  coordinates={routeCoordinates}
                  strokeColor={colors.teal}
                  strokeWidth={5}
                  lineCap="round"
                  lineJoin="round"
                />
              ) : null}
              {currentDay.stops.map((stop, index) => {
                if (!Number.isFinite(stop.latitude) || !Number.isFinite(stop.longitude)) {
                  return null;
                }
                const isLast = index === currentDay.stops.length - 1;
                return (
                  <Marker
                    key={`${stop.order}-${stop.title}`}
                    coordinate={{ latitude: stop.latitude, longitude: stop.longitude }}
                    title={`${index + 1}. ${stop.title}`}
                    description={stop.timeWindow}
                    onPress={() => setSelectedStop(stop)}
                    onCalloutPress={() => openStop(stop)}
                  >
                    <View style={[
                      styles.mapMarker,
                      index === 0 && styles.mapMarkerStart,
                      isLast && styles.mapMarkerEnd,
                      selectedStop?.id === stop.id && styles.mapMarkerSelected,
                    ]}>
                      <Text style={[styles.mapMarkerText, isLast && styles.mapMarkerTextEnd]}>{index + 1}</Text>
                    </View>
                  </Marker>
                );
              })}
            </MapView>
            <View style={styles.routeDistanceBadge}>
              <Ionicons name="walk-outline" size={13} color={colors.teal} />
              <Text style={styles.routeDistanceText}>{t('dayRoute.walkingRoute')} · {mappedWalkKm.toFixed(1)} km · {estimateRouteMinutes(mappedWalkKm)} min</Text>
            </View>
            <TouchableOpacity style={styles.openMapsPill} activeOpacity={0.86} onPress={startRoute}>
              <Ionicons name="open-outline" size={13} color={colors.teal} />
              <Text style={styles.openMapsText}>{t('dayRoute.openMaps')}</Text>
            </TouchableOpacity>
          </View>
          {selectedStop ? (
            <View style={styles.markerCard}>
              <View style={styles.markerCardTop}>
                <View style={styles.markerCardIcon}>
                  <Ionicons name={iconForStop(selectedStop.category)} size={18} color={colors.teal} />
                </View>
                <View style={styles.markerCardCopy}>
                  <Text style={styles.markerCardTitle}>{selectedStop.title}</Text>
              <Text style={styles.markerCardMeta}>{selectedStop.timeWindow} · {formatCategory(selectedStop.category, t)}</Text>
                </View>
                <TouchableOpacity style={styles.markerViewButton} activeOpacity={0.86} onPress={() => openStop(selectedStop)}>
                  <Text style={styles.markerViewText}>{t('dayRoute.view')}</Text>
                </TouchableOpacity>
              </View>
              <View style={styles.markerActions}>
                <TouchableOpacity style={styles.markerAction} activeOpacity={0.86} onPress={() => updateStopStatus(selectedStop, 'ARRIVED')}>
                  <Ionicons name="navigate-outline" size={15} color={colors.teal} />
                  <Text style={styles.markerActionText}>{t('itinerary.imHere')}</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.markerAction} activeOpacity={0.86} onPress={() => updateStopStatus(selectedStop, 'DONE')}>
                  <Ionicons name="checkmark-circle-outline" size={15} color={colors.teal} />
                  <Text style={styles.markerActionText}>{t('itinerary.done')}</Text>
                </TouchableOpacity>
              </View>
              <View style={styles.markerActions}>
                <TouchableOpacity style={styles.markerAction} activeOpacity={0.86} onPress={startRoute}>
                  <Ionicons name="navigate-outline" size={15} color={colors.teal} />
                  <Text style={styles.markerActionText}>{t('dayRoute.directions')}</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.markerAction} activeOpacity={0.86} onPress={() => chooseAction('replace')}>
                  <Ionicons name="swap-horizontal-outline" size={15} color={colors.teal} />
                  <Text style={styles.markerActionText}>{t('dayRoute.replace')}</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.markerAction} activeOpacity={0.86} onPress={() => chooseAction('lighter')}>
                  <Ionicons name="remove-circle-outline" size={15} color={colors.teal} />
                  <Text style={styles.markerActionText}>{t('dayRoute.remove')}</Text>
                </TouchableOpacity>
              </View>
            </View>
          ) : null}
        </View>

        <View style={styles.actionRow}>
          <ActionChip label={t('dayRoute.makeLighter')} icon="leaf-outline" active={selectedAction === 'lighter'} onPress={() => chooseAction('lighter')} styles={styles} />
          <ActionChip label={t('dayRoute.addFoodStop')} icon="restaurant-outline" active={selectedAction === 'food'} onPress={() => chooseAction('food')} styles={styles} />
          <ActionChip label={t('dayRoute.replaceStop')} icon="swap-horizontal-outline" active={selectedAction === 'replace'} onPress={() => chooseAction('replace')} styles={styles} />
        </View>
        <View style={styles.aiNote}>
          <View style={styles.aiNoteHeader}>
            <View style={styles.aiIconWrap}>
              <Ionicons name="sparkles-outline" size={18} color={colors.teal} />
            </View>
            <View style={styles.aiNoteCopy}>
              <Text style={styles.aiNoteLabel}>{t('dayRoute.previewChange')}</Text>
              <Text style={styles.aiNoteTitle}>{suggestion?.title ?? t('dayRoute.suggestedAdjustment')}</Text>
            </View>
          </View>
          <Text style={styles.aiNoteText}>
            {suggestionLoading ? t('dayRoute.checkingContext') : suggestion?.message ?? fallbackActionMessage}
          </Text>

          <View style={styles.previewGrid}>
            <PreviewRow icon="git-branch-outline" label={t('dayRoute.change')} value={suggestion?.suggestedAction ?? previewFallbackAction(selectedAction, t)} styles={styles} />
            <PreviewRow icon="location-outline" label={t('dayRoute.affectedStop')} value={previewAffectedStop(suggestion, t)} styles={styles} />
            <PreviewRow icon="walk-outline" label={t('dayRoute.walkImpact')} value={previewWalkImpact(selectedAction, suggestion?.minutesSaved, t)} styles={styles} />
          </View>

          {suggestion?.routeSummary ? <Text style={styles.routeSummary}>{suggestion.routeSummary}</Text> : null}
          {applySuccess ? <Text style={styles.applySuccess}>{t('dayRoute.updated')}</Text> : null}
          {suggestionError ? <Text style={styles.suggestionError}>{t('dayRoute.suggestionError')}</Text> : null}

          <View style={styles.previewActions}>
            <TouchableOpacity
              style={styles.previewSecondaryButton}
              activeOpacity={0.86}
              onPress={() => loadSuggestion(selectedAction)}
              disabled={suggestionLoading || tripId === 'preview-trip'}
            >
              <Text style={styles.previewSecondaryText}>{suggestionLoading ? t('dayRoute.checking') : t('dayRoute.refresh')}</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.applyButton, applyLoading && styles.applyButtonDisabled]} activeOpacity={0.86} onPress={applySuggestion}>
              <Text style={styles.applyButtonText}>{applyLoading ? t('dayRoute.applying') : t('dayRoute.applyChanges')}</Text>
            </TouchableOpacity>
          </View>
        </View>

        <Text style={styles.sectionTitle}>{t('dayRoute.dailySchedule')}</Text>
        <View style={styles.stopList}>
          {timelineItems.map((item, index) => (
            item.type === 'TRAVEL' ? (
              <View key={item.id} style={styles.travelRow}>
                <View style={styles.stopRail}>
                  <View style={styles.travelIcon}>
                    <Ionicons name="walk-outline" size={14} color={colors.teal} />
                  </View>
                  {index !== timelineItems.length - 1 ? <View style={styles.verticalLine} /> : null}
                </View>
                <View style={styles.stopCopy}>
                  <Text style={styles.travelTime}>{item.startTime} - {item.endTime}</Text>
                  <Text style={styles.travelTitle}>{localizeDynamicText(item.title, language)}{item.distanceKm ? ` · ${item.distanceKm.toFixed(1)} km` : ''}</Text>
                </View>
              </View>
            ) : (() => {
              const stop = currentDay.stops.find((candidate) => candidate.id === item.id);
              const status = stop?.status ?? 'PLANNED';
              return (
                <TouchableOpacity
                  key={item.id}
                  style={styles.stopRow}
                  activeOpacity={0.86}
                  onPress={() => {
                    if (stop) openStop(stop);
                  }}
                >
                  <View style={styles.stopRail}>
                    <View style={[styles.stopNumber, status === 'DONE' && styles.stopNumberDone, status === 'SKIPPED' && styles.stopNumberSkipped]}>
                      <Text style={[styles.stopNumberText, (status === 'DONE' || status === 'SKIPPED') && styles.stopNumberTextDone]}>{status === 'DONE' ? '✓' : stopNumberForTimelineItem(timelineItems, index)}</Text>
                    </View>
                    {index !== timelineItems.length - 1 ? <View style={styles.verticalLine} /> : null}
                  </View>
                  <View style={styles.stopCopy}>
                    <View style={styles.stopTopLine}>
                      <Text style={styles.stopWindow}>{item.startTime} - {item.endTime}</Text>
                      <Text style={styles.stopCategory}>{status === 'PLANNED' ? formatCategory(item.category ?? 'STOP', t) : statusLabel(status, t)}</Text>
                    </View>
                    <Text style={[styles.stopTitle, status === 'SKIPPED' && styles.stopTitleMuted]}>{localizeDynamicText(item.title, language)}</Text>
                    <Text style={styles.stopNote}>{localizeDynamicText(item.note, language)}</Text>
                    {item.constraintWarning ? <Text style={styles.constraintWarning}>{localizeDynamicText(item.constraintWarning, language)}</Text> : null}
                    {stop ? (
                      <View style={styles.progressActions}>
                        <TouchableOpacity style={styles.progressChip} activeOpacity={0.84} onPress={() => updateStopStatus(stop, 'ARRIVED')}>
                          <Text style={styles.progressChipText}>{t('itinerary.imHere')}</Text>
                        </TouchableOpacity>
                        <TouchableOpacity style={[styles.progressChip, styles.progressChipPrimary]} activeOpacity={0.84} onPress={() => updateStopStatus(stop, 'DONE')}>
                          <Text style={styles.progressChipPrimaryText}>{t('itinerary.done')}</Text>
                        </TouchableOpacity>
                        <TouchableOpacity style={styles.progressChip} activeOpacity={0.84} onPress={() => updateStopStatus(stop, 'SKIPPED')}>
                          <Text style={styles.progressChipText}>{t('itinerary.skip')}</Text>
                        </TouchableOpacity>
                      </View>
                    ) : null}
                  </View>
                  <Ionicons name="chevron-forward" size={18} color={colors.softMuted} />
                </TouchableOpacity>
              );
            })()
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
      <Text style={styles.heroMetricValue} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.72}>
        {value}
      </Text>
      <Text style={styles.heroMetricLabel}>{label}</Text>
    </View>
  );
}

function cleanRepeatedPrefix(value: string, prefix: string) {
  const pattern = new RegExp(`^(${prefix}\\s+)+`, 'i');
  const match = value.match(pattern);
  if (!match) return value;
  return `${prefix} ${value.replace(pattern, '')}`;
}

function compactRepeatedSentences(value: string) {
  const seen = new Set<string>();
  return value
    .split(/(?<=\.)\s+/)
    .map((sentence) => sentence.trim())
    .filter(Boolean)
    .filter((sentence) => {
      const key = sentence.toLowerCase();
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    })
    .join(' ');
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

function PreviewRow({
  icon,
  label,
  value,
  styles,
}: {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  label: string;
  value: string;
  styles: DetailStyles;
}) {
  return (
    <View style={styles.previewRow}>
      <View style={styles.previewIcon}>
        <Ionicons name={icon} size={15} style={styles.previewIconGlyph} />
      </View>
      <View style={styles.previewCopy}>
        <Text style={styles.previewLabel}>{label}</Text>
        <Text style={styles.previewValue}>{value}</Text>
      </View>
    </View>
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
    imageUrl: placeImage(destination, category, stop.title),
    address: `${destination} city center`,
    latitude: stop.latitude,
    longitude: stop.longitude,
    openingHours: stop.timeWindow,
    estimatedVisitMinutes: category === 'FOOD' || category === 'COFFEE' ? 45 : 60,
    tags: `${formatCategory(category)}, Walkable`,
  };
}

type Translate = ReturnType<typeof useTranslation>;

function statusLabel(status: NonNullable<ItineraryStop['status']>, t: Translate) {
  if (status === 'ARRIVED') return t('itinerary.arrived');
  if (status === 'DONE') return t('itinerary.completed');
  if (status === 'SKIPPED') return t('itinerary.skipped');
  return t('itinerary.planned');
}

function previewFallbackAction(action: ActionKey, t: Translate) {
  if (action === 'food') return t('dayRoute.addNearbyFood');
  if (action === 'replace') return t('dayRoute.swapStop');
  return t('dayRoute.lightenRoute');
}

function previewAffectedStop(suggestion: AiItinerarySuggestionResponse | null, t: Translate) {
  if (!suggestion?.stopsAffected?.length) {
    return t('dayRoute.routeRhythm');
  }
  return suggestion.stopsAffected.slice(0, 2).join(', ');
}

function previewWalkImpact(action: ActionKey, minutesSaved: number | null | undefined, t: Translate) {
  if (typeof minutesSaved === 'number' && minutesSaved > 0) {
    return t('dayRoute.minSaved', { minutes: minutesSaved });
  }
  if (action === 'food') {
    return t('dayRoute.smallDetour');
  }
  if (action === 'replace') {
    return t('dayRoute.similarDistance');
  }
  return t('dayRoute.lowerEffort');
}

function estimateRouteMinutes(km: number) {
  return Math.max(8, Math.round(km * 13));
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

function iconForStop(category: string): React.ComponentProps<typeof Ionicons>['name'] {
  const value = category.toLowerCase();
  if (value.includes('coffee')) return 'cafe-outline';
  if (value.includes('food')) return 'restaurant-outline';
  if (value.includes('culture')) return 'color-palette-outline';
  if (value.includes('free')) return 'leaf-outline';
  return 'walk-outline';
}

function stopsToCoordinates(stops: ItineraryStop[]): LatLng[] {
  return stops
    .filter((stop) => Number.isFinite(stop.latitude) && Number.isFinite(stop.longitude))
    .map((stop) => ({ latitude: stop.latitude, longitude: stop.longitude }));
}

function regionForCoordinates(coordinates: LatLng[]) {
  if (!coordinates.length) {
    return {
      latitude: 52.3676,
      longitude: 4.9041,
      latitudeDelta: 0.06,
      longitudeDelta: 0.06,
    };
  }

  const latitudes = coordinates.map((coordinate) => coordinate.latitude);
  const longitudes = coordinates.map((coordinate) => coordinate.longitude);
  const minLat = Math.min(...latitudes);
  const maxLat = Math.max(...latitudes);
  const minLon = Math.min(...longitudes);
  const maxLon = Math.max(...longitudes);

  return {
    latitude: (minLat + maxLat) / 2,
    longitude: (minLon + maxLon) / 2,
    latitudeDelta: Math.max(0.018, (maxLat - minLat) * 1.8),
    longitudeDelta: Math.max(0.018, (maxLon - minLon) * 1.8),
  };
}

function openRouteInMaps(coordinates: LatLng[]) {
  if (!coordinates.length) {
    return;
  }

  const origin = coordinates[0];
  const destination = coordinates[coordinates.length - 1];
  const waypoints = coordinates.slice(1, -1);
  const url = Platform.select({
    ios: `http://maps.apple.com/?saddr=${origin.latitude},${origin.longitude}&daddr=${destination.latitude},${destination.longitude}&dirflg=w`,
    android: `https://www.google.com/maps/dir/?api=1&origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&travelmode=walking${waypoints.length ? `&waypoints=${waypoints.map((point) => `${point.latitude},${point.longitude}`).join('|')}` : ''}`,
    default: `https://www.google.com/maps/dir/?api=1&origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&travelmode=walking`,
  });

  if (url) {
    Linking.openURL(url).catch(() => undefined);
  }
}

type RoutePoint = {
  order: number;
  title: string;
  x: number;
  y: number;
  latitude: number;
  longitude: number;
  stop: ItineraryStop;
};

function buildRoutePoints(stops: ItineraryStop[]): RoutePoint[] {
  const validStops = stops.filter((stop) => Number.isFinite(stop.latitude) && Number.isFinite(stop.longitude));
  if (!validStops.length) {
    return stops.map((stop, index) => ({
      order: stop.order,
      title: stop.title,
      x: 18 + index * 16,
      y: index % 2 === 0 ? 36 : 58,
      latitude: stop.latitude,
      longitude: stop.longitude,
      stop,
    }));
  }

  const latitudes = validStops.map((stop) => stop.latitude);
  const longitudes = validStops.map((stop) => stop.longitude);
  const minLat = Math.min(...latitudes);
  const maxLat = Math.max(...latitudes);
  const minLon = Math.min(...longitudes);
  const maxLon = Math.max(...longitudes);
  const latRange = Math.max(0.0001, maxLat - minLat);
  const lonRange = Math.max(0.0001, maxLon - minLon);

  return stops.map((stop, index) => {
    const longitude = Number.isFinite(stop.longitude) ? stop.longitude : minLon + lonRange / 2;
    const latitude = Number.isFinite(stop.latitude) ? stop.latitude : minLat + latRange / 2;
    return {
      order: stop.order,
      title: stop.title,
      x: 14 + ((longitude - minLon) / lonRange) * 72,
      y: 18 + (1 - (latitude - minLat) / latRange) * 64 + (index % 2 === 0 ? 0 : 4),
      latitude,
      longitude,
      stop,
    };
  });
}

function routeSegment(from: RoutePoint, to: RoutePoint) {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const width = Math.max(10, Math.sqrt(dx * dx + dy * dy));
  return {
    left: from.x,
    top: from.y,
    width,
    angle: Math.atan2(dy, dx) * (180 / Math.PI),
  };
}

function estimateRouteDistanceKm(stops: ItineraryStop[]) {
  if (stops.length < 2) {
    return 0;
  }
  return stops.slice(1).reduce((sum, stop, index) => {
    const previous = stops[index];
    if (![previous.latitude, previous.longitude, stop.latitude, stop.longitude].every(Number.isFinite)) {
      return sum;
    }
    return sum + haversineKm(previous.latitude, previous.longitude, stop.latitude, stop.longitude);
  }, 0);
}

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number) {
  const radiusKm = 6371;
  const dLat = toRadians(lat2 - lat1);
  const dLon = toRadians(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2
    + Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) * Math.sin(dLon / 2) ** 2;
  return radiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function toRadians(value: number) {
  return value * (Math.PI / 180);
}

function formatCategory(category: string, t?: Translate) {
  const value = category.toLowerCase();
  if (!t) return value.replace(/_/g, ' ');
  if (value.includes('coffee')) return t('setup.coffee');
  if (value.includes('food')) return t('setup.localFood');
  if (value.includes('culture') || value.includes('museum')) return t('setup.museums');
  if (value.includes('walking')) return t('setup.walking');
  return value.replace(/_/g, ' ');
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
      justifyContent: 'center',
      minHeight: 88,
      minWidth: 0,
      paddingHorizontal: spacing.xs,
      paddingVertical: spacing.sm,
    },
    heroMetricValue: {
      color: colors.midnight,
      fontSize: typography.h3,
      fontWeight: '900',
      lineHeight: 26,
      marginTop: spacing.xs,
      maxWidth: '100%',
      textAlign: 'center',
    },
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
    mapModeToggle: {
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.pill,
      borderWidth: 1,
      flexDirection: 'row',
      padding: 3,
    },
    mapModeButton: {
      borderRadius: radius.pill,
      paddingHorizontal: spacing.sm,
      paddingVertical: 6,
    },
    mapModeButtonActive: {
      backgroundColor: colors.midnight,
    },
    mapModeText: {
      color: colors.slate,
      fontSize: 10,
      fontWeight: '900',
    },
    mapModeTextActive: {
      color: colors.surface,
    },
    mapCanvas: {
      backgroundColor: isDark ? colors.canvas : colors.fog,
      borderRadius: radius.lg,
      height: 250,
      overflow: 'hidden',
      position: 'relative',
    },
    nativeMap: {
      ...StyleSheet.absoluteFillObject,
    },
    mapMarker: {
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderColor: colors.teal,
      borderRadius: radius.pill,
      borderWidth: 3,
      height: 38,
      justifyContent: 'center',
      shadowColor: colors.shadow,
      shadowOffset: { width: 0, height: 8 },
      shadowOpacity: 0.18,
      shadowRadius: 12,
      width: 38,
    },
    mapMarkerStart: {
      borderColor: colors.midnight,
    },
    mapMarkerEnd: {
      backgroundColor: isDark ? colors.surface : colors.midnight,
      borderColor: isDark ? colors.teal : colors.midnight,
    },
    mapMarkerSelected: {
      transform: [{ scale: 1.12 }],
    },
    mapMarkerText: {
      color: colors.midnight,
      fontSize: typography.tiny,
      fontWeight: '900',
    },
    mapMarkerTextEnd: {
      color: isDark ? colors.midnight : colors.surface,
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
    routeSegment: {
      backgroundColor: colors.teal,
      borderRadius: radius.pill,
      height: 5,
      opacity: 0.9,
      position: 'absolute',
    },
    routeDistanceBadge: {
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.pill,
      borderWidth: 1,
      bottom: 12,
      flexDirection: 'row',
      gap: 5,
      left: 12,
      paddingHorizontal: spacing.sm,
      paddingVertical: spacing.xs,
      position: 'absolute',
    },
    openMapsPill: {
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.pill,
      borderWidth: 1,
      bottom: 12,
      flexDirection: 'row',
      gap: 5,
      paddingHorizontal: spacing.sm,
      paddingVertical: spacing.xs,
      position: 'absolute',
      right: 12,
    },
    openMapsText: {
      color: colors.midnight,
      fontSize: typography.tiny,
      fontWeight: '900',
    },
    markerCard: {
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.lg,
      borderWidth: 1,
      marginTop: spacing.sm,
      padding: spacing.sm,
    },
    markerCardTop: {
      alignItems: 'center',
      flexDirection: 'row',
      gap: spacing.sm,
    },
    markerCardIcon: {
      alignItems: 'center',
      backgroundColor: colors.fog,
      borderRadius: radius.md,
      height: 38,
      justifyContent: 'center',
      width: 38,
    },
    markerCardCopy: {
      flex: 1,
      minWidth: 0,
    },
    markerCardTitle: {
      color: colors.midnight,
      fontSize: typography.small,
      fontWeight: '900',
    },
    markerCardMeta: {
      color: colors.slate,
      fontSize: typography.tiny,
      fontWeight: '800',
      marginTop: 2,
      textTransform: 'capitalize',
    },
    markerViewButton: {
      alignItems: 'center',
      backgroundColor: colors.midnight,
      borderRadius: radius.pill,
      minHeight: 34,
      justifyContent: 'center',
      paddingHorizontal: spacing.md,
    },
    markerViewText: {
      color: colors.surface,
      fontSize: typography.tiny,
      fontWeight: '900',
    },
    markerActions: {
      flexDirection: 'row',
      gap: spacing.xs,
      marginTop: spacing.sm,
    },
    markerAction: {
      alignItems: 'center',
      backgroundColor: colors.surfaceWarm,
      borderRadius: radius.pill,
      flex: 1,
      flexDirection: 'row',
      gap: 4,
      justifyContent: 'center',
      minHeight: 38,
    },
    markerActionText: {
      color: colors.midnight,
      fontSize: 10,
      fontWeight: '900',
    },
    routeDistanceText: {
      color: colors.midnight,
      fontSize: typography.tiny,
      fontWeight: '900',
    },
    mapPin: {
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderColor: colors.teal,
      borderRadius: radius.pill,
      borderWidth: 3,
      height: 40,
      justifyContent: 'center',
      marginLeft: -20,
      marginTop: -20,
      position: 'absolute',
      width: 40,
    },
    mapPinStart: {
      borderColor: colors.midnight,
    },
    mapPinEnd: {
      backgroundColor: colors.midnight,
      borderColor: colors.midnight,
    },
    pinOne: { left: 48, top: 84 },
    pinTwo: { left: 144, top: 128 },
    pinThree: { left: 234, top: 96 },
    pinFour: { left: 194, top: 196 },
    pinFive: { left: 274, top: 178 },
    mapPinText: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900' },
    mapPinTextEnd: { color: colors.surface },
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
      backgroundColor: colors.lilac,
      borderColor: colors.mist,
      borderRadius: radius.xl,
      borderWidth: 1,
      marginTop: spacing.sm,
      padding: spacing.md,
    },
    aiNoteHeader: {
      alignItems: 'center',
      flexDirection: 'row',
      gap: spacing.sm,
    },
    aiIconWrap: {
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderRadius: radius.md,
      height: 42,
      justifyContent: 'center',
      width: 42,
    },
    aiNoteCopy: { flex: 1 },
    aiNoteLabel: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
    aiNoteTitle: { color: colors.midnight, fontSize: typography.body, fontWeight: '900', marginTop: 2 },
    aiNoteText: { color: colors.slate, fontSize: typography.small, fontWeight: '800', lineHeight: 19, marginTop: spacing.sm },
    previewGrid: {
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.lg,
      borderWidth: 1,
      gap: spacing.xs,
      marginTop: spacing.md,
      padding: spacing.sm,
    },
    previewRow: {
      alignItems: 'center',
      flexDirection: 'row',
      gap: spacing.sm,
      minHeight: 44,
    },
    previewIcon: {
      alignItems: 'center',
      backgroundColor: colors.surfaceWarm,
      borderRadius: radius.md,
      height: 34,
      justifyContent: 'center',
      width: 34,
    },
    previewIconGlyph: { color: colors.teal },
    previewCopy: { flex: 1, minWidth: 0 },
    previewLabel: {
      color: colors.slate,
      fontSize: typography.tiny,
      fontWeight: '900',
      textTransform: 'uppercase',
    },
    previewValue: {
      color: colors.midnight,
      fontSize: typography.small,
      fontWeight: '900',
      marginTop: 1,
    },
    routeSummary: {
      color: colors.slate,
      fontSize: typography.tiny,
      fontWeight: '800',
      lineHeight: 16,
      marginTop: spacing.xs,
    },
    minutesSaved: {
      alignSelf: 'flex-start',
      backgroundColor: colors.surface,
      borderRadius: radius.pill,
      color: colors.teal,
      fontSize: typography.tiny,
      fontWeight: '900',
      marginTop: spacing.xs,
      overflow: 'hidden',
      paddingHorizontal: spacing.sm,
      paddingVertical: 3,
    },
    applySuccess: {
      alignSelf: 'flex-start',
      backgroundColor: colors.surface,
      borderRadius: radius.pill,
      color: colors.teal,
      fontSize: typography.tiny,
      fontWeight: '900',
      marginTop: spacing.xs,
      overflow: 'hidden',
      paddingHorizontal: spacing.sm,
      paddingVertical: 3,
    },
    suggestionError: {
      color: colors.slate,
      fontSize: typography.tiny,
      fontWeight: '800',
      marginTop: spacing.xs,
    },
    previewActions: {
      flexDirection: 'row',
      gap: spacing.sm,
      marginTop: spacing.md,
    },
    previewSecondaryButton: {
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.pill,
      borderWidth: 1,
      flex: 1,
      justifyContent: 'center',
      minHeight: 46,
      paddingHorizontal: spacing.md,
    },
    previewSecondaryText: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900' },
    applyButton: {
      alignItems: 'center',
      backgroundColor: colors.midnight,
      borderColor: colors.midnight,
      borderRadius: radius.pill,
      borderWidth: 1,
      flex: 1.35,
      justifyContent: 'center',
      minHeight: 46,
      paddingHorizontal: spacing.md,
    },
    applyButtonDisabled: {
      opacity: 0.56,
    },
    applyButtonText: { color: colors.surface, fontSize: typography.tiny, fontWeight: '900' },
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
    stopNumberDone: { backgroundColor: colors.teal },
    stopNumberSkipped: { backgroundColor: colors.mist },
    stopNumberTextDone: { color: isDark ? colors.ivory : colors.surface },
    travelIcon: {
      alignItems: 'center',
      backgroundColor: colors.surfaceWarm,
      borderRadius: radius.pill,
      height: 28,
      justifyContent: 'center',
      width: 28,
    },
    verticalLine: { backgroundColor: colors.mist, flex: 1, marginTop: 6, width: 1 },
    travelRow: {
      alignItems: 'center',
      backgroundColor: colors.ivory,
      flexDirection: 'row',
      minHeight: 58,
      paddingHorizontal: spacing.md,
      paddingVertical: spacing.xs,
    },
    travelTime: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900' },
    travelTitle: { color: colors.midnight, fontSize: typography.small, fontWeight: '900', marginTop: 2 },
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
    stopTitleMuted: { color: colors.slate, textDecorationLine: 'line-through' },
    stopNote: { color: colors.slate, fontSize: typography.small, fontWeight: '700', lineHeight: 18, marginTop: 4 },
    progressActions: {
      flexDirection: 'row',
      flexWrap: 'wrap',
      gap: spacing.xs,
      marginTop: spacing.sm,
    },
    progressChip: {
      backgroundColor: colors.surfaceWarm,
      borderColor: colors.mist,
      borderRadius: radius.pill,
      borderWidth: 1,
      paddingHorizontal: spacing.sm,
      paddingVertical: spacing.xs,
    },
    progressChipPrimary: {
      backgroundColor: colors.teal,
      borderColor: colors.teal,
    },
    progressChipText: {
      color: colors.midnight,
      fontSize: typography.tiny,
      fontWeight: '900',
    },
    progressChipPrimaryText: {
      color: isDark ? colors.ivory : colors.surface,
      fontSize: typography.tiny,
      fontWeight: '900',
    },
    constraintWarning: {
      color: colors.teal,
      fontSize: typography.tiny,
      fontWeight: '900',
      lineHeight: 16,
      marginTop: spacing.xs,
    },
  });
}
