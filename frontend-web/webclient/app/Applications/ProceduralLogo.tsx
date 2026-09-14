import * as React from "react";
import * as HeroIcons from "@/ui-components/icons";
import {ApplicationGroupLogo, ApplicationGroupLogoColor, ApplicationGroupLogoDirection, ApplicationGroupLogoIcon, ApplicationGroupLogoShape, AppCatalogCustomGroup} from "@/Applications/AppStoreApi";
import {appColors, useIsLightThemeStored} from "@/ui-components/theme";
import {Box, Button, Flex, Icon, Input, Select, Text, TextArea} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {injectStyle} from "@/Unstyled";
import {dialogStore} from "@/Dialog/DialogStore";
import {extractErrorMessage} from "@/UtilityFunctions";
import {FieldGroup, FieldRow} from "@/Applications/Jobs/Widgets";
import {KeyboardNavigation, SubmitShortcut} from "@/Applications/KeyboardNavigation";

type PaletteColor = Exclude<ApplicationGroupLogoColor, "auto">;

const colors: Record<PaletteColor, string[]> = {
    gold: appColors[0],
    blue: appColors[1],
    violet: appColors[2],
    green: appColors[3],
    cyan: appColors[4],
    rose: appColors[5],
};

const colorOptions = Object.keys(colors) as PaletteColor[];
const allColorOptions: ApplicationGroupLogoColor[] = ["auto", ...colorOptions];
const shapes: Array<{value: ApplicationGroupLogoShape; label: string}> = [
    {value: "circle", label: "Circle"},
    {value: "rounded-square", label: "Rounded square"},
    {value: "hexagon", label: "Hexagon"},
    {value: "diamond", label: "Diamond"},
    {value: "shield", label: "Shield"},
];
const icons: Array<{value: ApplicationGroupLogoIcon; label: string; icon: keyof typeof HeroIcons}> = [
    {value: "academic-cap", label: "Education", icon: "heroAcademicCap"},
    {value: "beaker", label: "Research", icon: "heroBeaker"},
    {value: "bolt", label: "Energy", icon: "heroBolt"},
    {value: "calculator", label: "Calculation", icon: "heroCalculator"},
    {value: "chart-bar", label: "Analytics", icon: "heroChartBar"},
    {value: "circle-stack", label: "Database", icon: "heroCircleStack"},
    {value: "cloud", label: "Cloud", icon: "heroCloud"},
    {value: "code-bracket", label: "Source code", icon: "heroCodeBracket"},
    {value: "command-line", label: "Terminal", icon: "heroCommandLine"},
    {value: "cpu-chip", label: "Computing", icon: "heroCpuChip"},
    {value: "cube", label: "Container", icon: "heroCube"},
    {value: "document", label: "Document", icon: "heroDocument"},
    {value: "folder", label: "Files", icon: "heroFolder"},
    {value: "globe", label: "Global", icon: "heroGlobeAlt"},
    {value: "photo", label: "Imaging", icon: "heroPhoto"},
    {value: "rocket", label: "Launch", icon: "heroRocketLaunch"},
    {value: "server", label: "Server", icon: "heroServer"},
    {value: "sparkles", label: "Discovery", icon: "heroSparkles"},
    {value: "wrench", label: "Tools", icon: "heroWrench"},
    {value: "gpu", label: "GPU", icon: "gpu"},
];

function logoHash(title: string): number {
    let hash = 5381;
    let index = title.length;
    while (index) hash = (Math.imul(hash, 33) ^ title.charCodeAt(--index)) >>> 0;
    return hash;
}

export const integratedTerminalLogo: ApplicationGroupLogo = {
    version: 1,
    shape: "rounded-square",
    border: {style: "none", color: "auto"},
    fill: {type: "solid", colorA: "blue", colorB: "blue", direction: "top-right"},
    content: {type: "icon", value: "command-line", size: "medium", color: "auto"},
};

export function defaultApplicationGroupLogo(title: string): ApplicationGroupLogo {
    let value = logoHash(title);
    const next = (length: number) => {
        value = (Math.imul(value, 1664525) + 1013904223) >>> 0;
        return value % length;
    };
    const colorA = colorOptions[next(colorOptions.length)];
    let colorB = colorOptions[next(colorOptions.length)];
    if (colorA === colorB) colorB = colorOptions[(colorOptions.indexOf(colorA) + 1) % colorOptions.length];
    return {
        version: 1,
        shape: shapes[next(shapes.length)].value,
        border: {
            style: (["none", "thin", "thick", "double"] as const)[next(4)],
            color: colorOptions[next(colorOptions.length)],
        },
        fill: {
            type: (["solid", "gradient"] as const)[next(2)],
            colorA,
            colorB,
            direction: (["top-right", "bottom-right", "bottom-left", "top-left"] as const)[next(4)],
        },
        content: {
            type: "icon",
            value: icons[next(icons.length - 1)].value,
            size: (["small", "medium", "large"] as const)[next(3)],
            color: "auto",
        },
    };
}

function shapeElement(shape: ApplicationGroupLogoShape, props: React.SVGProps<SVGElement>): React.ReactElement {
    if (shape === "circle") return <circle cx="50" cy="50" r="44" {...props as React.SVGProps<SVGCircleElement>} />;
    if (shape === "rounded-square") return <rect x="7" y="7" width="86" height="86" rx="21" {...props as React.SVGProps<SVGRectElement>} />;
    if (shape === "hexagon") return <polygon points="50,4 90,27 90,73 50,96 10,73 10,27" {...props as React.SVGProps<SVGPolygonElement>} />;
    if (shape === "diamond") return <polygon points="50,4 96,50 50,96 4,50" {...props as React.SVGProps<SVGPolygonElement>} />;
    return <path d="M50 4L91 19V48C91 71 74 87 50 95C26 87 9 71 9 48V19L50 4Z" {...props as React.SVGProps<SVGPathElement>} />;
}

function gradientCoordinates(direction: ApplicationGroupLogoDirection): {x1: string; y1: string; x2: string; y2: string} {
    if (direction === "bottom-right") return {x1: "0%", y1: "0%", x2: "100%", y2: "100%"};
    if (direction === "bottom-left") return {x1: "100%", y1: "0%", x2: "0%", y2: "100%"};
    if (direction === "top-left") return {x1: "100%", y1: "100%", x2: "0%", y2: "0%"};
    return {x1: "0%", y1: "100%", x2: "100%", y2: "0%"};
}

function luminance(color: string): number {
    const channels = [1, 3, 5].map(index => parseInt(color.slice(index, index + 2), 16) / 255)
        .map(value => value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4));
    return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
}

function mixColor(first: string, second: string, ratio: number): string {
    const channel = (index: number) => Math.round(
        parseInt(first.slice(index, index + 2), 16) * (1 - ratio) + parseInt(second.slice(index, index + 2), 16) * ratio
    ).toString(16).padStart(2, "0");
    return `#${channel(1)}${channel(3)}${channel(5)}`;
}

function roleColor(color: ApplicationGroupLogoColor, isLight: boolean, role: "primary" | "secondary" | "border"): string {
    if (color === "auto") return isLight ? "#252B36" : "#EDF1F6";
    if (role === "primary") return colors[color][isLight ? 1 : 0];
    if (role === "secondary") return colors[color][isLight ? 2 : 1];
    return colors[color][isLight ? 2 : 0];
}

export function ProceduralLogo(props: {logo: ApplicationGroupLogo; size: string; isLightOverride?: boolean; title?: string}): React.ReactNode {
    let isLight = useIsLightThemeStored();
    if (props.isLightOverride !== undefined) isLight = props.isLightOverride;
    const id = React.useId().replaceAll(":", "");
    const fillA = roleColor(props.logo.fill.colorA, isLight, "primary");
    const fillB = props.logo.fill.colorB === props.logo.fill.colorA
        ? mixColor(fillA, isLight ? "#FFFFFF" : "#000000", 0.14)
        : roleColor(props.logo.fill.colorB, isLight, "secondary");
    const fill = props.logo.fill.type === "gradient" ? `url(#logo-gradient-${id})` : fillA;
    const borderBase = roleColor(props.logo.border.color, isLight, "border");
    const borderColor = props.logo.border.color === props.logo.fill.colorA
        ? mixColor(fillA, isLight ? "#000000" : "#FFFFFF", 0.18)
        : props.logo.border.color === props.logo.fill.colorB
        ? mixColor(fillB, isLight ? "#000000" : "#FFFFFF", 0.18)
        : borderBase;
    const averageLuminance = (luminance(fillA) + luminance(props.logo.fill.type === "gradient" ? fillB : fillA)) / 2;
    const contentColor = props.logo.content.color === "auto"
        ? averageLuminance > 0.48 ? "#172033" : "#FFFFFF"
        : props.logo.content.color === "light" ? "#FFFFFF"
        : props.logo.content.color === "dark" ? "#172033"
        : roleColor(props.logo.content.color, isLight, "border");
    const strokeWidth = props.logo.border.style === "thin" ? 3 : props.logo.border.style === "thick" ? 7 : props.logo.border.style === "double" ? 7 : 0;
    const contentScale = props.logo.content.size === "small" ? 0.72 : props.logo.content.size === "large" ? 1.12 : 0.92;
    const icon = icons.find(option => option.value === props.logo.content.value) ?? icons[0];
    const IconComponent = HeroIcons[icon.icon] as React.ComponentType<React.SVGProps<SVGSVGElement>>;
    const iconSize = 48 * contentScale;
    const maxTextSize = props.logo.shape === "diamond" || props.logo.shape === "shield" ? 38 : 46;
    const textLength = [...props.logo.content.value].length;
    const textSize = Math.min(maxTextSize * contentScale, 62 / Math.max(1, textLength * 0.62));
    return <svg width={props.size} height={props.size} viewBox="0 0 100 100" role="img" aria-label={props.title ? `${props.title} logo` : "Application logo"}>
        <defs>
            <linearGradient id={`logo-gradient-${id}`} {...gradientCoordinates(props.logo.fill.direction)}>
                <stop offset="0%" stopColor={fillA} />
                <stop offset="100%" stopColor={fillB} />
            </linearGradient>
        </defs>
        {shapeElement(props.logo.shape, {fill, stroke: borderColor, strokeWidth, strokeLinejoin: "round"})}
        {props.logo.border.style === "double" ? <>
            {shapeElement(props.logo.shape, {fill: "none", stroke: fillA, strokeWidth: 3, strokeLinejoin: "round"})}
            {shapeElement(props.logo.shape, {fill: "none", stroke: borderColor, strokeWidth: 1, strokeLinejoin: "round"})}
        </> : null}
        {props.logo.content.type === "icon" ? (
            <IconComponent x={50 - iconSize / 2} y={50 - iconSize / 2} width={iconSize} height={iconSize} style={{color: contentColor}} />
        ) : (
            <text x="50" y={props.logo.shape === "shield" ? "53" : "51"} dominantBaseline="middle" textAnchor="middle"
                fill={contentColor} fontFamily="inherit" fontSize={textSize} fontWeight="700">
                {props.logo.content.value}
            </text>
        )}
    </svg>;
}

const EditorClass = injectStyle("procedural-logo-editor", k => `
    ${k} {
        display: grid;
        grid-template-columns: minmax(220px, 280px) minmax(320px, 1fr);
        gap: 28px;
        min-height: 0;
        height: 100%;
    }
    ${k} .logo-preview {
        height: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 18px;
        border-radius: 12px;
        background: var(--backgroundCard);
        border: 1px solid var(--borderColor);
        min-height: 0;
    }
    ${k} .logo-preview-sizes {
        display: flex;
        align-items: end;
        gap: 16px;
    }
    ${k} .logo-controls {
        display: flex;
        flex-direction: column;
        gap: 18px;
        min-width: 0;
        min-height: 0;
        overflow-y: auto;
        padding: 2px 12px 4px 2px;
    }
    ${k} .logo-control {
        display: flex;
        flex-direction: column;
        gap: 7px;
        flex-shrink: 0;
    }
    ${k} .logo-control-title {
        font-size: 13px;
        font-weight: 600;
        color: var(--textPrimary);
    }
    ${k} .logo-option-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(92px, 1fr));
        gap: 7px;
    }
    ${k} .logo-option {
        min-height: 38px;
        padding: 7px;
        color: var(--textPrimary);
        background: var(--backgroundDefault);
        border: 1px solid var(--borderColor);
        border-radius: 7px;
        cursor: pointer;
        font: inherit;
    }
    ${k} .logo-option:hover {
        border-color: var(--borderColorHover);
    }
    ${k} .logo-option[data-selected="true"] {
        border-color: var(--primaryMain);
        box-shadow: 0 0 0 1px var(--primaryMain);
    }
    ${k} .logo-shape-option {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 5px;
        text-align: center;
    }
    ${k} .logo-shape-preview {
        display: flex;
        width: 40px;
        height: 40px;
        align-items: center;
        justify-content: center;
    }
    ${k} .logo-shape-label {
        min-height: 32px;
        display: flex;
        align-items: center;
        justify-content: center;
    }
    ${k} .logo-swatches {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
    }
    ${k} .logo-swatch {
        width: 31px;
        height: 31px;
        border-radius: 50%;
        border: 2px solid var(--backgroundDefault);
        cursor: pointer;
        box-shadow: 0 0 0 1px var(--borderColor);
    }
    ${k} .logo-swatch[data-selected="true"] {
        box-shadow: 0 0 0 2px var(--primaryMain);
    }
    ${k} .logo-icon-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(105px, 1fr));
        gap: 7px;
        max-height: 190px;
        overflow: auto;
        padding: 2px;
    }
    ${k} .logo-icon-option {
        display: flex;
        align-items: center;
        gap: 6px;
        text-align: left;
    }
    ${k} .logo-icon-option svg {
        flex: none;
    }
    @media (max-width: 720px) {
        ${k} {
            grid-template-columns: 1fr;
            height: auto;
        }
        ${k} .logo-preview {
            height: auto;
            min-height: 220px;
        }
        ${k} .logo-controls {
            overflow-y: visible;
        }
    }
`);

type SelectableColor = ApplicationGroupLogo["content"]["color"];

function swatchBackground(color: SelectableColor): string {
    if (color === "auto") return "linear-gradient(135deg, #15191F 50%, #FFFFFF 50%)";
    if (color === "light") return "#FFFFFF";
    if (color === "dark") return "#172033";
    return colors[color][1];
}

function Swatches<T extends SelectableColor>(props: {value: T; options?: T[]; onChange: (value: T) => void}): React.ReactNode {
    const options = props.options ?? allColorOptions as T[];
    return <div className="logo-swatches">
        {options.map(color => <button key={color} type="button" className="logo-swatch" data-selected={props.value === color}
            aria-label={color === "auto" ? "Automatic black or white" : color} title={color === "auto" ? "Automatic black or white" : color}
            style={{background: swatchBackground(color)}}
            onClick={() => props.onChange(color)} />)}
    </div>;
}

export function ApplicationGroupLogoEditor(props: {logo: ApplicationGroupLogo; onChange: (logo: ApplicationGroupLogo) => void}): React.ReactNode {
    const [iconQuery, setIconQuery] = React.useState("");
    const set = (patch: Partial<ApplicationGroupLogo>) => props.onChange({...props.logo, ...patch});
    const matchingIcons = icons.filter(icon => icon.label.toLowerCase().includes(iconQuery.trim().toLowerCase()));
    return <div className={EditorClass}>
        <div className="logo-preview">
            <ProceduralLogo logo={props.logo} size="170px" />
            <div className="logo-preview-sizes">
                {[24, 36, 56, 64].map(size => <ProceduralLogo key={size} logo={props.logo} size={`${size}px`} />)}
            </div>
        </div>
        <div className="logo-controls">
            <div className="logo-control">
                <span className="logo-control-title">Background shape</span>
                <div className="logo-option-grid">
                    {shapes.map(shape => <button key={shape.value} type="button" className="logo-option logo-shape-option"
                        data-selected={props.logo.shape === shape.value} onClick={() => set({shape: shape.value})}>
                        <span className="logo-shape-preview"><ProceduralLogo logo={{...props.logo, shape: shape.value}} size="36px" /></span>
                        <span className="logo-shape-label">{shape.label}</span>
                    </button>)}
                </div>
            </div>
            <div className="logo-control">
                <span className="logo-control-title">Border</span>
                <Select value={props.logo.border.style} onChange={event => set({border: {...props.logo.border, style: event.target.value as ApplicationGroupLogo["border"]["style"]}})}>
                    <option value="none">None</option><option value="thin">Thin</option><option value="thick">Thick</option><option value="double">Double</option>
                </Select>
                {props.logo.border.style === "none" ? null : <Swatches value={props.logo.border.color} onChange={color => set({border: {...props.logo.border, color}})} />}
            </div>
            <div className="logo-control">
                <span className="logo-control-title">Background</span>
                <Flex gap="7px">
                    <button type="button" className="logo-option" data-selected={props.logo.fill.type === "solid"} onClick={() => set({fill: {...props.logo.fill, type: "solid"}})}>Solid</button>
                    <button type="button" className="logo-option" data-selected={props.logo.fill.type === "gradient"} onClick={() => set({fill: {...props.logo.fill, type: "gradient"}})}>Gradient</button>
                </Flex>
                <Text fontSize={12} color="textSecondary">First color</Text>
                <Swatches value={props.logo.fill.colorA} onChange={colorA => set({fill: {...props.logo.fill, colorA}})} />
                {props.logo.fill.type === "solid" ? null : <>
                    <Text fontSize={12} color="textSecondary">Second color</Text>
                    <Swatches value={props.logo.fill.colorB} onChange={colorB => set({fill: {...props.logo.fill, colorB}})} />
                    <Select value={props.logo.fill.direction} onChange={event => set({fill: {...props.logo.fill, direction: event.target.value as ApplicationGroupLogoDirection}})}>
                        <option value="top-right">Towards top right</option><option value="bottom-right">Towards bottom right</option>
                        <option value="bottom-left">Towards bottom left</option><option value="top-left">Towards top left</option>
                    </Select>
                </>}
            </div>
            <div className="logo-control">
                <span className="logo-control-title">Center content</span>
                <Flex gap="7px">
                    <button type="button" className="logo-option" data-selected={props.logo.content.type === "icon"} onClick={() => set({content: {...props.logo.content, type: "icon", value: icons[0].value}})}>Symbol</button>
                    <button type="button" className="logo-option" data-selected={props.logo.content.type === "text"} onClick={() => set({content: {...props.logo.content, type: "text", value: "APP"}})}>Text</button>
                </Flex>
                {props.logo.content.type === "text" ? (
                    <Input value={props.logo.content.value} placeholder="APP" onChange={event => set({content: {...props.logo.content, value: [...event.target.value].slice(0, 4).join("")}})} />
                ) : <>
                    <Input value={iconQuery} placeholder="Search symbols" onChange={event => setIconQuery(event.target.value)} />
                    <div className="logo-icon-grid">
                        {matchingIcons.map(icon => {
                            const Component = HeroIcons[icon.icon] as React.ComponentType<React.SVGProps<SVGSVGElement>>;
                            return <button key={icon.value} type="button" className="logo-option logo-icon-option"
                                data-selected={props.logo.content.value === icon.value} onClick={() => set({content: {...props.logo.content, value: icon.value}})}>
                                <Component width={19} height={19} />{icon.label}
                            </button>;
                        })}
                    </div>
                </>}
                <span className="logo-control-title">Symbol or text size</span>
                <Select value={props.logo.content.size} onChange={event => set({content: {...props.logo.content, size: event.target.value as ApplicationGroupLogo["content"]["size"]}})}>
                    <option value="small">Small</option><option value="medium">Medium</option><option value="large">Large</option>
                </Select>
                <span className="logo-control-title">Symbol or text color</span>
                <Swatches value={props.logo.content.color} options={["auto", "light", "dark", ...colorOptions]}
                    onChange={color => set({content: {...props.logo.content, color}})} />
            </div>
        </div>
    </div>;
}

export function ApplicationGroupLogoDialog(props: {title: string; initialLogo: ApplicationGroupLogo; onSave: (logo: ApplicationGroupLogo) => Promise<void>; onCancel: () => void}): React.ReactNode {
    const [logo, setLogo] = React.useState(props.initialLogo);
    const [saving, setSaving] = React.useState(false);
    const save = async () => {
        setSaving(true);
        try {
            await props.onSave(logo);
        } finally {
            setSaving(false);
        }
    };
    return <Box height="100%" style={{display: "flex", flexDirection: "column", minHeight: 0}}>
        <Flex justifyContent="space-between" alignItems="center" mb="20px" flexShrink={0}>
            <Text fontSize={22} fontWeight={600}>Logo for {props.title}</Text>
            <Button type="button" color="secondaryMain" onClick={() => setLogo(defaultApplicationGroupLogo(props.title))}>Reset</Button>
        </Flex>
        <Box flexGrow={1} minHeight="0">
            <ApplicationGroupLogoEditor logo={logo} onChange={setLogo} />
        </Box>
        <Flex justifyContent="end" gap="8px" mt="32px" px="20px" py="12px" mx="-20px" mb="-20px" background="var(--dialogToolbar)" flexShrink={0}>
            <Button type="button" color="errorMain" onClick={props.onCancel}>Cancel</Button>
            <Button type="button" color="successMain" disabled={saving || (logo.content.type === "text" && logo.content.value.trim() === "")}
                onClick={() => void save()}>Save logo</Button>
        </Flex>
    </Box>;
}

export function CustomGroupEditDialog(props: {
    group: AppCatalogCustomGroup;
    onSave: (title: string, description: string) => Promise<void>;
}): React.ReactNode {
    const [title, setTitle] = React.useState(props.group.specification.title);
    const [description, setDescription] = React.useState(props.group.specification.description);
    const [error, setError] = React.useState<string | null>(null);
    const [saving, setSaving] = React.useState(false);

    const submit = async (event: React.FormEvent) => {
        event.preventDefault();
        if (saving) return;
        const cleanTitle = title.trim();
        const cleanDescription = description.trim();
        if (!cleanTitle || !cleanDescription) {
            setError("Enter a name and description.");
            return;
        }
        setError(null);
        setSaving(true);
        try {
            await props.onSave(cleanTitle, cleanDescription);
            dialogStore.success();
        } catch (cause) {
            setError(extractErrorMessage(cause as {request: XMLHttpRequest; response: any}));
            setSaving(false);
        }
    };

    return <Box height="100%" style={{display: "flex", flexDirection: "column", minHeight: 0}}>
        <Heading.h3 mb="16px" flexShrink={0}>Edit application group</Heading.h3>
        <form onSubmit={submit} style={{display: "flex", flexDirection: "column", flex: "1 1 0", minHeight: 0}}>
            <KeyboardNavigation>
                <FieldGroup>
                    <Flex flexDirection="column" gap="12px" mb="20px">
                        <FieldRow
                            title="Name"
                            required
                            error={error ?? undefined}
                            control={<Input value={title} onChange={event => setTitle(event.target.value)} placeholder="My group" />}
                        />
                        <FieldRow
                            title="Description"
                            required
                            control={<TextArea value={description} onChange={event => setDescription(event.target.value)} rows={5}
                                placeholder="A short description shown to users." />}
                        />
                    </Flex>
                </FieldGroup>
            </KeyboardNavigation>
            <Flex justifyContent="end" gap="8px" mt="auto" px="20px" py="12px" mx="-20px" mb="-20px" background="var(--dialogToolbar)" flexShrink={0}>
                <Button color="errorMain" type="button" onClick={() => dialogStore.failure()}>Cancel</Button>
                <Button color="successMain" type="submit" disabled={saving}>
                    {saving ? <Icon name="refresh" spin /> : null}
                    Save changes<SubmitShortcut />
                </Button>
            </Flex>
        </form>
    </Box>;
}
