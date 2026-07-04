import React, { useMemo } from 'react';
import {
  ImageBackground,
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
import { useAppTheme } from '../theme/ThemeContext';

type Props = NativeStackScreenProps<RootStackParamList, 'PlaceDetail'>;

export default function PlaceDetailScreen({ navigation, route }: Props) {
  const { theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const { place } = route.params;
  const categoryLabel = formatCategory(place.category);
  const role = roleForCategory(place.category);
  const walkTime = estimatedWalkTime(place.category);
  const tags = (place.tags ?? `${categoryLabel},walkable`)
    .split(',')
    .map((tag) => tag.trim())
    .filter(Boolean)
    .slice(0, 4);

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="light-content" backgroundColor={colors.midnight} />

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.content}>
        <ImageBackground
          source={{
            uri: place.imageUrl,
          }}
          style={styles.hero}
          imageStyle={styles.heroImage}
        >
          <LinearGradient colors={['rgba(23,32,51,0.1)', 'rgba(23,32,51,0.92)']} style={styles.overlay}>
            <TouchableOpacity
              style={styles.backButton}
              onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('MainTabs'))}
            >
              <Ionicons name="arrow-back" size={21} color={colors.midnight} />
            </TouchableOpacity>

            <View style={styles.heroCopy}>
              <View style={styles.categoryPill}>
                <Ionicons name={iconForCategory(place.category)} size={14} color={colors.teal} />
                <Text style={styles.categoryText}>{categoryLabel} pick</Text>
              </View>
              <Text style={styles.title}>{place.name}</Text>
              <Text style={styles.location}>{place.city} - curated for your route</Text>
            </View>
          </LinearGradient>
        </ImageBackground>

        <View style={styles.sheet}>
          <View style={styles.statsRow}>
            <Stat icon="star" value={place.rating.toFixed(1)} label="Rating" colors={colors} styles={styles} />
            <Stat icon="walk-outline" value={walkTime} label="Est. walk" colors={colors} styles={styles} />
            <Stat icon="time-outline" value={`${place.estimatedVisitMinutes ?? 60}m`} label="Duration" colors={colors} styles={styles} />
          </View>

          <View style={styles.detailGrid}>
            <DetailItem icon="location-outline" label="Address" value={place.address ?? `${place.city} city center`} colors={colors} styles={styles} />
            <DetailItem icon="cash-outline" label="Budget" value={place.priceLevel} colors={colors} styles={styles} />
            <DetailItem icon="time-outline" label="Hours" value={place.openingHours ?? 'Flexible route window'} colors={colors} styles={styles} />
          </View>

          <View style={styles.tagRow}>
            {tags.map((tag) => (
              <View key={tag} style={styles.tagChip}>
                <Text style={styles.tagText}>{tag}</Text>
              </View>
            ))}
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Why it fits</Text>
            <Text style={styles.description}>{place.description}</Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Role in the plan</Text>
            <View style={styles.infoCard}>
              <Ionicons name="time-outline" size={20} color={colors.teal} />
              <View style={{ flex: 1 }}>
                <Text style={styles.infoTitle}>{role.title}</Text>
                <Text style={styles.infoText}>{role.text}</Text>
              </View>
            </View>
          </View>

          <TouchableOpacity style={styles.primaryButton} activeOpacity={0.9}>
            <Text style={styles.primaryButtonText}>Add to today's plan</Text>
            <Ionicons name="add" size={20} color={colors.surface} />
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function formatCategory(category: string) {
  return category.toLowerCase().replaceAll('_', ' ');
}

function iconForCategory(category: string): React.ComponentProps<typeof Ionicons>['name'] {
  const value = category.toLowerCase();
  if (value.includes('coffee') || value.includes('cafe')) return 'cafe-outline';
  if (value.includes('food') || value.includes('restaurant')) return 'restaurant-outline';
  if (value.includes('culture') || value.includes('museum')) return 'color-palette-outline';
  if (value.includes('free')) return 'leaf-outline';
  return 'walk-outline';
}

function estimatedWalkTime(category: string) {
  const value = category.toLowerCase();
  if (value.includes('coffee')) return '6 min';
  if (value.includes('food')) return '9 min';
  if (value.includes('culture')) return '12 min';
  return '8 min';
}

function roleForCategory(category: string) {
  const value = category.toLowerCase();
  if (value.includes('coffee')) {
    return {
      title: 'Soft break window',
      text: 'Best used between two anchor stops so the day feels calm instead of packed.',
    };
  }
  if (value.includes('food')) {
    return {
      title: 'Local food stop',
      text: 'Works well near lunch or dinner so the route avoids crossing the city late in the day.',
    };
  }
  if (value.includes('culture')) {
    return {
      title: 'Anchor experience',
      text: 'Use it as the main stop of the day, then keep nearby cafes and walks flexible.',
    };
  }
  return {
    title: 'Flexible route moment',
    text: 'A low-pressure stop that keeps the plan walkable and easy to adjust.',
  };
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

function createStyles({ colors, radius, spacing, typography }: Theme) {
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
  backButton: {
    width: 46,
    height: 46,
    borderRadius: radius.lg,
    backgroundColor: colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
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
    color: colors.surface,
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
  primaryButton: {
    minHeight: 58,
    borderRadius: radius.lg,
    backgroundColor: colors.midnight,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.xl,
  },
  primaryButtonText: {
    color: colors.surface,
    fontSize: typography.body,
    fontWeight: '900',
  },
});
}
