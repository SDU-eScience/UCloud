import * as React from "react";
import {useState} from "react";
import {useLocation} from "react-router-dom";

import {Feature, hasFeature} from "@/Features";
import {Box, Button, Flex, Text} from "@/ui-components";
import {getQueryParam} from "@/Utilities/URIUtilities";
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
        id: "test",
        title: "Test service",
        description: "Send a new UCloud session to the development console.",
        feature: Feature.EXTERNAL_LOGIN_TEST,
        handle(response) {
            // eslint-disable-next-line no-console
            console.log("External login response", response);
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
            <Text fontSize={24} fontWeight={600}>Unknown service</Text>
            <Text mt={12}>This service cannot receive UCloud authentication data.</Text>
        </ExternalLoginPanel>;
    }

    if (response === undefined) {
        return <LoginPage initialState={initialState} service={service.id} onComplete={setResponse} />;
    }

    if (completed) {
        return <ExternalLoginPanel>
            <Text fontSize={24} fontWeight={600}>Connected to {service.title}</Text>
            <Text mt={12}>You can close this page.</Text>
        </ExternalLoginPanel>;
    }

    return <ExternalLoginPanel>
        <Text fontSize={24} fontWeight={600}>Connect to {service.title}?</Text>
        <Text mt={12}>{service.description}</Text>
        <Text mt={12}>The service will receive a token that can access UCloud as you.</Text>
        <Flex gap={"12px"} mt={24} justifyContent="flex-end">
            <Button color="secondaryMain" onClick={() => window.location.assign("/app/login")}>Cancel</Button>
            <Button color="primaryMain" onClick={() => {
                service.handle(response);
                setCompleted(true);
            }}>Connect</Button>
        </Flex>
    </ExternalLoginPanel>;
}

function ExternalLoginPanel({children}: React.PropsWithChildren): React.ReactNode {
    return <Flex minHeight="100vh" alignItems="center" justifyContent="center" backgroundColor="var(--backgroundDefault)">
        <Box width="420px" maxWidth="calc(100vw - 32px)" padding={24} borderRadius={8} backgroundColor="var(--backgroundCard)">
            {children}
        </Box>
    </Flex>;
}
