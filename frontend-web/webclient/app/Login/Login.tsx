import * as React from "react";
import {Box, Button, Input, Flex, Text, Image, Icon} from "ui-components";
import styled from "styled-components";
import {useState, useEffect, useRef} from "react";
import PromiseKeeper from "PromiseKeeper";
import {Cloud} from "Authentication/SDUCloudObject";
import {errorMessageOrDefault} from "UtilityFunctions";
import {History} from "history";
import {TextSpan} from "ui-components/Text";
import Absolute from "ui-components/Absolute";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {DropdownContent} from "ui-components/Dropdown";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SnackType} from "Snackbar/Snackbars";
const sduPlainBlack = require("Assets/Images/SDU_WHITE_RGB-png.png");
const bg1 = require("Assets/Images/bg1.svg");
const bg2 = require("Assets/Images/bg2.svg");
//const bg2 = require("Assets/Images/cloud_bg_small.jpg");
const wayfLogo = require("Assets/Images/WAYFLogo.svg");

const BackgroundImage = styled.div<{image: string}>`
    background: url(${({image}) => image}) no-repeat 40% 0%;
    background-size: cover;
    overflow: hidden;
`;

const BGLogo = styled(Absolute) <{image: string}>`
    background: url(${({image}) => image}) no-repeat 40% 0%;
    background-size: cover;
`;


const inDevEnvironment = process.env.NODE_ENV === "development"
const enabledWayf = true;
const wayfService = inDevEnvironment ? "dev-web" : "web";

export const LoginPage = (props: {history: History, initialState?: any}) => {
    if (Cloud.isLoggedIn) {
        props.history.push("/");
        return <div />;
    }

    const [challengeId, setChallengeID] = useState("");
    const verificationInput = useRef<HTMLInputElement>(null);
    const usernameInput = useRef<HTMLInputElement>(null);
    const passwordInput = useRef<HTMLInputElement>(null);
    const [promises] = useState(new PromiseKeeper());
    const [loading, setLoading] = useState(false);

    useEffect(() => () => promises.cancelPromises(), []);

    if (props.initialState !== undefined) {
        handleAuthState(props.initialState);
    }

    async function attemptLogin() {
        if (!(usernameInput.current!.value) || !(passwordInput.current!.value)) {
            snackbarStore.addSnack({message: "Invalid username or password", type: SnackType.Failure});
            return;
        }
        try {
            setLoading(true);
            const body = new FormData();
            body.append("service", wayfService);
            body.append("username", usernameInput.current!.value);
            body.append("password", passwordInput.current!.value);
            const response = await promises.makeCancelable(fetch(`/auth/login?service=${wayfService}`, {
                method: "POST",
                headers: {
                    "Accept": "application/json"
                },
                body
            })).promise;
            if (!response.ok) throw response;
            handleAuthState(await response.json());
        } catch (e) {
            snackbarStore.addSnack({
                type: SnackType.Failure,
                message: errorMessageOrDefault({request: e, response: await e.json()}, "An error occurred")
            })
        } finally {
            setLoading(false);
        }
    }

    async function handleAuthState(result: any) {
        if ("2fa" in result) {
            setChallengeID(result["2fa"]);
        } else {
            Cloud.setTokens(result.accessToken, result.csrfToken);
            props.history.push("/loginSuccess");
        }
    }

    async function submit2FA() {
        const verificationCode = verificationInput.current && verificationInput.current.value || "";
        if (!verificationCode) return;
        try {
            setLoading(true);
            const response = await fetch(`/auth/2fa/challenge`, {
                method: "POST",
                headers: {
                    "Accept": "application/json",
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    challengeId,
                    verificationCode
                })
            });
            if (!response.ok) throw response;
            const result = await response.json();
            Cloud.setTokens(result.accessToken, result.csrfToken);
            props.history.push("/loginSuccess");
        } catch (e) {
            setLoading(false);
            snackbarStore.addSnack({
                message: errorMessageOrDefault({request: e, response: await e.json()}, "Could not submit verification code. Try again later"),
                type: SnackType.Failure
            });
        }
    }
    return (<>
        <Absolute top="43px" left="80px"><Box width="200px"><Icon color="white" name="logoSdu" size="20vw" /></Box></Absolute>
        <BGLogo image={bg1} bottom="0px" height="50%" width="100%"> </BGLogo>
        <BackgroundImage image={bg2} >
            <Flex alignItems={"top"} justifyContent={"center"} width={"100vw"} height={"100vh"} pt="20vh">
                <Box width="315px">
                    {enabledWayf && !challengeId ?
                        <a href={`/auth/saml/login?service=${wayfService}`}>
                            <Button disabled={loading} fullWidth color="wayfGreen">
                                <Image width="100px" src={wayfLogo} />
                                <TextSpan fontSize={3} ml="2.5em">Login</TextSpan>
                            </Button>
                        </a> : null}
                    {!challengeId ?
                        <ClickableDropdown colorOnHover={false} keepOpenOnClick top="30px" width={"315px"} left={"0px"}
                            trigger={
                                <Text fontSize={1} color="white" mt="5px">More login options</Text>
                            }>
                            <Box width="100%">
                                <form onSubmit={e => e.preventDefault()}>
                                    <Login enabled2fa={!!challengeId} usernameRef={usernameInput} passwordRef={passwordInput} />
                                    <TwoFactor enabled2fa={challengeId} inputRef={verificationInput} />
                                    <Button fullWidth disabled={loading} onClick={() => challengeId ? submit2FA() : attemptLogin()}>
                                        Login
                                    </Button>
                                </form>
                            </Box>
                        </ClickableDropdown> :
                        <>
                            <Text fontSize={1} color="white" mt="5px">Enter 2-factor authentication code</Text>
                            <DropdownContent
                                overflow={"visible"}
                                squareTop={false}
                                cursor="pointer"
                                width={"315px"}
                                hover={false}
                                visible={true}
                            >
                                <Box width="100%">
                                    <form onSubmit={e => e.preventDefault()}>
                                        <TwoFactor enabled2fa={challengeId} inputRef={verificationInput} />
                                        <Button fullWidth disabled={loading} onClick={() => challengeId ? submit2FA() : attemptLogin()}>
                                            Submit
                                        </Button>
                                    </form>
                                </Box>
                            </DropdownContent>
                        </>
                    }
                </Box>
            </Flex>
        </BackgroundImage>
    </>);

}

const TwoFactor = ({enabled2fa, inputRef}: {enabled2fa: string, inputRef: React.RefObject<HTMLInputElement>}) => enabled2fa ? (
    <Input ref={inputRef} autoComplete="off" autoFocus mb="0.5em" type="text" name="2fa" id="2fa" placeholder="6-digit code" />
) : null;

const Login = ({enabled2fa, usernameRef, passwordRef}: {enabled2fa: boolean, usernameRef: React.RefObject<HTMLInputElement>, passwordRef: React.RefObject<HTMLInputElement>}) => !enabled2fa ? (
    <>
        <Input type="hidden" value="web-csrf" name="service" />
        <Input ref={usernameRef} autoFocus mb="0.5em" type="text" name="username" id="username" placeholder="Username" />
        <Input ref={passwordRef} mb="0.8em" type="password" name="password" id="password" placeholder="Password" />
    </>
) : null;