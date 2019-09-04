import * as React from "react";
import {TopAccessory} from "UserSettings/AvatarOptions";
import Blank from "./Blank";
import Kurt from "./Kurt";
import Prescription01 from "./Prescription01";
import Prescription02 from "./Prescription02";
import Round from "./Round";
import Sunglasses from "./Sunglasses";
import Wayfarers from "./Wayfarers";

export default function Accessories(props: {optionValue: TopAccessory}) {
  switch (props.optionValue) {
    case TopAccessory.Blank:
      return <Blank />;
    case TopAccessory.Kurt:
      return <Kurt />;
    case TopAccessory.Prescription01:
      return <Prescription01 />;
    case TopAccessory.Prescription02:
      return <Prescription02 />;
    case TopAccessory.Round:
      return <Round />;
    case TopAccessory.Sunglasses:
      return <Sunglasses />;
    case TopAccessory.Wayfarers:
      return <Wayfarers />;
  }
}
