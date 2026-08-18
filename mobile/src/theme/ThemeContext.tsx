import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { colors, radius, spacing, typography } from './colors';

type AppColors = typeof colors;
type ThemeMode = 'light' | 'dark';
const THEME_STORAGE_KEY = 'journy.theme.mode';

const darkColors: AppColors = {
  ivory: '#151819',
  canvas: '#101213',
  midnight: '#F6F0E8',
  graphite: '#DED5CC',
  slate: '#B8AEA6',
  mist: '#353D3E',
  fog: '#2B3031',
  sage: '#AFC5B2',
  blush: '#3A2E37',
  lilac: '#352A3A',
  sand: '#303231',
  gold: '#D9B96F',
  teal: '#C9A3CC',

  cream: '#1A1E1F',
  warmCream: '#222728',
  surface: '#202526',
  surfaceWarm: '#272D2E',

  mapBase: '#172122',
  mapWater: '#203639',
  mapRoad: '#5B6666',

  ink: '#FBF7F2',
  muted: '#C9BFB7',
  softMuted: '#8D999A',
  border: '#374042',
  line: '#465154',
  shadow: '#030404',
};

const themes = {
  light: { colors, radius, spacing, typography },
  dark: { colors: darkColors, radius, spacing, typography },
};

type ThemeContextValue = {
  mode: ThemeMode;
  isDark: boolean;
  ready: boolean;
  theme: typeof themes.light;
  setDarkMode: (enabled: boolean) => void;
};

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>('light');
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let mounted = true;

    const loadTheme = async () => {
      try {
        const storedMode = await AsyncStorage.getItem(THEME_STORAGE_KEY);
        if (mounted && (storedMode === 'light' || storedMode === 'dark')) {
          setMode(storedMode);
        }
      } finally {
        if (mounted) {
          setReady(true);
        }
      }
    };

    loadTheme();

    return () => {
      mounted = false;
    };
  }, []);

  const setDarkMode = (enabled: boolean) => {
    const nextMode: ThemeMode = enabled ? 'dark' : 'light';
    setMode(nextMode);
    AsyncStorage.setItem(THEME_STORAGE_KEY, nextMode).catch(() => undefined);
  };

  const value = useMemo<ThemeContextValue>(() => ({
    mode,
    isDark: mode === 'dark',
    ready,
    theme: themes[mode],
    setDarkMode,
  }), [mode, ready]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useAppTheme() {
  const context = useContext(ThemeContext);

  if (!context) {
    throw new Error('useAppTheme must be used inside ThemeProvider');
  }

  return context;
}
