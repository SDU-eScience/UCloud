import * as React from "react";
import {Resource, ResourceApi} from "UCloud/ResourceApi";

export interface ResourceCreator<Res extends Resource> {
    onResourceCreated: (resource: Res) => void;
}

export const ResourceCreatorPage: React.FunctionComponent = () => {
    return null;
};

export const InlineResourceCreator: React.FunctionComponent = () => {
    return null;
};
