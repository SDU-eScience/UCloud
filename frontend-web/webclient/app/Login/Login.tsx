import * as React from "react";
import {Client} from "@/Authentication/HttpClientInstance";
import {usePromiseKeeper} from "@/PromiseKeeper";
import {useEffect, useRef, useState} from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import styled from "styled-components";
import {Absolute, Box, Button, Flex, Icon, Image, Input, Text, ExternalLink, Link} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {DropdownContent, Dropdown} from "@/ui-components/Dropdown";
import {TextSpan} from "@/ui-components/Text";
import {getQueryParamOrElse, getQueryParam} from "@/Utilities/URIUtilities";
import {errorMessageOrDefault, preventDefault} from "@/UtilityFunctions";
import {SITE_DOCUMENTATION_URL, SUPPORT_EMAIL} from "../../site.config.json";
import {BG1} from "./BG1";

import bg2 from "@/Assets/Images/bg2.svg";
import wayfLogo from "@/Assets/Images/WAYFLogo.svg";
import aarhusu_logo from "@/Assets/Images/aarhusu_logo.png";
import aalborgu_logo from "@/Assets/Images/aalborgu_logo.png";
import {useLocation, useNavigate} from "react-router";

const BackgroundImage = styled.div<{image: string}>`
    background: url(${({image}) => image}) no-repeat 40% 0%;
    background-size: cover;
    overflow: hidden;
`;

export const LOGIN_REDIRECT_KEY = "redirect_on_login";

const inDevEnvironment = DEVELOPMENT_ENV;
const enabledWayf = true;
const wayfService = inDevEnvironment ? "dev-web" : "web";

export const LoginPage: React.FC<{initialState?: any}> = props => {
    const [challengeId, setChallengeID] = useState("");
    const verificationInput = useRef<HTMLInputElement>(null);
    const usernameInput = useRef<HTMLInputElement>(null);
    const passwordInput = useRef<HTMLInputElement>(null);
    const resetEmailInput = useRef<HTMLInputElement>(null);
    const resetPasswordInput = useRef<HTMLInputElement>(null);
    const resetPasswordRepeatInput = useRef<HTMLInputElement>(null);

    const promises = usePromiseKeeper();
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (props.initialState !== undefined) {
            handleAuthState(props.initialState);
        }
    }, []);
    

    const location = useLocation();
    const navigate = useNavigate();

    const isPasswordReset = getQueryParamOrElse({location, navigate}, "password-reset", "false") === "true";
    const service = inDevEnvironment ? "dev-web" : "web";
    const resetToken = getQueryParam({location, navigate}, "token");

    React.useEffect(() => {
        if (Client.isLoggedIn) {
            navigate("/");
        }
    }, [Client.isLoggedIn]);

    async function attemptLogin(): Promise<void> {
        if (!(usernameInput.current?.value) || !(passwordInput.current?.value)) {
            snackbarStore.addFailure("Invalid username or password", false);
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
                if (response.status === 401) {
                    throw response;
                }

                snackbarStore.addFailure(response.statusText, false);
                return;
            }

            handleAuthState(await response.json());
        } catch (e) {
            snackbarStore.addFailure(
                errorMessageOrDefault({request: e, response: "json" in e ? await e.json() : e}, "An error occurred"), false
            );
        } finally {
            setLoading(false);
        }
    }

    async function attemptSaveNewPassword(e: {preventDefault(): void}): Promise<void> {
        e.preventDefault();

        if (!(resetPasswordInput.current?.value) || !(resetPasswordRepeatInput.current?.value)) {
            snackbarStore.addFailure("Invalid password", false);
            return;
        }

        if (resetPasswordInput.current?.value !== resetPasswordRepeatInput.current?.value) {
            snackbarStore.addFailure("Passwords does not match", false);
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

            resetPasswordInput.current.value = "";
            resetPasswordRepeatInput.current.value = "";

            if (!response.ok) {
                throw response;
            }

            setLoading(false);

            snackbarStore.addSuccess(
                `Your password was changed successfully`,
                true,
                15_000
            );

            navigate("/login");
        } catch (err) {
            setLoading(false);

            snackbarStore.addFailure(
                err.statusText,
                false
            );
        }
    }



    function handleCompleteLogin(result: any): void {
        Client.setTokens(result.accessToken, result.csrfToken);
        navigate("/loginSuccess");
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
                false
            );
        }
    }

    async function submitResetPassword(e: {preventDefault(): void}): Promise<void> {
        e.preventDefault();
        setLoading(true);

        const body = {
            email: resetEmailInput.current!.value
        };

        await promises.makeCancelable(
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
        snackbarStore.addSuccess(
            `If an account exists with the entered email address, you will receive an email shortly.
            Please check your inbox and follow the instructions.`,
            true,
            15_000
        );
    }

    return (
        <LoginWrapper>
            <Flex>
                <LoginBox width="315px">
                    {enabledWayf && !challengeId && !isPasswordReset ? (
                        <a href={`/auth/saml/login?service=${service}`}>
                            <Button mb="8px" disabled={loading} fullWidth color="wayfGreen">
                                <Image width="100px" src={wayfLogo} />
                                <LoginTextSpan fontSize={2} ml="2.5em">Login</LoginTextSpan>
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
                                    {/* add 17px to width to compensate for negative margin */}
                                    <LoginBox color="red" width="calc(100% + 17px)">
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
                                            <Link to="/login?password-reset=true" mt={20}>
                                                <BlackLoginText fontSize={1}>Forgot your password?</BlackLoginText>
                                            </Link>
                                        </Box>
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
                                                    <BlackLoginText fontSize={1}>
                                                        Return to Login page
                                                    </BlackLoginText>
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
                                colorOnHover={false}
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
                    )}
                </LoginBox>
            </Flex>
        </LoginWrapper>
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

function LoginWrapper(props: React.PropsWithChildren<{selection?: boolean}>): JSX.Element {
    return (<>
        <Absolute right="1em" top=".5em">
            {!props.selection ? <div>
                {!SUPPORT_EMAIL ? null : (
                    <ClickableDropdown
                        width="238px"
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
            </div> : null}
        </Absolute>
        <Absolute top="4vw" left="8vw">
            <LoginBox width={"calc(96px + 10vw)"}>
                <LoginIcon name={"deiCLogo"} size="100%" />
                <Text textAlign="center" fontSize={"1.6vw"}>Interactive HPC</Text>
            </LoginBox>
        </Absolute>

        <Absolute style={{overflow: "hidden"}} bottom="0" height="50%" width="100%">
            <BG1 />
        </Absolute>

        <BackgroundImage image={bg2}>
            <Flex justifyContent="center" height="100vh" pt="20vh">
                {props.children}
            </Flex>
        </BackgroundImage>

        <Absolute bottom={10} width="100%">
            <WidthAwareDiv>
                {/* <div /> */}
                <Image width="150px" mt="-33px" src={aalborgu_logo} />
                <Image width="150px" mt="8px" src={aarhusu_logo} />
                <Icon width="150px" color="#fff" name="logoSdu" />
                {/* <div /> */}
            </WidthAwareDiv>
        </Absolute>
    </>);
}

const WidthAwareDiv = styled.div`
    display: grid;
    grid-template-columns: /* auto auto */ auto auto auto;
    grid-template-rows: auto;
    grid-gap: 55px;
    margin-left: auto;
    margin-right: auto;
    justify-content: center;
    width: 100%;

    & > div {
        width: 150px;
    }

    & > ${Image}, & > ${Icon} {
        width: 160px;
        vertical-align: baseline;
    }
    & > ${Icon} { 
        max-height: 41px;
        height: 150px;
    }
`


export default LoginPage;
