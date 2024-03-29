import * as React from "react";
import {Client} from "@/Authentication/HttpClientInstance";
import {usePromiseKeeper} from "@/PromiseKeeper";
import {useEffect, useRef, useState} from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import styled from "styled-components";
import {Absolute, Box, Button, Flex, Icon, Image, Input, Text, ExternalLink, Link} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {TextSpan} from "@/ui-components/Text";
import {getQueryParamOrElse, getQueryParam} from "@/Utilities/URIUtilities";
import {errorMessageOrDefault, preventDefault} from "@/UtilityFunctions";
import {SITE_DOCUMENTATION_URL, SUPPORT_EMAIL} from "../../site.config.json";
import {useLocation, useNavigate} from "react-router";
import wayfLogo from "@/Assets/Images/WAYFLogo.svg";
import ucloudBlue from "@/Assets/Images/ucloud-blue.svg";
import deicBackground from "@/Assets/Images/deic-cloud.svg";
import {Feature, hasFeature} from "@/Features";

const BackgroundImage = styled.div<{image: string}>`
    background: url(${({image}) => image}) no-repeat center;
    background-size: calc(3000px + 80vw);
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

    const [showingWayf, setShowingWayf] = useState(true);

    return (
        <LoginWrapper>
            <LoginIcon mx="auto" name={"deiCLogo"} size="180px" />
            <Text mx="auto" py="30px" color="#000" fontSize={32}>Integration Portal</Text>
            <Box width="315px" mx="auto" my="auto">
                {enabledWayf && !challengeId && !isPasswordReset && showingWayf ? (<>
                    <a href={`/auth/saml/login?service=${service}`}>
                        <Button mb="8px" style={{borderRadius: "16px"}} height={"92px"} disabled={loading} fullWidth color="wayfGreen">
                            <Image color="#fff" width="100px" src={wayfLogo} />
                            <LoginTextSpan fontSize={2} ml="2.5em">Login</LoginTextSpan>
                        </Button>
                    </a>
                    {!hasFeature(Feature.NEW_IDPS) ? null : <IdpList />}
                    <Text color="#000" onClick={() => setShowingWayf(false)} cursor="pointer" textAlign="center">Other login options →</Text>
                </>) : null}
                {(!challengeId) ? (
                    !isPasswordReset ? (!showingWayf ? (
                        <>
                            <DropdownLike>
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
                                <Box mt={20} textAlign="center">
                                    <Link to="/login?password-reset=true" mt={20}>
                                        <BlackLoginText fontSize={1}>Forgot your password?</BlackLoginText>
                                    </Link>
                                </Box>
                            </DropdownLike>
                            <Text mt="8px" color="#000" cursor="pointer" onClick={() => setShowingWayf(true)} textAlign="center">← Wayf login</Text>
                        </>
                    ) : null) : (
                        resetToken == null ? (
                            <DropdownLike>
                                <form onSubmit={(e) => submitResetPassword(e)}>
                                    <LoginInput
                                        placeholder="Email address"
                                        name="email"
                                        type="email"
                                        ref={resetEmailInput}
                                        autoFocus
                                        required
                                    />
                                    {!inDevEnvironment ? null : (
                                        <LoginButton
                                            fullWidth
                                            disabled={loading}
                                            marginTop={10}
                                        >
                                            Reset password
                                        </LoginButton>
                                    )}
                                </form>
                                <Box mt={20}>
                                    <Link to="/login">
                                        <BlackLoginText fontSize={1}>
                                            Return to Login page
                                        </BlackLoginText>
                                    </Link>
                                </Box>
                            </DropdownLike>
                        ) : (
                            <DropdownLike>
                                <div>
                                    <form onSubmit={(e) => attemptSaveNewPassword(e)}>
                                        <LoginInput
                                            mb={10}
                                            type="password"
                                            placeholder="New password"
                                            ref={resetPasswordInput}
                                            autoFocus
                                        />

                                        <LoginInput
                                            type="password"
                                            placeholder="Repeat new password"
                                            ref={resetPasswordRepeatInput}
                                        />
                                        <LoginButton
                                            fullWidth
                                            disabled={loading}
                                            marginTop={10}
                                        >
                                            Save new password
                                        </LoginButton>
                                    </form>
                                    <Box mt={20}>
                                        <Link to="/login">
                                            <BlackLoginText textAlign="center" fontSize={1}>Return to Login page</BlackLoginText>
                                        </Link>
                                    </Box>
                                </div>
                            </DropdownLike>
                        )
                    )
                ) : (
                    <DropdownLike>
                        <form onSubmit={preventDefault}>
                            <TwoFactor enabled2fa={challengeId} inputRef={verificationInput} />
                            <LoginButton
                                fullWidth
                                disabled={loading}
                                onClick={() => challengeId ? submit2FA() : attemptLogin()}
                            >
                                Submit
                            </LoginButton>
                        </form>
                    </DropdownLike>
                )}
            </Box>
            <Box mx="auto" mt="auto" width="280px"><img src={ucloudBlue} /> </Box>
            <Flex height="60px" backgroundColor="#cecfd1">
                <Text color="#000" mx="auto" my="auto" fontSize={12}>
                    Delivered by the Danish e-Infrastrucure Consortium
                </Text>
            </Flex>
        </LoginWrapper >
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

const LoginExternalLink = styled(ExternalLink)`
    color: white;
`;

const LoginTextSpan = styled(TextSpan)`
    color: white;
`;

const DropdownLike = styled.div`
    border-radius: 16px;
    background-color: #c8dd51;
    color: black;
    width: 315px;
    padding: 16px 16px;
`;

const LoginInput = styled(Input)`
    margin-bottom: 0.5em;
    border-color: gray;
    background-color: white;
    color: black;

    &:focus {
        background-color: white;
    }
`;

const LoginIcon = styled(Icon)`
    color: black;
`;

const LoginButton = styled(Button)`
    background-color: white;
    color: black;
`;

const BlackLoginText = styled(Text)`
    color: black;
`;

function LoginWrapper(props: React.PropsWithChildren<{selection?: boolean}>): JSX.Element {
    return (<Box backgroundColor="#fff" height="100vh">
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
                        <LoginIcon name="docs" /> <TextSpan color="#000">Docs</TextSpan>
                    </LoginExternalLink>
                )}
            </div> : null}
        </Absolute>
        <BackgroundImage image={deicBackground}>
            <Flex mx="auto" flexDirection={"column"} height="100vh" minHeight={"650px"}>
                {props.children}
            </Flex>
        </BackgroundImage>
    </Box >);
}

interface IdentityProvider {
    id: number;
    title: string;
    logoUrl?: string | null;
}

const IdpList: React.FunctionComponent = () => {
    const [idps, setIdps] = useState<IdentityProvider[]>([]);

    useEffect(() => {
        (async () => {
            const textResponse = await fetch("/auth/browseIdentityProviders").then(it => it.text());
            const parsed = JSON.parse(textResponse);
            if ("responses" in parsed) {
                const providers = (parsed as { responses: IdentityProvider[] }).responses;
                setIdps(providers);
            }
        })();
    }, []);

    if (idps.length === 0) return null;

    return <div style={{display: "flex", gap: "8px", flexDirection: "column", marginBottom: "16px"}}>{
        idps.map(idp => {
            let title = idp.title;
            switch (title) {
                case "wayf": return null;
                case "orcid": {
                    title = "ORCID";
                    break;
                }
            }

            return <a href={`/auth/startLogin?id=${idp.id}`} key={idp.id}>
                <Button style={{borderRadius: "16px"}} fullWidth color="wayfGreen">
                    Sign in with {title}
                </Button>
            </a>
        })
    }</div>
};

export default LoginPage;
