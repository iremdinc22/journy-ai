import React from 'react';
import { ActivityIndicator, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useAppTheme } from '../theme/ThemeContext';

export function InlineLoading({ label = 'Loading your trip...' }: { label?: string }) {
  const { theme } = useAppTheme();
  const styles = createStyles(theme);

  return (
    <View style={styles.stateCard}>
      <ActivityIndicator color={theme.colors.teal} />
      <Text style={styles.stateTitle}>{label}</Text>
    </View>
  );
}

export function InlineError({
  title = 'Could not load this right now',
  description = 'Check the backend connection and try again.',
  onRetry,
}: {
  title?: string;
  description?: string;
  onRetry?: () => void;
}) {
  const { theme } = useAppTheme();
  const styles = createStyles(theme);
  const { colors } = theme;

  return (
    <View style={styles.stateCard}>
      <View style={styles.iconBubble}>
        <Ionicons name="refresh-outline" size={18} color={colors.teal} />
      </View>
      <Text style={styles.stateTitle}>{title}</Text>
      <Text style={styles.stateText}>{description}</Text>
      {onRetry ? (
        <TouchableOpacity style={styles.retryButton} activeOpacity={0.86} onPress={onRetry}>
          <Text style={styles.retryText}>Retry</Text>
        </TouchableOpacity>
      ) : null}
    </View>
  );
}

export function InlineEmpty({
  title = 'Nothing here yet',
  description = 'Create or save something and it will appear here.',
}: {
  title?: string;
  description?: string;
}) {
  const { theme } = useAppTheme();
  const styles = createStyles(theme);
  const { colors } = theme;

  return (
    <View style={styles.stateCard}>
      <View style={styles.iconBubble}>
        <Ionicons name="sparkles-outline" size={18} color={colors.teal} />
      </View>
      <Text style={styles.stateTitle}>{title}</Text>
      <Text style={styles.stateText}>{description}</Text>
    </View>
  );
}

function createStyles({ colors, radius, spacing, typography }: ReturnType<typeof useAppTheme>['theme']) {
  return StyleSheet.create({
    stateCard: {
      alignItems: 'center',
      backgroundColor: colors.surface,
      borderColor: colors.mist,
      borderRadius: radius.lg,
      borderWidth: 1,
      marginVertical: spacing.md,
      padding: spacing.lg,
    },
    iconBubble: {
      alignItems: 'center',
      backgroundColor: colors.fog,
      borderRadius: radius.pill,
      height: 38,
      justifyContent: 'center',
      marginBottom: spacing.sm,
      width: 38,
    },
    stateTitle: {
      color: colors.midnight,
      fontSize: typography.small,
      fontWeight: '900',
      marginTop: spacing.sm,
      textAlign: 'center',
    },
    stateText: {
      color: colors.slate,
      fontSize: typography.tiny,
      fontWeight: '700',
      lineHeight: 17,
      marginTop: spacing.xs,
      textAlign: 'center',
    },
    retryButton: {
      backgroundColor: colors.midnight,
      borderRadius: radius.pill,
      marginTop: spacing.md,
      paddingHorizontal: spacing.lg,
      paddingVertical: spacing.sm,
    },
    retryText: {
      color: colors.surface,
      fontSize: typography.small,
      fontWeight: '900',
    },
  });
}
