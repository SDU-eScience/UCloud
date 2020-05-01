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
    HatColor,
    MouthTypes,
    SkinColors,
    Top,
    TopAccessory
} from "UserSettings/AvatarOptions";
import {APICallParameters} from "Authentication/DataHook";
import {Dictionary} from "Types";
import {AvatarType} from "UserSettings/Avataaar";
export {default as Avatar, AvatarStyle} from "./avatar";

export interface AvatarComponentProps {
    avatarStyle: string;
    style?: React.CSSProperties;
    top: Top;
    topAccessory: TopAccessory;
    hairColor: HairColor;
    facialHair: FacialHair;
    facialHairColor: FacialHairColor;
    clothes: Clothes;
    colorFabric: ColorFabric;
    eyes: Eyes;
    eyebrows: Eyebrows;
    mouthTypes: MouthTypes;
    skinColors: SkinColors;
    clothesGraphic: ClothesGraphic;
    hatColor: HatColor;
    pieceType?: string;
    pieceSize?: string;
    viewBox?: string;
}

export interface FetchBulkAvatarsRequest {
    usernames: string[];
}

export interface FetchBulkAvatarsResponse {
    avatars: Dictionary<AvatarType>;
}

export function fetchBulkAvatars(request: FetchBulkAvatarsRequest): APICallParameters<FetchBulkAvatarsRequest> {
    return {
        reloadId: Math.random(),
        path: "/avatar/bulk",
        method: "POST",
        payload: request,
        parameters: request
    };
}
