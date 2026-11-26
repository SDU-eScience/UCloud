import * as React from "react";
import {useBackendFeatures} from "@/Features/backend";
import {Feature, hasFeature} from "@/Features";
import UsageV1 from "@/Accounting/UsageV1";
import UsageV2 from "@/Accounting/UsageV2/Page";

const UsageRouter: React.FunctionComponent = () => {
    useBackendFeatures();

    return hasFeature(Feature.USAGE_V2) ? <UsageV2 /> : <UsageV1 />;
};

export default UsageRouter;
