import * as React from "react";
import {useNavigate, useSearchParams} from "react-router-dom";
import {Button, Flex, MainContainer} from "@/ui-components";
import * as ApiTokens from "@/Applications/ApiTokens/api";
import {useParams} from "react-router-dom";
import {callAPI} from "@/Authentication/DataHook";

export function CliAuth() {
    const { apiToken } = useParams();

    if (apiToken) {
        window.location.href = `http://localhost:8080/auth?token=${encodeURIComponent(apiToken)}`;
    }

    return null;
}

const CliConnectPage: React.FunctionComponent = () => {
    const [searchParams] = useSearchParams();

    const redirect = searchParams.get("redirect");

    const generateToken = React.useCallback(() => {
            void callAPI<ApiTokens.ApiToken>(ApiTokens.create({
            title: "UCloud api token",
            description: "Generated from the ucloud-cli",
            requestedPermissions: [],
            expiresAt: Date.now() + 90 * 24 * 60 * 60 * 1000,
            provider: null,
            product: {
                category: "",
                id: "",
                provider: ""
            },
        })).then(resp => {
            if (redirect && resp.status.token) {
                const redirectUrl = new URL(redirect);
                redirectUrl.searchParams.set("token", resp.status.token);
                window.location.href = redirectUrl.toString();
            }
        }).catch(err => {
            console.log("Err: ", err);
        });
    }, []);


    return <MainContainer
        main={
            <Flex justifyContent={"center"} alignItems={"center"} height={"100vh"}>
                <Button onClick={generateToken}>Connect with UCloud CLI</Button>
            </Flex>
        }
    />;
};

export default CliConnectPage;