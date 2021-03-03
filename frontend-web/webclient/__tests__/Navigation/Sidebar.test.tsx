const initialWidth = window.innerWidth;

describe("Sidebar", () => {
    describe("Mobile", () => {
        beforeAll(() =>
            // Make the window small enough to trigger responsive mode
            Object.defineProperty(window, "innerWidth", {value: 500})
        );

        test.skip("Sidebar", () => {/*  */});

        afterAll(() => Object.defineProperty(window, "innerWidth", {value: initialWidth}));
    });
});
