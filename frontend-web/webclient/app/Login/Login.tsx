import {Client} from "Authentication/HttpClientInstance";
import PromiseKeeper from "PromiseKeeper";
import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled, {ThemeProvider} from "styled-components";
import {Box, Button, Flex, Icon, Image, Input, Text, theme} from "ui-components";
import Absolute from "ui-components/Absolute";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {DropdownContent} from "ui-components/Dropdown";
import {TextSpan} from "ui-components/Text";
import {getQueryParamOrElse, RouterLocationProps} from "Utilities/URIUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {Instructions} from "WebDav/Instructions";
import {PRODUCT_NAME, VERSION_TEXT} from "../../site.config.json";

//const bg1 = require("Assets/Images/bg1.svg");
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

export const LoginPage = (props: RouterLocationProps & {initialState?: any}) => {
    const [challengeId, setChallengeID] = useState("");
    const [webDavInstructionToken, setWebDavToken] = useState<string | null>(null);
    const verificationInput = useRef<HTMLInputElement>(null);
    const usernameInput = useRef<HTMLInputElement>(null);
    const passwordInput = useRef<HTMLInputElement>(null);
    const [promises] = useState(new PromiseKeeper());
    const [loading, setLoading] = useState(false);

    useEffect(() => () => promises.cancelPromises(), []);

    const isWebDav = getQueryParamOrElse(props, "dav", "false") === "true";
    const service = isWebDav ? "dav" : (inDevEnvironment ? "dev-web" : "web");

    if (webDavInstructionToken !== null) {
        return <Instructions token={webDavInstructionToken} />;
    }

    if (Client.isLoggedIn && !isWebDav) {
        props.history.push("/");
        return <div />;
    }

    if (props.initialState !== undefined) {
        handleAuthState(props.initialState);
    }

    async function attemptLogin() {
        if (!(usernameInput.current?.value) || !(passwordInput.current?.value)) {
            snackbarStore.addSnack({message: "Invalid username or password", type: SnackType.Failure});
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
            snackbarStore.addSnack({
                type: SnackType.Failure,
                message: errorMessageOrDefault({request: e, response: await e.json()}, "An error occurred")
            });
        } finally {
            setLoading(false);
        }
    }

    function handleCompleteLogin(result: any) {
        if (isWebDav) {
            setWebDavToken(result.refreshToken);
        } else {
            Client.setTokens(result.accessToken, result.csrfToken);
            props.history.push("/loginSuccess");
        }
    }

    function handleAuthState(result: any) {
        if ("2fa" in result) {
            setChallengeID(result["2fa"]);
        } else {
            handleCompleteLogin(result);
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
            handleCompleteLogin(result);
        } catch (e) {
            setLoading(false);
            snackbarStore.addSnack({
                message: errorMessageOrDefault({
                    request: e,
                    response: await e.json()
                }, "Could not submit verification code. Try again later"),
                type: SnackType.Failure
            });
        }
    }

    return (
        <ThemeProvider theme={theme}>
            <>
                <Absolute top="-3vw" left="8vw">
                    <Box width="20vw">
                        <Icon color="white" name="logoSdu" size="20vw" />
                    </Box>
                </Absolute>

                <Absolute style={{pointerEvents: "none"}} bottom="0px" width="100%" height="50%">
                    <BG1 />
                </Absolute>

                <BackgroundImage image={bg2}>
                    <Flex alignItems="top" justifyContent="center" width="100vw" height="100vh" pt="20vh">
                        <Box width="315px">
                            {!isWebDav ? null : (
                                <Box color="white" mb={32}>
                                    You must re-authenticate with {PRODUCT_NAME} to use your files locally.
                                </Box>
                            )}
                            {enabledWayf && !challengeId ? (
                                <a href={`/auth/saml/login?service=${service}`}>
                                    <Button disabled={loading} fullWidth color="wayfGreen">
                                        <Image width="100px" src={wayfLogo} />
                                        <TextSpan fontSize={3} ml="2.5em">Login</TextSpan>
                                    </Button>
                                </a>
                            ) : null}
                            {!challengeId ? (
                                <ClickableDropdown
                                    colorOnHover={false}
                                    keepOpenOnClick
                                    top="30px"
                                    width="315px"
                                    left="0px"
                                    trigger={(
                                        <Text
                                            fontSize={1}
                                            color="white"
                                            mt="5px"
                                        >
                                            More login options
                                        </Text>
                                    )}
                                >
                                    <Box width="100%">
                                        <form onSubmit={e => e.preventDefault()}>
                                            <Login
                                                enabled2fa={!!challengeId}
                                                usernameRef={usernameInput}
                                                passwordRef={passwordInput}
                                            />
                                            <TwoFactor enabled2fa={challengeId} inputRef={verificationInput} />

                                            <Button
                                                fullWidth
                                                disabled={loading}
                                                onClick={() => challengeId ? submit2FA() : attemptLogin()}
                                            >
                                                Login
                                            </Button>
                                        </form>
                                    </Box>
                                </ClickableDropdown>
                            ) : (
                                    <>
                                        <Text fontSize={1} color="white" mt="5px">
                                            Enter 2-factor authentication code
                                        </Text>
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
                                                    <Button
                                                        fullWidth
                                                        disabled={loading}
                                                        onClick={() => challengeId ? submit2FA() : attemptLogin()}
                                                    >
                                                        Submit
                                                    </Button>
                                                </form>
                                            </Box>
                                        </DropdownContent>
                                    </>
                                )
                            }
                        </Box>
                    </Flex>
                </BackgroundImage>
            </>
        </ThemeProvider>
    );
};

interface TwoFactorProps {
    enabled2fa: string;
    inputRef: React.RefObject<HTMLInputElement>;
}

const TwoFactor: React.FunctionComponent<TwoFactorProps> = ({enabled2fa, inputRef}: TwoFactorProps) => enabled2fa ? (
    <Input
        ref={inputRef}
        autoComplete="off"
        autoFocus
        mb="0.5em"
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

const Login = ({enabled2fa, usernameRef, passwordRef}: LoginProps) => !enabled2fa ? (
    <>
        <Input type="hidden" value="web-csrf" name="service" />
        <Input
            ref={usernameRef}
            autoFocus
            mb="0.5em"
            type="text"
            name="username"
            id="username"
            placeholder="Username"
        />
        <Input ref={passwordRef} mb="0.8em" type="password" name="password" id="password" placeholder="Password" />
    </>
) : null;

const BG1 = () => (
    <svg
        viewBox="0 0 2296 1749"
        fillRule="evenodd"
        clipRule="evenodd"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeMiterlimit="1.5"
    >
        <g id="final">
            <g>
                <path d="M466.426,174.913l73.647,-141.732l-122.744,70.866l49.097,70.866Z" fill="url(#_Linear1)" />
                <path d="M417.329,245.78l49.097,-70.867l-49.097,-70.866l0,141.733Z" fill="url(#_Linear2)" />
                <path d="M540.073,316.646l-73.647,-141.733l-49.097,70.867l122.744,70.866Z" fill="url(#_Linear3)" />
                <path d="M540.073,316.646l36.823,-77.953l-110.47,-63.78l73.647,141.733Z" fill="url(#_Linear4)" />
                <path d="M662.816,245.78l-85.92,-7.087l-36.823,77.953l122.743,-70.866Z" fill="url(#_Linear5)" />
                <path d="M601.444,125.307l-24.548,113.386l-110.47,-63.78l135.018,-49.606Z" fill="url(#_Linear6)" />
                <path d="M540.073,33.181l61.371,92.126l-135.018,49.606l73.647,-141.732Z" fill="url(#_Linear7)" />
                <path d="M662.816,104.047l-61.372,21.26l-61.371,-92.126l122.743,70.866Z" fill="url(#_Linear8)" />
                <path d="M662.816,245.78l-61.372,-120.473l-24.548,113.386l85.92,7.087Z" fill="url(#_Linear9)" />
                <path d="M662.816,104.047l-61.372,21.26l61.372,120.473l0,-141.733Z" fill="url(#_Linear10)" />
            </g>
            <g>
                <path d="M454.152,182l85.921,-170.079l-141.156,81.496l55.235,88.583Z" fill="none" stroke="#fff"
                    strokeOpacity="0.4" strokeWidth="6px" />
                <path d="M398.917,256.409l141.156,81.497l-85.921,-155.906l-55.235,74.409Z" fill="none" stroke="#fff"
                    strokeOpacity="0.4" strokeWidth="6px" />
                <path d="M540.073,11.921l-85.921,170.079l159.567,-63.78l-73.646,-106.299Z" fill="none" stroke="#fff"
                    strokeOpacity="0.4" strokeWidth="6px" />
                <path d="M398.917,256.409l55.235,-74.409l-55.235,-88.583l0,162.992Z" fill="none" stroke="#fff"
                    strokeOpacity="0.4" strokeWidth="6px" />
                <path d="M576.896,252.866l36.823,-134.646l-159.567,63.78l122.744,70.866Z" fill="none" stroke="#fff"
                    strokeOpacity="0.4" strokeWidth="6px" />
                <path d="M454.152,182l122.744,70.866l-36.823,85.04l-85.921,-155.906Z" fill="none" stroke="#fff"
                    strokeOpacity="0.4" strokeWidth="6px" />
                <path d="M540.073,11.921l73.646,106.299l67.509,-24.803l-141.155,-81.496Z" fill="none" stroke="#fff"
                    strokeOpacity="0.4" strokeWidth="6px" />
                <path d="M681.228,256.409l-67.509,-138.189l-36.823,134.646l104.332,3.543Z" fill="none" stroke="#fff"
                    strokeOpacity="0.4" strokeWidth="6px" />
                <path d="M540.073,337.906l36.823,-85.04l104.332,3.543l-141.155,81.497Z" fill="none" stroke="#fff"
                    strokeOpacity="0.4" strokeWidth="6px" />
                <path d="M681.228,93.417l0,162.992l-67.509,-138.189l67.509,-24.803Z" fill="none" stroke="#fff"
                    strokeOpacity="0.4" strokeWidth="6px" />
                <path
                    d="M540.073,11.921l141.155,81.496l0,162.992l-141.155,81.497l-141.156,-81.497l0,-162.992l141.156,-81.496Z"
                    fill="none" stroke="#fff" strokeOpacity="0.4" strokeWidth="6px" />
                <circle cx="540.073" cy="11.921" r="9.921" fill="#fcfcfc" stroke="#fff" strokeOpacity={0.568627}
                    strokeWidth="4px" />
                <circle cx="681.228" cy="256.409" r="9.921" fill="#fcfcfc" stroke="#fff" strokeOpacity={0.568627}
                    strokeWidth="4px" />
                <circle cx="540.073" cy="337.906" r="9.921" fill="#fcfcfc" stroke="#fff" strokeOpacity={0.568627}
                    strokeWidth="4px" />
                <circle cx="398.917" cy="256.409" r="9.921" fill="#fcfcfc" stroke="#fff" strokeOpacity={0.568627}
                    strokeWidth="4px" />
                <circle cx="398.917" cy="93.417" r="9.921" fill="#fcfcfc" stroke="#fff" strokeOpacity={0.568627}
                    strokeWidth="4px" />
                <circle cx="681.228" cy="93.417" r="9.921" fill="#fcfcfc" stroke="#fff" strokeOpacity={0.568627}
                    strokeWidth="4px" />
                <circle cx="613.719" cy="118.22" r="12.756" fill="#fcfcfc" stroke="#fff" strokeOpacity={0.568627}
                    strokeWidth="5px" />
                <circle cx="576.896" cy="252.866" r="12.756" fill="#fcfcfc" stroke="#fff" strokeOpacity={0.568627}
                    strokeWidth="5px" />
                <circle cx="454.152" cy="182" r="12.756" fill="#fcfcfc" stroke="#fff" strokeOpacity={0.568627}
                    strokeWidth="5px" />
            </g>
        </g>
        <path d="M368.231,274.126l171.842,99.213l171.841,-99.213l1583.39,0l0,1474.02l-2295.31,0l0,-1474.02l368.231,0Z"
            fill="url(#_Linear11)" />
        <text x="722.462px" y="247.713px"
            fontFamily="'IBMPlexSans', 'IBM Plex Sans', sans-serif" fontSize="200px" fill="#fff">{PRODUCT_NAME}</text>
        <text
            x="1420px" y="350.713px"
            fontFamily="'IBMPlexSans', 'IBM Plex Sans', sans-serif"
            fontSize="80px"
            fill="#ff0024"
        >
            {VERSION_TEXT}
        </text>
        <defs>
            <linearGradient id="_Linear1" x1="0" y1="0" x2="1" y2="0" gradientUnits="userSpaceOnUse"
                gradientTransform="matrix(85.7479,-26.9821,26.9821,85.7479,434.129,99.833)">
                <stop offset="0" stopColor="#a1c4f5" stopOpacity={1} />
                <stop offset="1" stopColor="#2a79e7" stopOpacity={1} />
            </linearGradient>
            <linearGradient id="_Linear2" x1="0" y1="0" x2="1" y2="0" gradientUnits="userSpaceOnUse"
                gradientTransform="matrix(38.195,-9.35352,9.35352,38.195,423.983,170.992)">
                <stop offset="0" stopColor="#649dee" stopOpacity={1} />
                <stop offset="1" stopColor="#b8d3f7" stopOpacity={1} />
            </linearGradient>
            <linearGradient id="_Linear3" x1="0" y1="0" x2="1" y2="0" gradientUnits="userSpaceOnUse"
                gradientTransform="matrix(-67.5091,-62.606,62.606,-67.5091,515.524,290.669)">
                <stop offset="0" stopColor="#124fa3" stopOpacity={1} />
                <stop offset="1" stopColor="#1764d1" stopOpacity={1} />
            </linearGradient>
            <linearGradient id="_Linear4" x1="0" y1="0" x2="1" y2="0" gradientUnits="userSpaceOnUse"
                gradientTransform="matrix(66.6834,-3.73087,3.73087,66.6834,503.5,252.722)">
                <stop offset="0" stopColor="#124fa3" stopOpacity={1} />
                <stop offset="0.58" stopColor="#1b5ab1" stopOpacity={1} />
                <stop offset="0.8" stopColor="#2e71ce" stopOpacity={1} />
                <stop offset="1" stopColor="#4087ea" stopOpacity={1} />
            </linearGradient>
            <linearGradient id="_Linear5" x1="0" y1="0" x2="1" y2="0" gradientUnits="userSpaceOnUse"
                gradientTransform="matrix(106.835,-56.7082,56.7082,106.835,555.67,288.394)">
                <stop offset="0" stopColor="#2f7ce8" stopOpacity={1} />
                <stop offset="1" stopColor="#a7c7f5" stopOpacity={1} />
            </linearGradient>
            <linearGradient id="_Linear6" x1="0" y1="0" x2="1" y2="0" gradientUnits="userSpaceOnUse"
                gradientTransform="matrix(5.04797e-15,-82.4396,82.4396,5.04797e-15,564.621,222.571)">
                <stop offset="0" stopColor="#5494ec" stopOpacity={1} />
                <stop offset="0.46" stopColor="#2366c4" stopOpacity={1} />
                <stop offset="1" stopColor="#1559b9" stopOpacity={1} />
            </linearGradient>
            <linearGradient id="_Linear7" x1="0" y1="0" x2="1" y2="0" gradientUnits="userSpaceOnUse"
                gradientTransform="matrix(42.658,-100.531,100.531,42.658,503.56,153.474)">
                <stop offset="0" stopColor="#1e71e6" stopOpacity={1} />
                <stop offset="1" stopColor="#10448d" stopOpacity={1} />
            </linearGradient>
            <linearGradient id="_Linear8" x1="0" y1="0" x2="1" y2="0" gradientUnits="userSpaceOnUse"
                gradientTransform="matrix(97.8841,51.1652,-51.1652,97.8841,564.621,53.4205)">
                <stop offset="0" stopColor="#124ea2" stopOpacity={1} />
                <stop offset="1" stopColor="#4086ea" stopOpacity={1} />
            </linearGradient>
            <linearGradient id="_Linear9" x1="0" y1="0" x2="1" y2="0" gradientUnits="userSpaceOnUse"
                gradientTransform="matrix(-29.4468,-80.4216,80.4216,-29.4468,637.028,230.532)">
                <stop offset="0" stopColor="#dbe8fb" stopOpacity={1} />
                <stop offset="1" stopColor="#1763cd" stopOpacity={1} />
            </linearGradient>
            <linearGradient id="_Linear10" x1="0" y1="0" x2="1" y2="0" gradientUnits="userSpaceOnUse"
                gradientTransform="matrix(-7.30285,-127.084,127.084,-7.30285,648.312,218.541)">
                <stop offset="0" stopColor="#0e3e80" stopOpacity={1} />
                <stop offset="1" stopColor="#2475e7" stopOpacity={1} />
            </linearGradient>
            <linearGradient id="_Linear11" x1="0" y1="0" x2="1" y2="0" gradientUnits="userSpaceOnUse"
                gradientTransform="matrix(2167.2,1177.29,-1177.29,2167.2,51.0105,377.986)">
                <stop offset="0" stopColor="#1e71e6" stopOpacity={1} />
                <stop offset="0.47" stopColor="#1a64cd" stopOpacity={1} />
                <stop offset="1" stopColor="#10448d" stopOpacity={1} />
            </linearGradient>
        </defs>
    </svg>
)