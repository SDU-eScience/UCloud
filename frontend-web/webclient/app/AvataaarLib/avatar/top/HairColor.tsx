import * as React from "react";
import {HairColor as Color} from "UserSettings/AvatarOptions";

export interface Props {
  maskID: string;
  optionValue: Color;
}

function makeColor(name: string, color: string) {
  class ColorComponent extends React.Component<Props> {
    render() {
      return (
        <g
          id="Skin/👶🏽-03-Brown"
          mask={`url(#${this.props.maskID})`}
          fill={color}>
          <g transform="translate(0.000000, 0.000000) " id="Color">
            <rect x="0" y="0" width="264" height="280" />
          </g>
        </g>
      )
    }
  }
  const anyComponent = ColorComponent as any;
  anyComponent.displayName = name;
  anyComponent.optionValue = name;
  return anyComponent;
}

const Auburn = makeColor("Auburn", "#A55728");
const Black = makeColor("Black", "#2C1B18");
const Blonde = makeColor("Blonde", "#B58143");
const BlondeGolden = makeColor("BlondeGolden", "#D6B370");
const Brown = makeColor("Brown", "#724133");
const BrownDark = makeColor("BrownDark", "#4A312C");
const PastelPink = makeColor("PastelPink", "#F59797");
const Platinum = makeColor("Platinum", "#ECDCBF");
const Red = makeColor("Red", "#C93305");
const SilverGray = makeColor("SilverGray", "#E8E1E1");

export default function HairColor(props: Props) {
  switch (props.optionValue) {
    case Color.Auburn:
      return <Auburn maskID={props.maskID} />;
    case Color.Black:
      return <Black maskID={props.maskID} />;
    case Color.Blonde:
      return <Blonde maskID={props.maskID} />;
    case Color.BlondeGolden:
      return <BlondeGolden maskID={props.maskID} />;
    case Color.Brown:
      return <Brown maskID={props.maskID} />;
    case Color.BrownDark:
      return <BrownDark maskID={props.maskID} />;
    case Color.PastelPink:
      return <PastelPink maskID={props.maskID} />;
    case Color.Platinum:
      return <Platinum maskID={props.maskID} />;
    case Color.Red:
      return <Red maskID={props.maskID} />;
    case Color.SilverGray:
      return <SilverGray maskID={props.maskID} />;
  }
  return null;
}
