import * as React from "react";
import {Eyebrows, Eyes as EyeOptions, MouthTypes} from "UserSettings/AvatarOptions";
import Eyebrow from "./eyebrow";
import Eyes from "./eyes";
import Mouth from "./mouth";
import Nose from "./nose/Default";

export default function Face(props: {eyebrow: Eyebrows, eyes: EyeOptions, mouth: MouthTypes}) {
  return (
    <g id="Face" transform="translate(76.000000, 82.000000)" fill="#000000" >
      <Mouth optionValue={props.mouth} />
      <Nose />
      <Eyes optionValue={props.eyes} />
      <Eyebrow optionValue={props.eyebrow} />
    </g>
  );
}
