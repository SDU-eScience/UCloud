import {HeaderSearchType} from "@/DefaultObjects";
import {MainContainer} from "@/MainContainer/MainContainer";
import {setPrioritizedSearch, setRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {setActivePage, useTitle} from "@/Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {useHistory, useLocation, useRouteMatch} from "react-router";
import {Dispatch} from "redux";
import {SelectableText, SelectableTextWrapper} from "@/ui-components";
import Hide from "@/ui-components/Hide";
import {SidebarPages} from "@/ui-components/Sidebar";
import {Spacer} from "@/ui-components/Spacer";
import {searchPage} from "@/Utilities/SearchUtilities";
import {getQueryParamOrElse, RouterLocationProps} from "@/Utilities/URIUtilities";
import {prettierString} from "@/UtilityFunctions";
import {SearchProps, SimpleSearchOperations, SimpleSearchStateProps} from ".";
import * as SSActions from "./Redux/SearchActions";
import * as Applications from "@/Applications";

function Search(props: SearchProps): JSX.Element {
    const match = useRouteMatch<{priority: string}>();
    const history = useHistory();
    const location = useLocation();


    React.useEffect(() => {
        props.toggleAdvancedSearch();
        const q = query({location, history});
        props.setSearch(q);
        props.setPrioritizedSearch(match.params.priority as HeaderSearchType);
        props.setRefresh(fetchAll);
        return () => {
            props.toggleAdvancedSearch();
            props.clear();
            props.setRefresh();
        };
    }, []);


    React.useEffect(() => {
        props.setPrioritizedSearch(match.params.priority as HeaderSearchType);
    }, [match.params.priority]);

    useTitle("Search");

    const setPath = (text: string): void => history.push(searchPage(text.toLocaleLowerCase(), props.search));

    function fetchAll(): void {
        history.push(searchPage(match.params.priority, props.search));
    }

    const Tab = ({searchType}: {searchType: HeaderSearchType}): JSX.Element => (
        <SelectableText
            cursor="pointer"
            fontSize={3}
            onClick={() => setPath(searchType)}
            selected={priority === searchType}
            mr="1em"
        >
            {prettierString(searchType)}
        </SelectableText>
    );

    const allowedSearchTypes: HeaderSearchType[] = ["files", "applications"];

    let main: React.ReactNode = null;
    const {priority} = match.params;
    const entriesPerPage =  null;
    if (priority === "applications") {
        main = <>
            <Hide xxl xl lg>
                <Applications.SearchWidget partOfResults />
            </Hide>
            <Applications.SearchResults entriesPerPage={25} />
        </>
    }

    return (
        <MainContainer
            header={(
                <React.Fragment>
                    <SelectableTextWrapper>
                        {allowedSearchTypes.map((pane, index) => <Tab searchType={pane} key={index} />)}
                    </SelectableTextWrapper>
                    <Hide md sm xs>
                        <Spacer left={null} right={entriesPerPage} />
                    </Hide>
                </React.Fragment>
            )}
            main={main}
        />
    );
}

const mapDispatchToProps = (dispatch: Dispatch): SimpleSearchOperations => ({
    clear: () => {},
    setSearch: search => dispatch(SSActions.setSearch(search)),
    setPrioritizedSearch: sT => dispatch(setPrioritizedSearch(sT)),
    toggleAdvancedSearch: () => {},
    setActivePage: () => dispatch(setActivePage(SidebarPages.None)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
});

const mapStateToProps = ({
    simpleSearch,
}: ReduxObject): SimpleSearchStateProps => ({
    ...simpleSearch,
});

export default connect(mapStateToProps, mapDispatchToProps)(Search);

function query(props: RouterLocationProps): string {
    return queryFromProps(props);
}

function queryFromProps(p: RouterLocationProps): string {
    return getQueryParamOrElse(p, "query", "");
}
