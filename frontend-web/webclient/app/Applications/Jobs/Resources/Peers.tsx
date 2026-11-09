import {Application} from "@/Applications/AppStoreApi";

export function peerResourceAllowed(app: Application) {
    const invocation = app.invocation;
    const tool = invocation.tool.tool!.description;
    return (invocation.allowAdditionalPeers !== false && tool.backend === "DOCKER") ||
        invocation.allowAdditionalPeers === true;
}
