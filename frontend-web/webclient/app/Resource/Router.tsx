import * as React from "react";
import {Resource, ResourceApi} from "@/UCloud/ResourceApi";
import {PropsWithChildren, ReactElement} from "react";
import {Route, Switch} from "react-router-dom";
import {BrowseType} from "./BrowseType";

interface RouterProps<T extends Resource> {
    api: ResourceApi<T, never>;
    Browser: React.FunctionComponent<{isSearch?: boolean; browseType?: BrowseType}>;
    Create?: React.FunctionComponent;
}

export function ResourceRouter<T extends Resource>(props: PropsWithChildren<RouterProps<T>>): ReactElement | null {
    const Properties = props.api.Properties;
    const basePath = "/" + props.api.routingNamespace;
    return <Switch>
        <Route exact path={basePath} component={props.Browser} />
        <Route exact path={`${basePath}/properties/:id`} component={Properties} />
        {props.Create ? <Route exact path={`${basePath}/create`} component={props.Create} /> : null}
        <Route exact path={`${basePath}/search`}>
            <props.Browser isSearch />
        </Route>
    </Switch>;
}
