import { default as Avataaar } from "avataaars";
import * as React from "react";
import * as Options from "./AvatarOptions";
import { MainContainer } from "MainContainer/MainContainer";
import { Select, Label, Box, Flex, OutlineButton } from "ui-components";
import Spinner from "LoadingIcon/LoadingIcon";
import { connect } from "react-redux";
import { ReduxObject } from "DefaultObjects";
import { Dispatch } from "redux";
import { findAvatar, saveAvatar } from "./Redux/AvataaarActions";
import PromiseKeeper from "PromiseKeeper";
import { findAvatarQuery } from "Utilities/AvatarUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { failureNotification } from "UtilityFunctions";



interface AvataaarModificationState {
    top: Options.TopOptions
    topAccessory: Options.TopAccessoryOptions
    hairColor: Options.HairColorOptions
    facialHair: Options.FacialHairOptions
    facialHairColor: Options.FacialHairColorOptions
    clothes: Options.ClothesOptions
    colorFabric: Options.ColorFabricOptions
    clothesGraphic: Options.ClothesGraphicOptions
    eyes: Options.EyeOptions
    eyebrows: Options.EyeBrowOptions
    mouthTypes: Options.MouthTypeOptions
    skinColors: Options.SkinColorOptions
    promises: PromiseKeeper
    loading: boolean
}

type AvataaarModificationStateProps = AvatarType;
interface AvataaarModificationOperations {
    save: (avatar: AvatarType) => void
    findAvatar: () => void
}

type AvataaarModificationProps = AvataaarModificationStateProps & AvataaarModificationOperations;

class AvataaarModification extends React.Component<AvataaarModificationProps, AvataaarModificationState> {
    constructor(props) {
        super(props);
        this.state = {
            ...props,
            loading: true,
            promises: new PromiseKeeper()
        };
        this.fetchCurrentAvatar()
    }

    private fetchCurrentAvatar = () =>
        this.state.promises.makeCancelable(Cloud.get(findAvatarQuery)).promise
            .then(it => this.setState(() => ({ ...it.response, loading: false })))
            .catch(it => (failureNotification("An error occurred fetching current Avatar"), this.setState(() => ({ loading: false }))))

    private save() {
        this.props.save(this.state as AvatarType)
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    render() {
        const { ...state } = this.state;
        return (
            <MainContainer
                headerSize={220}
                header={<>
                    <Flex>
                        <Box ml="auto" />
                        <Avataaar
                            style={{ height: "150px" }}
                            avatarStyle="circle"
                            topType={state.top}
                            accessoriesType={state.topAccessory}
                            hairColor={state.hairColor}
                            facialHairType={state.facialHair}
                            facialHairColor={state.facialHairColor}
                            clotheType={state.clothes}
                            clotheColor={state.colorFabric}
                            graphicType={state.clothesGraphic}
                            eyeType={state.eyes}
                            eyebrowType={state.eyebrows}
                            mouthType={state.mouthTypes}
                            skinColor={state.skinColors}
                        />
                        <Box mr="auto" />
                    </Flex>
                    <Flex>
                        <OutlineButton
                            ml="auto"
                            mr="auto"
                            onClick={() => this.save()}
                            mt="5px"
                            mb="5px"
                            color="blue"
                        >Update avatar</OutlineButton>
                    </Flex></>}

                main={
                    this.state.loading ? (<Spinner size={24} />) : <>
                        <AvatarSelect
                            defaultValue={state.top}
                            update={value => this.setState(() => ({ top: value }))}
                            options={Options.Top}
                            title="Top"
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={state.topAccessory}
                            update={value => this.setState(() => ({ topAccessory: value }))}
                            options={Options.TopAccessory}
                            title="Accessories"
                            disabled={state.top === "Eyepatch"}
                        />
                        <AvatarSelect
                            defaultValue={state.hairColor}
                            update={value => this.setState(() => ({ hairColor: value }))}
                            options={Options.HairColor}
                            title="Hair color"
                            disabled={!state.top.includes("Hair") || state.top === "LongHairFrida"}
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
                            defaultValue={state.colorFabric}
                            title="Clothes Fabric"
                            options={Options.ColorFabric}
                            update={value => this.setState(() => ({ colorFabric: value }))}
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
                            defaultValue={state.eyebrows}
                            title="Eyebrow"
                            options={Options.Eyebrows}
                            update={value => this.setState(() => ({ eyebrows: value }))}
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={state.mouthTypes}
                            title="Mouth type"
                            options={Options.MouthTypes}
                            update={value => this.setState(() => ({ mouthTypes: value }))}
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={state.skinColors}
                            title={"Skin color"}
                            options={Options.SkinColors}
                            update={value => this.setState(() => ({ skinColors: value }))}
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

const mapStateToProps = ({ avatar }: ReduxObject) => avatar;
const mapDispatchToProps = (dispatch: Dispatch): AvataaarModificationOperations => ({
    save: async avatar => dispatch(await saveAvatar(avatar)),
    findAvatar: async () => dispatch(await findAvatar())
});

const defaultAvatar = ({
    top: Options.Top.NoHair,
    topAccessory: Options.TopAccessory.Blank,
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

type AvatarType = typeof defaultAvatar;
export default connect<AvataaarModificationStateProps, AvataaarModificationOperations>(mapStateToProps, mapDispatchToProps)(AvataaarModification);
export { defaultAvatar, AvatarType }