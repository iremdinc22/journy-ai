import { ApiError } from '../api/client';
import type { useTranslation } from '../i18n/LanguageContext';

type Translate = ReturnType<typeof useTranslation>;

export function loadingPlanErrorMessage(error: unknown, t: Translate) {
  if (error instanceof ApiError && error.code === 'INSUFFICIENT_DESTINATION_DATA') {
    return t('loading.insufficientData');
  }
  if (error instanceof ApiError && error.status === 401) return t('loading.sessionExpired');
  if (error instanceof ApiError && error.status === 403) return t('loading.freshSignIn');
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
    return error.message || t('loading.checkDetails');
  }
  return t('loading.networkError');
}
