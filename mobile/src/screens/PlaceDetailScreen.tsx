import React, { useEffect, useMemo, useState } from 'react';
import {
  Modal,
  ImageBackground,
  Linking,
  Platform,
  Pressable,
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
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../navigation/AppNavigator';
import { savedPlaceApi, tripApi } from '../api/journyApi';
import { session } from '../api/session';
import type { AddPlaceToPlanRequest, ItineraryDay, PlaceResponse, SavedPlaceRequest } from '../api/types';
import { useLanguage, useTranslation } from '../i18n/LanguageContext';
import { useAppTheme } from '../theme/ThemeContext';
import { placeImage } from '../utils/destinationVisuals';
import { localizeDynamicText } from '../utils/localizedDynamicText';

type Props = NativeStackScreenProps<RootStackParamList, 'PlaceDetail'>;

export default function PlaceDetailScreen({ navigation, route }: Props) {
  const { isDark, theme } = useAppTheme();
  const { language } = useLanguage();
  const t = useTranslation();
  const styles = useMemo(() => createStyles(theme, isDark), [isDark, theme]);
  const { colors } = theme;
  const { place } = route.params;
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [addedToPlan, setAddedToPlan] = useState(false);
  const [addingToPlan, setAddingToPlan] = useState(false);
  const [addToPlanError, setAddToPlanError] = useState(false);
  const [itineraryDays, setItineraryDays] = useState<ItineraryDay[]>([]);
  const [selectedDayNumber, setSelectedDayNumber] = useState(1);
  const [dayPickerOpen, setDayPickerOpen] = useState(false);
  const categoryLabel = formatCategory(place.category, t);
  const role = roleForCategory(place.category, t);
  const walkTime = estimatedWalkTime(place.category, language);
  const tags = (place.tags ?? `${categoryLabel},walkable`)
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
    .map((tag) => localizeDynamicText(tag, language))
    .slice(0, 4);
  const bestFit = useMemo(() => bestFitForPlace(place, itineraryDays, t, language), [itineraryDays, language, place, t]);
  const selectedDay = itineraryDays.find((day) => day.dayNumber === selectedDayNumber) ?? bestFit.day;
  const bestFitReasons = bestFit.reasons;
  const heroImage = place.imageUrl || fallbackImageForCategory(place.category, place.city, place.name);

  useEffect(() => {
    let mounted = true;
    savedPlaceApi.status(place.id)
      .then((response) => {
        if (mounted) {
          setSaved(response.saved);
        }
      })
      .catch(() => {
        if (mounted) {
          setSaved(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, [place.id]);

  useEffect(() => {
    let mounted = true;

    const loadItinerary = async () => {
      try {
        let currentTrip = session.getCurrentTrip();
        if (!currentTrip?.id) {
          currentTrip = await tripApi.current();
          session.setCurrentTrip(currentTrip);
        }
        const response = await tripApi.itinerary(currentTrip.id);
        if (mounted) {
          setItineraryDays(response.days);
          const fit = bestFitForPlace(place, response.days, t, language);
          setSelectedDayNumber(fit.day?.dayNumber ?? 1);
        }
      } catch {
        if (mounted) {
          setItineraryDays([]);
          setSelectedDayNumber(1);
        }
      }
    };

    loadItinerary();

    return () => {
      mounted = false;
    };
  }, [language, place, t]);

  const toggleSaved = async () => {
    if (saving) {
      return;
    }
    const nextSaved = !saved;
    setSaved(nextSaved);
    setSaving(true);
    try {
      if (nextSaved) {
        await savedPlaceApi.save(toSavedPlaceRequest(place));
      } else {
        await savedPlaceApi.remove(place.id);
      }
    } catch {
      setSaved(!nextSaved);
    } finally {
      setSaving(false);
    }
  };

  const openMaps = () => {
    const query = encodeURIComponent(`${place.name}, ${place.city}`);
    const lat = place.latitude;
    const lng = place.longitude;
    const url = lat && lng
      ? Platform.select({
          ios: `maps://?q=${query}&ll=${lat},${lng}`,
          android: `geo:${lat},${lng}?q=${query}`,
          default: `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`,
        })
      : `https://www.google.com/maps/search/?api=1&query=${query}`;

    Linking.openURL(url).catch(() => Linking.openURL(`https://www.google.com/maps/search/?api=1&query=${query}`));
  };

  const addToSelectedDay = async () => {
    if (addingToPlan || addedToPlan) {
      return;
    }

    setAddingToPlan(true);
    setAddToPlanError(false);
    try {
      let currentTrip = session.getCurrentTrip();
      if (!currentTrip?.id) {
        currentTrip = await tripApi.current();
        session.setCurrentTrip(currentTrip);
      }
      await tripApi.addPlaceToDay(currentTrip.id, selectedDayNumber, toAddPlaceToPlanRequest(place));
      setAddedToPlan(true);
    } catch {
      setAddToPlanError(true);
    } finally {
      setAddingToPlan(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="light-content" backgroundColor={colors.ivory} />

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.content}>
        <ImageBackground
          source={{
            uri: heroImage,
          }}
          style={styles.hero}
          imageStyle={styles.heroImage}
        >
          <LinearGradient colors={['rgba(23,32,51,0.1)', 'rgba(23,32,51,0.92)']} style={styles.overlay}>
            <View style={styles.heroTop}>
              <TouchableOpacity
                style={styles.backButton}
                onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('MainTabs'))}
              >
                <Ionicons name="arrow-back" size={21} color={colors.midnight} />
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.saveButton, saved && styles.saveButtonActive]}
                activeOpacity={0.86}
                onPress={toggleSaved}
              >
                <Ionicons name={saved ? 'heart' : 'heart-outline'} size={21} color={saved ? colors.surface : colors.midnight} />
              </TouchableOpacity>
            </View>

            <View style={styles.heroCopy}>
              <View style={styles.categoryPill}>
                <Ionicons name={iconForCategory(place.category)} size={14} color={colors.teal} />
                <Text style={styles.categoryText}>{categoryLabel} {t('place.pickSuffix')}</Text>
              </View>
              <Text style={styles.title}>{place.name}</Text>
              <Text style={styles.location}>{place.city} - {t('place.curatedRoute')}</Text>
            </View>
          </LinearGradient>
        </ImageBackground>

        <View style={styles.sheet}>
          <View style={styles.matchCard}>
            <View style={styles.matchIcon}>
              <Ionicons name="sparkles-outline" size={20} color={colors.teal} />
            </View>
            <View style={styles.matchCopy}>
              <Text style={styles.matchTitle}>{t('place.routeMatch')}</Text>
              <Text style={styles.matchText}>{role.text}</Text>
            </View>
          </View>

          <View style={styles.statsRow}>
            <Stat icon="star" value={place.rating.toFixed(1)} label={t('place.rating')} colors={colors} styles={styles} />
            <Stat icon="walk-outline" value={walkTime} label={t('place.estWalk')} colors={colors} styles={styles} />
            <Stat icon="time-outline" value={`${place.estimatedVisitMinutes ?? 60}m`} label={t('place.duration')} colors={colors} styles={styles} />
          </View>

          <View style={styles.detailGrid}>
            <DetailItem icon="location-outline" label={t('place.address')} value={place.address ?? t('place.cityCenter', { city: place.city })} colors={colors} styles={styles} />
            <DetailItem icon="cash-outline" label={t('place.budget')} value={localizeDynamicText(place.priceLevel, language)} colors={colors} styles={styles} />
            <DetailItem icon="time-outline" label={t('place.hours')} value={localizeDynamicText(place.openingHours ?? t('place.flexibleWindow'), language)} colors={colors} styles={styles} />
          </View>

          <View style={styles.tagRow}>
            {tags.map((tag) => (
              <View key={tag} style={styles.tagChip}>
                <Text style={styles.tagText}>{tag}</Text>
              </View>
            ))}
          </View>

          <View style={styles.actionRow}>
            <TouchableOpacity
              style={[styles.secondaryAction, saved && styles.secondaryActionActive]}
              activeOpacity={0.86}
              onPress={toggleSaved}
            >
              <Ionicons name={saved ? 'bookmark' : 'bookmark-outline'} size={17} color={saved ? colors.surface : colors.teal} />
              <Text style={[styles.secondaryActionText, saved && styles.secondaryActionTextActive]}>
                {saved ? t('place.saved') : t('place.save')}
              </Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.secondaryAction} activeOpacity={0.86} onPress={openMaps}>
              <Ionicons name="navigate-outline" size={17} color={colors.teal} />
              <Text style={styles.secondaryActionText}>{t('place.openMaps')}</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('place.whyFits')}</Text>
            <Text style={styles.description}>{localizeDynamicText(place.description, language)}</Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('place.bestFit')}</Text>
            <View style={styles.infoCard}>
              <Ionicons name="calendar-outline" size={20} color={colors.teal} />
              <View style={styles.infoCopy}>
                <Text style={styles.infoTitle}>
                  {t('place.dayWindow', { day: selectedDayNumber, window: bestTimeWindow(place.category, t) })}
                </Text>
                <Text style={styles.infoText}>
                  {t('place.bestFitDay', { day: selectedDayNumber, window: bestTimeWindow(place.category, t) })}
                </Text>
                <View style={styles.fitReasonList}>
                  {bestFitReasons.map((reason) => (
                    <View key={reason} style={styles.fitReasonRow}>
                      <Ionicons name="checkmark-circle" size={13} color={colors.teal} />
                      <Text style={styles.fitReasonText}>{reason}</Text>
                    </View>
                  ))}
                </View>
              </View>
            </View>
          </View>

          <TouchableOpacity style={styles.chooseDayButton} activeOpacity={0.86} onPress={() => setDayPickerOpen(true)}>
            <View>
              <Text style={styles.chooseDayLabel}>{t('place.addLocation')}</Text>
              <Text style={styles.chooseDayValue}>{t('place.dayTitle', { day: selectedDayNumber })}{selectedDay ? ` · ${localizeDynamicText(selectedDay.title, language)}` : ''}</Text>
            </View>
            <Ionicons name="chevron-down" size={18} color={colors.teal} />
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.primaryButton, addedToPlan && styles.primaryButtonActive]}
            activeOpacity={0.9}
            disabled={addingToPlan || addedToPlan}
            onPress={addToSelectedDay}
          >
            <Text style={styles.primaryButtonText}>
              {addingToPlan ? t('place.adding') : addedToPlan ? t('place.addedDay', { day: selectedDayNumber }) : t('place.addDay', { day: selectedDayNumber })}
            </Text>
            <Ionicons name={addingToPlan ? 'hourglass-outline' : addedToPlan ? 'checkmark' : 'add'} size={20} color={colors.surface} />
          </TouchableOpacity>
          {addToPlanError ? <Text style={styles.addError}>{t('place.addError')}</Text> : null}
        </View>
      </ScrollView>
      <Modal visible={dayPickerOpen} transparent animationType="fade" onRequestClose={() => setDayPickerOpen(false)}>
        <Pressable style={styles.dayPickerOverlay} onPress={() => setDayPickerOpen(false)}>
          <Pressable style={styles.dayPickerSheet}>
            <View style={styles.dayPickerHandle} />
            <Text style={styles.dayPickerTitle}>{t('place.chooseAnotherDay')}</Text>
            <Text style={styles.dayPickerSubtitle}>{t('place.chooseSubtitle')}</Text>
            {(itineraryDays.length ? itineraryDays : [{ dayNumber: 1, title: t('place.currentRoute'), summary: '', walkKm: 0, stopCount: 0, stops: [] }]).map((day) => (
              <TouchableOpacity
                key={`day-option-${day.dayNumber}`}
                style={[styles.dayOption, selectedDayNumber === day.dayNumber && styles.dayOptionActive]}
                activeOpacity={0.86}
                onPress={() => {
                  setSelectedDayNumber(day.dayNumber);
                  setDayPickerOpen(false);
                }}
              >
                <View>
                  <Text style={styles.dayOptionTitle}>{t('place.dayTitle', { day: day.dayNumber })}</Text>
                  <Text style={styles.dayOptionMeta}>{t('place.dayMeta', { title: localizeDynamicText(day.title, language), km: day.walkKm.toFixed(1), stops: day.stopCount })}</Text>
                </View>
                {selectedDayNumber === day.dayNumber ? <Ionicons name="checkmark-circle" size={20} color={colors.teal} /> : null}
              </TouchableOpacity>
            ))}
          </Pressable>
        </Pressable>
      </Modal>
    </SafeAreaView>
  );
}

function toAddPlaceToPlanRequest(place: PlaceResponse): AddPlaceToPlanRequest {
  return {
    placeId: place.id,
    name: place.name || 'Route stop',
    city: place.city || 'Current city',
    category: place.category || 'WALKING',
    description: place.description || 'Added from Explore.',
    priceLevel: place.priceLevel || 'Mid',
    rating: place.rating || 4.6,
    address: place.address,
    latitude: place.latitude,
    longitude: place.longitude,
    openingHours: place.openingHours,
    estimatedVisitMinutes: place.estimatedVisitMinutes,
    tags: place.tags,
  };
}

function toSavedPlaceRequest(place: PlaceResponse): SavedPlaceRequest {
  return {
    placeId: place.id,
    name: place.name || 'Saved place',
    city: place.city || 'Current city',
    category: place.category || 'WALKING',
    description: place.description || 'Saved from your Journy route.',
    priceLevel: place.priceLevel || 'Mid',
    rating: place.rating || 4.6,
    imageUrl: place.imageUrl || fallbackImageForCategory(place.category, place.city, place.name),
    address: place.address,
    openingHours: place.openingHours,
    estimatedVisitMinutes: place.estimatedVisitMinutes,
    tags: place.tags,
  };
}

function fallbackImageForCategory(category: string, city?: string, seed = category) {
  return placeImage(city, category, seed);
}

type Translate = ReturnType<typeof useTranslation>;

function formatCategory(category: string, t?: Translate) {
  const value = category.toLowerCase();
  if (!t) return value.replaceAll('_', ' ');
  if (value.includes('coffee') || value.includes('cafe')) return t('setup.coffee');
  if (value.includes('food') || value.includes('restaurant')) return t('setup.localFood');
  if (value.includes('culture') || value.includes('museum')) return t('setup.museums');
  if (value.includes('walking')) return t('setup.walking');
  return value.replaceAll('_', ' ');
}

function iconForCategory(category: string): React.ComponentProps<typeof Ionicons>['name'] {
  const value = category.toLowerCase();
  if (value.includes('coffee') || value.includes('cafe')) return 'cafe-outline';
  if (value.includes('food') || value.includes('restaurant')) return 'restaurant-outline';
  if (value.includes('culture') || value.includes('museum')) return 'color-palette-outline';
  if (value.includes('free')) return 'leaf-outline';
  return 'walk-outline';
}

function estimatedWalkTime(category: string, language: 'en' | 'tr' = 'en') {
  const value = category.toLowerCase();
  const suffix = language === 'tr' ? 'dk' : 'min';
  if (value.includes('coffee')) return `6 ${suffix}`;
  if (value.includes('food')) return `9 ${suffix}`;
  if (value.includes('culture')) return `12 ${suffix}`;
  return `8 ${suffix}`;
}

function roleForCategory(category: string, t: Translate) {
  const value = category.toLowerCase();
  if (value.includes('coffee')) {
    return {
      title: t('place.softBreak'),
      text: t('place.softBreakText'),
    };
  }
  if (value.includes('food')) {
    return {
      title: t('place.localFoodStop'),
      text: t('place.localFoodText'),
    };
  }
  if (value.includes('culture')) {
    return {
      title: t('place.anchorExperience'),
      text: t('place.anchorText'),
    };
  }
  return {
    title: t('place.flexibleMoment'),
    text: t('place.flexibleText'),
  };
}

function bestFitForPlace(place: PlaceResponse, days: ItineraryDay[], t: Translate, language: 'en' | 'tr') {
  const fallbackDay = days[0] ?? null;
  if (!days.length) {
    return {
      day: fallbackDay,
      reasons: [
        t('place.fromCurrentRoute', { minutes: estimatedWalkTime(place.category, language) }),
        t('place.fitsPreference', { category: formatCategory(place.category, t) }),
        t('place.keepsFlexible'),
      ],
    };
  }

  const category = place.category.toUpperCase();
  const scored = days.map((day) => {
    const hasCategory = day.stops.some((stop) => stop.category.toUpperCase().includes(category) || category.includes(stop.category.toUpperCase()));
    const hasFoodGap = !day.stops.some((stop) => ['FOOD', 'COFFEE'].includes(stop.category.toUpperCase()));
    const categoryBonus = category.includes('FOOD') || category.includes('COFFEE')
      ? hasFoodGap ? 3 : 1
      : hasCategory ? 3 : 1;
    const walkRoom = Math.max(0, 7 - day.walkKm);
    return {
      day,
      score: categoryBonus + walkRoom - day.stopCount * 0.25,
    };
  }).sort((first, second) => second.score - first.score);

  const day = scored[0]?.day ?? fallbackDay;
  return {
    day,
    reasons: [
      t('place.fromRouteCluster', { minutes: estimatedWalkTime(place.category, language), stop: day?.stops[0]?.title ?? t('place.currentRoute') }),
      t('place.fitsPreference', { category: formatCategory(place.category, t) }),
      t('place.keepsWalking', { km: Math.max(5.5, (day?.walkKm ?? 4.8) + 0.7).toFixed(1) }),
    ],
  };
}

function bestTimeWindow(category: string, t: Translate) {
  const value = category.toLowerCase();
  if (value.includes('coffee')) return t('place.lateMorning');
  if (value.includes('food')) return t('place.afternoon');
  if (value.includes('culture')) return t('place.morning');
  return t('place.afternoon');
}

function Stat({
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
  styles: PlaceDetailStyles;
}) {
  return (
    <View style={styles.stat}>
      <Ionicons name={icon} size={18} color={colors.teal} />
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

function DetailItem({
  icon,
  label,
  value,
  colors,
  styles,
}: {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  label: string;
  value: string;
  colors: Theme['colors'];
  styles: PlaceDetailStyles;
}) {
  return (
    <View style={styles.detailItem}>
      <Ionicons name={icon} size={16} color={colors.teal} />
      <View style={styles.detailCopy}>
        <Text style={styles.detailLabel}>{label}</Text>
        <Text style={styles.detailValue}>{value}</Text>
      </View>
    </View>
  );
}

type Theme = ReturnType<typeof useAppTheme>['theme'];
type PlaceDetailStyles = ReturnType<typeof createStyles>;

function createStyles({ colors, radius, spacing, typography }: Theme, isDark: boolean) {
  return StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  content: { paddingBottom: spacing.xxl },
  hero: { height: 370 },
  heroImage: { resizeMode: 'cover' },
  overlay: {
    flex: 1,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.lg,
    paddingBottom: spacing.xl,
    justifyContent: 'space-between',
  },
  heroTop: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  backButton: {
    width: 46,
    height: 46,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  saveButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    height: 46,
    justifyContent: 'center',
    width: 46,
  },
  saveButtonActive: {
    backgroundColor: colors.teal,
  },
  heroCopy: {
    paddingBottom: spacing.md,
  },
  categoryPill: {
    alignSelf: 'flex-start',
    backgroundColor: colors.surface,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    marginBottom: spacing.sm,
  },
  categoryText: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
  },
  title: {
    color: isDark ? colors.ink : colors.surface,
    fontSize: 34,
    fontWeight: '900',
    lineHeight: 38,
  },
  location: {
    color: 'rgba(255,255,255,0.78)',
    fontSize: typography.small,
    lineHeight: 18,
    marginTop: spacing.xs,
    fontWeight: '700',
  },
  sheet: {
    backgroundColor: colors.ivory,
    borderTopLeftRadius: radius.xl,
    borderTopRightRadius: radius.xl,
    marginTop: -18,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.lg,
    paddingBottom: spacing.xxl,
  },
  matchCard: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    flexDirection: 'row',
    marginBottom: spacing.md,
    padding: spacing.md,
    shadowColor: colors.shadow,
    shadowOffset: { width: 0, height: 12 },
    shadowOpacity: 0.08,
    shadowRadius: 18,
  },
  matchIcon: {
    alignItems: 'center',
    backgroundColor: colors.lilac,
    borderRadius: radius.lg,
    height: 48,
    justifyContent: 'center',
    marginRight: spacing.sm,
    width: 48,
  },
  matchCopy: { flex: 1 },
  matchTitle: { color: colors.midnight, fontSize: typography.body, fontWeight: '900' },
  matchText: { color: colors.slate, fontSize: typography.small, fontWeight: '700', lineHeight: 19, marginTop: 3 },
  statsRow: {
    flexDirection: 'row',
    gap: spacing.xs,
  },
  detailGrid: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    gap: spacing.sm,
    marginTop: spacing.md,
    padding: spacing.md,
  },
  detailItem: { alignItems: 'center', flexDirection: 'row', gap: spacing.sm },
  detailCopy: { flex: 1 },
  detailLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  detailValue: { color: colors.midnight, fontSize: typography.small, fontWeight: '900', marginTop: 2 },
  tagRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  tagChip: {
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  tagText: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'capitalize' },
  actionRow: {
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  secondaryAction: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flex: 1,
    flexDirection: 'row',
    gap: spacing.xs,
    justifyContent: 'center',
    minHeight: 50,
  },
  secondaryActionActive: {
    backgroundColor: colors.teal,
    borderColor: colors.teal,
  },
  secondaryActionText: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  secondaryActionTextActive: { color: isDark ? colors.ivory : colors.surface },
  stat: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.mist,
    alignItems: 'center',
    minHeight: 104,
    justifyContent: 'center',
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.md,
  },
  statValue: {
    color: colors.midnight,
    fontSize: 22,
    fontWeight: '900',
    marginTop: spacing.xs,
    lineHeight: 26,
  },
  statLabel: {
    color: colors.slate,
    fontSize: typography.tiny,
    fontWeight: '800',
    marginTop: 2,
  },
  section: { marginTop: spacing.xl },
  sectionTitle: {
    color: colors.midnight,
    fontSize: typography.h3,
    fontWeight: '900',
    marginBottom: spacing.sm,
  },
  description: {
    color: colors.slate,
    fontSize: typography.body,
    lineHeight: 24,
  },
  infoCard: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.mist,
    padding: spacing.md,
    flexDirection: 'row',
    gap: spacing.sm,
  },
  infoCopy: { flex: 1 },
  infoTitle: {
    color: colors.midnight,
    fontSize: typography.body,
    fontWeight: '900',
  },
  infoText: {
    color: colors.slate,
    fontSize: typography.small,
    marginTop: 3,
  },
  fitReasonList: {
    gap: 5,
    marginTop: spacing.sm,
  },
  fitReasonRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 5,
  },
  fitReasonText: {
    color: colors.midnight,
    flex: 1,
    fontSize: typography.tiny,
    fontWeight: '800',
    lineHeight: 16,
  },
  chooseDayButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing.md,
    minHeight: 62,
    paddingHorizontal: spacing.md,
  },
  chooseDayLabel: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  chooseDayValue: {
    color: colors.midnight,
    fontSize: typography.small,
    fontWeight: '900',
    marginTop: 3,
  },
  primaryButton: {
    minHeight: 58,
    borderRadius: radius.lg,
    backgroundColor: isDark ? colors.teal : colors.midnight,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.xl,
  },
  primaryButtonActive: {
    backgroundColor: colors.teal,
  },
  primaryButtonText: {
    color: isDark ? colors.ivory : colors.surface,
    fontSize: typography.body,
    fontWeight: '900',
  },
  addError: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '800',
    lineHeight: 16,
    marginTop: spacing.sm,
    textAlign: 'center',
  },
  dayPickerOverlay: {
    backgroundColor: 'rgba(39, 35, 33, 0.34)',
    flex: 1,
    justifyContent: 'flex-end',
    padding: spacing.md,
  },
  dayPickerSheet: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    padding: spacing.md,
  },
  dayPickerHandle: {
    alignSelf: 'center',
    backgroundColor: colors.mist,
    borderRadius: radius.pill,
    height: 4,
    marginBottom: spacing.md,
    width: 42,
  },
  dayPickerTitle: {
    color: colors.midnight,
    fontSize: typography.h3,
    fontWeight: '900',
  },
  dayPickerSubtitle: {
    color: colors.slate,
    fontSize: typography.small,
    fontWeight: '800',
    lineHeight: 19,
    marginTop: spacing.xs,
  },
  dayOption: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing.sm,
    minHeight: 64,
    paddingHorizontal: spacing.md,
  },
  dayOptionActive: {
    borderColor: colors.teal,
  },
  dayOptionTitle: {
    color: colors.midnight,
    fontSize: typography.small,
    fontWeight: '900',
  },
  dayOptionMeta: {
    color: colors.slate,
    fontSize: typography.tiny,
    fontWeight: '800',
    marginTop: 3,
  },
});
}
