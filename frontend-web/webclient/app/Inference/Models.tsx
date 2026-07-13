import * as React from "react";

import {callAPI} from "@/Authentication/DataHook";
import {MainContainer} from "@/ui-components/MainContainer";
import {Box, Button, Flex, Icon, Input, Link, Text} from "@/ui-components";
import AppRoutes from "@/Routes";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {useProjectId} from "@/Project/Api";
import {InferenceCapability, InferenceModel, listModels} from "./api";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import ModelInferenceLogo, {modelProviderName} from "@/Inference/ModelLogo";
import {injectStyle} from "@/Unstyled";
import {SingleLineMarkdown} from "@/ui-components/Markdown";
// import HeroImage from "@/ui-components/icons/logo_esc.svg";
import HeroImage from "@/Assets/Images/inference/ucloud-ai-logo.png";
import {RichSelect} from "@/ui-components/RichSelect";
import {useIsLightThemeStored} from "@/ui-components/theme";

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
        box-sizing: border-box;
        color: white;
        margin-top: -16px;
        padding-bottom: 24px;
        padding-top: 24px;
        height: 435px;
    }

    ${k} .hero::before,
    ${k} .hero::after {
        content: "";
        inset: auto -12% -42% auto;
        opacity: 0;
        pointer-events: none;
        position: absolute;
        transition: opacity 420ms ease, transform 520ms ease;
        z-index: 0;
    }

    ${k} .hero::before {
        background: radial-gradient(circle, rgba(64, 147, 255, 0.24) 0%, rgba(91, 198, 255, 0.12) 34%, rgba(91, 198, 255, 0) 68%);
        filter: blur(10px);
        height: min(54vw, 620px);
        transform: translate3d(24px, 18px, 0) scale(0.92);
        width: min(54vw, 620px);
    }

    ${k} .hero::after {
        background: linear-gradient(110deg, rgba(255, 255, 255, 0) 8%, rgba(133, 211, 255, 0.12) 45%, rgba(255, 255, 255, 0) 74%);
        height: 100%;
        inset: 0;
        transform: translateX(-18%);
    }

    ${k} .hero:hover::before,
    ${k} .hero:focus-within::before {
        opacity: 1;
        transform: translate3d(0, 0, 0) scale(1);
    }

    ${k} .hero:hover::after,
    ${k} .hero:focus-within::after {
        opacity: 1;
        transform: translateX(0);
    }

    ${k} .hero-top {
        display: flex;
        justify-content: flex-end;
        margin-bottom: 0;
        position: absolute;
        right: 16px;
        top: 0;
        z-index: 2;
    }

    ${k} .hero-icon {
        bottom: -55px;
        filter: grayscale(0.0) saturate(0.70) drop-shadow(0 0 0 rgba(81, 161, 255, 0));
        max-height: min(42vw, 520px);
        mask-image: linear-gradient(135deg, rgba(0, 0, 0, 0.92) 0%, rgba(0, 0, 0, 0.62) 46%, rgba(0, 0, 0, 0) 86%);
        opacity: 0.60;
        pointer-events: none;
        position: absolute;
        right: 0;
        transition: filter 420ms ease, opacity 420ms ease, transform 520ms ease;
        max-width: min(42vw, 520px);
    }

    ${k} .hero:hover .hero-icon,
    ${k} .hero:focus-within .hero-icon {
        filter: grayscale(0.15) saturate(1.35) drop-shadow(0 0 30px rgba(81, 161, 255, 0.28));
        opacity: 0.80;
        transform: translate3d(-6px, -4px, 0) scale(1.015);
    }

    @media (prefers-reduced-motion: reduce) {
        ${k} .hero::before,
        ${k} .hero::after,
        ${k} .hero-icon {
            transition: none;
        }
    }

    ${k} .hero-content {
        align-items: center;
        display: flex;
        height: 100%;
        position: relative;
        z-index: 1;
        max-width: 1400px;
    }

    ${k} .hero-copy {
        max-width: 720px;
    }

    ${k} .hero p.eyebrow {
        color: rgba(255, 255, 255, 0.68);
        font-weight: 700;
        letter-spacing: 0.12em;
        margin: 0;
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

    ${k} .model-toolbar {
        align-items: flex-start;
        display: grid;
        gap: 18px;
        grid-template-columns: minmax(0, 1fr) minmax(260px, 360px);
        margin-bottom: 16px;
    }

    ${k} .catalog-left-controls,
    ${k} .catalog-search-and-providers {
        display: grid;
        gap: 10px;
    }

    ${k} .capability-filters {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
    }

    ${k} .catalog-filter-button {
        align-items: center;
        background: var(--backgroundDefault);
        border: 1px solid rgba(148, 163, 184, 0.28);
        border-radius: 8px;
        color: var(--textPrimary);
        cursor: pointer;
        display: inline-flex;
        font: inherit;
        gap: 8px;
        min-height: 34px;
        padding: 0 13px;
    }

    ${k} .catalog-filter-button:hover,
    ${k} .catalog-filter-button[data-active="true"] {
        border-color: var(--primaryMain);
    }

    ${k} .catalog-filter-button[data-active="true"] {
        background: var(--primaryMain);
        color: var(--primaryContrast);
    }

    ${k} .catalog-search-and-providers {
        justify-items: end;
        min-width: min(100%, 360px);
    }

    ${k} .model-count {
        color: var(--textSecondary);
        font-size: 13px;
        white-space: nowrap;
    }

    ${k} .catalog-search {
        width: 320px;
    }

    ${k} .provider-trigger {
        align-items: center;
        background: var(--backgroundDefault);
        border: 1px solid rgba(148, 163, 184, 0.34);
        border-radius: 5px;
        box-sizing: border-box;
        color: var(--textPrimary);
        cursor: pointer;
        display: flex;
        height: 36px;
        gap: 8px;
        max-width: 100%;
        padding: 0 10px;
        text-align: left;
        width: 320px;
    }

    ${k} .provider-trigger-label {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .model-results {
        max-height: calc(4 * 250px + 3 * 14px);
        overflow-y: auto;
        padding-right: 4px;
    }

    ${k} .no-results {
        color: var(--textSecondary);
        margin: 24px 0 0;
    }

    ${k} .model-grid {
        display: grid;
        gap: 14px;
        grid-template-columns: repeat(auto-fill, minmax(250px, 300px));
        padding: 5px 0;
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

        ${k} .model-toolbar,
        ${k} .catalog-search-and-providers {
            align-items: stretch;
            justify-items: stretch;
            grid-template-columns: 1fr;
        }

        ${k} .catalog-search,
        ${k} .provider-trigger,
        ${k} .catalog-search-and-providers {
            width: 100%;
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


const providerFilterOptionStyle = injectStyle("provider-filter-option", k => `
    ${k} {
        align-items: center;
        color: inherit;
        cursor: pointer;
        display: flex;
        gap: 10px;
        justify-content: flex-start;
        min-height: 42px;
        padding: 7px 10px;
    }

    ${k}[data-selected="true"] {
        background: var(--rowHover);
    }

    ${k} .provider-option-label {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
`);


export default function Models(): React.ReactNode {
    const projectId = useProjectId();
    const [models, setModels] = React.useState<InferenceModel[]>([]);
    const [loading, setLoading] = React.useState(true);
    const [error, setError] = React.useState("");
    const [capabilityFilter, setCapabilityFilter] = React.useState<InferenceCapability | "All">("All");
    const [search, setSearch] = React.useState("");
    const [providerFilters, setProviderFilters] = React.useState<string[]>([]);

    usePage("Inference models", SidebarTabId.INFERENCE);

    const refresh = React.useCallback(() => {
        setLoading(true);
        setError("");
        void callAPI(listModels({providerId: null}))
            .then(resp => {
                setModels(resp.models ?? []);
                setLoading(false);
            })
            .catch(err => {
                setError(err instanceof Error ? err.message : "Failed to load inference models");
                setLoading(false);
            });
    }, []);

    React.useEffect(() => refresh(), [refresh, projectId]);

    const providerOptions = React.useMemo(() => modelProviderOptions(models), [models]);
    const filteredModels = React.useMemo(() => {
        const query = search.trim().toLowerCase();
        return models.filter(model => {
            if (capabilityFilter !== "All" && !model.capabilities.includes(capabilityFilter)) return false;
            if (providerFilters.length > 0 && !providerFilters.includes(modelProviderName(model.name))) return false;
            if (query !== "") {
                const haystack = [model.title, model.name, model.page?.shortDescription, model.page?.datasheet?.parameters, modelProviderName(model.name)]
                    .filter(Boolean)
                    .join(" ")
                    .toLowerCase();
                if (!haystack.includes(query)) return false;
            }
            return true;
        });
    }, [capabilityFilter, models, providerFilters, search]);

    const toggleProviderFilter = (provider: string) => {
        setProviderFilters(current => current.includes(provider) ? current.filter(it => it !== provider) : [...current, provider]);
    };

    const lightTheme = useIsLightThemeStored();
    const secondaryCtaColor = lightTheme ? "primaryMain" : "secondaryMain";

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
                <img className="hero-icon" src={HeroImage} alt="" aria-hidden="true" />
            </div>
        </section>

        <section id="model-catalog" className="panel panel-muted">
            <div className="panel-inner">
                <Flex alignItems={"center"} mb={8}>
                    <Flex gap={"4px"}>
                        <CatalogFilterButton active={capabilityFilter === "All"} onClick={() => setCapabilityFilter("All")}>All</CatalogFilterButton>
                        {capabilities.map(capability => <CatalogFilterButton key={capability} active={capabilityFilter === capability} onClick={() => setCapabilityFilter(capability)}>
                            {capability}
                        </CatalogFilterButton>)}
                    </Flex>

                    <Box flexGrow={1} />

                    <Input
                        className="catalog-search"
                        type="search"
                        placeholder="Search models"
                        value={search}
                        onChange={ev => setSearch(ev.currentTarget.value)}
                    />
                </Flex>
                <Flex alignItems={"center"} mb={16}>
                    <span className="model-count">Showing {filteredModels.length} models</span>
                    <Box flexGrow={1} />
                    <RichSelect<ModelProviderOption, keyof ModelProviderOption>
                        items={providerOptions}
                        keys={["provider"]}
                        selected={undefined}
                        onSelect={(option) => toggleProviderFilter(option.provider)}
                        dropdownWidth="320px"
                        dropdownVerticalGap={8}
                        elementHeight={42}
                        matchTriggerWidth={false}
                        showSearchField={providerOptions.length > 8}
                        trigger={<ProviderFilterTrigger selectedProviders={providerFilters} providerOptions={providerOptions} />}
                        RenderRow={(props) => <ProviderFilterOption
                            option={props.element}
                            selected={props.element ? providerFilters.includes(props.element.provider) : false}
                            onSelect={props.onSelect}
                            dataProps={props.dataProps}
                        />}
                    />
                </Flex>

                {models.length === 0 && !loading ? <p className="no-results">No inference models are available.</p> : null}
                {models.length !== 0 && filteredModels.length === 0 ? <p className="no-results">No models match the selected filters.</p> : null}
                {filteredModels.length === 0 ? null : <div className="model-results">
                    <div className="model-grid">
                        {filteredModels.map(model => <ModelCatalogCard key={model.name} model={model} />)}
                    </div>
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
                    <Link className="button-link" to={AppRoutes.inference.playground()}>
                        <Button type="button" color={"successMain"}>Try the playground</Button>
                    </Link>
                    <Link className="button-link" to={AppRoutes.grants.newApplication({})}>
                        <Button type="button" color={secondaryCtaColor}>Apply for access</Button>
                    </Link>
                </div>
            </div>
        </section>
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

function CatalogFilterButton(props: React.PropsWithChildren<{active: boolean; onClick: () => void; className?: string;}>): React.ReactNode {
    return <button
        className={`catalog-filter-button ${props.className ?? ""}`}
        data-active={props.active.toString()}
        type="button"
        onClick={props.onClick}
    >
        {props.children}
    </button>;
}

function ProviderFilterTrigger(props: {selectedProviders: string[]; providerOptions: ModelProviderOption[]}): React.ReactNode {
    let label = "All providers";
    if (props.selectedProviders.length === 1) {
        label = props.selectedProviders[0];
    } else if (props.selectedProviders.length > 1) {
        label = `${props.selectedProviders.length} providers`;
    }

    const logoModelName = props.selectedProviders.length === 1
        ? props.providerOptions.find(option => option.provider === props.selectedProviders[0])?.modelName
        : null;

    return <button type="button" className="provider-trigger">
        {logoModelName ? <ModelInferenceLogo modelName={logoModelName} size={20} /> : null}
        <span className="provider-trigger-label">{label}</span>
        <Icon name="heroChevronDown" size={14} />
    </button>;
}

function ProviderFilterOption(props: {
    option?: ModelProviderOption;
    selected: boolean;
    onSelect: () => void;
    dataProps?: Record<string, string>;
}): React.ReactNode {
    if (!props.option) return null;
    return <div
        {...props.dataProps}
        className={providerFilterOptionStyle}
        data-selected={props.selected.toString()}
        onClick={ev => {
            ev.stopPropagation();
            props.onSelect();
        }}
    >
        <ModelInferenceLogo modelName={props.option.modelName} size={24} />
        <span className="provider-option-label">{props.option.provider}</span>
        {props.selected ? <Icon name="heroCheck" size={16} color="primaryMain" /> : <span style={{width: 16}} />}
    </div>;
}

interface ModelProviderOption {
    provider: string;
    modelName: string;
}

function modelProviderOptions(models: InferenceModel[]): ModelProviderOption[] {
    const options = new Map<string, string>();
    for (const model of models) {
        const provider = modelProviderName(model.name);
        if (!options.has(provider)) options.set(provider, model.name);
    }
    return [...options.entries()]
        .map(([provider, modelName]) => ({provider, modelName}))
        .sort((a, b) => a.provider.localeCompare(b.provider));
}

function ModelCatalogCard(props: {model: InferenceModel;}): React.ReactNode {
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
                <ModelMetric title="Context" value={model.contextWindow?.toLocaleString() ?? "-"} />
                <ModelMetric title="Parameters" value={model.page?.datasheet?.parameters ?? "-"} />
            </div>

            <div className="metric-grid">
                <ModelMultiplier title="Cached" value={model.priceMultiplier.cachedInput} />
                <ModelMultiplier title="Input" value={model.priceMultiplier.input} />
                <ModelMultiplier title="Output" value={model.priceMultiplier.output} />
            </div>
        </div>
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
