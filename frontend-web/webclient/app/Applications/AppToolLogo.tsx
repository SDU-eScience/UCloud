import {AppOrTool} from "@/Applications/api";
import * as localForage from "localforage";
import {AppLogo, hashF} from "@/Applications/Card";
import {Client} from "@/Authentication/HttpClientInstance";
import * as React from "react";
import {useEffect, useState} from "react";

interface AppToolLogoProps {
    name: string;
    size?: string;
    type: AppOrTool;
}

export const AppToolLogo: React.FunctionComponent<AppToolLogoProps> = props => {
    const size = props.size !== undefined ? props.size : "48px";

    const [dataUrl, setDataUrl] = useState<string | null | "loading">("loading");
    useEffect(() => {
        let didCancel = false;
        /* NOTE(jonas): `props.name` is sometimes an empty string, why? */
        if (!props.name) return;
        (async () => {
            const fetchedLogo = props.type === "APPLICATION" ?
                await appLogoCache.fetchLogo(props.name) :
                await toolLogoCache.fetchLogo(props.name);

            if (!didCancel) {
                setDataUrl(fetchedLogo);
            }
        })();

        return () => {
            didCancel = true;
        };
    }, [props.name]);

    return dataUrl === "loading" ?
        <div style={{width: size, height: size}} /> :
        dataUrl === null ?
            <AppLogo size={size} hash={hashF(props.name)} /> :
            <img src={dataUrl} alt={props.name}
                style={{width: size, height: size, objectFit: "contain"}}
            />;
};

class LogoCache {
    private readonly context: string;

    constructor(context: string) {
        this.context = context;
        (async () => {
            const now = window.performance &&
                window.performance["now"] &&
                window.performance.timing &&
                window.performance.timing.navigationStart ?
                window.performance.now() + window.performance.timing.navigationStart : Date.now();
            const expiry = await localForage.getItem<number>("logoCacheExpiry");
            if (expiry !== null && expiry >= now) {
                this.clear();
            }

            localForage.setItem("logoCacheExpiry", now + (1000 * 60 * 60 * 24 * 3));
        })();

    }

    public async fetchLogo(name: string): Promise<string | null> {
        const itemKey = `${this.context}/${name}`;
        const retrievedItem = await localForage.getItem<Blob | false>(itemKey);
        if (retrievedItem === null) {
            // No cache entry at all
            const url = Client.computeURL("/api", `/hpc/${this.context}/logo?name=${encodeURIComponent(name)}`);
            try {
                const blob = await (await fetch(url)).blob();
                if (blob.type.indexOf("image/") === 0) {
                    localForage.setItem(itemKey, blob);
                    return URL.createObjectURL(blob);
                } else {
                    localForage.setItem(itemKey, false);
                    return null;
                }
            } catch (e) {
                console.warn(e);
                localForage.setItem(itemKey, false);
                return null;
            }
        } else {
            if (retrievedItem === false) {
                return null;
            } else if (retrievedItem.type.indexOf("image/") === 0) {
                return URL.createObjectURL(retrievedItem);
            } else {
                return null;
            }
        }
    }

    public forget(name: string) {
        localForage.removeItem(`${this.context}/${name}`);
    }

    public clear() {
        const itemsToRemove: string[] = [];
        localForage.iterate((value, key) => {
            if (key.indexOf(this.context) === 0) {
                itemsToRemove.push(key);
            }
        });

        itemsToRemove.forEach(key => localForage.removeItem(key));
    }
}

export const appLogoCache = new LogoCache("apps");
export const toolLogoCache = new LogoCache("tools");
