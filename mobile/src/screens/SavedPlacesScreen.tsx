import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Image, Modal, Pressable, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import { savedPlaceApi } from '../api/journyApi';
import type { SavedPlaceResponse } from '../api/types';
import type { RootStackParamList } from '../navigation/AppNavigator';
import { useLanguage, useTranslation } from '../i18n/LanguageContext';
import { InlineEmpty, InlineError, InlineLoading } from '../components/StateViews';
import { useAppTheme } from '../theme/ThemeContext';
import { placeImage } from '../utils/destinationVisuals';
import { localizeDynamicText } from '../utils/localizedDynamicText';

type Props = NativeStackScreenProps<RootStackParamList, 'SavedPlaces'>;
type IconName = React.ComponentProps<typeof Ionicons>['name'];
type CustomCollection = { id: string; label: string; placeIds: string[] };

const COLLECTIONS_STORAGE_KEY = 'journy.savedPlaceCollections';

export default function SavedPlacesScreen({ navigation }: Props) {
  const { isDark, theme } = useAppTheme();
  const { language } = useLanguage();
  const t = useTranslation();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const [places, setPlaces] = useState<SavedPlaceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [busyPlaceId, setBusyPlaceId] = useState<string | null>(null);
  const [activeCollection, setActiveCollection] = useState('all');
  const [customCollections, setCustomCollections] = useState<CustomCollection[]>([]);
  const [collectionName, setCollectionName] = useState('');
  const [collectionModalVisible, setCollectionModalVisible] = useState(false);
  const [collectionPickerPlace, setCollectionPickerPlace] = useState<SavedPlaceResponse | null>(null);

  useEffect(() => {
    AsyncStorage.getItem(COLLECTIONS_STORAGE_KEY)
      .then((stored) => {
        if (!stored) return;
        const parsed = JSON.parse(stored) as CustomCollection[];
        if (Array.isArray(parsed)) {
          setCustomCollections(parsed.filter((item) => item.id && item.label && Array.isArray(item.placeIds)));
        }
      })
      .catch(() => undefined);
  }, []);

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
      t('savedPlaces.removeTitle'),
      t('savedPlaces.removeMessage', { name: place.name }),
      [
        { text: t('common.cancel'), style: 'cancel' },
        { text: t('savedPlaces.remove'), style: 'destructive', onPress: () => removePlace(place) },
      ],
    );
  };

  const removePlace = async (place: SavedPlaceResponse) => {
    setBusyPlaceId(place.placeId);
    try {
      await savedPlaceApi.remove(place.placeId);
      setPlaces((current) => current.filter((item) => item.placeId !== place.placeId));
    } catch {
      Alert.alert(t('savedPlaces.removeErrorTitle'), t('savedPlaces.removeErrorMessage'));
    } finally {
      setBusyPlaceId(null);
    }
  };

  const persistCustomCollections = (next: CustomCollection[]) => {
    setCustomCollections(next);
    AsyncStorage.setItem(COLLECTIONS_STORAGE_KEY, JSON.stringify(next)).catch(() => undefined);
  };

  const collections = useMemo(() => buildCollections(places, customCollections, t), [customCollections, places, t]);
  const activePlaces = useMemo(() => {
    const active = collections.find((collection) => collection.id === activeCollection);
    if (!active || active.id === 'all') {
      return places;
    }
    return places.filter(active.filter);
  }, [activeCollection, collections, places]);

  const createCollection = () => {
    setCollectionName('');
    setCollectionPickerPlace(null);
    setCollectionModalVisible(true);
  };

  const saveCollection = () => {
    const label = collectionName.trim();
    if (!label) {
      return;
    }
    const id = `custom-${Date.now()}-${label.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
    const next = [...customCollections, { id, label, placeIds: collectionPickerPlace ? [collectionPickerPlace.placeId] : [] }];
    persistCustomCollections(next);
    setActiveCollection(id);
    setCollectionName('');
    setCollectionModalVisible(false);
    setCollectionPickerPlace(null);
  };

  const openCollectionPicker = (place: SavedPlaceResponse) => {
    setCollectionName('');
    setCollectionPickerPlace(place);
    setCollectionModalVisible(true);
  };

  const togglePlaceInCollection = (collectionId: string, place: SavedPlaceResponse) => {
    const next = customCollections.map((collection) => {
      if (collection.id !== collectionId) {
        return collection;
      }
      const alreadySaved = collection.placeIds.includes(place.placeId);
      return {
        ...collection,
        placeIds: alreadySaved
          ? collection.placeIds.filter((placeId) => placeId !== place.placeId)
          : [...collection.placeIds, place.placeId],
      };
    });
    persistCustomCollections(next);
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
            <Text style={styles.exploreButtonText}>{t('savedPlaces.findPlaces')}</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.hero}>
          <Text style={styles.eyebrow}>{t('savedPlaces.eyebrow')}</Text>
          <Text style={styles.title}>{t('savedPlaces.title')}</Text>
          <Text style={styles.subtitle}>{t('savedPlaces.subtitle')}</Text>
        </View>

        <View style={styles.collectionHeader}>
          <Text style={styles.collectionTitle}>{t('savedPlaces.collections')}</Text>
          <TouchableOpacity style={styles.createButton} activeOpacity={0.86} onPress={createCollection}>
            <Ionicons name="add" size={14} color={colors.teal} />
            <Text style={styles.createButtonText}>{t('savedPlaces.create')}</Text>
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
                <Text style={[styles.collectionCount, active && styles.collectionCountActive]}>{t('savedPlaces.places', { count: collection.count })}</Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>

        {loading ? <InlineLoading label={t('savedPlaces.loading')} /> : null}
        {error ? (
          <InlineError
            title={t('savedPlaces.errorTitle')}
            description={t('savedPlaces.errorDescription')}
            onRetry={loadPlaces}
          />
        ) : null}
        {!loading && !error && !places.length ? (
          <InlineEmpty
            title={t('savedPlaces.emptyTitle')}
            description={t('savedPlaces.emptyDescription')}
          />
        ) : null}

        <View style={styles.listHeader}>
          <Text style={styles.listTitle}>{collections.find((item) => item.id === activeCollection)?.label ?? t('savedPlaces.allSaved')}</Text>
          <Text style={styles.listMeta}>{t('savedPlaces.savedCount', { count: activePlaces.length })}</Text>
        </View>
        <View style={styles.placeList}>
          {!loading && !error && places.length > 0 && activePlaces.length === 0 ? (
            <InlineEmpty
              title={t('savedPlaces.collectionEmptyTitle')}
              description={t('savedPlaces.collectionEmptyDescription')}
            />
          ) : null}
          {activePlaces.map((place) => {
            const busy = busyPlaceId === place.placeId;
            return (
              <View key={place.id} style={styles.placeCard}>
                <Image source={{ uri: place.imageUrl || placeImage(place.city, place.category, place.name) }} style={styles.placeImage} />
                <View style={styles.placeBody}>
                  <View style={styles.placeTop}>
                    <View style={styles.placeCopy}>
                      <Text style={styles.placeTitle}>{place.name}</Text>
                      <Text style={styles.placeMeta}>{formatCategory(place.category, t)} - {place.city} - {place.rating.toFixed(1)}</Text>
                    </View>
                    <View style={styles.categoryIcon}>
                      <Ionicons name={mapPlaceIcon(place.category)} size={18} color={colors.teal} />
                    </View>
                  </View>
                  <Text style={styles.placeDescription} numberOfLines={2}>{localizeDynamicText(place.description, language)}</Text>
                  <View style={styles.metaFooter}>
                    <View style={styles.footerLeft}>
                      <View style={styles.infoPill}>
                        <Ionicons name="wallet-outline" size={13} color={colors.teal} />
                        <Text style={styles.infoPillText}>{formatPriceLevel(place.priceLevel, language)}</Text>
                      </View>
                      <View style={styles.savedState}>
                        <Ionicons name="heart" size={13} color={colors.teal} />
                        <Text style={styles.savedStateText}>{t('savedPlaces.saved')}</Text>
                      </View>
                    </View>
                    <View style={styles.footerActions}>
                      <TouchableOpacity
                        accessibilityLabel={t('savedPlaces.addToCollectionA11y', { name: place.name })}
                        style={styles.collectionActionButton}
                        activeOpacity={0.78}
                        onPress={() => openCollectionPicker(place)}
                      >
                        <Ionicons name="folder-open-outline" size={16} color={colors.teal} />
                      </TouchableOpacity>
                      <TouchableOpacity
                        accessibilityLabel={t('savedPlaces.removeA11y', { name: place.name })}
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
              </View>
            );
          })}
        </View>
      </ScrollView>

      <Modal visible={collectionModalVisible} transparent animationType="fade" onRequestClose={() => setCollectionModalVisible(false)}>
        <Pressable style={styles.modalBackdrop} onPress={() => setCollectionModalVisible(false)}>
          <Pressable style={styles.collectionSheet}>
            <View style={styles.sheetHandle} />
            <Text style={styles.sheetTitle}>{collectionPickerPlace ? t('savedPlaces.addToCollection') : t('savedPlaces.createCollection')}</Text>
            <Text style={styles.sheetSubtitle}>
              {collectionPickerPlace
                ? t('savedPlaces.addToCollectionSubtitle', { name: collectionPickerPlace.name })
                : t('savedPlaces.createCollectionSubtitle')}
            </Text>

            {collectionPickerPlace && customCollections.length ? (
              <View style={styles.collectionPickerList}>
                {customCollections.map((collection) => {
                  const selected = collection.placeIds.includes(collectionPickerPlace.placeId);
                  return (
                    <TouchableOpacity
                      key={collection.id}
                      style={[styles.collectionPickerRow, selected && styles.collectionPickerRowActive]}
                      activeOpacity={0.84}
                      onPress={() => togglePlaceInCollection(collection.id, collectionPickerPlace)}
                    >
                      <View style={[styles.collectionPickerIcon, selected && styles.collectionPickerIconActive]}>
                        <Ionicons name={selected ? 'checkmark' : 'folder-outline'} size={16} color={selected ? colors.surface : colors.teal} />
                      </View>
                      <View style={styles.collectionPickerCopy}>
                        <Text style={styles.collectionPickerTitle}>{collection.label}</Text>
                        <Text style={styles.collectionPickerMeta}>{t('savedPlaces.places', { count: collection.placeIds.length })}</Text>
                      </View>
                    </TouchableOpacity>
                  );
                })}
              </View>
            ) : null}

            <View style={styles.collectionInputShell}>
              <Ionicons name="folder-outline" size={16} color={colors.softMuted} />
              <TextInput
                value={collectionName}
                onChangeText={setCollectionName}
                placeholder={collectionPickerPlace ? t('savedPlaces.newCollectionName') : t('savedPlaces.collectionPlaceholder')}
                placeholderTextColor={colors.softMuted}
                style={styles.collectionInput}
              />
            </View>
            <TouchableOpacity
              style={[styles.sheetPrimaryButton, !collectionName.trim() && styles.sheetPrimaryButtonDisabled]}
              activeOpacity={0.86}
              onPress={saveCollection}
              disabled={!collectionName.trim()}
            >
              <Text style={styles.sheetPrimaryText}>{collectionPickerPlace ? t('savedPlaces.createAndAdd') : t('savedPlaces.createCollection')}</Text>
              <Ionicons name="add" size={17} color={colors.surface} />
            </TouchableOpacity>
          </Pressable>
        </Pressable>
      </Modal>
    </SafeAreaView>
  );
}

type SavedCollection = {
  id: string;
  label: string;
  count: number;
  icon: IconName;
  filter: (place: SavedPlaceResponse) => boolean;
  manual?: boolean;
};

type Translate = ReturnType<typeof useTranslation>;

function buildCollections(places: SavedPlaceResponse[], customCollections: CustomCollection[], t: Translate): SavedCollection[] {
  const base: SavedCollection[] = [
    { id: 'all', label: t('savedPlaces.allSaved'), count: places.length, icon: 'heart-outline', filter: () => true },
    { id: 'coffee', label: t('savedPlaces.coffeeSpots'), count: countCategory(places, 'coffee'), icon: 'cafe-outline', filter: (place) => place.category.toLowerCase().includes('coffee') },
    { id: 'food', label: t('savedPlaces.foodList'), count: countCategory(places, 'food'), icon: 'restaurant-outline', filter: (place) => place.category.toLowerCase().includes('food') },
    { id: 'culture', label: t('savedPlaces.culture'), count: countCategory(places, 'culture'), icon: 'color-palette-outline', filter: (place) => place.category.toLowerCase().includes('culture') },
  ];
  const cities: SavedCollection[] = [...new Set(places.map((place) => place.city).filter(Boolean))]
    .slice(0, 4)
    .map((city) => ({
      id: `city-${city}`,
      label: city,
      count: places.filter((place) => place.city === city).length,
      icon: 'map-outline' as IconName,
      filter: (place: SavedPlaceResponse) => place.city === city,
    }));
  const custom: SavedCollection[] = customCollections.map((collection) => ({
    id: collection.id,
    label: collection.label,
    count: places.filter((place) => collection.placeIds.includes(place.placeId)).length,
    icon: 'folder-outline' as IconName,
    filter: (place: SavedPlaceResponse) => collection.placeIds.includes(place.placeId),
    manual: true,
  }));
  return [...base, ...cities, ...custom].filter((collection) => collection.id === 'all' || collection.count > 0 || collection.manual);
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

function formatCategory(category: string, t?: Translate) {
  const value = category.toLowerCase();
  if (!t) return value.replace(/_/g, ' ');
  if (value.includes('coffee')) return t('setup.coffee');
  if (value.includes('food') || value.includes('restaurant')) return t('setup.localFood');
  if (value.includes('culture') || value.includes('museum')) return t('setup.museums');
  if (value.includes('walking')) return t('setup.walking');
  return value.replace(/_/g, ' ');
}

function formatPriceLevel(value: string, language: 'en' | 'tr') {
  if (language !== 'tr') return value;
  const normalized = value.toLowerCase();
  if (normalized.includes('free')) return 'Ücretsiz';
  if (normalized.includes('lean') || normalized.includes('low')) return 'Ekonomik';
  if (normalized.includes('mid') || normalized.includes('balanced')) return 'Orta';
  if (normalized.includes('comfort')) return 'Konfor';
  return value;
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
    footerActions: { alignItems: 'center', flexDirection: 'row', gap: spacing.xs, marginLeft: spacing.sm },
    collectionActionButton: {
      alignItems: 'center',
      backgroundColor: colors.surfaceWarm,
      borderColor: colors.mist,
      borderRadius: radius.pill,
      borderWidth: 1,
      height: 36,
      justifyContent: 'center',
      width: 36,
    },
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
    modalBackdrop: {
      backgroundColor: 'rgba(31, 42, 43, 0.34)',
      flex: 1,
      justifyContent: 'flex-end',
      padding: spacing.md,
    },
    collectionSheet: {
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.xl,
      borderWidth: 1,
      padding: spacing.lg,
    },
    sheetHandle: {
      alignSelf: 'center',
      backgroundColor: colors.mist,
      borderRadius: radius.pill,
      height: 5,
      marginBottom: spacing.md,
      width: 54,
    },
    sheetTitle: { color: colors.midnight, fontSize: typography.h2, fontWeight: '900' },
    sheetSubtitle: { color: colors.slate, fontSize: typography.small, fontWeight: '800', lineHeight: 21, marginTop: spacing.xs },
    collectionPickerList: { gap: spacing.sm, marginTop: spacing.md },
    collectionPickerRow: {
      alignItems: 'center',
      backgroundColor: colors.ivory,
      borderColor: colors.mist,
      borderRadius: radius.lg,
      borderWidth: 1,
      flexDirection: 'row',
      gap: spacing.sm,
      padding: spacing.sm,
    },
    collectionPickerRowActive: { borderColor: colors.teal },
    collectionPickerIcon: {
      alignItems: 'center',
      backgroundColor: colors.fog,
      borderRadius: radius.md,
      height: 34,
      justifyContent: 'center',
      width: 34,
    },
    collectionPickerIconActive: { backgroundColor: colors.midnight },
    collectionPickerCopy: { flex: 1, minWidth: 0 },
    collectionPickerTitle: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
    collectionPickerMeta: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 2 },
    collectionInputShell: {
      alignItems: 'center',
      backgroundColor: colors.fog,
      borderRadius: radius.lg,
      flexDirection: 'row',
      gap: spacing.xs,
      minHeight: 54,
      marginTop: spacing.md,
      paddingHorizontal: spacing.md,
    },
    collectionInput: { color: colors.midnight, flex: 1, fontSize: typography.body, fontWeight: '900' },
    sheetPrimaryButton: {
      alignItems: 'center',
      backgroundColor: colors.midnight,
      borderRadius: radius.pill,
      flexDirection: 'row',
      gap: spacing.xs,
      justifyContent: 'center',
      minHeight: 54,
      marginTop: spacing.md,
      paddingHorizontal: spacing.lg,
    },
    sheetPrimaryButtonDisabled: { opacity: 0.45 },
    sheetPrimaryText: { color: colors.surface, fontSize: typography.body, fontWeight: '900' },
  });
}
