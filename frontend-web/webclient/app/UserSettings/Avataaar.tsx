import { default as Avataaar } from "avataaars";
import * as React from "react";
import * as Options from "./AvatarOptions";
import { MainContainer } from "MainContainer/MainContainer";
import { Select, Label, Box, Flex } from "ui-components";
import { connect } from "react-redux";
import { ReduxObject } from "DefaultObjects";


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

type AvataaarModificationStateProps = typeof defaultAvatar

class AvataaarModification extends React.Component<AvataaarModificationStateProps, AvataaarModificationState> {
    constructor(props) {
        super(props);
        this.state = {
            top: this.props.top,
            accessories: this.props.accessories,
            hairColor: this.props.hairColor,
            facialHair: this.props.facialHair,
            facialHairColor: this.props.facialHairColor,
            clothes: this.props.clothes,
            clothesFabric: this.props.clothesFabric,
            clothesGraphic: this.props.clothesGraphic,
            eyes: this.props.eyes,
            eyebrow: this.props.eyebrow,
            mouth: this.props.mouth,
            skin: this.props.skin
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
                            defaultValue={state.top}
                            update={value => this.setState(() => ({ top: value }))}
                            options={Options.Top}
                            title="Top"
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={state.accessories}
                            update={value => this.setState(() => ({ accessories: value }))}
                            options={Options.TopAccessory}
                            title="Accessories"
                            disabled={state.top === "Eyepatch"}
                        />
                        <AvatarSelect
                            defaultValue={state.hairColor}
                            update={value => this.setState(() => ({ hairColor: value }))}
                            options={Options.HairColor}
                            title="Hair color"
                            disabled={!state.top.includes("Long") || state.top === "LongHairFrida"}
                        />
                        <AvatarSelect
                            defaultValue={state.facialHair}
                            update={value => this.setState(() => ({ facialHair: value }))}
                            options={Options.FacialHair}
                            title="Facial Hair"
                            disabled={state.top === "Hijab"}
                        />
                        <AvatarSelect
                            defaultValue={state.facialHairColor}
                            update={value => this.setState(() => ({ facialHairColor: value }))}
                            options={Options.FacialHairColor}
                            title="Facial Hair Color"
                            disabled={state.facialHair === "Blank"}
                        />
                        <AvatarSelect
                            defaultValue={state.clothes}
                            update={value => this.setState(() => ({ clothes: value }))}
                            options={Options.Clothes}
                            title="Clothes"
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={state.clothesFabric}
                            title="Clothes Fabric"
                            options={Options.ColorFabric}
                            update={value => this.setState(() => ({ clothesFabric: value }))}
                            disabled={state.clothes === "BlazerShirt" || state.clothes === "BlazerSweater"}
                        />
                        <AvatarSelect
                            defaultValue={state.clothesGraphic}
                            title="Graphic"
                            update={value => this.setState(() => ({ clothesGraphic: value }))}
                            options={Options.ClothesGraphic}
                            disabled={state.clothes !== "GraphicShirt"}
                        />
                        <AvatarSelect
                            defaultValue={state.eyes}
                            title="Eyes"
                            options={Options.Eyes}
                            update={value => this.setState(() => ({ eyes: value }))}
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={state.eyebrow}
                            title="Eyebrow"
                            options={Options.Eyebrows}
                            update={value => this.setState(() => ({ eyebrow: value }))}
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={state.mouth}
                            title="Mouth type"
                            options={Options.MouthTypes}
                            update={value => this.setState(() => ({ mouth: value }))}
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={state.skin}
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
    defaultValue: keyof T
    options: T
    title: string
    disabled: boolean
}

function AvatarSelect<T>({ update, options, title, disabled, defaultValue }: AvatarSelect<T>) {
    if (disabled) return null;
    return (
        <Label mt="0.8em">{title}
            <Select defaultValue={defaultValue} onChange={({ target: { value } }) => update(value as keyof T)}>
                {Object.keys(options).map(it => <option key={it}>{it}</option>)}
            </Select>
        </Label>
    )
}

const mapStateToProps = ({ avatar }: ReduxObject) => avatar
const mapDispatchToProps = ({ }) => ({})

const defaultAvatar = ({
    top: Options.Top.NoHair,
    accessories: Options.TopAccessory.Blank,
    hairColor: Options.HairColor.Auburn,
    facialHair: Options.FacialHair.Blank,
    facialHairColor: Options.FacialHairColor.Auburn,
    clothes: Options.Clothes.BlazerShirt,
    clothesFabric: Options.ColorFabric.Black,
    clothesGraphic: Options.ClothesGraphic.Bat,
    eyes: Options.Eyes.Default,
    eyebrow: Options.Eyebrows.DefaultNatural,
    mouth: Options.MouthTypes.Default,
    skin: Options.SkinColors.Pale
});

export default connect(mapStateToProps, mapDispatchToProps)(AvataaarModification);
export { defaultAvatar }