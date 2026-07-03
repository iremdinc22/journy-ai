import React, { useEffect, useRef, useState } from 'react';
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
import { colors, radius, spacing, typography } from '../theme/colors';

type Message = {
  id: string;
  role: 'ai' | 'user';
  text: string;
  time?: string;
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
  const [messages, setMessages] = useState<Message[]>(initialMessages);
  const [input, setInput] = useState('');
  const [keyboardHeight, setKeyboardHeight] = useState(0);
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

  const sendPrompt = (prompt: string, answer?: string) => {
    const cleanPrompt = prompt.trim();
    if (!cleanPrompt) return;

    const timestamp = Date.now().toString();
    setMessages((current) => [
      ...current,
      { id: `${timestamp}-user`, role: 'user', text: cleanPrompt },
      { id: `${timestamp}-ai`, role: 'ai', text: answer ?? buildAnswer(cleanPrompt), time: 'Now' },
    ]);
    setInput('');
    requestAnimationFrame(() => scrollRef.current?.scrollToEnd({ animated: true }));
  };

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="dark-content" backgroundColor={colors.ivory} />
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
              style={[styles.sendButton, !input.trim() && styles.sendButtonDisabled]}
              activeOpacity={0.86}
              disabled={!input.trim()}
              onPress={() => sendPrompt(input)}
            >
              <Ionicons name="arrow-up" size={18} color={colors.surface} />
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

const styles = StyleSheet.create({
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
