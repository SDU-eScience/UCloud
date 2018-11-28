import * as React from "react";
import * as Pagination from "Pagination";
import { connect } from "react-redux";
import {
    fetchApplications,
    setLoading,
    receiveApplications,
    fetchFavoriteApplications,
    setFavoritesLoading
} from "./Redux/ApplicationsActions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { Page } from "Types";
import { Application } from ".";
import { ApplicationsProps, ApplicationsOperations } from ".";
import { setErrorMessage } from "./Redux/ApplicationsActions";
import { favoriteApplicationFromPage } from "Utilities/ApplicationUtilities";
import { Cloud } from "Authentication/SDUCloudObject";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";
import { Dispatch } from "redux";
import { CardGroup } from "ui-components/Card";
import { ReduxObject, ApplicationReduxObject } from "DefaultObjects";
import { MainContainer } from "MainContainer/MainContainer";
import DetailedApplicationSearch from "./DetailedApplicationSearch";
import { Header, Pages } from "./Header";
import { ApplicationCardContainer, SlimApplicationCard } from "./Card";
import styled from "styled-components";
import { ContainerForText } from "ui-components";
import * as Heading from "ui-components/Heading";


class Applications extends React.Component<ApplicationsProps> {
    componentDidMount() {
        const { props } = this;
        props.updatePageTitle();
        props.prioritizeApplicationSearch();
        if (this.props.page.items.length === 0) {
            props.setLoading(true);
            props.fetchApplications(props.page.pageNumber, props.page.itemsPerPage);
            props.fetchFavorites(props.page.pageNumber, props.page.itemsPerPage);
        }
    }

    render() {
        const { page, loading, fetchApplications, favorites, onErrorDismiss, receiveApplications, ...props } = this.props;
        const favoriteApp = async (name: string, version: string) => {
            receiveApplications(await favoriteApplicationFromPage(name, version, page, Cloud));
            props.fetchFavorites(0, favorites.itemsPerPage);
        }

        const main = (
            <Pagination.List
                loading={loading}
                onErrorDismiss={onErrorDismiss}
                errorMessage={props.error}
                onRefresh={() => fetchApplications(page.pageNumber, page.itemsPerPage)}
                pageRenderer={({ items }: Page<Application>) =>
                    <ContainerForText>
                        <Heading.h2>Featured Applications</Heading.h2>
                        <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Mauris ut lacinia lorem, tincidunt efficitur tellus. Integer suscipit auctor orci, sit amet consectetur nibh vehicula sed. Curabitur nec metus eu massa efficitur auctor. Nullam porta bibendum mauris vitae ultricies. Aliquam eget dolor magna. Suspendisse a turpis erat. Aliquam pretium purus non felis porta interdum. Sed tempus faucibus urna pulvinar egestas. Cras id lectus sed elit fermentum interdum. Suspendisse ullamcorper nisl eu ultricies faucibus. Integer a odio neque. Vestibulum justo eros, vulputate ut nisl vitae, rutrum ornare lectus. Integer quam ex, vehicula in convallis nec, pretium eget neque. Pellentesque a justo a augue euismod aliquet. Proin pretium purus nec ligula finibus dignissim. </p>

                        <ApplicationCardContainer>
                            {items.map((app, index) =>
                                <SlimApplicationCard
                                    key={index}
                                    favoriteApp={favoriteApp}
                                    app={app}
                                    isFavorite={app.favorite}
                                />
                            )}
                        </ApplicationCardContainer>
                    </ContainerForText>
                }
                page={page}
                onItemsPerPageChanged={size => fetchApplications(0, size)}
                onPageChanged={pageNumber => fetchApplications(pageNumber, page.itemsPerPage)}
            />
        );

        const sidebar = (<DetailedApplicationSearch />);

        return (
            <MainContainer
                header={<Header selected={Pages.BROWSE} />}
                main={main}
                sidebar={sidebar}
            />
        );
    }
}

const mapDispatchToProps = (dispatch: Dispatch): ApplicationsOperations => ({
    prioritizeApplicationSearch: () => dispatch(setPrioritizedSearch("applications")),
    onErrorDismiss: () => dispatch(setErrorMessage()),
    updatePageTitle: () => dispatch(updatePageTitle("Applications")),
    setLoading: (loading: boolean) => dispatch(setLoading(loading)),
    setFavoritesLoading: (loading: boolean) => dispatch(setFavoritesLoading(loading)),
    fetchApplications: async (pageNumber: number, itemsPerPage: number) => dispatch(await fetchApplications(pageNumber, itemsPerPage)),
    receiveApplications: (applications: Page<Application>) => dispatch(receiveApplications(applications)),
    fetchFavorites: async (pageNumber: number, itemsPerPage: number) => dispatch(await fetchFavoriteApplications(pageNumber, itemsPerPage))
});

const mapStateToProps = ({ applications }: ReduxObject): ApplicationReduxObject & { favCount: number } => ({
    ...applications,
    favCount: applications.page.items.filter(it => it.favorite).length
});

export default connect(mapStateToProps, mapDispatchToProps)(Applications);