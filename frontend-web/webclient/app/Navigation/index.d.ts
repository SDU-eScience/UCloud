import {HeaderSearchType} from "DefaultObjects";
import {defaultAvatar} from "UserSettings/Avataaar";

interface HeaderStateToProps {
    prioritizedSearch: HeaderSearchType;
    refresh?: () => void;
    avatar: typeof defaultAvatar;
    spin: boolean;
    statusLoading: boolean;
}
