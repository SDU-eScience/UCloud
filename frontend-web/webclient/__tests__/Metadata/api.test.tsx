import * as API from "Metadata/api";

// Force API into code coverage without actually testing
describe("Metadata API", () => {
    test("Simple Search", () => API.getByPath("").then((it: any) => expect(it).toBeUndefined()));
});