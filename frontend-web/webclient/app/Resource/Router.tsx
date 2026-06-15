import * as React from "react";
import {Resource, ResourceApi, ResourceSpecification} from "@/UCloud/ResourceApi";
import {PropsWithChildren, ReactElement} from "react";
import {Route, Routes} from "react-router-dom";
import {Product} from "@/Accounting";

interface RouterProps<R extends Resource, P extends Product, S extends ResourceSpecification> {
    api: ResourceApi<R, P, S>;
    Browser: React.FunctionComponent;
    Create?: React.FunctionComponent;
}

export function ResourceRouter<T extends Resource, P extends Product, S extends ResourceSpecification>(props: PropsWithChildren<RouterProps<T, P, S>>): ReactElement | null {
    const Properties = props.api.Properties;
    return <Routes>
        <Route path={"/"} element={<props.Browser />} />
        <Route path={`/properties/:id/`} element={<Properties api={props.api} />} />
        {props.Create ? <Route path="/create" element={<props.Create />} /> : null}
    </Routes>;
}
