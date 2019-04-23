import { Snackbar } from "ui-components/Snackbar";
import * as React from "react";
import { connect } from "react-redux";
import { IconName } from "ui-components/Icon";
import { Flex, Icon } from "ui-components";
import { ReduxObject } from "DefaultObjects";
import { Dispatch } from "redux";
import { removeSnack, SnackbarAction } from "./Redux/SnackbarsActions";
import { ThemeColor } from "ui-components/theme";
import { Object } from "./Redux";

interface RemoveSnackOperation {
    removeSnack: (number: number) => void
}

export interface AddSnackOperation {
    addSnack: (snack: Snack) => void
}

type SnackbarOperations = RemoveSnackOperation;
interface IconColorAndName { name: IconName, color: ThemeColor, color2: ThemeColor }
class Snackbars extends React.Component<Object & SnackbarOperations> {

    // FIXME: Couldn't these be merged rather easily?
    private renderCustom(customSnack: CustomSnack) {
        return <Flex><Icon color="white" color2="white" name={customSnack.icon} pr="10px" />{customSnack.message}</Flex>
    }

    private renderDefault(defaultSnack: DefaultSnack) {
        const icon = Snackbars.iconNameAndColorFromSnack(defaultSnack.type);
        return <Flex><Icon pr="10px" {...icon} />{defaultSnack.message}</Flex>
    }
    // FIXME END

    shouldComponentUpdate(nextProps: Object) {
        if (nextProps.snackbar.length === this.props.snackbar.length) return false;
        const [snack] = nextProps.snackbar;
        if (!!snack) setTimeout(() => this.props.removeSnack(snack.id!), snack.lifetime);
        return true;
    }

    private static iconNameAndColorFromSnack(type: Exclude<SnackType, SnackType.Custom>): IconColorAndName {
        switch (type) {
            case SnackType.Success: return { name: "check", color: "white", color2: "white" };
            case SnackType.Information: return { name: "info", color: "black", color2: "white" };
            case SnackType.Failure: return { name: "close", color: "white", color2: "white" };
        }
    }

    private renderSnack = (snack: Snack) =>
        snack.type !== SnackType.Custom ? this.renderDefault(snack) : this.renderCustom(snack);

    render() {
        const { snackbar } = this.props;
        if (!snackbar.length) return null;
        const [currentSnack] = snackbar;
        return (<Snackbar onClick={() => this.props.removeSnack(currentSnack.id!)} visible={true}>
            {this.renderSnack(currentSnack)}
        </Snackbar>);
    }
}

const mapStateToProps = ({ snackbar }: ReduxObject): Object => snackbar;
const mapDispatchToProps = (dispatch: Dispatch<SnackbarAction>): SnackbarOperations => ({
    removeSnack: index => dispatch(removeSnack(index))
});

export default connect(mapStateToProps, mapDispatchToProps)(Snackbars);

export const enum SnackType {
    Success,
    Information,
    Failure,
    Custom
}

interface DefaultSnack {
    message: string
    type: SnackType.Success | SnackType.Information | SnackType.Failure
    id?: number
    lifetime?: number
}

interface CustomSnack {
    message: string
    type: SnackType.Custom
    id?: number
    lifetime?: number
    icon: IconName
}

export type Snack = CustomSnack | DefaultSnack;