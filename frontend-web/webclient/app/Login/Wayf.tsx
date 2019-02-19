import { History } from "history";

function Wayf(props: { history: History }) {
    console.log(document.cookie); // Read cookie and redirect based on result.
    /* FIXME, not valid, but the intention is clear */
    if (!document.cookie.includes("2fa")) {
        props.history.push("/login", { "2fa": "value" })
        return null
    } else {
        props.history.push("/")
        return null;
    }
}

export default Wayf