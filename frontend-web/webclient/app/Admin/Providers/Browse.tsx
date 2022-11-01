import * as React from "react";
import {ResourceBrowse} from "@/Resource/Browse";
import ProvidersApi from "@/UCloud/ProvidersApi";
import {BrowseType} from "@/Resource/BrowseType";
import {useHistory} from "react-router";

function Browse(): JSX.Element | null {
    const history = useHistory();
    return <ResourceBrowse
        api={ProvidersApi}
        browseType={BrowseType.MainContent}
        extraCallbacks={{
            startCreation() {
                history.push("/providers/create")
            }
        }}
        inlineCreationMode={"NONE"}
    />;
}

export default Browse;
