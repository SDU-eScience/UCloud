import { default as Avataaar } from "avataaars";
import * as React from "react";
import * as Options from "./AvatarOptions";
import { MainContainer } from "MainContainer/MainContainer";
import { Select, Label, Box, Flex, OutlineButton, Error } from "ui-components";
import Spinner from "LoadingIcon/LoadingIcon";
import { connect } from "react-redux";
import { ReduxObject } from "DefaultObjects";
import { Dispatch } from "redux";
import { saveAvatar, setAvatarError } from "./Redux/AvataaarActions";
import PromiseKeeper from "PromiseKeeper";
import { findAvatarQuery } from "Utilities/AvatarUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { failureNotification } from "UtilityFunctions";
import { setActivePage } from "Navigation/Redux/StatusActions";
import { SidebarPages } from "ui-components/Sidebar";

type AvataaarModificationStateProps = AvatarType;
interface AvataaarModificationOperations {
    save: (avatar: AvatarType) => void
    setActivePage: () => void
    setError: (err?: string) => void
}

function Modification(props: AvataaarModificationOperations & { error?: string }) {
    const [avatar, setAvatar] = React.useState(defaultAvatar)
    const [loading, setLoading] = React.useState(true)
    React.useEffect(() => {
        const promises = new PromiseKeeper();
        promises.makeCancelable(Cloud.get<AvatarType>(findAvatarQuery)).promise
            .then(({ response }) => setAvatar(response))
            .catch(it => {
                if (!it.isCanceled) {
                    failureNotification("An error occurred fetching current Avatar");
                }
            }).finally(() => setLoading(false));
        return () => promises.cancelPromises();
    }, []);

    return (
        <MainContainer
            headerSize={220 + (!props.error ? 0 : 60)}
            header={<>
                <Error error={props.error} clearError={() => props.setError()} />
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
                        update={value => setAvatar({ ...avatar, top: value })}
                        options={Options.Top}
                        title="Top"
                        disabled={false}
                    />
                    <AvatarSelect
                        defaultValue={avatar.topAccessory}
                        update={value => setAvatar({ ...avatar, topAccessory: value })}
                        options={Options.TopAccessory}
                        title="Accessories"
                        disabled={avatar.top === "Eyepatch"}
                    />
                    <AvatarSelect
                        defaultValue={avatar.hairColor}
                        update={value => setAvatar({ ...avatar, hairColor: value })}
                        options={Options.HairColor}
                        title="Hair color"
                        disabled={!avatar.top.includes("Hair") || avatar.top === "LongHairFrida"}
                    />
                    <AvatarSelect
                        defaultValue={avatar.facialHair}
                        update={value => setAvatar({ ...avatar, facialHair: value })}
                        options={Options.FacialHair}
                        title="Facial Hair"
                        disabled={avatar.top === "Hijab"}
                    />
                    <AvatarSelect
                        defaultValue={avatar.facialHairColor}
                        update={value => setAvatar({ ...avatar, facialHairColor: value })}
                        options={Options.FacialHairColor}
                        title="Facial Hair Color"
                        disabled={avatar.facialHair === "Blank"}
                    />
                    <AvatarSelect
                        defaultValue={avatar.clothes}
                        update={value => setAvatar({ ...avatar, clothes: value })}
                        options={Options.Clothes}
                        title="Clothes"
                        disabled={false}
                    />
                    <AvatarSelect
                        defaultValue={avatar.colorFabric}
                        title="Clothes Fabric"
                        options={Options.ColorFabric}
                        update={value => setAvatar({ ...avatar, colorFabric: value })}
                        disabled={avatar.clothes === "BlazerShirt" || avatar.clothes === "BlazerSweater"}
                    />
                    <AvatarSelect
                        defaultValue={avatar.clothesGraphic}
                        title="Graphic"
                        update={value => setAvatar({ ...avatar, clothesGraphic: value })}
                        options={Options.ClothesGraphic}
                        disabled={avatar.clothes !== "GraphicShirt"}
                    />
                    <AvatarSelect
                        defaultValue={avatar.eyes}
                        title="Eyes"
                        options={Options.Eyes}
                        update={value => setAvatar({ ...avatar, eyes: value })}
                        disabled={false}
                    />
                    <AvatarSelect
                        defaultValue={avatar.eyebrows}
                        title="Eyebrow"
                        options={Options.Eyebrows}
                        update={value => setAvatar({ ...avatar, eyebrows: value })}
                        disabled={false}
                    />
                    <AvatarSelect
                        defaultValue={avatar.mouthTypes}
                        title="Mouth type"
                        options={Options.MouthTypes}
                        update={value => setAvatar({ ...avatar, mouthTypes: value })}
                        disabled={false}
                    />
                    <AvatarSelect
                        defaultValue={avatar.skinColors}
                        title={"Skin color"}
                        options={Options.SkinColors}
                        update={value => setAvatar({ ...avatar, skinColors: value })}
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
    setActivePage: () => dispatch(setActivePage(SidebarPages.None)),
    setError: error => dispatch(setAvatarError(error))
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