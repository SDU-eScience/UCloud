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
    const [bg] = useState(randImage());
    const [challengeId, setChallengeID] = useState<string>("");
    const verificationInput = useRef<HTMLInputElement>(null);
    const usernameInput = useRef<HTMLInputElement>(null);
    const passwordInput = useRef<HTMLInputElement>(null);
    const [promises] = useState(new PromiseKeeper());
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => () => promises.cancelPromises(), []);

    async function attemptLogin() {
        if (!(usernameInput.current!.value) || !(passwordInput.current!.value)) {
            setError("Invalid username or password");
            return;
        }
        try {
            setLoading(true);
            const formData = new FormData();
            const service = inDevEnvironment ? "local-dev-csrf" : "web-csrf";
            formData.append("service", service);
            formData.append("username", usernameInput.current!.value);
            formData.append("password", passwordInput.current!.value);
            const result = await fetch(`/auth/login?service=${service}`, {
                method: "POST",
                headers: {
                    "Accept": "application/json"
                },
                body: formData
            }).then(it => it.json());
            if ("2fa" in result) {
                setChallengeID(result["2fa"]);
            } else {
                Cloud.setTokens(result.accessToken, result.csrfToken)
            }
        } catch (e) {
            setError(errorMessageOrDefault(e, "An error occurred"))
        } finally {
            setLoading(false);
        }
    }

    async function submit2FA() {
        console.log(verificationInput.current);
        const verificationCode = verificationInput.current && verificationInput.current.value || "";
        if (!verificationCode) return;
        try {
            setLoading(true);
            const formData = new FormData();
            formData.append("challengeId", challengeId);
            formData.append("verificationCode", verificationCode);
            const result = await fetch(`/auth/2fa/challenge/form`, {
                method: "POST",
                headers: {
                    "Accept": "application/json"
                },
                body: formData
            }).then(it => it.json());
            Cloud.setTokens(result.accessToken, result.csrfToken);
            props.history.push("/");
        } catch (e) {
            setError(errorMessageOrDefault(e, "Could not submit verification code. Try again later"));
        }
    }

    return (<>
        <FullPageImage src={bg} />
        <Box alignItems="center" width="25%" minWidth="400px" maxWidth="420px">
            <CenteredBox>
                <Flex><Box mr="auto" /><Heading.h2>SDUCloud</Heading.h2><Box ml="auto" /></Flex>
                <form onSubmit={e => e.preventDefault()}>
                    <Card bg="lightGray" borderRadius="0.5em" p="1em 1em 1em 1em">
                        <Login enabled2fa={challengeId} usernameRef={usernameInput} passwordRef={passwordInput} />
                        <TwoFactor enabled2fa={challengeId} inputRef={verificationInput} />
                        <Button fullWidth onClick={() => challengeId ? submit2FA() : attemptLogin()}>
                            {challengeId ? "Submit" : "Login"}
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

export const TwoFactor = ({ enabled2fa, inputRef }) => enabled2fa ? (
    <Input ref={inputRef} mb="0.5em" type="text" name="2fa" id="2fa" placeholder="6-digit code" />
) : null;

export const Login = ({ enabled2fa, usernameRef, passwordRef }) => !enabled2fa ? (
    <>
        <Input type="hidden" value="web-csrf" name="service" />
        <Input ref={usernameRef} mb="0.5em" type="text" name="username" id="username" placeholder="Username" />
        <Input ref={passwordRef} mb="0.8em" type="password" name="password" id="password" placeholder="Password" />
    </>
) : null;



const CenteredBox = styled(Box)`
    margin: 0;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
`; 