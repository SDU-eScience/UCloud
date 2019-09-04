import * as React from "react";
import {Clothes as ClothesOpts, ClothesGraphic, ColorFabric} from "UserSettings/AvatarOptions";
import BlazerShirt from "./BlazerShirt";
import BlazerSweater from "./BlazerSweater";
import CollarSweater from "./CollarSweater";
import GraphicShirt from "./GraphicShirt";
import Hoodie from "./Hoodie";
import Overall from "./Overall";
import ShirtCrewNeck from "./ShirtCrewNeck";
import ShirtScoopNeck from "./ShirtScoopNeck";
import ShirtVNeck from "./ShirtVNeck";

export default function Clothes(props: {optionValue: ClothesOpts, graphic: ClothesGraphic, color: ColorFabric}) {
  const {color, graphic} = props;
  switch (props.optionValue) {
    case ClothesOpts.BlazerShirt:
      return <BlazerShirt />;
    case ClothesOpts.BlazerSweater:
      return <BlazerSweater />;
    case ClothesOpts.CollarSweater:
      return <CollarSweater color={color} />;
    case ClothesOpts.GraphicShirt:
      return <GraphicShirt color={color} graphic={graphic} />;
    case ClothesOpts.Hoodie:
      return <Hoodie color={color} />;
    case ClothesOpts.Overall:
      return <Overall color={color} />;
    case ClothesOpts.ShirtCrewNeck:
      return <ShirtCrewNeck color={color} />;
    case ClothesOpts.ShirtScoopNeck:
      return <ShirtScoopNeck color={color} />;
    case ClothesOpts.ShirtVNeck:
      return <ShirtVNeck color={color} />;
  }
}
