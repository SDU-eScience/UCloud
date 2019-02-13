import * as jwt from "jsonwebtoken";
import {
    failureNotification,
    inRange,
    is5xxStatusCode,
    inSuccessRange
} from "UtilityFunctions";

export interface Override {
    path: string,
    destination: {
        scheme?: string
        host?: string
        port: number
    }
}

/**
 * Represents an instance of the SDUCloud object used for contacting the backend, implicitly using JWTs.
 */
export default class SDUCloud {
    /**
     * @constructor
     * @param {string} - context 
     * @param {string} - serviceName 
     */

    private readonly context: string;
    private readonly serviceName: string;
    private readonly authContext: string;
    private readonly redirectOnInvalidTokens: boolean;

    private apiContext: string;
    private accessToken: string;
    private csrfToken: string;
    private decodedToken: any;

    overrides: Override[] = [];

    constructor() {
        let context = location.protocol + '//' +
            location.hostname +
            (location.port ? ':' + location.port : '');

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
        this.redirectOnInvalidTokens = true;

        let accessToken = SDUCloud.storedAccessToken;
        let csrfToken = SDUCloud.storedCsrfToken;
        if (accessToken && csrfToken) {
            this.setTokens(accessToken, csrfToken);
        }
    }

    /**
     * Makes an AJAX call to the API. This will automatically add relevant authorization headers.
     * If the user's JWT has expired this will automatically attempt to refresh it.
     *
     * The path argument should be without the context, it should also not include the /api/ part of the context.
     * For example, to call `GET https://cloud.sdu.dk/api/files?path=/home/foobar` you should use
     * `cloud.call("GET", "/files?path=/home/foobar/")`
     *
     * The body argument is assumed to be JSON.
     *
     * @param {string} method - the HTTP method
     * @param {string} path - the path, should not contain context or /api/
     * @param {object} body - the request body, assumed to be a JS object to be encoded as JSON.
     * @param {string} context - the base of the request (e.g. "/api")
     * @return {Promise} promise
     */
    async call(method: string, path: string, body?: object, context: string = this.apiContext): Promise<any> {
        if (path.indexOf("/") !== 0) path = "/" + path;
        let baseContext = this.context;
        return this.receiveAccessTokenOrRefreshIt().then(token => {
            return new Promise((resolve, reject) => {
                let req = new XMLHttpRequest();
                req.open(method, this.computeURL(context, path));
                req.setRequestHeader("Authorization", `Bearer ${token}`);
                req.setRequestHeader("Content-Type", "application/json; charset=utf-8");
                req.responseType = "text"; // Explicitly set, otherwise issues with empty response
                req.onload = () => {
                    try {
                        let responseContentType = req.getResponseHeader("content-type");
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
                            reject({ request: req, response: parsedResponse });
                        }
                    } catch (e) {
                        reject({ request: e.req, response: e.response })
                    }
                };
                if (body) {
                    req.send(JSON.stringify(body));
                } else {
                    req.send();
                }
            });
        });
    }

    private computeURL(context: string, path: string): string {
        let absolutePath = context + path;
        for (let i = 0; i < this.overrides.length; i++) {
            let override = this.overrides[i];
            if (absolutePath.indexOf(override.path) === 0) {
                let scheme = override.destination.scheme ?
                    override.destination.scheme : "http";
                let host = override.destination.host ?
                    override.destination.host : "localhost";
                let port = override.destination.port;

                return scheme + "://" + host + ":" + port + absolutePath;
            }
        }

        return this.context + absolutePath;
    }

    /**
     * Calls with the GET HTTP method. See call(method, path, body)
     */
    async get<T = any>(path: string, context = this.apiContext): Promise<{ request: XMLHttpRequest, response: T }> {
        return this.call("GET", path, undefined, context);
    }

    /**
     * Calls with the POST HTTP method. See call(method, path, body)
     */
    async post<T = any>(path, body?: object, context = this.apiContext): Promise<{ request: XMLHttpRequest, response: T }> {
        return this.call("POST", path, body, context);
    }

    /**
     * Calls with the PUT HTTP method. See call(method, path, body)
     */
    async put<T = any>(path: string, body: object, context = this.apiContext): Promise<{ request: XMLHttpRequest, response: T }> {
        return this.call("PUT", path, body, context);
    }

    /**
     * Calls with the DELETE HTTP method. See call(method, path, body)
     */
    async delete(path: string, body: object, context = this.apiContext): Promise<any> {
        return this.call("DELETE", path, body, context);
    }

    /**
     * Calls with the PATCH HTTP method. See call(method, path, body)
     */
    async patch(path: string, body: object, context = this.apiContext): Promise<any> {
        return this.call("PATCH", path, body, context);
    }

    /**
     * Calls with the OPTIONS HTTP method. See call(method, path, body)
     */
    async options(path, body, context = this.apiContext): Promise<any> {
        return this.call("OPTIONS", path, body, context);
    }

    /**
     * Calls with the HEAD HTTP method. See call(method, path, body)
     */
    async head(path, context = this.apiContext): Promise<any> {
        return this.call("HEAD", path, undefined, context);
    }

    /**
     * Opens up a new page which contains the login page at the auth service. This login page will automatically
     * redirect back to the correct service (using serviceName).
     */
    openBrowserLoginPage() {
        window.location.href = this.context + this.authContext + "/login?service=" + encodeURIComponent(this.serviceName);
    }

    /**
     * @returns the username of the authenticated user or null
     */
    get username(): string | undefined {
        let info = this.userInfo;
        if (info) return info.sub;
        else return undefined;
    }

    /**
     * @returns {string} the homefolder path for the currently logged in user (with trailing slash).
     */
    get homeFolder(): string {
        let username = this.username;
        return `/home/${username}/`
    }

    /**
     * @returns {string} the location of the jobs folder for the currently logged in user (with trailing slash)
     */
    get jobFolder(): string {
        return `${this.homeFolder}Jobs/`
    }

    get trashFolder(): string {
        return `${this.homeFolder}Trash/`
    }

    get isLoggedIn(): boolean {
        return this.userInfo != null;
    }

    /**
     * @returns {string} the userrole. Null if none available in the JWT
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
     * Returns the userInfo (the payload of the JWT). Be aware that the JWT is not verified, this means that a user
     * will be able to put whatever they want in this. This is normally not a problem since all backend services _will_
     * verify the token.
     */
    get userInfo() {
        let token = this.decodedToken;
        if (!token) return null;
        else return this.decodedToken.payload;
    }

    /**
     * Attempts to receive a (non-expired) JWT access token from storage. In case the token has expired at attempt will
     * be made to refresh it. If it is not possible to refresh the token a MissingAuthError will be thrown. This would
     * indicate the user no longer has valid credentials. At this point it would make sense to present the user with
     * the login page.
     *
     * @return {Promise} a promise of an access token
     */
    receiveAccessTokenOrRefreshIt(): Promise<any> {
        let tokenPromise: Promise<any> | null = null;
        if (this.isTokenExpired()) {
            tokenPromise = this.refresh();
        } else {
            tokenPromise = new Promise((resolve, reject) => resolve(SDUCloud.storedAccessToken));
        }
        return tokenPromise;
    }

    createOneTimeTokenWithPermission(permission): Promise<any> {
        return this.receiveAccessTokenOrRefreshIt()
            .then(token => {
                let oneTimeToken = `${this.context}${this.authContext}/request/?audience=${permission}`;
                return new Promise((resolve, reject) => {
                    let req = new XMLHttpRequest();
                    req.open("POST", oneTimeToken);
                    req.setRequestHeader("Authorization", `Bearer ${token}`);
                    req.setRequestHeader("Content-Type", "application/json");
                    req.onload = () => {
                        try {
                            if (inRange({ status: req.status, min: 200, max: 299 })) {
                                const response = req.response.length === 0 ? "{}" : req.response;
                                resolve({ response: JSON.parse(response), request: req });
                            } else {
                                reject(req.response);
                            }
                        } catch (e) {
                            reject(e.response)
                        }
                    };
                    req.send();
                });
            }).then((data: any) => new Promise((resolve, reject) => resolve(data.response.accessToken)));
    }

    private refresh() {
        let csrfToken = SDUCloud.storedCsrfToken;
        if (!csrfToken) {
            return new Promise((resolve, reject) => {
                reject(this.missingAuth());
            });
        }

        let refreshPath = this.context + this.authContext + "/refresh/web";
        return new Promise((resolve, reject) => {
            let req = new XMLHttpRequest();
            req.open("POST", refreshPath);
            req.setRequestHeader("X-CSRFToken", csrfToken);
            req.onload = () => {
                try {
                    if (inSuccessRange(req.status)) {
                        resolve(JSON.parse(req.response));
                    } else {
                        if (req.status === 401 || req.status === 400) this.clearTokens();
                        reject({ status: req.status, response: req.response });
                    }
                } catch (e) {
                    reject({ status: e.status, response: e.response });
                }
            };
            req.send();
        }).then((data: any) => {
            return new Promise((resolve, reject) => {
                this.setTokens(data.accessToken, data.csrfToken);
                resolve(data.accessToken);
            });
        });
    }

    /**
     * Updates tokens received by the auth service.
     *
     * @param accessToken the (JWT) access token.
     * @param csrfToken the csrf token
     */
    setTokens(accessToken: string, csrfToken: string) {
        if (!accessToken) throw this.missingAuth();

        this.accessToken = accessToken;
        SDUCloud.storedAccessToken = accessToken;

        this.csrfToken = csrfToken;
        SDUCloud.storedCsrfToken = csrfToken;

        try {
            this.decodedToken = jwt.decode(accessToken, { complete: true });
        } catch (err) {
            console.log("Received malformed JWT");
            this.clearTokens();
            throw err;
        }
    }

    logout() {
        fetch(`${this.context}${this.authContext}/logout/web`, {
            headers: {
                "X-CSRFToken": SDUCloud.storedCsrfToken,
                "Content-Type": "application/json",
            },
            method: "POST",
            credentials: "same-origin"
        }).then(response => {
            if (!is5xxStatusCode(response.status)) {
                window.localStorage.removeItem("accessToken");
                window.localStorage.removeItem("csrfToken");
                this.openBrowserLoginPage();
                return;
            };
            throw Error("The server was unreachable, please try again later.")
        }).catch(err => failureNotification(err.message));
    }

    clearTokens() {
        SDUCloud.storedAccessToken = "";
        SDUCloud.storedCsrfToken = "";
    }

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

    private isTokenExpired() {
        let token = this.decodedToken;
        if (!token || !token.payload) return true;
        let nowInSeconds = Math.floor(Date.now() / 1000);
        let inFiveMinutes = nowInSeconds + (5 * 60);
        return token.payload.exp < inFiveMinutes;
    }

    private missingAuth() {
        if (this.redirectOnInvalidTokens) {
            this.openBrowserLoginPage();
            return 0;
        } else {
            return new MissingAuthError();
        }
    }
}

export class MissingAuthError {
    private name: string;
    constructor() {
        this.name = "MissingAuthError";
    }
}
