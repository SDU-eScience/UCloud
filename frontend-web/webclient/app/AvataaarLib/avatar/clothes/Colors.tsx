import * as React from "react";
import {ColorFabric} from "UserSettings/AvatarOptions";

export interface Props {
  maskID: string;
  color: ColorFabric;
}

function makeColor(name: string, color: string) {
  class ColorComponent extends React.Component<Props> {
    public render() {
      return (
        <g
          id="Color/Palette/Gray-01"
          mask={`url(#${this.props.maskID})`}
          fillRule="evenodd"
          fill={color}>
          <rect id="🖍Color" x="0" y="0" width="264" height="110" />
        </g>
      );
    }
  }
  const anyComponent = ColorComponent as any;
  anyComponent.displayName = name;
  anyComponent.optionValue = name;
  return anyComponent;
}

const Black = makeColor("Black", "#262E33");
const Blue01 = makeColor("Blue01", "#65C9FF");
const Blue02 = makeColor("Blue02", "#5199E4");
const Blue03 = makeColor("Blue03", "#25557C");
const Gray01 = makeColor("Gray01", "#E6E6E6");
const Gray02 = makeColor("Gray02", "#929598");
const Heather = makeColor("Heather", "#3C4F5C");
const PastelBlue = makeColor("PastelBlue", "#B1E2FF");
const PastelGreen = makeColor("PastelGreen", "#A7FFC4");
const PastelOrange = makeColor("PastelOrange", "#FFDEB5");
const PastelRed = makeColor("PastelRed", "#FFAFB9");
const PastelYellow = makeColor("PastelYellow", "#FFFFB1");
const Pink = makeColor("Pink", "#FF488E");
const Red = makeColor("Red", "#FF5C5C");
const White = makeColor("White", "#FFFFFF");

export default function Colors(props: Props) {
  switch (props.color) {
    case ColorFabric.Black:
      return <Black maskID={props.maskID} />;
    case ColorFabric.Blue01:
      return <Blue01 maskID={props.maskID} />;
    case ColorFabric.Blue02:
      return <Blue02 maskID={props.maskID} />;
    case ColorFabric.Blue03:
      return <Blue03 maskID={props.maskID} />;
    case ColorFabric.Gray01:
      return <Gray01 maskID={props.maskID} />;
    case ColorFabric.Gray02:
      return <Gray02 maskID={props.maskID} />;
    case ColorFabric.Heather:
      return <Heather maskID={props.maskID} />;
    case ColorFabric.PastelBlue:
      return <PastelBlue maskID={props.maskID} />;
    case ColorFabric.PastelGreen:
      return <PastelGreen maskID={props.maskID} />;
    case ColorFabric.PastelOrange:
      return <PastelOrange maskID={props.maskID} />;
    case ColorFabric.PastelRed:
      return <PastelRed maskID={props.maskID} />;
    case ColorFabric.PastelYellow:
      return <PastelYellow maskID={props.maskID} />;
    case ColorFabric.Pink:
      return <Pink maskID={props.maskID} />;
    case ColorFabric.Red:
      return <Red maskID={props.maskID} />;
    case ColorFabric.White:
      return <White maskID={props.maskID} />;
  }
}
