import * as React from "react";
import { connect } from "react-redux";
import Installed from "Applications/Installed";
import { SearchOptions, SelectableText } from "Search/Search";
import { prettierString } from "UtilityFunctions";
import FavoriteFiles from "./FavoriteFiles";
import { Dispatch } from "redux";
import { updatePageTitle, setActivePage } from "Navigation/Redux/StatusActions";
import { ReduxObject } from "DefaultObjects";
import { setFavoritesShown } from "./Redux/FavoritesActions";
import { SidebarPages } from "ui-components/Sidebar";
import { History } from "history";

export enum FavoriteType {
    FILES = "FILES",
    APPLICATIONS = "APPLICATIONS"
}

const Favorites = (props: FavoritesStateProps & FavoritesOperations & { history: History }) => {
    React.useEffect(() => {
        props.setPageTitle();
        props.setActivePage(SidebarPages.Favorites);
        return () => props.setActivePage(SidebarPages.None)
    }, []);

    const { shown, setShown } = props;
    const header = (<SearchOptions>
        {Object.keys(FavoriteType).map((it: FavoriteType) =>
            <SelectableText cursor="pointer" key={it} onClick={() => setShown(it)} mr="1em" selected={it === shown}>
                {prettierString(it)}
            </SelectableText>
        )}
    </SearchOptions>);
    if (shown === FavoriteType.FILES) {
        return (<FavoriteFiles header={header} history={props.history} />);
    } else {
        return (<Installed header={header} />)
    }
}

interface FavoritesStateProps { shown: FavoriteType }

const mapStateToProps = (state: ReduxObject): FavoritesStateProps => ({ shown: state.favorites.shown })

interface FavoritesOperations {
    setPageTitle: () => void
    setShown: (type: FavoriteType) => void
    setActivePage: (page: SidebarPages) => void
}

const mapDispatchToProps = (dispatch: Dispatch): FavoritesOperations => ({
    setPageTitle: () => dispatch(updatePageTitle("Favorites")),
    setActivePage: page => dispatch(setActivePage(page)),
    setShown: type => dispatch(setFavoritesShown(type))
});

export default connect(mapStateToProps, mapDispatchToProps)(Favorites);