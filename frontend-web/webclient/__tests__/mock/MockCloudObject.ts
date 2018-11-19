// Possible as we are running JS, not a browser
import SDUCloud from "Authentication/lib";
import * as jwt from "jsonwebtoken";

// Storage Mock
const storageMock = () => {
    let storage: any = {};

    return {
        setItem: (key: string, value: any) => {
            storage[key] = value || "";
        },
        getItem: (key: string) => {
            return key in storage ? storage[key] : null;
        },
        removeItem: (key: string) => {
            delete storage[key];
        },
        get length() {
            return Object.keys(storage).length;
        },
        key: (i: number) => {
            var keys = Object.keys(storage);
            return keys[i] || null;
        },
        clear: () => null
    };
}


export default function initializeTestCloudObject() {
    Object.defineProperty(window, "localStorage", { value: storageMock });
    // Note: Test user access token. Missing refresh token, so any backend contact will result in redirection to login.
    const accessToken = jwt.decode("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuZGsiLCJsYXN0TmFtZSI6InRlc3QiLCJyb2xlIjoiVVNFUiIsIm" +
        "lzcyI6ImNsb3VkLnNkdS5kayIsImZpcnN0TmFtZXMiOiJ0ZXN0IiwiZXhwIjozNjE1NDkxMDkzLCJpYXQiOjE1MTU0ODkyO" +
        "TMsInByaW5jaXBhbFR5cGUiOiJwYXNzd29yZCIsImF1ZCI6WyJhcGkiLCJpcm9kcyJdfQ.gfLvmBWET-WpwtWLdrN9SL0tD" +
        "-0vrHrriWWDxnQljB8", { complete: true }) as string;

    localStorage.setItem("accessToken", accessToken);
    return new SDUCloud();
}

// FIXME Shouldn't be necessary
test("Error silencer", () =>
    expect(1).toBe(1)
)