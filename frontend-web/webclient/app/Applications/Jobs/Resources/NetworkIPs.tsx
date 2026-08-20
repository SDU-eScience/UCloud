import {Application} from "@/Applications/AppStoreApi";

export function networkIPResourceAllowed(app: Application): boolean {
    return app.invocation.allowPublicIp;
}
