import * as React from "react";

import {callAPI} from "@/Authentication/DataHook";
import {MainContainer} from "@/ui-components/MainContainer";
import {Box, Button, Card, Flex, Input, Link, Select, Text, TextArea} from "@/ui-components";
import AppRoutes from "@/Routes";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {useProjectId} from "@/Project/Api";
import {InferenceBenchmark, InferenceCapability, InferenceModel, listModels, updateBenchmarks, updateModel} from "./api";
import Table, {TableHeader, TableRow} from "@/ui-components/Table";
import * as Heading from "@/ui-components/Heading";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import ConfiguringTools from "./ConfiguringTools";

const capabilities: InferenceCapability[] = ["TextGeneration", "TextToImage", "SpeechToText"];

export default function Models(): React.ReactNode {
    const projectId = useProjectId();
    const [models, setModels] = React.useState<InferenceModel[]>([]);
    const [benchmarks, setBenchmarks] = React.useState<InferenceBenchmark[]>([]);
    const [isAdmin, setIsAdmin] = React.useState(false);
    const [providerId, setProviderId] = React.useState("");
    const [server, setServer] = React.useState("");
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
                setProviderId(resp.providerId ?? "");
                setServer(resp.server ?? "");
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

    return <MainContainer main={<Box style={{display: "flex", flexDirection: "column", gap: 20, paddingBottom: 32}}>
        <Flex mb={8} style={{gap: 12, alignItems: "center", flexWrap: "wrap"}}>
            <h3 className="title" style={{margin: 0}}>AI Inference: Models</h3>
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
                    <th>Title model</th>
                    <th>Cached</th>
                    <th>Input</th>
                    <th>Output</th>
                    <th>Capabilities</th>
                    {!isAdmin ? null : <th/>}
                </TableRow>
                </TableHeader>
                <tbody>
                {models.map(model => <TableRow key={model.name}>
                    <td><Link to={AppRoutes.inference.model(model.name)}>{model.title}</Link></td>
                    <td>{model.name}</td>
                    <td>{model.titleModelName || model.name}</td>
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

        {!isAdmin ? null : <Card>
            <Heading.h3 mb={16}>Benchmark definitions</Heading.h3>
            <Text color="textSecondary">
                Curated benchmark sections are global. Each definition chooses the benchmark id, display title, sort direction, and comparison model names. Scores are read from each model page metadata under <code>benchmarkScores</code>.
            </Text>
            <BenchmarkDefinitionsEditor benchmarks={benchmarks} setBenchmarks={setBenchmarks} models={models} />
            <Flex justifyContent="end" mt={12}>
                <Button color="successMain" type="button" onClick={saveBenchmarks} disabled={savingBenchmarks}>{savingBenchmarks ? "Saving..." : "Save benchmarks"}</Button>
            </Flex>
        </Card>}

        <Card>
            <ConfiguringTools title="Configuring tools" providerId={providerId} server={server} models={models} />
        </Card>


    </Box>} />;
}

function formatMultiplier(value: number): string {
    if (value === 0) return "N/A";
    if (value % 1000 === 0) return `${value / 1000}x`;
    return `${value / 1000}x`;
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
