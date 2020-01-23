import "jest-styled-components";

Object.defineProperty(window, "matchMedia", {
    value: jest.fn(() => ({
        matches: false,
        addListener: () => undefined,
        removeListener: () => undefined
    }))
});