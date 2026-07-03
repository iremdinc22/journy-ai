import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { colors, radius, spacing, typography } from './colors';

type AppColors = typeof colors;
type ThemeMode = 'light' | 'dark';
const THEME_STORAGE_KEY = 'journy.theme.mode';

const darkColors: AppColors = {
  ivory: '#262423',
  canvas: '#211F1F',
  midnight: '#F7F0EA',
  graphite: '#E6DCD4',
  slate: '#C9BFB8',
  mist: '#4B4441',
  fog: '#342F2E',
  sage: '#B9C9B5',
  blush: '#4A3944',
  lilac: '#3D3440',
  sand: '#433A35',
  gold: '#D8BA7A',
  teal: '#D2B8D3',

  cream: '#2E2B2A',
  warmCream: '#36302D',
  surface: '#312E2D',
  surfaceWarm: '#3A3431',

  mapBase: '#2D363B',
  mapWater: '#34464F',
  mapRoad: '#566268',

  ink: '#FBF7F2',
  muted: '#C2B8B1',
  softMuted: '#A79C96',
  border: '#514946',
  line: '#5A504B',
  shadow: '#121010',
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
