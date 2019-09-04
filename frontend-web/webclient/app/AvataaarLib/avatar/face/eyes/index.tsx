import * as React from "react";
import {Eyes as EyeOptions} from "UserSettings/AvatarOptions";
import Close from "./Close";
import Cry from "./Cry";
import Default from "./Default";
import Dizzy from "./Dizzy";
import EyeRoll from "./EyeRoll";
import Happy from "./Happy";
import Hearts from "./Hearts";
import Side from "./Side";
import Squint from "./Squint";
import Surprised from "./Surprised";
import Wink from "./Wink";
import WinkWacky from "./WinkWacky";

export default function Eyes(props: {optionValue: EyeOptions}) {
  switch (props.optionValue) {
    case EyeOptions.Close:
      return <Close />;
    case EyeOptions.Cry:
      return <Cry />;
    case EyeOptions.Default:
      return <Default />;
    case EyeOptions.Dizzy:
      return <Dizzy />;
    case EyeOptions.EyeRoll:
      return <EyeRoll />;
    case EyeOptions.Happy:
      return <Happy />;
    case EyeOptions.Hearts:
      return <Hearts />;
    case EyeOptions.Side:
      return <Side />;
    case EyeOptions.Squint:
      return <Squint />;
    case EyeOptions.Surprised:
      return <Surprised />;
    case EyeOptions.Wink:
      return <Wink />;
    case EyeOptions.WinkWacky:
      return <WinkWacky />;
  }
}
