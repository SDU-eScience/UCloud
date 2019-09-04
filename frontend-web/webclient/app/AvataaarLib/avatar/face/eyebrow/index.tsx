import * as React from "react";
import {Eyebrows} from "UserSettings/AvatarOptions";
import Angry from "./Angry";
import AngryNatural from "./AngryNatural";
import Default from "./Default";
import DefaultNatural from "./DefaultNatural";
import FlatNatural from "./FlatNatural";
import RaisedExcited from "./RaisedExcited";
import RaisedExcitedNatural from "./RaisedExcitedNatural";
import SadConcerned from "./SadConcerned";
import SadConcernedNatural from "./SadConcernedNatural";
import UnibrowNatural from "./UnibrowNatural";
import UpDown from "./UpDown";
import UpDownNatural from "./UpDownNatural";

export default function Eyebrow(props: {optionValue: Eyebrows}) {
  switch (props.optionValue) {
    case Eyebrows.Angry:
      return <Angry />;
    case Eyebrows.AngryNatural:
      return <AngryNatural />;
    case Eyebrows.Default:
      return <Default />;
    case Eyebrows.DefaultNatural:
      return <DefaultNatural />;
    case Eyebrows.FlatNatural:
      return <FlatNatural />;
    case Eyebrows.RaisedExcited:
      return <RaisedExcited />;
    case Eyebrows.RaisedExcitedNatural:
      return <RaisedExcitedNatural />;
    case Eyebrows.SadConcerned:
      return <SadConcerned />;
    case Eyebrows.SadConcernedNatural:
      return <SadConcernedNatural />;
    case Eyebrows.UnibrowNatural:
      return <UnibrowNatural />;
    case Eyebrows.UpDown:
      return <UpDown />;
    case Eyebrows.UpDownNatural:
      return <UpDownNatural />;
  }
}
