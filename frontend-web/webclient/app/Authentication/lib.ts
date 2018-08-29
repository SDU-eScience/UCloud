import * as jwt from "jsonwebtoken";
import { failureNotification, inRange, is5xxStatusCode } from "UtilityFunctions";

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
    private refreshToken: string;
    private accessToken: string;
    private decodedToken: any;

    constructor() {
        let context = location.protocol + '//' +
            location.hostname +
            (location.port ? ':' + location.port : '');

        let serviceName: string;
        switch (location.hostname) {
            case "localhost":
                serviceName = "local-dev";
                break;

            default:
                serviceName = "web";
                break;
        }

        this.context = context;
        this.serviceName = serviceName;

        this.apiContext = "/api";
        this.authContext = "/auth";

        this.decodedToken = null;
        this.redirectOnInvalidTokens = true;

        let accessToken = SDUCloud.storedAccessToken;
        let refreshToken = SDUCloud.storedRefreshToken;
        if (accessToken) {
            this.setTokens(accessToken, refreshToken);
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
    call(method: string, path: string, body?: object, context: string = this.apiContext): Promise<any> {
        if (path.indexOf("/") !== 0) path = "/" + path;
        let baseContext = this.context;
        return this.receiveAccessTokenOrRefreshIt().then((token) => {
            // As long as same-origin is not handled server-side, use XHR.
            /* return fetch(baseContext + context + path, {
                body: !!body ? JSON.stringify(body) : null,
                method: method,
                headers: {
                    "Authorization": `Bearer ${token}`,
                    "content-type": "application/json"
                },
            }).then(response => {
                const contentType = response.headers.get("content-type");
                if (response.ok) {
                    if (contentType && contentType.includes("application/json")) {
                        return response.json().then((data) => ({ response: data, request: response }))
                    }
                    return { response: {}, request: response };
                }
                throw response.blob().then(data => ({ response: data, request: response }));
            }); */

            return new Promise((resolve, reject) => {
                let req = new XMLHttpRequest();
                req.open(method, baseContext + context + path);
                req.setRequestHeader("Authorization", `Bearer ${token}`);
                req.setRequestHeader("content-type", "application/json");
                req.responseType = "text"; // Explicitly set, otherwise issues with empty response
                req.onload = () => {
                    let responseContentType = req.getResponseHeader("content-type");
                    let parsedResponse = req.response.length === 0 ? "{}" : req.response;

                    // JSON Parsing
                    if (responseContentType !== null) {
                        if (responseContentType.indexOf("application/json") !== -1 ||
                            responseContentType.indexOf("application/javascript") !== -1) {
                            parsedResponse = JSON.parse(parsedResponse);
                        }
                    }

                    if (inRange({ status: req.status, min: 200, max: 299 })) {
                        resolve({
                            response: parsedResponse,
                            request: req,
                        });
                    } else {
                        reject({ request: req, response: parsedResponse });
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

    /**
     * Calls with the GET HTTP method. See call(method, path, body)
     */
    get(path, context = this.apiContext) {
        return this.call("GET", path, undefined, context);
    }

    /**
     * Calls with the POST HTTP method. See call(method, path, body)
     */
    post(path, body?: object, context = this.apiContext) {
        return this.call("POST", path, body, context);
    }

    /**
     * Calls with the PUT HTTP method. See call(method, path, body)
     */
    put(path, body, context = this.apiContext) {
        return this.call("PUT", path, body, context);
    }

    /**
     * Calls with the DELETE HTTP method. See call(method, path, body)
     */
    delete(path, body, context = this.apiContext) {
        return this.call("DELETE", path, body, context);
    }

    /**
     * Calls with the PATCH HTTP method. See call(method, path, body)
     */
    patch(path, body, context = this.apiContext) {
        return this.call("PATCH", path, body, context);
    }

    /**
     * Calls with the OPTIONS HTTP method. See call(method, path, body)
     */
    options(path, body, context = this.apiContext) {
        return this.call("OPTIONS", path, body, context);
    }

    /**
     * Calls with the HEAD HTTP method. See call(method, path, body)
     */
    head(path, context = this.apiContext) {
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
    get username() {
        let info = this.userInfo;
        if (info) return info.sub;
        else return null
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

    createOneTimeTokenWithPermission(permission) {
        return this.receiveAccessTokenOrRefreshIt()
            .then((token) => {
                let oneTimeToken = `${this.context}${this.authContext}/request/?audience=${permission}`;
                return new Promise((resolve, reject) => {
                    let req = new XMLHttpRequest();
                    req.open("POST", oneTimeToken);
                    req.setRequestHeader("Authorization", `Bearer ${token}`);
                    req.setRequestHeader("contentType", "application/json");
                    req.onload = () => {
                        if (inRange({ status: req.status, min: 200, max: 299 })) {
                            const response = req.response.length === 0 ? "{}" : req.response;
                            resolve({ response: JSON.parse(response), request: req });
                        } else {
                            reject(req.response);
                        }
                    };
                    req.send();
                });
            }).then((data: any) => new Promise((resolve, reject) => resolve(data.response.accessToken)));
    }

    private refresh() {
        const token = SDUCloud.storedRefreshToken;
        if (!token) {
            return new Promise((resolve, reject) => reject(this.missingAuth()));
        };
        const refreshPath = this.context + this.authContext + "/refresh";
        return new Promise((resolve, reject) => {
            let req = new XMLHttpRequest();
            req.open("POST", refreshPath);
            req.setRequestHeader("Authorization", `Bearer ${token}`);
            req.onload = () => {
                if (req.status === 200) {
                    resolve(JSON.parse(req.response));
                } else {
                    if (req.status === 401) this.clearTokens();
                    reject({ status: req.status, response: req.response });
                }
            };
            req.send();
        }).then((data: any) => {
            return new Promise((resolve, reject) => {
                this.setTokens(data.accessToken);
                resolve(data.accessToken);
            });
        });
    }

    /**
     * Updates tokens received by the auth service.
     *
     * @param accessToken the (JWT) access token.
     * @param refreshToken the refresh token (can be null)
     */
    setTokens(accessToken: string, refreshToken?: string) {
        if (!accessToken) throw this.missingAuth();

        this.accessToken = accessToken;
        SDUCloud.storedAccessToken = accessToken;

        if (refreshToken) {
            this.refreshToken = refreshToken;
            SDUCloud.storedRefreshToken = refreshToken;
        }

        try {
            this.decodedToken = jwt.decode(accessToken, { complete: true });
        } catch (err) {
            console.log("Received malformed JWT");
            this.clearTokens();
            throw err;
        }
    }

    logout() {
        /* fetch(`${this.context}${this.authContext}/logout`, {
            headers: {
                "Authorization": `Bearer ${SDUCloud.storedRefreshToken}`,
                "contentType": "application/json"
            },
            method: "POST"
        }).then(response => {
            if (!is5xxStatusCode(response.status)) {
                window.localStorage.removeItem("accessToken");
                window.localStorage.removeItem("refreshToken");
                this.openBrowserLoginPage();
                return;
            };
            throw Error("The server was unreachable, please try again later.")
        }).catch(err => failureNotification(err.message)); */
        new Promise((resolve, reject) => {
            let req = new XMLHttpRequest();
            req.open("POST", `${this.context}${this.authContext}/logout`);
            req.setRequestHeader("Authorization", `Bearer ${SDUCloud.storedRefreshToken}`);
            req.setRequestHeader("contentType", "application/json");
            req.onload = () => {
                if (!is5xxStatusCode(req.status)) {
                    resolve(req.response);
                }
                reject(req.response);
            };
            req.send();
        }).then(() => {
            this.clearTokens();
            this.openBrowserLoginPage();
        }).catch(() => {
            failureNotification("The server was unreachable, please try again later.");
        });
    }

    clearTokens() {
        SDUCloud.storedAccessToken = "";
        SDUCloud.storedRefreshToken = "";
    }

    static get storedAccessToken(): string {
        return window.localStorage.getItem("accessToken") || "";
    }

    static set storedAccessToken(value: string) {
        window.localStorage.setItem("accessToken", value);
    }

    static get storedRefreshToken(): string {
        return window.localStorage.getItem("refreshToken") || "";
    }

    static set storedRefreshToken(value) {
        window.localStorage.setItem("refreshToken", value);
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
