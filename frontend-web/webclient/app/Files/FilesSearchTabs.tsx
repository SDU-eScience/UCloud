import * as React from "react";
import {useHistory} from "react-router";
import {buildQueryString, getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useCallback} from "react";
import {SelectableText, SelectableTextWrapper} from "@/ui-components";

type ActiveTab = "FILES" | "COLLECTIONS" | "APPLICATIONS";

export const FilesSearchTabs: React.FunctionComponent<{active: ActiveTab}> = props => {
    const history = useHistory();
    const query = getQueryParamOrElse(history.location.search, "q", "");

    const goToApplications = useCallback(() => {
        history.push(buildQueryString("/applications/search", {q: query}));
    }, [query]);

    const goToFiles = useCallback(() => {
        history.push(buildQueryString("/files/search", {q: query}));
    }, [query]);

    const goToCollections = useCallback(() => {
        history.push(buildQueryString("/drives/search", {q: query}));
    }, [query]);

    return <SelectableTextWrapper>
        <SelectableText selected={props.active === "COLLECTIONS"} onClick={goToCollections}>Drives</SelectableText>
        <SelectableText selected={props.active === "FILES"} onClick={goToFiles}>Files</SelectableText>
        <SelectableText selected={props.active === "APPLICATIONS"} onClick={goToApplications}>Applications</SelectableText>
    </SelectableTextWrapper>
};
