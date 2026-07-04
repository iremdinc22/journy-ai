import { ApiError, NetworkError } from '../api/client';

export function authErrorMessage(error: unknown) {
  if (error instanceof NetworkError) {
    return 'Backend is not reachable. Start the Spring Boot server on port 8080, then try again.';
  }

  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403) {
      return 'Email or password is incorrect.';
    }

    if (error.status >= 500) {
      return 'The backend is running but returned a server error. Check the backend terminal logs.';
    }

    return error.message || 'The request could not be completed.';
  }

  return 'Something went wrong while signing in. Please try again.';
}
