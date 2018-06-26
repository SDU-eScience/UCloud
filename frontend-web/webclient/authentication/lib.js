import jwt from "jsonwebtoken";
import { failureNotification } from "../app/UtilityFunctions";

export default class SDUCloud {
    constructor(context, serviceName) {
        this.context = context;
        this.serviceName = serviceName;

        this.apiContext = context + "/api";
        this.authContext = context + "/auth";

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
     * @param method the HTTP method
     * @param path the path, should not contain context or /api/
     * @param body the request body, assumed to be a JS object to be encoded as JSON.
     */
    call(method, path, body) {
        if (path.indexOf("/") !== 0) path = "/" + path;
        return this.receiveAccessTokenOrRefreshIt().then((token) => {
            return new Promise((resolve, reject) => {
                let req = new XMLHttpRequest();
                req.open(method, this.apiContext + path);
                req.setRequestHeader("Authorization", `Bearer ${token}`);
                req.setRequestHeader("contentType", "application/json");
                req.responseType = "text"; // Explicitely set, otherwise issues with empty response
                req.onload = () => {
                    let responseContentType = req.getResponseHeader("content-type");
                    let parsedResponse = req.response;

                    // JSON Parsing
                    if (responseContentType !== null) {
                        if (responseContentType.indexOf("application/json") !== -1 ||
                            responseContentType.indexOf("application/javascript") !== -1) {
                            parsedResponse = JSON.parse(parsedResponse);
                        }
                    }

                    if (req.status >= 200 && req.status <= 299) {
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
    get(path) {
        return this.call("GET", path, null);
    }

    /**
     * Calls with the POST HTTP method. See call(method, path, body)
     */
    post(path, body) {
        return this.call("POST", path, body);
    }

    /**
     * Calls with the PUT HTTP method. See call(method, path, body)
     */
    put(path, body) {
        return this.call("PUT", path, body);
    }

    /**
     * Calls with the DELETE HTTP method. See call(method, path, body)
     */
    delete(path, body) {
        return this.call("DELETE", path, body);
    }

    /**
     * Calls with the PATCH HTTP method. See call(method, path, body)
     */
    patch(path, body) {
        return this.call("PATCH", path, body);
    }

    /**
     * Calls with the OPTIONS HTTP method. See call(method, path, body)
     */
    options(path, body) {
        return this.call("OPTIONS", path, body);
    }

    /**
     * Calls with the HEAD HTTP method. See call(method, path, body)
     */
    head(path) {
        return this.call("HEAD", path, null);
    }

    /**
     * Opens up a new page which contains the login page at the auth service. This login page will automatically
     * redirect back to the correct service (using serviceName).
     */
    openBrowserLoginPage() {
        window.location.href = this.authContext + "/login?service=" + encodeURIComponent(this.serviceName);
    }

    /**
     * Returns the username of the authenticated user or null
     */
    get username() {
        let info = this.userInfo;
        if (info) return info.sub;
        else return null
    }

    get homeFolder() {
        let username = this.username;
        return `/home/${username}/`
    }

    get jobFolder() {
        return `${this.homeFolder}/Jobs`
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
     * @return a promise of an access token
     */
    receiveAccessTokenOrRefreshIt() {
        let tokenPromise = null;
        if (this._isTokenExpired()) {
            tokenPromise = this._refresh();
        } else {
            tokenPromise = new Promise((resolve, reject) => {
                resolve(SDUCloud.storedAccessToken);
            });
        }
        return tokenPromise;
    }

    createOneTimeTokenWithPermission(permission) {
        return this.receiveAccessTokenOrRefreshIt()
            .then((token) => {
                let oneTimeToken = `${this.authContext}/request/?audience=${permission}`;
                return new Promise((resolve, reject) => {
                    let req = new XMLHttpRequest();
                    req.open("POST", oneTimeToken);
                    req.setRequestHeader("Authorization", `Bearer ${token}`);
                    req.setRequestHeader("contentType", "application/json");
                    req.onload = () => {
                        if (req.status >= 200 && req.status <= 299) {
                            resolve({ response: JSON.parse(req.response), request: req });
                        } else {
                            reject(req.response);
                        }
                    };
                    req.send();
                });
            }).then((data) => {
                return new Promise((resolve, reject) => {
                    resolve(data.response.accessToken);
                });
            });
    }

    _refresh() {
        let token = SDUCloud.storedRefreshToken;
        if (!token) {
            return new Promise((resolve, reject) => {
                reject(this._missingAuth());
            });
        }
        ;
        let refreshPath = this.authContext + "/refresh";
        return new Promise((resolve, reject) => {
            let req = new XMLHttpRequest();
            req.open("POST", refreshPath);
            req.setRequestHeader("Authorization", `Bearer ${token}`);
            req.onload = () => {
                if (req.status === 200) {
                    resolve(JSON.parse(req.response));
                } else {
                    reject(req.status, JSON.parse(req.response));
                }
            };
            req.send();
        }).then((data) => {
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
    setTokens(accessToken, refreshToken) {
        if (!accessToken) throw this._missingAuth();

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
            SDUCloud.storedAccessToken = null;
            SDUCloud.storedRefreshToken = null;
            throw err;
        }
    }

    logout() {
        new Promise((resolve, reject) => {
            let req = new XMLHttpRequest();
            req.open("POST", `${this.authContext}/logout`);
            req.setRequestHeader("Authorization", `Bearer ${SDUCloud.storedRefreshToken}`);
            req.setRequestHeader("contentType", "application/json");
            req.onload = () => {
                if (req.status >= 200 && req.status <= 299) {
                    resolve(req.response);
                } else {
                    reject(req.response);
                }
            };
            req.send();
        })
        .then(e => {
            window.localStorage.removeItem("accessToken");
            window.localStorage.removeItem("refreshToken");
            this.openBrowserLoginPage()
        })
        .catch(e => {
            failureNotification("Unable to logout. Try again later...");
            console.warn("Unable to invalidate session server side!");
        });
    }

    static get storedAccessToken() {
        return window.localStorage.getItem("accessToken");
    }

    static set storedAccessToken(value) {
        window.localStorage.setItem("accessToken", value);
    }

    static get storedRefreshToken() {
        return window.localStorage.getItem("refreshToken");
    }

    static set storedRefreshToken(value) {
        window.localStorage.setItem("refreshToken", value);
    }

    _isTokenExpired() {
        let token = this.decodedToken;
        if (!token || !token.payload) return true;
        let nowInSeconds = Math.floor(Date.now() / 1000);
        let inFiveMinutes = nowInSeconds + (5 * 60);
        return token.payload.exp < inFiveMinutes;
    }

    _missingAuth() {
        if (this.redirectOnInvalidTokens) {
            this.openBrowserLoginPage();
            return 0;
        } else {
            return new MissingAuthError();
        }
    }
}

export class MissingAuthError {
    constructor() {
        this.name = "MissingAuthError";
    }
}
