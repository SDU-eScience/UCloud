import * as React from "react";
import {useBackendFeatures} from "@/Features/backend";
import {Feature, hasFeature} from "@/Features";
import AllocationsV1 from "@/Accounting/AllocationsV1";
import Allocations from "@/Accounting/Allocations";

const AllocationsRouter: React.FunctionComponent = () => {
    useBackendFeatures();

    return hasFeature(Feature.ALLOCATIONS_V2) ? <Allocations /> : <AllocationsV1 />;
};

export default AllocationsRouter;
