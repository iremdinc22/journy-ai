import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import type { NavigatorScreenParams } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';

import WelcomeScreen from '../screens/WelcomeScreen';
import LoginScreen from '../screens/LoginScreen';
import RegisterScreen from '../screens/RegisterScreen';
import TripSetupScreen from '../screens/TripSetupScreen';
import LoadingPlanScreen from '../screens/LoadingPlanScreen';
import PlaceDetailScreen from '../screens/PlaceDetailScreen';
import NotificationsScreen from '../screens/NotificationsScreen';
import SettingsScreen from '../screens/SettingsScreen';
import DayRouteDetailScreen from '../screens/DayRouteDetailScreen';

import HomeScreen from '../screens/HomeScreen';
import ItineraryScreen from '../screens/ItineraryScreen';
import ExploreScreen from '../screens/ExploreScreen';
import AssistantScreen from '../screens/AssistantScreen';
import ProfileScreen from '../screens/ProfileScreen';

import type { CreateTripRequest, ItineraryDay, PlaceResponse } from '../api/types';
import { useAppTheme } from '../theme/ThemeContext';

export type RootStackParamList = {
  Welcome: undefined;
  Login: undefined;
  Register: undefined;
  TripSetup: undefined;
  LoadingPlan: { tripDraft: CreateTripRequest };
  MainTabs: NavigatorScreenParams<MainTabParamList> | undefined;
  PlaceDetail: { place: PlaceResponse };
  Notifications: undefined;
  Settings: undefined;
  DayRouteDetail: {
    tripId: string;
    destination: string;
    day: ItineraryDay;
  };
};

export type MainTabParamList = {
  Home: undefined;
  Itinerary: undefined;
  Explore: undefined;
  Assistant: undefined;
  Profile: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator<MainTabParamList>();

function MainTabs() {
  const { theme } = useAppTheme();
  const { colors, radius } = theme;

  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarShowLabel: true,
        tabBarActiveTintColor: colors.midnight,
        tabBarInactiveTintColor: colors.softMuted,
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: '800',
          marginTop: 0,
        },
        tabBarStyle: {
          position: 'absolute',
          bottom: 16,
          left: 16,
          right: 16,
          height: 68,
          paddingTop: 7,
          paddingBottom: 8,
          borderRadius: radius.lg,
          backgroundColor: colors.surface,
          borderWidth: 1,
          borderColor: colors.mist,
          shadowColor: '#000',
          shadowOffset: { width: 0, height: 14 },
          shadowOpacity: 0.1,
          shadowRadius: 24,
          elevation: 10,
        },
        tabBarIcon: ({ color, focused }) => {
          const icons = {
            Home: focused ? 'home' : 'home-outline',
            Itinerary: focused ? 'calendar' : 'calendar-outline',
            Explore: focused ? 'map' : 'map-outline',
            Assistant: focused ? 'sparkles' : 'sparkles-outline',
            Profile: focused ? 'person' : 'person-outline',
          } as const;

          return <Ionicons name={icons[route.name]} size={23} color={color} />;
        },
      })}
    >
      <Tab.Screen
        name="Home"
        component={HomeScreen}
        options={{ title: 'Home' }}
      />
      <Tab.Screen
        name="Itinerary"
        component={ItineraryScreen}
        options={{ title: 'Plan' }}
      />
      <Tab.Screen
        name="Explore"
        component={ExploreScreen}
        options={{ title: 'Explore' }}
      />
      <Tab.Screen
        name="Assistant"
        component={AssistantScreen}
        options={{ title: 'AI' }}
      />
      <Tab.Screen
        name="Profile"
        component={ProfileScreen}
        options={{ title: 'Profile' }}
      />
    </Tab.Navigator>
  );
}

export default function AppNavigator() {
  const { theme } = useAppTheme();

  return (
    <Stack.Navigator
      initialRouteName="Welcome"
      screenOptions={{
        headerShown: false,
        animation: 'slide_from_right',
        contentStyle: {
          backgroundColor: theme.colors.ivory,
        },
      }}
    >
      <Stack.Screen name="Welcome" component={WelcomeScreen} />
      <Stack.Screen name="Login" component={LoginScreen} />
      <Stack.Screen name="Register" component={RegisterScreen} />
      <Stack.Screen name="TripSetup" component={TripSetupScreen} />
      <Stack.Screen name="LoadingPlan" component={LoadingPlanScreen} />
      <Stack.Screen name="MainTabs" component={MainTabs} />
      <Stack.Screen name="PlaceDetail" component={PlaceDetailScreen} />
      <Stack.Screen name="Notifications" component={NotificationsScreen} />
      <Stack.Screen name="Settings" component={SettingsScreen} />
      <Stack.Screen name="DayRouteDetail" component={DayRouteDetailScreen} />
    </Stack.Navigator>
  );
}
