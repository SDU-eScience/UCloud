import React from "react";
import GrantApplications from "@/Grants/GrantApplications";

const OutgoingApplications: React.FunctionComponent = () => {
    return <GrantApplications ingoing={false} />
};

export default OutgoingApplications;
