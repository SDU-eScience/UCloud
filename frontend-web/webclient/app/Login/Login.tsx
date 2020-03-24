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
import {getQueryParamOrElse, RouterLocationProps, getQueryParam} from "Utilities/URIUtilities";
import {errorMessageOrDefault, preventDefault} from "UtilityFunctions";
import {Instructions} from "WebDav/Instructions";
import {PRODUCT_NAME, SITE_DOCUMENTATION_URL, SUPPORT_EMAIL} from "../../site.config.json";
import {BG1} from "./BG1";
import {SnackType} from "Snackbar/Snackbars.js";

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

export const LoginPage: React.FC<RouterLocationProps & {initialState?: any}> = props => {
    const [challengeId, setChallengeID] = useState("");
    const [webDavInstructionToken, setWebDavToken] = useState<string | null>(null);
    const verificationInput = useRef<HTMLInputElement>(null);
    const usernameInput = useRef<HTMLInputElement>(null);
    const passwordInput = useRef<HTMLInputElement>(null);
    const resetEmailInput = useRef<HTMLInputElement>(null);
    const resetPasswordInput = useRef<HTMLInputElement>(null);
    const resetPasswordRepeatInput = useRef<HTMLInputElement>(null);

    const [promises] = useState(new PromiseKeeper());
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (props.initialState !== undefined) {
            handleAuthState(props.initialState);
        }
        return () => promises.cancelPromises();
    }, []);

    const isWebDav = getQueryParamOrElse(props, "dav", "false") === "true";
    const isPasswordReset = getQueryParamOrElse(props, "password-reset", "false") === "true";
    const service = isWebDav ? "dav" : (inDevEnvironment ? "dev-web" : "web");
    const resetToken = getQueryParam(props, "token");

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

    async function attemptSaveNewPassword(e: {preventDefault(): void}): Promise<void> {
        e.preventDefault();

        if (!(resetPasswordInput.current?.value) || !(resetPasswordRepeatInput.current?.value)) {
            snackbarStore.addFailure("Invalid password");
            return;
        }

        if (resetPasswordInput.current?.value !== resetPasswordRepeatInput.current?.value) {
            snackbarStore.addFailure("Passwords does not match");
            return;
        }

        if (resetToken == null) {
            return;
        }

        try {
            setLoading(true);

            const body = {
                token: resetToken,
                newPassword: resetPasswordInput.current!.value
            };
            const response = await promises.makeCancelable(
                fetch(Client.computeURL("/api/password/reset", `/new`), {
                    method: "POST",
                    headers: {
                        Accept: "application/json"
                    },
                    body: JSON.stringify(body)
                })
            ).promise;

            resetPasswordInput.current.value = ""
            resetPasswordRepeatInput.current.value = ""

            if (!response.ok) {
                throw response;
            }

            setLoading(false);

            snackbarStore.addSnack({
                type: SnackType.Success,
                message: `Your password was changed successfully`,
                lifetime: 15_000
            });

            props.history.push("/login");
        } catch (e) {
            setLoading(false);

            snackbarStore.addFailure(
                e.statusText
            );
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
            });
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

    async function submitResetPassword(e: {preventDefault(): void}): Promise<void> {
        e.preventDefault();
        setLoading(true);

        const body = {
            email: resetEmailInput.current!.value
        };

        const response = await promises.makeCancelable(
            fetch("/api/password/reset", {
                method: "POST",
                headers: {
                    "Accept": "application/json",
                    "Content-type": "application/json"
                },
                body: JSON.stringify(body)
            })
        ).promise;

        resetEmailInput.current!.value = "";
        setLoading(false);
        snackbarStore.addSnack({
            type: SnackType.Success,
            message: `If an account exists with the entered email address, you will receive an email shortly.
                Please check your inbox and follow the instructions.`,
            lifetime: 15_000
        });
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
                        {enabledWayf && !challengeId && !isPasswordReset ? (
                            <a href={`/auth/saml/login?service=${service}`}>
                                <Button disabled={loading} fullWidth color="wayfGreen">
                                    <Image width="100px" src={wayfLogo} />
                                    <LoginTextSpan fontSize={3} ml="2.5em">Login</LoginTextSpan>
                                </Button>
                            </a>
                        ) : null}
                        {(!challengeId) ? (
                            !isPasswordReset ? (
                                <DropdownContentWrapper>
                                    <ClickableDropdown
                                        colorOnHover={false}
                                        keepOpenOnClick
                                        keepOpenOnOutsideClick
                                        top="30px"
                                        width="315px"
                                        left="0px"
                                        trigger={(
                                            <LoginText
                                                fontSize={1}
                                                mt="5px"
                                            >
                                                More login options
                                        </LoginText>
                                        )}
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
                                            {!inDevEnvironment ? null : (
                                                <Box mt={20}>
                                                    <Link to="/login?password-reset=true" mt={20}>
                                                        <BlackLoginText fontSize={1}>Forgot your password?</BlackLoginText>
                                                    </Link>
                                                </Box>
                                            )}
                                        </LoginBox>
                                    </ClickableDropdown>
                                </DropdownContentWrapper>
                            ) : (
                                resetToken == null ? (
                                <>
                                    <LoginText fontSize={1} mt="5px">
                                        To reset your password, enter your email address
                                    </LoginText>
                                    <LoginDropdownContent
                                        overflow="visible"
                                        squareTop={false}
                                        cursor="pointer"
                                        width="315px"
                                        hover={false}
                                        colorOnHover={false}
                                        visible
                                    >
                                        <LoginBox width="100%">
                                            <form onSubmit={(e) => submitResetPassword(e)}>
                                                <Input
                                                    placeholder="Email address"
                                                    name="email"
                                                    type="email"
                                                    ref={resetEmailInput}
                                                    autoFocus required
                                                />
                                                {!inDevEnvironment ? null : (
                                                    <Button
                                                        fullWidth
                                                        disabled={loading}
                                                        marginTop={10}
                                                    >
                                                        Reset password
                                                    </Button>
                                                )}
                                            </form>
                                            <Box mt={20}>
                                                <Link to="/login">
                                                    <BlackLoginText fontSize={1}>Return to Login page</BlackLoginText>
                                                </Link>
                                            </Box>
                                        </LoginBox>
                                    </LoginDropdownContent>
                                </>
                            ) : (
                            <LoginBox width="315px">
                                <LoginText fontSize={1} mt="5px">
                                    Please enter a new password
                                </LoginText>
                                <LoginDropdownContent
                                    overflow="visible"
                                    squareTop={false}
                                    cursor="pointer"
                                    width="315px"
                                    hover={false}
                                    colorOnHover={false}
                                    visible
                                >
                                    <LoginBox width="100%">
                                        <form onSubmit={(e) => attemptSaveNewPassword(e)}>
                                            <Input
                                                mb={10}
                                                type="password"
                                                placeholder="New password"
                                                ref={resetPasswordInput}
                                                autoFocus
                                            />

                                            <Input
                                                type="password"
                                                placeholder="Repeat new password"
                                                ref={resetPasswordRepeatInput}
                                            />
                                            <Button
                                                fullWidth
                                                disabled={loading}
                                                marginTop={10}
                                            >
                                                Save new password
                                                </Button>
                                        </form>
                                        <Box mt={20}>
                                            <Link to="/login">
                                                <BlackLoginText fontSize={1}>Return to Login page</BlackLoginText>
                                            </Link>
                                        </Box>
                                    </LoginBox>
                                </LoginDropdownContent>
                            </LoginBox>

                            )
                            )
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

const BlackLoginText = styled(Text)`
    color: black;
`;
