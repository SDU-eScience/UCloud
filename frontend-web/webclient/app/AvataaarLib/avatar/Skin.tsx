import * as React from "react";
import {SkinColors} from "@/UserSettings/AvatarOptions";

export interface Props {
    maskID: string;
    optionValue: SkinColors;
}

function makeColor(name: string, color: string) {
    function ColorComponent(props: Props) {
        return (
            <g
                id="Skin/👶🏽-03-Brown"
                mask={`url(#${props.maskID})`}
                fill={color}
            >
                <g transform="translate(0.000000, 0.000000)" id="Color">
                    <rect x="0" y="0" width="264" height="280" />
                </g>
            </g>
        );
    }
    const anyComponent = ColorComponent as any;
    anyComponent.displayName = name;
    anyComponent.optionValue = name;
    return anyComponent;
}

const Tanned = makeColor("Tanned", "#FD9841");
const Yellow = makeColor("Yellow", "#F8D25C");
const Pale = makeColor("Pale", "#FFDBB4");
const Light = makeColor("Light", "#EDB98A");
const Brown = makeColor("Brown", "#D08B5B");
const DarkBrown = makeColor("DarkBrown", "#AE5D29");
const Black = makeColor("Black", "#614335");

export default function Skin(props: Props): JSX.Element {
    switch (props.optionValue) {
        case SkinColors.Black:
            return <Black maskID={props.maskID} />;
        case SkinColors.Brown:
            return <Brown maskID={props.maskID} />;
        case SkinColors.DarkBrown:
            return <DarkBrown maskID={props.maskID} />;
        case SkinColors.Light:
            return <Light maskID={props.maskID} />;
        case SkinColors.Pale:
            return <Pale maskID={props.maskID} />;
        case SkinColors.Tanned:
            return <Tanned maskID={props.maskID} />;
        case SkinColors.Yellow:
            return <Yellow maskID={props.maskID} />;
    }
}
