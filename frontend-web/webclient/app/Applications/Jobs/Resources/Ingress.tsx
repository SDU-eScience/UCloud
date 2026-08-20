import {Application} from "@/Applications/AppStoreApi";

export function ingressResourceAllowed(app: Application, bindLinkToPort = false): boolean {
    if (app.invocation.allowPublicLink === false) return false;
    if (app.invocation.applicationType === "WEB") return true;
    return app.invocation.tool.tool?.description.backend === "VIRTUAL_MACHINE" && bindLinkToPort;
}
