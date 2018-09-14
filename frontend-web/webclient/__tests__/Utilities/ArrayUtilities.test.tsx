import * as ArrayUtilities from "Utilities/ArrayUtilities";

describe("Array Utilities", () => {
    test("Add entry to set where entry is not present", () => {
        const set = new Set<number>().add(1);
        expect(set.size).toBe(1);
        expect(ArrayUtilities.addEntryIfNotPresent(set, 2)).toBe(true);
    });

    test("Add entry to set where entry is present", () => {
        const set = new Set<number>().add(1);
        expect(set.size).toBe(1);
        expect(ArrayUtilities.addEntryIfNotPresent(set, 1)).toBe(false);
    });

    test("Remove entry from array", () => {
        const array = [0, 1, 2, 3];
        expect(ArrayUtilities.removeEntry(array, 1).every(it => it !== 1)).toBe(true);
    });

    test("Remove non-existant entry in array", () => {
        const array = [] as number[];
        expect(ArrayUtilities.removeEntry(array, 2).length).toBe(array.length)
    });

})