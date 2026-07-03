import React, { useMemo } from 'react';
import { SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../navigation/AppNavigator';
import { useAppTheme } from '../theme/ThemeContext';

type Props = NativeStackScreenProps<RootStackParamList, 'DayRouteDetail'>;

export default function DayRouteDetailScreen({ navigation, route }: Props) {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme, isDark), [theme, isDark]);
  const { colors } = theme;
  const { day, city, area, stats, stops } = route.params;

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <TouchableOpacity
            style={styles.backButton}
            activeOpacity={0.86}
            onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('MainTabs'))}
          >
            <Ionicons name="arrow-back" size={21} color={colors.midnight} />
          </TouchableOpacity>
          <View style={styles.headerAction}>
            <Ionicons name="navigate-outline" size={16} color={colors.teal} />
            <Text style={styles.headerActionText}>Start</Text>
          </View>
        </View>

        <Text style={styles.eyebrow}>{day} - {city}</Text>
        <Text style={styles.title}>{area}</Text>
        <Text style={styles.subtitle}>{stats} grouped into a practical route with room for breaks.</Text>

        <View style={styles.mapCard}>
          <View style={styles.mapWater} />
          <View style={[styles.mapRoad, styles.roadOne]} />
          <View style={[styles.mapRoad, styles.roadTwo]} />
          <View style={[styles.mapRoad, styles.roadThree]} />
          <View style={styles.routeLineOne} />
          <View style={styles.routeLineTwo} />
          {stops.map((stop, index) => (
            <View
              key={stop}
              style={[
                styles.mapPin,
                index === 0 && styles.pinOne,
                index === 1 && styles.pinTwo,
                index === 2 && styles.pinThree,
                index === 3 && styles.pinFour,
              ]}
            >
              <Text style={styles.mapPinText}>{index + 1}</Text>
            </View>
          ))}
          <View style={styles.mapMeta}>
            <Text style={styles.mapMetaTitle}>Optimized route</Text>
            <Text style={styles.mapMetaText}>Nearby stops, balanced walking time</Text>
          </View>
        </View>

        <View style={styles.infoRow}>
          <Info icon="walk-outline" value={stats.split(' - ')[0]} label="Walking" colors={colors} styles={styles} />
          <Info icon="location-outline" value={`${stops.length}`} label="Stops" colors={colors} styles={styles} />
          <Info icon="time-outline" value="Easy" label="Pace" colors={colors} styles={styles} />
        </View>

        <Text style={styles.sectionTitle}>Day stops</Text>
        <View style={styles.stopList}>
          {stops.map((stop, index) => (
            <View key={stop} style={styles.stopRow}>
              <View style={styles.stopNumber}>
                <Text style={styles.stopNumberText}>{index + 1}</Text>
              </View>
              <View style={styles.stopCopy}>
                <Text style={styles.stopTitle}>{stop}</Text>
                <Text style={styles.stopMeta}>
                  {index === 0 ? 'Start here' : index === stops.length - 1 ? 'End the day nearby' : 'Short transfer'}
                </Text>
              </View>
            </View>
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

type Theme = ReturnType<typeof useAppTheme>['theme'];
type DetailStyles = ReturnType<typeof createStyles>;

function Info({
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
  styles: DetailStyles;
}) {
  return (
    <View style={styles.infoCard}>
      <Ionicons name={icon} size={18} color={colors.teal} />
      <Text style={styles.infoValue}>{value}</Text>
      <Text style={styles.infoLabel}>{label}</Text>
    </View>
  );
}

function createStyles({ colors, radius, spacing, typography }: Theme, isDark: boolean) {
  return StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  content: { padding: spacing.lg, paddingBottom: spacing.xxl },
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
    height: 44,
    justifyContent: 'center',
    width: 44,
  },
  headerAction: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 5,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  headerActionText: { color: colors.teal, fontSize: typography.small, fontWeight: '900' },
  eyebrow: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    marginTop: spacing.xl,
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
    fontWeight: '600',
    lineHeight: 23,
    marginTop: spacing.sm,
  },
  mapCard: {
    backgroundColor: colors.sand,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    height: 270,
    marginTop: spacing.xl,
    overflow: 'hidden',
    position: 'relative',
  },
  mapWater: {
    backgroundColor: colors.sage,
    borderRadius: 140,
    height: 180,
    left: -42,
    position: 'absolute',
    top: 78,
    transform: [{ rotate: '-14deg' }],
    width: 430,
  },
  mapRoad: {
    backgroundColor: isDark ? 'rgba(247,240,234,0.42)' : 'rgba(255,255,255,0.86)',
    borderRadius: radius.pill,
    height: 12,
    position: 'absolute',
  },
  roadOne: { left: -10, top: 64, transform: [{ rotate: '18deg' }], width: 330 },
  roadTwo: { left: 70, top: 138, transform: [{ rotate: '-30deg' }], width: 320 },
  roadThree: { left: -2, top: 214, transform: [{ rotate: '24deg' }], width: 260 },
  routeLineOne: {
    backgroundColor: colors.teal,
    borderRadius: radius.pill,
    height: 5,
    left: 72,
    position: 'absolute',
    top: 128,
    transform: [{ rotate: '28deg' }],
    width: 130,
  },
  routeLineTwo: {
    backgroundColor: colors.midnight,
    borderRadius: radius.pill,
    height: 5,
    left: 160,
    position: 'absolute',
    top: 172,
    transform: [{ rotate: '-24deg' }],
    width: 112,
  },
  mapPin: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.teal,
    borderRadius: radius.pill,
    borderWidth: 3,
    height: 38,
    justifyContent: 'center',
    position: 'absolute',
    width: 38,
  },
  pinOne: { left: 52, top: 92 },
  pinTwo: { left: 148, top: 136 },
  pinThree: { left: 238, top: 106 },
  pinFour: { left: 198, top: 206 },
  mapPinText: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900' },
  mapMeta: {
    backgroundColor: isDark ? 'rgba(49,46,45,0.94)' : 'rgba(255,255,255,0.94)',
    borderRadius: radius.md,
    left: spacing.md,
    padding: spacing.md,
    position: 'absolute',
    right: spacing.md,
    top: spacing.md,
  },
  mapMetaTitle: { color: colors.midnight, fontSize: typography.body, fontWeight: '900' },
  mapMetaText: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 3 },
  infoRow: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.md },
  infoCard: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flex: 1,
    padding: spacing.md,
  },
  infoValue: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900', marginTop: spacing.xs },
  infoLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 2 },
  sectionTitle: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900', marginTop: spacing.xl },
  stopList: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    marginTop: spacing.md,
    overflow: 'hidden',
  },
  stopRow: {
    alignItems: 'center',
    borderBottomColor: colors.mist,
    borderBottomWidth: 1,
    flexDirection: 'row',
    minHeight: 68,
    paddingHorizontal: spacing.md,
  },
  stopNumber: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    height: 34,
    justifyContent: 'center',
    width: 34,
  },
  stopNumberText: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900' },
  stopCopy: { flex: 1, marginLeft: spacing.md },
  stopTitle: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  stopMeta: { color: colors.slate, fontSize: typography.tiny, fontWeight: '700', marginTop: 3 },
});
}
