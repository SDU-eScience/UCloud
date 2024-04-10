import Rusha from "rusha";
import {ShaSumMessage} from ".";

const hasher = Rusha.createHash();

onmessage = (e) => {
    const message = e.data as ShaSumMessage;
    switch (message.type) {
        case "Start": {
            hasher.digest("hex");
            break;
        }
        case "Update": {
            hasher.update(message.data);
            break;
        }
        case "End": {
            postMessage(hasher.digest("hex"));
            break;
        }
    }
};
