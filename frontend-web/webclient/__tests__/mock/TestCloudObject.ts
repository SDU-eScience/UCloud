// Possible as we are running JS, not a browser
import SDUCloud from "../../authentication/lib";

// Storage Mock
const storageMock = () => {
    let storage = {};

    return {
        setItem: function(key, value) {
            storage[key] = value || "";
        },
        getItem: function(key) {
            return key in storage ? storage[key] : null;
        },
        removeItem: function(key) {
            delete storage[key];
        },
        get length() {
            return Object.keys(storage).length;
        },
        key: function(i) {
            var keys = Object.keys(storage);
            return keys[i] || null;
        },
        clear: () => null
    };
}


/* export default function initializeTestCloudObject() {
    window.localStorage = storageMock();
    console.log(localStorage)
    // Note: Test user access token. Missing refresh token, so any backend contact will result in redirection to login.
    const accessToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuZGsiLCJsYXN0TmFtZSI6InRlc3QiLCJyb2xlIjoiVVNFUiIsIm" +
        "lzcyI6ImNsb3VkLnNkdS5kayIsImZpcnN0TmFtZXMiOiJ0ZXN0IiwiZXhwIjozNjE1NDkxMDkzLCJpYXQiOjE1MTU0ODkyO" +
        "TMsInByaW5jaXBhbFR5cGUiOiJwYXNzd29yZCIsImF1ZCI6WyJhcGkiLCJpcm9kcyJdfQ.gfLvmBWET-WpwtWLdrN9SL0tD" +
        "-0vrHrriWWDxnQljB8";

    localStorage.setItem("accessToken", accessToken);
    return new SDUCloud("http://localhost:9000", "local-dev");
} */

test("Error silencer", () => {
    expect(1).toBe(1);
});