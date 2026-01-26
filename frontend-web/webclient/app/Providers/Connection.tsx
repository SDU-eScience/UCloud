import * as React from "react";
import {useEffect, useState} from "react";
import {apiUpdate, callAPI} from "@/Authentication/DataHook";
import {useLocation, useNavigate} from "react-router-dom";
import {extractErrorMessage} from "@/UtilityFunctions";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {MainContainer} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import HexSpin from "@/LoadingIcon/LoadingIcon";

const Connection: React.FunctionComponent = () => {
    const navigate = useNavigate();
    const [errorMessage, setErrorMessage] = useState("");
    const location = useLocation();
    const token = getQueryParamOrElse(location.search, "token", "");

    useEffect(() => {
        (async () => {
            try {
                await callAPI(
                    apiUpdate(
                        {token},
                        "/api/providers/integration",
                        "claimReverseConnection"
                    )
                );
                navigate("/");
            } catch (e) {
                setErrorMessage(extractErrorMessage(e));
            }
        })();
    }, [token]);

    return <MainContainer
        main={<>
            {!errorMessage ?
                <HexSpin /> :
                <>
                    <Heading.h2>An error has occurred</Heading.h2>
                    <p>{errorMessage}</p>
                </>
            }
        </>}
    />;
};

export default Connection;