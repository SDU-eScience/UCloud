import React from 'react';
import pubsub from 'pubsub-js';

import RadialRun from './Radial.run'

class Radial extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        RadialRun();
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <h4 className="page-header clearfix mt0">Knob</h4>
                    <div className="row">
                        <div className="col-lg-3 col-sm-6">
                            <div className="card">
                                <div className="card-heading">Default</div>
                                <div className="card-body text-center">
                                    <input id="knob-chart1" type="text" defaultValue="80" data-max="100"/>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-sm-6">
                            <div className="card">
                                <div className="card-heading">Ready only</div>
                                <div className="card-body text-center">
                                    <input id="knob-chart2" type="text" defaultValue="45" data-max="100"/>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-sm-6">
                            <div className="card">
                                <div className="card-heading">Offset and arc</div>
                                <div className="card-body text-center">
                                    <input id="knob-chart4" type="text" defaultValue="20" data-max="100"/>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-sm-6">
                            <div className="card">
                                <div className="card-heading">Display previous</div>
                                <div className="card-body text-center">
                                    <input id="knob-chart3" type="text" defaultValue="30" data-max="100"/>
                                </div>
                            </div>
                        </div>
                    </div>
                    <h4 className="page-header clearfix">Easypie Charts</h4>
                    <div className="row">
                        <div className="col-lg-3 col-sm-6">
                            <div className="card">
                                <div className="card-heading">Default</div>
                                <div className="card-body text-center">
                                    <div id="easypiechart1" data-percent="85" className="easypie-chart"><span>85</span></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-sm-6">
                            <div className="card">
                                <div className="card-heading">Slim</div>
                                <div className="card-body text-center">
                                    <div id="easypiechart2" data-percent="45" className="easypie-chart"><span>45</span></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-sm-6">
                            <div className="card">
                                <div className="card-heading">Track color</div>
                                <div className="card-body text-center">
                                    <div id="easypiechart3" data-percent="25" className="easypie-chart"><span>25</span></div>
                                </div>
                            </div>
                        </div>
                        <div className="col-lg-3 col-sm-6">
                            <div className="card">
                                <div className="card-heading">Scale color</div>
                                <div className="card-body text-center">
                                    <div id="easypiechart4" data-percent="60" className="easypie-chart"><span>60</span></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default Radial;
