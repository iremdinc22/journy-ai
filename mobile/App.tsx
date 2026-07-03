import { DarkTheme, DefaultTheme, NavigationContainer } from "@react-navigation/native";
import { StatusBar } from "expo-status-bar";

import AppNavigator from "./src/navigation/AppNavigator";
import { ThemeProvider, useAppTheme } from "./src/theme/ThemeContext";

export default function App() {
  return (
    <ThemeProvider>
      <JournyApp />
    </ThemeProvider>
  );
}

function JournyApp() {
  const { isDark, theme } = useAppTheme();
  const navigationTheme = {
    ...(isDark ? DarkTheme : DefaultTheme),
    colors: {
      ...(isDark ? DarkTheme.colors : DefaultTheme.colors),
      background: theme.colors.ivory,
      card: theme.colors.surface,
      text: theme.colors.midnight,
      border: theme.colors.mist,
      primary: theme.colors.teal,
    },
  };

  return (
    <NavigationContainer theme={navigationTheme}>
      <StatusBar style={isDark ? 'light' : 'dark'} />
      <AppNavigator />
    </NavigationContainer>
  );
}
