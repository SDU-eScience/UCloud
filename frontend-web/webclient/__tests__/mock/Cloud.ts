import * as jwt from "jsonwebtoken";

interface JWT {
    sub: string
    uid: number
    lastName: string
    aud: string
    role: string
    iss: string
    firstNames: string
    exp: number
    extendedByChain: any[]
    iat: number
    principalType: string
    publicSessionReference: string
}

class MockCloud {
    private readonly context: string;
    private readonly serviceName: string;
    private readonly authContext: string;
    private readonly redirectOnInvalidTokens: boolean;

    private apiContext: string;
    private accessToken: string;
    private csrfToken: string;
    private decodedToken: any;

    constructor() {
        this.decodedToken = jwt.decode("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuZGsiLCJsYXN0TmFtZSI6InRlc3QiLCJyb2xlIjoiVVNFUiIsIm" +
            "lzcyI6ImNsb3VkLnNkdS5kayIsImZpcnN0TmFtZXMiOiJ0ZXN0IiwiZXhwIjozNjE1NDkxMDkzLCJpYXQiOjE1MTU0ODkyO" +
            "TMsInByaW5jaXBhbFR5cGUiOiJwYXNzd29yZCIsImF1ZCI6WyJhcGkiLCJpcm9kcyJdfQ.gfLvmBWET-WpwtWLdrN9SL0tD" +
            "-0vrHrriWWDxnQljB8", { complete: true });
    }

    call = (method: string, path: string, body?: object, context: string = this.apiContext): Promise<any> =>
        new Promise((resolve, reject) => resolve(1));

    get = (path: string, context = this.apiContext) => this.call("GET", path, undefined, context);

    post = (path: string, body?: object, context = this.apiContext) => this.call("POST", path, body, context);

    put = (path: string, body: object, context = this.apiContext) => this.call("PUT", path, body, context);

    delete = (path: string, body: object, context = this.apiContext) => this.call("DELETE", path, body, context);

    patch = (path: string, body: object, context = this.apiContext) => this.call("PATCH", path, body, context);

    options = (path: string, body: object, context = this.apiContext) => this.call("OPTIONS", path, body, context);

    head = (path: string, context = this.apiContext) => this.call("HEAD", path, undefined, context);

    openBrowserLoginPage() {
        window.location.href = this.context + this.authContext + "/login?service=" + encodeURIComponent(this.serviceName);
    }

    get username() {
        let info = this.userInfo;
        if (info) return info.sub;
        else return null
    }

    get homeFolder(): string {
        let username = this.username;
        return `/home/${username}/`
    }

    get jobFolder(): string {
        return `${this.homeFolder}Jobs/`
    }

    get userRole(): string {
        const info = this.userInfo;
        if (info) return info.role;
        return "";
    }

    get userIsAdmin(): boolean {
        return true;
    }

    get userInfo(): undefined | JWT {
        let token = this.decodedToken;
        if (!token) return undefined;
        else return this.decodedToken.payload;
    }

    receiveAccessTokenOrRefreshIt = (): Promise<any> => new Promise(resolve => resolve("1"));

    createOneTimeTokenWithPermission(permission: string) { }

    private refresh() { }

    setTokens(accessToken: string, csrfToken: string) { }

    logout() { }

    clearTokens() { }

    static get storedAccessToken(): string {
        return window.localStorage.getItem("accessToken") || "";
    }

    static set storedAccessToken(value: string) {
        window.localStorage.setItem("accessToken", value);
    }

    static get storedCsrfToken(): string {
        return window.localStorage.getItem("csrfToken") || "";
    }

    static set storedCsrfToken(value) {
        window.localStorage.setItem("csrfToken", value);
    }

    private isTokenExpired = () => false

    private missingAuth() { }
}

export const Cloud = new MockCloud();

test("Empty test", () =>
    expect(1).toBe(1)
);