import * as React from "react";
import { connect } from "react-redux";
import { Dispatch } from "redux";
import { ReduxObject } from "DefaultObjects";
import * as Heading from "ui-components/Heading";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";
import { setErrorMessage } from "./Redux/ApplicationsActions";
import { fetchFavoriteApplications, setFavoritesLoading } from "./Redux/ApplicationsActions";
import { Application } from "Applications";
import { Page } from "Types";
import * as Pagination from "Pagination";
import { Navigation, Pages } from "./Navigation";
import { SlimApplicationCard, ApplicationCardContainer } from "./Card";
import { MainContainer } from "MainContainer/MainContainer";
import { ContainerForText } from "ui-components";

interface InstalledOperations {
    onComponentDidMount: () => void
    onErrorDismiss: () => void
    fetchItems: (pageNumber: number, itemsPerPage: number) => void
}

interface InstalledStateProps {
    page: Page<Application>
    loading: boolean
    error?: string
}

type InstalledProps = InstalledOperations & InstalledStateProps;

class Installed extends React.Component<InstalledProps> {
    componentDidMount() {
        const { props } = this;
        props.onComponentDidMount()

        if (props.page.items.length === 0) {
            props.fetchItems(0, 25);
        }
    }

    render() {
        const { page } = this.props;
        const main = (
            <Pagination.List
                loading={this.props.loading}
                onErrorDismiss={this.props.onErrorDismiss}
                errorMessage={this.props.error}
                onRefresh={() => this.props.fetchItems(page.pageNumber, page.itemsPerPage)}
                page={this.props.page}
                onItemsPerPageChanged={size => this.props.fetchItems(0, size)}
                onPageChanged={pageNumber => this.props.fetchItems(pageNumber, page.itemsPerPage)}
                pageRenderer={page => <InstalledPage page={page} />}
            />
        );

        return (
            <MainContainer
                header={<Navigation selected={Pages.INSTALLED} />}
                main={main}
                sidebar={null}
            />
        );
    }
}

const InstalledPage: React.StatelessComponent<{ page: Page<Application> }> = props => (
    <ContainerForText>
        <Heading.h2>Installed Applications</Heading.h2>
        <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Mauris ut lacinia lorem, tincidunt efficitur tellus. Integer suscipit auctor orci, sit amet consectetur nibh vehicula sed. Curabitur nec metus eu massa efficitur auctor. Nullam porta bibendum mauris vitae ultricies. Aliquam eget dolor magna. Suspendisse a turpis erat. Aliquam pretium purus non felis porta interdum. Sed tempus faucibus urna pulvinar egestas. Cras id lectus sed elit fermentum interdum. Suspendisse ullamcorper nisl eu ultricies faucibus. Integer a odio neque. Vestibulum justo eros, vulputate ut nisl vitae, rutrum ornare lectus. Integer quam ex, vehicula in convallis nec, pretium eget neque. Pellentesque a justo a augue euismod aliquet. Proin pretium purus nec ligula finibus dignissim. </p>

        <ApplicationCardContainer>
            {props.page.items.map((it, idx) => (
                <SlimApplicationCard app={it} key={idx} linkToRun />)
            )}
        </ApplicationCardContainer>
    </ContainerForText>
);

const mapDispatchToProps = (dispatch: Dispatch): InstalledOperations => ({
    onComponentDidMount: () => {
        dispatch(setPrioritizedSearch("applications"))
        dispatch(updatePageTitle("Installed Applications"))
        dispatch(setErrorMessage());
    },

    onErrorDismiss: () => {
        dispatch(setErrorMessage())
    },

    fetchItems: async (pageNumber: number, itemsPerPage: number) => {
        dispatch(setFavoritesLoading(true));
        dispatch(await fetchFavoriteApplications(pageNumber, itemsPerPage));
    }
});

const mapStateToProps = ({ applications }: ReduxObject): InstalledStateProps => ({
    page: applications.favorites,
    loading: applications.favoritesLoading,
    error: applications.error
});

export default connect(mapStateToProps, mapDispatchToProps)(Installed);