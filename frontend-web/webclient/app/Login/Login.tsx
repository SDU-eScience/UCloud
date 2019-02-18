import * as React from "react";
import { Box, Button, Input, Card, Flex, Text, Image, Error } from "ui-components";
import * as Heading from "ui-components/Heading";
import styled from "styled-components";
import { useState, useEffect, useRef } from "react";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { errorMessageOrDefault } from "UtilityFunctions";
const sduPlainBlack = require("Assets/Images/sdu_plain_black.png");
const bg1 = require("Assets/LoginImages/cloud1.jpg");
const bg2 = require("Assets/LoginImages/cloud2.jpg");
const bg3 = require("Assets/LoginImages/cloud3.jpg");

function randImage() {
    switch ((Math.random() * 3) | 0) {
        case 0:
            return bg1;
        case 1:
            return bg2;
        case 2:
        default:
            return bg3;
    }
}

const FullPageImage = styled(Image)`
    max-height: 100vh;
    max-width: 100vw;
    min-height: 100vh;
    min-width: 100vw;
`;

const inDevEnvironment = process.env.NODE_ENV === "development"

export const LoginPage = (props) => {
    if (Cloud.isLoggedIn && false) {
        //@ts-ignore
        props.history.push("/");
        return <div />;
    }
    const [challengeId, setChallengeID] = useState<string | undefined>(undefined);
    const verificationInput = useRef<HTMLInputElement>(null);
    const usernameInput = useRef<HTMLInputElement>(null);
    const passwordInput = useRef<HTMLInputElement>(null);
    const [promises] = useState(new PromiseKeeper());
    const [enabled2fa] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => () => promises.cancelPromises(), []);

    async function attemptLogin() {
        if (!(usernameInput.current && usernameInput.current.value) || !(passwordInput.current && passwordInput.current.value)) {
            setError("Invalid username or password");
            return;
        }
        try {
            setLoading(true);
            const { response } = await Cloud.post<{ accessToken: string, csrfToken: string } | { "2fa": string }>(inDevEnvironment ? "/login?service=local-dev-csrf" : "/login?service=web-csrf", {
                /* FIXME, missing body */
            }, "auth");
            if ("2fa" in response) {
                setChallengeID(response["2fa"]);
            } else {
                Cloud.setTokens(response.accessToken, response.csrfToken)
            }
        } catch (e) {
            setError(errorMessageOrDefault(e, "An error occurred"))
        } finally {
            setLoading(false);
        }
    }

    async function submit2FA() {
        const verificationCode = verificationInput.current && verificationInput.current.value || "";
        if (!verificationCode) return;
        try {
            setLoading(true);
            // FIXME: MISSING `Accept: application/json`
            const { response } = await promises.makeCancelable(Cloud.post("2fa/challenge", {
                challengeId,
                verificationCode
            }, "/auth")).promise;
            Cloud.setTokens(response.accessToken, response.csrfToken);
            props.history.push("/");
        } catch (e) {
            setError(errorMessageOrDefault(e, "Could not submit verification code. Try again later"));
        }
    }

    return (<>
        <FullPageImage src={randImage()} />
        <Box alignItems="center" width="25%" minWidth="400px" maxWidth="420px">
            <CenteredBox>
                <Flex><Box mr="auto" /><Heading.h2>SDUCloud</Heading.h2><Box ml="auto" /></Flex>
                <form onSubmit={e => e.preventDefault()}>
                    <Card bg="lightGray" borderRadius="0.5em" p="1em 1em 1em 1em">
                        <Login enabled2fa={enabled2fa} />
                        <TwoFactor enabled2fa={enabled2fa} ref={verificationInput} />
                        <Button fullWidth onClick={() => challengeId ? submit2FA() : attemptLogin()}>
                            {enabled2fa ? "Submit" : "Login"}
                        </Button>
                        <Box mt="5px"><Error error={error} clearError={() => setError("")} /></Box>
                    </Card>
                </form>
                <Card borderRadius="0.5em" mt="0.3em" height="auto" p="1em 1em 1em 1em" bg="lightBlue">
                    <Flex>
                        <Box><Text fontSize={1} color="textColor">Under construction - Not yet available to the public.</Text></Box>
                    </Flex>
                </Card>
                <Flex mt="0.3em"><Box ml="auto" /><Image width="80px" src={sduPlainBlack} /></Flex>
            </CenteredBox>
        </Box>
    </>);
}

export const TwoFactor = ({ enabled2fa, ref }) => enabled2fa ? (
    <Input ref={ref} mb="0.5em" type="text" name="2fa" id="2fa" placeholder="6-digit code" />
) : null;

export const Login = ({ enabled2fa }) => !enabled2fa ? (
    <>
        <Input type="hidden" value="web-csrf" name="service" />
        <Input mb="0.5em" type="text" name="username" id="username" placeholder="Username" />
        <Input mb="0.8em" type="password" name="password" id="password" placeholder="Password" />
    </>
) : null;



const CenteredBox = styled(Box)`
    margin: 0;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
`; 