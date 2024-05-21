import * as React from "react";
import {inDevEnvironment} from "@/UtilityFunctions";
import {LoginPage} from "./Login";
import {Navigate} from "react-router";

// https://stackoverflow.com/a/2138471
export function setCookie(name: string, value: string, days: number): void {
    let expires = "";
    if (days) {
        const date = new Date();
        date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
        expires = "; expires=" + date.toUTCString();
    }
    document.cookie = name + "=" + (value || "") + expires + "; path=/";
}

export function getCookie(name: string): string | null {
    const nameEQ = name + "=";
    const ca = document.cookie.split(";");
    for (let c of ca) {
        while (c.charAt(0) === " ") c = c.substring(1, c.length);
        if (c.indexOf(nameEQ) === 0) return c.substring(nameEQ.length, c.length);
    }
    return null;
}

function eraseCookie(name: string): void {
    document.cookie = name + "=; path=/; Max-Age=-99999999;";
}

type WayfTestState = "success" | "2fa";
const testState: WayfTestState | null = null;

function Wayf(): React.ReactNode {
    const authCookieName = "authState";

    if (inDevEnvironment()) {
        if (testState === "success") {
            setCookie(
                authCookieName,
                JSON.stringify({
                    accessToken: "access",
                    csrfToken: "csrf"
                }),
                7
            );

            setCookie(
                "refreshToken",
                "refresh",
                7
            );
        } else if (testState === "2fa") {
            setCookie(
                authCookieName,
                JSON.stringify({
                    "2fa": "fakechallenge"
                }),
                7
            );
        }
    }

    const authStateCookie = getCookie(authCookieName);
    if (authStateCookie === null) {
        // eslint-disable-next-line no-console
        console.log("Found no auth state!");
    } else {
        const authState = JSON.parse(decodeURIComponent(authStateCookie));
        eraseCookie(authCookieName);
        return <LoginPage initialState={authState} />;
    }

    // Note(Jonas): An attempt to work around the bad setState warning/error.
    return <Navigate to="/" />;
}

export default Wayf;
