import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Image, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { exploreApi } from '../api/journyApi';
import type { PlaceResponse } from '../api/types';
import { useAppTheme } from '../theme/ThemeContext';
import { InlineError, InlineLoading } from '../components/StateViews';
import { useNavigation } from '@react-navigation/native';

type Category = 'For you' | 'Food' | 'Culture' | 'Coffee' | 'Free';

const categories: Category[] = ['For you', 'Food', 'Culture', 'Coffee', 'Free'];

type PreviewPlace = {
  title: string;
  city: string;
  type: string;
  rating: string;
  reason: string;
  image: string;
};

const placeGroups: Record<Category, PreviewPlace[]> = {
  'For you': [
    {
      title: 'De Pijp Market Loop',
      city: 'Amsterdam',
      type: 'Neighborhood',
      rating: '4.7',
      reason: 'Good for low-cost food, easy walking and local rhythm.',
      image: 'https://images.unsplash.com/photo-1584003564911-a7a321c84e1c?auto=format&fit=crop&w=700&q=85',
    },
    {
      title: 'Monti Aperitivo',
      city: 'Rome',
      type: 'Evening',
      rating: '4.9',
      reason: 'A polished dinner area that still feels local and walkable.',
      image: 'https://images.unsplash.com/photo-1552832230-c0197dd311b5?auto=format&fit=crop&w=700&q=85',
    },
  ],
  Food: [
    {
      title: 'Du Pain et des Idees',
      city: 'Paris',
      type: 'Bakery',
      rating: '4.8',
      reason: 'A classic bakery stop for a relaxed morning route.',
      image: 'https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=700&q=85',
    },
    {
      title: 'Testaccio Table',
      city: 'Rome',
      type: 'Trattoria',
      rating: '4.8',
      reason: 'Local dinner zone with simple Roman plates and an easy evening walk.',
      image: 'https://images.unsplash.com/photo-1533777857889-4be7c70b33f7?auto=format&fit=crop&w=700&q=85',
    },
    {
      title: 'Jordaan Cheese Stop',
      city: 'Amsterdam',
      type: 'Market',
      rating: '4.6',
      reason: 'A compact food detour that fits neatly between canal stops.',
      image: 'https://images.unsplash.com/photo-1452195100486-9cc805987862?auto=format&fit=crop&w=700&q=85',
    },
  ],
  Culture: [
    {
      title: 'Rijksmuseum Morning',
      city: 'Amsterdam',
      type: 'Museum',
      rating: '4.9',
      reason: 'Best as the first anchor of the day before the city gets busy.',
      image: 'https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=700&q=85',
    },
    {
      title: 'Montmartre Sketch Walk',
      city: 'Paris',
      type: 'Art walk',
      rating: '4.7',
      reason: 'Culture without rushing: studios, small streets and a late cafe pause.',
      image: 'https://images.unsplash.com/photo-1499856871958-5b9627545d1a?auto=format&fit=crop&w=700&q=85',
    },
    {
      title: 'Born Design Route',
      city: 'Barcelona',
      type: 'Gallery',
      rating: '4.6',
      reason: 'Independent galleries and design shops grouped into one walkable area.',
      image: 'https://images.unsplash.com/photo-1583422409516-2895a77efded?auto=format&fit=crop&w=700&q=85',
    },
  ],
  Coffee: [
    {
      title: 'Ten Belles',
      city: 'Paris',
      type: 'Coffee',
      rating: '4.7',
      reason: 'A small specialty stop that pairs well with a canal-side morning.',
      image: 'https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=700&q=85',
    },
    {
      title: 'Scandinavian Embassy',
      city: 'Amsterdam',
      type: 'Cafe',
      rating: '4.8',
      reason: 'Good for a slower breakfast before Museumplein.',
      image: 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=700&q=85',
    },
    {
      title: 'Sant Eustachio Pause',
      city: 'Rome',
      type: 'Espresso',
      rating: '4.6',
      reason: 'A short, central coffee break between historic stops.',
      image: 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=700&q=85',
    },
  ],
  Free: [
    {
      title: 'Seine Golden Hour',
      city: 'Paris',
      type: 'Walk',
      rating: '4.8',
      reason: 'A free sunset route that keeps the day calm after museums.',
      image: 'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=700&q=85',
    },
    {
      title: 'Vondelpark Reset',
      city: 'Amsterdam',
      type: 'Park',
      rating: '4.7',
      reason: 'A low-effort break when the plan needs breathing room.',
      image: 'https://images.unsplash.com/photo-1525968902-070804c45d6b?auto=format&fit=crop&w=700&q=85',
    },
    {
      title: 'Gothic Quarter Drift',
      city: 'Barcelona',
      type: 'Streets',
      rating: '4.6',
      reason: 'Free wandering through small lanes, plazas and local corners.',
      image: 'https://images.unsplash.com/photo-1539037116277-4db20889f2d4?auto=format&fit=crop&w=700&q=85',
    },
  ],
};

export default function ExploreScreen() {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const navigation = useNavigation<any>();
  const [activeCategory, setActiveCategory] = useState<Category>('For you');
  const [apiPlaces, setApiPlaces] = useState<PlaceResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const places = useMemo(() => apiPlaces ?? placeGroups[activeCategory], [activeCategory, apiPlaces]);

  const loadPlaces = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const response = await exploreApi.places(activeCategory);
      setApiPlaces(response);
    } catch {
      setApiPlaces(null);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [activeCategory]);

  useEffect(() => {
    loadPlaces();
  }, [loadPlaces]);

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <Text style={styles.eyebrow}>Explore</Text>
        <Text style={styles.title}>Local picks across every city.</Text>
        <Text style={styles.subtitle}>
          Recommendations adapt by destination, budget and the kind of day you want.
        </Text>

        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.categoryRail}
        >
          {categories.map((item) => (
            <TouchableOpacity
              key={item}
              style={[styles.category, item === activeCategory && styles.categoryActive]}
              activeOpacity={0.86}
              onPress={() => setActiveCategory(item)}
            >
              <Text style={[styles.categoryText, item === activeCategory && styles.categoryTextActive]}>
                {item}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        {loading ? <InlineLoading label="Finding local picks..." /> : null}
        {error ? (
          <InlineError
            title="Explore is using preview picks"
            description="Backend places could not be loaded. Retry to refresh recommendations."
            onRetry={loadPlaces}
          />
        ) : null}

        {places.map((place, index) => {
          const normalized = normalizePlace(place, activeCategory);
          return (
          <TouchableOpacity
            key={`${normalized.city}-${normalized.title}-${index}`}
            style={styles.card}
            activeOpacity={0.88}
            onPress={() => navigation.navigate('PlaceDetail', { place: toPlaceDetail(place, activeCategory) })}
          >
            <Image source={{ uri: normalized.image }} style={styles.image} />
            <View style={styles.body}>
              <View style={styles.metaRow}>
                <Text style={styles.type}>{normalized.city} - {normalized.type}</Text>
                <View style={styles.rating}>
                  <Ionicons name="star" size={13} color={colors.gold} />
                  <Text style={styles.ratingText}>{normalized.rating}</Text>
                </View>
              </View>
              <Text style={styles.placeTitle}>{normalized.title}</Text>
              <Text style={styles.description}>{normalized.reason}</Text>
            </View>
          </TouchableOpacity>
        )})}
      </ScrollView>
    </SafeAreaView>
  );
}

function normalizePlace(
  place: PlaceResponse | PreviewPlace,
  activeCategory: Category,
) {
  if ('name' in place) {
    return {
      title: place.name,
      city: place.city,
      type: formatCategory(place.category),
      rating: place.rating.toFixed(1),
      reason: place.description,
      image: place.imageUrl || fallbackImage(place.category || activeCategory, place.name),
    };
  }

  return place;
}

function toPlaceDetail(place: PlaceResponse | PreviewPlace, activeCategory: Category): PlaceResponse {
  if ('name' in place) {
    return {
      ...place,
      imageUrl: place.imageUrl || fallbackImage(place.category || activeCategory, place.name),
    };
  }

  return {
    id: `${place.city}-${place.title}`,
    name: place.title,
    city: place.city,
    category: place.type.toUpperCase(),
    description: place.reason,
    priceLevel: activeCategory === 'Free' ? 'Free' : 'Mid',
    rating: Number(place.rating),
    imageUrl: place.image,
    address: `${place.city} city center`,
    openingHours: activeCategory === 'Food' ? '12:00 - 22:30' : activeCategory === 'Coffee' ? '08:00 - 18:00' : 'Flexible route window',
    estimatedVisitMinutes: activeCategory === 'Food' ? 90 : activeCategory === 'Culture' ? 120 : 60,
    tags: `${place.type.toLowerCase()},walkable,local`,
  };
}

function formatCategory(category: string) {
  return category.toLowerCase().replaceAll('_', ' ');
}

const categoryFallbackImages: Record<'FOOD' | 'COFFEE' | 'CULTURE' | 'FREE', string[]> = {
  FOOD: [
    'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1533777857889-4be7c70b33f7?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1559339352-11d035aa65de?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=700&q=85',
  ],
  COFFEE: [
    'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1511920170033-f8396924c348?auto=format&fit=crop&w=700&q=85',
  ],
  CULTURE: [
    'https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1564399579883-451a5d44ec08?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1545987796-200677ee1011?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=700&q=85',
  ],
  FREE: [
    'https://images.unsplash.com/photo-1584003564911-a7a321c84e1c?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1525968902-070804c45d6b?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=700&q=85',
    'https://images.unsplash.com/photo-1539037116277-4db20889f2d4?auto=format&fit=crop&w=700&q=85',
  ],
};

function fallbackImage(category: string, seed: string) {
  const normalized = category.toUpperCase();
  if (normalized.includes('FOOD')) {
    return pickImage(categoryFallbackImages.FOOD, seed);
  }
  if (normalized.includes('COFFEE')) {
    return pickImage(categoryFallbackImages.COFFEE, seed);
  }
  if (normalized.includes('CULTURE')) {
    return pickImage(categoryFallbackImages.CULTURE, seed);
  }
  return pickImage(categoryFallbackImages.FREE, seed);
}

function pickImage(images: string[], seed: string) {
  return images[hashSeed(seed) % images.length];
}

function hashSeed(value: string) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0;
  }
  return hash;
}

type Theme = ReturnType<typeof useAppTheme>['theme'];

function createStyles({ colors, radius, spacing, typography }: Theme) {
  return StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  content: { padding: spacing.lg, paddingBottom: 132 },
  eyebrow: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    marginTop: spacing.md,
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
    lineHeight: 23,
    marginBottom: spacing.lg,
    marginTop: spacing.sm,
  },
  categoryRail: {
    gap: spacing.sm,
    paddingBottom: spacing.md,
  },
  category: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
  },
  categoryActive: {
    backgroundColor: colors.midnight,
    borderColor: colors.midnight,
  },
  categoryText: {
    color: colors.slate,
    fontSize: typography.small,
    fontWeight: '900',
  },
  categoryTextActive: {
    color: colors.surface,
  },
  card: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    marginBottom: spacing.md,
    overflow: 'hidden',
  },
  image: { height: 178, width: '100%' },
  body: { padding: spacing.md },
  metaRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  type: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  rating: { alignItems: 'center', flexDirection: 'row', gap: 4 },
  ratingText: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900' },
  placeTitle: {
    color: colors.midnight,
    fontSize: typography.h3,
    fontWeight: '900',
    marginTop: spacing.xs,
  },
  description: {
    color: colors.slate,
    fontSize: typography.small,
    lineHeight: 20,
    marginTop: spacing.xs,
  },
});
}
