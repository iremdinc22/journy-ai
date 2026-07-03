import React from 'react';
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

import { colors, radius, spacing, typography } from '../theme/colors';
import type { RootStackParamList } from '../navigation/AppNavigator';

type Props = NativeStackScreenProps<RootStackParamList, 'PlaceDetail'>;

export default function PlaceDetailScreen({ navigation }: Props) {
  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="light-content" backgroundColor={colors.midnight} />

      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.content}>
        <ImageBackground
          source={{
            uri: 'https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=900&q=85',
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

            <View>
              <View style={styles.categoryPill}>
                <Ionicons name="cafe-outline" size={14} color={colors.teal} />
                <Text style={styles.categoryText}>Coffee pick</Text>
              </View>
              <Text style={styles.title}>Café de Flore</Text>
              <Text style={styles.location}>Saint-Germain - 450 m away</Text>
            </View>
          </LinearGradient>
        </ImageBackground>

        <View style={styles.sheet}>
          <View style={styles.statsRow}>
            <Stat icon="star" value="4.8" label="Rating" />
            <Stat icon="walk-outline" value="6 min" label="Walk" />
            <Stat icon="cash-outline" value="Mid" label="Budget" />
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Why it fits</Text>
            <Text style={styles.description}>
              Journy selected this place because coffee, local atmosphere and walkable breaks are
              high-priority preferences. It can be added to the same day as nearby museum and river routes.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Role in the plan</Text>
            <View style={styles.infoCard}>
              <Ionicons name="time-outline" size={20} color={colors.teal} />
              <View style={{ flex: 1 }}>
                <Text style={styles.infoTitle}>11:00 coffee break</Text>
                <Text style={styles.infoText}>Ideal as a short reset after the first landmark stop.</Text>
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

function Stat({
  icon,
  value,
  label,
}: {
  icon: React.ComponentProps<typeof Ionicons>['name'];
  value: string;
  label: string;
}) {
  return (
    <View style={styles.stat}>
      <Ionicons name={icon} size={18} color={colors.teal} />
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  content: { paddingBottom: spacing.xxl },
  hero: { height: 430 },
  heroImage: { resizeMode: 'cover' },
  overlay: {
    flex: 1,
    padding: spacing.lg,
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
    fontSize: typography.title,
    fontWeight: '900',
  },
  location: {
    color: 'rgba(255,255,255,0.78)',
    fontSize: typography.body,
    marginTop: spacing.xs,
    fontWeight: '700',
  },
  sheet: {
    backgroundColor: colors.ivory,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    marginTop: -34,
    padding: spacing.lg,
  },
  statsRow: {
    flexDirection: 'row',
    gap: spacing.sm,
  },
  stat: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.mist,
    alignItems: 'center',
    padding: spacing.md,
  },
  statValue: {
    color: colors.midnight,
    fontSize: typography.h3,
    fontWeight: '900',
    marginTop: spacing.xs,
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
