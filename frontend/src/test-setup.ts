import '@testing-library/jest-dom/vitest';

// jsdom has no layout; the router restores scroll positions on navigation.
window.scrollTo = () => {};
