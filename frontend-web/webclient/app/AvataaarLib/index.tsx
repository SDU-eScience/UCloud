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
import * as Options from "@/UserSettings/AvatarOptions";

export const defaultAvatar = ({
    top: Options.Top.NoHair,
    topAccessory: Options.TopAccessory.Blank,
    hatColor: Options.HatColor.Black,
    hairColor: Options.HairColor.Auburn,
    facialHair: Options.FacialHair.Blank,
    facialHairColor: Options.FacialHairColor.Auburn,
    clothes: Options.Clothes.BlazerShirt,
    colorFabric: Options.ColorFabric.Black,
    clothesGraphic: Options.ClothesGraphic.Bat,
    eyes: Options.Eyes.Default,
    eyebrows: Options.Eyebrows.DefaultNatural,
    mouthTypes: Options.MouthTypes.Default,
    skinColors: Options.SkinColors.Pale
});

export type AvatarType = typeof defaultAvatar;

export enum AvatarStyle {
    Circle = "Circle",
    Transparent = "Transparent"
}

export interface AvatarComponentProps {
    avatarStyle: "Circle" | "Transparent";
    style?: React.CSSProperties;
    top: Options.Top;
    topAccessory: Options.TopAccessory;
    hairColor: Options.HairColor;
    facialHair: Options.FacialHair;
    facialHairColor: Options.FacialHairColor;
    clothes: Options.Clothes;
    colorFabric: Options.ColorFabric;
    eyes: Options.Eyes;
    eyebrows: Options.Eyebrows;
    mouthTypes: Options.MouthTypes;
    skinColors: Options.SkinColors;
    clothesGraphic: Options.ClothesGraphic;
    hatColor: Options.HatColor;
    pieceType?: string;
    pieceSize?: string;
    viewBox?: string;
}

export interface FetchBulkAvatarsRequest {
    usernames: string[];
}

export interface FetchBulkAvatarsResponse {
    avatars: Record<string, AvatarType>;
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
