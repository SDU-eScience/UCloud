import {setPrioritizedSearch} from "Navigation/Redux/HeaderActions";
import * as React from "react";
import {useDispatch} from "react-redux";

export const searchPage = (priority: string, query: string): string => {
    return `/search/${encodeURIComponent(priority)}?query=${encodeURIComponent(query)}`;
};

export function usePrioritizedSearch(priority: "files" | "applications"): void {
    const dispatch = useDispatch();
    React.useEffect(() => {
        dispatch(setPrioritizedSearch(priority));
    }, []);
}
