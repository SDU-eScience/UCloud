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
import { setActivePage } from "Navigation/Redux/StatusActions";
import { SidebarPages } from "ui-components/Sidebar";

type AvataaarModificationStateProps = AvatarType;
interface AvataaarModificationOperations {
    save: (avatar: AvatarType) => void
    findAvatar: () => void
    setActivePage: () => void
}

function Modification(props: AvataaarModificationOperations) {
    const [state, setState] = React.useState({ ...defaultAvatar, loading: true })
    React.useEffect(() => {
        const promises = new PromiseKeeper();
        promises.makeCancelable(Cloud.get<AvatarType>(findAvatarQuery)).promise
            .then(it => setState({ ...it.response, loading: false }))
            .catch(it => {
                if (!it.isCanceled) {
                    failureNotification("An error occurred fetching current Avatar");
                    setState({ ...state, loading: false });
                }
            });
        return () => promises.cancelPromises();
    }, []);

    const { loading, ...avatar } = state;

    return (
        <MainContainer
            headerSize={220}
            header={<>
                <Flex>
                    <Box ml="auto" />
                    <Avataaar
                        style={{ height: "150px" }}
                        avatarStyle="circle"
                        topType={avatar.top}
                        accessoriesType={avatar.topAccessory}
                        hairColor={avatar.hairColor}
                        facialHairType={avatar.facialHair}
                        facialHairColor={avatar.facialHairColor}
                        clotheType={avatar.clothes}
                        clotheColor={avatar.colorFabric}
                        graphicType={avatar.clothesGraphic}
                        eyeType={avatar.eyes}
                        eyebrowType={avatar.eyebrows}
                        mouthType={avatar.mouthTypes}
                        skinColor={avatar.skinColors}
                    />
                    <Box mr="auto" />
                </Flex>
                <Flex>
                    <OutlineButton
                        ml="auto"
                        mr="auto"
                        onClick={() => props.save(avatar)}
                        mt="5px"
                        mb="5px"
                        color="blue"
                    >Update avatar</OutlineButton>
                </Flex></>}

            main={
                loading ? (<Spinner size={24} />) : <>
                    <AvatarSelect
                        defaultValue={avatar.top}
                        update={value => setState({ loading, ...avatar, top: value })}
                        options={Options.Top}
                        title="Top"
                        disabled={false}
                    />
                    <AvatarSelect
                        defaultValue={state.topAccessory}
                        update={value => setState({ loading, ...avatar, topAccessory: value })}
                        options={Options.TopAccessory}
                        title="Accessories"
                        disabled={state.top === "Eyepatch"}
                    />
                    <AvatarSelect
                        defaultValue={state.hairColor}
                        update={value => setState({ loading, ...avatar, hairColor: value })}
                        options={Options.HairColor}
                        title="Hair color"
                        disabled={!state.top.includes("Hair") || state.top === "LongHairFrida"}
                    />
                    <AvatarSelect
                        defaultValue={state.facialHair}
                        update={value => setState({ loading, ...avatar, facialHair: value })}
                        options={Options.FacialHair}
                        title="Facial Hair"
                        disabled={state.top === "Hijab"}
                    />
                    <AvatarSelect
                        defaultValue={state.facialHairColor}
                        update={value => setState({ loading, ...avatar, facialHairColor: value })}
                        options={Options.FacialHairColor}
                        title="Facial Hair Color"
                        disabled={state.facialHair === "Blank"}
                    />
                    <AvatarSelect
                        defaultValue={state.clothes}
                        update={value => setState({ loading, ...avatar, clothes: value })}
                        options={Options.Clothes}
                        title="Clothes"
                        disabled={false}
                    />
                    <AvatarSelect
                        defaultValue={state.colorFabric}
                        title="Clothes Fabric"
                        options={Options.ColorFabric}
                        update={value => setState({ loading, ...avatar, colorFabric: value })}
                        disabled={state.clothes === "BlazerShirt" || state.clothes === "BlazerSweater"}
                    />
                    <AvatarSelect
                        defaultValue={state.clothesGraphic}
                        title="Graphic"
                        update={value => setState({ loading, ...avatar, clothesGraphic: value })}
                        options={Options.ClothesGraphic}
                        disabled={state.clothes !== "GraphicShirt"}
                    />
                    <AvatarSelect
                        defaultValue={state.eyes}
                        title="Eyes"
                        options={Options.Eyes}
                        update={value => setState({ loading, ...avatar, eyes: value })}
                        disabled={false}
                    />
                    <AvatarSelect
                        defaultValue={state.eyebrows}
                        title="Eyebrow"
                        options={Options.Eyebrows}
                        update={value => setState({ loading, ...avatar, eyebrows: value })}
                        disabled={false}
                    />
                    <AvatarSelect
                        defaultValue={state.mouthTypes}
                        title="Mouth type"
                        options={Options.MouthTypes}
                        update={value => setState({ loading, ...avatar, mouthTypes: value })}
                        disabled={false}
                    />
                    <AvatarSelect
                        defaultValue={state.skinColors}
                        title={"Skin color"}
                        options={Options.SkinColors}
                        update={value => setState({ loading, ...avatar, skinColors: value })}
                        disabled={false}
                    />
                </>
            }
        />)
}

interface AvatarSelect<T1, T2> {
    update: (value: T1) => void
    defaultValue: T1
    options: T2
    title: string
    disabled: boolean
}

function AvatarSelect<T1, T2>({ update, options, title, disabled, defaultValue }: AvatarSelect<T1, T2>) {
    if (disabled) return null;
    return (
        <Label mt="0.8em">{title}
            <Select defaultValue={defaultValue} onChange={({ target: { value } }: { target: { value: T1 } }) => update(value)}>
                {Object.keys(options).map(it => <option key={it}>{it}</option>)}
            </Select>
        </Label>
    )
}

const mapStateToProps = ({ avatar }: ReduxObject) => avatar;
const mapDispatchToProps = (dispatch: Dispatch): AvataaarModificationOperations => ({
    save: async avatar => dispatch(await saveAvatar(avatar)),
    findAvatar: async () => dispatch(await findAvatar()),
    setActivePage: () => dispatch(setActivePage(SidebarPages.None))
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
export default connect<AvataaarModificationStateProps, AvataaarModificationOperations>(mapStateToProps, mapDispatchToProps)(Modification);
export { defaultAvatar, AvatarType }