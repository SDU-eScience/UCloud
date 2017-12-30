import React from 'react';
import pubsub from 'pubsub-js';

class LayoutsBoxed extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <div className="container container-md">
                    <div className="card">
                        <div className="card-body">
                            <h2 className="text-center">Big card with cards</h2>
                            <div className="row">
                                <div className="col-md-6">
                                    <div className="card">
                                        <div className="card-heading">
                                            <div className="pull-right">
                                                <button type="button" className="btn btn-default btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                            </div>
                                            <div className="card-title">Morbi a odio</div>
                                        </div>
                                        <div className="card-body">Fusce pellentesque congue justo in rutrum. Ut facilisis orci lorem. Cras eget orci sit amet eros interdum malesuada. </div>
                                    </div>
                                </div>
                                <div className="col-md-6">
                                    <div className="card">
                                        <div className="card-heading">
                                            <div className="pull-right">
                                                <button type="button" className="btn btn-default btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                            </div>
                                            <div className="card-title">Morbi a odio</div>
                                        </div>
                                        <div className="card-body">Fusce pellentesque congue justo in rutrum. Ut facilisis orci lorem. Cras eget orci sit amet eros interdum malesuada. </div>
                                    </div>
                                </div>
                                <div className="col-md-6">
                                    <div className="card">
                                        <div className="card-heading">
                                            <div className="pull-right">
                                                <button type="button" className="btn btn-default btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                            </div>
                                            <div className="card-title">Morbi a odio</div>
                                        </div>
                                        <div className="card-body">Fusce pellentesque congue justo in rutrum. Ut facilisis orci lorem. Cras eget orci sit amet eros interdum malesuada. </div>
                                    </div>
                                </div>
                                <div className="col-md-6">
                                    <div className="card">
                                        <div className="card-heading">
                                            <div className="pull-right">
                                                <button type="button" className="btn btn-default btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                            </div>
                                            <div className="card-title">Morbi a odio</div>
                                        </div>
                                        <div className="card-body">Fusce pellentesque congue justo in rutrum. Ut facilisis orci lorem. Cras eget orci sit amet eros interdum malesuada. </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="card">
                        <div className="card-body">
                            <h2 className="text-center">Big card with cards</h2>
                            <div className="row">
                                <div className="col-md-6">
                                    <div className="card">
                                        <div className="card-heading">
                                            <div className="pull-right">
                                                <button type="button" className="btn btn-default btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                            </div>
                                            <div className="card-title">Morbi a odio</div>
                                        </div>
                                        <div className="card-body">Fusce pellentesque congue justo in rutrum. Ut facilisis orci lorem. Cras eget orci sit amet eros interdum malesuada. </div>
                                    </div>
                                </div>
                                <div className="col-md-6">
                                    <div className="card">
                                        <div className="card-heading">
                                            <div className="pull-right">
                                                <button type="button" className="btn btn-default btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                            </div>
                                            <div className="card-title">Morbi a odio</div>
                                        </div>
                                        <div className="card-body">Fusce pellentesque congue justo in rutrum. Ut facilisis orci lorem. Cras eget orci sit amet eros interdum malesuada. </div>
                                    </div>
                                </div>
                                <div className="col-md-6">
                                    <div className="card">
                                        <div className="card-heading">
                                            <div className="pull-right">
                                                <button type="button" className="btn btn-default btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                            </div>
                                            <div className="card-title">Morbi a odio</div>
                                        </div>
                                        <div className="card-body">Fusce pellentesque congue justo in rutrum. Ut facilisis orci lorem. Cras eget orci sit amet eros interdum malesuada. </div>
                                    </div>
                                </div>
                                <div className="col-md-6">
                                    <div className="card">
                                        <div className="card-heading">
                                            <div className="pull-right">
                                                <button type="button" className="btn btn-default btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                            </div>
                                            <div className="card-title">Morbi a odio</div>
                                        </div>
                                        <div className="card-body">Fusce pellentesque congue justo in rutrum. Ut facilisis orci lorem. Cras eget orci sit amet eros interdum malesuada. </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default LayoutsBoxed;
