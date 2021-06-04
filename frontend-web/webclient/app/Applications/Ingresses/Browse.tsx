import * as React from "react";
import {default as IngressApi, Ingress} from "UCloud/IngressApi";
import {ResourceBrowse} from "Resource/ResourceBrowse";
import {Icon} from "ui-components";
import {doNothing} from "UtilityFunctions";

const Browse: React.FunctionComponent<{
    computeProvider?: string;
    onSelect?: (selection: Ingress) => void
}> = props => {
    return <ResourceBrowse
        api={IngressApi}
        TitleRenderer={p => <>{p.resource.specification.domain}</>}
        IconRenderer={() => <Icon name={"globeEuropeSolid"}/>}
        onSelect={props.onSelect}
        onInlineCreation={doNothing}
    />;
};
export default Browse;
