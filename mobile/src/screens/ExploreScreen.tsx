import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Image, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { exploreApi } from '../api/journyApi';
import { session } from '../api/session';
import type { PlaceResponse } from '../api/types';
import { useLanguage, useTranslation } from '../i18n/LanguageContext';
import { useAppTheme } from '../theme/ThemeContext';
import { InlineError, InlineLoading } from '../components/StateViews';
import { useNavigation } from '@react-navigation/native';
import { cityImage, placeImage } from '../utils/destinationVisuals';
import { localizeDynamicText } from '../utils/localizedDynamicText';

type Category = 'For you' | 'Food' | 'Culture' | 'Coffee' | 'Free';
type Translate = ReturnType<typeof useTranslation>;

const categories: Category[] = ['For you', 'Food', 'Culture', 'Coffee', 'Free'];

function categoryLabel(category: Category, t: Translate) {
  if (category === 'For you') return t('explore.forYou');
  if (category === 'Food') return t('explore.food');
  if (category === 'Culture') return t('explore.culture');
  if (category === 'Coffee') return t('explore.coffee');
  return t('explore.free');
}

function sourceLabel(label: string | undefined, t: Translate) {
  if (label === 'Real place') return t('explore.realPlace');
  if (label === 'Curated seed') return t('explore.curatedSeed');
  return t('explore.starterPick');
}

type PreviewPlace = {
  title: string;
  city: string;
  type: string;
  rating: string;
  reason: string;
  image: string;
  sourceLabel?: string;
};

export default function ExploreScreen() {
  const { isDark, theme } = useAppTheme();
  const { language } = useLanguage();
  const t = useTranslation();
  const styles = useMemo(() => createStyles(theme, isDark), [isDark, theme]);
  const { colors } = theme;
  const navigation = useNavigation<any>();
  const [activeCategory, setActiveCategory] = useState<Category>('For you');
  const [apiPlaces, setApiPlaces] = useState<PlaceResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const currentTrip = session.getCurrentTrip();
  const destination = currentTrip?.destination ?? t('home.yourTrip').toLowerCase();
  const places = useMemo(() => apiPlaces ?? starterPreviewPlaces(destination, activeCategory, language), [activeCategory, apiPlaces, destination, language]);

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
        <Text style={styles.eyebrow}>{t('explore.eyebrow')}</Text>
        <Text style={styles.title}>{t('explore.title', { destination })}</Text>
        <Text style={styles.subtitle}>{t('explore.subtitle')}</Text>

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
                {categoryLabel(item, t)}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        {loading ? <InlineLoading label={t('explore.loading')} /> : null}
        {error ? (
          <InlineError
            title={t('explore.previewTitle')}
            description={t('explore.previewDescription', { destination })}
            onRetry={loadPlaces}
          />
        ) : null}

        {places.map((place, index) => {
          const normalized = normalizePlace(place, activeCategory, language);
          const reasons = whyPicked(normalized, currentTrip, language);
          return (
          <TouchableOpacity
            key={`${normalized.city}-${normalized.title}-${index}`}
            style={styles.card}
            activeOpacity={0.88}
            onPress={() => navigation.navigate('PlaceDetail', { place: toPlaceDetail(place, activeCategory, language) })}
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
              <View style={styles.sourcePill}>
                <Ionicons name={normalized.sourceLabel === 'Real place' ? 'checkmark-circle' : 'sparkles-outline'} size={12} color={colors.teal} />
                <Text style={styles.sourcePillText}>{sourceLabel(normalized.sourceLabel, t)}</Text>
              </View>
              <Text style={styles.description}>{localizeDynamicText(normalized.reason, language)}</Text>
              <View style={styles.whyBlock}>
                <View style={styles.whyHeader}>
                  <Ionicons name="sparkles-outline" size={13} color={colors.teal} />
                  <Text style={styles.whyTitle}>{t('explore.goodFit')}</Text>
                </View>
                <Text style={styles.whyText}>{reasonSentence(reasons, currentTrip, language)}</Text>
                <View style={styles.reasonChips}>
                  {reasons.slice(0, 2).map((reason) => (
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

function normalizePlace(place: PlaceResponse | PreviewPlace, activeCategory: Category, language: 'en' | 'tr') {
  if ('name' in place) {
    return {
      title: localizeDynamicText(place.name, language),
      city: place.city,
      type: formatCategory(place.category, language),
      rating: place.rating.toFixed(1),
      reason: place.description,
      image: place.imageUrl || fallbackImage(place.city, place.category || activeCategory, place.name),
      sourceLabel: place.provider && place.provider !== 'seed' && place.provider !== 'starter' ? 'Real place' : 'Curated seed',
    };
  }

  return place;
}

function whyPicked(place: ReturnType<typeof normalizePlace>, trip: ReturnType<typeof session.getCurrentTrip> | undefined, language: 'en' | 'tr') {
  const type = place.type.toUpperCase();
  const interests = trip?.interests.map((item) => item.toUpperCase()) ?? [];
  const chips: string[] = [];

  if ((type.includes('CULTURE') || type.includes('MUSEUM')) && (interests.includes('MUSEUMS') || interests.includes('CULTURE'))) {
    chips.push(language === 'tr' ? 'Kültür tercihine uyuyor' : 'Matches culture');
  } else if (type.includes('COFFEE') && interests.includes('COFFEE')) {
    chips.push(language === 'tr' ? 'Kahve tercihine uyuyor' : 'Matches coffee');
  } else if (type.includes('FOOD') && interests.includes('LOCAL_FOOD')) {
    chips.push(language === 'tr' ? 'Yemek tercihine uyuyor' : 'Matches food');
  } else if (type.includes('FREE') && interests.includes('FREE_ACTIVITIES')) {
    chips.push(language === 'tr' ? 'Bütçeye uygun' : 'Low-cost fit');
  } else if (interests.includes('WALKING')) {
    chips.push(language === 'tr' ? 'Yürünebilir rota' : 'Walkable route');
  } else {
    chips.push(language === 'tr' ? 'Seyahate uygun' : 'Trip fit');
  }

  const detour = type.includes('FOOD') || type.includes('COFFEE')
    ? (language === 'tr' ? '+0.4 km sapma' : '+0.4 km detour')
    : (language === 'tr' ? '+0.6 km sapma' : '+0.6 km detour');
  chips.push(detour);

  if (trip?.budget) {
    chips.push(language === 'tr' ? `${formatCategory(trip.budget, language)} bütçe` : `${formatCategory(trip.budget)} budget`);
  } else {
    chips.push(language === 'tr' ? 'Esnek bütçe' : 'Flexible budget');
  }

  return chips.slice(0, 3);
}

function reasonSentence(reasons: string[], trip: ReturnType<typeof session.getCurrentTrip> | undefined, language: 'en' | 'tr') {
  if (!trip) {
    return language === 'tr'
      ? 'Bu başlangıç önerisi seçili şehre göre hazırlandı ve esnek bir rotaya eklenebilir.'
      : 'This starter pick is shaped around the current city and can be added into a flexible route.';
  }
  return language === 'tr'
    ? `${friendlyInterests(trip.interests, language)} seçtin. Bu öneri rotayı kompakt tutar ve ${formatCategory(trip.budget, language)} bütçene uyar.`
    : `You selected ${friendlyInterests(trip.interests)}. This pick keeps the route compact and fits your ${formatCategory(trip.budget)} budget.`;
}

function friendlyInterests(interests: string[], language: 'en' | 'tr' = 'en') {
  const labels = interests.slice(0, 2).map((interest) => formatCategory(interest, language));
  if (!labels.length) return language === 'tr' ? 'dengeli seyahat' : 'balanced travel';
  if (labels.length === 1) return labels[0];
  return language === 'tr' ? `${labels[0]} ve ${labels[1]}` : `${labels[0]} and ${labels[1]}`;
}

function starterPreviewPlaces(city: string, category: Category, language: 'en' | 'tr'): PreviewPlace[] {
  const categoriesForPreview: Category[] = category === 'For you' ? ['Free', 'Coffee', 'Food', 'Culture'] : [category];
  return categoriesForPreview.flatMap((item) => {
    const count = category === 'For you' ? 1 : 4;
    return Array.from({ length: count }, (_, index) => starterPreviewPlace(city, item, index + 1, language));
  });
}

function starterPreviewPlace(city: string, category: Category, index: number, language: 'en' | 'tr'): PreviewPlace {
  const type = category === 'For you' ? (language === 'tr' ? 'Yerel' : 'Local') : formatCategory(category, language);
  const title = `${city} ${starterTitle(category, index)}`;
  return {
    title: localizeDynamicText(title, language),
    city,
    type,
    rating: (4.6 + (index % 3) * 0.1).toFixed(1),
    reason: starterReason(city, category),
    image: categoryImage(city, category, title),
    sourceLabel: 'Starter pick',
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

function categoryImage(city: string, category: Category, seed: string) {
  return placeImage(city, category, seed);
}

function toPlaceDetail(place: PlaceResponse | PreviewPlace, activeCategory: Category, language: 'en' | 'tr'): PlaceResponse {
  if ('name' in place) {
    return {
      ...place,
      name: localizeDynamicText(place.name, language),
      description: localizeDynamicText(place.description, language),
      imageUrl: place.imageUrl || fallbackImage(place.city, place.category || activeCategory, place.name),
    };
  }

  return {
    id: `${place.city}-${place.title}`,
    name: place.title,
    city: place.city,
    category: place.type.toUpperCase(),
    description: localizeDynamicText(place.reason, language),
    priceLevel: activeCategory === 'Free' ? (language === 'tr' ? 'Ücretsiz' : 'Free') : (language === 'tr' ? 'Orta' : 'Mid'),
    rating: Number(place.rating),
    imageUrl: place.image,
    address: language === 'tr' ? `${place.city} şehir merkezi` : `${place.city} city center`,
    openingHours: activeCategory === 'Food' ? '12:00 - 22:30' : activeCategory === 'Coffee' ? '08:00 - 18:00' : 'Flexible route window',
    estimatedVisitMinutes: activeCategory === 'Food' ? 90 : activeCategory === 'Culture' ? 120 : 60,
    tags: language === 'tr' ? `${place.type.toLowerCase()},yürünebilir,yerel` : `${place.type.toLowerCase()},walkable,local`,
    provider: 'starter',
  };
}

function formatCategory(category: string, language: 'en' | 'tr' = 'en') {
  const value = category.toLowerCase().replaceAll('_', ' ');
  if (language !== 'tr') return value;
  if (value.includes('local food') || value.includes('food')) return 'yemek';
  if (value.includes('coffee')) return 'kahve';
  if (value.includes('museum') || value.includes('culture')) return 'kültür';
  if (value.includes('walking')) return 'yürüyüş';
  if (value.includes('free')) return 'ücretsiz';
  if (value.includes('lean')) return 'ekonomik';
  if (value.includes('comfort')) return 'konfor';
  if (value.includes('balanced')) return 'dengeli';
  return value;
}

function fallbackImage(city: string | undefined, category: string, seed: string) {
  return placeImage(city, category, seed) || cityImage(city);
}

type Theme = ReturnType<typeof useAppTheme>['theme'];

function createStyles({ colors, radius, spacing, typography }: Theme, isDark: boolean) {
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
    backgroundColor: isDark ? colors.teal : colors.midnight,
    borderColor: isDark ? colors.teal : colors.midnight,
  },
  categoryText: {
    color: colors.slate,
    fontSize: typography.small,
    fontWeight: '900',
  },
  categoryTextActive: {
    color: isDark ? colors.ivory : colors.surface,
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
  sourcePill: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    flexDirection: 'row',
    gap: 4,
    marginTop: spacing.xs,
    paddingHorizontal: spacing.sm,
    paddingVertical: 5,
  },
  sourcePillText: { color: colors.teal, fontSize: 10, fontWeight: '900', textTransform: 'uppercase' },
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
