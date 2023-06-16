import * as React from "react";
import {useLocation} from "react-router";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import { Box, Button, Flex, Link } from "@/ui-components";
import {Client} from "@/Authentication/HttpClientInstance";
import MainContainer from "@/MainContainer/MainContainer";
import {NonAuthenticatedHeader} from "@/Navigation/Header";

const VerifyResult: React.FunctionComponent = () => {
    const location = useLocation();
    const success = getQueryParamOrElse(location.search, "success", "false") === "true";

    const main =
        <Flex alignItems={"center"} justifyContent={"center"} gap={"16px"} flexDirection={"column"} height={"calc(100vh - 64px)"}
              width={"100%"}>
            <div>
                {success ?
                    "Thanks for verifying the request. You can now close this tab." :
                    "An error occurred. Please make sure that the link you clicked is still valid."
                }
            </div>

            <Link to={"/"}><Button>Return to the frontpage</Button></Link>
        </Flex>;

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
