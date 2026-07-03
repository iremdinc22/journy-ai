import React, { useMemo, useState } from 'react';
import { Image, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, radius, spacing, typography } from '../theme/colors';

type Category = 'For you' | 'Food' | 'Culture' | 'Coffee' | 'Free';

const categories: Category[] = ['For you', 'Food', 'Culture', 'Coffee', 'Free'];

const placeGroups: Record<Category, Array<{
  title: string;
  city: string;
  type: string;
  rating: string;
  reason: string;
  image: string;
}>> = {
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
  const [activeCategory, setActiveCategory] = useState<Category>('For you');
  const places = useMemo(() => placeGroups[activeCategory], [activeCategory]);

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="dark-content" backgroundColor={colors.ivory} />
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

        {places.map((place) => (
          <View key={place.title} style={styles.card}>
            <Image source={{ uri: place.image }} style={styles.image} />
            <View style={styles.body}>
              <View style={styles.metaRow}>
                <Text style={styles.type}>{place.city} - {place.type}</Text>
                <View style={styles.rating}>
                  <Ionicons name="star" size={13} color={colors.gold} />
                  <Text style={styles.ratingText}>{place.rating}</Text>
                </View>
              </View>
              <Text style={styles.placeTitle}>{place.title}</Text>
              <Text style={styles.description}>{place.reason}</Text>
            </View>
          </View>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
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
