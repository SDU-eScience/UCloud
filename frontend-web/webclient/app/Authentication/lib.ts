import {Store} from "redux";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {b64DecodeUnicode, inRange, inSuccessRange, is5xxStatusCode, onDevSite} from "@/UtilityFunctions";
import {setStoredProject} from "@/Project/ReduxState";
import {CallParameters} from "./CallParameters";
import {signIntentToCall, clearSigningKey} from "@/Authentication/MessageSigning";

/**
 * Used to parse and validate the structure of the JWT. If the JWT is invalid, the function returns null, otherwise as
 * a an object.
 * @param encodedJWT The JWT sent from the backend, in the form of a string.
 */
export function parseJWT(encodedJWT: string): JWT | null {
    const [, right] = encodedJWT.split(".");
    if (right == null) return null;

    const decoded = b64DecodeUnicode(right);
    const parsed = JSON.parse(decoded);
    const isValid = "sub" in parsed &&
        "aud" in parsed &&
        "role" in parsed &&
        "iss" in parsed &&
        "exp" in parsed &&
        "extendedByChain" in parsed &&
        "iat" in parsed &&
        "principalType" in parsed;
    if (!isValid) return null;

    return parsed;
}

/**
 * Represents an instance of the HTTPClient object used for contacting the backend, implicitly using JWTs.
 */
export class HttpClient {
    private readonly context: string;
    private readonly serviceName: string;
    private readonly authContext: string;
    private readonly redirectOnInvalidTokens: boolean;
    private readonly guestPages: string[] = ["/skus/", "/skus"];

    public apiContext: string;
    private accessToken: string;
    private csrfToken: string;
    private decodedToken: any;
    private forceRefresh = false;
    private overridesPromise: Promise<void> | null = null;

    public projectId: string | undefined = undefined;

    constructor() {
        const context = location.protocol + "//" +
            location.hostname +
            (location.port ? ":" + location.port : "");

        let serviceName: string;
        switch (location.hostname) {
            case "localhost":
                serviceName = "local-dev-csrf";
                break;

            default:
                serviceName = "web-csrf";
                break;
        }

        this.context = context;
        this.serviceName = serviceName;

        this.apiContext = "/api";
        this.authContext = "/auth";

        this.decodedToken = null;
        this.redirectOnInvalidTokens = false;

        const accessToken = HttpClient.storedAccessToken;
        const csrfToken = HttpClient.storedCsrfToken;
        if (accessToken && csrfToken) {
            this.setTokens(accessToken, csrfToken);
        }
    }

    private get isPublicPage(): boolean {
        return this.guestPages.some(page => window.location.pathname.endsWith(page));
    }

    public async waitForCloudReady(): Promise<void> {
        if (this.overridesPromise !== null) {
            try {
                await this.overridesPromise;
            } catch {
                // Ignored
            }

            this.overridesPromise = null;
        }
    }

    public initializeStore(store: Store<ReduxObject>): void {
        {
            const project = store.getState().project.project;
            if (project !== this.projectId) {
                this.projectId = project;
            }
        }

        store.subscribe(() => {
            const project = store.getState().project.project;
            if (project !== this.projectId) {
                this.projectId = project;
            }
        });
    }

    /**
     * Makes an AJAX call to the API. This will automatically add relevant authorization headers.
     * If the user's JWT has expired this will automatically attempt to refresh it.
     */
    public async call(params: CallParameters): Promise<any> {
        let {
            method,
            path,
            body,
            context = this.apiContext,
            withCredentials = false,
            projectOverride,
            accessTokenOverride,
            unauthenticated = false,
            responseType = "text",
            acceptType = "*/*",
        } = params;

        await this.waitForCloudReady();

        if (path.indexOf("/") !== 0) path = "/" + path;
        const queryBegin = path.indexOf("?");
        const beforeQuery = queryBegin === -1 ? path : path.substring(0, queryBegin);
        const afterQuery = queryBegin === -1 ? null : path.substring(queryBegin);
        if (beforeQuery.length > 1 && beforeQuery[beforeQuery.length - 1] === '/') {
            path = beforeQuery.substring(0, beforeQuery.length - 1);
            if (afterQuery !== null) {
                path += afterQuery;
            }
        }

        try {
            const token = unauthenticated ? null : await this.receiveAccessTokenOrRefreshIt();
            return new Promise((resolve, reject) => {
                const req = new XMLHttpRequest();
                req.open(method, this.computeURL(context, path));
                if (token !== null || accessTokenOverride !== undefined) {
                    req.setRequestHeader(
                        "Authorization",
                        accessTokenOverride === undefined ? `Bearer ${token}` : `Bearer ${accessTokenOverride}`
                    );

                    const signedIntent = signIntentToCall(this.username ?? "", this.projectId ?? null, params);
                    if (signedIntent !== null) {
                        req.setRequestHeader("UCloud-Signed-Intent", signedIntent);
                    }
                }

                req.setRequestHeader("Content-Type", "application/json; charset=utf-8");
                req.setRequestHeader("Accept", acceptType);
                const projectId = projectOverride ?? this.projectId;
                if (projectId) req.setRequestHeader("Project", projectId);
                req.responseType = responseType; // Explicitly set, otherwise issues with empty response
                if (withCredentials) {
                    req.withCredentials = true;
                }

                const rejectOrRetry = (parsedResponse?) => {
                    if (req.status === 401) {
                        this.forceRefresh = true;
                    }

                    if (req.status === 482) {
                        clearSigningKey();
                    }

                    reject({request: req, response: parsedResponse});
                };

                req.onload = async () => {
                    try {
                        const responseContentType = req.getResponseHeader("content-type");
                        let parsedResponse = req.response.length === 0 ? "{}" : req.response;

                        // JSON Parsing
                        if (responseContentType !== null) {
                            if (responseContentType.indexOf("application/json") !== -1 ||
                                responseContentType.indexOf("application/javascript") !== -1) {
                                parsedResponse = JSON.parse(parsedResponse);
                            }
                        }

                        if (inSuccessRange(req.status)) {
                            resolve({
                                response: parsedResponse,
                                request: req,
                            });
                        } else {
                            rejectOrRetry(parsedResponse);
                        }
                    } catch (e) {
                        rejectOrRetry();
                    }
                };

                if (body) {
                    req.send(JSON.stringify(body));
                } else {
                    req.send();
                }
            });
        } catch (e) {
            console.warn(e);
            if (!this.isPublicPage) snackbarStore.addFailure("Could not refresh login token.", false);
            if ([401, 403].includes(e.status)) HttpClient.clearTokens();
        }
    }

    public computeURL(context: string, path: string): string {
        if (path.indexOf("http://") === 0 || path.indexOf("https://") === 0 ||
            path.indexOf("ws://") === 0 || path.indexOf("wss://") === 0) {
            return path;
        }
        const absolutePath = context + path;
        return this.context + absolutePath;
    }

    /**
     * Calls with the GET HTTP method. See call(method, path, body)
     */
    public async get<T = any>(
        path: string,
        context = this.apiContext,
    ): Promise<{request: XMLHttpRequest, response: T}> {
        return this.call({method: "GET", path, body: undefined, context});
    }

    /**
     * Calls with the POST HTTP method. See call(method, path, body)
     */
    public async post<T = any>(
        path: string,
        body?: any,
        context = this.apiContext,
    ): Promise<{request: XMLHttpRequest, response: T}> {
        return this.call({method: "POST", path, body, context});
    }

    /**
     * Calls with the PUT HTTP method. See call(method, path, body)
     */
    public async put<T = any>(
        path: string,
        body: Record<string, unknown>,
        context = this.apiContext,
    ): Promise<{request: XMLHttpRequest, response: T}> {
        return this.call({method: "PUT", path, body, context});
    }

    /**
     * Calls with the DELETE HTTP method. See call(method, path, body)
     */
    public async delete<T = void>(
        path: string,
        body: Record<string, unknown>,
        context = this.apiContext,
    ): Promise<{request: XMLHttpRequest, response: T}> {
        return this.call({method: "DELETE", path, body, context});
    }

    /**
     * Calls with the PATCH HTTP method. See call(method, path, body)
     */
    public async patch<T = any>(
        path: string,
        body: Record<string, unknown>,
        context = this.apiContext,
    ): Promise<{request: XMLHttpRequest, response: T}> {
        return this.call({method: "PATCH", path, body, context});
    }

    /**
     * Calls with the OPTIONS HTTP method. See call(method, path, body)
     */
    public async options<T = any>(
        path: string,
        body: Record<string, unknown>,
        context = this.apiContext,
    ): Promise<{request: XMLHttpRequest; response: T}> {
        return this.call({method: "OPTIONS", path, body, context});
    }

    /**
     * Calls with the HEAD HTTP method. See call(method, path, body)
     */
    public async head<T = any>(
        path: string,
        context = this.apiContext,
    ): Promise<{request: XMLHttpRequest; response: T}> {
        return this.call({method: "HEAD", path, body: undefined, context});
    }

    /**
     * Opens up a new page which contains the login page at the auth service. This login page will automatically
     * redirect back to the correct service (using serviceName).
     */
    public openBrowserLoginPage(): void {
        if (window.location.href !== this.context + "/app/login")
            window.location.href = this.context + "/app/login";
    }

    public openLandingPage(): void {
        if (window.location.href !== this.context + "/app/")
            window.location.href = this.context + "/app/";
    }

    /**
     * @returns the username of the authenticated user or null
     */
    public get username(): string | undefined {
        const info = this.userInfo;
        if (info) return info.sub;
        else return undefined;
    }

    /* Note(Jonas): Why is this necesary? This is just calling the method above. */
    public get activeUsername(): string | undefined {
        return this.username;
    }

    /**
     * @returns {string} the homefolder path for the currently logged in user (with trailing slash).
     */
    public get homeFolder(): string {
        return `/home/${this.username}/`;
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

    public get isLoggedIn(): boolean {
        return this.userInfo != null;
    }

    public get hasActiveProject(): boolean {
        return this.projectId !== undefined;
    }

    /**
     * @returns {string} the userrole. Empty string if none available in the JWT
     */
    get userRole(): string {
        const info = this.userInfo;
        if (info) return info.role;
        return "";
    }

    /**
     * @returns {boolean} whether or not the user is listed as an admin.
     */
    get userIsAdmin(): boolean {
        return this.userRole === "ADMIN";
    }

    /**
     * Attempts to receive a (non-expired) JWT access token from storage. In case the token has expired at attempt will
     * be made to refresh it. If it is not possible to refresh the token a MissingAuthError will be thrown. This would
     * indicate the user no longer has valid credentials. At this point it would make sense to present the user with
     * the login page.
     *
     * @return {Promise} a promise of an access token
     */
    public async receiveAccessTokenOrRefreshIt(): Promise<string> {
        await this.waitForCloudReady();

        let tokenPromise: Promise<any> | null = null;
        if (this.isTokenExpired() || this.forceRefresh) {
            tokenPromise = this.refresh();
            this.forceRefresh = false;
        } else {
            tokenPromise = new Promise((resolve) => resolve(this.retrieveTokenNow()));
        }
        return tokenPromise;
    }

    public createOneTimeTokenWithPermission(permission): Promise<any> {
        return this.receiveAccessTokenOrRefreshIt()
            .then(token => {
                const oneTimeToken = this.computeURL(this.authContext, `/request?audience=${permission}`);
                return new Promise((resolve, reject) => {
                    const req = new XMLHttpRequest();
                    req.open("POST", oneTimeToken);
                    req.setRequestHeader("Authorization", `Bearer ${token}`);
                    req.setRequestHeader("Content-Type", "application/json");
                    req.onload = () => {
                        try {
                            if (inRange({status: req.status, min: 200, max: 299})) {
                                const response = req.response.length === 0 ? "{}" : req.response;
                                resolve({response: JSON.parse(response), request: req});
                            } else {
                                reject(req.response);
                            }
                        } catch (e) {
                            reject(e.response);
                        }
                    };
                    req.send();
                });
            }).then((data: any) => new Promise(resolve => resolve(data.response.accessToken)));
    }

    /**
     * Returns the userInfo (the payload of the JWT). Be aware that the JWT is not verified, this means that a user
     * will be able to put whatever they want in this. This is normally not a problem since all backend services _will_
     * verify the token.
     */
    get userInfo(): undefined | JWT {
        const token = this.decodedToken;
        if (!token) return undefined;
        else return this.decodedToken.payload;
    }

    get principalType(): undefined | string {
        const userInfo = this.userInfo;
        if (!userInfo) return undefined;
        else return userInfo.principalType;
    }

    retrieveTokenNow(): string {
        return HttpClient.storedAccessToken;
    }

    private refreshPromise: Promise<string> | null = null;
    private async refresh(): Promise<string> {
        const loadingPromise = this.refreshPromise;
        if (loadingPromise !== null) return loadingPromise;

        const csrfToken = HttpClient.storedCsrfToken;
        if (!csrfToken) {
            return this.refreshPromise = new Promise((resolve, reject) => {
                this.refreshPromise = null;
                reject(this.missingAuth());
            });
        }

        const refreshPath = this.computeURL(this.authContext, "/refresh/web");
        return this.refreshPromise = new Promise((resolve, reject) => {
            const req = new XMLHttpRequest();
            req.open("POST", refreshPath);
            req.setRequestHeader("X-CSRFToken", csrfToken);
            if (DEVELOPMENT_ENV) {
                req.withCredentials = true;
            }

            req.onload = () => {
                try {
                    if (inSuccessRange(req.status)) {
                        resolve(JSON.parse(req.response));
                    } else {
                        if (req.status === 401 || req.status === 400) {
                            HttpClient.clearTokens();
                            this.openBrowserLoginPage();
                        }
                        reject({status: req.status, response: req.response});
                    }
                } catch (e) {
                    reject({status: e.status, response: e.response});
                }
            };
            req.send();
        }).then((data: any) => {
            return new Promise(resolve => {
                this.setTokens(data.accessToken, data.csrfToken);
                this.refreshPromise = null;
                resolve(data.accessToken);
            });
        });
    }

    public async invalidateAccessToken(): Promise<void> {
        this.accessToken = "invalid-token";
        await this.refresh();
    }

    /**
     * Updates tokens received by the auth service.
     *
     * @param accessToken the (JWT) access token.
     * @param csrfToken the csrf token
     */
    public setTokens(accessToken: string, csrfToken: string): void {
        if (!accessToken) throw this.missingAuth();

        this.accessToken = accessToken;
        HttpClient.storedAccessToken = accessToken;

        this.csrfToken = csrfToken;
        HttpClient.storedCsrfToken = csrfToken;

        this.decodedToken = this.decodeToken(accessToken);
    }

    private decodeToken(accessToken: string): {payload: any} | never {
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

    public async logout(): Promise<void> {
        try {
            const res = await fetch(`${this.context}${this.authContext}/logout/web`, {
                headers: {
                    "X-CSRFToken": HttpClient.storedCsrfToken,
                    "Content-Type": "application/json",
                },
                method: "POST",
                credentials: "same-origin"
            });
            if (!is5xxStatusCode(res.status)) {
                window.localStorage.removeItem("accessToken");
                window.localStorage.removeItem("csrfToken");
                window.sessionStorage.removeItem("redirect_on_login");
                setStoredProject(null);
                this.openBrowserLoginPage();
                return;
            }
            throw Error("The server was unreachable, please try again later.");
        } catch (err) {
            snackbarStore.addFailure(err.message, false);
        }
    }

    static clearTokens(): void {
        HttpClient.storedAccessToken = "";
        HttpClient.storedCsrfToken = "";
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

    static set storedCsrfToken(value: string) {
        window.localStorage.setItem("csrfToken", value);
    }

    private isTokenExpired(): boolean {
        const token = this.decodedToken;
        if (!token || !token.payload) return true;
        const nowInSeconds = Math.floor(Date.now() / 1000);
        const inOneMinute = nowInSeconds + (60);
        return token.payload.exp < inOneMinute;
    }

    private missingAuth(): 0 | MissingAuthError {
        if (this.redirectOnInvalidTokens) {
            this.openBrowserLoginPage();
            return 0;
        } else {
            return new MissingAuthError();
        }
    }
}

export interface JWT {
    sub: string;
    lastName?: string;
    aud: string;
    role: string;
    iss: string;
    firstNames?: string;
    exp: number;
    extendedByChain: any[];
    iat: number;
    principalType: string;
    publicSessionReference?: string;
    twoFactorAuthentication?: boolean;
    serviceLicenseAgreement?: boolean;
}

export class MissingAuthError {
    private name: string;

    constructor() {
        this.name = "MissingAuthError";
    }
}
