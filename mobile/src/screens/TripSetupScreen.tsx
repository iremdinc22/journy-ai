import React, { useEffect, useMemo, useState } from 'react';
import { Alert, ImageBackground, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../navigation/AppNavigator';
import { destinationApi, tripApi } from '../api/journyApi';
import type { CreateTripRequest, DestinationResponse, TripPreviewResponse } from '../api/types';
import { useAppTheme } from '../theme/ThemeContext';

type Props = NativeStackScreenProps<RootStackParamList, 'TripSetup'>;
type IconName = React.ComponentProps<typeof Ionicons>['name'];
type CityDetail = { image: string; meta: string };

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
const paceOptions = ['Relaxed', 'Balanced', 'Full'];
const defaultStartSuggestions = ['Hotel area', 'Main station', 'Old town', 'City center'];
const cityStartSuggestions: Record<string, string[]> = {
  Amsterdam: ['Centraal Station', 'Jordaan', 'De Pijp', 'Museumplein'],
  Paris: ['Saint-Germain', 'Le Marais', 'Latin Quarter', 'Montmartre'],
  Rome: ['Trastevere', 'Centro Storico', 'Monti', 'Termini'],
  Barcelona: ['Eixample', 'Gothic Quarter', 'Gracia', 'El Born'],
};
const fallbackDestinations: DestinationResponse[] = cities.map((name) => ({
  id: `fallback-${name.toLowerCase()}`,
  name,
  country: name === 'Amsterdam' ? 'Netherlands' : name === 'Paris' ? 'France' : name === 'Rome' ? 'Italy' : 'Spain',
  description: cityDetails[name as keyof typeof cityDetails].meta,
  imageUrl: cityDetails[name as keyof typeof cityDetails].image,
  tags: cityDetails[name as keyof typeof cityDetails].meta,
  bestFor: cityDetails[name as keyof typeof cityDetails].meta,
  placeCount: 4,
  averageDailyWalkKm: 5.4,
  available: true,
  popular: true,
}));

export default function TripSetupScreen({ navigation }: Props) {
  const { isDark, theme } = useAppTheme();
  const styles = useMemo(() => createStyles(theme), [theme]);
  const { colors } = theme;
  const [city, setCity] = useState('');
  const [citySearch, setCitySearch] = useState('');
  const [cityOpen, setCityOpen] = useState(false);
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [monthIndex, setMonthIndex] = useState(9);
  const [year, setYear] = useState(2026);
  const [startDay, setStartDay] = useState<number | null>(null);
  const [endDay, setEndDay] = useState<number | null>(null);
  const [selectedInterests, setSelectedInterests] = useState<string[]>([]);
  const [travelType, setTravelType] = useState('Couple');
  const [budget, setBudget] = useState('Balanced');
  const [pace, setPace] = useState('Balanced');
  const [startArea, setStartArea] = useState('');
  const [destinations, setDestinations] = useState<DestinationResponse[]>([]);
  const [popularDestinations, setPopularDestinations] = useState<DestinationResponse[]>([]);
  const [destinationLoading, setDestinationLoading] = useState(false);
  const [backendPreview, setBackendPreview] = useState<TripPreviewResponse | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewLive, setPreviewLive] = useState(false);
  const destinationDetails = useMemo<Record<string, CityDetail>>(() => {
    const mapped = destinations.reduce<Record<string, { image: string; meta: string }>>((acc, destination) => {
      acc[destination.name] = {
        image: destination.imageUrl,
        meta: destination.tags,
      };
      return acc;
    }, {});
    return { ...cityDetails, ...mapped };
  }, [destinations]);
  const selectedCity = city ? destinationDetails[city] ?? destinationDetails.Discover : destinationDetails.Discover;
  const destinationOptions = destinations.length ? destinations : fallbackDestinations.filter((item) => item.name.toLowerCase().includes(citySearch.trim().toLowerCase()));
  const popularOptions = popularDestinations.length ? popularDestinations : fallbackDestinations.slice(0, 4);
  const canCreateDraftDestination = citySearch.trim().length > 1 && !destinationOptions.some((item) => item.name.toLowerCase() === citySearch.trim().toLowerCase());

  useEffect(() => {
    let mounted = true;
    destinationApi.popular()
      .then((response) => {
        if (mounted) {
          setPopularDestinations(response);
        }
      })
      .catch(() => {
        if (mounted) {
          setPopularDestinations([]);
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    setDestinationLoading(true);
    destinationApi.search(citySearch)
      .then((response) => {
        if (mounted) {
          setDestinations(response);
        }
      })
      .catch(() => {
        if (mounted) {
          setDestinations([]);
        }
      })
      .finally(() => {
        if (mounted) {
          setDestinationLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, [citySearch]);

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
  const tripDays = startDate && endDate ? Math.max(1, Math.round((new Date(endDate).getTime() - new Date(startDate).getTime()) / 86400000)) : 0;
  const previewStops = tripDays ? tripDays * stopsForPace(pace) : 0;
  const previewFocus = selectedInterests.slice(0, 3).join(' - ') || 'Choose taste signals';
  const startSuggestions = cityStartSuggestions[city] ?? defaultStartSuggestions;
  const estimatedDailyWalk = previewStops ? estimateDailyWalkKm(stopsForPace(pace), pace, budget) : 0;
  const routeStyle = routeStyleFor({ pace, budget, selectedInterests, startArea });
  const resolvedPreviewStops = backendPreview?.estimatedStops ?? previewStops;
  const resolvedDailyWalk = backendPreview?.dailyWalkKm ?? estimatedDailyWalk;
  const resolvedRouteStyle = backendPreview?.routeStyle ?? routeStyle;
  const resolvedPlaceCount = backendPreview?.availablePlaceCount ?? (destinationOptions.find((item) => item.name === city)?.placeCount ?? 0);
  const previewConfidence = backendPreview?.confidence ?? (city ? 'Draft' : 'Waiting');
  const previewSummary = backendPreview?.summary;

  useEffect(() => {
    if (!city.trim()) {
      setBackendPreview(null);
      setPreviewLive(false);
      setPreviewLoading(false);
      return;
    }

    let mounted = true;
    const timer = setTimeout(() => {
      setPreviewLoading(true);
      tripApi.preview({
        destination: city.trim(),
        startingArea: startArea.trim() || undefined,
        startDate: startDate ?? undefined,
        endDate: endDate ?? undefined,
        budget: mapBudget(budget),
        pace: mapPace(pace),
        interests: selectedInterests.map(mapInterest),
      })
        .then((response) => {
          if (mounted) {
            setBackendPreview(response);
            setPreviewLive(true);
          }
        })
        .catch(() => {
          if (mounted) {
            setBackendPreview(null);
            setPreviewLive(false);
          }
        })
        .finally(() => {
          if (mounted) {
            setPreviewLoading(false);
          }
        });
    }, 360);

    return () => {
      mounted = false;
      clearTimeout(timer);
    };
  }, [budget, city, endDate, pace, selectedInterests, startArea, startDate]);

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

  const selectDestination = (destination: DestinationResponse) => {
    setCity(destination.name);
    setCitySearch('');
    setCityOpen(false);
  };

  const createDraftDestination = () => {
    const draftCity = citySearch.trim();
    if (!draftCity) {
      return;
    }
    setCity(draftCity);
    setCityOpen(false);
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
      startingArea: startArea.trim() || undefined,
      startDate,
      endDate,
      travelerType: mapTravelerType(travelType),
      budget: mapBudget(budget),
      pace: mapPace(pace),
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
          <LinearGradient colors={['rgba(33,43,45,0.04)', 'rgba(33,43,45,0.74)']} style={styles.cityPreviewOverlay}>
            <View style={styles.cityBadge}>
              <Ionicons name="sparkles-outline" size={13} color={colors.surface} />
              <Text style={styles.cityBadgeText}>Personal trip setup</Text>
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
              <View style={styles.searchBox}>
                <Ionicons name="search-outline" size={16} color={colors.teal} />
                <TextInput
                  value={citySearch}
                  onChangeText={setCitySearch}
                  placeholder="Search city"
                  placeholderTextColor={colors.softMuted}
                  style={styles.searchInput}
                />
              </View>
              {!citySearch.trim() ? (
                <>
                  <Text style={styles.dropdownSectionTitle}>Popular destinations</Text>
                  <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.popularRail}>
                    {popularOptions.map((item) => (
                      <TouchableOpacity key={item.id} style={styles.popularCard} activeOpacity={0.86} onPress={() => selectDestination(item)}>
                        <ImageBackground source={{ uri: item.imageUrl }} style={styles.popularImage} imageStyle={styles.popularImageStyle}>
                          <LinearGradient colors={['rgba(33,43,45,0.02)', 'rgba(33,43,45,0.68)']} style={styles.popularOverlay}>
                            <Text style={styles.popularCity}>{item.name}</Text>
                            <Text style={styles.popularCountry}>{item.country}</Text>
                          </LinearGradient>
                        </ImageBackground>
                      </TouchableOpacity>
                    ))}
                  </ScrollView>
                </>
              ) : null}
              <Text style={styles.dropdownSectionTitle}>{citySearch.trim() ? 'Search results' : 'All available'}</Text>
              {destinationLoading ? <Text style={styles.emptyDropdown}>Searching destinations...</Text> : null}
              {destinationOptions.map((item) => (
                <TouchableOpacity
                  key={item.id}
                  style={[styles.destinationResult, item.name === city && styles.destinationResultActive]}
                  activeOpacity={0.86}
                  onPress={() => selectDestination(item)}
                >
                  <View style={styles.destinationThumb}>
                    <ImageBackground source={{ uri: item.imageUrl }} style={styles.destinationThumbImage} imageStyle={styles.destinationThumbImageStyle} />
                  </View>
                  <View style={styles.destinationCopy}>
                    <View style={styles.destinationTitleRow}>
                      <Text style={styles.destinationName}>{item.name}</Text>
                      {!item.available ? <Text style={styles.unavailablePill}>Draft</Text> : null}
                    </View>
                    <Text style={styles.destinationMeta}>{item.country} - {item.tags}</Text>
                    <Text style={styles.destinationSmall}>{item.placeCount} picks - {item.averageDailyWalkKm.toFixed(1)} km avg walk</Text>
                  </View>
                  {item.name === city ? <Ionicons name="checkmark-circle" size={19} color={colors.teal} /> : <Ionicons name="chevron-forward" size={17} color={colors.softMuted} />}
                </TouchableOpacity>
              ))}
              {!destinationOptions.length && !destinationLoading ? <Text style={styles.emptyDropdown}>No city match yet</Text> : null}
              {canCreateDraftDestination ? (
                <TouchableOpacity style={styles.draftCard} activeOpacity={0.86} onPress={createDraftDestination}>
                  <Ionicons name="add-circle-outline" size={20} color={colors.teal} />
                  <View style={styles.draftCopy}>
                    <Text style={styles.draftTitle}>Create a draft for {citySearch.trim()}</Text>
                    <Text style={styles.draftText}>Curated data is not ready yet, but Journy can still build a flexible starter plan.</Text>
                  </View>
                </TouchableOpacity>
              ) : null}
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

        <View style={styles.previewPanel}>
          <View style={styles.previewTop}>
            <Text style={styles.previewTitle}>Plan preview</Text>
            <View style={styles.previewBadge}>
              <Ionicons name="sparkles-outline" size={13} color={colors.teal} />
              <Text style={styles.previewBadgeText}>{previewLoading ? 'Updating' : previewLive ? `${previewConfidence} confidence` : `${[city, startDate && endDate ? 'dates' : '', selectedInterests.length ? 'taste' : '', pace, startArea.trim()].filter(Boolean).length}/5 ready`}</Text>
            </View>
          </View>
          <Text style={styles.previewMain}>
            {city ? `${city} - ${tripDays || 1} day ${pace.toLowerCase()} route` : 'Choose a city to start shaping your route'}
          </Text>
          <View style={styles.previewMetaRow}>
            <PreviewMetric icon="location-outline" label="Stops" value={resolvedPreviewStops ? `${resolvedPreviewStops}` : '--'} colors={colors} styles={styles} />
            <PreviewMetric icon="walk-outline" label="Daily walk" value={resolvedDailyWalk ? `${resolvedDailyWalk.toFixed(1)} km` : '--'} colors={colors} styles={styles} />
            <PreviewMetric icon="sparkles-outline" label="Route style" value={resolvedRouteStyle} colors={colors} styles={styles} />
          </View>
          <View style={styles.previewInsightRow}>
            <View style={styles.previewInsight}>
              <Ionicons name="albums-outline" size={14} color={colors.teal} />
              <Text style={styles.previewInsightText}>{resolvedPlaceCount ? `${resolvedPlaceCount} city picks available` : 'City picks appear after selecting a destination'}</Text>
            </View>
            <View style={styles.previewInsight}>
              <Ionicons name={previewLive ? 'cloud-done-outline' : 'phone-portrait-outline'} size={14} color={colors.teal} />
              <Text style={styles.previewInsightText}>{previewLive ? 'Backend preview' : 'Local fallback'}</Text>
            </View>
          </View>
          <Text style={styles.previewFocus}>
            {previewSummary ?? (city && tripDays
              ? `${previewFocus}. ${startArea.trim() ? `Day 1 starts around ${startArea.trim()}.` : 'First day starts flexibly around your chosen city.'}`
              : previewFocus)}
          </Text>
        </View>

        <View style={styles.startCard}>
          <View style={styles.startHeader}>
            <View style={styles.startTitleRow}>
              <View style={styles.startIcon}>
                <Ionicons name="business-outline" size={18} color={colors.teal} />
              </View>
              <View style={styles.startTitleCopy}>
                <Text style={styles.startLabel}>Starting area</Text>
                <Text style={styles.startHelper}>Optional, but it helps Journy keep the first route realistic.</Text>
              </View>
            </View>
            {startArea.trim() ? (
              <TouchableOpacity style={styles.startClearButton} activeOpacity={0.82} onPress={() => setStartArea('')}>
                <Ionicons name="close" size={15} color={colors.teal} />
              </TouchableOpacity>
            ) : null}
          </View>

          <View style={styles.startInputShell}>
            <Ionicons name="search-outline" size={16} color={colors.softMuted} />
            <TextInput
              value={startArea}
              onChangeText={setStartArea}
              placeholder={city ? `Search a ${city} area or hotel zone` : 'Hotel, station or neighborhood'}
              placeholderTextColor={colors.softMuted}
              style={styles.startInput}
            />
          </View>

          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.startSuggestionRail}>
            {startSuggestions.map((item) => {
              const active = startArea.trim().toLowerCase() === item.toLowerCase();
              return (
                <TouchableOpacity
                  key={item}
                  style={[styles.startSuggestion, active && styles.startSuggestionActive]}
                  activeOpacity={0.84}
                  onPress={() => setStartArea(item)}
                >
                  <Ionicons name={active ? 'checkmark-circle' : 'location-outline'} size={14} color={active ? colors.surface : colors.teal} />
                  <Text style={[styles.startSuggestionText, active && styles.startSuggestionTextActive]}>{item}</Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
        </View>

        <Section title="Travelers" value={travelType} styles={styles} />
        <Segment options={['Solo', 'Couple', 'Friends', 'Family']} value={travelType} onChange={setTravelType} styles={styles} />

        <Section title="Pace" value={pace} styles={styles} />
        <Segment options={paceOptions} value={pace} onChange={setPace} styles={styles} />

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

function mapPace(value: string): CreateTripRequest['pace'] {
  const map: Record<string, CreateTripRequest['pace']> = {
    Relaxed: 'RELAXED',
    Balanced: 'BALANCED',
    Full: 'FULL',
  };
  return map[value] ?? 'BALANCED';
}

function stopsForPace(value: string) {
  if (value === 'Relaxed') return 3;
  if (value === 'Full') return 5;
  return 4;
}

function estimateDailyWalkKm(stopsPerDay: number, pace: string, budget: string) {
  const paceBase = pace === 'Relaxed' ? 1.1 : pace === 'Full' ? 1.55 : 1.35;
  const budgetAdjustment = budget === 'Lean' ? -0.2 : budget === 'Comfort' ? 0.15 : 0;
  return Math.max(2.4, Math.round(stopsPerDay * (paceBase + budgetAdjustment) * 10) / 10);
}

function routeStyleFor({
  pace,
  budget,
  selectedInterests,
  startArea,
}: {
  pace: string;
  budget: string;
  selectedInterests: string[];
  startArea: string;
}) {
  if (pace === 'Relaxed') return 'Easy flow';
  if (budget === 'Lean') return 'Low-cost';
  if (selectedInterests.includes('Local food')) return 'Food-led';
  if (selectedInterests.includes('Museums')) return 'Culture-led';
  if (startArea.trim()) return 'Area-first';
  return 'Balanced';
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

function PreviewMetric({
  icon,
  label,
  value,
  colors,
  styles,
}: {
  icon: IconName;
  label: string;
  value: string;
  colors: Theme['colors'];
  styles: TripSetupStyles;
}) {
  return (
    <View style={styles.previewMetric}>
      <Ionicons name={icon} size={15} color={colors.teal} />
      <Text style={styles.previewMetricLabel}>{label}</Text>
      <Text style={styles.previewMetricValue} numberOfLines={1}>{value}</Text>
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
    height: 224,
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
    borderColor: 'rgba(255,255,255,0.32)',
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
    fontSize: 34,
    fontWeight: '900',
    lineHeight: 38,
  },
  cityMeta: {
    color: 'rgba(255,255,255,0.86)',
    fontSize: typography.small,
    fontWeight: '800',
    marginTop: 4,
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
  dropdownSectionTitle: {
    color: colors.slate,
    fontSize: typography.tiny,
    fontWeight: '900',
    marginBottom: spacing.xs,
    marginTop: spacing.xs,
    paddingHorizontal: spacing.xs,
    textTransform: 'uppercase',
  },
  popularRail: {
    gap: spacing.sm,
    paddingBottom: spacing.sm,
  },
  popularCard: {
    borderRadius: radius.lg,
    height: 116,
    overflow: 'hidden',
    width: 132,
  },
  popularImage: { flex: 1 },
  popularImageStyle: { borderRadius: radius.lg },
  popularOverlay: {
    flex: 1,
    justifyContent: 'flex-end',
    padding: spacing.sm,
  },
  popularCity: { color: colors.surface, fontSize: typography.body, fontWeight: '900' },
  popularCountry: { color: 'rgba(255,255,255,0.82)', fontSize: typography.tiny, fontWeight: '800', marginTop: 2 },
  searchBox: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    flexDirection: 'row',
    gap: spacing.xs,
    marginBottom: spacing.xs,
    minHeight: 42,
    paddingHorizontal: spacing.sm,
  },
  searchInput: {
    color: colors.midnight,
    flex: 1,
    fontSize: typography.small,
    fontWeight: '800',
    paddingVertical: 0,
  },
  emptyDropdown: {
    color: colors.slate,
    fontSize: typography.small,
    fontWeight: '800',
    padding: spacing.md,
    textAlign: 'center',
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
  destinationResult: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    marginBottom: spacing.xs,
    minHeight: 86,
    padding: spacing.sm,
  },
  destinationResultActive: { borderColor: colors.teal },
  destinationThumb: {
    borderRadius: radius.md,
    height: 58,
    marginRight: spacing.sm,
    overflow: 'hidden',
    width: 58,
  },
  destinationThumbImage: { flex: 1 },
  destinationThumbImageStyle: { borderRadius: radius.md },
  destinationCopy: { flex: 1 },
  destinationTitleRow: { alignItems: 'center', flexDirection: 'row', gap: spacing.xs },
  destinationName: { color: colors.midnight, fontSize: typography.body, fontWeight: '900' },
  unavailablePill: {
    backgroundColor: colors.lilac,
    borderRadius: radius.pill,
    color: colors.teal,
    fontSize: typography.tiny,
    fontWeight: '900',
    overflow: 'hidden',
    paddingHorizontal: spacing.xs,
    paddingVertical: 2,
    textTransform: 'uppercase',
  },
  destinationMeta: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', marginTop: 3 },
  destinationSmall: { color: colors.softMuted, fontSize: typography.tiny, fontWeight: '800', marginTop: 2 },
  draftCard: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderColor: colors.mist,
    borderRadius: radius.lg,
    borderWidth: 1,
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.xs,
    padding: spacing.md,
  },
  draftCopy: { flex: 1 },
  draftTitle: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  draftText: { color: colors.slate, fontSize: typography.tiny, fontWeight: '800', lineHeight: 16, marginTop: 3 },
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
  startCard: {
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.md,
    padding: spacing.md,
  },
  startHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  startTitleRow: {
    alignItems: 'center',
    flex: 1,
    flexDirection: 'row',
  },
  startIcon: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.md,
    height: 42,
    justifyContent: 'center',
    marginRight: spacing.sm,
    width: 42,
  },
  startTitleCopy: { flex: 1 },
  startLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  startHelper: {
    color: colors.slate,
    fontSize: typography.tiny,
    fontWeight: '800',
    lineHeight: 16,
    marginTop: 3,
  },
  startClearButton: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.pill,
    height: 30,
    justifyContent: 'center',
    marginLeft: spacing.sm,
    width: 30,
  },
  startInputShell: {
    alignItems: 'center',
    backgroundColor: colors.fog,
    borderRadius: radius.lg,
    flexDirection: 'row',
    gap: spacing.xs,
    marginTop: spacing.md,
    minHeight: 50,
    paddingHorizontal: spacing.md,
  },
  startInput: {
    color: colors.midnight,
    flex: 1,
    fontSize: typography.small,
    fontWeight: '900',
    padding: 0,
  },
  startSuggestionRail: {
    gap: spacing.xs,
    paddingTop: spacing.sm,
  },
  startSuggestion: {
    alignItems: 'center',
    backgroundColor: colors.surfaceWarm,
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 5,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  startSuggestionActive: {
    backgroundColor: colors.teal,
    borderColor: colors.teal,
  },
  startSuggestionText: {
    color: colors.midnight,
    fontSize: typography.tiny,
    fontWeight: '900',
  },
  startSuggestionTextActive: {
    color: colors.surface,
  },
  previewPanel: {
    backgroundColor: colors.surfaceWarm,
    borderColor: colors.mist,
    borderRadius: radius.xl,
    borderWidth: 1,
    marginTop: spacing.md,
    padding: spacing.md,
  },
  previewTop: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  previewTitle: { color: colors.midnight, fontSize: typography.small, fontWeight: '900' },
  previewBadge: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radius.pill,
    flexDirection: 'row',
    gap: 4,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  previewBadgeText: { color: colors.teal, fontSize: typography.tiny, fontWeight: '900', textTransform: 'uppercase' },
  previewMain: {
    color: colors.midnight,
    fontSize: typography.h2,
    fontWeight: '900',
    lineHeight: 29,
    marginTop: spacing.sm,
  },
  previewMetaRow: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.md },
  previewMetric: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    flex: 1,
    minHeight: 76,
    padding: spacing.sm,
  },
  previewMetricLabel: { color: colors.slate, fontSize: typography.tiny, fontWeight: '900', marginTop: spacing.xs },
  previewMetricValue: { color: colors.midnight, fontSize: typography.tiny, fontWeight: '900', marginTop: 2 },
  previewInsightRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginTop: spacing.sm,
  },
  previewInsight: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.mist,
    borderRadius: radius.pill,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 5,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
  },
  previewInsightText: {
    color: colors.slate,
    fontSize: typography.tiny,
    fontWeight: '900',
  },
  previewFocus: {
    color: colors.slate,
    fontSize: typography.small,
    fontWeight: '800',
    lineHeight: 19,
    marginTop: spacing.md,
  },
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
