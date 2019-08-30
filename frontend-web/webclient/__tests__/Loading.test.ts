import {loadingAction} from "../app/Loading";

describe("Loading actions", () => {
    test("Loading", () => {
        expect(loadingAction(true)).toStrictEqual({type: "LOADING_START"});
    });

    test("Not loading", () => {
        expect(loadingAction(false)).toStrictEqual({type: "LOADING_END"});
    });
});
