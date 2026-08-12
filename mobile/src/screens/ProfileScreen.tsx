import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Image,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { authApi, destinationApi, profileApi } from '../api/journyApi';
import { session } from '../api/session';
import type { ProfileResponse, TripResponse } from '../api/types';
import { useAppTheme } from '../theme/ThemeContext';
import { InlineError, InlineLoading } from '../components/StateViews';
import { cityBackupImage, cityImage } from '../utils/destinationVisuals';

type IconName = React.ComponentProps<typeof Ionicons>['name'];

const tasteSignals: Array<{ label: string; detail: string; icon: IconName }> = [
  { label: 'Local food', detail: 'Hidden restaurants', icon: 'restaurant-outline' },
  { label: 'Museums', detail: 'Culture windows', icon: 'color-palette-outline' },
  { label: 'Coffee', detail: 'Quiet breaks', icon: 'cafe-outline' },
  { label: 'Walking', detail: 'Easy pace', icon: 'walk-outline' },
];

export default function ProfileScreen() {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const navigation = useNavigation<any>();
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [destinationImages, setDestinationImages] = useState<Record<string, string>>({});

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const response = await profileApi.me();
      setProfile(response);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(useCallback(() => {
    let mounted = true;

    const load = async () => {
      setError(false);
      try {
        const response = await profileApi.me();
        if (mounted) {
          setProfile(response);
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
  }, []));

  const currentTrip = normalizeCurrentTrip(profile?.currentTrip, session.getCurrentTrip());
  const fallbackDestination = currentTrip?.destination ?? 'Plan a trip';
  const savedPlanDestinations = useMemo(() => {
    const seen = new Set<string>();
    const destinations = [
      ...(profile?.savedPlans.map((plan) => plan.destination) ?? []),
      ...(currentTrip?.destination ? [currentTrip.destination] : []),
    ];

    return destinations.filter((destination) => {
      const trimmed = destination.trim();
      const key = trimmed.toLowerCase();
      if (!trimmed || seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    });
  }, [currentTrip?.destination, profile?.savedPlans]);

  useEffect(() => {
    if (!savedPlanDestinations.length) {
      setDestinationImages({});
      return;
    }

    let mounted = true;
    Promise.all(savedPlanDestinations.map(async (destination) => {
      try {
        const matches = await destinationApi.search(destination);
        const exact = matches.find((item) => item.name.toLowerCase() === destination.toLowerCase()) ?? matches[0];
        return [destination, exact?.imageUrl] as const;
      } catch {
        return [destination, undefined] as const;
      }
    })).then((entries) => {
      if (!mounted) {
        return;
      }
      const nextImages = entries.reduce<Record<string, string>>((acc, [destination, imageUrl]) => {
        if (imageUrl) {
          acc[destination] = imageUrl;
        }
        return acc;
      }, {});
      setDestinationImages(nextImages);
    });

    return () => {
      mounted = false;
    };
  }, [savedPlanDestinations]);
  const displayTaste = profile?.tasteProfile?.length
    ? profile.tasteProfile.map((item) => ({
        label: item.title,
        detail: item.description,
        icon: mapTasteIcon(item.icon),
      }))
    : tasteSignals;
  const displaySavedPlans = profile?.savedPlans?.length
    ? profile.savedPlans.map((plan) => ({
        key: plan.id,
        id: plan.id,
        city: plan.destination,
        detail: plan.summary,
        image: profileCityImage(plan.destination, destinationImages),
        stops: plan.stops,
        walk: plan.averageWalkKm,
      }))
    : profile
      ? []
      : currentTrip
        ? [{
            key: `preview-${currentTrip.destination}`,
            id: `preview-${currentTrip.id}`,
            city: currentTrip.destination,
            detail: `${currentTrip.days} days - ${formatEnum(currentTrip.pace)} pace`,
            image: profileCityImage(currentTrip.destination, destinationImages),
            stops: currentTrip.stops,
            walk: currentTrip.averageWalkKm,
          }]
        : [];
  const displayFavoritePlaces = profile?.savedPlaces
    ? profile.savedPlaces.map((place) => ({
        title: place.name,
        meta: `${formatCategory(place.category)} - ${place.city} - ${place.rating.toFixed(1)}`,
        icon: mapPlaceIcon(place.category),
      }))
    : [];
  const accountPreferences: Array<{ label: string; value: string; icon: IconName }> = [
    { label: 'Default pace', value: formatEnum(profile?.preferences.defaultPace ?? currentTrip?.pace ?? 'BALANCED'), icon: 'speedometer-outline' },
    { label: 'Default budget', value: formatEnum(profile?.preferences.defaultBudget ?? currentTrip?.budget ?? 'BALANCED'), icon: 'wallet-outline' },
    { label: 'Food discovery', value: formatEnum(profile?.preferences.foodDiscovery ?? 'LOCAL_FIRST'), icon: 'restaurant-outline' },
    {
      label: 'Notifications',
      value: profile?.preferences.planChangeNotifications ? 'Plan changes on' : 'Plan changes off',
      icon: 'notifications-outline',
    },
  ];
  const tripStatus = currentTrip ? `${currentTrip.destination} live` : displaySavedPlans.length ? 'Saved' : 'Ready';
  const travelIdentity = useMemo(() => buildTravelIdentity(profile), [profile]);

  const editCurrentTrip = () => {
    if (!currentTrip) {
      navigation.navigate('TripSetup');
      return;
    }
    navigation.navigate('TripSetup', {
      initialTrip: {
        tripId: currentTrip.id,
        destination: currentTrip.destination,
        startingArea: currentTrip.startingArea,
        startDate: currentTrip.startDate,
        endDate: currentTrip.endDate,
        travelerType: currentTrip.travelerType as any,
        budget: currentTrip.budget as any,
        pace: currentTrip.pace as any,
        interests: currentTrip.interests as any,
      },
    });
  };

  const signOut = async () => {
    await authApi.logout();
    navigation.reset({
      index: 0,
      routes: [{ name: 'Welcome' }],
    });
  };

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>ID</Text>
          </View>
          <View style={styles.headerCopy}>
            <Text style={styles.name}>{profile?.fullName ?? 'Irem Dinc'}</Text>
            <Text style={styles.meta}>{profile?.travelStyle ?? 'Balanced traveler'}</Text>
          </View>
          <TouchableOpacity style={styles.settingsButton} activeOpacity={0.86} onPress={() => navigation.navigate('Settings')}>
            <Ionicons name="settings-outline" size={21} color={colors.midnight} />
          </TouchableOpacity>
        </View>

        <View style={styles.memberStrip}>
          <View style={styles.memberMetric}>
            <Text style={styles.memberValue}>{profile ? profile.favoriteCount : 3}</Text>
            <Text style={styles.memberLabel}>Favorites</Text>
          </View>
          <View style={styles.memberDivider} />
          <View style={styles.memberMetric}>
            <Text style={styles.memberValue}>{displayTaste.length}</Text>
            <Text style={styles.memberLabel}>Taste signals</Text>
          </View>
          <View style={styles.memberDivider} />
          <View style={styles.memberMetric}>
            <Text style={styles.memberValue} numberOfLines={1} adjustsFontSizeToFit>{tripStatus}</Text>
            <Text style={styles.memberLabel}>Trip status</Text>
          </View>
        </View>

        {loading ? <InlineLoading label="Loading your profile..." /> : null}
        {error ? (
          <InlineError
            title="Profile is showing a local preview"
            description="Retry after the API connection is available."
            onRetry={loadProfile}
          />
        ) : null}

        <View style={styles.tripCard}>
          <View style={styles.tripTop}>
            <View>
              <Text style={styles.kicker}>Current trip</Text>
              <Text style={styles.tripTitle}>{fallbackDestination}</Text>
            </View>
            <TouchableOpacity style={styles.editPill} activeOpacity={0.82} onPress={editCurrentTrip}>
              <Ionicons name="pencil-outline" size={14} color={colors.teal} />
              <Text style={styles.editText}>Edit</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.tripMetaRow}>
            <InfoChip icon="calendar-outline" text={currentTrip?.dates ?? 'Choose dates'} colors={colors} styles={styles} />
            <InfoChip icon="wallet-outline" text={`${formatEnum(currentTrip?.budget ?? 'BALANCED')} budget`} colors={colors} styles={styles} />
            <InfoChip icon="speedometer-outline" text={`${formatEnum(currentTrip?.pace ?? 'BALANCED')} pace`} colors={colors} styles={styles} />
          </View>

          <View style={styles.statRow}>
            <Stat icon="location-outline" value={`${currentTrip?.stops ?? 0}`} label="Stops" colors={colors} styles={styles} />
            <Stat icon="restaurant-outline" value={`${currentTrip?.foodPicks ?? 0}`} label="Food picks" colors={colors} styles={styles} />
            <Stat icon="walk-outline" value={`${(currentTrip?.averageWalkKm ?? 0).toFixed(1)} km`} label="Avg walk" colors={colors} styles={styles} />
          </View>
        </View>

        {currentTrip?.planningStrategy ? (
          <View style={styles.strategyCard}>
            <View style={styles.strategyTop}>
              <View style={styles.strategyIcon}>
                <Ionicons name="sparkles-outline" size={18} color={colors.teal} />
              </View>
              <View style={styles.strategyCopy}>
                <Text style={styles.strategyKicker}>Planning strategy</Text>
                <Text style={styles.strategyTitle}>{currentTrip.planningStrategy.title}</Text>
              </View>
            </View>
            <Text style={styles.strategyDescription}>{currentTrip.planningStrategy.description}</Text>
            <View style={styles.strategySignals}>
              {currentTrip.planningStrategy.signals.map((signal) => (
                <View key={signal} style={styles.strategyChip}>
                  <Text style={styles.strategyChipText}>{signal}</Text>
                </View>
              ))}
            </View>
          </View>
        ) : null}

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Taste profile</Text>
          <TouchableOpacity activeOpacity={0.82} onPress={editCurrentTrip}>
            <Text style={styles.sectionAction}>Refine</Text>
          </TouchableOpacity>
        </View>
        <View style={styles.tasteGrid}>
          {displayTaste.map((item, index) => (
            <View key={`${item.label}-${index}`} style={styles.tasteCard}>
              <View style={styles.tasteIcon}>
                <Ionicons name={item.icon} size={18} color={colors.teal} />
              </View>
              <View style={styles.tasteCopy}>
                <Text style={styles.tasteLabel}>{item.label}</Text>
                <Text style={styles.tasteDetail}>{item.detail}</Text>
              </View>
            </View>
          ))}
        </View>

        <View style={styles.identityCard}>
          <View style={styles.identityTop}>
            <View>
              <Text style={styles.identityKicker}>Your travel taste</Text>
              <Text style={styles.identityTitle}>{travelIdentity.headline}</Text>
            </View>
            <View style={styles.identityIcon}>
              <Ionicons name="finger-print-outline" size={20} color={colors.teal} />
            </View>
          </View>
          <View style={styles.tasteLevelList}>
            {travelIdentity.levels.map((level) => (
              <View key={level.label} style={styles.tasteLevelRow}>
                <View style={styles.tasteLevelTop}>
                  <Text style={styles.tasteLevelLabel}>{level.label}</Text>
                  <Text style={styles.tasteLevelValue}>{level.value}%</Text>
                </View>
                <View style={styles.tasteTrack}>
                  <View style={[styles.tasteFill, { width: `${level.value}%` }]} />
                </View>
              </View>
            ))}
          </View>
          <Text style={styles.identityInsight}>{travelIdentity.insight}</Text>
          <View style={styles.identityLearningRow}>
            <Ionicons name="analytics-outline" size={15} color={colors.teal} />
            <Text style={styles.identityLearningText}>{travelIdentity.learningSource}</Text>
          </View>
          <Text style={styles.preferenceSectionLabel}>Journy knows you prefer</Text>
          <View style={styles.preferenceChips}>
            {travelIdentity.preferences.map((item) => (
              <View key={item} style={styles.preferenceChip}>
                <Ionicons name="checkmark-circle" size={13} color={colors.teal} />
                <Text style={styles.preferenceChipText}>{item}</Text>
              </View>
            ))}
          </View>
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Saved plans</Text>
          <TouchableOpacity activeOpacity={0.82} onPress={() => navigation.navigate('SavedPlans')}>
            <Text style={styles.sectionAction}>View all</Text>
          </TouchableOpacity>
        </View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.savedRail}>
          {displaySavedPlans.map((plan) => (
            <SavedPlanCard
              key={plan.key}
              plan={plan}
              onPress={() => navigation.navigate('SavedPlans')}
              colors={colors}
              styles={styles}
            />
          ))}
          {!displaySavedPlans.length ? (
            <TouchableOpacity style={styles.savedEmptyCard} activeOpacity={0.86} onPress={() => navigation.navigate('TripSetup')}>
              <View style={styles.savedEmptyIcon}>
                <Ionicons name="map-outline" size={22} color={colors.teal} />
              </View>
              <Text style={styles.savedCity}>No saved plans yet</Text>
              <Text style={styles.savedDetail}>Create a trip and Journy will keep it ready here.</Text>
            </TouchableOpacity>
          ) : null}
        </ScrollView>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Favorites</Text>
          <TouchableOpacity activeOpacity={0.82} onPress={() => navigation.navigate('SavedPlaces')}>
            <Text style={styles.sectionAction}>Manage</Text>
          </TouchableOpacity>
        </View>
        <View style={styles.favoriteList}>
          {displayFavoritePlaces.length ? (
            displayFavoritePlaces.map((place) => (
              <View key={place.title} style={styles.favoriteRow}>
                <View style={styles.favoriteIcon}>
                  <Ionicons name={place.icon} size={18} color={colors.teal} />
                </View>
                <View style={styles.favoriteCopy}>
                  <Text style={styles.favoriteTitle}>{place.title}</Text>
                  <Text style={styles.favoriteMeta}>{place.meta}</Text>
                </View>
                <Ionicons name="heart" size={18} color={colors.teal} />
              </View>
            ))
          ) : (
            <View style={styles.favoriteRow}>
              <View style={styles.favoriteIcon}>
                <Ionicons name="heart-outline" size={18} color={colors.teal} />
              </View>
              <View style={styles.favoriteCopy}>
                <Text style={styles.favoriteTitle}>No saved places yet</Text>
                <Text style={styles.favoriteMeta}>Save places from Explore or day route details.</Text>
              </View>
            </View>
          )}
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Recent trips</Text>
        </View>
        <View style={styles.recentCard}>
          {displaySavedPlans.slice(0, 3).map((plan, index) => (
            <View key={`recent-${plan.key}`} style={[styles.recentRow, index > 0 && styles.recentRowBorder]}>
              <View style={styles.recentIndex}>
                <Text style={styles.recentIndexText}>{index + 1}</Text>
              </View>
              <View style={styles.recentCopy}>
                <Text style={styles.recentTitle}>{plan.city}</Text>
                <Text style={styles.recentMeta}>{plan.detail}</Text>
              </View>
              <Text style={styles.recentWalk}>{plan.walk.toFixed(1)} km</Text>
            </View>
          ))}
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Account preferences</Text>
        </View>
        <View style={styles.preferenceCard}>
          {accountPreferences.map((item, index) => (
            <View key={item.label} style={[styles.preferenceRow, index > 0 && styles.preferenceRowBorder]}>
              <View style={styles.preferenceIcon}>
                <Ionicons name={item.icon} size={18} color={colors.teal} />
              </View>
              <Text style={styles.preferenceLabel}>{item.label}</Text>
              <Text style={styles.preferenceValue}>{item.value}</Text>
            </View>
          ))}
        </View>

        <TouchableOpacity style={styles.signOutButton} activeOpacity={0.86} onPress={signOut}>
          <Ionicons name="log-out-outline" size={18} color={colors.teal} />
          <Text style={styles.signOutText}>Sign out</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

type ProfileTrip = NonNullable<ProfileResponse['currentTrip']>;
type DisplayCurrentTrip = ProfileTrip & {
  days: number;
  stops: number;
  foodPicks: number;
  averageWalkKm: number;
  dates: string;
};
type DisplaySavedPlan = {
  key: string;
  id: string;
  city: string;
  detail: string;
  image: string;
  stops: number;
  walk: number;
};

function SavedPlanCard({
  plan,
  onPress,
  colors,
  styles,
}: {
  plan: DisplaySavedPlan;
  onPress: () => void;
  colors: Theme['colors'];
  styles: ProfileStyles;
}) {
  const [imageUri, setImageUri] = useState(plan.image);
  const [usedBackup, setUsedBackup] = useState(false);

  useEffect(() => {
    setImageUri(plan.image);
    setUsedBackup(false);
  }, [plan.image]);

  const handleImageError = () => {
    if (!usedBackup) {
      setImageUri(cityBackupImage(plan.city));
      setUsedBackup(true);
    }
  };

  return (
    <TouchableOpacity
      style={styles.savedCard}
      activeOpacity={0.88}
      onPress={onPress}
    >
      <Image source={{ uri: imageUri }} style={styles.savedImage} onError={handleImageError} />
      <View style={styles.savedBody}>
        <Text style={styles.savedCity}>{plan.city}</Text>
        <Text style={styles.savedDetail}>{plan.detail}</Text>
        <View style={styles.savedStats}>
          <View style={styles.savedMetric}>
            <Ionicons name="location-outline" size={13} color={colors.teal} />
            <Text style={styles.savedMetricText}>{plan.stops} stops</Text>
          </View>
          <View style={styles.savedMetric}>
            <Ionicons name="walk-outline" size={13} color={colors.teal} />
            <Text style={styles.savedMetricText}>{plan.walk.toFixed(1)} km</Text>
          </View>
        </View>
      </View>
    </TouchableOpacity>
  );
}

function normalizeCurrentTrip(profileTrip?: ProfileResponse['currentTrip'], sessionTrip?: TripResponse | null): DisplayCurrentTrip | null {
  if (profileTrip) {
    return {
      ...profileTrip,
      days: tripDays(profileTrip.startDate, profileTrip.endDate),
    };
  }
  if (!sessionTrip) {
    return null;
  }
  return {
    id: sessionTrip.id,
    destination: sessionTrip.destination,
    startingArea: sessionTrip.startingArea,
    startDate: sessionTrip.startDate,
    endDate: sessionTrip.endDate,
    days: sessionTrip.days,
    dates: formatDateRange(sessionTrip.startDate, sessionTrip.endDate),
    travelerType: sessionTrip.travelerType,
    budget: sessionTrip.budget,
    pace: sessionTrip.pace,
    interests: sessionTrip.interests,
    stops: sessionTrip.stats.stops,
    foodPicks: sessionTrip.stats.foodPicks,
    averageWalkKm: sessionTrip.stats.averageWalkKm,
  };
}

function tripDays(startDate: string, endDate: string) {
  const start = new Date(startDate);
  const end = new Date(endDate);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    return 1;
  }
  return Math.max(1, Math.ceil((end.getTime() - start.getTime()) / 86400000));
}

function formatDateRange(startDate: string, endDate: string) {
  const start = shortDate(startDate);
  const end = shortDate(endDate);
  return start && end ? `${start} - ${end}` : 'Choose dates';
}

function shortDate(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return '';
  }
  return parsed.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function mapTasteIcon(icon: string): IconName {
  const value = icon.toLowerCase();
  if (value.includes('food') || value.includes('restaurant')) return 'restaurant-outline';
  if (value.includes('museum') || value.includes('culture')) return 'color-palette-outline';
  if (value.includes('coffee') || value.includes('cafe')) return 'cafe-outline';
  return 'walk-outline';
}

function mapPlaceIcon(category: string): IconName {
  const value = category.toLowerCase();
  if (value.includes('coffee')) return 'cafe-outline';
  if (value.includes('food') || value.includes('restaurant')) return 'restaurant-outline';
  if (value.includes('culture') || value.includes('museum')) return 'color-palette-outline';
  if (value.includes('free')) return 'leaf-outline';
  return 'walk-outline';
}

function formatCategory(category: string) {
  return category.toLowerCase().replace(/_/g, ' ');
}

function formatEnum(value: string) {
  return value
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function profileCityImage(destination: string, destinationImages: Record<string, string>) {
  const apiImage = destinationImages[destination];
  if (apiImage) {
    return apiImage;
  }

  const localImage = cityImage(destination);
  if (localImage.includes('Special:FilePath')) {
    return cityBackupImage(destination);
  }
  return localImage;
}

function buildTravelIdentity(profile: ProfileResponse | null) {
  const weights: Record<string, number> = {
    Coffee: 18,
    'Local food': 20,
    Culture: 18,
    Walking: 22,
  };

  profile?.currentTrip?.interests.forEach((interest) => {
    if (interest.includes('COFFEE')) weights.Coffee += 26;
    if (interest.includes('LOCAL_FOOD')) weights['Local food'] += 26;
    if (interest.includes('MUSEUMS') || interest.includes('CULTURE')) weights.Culture += 26;
    if (interest.includes('WALKING')) weights.Walking += 26;
  });

  profile?.tasteProfile.forEach((signal) => {
    const title = signal.title.toLowerCase();
    if (title.includes('coffee')) weights.Coffee += 18;
    if (title.includes('food')) weights['Local food'] += 18;
    if (title.includes('culture')) weights.Culture += 18;
    if (title.includes('walk')) weights.Walking += 18;
  });

  profile?.savedPlaces.forEach((place) => {
    const category = place.category.toLowerCase();
    if (category.includes('coffee')) weights.Coffee += 14;
    if (category.includes('food')) weights['Local food'] += 14;
    if (category.includes('culture')) weights.Culture += 14;
    if (category.includes('walk') || category.includes('free')) weights.Walking += 10;
  });

  const levels = Object.entries(weights).map(([label, value]) => ({
    label,
    value: Math.min(96, Math.max(28, value)),
  })).sort((first, second) => second.value - first.value);
  const strongest = levels[0];
  const headline = `${strongest.label} is becoming a signature preference`;
  const insight = `${strongest.label} is one of your strongest travel signals, so Journy will keep using it when shaping route rhythm and recommendations.`;
  const savedCount = profile?.savedPlaces.length ?? 0;
  const interestCount = profile?.currentTrip?.interests.length ?? 0;
  const learningSource = savedCount
    ? `Learned from ${savedCount} saved places and your current trip setup.`
    : interestCount
      ? `Learned from ${interestCount} trip setup signals. Save places to make this sharper.`
      : 'Journy will sharpen this profile as you save places and plan trips.';
  const preferences: string[] = levels.slice(0, 3).map((level) => {
    if (level.label === 'Coffee') return 'Independent coffee';
    if (level.label === 'Local food') return 'Local food';
    if (level.label === 'Culture') return 'Culture windows';
    return 'Walkable routes';
  });
  if (profile?.currentTrip?.pace) {
    preferences.push(`${formatEnum(profile.currentTrip.pace)} days`);
  }

  return {
    headline,
    insight,
    learningSource,
    levels,
    preferences: [...new Set(preferences)].slice(0, 4),
  };
}

type Theme = ReturnType<typeof useAppTheme>['theme'];
type AppColors = Theme['colors'];
type ProfileStyles = ReturnType<typeof createStyles>;

function InfoChip({ icon, text, colors, styles }: { icon: IconName; text: string; colors: AppColors; styles: ProfileStyles }) {
  return (
    <View style={styles.infoChip}>
      <Ionicons name={icon} size={14} color={colors.teal} />
      <Text style={styles.infoChipText}>{text}</Text>
    </View>
  );
}

function Stat({
  icon,
  value,
  label,
  colors,
  styles,
}: {
  icon: IconName;
  value: string;
  label: string;
  colors: AppColors;
  styles: ProfileStyles;
}) {
  return (
    <View style={styles.stat}>
      <View style={styles.statIcon}>
        <Ionicons name={icon} size={18} color={colors.teal} />
      </View>
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
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
    marginTop: spacing.md,
  },
  avatar: {
    alignItems: 'center',
    backgroundColor: colors.midnight,
    borderRadius: radius.lg,
    height: 62,
    justifyContent: 'center',
    width: 62,
  },
  avatarText: { color: colors.surface, fontSize: typography.h3, fontWeight: '900' },
  headerCopy: { flex: 1, marginLeft: spacing.md },
  name: { color: colors.midnight, fontSize: typography.h2, fontWeight: '900' },
  meta: { color: colors.slate, fontSize: typography.small, fontWeight: '800', marginTop: 3 },
  settingsButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.md,
    borderWidth: 1,
    height: 46,
    justifyContent: 'center',
    width: 46,
  },
  memberStrip: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    flexDirection: 'row',
    marginTop: spacing.lg,
    minHeight: 82,
    paddingHorizontal: spacing.md,
  },
  memberMetric: { alignItems: 'center', flex: 1 },
  memberValue: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
  memberLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 3, textAlign: 'center' },
  memberDivider: { backgroundColor: colors.mist, height: 38, width: 1 },
  tripCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.xl,
    padding: spacing.lg,
    shadowColor: colors.shadow,
    shadowOffset: { width: 0, height: 14 },
    shadowOpacity: 0.1,
    shadowRadius: 24,
  },
  tripTop: { alignItems: 'flex-start', flexDirection: 'row', justifyContent: 'space-between' },
  kicker: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  tripTitle: { color: colors.midnight, fontSize: 32, fontWeight: '900', letterSpacing: 0, lineHeight: 37, marginTop: spacing.xs },
  editPill: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    flexDirection: 'row',
    gap: 5,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  editText: { color: colors.teal, fontSize: typography.small, fontWeight: '900' },
  tripMetaRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginTop: spacing.md },
  infoChip: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    flexDirection: 'row',
    gap: 5,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  infoChipText: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900' },
  statRow: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.lg },
  stat: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderRadius: radius.lg,
    flex: 1,
    minHeight: 98,
    padding: spacing.sm,
  },
  statIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    height: 34,
    justifyContent: 'center',
    width: 34,
  },
  statValue: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900', marginTop: spacing.xs },
  statLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 2, textAlign: 'center' },
  strategyCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.md,
    padding: spacing.lg,
  },
  strategyTop: { alignItems: 'center', flexDirection: 'row' },
  strategyIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    height: 42,
    justifyContent: 'center',
    marginRight: spacing.sm,
    width: 42,
  },
  strategyCopy: { flex: 1, minWidth: 0 },
  strategyKicker: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  strategyTitle: { color: colors.midnight, fontSize: typography.small, fontWeight: '900', marginTop: 3 },
  strategyDescription: { color: colors.slate, fontSize: typography.small, fontWeight: '800', lineHeight: 20, marginTop: spacing.md },
  strategySignals: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.xs, marginTop: spacing.md },
  strategyChip: {
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  strategyChipText: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900' },
  sectionHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing.xl,
  },
  sectionTitle: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
  sectionAction: { color: colors.teal, fontSize: typography.small, fontWeight: '900' },
  tasteGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  tasteCard: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    minHeight: 76,
    padding: spacing.sm,
    width: '48%',
  },
  tasteIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    height: 42,
    justifyContent: 'center',
    marginRight: spacing.sm,
    width: 42,
  },
  tasteCopy: { flex: 1 },
  tasteLabel: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  tasteDetail: { color: colors.slate, fontSize: typography.tiny, fontWeight: '700', lineHeight: 15, marginTop: 3 },
  identityCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.md,
    padding: spacing.lg,
  },
  identityTop: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: spacing.md,
  },
  identityKicker: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  identityTitle: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900', lineHeight: 22, marginTop: 3 },
  identityIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    height: 42,
    justifyContent: 'center',
    width: 42,
  },
  tasteLevelList: { gap: spacing.sm, marginTop: spacing.md },
  tasteLevelRow: { gap: spacing.xs },
  tasteLevelTop: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  tasteLevelLabel: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  tasteLevelValue: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900' },
  tasteTrack: {
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    height: 8,
    overflow: 'hidden',
  },
  tasteFill: {
    backgroundColor: colors.teal,
    borderRadius: radius.pill,
    height: '100%',
  },
  identityInsight: {
    color: colors.slate,
    fontSize: typography.small,
    fontWeight: '800',
    lineHeight: 20,
    marginTop: spacing.md,
  },
  identityLearningRow: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderRadius: radius.lg,
    flexDirection: 'row',
    gap: spacing.xs,
    marginTop: spacing.md,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.sm,
  },
  identityLearningText: { color: colors.midnight, flex: 1, fontSize: typography.tiny, fontWeight: '900', lineHeight: 16 },
  preferenceSectionLabel: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    marginTop: spacing.md,
    textTransform: 'uppercase',
  },
  preferenceChips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.xs,
    marginTop: spacing.md,
  },
  preferenceChip: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    flexDirection: 'row',
    gap: 4,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  preferenceChipText: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900' },
  savedRail: { gap: spacing.md, paddingVertical: spacing.md },
  savedCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    overflow: 'hidden',
    width: 252,
  },
  savedEmptyCard: {
    alignItems: 'flex-start',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    minHeight: 188,
    padding: spacing.md,
    width: 252,
  },
  savedEmptyIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    height: 48,
    justifyContent: 'center',
    marginBottom: spacing.md,
    width: 48,
  },
  savedImage: { height: 126, width: '100%' },
  savedBody: { padding: spacing.md },
  savedCity: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
  savedDetail: { color: colors.slate, fontSize: typography.small, fontWeight: '800', marginTop: 4 },
  savedStats: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.md },
  savedMetric: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    flexDirection: 'row',
    gap: 4,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  savedMetricText: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900' },
  favoriteList: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.md,
    overflow: 'hidden',
  },
  favoriteRow: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 74,
    paddingHorizontal: spacing.md,
  },
  favoriteIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    height: 42,
    justifyContent: 'center',
    marginRight: spacing.sm,
    width: 42,
  },
  favoriteCopy: { flex: 1 },
  favoriteTitle: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  favoriteMeta: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 3 },
  recentCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.md,
    overflow: 'hidden',
  },
  recentRow: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 72,
    paddingHorizontal: spacing.md,
  },
  recentRowBorder: { borderColor: colors.mist, borderTopWidth: 1 },
  recentIndex: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderRadius: radius.pill,
    height: 34,
    justifyContent: 'center',
    marginRight: spacing.sm,
    width: 34,
  },
  recentIndexText: { color: colors.teal, fontSize: typography.small, fontWeight: '900' },
  recentCopy: { flex: 1 },
  recentTitle: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  recentMeta: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 3 },
  recentWalk: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900' },
  preferenceCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.md,
    overflow: 'hidden',
  },
  preferenceRow: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 68,
    paddingHorizontal: spacing.md,
  },
  preferenceRowBorder: { borderColor: colors.mist, borderTopWidth: 1 },
  preferenceIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    height: 38,
    justifyContent: 'center',
    marginRight: spacing.sm,
    width: 38,
  },
  preferenceLabel: { color: colors.midnight, flex: 1, fontSize: typography.small, fontWeight: '900' },
  preferenceValue: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900' },
  signOutButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    gap: spacing.sm,
    justifyContent: 'center',
    marginTop: spacing.md,
    minHeight: 52,
  },
  signOutText: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
});
}
