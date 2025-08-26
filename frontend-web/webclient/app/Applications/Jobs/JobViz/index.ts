import {IconName} from "@/ui-components/Icon";
import {Line} from "@/ui-components/TemporalLineChart";

export interface WidgetId {
    id: string;
}

export interface WidgetPacketHeader {
    action: WidgetAction;
}

export enum WidgetType {
    WidgetTypeLabel,
    WidgetTypeProgressBar,
    WidgetTypeTable,
    WidgetTypeContainer,
    Tombstone1,
    WidgetTypeSnippet,
    WidgetTypeLineChart,
}

export enum WidgetWindow {
    WidgetWindowMain,
    WidgetWindowAux1,
    WidgetWindowAux2
}

export interface WidgetLocation {
    window: WidgetWindow;
    tab: string;
    icon: WidgetIcon;
}

export interface Widget {
    id: string;
    type: WidgetType;
    location: WidgetLocation;
}

export enum WidgetColorShade {
    WidgetColorNone,
    WidgetColorPrimary,
    WidgetColorSecondary,
    WidgetColorError,
    WidgetColorWarning,
    WidgetColorInfo,
    WidgetColorSuccess,
    WidgetColorText,
    WidgetColorPurple,
    WidgetColorRed,
    WidgetColorOrange,
    WidgetColorYellow,
    WidgetColorGreen,
    WidgetColorGray,
    WidgetColorBlue
}

export enum WidgetColorIntensity {
    WidgetColorMain,
    WidgetColorLight,
    WidgetColorDark,
    WidgetColorContrast,
    WidgetColor5,
    WidgetColor10,
    WidgetColor20,
    WidgetColor30,
    WidgetColor40,
    WidgetColor50,
    WidgetColor60,
    WidgetColor70,
    WidgetColor80,
    WidgetColor90
}

export interface WidgetColor {
    shade: WidgetColorShade;
    intensity: WidgetColorIntensity;
}

export interface WidgetDimensions {
    minimum: number;
    maximum: number;
}

export enum WidgetDirection {
    WidgetDirectionColumn,
    WidgetDirectionRow,
}

export interface WidgetContainer {
    foreground: WidgetColor;
    background: WidgetColor;
    width: WidgetDimensions;
    height: WidgetDimensions;
    direction: WidgetDirection;
    grow: number;
    gap: number;
    children: WidgetContainerOrId[];
}

export interface WidgetContainerOrId {
    container?: WidgetContainer | null;
    id?: WidgetId | null;
}

export enum WidgetLabelAlign {
    WidgetLabelAlignBegin,
    WidgetLabelAlignCenter,
    WidgetLabelAlignEnd
}

export interface WidgetLabel {
    text: string;
}

export interface WidgetTable {
    rows: WidgetTableRow[];
}

export interface WidgetTableRow {
    cells: WidgetTableCell[];
}

export enum WidgetTableCellFlag {
    WidgetTableCellHeader = 1 << 0
}

export interface WidgetTableCell {
    flags: WidgetTableCellFlag;
    width: WidgetDimensions;
    height: WidgetDimensions;
    label: WidgetLabel;
}

export enum WidgetDiagramUnit {
    GenericInt,
    GenericFloat,
    GenericPercent1,
    GenericPercent100,
    Bytes,
    BytesPerSecond,
    DateTime,
    Milliseconds,
    OperationsPerSecond,
}

export interface WidgetDiagramSeries {
    name: string;
    column: number;
}

export interface WidgetDiagramAxis {
    unit: WidgetDiagramUnit;
    minimum?: number | null;
    maximum?: number | null;
    logarithmic: boolean;
}

export interface WidgetDiagramDefinition {
    series: WidgetDiagramSeries[];
    xAxis: WidgetDiagramAxis;
    yAxis: WidgetDiagramAxis;

    channel: string;
    yAxisColumn: number;

    data?: Line[]; // frontend only property
}

export interface WidgetProgressBar {
    progress: number; // Value between 0 and 1
}

export enum WidgetIcon {
    Generic,
    Chat,
    Cpu,
    Gpu,
    Memory,
    Network,
    Directory,
    Drive,
    Question,
    Info,
    Warning,
}

const iconMap: Record<WidgetIcon, IconName> = {
    [WidgetIcon.Cpu]: "heroCpuChip",
    [WidgetIcon.Gpu]: "heroComputerDesktop",
    [WidgetIcon.Memory]: "memorySolid",
    [WidgetIcon.Network]: "heroGlobeEuropeAfrica",
    [WidgetIcon.Directory]: "heroFolder",
    [WidgetIcon.Drive]: "ftFileSystem",
    [WidgetIcon.Question]: "heroQuestionMarkCircle",
    [WidgetIcon.Chat]: "heroChatBubbleBottomCenter",
    [WidgetIcon.Info]: "heroInformationCircle",
    [WidgetIcon.Warning]: "warning",
    [WidgetIcon.Generic]: "heroMegaphone",
};

export function widgetIconToIcon(icon: WidgetIcon): IconName {
    return iconMap[icon] ?? "heroBugAnt";
}

export interface WidgetSnippet {
    text: string;
}

export enum WidgetAction {
    WidgetActionCreate,
    WidgetActionUpdate,
    WidgetActionDelete
}

const shadeToBaseName: string[] = [
    "",
    "primary",
    "secondary",
    "error",
    "warning",
    "info",
    "success",
    "text",
    "purple",
    "red",
    "orange",
    "yellow",
    "green",
    "gray",
    "blue"
];

const intensityToSuffix: string[] = [
    "Main",
    "Light",
    "Dark",
    "Contrast",
    "-5",
    "-10",
    "-20",
    "-30",
    "-40",
    "-50",
    "-60",
    "-70",
    "-80",
    "-90"
];

export function widgetColorToVariable(color: WidgetColor): string | null {
    if (color.shade == WidgetColorShade.WidgetColorNone) return null;
    return shadeToBaseName[color.shade] + intensityToSuffix[color.intensity];
}

export {Renderer} from "./Renderer";
export {StreamProcessor} from "./StreamProcessor";