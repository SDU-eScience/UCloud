import {useHistory} from "react-router";
import {useSearch, useSearchPlaceholder} from "@/DefaultObjects";
import {useCallback} from "react";
import {buildQueryString, getQueryParam, getQueryParamOrElse} from "@/Utilities/URIUtilities";

export interface ReducedApiInterface {
    routingNamespace: string;
    titlePlural: string;
}

export function useResourceSearch(api: ReducedApiInterface) {
    const history = useHistory();
    const showTabs = getQueryParam(history.location.search, "showTabs");
    const onSearch = useCallback((q) => {
        if (q === "") {
            history.push(`/${api.routingNamespace}`);
        } else {
            history.push(buildQueryString(`/${api.routingNamespace}/search`, {q, showTabs: showTabs ?? undefined}));
        }
    }, [api]);

    useSearch(onSearch);
    const searchPlaceholder = `Search ${api.titlePlural.toLowerCase()}...`;
    useSearchPlaceholder(searchPlaceholder);
}
