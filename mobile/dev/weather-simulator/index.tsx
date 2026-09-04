// Separate Expo entry point. The normal application never imports this file.
import React, { useCallback, useEffect, useState } from 'react';
import { registerRootComponent } from 'expo';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../../src/navigation/AppNavigator';
import DayRouteDetailScreen from '../../src/screens/DayRouteDetailScreen';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { SafeAreaView, Text, Button, View } from 'react-native';
import { authApi, tripApi } from '../../src/api/journyApi';
import { session } from '../../src/api/session';
import { LanguageProvider } from '../../src/i18n/LanguageContext';
import { ThemeProvider } from '../../src/theme/ThemeContext';
import ItineraryScreen from '../../src/screens/ItineraryScreen';
import AssistantScreen from '../../src/screens/AssistantScreen';

const Tabs = createBottomTabNavigator();
const Stack = createNativeStackNavigator<RootStackParamList>();
function FixtureTabs() {
  return <Tabs.Navigator screenOptions={{ headerShown: false }}>
    <Tabs.Screen name="Plan" component={ItineraryScreen} />
    <Tabs.Screen name="Assistant" component={AssistantScreen} />
  </Tabs.Navigator>;
}
function Fixture() {
  const [date, setDate] = useState('');
  const [error, setError] = useState('');
  const connect = useCallback(async () => {
    setError('');
    try {
      // Refuse the normal backend before sending fixture credentials or opening any screen.
      const response = await fetch('http://localhost:8080/__dev/weather-simulator');
      const marker = await response.json().catch(() => null);
      if (!response.ok || marker?.fixture !== 'journy-positive-rain-v1' || marker.production !== false) {
        throw new Error('The isolated fixture backend is not running on port 8080.');
      }
      session.clearAuth();
      await authApi.login('weather.simulator@example.test', 'WeatherDemo123!');
      const trip = await tripApi.current();
      if (trip.id !== marker.tripId) throw new Error('Fixture trip identity mismatch.');
      session.setCurrentTrip(trip);
      setDate(marker.date);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Start the isolated fixture backend first.');
    }
  }, []);
  useEffect(() => { void connect(); }, [connect]);
  return <SafeAreaView style={{ flex: 1, backgroundColor: '#fff' }}>
    <View style={{ backgroundColor: '#fff0c2', padding: 10 }}>
      <Text style={{ fontWeight: 'bold', color: '#362500' }}>DEV ONLY · Synthetic rain · Real recorded OSM places</Text>
      <Text style={{ color: '#362500' }}>{date || 'Connecting…'} · Europe/Istanbul · Rain 14:00–15:00; dry 16:00</Text>
    </View>
    {error ? <View style={{ padding: 20 }}><Text>{error}</Text><Button title="Retry fixture connection" onPress={connect} /></View>
      : date ? <NavigationContainer>
        <Stack.Navigator screenOptions={{ headerShown: false }}>
          <Stack.Screen name="MainTabs" component={FixtureTabs} />
          <Stack.Screen name="DayRouteDetail" component={DayRouteDetailScreen} />
        </Stack.Navigator>
      </NavigationContainer> : <Text style={{ padding: 20 }}>Waiting for the isolated fixture…</Text>}
  </SafeAreaView>;
}
function WeatherSimulatorApp() {
  return <LanguageProvider><ThemeProvider><Fixture /></ThemeProvider></LanguageProvider>;
}
registerRootComponent(WeatherSimulatorApp);
