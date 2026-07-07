import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Keyboard,
  Platform,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { agentApi } from '../api/journyApi';
import { session } from '../api/session';
import type { AgentActionPreview, AgentIntent } from '../api/types';
import { useAppTheme } from '../theme/ThemeContext';

type Message = {
  id: string;
  role: 'ai' | 'user';
  text: string;
  time?: string;
  intent?: AgentIntent;
  preview?: AgentActionPreview;
  applied?: boolean;
};

const quickPrompts = [
  {
    label: 'Make today lighter',
    prompt: 'Can you make today lighter?',
    answer:
      'Yes. I would keep Museumplein as the main anchor, move the canal walk before lunch, and remove the optional market stop. Your day becomes about 22 minutes shorter with a longer afternoon break.',
  },
  {
    label: 'Coffee nearby',
    prompt: 'Find a quiet coffee spot near me.',
    answer:
      'I would add a quiet coffee stop near De Pijp before the canal loop. It keeps the detour under 10 minutes and gives you a calm break before the next stop.',
  },
  {
    label: 'Dinner idea',
    prompt: 'Suggest dinner near the last stop.',
    answer:
      'For dinner, stay near the final neighborhood instead of crossing the city. A small local bistro around the evening area fits the route and keeps the night relaxed.',
  },
  {
    label: 'Rain backup',
    prompt: 'Rebuild the plan if it rains.',
    answer:
      'If it rains, move the outdoor canal walk to tomorrow morning and keep today focused on one museum, a covered food stop, and a longer cafe window.',
  },
];

const initialMessages: Message[] = [
  {
    id: 'm1',
    role: 'ai',
    text: 'I can adjust your current city plan. Ask me to slow the pace, find food nearby, rebuild around rain, or change the route.',
    time: 'Now',
  },
];

export default function AssistantScreen() {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const [messages, setMessages] = useState<Message[]>(initialMessages);
  const [input, setInput] = useState('');
  const [keyboardHeight, setKeyboardHeight] = useState(0);
  const [sending, setSending] = useState(false);
  const [applyingMessageId, setApplyingMessageId] = useState<string | null>(null);
  const scrollRef = useRef<ScrollView>(null);

  useEffect(() => {
    const showEvent = Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow';
    const hideEvent = Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide';
    const showSub = Keyboard.addListener(showEvent, (event) => {
      setKeyboardHeight(Math.max(event.endCoordinates.height - 34, 0));
      requestAnimationFrame(() => scrollRef.current?.scrollToEnd({ animated: true }));
    });
    const hideSub = Keyboard.addListener(hideEvent, () => setKeyboardHeight(0));

    return () => {
      showSub.remove();
      hideSub.remove();
    };
  }, []);

  const sendPrompt = async (prompt: string, fallbackAnswer?: string) => {
    const cleanPrompt = prompt.trim();
    if (!cleanPrompt || sending) return;

    const timestamp = Date.now().toString();
    setMessages((current) => [
      ...current,
      { id: `${timestamp}-user`, role: 'user', text: cleanPrompt },
    ]);
    setInput('');
    setSending(true);
    requestAnimationFrame(() => scrollRef.current?.scrollToEnd({ animated: true }));

    try {
      const response = await agentApi.message(cleanPrompt, session.getCurrentTrip()?.id, 1);
      setMessages((current) => [
        ...current,
        {
          id: `${timestamp}-ai`,
          role: 'ai',
          text: response.message,
          time: response.preview?.requiresConfirmation ? 'Agent preview' : 'Now',
          intent: response.intent,
          preview: response.preview,
        },
      ]);
    } catch {
      const intent = intentFromSuggestion(cleanPrompt);
      setMessages((current) => [
        ...current,
        {
          id: `${timestamp}-ai`,
          role: 'ai',
          text: fallbackAnswer ?? buildAnswer(cleanPrompt),
          time: 'Offline preview',
          intent,
          preview: offlinePreview(intent, cleanPrompt),
        },
      ]);
    } finally {
      setSending(false);
      requestAnimationFrame(() => scrollRef.current?.scrollToEnd({ animated: true }));
    }
  };

  const applyAction = async (message: Message) => {
    const trip = session.getCurrentTrip();
    if (!trip?.id || !message.intent || applyingMessageId || message.intent === 'GENERAL_GUIDANCE') {
      return;
    }

    setApplyingMessageId(message.id);
    try {
      const updatedDay = await agentApi.apply(trip.id, 1, message.intent);
      setMessages((current) => current.map((item) => (
        item.id === message.id ? { ...item, applied: true } : item
      )));
      setMessages((current) => [
        ...current,
        {
          id: `${Date.now()}-applied`,
          role: 'ai',
          text: `Done. Day 1 is now updated as "${updatedDay.title}" with ${updatedDay.stopCount} stops and ${updatedDay.walkKm.toFixed(1)} km of walking.`,
          time: 'Plan updated',
        },
      ]);
    } catch {
      setMessages((current) => [
        ...current,
        {
          id: `${Date.now()}-apply-error`,
          role: 'ai',
          text: 'I could not update the plan right now. Check that the backend is running, then try again.',
          time: 'Needs connection',
        },
      ]);
    } finally {
      setApplyingMessageId(null);
      requestAnimationFrame(() => scrollRef.current?.scrollToEnd({ animated: true }));
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <View style={styles.screen}>
        <View style={styles.header}>
          <View style={styles.headerLeft}>
            <View style={styles.aiAvatar}>
              <Ionicons name="sparkles" size={17} color={colors.surface} />
            </View>
            <View>
              <Text style={styles.title}>Journy AI</Text>
              <Text style={styles.status}>Ready to adjust your trip</Text>
            </View>
          </View>
        </View>

        <ScrollView
          ref={scrollRef}
          contentContainerStyle={styles.messages}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
          onContentSizeChange={() => scrollRef.current?.scrollToEnd({ animated: true })}
        >
          {messages.map((message) => (
            <View key={message.id} style={[styles.messageLine, message.role === 'user' && styles.messageLineUser]}>
              {message.role === 'ai' ? (
                <View style={styles.smallAvatar}>
                  <Ionicons name="sparkles" size={12} color={colors.surface} />
                </View>
              ) : null}

              <View style={message.role === 'ai' ? styles.aiBubble : styles.userBubble}>
                <Text style={message.role === 'ai' ? styles.aiText : styles.userText}>{message.text}</Text>
                {message.role === 'ai' && message.time ? <Text style={styles.messageTime}>{message.time}</Text> : null}
                {message.role === 'ai' && message.preview ? (
                  <View style={styles.previewCard}>
                    <View style={styles.previewTop}>
                      <Ionicons name="git-branch-outline" size={14} color={colors.teal} />
                      <Text style={styles.previewTitle}>{message.preview.title}</Text>
                    </View>
                    <Text style={styles.previewText}>{message.preview.message}</Text>
                    <View style={styles.previewMetaRow}>
                      <View style={styles.previewMeta}>
                        <Text style={styles.previewMetaLabel}>Change</Text>
                        <Text style={styles.previewMetaValue}>{message.preview.suggestedAction}</Text>
                      </View>
                      <View style={styles.previewMeta}>
                        <Text style={styles.previewMetaLabel}>Impact</Text>
                        <Text style={styles.previewMetaValue}>{message.preview.minutesSaved ? `${message.preview.minutesSaved} min saved` : 'Route fit'}</Text>
                      </View>
                    </View>
                    {message.preview.affectedStops.length ? (
                      <Text style={styles.previewRoute}>Affects: {message.preview.affectedStops.join(', ')}</Text>
                    ) : null}
                    <Text style={styles.previewRoute}>{message.preview.routeSummary}</Text>
                    <View style={styles.reasonList}>
                      {message.preview.reasons.slice(0, 3).map((reason) => (
                        <View key={reason} style={styles.reasonRow}>
                          <Ionicons name="checkmark-circle" size={13} color={colors.teal} />
                          <Text style={styles.reasonText}>{reason}</Text>
                        </View>
                      ))}
                    </View>
                  </View>
                ) : null}
                {message.role === 'ai' && message.preview?.requiresConfirmation ? (
                  <TouchableOpacity
                    style={[styles.applyButton, message.applied && styles.applyButtonDone]}
                    activeOpacity={0.86}
                    disabled={message.applied || applyingMessageId === message.id}
                    onPress={() => applyAction(message)}
                  >
                    <Ionicons
                      name={message.applied ? 'checkmark' : applyingMessageId === message.id ? 'hourglass-outline' : 'sparkles-outline'}
                      size={13}
                      color={message.applied ? colors.surface : colors.teal}
                    />
                    <Text style={[styles.applyButtonText, message.applied && styles.applyButtonTextDone]}>
                      {message.applied ? 'Applied to Day 1' : applyingMessageId === message.id ? 'Updating...' : 'Apply changes'}
                    </Text>
                  </TouchableOpacity>
                ) : null}
              </View>
            </View>
          ))}
        </ScrollView>

        <View style={[styles.bottomArea, { paddingBottom: keyboardHeight || 88 }]}>
          <ScrollView
            horizontal
            keyboardShouldPersistTaps="handled"
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.quickRail}
          >
            {quickPrompts.map((item) => (
              <TouchableOpacity
                key={item.label}
                style={styles.quickChip}
                activeOpacity={0.86}
                onPress={() => sendPrompt(item.prompt, item.answer)}
              >
                <Text style={styles.quickChipText}>{item.label}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>

          <View style={styles.composer}>
            <TextInput
              placeholder="Message Journy..."
              placeholderTextColor={colors.softMuted}
              style={styles.input}
              value={input}
              onChangeText={setInput}
              multiline
              returnKeyType="send"
              onSubmitEditing={() => sendPrompt(input)}
            />
            <TouchableOpacity
              style={[styles.sendButton, (!input.trim() || sending) && styles.sendButtonDisabled]}
              activeOpacity={0.86}
              disabled={!input.trim() || sending}
              onPress={() => sendPrompt(input)}
            >
              <Ionicons name={sending ? 'hourglass-outline' : 'arrow-up'} size={18} color={colors.surface} />
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </SafeAreaView>
  );
}

function buildAnswer(prompt: string) {
  const lower = prompt.toLowerCase();

  if (lower.includes('coffee') || lower.includes('cafe')) {
    return 'I found a quiet coffee break that fits between your museum stop and canal walk. It adds a small rest without changing the route shape.';
  }
  if (lower.includes('dinner') || lower.includes('food')) {
    return 'I would place dinner near your last stop, then keep the evening open. That avoids a long transfer after the busiest part of the day.';
  }
  if (lower.includes('rain') || lower.includes('weather')) {
    return 'I can switch the afternoon to indoor stops and move the canal walk to a clearer window. The day stays balanced without rushing.';
  }
  if (lower.includes('easy') || lower.includes('light') || lower.includes('short')) {
    return 'I would remove one optional stop and add a longer break after lunch. You still keep the main experience, but the day feels lighter.';
  }

  return 'I can help with that. I would keep the main anchor stops, reduce backtracking, and leave one flexible window so the day stays realistic.';
}

function intentFromSuggestion(value: string): AgentIntent {
  const lower = value.toLowerCase();
  if (lower.includes('budget') || lower.includes('cheap') || lower.includes('ucuz') || lower.includes('bütçe')) return 'BUDGET_OPTIMIZE';
  if (lower.includes('rain') || lower.includes('weather') || lower.includes('yağmur')) return 'RAIN_REPLAN';
  if (lower.includes('coffee') || lower.includes('dinner') || lower.includes('food') || lower.includes('kahve') || lower.includes('yemek')) return 'ADD_FOOD_STOP';
  if (lower.includes('replace') || lower.includes('change') || lower.includes('değiştir')) return 'REPLACE_STOP';
  if (lower.includes('light') || lower.includes('easy') || lower.includes('short') || lower.includes('slow') || lower.includes('yorul') || lower.includes('hafif')) return 'MAKE_DAY_LIGHTER';
  return 'GENERAL_GUIDANCE';
}

function offlinePreview(intent: AgentIntent, prompt: string): AgentActionPreview {
  const title = {
    MAKE_DAY_LIGHTER: 'Make today lighter',
    ADD_FOOD_STOP: 'Add a better food break',
    REPLACE_STOP: 'Replace one flexible stop',
    BUDGET_OPTIMIZE: 'Optimize for budget',
    RAIN_REPLAN: 'Rebuild around rain',
    GENERAL_GUIDANCE: 'Journy can adjust your route',
  }[intent];
  const requiresConfirmation = intent !== 'GENERAL_GUIDANCE';

  return {
    intent,
    title,
    message: buildAnswer(prompt),
    suggestedAction: {
      MAKE_DAY_LIGHTER: 'Remove optional final stop',
      ADD_FOOD_STOP: 'Add food or coffee stop near route',
      REPLACE_STOP: 'Swap one stop in the same area',
      BUDGET_OPTIMIZE: 'Replace expensive flexible stop',
      RAIN_REPLAN: 'Move route toward indoor stops',
      GENERAL_GUIDANCE: 'Ask for a route adjustment',
    }[intent],
    minutesSaved: intent === 'MAKE_DAY_LIGHTER' ? 22 : intent === 'RAIN_REPLAN' ? 12 : null,
    affectedStops: [],
    routeSummary: requiresConfirmation
      ? 'Connect to the backend to apply this preview to your current itinerary.'
      : 'Ask Journy to make the day lighter, cheaper, food-focused or weather-ready.',
    reasons: [
      'Uses your current trip context when the backend is available',
      'Shows preview before changing the plan',
      'Applies only after confirmation',
    ],
    requiresConfirmation,
  };
}

type Theme = ReturnType<typeof useAppTheme>['theme'];

function createStyles({ colors, radius, spacing, typography }: Theme) {
  return StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.ivory },
  screen: { flex: 1 },
  header: {
    alignItems: 'center',
    borderBottomColor: colors.mist,
    borderBottomWidth: 1,
    flexDirection: 'row',
    justifyContent: 'flex-start',
    paddingBottom: spacing.md,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
  },
  headerLeft: { alignItems: 'center', flexDirection: 'row', flex: 1 },
  aiAvatar: {
    alignItems: 'center',
    backgroundColor: colors.teal,
    borderRadius: radius.md,
    height: 42,
    justifyContent: 'center',
    marginRight: spacing.sm,
    width: 42,
  },
  title: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
  status: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 2 },
  messages: {
    paddingBottom: spacing.lg,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
  },
  messageLine: {
    alignItems: 'flex-end',
    flexDirection: 'row',
    marginBottom: spacing.md,
  },
  messageLineUser: {
    justifyContent: 'flex-end',
  },
  smallAvatar: {
    alignItems: 'center',
    backgroundColor: colors.teal,
    borderRadius: radius.pill,
    height: 26,
    justifyContent: 'center',
    marginRight: spacing.sm,
    width: 26,
  },
  aiBubble: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderBottomLeftRadius: radius.sm,
    borderWidth: 1,
    maxWidth: '82%',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  userBubble: {
    backgroundColor: colors.midnight,
    borderRadius: radius.lg,
    borderBottomRightRadius: radius.sm,
    maxWidth: '82%',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  aiText: { color: colors.midnight, fontSize: typography.body, fontWeight: '700', lineHeight: 22 },
  userText: { color: colors.surface, fontSize: typography.body, fontWeight: '700', lineHeight: 22 },
  messageTime: {
    color: colors.softMuted,
    fontSize: 10,
    fontWeight: '800',
    marginTop: spacing.xs,
  },
  previewCard: {
    backgroundColor: colors.surfaceWarm,
    borderColor: colors.mist,
    borderRadius: radius.md,
    borderWidth: 1,
    marginTop: spacing.sm,
    padding: spacing.sm,
  },
  previewTop: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 5,
  },
  previewTitle: {
    color: colors.midnight,
    flex: 1,
    fontSize: typography.small,
    fontWeight: '900',
  },
  previewText: {
    color: colors.slate,
    fontSize: typography.tiny,
    fontWeight: '800',
    lineHeight: 17,
    marginTop: spacing.xs,
  },
  previewMetaRow: {
    flexDirection: 'row',
    gap: spacing.xs,
    marginTop: spacing.sm,
  },
  previewMeta: {
    backgroundColor: colors.surface,
    borderRadius: radius.sm,
    flex: 1,
    padding: spacing.xs,
  },
  previewMetaLabel: {
    color: colors.slate,
    fontSize: 9,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  previewMetaValue: {
    color: colors.midnight,
    fontSize: 11,
    fontWeight: '900',
    marginTop: 2,
  },
  previewRoute: {
    color: colors.slate,
    fontSize: 11,
    fontWeight: '800',
    lineHeight: 16,
    marginTop: spacing.xs,
  },
  reasonList: {
    gap: 4,
    marginTop: spacing.sm,
  },
  reasonRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 5,
  },
  reasonText: {
    color: colors.midnight,
    flex: 1,
    fontSize: 11,
    fontWeight: '800',
    lineHeight: 15,
  },
  applyButton: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    flexDirection: 'row',
    gap: 5,
    marginTop: spacing.sm,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  applyButtonDone: {
    backgroundColor: colors.teal,
  },
  applyButtonText: {
    color: colors.midnight,
    fontSize: 11,
    fontWeight: '900',
  },
  applyButtonTextDone: {
    color: colors.surface,
  },
  bottomArea: {
    backgroundColor: colors.ivory,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.sm,
  },
  quickRail: {
    gap: spacing.sm,
    paddingBottom: spacing.sm,
  },
  quickChip: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  quickChipText: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  composer: {
    alignItems: 'flex-end',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    flexDirection: 'row',
    padding: spacing.sm,
    shadowColor: colors.shadow,
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.12,
    shadowRadius: 18,
  },
  input: {
    color: colors.midnight,
    flex: 1,
    fontSize: typography.body,
    fontWeight: '700',
    maxHeight: 92,
    minHeight: 40,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.sm,
  },
  sendButton: {
    alignItems: 'center',
    backgroundColor: colors.teal,
    borderRadius: radius.md,
    height: 40,
    justifyContent: 'center',
    width: 40,
  },
  sendButtonDisabled: { opacity: 0.45 },
});
}
