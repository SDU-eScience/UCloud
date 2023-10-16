import {useNavigate} from "react-router";
import {useSearch, useSearchPlaceholder} from "@/DefaultObjects";
import {useCallback} from "react";
import {buildQueryString} from "@/Utilities/URIUtilities";

export interface ReducedApiInterface {
    routingNamespace: string;
    titlePlural: string;
}

export function useResourceSearch(api: ReducedApiInterface) {
    const navigate = useNavigate();
    const onSearch = useCallback((q) => {
        if (q === "") {
            navigate(`/${api.routingNamespace}`);
        } else {
            navigate(buildQueryString(`/${api.routingNamespace}/search`, {q}));
        }
    }, [api]);

    useSearch(onSearch);
    const searchPlaceholder = `Search ${api.titlePlural.toLowerCase()}...`;
    useSearchPlaceholder(searchPlaceholder);
}
