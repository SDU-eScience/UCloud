import * as React from "react";
import SyntaxHighlighter from "react-syntax-highlighter";

import {callAPI} from "@/Authentication/DataHook";
import * as ApiTokens from "@/Applications/ApiTokens/api";
import {IconButton} from "@/ui-components/IconButton";
import AppRoutes from "@/Routes";
import {Box, Button, ExternalLink, Flex, Link, Text} from "@/ui-components";
import {UcxAccordion} from "@/UCX/UcxAccordion";
import {copyToClipboard} from "@/UtilityFunctions";
import {InferenceModel} from "./api";
import {CopyButton} from "@/ui-components/CopyButton";
import CodeSnippet from "@/ui-components/CodeSnippet";

export default function ConfiguringTools({
    title,
    providerId,
    server,
    models,
    modelId,
}: {
    title: string;
    providerId: string;
    server: string;
    models: InferenceModel[];
    modelId?: string;
}): React.ReactNode {
    const [tokenStatus, setTokenStatus] = React.useState<ApiTokens.ApiTokenStatus | null>(null);
    const [generatingToken, setGeneratingToken] = React.useState(false);
    const [openTool, setOpenTool] = React.useState("generic");
    const [error, setError] = React.useState("");

    const generateToken = () => {
        if (providerId === "") {
            setError("Cannot generate an API token before the inference provider has loaded.");
            return;
        }

        setGeneratingToken(true);
        setError("");
        void callAPI<ApiTokens.ApiToken>(ApiTokens.create({
            title: "UCloud inference token",
            description: "Generated from the inference models page.",
            requestedPermissions: [{name: "inference", action: "use"}],
            expiresAt: Date.now() + 90 * 24 * 60 * 60 * 1000,
            provider: providerId,
            product: {
                category: "",
                id: "",
                provider: ""
            },
        })).then(resp => {
            setTokenStatus(resp.status);
            setGeneratingToken(false);
        }).catch(err => {
            setGeneratingToken(false);
            setError(err instanceof Error ? err.message : "Failed to generate API token");
        });
    };

    const resolvedServer = tokenStatus?.server ?? server;
    const apiToken = tokenStatus?.token ?? "$API_TOK";
    const configuredModels = modelId ? models.filter(model => model.name === modelId) : models.filter(model => model.capabilities.includes("TextGeneration"));
    const modelRefs = configuredModels.length > 0 ? configuredModels.map(model => ({
        id: model.name,
        title: model.title,
        contextWindow: model.contextWindow,
    })) : [{id: modelId ?? "$MODEL_ID", title: "$MODEL_TITLE", contextWindow: undefined}];
    const firstModelId = modelRefs[0]?.id ?? modelId ?? "$MODEL_ID";

    return <>
        {error === "" ? null : <Text color="errorMain">{error}</Text>}
        <Flex gap="12px" alignItems="center" flexWrap="wrap">
            <h3 className="title" style={{margin: 0}}>{title}</h3>
            <Box flexGrow={1} />
            <Button type="button" color="successMain" onClick={generateToken} disabled={generatingToken || providerId === ""} m={0}>
                {generatingToken ? "Generating..." : "Generate API key"}
            </Button>
            <Link to={AppRoutes.resources.apiTokens()}><Button type="button" color="secondaryMain" m={0}>Manage API keys</Button></Link>
        </Flex>
        {tokenStatus === null ? null : <Box mt={16} style={{display: "grid", gap: 12}}>
            <Flex gap={"12px"} flexWrap={"wrap"} alignItems={"center"}>
                <CopyableValue label="Server" value={resolvedServer} />
                <CopyableValue label="API key" value={apiToken} />
            </Flex>
            <Text color="textSecondary">
                Save this API key now. It will not be visible after leaving this page.
            </Text>
        </Box>}

        <Box mt={20} style={{display: "grid", gap: 12}}>
            <ToolGuide id="generic" title="Generic OpenAI-compatible client" openTool={openTool} setOpenTool={setOpenTool}>
                <CodeSnippet lang="bash" children={`curl "${resolvedServer}/chat/completions" \\
  -H "Authorization: Bearer ${apiToken}" \\
  -H "Content-Type: application/json" \\
  -d '{
    "model": "${firstModelId}",
    "messages": [{"role": "user", "content": "Hello"}]
  }'`} />

                <Text>Only the chat completions API is supported. Other OpenAI APIs, such as the responses API, are not available.</Text>
            </ToolGuide>

            <ToolGuide id="vscode" title="VS Code" openTool={openTool} setOpenTool={setOpenTool}>
                <Text>Add UCloud as a custom chat-completions endpoint in VS Code.</Text>
                <ul>
                    <li>Open the Command Palette {"->"} Chat: Manage Language Models.</li>
                    <li>Select "Add models".</li>
                    <li>Select "Custom endpoint".</li>
                    <li>Enter a group name such as "UCloud".</li>
                    <li>Use your API key (<CopyableInline value={apiToken} />).</li>
                    <li>Use the "Chat completions" API.</li>
                </ul>
                <Text>Use this model configuration:</Text>
                <CodeSnippet lang="json" children={JSON.stringify({
                    name: "UCloud",
                    vendor: "customendpoint",
                    apiKey: apiToken,
                    apiType: "chat-completions",
                    models: modelRefs.map(model => ({
                        id: model.id,
                        name: model.title,
                        url: resolvedServer,
                        toolCalling: true,
                        vision: false,
                        maxInputTokens: model.contextWindow ?? 128000,
                        maxOutputTokens: 16000,
                    })),
                }, null, 2)} />
            </ToolGuide>

            <ToolGuide id="opencode" title="OpenCode" openTool={openTool} setOpenTool={setOpenTool}>
                <Text>Documentation: <ExternalLink href="https://opencode.ai/docs/providers/">OpenCode providers</ExternalLink></Text>
                <ul>
                    <li>Open OpenCode.</li>
                    <li>Run <CopyableInline value="/connect" />.</li>
                    <li>Choose <b>Other</b>.</li>
                    <li>Use a provider ID such as <CopyableInline value="custom-openai" />.</li>
                    <li>Paste your API key when prompted.</li>
                    <li>Create or edit <code>opencode.json</code>.</li>
                    <li>Add the provider configuration below.</li>
                    <li>Restart OpenCode.</li>
                    <li>Select the desired UCloud model from the model list.</li>
                </ul>
                <CodeSnippet lang="json" children={JSON.stringify({
                    $schema: "https://opencode.ai/config.json",
                    provider: {
                        "custom-openai": {
                            npm: "@ai-sdk/openai-compatible",
                            name: "Custom OpenAI-Compatible API",
                            options: {
                                baseURL: resolvedServer,
                            },
                            models: Object.fromEntries(modelRefs.map(model => [model.id, {name: model.title}])),
                        },
                    },
                }, null, 2)} />
            </ToolGuide>

            <ToolGuide id="codex" title="Codex CLI" openTool={openTool} setOpenTool={setOpenTool}>
                <Text>Documentation: <ExternalLink href="https://developers.openai.com/codex/config-reference">Codex configuration reference</ExternalLink></Text>
                <Text>Codex CLI supports custom model providers configured in <code>~/.codex/config.toml</code>.</Text>
                <ul>
                    <li>Export your API key before starting Codex:</li>
                </ul>
                <CodeSnippet lang="bash" children={`export API_TOK="${tokenStatus?.token ?? "your-api-key"}"`} />
                <ul>
                    <li>Create or edit <code>~/.codex/config.toml</code>.</li>
                    <li>Add a custom provider:</li>
                </ul>
                <CodeSnippet lang="toml" children={`model = "${firstModelId}"
model_provider = "ucloud"

[model_providers.ucloud]
name = "UCloud"
base_url = "${resolvedServer}"
env_key = "API_TOK"`} />
                <ul>
                    <li>Start Codex normally with <CopyableInline value="codex" />.</li>
                    <li>To use the model directly from the command line, run <CopyableInline value={`codex --model "${firstModelId}"`} />.</li>
                    <li>Keep the API key in the environment rather than hard-coding it in <code>config.toml</code>.</li>
                </ul>
            </ToolGuide>
        </Box>
    </>;
}

function ToolGuide(props: {
    id: string;
    title: string;
    openTool: string;
    setOpenTool: (id: string) => void;
    children: React.ReactNode;
}): React.ReactNode {
    const open = props.openTool === props.id;
    return <UcxAccordion title={props.title} open={open} onOpenChange={next => props.setOpenTool(next ? props.id : "")}>
        <Box style={{display: "grid", gap: 10}}>{props.children}</Box>
    </UcxAccordion>;
}

function CopyableValue(props: {label: string; value: string}): React.ReactNode {
    return <Flex style={{gap: 8, alignItems: "center", flexWrap: "wrap"}}>
        <b>{props.label}: </b>
        <code style={{cursor: "pointer"}} onClick={() => copyToClipboard(props.value)} title="Click to copy">{props.value}</code>
        <CopyButton onClick={() => copyToClipboard(props.value)} />
    </Flex>;
}

function CopyableInline(props: {value: string}): React.ReactNode {
    return <code style={{cursor: "pointer"}} onClick={() => copyToClipboard(props.value)} title="Click to copy">{props.value}</code>;
}
