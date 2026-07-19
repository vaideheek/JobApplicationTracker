/**
 * Centralized API URL resolution for local and production environments.
 */
export const getApiBaseUrl = (): string => {
  const envUrl = import.meta.env.VITE_API_URL;
  if (envUrl && envUrl.trim() !== '') {
    // Normalise and strip trailing slashes, then append '/api'
    const cleanUrl = envUrl.trim().replace(/\/+$/, '');
    return `${cleanUrl}/api`;
  }
  return '/api';
};
