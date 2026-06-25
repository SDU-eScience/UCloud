import * as React from "react";

import {callAPI} from "@/Authentication/DataHook";
import {MainContainer} from "@/ui-components/MainContainer";
import {Box, Button, Card, ExternalLink, Flex, Input, Link, Select, Text} from "@/ui-components";
import AppRoutes from "@/Routes";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {useProjectId} from "@/Project/Api";
import {InferenceCapability, InferenceModel, listModels, updateModel} from "./api";
import * as ApiTokens from "@/Applications/ApiTokens/api";
import Table, {TableHeader, TableRow} from "@/ui-components/Table";
import * as Heading from "@/ui-components/Heading";
import {copyToClipboard} from "@/UtilityFunctions";
import SyntaxHighlighter from "react-syntax-highlighter";
import {UcxAccordion} from "@/UCX/UcxAccordion";
import {VirtualMachineIconButton} from "@/Applications/Jobs/VirtualMachineIconButton";

const capabilities: InferenceCapability[] = ["TextGeneration", "TextToImage", "SpeechToText"];

export default function Models(): React.ReactNode {
    const projectId = useProjectId();
    const [models, setModels] = React.useState<InferenceModel[]>([]);
    const [isAdmin, setIsAdmin] = React.useState(false);
    const [providerId, setProviderId] = React.useState("");
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState("");
    const [editing, setEditing] = React.useState<InferenceModel | null>(null);
    const [editOriginalName, setEditOriginalName] = React.useState("");
    const [saving, setSaving] = React.useState(false);
    const [tokenStatus, setTokenStatus] = React.useState<ApiTokens.ApiTokenStatus | null>(null);
    const [generatingToken, setGeneratingToken] = React.useState(false);
    const [openTool, setOpenTool] = React.useState("generic");

    const refresh = React.useCallback(() => {
        setLoading(true);
        setError("");
        void callAPI(listModels({providerId: null}))
            .then(resp => {
                setModels(resp.models ?? []);
                setIsAdmin(resp.isAdmin);
                setProviderId(resp.providerId ?? "");
                setLoading(false);
            })
            .catch(err => {
                setError(err instanceof Error ? err.message : "Failed to load inference models");
                setLoading(false);
            });
    }, []);

    React.useEffect(() => refresh(), [refresh, projectId]);

    const startEdit = (model: InferenceModel) => {
        setEditOriginalName(model.name);
        setEditing({
            ...JSON.parse(JSON.stringify(model)),
            chatSettings: {
                temperature: model.chatSettings?.temperature ?? 0.8,
                topP: model.chatSettings?.topP ?? 0.1,
                maxCompletionTokens: model.chatSettings?.maxCompletionTokens ?? 65536,
                systemPrompt: model.chatSettings?.systemPrompt,
            },
        });
    };

    const saveEdit = () => {
        if (!editing) return;
        setSaving(true);
        setError("");
        void callAPI(updateModel({providerId: null, oldName: editOriginalName, model: editing}))
            .then(() => {
                setSaving(false);
                setEditing(null);
                refresh();
            })
            .catch(err => {
                setSaving(false);
                setError(err instanceof Error ? err.message : "Failed to update inference model");
            });
    };

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

    const server = tokenStatus?.server ?? "$SERVER";
    const apiServer = tokenStatus?.server ?? "$API_SERVER";
    const apiToken = tokenStatus?.token ?? "$API_TOK";
    const textModels = models.filter(model => model.capabilities.includes("TextGeneration"));
    const modelRefs = textModels.length > 0 ? textModels.map(model => ({
        id: model.name,
        title: model.title,
        contextWindow: model.contextWindow,
    })) : [{id: "$MODEL_ID", title: "$MODEL_TITLE", contextWindow: undefined}];
    const firstModelId = modelRefs[0]?.id ?? "$MODEL_ID";

    return <MainContainer main={<Box style={{display: "flex", flexDirection: "column", gap: 20, paddingBottom: 32}}>
        <Flex mb={8} style={{gap: 12, alignItems: "center", flexWrap: "wrap"}}>
            <h3 className="title" style={{margin: 0}}>AI Inference: Models</h3>
            <Link to={AppRoutes.inference.playground()}><Button m={0}>Playground</Button></Link>
            <Box flexGrow={1} />
            <ProjectSwitcher />
        </Flex>

        {error === "" ? null : <Text color="errorMain">{error}</Text>}
        {loading ? <Text>Loading inference models...</Text> : null}
        {!loading && models.length === 0 ? <Text color="textSecondary">No inference models are available.</Text> : null}

        {models.length === 0 ? null : <div style={{overflowX: "auto"}}>
            <Table tableType={"presentation"} width={"100%"} minWidth={"760px"}>
                <TableHeader>
                <TableRow>
                    <th>Model</th>
                    <th>Name</th>
                    <th>Cached</th>
                    <th>Input</th>
                    <th>Output</th>
                    <th>Capabilities</th>
                    {!isAdmin ? null : <th/>}
                </TableRow>
                </TableHeader>
                <tbody>
                {models.map(model => <TableRow key={model.name}>
                    <td>{model.title}</td>
                    <td>{model.name}</td>
                    <td>{formatMultiplier(model.priceMultiplier.cachedInput)}</td>
                    <td>{formatMultiplier(model.priceMultiplier.input)}</td>
                    <td>{formatMultiplier(model.priceMultiplier.output)}</td>
                    <td>{model.capabilities.join(", ")}</td>
                    {!isAdmin ? null : <td><Button type="button" onClick={() => startEdit(model)} m={0}>Edit</Button></td>}
                </TableRow>)}
                </tbody>
            </Table>
        </div>}

        {editing === null ? null : <Card>
            <Heading.h3 mb={16}>Editing {editOriginalName}</Heading.h3>
            <div style={{display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 12}}>
                <label>
                    Title
                    <Input value={editing.title} onChange={ev => setEditing({...editing, title: ev.currentTarget.value})} />
                </label>
                <label>
                    Name
                    <Input value={editing.name} onChange={ev => setEditing({...editing, name: ev.currentTarget.value})} />
                </label>
                <label>
                    Cached multiplier
                    <Input
                        type="number"
                        value={editing.priceMultiplier.cachedInput}
                        onChange={ev => setEditing({...editing, priceMultiplier: {...editing.priceMultiplier, cachedInput: parseInt(ev.currentTarget.value || "0")}})}
                    />
                </label>
                <label>
                    Input multiplier
                    <Input
                        type="number"
                        value={editing.priceMultiplier.input}
                        onChange={ev => setEditing({...editing, priceMultiplier: {...editing.priceMultiplier, input: parseInt(ev.currentTarget.value || "0")}})}
                    />
                </label>
                <label>
                    Output multiplier
                    <Input
                        type="number"
                        value={editing.priceMultiplier.output}
                        onChange={ev => setEditing({...editing, priceMultiplier: {...editing.priceMultiplier, output: parseInt(ev.currentTarget.value || "0")}})}
                    />
                </label>
                <label>
                    Public
                    <Select
                        value={editing.availability.public ? "true" : "false"}
                        onChange={ev => setEditing({...editing, availability: {...editing.availability, public: ev.currentTarget.value === "true"}})}
                        style={{width: "100%", height: 40}}
                    >
                        <option value="false">No</option>
                        <option value="true">Yes</option>
                    </Select>
                </label>
                <label>
                    Base path
                    <Input
                        value={editing.endpoint.basePath}
                        onChange={ev => setEditing({...editing, endpoint: {...editing.endpoint, basePath: ev.currentTarget.value}})}
                    />
                </label>
                <label>
                    Backend model name
                    <Input
                        value={editing.endpoint.backendModelName}
                        onChange={ev => setEditing({...editing, endpoint: {...editing.endpoint, backendModelName: ev.currentTarget.value}})}
                    />
                </label>
                <label>
                    Available to projects
                    <Input
                        value={editing.availability.availableTo.join(",")}
                        onChange={ev => setEditing({...editing, availability: {...editing.availability, availableTo: ev.currentTarget.value.split(",").map(x => x.trim()).filter(x => x !== "")}})}
                    />
                </label>
                <label>
                    Temperature
                    <Input
                        type="number"
                        step="0.1"
                        min="0"
                        max="2"
                        value={editing.chatSettings.temperature}
                        onChange={ev => setEditing({...editing, chatSettings: {...editing.chatSettings, temperature: parseFloat(ev.currentTarget.value || "0")}})}
                    />
                </label>
                <label>
                    Top P
                    <Input
                        type="number"
                        step="0.1"
                        min="0"
                        max="1"
                        value={editing.chatSettings.topP}
                        onChange={ev => setEditing({...editing, chatSettings: {...editing.chatSettings, topP: parseFloat(ev.currentTarget.value || "0")}})}
                    />
                </label>
                <label>
                    Max completion tokens
                    <Input
                        type="number"
                        min="1"
                        value={editing.chatSettings.maxCompletionTokens}
                        onChange={ev => setEditing({...editing, chatSettings: {...editing.chatSettings, maxCompletionTokens: parseInt(ev.currentTarget.value || "0")}})}
                    />
                </label>
                <label>
                    System prompt
                    <Input
                        value={editing.chatSettings.systemPrompt ?? ""}
                        placeholder="Use global default"
                        onChange={ev => {
                            const value = ev.currentTarget.value;
                            setEditing({...editing, chatSettings: {...editing.chatSettings, systemPrompt: value.trim() === "" ? undefined : value}});
                        }}
                    />
                </label>
            </div>
            <div style={{display: "flex", flexWrap: "wrap", gap: 12, marginTop: "12px"}}>
                {capabilities.map(capability => <label key={capability} style={{display: "flex", gap: 6, alignItems: "center"}}>
                    <input type="checkbox" checked={editing.capabilities.includes(capability)} onChange={ev => {
                        const next = ev.currentTarget.checked ? [...editing.capabilities, capability] : editing.capabilities.filter(it => it !== capability);
                        setEditing({...editing, capabilities: next});
                    }} />
                    {capability}
                </label>)}
            </div>

            <Box mt={"40px"} />
            <Flex justifyContent="end" px={"20px"} py={"12px"} margin={"-20px"} background={"var(--dialogToolbar)"} gap={"8px"} borderRadius={"0 0 10px 10px"}>
                <Button color={"errorMain"} type="button" onClick={() => setEditing(null)}>Cancel</Button>
                <Button color={"successMain"} type={"button"} onClick={saveEdit} disabled={saving}>{saving ? "Saving..." : "Save"}</Button>
            </Flex>
        </Card>}

        <Card>
            <Heading.h3 mb={16}>Configuring tools</Heading.h3>
            <Text color="textSecondary">
                Generate an inference API token, then use the server URL and token in any OpenAI-compatible tool that supports chat completions.
            </Text>

            <Box mt={20} style={{display: "grid", gap: 12}}>
                <Heading.h4>1. Generate an API token</Heading.h4>
                <Text>
                    This creates a 90 day token for the inference provider used by this page. The token is shown once, immediately after generation.
                </Text>
                <Flex gap={"12px"} flexWrap={"wrap"} alignItems={"center"}>
                    <Button type="button" color="successMain" onClick={generateToken} disabled={generatingToken || providerId === ""} m={0}>
                        {generatingToken ? "Generating..." : "Generate API token"}
                    </Button>
                    <Link to={AppRoutes.resources.apiTokens()}><Button type="button" color="secondaryMain" m={0}>Manage API tokens</Button></Link>
                </Flex>
                {tokenStatus === null ? null :
                    <div style={{display: "grid", gap: 8, marginTop: "16px"}}>
                        <CopyableValue label="Server" value={server} />
                        <CopyableValue label="API token" value={apiToken} />
                    </div>
                }
                {tokenStatus === null ? null : <Text color="textSecondary">
                    Save this API token now. It will not be visible after leaving this page.
                </Text>}
            </Box>

            <Box mt={28} style={{display: "grid", gap: 12}}>
                <Heading.h4>2. Choose a tool</Heading.h4>
                <Text color="textSecondary" mb={16}>
                    Select the tool you want to configure. Until a token has been generated, examples use placeholders that you can replace later.
                </Text>

                <ToolGuide id="generic" title="Generic OpenAI-compatible client" openTool={openTool} setOpenTool={setOpenTool}>
                    <Text>Use these values with clients that support custom OpenAI-compatible chat completion endpoints.</Text>
                    <ul>
                        <li>Server: <CopyableInline value={server} /></li>
                        <li>API token: <CopyableInline value={apiToken} /></li>
                    </ul>
                    <Text>Only the chat completions API is supported. Other OpenAI APIs, such as the responses API, are not available.</Text>
                </ToolGuide>

                <ToolGuide id="vscode" title="VS Code" openTool={openTool} setOpenTool={setOpenTool}>
                    <Text>Add UCloud as a custom chat-completions endpoint in VS Code.</Text>
                    <ul>
                        <li>Open the Command Palette {"->"} Chat: Manage Language Models.</li>
                        <li>Select "Add models".</li>
                        <li>Select "Custom endpoint".</li>
                        <li>Enter a group name such as "UCloud".</li>
                        <li>Use your API token (<CopyableInline value={apiToken} />).</li>
                        <li>Use the "Chat completions" API.</li>
                    </ul>
                    <Text>Use this model configuration:</Text>
                    <CodeBlock language="json" value={JSON.stringify({
                        name: "UCloud",
                        vendor: "customendpoint",
                        apiKey: apiToken,
                        apiType: "chat-completions",
                        models: modelRefs.map(model => ({
                            id: model.id,
                            name: model.title,
                            url: server,
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
                        <li>Paste your API token when prompted.</li>
                        <li>Create or edit <code>opencode.json</code>.</li>
                        <li>Add the provider configuration below.</li>
                        <li>Restart OpenCode.</li>
                        <li>Select the desired UCloud model from the model list.</li>
                    </ul>
                    <CodeBlock language="json" value={JSON.stringify({
                        $schema: "https://opencode.ai/config.json",
                        provider: {
                            "custom-openai": {
                                npm: "@ai-sdk/openai-compatible",
                                name: "Custom OpenAI-Compatible API",
                                options: {
                                    baseURL: apiServer,
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
                        <li>Export your API token before starting Codex:</li>
                    </ul>
                    <CodeBlock language="bash" value={`export API_TOK="${tokenStatus?.token ?? "your-api-token"}"`} />
                    <ul>
                        <li>Create or edit <code>~/.codex/config.toml</code>.</li>
                        <li>Add a custom provider:</li>
                    </ul>
                    <CodeBlock language="toml" value={`model = "${firstModelId}"
model_provider = "ucloud"

[model_providers.ucloud]
name = "UCloud"
base_url = "${apiServer}"
env_key = "API_TOK"`} />
                    <ul>
                        <li>Start Codex normally with <CopyableInline value="codex" />.</li>
                        <li>To use the model directly from the command line, run <CopyableInline value={`codex --model "${firstModelId}"`} />.</li>
                        <li>Keep the API key in the environment rather than hard-coding it in <code>config.toml</code>.</li>
                    </ul>
                </ToolGuide>
            </Box>
        </Card>


    </Box>} />;
}

function formatMultiplier(value: number): string {
    if (value === 0) return "N/A";
    if (value % 1000 === 0) return `${value / 1000}x`;
    return `${value / 1000}x`;
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
        <VirtualMachineIconButton
            tooltip={"Copy to clipboard"}
            onClick={() => copyToClipboard(props.value)}
            icon={"heroDocumentDuplicate"}
        />
    </Flex>;
}

function CopyableInline(props: {value: string}): React.ReactNode {
    return <code style={{cursor: "pointer"}} onClick={() => copyToClipboard(props.value)} title="Click to copy">{props.value}</code>;
}

function CodeBlock(props: {value: string; language: string}): React.ReactNode {
    return <div style={{position: "relative"}}>
        <div style={{position: "absolute", top: 8, right: 8, zIndex: 1}}>
            <Button type="button" m={0} onClick={() => copyToClipboard(props.value)}>Copy</Button>
        </div>
        <SyntaxHighlighter
            language={props.language}
            customStyle={{
                margin: 0,
                padding: "12px 64px 12px 12px",
                borderRadius: 8,
                border: "var(--defaultCardBorder)",
                fontSize: 13,
            }}
        >
            {props.value}
        </SyntaxHighlighter>
    </div>;
}
