import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Image, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { exploreApi } from '../api/journyApi';
import { session } from '../api/session';
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

export default function ExploreScreen() {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const navigation = useNavigation<any>();
  const [activeCategory, setActiveCategory] = useState<Category>('For you');
  const [apiPlaces, setApiPlaces] = useState<PlaceResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const currentTrip = session.getCurrentTrip();
  const destination = currentTrip?.destination ?? 'your trip';
  const places = useMemo(() => apiPlaces ?? starterPreviewPlaces(destination, activeCategory), [activeCategory, apiPlaces, destination]);

  const loadPlaces = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      await session.restore();
      const city = session.getCurrentTrip()?.destination ?? currentTrip?.destination;
      const response = await exploreApi.places(activeCategory, city);
      setApiPlaces(response);
    } catch {
      setApiPlaces(null);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [activeCategory, currentTrip?.destination]);

  useEffect(() => {
    loadPlaces();
  }, [loadPlaces]);

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <Text style={styles.eyebrow}>Explore</Text>
        <Text style={styles.title}>Local picks in {destination}.</Text>
        <Text style={styles.subtitle}>
          Recommendations adapt by your current trip, budget and the kind of day you want.
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
            description={`Backend places could not be loaded. Showing ${destination} starter picks for now.`}
            onRetry={loadPlaces}
          />
        ) : null}

        {places.map((place, index) => {
          const normalized = normalizePlace(place, activeCategory);
          const reasons = whyPicked(normalized, currentTrip);
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
              <View style={styles.whyBlock}>
                <View style={styles.whyHeader}>
                  <Ionicons name="sparkles-outline" size={13} color={colors.teal} />
                  <Text style={styles.whyTitle}>Why Journy picked this</Text>
                </View>
                <Text style={styles.whyText}>{reasonSentence(reasons, currentTrip)}</Text>
                <View style={styles.reasonChips}>
                  {reasons.map((reason) => (
                    <View key={reason} style={styles.reasonChip}>
                      <Text style={styles.reasonChipText}>{reason}</Text>
                    </View>
                  ))}
                </View>
              </View>
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

function whyPicked(place: ReturnType<typeof normalizePlace>, trip?: ReturnType<typeof session.getCurrentTrip>) {
  const type = place.type.toUpperCase();
  const interests = trip?.interests.map((item) => item.toUpperCase()) ?? [];
  const chips: string[] = [];

  if ((type.includes('CULTURE') || type.includes('MUSEUM')) && (interests.includes('MUSEUMS') || interests.includes('CULTURE'))) {
    chips.push('Matches culture');
  } else if (type.includes('COFFEE') && interests.includes('COFFEE')) {
    chips.push('Matches coffee');
  } else if (type.includes('FOOD') && interests.includes('LOCAL_FOOD')) {
    chips.push('Matches food');
  } else if (type.includes('FREE') && interests.includes('FREE_ACTIVITIES')) {
    chips.push('Low-cost fit');
  } else if (interests.includes('WALKING')) {
    chips.push('Walkable route');
  } else {
    chips.push('Trip fit');
  }

  const detour = type.includes('FOOD') || type.includes('COFFEE') ? '+0.4 km detour' : '+0.6 km detour';
  chips.push(detour);

  if (trip?.budget) {
    chips.push(`${formatCategory(trip.budget)} budget`);
  } else {
    chips.push('Flexible budget');
  }

  return chips.slice(0, 3);
}

function reasonSentence(reasons: string[], trip?: ReturnType<typeof session.getCurrentTrip>) {
  if (!trip) {
    return 'This starter pick is shaped around the current city and can be added into a flexible route.';
  }
  return `You selected ${friendlyInterests(trip.interests)}. This pick keeps the route compact and fits your ${formatCategory(trip.budget)} budget.`;
}

function friendlyInterests(interests: string[]) {
  const labels = interests.slice(0, 2).map((interest) => formatCategory(interest));
  if (!labels.length) return 'balanced travel';
  if (labels.length === 1) return labels[0];
  return `${labels[0]} and ${labels[1]}`;
}

function starterPreviewPlaces(city: string, category: Category): PreviewPlace[] {
  const categoriesForPreview: Category[] = category === 'For you' ? ['Free', 'Coffee', 'Food', 'Culture'] : [category];
  return categoriesForPreview.flatMap((item) => {
    const count = category === 'For you' ? 1 : 4;
    return Array.from({ length: count }, (_, index) => starterPreviewPlace(city, item, index + 1));
  });
}

function starterPreviewPlace(city: string, category: Category, index: number): PreviewPlace {
  const type = category === 'For you' ? 'Local' : category;
  const title = `${city} ${starterTitle(category, index)}`;
  return {
    title,
    city,
    type,
    rating: (4.6 + (index % 3) * 0.1).toFixed(1),
    reason: starterReason(city, category),
    image: categoryImage(category, title),
  };
}

function starterTitle(category: Category, index: number) {
  if (category === 'Food') return `local food stop ${index}`;
  if (category === 'Coffee') return `coffee pause ${index}`;
  if (category === 'Culture') return `culture window ${index}`;
  if (category === 'Free') return `free city moment ${index}`;
  return `starter pick ${index}`;
}

function starterReason(city: string, category: Category) {
  if (category === 'Food') return `A local food candidate for your ${city} route.`;
  if (category === 'Coffee') return `A coffee break that can fit into your ${city} day.`;
  if (category === 'Culture') return `A culture anchor candidate for a stronger ${city} itinerary.`;
  if (category === 'Free') return `A low-cost ${city} pick for flexible pacing.`;
  return `A starter recommendation shaped around your current ${city} trip.`;
}

function categoryImage(category: Category, seed: string) {
  if (category === 'Food') return fallbackImage('FOOD', seed);
  if (category === 'Coffee') return fallbackImage('COFFEE', seed);
  if (category === 'Culture') return fallbackImage('CULTURE', seed);
  return fallbackImage('FREE', seed);
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
  whyBlock: {
    backgroundColor: colors.surfaceWarm,
    borderColor: colors.mist,
    borderRadius: radius.md,
    borderWidth: 1,
    marginTop: spacing.md,
    padding: spacing.sm,
  },
  whyHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 5,
  },
  whyTitle: {
    color: colors.midnight,
    fontSize: typography.tiny,
    fontWeight: '900',
  },
  whyText: {
    color: colors.slate,
    fontSize: 11,
    fontWeight: '800',
    lineHeight: 16,
    marginTop: spacing.xs,
  },
  reasonChips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.xs,
    marginTop: spacing.sm,
  },
  reasonChip: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    paddingHorizontal: spacing.sm,
    paddingVertical: 5,
  },
  reasonChipText: {
    color: colors.midnight,
    fontSize: 10,
    fontWeight: '900',
  },
});
}
