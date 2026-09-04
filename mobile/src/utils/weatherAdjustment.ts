import type { WeatherAdjustmentResponse } from '../api/types';

export function meaningfulWeatherAdjustment(value: WeatherAdjustmentResponse | null | undefined): value is WeatherAdjustmentResponse {
  return value?.available === true && value.weatherStatus === 'AVAILABLE'
    && Boolean(value.previewId) && value.stopChanges?.length === 2;
}
