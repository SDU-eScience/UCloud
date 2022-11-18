import * as React from "react";
import {Resource, ResourceApi} from "@/UCloud/ResourceApi";
import {PropsWithChildren, ReactElement} from "react";
import {Route, Routes} from "react-router-dom";
import {BrowseType} from "./BrowseType";

interface RouterProps<T extends Resource> {
    api: ResourceApi<T, never>;
    Browser: React.FunctionComponent<{isSearch?: boolean; browseType?: BrowseType}>;
    Create?: React.FunctionComponent;
}

export function ResourceRouter<T extends Resource>(props: PropsWithChildren<RouterProps<T>>): ReactElement | null {
    const Properties = props.api.Properties;
    return <Routes>
        <Route path={"/"} element={<props.Browser />} />
        <Route path={`/properties/:id/`} element={<Properties api={props.api} />} />
        {props.Create ? <Route path="/create" element={<props.Create />} /> : null}
        <Route path={`/search`} element={<props.Browser isSearch />} />
    </Routes>;
}
