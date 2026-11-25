import * as React from "react";
import {useState} from "react";
import {useLocation} from "react-router-dom";

import {Feature, hasFeature} from "@/Features";
import {Button, Icon} from "@/ui-components";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {injectStyle, makeKeyframe} from "@/Unstyled";
import {LoginPage} from "./Login";

interface AuthenticationTokens {
    username: string;
    refreshToken: string;
    accessToken: string;
    csrfToken: string;
    expiresAt: number;
}

interface ExternalLoginService {
    id: string;
    title: string;
    description: string;
    feature: Feature;
    handle(response: AuthenticationTokens): void;
}

const externalLoginServices: ExternalLoginService[] = [
    {
        id: "ucloud-cli",
        title: "UCloud",
        description: "Performing ucloud-cli connection procedure.",
        feature: Feature.EXTERNAL_LOGIN_UCLOUD_CLI,
        handle(response) {
            const redirectUrl = new URL("http://localhost:59421/auth");
            redirectUrl.searchParams.set("username", response.username);
            redirectUrl.searchParams.set("token", response.refreshToken);
            window.location.href = redirectUrl.toString();
            
        },
    },
];

function externalLoginServiceFind(id: string | null): ExternalLoginService | null {
    if (id === null) return null;
    return externalLoginServices.find(service => service.id === id && hasFeature(service.feature)) ?? null;
}

export function ExternalLogin({initialState}: {initialState?: any}): React.ReactNode {
    const location = useLocation();
    const service = externalLoginServiceFind(getQueryParam(location.search, "service"));
    const [response, setResponse] = useState<AuthenticationTokens>();
    const [completed, setCompleted] = useState(false);

    if (service === null) {
        return <ExternalLoginPanel>
            <div className="external-login-icon"><Icon name="heroExclamationTriangle" size={36} /></div>
            <h1 className="external-login-title">Unknown service</h1>
            <p className="external-login-text">This service cannot receive UCloud authentication data.</p>
        </ExternalLoginPanel>;
    }

    if (response === undefined) {
        return <LoginPage initialState={initialState} service={service.id} onComplete={setResponse} />;
    }

    if (completed) {
        return <ExternalLoginPanel>
            <div className="external-login-icon"><Icon name="heroCheckCircle" size={36} /></div>
            <h1 className="external-login-title">Connected to {service.title}</h1>
            <p className="external-login-text">Your CLI is now authenticated and ready to use.</p>
            <p className="external-login-hint">You can safely close this browser window.</p>
        </ExternalLoginPanel>;
    }

    return <ExternalLoginPanel>
        <div className="external-login-icon"><Icon name="heroCommandLine" size={36} /></div>
        <h1 className="external-login-title">Connect to {service.title}?</h1>
        <p className="external-login-text">{service.description}</p>
        <div className="external-login-actions">
            <Button color="secondaryMain" onClick={() => window.location.assign("/app/login")}>Cancel</Button>
            <Button color="primaryMain" onClick={() => {
                service.handle(response);
                setCompleted(true);
            }}>Connect</Button>
        </div>
    </ExternalLoginPanel>;
}

const ExternalLoginPanelAppear = makeKeyframe("external-login-appear", `
    from {
        opacity: 0;
        transform: translateY(10px) scale(0.98);
    }

    to {
        opacity: 1;
        transform: translateY(0) scale(1);
    }
`);

const ExternalLoginPanelClass = injectStyle("external-login-panel", k => `
    ${k} {
        --externalLoginBackgroundStart: #eef5ff;
        --externalLoginBackgroundEnd: #f8fbff;
        --externalLoginCardBackground: rgba(255, 255, 255, 0.9);
        --externalLoginShadow: rgba(23, 32, 51, 0.12);
        min-height: 100vh;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 24px;
        text-align: center;
        background:
            radial-gradient(circle at top, rgba(34, 197, 94, 0.18), transparent 32rem),
            linear-gradient(135deg, var(--externalLoginBackgroundStart), var(--externalLoginBackgroundEnd));
    }

    html.dark ${k} {
        --externalLoginBackgroundStart: #0f172a;
        --externalLoginBackgroundEnd: #111827;
        --externalLoginCardBackground: rgba(30, 41, 59, 0.9);
        --externalLoginShadow: rgba(0, 0, 0, 0.35);
    }

    ${k} .external-login-card {
        width: min(100%, 440px);
        padding: 40px 32px;
        border-radius: 24px;
        background: var(--externalLoginCardBackground);
        box-shadow: 0 24px 80px var(--externalLoginShadow);
        backdrop-filter: blur(16px);
        animation: ${ExternalLoginPanelAppear} 360ms ease-out;
    }

    ${k} .external-login-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 72px;
        height: 72px;
        margin-bottom: 24px;
        border-radius: 999px;
        background: rgba(34, 197, 94, 0.14);
        color: var(--successMain);
    }

    ${k} .external-login-title {
        margin: 0;
        font-size: clamp(1.7rem, 4vw, 2.2rem);
        font-weight: 600;
        line-height: 1.15;
        color: var(--textPrimary);
    }

    ${k} .external-login-text {
        margin: 16px 0 0;
        color: var(--textSecondary);
        font-size: 1rem;
        line-height: 1.6;
    }

    ${k} .external-login-hint {
        margin: 28px 0 0;
        padding-top: 24px;
        border-top: 1px solid var(--borderColor);
        color: var(--textSecondary);
        font-size: 0.9rem;
    }

    ${k} .external-login-actions {
        display: flex;
        gap: 12px;
        justify-content: flex-end;
        margin-top: 24px;
    }
`);

function ExternalLoginPanel({children}: React.PropsWithChildren): React.ReactNode {
    return <div className={ExternalLoginPanelClass}>
        <div className="external-login-card">{children}</div>
    </div>;
}
