import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Odmontuj komponenty i wyczyść DOM po każdym teście (izolacja).
afterEach(() => {
  cleanup();
});
