import React, { useCallback, useMemo, useState } from 'react';
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
import { authApi, profileApi } from '../api/journyApi';
import type { ProfileResponse } from '../api/types';
import { useAppTheme } from '../theme/ThemeContext';
import { InlineError, InlineLoading } from '../components/StateViews';

type IconName = React.ComponentProps<typeof Ionicons>['name'];

const tasteSignals: Array<{ label: string; detail: string; icon: IconName }> = [
  { label: 'Local food', detail: 'Hidden restaurants', icon: 'restaurant-outline' },
  { label: 'Museums', detail: 'Culture windows', icon: 'color-palette-outline' },
  { label: 'Coffee', detail: 'Quiet breaks', icon: 'cafe-outline' },
  { label: 'Walking', detail: 'Easy pace', icon: 'walk-outline' },
];

const savedPlans = [
  {
    city: 'Amsterdam',
    detail: '4 days - Balanced pace',
    image:
      'https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=500&q=88',
  },
  {
    city: 'Rome',
    detail: 'Food-first weekend',
    image:
      'https://images.unsplash.com/photo-1552832230-c0197dd311b5?auto=format&fit=crop&w=500&q=88',
  },
];

const favoritePlaces: Array<{ title: string; meta: string; icon: IconName }> = [
  { title: 'Quiet Cup De Pijp', meta: 'Coffee - Amsterdam', icon: 'cafe-outline' },
  { title: 'Canal Bakery', meta: 'Food - Amsterdam', icon: 'restaurant-outline' },
  { title: 'Small Gallery Walk', meta: 'Culture - Amsterdam', icon: 'color-palette-outline' },
];

const accountPreferences: Array<{ label: string; value: string; icon: IconName }> = [
  { label: 'Default pace', value: 'Balanced', icon: 'speedometer-outline' },
  { label: 'Food discovery', value: 'Local-first', icon: 'restaurant-outline' },
  { label: 'Notifications', value: 'Plan changes', icon: 'notifications-outline' },
];

export default function ProfileScreen() {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const navigation = useNavigation<any>();
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

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

  const currentTrip = profile?.currentTrip;
  const displayTaste = profile?.tasteProfile?.length
    ? profile.tasteProfile.map((item) => ({
        label: item.title,
        detail: item.description,
        icon: mapTasteIcon(item.icon),
      }))
    : tasteSignals;
  const displaySavedPlans = profile?.savedPlans?.length
    ? profile.savedPlans.map((plan, index) => ({
        key: plan.id,
        city: plan.destination,
        detail: plan.summary,
        image: savedPlans[index % savedPlans.length].image,
        stops: plan.stops,
        walk: plan.averageWalkKm,
      }))
    : savedPlans.map((plan, index) => ({ ...plan, key: `${plan.city}-${index}`, stops: 18, walk: 6.2 }));
  const displayFavoritePlaces = profile
    ? (profile.savedPlaces ?? []).map((place) => ({
        title: place.name,
        meta: `${formatCategory(place.category)} - ${place.city} - ${place.rating.toFixed(1)}`,
        icon: mapPlaceIcon(place.category),
      }))
    : favoritePlaces;

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
            <Text style={styles.memberValue}>{profile ? (profile.savedPlaces ?? []).length : 3}</Text>
            <Text style={styles.memberLabel}>Favorites</Text>
          </View>
          <View style={styles.memberDivider} />
          <View style={styles.memberMetric}>
            <Text style={styles.memberValue}>{displayTaste.length}</Text>
            <Text style={styles.memberLabel}>Taste signals</Text>
          </View>
          <View style={styles.memberDivider} />
          <View style={styles.memberMetric}>
            <Text style={styles.memberValue}>{currentTrip ? 'Live' : 'Ready'}</Text>
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
              <Text style={styles.tripTitle}>{currentTrip?.destination ?? 'Amsterdam'}</Text>
            </View>
            <TouchableOpacity style={styles.editPill} activeOpacity={0.82}>
              <Ionicons name="pencil-outline" size={14} color={colors.teal} />
              <Text style={styles.editText}>Edit</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.tripMetaRow}>
            <InfoChip icon="calendar-outline" text={currentTrip?.dates ?? 'Oct 10 - Oct 14'} colors={colors} styles={styles} />
            <InfoChip icon="wallet-outline" text="Mid budget" colors={colors} styles={styles} />
          </View>

          <View style={styles.statRow}>
            <Stat icon="location-outline" value={`${currentTrip?.stops ?? 18}`} label="Stops" colors={colors} styles={styles} />
            <Stat icon="restaurant-outline" value={`${currentTrip?.foodPicks ?? 7}`} label="Food picks" colors={colors} styles={styles} />
            <Stat icon="walk-outline" value={`${(currentTrip?.averageWalkKm ?? 6.2).toFixed(1)} km`} label="Avg walk" colors={colors} styles={styles} />
          </View>
        </View>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Taste profile</Text>
          <Text style={styles.sectionAction}>Refine</Text>
        </View>
        <View style={styles.tasteGrid}>
          {displayTaste.map((item) => (
            <View key={item.label} style={styles.tasteCard}>
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

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Saved plans</Text>
          <Text style={styles.sectionAction}>View all</Text>
        </View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.savedRail}>
          {displaySavedPlans.map((plan) => (
            <TouchableOpacity key={plan.key} style={styles.savedCard} activeOpacity={0.88}>
              <Image source={{ uri: plan.image }} style={styles.savedImage} />
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
          ))}
        </ScrollView>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Favorites</Text>
          <Text style={styles.sectionAction}>Manage</Text>
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
  savedRail: { gap: spacing.md, paddingVertical: spacing.md },
  savedCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    overflow: 'hidden',
    width: 252,
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
