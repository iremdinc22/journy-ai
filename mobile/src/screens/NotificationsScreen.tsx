import React, { useEffect, useMemo, useState } from 'react';
import { SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../navigation/AppNavigator';
import { notificationApi } from '../api/journyApi';
import type { NotificationResponse } from '../api/types';
import { useTranslation } from '../i18n/LanguageContext';
import { useAppTheme } from '../theme/ThemeContext';
import { InlineError, InlineLoading } from '../components/StateViews';

type Props = NativeStackScreenProps<RootStackParamList, 'Notifications'>;
type IconName = React.ComponentProps<typeof Ionicons>['name'];
type Filter = 'All' | 'Route' | 'Food' | 'Weather';

const filters: Filter[] = ['All', 'Route', 'Food', 'Weather'];

function fallbackNotifications(t: Translate): Array<{
  title: string;
  body: string;
  time: string;
  icon: IconName;
  type: Exclude<Filter, 'All'>;
  unread?: boolean;
}> {
  return [
    {
      title: t('notifications.rainTitle'),
      body: t('notifications.rainBody'),
      time: t('notifications.minAgo', { count: 12 }),
      icon: 'rainy-outline',
      type: 'Weather',
      unread: true,
    },
    {
      title: t('notifications.dinnerTitle'),
      body: t('notifications.dinnerBody'),
      time: t('notifications.hourAgo', { count: 1 }),
      icon: 'restaurant-outline',
      type: 'Food',
      unread: true,
    },
    {
      title: t('notifications.walkTitle'),
      body: t('notifications.walkBody'),
      time: t('notifications.yesterday'),
      icon: 'walk-outline',
      type: 'Route',
    },
  ];
}

export default function NotificationsScreen({ navigation }: Props) {
  const { isDark, theme } = useAppTheme();
  const t = useTranslation();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const [activeFilter, setActiveFilter] = useState<Filter>('All');
  const [apiNotifications, setApiNotifications] = useState<NotificationResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const displayNotifications = useMemo(
    () => apiNotifications?.map((item) => normalizeNotification(item, t)) ?? fallbackNotifications(t),
    [apiNotifications, t],
  );
  const visibleNotifications =
    activeFilter === 'All' ? displayNotifications : displayNotifications.filter((item) => item.type === activeFilter);
  const unreadCount = displayNotifications.filter((item) => item.unread).length;

  useEffect(() => {
    let mounted = true;

    const load = async () => {
      setLoading(true);
      setError(false);
      try {
        const response = await notificationApi.list();
        if (mounted) {
          setApiNotifications(response);
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
  }, []);

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.topBar}>
          <TouchableOpacity
            style={styles.iconButton}
            activeOpacity={0.86}
            onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('MainTabs'))}
          >
            <Ionicons name="arrow-back" size={21} color={colors.midnight} />
          </TouchableOpacity>
          <TouchableOpacity style={styles.markButton} activeOpacity={0.86}>
            <Ionicons name="checkmark-done-outline" size={16} color={colors.teal} />
            <Text style={styles.markText}>{t('notifications.markRead')}</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.headerCard}>
          <View style={styles.headerIcon}>
            <Ionicons name="notifications-outline" size={22} color={colors.surface} />
          </View>
          <Text style={styles.eyebrow}>{t('notifications.eyebrow')}</Text>
          <Text style={styles.title}>{t('notifications.title')}</Text>
          <Text style={styles.subtitle}>{t('notifications.subtitle')}</Text>

          <View style={styles.summaryRow}>
            <SummaryItem value={`${unreadCount}`} label={t('notifications.new')} styles={styles} />
            <View style={styles.summaryDivider} />
            <SummaryItem value="0" label={t('notifications.urgent')} styles={styles} />
            <View style={styles.summaryDivider} />
            <SummaryItem value={`${displayNotifications.length}`} label={t('notifications.today')} styles={styles} />
          </View>
        </View>

        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterRail}>
          {filters.map((item) => (
            <TouchableOpacity
              key={item}
              style={[styles.filterChip, item === activeFilter && styles.filterChipActive]}
              activeOpacity={0.86}
              onPress={() => setActiveFilter(item)}
            >
              <Text style={[styles.filterText, item === activeFilter && styles.filterTextActive]}>{filterLabel(item, t)}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        {loading ? <InlineLoading label={t('notifications.loading')} /> : null}
        {error ? (
          <InlineError
            title={t('notifications.previewTitle')}
            description={t('notifications.previewDescription')}
            onRetry={() => {
              setApiNotifications(null);
              setLoading(true);
              setError(false);
              notificationApi.list()
                .then(setApiNotifications)
                .catch(() => setError(true))
                .finally(() => setLoading(false));
            }}
          />
        ) : null}

        <View style={styles.stack}>
          {visibleNotifications.map((item) => (
            <TouchableOpacity key={item.title} style={styles.notificationCard} activeOpacity={0.88}>
              <View style={[styles.itemIcon, item.unread && styles.itemIconActive]}>
                <Ionicons name={item.icon} size={19} color={item.unread ? colors.surface : colors.teal} />
              </View>

              <View style={styles.itemCopy}>
                <View style={styles.itemTop}>
                  <Text style={styles.itemType}>{filterLabel(item.type, t)}</Text>
                  <Text style={styles.itemTime}>{item.time}</Text>
                </View>
                <Text style={styles.itemTitle}>{item.title}</Text>
                <Text style={styles.itemBody}>{item.body}</Text>
              </View>

              {item.unread ? <View style={styles.unreadDot} /> : null}
            </TouchableOpacity>
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

type Translate = ReturnType<typeof useTranslation>;

function normalizeNotification(item: NotificationResponse, t: Translate) {
  const type = normalizeType(item.type);
  return {
    title: item.title,
    body: item.message,
    time: formatTime(item.createdAt, t),
    icon: iconForType(type),
    type,
    unread: item.unread,
  };
}

function normalizeType(type: string): Exclude<Filter, 'All'> {
  const value = type.toLowerCase();
  if (value.includes('food')) return 'Food';
  if (value.includes('weather')) return 'Weather';
  return 'Route';
}

function iconForType(type: Exclude<Filter, 'All'>): IconName {
  if (type === 'Food') return 'restaurant-outline';
  if (type === 'Weather') return 'rainy-outline';
  return 'walk-outline';
}

function filterLabel(value: Filter, t: Translate) {
  if (value === 'Food') return t('notifications.food');
  if (value === 'Weather') return t('notifications.weather');
  if (value === 'Route') return t('notifications.route');
  return t('notifications.all');
}

function formatTime(value: string, t: Translate) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return t('notifications.now');
  }

  const minutes = Math.max(1, Math.round((Date.now() - date.getTime()) / 60000));
  if (minutes < 60) return t('notifications.minAgo', { count: minutes });
  const hours = Math.round(minutes / 60);
  if (hours < 24) return t('notifications.hourAgo', { count: hours });
  return t('notifications.dayAgo', { count: Math.round(hours / 24) });
}

type Theme = ReturnType<typeof useAppTheme>['theme'];
type NotificationStyles = ReturnType<typeof createStyles>;

function SummaryItem({ value, label, styles }: { value: string; label: string; styles: NotificationStyles }) {
  return (
    <View style={styles.summaryItem}>
      <Text style={styles.summaryValue}>{value}</Text>
      <Text style={styles.summaryLabel}>{label}</Text>
    </View>
  );
}

function createStyles({ colors, radius, spacing, typography }: Theme) {
  return StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  content: { padding: spacing.lg, paddingBottom: spacing.xxl },
  topBar: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing.md,
  },
  iconButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.md,
    borderWidth: 1,
    height: 44,
    justifyContent: 'center',
    width: 44,
  },
  markButton: {
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
  markText: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  headerCard: {
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
  headerIcon: {
    alignItems: 'center',
    backgroundColor: colors.teal,
    borderRadius: radius.md,
    height: 46,
    justifyContent: 'center',
    marginBottom: spacing.md,
    width: 46,
  },
  eyebrow: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  title: {
    color: colors.midnight,
    fontSize: typography.h1,
    fontWeight: '900',
    letterSpacing: 0,
    lineHeight: 35,
    marginTop: spacing.xs,
  },
  subtitle: {
    color: colors.slate,
    fontSize: typography.body,
    fontWeight: '700',
    lineHeight: 23,
    marginTop: spacing.sm,
  },
  summaryRow: {
    alignItems: 'center',
    backgroundColor: colors.lilac,
    borderRadius: radius.lg,
    flexDirection: 'row',
    marginTop: spacing.lg,
    padding: spacing.md,
  },
  summaryItem: { alignItems: 'center', flex: 1 },
  summaryValue: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
  summaryLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900', marginTop: 2 },
  summaryDivider: { backgroundColor: colors.mist, height: 34, width: 1 },
  filterRail: {
    gap: spacing.sm,
    paddingVertical: spacing.lg,
  },
  filterChip: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
  },
  filterChipActive: {
    backgroundColor: colors.teal,
    borderColor: colors.teal,
  },
  filterText: { color: colors.slate, fontSize: typography.small, fontWeight: '900' },
  filterTextActive: { color: colors.surface },
  stack: { gap: spacing.md },
  notificationCard: {
    alignItems: 'flex-start',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    flexDirection: 'row',
    padding: spacing.md,
    position: 'relative',
  },
  itemIcon: {
    alignItems: 'center',
    backgroundColor: colors.lilac,
    borderRadius: radius.lg,
    height: 46,
    justifyContent: 'center',
    width: 46,
  },
  itemIconActive: { backgroundColor: colors.teal },
  itemCopy: { flex: 1, marginLeft: spacing.md },
  itemTop: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  itemType: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  itemTime: { color: colors.softMuted, fontSize: typography.tiny, fontWeight: '900' },
  itemTitle: {
    color: colors.midnight,
    fontSize: typography.body,
    fontWeight: '900',
    marginTop: spacing.xs,
  },
  itemBody: {
    color: colors.slate,
    fontSize: typography.small,
    fontWeight: '700',
    lineHeight: 20,
    marginTop: spacing.xs,
  },
  unreadDot: {
    backgroundColor: colors.teal,
    borderColor: colors.surface,
    borderRadius: radius.pill,
    borderWidth: 2,
    height: 13,
    position: 'absolute',
    right: spacing.md,
    top: spacing.md,
    width: 13,
  },
});
}
