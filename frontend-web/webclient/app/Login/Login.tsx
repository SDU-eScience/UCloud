import * as React from "react";
import {Client} from "@/Authentication/HttpClientInstance";
import {usePromiseKeeper} from "@/PromiseKeeper";
import {useEffect, useRef, useState} from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Absolute, Box, Button, Flex, Icon, Image, Input, Text, ExternalLink, Link, Relative} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {TextProps, TextSpan} from "@/ui-components/Text";
import {getQueryParamOrElse, getQueryParam} from "@/Utilities/URIUtilities";
import {errorMessageOrDefault, preventDefault} from "@/UtilityFunctions";
import {SITE_DOCUMENTATION_URL, SUPPORT_EMAIL, DEFAULT_LOGIN} from "../../site.config.json";
import {useLocation, useNavigate} from "react-router";
import wayfLogo from "@/Assets/Images/WAYFLogo.svg?url";
import ucloudBlue from "@/Assets/Images/ucloud-blue.svg?url";
import deicBackground from "@/Assets/Images/deic-cloud.svg?url";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {InputProps} from "@/ui-components/Input";
import {ButtonProps} from "@/ui-components/Button";
import {Feature, hasFeature} from "@/Features";
import {Gradient, GradientWithPolygons} from "@/ui-components/GradientBackground";

const IS_SANDBOX = window.location.host.startsWith("sandbox.dev");

const BackgroundImageClass = injectStyleSimple("background-image", `
    background: url(${deicBackground}) no-repeat center;
    background-size: calc(3000px + 80vw);
    color: black;
    overflow: hidden;
`);

export const LOGIN_REDIRECT_KEY = "redirect_on_login";

const inDevEnvironment = DEVELOPMENT_ENV;
const enabledWayf = true;

const TEXT_COLOR = IS_SANDBOX ? "#fff" : "#000";
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

    const [showingWayf, setShowingWayf] = useState(DEFAULT_LOGIN === "wayf");

    return (
        <LoginWrapper>
            {IS_SANDBOX ?
                <Flex width="auto" mx="auto" paddingTop="80px"><Icon size={128} name="logoEsc" /><Text my="auto" ml="16px" color="#fff" fontSize={64}>UCloud</Text></Flex> :
                <Icon className={LoginIconClass} mx="auto" hoverColor={"fixedBlack"} name={"deiCLogo"} size="180px" />}
            <Text mx="auto" py="30px" width="fit-content" color={TEXT_COLOR} fontSize={32}>{IS_SANDBOX ? "Sandbox Environment" : "Integration Portal"}</Text>
            <Box width="315px" mx="auto" my="auto">
                {enabledWayf && !challengeId && !isPasswordReset && showingWayf ? (<>
                    <a href={`/auth/saml/login?service=${service}`}>
                        <Button mb="8px" className={BorderRadiusButton} height={"92px"} disableStandardSizes disabled={loading} fullWidth color={IS_SANDBOX ? "primaryLight" : "wayfGreen"}>
                            <Image color="#fff" width="100px" src={wayfLogo} />
                            <TextSpan className={LoginTextSpanClass} fontSize={2} ml="2.5em">Login</TextSpan>
                        </Button>
                    </a>
                    {!hasFeature(Feature.NEW_IDPS) ? null : <IdpList />}
                    <Text color={TEXT_COLOR} onClick={() => setShowingWayf(false)} cursor="pointer" textAlign="center">Other login options →</Text>
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
                            <Text mt="8px" color={TEXT_COLOR} cursor="pointer" onClick={() => setShowingWayf(true)} textAlign="center">← Other login</Text>
                        </>
                    ) : null) : (
                        resetToken == null ? (
                            <DropdownLike>
                                <form onSubmit={(e) => submitResetPassword(e)}>
                                    <LoginInput
                                        placeholder="Email address"
                                        name="email"
                                        type="email"
                                        inputRef={resetEmailInput}
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
                                            inputRef={resetPasswordInput}
                                            autoFocus
                                        />

                                        <LoginInput
                                            type="password"
                                            placeholder="Repeat new password"
                                            inputRef={resetPasswordRepeatInput}
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
            {IS_SANDBOX ? <Box height="280px" /> : <Box mx="auto" mt="auto" width="280px"><img alt="UCloud logo" src={ucloudBlue} /> </Box>}
            {IS_SANDBOX ? <Box height="60px" minHeight="60px" /> : <Flex height="60px" minHeight="60px" backgroundColor="#cecfd1">
                <Text color="#000" mx="auto" my="auto" fontSize={12}>
                    Delivered by the Danish e-Infrastructure Consortium
                </Text>
            </Flex>}
        </LoginWrapper >
    );
};

interface TwoFactorProps {
    enabled2fa: string;
    inputRef: React.RefObject<HTMLInputElement>;
}

const TwoFactor: React.FunctionComponent<TwoFactorProps> = ({enabled2fa, inputRef}) => enabled2fa ? (
    <LoginInput
        inputRef={inputRef}
        autoComplete="off"
        autoFocus
        type="text"
        name="2fa"
        id="2fa"
        placeholder="6-digit code"
    />
) : null;

const BorderRadiusButton = injectStyleSimple("border-radius", `
    border-radius: 16px;
`)

interface LoginProps {
    enabled2fa: boolean;
    usernameRef: React.RefObject<HTMLInputElement>;
    passwordRef: React.RefObject<HTMLInputElement>;
}

const Login = ({enabled2fa, usernameRef, passwordRef}: LoginProps): React.ReactNode => !enabled2fa ? (
    <>
        <LoginInput type="hidden" value="web-csrf" name="service" />
        <LoginInput
            inputRef={usernameRef}
            autoFocus
            type="text"
            name="username"
            id="username"
            placeholder="Username"
        />
        <LoginInput inputRef={passwordRef} mb="0.8em" type="password" name="password" id="password" placeholder="Password" />
    </>
) : null;

//ExternalLink
const LoginExternalLinkClass = injectStyleSimple("login-external-link", `
    color: white;
`);


//TextSpan
const LoginTextSpanClass = injectStyleSimple("login-text", `
    color: white;
`);

function DropdownLike({children}): React.ReactNode {
    return <div className={DropdownLikeClass}>
        {children}
    </div>
}

const DropdownLikeClass = injectStyleSimple("dropdown-like", `
    border-radius: 16px;
    background-color: ${IS_SANDBOX ? "var(--primaryLight)" : "#c8dd51"};
    color: black;
    width: 315px;
    padding: 16px 16px;
`);

function LoginInput(props: InputProps): React.ReactNode {
    return <Input {...props} className={LoginInputClass} />
}

const LoginInputClass = injectStyle("login-input", k => `
    ${k} {
        margin-bottom: 0.5em;
        border-color: gray;
        background-color: white;
        color: black;
    }
    
    ${k}::placeholder {
        color: gray;
    }

    ${k}:focus {
        background-color: white;
    }
`);

const LoginIconClass = injectStyle("login-icon", k => `
    ${k} {
        color: black;
    }

    ${k}:hover {
        color: black;
    }
`);

function LoginButton(props: ButtonProps): React.ReactNode {
    return <Button {...props} textColor="fixedBlack" color="fixedWhite" />
}

function BlackLoginText(props: React.PropsWithChildren<TextProps>): React.ReactNode {
    return <Text className={LoginTextClass} {...props} />
}

const LoginTextClass = injectStyleSimple("login-text", `
    color: ${TEXT_COLOR};
    font-size: var(--interactiveElementsSize);
`);

function LoginWrapper(props: React.PropsWithChildren<{selection?: boolean}>): React.ReactNode {
    return (<Box backgroundColor="#fff" className={"dark"}>
        <Absolute right="1em" top=".5em">
            {!props.selection ? <div>
                {!SUPPORT_EMAIL ? null : (
                    <ClickableDropdown
                        width="238px"
                        top="0"
                        left="-248px"
                        right="5px"
                        colorOnHover={false}
                        trigger={<Relative><Icon color={TEXT_COLOR} color2={TEXT_COLOR} mr={"1em"} name="suggestion" /></Relative>}
                    >
                        <ExternalLink href={`mailto:${SUPPORT_EMAIL}`}>
                            Need help?
                            {" "}<b>{SUPPORT_EMAIL}</b>
                        </ExternalLink>
                    </ClickableDropdown>
                )}
                {!SITE_DOCUMENTATION_URL ? null : (
                    <ExternalLink className={LoginExternalLinkClass} href={SITE_DOCUMENTATION_URL}>
                        <Icon color={TEXT_COLOR} color2={TEXT_COLOR} name="docs" /> <TextSpan color={TEXT_COLOR}>Docs</TextSpan>
                    </ExternalLink>
                )}
            </div> : null}
        </Absolute>
        <BackgroundImage>{props.children}</BackgroundImage>
    </Box >);
}

function BackgroundImage({children}: React.PropsWithChildren) {
    if (IS_SANDBOX) {
        const overridenColors = {
            "--gradientStart": "var(--blue-90)",
            "--gradientEnd": "var(--blue-80)",
            "--primaryLight": "var(--blue-70)",
        } as React.CSSProperties;
        return <div className={Gradient} style={overridenColors}>
            <div className={GradientWithPolygons + " dark"}>
                <Flex mx="auto" flexDirection={"column"} minHeight="100vh">
                    {children}
                </Flex>
            </div>
        </div>
    } else {
        return <div className={BackgroundImageClass}>
            <Flex mx="auto" flexDirection={"column"} minHeight="100vh">
                {children}
            </Flex>
        </div>
    }
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
                const providers = (parsed as {responses: IdentityProvider[]}).responses;
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

            const color = IS_SANDBOX ? "primaryLight" : "wayfGreen";

            return <a href={`/auth/startLogin?id=${idp.id}`} key={idp.id}>
                <Button borderRadius="16px" fullWidth color={color}>
                    <Text color="fixedWhite">Sign in with {title}</Text>
                </Button>
            </a>
        })
    }</div>
};

export default LoginPage;
