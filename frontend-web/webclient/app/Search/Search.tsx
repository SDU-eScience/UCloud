import {HeaderSearchType} from "@/DefaultObjects";
import {MainContainer} from "@/MainContainer/MainContainer";
import {setPrioritizedSearch, setRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {setActivePage, useTitle} from "@/Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {useLocation, useMatch, useNavigate} from "react-router";
import {Dispatch} from "redux";
import {SidebarPages} from "@/ui-components/Sidebar";
import {searchPage} from "@/Utilities/SearchUtilities";
import {getQueryParamOrElse, RouterLocationProps} from "@/Utilities/URIUtilities";
import {SearchProps, SimpleSearchOperations, SimpleSearchStateProps} from ".";
import * as Applications from "@/Applications";
import {useResourceSearch} from "@/Resource/Search";
import {ApiLike} from "@/Applications/Overview";

function Search(props: SearchProps): JSX.Element {
    const match = useMatch("/:priority/*");
    const priority = match?.params.priority!;
    const navigate = useNavigate();
    const location = useLocation();

    const q = query({location, navigate});

    React.useEffect(() => {
        props.setRefresh(fetchAll);
        return () => {
            props.clear();
            props.setRefresh();
        };
    }, []);


    React.useEffect(() => {
        props.setPrioritizedSearch(priority as HeaderSearchType);
    }, [priority]);

    useTitle("Search");

    function fetchAll(): void {
        navigate(searchPage(priority, q));
    }

    useResourceSearch(ApiLike);

    return (
        <MainContainer main={<Applications.SearchResults entriesPerPage={25} />} />
    );
}

const mapDispatchToProps = (dispatch: Dispatch): SimpleSearchOperations => ({
    clear: () => { },
    setPrioritizedSearch: sT => dispatch(setPrioritizedSearch(sT)),
    setActivePage: () => dispatch(setActivePage(SidebarPages.None)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
});

const mapStateToProps = ({}: ReduxObject): SimpleSearchStateProps => ({
});

export default connect(mapStateToProps, mapDispatchToProps)(Search);

function query(props: RouterLocationProps): string {
    return queryFromProps(props);
}

function queryFromProps(p: RouterLocationProps): string {
    return getQueryParamOrElse(p, "query", "");
}
