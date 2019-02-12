import * as React from "react";
import { connect } from "react-redux";
import Installed from "Applications/Installed";
import { SearchOptions, SelectableText } from "Search/Search";
import { prettierString } from "UtilityFunctions";
import FavoriteFiles from "./FavoriteFiles";

enum FavoriteType {
    FILES = "FILES",
    APPLICATIONS = "APPLICATIONS"
}

const Favorites = () => {
    const [shown, setShown] = React.useState(FavoriteType.FILES);
    const header = (<SearchOptions>
        {Object.keys(FavoriteType).map((it: FavoriteType) =>
            <SelectableText cursor="pointer" key={it} onClick={() => setShown(it)} mr="1em" selected={it === shown}>
                {prettierString(it)}
            </SelectableText>
        )}
    </SearchOptions>);
    if (shown === FavoriteType.FILES) {
        return (<FavoriteFiles header={header} />);
    } else {
        return (<Installed header={header} />)
    }
}

export default connect(null, null)(Favorites);