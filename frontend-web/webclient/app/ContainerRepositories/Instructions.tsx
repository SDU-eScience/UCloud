import * as React from "react";

import * as ApiTokens from "@/Applications/ApiTokens/api";
import AppRoutes from "@/Routes";
import {callAPI} from "@/Authentication/DataHook";
import {Box, Button, Flex, Link, Text} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import CodeSnippet from "@/ui-components/CodeSnippet";
import {CopyButton} from "@/ui-components/CopyButton";
import {copyToClipboard} from "@/UtilityFunctions";
import {injectStyle} from "@/Unstyled";
import {dialogStore} from "@/Dialog/DialogStore";
import {DocumentTypography} from "@/ui-components/Markdown";

export default function ContainerRepositoryInstructions({
    providerId,
    server,
    imageReference,
}: {
    providerId: string;
    server: string;
    imageReference: string;
}): React.ReactNode {
    const [tokenStatus, setTokenStatus] = React.useState<ApiTokens.ApiTokenStatus | null>(null);
    const [generatingToken, setGeneratingToken] = React.useState(false);
    const [error, setError] = React.useState("");

    const generateToken = () => {
        setGeneratingToken(true);
        setError("");
        void callAPI<ApiTokens.ApiToken>(ApiTokens.create({
            title: "UCloud container registry token",
            description: "Generated from the container repositories page.",
            requestedPermissions: [
                {name: "containerRepository", action: "pull"},
                {name: "containerRepository", action: "push"},
            ],
            expiresAt: Date.now() + 90 * 24 * 60 * 60 * 1000,
            provider: providerId,
            product: {
                category: "",
                id: "",
                provider: "",
            },
        })).then(response => {
            setTokenStatus(response.status);
            setGeneratingToken(false);
        }).catch(reason => {
            setGeneratingToken(false);
            setError(reason instanceof Error ? reason.message : "Failed to generate API token");
        });
    };

    const apiToken = tokenStatus?.token ?? "<api-token>";
    const image = `${server}/${imageReference}`;

    const instructions = `
$ docker login ${server}
Username: ucloud
Password: ${apiToken}

$ docker pull ${image}
... OK!

$ docker push ${image}
... OK!
    `.trim();


    return <Box style={{display: "grid"}}>
        <DocumentTypography>
            <h1>Using the container image registry</h1>
            <p>
                You can use Docker or any other compatible client to push and pull images from the container registry.
                Below, you can find instructions on how to <i>generate an API token</i> and use it to authenticate
                yourself.
            </p>

            {error ? <Text color="errorMain">{error}</Text> : null}

            <CopyableValue label="Registry server" value={server} />
            <CopyableValue label="Username" value={"ucloud"} />
            <CopyableValue label="Password" value={apiToken} />

            <Flex gap="12px" alignItems="center" flexWrap="wrap" my={16}>
                <Button type="button" color="successMain" onClick={generateToken} disabled={generatingToken} m={0}>
                    {generatingToken ? "Generating..." : "Generate API token"}
                </Button>
                <Link to={AppRoutes.resources.apiTokens()} target={"_blank"}>
                    <Button type="button" color="secondaryMain" m={0}>Manage API tokens</Button>
                </Link>
            </Flex>

            <h2>Usage</h2>
            <CodeSnippet lang="bash">{instructions}</CodeSnippet>
        </DocumentTypography>
    </Box>;
}

function CopyableValue({label, value}: {label: string; value: string}): React.ReactNode {
    return <Flex gap="8px" alignItems="center" flexWrap="wrap">
        <b>{label}:</b>
        <code style={{cursor: "pointer"}} onClick={() => copyToClipboard(value)} title="Click to copy">{value}</code>
        <CopyButton onClick={() => copyToClipboard(value)} />
    </Flex>;
}
