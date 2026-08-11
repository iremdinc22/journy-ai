import React, { useCallback, useMemo, useState } from 'react';
import { Alert, Image, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { savedPlaceApi } from '../api/journyApi';
import type { SavedPlaceResponse } from '../api/types';
import type { RootStackParamList } from '../navigation/AppNavigator';
import { InlineEmpty, InlineError, InlineLoading } from '../components/StateViews';
import { useAppTheme } from '../theme/ThemeContext';

type Props = NativeStackScreenProps<RootStackParamList, 'SavedPlaces'>;
type IconName = React.ComponentProps<typeof Ionicons>['name'];

export default function SavedPlacesScreen({ navigation }: Props) {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const [places, setPlaces] = useState<SavedPlaceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [busyPlaceId, setBusyPlaceId] = useState<string | null>(null);
  const [activeCollection, setActiveCollection] = useState('all');

  const loadPlaces = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const response = await savedPlaceApi.list();
      setPlaces(response);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(useCallback(() => {
    loadPlaces();
  }, [loadPlaces]));

  const confirmRemove = (place: SavedPlaceResponse) => {
    Alert.alert(
      'Remove favorite?',
      `${place.name} will be removed from your saved places.`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Remove', style: 'destructive', onPress: () => removePlace(place) },
      ],
    );
  };

  const removePlace = async (place: SavedPlaceResponse) => {
    setBusyPlaceId(place.placeId);
    try {
      await savedPlaceApi.remove(place.placeId);
      setPlaces((current) => current.filter((item) => item.placeId !== place.placeId));
    } catch {
      Alert.alert('Could not remove favorite', 'Please check the backend connection and try again.');
    } finally {
      setBusyPlaceId(null);
    }
  };

  const collections = useMemo(() => buildCollections(places), [places]);
  const activePlaces = useMemo(() => {
    const active = collections.find((collection) => collection.id === activeCollection);
    if (!active || active.id === 'all') {
      return places;
    }
    return places.filter(active.filter);
  }, [activeCollection, collections, places]);

  const createCollection = () => {
    Alert.alert(
      'Create collection',
      'Custom collections are coming next. For now, Journy groups your saved places by city and taste automatically.',
    );
  };

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <TouchableOpacity
            style={styles.backButton}
            activeOpacity={0.86}
            onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('MainTabs', { screen: 'Profile' }))}
          >
            <Ionicons name="arrow-back" size={21} color={colors.midnight} />
          </TouchableOpacity>
          <TouchableOpacity style={styles.exploreButton} activeOpacity={0.86} onPress={() => navigation.navigate('MainTabs', { screen: 'Explore' })}>
            <Ionicons name="map-outline" size={17} color={colors.surface} />
            <Text style={styles.exploreButtonText}>Find places</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.hero}>
          <Text style={styles.eyebrow}>Favorites</Text>
          <Text style={styles.title}>Saved places.</Text>
          <Text style={styles.subtitle}>Organize the spots Journy should learn from and reuse in future trips.</Text>
        </View>

        <View style={styles.collectionHeader}>
          <Text style={styles.collectionTitle}>Collections</Text>
          <TouchableOpacity style={styles.createButton} activeOpacity={0.86} onPress={createCollection}>
            <Ionicons name="add" size={14} color={colors.teal} />
            <Text style={styles.createButtonText}>Create</Text>
          </TouchableOpacity>
        </View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.collectionRail}>
          {collections.map((collection) => {
            const active = collection.id === activeCollection;
            return (
              <TouchableOpacity
                key={collection.id}
                style={[styles.collectionCard, active && styles.collectionCardActive]}
                activeOpacity={0.86}
                onPress={() => setActiveCollection(collection.id)}
              >
                <View style={[styles.collectionIcon, active && styles.collectionIconActive]}>
                  <Ionicons name={collection.icon} size={17} color={active ? colors.surface : colors.teal} />
                </View>
                <Text style={[styles.collectionName, active && styles.collectionNameActive]}>{collection.label}</Text>
                <Text style={[styles.collectionCount, active && styles.collectionCountActive]}>{collection.count} places</Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>

        {loading ? <InlineLoading label="Loading saved places..." /> : null}
        {error ? (
          <InlineError
            title="Saved places could not load"
            description="Retry after the API connection is available."
            onRetry={loadPlaces}
          />
        ) : null}
        {!loading && !error && !places.length ? (
          <InlineEmpty
            title="No favorite places yet"
            description="Save places from Explore and they will appear here."
          />
        ) : null}

        <View style={styles.listHeader}>
          <Text style={styles.listTitle}>{collections.find((item) => item.id === activeCollection)?.label ?? 'All saved'}</Text>
          <Text style={styles.listMeta}>{activePlaces.length} saved</Text>
        </View>
        <View style={styles.placeList}>
          {activePlaces.map((place) => {
            const busy = busyPlaceId === place.placeId;
            return (
              <View key={place.id} style={styles.placeCard}>
                <Image source={{ uri: place.imageUrl }} style={styles.placeImage} />
                <View style={styles.placeBody}>
                  <View style={styles.placeTop}>
                    <View style={styles.placeCopy}>
                      <Text style={styles.placeTitle}>{place.name}</Text>
                      <Text style={styles.placeMeta}>{formatCategory(place.category)} - {place.city} - {place.rating.toFixed(1)}</Text>
                    </View>
                    <View style={styles.categoryIcon}>
                      <Ionicons name={mapPlaceIcon(place.category)} size={18} color={colors.teal} />
                    </View>
                  </View>
                  <Text style={styles.placeDescription} numberOfLines={2}>{place.description}</Text>
                  <View style={styles.metaFooter}>
                    <View style={styles.footerLeft}>
                      <View style={styles.infoPill}>
                        <Ionicons name="wallet-outline" size={13} color={colors.teal} />
                        <Text style={styles.infoPillText}>{place.priceLevel}</Text>
                      </View>
                      <View style={styles.savedState}>
                        <Ionicons name="heart" size={13} color={colors.teal} />
                        <Text style={styles.savedStateText}>Saved</Text>
                      </View>
                    </View>
                    <TouchableOpacity
                      accessibilityLabel={`Remove ${place.name} from favorites`}
                      style={[styles.removeButton, busy && styles.disabledButton]}
                      activeOpacity={0.78}
                      onPress={() => confirmRemove(place)}
                      disabled={busy}
                    >
                      <Ionicons name={busy ? 'hourglass-outline' : 'trash-outline'} size={16} color={colors.teal} />
                    </TouchableOpacity>
                  </View>
                </View>
              </View>
            );
          })}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

type SavedCollection = {
  id: string;
  label: string;
  count: number;
  icon: IconName;
  filter: (place: SavedPlaceResponse) => boolean;
};

function buildCollections(places: SavedPlaceResponse[]): SavedCollection[] {
  const base: SavedCollection[] = [
    { id: 'all', label: 'All saved', count: places.length, icon: 'heart-outline', filter: () => true },
    { id: 'coffee', label: 'Coffee spots', count: countCategory(places, 'coffee'), icon: 'cafe-outline', filter: (place) => place.category.toLowerCase().includes('coffee') },
    { id: 'food', label: 'Food list', count: countCategory(places, 'food'), icon: 'restaurant-outline', filter: (place) => place.category.toLowerCase().includes('food') },
    { id: 'culture', label: 'Culture', count: countCategory(places, 'culture'), icon: 'color-palette-outline', filter: (place) => place.category.toLowerCase().includes('culture') },
  ];
  const cities = [...new Set(places.map((place) => place.city).filter(Boolean))]
    .slice(0, 4)
    .map((city) => ({
      id: `city-${city}`,
      label: city,
      count: places.filter((place) => place.city === city).length,
      icon: 'map-outline' as IconName,
      filter: (place: SavedPlaceResponse) => place.city === city,
    }));
  return [...base, ...cities].filter((collection) => collection.id === 'all' || collection.count > 0);
}

function countCategory(places: SavedPlaceResponse[], value: string) {
  return places.filter((place) => place.category.toLowerCase().includes(value)).length;
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

function createStyles({ colors, radius, spacing, typography }: Theme) {
  return StyleSheet.create({
    safe: { flex: 1, backgroundColor: colors.ivory },
    content: { padding: spacing.lg, paddingBottom: 56 },
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
    exploreButton: {
      alignItems: 'center',
      backgroundColor: colors.midnight,
      borderRadius: radius.pill,
      flexDirection: 'row',
      gap: 6,
      minHeight: 42,
      paddingHorizontal: spacing.md,
    },
    exploreButtonText: { color: colors.surface, fontSize: typography.small, fontWeight: '900' },
    hero: { marginTop: spacing.xl },
    eyebrow: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
    title: { color: colors.midnight, fontSize: typography.title, fontWeight: '900', letterSpacing: 0, lineHeight: 42, marginTop: spacing.xs },
    subtitle: { color: colors.slate, fontSize: typography.body, fontWeight: '700', lineHeight: 24, marginTop: spacing.sm },
    collectionHeader: {
      alignItems: 'center',
      flexDirection: 'row',
      justifyContent: 'space-between',
      marginTop: spacing.xl,
    },
    collectionTitle: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
    createButton: {
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.pill,
      borderWidth: 1,
      flexDirection: 'row',
      gap: 4,
      paddingHorizontal: spacing.sm,
      paddingVertical: spacing.xs,
    },
    createButtonText: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900' },
    collectionRail: { gap: spacing.sm, paddingVertical: spacing.md },
    collectionCard: {
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.lg,
      borderWidth: 1,
      minHeight: 104,
      padding: spacing.sm,
      width: 132,
    },
    collectionCardActive: {
      backgroundColor: colors.midnight,
      borderColor: colors.midnight,
    },
    collectionIcon: {
      alignItems: 'center',
      backgroundColor: colors.fog,
      borderRadius: radius.md,
      height: 34,
      justifyContent: 'center',
      width: 34,
    },
    collectionIconActive: { backgroundColor: 'rgba(255,255,255,0.16)' },
    collectionName: { color: colors.midnight, fontSize: typography.small, fontWeight: '900', marginTop: spacing.sm },
    collectionNameActive: { color: colors.surface },
    collectionCount: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 3 },
    collectionCountActive: { color: 'rgba(255,255,255,0.72)' },
    listHeader: {
      alignItems: 'center',
      flexDirection: 'row',
      justifyContent: 'space-between',
      marginTop: spacing.sm,
    },
    listTitle: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
    listMeta: { color: colors.teal, fontSize: typography.small, fontWeight: '900' },
    placeList: { gap: spacing.md, marginTop: spacing.lg },
    placeCard: {
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.xl,
      borderWidth: 1,
      overflow: 'hidden',
    },
    placeImage: { height: 138, width: '100%' },
    placeBody: { padding: spacing.md },
    placeTop: { alignItems: 'flex-start', flexDirection: 'row' },
    placeCopy: { flex: 1, minWidth: 0 },
    placeTitle: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
    placeMeta: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', marginTop: 5, textTransform: 'capitalize' },
    categoryIcon: {
      alignItems: 'center',
      backgroundColor: colors.fog,
      borderRadius: radius.md,
      height: 40,
      justifyContent: 'center',
      marginLeft: spacing.sm,
      width: 40,
    },
    placeDescription: { color: colors.slate, fontSize: typography.small, fontWeight: '800', lineHeight: 20, marginTop: spacing.sm },
    metaFooter: {
      alignItems: 'center',
      borderColor: colors.mist,
      borderTopWidth: 1,
      flexDirection: 'row',
      justifyContent: 'space-between',
      marginTop: spacing.md,
      paddingTop: spacing.md,
    },
    footerLeft: { alignItems: 'center', flexDirection: 'row', flex: 1, gap: spacing.xs, minWidth: 0 },
    infoPill: {
      alignItems: 'center',
      backgroundColor: colors.fog,
      borderRadius: radius.pill,
      flexDirection: 'row',
      gap: 5,
      paddingHorizontal: spacing.sm,
      paddingVertical: spacing.xs,
    },
    infoPillText: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900' },
    savedState: {
      alignItems: 'center',
      backgroundColor: colors.surfaceWarm,
      borderRadius: radius.pill,
      flexDirection: 'row',
      gap: 4,
      paddingHorizontal: spacing.sm,
      paddingVertical: spacing.xs,
    },
    savedStateText: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900' },
    removeButton: {
      alignItems: 'center',
      backgroundColor: colors.fog,
      borderColor: colors.mist,
      borderRadius: radius.pill,
      borderWidth: 1,
      height: 36,
      justifyContent: 'center',
      width: 36,
    },
    disabledButton: { opacity: 0.55 },
  });
}
