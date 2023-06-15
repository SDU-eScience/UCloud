import * as React from "react";
import {useLocation} from "react-router";
import {buildQueryString, getQueryParam} from "@/Utilities/URIUtilities";

const VerifyEmail: React.FunctionComponent = () => {
    const location = useLocation();
    const type = getQueryParam(location.search, "type");
    const token = getQueryParam(location.search, "token");

    switch (type) {
        case "registration": {
            window.location.href = buildQueryString("/auth/registration/verifyEmail", { id: token });
            break;
        }

        default: {
            window.location.href = "/";
            break;
        }
    }
    return null;
};

export default VerifyEmail;
