import {useGlobal} from "@/Utilities/ReduxHooks";
import * as AppStore from "@/Applications/AppStoreApi";
import {CatalogDiscovery} from "@/Applications/AppStoreApi";
import {useCallback} from "react";

const localStorageKey = "catalog-discovery-pref";
let _initialDiscovery: CatalogDiscovery | null = null;

// NOTE(Dan): separate function to ensure we don't do unnecessary reads from localStorage.
function getInitialDiscovery(): CatalogDiscovery {
    if (_initialDiscovery) return _initialDiscovery;
    const text = localStorage.getItem(localStorageKey);
    if (!text) {
        _initialDiscovery = AppStore.defaultCatalogDiscovery;
    } else {
        try {
            _initialDiscovery = JSON.parse(text) as CatalogDiscovery;
        } catch (e) {
            _initialDiscovery = AppStore.defaultCatalogDiscovery;
        }
    }
    return _initialDiscovery;
}


export function useDiscovery(): [CatalogDiscovery, (newDiscovery: CatalogDiscovery) => void] {
    const [discoveryMode, setDiscoveryMode] = useGlobal("catalogDiscovery", getInitialDiscovery());

    const setter = useCallback((mode: CatalogDiscovery) => {
        setDiscoveryMode(mode);
        localStorage.setItem(localStorageKey, JSON.stringify(mode));
    }, [setDiscoveryMode]);

    return [discoveryMode, setter];
}
