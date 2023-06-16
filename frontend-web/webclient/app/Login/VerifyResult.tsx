import * as React from "react";
import {useLocation} from "react-router";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {Box, Button, Link} from "@/ui-components";
import {Client} from "@/Authentication/HttpClientInstance";
import MainContainer from "@/MainContainer/MainContainer";
import {NonAuthenticatedHeader} from "@/Navigation/Header";

const VerifyResult: React.FunctionComponent = () => {
    const location = useLocation();
    const success = getQueryParamOrElse(location.search, "success", "false") === "true";

    const main = <>
        {success ?
            "Thanks for verifying the request. You can now close this tab." :
            "An error occurred. Please make sure that the link you clicked is still valid."
        }

        <Link to={"/"}><Button>Return to the frontpage</Button></Link>
    </>;

    if (Client.isLoggedIn) {
        return <MainContainer main={main}/>
    } else {
        return <>
            <NonAuthenticatedHeader/>
            <Box mb="72px"/>
            <Box m={[0, 0, "15px"]}>
                {main}
            </Box>
        </>;
    }
};

export default VerifyResult;
