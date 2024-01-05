import {HttpClient} from "./lib";
import {WebSocketFactory} from "./ws";
import {onDevSite} from "@/UtilityFunctions";
import {snackbarStore} from "@/Snackbar/SnackbarStore";

export const Client = new HttpClient();
export const WSFactory = new WebSocketFactory(Client);

// Note(Jonas): This code adds an event listener on localhost and the dev site. With my current setup, I get logged out on
// retrieving a new JWT, so this solution will allow me to more quickly fetch the token values from `dev`, and copy them to my
// localhost run on my machine.
if (onDevSite()) {
    document.body.addEventListener("keydown", async e => {
        if (e.altKey) {
            if (e.code === "KeyK") {
                await Client.receiveAccessTokenOrRefreshIt();
                await navigator.clipboard.writeText(`localStorage.accessToken="${localStorage.accessToken}";localStorage.csrfToken="${localStorage.csrfToken}";`);
                snackbarStore.addFailure("Copied CSRF and access token to clipboard", false);
            } else if (e.code === "KeyL") {

                // Not allowed on Firefox!
                // const [accessToken, csrfToken] = (await navigator.clipboard.readText()).split("\n");
                // localStorage.setItem("accessToken", accessToken);
                // localStorage.setItem("csrfToken", csrfToken);
                // window.location.reload();
            }
        }
    });
}
