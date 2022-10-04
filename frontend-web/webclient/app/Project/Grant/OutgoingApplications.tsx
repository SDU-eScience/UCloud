import React from "react";
import GrantApplications from "@/Project/Grant/GrantApplications";

const OutgoingApplications: React.FunctionComponent = () => {
    return <GrantApplications ingoing={false} />
};

export default OutgoingApplications;
