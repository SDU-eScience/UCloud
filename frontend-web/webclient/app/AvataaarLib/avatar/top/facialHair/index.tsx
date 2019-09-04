import * as React from "react";
import {FacialHairColor, FacialHair as Hair} from "UserSettings/AvatarOptions";
import BeardLight from "./BeardLight";
import BeardMajestic from "./BeardMajestic";
import BeardMedium from "./BeardMedium";
import Blank from "./Blank";
import MoustacheFancy from "./MoustacheFancy";
import MoustacheMagnum from "./MoustacheMagnum";

export default function FacialHair(props: {facialHairColor: FacialHairColor, facialHair: Hair}) {
  switch (props.facialHair) {
    case Hair.Blank:
      return <Blank />;
    case Hair.BeardMedium:
      return <BeardMedium {...props} />;
    case Hair.BeardLight:
      return <BeardLight {...props} />;
    case Hair.BeardMajestic:
      return <BeardMajestic {...props} />;
    case Hair.MoustacheFancy:
      return <MoustacheFancy {...props} />;
    case Hair.MoustacheMagnum:
      return <MoustacheMagnum {...props} />;
  }







}
