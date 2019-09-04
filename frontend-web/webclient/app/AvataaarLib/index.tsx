/*!

  MIT License

  Copyright (c) 2017 Pablo Stanley, Fang-Pen Lin

  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  SOFTWARE.

*/

import * as React from "react";
import {
  Clothes,
  ClothesGraphic,
  ColorFabric,
  Eyebrows,
  Eyes,
  FacialHair,
  FacialHairColor,
  HairColor,
  MouthTypes,
  SkinColors,
  Top,
  TopAccessory
} from "UserSettings/AvatarOptions";
import Avatar from "./avatar";
export {default as Avatar, AvatarStyle} from "./avatar";

export interface AvatarComponentProps {
  avatarStyle: string;
  style?: React.CSSProperties;
  topType: Top;
  accessoriesType: TopAccessory;
  hairColor: HairColor;
  facialHairType: FacialHair;
  facialHairColor: FacialHairColor;
  clotheType: Clothes;
  clotheColor: ColorFabric;
  graphicType: ClothesGraphic;
  eyeType: Eyes;
  eyebrowType: Eyebrows;
  mouthType: MouthTypes;
  skinColor: SkinColors;
  pieceType?: string;
  pieceSize?: string;
  viewBox?: string;
}

export default function AvatarComponent(props: AvatarComponentProps) {
  return <Avatar {...props} />;
}
