

class MockCloud {
    private readonly context: string;
    private readonly serviceName: string;
    private readonly authContext: string;
    private readonly redirectOnInvalidTokens: boolean;

    private apiContext: string;
    private accessToken: string;
    private csrfToken: string;
    private decodedToken: any;

    constructor() {}

    call = (method: string, path: string, body?: object, context: string = this.apiContext): Promise<any> =>
        new Promise((resolve, reject) => resolve(1));

    get = (path, context = this.apiContext) => this.call("GET", path, undefined, context);

    post = (path, body?: object, context = this.apiContext) => this.call("POST", path, body, context);
    
    put = (path, body, context = this.apiContext) => this.call("PUT", path, body, context);

    delete = (path, body, context = this.apiContext) => this.call("DELETE", path, body, context);
    
    patch = (path, body, context = this.apiContext) => this.call("PATCH", path, body, context);

    options = (path, body, context = this.apiContext) => this.call("OPTIONS", path, body, context);

    head = (path, context = this.apiContext) => this.call("HEAD", path, undefined, context);

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

    get userInfo() {
        let token = this.decodedToken;
        if (!token) return null;
        else return this.decodedToken.payload;
    }

    receiveAccessTokenOrRefreshIt = (): Promise<any> => new Promise(resolve => resolve("1"));

    createOneTimeTokenWithPermission(permission) { }

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

    private missingAuth() {}
}

export const Cloud = new MockCloud();

test("Empty test", () =>
    expect(1).toBe(1)
);