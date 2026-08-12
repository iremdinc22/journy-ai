import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';

import { translations, type TranslationKey } from './translations';

export type LanguageCode = keyof typeof translations;

const LANGUAGE_STORAGE_KEY = 'journy.language';

type TranslateOptions = Record<string, string | number>;

type LanguageContextValue = {
  language: LanguageCode;
  ready: boolean;
  setLanguage: (language: LanguageCode) => void;
  t: (key: TranslationKey, options?: TranslateOptions) => string;
};

const LanguageContext = createContext<LanguageContextValue | undefined>(undefined);

function deviceDefaultLanguage(): LanguageCode {
  const locale = Intl.DateTimeFormat().resolvedOptions().locale.toLowerCase();
  return locale.startsWith('tr') ? 'tr' : 'en';
}

function interpolate(template: string, options?: TranslateOptions) {
  if (!options) {
    return template;
  }
  return template.replace(/\{\{(\w+)\}\}/g, (_, key: string) => String(options[key] ?? ''));
}

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  const [language, setLanguageState] = useState<LanguageCode>(deviceDefaultLanguage);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let mounted = true;

    const loadLanguage = async () => {
      try {
        const storedLanguage = await AsyncStorage.getItem(LANGUAGE_STORAGE_KEY);
        if (mounted && (storedLanguage === 'en' || storedLanguage === 'tr')) {
          setLanguageState(storedLanguage);
        }
      } finally {
        if (mounted) {
          setReady(true);
        }
      }
    };

    loadLanguage();

    return () => {
      mounted = false;
    };
  }, []);

  const setLanguage = (nextLanguage: LanguageCode) => {
    setLanguageState(nextLanguage);
    AsyncStorage.setItem(LANGUAGE_STORAGE_KEY, nextLanguage).catch(() => undefined);
  };

  const value = useMemo<LanguageContextValue>(() => ({
    language,
    ready,
    setLanguage,
    t: (key, options) => interpolate(translations[language][key] ?? translations.en[key], options),
  }), [language, ready]);

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
}

export function useLanguage() {
  const context = useContext(LanguageContext);

  if (!context) {
    throw new Error('useLanguage must be used inside LanguageProvider');
  }

  return context;
}

export function useTranslation() {
  return useLanguage().t;
}
