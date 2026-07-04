import React, { useMemo, useState } from 'react';
import { Alert, ImageBackground, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../navigation/AppNavigator';
import type { CreateTripRequest } from '../api/types';
import { useAppTheme } from '../theme/ThemeContext';

type Props = NativeStackScreenProps<RootStackParamList, 'TripSetup'>;
type IconName = React.ComponentProps<typeof Ionicons>['name'];

const cityDetails = {
  Discover: {
    image:
      'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=900&q=88',
    meta: 'Choose a city, dates and travel style',
  },
  Amsterdam: {
    image:
      'https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?auto=format&fit=crop&w=900&q=88',
    meta: 'Canals - coffee - museums',
  },
  Rome: {
    image:
      'https://images.unsplash.com/photo-1552832230-c0197dd311b5?auto=format&fit=crop&w=900&q=88',
    meta: 'History - piazzas - dinner',
  },
  Barcelona: {
    image:
      'https://images.unsplash.com/photo-1583422409516-2895a77efded?auto=format&fit=crop&w=900&q=88',
    meta: 'Design - beach - tapas',
  },
  Paris: {
    image:
      'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=900&q=88',
    meta: 'Museums - bakeries - walks',
  },
};
const cities = Object.keys(cityDetails).filter((item) => item !== 'Discover');
const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const years = [2026, 2027, 2028];
const weekDays = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
const interests: Array<{ label: string; icon: IconName }> = [
  { label: 'Coffee', icon: 'cafe-outline' },
  { label: 'Museums', icon: 'color-palette-outline' },
  { label: 'Local food', icon: 'restaurant-outline' },
  { label: 'Walking', icon: 'walk-outline' },
  { label: 'Shopping', icon: 'bag-outline' },
  { label: 'Nightlife', icon: 'moon-outline' },
];

export default function TripSetupScreen({ navigation }: Props) {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const [city, setCity] = useState('');
  const [cityOpen, setCityOpen] = useState(false);
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [monthIndex, setMonthIndex] = useState(9);
  const [year, setYear] = useState(2026);
  const [startDay, setStartDay] = useState<number | null>(null);
  const [endDay, setEndDay] = useState<number | null>(null);
  const [selectedInterests, setSelectedInterests] = useState<string[]>([]);
  const [travelType, setTravelType] = useState('Couple');
  const [budget, setBudget] = useState('Balanced');
  const selectedCity = city ? cityDetails[city as keyof typeof cityDetails] : cityDetails.Discover;

  const daysInMonth = useMemo(() => new Date(year, monthIndex + 1, 0).getDate(), [monthIndex, year]);
  const firstOffset = useMemo(() => (new Date(year, monthIndex, 1).getDay() + 6) % 7, [monthIndex, year]);
  const calendarCells = useMemo(
    () => Array.from({ length: Math.ceil((firstOffset + daysInMonth) / 7) * 7 }, (_, index) => {
      const day = index - firstOffset + 1;
      return day > 0 && day <= daysInMonth ? day : null;
    }),
    [daysInMonth, firstOffset],
  );
  const dateLabel = startDay && endDay
    ? `${months[monthIndex]} ${startDay} - ${months[monthIndex]} ${endDay}, ${year}`
    : 'Choose travel dates';
  const startDate = startDay ? toDateString(year, monthIndex, startDay) : null;
  const endDate = endDay ? toDateString(year, monthIndex, endDay) : null;

  const selectDay = (day: number) => {
    if (!startDay || !endDay || day <= startDay || endDay !== startDay) {
      setStartDay(day);
      setEndDay(day);
      return;
    }

    setEndDay(day);
    setCalendarOpen(false);
  };

  const shiftMonth = (direction: -1 | 1) => {
    setMonthIndex((current) => {
      const next = current + direction;
      if (next < 0) {
        setYear((value) => value - 1);
        return 11;
      }
      if (next > 11) {
        setYear((value) => value + 1);
        return 0;
      }
      return next;
    });
    setStartDay(null);
    setEndDay(null);
  };

  const toggleInterest = (interest: string) => {
    setSelectedInterests((current) =>
      current.includes(interest) ? current.filter((item) => item !== interest) : [...current, interest],
    );
  };

  const generatePlan = () => {
    if (!city.trim()) {
      Alert.alert('Destination required', 'Please choose a city before generating a plan.');
      return;
    }
    if (!startDate || !endDate) {
      Alert.alert('Travel dates required', 'Please choose your start and end dates from the calendar.');
      setCalendarOpen(true);
      return;
    }
    if (selectedInterests.length === 0) {
      Alert.alert('Choose at least one interest', 'Journy needs a few taste signals to build a useful route.');
      return;
    }
    if (new Date(endDate) < new Date(startDate)) {
      Alert.alert('Invalid dates', 'End date cannot be before the start date.');
      return;
    }

    const tripDraft: CreateTripRequest = {
      destination: city,
      startDate,
      endDate,
      travelerType: mapTravelerType(travelType),
      budget: mapBudget(budget),
      pace: 'BALANCED',
      interests: selectedInterests.map(mapInterest),
    };

    navigation.replace('LoadingPlan', { tripDraft });
  };

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.ivory} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <TouchableOpacity
            style={styles.backButton}
            activeOpacity={0.86}
            onPress={() => (navigation.canGoBack() ? navigation.goBack() : navigation.navigate('Welcome'))}
          >
            <Ionicons name="arrow-back" size={21} color={colors.midnight} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>Plan setup</Text>
          <Text style={styles.stepText}>1/3</Text>
        </View>

        <ImageBackground source={{ uri: selectedCity.image }} style={styles.cityPreview} imageStyle={styles.cityPreviewImage}>
          <LinearGradient colors={['rgba(33,43,45,0.02)', 'rgba(33,43,45,0.68)']} style={styles.cityPreviewOverlay}>
            <View style={styles.cityBadge}>
              <Ionicons name="sparkles-outline" size={13} color={colors.surface} />
              <Text style={styles.cityBadgeText}>Trip draft</Text>
            </View>
            <View>
              <Text style={styles.cityName}>{city || 'Choose your city'}</Text>
              <Text style={styles.cityMeta}>{selectedCity.meta}</Text>
            </View>
          </LinearGradient>
        </ImageBackground>

        <View style={styles.panel}>
          <PickerRow
            label="Destination"
            value={city || 'Select destination'}
            icon="location-outline"
            expanded={cityOpen}
            onPress={() => setCityOpen((value) => !value)}
            colors={colors}
            styles={styles}
          />
          {cityOpen ? (
            <View style={styles.dropdown}>
              {cities.map((item) => (
                <TouchableOpacity
                  key={item}
                  style={styles.dropdownItem}
                  activeOpacity={0.86}
                  onPress={() => {
                    setCity(item);
                    setCityOpen(false);
                  }}
                >
                  <Text style={[styles.dropdownText, item === city && styles.dropdownTextActive]}>{item}</Text>
                  {item === city ? <Ionicons name="checkmark" size={18} color={colors.teal} /> : null}
                </TouchableOpacity>
              ))}
            </View>
          ) : null}

          <View style={styles.divider} />

          <PickerRow
            label="Dates"
            value={dateLabel}
            icon="calendar-outline"
            expanded={calendarOpen}
            onPress={() => setCalendarOpen((value) => !value)}
            colors={colors}
            styles={styles}
          />

          {calendarOpen ? (
            <View style={styles.calendar}>
              <View style={styles.calendarTop}>
                <TouchableOpacity style={styles.monthButton} onPress={() => shiftMonth(-1)}>
                  <Ionicons name="chevron-back" size={18} color={colors.midnight} />
                </TouchableOpacity>
                <View style={styles.monthCenter}>
                  <Text style={styles.monthTitle}>{months[monthIndex]} {year}</Text>
                  <View style={styles.yearRow}>
                    {years.map((item) => (
                      <TouchableOpacity key={item} onPress={() => setYear(item)} style={[styles.yearChip, item === year && styles.yearChipActive]}>
                        <Text style={[styles.yearText, item === year && styles.yearTextActive]}>{item}</Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
                <TouchableOpacity style={styles.monthButton} onPress={() => shiftMonth(1)}>
                  <Ionicons name="chevron-forward" size={18} color={colors.midnight} />
                </TouchableOpacity>
              </View>

              <View style={styles.weekRow}>
                {weekDays.map((item, index) => (
                  <Text key={`${item}-${index}`} style={styles.weekText}>{item}</Text>
                ))}
              </View>
              <View style={styles.daysGrid}>
                {calendarCells.map((day, index) => {
                  const selected = day !== null && (day === startDay || day === endDay);
                  const inRange = day !== null && startDay !== null && endDay !== null && day > startDay && day < endDay;
                  return (
                    <TouchableOpacity
                      key={`${day ?? 'blank'}-${index}`}
                      style={[styles.dayCell, inRange && styles.dayRange, selected && styles.daySelected]}
                      disabled={!day}
                      activeOpacity={0.82}
                      onPress={() => day && selectDay(day)}
                    >
                      <Text style={[styles.dayText, selected && styles.dayTextSelected]}>{day ?? ''}</Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>
          ) : null}
        </View>

        <View style={styles.readinessCard}>
          <View style={styles.readinessHeader}>
            <Text style={styles.readinessTitle}>Plan readiness</Text>
            <Text style={styles.readinessMeta}>
              {[city, startDate && endDate ? 'dates' : '', selectedInterests.length ? 'taste' : ''].filter(Boolean).length}/3
            </Text>
          </View>
          <View style={styles.signalRow}>
            <Signal label="City" value={city || 'Not selected'} active={Boolean(city)} />
            <Signal label="Dates" value={startDate && endDate ? `${startDay}-${endDay} ${months[monthIndex]}` : 'Not selected'} active={Boolean(startDate && endDate)} />
            <Signal label="Taste" value={selectedInterests.length ? `${selectedInterests.length} interests` : 'Not selected'} active={selectedInterests.length > 0} />
          </View>
        </View>

        <Section title="Travelers" value={travelType} styles={styles} />
        <Segment options={['Solo', 'Couple', 'Friends', 'Family']} value={travelType} onChange={setTravelType} styles={styles} />

        <Section title="Interests" value={`${selectedInterests.length} selected`} styles={styles} />
        <View style={styles.interestGrid}>
          {interests.map((item) => {
            const active = selectedInterests.includes(item.label);
            return (
              <TouchableOpacity key={item.label} style={[styles.interest, active && styles.interestActive]} onPress={() => toggleInterest(item.label)} activeOpacity={0.86}>
                <Ionicons name={item.icon} size={18} color={active ? colors.surface : colors.teal} />
                <Text style={[styles.interestText, active && styles.interestTextActive]}>{item.label}</Text>
              </TouchableOpacity>
            );
          })}
        </View>

        <Section title="Budget" value={budget} styles={styles} />
        <Segment options={['Lean', 'Balanced', 'Comfort']} value={budget} onChange={setBudget} styles={styles} />

        <TouchableOpacity style={styles.primaryButton} activeOpacity={0.9} onPress={generatePlan}>
          <Text style={styles.primaryButtonText}>Generate plan</Text>
          <Ionicons name="arrow-forward" size={18} color={colors.surface} />
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

function toDateString(year: number, monthIndex: number, day: number) {
  const month = String(monthIndex + 1).padStart(2, '0');
  const date = String(day).padStart(2, '0');
  return `${year}-${month}-${date}`;
}

function mapTravelerType(value: string): CreateTripRequest['travelerType'] {
  const map: Record<string, CreateTripRequest['travelerType']> = {
    Solo: 'SOLO',
    Couple: 'COUPLE',
    Friends: 'FRIENDS',
    Family: 'FAMILY',
  };
  return map[value] ?? 'COUPLE';
}

function mapBudget(value: string): CreateTripRequest['budget'] {
  const map: Record<string, CreateTripRequest['budget']> = {
    Lean: 'LEAN',
    Balanced: 'BALANCED',
    Comfort: 'COMFORT',
  };
  return map[value] ?? 'BALANCED';
}

function mapInterest(value: string): CreateTripRequest['interests'][number] {
  const map: Record<string, CreateTripRequest['interests'][number]> = {
    Coffee: 'COFFEE',
    Museums: 'MUSEUMS',
    'Local food': 'LOCAL_FOOD',
    Walking: 'WALKING',
    Shopping: 'SHOPPING',
    Nightlife: 'NIGHTLIFE',
  };
  return map[value] ?? 'WALKING';
}

function PickerRow({
  label,
  value,
  icon,
  expanded,
  onPress,
  colors,
  styles,
}: {
  label: string;
  value: string;
  icon: IconName;
  expanded: boolean;
  onPress: () => void;
  colors: Theme['colors'];
  styles: TripSetupStyles;
}) {
  return (
    <TouchableOpacity style={styles.pickerRow} activeOpacity={0.86} onPress={onPress}>
      <View style={styles.pickerIcon}>
        <Ionicons name={icon} size={18} color={colors.teal} />
      </View>
      <View style={styles.pickerCopy}>
        <Text style={styles.pickerLabel}>{label}</Text>
        <Text style={styles.pickerValue}>{value}</Text>
      </View>
      <Ionicons name={expanded ? 'chevron-up' : 'chevron-down'} size={18} color={colors.slate} />
    </TouchableOpacity>
  );
}

type Theme = ReturnType<typeof useAppTheme>['theme'];
type TripSetupStyles = ReturnType<typeof createStyles>;

function Section({ title, value, styles }: { title: string; value: string; styles: TripSetupStyles }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <Text style={styles.sectionValue}>{value}</Text>
    </View>
  );
}

function Segment({
  options,
  value,
  onChange,
  styles,
}: {
  options: string[];
  value: string;
  onChange: (value: string) => void;
  styles: TripSetupStyles;
}) {
  return (
    <View style={styles.segment}>
      {options.map((item) => (
        <TouchableOpacity key={item} style={[styles.segmentItem, item === value && styles.segmentItemActive]} onPress={() => onChange(item)} activeOpacity={0.86}>
          <Text style={[styles.segmentText, item === value && styles.segmentTextActive]}>{item}</Text>
        </TouchableOpacity>
      ))}
    </View>
  );
}

function Signal({
  label,
  value,
  active,
}: {
  label: string;
  value: string;
  active: boolean;
}) {
  const { theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;

  return (
    <View style={[styles.signal, active && styles.signalActive]}>
      <Ionicons name={active ? 'checkmark-circle' : 'ellipse-outline'} size={16} color={active ? colors.teal : colors.softMuted} />
      <Text style={styles.signalLabel}>{label}</Text>
      <Text style={styles.signalValue}>{value}</Text>
    </View>
  );
}

function createStyles({ colors, radius, spacing, typography }: Theme) {
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
  headerTitle: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
  stepText: { color: colors.slate, fontSize: typography.small, fontWeight: '900', width: 44, textAlign: 'right' },
  cityPreview: {
    height: 178,
    marginTop: spacing.xl,
  },
  cityPreviewImage: {
    borderRadius: radius.xl,
  },
  cityPreviewOverlay: {
    borderRadius: radius.xl,
    flex: 1,
    justifyContent: 'space-between',
    padding: spacing.md,
  },
  cityBadge: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(255,255,255,0.18)',
    borderColor: 'rgba(255,255,255,0.28)',
    borderRadius: radius.pill,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 5,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  cityBadgeText: {
    color: colors.surface,
    fontSize: typography.tiny,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  cityName: {
    color: colors.surface,
    fontSize: 32,
    fontWeight: '900',
    lineHeight: 36,
  },
  cityMeta: {
    color: 'rgba(255,255,255,0.82)',
    fontSize: typography.small,
    fontWeight: '800',
    marginTop: 3,
  },
  panel: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.md,
    padding: spacing.md,
  },
  readinessCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.md,
    padding: spacing.md,
  },
  readinessHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  readinessTitle: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  readinessMeta: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900' },
  signalRow: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm },
  signal: {
    backgroundColor: colors.fog,
    borderRadius: radius.lg,
    flex: 1,
    minHeight: 82,
    padding: spacing.sm,
  },
  signalActive: { backgroundColor: colors.surfaceWarm },
  signalLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900', marginTop: spacing.xs },
  signalValue: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900', lineHeight: 15, marginTop: 3 },
  pickerRow: { alignItems: 'center', flexDirection: 'row', minHeight: 62 },
  pickerIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    height: 40,
    justifyContent: 'center',
    marginRight: spacing.md,
    width: 40,
  },
  pickerCopy: { flex: 1 },
  pickerLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  pickerValue: { color: colors.midnight, fontSize: typography.body, fontWeight: '900', marginTop: 3 },
  divider: { backgroundColor: colors.mist, height: 1, marginVertical: spacing.sm },
  dropdown: {
    backgroundColor: colors.fog,
    borderRadius: radius.lg,
    marginBottom: spacing.sm,
    padding: spacing.xs,
  },
  dropdownItem: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    minHeight: 42,
    paddingHorizontal: spacing.md,
  },
  dropdownText: { color: colors.slate, fontSize: typography.small, fontWeight: '900' },
  dropdownTextActive: { color: colors.midnight },
  calendar: { backgroundColor: colors.fog, borderRadius: radius.lg, marginTop: spacing.md, padding: spacing.md },
  calendarTop: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  monthButton: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    height: 36,
    justifyContent: 'center',
    width: 36,
  },
  monthCenter: { alignItems: 'center', flex: 1 },
  monthTitle: { color: colors.midnight, fontSize: typography.body, fontWeight: '900' },
  yearRow: { flexDirection: 'row', gap: spacing.xs, marginTop: spacing.sm },
  yearChip: { borderRadius: radius.pill, paddingHorizontal: spacing.sm, paddingVertical: 4 },
  yearChipActive: { backgroundColor: colors.surface },
  yearText: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900' },
  yearTextActive: { color: colors.teal },
  weekRow: { flexDirection: 'row', marginTop: spacing.md },
  weekText: { color: colors.slate, flex: 1, fontSize: typography.tiny, fontWeight: '900', textAlign: 'center' },
  daysGrid: { flexDirection: 'row', flexWrap: 'wrap', marginTop: spacing.xs },
  dayCell: {
    alignItems: 'center',
    aspectRatio: 1,
    borderRadius: radius.md,
    justifyContent: 'center',
    width: `${100 / 7}%`,
  },
  dayRange: { backgroundColor: colors.surface },
  daySelected: { backgroundColor: colors.teal },
  dayText: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  dayTextSelected: { color: colors.surface },
  section: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing.xl,
  },
  sectionTitle: { color: colors.midnight, fontSize: typography.h3, fontWeight: '900' },
  sectionValue: { color: colors.teal, fontSize: typography.small, fontWeight: '900' },
  segment: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    gap: spacing.xs,
    marginTop: spacing.md,
    padding: spacing.xs,
  },
  segmentItem: { alignItems: 'center', borderRadius: radius.md, flex: 1, paddingVertical: spacing.sm },
  segmentItemActive: { backgroundColor: colors.teal },
  segmentText: { color: colors.slate, fontSize: typography.small, fontWeight: '900' },
  segmentTextActive: { color: colors.surface },
  interestGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginTop: spacing.md },
  interest: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    gap: spacing.sm,
    padding: spacing.md,
    width: '48%',
  },
  interestActive: { backgroundColor: colors.teal, borderColor: colors.teal },
  interestText: { color: colors.midnight, flex: 1, fontSize: typography.small, fontWeight: '900' },
  interestTextActive: { color: colors.surface },
  primaryButton: {
    alignItems: 'center',
    backgroundColor: colors.midnight,
    borderRadius: radius.lg,
    flexDirection: 'row',
    gap: spacing.sm,
    justifyContent: 'center',
    marginTop: spacing.xl,
    minHeight: 58,
  },
  primaryButtonText: { color: colors.surface, fontSize: typography.body, fontWeight: '900' },
});
}
