import {HeaderSearchType} from "@/DefaultObjects";
import {MainContainer} from "@/MainContainer/MainContainer";
import {setPrioritizedSearch, setRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useLocation, useMatch, useNavigate} from "react-router";
import {searchPage} from "@/Utilities/SearchUtilities";
import {getQueryParamOrElse, RouterLocationProps} from "@/Utilities/URIUtilities";
import * as Applications from "@/Applications";
import {ReducedApiInterface, useResourceSearch} from "@/Resource/Search";

export const ApiLike: ReducedApiInterface = {
    routingNamespace: "applications",
    titlePlural: "Applications"
};

function Search(): JSX.Element {
    const match = useMatch("/:priority/*");
    const priority = match?.params.priority!;
    const navigate = useNavigate();
    const location = useLocation();
    const dispatch = useDispatch();

    const q = query({location, navigate});

    React.useEffect(() => {
        dispatch(setRefreshFunction(fetchAll));
        return () => {
            dispatch(setRefreshFunction());
        };
    }, []);

    React.useEffect(() => {
        dispatch(setPrioritizedSearch(priority as HeaderSearchType))
    }, [priority]);

    useTitle("Search");

    function fetchAll(): void {
        navigate(searchPage(priority, q));
    }

    useResourceSearch(ApiLike);

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
