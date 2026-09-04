import type { StartAreaSuggestion } from '../api/types';

export function visibleStartAreas(items: StartAreaSuggestion[], selected: StartAreaSuggestion | null = null): StartAreaSuggestion[] {
  const candidates = selected ? [selected, ...items.filter(item => item.id !== selected.id)] : items;
  return candidates.filter(item => Boolean(item.id && item.name?.trim() && item.source?.startsWith('provider:'))
    && Number.isFinite(item.latitude) && Number.isFinite(item.longitude)
    && Math.abs(item.latitude) <= 90 && Math.abs(item.longitude) <= 180
    && !(item.latitude === 0 && item.longitude === 0));
}

// Search text is not a resolved location. Only a selected API identity is submitted.
export function startAreaSelectionPayload(selected: StartAreaSuggestion | null) {
  return selected ? { startingArea: selected.name, startingAreaSelection: selected } : {};
}
