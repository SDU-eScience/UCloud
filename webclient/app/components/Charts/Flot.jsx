import React from 'react';
import pubsub from 'pubsub-js';

import FlotRun from './Flot.run';

class Flot extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        FlotRun();
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    {/* START row */}
                    <div className="row">
                        <div className="col-lg-4">
                            <div className="card">
                                <div className="card-heading">
                                    <div className="card-title">Area</div>
                                </div>
                                <div className="card-body">
                                    <div id="area-flotchart" className="flot-chart flot-chart-lg"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-4">
                            <div className="card">
                                <div className="card-heading">
                                    <div className="card-title">Area Spline</div>
                                </div>
                                <div className="card-body">
                                    <div id="spline-flotchart" className="flot-chart flot-chart-lg"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-4">
                            <div className="card">
                                <div className="card-heading">
                                    <div className="card-title">Bar</div>
                                </div>
                                <div className="card-body">
                                    <div id="bar-flotchart" className="flot-chart flot-chart-lg"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    {/* END row */}
                    {/* START row */}
                    <div className="row">
                        <div className="col-lg-4">
                            <div className="card">
                                <div className="card-heading">
                                    <div className="card-title">Pie</div>
                                </div>
                                <div className="card-body">
                                    <div id="pie-flotchart" className="flot-chart flot-chart-lg"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-4">
                            <div className="card">
                                <div className="card-heading">
                                    <div className="card-title">Donut</div>
                                </div>
                                <div className="card-body">
                                    <div id="donut-flotchart" className="flot-chart flot-chart-lg"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-4">
                            <div className="card">
                                <div className="card-heading">
                                    <div className="card-title">Bar - Stacked</div>
                                </div>
                                <div className="card-body">
                                    <div id="barstacked-flotchart" className="flot-chart flot-chart-lg"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    {/* END row */}
                    {/* START row */}
                    <div className="row">
                        <div className="col-lg-6">
                            <div className="card">
                                <div className="card-heading">
                                    <div className="card-title">Real Time</div>
                                </div>
                                <div className="card-body">
                                    <div id="realtime-flotchart" className="flot-chart flot-chart-lg"></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-6">
                            <div className="card">
                                <div className="card-heading">
                                    <div className="card-title">Line</div>
                                </div>
                                <div className="card-body">
                                    <div id="line-flotchart" className="flot-chart flot-chart-lg"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    {/* END row */}
                </div>
            </section>
        );
    }
}

export default Flot;
