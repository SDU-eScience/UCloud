import {useEffect, useState} from "react";
import {Client} from "@/Authentication/HttpClientInstance";
import {callAPI} from "@/Authentication/DataHook";
import * as Grants from "@/Grants";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {useProjectId} from "@/Project/Api";

export function useProjectHasProviderTools(): boolean {
    const projectId = useProjectId();
    const didUnmount = useDidUnmount();
    const [giftRequestsEnabled, setGiftRequestsEnabled] = useState(false);

    useEffect(() => {
        setGiftRequestsEnabled(false);
        if (!projectId) return;

        (async () => {
            try {
                const settings = await callAPI<Grants.RequestSettings>({
                    ...Grants.retrieveRequestSettings(),
                    projectOverride: projectId,
                });
                if (!didUnmount.current) setGiftRequestsEnabled(settings.enabled);
            } catch (e) {
                // Ignoring failure (the project does not support gift requests)
            }
        })();
    }, [projectId]);

    const managedProviders = Client.userInfo?.providerProjects ?? {};
    const managesAnyProvider = Object.values(managedProviders).includes(projectId ?? "");
    return managesAnyProvider || giftRequestsEnabled;
}
