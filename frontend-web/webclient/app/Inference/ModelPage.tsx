import * as React from "react";
import {useSearchParams} from "react-router-dom";

import {callAPI} from "@/Authentication/DataHook";
import {IconButton} from "@/ui-components/IconButton";
import AppRoutes from "@/Routes";
import {Box, Button, ExternalLink, Flex, Link, Text} from "@/ui-components";
import {MainContainer, MAIN_CONTAINER_MAX_WIDTH} from "@/ui-components/MainContainer";
import Table, {TableHeader, TableRow} from "@/ui-components/Table";
import {copyToClipboard} from "@/UtilityFunctions";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {InferenceBenchmark, InferenceModel, listModels} from "./api";
import ConfiguringTools from "./ConfiguringTools";
import ModelInferenceLogo from "./ModelLogo";
import ReactMarkdown from "react-markdown";
import CodeSnippet from "@/ui-components/CodeSnippet";
import remarkGfm from "remark-gfm";
import {MarkdownTable} from "@/ui-components/Markdown";
import {CopyButton} from "@/ui-components/CopyButton";
import {injectStyle} from "@/Unstyled";
import {useIsLightThemeStored} from "@/ui-components/theme";
import {formatDate} from "date-fns";

const fallbackDocs = "https://docs.cloud.sdu.dk";

export default function ModelPage(): React.ReactNode {
    const [params] = useSearchParams();
    const modelName = params.get("name") ?? "";
    const [models, setModels] = React.useState<InferenceModel[]>([]);
    const [benchmarks, setBenchmarks] = React.useState<InferenceBenchmark[]>([]);
    const [providerId, setProviderId] = React.useState("");
    const [server, setServer] = React.useState("");
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState("");

    const model = models.find(it => it.name === modelName);
    usePage(model?.title ?? "Inference model", SidebarTabId.INFERENCE);

    React.useEffect(() => {
        setLoading(true);
        setError("");
        void callAPI(listModels({providerId: null}))
            .then(resp => {
                setModels(resp.models ?? []);
                setBenchmarks(resp.benchmarks ?? []);
                setProviderId(resp.providerId ?? "");
                setServer(resp.server ?? "");
                setLoading(false);
            })
            .catch(err => {
                setError(err instanceof Error ? err.message : "Failed to load inference model");
                setLoading(false);
            });
    }, [modelName]);

    if (model) {
        return <ModelPageContent model={model} models={models} benchmarks={benchmarks} providerId={providerId} server={server} />;
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
    }

    ${k} .model-hero-inner {
        align-items: center;
        display: flex;
        flex-wrap: wrap;
        gap: 32px;
        justify-content: space-between;
        margin: 0 auto;
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
     
    html.dark ${k} {
        --model-hero: var(--blue-80);
        --model-hero-border: var(--blue-90);
    }
`);

function ModelPageContent({model, models, benchmarks, providerId, server}: {model: InferenceModel; models: InferenceModel[]; benchmarks: InferenceBenchmark[]; providerId: string; server: string}): React.ReactNode {
    const page = model.page;
    const documentationUrl = page?.documentationUrl || fallbackDocs;
    const shortDescription = page?.shortDescription || `${model.title} is available through the UCloud OpenAI-compatible inference endpoint.`;
    const keyStats = page?.about?.keyStats?.length ? page.about.keyStats : fallbackKeyStats(model);
    const lightTheme = useIsLightThemeStored();

    const docButtonColor = lightTheme ? "primaryMain" : "secondaryMain";

    return <div className={PageStyle}>
        <Box className="model-hero">
            <Flex className="model-hero-inner">
                <Box className="model-hero-copy">
                    <h2 className="title model-hero-title">{model.title}</h2>
                    <p className="model-hero-description">{shortDescription}</p>
                    <Flex gap="12px" flexWrap="wrap">
                        <Link to={AppRoutes.inference.playground(model.name)}><Button type="button" color="successMain">Try now</Button></Link>
                        <ExternalLink href={documentationUrl}><Button type="button" color={docButtonColor}>Documentation</Button></ExternalLink>
                    </Flex>
                </Box>
                <ModelInferenceLogo modelName={model.name} size={160} />
            </Flex>
        </Box>

        <MainContainer main={<Box style={{display: "grid", gridTemplateColumns: "minmax(0, 1fr) minmax(260px, 360px)", gap: 36, paddingBottom: 32}}>
            <Box style={{display: "grid", gap: 34}}>
                <Section title="About model">
                    {page?.about?.description ? <Markdown text={page.about.description} /> : <Text>{shortDescription}</Text>}
                </Section>
                <Box style={{display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 16}}>
                    {keyStats.map((stat, idx) => <Box key={idx} style={{borderTop: "2px solid var(--primaryMain)"}} my={22} pt={12}>
                        <Text color="textSecondary">{stat.label}</Text>
                        <Text fontSize="24px" fontWeight={700}>{stat.value}</Text>
                        {stat.description ? <Text color="textSecondary">{stat.description}</Text> : null}
                    </Box>)}
                </Box>
                {page?.about?.highlights?.length ? <>
                    <Section title={"Highlights"}>
                        <ul style={{margin: "0", paddingLeft: "20px"}}>{page.about.highlights.map((item, idx) => <li key={idx}><Markdown text={item} /></li>)}</ul>
                    </Section>
                </> : null}

                <BenchmarkSection model={model} models={models} benchmarks={benchmarks} />

                <Section>
                    <ConfiguringTools title="API usage" providerId={providerId} server={server} models={models} modelId={model.name} />
                </Section>
            </Box>

            <Section>
                <Datasheet model={model} />
                <Flex gap="12px" flexWrap="wrap" flexDirection={"column"}>
                    <Link to={AppRoutes.inference.playground(model.name)}>
                        <Button type="button" color="successMain" width={"100%"}>Try now</Button>
                    </Link>
                    <ExternalLink href={documentationUrl}>
                        <Button type="button" width={"100%"}>Documentation</Button>
                    </ExternalLink>
                </Flex>
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

function Markdown({text}: { text: string }): React.ReactNode {
    if (text.trim() === "") return null;
    return (
        <ReactMarkdown
            components={{
                a: (p) => <ExternalLink href={p.href}>{p.children}</ExternalLink>,
                pre: (p) => <Box my={16}><CodeSnippet children={p.children} maxHeight=""/></Box>,
                table: p => <MarkdownTable>{p.children}</MarkdownTable>,
            }}
            allowedElements={[
                "h1",
                "h2",
                "h3",
                "h4",
                "h5",
                "h6",
                "br",
                "a",
                "p",
                "strong",
                "b",
                "i",
                "em",
                "ul",
                "ol",
                "li",
                "pre",
                "code",
                "table",
                "th",
                "tbody",
                "thead",
                "td",
                "tr",
            ]}
            children={text}
            remarkPlugins={[remarkGfm]}
        />
    );
}
