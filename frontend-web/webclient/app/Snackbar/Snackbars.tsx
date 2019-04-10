import { Snackbar } from "ui-components/Snackbar";
import * as React from "react";
import { connect } from "react-redux";
import { IconName } from "ui-components/Icon";
import { Flex, Icon } from "ui-components";
import { ReduxObject } from "DefaultObjects";
import { Dispatch } from "redux";
import { removeSnack, SnackbarAction, addSnack } from "./Redux/SnackbarsActions";
import { ThemeColor } from "ui-components/theme";

interface RemoveSnackOperation {
    removeSnack: (number: number) => void
}

export interface AddSnackOperation {
    addSnack: (snack: Snack) => void
}

type SnackbarOperations = AddSnackOperation & RemoveSnackOperation;
interface IconColorAndName { name: IconName, color: ThemeColor, color2: ThemeColor }
class Snackbars extends React.Component<{ snackbar: Snack[] } & SnackbarOperations> {

    private renderCustom(customSnack: CustomSnack) {
        return <Flex><Icon color="white" color2="white" name={customSnack.icon} pr="10px" />{customSnack.message}</Flex>
    }

    private renderDefault(defaultSnack: DefaultSnack) {
        const icon = Snackbars.iconNameAndColorFromSnack(defaultSnack.type);
        return <Flex><Icon pr="10px" {...icon} />{defaultSnack.message}</Flex>
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
        return (<Snackbar onClick={() => this.props.removeSnack(0)} visible={true} width="auto" minWidth="250px">
            {this.renderSnack(currentSnack)}
        </Snackbar>);
    }
}

const mapStateToProps = ({ snackbar }: ReduxObject): { snackbar: Snack[] } => snackbar;
const mapDispatchToProps = (dispatch: Dispatch<SnackbarAction>): SnackbarOperations => ({
    removeSnack: index => dispatch(removeSnack(index)),
    // FIXME: Likely not necessary.
    addSnack: snack => dispatch(addSnack(snack))
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
}

interface CustomSnack {
    message: string
    type: SnackType.Custom
    icon: IconName
}

export type Snack = CustomSnack | DefaultSnack;
type Snacks = Snack[];

const mockSnacks: Snacks = [{
    message: "I am a success",
    type: SnackType.Success
}, {
    message: "I am information",
    type: SnackType.Information
}, {
    message: "I am a failure",
    type: SnackType.Failure
}, {
    message: "I am custom",
    type: SnackType.Custom,
    icon: "apps"
}];