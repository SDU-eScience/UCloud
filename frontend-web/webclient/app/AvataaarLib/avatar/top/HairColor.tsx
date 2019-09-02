import * as React from "react"

import {HairColorOption} from "../../options"
import Selector from "AvataaarLib/options/Selector";

export interface Props {
  maskID: string
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
  return (
    <Selector option={HairColorOption} defaultOption={BrownDark}>
      <Auburn maskID={props.maskID} />
      <Black maskID={props.maskID} />
      <Blonde maskID={props.maskID} />
      <BlondeGolden maskID={props.maskID} />
      <Brown maskID={props.maskID} />
      <BrownDark maskID={props.maskID} />
      <PastelPink maskID={props.maskID} />
      <Platinum maskID={props.maskID} />
      <Red maskID={props.maskID} />
      <SilverGray maskID={props.maskID} />
    </Selector>
  )
}
