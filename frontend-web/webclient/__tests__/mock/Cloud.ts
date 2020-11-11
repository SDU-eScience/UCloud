import {Store} from "redux";
import HttpClient, {JWT, MissingAuthError} from "../../app/Authentication/lib";
import {emptyPage} from "../../app/DefaultObjects";
import {parseJWT} from "../../app/UtilityFunctions";

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

    public get activeHomeFolder(): string {
        if (!this.hasActiveProject) {
            return this.homeFolder;
        } else {
            return this.currentProjectFolder;
        }
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

    constructor() {
        this.decodedToken = parseJWT("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuZGsiLCJsYXN0TmFtZSI6InRlc3QiLCJyb2xlIjoiVVNFUiIsIm" +
            "lzcyI6ImNsb3VkLnNkdS5kayIsImZpcnN0TmFtZXMiOiJ0ZXN0IiwiZXhwIjozNjE1NDkxMDkzLCJpYXQiOjE1MTU0ODkyO" +
            "TMsInByaW5jaXBhbFR5cGUiOiJwYXNzd29yZCIsImF1ZCI6WyJhcGkiLCJpcm9kcyJdfQ.gfLvmBWET-WpwtWLdrN9SL0tD" +
            "-0vrHrriWWDxnQljB8");
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
            const token = {payload: parseJWT(accessToken)};
            if (token.payload == null) return bail();
            return token;
        } catch (e) {
            return bail();
        }
    }
}

export const Client = new MockHttpClient();

/* Why is this necessary? */
test("Silencer", () => {/*  */});
