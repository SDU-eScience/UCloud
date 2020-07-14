import * as CollUtils from "../../app/Utilities/CollectionUtilities";

describe("setsDiffer", () => {
    test("Equal", () => {
        expect(CollUtils.setsDiffer(new Set([1, 2, 3, 4]), new Set([1, 3, 2, 4]))).toBe(false);
    });

    test("Different sizes", () => {
        expect(CollUtils.setsDiffer(new Set([1, 2, 3, 4]), new Set([1, 2, 3]))).toBe(true);
    });

    test("Different content", () => {
        expect(CollUtils.setsDiffer(new Set([1, 2, 3, 4]), new Set([1, 2, 4, 5]))).toBe(true);
    });
});

test("groupBy", () => {
    const values = ["a", "aa", "aaa", "b", "bb", "c"];
    const groupedBy = CollUtils.groupBy(values, it => `${it.length}`);
    expect(groupedBy["1"].length).toBe(3);
    expect(groupedBy["2"].length).toBe(2);
    expect(groupedBy["3"].length).toBe(1);
});


test("associateBy", () => {
    const values = [0, 1, 2, 3, 4, 5];
    expect(CollUtils.associateBy(values, it => String.fromCharCode(it + 97))).toStrictEqual({a: 0, b: 1, c: 2, d: 3, e: 4, f: 5});
});


describe("takeLast", () => {
    test("1", () => {
        expect(CollUtils.takeLast([1, 2, 3], 1)[0]).toBe(3);
    });
    test("All", () => {
        expect(CollUtils.takeLast([1, 2, 3], 3).length).toBe(3);
    });
    test("Too many", () => {
        expect(CollUtils.takeLast([1, 2, 3], 4).length).toBe(3);
    });
});
