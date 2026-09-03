import type { ItineraryDay } from '../api/types';
import { localizeDynamicText } from './localizedDynamicText';

// Provider day titles are complete translations; never translate their fragments or proper names.
export function itineraryDayTitle(day: ItineraryDay, language: 'en' | 'tr'): string {
  return day.titleTranslations?.[language] ?? localizeDynamicText(day.title, language);
}
