import * as React from "react";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import Avatar from "../../app/AvataaarLib/index";
import {UserAvatar} from "../../app/AvataaarLib/UserAvatar";
import theme from "../../app/ui-components/theme";
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
} from "../../app/UserSettings/AvatarOptions";

describe("Avatar", () => {
    test("Avatar component", () => {
        expect(create(
            <Avatar
                accessoriesType={TopAccessory.Blank}
                avatarStyle="circle"
                clotheColor={ColorFabric.Black}
                eyeType={Eyes.Close}
                clotheType={Clothes.BlazerShirt}
                eyebrowType={Eyebrows.Angry}
                facialHairColor={FacialHairColor.Black}
                facialHairType={FacialHair.BeardLight}
                graphicType={ClothesGraphic.Bat}
                hairColor={HairColor.Black}
                hatColor={HatColor.Black}
                mouthType={MouthTypes.Concerned}
                skinColor={SkinColors.Black}
                topType={Top.Eyepatch}
            />
        )).toMatchSnapshot();
    });

    test("UserAvatar", () => {
        expect(create(
            <ThemeProvider theme={theme}>
                <UserAvatar
                    avatar={{
                        topAccessory: TopAccessory.Blank,
                        colorFabric: ColorFabric.Black,
                        eyes: Eyes.Close,
                        clothes: Clothes.BlazerShirt,
                        eyebrows: Eyebrows.Angry,
                        facialHairColor: FacialHairColor.Black,
                        facialHair: FacialHair.BeardLight,
                        clothesGraphic: ClothesGraphic.Bat,
                        hairColor: HairColor.Black,
                        hatColor: HatColor.Black,
                        mouthTypes: MouthTypes.Concerned,
                        skinColors: SkinColors.Black,
                        top: Top.Eyepatch,
                    }}
                />
            </ThemeProvider>
        ));
    });
});

export interface AvatarComponentProps {
    avatarStyle: string;
    style?: React.CSSProperties;
    topType: Top;
    accessoriesType: TopAccessory;
    hairColor: HairColor;
    facialHairType: FacialHair;
    facialHairColor: FacialHairColor;
    clotheType: Clothes;
    hatColor: HatColor;
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
