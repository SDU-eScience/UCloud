import * as React from "react";
import {Resource, ResourceApi} from "@/UCloud/ResourceApi";
import {useHistory} from "react-router";
import {useSearch, useSearchPlaceholder} from "@/DefaultObjects";
import {useCallback} from "react";
import {buildQueryString} from "@/Utilities/URIUtilities";

export function useResourceSearch<Res extends Resource>(api: ResourceApi<Res, never>) {
    const history = useHistory();
    const onSearch = useCallback((q) => {
        if (q === "") {
            history.push(`/${api.routingNamespace}`);
        } else {
            history.push(buildQueryString(`/${api.routingNamespace}/search`, {q}));
        }
    }, [api]);

    useSearch(onSearch);
    const searchPlaceholder = `Search ${api.titlePlural.toLowerCase()}...`;
    useSearchPlaceholder(searchPlaceholder);
}
