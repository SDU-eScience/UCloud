import * as React from "react";
import {useNavigate, useSearchParams} from "react-router-dom";

import {callAPI} from "@/Authentication/DataHook";
import AppRoutes from "@/Routes";
import {Box, Button, Card, ExternalLink, Flex, Input, Link, Select, Text, TextArea} from "@/ui-components";
import {MainContainer, MAIN_CONTAINER_MAX_WIDTH} from "@/ui-components/MainContainer";
import Table, {TableHeader, TableRow} from "@/ui-components/Table";
import {copyToClipboard} from "@/UtilityFunctions";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {InferenceBenchmark, InferenceCapability, InferenceModel, listModels, updateBenchmarks, updateModel} from "./api";
import ConfiguringTools from "./ConfiguringTools";
import ModelInferenceLogo from "./ModelLogo";
import {CopyButton} from "@/ui-components/CopyButton";
import {injectStyle} from "@/Unstyled";
import {useIsLightThemeStored} from "@/ui-components/theme";
import {formatDate} from "date-fns";
import {MarkdownDocument} from "@/ui-components/Markdown";

const fallbackDocs = "https://docs.cloud.sdu.dk";
const capabilities: InferenceCapability[] = ["TextGeneration", "TextToImage", "SpeechToText"];

export default function ModelPage(): React.ReactNode {
    const [params] = useSearchParams();
    const navigate = useNavigate();
    const modelName = params.get("name") ?? "";
    const [models, setModels] = React.useState<InferenceModel[]>([]);
    const [benchmarks, setBenchmarks] = React.useState<InferenceBenchmark[]>([]);
    const [isAdmin, setIsAdmin] = React.useState(false);
    const [providerId, setProviderId] = React.useState("");
    const [server, setServer] = React.useState("");
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState("");
    const [editing, setEditing] = React.useState(false);
    const [draft, setDraft] = React.useState<InferenceModel | null>(null);
    const [savingModel, setSavingModel] = React.useState(false);
    const [savingBenchmarks, setSavingBenchmarks] = React.useState(false);

    const model = models.find(it => it.name === modelName);
    usePage(model?.title ?? "Inference model", SidebarTabId.INFERENCE);

    const refresh = React.useCallback(() => {
        setLoading(true);
        setError("");
        void callAPI(listModels({providerId: null}))
            .then(resp => {
                setModels(resp.models ?? []);
                setBenchmarks(resp.benchmarks ?? []);
                setIsAdmin(resp.isAdmin);
                setProviderId(resp.providerId ?? "");
                setServer(resp.server ?? "");
                setLoading(false);
            })
            .catch(err => {
                setError(err instanceof Error ? err.message : "Failed to load inference model");
                setLoading(false);
            });
    }, []);

    React.useEffect(() => refresh(), [refresh, modelName]);

    React.useEffect(() => {
        if (!editing || !model) return;
        setDraft(normalizeEditableModel(model));
    }, [editing, model?.name]);

    const startEdit = () => {
        if (!model) return;
        setDraft(normalizeEditableModel(model));
        setEditing(true);
    };

    const saveModel = () => {
        if (!draft || !model) return;
        setSavingModel(true);
        setError("");
        void callAPI(updateModel({providerId: null, oldName: model.name, model: draft}))
            .then(() => {
                setSavingModel(false);
                setEditing(false);
                refresh();
                if (draft.name !== model.name) navigate(AppRoutes.inference.model(draft.name));
            })
            .catch(err => {
                setSavingModel(false);
                setError(err instanceof Error ? err.message : "Failed to update inference model");
            });
    };

    const saveBenchmarks = () => {
        setSavingBenchmarks(true);
        setError("");
        void callAPI(updateBenchmarks({providerId: null, benchmarks}))
            .then(() => {
                setSavingBenchmarks(false);
                refresh();
            })
            .catch(err => {
                setSavingBenchmarks(false);
                setError(err instanceof Error ? err.message : "Failed to update benchmarks");
            });
    };

    if (model) {
        return <>
            {error === "" ? null : <MainContainer main={<Text color="errorMain">{error}</Text>} />}
            <ModelPageContent
                model={editing && draft ? draft : model}
                models={models}
                benchmarks={benchmarks}
                setBenchmarks={setBenchmarks}
                providerId={providerId}
                server={server}
                isAdmin={isAdmin}
                editing={editing}
                savingModel={savingModel}
                savingBenchmarks={savingBenchmarks}
                onStartEdit={startEdit}
                onCancelEdit={() => { setEditing(false); setDraft(null); }}
                onSaveModel={saveModel}
                onSaveBenchmarks={saveBenchmarks}
                setModel={model => setDraft(model)}
            />
        </>;
    }

    return <MainContainer main={<Box style={{display: "grid", gap: 20, paddingBottom: 32}}>
        {error === "" ? null : <Text color="errorMain">{error}</Text>}
        {loading ? <Text>Loading inference model...</Text> : null}
        {!loading ? <Text>Model not found.</Text> : null}
    </Box>} />;
}

const PageStyle = injectStyle("model-page", k => `
    ${k} {
        --model-hero: var(--blue-10);
        --model-hero-border: var(--blue-20);
    }

    ${k} .model-hero {
        background: var(--model-hero);
        border-bottom: 5px solid var(--model-hero-border);
        margin-bottom: 16px;
        box-sizing: border-box;
        height: 435px;
        padding: 56px 16px;
        display: flex;
        justify-content: center;
    }

    ${k} .model-hero-inner {
        align-items: center;
        display: flex;
        flex-wrap: wrap;
        gap: 32px;
        justify-content: space-between;
        width: 100%;
        max-width: ${MAIN_CONTAINER_MAX_WIDTH};
        padding: 0 16px;
    }

    ${k} .model-hero-copy {
        display: grid;
        gap: 14px;
        max-width: 720px;
    }

    ${k} .model-hero-title {
        font-size: clamp(32px, 5vw, 52px);
        line-height: 1;
        margin: 0;
    }

    ${k} .model-hero-description {
        color: var(--textPrimary);
        font-size: 16px;
        line-height: 1.6;
        margin: 0;
        max-width: 660px;
    }

    ${k} .model-page-layout {
        display: grid;
        grid-template-columns: minmax(0, 1fr) minmax(260px, 360px);
        gap: 36px;
        padding-bottom: 32px;
    }

    ${k} .model-main-content {
        display: grid;
        gap: 34px;
        min-width: 0;
    }

    ${k} .model-page-layout > * {
        min-width: 0;
    }

    ${k} .model-sidebar-separator {
        display: none;
    }

    ${k} .model-stats-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
        gap: 16px;
    }

    @media (max-width: 1000px) {
        ${k} .model-hero-logo {
            display: none;
        }

        ${k} .model-hero-inner {
            justify-content: flex-start;
        }

        ${k} .model-page-layout {
            grid-template-columns: minmax(0, 1fr);
        }

        ${k} .model-sidebar-separator {
            border: 0;
            border-top: 1px solid var(--borderColor);
            display: block;
            margin: 0 0 20px;
            width: 100%;
        }
    }

    @media (max-width: 640px) {
        ${k} .model-hero {
            height: auto;
            min-height: 320px;
            padding: 40px 16px;
        }

        ${k} .model-stats-grid {
            grid-template-columns: 1fr;
            gap: 0;
        }
    }
     
    html.dark ${k} {
        --model-hero: var(--blue-80);
        --model-hero-border: var(--blue-90);
    }
`);

function ModelPageContent(props: {
    model: InferenceModel;
    models: InferenceModel[];
    benchmarks: InferenceBenchmark[];
    setBenchmarks: (benchmarks: InferenceBenchmark[]) => void;
    providerId: string;
    server: string;
    isAdmin: boolean;
    editing: boolean;
    savingModel: boolean;
    savingBenchmarks: boolean;
    onStartEdit: () => void;
    onCancelEdit: () => void;
    onSaveModel: () => void;
    onSaveBenchmarks: () => void;
    setModel: (model: InferenceModel) => void;
}): React.ReactNode {
    const {model, models, benchmarks, providerId, server} = props;
    const page = model.page;
    const documentationUrl = page?.documentationUrl || fallbackDocs;
    const shortDescription = page?.shortDescription || `${model.title} is available through the UCloud OpenAI-compatible inference endpoint.`;
    const keyStats = page?.about?.keyStats?.length ? page.about.keyStats : fallbackKeyStats(model);
    const lightTheme = useIsLightThemeStored();

    const docButtonColor = lightTheme ? "primaryMain" : "secondaryMain";

    const updateModel = (next: InferenceModel) => props.setModel(next);

    return <div className={PageStyle}>
        <Box className="model-hero">
            <Flex className="model-hero-inner">
                <Box className="model-hero-copy">
                    {props.editing ? <Input value={model.title} onChange={ev => updateModel({...model, title: ev.currentTarget.value})} style={{fontSize: 36, fontWeight: 700, height: 54}} /> : <h2 className="title model-hero-title">{model.title}</h2>}
                    {props.editing ? <TextArea value={page?.shortDescription ?? ""} placeholder={shortDescription} rows={4} onChange={ev => updatePage(model, updateModel, {...defaultModelPage(), ...page, shortDescription: ev.currentTarget.value})} /> : <p className="model-hero-description">{shortDescription}</p>}
                    {props.editing ? null : <Flex gap="12px" flexWrap="wrap">
                        <Link to={AppRoutes.inference.playground(model.name)}><Button type="button" color="successMain">Try now</Button></Link>
                        <ExternalLink href={documentationUrl}><Button type="button" color={docButtonColor}>Documentation</Button></ExternalLink>
                    </Flex>}
                </Box>
                <span className="model-hero-logo"><ModelInferenceLogo modelName={model.name} size={160} /></span>
            </Flex>
        </Box>

        <MainContainer main={<Box className="model-page-layout">
            <Box className="model-main-content">
                <Section title="About model">
                    {props.editing ? <TextArea value={page?.about?.description ?? ""} placeholder={shortDescription} rows={12} onChange={ev => updateAbout(model, updateModel, {...(page?.about ?? {}), description: ev.currentTarget.value})} /> : page?.about?.description ? <MarkdownDocument text={page.about.description} /> : <Text>{shortDescription}</Text>}
                </Section>
                {props.editing ? <KeyStatsEditor stats={page?.about?.keyStats ?? []} setStats={stats => updateAbout(model, updateModel, {...(page?.about ?? {}), keyStats: stats})} /> : <Box className="model-stats-grid">
                    {keyStats.map((stat, idx) => <Box key={idx} style={{borderTop: "2px solid var(--primaryMain)"}} my={22} pt={12}>
                        <Text color="textSecondary">{stat.label}</Text>
                        <Text fontSize="24px" fontWeight={700}>{stat.value}</Text>
                        {stat.description ? <Text color="textSecondary">{stat.description}</Text> : null}
                    </Box>)}
                </Box>}
                {props.editing ? <RepeatableStrings title="Highlights" values={page?.about?.highlights ?? []} placeholder="Highlight shown as a bullet point" setValues={values => updateAbout(model, updateModel, {...(page?.about ?? {}), highlights: values})} /> : page?.about?.highlights?.length ? <>
                    <Section title={"Highlights"}>
                        <ul style={{margin: "0", paddingLeft: "20px"}}>{page.about.highlights.map((item, idx) => <li key={idx}><MarkdownDocument text={item} /></li>)}</ul>
                    </Section>
                </> : null}

                {props.editing ? <BenchmarkScoresEditor benchmarks={benchmarks} scores={page?.benchmarkScores ?? {}} setScores={scores => updatePage(model, updateModel, {...defaultModelPage(), ...page, benchmarkScores: scores})} /> : <BenchmarkSection model={model} models={models} benchmarks={benchmarks} />}

                {props.editing ? <Section title="Benchmark definitions">
                    <Text color="textSecondary">Curated benchmark sections are global. Scores are edited on each model page.</Text>
                    <BenchmarkDefinitionsEditor benchmarks={benchmarks} setBenchmarks={props.setBenchmarks} models={models} />
                    <Flex justifyContent="end" mt={12}>
                        <Button color="successMain" type="button" onClick={props.onSaveBenchmarks} disabled={props.savingBenchmarks}>{props.savingBenchmarks ? "Saving..." : "Save benchmarks"}</Button>
                    </Flex>
                </Section> : <Section>
                    <ConfiguringTools title="API usage" providerId={providerId} server={server} models={models} modelId={model.name} />
                </Section>}
            </Box>

            <Section>
                <hr className="model-sidebar-separator" />
                {props.editing ? <ModelSettingsEditor model={model} models={models} setModel={updateModel} /> : <Datasheet model={model} />}

                {props.editing ? null : <Flex gap="12px" flexWrap="wrap" flexDirection={"column"}>
                    <Link to={AppRoutes.inference.playground(model.name)}>
                        <Button type="button" color="successMain" width={"100%"}>Try now</Button>
                    </Link>
                    <ExternalLink href={documentationUrl}>
                        <Button type="button" width={"100%"}>Documentation</Button>
                    </ExternalLink>
                </Flex>}
                {!props.isAdmin ? null : <Box>
                    {!props.editing ? <Button type="button" color={"errorMain"} width="100%" onClick={props.onStartEdit}>Edit model</Button> : <Flex gap="8px" flexDirection="column">
                        <Button type="button" color="successMain" width="100%" onClick={props.onSaveModel} disabled={props.savingModel}>{props.savingModel ? "Saving..." : "Save model"}</Button>
                        <Button type="button" color="errorMain" width="100%" onClick={props.onCancelEdit}>Cancel</Button>
                    </Flex>}
                </Box>}
            </Section>
        </Box>} />
    </div>;
}

function Section({title, children}: React.PropsWithChildren<{title?: string}>): React.ReactNode {
    return <section style={{display: "flex", gap: 14, flexDirection: "column"}}>
        {title ? <h3 className="title" style={{margin: 0}}>{title}</h3> : null}
        {children}
    </section>;
}

function BenchmarkSection({model, models, benchmarks}: {model: InferenceModel; models: InferenceModel[]; benchmarks: InferenceBenchmark[]}): React.ReactNode {
    const visible = benchmarks.filter(benchmark => model.page?.benchmarkScores?.[benchmark.id]);
    if (visible.length === 0) return null;

    return <Section title="Benchmarks">
        <Box style={{display: "grid", gap: 16}}>
            {visible.map(benchmark => {
                const rows = [model.name, ...benchmark.modelNames.filter(name => name !== model.name)]
                    .map(name => ({model: models.find(it => it.name === name), score: models.find(it => it.name === name)?.page?.benchmarkScores?.[benchmark.id]}))
                    .filter(row => row.model && row.score);
                return <Box key={benchmark.id}>
                    <h4 style={{marginBottom: 4}}>{benchmark.title}</h4>
                    {benchmark.description ? <Text color="textSecondary">{benchmark.description}</Text> : null}
                    <Table tableType="presentation" width="100%">
                        <TableHeader><TableRow><th>Model</th><th>Score</th></TableRow></TableHeader>
                        <tbody>{rows.map(row => <TableRow key={row.model!.name}>
                            <td>{row.model!.title}</td>
                            <td>{row.score}</td>
                        </TableRow>)}</tbody>
                    </Table>
                </Box>;
            })}
        </Box>
    </Section>;
}

const DATE_FORMAT = "dd/MM/yyyy";

function Datasheet({model}: {model: InferenceModel}): React.ReactNode {
    const page = model.page;
    const rows: [string, React.ReactNode][] = [
        ["Model provider", <Flex key="provider" gap="8px" alignItems="center"><ModelInferenceLogo modelName={model.name} />{providerName(model.name)}</Flex>],
        ["Release date", page?.releaseDate ? formatDate(new Date(page.releaseDate), DATE_FORMAT) : null],
        ["Capabilities", model.capabilities.join(", ")],
        ["Endpoint", <CopyableEndpoint key="endpoint" value={model.name} />],
        ["Parameters", page?.datasheet?.parameters ?? "Not specified"],
        ["Activated parameters", page?.datasheet?.activatedParameters ?? null],
        ["Context length", model.contextWindow ? model.contextWindow.toLocaleString() : "Not specified"],
        ["Quantization level", page?.datasheet?.quantization ?? null],
        ["Input multiplier", formatMultiplier(model.priceMultiplier.input)],
        ["Cached input multiplier", formatMultiplier(model.priceMultiplier.cachedInput)],
        ["Output multiplier", formatMultiplier(model.priceMultiplier.output)],
    ];

    return <Table tableType="presentation" width="100%">
        <tbody>{rows.map(([label, value]) => !value ? null : <TableRow key={label} height={"58px"}>
            <th style={{textAlign: "left", width: "38%", verticalAlign: "middle"}}>{label}</th>
            <td style={{verticalAlign: "middle"}}>{value}</td>
        </TableRow>)}</tbody>
    </Table>;
}

function CopyableEndpoint({value}: {value: string}): React.ReactNode {
    return <Flex gap="8px" alignItems="center">
        <code>{value}</code>
        <CopyButton onClick={() => copyToClipboard(value)} />
    </Flex>;
}

function fallbackKeyStats(model: InferenceModel): {label: string; value: string; description?: string}[] {
    return [
        {label: "Context length", value: model.contextWindow ? model.contextWindow.toLocaleString() : "Not specified"},
        {label: "Input multiplier", value: formatMultiplier(model.priceMultiplier.input)},
        {label: "Output multiplier", value: formatMultiplier(model.priceMultiplier.output)},
    ];
}

function providerName(modelName: string): string {
    const norm = modelName.toLowerCase();
    if (norm.includes("deepseek")) return "DeepSeek";
    if (norm.includes("llama")) return "Meta";
    if (norm.includes("qwen")) return "Qwen";
    if (norm.includes("minimax")) return "Minimax";
    if (norm.includes("glm")) return "Z.ai";
    if (norm.includes("mistral")) return "Mistral";
    if (norm.includes("google") || norm.includes("gemma")) return "Google";
    if (norm.includes("kimi") || norm.includes("k2.")) return "Moonshot AI";
    if (norm.includes("gpt")) return "OpenAI";
    return "Unknown";
}

function formatMultiplier(value: number): string {
    if (value === 0) return "N/A";
    return `${value / 1000}x`;
}

function normalizeEditableModel(model: InferenceModel): InferenceModel {
    const defaults = defaultModelPage();
    return {
        ...JSON.parse(JSON.stringify(model)),
        titleModelName: model.titleModelName || model.name,
        chatSettings: {
            temperature: model.chatSettings?.temperature ?? 0.8,
            topP: model.chatSettings?.topP ?? 0.1,
            maxCompletionTokens: model.chatSettings?.maxCompletionTokens ?? 65536,
            systemPrompt: model.chatSettings?.systemPrompt,
        },
        page: {
            ...defaults,
            ...model.page,
            about: {...defaults.about!, ...model.page?.about},
            datasheet: {...defaults.datasheet!, ...model.page?.datasheet},
            benchmarkScores: {...model.page?.benchmarkScores},
        },
    };
}

function defaultModelPage(): NonNullable<InferenceModel["page"]> {
    return {
        shortDescription: "",
        documentationUrl: "",
        about: {description: "", highlights: [], keyStats: []},
        benchmarkScores: {},
        datasheet: {parameters: "", activatedParameters: "", quantization: ""},
    };
}

function updatePage(model: InferenceModel, setModel: (model: InferenceModel) => void, page: NonNullable<InferenceModel["page"]>) {
    setModel({...model, page});
}

function updateAbout(model: InferenceModel, setModel: (model: InferenceModel) => void, about: NonNullable<NonNullable<InferenceModel["page"]>["about"]>) {
    const page = {...defaultModelPage(), ...model.page};
    setModel({...model, page: {...page, about}});
}

function updateDatasheet(model: InferenceModel, setModel: (model: InferenceModel) => void, datasheet: NonNullable<NonNullable<InferenceModel["page"]>["datasheet"]>) {
    const page = {...defaultModelPage(), ...model.page};
    setModel({...model, page: {...page, datasheet}});
}

function ModelSettingsEditor(props: {model: InferenceModel; models: InferenceModel[]; setModel: (model: InferenceModel) => void;}): React.ReactNode {
    const {model, setModel} = props;
    const defaults = defaultModelPage();
    const page = {...defaults, ...model.page};
    const datasheet = {...defaults.datasheet!, ...page.datasheet};

    return <Box style={{display: "grid", gap: 14}}>
        <label>Model name<Input value={model.name} onChange={ev => setModel({...model, name: ev.currentTarget.value})} /></label>
        <label>Documentation URL<Input value={page.documentationUrl ?? ""} onChange={ev => updatePage(model, setModel, {...page, documentationUrl: ev.currentTarget.value})} /></label>
        <label>Release date<Input type="date" value={dateInputValue(page.releaseDate)} onChange={ev => updatePage(model, setModel, {...page, releaseDate: timestampFromDateInput(ev.currentTarget.value)})} /></label>
        <label>Title generation model<Select value={model.titleModelName || model.name} onChange={ev => setModel({...model, titleModelName: ev.currentTarget.value})} style={{width: "100%", height: 40}}>{props.models.filter(it => it.capabilities.includes("TextGeneration")).map(it => <option key={it.name} value={it.name}>{it.title} ({it.name})</option>)}</Select></label>
        <label>Public<Select value={model.availability.public ? "true" : "false"} onChange={ev => setModel({...model, availability: {...model.availability, public: ev.currentTarget.value === "true"}})} style={{width: "100%", height: 40}}><option value="false">No</option><option value="true">Yes</option></Select></label>
        <label>Available to projects<Input value={model.availability.availableTo.join(", ")} onChange={ev => setModel({...model, availability: {...model.availability, availableTo: parseCommaList(ev.currentTarget.value)}})} /></label>
        <label>Base path<Input value={model.endpoint.basePath} onChange={ev => setModel({...model, endpoint: {...model.endpoint, basePath: ev.currentTarget.value}})} /></label>
        <label>Backend model name<Input value={model.endpoint.backendModelName} onChange={ev => setModel({...model, endpoint: {...model.endpoint, backendModelName: ev.currentTarget.value}})} /></label>
        <label>Parameters<Input value={datasheet.parameters ?? ""} onChange={ev => updateDatasheet(model, setModel, {...datasheet, parameters: ev.currentTarget.value})} /></label>
        <label>Activated parameters<Input value={datasheet.activatedParameters ?? ""} onChange={ev => updateDatasheet(model, setModel, {...datasheet, activatedParameters: ev.currentTarget.value})} /></label>
        <label>Quantization<Input value={datasheet.quantization ?? ""} onChange={ev => updateDatasheet(model, setModel, {...datasheet, quantization: ev.currentTarget.value})} /></label>
        <label>Cached multiplier<Input type="number" value={model.priceMultiplier.cachedInput} onChange={ev => setModel({...model, priceMultiplier: {...model.priceMultiplier, cachedInput: parseInt(ev.currentTarget.value || "0")}})} /></label>
        <label>Input multiplier<Input type="number" value={model.priceMultiplier.input} onChange={ev => setModel({...model, priceMultiplier: {...model.priceMultiplier, input: parseInt(ev.currentTarget.value || "0")}})} /></label>
        <label>Output multiplier<Input type="number" value={model.priceMultiplier.output} onChange={ev => setModel({...model, priceMultiplier: {...model.priceMultiplier, output: parseInt(ev.currentTarget.value || "0")}})} /></label>
        <label>Temperature<Input type="number" step="0.1" min="0" max="2" value={model.chatSettings.temperature} onChange={ev => setModel({...model, chatSettings: {...model.chatSettings, temperature: parseFloat(ev.currentTarget.value || "0")}})} /></label>
        <label>Top P<Input type="number" step="0.1" min="0" max="1" value={model.chatSettings.topP} onChange={ev => setModel({...model, chatSettings: {...model.chatSettings, topP: parseFloat(ev.currentTarget.value || "0")}})} /></label>
        <label>Max completion tokens<Input type="number" min="1" value={model.chatSettings.maxCompletionTokens} onChange={ev => setModel({...model, chatSettings: {...model.chatSettings, maxCompletionTokens: parseInt(ev.currentTarget.value || "0")}})} /></label>
        <label>System prompt<Input value={model.chatSettings.systemPrompt ?? ""} placeholder="Use global default" onChange={ev => setModel({...model, chatSettings: {...model.chatSettings, systemPrompt: ev.currentTarget.value.trim() === "" ? undefined : ev.currentTarget.value}})} /></label>
        <Box>
            <Text fontWeight={600}>Capabilities</Text>
            <Flex gap="12px" flexWrap="wrap" mt={8}>{capabilities.map(capability => <label key={capability} style={{display: "flex", gap: 6, alignItems: "center"}}><input type="checkbox" checked={model.capabilities.includes(capability)} onChange={ev => setModel({...model, capabilities: ev.currentTarget.checked ? [...model.capabilities, capability] : model.capabilities.filter(it => it !== capability)})} />{capability}</label>)}</Flex>
        </Box>
    </Box>;
}

function RepeatableStrings(props: {title: string; values: string[]; placeholder: string; setValues: (values: string[]) => void;}): React.ReactNode {
    return <Box style={{display: "grid", gap: 8}}>
        <Flex alignItems="center" gap="8px"><h3 className="title" style={{margin: 0}}>{props.title}</h3><Button type="button" m={0} onClick={() => props.setValues([...props.values, ""])}>Add</Button></Flex>
        {props.values.length === 0 ? <Text color="textSecondary">No {props.title.toLowerCase()} added.</Text> : null}
        {props.values.map((value, idx) => <Flex key={idx} gap="8px" alignItems="center"><TextArea value={value} placeholder={props.placeholder} rows={3} onChange={ev => props.setValues(props.values.map((it, itIdx) => itIdx === idx ? ev.currentTarget.value : it))} /><Button type="button" color="errorMain" m={0} onClick={() => props.setValues(props.values.filter((_, itIdx) => itIdx !== idx))}>Remove</Button></Flex>)}
    </Box>;
}

function KeyStatsEditor(props: {stats: NonNullable<NonNullable<NonNullable<InferenceModel["page"]>["about"]>["keyStats"]>; setStats: (stats: NonNullable<NonNullable<NonNullable<InferenceModel["page"]>["about"]>["keyStats"]>) => void;}): React.ReactNode {
    return <Box style={{display: "grid", gap: 8}}>
        <Flex alignItems="center" gap="8px"><h3 className="title" style={{margin: 0}}>Key stats</h3><Button type="button" m={0} onClick={() => props.setStats([...props.stats, {label: "", value: "", description: ""}])}>Add</Button></Flex>
        {props.stats.length === 0 ? <Text color="textSecondary">If left empty, the model page shows context length, input multiplier and output multiplier.</Text> : null}
        {props.stats.map((stat, idx) => <div key={idx} style={{display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(120px, 1fr)) auto", gap: 8, alignItems: "end"}}><label>Label<Input value={stat.label} onChange={ev => props.setStats(props.stats.map((it, itIdx) => itIdx === idx ? {...it, label: ev.currentTarget.value} : it))} /></label><label>Value<Input value={stat.value} onChange={ev => props.setStats(props.stats.map((it, itIdx) => itIdx === idx ? {...it, value: ev.currentTarget.value} : it))} /></label><label>Description<Input value={stat.description ?? ""} onChange={ev => props.setStats(props.stats.map((it, itIdx) => itIdx === idx ? {...it, description: ev.currentTarget.value} : it))} /></label><Button type="button" color="errorMain" m={0} onClick={() => props.setStats(props.stats.filter((_, itIdx) => itIdx !== idx))}>Remove</Button></div>)}
    </Box>;
}

function BenchmarkScoresEditor(props: {benchmarks: InferenceBenchmark[]; scores: Record<string, string>; setScores: (scores: Record<string, string>) => void;}): React.ReactNode {
    if (props.benchmarks.length === 0) return <Text color="textSecondary">Create benchmark definitions below before adding model scores.</Text>;
    return <Box style={{display: "grid", gap: 8}}><h3 className="title" style={{margin: 0}}>Benchmark scores</h3><div style={{display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 12}}>{props.benchmarks.map(benchmark => <label key={benchmark.id}>{benchmark.title}<Input value={props.scores[benchmark.id] ?? ""} placeholder="No score" onChange={ev => { const scores = {...props.scores}; const value = ev.currentTarget.value; if (value.trim() === "") delete scores[benchmark.id]; else scores[benchmark.id] = value; props.setScores(scores); }} /></label>)}</div></Box>;
}

function BenchmarkDefinitionsEditor(props: {benchmarks: InferenceBenchmark[]; setBenchmarks: (benchmarks: InferenceBenchmark[]) => void; models: InferenceModel[];}): React.ReactNode {
    return <Box mt={12} style={{display: "grid", gap: 12}}>
        <Flex justifyContent="end"><Button type="button" m={0} onClick={() => props.setBenchmarks([...props.benchmarks, {id: "", title: "", description: "", higherIsBetter: true, modelNames: []}])}>Add benchmark</Button></Flex>
        {props.benchmarks.length === 0 ? <Text color="textSecondary">No benchmark definitions added.</Text> : null}
        {props.benchmarks.map((benchmark, idx) => <Card key={idx} p="16px"><div style={{display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 12}}><label>ID<Input value={benchmark.id} onChange={ev => updateBenchmark(props, idx, {...benchmark, id: ev.currentTarget.value})} /></label><label>Title<Input value={benchmark.title} onChange={ev => updateBenchmark(props, idx, {...benchmark, title: ev.currentTarget.value})} /></label><label>Higher score is better<Select value={benchmark.higherIsBetter ? "true" : "false"} onChange={ev => updateBenchmark(props, idx, {...benchmark, higherIsBetter: ev.currentTarget.value === "true"})} style={{width: "100%", height: 40}}><option value="true">Yes</option><option value="false">No</option></Select></label></div><label>Description<Input value={benchmark.description ?? ""} onChange={ev => updateBenchmark(props, idx, {...benchmark, description: ev.currentTarget.value})} /></label><label>Comparison models<Input value={benchmark.modelNames.join(", ")} placeholder={props.models.map(model => model.name).slice(0, 2).join(", ")} onChange={ev => updateBenchmark(props, idx, {...benchmark, modelNames: parseCommaList(ev.currentTarget.value)})} /></label><Flex justifyContent="end" mt={12}><Button type="button" color="errorMain" m={0} onClick={() => props.setBenchmarks(props.benchmarks.filter((_, itIdx) => itIdx !== idx))}>Remove</Button></Flex></Card>)}
    </Box>;
}

function updateBenchmark(props: {benchmarks: InferenceBenchmark[]; setBenchmarks: (benchmarks: InferenceBenchmark[]) => void}, idx: number, benchmark: InferenceBenchmark) {
    props.setBenchmarks(props.benchmarks.map((it, itIdx) => itIdx === idx ? benchmark : it));
}

function parseCommaList(value: string): string[] {
    return value.split(",").map(it => it.trim()).filter(it => it !== "");
}

function dateInputValue(timestamp: number | undefined): string {
    if (!timestamp) return "";
    const date = new Date(timestamp);
    if (Number.isNaN(date.getTime())) return "";
    return date.toISOString().slice(0, 10);
}

function timestampFromDateInput(value: string): number | undefined {
    if (value === "") return undefined;
    const timestamp = new Date(`${value}T00:00:00.000Z`).getTime();
    return Number.isNaN(timestamp) ? undefined : timestamp;
}
