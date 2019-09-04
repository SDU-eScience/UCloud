import * as React from "react";
import {Top as TopOption, HairColor, FacialHair, FacialHairColor} from "UserSettings/AvatarOptions";
import Eyepatch from "./Eyepatch";
import Hat from "./Hat";
import Hijab from "./Hijab";
import LongHairBigHair from "./LongHairBigHair";
import LongHairBob from "./LongHairBob";
import LongHairBun from "./LongHairBun";
import LongHairCurly from "./LongHairCurly";
import LongHairCurvy from "./LongHairCurvy";
import LongHairDreads from "./LongHairDreads";
import LongHairFrida from "./LongHairFrida";
import LongHairFro from "./LongHairFro";
import LongHairFroBand from "./LongHairFroBand";
import LongHairMiaWallace from "./LongHairMiaWallace";
import LongHairNotTooLong from "./LongHairNotTooLong";
import LongHairShavedSides from "./LongHairShavedSides";
import LongHairStraight from "./LongHairStraight";
import LongHairStraight2 from "./LongHairStraight2";
import LongHairStraightStrand from "./LongHairStraightStrand";
import NoHair from "./NoHair";
import ShortHairDreads01 from "./ShortHairDreads01";
import ShortHairDreads02 from "./ShortHairDreads02";
import ShortHairFrizzle from "./ShortHairFrizzle";
import ShortHairShaggyMullet from "./ShortHairShaggyMullet";
import ShortHairShortCurly from "./ShortHairShortCurly";
import ShortHairShortFlat from "./ShortHairShortFlat";
import ShortHairShortRound from "./ShortHairShortRound";
import ShortHairShortWaved from "./ShortHairShortWaved";
import ShortHairSides from "./ShortHairSides";
import ShortHairTheCaesar from "./ShortHairTheCaesar";
import ShortHairTheCaesarSidePart from "./ShortHairTheCaesarSidePart";
import Turban from "./Turban";
import WinterHat1 from "./WinterHat1";
import WinterHat2 from "./WinterHat2";
import WinterHat3 from "./WinterHat3";
import WinterHat4 from "./WinterHat4";

interface TopProps {
  optionValue: TopOption;
  hairColor: HairColor;
  facialHairColor: FacialHairColor;
  facialHair: FacialHair;
  children: JSX.Element;
}


export default function Top(props: TopProps) {
  const {children} = props;
  switch (props.optionValue) {
    case TopOption.NoHair:
      return <NoHair {...props}>{children}</NoHair>;
    case TopOption.Eyepatch:
      return <Eyepatch {...props}>{children}</Eyepatch>;
    case TopOption.Hat:
      return <Hat {...props}>{children}</Hat>;
    case TopOption.Hijab:
      return <Hijab>{children}</Hijab>;
    case TopOption.Turban:
      return <Turban {...props}>{children}</Turban>;
    case TopOption.WinterHat1:
      return <WinterHat1 {...props}>{children}</WinterHat1>;
    case TopOption.WinterHat2:
      return <WinterHat2 {...props}>{children}</WinterHat2>;
    case TopOption.WinterHat3:
      return <WinterHat3 {...props}>{children}</WinterHat3>;
    case TopOption.WinterHat4:
      return <WinterHat4 {...props}>{children}</WinterHat4>;
    case TopOption.LongHairBigHair:
      return <LongHairBigHair {...props}>{children}</LongHairBigHair>;
    case TopOption.LongHairBob:
      return <LongHairBob {...props}>{children}</LongHairBob>;
    case TopOption.LongHairBun:
      return <LongHairBun {...props}>{children}</LongHairBun>;
    case TopOption.LongHairCurly:
      return <LongHairCurly {...props}>{children}</LongHairCurly>;
    case TopOption.LongHairCurvy:
      return <LongHairCurvy {...props}>{children}</LongHairCurvy>;
    case TopOption.LongHairDreads:
      return <LongHairDreads {...props}>{children}</LongHairDreads>;
    case TopOption.LongHairFrida:
      return <LongHairFrida {...props}>{children}</LongHairFrida>;
    case TopOption.LongHairFro:
      return <LongHairFro {...props}>{children}</LongHairFro>;
    case TopOption.LongHairFroBand:
      return <LongHairFroBand {...props}>{children}</LongHairFroBand>;
    case TopOption.LongHairNotTooLong:
      return <LongHairNotTooLong {...props}>{children}</LongHairNotTooLong>;
    case TopOption.LongHairShavedSides:
      return <LongHairShavedSides {...props}>{children}</LongHairShavedSides>;
    case TopOption.LongHairMiaWallace:
      return <LongHairMiaWallace {...props}>{children}</LongHairMiaWallace>;
    case TopOption.LongHairStraight:
      return <LongHairStraight {...props}>{children}</LongHairStraight>;
    case TopOption.LongHairStraight2:
      return <LongHairStraight2 {...props}>{children}</LongHairStraight2>;
    case TopOption.LongHairStraightStrand:
      return <LongHairStraightStrand {...props}>{children}</LongHairStraightStrand>;
    case TopOption.ShortHairDreads01:
      return <ShortHairDreads01 {...props}>{children}</ShortHairDreads01>;
    case TopOption.ShortHairDreads02:
      return <ShortHairDreads02 {...props}>{children}</ShortHairDreads02>;
    case TopOption.ShortHairFrizzle:
      return <ShortHairFrizzle {...props}>{children}</ShortHairFrizzle>;
    case TopOption.ShortHairShaggyMullet:
      return <ShortHairShaggyMullet {...props}>{children}</ShortHairShaggyMullet>;
    case TopOption.ShortHairShortCurly:
      return <ShortHairShortCurly {...props}>{children}</ShortHairShortCurly>;
    case TopOption.ShortHairShortFlat:
      return <ShortHairShortFlat {...props}>{children}</ShortHairShortFlat>;
    case TopOption.ShortHairShortRound:
      return <ShortHairShortRound {...props}>{children}</ShortHairShortRound>;
    case TopOption.ShortHairShortWaved:
      return <ShortHairShortWaved {...props}>{children}</ShortHairShortWaved>;
    case TopOption.ShortHairSides:
      return <ShortHairSides {...props}>{children}</ShortHairSides>;
    case TopOption.ShortHairTheCaesar:
      return <ShortHairTheCaesar {...props}>{children}</ShortHairTheCaesar>;
    case TopOption.ShortHairTheCaesarSidePart:
      return <ShortHairTheCaesarSidePart {...props}>{children}</ShortHairTheCaesarSidePart>;
  }
}
