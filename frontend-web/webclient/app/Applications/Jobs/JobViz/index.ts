import {ThemeColor} from "@/ui-components/theme";

const jobVizHeader = "#- UCloud JobViz -#";

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
    WidgetTypeDiagram
}

export enum WidgetWindow {
    WidgetWindowMain,
    WidgetWindowAux1,
    WidgetWindowAux2
}

export interface WidgetLocation {
    window: WidgetWindow;
    tab: string;
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
    WidgetDirectionRow,
    WidgetDirectionColumn
}

export interface WidgetContainer {
    foreground: WidgetColor;
    background: WidgetColor;
    width: WidgetDimensions;
    height: WidgetDimensions;
    direction: WidgetDirection;
    grow: number;
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

export interface WidgetVegaLiteDiagram {
    definition: any;
    data: any;
}

export interface WidgetProgressBar {
    progress: number; // Value between 0 and 1
}

export enum WidgetAction {
    WidgetActionCreate,
    WidgetActionUpdate,
    WidgetActionDelete
}

export type WidgetStreamEncoding = "binary" | "json";

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