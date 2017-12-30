import React from 'react';
import pubsub from 'pubsub-js';

import RickshawRun from './Rickshaw.run';

class Rickshaw extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        RickshawRun();
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <div className="card">
                        <div className="card-heading">Area graph</div>
                        <div className="card-body">
                            <div id="rickshaw-chart1"></div>
                        </div>
                    </div>
                    <div className="row">
                        <div className="col-md-6">
                            <div className="card">
                                <div className="card-heading">Bar graph</div>
                                <div className="card-body">
                                    <div id="rickshaw-chart2"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-md-6">
                            <div className="card">
                                <div className="card-heading">Scatterplot graph</div>
                                <div className="card-body">
                                    <div id="rickshaw-chart3"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default Rickshaw;
