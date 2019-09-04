import * as React from "react"
import {FacialHairColor} from "UserSettings/AvatarOptions";

export interface Props {
  maskID: string;
  color: FacialHairColor;
}

function makeColor(name: string, color: string) {
  class ColorComponent extends React.Component<Props> {
    public render() {
      return (
        <g
          id="Color/Hair/Brown"
          mask={`url(#${this.props.maskID})`}
          fill={color}>
          <g transform="translate(-32.000000, 0.000000)" id="Color">
            <rect x="0" y="0" width="264" height="244" />
          </g>
        </g>
      );
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
const Platinum = makeColor("Platinum", "#ECDCBF");
const Red = makeColor("Red", "#C93305");

export default function Colors(props: Props) {
  switch (props.color) {
    case FacialHairColor.Auburn:
      return <Auburn maskID={props.maskID} />;
    case FacialHairColor.Black:
      return <Black maskID={props.maskID} />;
    case FacialHairColor.Blonde:
      return <Blonde maskID={props.maskID} />;
    case FacialHairColor.BlondeGolden:
      return <BlondeGolden maskID={props.maskID} />;
    case FacialHairColor.Brown:
      return <Brown maskID={props.maskID} />;
    case FacialHairColor.BrownDark:
      return <BrownDark maskID={props.maskID} />;
    case FacialHairColor.Platinum:
      return <Platinum maskID={props.maskID} />;
    case FacialHairColor.Red:
      return <Red maskID={props.maskID} />;
  }
}
