import * as React from "react";
import {ResourceBrowse} from "@/Resource/Browse";
import ProvidersApi from "@/UCloud/ProvidersApi";
import {BrowseType} from "@/Resource/BrowseType";
import {useNavigate} from "react-router";

function Browse(): JSX.Element | null {
    const navigate = useNavigate();
    return <ResourceBrowse
        api={ProvidersApi}
        browseType={BrowseType.MainContent}
        extraCallbacks={{
            startCreation() {
                navigate("/providers/create")
            }
        }}
        inlineCreationMode={"NONE"}
    />;
}

export default Browse;
