import * as React from "react";

import {callAPI} from "@/Authentication/DataHook";
import {MainContainer} from "@/ui-components/MainContainer";
import {Box, Button, Card, Flex, Icon, Input, Link, Select, Text, TextArea} from "@/ui-components";
import AppRoutes from "@/Routes";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {useProjectId} from "@/Project/Api";
import {InferenceBenchmark, InferenceCapability, InferenceModel, listModels, updateBenchmarks, updateModel} from "./api";
import * as Heading from "@/ui-components/Heading";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {IconButton} from "@/ui-components/IconButton";
import ModelInferenceLogo from "@/Inference/ModelLogo";
import {injectStyle} from "@/Unstyled";
import {SingleLineMarkdown} from "@/ui-components/Markdown";
import HeroSvg from "@/ui-components/icons/logo_esc.svg";

const capabilities: InferenceCapability[] = ["TextGeneration", "TextToImage", "SpeechToText"];

const pageStyle = injectStyle("inference-models-page", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 0;
        margin-bottom: -16px;
        padding-bottom: 0;
    }

    ${k} .panel {
        box-sizing: border-box;
        margin-left: calc(50% - 50vw + (var(--sidebarBlockWidth, 0px) / 2));
        margin-right: calc(50% - 50vw + (var(--sidebarBlockWidth, 0px) / 2));
        padding: 48px 16px;
    }

    ${k} .panel-inner {
        box-sizing: border-box;
        margin: 0 auto;
        max-width: 1400px;
        padding: 0 16px;
    }

    ${k} .panel-muted {
        background: var(--backgroundCard);
        border-top: 5px solid rgba(148, 163, 184, 0.18);
    }
    
    ${k} .panel-solid,
    ${k} .panel-accent {
        padding: 150px 16px;
    }

    ${k} .panel-solid {
        background: var(--backgroundDefault);
        border-top: 5px solid rgba(148, 163, 184, 0.18);
    }

    ${k} .panel-accent {
        background: var(--blue-10);
        border-top: 5px solid var(--blue-20);
    }

    html.dark ${k} .panel-accent {
        background: var(--blue-80);
        border-bottom-color: var(--blue-90);
        border-top-color: var(--blue-90);
    }

    ${k} .hero {
        position: relative;
        overflow: hidden;
        background: linear-gradient(135deg, #151927 0%, #1b2336 58%, #111827 100%);
        color: white;
        margin-top: -16px;
        padding-bottom: 68px;
        padding-top: 24px;
        height: 435px;
    }

    ${k} .hero-top {
        display: flex;
        justify-content: flex-end;
        margin-bottom: 44px;
        position: relative;
        z-index: 2;
    }

    ${k} .hero-icon {
        bottom: -120px;
        filter: grayscale(1) saturate(0.50);
        height: min(42vw, 520px);
        mask-image: linear-gradient(135deg, rgba(0, 0, 0, 0.92) 0%, rgba(0, 0, 0, 0.62) 46%, rgba(0, 0, 0, 0) 86%);
        opacity: 0.22;
        pointer-events: none;
        position: absolute;
        right: 0;
        transform: rotate(-12deg);
        transform-origin: 58% 58%;
        width: min(42vw, 520px);
    }

    ${k} .hero-content {
        position: relative;
        z-index: 1;
        max-width: 1400px;
    }

    ${k} .hero-copy {
        max-width: 720px;
    }

    ${k} .eyebrow {
        color: rgba(255, 255, 255, 0.68);
        font-size: 13px;
        font-weight: 700;
        letter-spacing: 0.12em;
        margin: 0 0 14px;
        text-transform: uppercase;
    }

    ${k} .hero h1 {
        font-size: clamp(32px, 5vw, 52px);
        line-height: 1;
        margin: 0;
    }

    ${k} .hero p {
        color: rgba(255, 255, 255, 0.78);
        font-size: 16px;
        line-height: 1.6;
        margin: 22px 0 0;
        max-width: 660px;
    }

    ${k} .hero-actions,
    ${k} .cta-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        margin-top: 28px;
    }

    ${k} .button-link {
        text-decoration: none;
    }

    ${k} .section-heading {
        align-items: flex-end;
        display: flex;
        gap: 18px;
        justify-content: space-between;
        margin-bottom: 16px;
    }

    ${k} .section-heading > div {
        display: grid;
        gap: 10px;
    }

    ${k} .section-heading h2 {
        color: var(--textPrimary);
        font-size: 26px;
        line-height: 1.08;
        margin: 0;
    }

    ${k} .section-heading p,
    ${k} .muted {
        color: var(--textSecondary);
        font-size: 14px;
        line-height: 1.55;
        margin: 0;
    }

    ${k} .model-grid {
        display: grid;
        gap: 14px;
        grid-template-columns: repeat(auto-fill, minmax(250px, 300px));
        justify-content: start;
    }

    ${k} .model-card {
        background: var(--backgroundCard);
        border: 1px solid rgba(148, 163, 184, 0.28);
        border-radius: 18px;
        color: var(--textPrimary);
        display: flex;
        flex-direction: column;
        gap: 14px;
        min-height: 250px;
        padding: 16px;
        position: relative;
        text-decoration: none;
    }

    ${k} .model-card:hover {
        border-color: var(--primaryMain);
        color: var(--textPrimary);
        text-decoration: none;
        transform: translateY(-1px);
    }

    html.dark ${k} .model-card {
        background: var(--backgroundCard);
        border-color: rgba(148, 163, 184, 0.18);
    }

    ${k} .model-card-header {
        align-items: flex-start;
        display: flex;
        gap: 14px;
        justify-content: space-between;
    }

    ${k} .capability {
        color: var(--textSecondary);
        font-size: 11px;
        margin: 0 0 8px;
    }

    ${k} .model-title {
        color: var(--textPrimary);
        display: inline-block;
        font-size: 18px;
        line-height: 1.12;
        text-decoration: none;
    }

    ${k} .model-specs {
        display: grid;
        gap: 8px;
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    ${k} .model-spec {
        padding-top: 8px;
    }

    ${k} .model-spec-label {
        color: var(--textSecondary);
        font-size: 10px;
        line-height: 1.2;
        margin: 0 0 4px;
    }

    ${k} .model-spec-value {
        color: var(--textPrimary);
        font-size: 13px;
        line-height: 1.25;
        margin: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .metric-grid {
        display: grid;
        gap: 8px;
        grid-template-columns: repeat(3, minmax(0, 1fr));
    }
    
    ${k} .model-spec-section {
        border-top: 1px solid var(--borderColor);
        padding-top: 16px;
        margin-top: auto;
        display: flex;
        flex-direction: column;
        gap: 16px;
    }

    ${k} .metric {
        padding: 0;
    }

    ${k} .metric-label {
        color: var(--textSecondary);
        font-size: 10px;
        font-weight: 700;
        line-height: 1.2;
        margin: 0 0 7px;
    }

    ${k} .metric-value {
        color: var(--textPrimary);
        font-size: 16px;
        margin: 0;
    }

    ${k} .model-card-footer {
        display: flex;
        justify-content: flex-end;
        min-height: 32px;
    }

    ${k} .consume-grid {
        display: grid;
        gap: 16px;
        margin-top: 32px;
        grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    ${k} .consume-card,
    ${k} .cta {
        padding: 0;
    }

    ${k} .consume-card {
        border-left: 2px solid var(--primaryMain);
        padding-left: 18px;
    }

    ${k} .consume-card h3,
    ${k} .cta h2 {
        color: var(--textPrimary);
        margin: 0;
    }

    ${k} .consume-card p,
    ${k} .cta p {
        color: var(--textSecondary);
        line-height: 1.6;
        margin: 12px 0 0;
    }

    ${k} .consume-number {
        align-items: center;
        background: var(--primaryMain);
        border-radius: 14px;
        color: white;
        display: flex;
        font-weight: 700;
        height: 38px;
        justify-content: center;
        margin-bottom: 18px;
        width: 38px;
    }

    ${k} .cta {
        align-items: flex-start;
        display: flex;
        flex-direction: column;
        gap: 24px;
        min-height: 220px;
        padding: 0 16px;
    }

    ${k} .cta h2 {
        font-size: clamp(30px, 4vw, 46px);
        line-height: 1;
    }

    ${k} .cta p {
        font-size: 17px;
        max-width: 680px;
    }

    @media (max-width: 900px) {
        ${k} .hero-icon {
            display: none;
        }

        ${k} .consume-grid,
        ${k} .cta {
            grid-template-columns: 1fr;
        }

        ${k} .consume-grid {
            display: grid;
        }

        ${k} .cta {
            align-items: flex-start;
            flex-direction: column;
        }
    }

    @media (max-width: 620px) {
        ${k} .section-heading {
            align-items: flex-start;
            flex-direction: column;
        }

        ${k} .model-grid,
        ${k} .consume-grid {
            grid-template-columns: 1fr;
        }

        ${k} .model-card {
            min-width: 0;
        }

        ${k} .metric-grid {
            gap: 6px;
            grid-template-columns: repeat(3, minmax(0, 1fr));
        }

        ${k} .metric-value {
            font-size: 14px;
        }
    }
`);

export default function Models(): React.ReactNode {
    const projectId = useProjectId();
    const [models, setModels] = React.useState<InferenceModel[]>([]);
    const [benchmarks, setBenchmarks] = React.useState<InferenceBenchmark[]>([]);
    const [isAdmin, setIsAdmin] = React.useState(false);
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState("");
    const [editing, setEditing] = React.useState<InferenceModel | null>(null);
    const [editOriginalName, setEditOriginalName] = React.useState("");
    const [saving, setSaving] = React.useState(false);
    const [savingBenchmarks, setSavingBenchmarks] = React.useState(false);

    usePage("Inference models", SidebarTabId.INFERENCE);

    const refresh = React.useCallback(() => {
        setLoading(true);
        setError("");
        void callAPI(listModels({providerId: null}))
            .then(resp => {
                setModels(resp.models ?? []);
                setBenchmarks(resp.benchmarks ?? []);
                setIsAdmin(resp.isAdmin);
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
            titleModelName: model.titleModelName || model.name,
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

    let effectiveIsAdmin = isAdmin;
    if (true) effectiveIsAdmin = false;

    return <MainContainer main={<Box className={pageStyle}>
        {error === "" ? null : <Text color="errorMain">{error}</Text>}
        {loading ? <Text>Loading inference models...</Text> : null}

        <section className="panel hero">
            <div className="panel-inner hero-content">
                <div className="hero-top">
                    <ProjectSwitcher />
                </div>
                <div className="hero-copy">
                    <p className="eyebrow">AI Inference</p>
                    <h1>Production-ready models for research and automation.</h1>
                    <p>Browse a growing catalog of hosted models for text generation. Use them interactively from the playground, through jobs, or through OpenAI-compatible endpoints.</p>
                    <div className="hero-actions">
                        <Link className="button-link" to={AppRoutes.inference.playground()}><Button type="button">Open playground</Button></Link>
                        <Button type="button" color="secondaryMain" onClick={() => document.getElementById("model-catalog")?.scrollIntoView({behavior: "smooth"})}>Browse models</Button>
                    </div>
                </div>
                <img className="hero-icon" src={HeroSvg} alt="" aria-hidden="true" />
            </div>
        </section>

        <section id="model-catalog" className="panel panel-muted">
            <div className="panel-inner">
                <div className="section-heading">
                    <div>
                        <h2>Models</h2>
                        <p>Pick a model based on capability, context length and price multipliers. Details are available per model.</p>
                    </div>
                    {models.length === 0 && !loading ? <p className="muted">No inference models are available.</p> : null}
                </div>

                {models.length === 0 ? null : <div className="model-grid">
                    {models.map(model => <ModelCatalogCard key={model.name} model={model} isAdmin={effectiveIsAdmin} onEdit={() => startEdit(model)} />)}
                </div>}
            </div>
        </section>

        <section className="panel panel-solid">
            <div className="panel-inner">
                <div className="section-heading">
                    <div>
                        <h2>Consume models your way</h2>
                        <p>Start in the browser, automate through UCloud jobs, or connect existing tools to the compatible endpoint.</p>
                    </div>
                </div>
                <div className="consume-grid">
                    <div className="consume-card">
                        <div className="consume-number"><Icon name={"heroBeaker"} /></div>
                        <h3>Playground</h3>
                        <p>Try prompts, compare model behavior and iterate on ideas before wiring anything into production workflows.</p>
                    </div>
                    <div className="consume-card">
                        <div className="consume-number"><Icon name={"heroCpuChip"} /></div>
                        <h3>Jobs</h3>
                        <p>Use models from batch jobs and applications running on UCloud when inference needs to be part of a larger pipeline.</p>
                    </div>
                    <div className="consume-card">
                        <div className="consume-number"><Icon name={"heroGlobeEuropeAfrica"} /></div>
                        <h3>OpenAI-compatible endpoints</h3>
                        <p>Point existing clients at the endpoint and keep familiar request formats while running against UCloud-hosted models.</p>
                    </div>
                </div>
            </div>
        </section>

        <section className="panel panel-accent">
            <div className="panel-inner cta">
                <div>
                    <h2>Ready to build with hosted inference?</h2>
                    <p>Open the playground to test a model, or use the catalog to find details and integration settings.</p>
                </div>
                <div className="cta-actions">
                    <Link className="button-link" to={AppRoutes.inference.playground()}><Button type="button">Start in playground</Button></Link>
                </div>
            </div>
        </section>

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
                    Title generation model
                    <Select
                        value={editing.titleModelName || editing.name}
                        onChange={ev => setEditing({...editing, titleModelName: ev.currentTarget.value})}
                        style={{width: "100%", height: 40}}
                    >
                        {models.filter(model => model.capabilities.includes("TextGeneration")).map(model => <option key={model.name} value={model.name}>{model.title} ({model.name})</option>)}
                    </Select>
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

            <ModelPageMetadataEditor model={editing} benchmarks={benchmarks} setModel={setEditing} />
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

        {!effectiveIsAdmin ? null : <Card>
            <Heading.h3 mb={16}>Benchmark definitions</Heading.h3>
            <Text color="textSecondary">
                Curated benchmark sections are global. Each definition chooses the benchmark id, display title, sort direction, and comparison model names. Scores are read from each model page metadata under <code>benchmarkScores</code>.
            </Text>
            <BenchmarkDefinitionsEditor benchmarks={benchmarks} setBenchmarks={setBenchmarks} models={models} />
            <Flex justifyContent="end" mt={12}>
                <Button color="successMain" type="button" onClick={saveBenchmarks} disabled={savingBenchmarks}>{savingBenchmarks ? "Saving..." : "Save benchmarks"}</Button>
            </Flex>
        </Card>}
    </Box>} />;
}

function formatMultiplier(value: number): string {
    if (value === 0) return "N/A";
    if (value % 1000 === 0) return `${value / 1000}x`;
    return `${value / 1000}x`;
}

function primaryCapability(model: InferenceModel): InferenceCapability | string {
    const priority: InferenceCapability[] = ["SpeechToText", "TextGeneration", "TextToImage"];
    return priority.find(capability => model.capabilities.includes(capability)) ?? model.capabilities[0] ?? "Unknown";
}

function ModelCatalogCard(props: {model: InferenceModel; isAdmin: boolean; onEdit: () => void;}): React.ReactNode {
    const model = props.model;
    return <Link className="model-card" to={AppRoutes.inference.model(model.name)}>
        <div className="model-card-header">
            <div>
                <p className="capability">{primaryCapability(model)}</p>
                <span className="model-title">{model.title}</span>
            </div>
            <ModelInferenceLogo modelName={model.name} size={46} />
        </div>

        <SingleLineMarkdown width={"100%"}>{model.page?.shortDescription ?? ""}</SingleLineMarkdown>

        <div className={"model-spec-section"}>
            <div className="model-specs">
                <ModelMetric title="Context" value={model.contextWindow?.toString() ?? "-"} />
                <ModelMetric title="Parameters" value={model.page?.datasheet?.parameters ?? "-"} />
            </div>

            <div className="metric-grid">
                <ModelMultiplier title="Cached" value={model.priceMultiplier.cachedInput} />
                <ModelMultiplier title="Input" value={model.priceMultiplier.input} />
                <ModelMultiplier title="Output" value={model.priceMultiplier.output} />
            </div>
        </div>


        {!props.isAdmin ? null : <div className="model-card-footer">
            <IconButton tooltip={`Edit ${model.title}`} icon="heroPencil" onClick={props.onEdit} />
        </div>}
    </Link>;
}

function ModelMultiplier(props: {title: string; value: number;}): React.ReactNode {
    return <ModelMetric title={props.title} value={formatMultiplier(props.value)} />;
}

const ModelMetric: React.FunctionComponent<{ title: string; value: string; }> = props => {
    return <div className="metric">
        <p className="metric-label">{props.title}</p>
        <p className="metric-value">{props.value}</p>
    </div>;
}

function defaultModelPage(): NonNullable<InferenceModel["page"]> {
    return {
        shortDescription: "",
        documentationUrl: "",
        about: {
            description: "",
            highlights: [],
            keyStats: [],
        },
        benchmarkScores: {},
        datasheet: {
            parameters: "",
            activatedParameters: "",
            quantization: "",
        },
    };
}

function ModelPageMetadataEditor(props: {
    model: InferenceModel;
    benchmarks: InferenceBenchmark[];
    setModel: (model: InferenceModel) => void;
}): React.ReactNode {
    const page = props.model.page ?? defaultModelPage();
    const about = page.about ?? defaultModelPage().about!;
    const datasheet = page.datasheet ?? defaultModelPage().datasheet!;

    const updatePage = (next: NonNullable<InferenceModel["page"]>) => {
        props.setModel({...props.model, page: next});
    };
    const updateAbout = (next: NonNullable<NonNullable<InferenceModel["page"]>["about"]>) => updatePage({...page, about: next});
    const updateDatasheet = (next: NonNullable<NonNullable<InferenceModel["page"]>["datasheet"]>) => updatePage({...page, datasheet: next});

    return <Box mt={24} style={{display: "grid", gap: 18}}>
        <Heading.h4>Model page metadata</Heading.h4>
        <div style={{display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 12}}>
            <label>
                Short description
                <Input value={page.shortDescription ?? ""} onChange={ev => updatePage({...page, shortDescription: ev.currentTarget.value})} />
            </label>
            <label>
                Documentation URL
                <Input value={page.documentationUrl ?? ""} onChange={ev => updatePage({...page, documentationUrl: ev.currentTarget.value})} />
            </label>
            <label>
                Release date
                <Input type="date" value={dateInputValue(page.releaseDate)} onChange={ev => updatePage({...page, releaseDate: timestampFromDateInput(ev.currentTarget.value)})} />
            </label>
            <label>
                Parameters
                <Input value={datasheet.parameters ?? ""} onChange={ev => updateDatasheet({...datasheet, parameters: ev.currentTarget.value})} />
            </label>
            <label>
                Activated parameters
                <Input value={datasheet.activatedParameters ?? ""} onChange={ev => updateDatasheet({...datasheet, activatedParameters: ev.currentTarget.value})} />
            </label>
            <label>
                Quantization
                <Input value={datasheet.quantization ?? ""} onChange={ev => updateDatasheet({...datasheet, quantization: ev.currentTarget.value})} />
            </label>
        </div>

        <label>
            About description
            <TextArea
                style={{width: "100%", minHeight: 120}}
                value={about.description ?? ""}
                onChange={ev => updateAbout({...about, description: ev.currentTarget.value})}
            />
        </label>

        <RepeatableStrings
            title="Highlights"
            values={about.highlights ?? []}
            placeholder="Highlight shown as a bullet point"
            setValues={values => updateAbout({...about, highlights: values})}
        />

        <KeyStatsEditor
            stats={about.keyStats ?? []}
            setStats={stats => updateAbout({...about, keyStats: stats})}
        />

        <BenchmarkScoresEditor
            benchmarks={props.benchmarks}
            scores={page.benchmarkScores ?? {}}
            setScores={scores => updatePage({...page, benchmarkScores: scores})}
        />
    </Box>;
}

function RepeatableStrings(props: {
    title: string;
    values: string[];
    placeholder: string;
    setValues: (values: string[]) => void;
}): React.ReactNode {
    return <Box style={{display: "grid", gap: 8}}>
        <Flex alignItems="center" gap="8px">
            <Heading.h4 m={0}>{props.title}</Heading.h4>
            <Button type="button" m={0} onClick={() => props.setValues([...props.values, ""])}>Add</Button>
        </Flex>
        {props.values.length === 0 ? <Text color="textSecondary">No {props.title.toLowerCase()} added.</Text> : null}
        {props.values.map((value, idx) => <Flex key={idx} gap="8px" alignItems="center">
            <TextArea
                value={value}
                placeholder={props.placeholder}
                rows={3}
                onChange={ev => props.setValues(props.values.map((it, itIdx) => itIdx === idx ? ev.currentTarget.value : it))}
            />
            <Button type="button" color="errorMain" m={0} onClick={() => props.setValues(props.values.filter((_, itIdx) => itIdx !== idx))}>Remove</Button>
        </Flex>)}
    </Box>;
}

function KeyStatsEditor(props: {
    stats: NonNullable<NonNullable<NonNullable<InferenceModel["page"]>["about"]>["keyStats"]>;
    setStats: (stats: NonNullable<NonNullable<NonNullable<InferenceModel["page"]>["about"]>["keyStats"]>) => void;
}): React.ReactNode {
    return <Box style={{display: "grid", gap: 8}}>
        <Flex alignItems="center" gap="8px">
            <Heading.h4 m={0}>Key stats</Heading.h4>
            <Button type="button" m={0} onClick={() => props.setStats([...props.stats, {label: "", value: "", description: ""}])}>Add</Button>
        </Flex>
        {props.stats.length === 0 ? <Text color="textSecondary">If left empty, the model page shows context length, input multiplier and output multiplier.</Text> : null}
        {props.stats.map((stat, idx) => <div key={idx} style={{display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr)) auto", gap: 8, alignItems: "end"}}>
            <label>
                Label
                <Input value={stat.label} onChange={ev => props.setStats(props.stats.map((it, itIdx) => itIdx === idx ? {...it, label: ev.currentTarget.value} : it))} />
            </label>
            <label>
                Value
                <Input value={stat.value} onChange={ev => props.setStats(props.stats.map((it, itIdx) => itIdx === idx ? {...it, value: ev.currentTarget.value} : it))} />
            </label>
            <label>
                Description
                <Input value={stat.description ?? ""} onChange={ev => props.setStats(props.stats.map((it, itIdx) => itIdx === idx ? {...it, description: ev.currentTarget.value} : it))} />
            </label>
            <Button type="button" color="errorMain" m={0} onClick={() => props.setStats(props.stats.filter((_, itIdx) => itIdx !== idx))}>Remove</Button>
        </div>)}
    </Box>;
}

function BenchmarkScoresEditor(props: {
    benchmarks: InferenceBenchmark[];
    scores: Record<string, string>;
    setScores: (scores: Record<string, string>) => void;
}): React.ReactNode {
    if (props.benchmarks.length === 0) {
        return <Text color="textSecondary">Create benchmark definitions below before adding model scores.</Text>;
    }

    return <Box style={{display: "grid", gap: 8}}>
        <Heading.h4 m={0}>Benchmark scores</Heading.h4>
        <div style={{display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 12}}>
            {props.benchmarks.map(benchmark => <label key={benchmark.id}>
                {benchmark.title}
                <Input
                    value={props.scores[benchmark.id] ?? ""}
                    placeholder="No score"
                    onChange={ev => {
                        const scores = {...props.scores};
                        const value = ev.currentTarget.value;
                        if (value.trim() === "") {
                            delete scores[benchmark.id];
                        } else {
                            scores[benchmark.id] = value;
                        }
                        props.setScores(scores);
                    }}
                />
            </label>)}
        </div>
    </Box>;
}

function BenchmarkDefinitionsEditor(props: {
    benchmarks: InferenceBenchmark[];
    setBenchmarks: (benchmarks: InferenceBenchmark[]) => void;
    models: InferenceModel[];
}): React.ReactNode {
    return <Box mt={12} style={{display: "grid", gap: 12}}>
        <Flex justifyContent="end">
            <Button type="button" m={0} onClick={() => props.setBenchmarks([...props.benchmarks, {id: "", title: "", description: "", higherIsBetter: true, modelNames: []}])}>Add benchmark</Button>
        </Flex>
        {props.benchmarks.length === 0 ? <Text color="textSecondary">No benchmark definitions added.</Text> : null}
        {props.benchmarks.map((benchmark, idx) => <Card key={idx} p="16px">
            <div style={{display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 12}}>
                <label>
                    ID
                    <Input value={benchmark.id} onChange={ev => updateBenchmark(props, idx, {...benchmark, id: ev.currentTarget.value})} />
                </label>
                <label>
                    Title
                    <Input value={benchmark.title} onChange={ev => updateBenchmark(props, idx, {...benchmark, title: ev.currentTarget.value})} />
                </label>
                <label>
                    Higher score is better
                    <Select
                        value={benchmark.higherIsBetter ? "true" : "false"}
                        onChange={ev => updateBenchmark(props, idx, {...benchmark, higherIsBetter: ev.currentTarget.value === "true"})}
                        style={{width: "100%", height: 40}}
                    >
                        <option value="true">Yes</option>
                        <option value="false">No</option>
                    </Select>
                </label>
            </div>
            <label>
                Description
                <Input value={benchmark.description ?? ""} onChange={ev => updateBenchmark(props, idx, {...benchmark, description: ev.currentTarget.value})} />
            </label>
            <label>
                Comparison models
                <Input
                    value={benchmark.modelNames.join(", ")}
                    placeholder={props.models.map(model => model.name).slice(0, 2).join(", ")}
                    onChange={ev => updateBenchmark(props, idx, {...benchmark, modelNames: parseCommaList(ev.currentTarget.value)})}
                />
            </label>
            <Flex justifyContent="end" mt={12}>
                <Button type="button" color="errorMain" m={0} onClick={() => props.setBenchmarks(props.benchmarks.filter((_, itIdx) => itIdx !== idx))}>Remove</Button>
            </Flex>
        </Card>)}
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
