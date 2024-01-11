import {MainContainer} from "@/ui-components/MainContainer";
import {useTitle} from "@/Navigation/Redux";
import * as React from "react";
import {useLocation, useMatch, useNavigate} from "react-router";
import {searchPage} from "@/Utilities/SearchUtilities";
import {getQueryParamOrElse, RouterLocationProps} from "@/Utilities/URIUtilities";
import * as Applications from "@/Applications";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";

function Search(): JSX.Element {
    const match = useMatch("/:priority/*");
    const priority = match?.params.priority!;
    const navigate = useNavigate();
    const location = useLocation();

    const q = query({location, navigate});
    
    useSetRefreshFunction(fetchAll);

    useTitle("Search");

    function fetchAll(): void {
        navigate(searchPage(priority, q));
    }

    return (
        <MainContainer main={<Applications.SearchResults />} />
    );
}

export default Search;

function query(props: RouterLocationProps): string {
    return queryFromProps(props);
}

function queryFromProps(p: RouterLocationProps): string {
    return getQueryParamOrElse(p, "query", "");
}
