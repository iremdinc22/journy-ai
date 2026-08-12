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
import { agentApi, tripApi } from '../api/journyApi';
import { session } from '../api/session';
import type { AgentActionPreview, AgentIntent, ItineraryDay, ItineraryResponse, TripResponse } from '../api/types';
import { useLanguage, useTranslation } from '../i18n/LanguageContext';
import { useAppTheme } from '../theme/ThemeContext';
import { localizeDynamicText } from '../utils/localizedDynamicText';

type Message = {
  id: string;
  role: 'ai' | 'user';
  text: string;
  time?: string;
  intent?: AgentIntent;
  preview?: AgentActionPreview;
  applied?: boolean;
  result?: ItineraryDay;
  appliedIntent?: AgentIntent;
};

type IoniconName = keyof typeof Ionicons.glyphMap;

type QuickPrompt = {
  label: string;
  icon: IoniconName;
  prompt: string;
  answer: string;
};

const defaultQuickPrompts: QuickPrompt[] = [
  {
    label: 'Make today lighter',
    icon: 'walk-outline',
    prompt: 'Can you make today lighter?',
    answer:
      'Yes. I would keep Museumplein as the main anchor, move the canal walk before lunch, and remove the optional market stop. Your day becomes about 22 minutes shorter with a longer afternoon break.',
  },
  {
    label: 'Coffee nearby',
    icon: 'cafe-outline',
    prompt: 'Find a quiet coffee spot near me.',
    answer:
      'I would add a quiet coffee stop near De Pijp before the canal loop. It keeps the detour under 10 minutes and gives you a calm break before the next stop.',
  },
  {
    label: 'Dinner idea',
    icon: 'restaurant-outline',
    prompt: 'Suggest dinner near the last stop.',
    answer:
      'For dinner, stay near the final neighborhood instead of crossing the city. A small local bistro around the evening area fits the route and keeps the night relaxed.',
  },
  {
    label: 'Rain backup',
    icon: 'rainy-outline',
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
  const { language } = useLanguage();
  const t = useTranslation();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const [messages, setMessages] = useState<Message[]>(() => initialMessages.map((message) => ({
    ...message,
    text: message.id === 'm1' ? t('assistant.initial') : message.text,
  })));
  const [input, setInput] = useState('');
  const [keyboardHeight, setKeyboardHeight] = useState(0);
  const [sending, setSending] = useState(false);
  const [applyingMessageId, setApplyingMessageId] = useState<string | null>(null);
  const [currentTrip, setCurrentTrip] = useState<TripResponse | null>(() => session.getCurrentTrip() ?? null);
  const [itinerary, setItinerary] = useState<ItineraryResponse | null>(null);
  const scrollRef = useRef<ScrollView>(null);
  const activeDayNumber = useMemo(() => dayNumberForTrip(currentTrip ?? undefined), [currentTrip]);
  const currentDay = useMemo(
    () => itinerary?.days.find((day) => day.dayNumber === activeDayNumber) ?? itinerary?.days[0],
    [activeDayNumber, itinerary?.days],
  );
  const quickPrompts = useMemo(() => buildQuickPrompts(currentTrip ?? undefined, currentDay, t), [currentDay, currentTrip, t]);
  const hasWeatherRisk = useMemo(() => currentDay?.stops.some(isWeatherSensitiveStop) ?? false, [currentDay]);

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

  useEffect(() => {
    let mounted = true;

    const loadContext = async () => {
      try {
        await session.restore();
        const trip = session.getCurrentTrip() ?? await tripApi.current();
        session.setCurrentTrip(trip);
        const response = await tripApi.itinerary(trip.id);
        if (mounted) {
          setCurrentTrip(trip);
          setItinerary(response);
        }
      } catch {
        if (mounted) {
          setCurrentTrip(session.getCurrentTrip() ?? null);
        }
      }
    };

    loadContext();

    return () => {
      mounted = false;
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
      const response = await agentApi.message(cleanPrompt, session.getCurrentTrip()?.id, currentDay?.dayNumber ?? activeDayNumber, language);
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
    const intent = message.intent;
    if (!trip?.id || !intent || applyingMessageId || intent === 'GENERAL_GUIDANCE') {
      return;
    }

    setApplyingMessageId(message.id);
    try {
      const updatedDay = await agentApi.apply(trip.id, currentDay?.dayNumber ?? activeDayNumber, intent, language);
      setItinerary((current) => current
        ? { ...current, days: current.days.map((day) => day.dayNumber === updatedDay.dayNumber ? updatedDay : day) }
        : current);
      setMessages((current) => current.map((item) => (
        item.id === message.id ? { ...item, applied: true } : item
      )));
      setMessages((current) => [
        ...current,
        {
          id: `${Date.now()}-applied`,
          role: 'ai',
          text: buildApplyResultMessage(intent, updatedDay),
          time: 'Plan updated',
          result: updatedDay,
          appliedIntent: intent,
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
              <Text style={styles.title}>{t('assistant.title')}</Text>
              <Text style={styles.status}>{t('assistant.status')}</Text>
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
          {currentTrip && currentDay ? (
            <View style={styles.contextCard}>
              <View style={styles.contextTop}>
                <View style={styles.contextTitleWrap}>
                  <Text style={styles.contextKicker}>{t('assistant.today', { day: currentDay.dayNumber })}</Text>
                  <Text style={styles.contextTitle}>{localizeDynamicText(currentDay.title, language)}</Text>
                </View>
                <View style={styles.contextBadge}>
                  <Ionicons name="sparkles-outline" size={13} color={colors.teal} />
                  <Text style={styles.contextBadgeText}>{t('assistant.aiReady')}</Text>
                </View>
              </View>
              <Text style={styles.contextSummary}>
                {currentDay.stopCount} {t('itinerary.stops').toLowerCase()} · {currentDay.walkKm.toFixed(1)} km · {currentTrip.destination}
              </Text>
              <View style={styles.contextStats}>
                <ContextStat label={t('itinerary.stops')} value={`${currentDay.stopCount}`} icon="location-outline" styles={styles} />
                <ContextStat label={t('itinerary.walk')} value={`${currentDay.walkKm.toFixed(1)} km`} icon="walk-outline" styles={styles} />
                <ContextStat label={t('home.pace')} value={formatEnum(currentTrip.pace)} icon="speedometer-outline" styles={styles} />
              </View>
              <View style={styles.contextHint}>
                <Ionicons name="bulb-outline" size={14} color={colors.teal} />
                <Text style={styles.contextHintText}>{t('assistant.initial')}</Text>
              </View>
              {hasWeatherRisk ? (
                <TouchableOpacity
                  style={styles.weatherAgentCard}
                  activeOpacity={0.86}
                  onPress={() => sendPrompt('Make an indoor plan for today.', 'I would protect the weather-sensitive parts of today by moving outdoor time earlier and keeping indoor culture, cafe or food stops for the rain window.')}
                >
                  <View style={styles.weatherAgentIcon}>
                    <Ionicons name="rainy-outline" size={17} color={colors.teal} />
                  </View>
                  <View style={styles.weatherAgentCopy}>
                    <Text style={styles.weatherAgentKicker}>{t('assistant.weatherAgent')}</Text>
                    <Text style={styles.weatherAgentText}>{t('assistant.weatherPreview')}</Text>
                  </View>
                  <Ionicons name="chevron-forward" size={17} color={colors.teal} />
                </TouchableOpacity>
              ) : null}
            </View>
          ) : null}

          {messages.map((message) => {
            const previewVisual = message.preview ? agentVisual(message.preview.intent) : null;
            const resultVisual = message.appliedIntent ? agentVisual(message.appliedIntent) : null;

            return (
            <View key={message.id} style={[styles.messageLine, message.role === 'user' && styles.messageLineUser]}>
              {message.role === 'ai' ? (
                <View style={styles.smallAvatar}>
                  <Ionicons name="sparkles" size={12} color={colors.surface} />
                </View>
              ) : null}

              <View style={message.role === 'ai' ? styles.aiBubble : styles.userBubble}>
                <Text style={message.role === 'ai' ? styles.aiText : styles.userText}>{message.role === 'ai' ? localizeDynamicText(message.text, language) : message.text}</Text>
                {message.role === 'ai' && message.time ? <Text style={styles.messageTime}>{message.time}</Text> : null}
                {message.role === 'ai' && message.preview ? (
                  <View style={styles.previewCard}>
                    <View style={styles.previewHeader}>
                      <View style={styles.intentBadge}>
                        <Ionicons name={previewVisual?.icon ?? 'sparkles-outline'} size={12} color={colors.teal} />
                        <Text style={styles.intentBadgeText}>{localizeDynamicText(previewVisual?.label ?? 'Agent preview', language)}</Text>
                      </View>
                      <Text style={styles.previewConfidence}>{t('assistant.preview')}</Text>
                    </View>
                    <Text style={styles.previewTitle}>{localizeDynamicText(message.preview.title, language)}</Text>
                    <Text style={styles.previewText}>{localizeDynamicText(message.preview.message, language)}</Text>
                    <View style={styles.previewMetaRow}>
                      <View style={styles.previewMeta}>
                        <Text style={styles.previewMetaLabel}>{t('assistant.planChange')}</Text>
                        <Text style={styles.previewMetaValue}>{localizeDynamicText(message.preview.suggestedAction, language)}</Text>
                      </View>
                      <View style={styles.previewMeta}>
                        <Text style={styles.previewMetaLabel}>{t('assistant.routeImpact')}</Text>
                        <Text style={styles.previewMetaValue}>{localizeDynamicText(previewImpactLabel(message.preview), language)}</Text>
                      </View>
                    </View>
                    {currentDay ? (
                      <View style={styles.beforeAfterCard}>
                        <View style={styles.beforeAfterColumn}>
                          <Text style={styles.beforeAfterLabel}>{t('itinerary.before')}</Text>
                          <Text style={styles.beforeAfterValue}>{currentDay.stopCount} {t('itinerary.stops').toLowerCase()}</Text>
                          <Text style={styles.beforeAfterDetail}>{currentDay.walkKm.toFixed(1)} km {t('home.walk')}</Text>
                        </View>
                        <Ionicons name="arrow-forward" size={16} color={colors.teal} />
                        <View style={styles.beforeAfterColumn}>
                          <Text style={styles.beforeAfterLabel}>{t('itinerary.after')}</Text>
                          <Text style={styles.beforeAfterValue}>{afterStopCount(currentDay, message.preview)} {t('itinerary.stops').toLowerCase()}</Text>
                          <Text style={styles.beforeAfterDetail}>{afterWalkKm(currentDay, message.preview).toFixed(1)} km {t('home.walk')}</Text>
                        </View>
                      </View>
                    ) : null}
                    {message.preview.affectedStops.length ? (
                      <View style={styles.affectedBlock}>
                        <Text style={styles.blockLabel}>{t('assistant.routeWindow')}</Text>
                        <View style={styles.stopChipRow}>
                          {message.preview.affectedStops.slice(0, 3).map((stop) => (
                            <View key={stop} style={styles.stopChip}>
                              <Text style={styles.stopChipText}>{localizeDynamicText(stop, language)}</Text>
                            </View>
                          ))}
                        </View>
                      </View>
                    ) : null}
                    <View style={styles.summaryStrip}>
                      <Ionicons name="map-outline" size={13} color={colors.teal} />
                      <Text style={styles.previewRoute}>{localizeDynamicText(message.preview.routeSummary, language)}</Text>
                    </View>
                    <View style={styles.reasonList}>
                      <Text style={styles.blockLabel}>{t('assistant.signals')}</Text>
                      {message.preview.reasons.slice(0, 2).map((reason) => (
                        <View key={reason} style={styles.reasonRow}>
                          <Ionicons name="checkmark-circle" size={13} color={colors.teal} />
                          <Text style={styles.reasonText}>{localizeDynamicText(reason, language)}</Text>
                        </View>
                      ))}
                    </View>
                  </View>
                ) : null}
                {message.role === 'ai' && message.result ? (
                  <View style={styles.resultCard}>
                    <View style={styles.resultTop}>
                      <View style={styles.resultIcon}>
                        <Ionicons name={resultVisual?.icon ?? 'checkmark'} size={15} color={colors.surface} />
                      </View>
                      <View style={styles.resultTitleWrap}>
                        <Text style={styles.resultEyebrow}>{t('assistant.updatedRoute')}</Text>
                        <Text style={styles.resultTitle}>{localizeDynamicText(message.result.title, language)}</Text>
                      </View>
                    </View>
                    <View style={styles.resultStats}>
                      <View style={styles.resultStat}>
                        <Text style={styles.resultStatValue}>{message.result.stopCount}</Text>
                        <Text style={styles.resultStatLabel}>{t('itinerary.stops')}</Text>
                      </View>
                      <View style={styles.resultStat}>
                        <Text style={styles.resultStatValue}>{message.result.walkKm.toFixed(1)} km</Text>
                        <Text style={styles.resultStatLabel}>{t('assistant.walking')}</Text>
                      </View>
                      <View style={styles.resultStat}>
                        <Text style={styles.resultStatValue}>{resultVisual?.short ?? 'Done'}</Text>
                        <Text style={styles.resultStatLabel}>{t('assistant.change')}</Text>
                      </View>
                    </View>
                  </View>
                ) : null}
                {message.role === 'ai' && message.preview?.requiresConfirmation ? (
                  <View style={styles.actionRow}>
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
                        {message.applied ? t('assistant.applied') : applyingMessageId === message.id ? t('assistant.updating') : t('assistant.apply')}
                      </Text>
                    </TouchableOpacity>
                    {!message.applied ? (
                      <TouchableOpacity
                        style={styles.dismissButton}
                        activeOpacity={0.86}
                        onPress={() => setMessages((current) => current.filter((item) => item.id !== message.id))}
                      >
                        <Text style={styles.dismissButtonText}>{t('assistant.dismiss')}</Text>
                      </TouchableOpacity>
                    ) : null}
                  </View>
                ) : null}
              </View>
            </View>
            );
          })}
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
                <Ionicons name={item.icon} size={13} color={colors.teal} />
                <Text style={styles.quickChipText}>{item.label}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>

          <View style={styles.composer}>
            <TextInput
              placeholder={t('assistant.input')}
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

function formatEnum(value: string) {
  return value
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/^\w/, (letter) => letter.toUpperCase());
}

type Translate = ReturnType<typeof useTranslation>;

function buildQuickPrompts(trip?: TripResponse, day?: ItineraryDay, t?: Translate): QuickPrompt[] {
  if (!trip) {
    return t ? defaultQuickPrompts.map((prompt) => ({ ...prompt, label: quickLabel(prompt.label, t) })) : defaultQuickPrompts;
  }

  const prompts: QuickPrompt[] = [];
  const addPrompt = (prompt: QuickPrompt) => {
    if (!prompts.some((item) => item.label === prompt.label)) {
      prompts.push(prompt);
    }
  };
  const interests = trip.interests.map((item) => item.toUpperCase());
  const isFoodFocused = interests.includes('COFFEE') || interests.includes('LOCAL_FOOD');
  const isWalkHeavy = (day?.walkKm ?? trip.stats.averageWalkKm) >= 4.5 || (day?.stopCount ?? trip.stats.stops) >= 5 || trip.pace === 'FULL';

  if (isWalkHeavy) {
    addPrompt({
      label: t ? t('assistant.quickLighter') : 'Make today lighter',
      icon: 'walk-outline',
      prompt: 'I am tired. Can you make today lighter?',
      answer: 'I would compare today against the rest of your trip, keep the anchor stops, and reduce the most flexible route pressure.',
    });
  }

  if (isFoodFocused || trip.stats.foodPicks < Math.max(1, trip.days)) {
    addPrompt({
      label: t ? t('assistant.quickLunch') : 'Find lunch nearby',
      icon: 'restaurant-outline',
      prompt: 'Find lunch nearby without stretching the route.',
      answer: 'I would place a food or coffee break near your existing route cluster so the day feels better paced without extra travel.',
    });
  }

  addPrompt({
    label: 'Replace next stop',
    icon: 'swap-horizontal-outline',
    prompt: 'Replace my next stop with something that fits my route.',
    answer: 'I would swap the next flexible stop for a nearby place that better matches your current pace and interests.',
  });

  if (trip.budget === 'LEAN') {
    addPrompt({
      label: 'Cheaper options',
      icon: 'wallet-outline',
      prompt: 'Can you make today more budget friendly?',
      answer: 'I would keep the core stops and swap the most flexible paid or food stop for a lower-cost local alternative.',
    });
  }

  addPrompt({
    label: t ? t('assistant.quickIndoor') : 'Indoor plan',
    icon: 'rainy-outline',
    prompt: 'Make an indoor plan for today.',
    answer: 'I would look for outdoor-heavy parts of the day and swap them into indoor culture, cafe, or covered local stops.',
  });

  addPrompt({
    label: t ? t('assistant.quickEarlier') : 'Finish earlier',
    icon: 'time-outline',
    prompt: 'Can we finish today earlier?',
    answer: 'I would pull the strongest stops earlier and remove or move the final flexible stop so the evening opens up.',
  });

  addPrompt({
    label: t ? t('assistant.quickLocal') : 'More local places',
    icon: 'map-outline',
    prompt: 'Show me more local places near today’s route.',
    answer: 'I would look near today’s route cluster and suggest places that match your interests without adding much walking.',
  });

  return prompts.slice(0, 6);
}

function quickLabel(label: string, t: Translate) {
  if (label === 'Make today lighter') return t('assistant.quickLighter');
  if (label === 'Coffee nearby') return t('explore.coffee');
  if (label === 'Rain backup') return t('assistant.quickIndoor');
  return label;
}

function intentFromSuggestion(value: string): AgentIntent {
  const lower = value.toLowerCase();
  if (lower.includes('budget') || lower.includes('cheap') || lower.includes('ucuz') || lower.includes('bütçe')) return 'BUDGET_OPTIMIZE';
  if (lower.includes('rain') || lower.includes('weather') || lower.includes('indoor') || lower.includes('inside') || lower.includes('covered') || lower.includes('yağmur') || lower.includes('kapalı')) return 'RAIN_REPLAN';
  if (lower.includes('coffee') || lower.includes('dinner') || lower.includes('food') || lower.includes('kahve') || lower.includes('yemek')) return 'ADD_FOOD_STOP';
  if (lower.includes('replace') || lower.includes('change') || lower.includes('değiştir')) return 'REPLACE_STOP';
  if (lower.includes('light') || lower.includes('easy') || lower.includes('short') || lower.includes('slow') || lower.includes('tired') || lower.includes('finish earlier') || lower.includes('yorul') || lower.includes('yorgun') || lower.includes('hafif')) return 'MAKE_DAY_LIGHTER';
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
      ? 'Backend needed to apply this preview.'
      : 'Ask for lighter, cheaper, food-focused or rain-ready.',
    reasons: [
      'Current trip',
      'Preview first',
    ],
    requiresConfirmation,
  };
}

function previewImpactLabel(preview: AgentActionPreview) {
  if (preview.minutesSaved && preview.minutesSaved > 0) {
    return `${preview.minutesSaved} min saved`;
  }
  if (preview.intent === 'ADD_FOOD_STOP') {
    return 'Better break';
  }
  if (preview.intent === 'RAIN_REPLAN') {
    return 'Indoor-ready';
  }
  if (preview.intent === 'BUDGET_OPTIMIZE') {
    return 'Lower cost';
  }
  return 'Route fit';
}

function dayNumberForTrip(trip?: TripResponse) {
  if (!trip) return 1;
  const today = startOfDay(new Date());
  const start = startOfDay(new Date(trip.startDate));
  const end = startOfDay(new Date(trip.endDate));
  if (today < start) return 1;
  if (today >= end) return trip.days;
  return Math.min(trip.days, Math.max(1, Math.floor((today.getTime() - start.getTime()) / 86400000) + 1));
}

function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function afterStopCount(day: ItineraryDay, preview: AgentActionPreview) {
  if (preview.intent === 'MAKE_DAY_LIGHTER') return Math.max(1, day.stopCount - 1);
  if (preview.intent === 'ADD_FOOD_STOP') return day.stopCount + 1;
  return day.stopCount;
}

function afterWalkKm(day: ItineraryDay, preview: AgentActionPreview) {
  if (preview.intent === 'MAKE_DAY_LIGHTER') return Math.max(1.2, day.walkKm - 1.1);
  if (preview.intent === 'RAIN_REPLAN') return Math.max(1.2, day.walkKm - 0.4);
  if (preview.intent === 'ADD_FOOD_STOP') return day.walkKm + 0.3;
  if (preview.intent === 'BUDGET_OPTIMIZE') return Math.max(1.2, day.walkKm - 0.2);
  return day.walkKm;
}

function isWeatherSensitiveStop(stop: ItineraryDay['stops'][number]) {
  const category = stop.category.toUpperCase();
  const title = stop.title.toUpperCase();
  return category.includes('WALKING')
    || category.includes('FREE')
    || title.includes('WALK')
    || title.includes('PARK')
    || title.includes('GARDEN')
    || title.includes('WATERFRONT')
    || title.includes('VIEW');
}

function ContextStat({
  label,
  value,
  icon,
  styles,
}: {
  label: string;
  value: string;
  icon: IoniconName;
  styles: ReturnType<typeof createStyles>;
}) {
  return (
    <View style={styles.contextStat}>
      <Ionicons name={icon} size={14} color="#A989AA" />
      <Text style={styles.contextStatValue}>{value}</Text>
      <Text style={styles.contextStatLabel}>{label}</Text>
    </View>
  );
}

type AgentVisual = {
  label: string;
  short: string;
  icon: IoniconName;
};

function agentVisual(intent: AgentIntent): AgentVisual {
  const visuals: Record<AgentIntent, AgentVisual> = {
    MAKE_DAY_LIGHTER: {
      label: 'Pace Agent',
      short: 'Lighter',
      icon: 'walk-outline',
    },
    ADD_FOOD_STOP: {
      label: 'Food Agent',
      short: 'Break',
      icon: 'restaurant-outline',
    },
    REPLACE_STOP: {
      label: 'Route Agent',
      short: 'Swap',
      icon: 'git-branch-outline',
    },
    BUDGET_OPTIMIZE: {
      label: 'Budget Agent',
      short: 'Budget',
      icon: 'wallet-outline',
    },
    RAIN_REPLAN: {
      label: 'Weather Agent',
      short: 'Rain',
      icon: 'rainy-outline',
    },
    GENERAL_GUIDANCE: {
      label: 'Journy Agent',
      short: 'Guide',
      icon: 'sparkles-outline',
    },
  };
  return visuals[intent];
}

function buildApplyResultMessage(intent: AgentIntent, updatedDay: ItineraryDay) {
  const base = `Day ${updatedDay.dayNumber} is now "${updatedDay.title}" with ${updatedDay.stopCount} stops and ${updatedDay.walkKm.toFixed(1)} km of walking.`;
  if (intent === 'ADD_FOOD_STOP') {
    return `Done. I added a local break to the route. ${base}`;
  }
  if (intent === 'RAIN_REPLAN') {
    return `Done. I made the day more rain-ready with an indoor-friendly adjustment. ${base}`;
  }
  if (intent === 'BUDGET_OPTIMIZE') {
    return `Done. I switched one flexible part of the route toward a lower-cost local option. ${base}`;
  }
  if (intent === 'MAKE_DAY_LIGHTER') {
    return `Done. I reduced pressure on the day while keeping the main anchors. ${base}`;
  }
  if (intent === 'REPLACE_STOP') {
    return `Done. I replaced one stop while preserving the route shape. ${base}`;
  }
  return `Done. ${base}`;
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
  contextCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginBottom: spacing.md,
    padding: spacing.md,
  },
  contextTop: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: spacing.sm,
    justifyContent: 'space-between',
  },
  contextTitleWrap: {
    flex: 1,
    minWidth: 0,
  },
  contextKicker: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  contextTitle: {
    color: colors.midnight,
    fontSize: typography.h3,
    fontWeight: '900',
    lineHeight: 22,
    marginTop: 3,
  },
  contextBadge: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    flexShrink: 0,
    flexDirection: 'row',
    gap: 4,
    paddingHorizontal: spacing.sm,
    paddingVertical: 5,
  },
  contextBadgeText: {
    color: colors.teal,
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  contextSummary: {
    color: colors.slate,
    fontSize: typography.small,
    fontWeight: '800',
    lineHeight: 19,
    marginTop: spacing.sm,
  },
  contextStats: {
    flexDirection: 'row',
    gap: spacing.xs,
    marginTop: spacing.md,
  },
  contextStat: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderRadius: radius.md,
    flex: 1,
    minHeight: 72,
    padding: spacing.sm,
  },
  contextStatValue: {
    color: colors.midnight,
    fontSize: 13,
    fontWeight: '900',
    marginTop: 5,
  },
  contextStatLabel: {
    color: colors.slate,
    fontSize: 10,
    fontWeight: '800',
    marginTop: 2,
  },
  contextHint: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    flexDirection: 'row',
    gap: spacing.xs,
    marginTop: spacing.md,
    padding: spacing.sm,
  },
  contextHintText: {
    color: colors.midnight,
    flex: 1,
    fontSize: 11,
    fontWeight: '800',
    lineHeight: 16,
  },
  weatherAgentCard: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.sm,
    padding: spacing.sm,
  },
  weatherAgentIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    height: 38,
    justifyContent: 'center',
    width: 38,
  },
  weatherAgentCopy: {
    flex: 1,
    minWidth: 0,
  },
  weatherAgentKicker: {
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  weatherAgentText: {
    color: colors.midnight,
    fontSize: typography.tiny,
    fontWeight: '800',
    lineHeight: 16,
    marginTop: 2,
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
    borderRadius: radius.lg,
    borderWidth: 1,
    marginTop: spacing.sm,
    padding: spacing.md,
  },
  previewHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
  },
  intentBadge: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 5,
    paddingHorizontal: spacing.sm,
    paddingVertical: 5,
  },
  intentBadgeText: {
    color: colors.midnight,
    fontSize: 10,
    fontWeight: '900',
  },
  previewConfidence: {
    color: colors.softMuted,
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  previewTitle: {
    color: colors.midnight,
    fontSize: typography.body,
    fontWeight: '900',
    lineHeight: 22,
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
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  previewMeta: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.md,
    borderWidth: 1,
    flex: 1,
    minHeight: 70,
    padding: spacing.sm,
  },
  previewMetaLabel: {
    color: colors.slate,
    fontSize: 9,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  previewMetaValue: {
    color: colors.midnight,
    fontSize: 12,
    fontWeight: '900',
    lineHeight: 16,
    marginTop: 4,
  },
  beforeAfterCard: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    gap: spacing.sm,
    justifyContent: 'space-between',
    marginTop: spacing.sm,
    padding: spacing.sm,
  },
  beforeAfterColumn: {
    flex: 1,
  },
  beforeAfterLabel: {
    color: colors.softMuted,
    fontSize: 9,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  beforeAfterValue: {
    color: colors.midnight,
    fontSize: 13,
    fontWeight: '900',
    marginTop: 3,
  },
  beforeAfterDetail: {
    color: colors.slate,
    fontSize: 10,
    fontWeight: '800',
    marginTop: 2,
  },
  affectedBlock: {
    marginTop: spacing.md,
  },
  blockLabel: {
    color: colors.softMuted,
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 0,
    marginBottom: spacing.xs,
    textTransform: 'uppercase',
  },
  stopChipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.xs,
  },
  stopChip: {
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.sm,
    paddingVertical: 5,
  },
  stopChipText: {
    color: colors.midnight,
    fontSize: 11,
    fontWeight: '900',
  },
  summaryStrip: {
    alignItems: 'flex-start',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    gap: spacing.xs,
    marginTop: spacing.md,
    padding: spacing.sm,
  },
  previewRoute: {
    color: colors.slate,
    fontSize: 11,
    fontWeight: '800',
    flex: 1,
    lineHeight: 16,
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
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    flexDirection: 'row',
    flex: 1,
    gap: spacing.xs,
    height: 42,
    justifyContent: 'center',
    paddingHorizontal: spacing.md,
  },
  actionRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing.xs,
    marginTop: spacing.md,
    width: '100%',
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
  dismissButton: {
    alignItems: 'center',
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    height: 42,
    justifyContent: 'center',
    minWidth: 96,
    paddingHorizontal: spacing.md,
  },
  dismissButtonText: {
    color: colors.slate,
    fontSize: 11,
    fontWeight: '900',
  },
  resultCard: {
    backgroundColor: colors.surfaceWarm,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    marginTop: spacing.sm,
    padding: spacing.md,
  },
  resultTop: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing.sm,
  },
  resultIcon: {
    alignItems: 'center',
    backgroundColor: colors.teal,
    borderRadius: radius.md,
    height: 34,
    justifyContent: 'center',
    width: 34,
  },
  resultTitleWrap: {
    flex: 1,
  },
  resultEyebrow: {
    color: colors.softMuted,
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  resultTitle: {
    color: colors.midnight,
    fontSize: typography.small,
    fontWeight: '900',
    marginTop: 2,
  },
  resultStats: {
    flexDirection: 'row',
    gap: spacing.xs,
    marginTop: spacing.md,
  },
  resultStat: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.md,
    borderWidth: 1,
    flex: 1,
    padding: spacing.sm,
  },
  resultStatValue: {
    color: colors.midnight,
    fontSize: 13,
    fontWeight: '900',
  },
  resultStatLabel: {
    color: colors.slate,
    fontSize: 10,
    fontWeight: '800',
    marginTop: 2,
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
