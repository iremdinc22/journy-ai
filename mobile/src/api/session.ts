import AsyncStorage from '@react-native-async-storage/async-storage';
import type { AuthResponse, TripResponse, UserSummary } from './types';

type SessionState = {
  accessToken?: string;
  refreshToken?: string;
  user?: UserSummary;
  currentTrip?: TripResponse;
};

const state: SessionState = {};
const AUTH_STORAGE_KEY = 'journy.session.auth';
const TRIP_STORAGE_KEY = 'journy.session.currentTrip';

export const session = {
  async restore() {
    const [storedAuth, storedTrip] = await Promise.all([
      AsyncStorage.getItem(AUTH_STORAGE_KEY),
      AsyncStorage.getItem(TRIP_STORAGE_KEY),
    ]);

    if (storedAuth) {
      const auth = JSON.parse(storedAuth) as AuthResponse;
      state.accessToken = auth.accessToken;
      state.refreshToken = auth.refreshToken;
      state.user = auth.user;
    }

    if (storedTrip) {
      state.currentTrip = JSON.parse(storedTrip) as TripResponse;
    }
  },
  setAuth(auth: AuthResponse) {
    state.accessToken = auth.accessToken;
    state.refreshToken = auth.refreshToken;
    state.user = auth.user;
    AsyncStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth)).catch(() => undefined);
  },
  clearAuth() {
    state.accessToken = undefined;
    state.refreshToken = undefined;
    state.user = undefined;
    state.currentTrip = undefined;
    AsyncStorage.multiRemove([AUTH_STORAGE_KEY, TRIP_STORAGE_KEY]).catch(() => undefined);
  },
  getAccessToken() {
    return state.accessToken;
  },
  getRefreshToken() {
    return state.refreshToken;
  },
  getUser() {
    return state.user;
  },
  setCurrentTrip(trip: TripResponse) {
    state.currentTrip = trip;
    AsyncStorage.setItem(TRIP_STORAGE_KEY, JSON.stringify(trip)).catch(() => undefined);
  },
  getCurrentTrip() {
    return state.currentTrip;
  },
};
