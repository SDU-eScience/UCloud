import * as jwt from "jsonwebtoken";
import {Store} from "redux";
import HttpClient, {MissingAuthError, Override} from "../../app/Authentication/lib";
import {emptyPage} from "../../app/DefaultObjects";

interface JWT {
    sub: string;
    uid: number;
    lastName: string;
    aud: string;
    role: string;
    iss: string;
    firstNames: string;
    exp: number;
    extendedByChain: string[];
    iat: number;
    principalType: string;
    publicSessionReference: string;
}

class MockHttpClient {

    get username(): string | undefined {
        const info = this.userInfo;
        if (info) return info.sub;
        else return undefined;
    }

    get homeFolder(): string {
        const username = this.username;
        return `/home/${username}/`;
    }

    get jobFolder(): string {
        return `${this.homeFolder}Jobs/`;
    }

    public get isLoggedIn(): boolean {
        return this.userInfo != null;
    }

    get principalType(): undefined | string {
        const userInfo = this.userInfo;
        if (!userInfo) return undefined;
        else return userInfo.principalType;
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
        const token = this.decodedToken;
        if (!token) return undefined;
        else return this.decodedToken.payload;
    }

    static get storedAccessToken(): string {
        return window.localStorage.getItem("accessToken") ?? "";
    }

    static set storedAccessToken(value: string) {
        window.localStorage.setItem("accessToken", value);
    }

    static get storedCsrfToken(): string {
        return window.localStorage.getItem("csrfToken") ?? "";
    }

    static set storedCsrfToken(value) {
        window.localStorage.setItem("csrfToken", value);
    }

    public get currentProjectFolder(): string {
        return `/projects/${this.projectId}`;
    }

    public get sharesFolder(): string {
        return `${this.homeFolder}Shares`;
    }

    public get favoritesFolder(): string {
        return `${this.homeFolder}Favorites`;
    }

    public get fakeFolders(): string[] {
        return [this.sharesFolder, this.favoritesFolder].concat(this.hasActiveProject ? [this.currentProjectFolder] : []);
    }

    public get hasActiveProject(): boolean {
        return this.projectId !== undefined;
    }

    public get activeUsername(): string | undefined {
        if (this.useProjectToken(false) && !!this.projectDecodedToken) {
            return this.projectDecodedToken.payload.sub;
        } else {
            return this.username;
        }
    }

    private readonly context: string;
    private readonly serviceName: string;
    private readonly authContext: string;
    private readonly redirectOnInvalidTokens: boolean;

    private apiContext: string;
    private accessToken: string;
    private csrfToken: string;
    private decodedToken: any;
    private forceRefresh: boolean = false;
    private overridesPromise: Promise<void> | null = null;

    private projectId: string | undefined = undefined;
    private projectAccessToken: string | undefined = undefined;
    private projectDecodedToken: any | undefined = undefined;

    private overrides: Override[] = [];

    constructor() {
        this.decodedToken = jwt.decode("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuZGsiLCJsYXN0TmFtZSI6InRlc3QiLCJyb2xlIjoiVVNFUiIsIm" +
            "lzcyI6ImNsb3VkLnNkdS5kayIsImZpcnN0TmFtZXMiOiJ0ZXN0IiwiZXhwIjozNjE1NDkxMDkzLCJpYXQiOjE1MTU0ODkyO" +
            "TMsInByaW5jaXBhbFR5cGUiOiJwYXNzd29yZCIsImF1ZCI6WyJhcGkiLCJpcm9kcyJdfQ.gfLvmBWET-WpwtWLdrN9SL0tD" +
            "-0vrHrriWWDxnQljB8", {complete: true});
    }

    public call = ({
        method,
        path,
        body,
        context = this.apiContext,
        maxRetries = 5,
        withCredentials = false
    }): Promise<any> =>
        new Promise(resolve => {
            switch (path) {
                case "/accounting/storage/bytesUsed/usage":
                    resolve({request: {} as XMLHttpRequest, response: {usage: 14690218167, quota: null, dataType: "bytes", title: "Storage Used"}});
                    return;
                case "/accounting/compute/timeUsed/usage":
                    resolve({request: {} as XMLHttpRequest, response: {usage: 36945000, quota: null, dataType: "duration", title: "Compute Time Used"}});
                    return;
            }
            resolve({request: {} as XMLHttpRequest, response: emptyPage});
        })

    public get = (path: string, context = this.apiContext) =>
        this.call({method: "GET", path, body: undefined, context});

    public post = (path: string, body?: object, context = this.apiContext) =>
        this.call({method: "POST", path, body, context});

    public put = (path: string, body: object, context = this.apiContext) =>
        this.call({method: "PUT", path, body, context});

    public delete = (path: string, body: object, context = this.apiContext) =>
        this.call({method: "DELETE", path, body, context});

    public patch = (path: string, body: object, context = this.apiContext) =>
        this.call({method: "PATCH", path, body, context});

    public options = (path: string, body: object, context = this.apiContext) =>
        this.call({method: "OPTIONS", path, body, context});

    public head = (path: string, context = this.apiContext) =>
        this.call({method: "HEAD", path, body: undefined, context});

    public openBrowserLoginPage(): void {
        window.location.href =
            `${this.context}${this.authContext}/login?service=${encodeURIComponent(this.serviceName)}`;
    }

    public receiveAccessTokenOrRefreshIt = (): Promise<any> => {
        return new Promise(resolve => resolve("1"));
    };

    public createOneTimeTokenWithPermission(permission: string) {return new Promise(resolve => resolve(1));}

    public setTokens(csrfToken: string): void {/*  */}

    public logout(): Promise<void> {
        return new Promise<void>(() => undefined);
    }

    public clearTokens() {/*  */}

    public async waitForCloudReady() {
        if (this.overridesPromise !== null) {
            try {
                await this.overridesPromise;
            } catch {
                // Ignored
            }

            this.overridesPromise = null;
        }
    }

    public initializeStore(store: Store<ReduxObject>) {
        store.subscribe(() => {
            const project = store.getState().project.project;
            if (project !== this.projectId) {
                this.projectId = project;
                this.projectDecodedToken = undefined;
                this.projectAccessToken = undefined;
            }
        });
    }

    public get trashFolder(): string {
        return `${this.homeFolder}Trash/`;
    }

    public computeURL(context: string, path: string): string {
        const absolutePath = context + path;
        for (let i = 0; i < this.overrides.length; i++) {
            const override = this.overrides[i];
            if (absolutePath.indexOf(override.path) === 0) {
                const scheme = override.destination.scheme ?
                    override.destination.scheme : "http";
                const host = override.destination.host ?
                    override.destination.host : "localhost";
                const port = override.destination.port;

                return scheme + "://" + host + ":" + port + absolutePath;
            }
        }

        return this.context + absolutePath;
    }

    public openLandingPage() {
        if (window.location.href !== this.context + "/app/")
            window.location.href = this.context + "/app/";
    }

    private refresh() {
        return new Promise<string>(() => "Hello");
    }

    private isTokenExpired = () => false;

    private missingAuth(): 0 | MissingAuthError {
        return 0;
    }

    private retrieveToken(disallowProjects: boolean): string {
        return HttpClient.storedAccessToken;
    }

    private useProjectToken(disallowProjects: boolean): boolean {
        return this.projectId !== undefined && !disallowProjects;
    }

    private decodeToken(accessToken: string): any {
        const bail = (): never => {
            HttpClient.clearTokens();
            this.openBrowserLoginPage();
            return void (0) as never;
        };
        try {
            const token = jwt.decode(accessToken, {complete: true});

            if (token === null) {
                return bail();
            }

            if (typeof token === "string") {
                return bail();
            } else if (typeof token === "object") {
                const payload = token.payload;
                const isValid = "sub" in payload &&
                    "uid" in payload &&
                    "aud" in payload &&
                    "role" in payload &&
                    "iss" in payload &&
                    "exp" in payload &&
                    "extendedByChain" in payload &&
                    "iat" in payload &&
                    "principalType" in payload;

                if (!isValid) {
                    console.log("Bailing. Bad JWT");
                    return bail();
                }

                return token;
            } else {
                return bail();
            }
        } catch (e) {
            return bail();
        }
    }
}

export const Client = new MockHttpClient();

/* Why is this necessary? */
test("Silencer", () => {/*  */});
