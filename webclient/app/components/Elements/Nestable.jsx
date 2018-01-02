import React from 'react';
import pubsub from 'pubsub-js';

import './Nestable.scss';
import NestableRun from './Nestable.run';

class Nestable extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        NestableRun();
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <div className="js-nestable-action"><a data-action="expand-all" className="btn btn-default btn-sm mr-sm">Expand All</a><a data-action="collapse-all" className="btn btn-default btn-sm">CollapseAll</a></div>
                    <div className="row">
                        <div className="col-md-6">
                            <div id="nestable" className="dd">
                                <ol className="dd-list">
                                    <li data-id="1" className="dd-item">
                                        <div className="card b0 dd-handle">
                                            <div className="mda-list">
                                                <div className="mda-list-item">
                                                    <div className="mda-list-item-icon item-grab"><em className="ion-drag icon-lg"></em></div>
                                                    <div className="mda-list-item-icon bg-info"><em className="ion-coffee icon-lg"></em></div>
                                                    <div className="mda-list-item-text mda-2-line">
                                                        <h3>Cassandra Gutierrez</h3>
                                                        <h4>Brunch this weekend?</h4>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </li>
                                    <li data-id="2" className="dd-item">
                                        <div className="card b0 dd-handle">
                                            <div className="mda-list">
                                                <div className="mda-list-item">
                                                    <div className="mda-list-item-icon item-grab"><em className="ion-drag icon-lg"></em></div>
                                                    <div className="mda-list-item-icon bg-info"><em className="ion-coffee icon-lg"></em></div>
                                                    <div className="mda-list-item-text mda-2-line">
                                                        <h3>Vincent Morgan</h3>
                                                        <h4>Brunch this weekend?</h4>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                        <ol className="dd-list">
                                            <li data-id="3" className="dd-item">
                                                <div className="card b0 dd-handle">
                                                    <div className="mda-list">
                                                        <div className="mda-list-item">
                                                            <div className="mda-list-item-icon item-grab"><em className="ion-drag icon-lg"></em></div>
                                                            <div className="mda-list-item-icon bg-info"><em className="ion-coffee icon-lg"></em></div>
                                                            <div className="mda-list-item-text mda-2-line">
                                                                <h3>Vivan Jennings</h3>
                                                                <h4>Brunch this weekend?</h4>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            </li>
                                            <li data-id="4" className="dd-item">
                                                <div className="card b0 dd-handle">
                                                    <div className="mda-list">
                                                        <div className="mda-list-item">
                                                            <div className="mda-list-item-icon item-grab"><em className="ion-drag icon-lg"></em></div>
                                                            <div className="mda-list-item-icon bg-info"><em className="ion-coffee icon-lg"></em></div>
                                                            <div className="mda-list-item-text mda-2-line">
                                                                <h3>Jason Foster</h3>
                                                                <h4>Brunch this weekend?</h4>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            </li>
                                            <li data-id="5" className="dd-item">
                                                <div className="card b0 dd-handle">
                                                    <div className="mda-list">
                                                        <div className="mda-list-item">
                                                            <div className="mda-list-item-icon item-grab"><em className="ion-drag icon-lg"></em></div>
                                                            <div className="mda-list-item-icon bg-info"><em className="ion-coffee icon-lg"></em></div>
                                                            <div className="mda-list-item-text mda-2-line">
                                                                <h3>Taylor Grant</h3>
                                                                <h4>Brunch this weekend?</h4>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                                <ol className="dd-list">
                                                    <li data-id="6" className="dd-item">
                                                        <div className="card b0 dd-handle">
                                                            <div className="mda-list">
                                                                <div className="mda-list-item">
                                                                    <div className="mda-list-item-icon item-grab"><em className="ion-drag icon-lg"></em></div>
                                                                    <div className="mda-list-item-icon bg-info"><em className="ion-coffee icon-lg"></em></div>
                                                                    <div className="mda-list-item-text mda-2-line">
                                                                        <h3>Lorraine Cooper</h3>
                                                                        <h4>Brunch this weekend?</h4>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </li>
                                                </ol>
                                            </li>
                                        </ol>
                                    </li>
                                    <li data-id="11" className="dd-item">
                                        <div className="card b0 dd-handle">
                                            <div className="mda-list">
                                                <div className="mda-list-item">
                                                    <div className="mda-list-item-icon item-grab"><em className="ion-drag icon-lg"></em></div>
                                                    <div className="mda-list-item-icon bg-info"><em className="ion-coffee icon-lg"></em></div>
                                                    <div className="mda-list-item-text mda-2-line">
                                                        <h3>Daryl Flores</h3>
                                                        <h4>Brunch this weekend?</h4>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </li>
                                </ol>
                            </div>
                            <p>Output</p>
                            <div id="nestable-output" className="well"></div>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default Nestable;
