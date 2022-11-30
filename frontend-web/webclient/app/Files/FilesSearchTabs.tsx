import * as React from "react";
import {useLocation, useNavigate} from "react-router";
import {buildQueryString, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useCallback} from "react";
import {SelectableText, SelectableTextWrapper} from "@/ui-components";

type ActiveTab = "FILES" | "COLLECTIONS" | "APPLICATIONS";

export const FilesSearchTabs: React.FunctionComponent<{active: ActiveTab}> = props => {
    const location = useLocation();
    const navigate = useNavigate();
    const query = getQueryParamOrElse(location.search, "q", "");

    const goToApplications = useCallback(() => {
        navigate(buildQueryString("/applications/search", {q: query}));
    }, [query]);

    const goToFiles = useCallback(() => {
        navigate(buildQueryString("/files/search", {q: query}));
    }, [query]);

    const goToCollections = useCallback(() => {
        navigate(buildQueryString("/drives/search", {q: query}));
    }, [query]);

    return <SelectableTextWrapper>
        <SelectableText selected={props.active === "COLLECTIONS"} onClick={goToCollections}>Drives</SelectableText>
        <SelectableText selected={props.active === "FILES"} onClick={goToFiles}>Files</SelectableText>
        <SelectableText selected={props.active === "APPLICATIONS"} onClick={goToApplications}>Applications</SelectableText>
    </SelectableTextWrapper>
};
