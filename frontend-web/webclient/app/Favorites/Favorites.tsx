import * as React from "react";
import {connect} from "react-redux";
import Installed from "Applications/Installed";
import {Dispatch} from "redux";
import {updatePageTitle, setActivePage} from "Navigation/Redux/StatusActions";
import {SidebarPages} from "ui-components/Sidebar";
import {History} from "history";

const Favorites = (props: FavoritesOperations & { history: History }) => {
    React.useEffect(() => {
        props.setPageTitle();
        props.setActivePage(SidebarPages.Favorites);
        return () => props.setActivePage(SidebarPages.None)
    }, []);

    return (<Installed header={null}/>)
};

interface FavoritesOperations {
    setPageTitle: () => void
    setActivePage: (page: SidebarPages) => void
}

const mapDispatchToProps = (dispatch: Dispatch): FavoritesOperations => ({
    setPageTitle: () => dispatch(updatePageTitle("Favorites")),
    setActivePage: page => dispatch(setActivePage(page))
});

export default connect(null, mapDispatchToProps)(Favorites);