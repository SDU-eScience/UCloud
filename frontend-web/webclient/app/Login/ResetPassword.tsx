import {Client} from "Authentication/HttpClientInstance";
import PromiseKeeper from "PromiseKeeper";
import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Absolute, Box, Button, Flex, Icon, Image, Input, Text, ExternalLink, Link} from "ui-components";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {DropdownContent, Dropdown} from "ui-components/Dropdown";
import {TextSpan} from "ui-components/Text";
import {getQueryParamOrElse, RouterLocationProps} from "Utilities/URIUtilities";
import {errorMessageOrDefault, preventDefault} from "UtilityFunctions";
import {Instructions} from "WebDav/Instructions";
import {PRODUCT_NAME, SITE_DOCUMENTATION_URL, SUPPORT_EMAIL} from "../../site.config.json";
import {BG1} from "./BG1";

const bg2 = require("Assets/Images/bg2.svg");
const wayfLogo = require("Assets/Images/WAYFLogo.svg");

const BackgroundImage = styled.div<{image: string}>`
    background: url(${({image}) => image}) no-repeat 40% 0%;
    background-size: cover;
    overflow: hidden;
`;

const inDevEnvironment = process.env.NODE_ENV === "development";
const enabledWayf = true;
const wayfService = inDevEnvironment ? "dev-web" : "web";

export const ResetPasswordPage: React.FC<RouterLocationProps & {initialState?: any}> = props => {
    const [challengeId, setChallengeID] = useState("");
    const [webDavInstructionToken, setWebDavToken] = useState<string | null>(null);
    const verificationInput = useRef<HTMLInputElement>(null);
    const usernameInput = useRef<HTMLInputElement>(null);
    const passwordInput = useRef<HTMLInputElement>(null);
    const [promises] = useState(new PromiseKeeper());
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (props.initialState !== undefined) {
            handleAuthState(props.initialState);
        }
        return () => promises.cancelPromises();
    }, []);

    const isWebDav = getQueryParamOrElse(props, "dav", "false") === "true";
    const service = isWebDav ? "dav" : (inDevEnvironment ? "dev-web" : "web");

    if (webDavInstructionToken !== null) {
        return <Instructions token={webDavInstructionToken} />;
    }

    if (Client.isLoggedIn && !isWebDav) {
        props.history.push("/");
        return <div />;
    }

    async function attemptLogin(): Promise<void> {
        if (!(usernameInput.current?.value) || !(passwordInput.current?.value)) {
            snackbarStore.addFailure("Invalid username or password");
            return;
        }

        try {
            setLoading(true);

            const body = new FormData();
            body.append("service", service);
            body.append("username", usernameInput.current!.value);
            body.append("password", passwordInput.current!.value);
            const response = await promises.makeCancelable(
                fetch(Client.computeURL("/auth", `/login?service=${service}`), {
                    method: "POST",
                    headers: {
                        Accept: "application/json"
                    },
                    body
                })
            ).promise;

            if (!response.ok) { // noinspection ExceptionCaughtLocallyJS
                throw response;
            }

            handleAuthState(await response.json());
        } catch (e) {
            snackbarStore.addFailure(
                errorMessageOrDefault({request: e, response: await e.json()}, "An error occurred")
            );
        } finally {
            setLoading(false);
        }
    }

    function handleCompleteLogin(result: any): void {
        if (isWebDav) {
            setWebDavToken(result.refreshToken);
        } else {
            Client.setTokens(result.accessToken, result.csrfToken);
            props.history.push("/loginSuccess");
        }
    }

    function handleAuthState(result: any): void {
        if ("2fa" in result) {
            setChallengeID(result["2fa"]);
        } else {
            handleCompleteLogin(result);
        }
    }

    async function submit2FA(): Promise<void> {
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
            })
            if (!response.ok) throw response;
            const result = await response.json();
            handleCompleteLogin(result);
        } catch (e) {
            setLoading(false);
            snackbarStore.addFailure(
                errorMessageOrDefault({
                    request: e,
                    response: await e.json()
                }, "Could not submit verification code. Try again later"),
            );
        }
    }

    return (
        <>
            <Absolute right="1em" top=".5em">
                <div>
                    {!SUPPORT_EMAIL ? null : (
                        <ClickableDropdown
                            width="224px"
                            top="36px"
                            right="5px"
                            colorOnHover={false}
                            trigger={<LoginIcon mr={"1em"} name="suggestion" />}
                        >
                            <ExternalLink href={`mailto:${SUPPORT_EMAIL}`}>
                                Need help?
                                    {" "}<b>{SUPPORT_EMAIL}</b>
                            </ExternalLink>
                        </ClickableDropdown>
                    )}
                    {!SITE_DOCUMENTATION_URL ? null : (
                        <LoginExternalLink href={SITE_DOCUMENTATION_URL}>
                            <LoginIcon name="docs" /> Docs
                        </LoginExternalLink>
                    )}
                </div>
            </Absolute>
            <Absolute top="4vw" left="8vw">
                <LoginBox width={"calc(96px + 10vw)"}>
                    <LoginIcon name="logoSdu" size="100%" />
                </LoginBox>
            </Absolute>


            <Absolute style={{overflowY: "hidden"}} bottom="0" height="50%" width="100%">
                <BG1 />
            </Absolute>

            <BackgroundImage image={bg2}>
                <Flex alignItems="top" justifyContent="center" width="100vw" height="100vh" pt="20vh">
                    <LoginBox width="315px">
                        {!isWebDav ? null : (
                            <LoginBox mb={32}>
                                You must re-authenticate with {PRODUCT_NAME} to use your files locally.
                            </LoginBox>
                        )}
                        {!challengeId ? (
                            <LoginDropdownContent
                                overflow="visible"
                                squareTop={false}
                                cursor="pointer"
                                width="315px"
                                hover={false}
                                visible
                            >
                                <LoginBox color="red" width="100%">
                                    <form onSubmit={preventDefault}>
                                        <Login
                                            enabled2fa={!!challengeId}
                                            usernameRef={usernameInput}
                                            passwordRef={passwordInput}
                                        />
                                        <TwoFactor enabled2fa={challengeId} inputRef={verificationInput} />

                                        <LoginButton
                                            fullWidth
                                            disabled={loading}
                                            onClick={() => challengeId ? submit2FA() : attemptLogin()}
                                        >
                                            Login
                                        </LoginButton>
                                    </form>
                                    <Box mt={20}>
                                        <Link to="/reset-password" mt={20}>
                                            <Text fontSize={1}>Forgot your password?</Text>
                                        </Link>
                                    </Box>
                                </LoginBox>
                            </LoginDropdownContent>
                        ) : (
                                <>
                                    <LoginText fontSize={1} mt="5px">
                                        Enter 2-factor authentication code
                                    </LoginText>
                                    <LoginDropdownContent
                                        overflow="visible"
                                        squareTop={false}
                                        cursor="pointer"
                                        width="315px"
                                        hover={false}
                                        visible
                                    >
                                        <LoginBox width="100%">
                                            <form onSubmit={preventDefault}>
                                                <TwoFactor enabled2fa={challengeId} inputRef={verificationInput} />
                                                <Button
                                                    fullWidth
                                                    disabled={loading}
                                                    onClick={() => challengeId ? submit2FA() : attemptLogin()}
                                                >
                                                    Submit
                                                    </Button>
                                            </form>
                                        </LoginBox>
                                    </LoginDropdownContent>
                                </>
                            )
                        }
                    </LoginBox>
                </Flex>
            </BackgroundImage>
        </>
    );
};

interface TwoFactorProps {
    enabled2fa: string;
    inputRef: React.RefObject<HTMLInputElement>;
}

const TwoFactor: React.FunctionComponent<TwoFactorProps> = ({enabled2fa, inputRef}) => enabled2fa ? (
    <LoginInput
        ref={inputRef}
        autoComplete="off"
        autoFocus
        type="text"
        name="2fa"
        id="2fa"
        placeholder="6-digit code"
    />
) : null;

interface LoginProps {
    enabled2fa: boolean;
    usernameRef: React.RefObject<HTMLInputElement>;
    passwordRef: React.RefObject<HTMLInputElement>;
}

const Login = ({enabled2fa, usernameRef, passwordRef}: LoginProps): JSX.Element | null => !enabled2fa ? (
    <>
        <LoginInput type="hidden" value="web-csrf" name="service" />
        <LoginInput
            ref={usernameRef}
            autoFocus
            type="text"
            name="username"
            id="username"
            placeholder="Username"
        />
        <LoginInput ref={passwordRef} mb="0.8em" type="password" name="password" id="password" placeholder="Password" />
    </>
) : null;

const LoginDropdownContent = styled(DropdownContent)`
    background-color: white;
    color: white;
`;

const LoginExternalLink = styled(ExternalLink)`
    color: white;
`;

const LoginTextSpan = styled(TextSpan)`
    color: white;
`;

const DropdownContentWrapper = styled.div`
    & > ${Dropdown} > ${DropdownContent} {
        color: black;
        background-color: white;
    }
`;

const LoginInput = styled(Input)`
    margin-bottom: 0.5em;
    border-color: lightgray;
    color: black;
`;

const LoginText = styled(Text)`
    color: white;
`;

const LoginIcon = styled(Icon)`
    color: white;
`;

const LoginBox = styled(Box)`
    color: white;
`;

const LoginButton = styled(Button)`
    color: white;
`;
