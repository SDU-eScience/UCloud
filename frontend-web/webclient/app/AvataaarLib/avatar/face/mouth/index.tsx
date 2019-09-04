import * as React from "react";
import {MouthTypes} from "UserSettings/AvatarOptions";
import Concerned from "./Concerned";
import Default from "./Default";
import Disbelief from "./Disbelief";
import Eating from "./Eating";
import Grimace from "./Grimace";
import Sad from "./Sad";
import ScreamOpen from "./ScreamOpen";
import Serious from "./Serious";
import Smile from "./Smile";
import Tongue from "./Tongue";
import Twinkle from "./Twinkle";
import Vomit from "./Vomit";

export default function Mouth(props: {optionValue: MouthTypes}) {
  switch (props.optionValue) {
    case MouthTypes.Concerned:
      return <Concerned />;
    case MouthTypes.Default:
      return <Default />;
    case MouthTypes.Disbelief:
      return <Disbelief />;
    case MouthTypes.Eating:
      return <Eating />;
    case MouthTypes.Grimace:
      return <Grimace />;
    case MouthTypes.Sad:
      return <Sad />;
    case MouthTypes.ScreamOpen:
      return <ScreamOpen />;
    case MouthTypes.Serious:
      return <Serious />;
    case MouthTypes.Smile:
      return <Smile />;
    case MouthTypes.Tongue:
      return <Tongue />;
    case MouthTypes.Twinkle:
      return <Twinkle />;
    case MouthTypes.Vomit:
      return <Vomit />;
  }
}
