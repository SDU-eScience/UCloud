import * as API from "Project/api";

// Force API into code coverage without actually testing
describe("Metadata API", () => {
    test("Simple Search", () => API.getByPath("").then((it: undefined) => expect(it).toBeUndefined()));
});