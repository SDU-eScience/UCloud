import {setPrioritizedSearch} from "@/Navigation/Redux/HeaderActions";
import * as React from "react";
import {useDispatch} from "react-redux";
import {buildQueryString} from "@/Utilities/URIUtilities";

export const searchPage = (priority: string, options: string | Record<string, string>): string => {
    let optionRecord: Record<string, string>;
    if (typeof options === "string") {
        optionRecord = {query: options};
    } else {
        optionRecord = options;
    }

    return buildQueryString(`/search/${encodeURIComponent(priority)}`, optionRecord);
};

export function usePrioritizedSearch(priority: "files" | "applications"): void {
    const dispatch = useDispatch();
    React.useEffect(() => {
        dispatch(setPrioritizedSearch(priority));
    }, []);
}
