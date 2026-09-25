import '@testing-library/jest-dom/vitest';

// jsdom has no layout; the router restores scroll positions on navigation.
window.scrollTo = () => {};

// jsdom does not lay anything out; ProseMirror asks where the cursor is to scroll to it.
const noRects = () => [] as unknown as DOMRectList;
const noBox = () => new DOMRect(0, 0, 0, 0);
Range.prototype.getClientRects ??= noRects;
Range.prototype.getBoundingClientRect ??= noBox;
Element.prototype.getClientRects ??= noRects;
document.elementFromPoint ??= () => null;
