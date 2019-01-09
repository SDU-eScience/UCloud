import { default as Avataaar } from "avataaars";
import * as React from "react";
import * as Options from "./AvatarOptions";
import { MainContainer } from "MainContainer/MainContainer";
import { Select, Label, Box, Flex } from "ui-components";


interface AvataaarModificationState {
    top: Options.TopOptions
    accessories: Options.TopAccessoryOptions
    hairColor: Options.HairColorOptions
    facialHair: Options.FacialHairOptions
    facialHairColor: Options.FacialHairColorOptions
    clothes: Options.ClothesOptions
    clothesFabric: Options.ColorFabricOptions
    clothesGraphic: Options.ClothesGraphicOptions
    eyes: Options.EyeOptions
    eyebrow: Options.EyeBrowOptions
    mouth: Options.MouthTypeOptions
    skin: Options.SkinColorOptions
}

class AvataaarModification extends React.Component<void, AvataaarModificationState> {
    constructor(props) {
        super(props);
        this.state = {
            top: Options.Top.NoHair,
            accessories: Options.TopAccessory.Blank,
            hairColor: Options.HairColor.Auburn,
            facialHair: Options.FacialHair.BeardMedium,
            facialHairColor: Options.FacialHairColor.Auburn,
            clothes: Options.Clothes.BlazerShirt,
            clothesFabric: Options.ColorFabric.Black,
            clothesGraphic: Options.ClothesGraphic.Bat,
            eyes: Options.Eyes.Cry,
            eyebrow: Options.Eyebrows.DefaultNatural,
            mouth: Options.MouthTypes.Concerned,
            skin: Options.SkinColors.Pale
        }
    }

    private extractAvataaar() {
        return {

        }
    }

    render() {
        const { ...state } = this.state;
        return (
            <MainContainer


                main={
                    <>
                        <Flex>
                            <Box ml="auto" />
                            <Avataaar
                                style={{ height: "150px" }}
                                avatarStyle="circle"
                                topType={state.top}
                                accessoriesType={state.accessories}
                                hairColor={state.hairColor}
                                facialHairType={state.facialHair}
                                facialHairColor={state.facialHairColor}
                                clotheType={state.clothes}
                                clotheColor={state.clothesFabric}
                                graphicType={state.clothesGraphic}
                                eyeType={state.eyes}
                                eyebrowType={state.eyebrow}
                                mouthType={state.mouth}
                                skinColor={state.skin}
                            />
                            <Box mr="auto" />
                        </Flex>
                        <AvatarSelect
                            update={value => this.setState(() => ({ top: value }))}
                            options={Options.Top}
                            title="Top"
                            disabled={false}
                        />
                        <AvatarSelect
                            update={value => this.setState(() => ({ accessories: value }))}
                            options={Options.TopAccessory}
                            title="Accessories"
                            disabled={state.top === "Eyepatch"}
                        />
                        <AvatarSelect
                            update={value => this.setState(() => ({ hairColor: value }))}
                            options={Options.HairColor}
                            title="Hair color"
                            disabled={!state.top.includes("Long") || state.top === "LongHairFrida"}
                        />
                        <AvatarSelect
                            update={value => this.setState(() => ({ facialHair: value }))}
                            options={Options.FacialHair}
                            title="Facial Hair"
                            disabled={state.top === "Hijab"}
                        />
                        <AvatarSelect
                            update={value => this.setState(() => ({ facialHairColor: value }))}
                            options={Options.FacialHairColor}
                            title="Facial Hair Color"
                            disabled={state.facialHair === "Blank"}
                        />
                        <AvatarSelect
                            update={value => this.setState(() => ({ clothes: value }))}
                            options={Options.Clothes}
                            title="Clothes"
                            disabled={false}
                        />
                        <AvatarSelect
                            title="Clothes Fabric"
                            options={Options.ColorFabric}
                            update={value => this.setState(() => ({ clothesFabric: value }))}
                            disabled={state.clothes === "BlazerShirt" || state.clothes === "BlazerSweater"}
                        />
                        <AvatarSelect
                            title="Graphic"
                            update={value => this.setState(() => ({ clothesGraphic: value }))}
                            options={Options.ClothesGraphic}
                            disabled={state.clothes !== "GraphicShirt"}
                        />
                        <AvatarSelect
                            title="Eyes"
                            options={Options.Eyes}
                            update={value => this.setState(() => ({ eyes: value }))}
                            disabled={false}
                        />
                        <AvatarSelect
                            title="Eyebrow"
                            options={Options.Eyebrows}
                            update={value => this.setState(() => ({ eyebrow: value }))}
                            disabled={false}
                        />
                        <AvatarSelect
                            title="Mouth type"
                            options={Options.MouthTypes}
                            update={value => this.setState(() => ({ mouth: value }))}
                            disabled={false}
                        />
                        <AvatarSelect
                            title={"Skin color"}
                            options={Options.SkinColors}
                            update={value => this.setState(() => ({ skin: value }))}
                            disabled={false}
                        />
                    </>
                }
            />)
    }
}

interface AvatarSelect<T> {
    update: (value: keyof T) => void
    options: T
    title: string
    disabled: boolean
}

function AvatarSelect<T>({ update, options, title, disabled }: AvatarSelect<T>) {
    if (disabled) return null;
    return (
        <Label mt="0.8em">{title}
            <Select onChange={({ target: { value } }) => update(value as keyof T)}>
                {Object.keys(options).map(it => <option key={it}>{it}</option>)}
            </Select>
        </Label>
    )
}

export default AvataaarModification;