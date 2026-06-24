import * as React from "react";

import {callAPI} from "@/Authentication/DataHook";
import {MainContainer} from "@/ui-components/MainContainer";
import {Box, Button, Card, Flex, Input, Link, Select, Text} from "@/ui-components";
import AppRoutes from "@/Routes";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {useProjectId} from "@/Project/Api";
import {InferenceCapability, InferenceModel, listModels, updateModel} from "./api";
import Table, {TableHeader, TableRow} from "@/ui-components/Table";
import * as Heading from "@/ui-components/Heading";

const capabilities: InferenceCapability[] = ["TextGeneration", "TextToImage", "SpeechToText"];

export default function Models(): React.ReactNode {
    const projectId = useProjectId();
    const [models, setModels] = React.useState<InferenceModel[]>([]);
    const [isAdmin, setIsAdmin] = React.useState(false);
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState("");
    const [editing, setEditing] = React.useState<InferenceModel | null>(null);
    const [editOriginalName, setEditOriginalName] = React.useState("");
    const [saving, setSaving] = React.useState(false);

    const refresh = React.useCallback(() => {
        setLoading(true);
        setError("");
        void callAPI(listModels({providerId: null}))
            .then(resp => {
                setModels(resp.models ?? []);
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
        setEditing(JSON.parse(JSON.stringify(model)));
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
    </Box>} />;
}

function formatMultiplier(value: number): string {
    if (value === 0) return "N/A";
    if (value % 1000 === 0) return `${value / 1000}x`;
    return `${value / 1000}x`;
}
