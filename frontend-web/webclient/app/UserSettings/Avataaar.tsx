import {Client} from "Authentication/HttpClientInstance";
import {Avatar} from "AvataaarLib";
import Spinner from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import {setActivePage, useTitle} from "Navigation/Redux/StatusActions";
import PromiseKeeper from "PromiseKeeper";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Box, Flex, Label, OutlineButton, Select} from "ui-components";
import {SidebarPages} from "ui-components/Sidebar";
import {findAvatarQuery} from "Utilities/AvatarUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import * as Options from "./AvatarOptions";
import {saveAvatar} from "./Redux/AvataaarActions";

type AvataaarModificationStateProps = AvatarType;

interface AvataaarModificationOperations {
    save: (avatar: AvatarType) => void;
    setActivePage: () => void;
}

function Modification(props: AvataaarModificationOperations): JSX.Element {
    const [avatar, setAvatar] = React.useState(defaultAvatar);
    const [loading, setLoading] = React.useState(true);

    useTitle("Edit Avatar");

    React.useEffect(() => {
        const promises = new PromiseKeeper();
        fetchAvatar(promises);
        return () => promises.cancelPromises();
    }, []);

    return (
        <MainContainer
            headerSize={220}
            header={(
                <>
                    <Flex>
                        <Box ml="auto" />
                        <Avatar
                            style={{height: "150px"}}
                            avatarStyle="circle"
                            {...avatar}
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
                        >
                            Update avatar
                        </OutlineButton>
                    </Flex>
                </>
            )}

            main={
                loading ? (<Spinner />) : (
                    <>
                        <AvatarSelect
                            defaultValue={avatar.top}
                            update={top => setAvatar({...avatar, top})}
                            options={Options.Top}
                            title="Top"
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={avatar.hatColor}
                            update={hatColor => setAvatar({...avatar, hatColor})}
                            options={Options.HatColor}
                            title="Hat color"
                            disabled={!["Turban", "Hijab", "WinterHat1", "WinterHat2", "WinterHat3", "WinterHat4"]
                                .includes(avatar.top)}
                        />
                        <AvatarSelect
                            defaultValue={avatar.topAccessory}
                            update={topAccessory => setAvatar({...avatar, topAccessory})}
                            options={Options.TopAccessory}
                            title="Accessories"
                            disabled={avatar.top === "Eyepatch"}
                        />
                        <AvatarSelect
                            defaultValue={avatar.hairColor}
                            update={hairColor => setAvatar({...avatar, hairColor})}
                            options={Options.HairColor}
                            title="Hair color"
                            disabled={!avatar.top.includes("Hair") || ["LongHairFrida", "LongHairShavedSides"].includes(avatar.top)}
                        />
                        <AvatarSelect
                            defaultValue={avatar.facialHair}
                            update={facialHair => setAvatar({...avatar, facialHair})}
                            options={Options.FacialHair}
                            title="Facial Hair"
                            disabled={avatar.top === "Hijab"}
                        />
                        <AvatarSelect
                            defaultValue={avatar.facialHairColor}
                            update={facialHairColor => setAvatar({...avatar, facialHairColor})}
                            options={Options.FacialHairColor}
                            title="Facial Hair Color"
                            disabled={avatar.facialHair === "Blank"}
                        />
                        <AvatarSelect
                            defaultValue={avatar.clothes}
                            update={clothes => setAvatar({...avatar, clothes})}
                            options={Options.Clothes}
                            title="Clothes"
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={avatar.colorFabric}
                            title="Clothes Fabric"
                            options={Options.ColorFabric}
                            update={colorFabric => setAvatar({...avatar, colorFabric})}
                            disabled={avatar.clothes === "BlazerShirt" || avatar.clothes === "BlazerSweater"}
                        />
                        <AvatarSelect
                            defaultValue={avatar.clothesGraphic}
                            title="Graphic"
                            update={clothesGraphic => setAvatar({...avatar, clothesGraphic})}
                            options={Options.ClothesGraphic}
                            disabled={avatar.clothes !== "GraphicShirt"}
                        />
                        <AvatarSelect
                            defaultValue={avatar.eyes}
                            title="Eyes"
                            options={Options.Eyes}
                            update={eyes => setAvatar({...avatar, eyes})}
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={avatar.eyebrows}
                            title="Eyebrow"
                            options={Options.Eyebrows}
                            update={eyebrows => setAvatar({...avatar, eyebrows})}
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={avatar.mouthTypes}
                            title="Mouth type"
                            options={Options.MouthTypes}
                            update={mouthTypes => setAvatar({...avatar, mouthTypes})}
                            disabled={false}
                        />
                        <AvatarSelect
                            defaultValue={avatar.skinColors}
                            title={"Skin color"}
                            options={Options.SkinColors}
                            update={skinColors => setAvatar({...avatar, skinColors})}
                            disabled={false}
                        />
                    </>
                )}
        />
    );

    async function fetchAvatar(promises: PromiseKeeper): Promise<void> {
        try {
            const r = await promises.makeCancelable(Client.get<AvatarType>(findAvatarQuery, undefined)).promise;
            setAvatar(r.response);
        } catch (e) {
            if (!e.isCanceled)
                snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred fetching current Avatar"), false);
        } finally {
            setLoading(false);
        }
    }
}

interface AvatarSelect<T1, T2> {
    update: (value: T1) => void;
    defaultValue: T1;
    options: T2;
    title: string;
    disabled: boolean;
}

function AvatarSelect<T1 extends string, T2>({
    update,
    options,
    title,
    disabled,
    defaultValue
}: AvatarSelect<T1, T2>): JSX.Element | null {
    if (disabled) return null;
    return (
        <Label mt="0.8em">{title}
            <Select
                defaultValue={defaultValue}
                onChange={e => update(e.target.value as T1)}
            >
                {Object.keys(options).map(it => <option key={it}>{it}</option>)}
            </Select>
        </Label>
    );
}

const mapStateToProps = ({avatar}: ReduxObject): AvataaarModificationStateProps => avatar;
const mapDispatchToProps = (dispatch: Dispatch): AvataaarModificationOperations => ({
    save: async avatar => dispatch(await saveAvatar(avatar)),
    setActivePage: () => dispatch(setActivePage(SidebarPages.None))
});

const defaultAvatar = ({
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

type AvatarType = typeof defaultAvatar;
export default connect(mapStateToProps, mapDispatchToProps)(Modification);
export {defaultAvatar, AvatarType};
