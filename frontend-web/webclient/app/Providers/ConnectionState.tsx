import {callAPI} from "@/Authentication/DataHook";
import {UState} from "@/Utilities/UState";
import {timestampUnixMs} from "@/UtilityFunctions";
import {PageV2, provider} from "@/UCloud";
import IntegrationApi = provider.im;
import {hasUploadedSigningKeyToProvider, retrieveOrInitializePublicSigningKey, markSigningKeyAsUploadedToProvider} from "@/Authentication/MessageSigning";
import {LocalStorageCache} from "@/Utilities/LocalStorageCache";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {sendNotification} from "@/Notifications";
import React from "react";
import BaseLink from "@/ui-components/BaseLink";
import {ProviderTitle} from "./ProviderTitle";

class ConnectionState extends UState<ConnectionState> {
    private lastConnectionAt = new LocalStorageCache<number>("last-connection-at");
    private lastFetch = 0;
    private connectionInfo: Record<string, provider.IntegrationBrowseResponseItem> = {};

    public fetch(maxAgeMs: number = 1000 * 60 * 30) {
        const now = timestampUnixMs();
        if (now - this.lastFetch < maxAgeMs) return;

        this.run(async () => {
            const now = timestampUnixMs();
            if (now - this.lastFetch < maxAgeMs) return;

            try {
                this.lastFetch = timestampUnixMs() - maxAgeMs + 500; 

                const page = await callAPI<PageV2<provider.IntegrationBrowseResponseItem>>(
                    IntegrationApi.browse({itemsPerPage: 250})
                );

                this.lastFetch = timestampUnixMs();

                page.items.forEach(p => {
                    this.connectionInfo[p.providerTitle] = p;

                    if (this.canConnectToProvider(p.providerTitle)) {
                        sendNotification({
                            icon: "key",
                            title: `Connection required`,
                            body: <>
                                You must <BaseLink href="#">re-connect</BaseLink> with
                                '<ProviderTitle providerId={p.providerTitle} />' to continue using it.
                            </>,
                            isPinned: true,
                            uniqueId: `${p.providerTitle}-${this.lastConnectionAt.retrieve() ?? 0}`,
                            onAction: () => {
                                document.location.href = "/app/providers/connect"; // TODO(???)
                            }
                        });
                    }
                });
            } catch (e) {
                window.setTimeout(() => {
                    this.fetch(maxAgeMs);
                }, 1000);
            }
        });
    }

    public fetchFresh() {
        this.fetch(1000);
    }

    public get lastRefresh() {
        return this.lastFetch;
    }

    public canConnectToProvider(providerId: string): boolean {
        if (providerId === "ucloud" || providerId === "aau" || providerId === "aau-test") return false;

        this.fetch();
        const data = this.connectionInfo[providerId];
        if (!data) return false;

        return !data.connected ||
            (data.requiresMessageSigning === true && !hasUploadedSigningKeyToProvider(data.providerTitle));
    }

    public get providers(): provider.IntegrationBrowseResponseItem[] {
        this.fetch();
        return Object.values(this.connectionInfo);
    }

    public connectToProvider(providerId: string) {
        if (!this.canConnectToProvider(providerId)) return;

        const providerData = this.connectionInfo[providerId];

        this.run(async () => {
            this.lastConnectionAt.update(timestampUnixMs());
            const res = await callAPI<provider.IntegrationConnectResponse>(
                IntegrationApi.connect({provider: providerData.provider})
            );

            if (res) {
                if (providerData.requiresMessageSigning) {
                    const postOptions: RequestInit = {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json",
                            "Accept": "application/json",
                        },
                        mode: "cors",
                        credentials: "omit",
                        body: JSON.stringify({
                            publicKey: retrieveOrInitializePublicSigningKey()
                        })
                    };

                    fetch(res.redirectTo, postOptions)
                        .then(it => it.json())
                        .then(resp => {
                            const redirectTo = resp["redirectTo"];
                            if (!redirectTo || typeof redirectTo !== "string") throw "Invalid response from server";

                            // TODO(Dan): There is no guarantee that this was _actually_ successful. But we are also never
                            //   notified by anything that this went well, so we have no other choice that to just assume
                            //   that authentication is going to go well. Worst case, the user will have their key
                            //   invalidated when they attempt to make a request.

                            markSigningKeyAsUploadedToProvider(providerData.providerTitle);

                            document.location.href = redirectTo;
                        })
                        .catch(() => {
                            snackbarStore.addFailure(
                                "UCloud was not able to initiate a connection. Try again later.",
                                true
                            );
                        });
                } else {
                    document.location.href = res.redirectTo;
                }
            }
        });
    }
}

export const connectionState = new ConnectionState();
