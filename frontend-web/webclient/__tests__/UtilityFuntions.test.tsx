import * as UF from "UtilityFunctions";

// TO LOWER CASE AND CAPITALIZE

test("All upper case and numbers", () => {
    expect(UF.toLowerCaseAndCapitalize("ABACUS 2.0")).toBe("Abacus 2.0");
});

test("All lower case", () => {
    expect(UF.toLowerCaseAndCapitalize("abacus")).toBe("Abacus");
});

test("Mixed case and special characters", () => {
    expect(UF.toLowerCaseAndCapitalize("aBaCuS 2.0 !@#$%^&*()")).toBe("Abacus 2.0 !@#$%^&*()")
});

test("Empty string", () => {
    expect(UF.toLowerCaseAndCapitalize("")).toBe("");
});

// Add trailing slash

test("Add trailing slash to string", () => 
    expect(UF.addTrailingSlash("/home/test@user.dk")).toBe("/home/test@user.dk/")
);

test("Don't add trailing slash to string", () => 
    expect(UF.addTrailingSlash("/home/test@user.dk/")).toBe("/home/test@user.dk/")
);

test("Add trailing slash to empty string", () =>
    expect(UF.addTrailingSlash("")).toBe("/")
);

// Remove trailing slash

test("Remove trailing slash from string", () =>
    expect(UF.removeTrailingSlash("/home/test@user.dk/")).toBe("/home/test@user.dk")
);

test("Don't remove trailing slash from string", () =>
    expect(UF.removeTrailingSlash("/home/test@user.dk")).toBe("/home/test@user.dk")
);

test("Empty string, no action", () =>
    expect(UF.removeTrailingSlash("")).toBe("")
);


// Prettier string

test("Prettify string", () =>
    expect(UF.prettierString("HELLO,_WORLD")).toBe("Hello, world")
);

test("Prettify string, upper and lower case", () =>
    expect(UF.prettierString("hEllO,_WorlD")).toBe("Hello, world")
);

test("Prettify string", () =>
    expect(UF.prettierString("path")).toBe("Path")
);