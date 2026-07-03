import type { AuthResponse, TripResponse, UserSummary } from './types';

type SessionState = {
  accessToken?: string;
  refreshToken?: string;
  user?: UserSummary;
  currentTrip?: TripResponse;
};

const state: SessionState = {};

export const session = {
  setAuth(auth: AuthResponse) {
    state.accessToken = auth.accessToken;
    state.refreshToken = auth.refreshToken;
    state.user = auth.user;
  },
  clearAuth() {
    state.accessToken = undefined;
    state.refreshToken = undefined;
    state.user = undefined;
    state.currentTrip = undefined;
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
  },
  getCurrentTrip() {
    return state.currentTrip;
  },
};
