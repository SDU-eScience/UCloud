import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Dropdown, MenuItem } from 'react-bootstrap';

import DashboardRun from './Dashboard.run';
import RippleRun from '../Ripple/Ripple.run';

class Dashboard extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        DashboardRun();
        RippleRun();
    }

    render() {
        return (
            <section>
                <div className="content-heading bg-white">
                    <Row>
                        <Col sm={9}>
                            <h4 className="m0 text-thin">
                            <span data-localize="WELCOME">Welcome to </span>
                            Centric dashboard</h4><small>Bootstrap admin dashboard template</small>
                        </Col>
                        <Col sm={3} className="text-right hidden-xs">
                            <button type="button" className="mt-sm btn btn-labeled btn-default ripple">Apps<span className="btn-label btn-label-right"><i className="ion-plus-round"></i></span></button>
                        </Col>
                    </Row>
                </div>
                <Grid fluid>
                    <Row>
                        <Col xs={6} lg={3}>
                            <div className="card">
                                <div className="card-body pv">
                                    <div className="clearfix">
                                        <div className="pull-left">
                                            <h4 className="m0 text-thin">350</h4><small className="m0 text-muted"><em className="mr-sm ion-arrow-up-b"></em>New visitors</small>
                                        </div>
                                        <div className="pull-right mt-lg">
                                            <div id="sparkline2" data-line-color="#4caf50" className="sparkline"></div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </Col>
                        <Col xs={6} lg={3}>
                            <div className="card">
                                <div className="card-body pv">
                                    <div className="clearfix">
                                        <div className="pull-left">
                                            <h4 className="m0 text-thin">10,200</h4><small className="m0 text-muted"><em className="mr-sm ion-arrow-down-b"></em>Page views</small>
                                        </div>
                                        <div className="pull-right mt-lg">
                                            <div id="sparkline1" data-line-color="#03A9F4" className="sparkline"></div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </Col>
                        <Col xs={6} lg={3} className="visible-lg">
                            <div className="card">
                                <div className="card-body pv">
                                    <div className="clearfix">
                                        <div className="pull-left">
                                            <h4 className="m0 text-thin">880</h4><small className="m0 text-muted"><em className="mr-sm ion-arrow-up-b"></em>Last income</small>
                                        </div>
                                        <div className="pull-right mt-lg">
                                            <div id="sparkline3" data-line-color="#ab47bc" className="sparkline"></div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </Col>
                        <Col xs={6} lg={3} className="visible-lg">
                            <div className="card">
                                <div className="card-body pv">
                                    <div className="clearfix">
                                        <div className="pull-left">
                                            <h4 className="m0 text-thin">780</h4><small className="m0 text-muted"><em className="mr-sm ion-arrow-up-b"></em>Reservations</small>
                                        </div>
                                        <div className="pull-right mt-lg">
                                            <div id="sparkline4" data-line-color="#e91e63" className="sparkline"></div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </Col>
                    </Row>
                    <Row>
                        <Col lg={9} md={12}>
                            <Row>
                                <Col lg={8}>
                                    <div className="card">
                                        <div className="card-heading">
                                            {/* START dropdown */}
                                            <div className="pull-right">
                                                <Dropdown pullRight id="dd1">
                                                    <Dropdown.Toggle bsStyle="default" noCaret className="btn btn-flat btn-flat-icon ripple">
                                                      <em className="ion-android-more-vertical"></em>
                                                    </Dropdown.Toggle>
                                                    <Dropdown.Menu className="md-dropdown-menu" >
                                                        <MenuItem eventKey="1">Last 30 days</MenuItem>
                                                        <MenuItem eventKey="2">Last week</MenuItem>
                                                        <MenuItem eventKey="3">Last year</MenuItem>
                                                    </Dropdown.Menu>
                                                </Dropdown>
                                            </div>
                                            {/* END dropdown */}
                                            <div className="card-title">Analytic board</div><small>Nulla commodo blandit cursus.</small>
                                        </div>
                                        <div className="card-body">
                                            <div role="group" aria-label="..." className="pull-right pr-sm btn-group btn-group-sm">
                                                <button type="button" className="btn btn-default btn-xs btn-default">Total</button>
                                                <button type="button" className="btn btn-default btn-xs btn-default">Average</button>
                                            </div>
                                            <div id="flot-main-spline" className="flot-chart flot-chart-lg flot-legend-left"></div>
                                        </div>
                                    </div>
                                </Col>
                                <Col lg={4}>
                                    <div className="card">
                                        <div className="card-heading">
                                            <div className="pull-right">
                                                <p className="m0 text-muted"><em className="mr-sm ion-arrow-up-b"></em>20%</p>
                                            </div>
                                            <div className="card-title">Earnings</div><small>Based on last month analytics.</small>
                                        </div>
                                        <div className="card-body">
                                            <div role="group" aria-label="..." className="btn-group btn-group-sm">
                                                <button type="button" className="btn btn-default btn-xs btn-default">2015</button>
                                                <button type="button" className="btn btn-default btn-xs btn-default">2016</button>
                                            </div>
                                            <div id="flot-stacked-chart" data-height="245px" className="flot-chart"></div>
                                        </div>
                                    </div>
                                </Col>
                            </Row>
                            <Row>
                                <Col xs={12} lg={4}>
                                    <div className="card">
                                        <div className="card-heading">
                                            <div className="pull-right"><span className="mr"><em className="ion-record spr text-primary"></em><small className="va-middle">2016</small></span><span><em className="ion-record spr text-success"></em><small className="va-middle">2015</small></span></div>
                                            <div className="card-title">Sales</div>
                                        </div>
                                        <div className="card-body">
                                            <div id="flot-bar-chart" data-height="235" className="flot-chart flot-chart-md"></div>
                                        </div>
                                    </div>
                                </Col>
                                <Col xs={12} lg={8}>
                                    <div className="card">
                                        {/* START table-responsive */}
                                        <div className="table-responsive">
                                            <table className="table">
                                                <thead>
                                                    <tr>
                                                        <th>#</th>
                                                        <th>Project</th>
                                                        <th>Completion</th>
                                                        <th className="text-right">Trend</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    <tr>
                                                        <td className="va-middle"><span className="badge bg-pink-500">1</span></td>
                                                        <td>
                                                            <p className="m0">Vestibulum<br/><small className="text-thin">Nunc posuere eleifend lobortis.</small></p>
                                                        </td>
                                                        <td className="va-middle">50%</td>
                                                        <td className="text-right va-middle"><em className="ion-arrow-graph-up-right text-success"></em></td>
                                                    </tr>
                                                    <tr>
                                                        <td className="va-middle"><span className="badge bg-purple-400">2</span></td>
                                                        <td>
                                                            <p className="m0">Runfaster<br/><small className="text-thin">Nunc posuere eleifend lobortis.</small></p>
                                                        </td>
                                                        <td className="va-middle">30%</td>
                                                        <td className="text-right va-middle"><em className="ion-arrow-graph-down-right text-warning"></em></td>
                                                    </tr>
                                                    <tr>
                                                        <td className="va-middle"><span className="badge bg-indigo-500">3</span></td>
                                                        <td>
                                                            <p className="m0">Medic Healthcare<br/><small className="text-thin">Nunc posuere eleifend lobortis.</small></p>
                                                        </td>
                                                        <td className="va-middle">80%</td>
                                                        <td className="text-right va-middle"><em className="ion-arrow-graph-up-right text-success"></em></td>
                                                    </tr>
                                                    <tr>
                                                        <td className="va-middle"><span className="badge bg-info">4</span></td>
                                                        <td>
                                                            <p className="m0">Videotubeyou<br/><small className="text-thin">Nunc posuere eleifend lobortis.</small></p>
                                                        </td>
                                                        <td className="va-middle">50%</td>
                                                        <td className="text-right va-middle"><em className="ion-arrow-graph-down-right text-warning"></em></td>
                                                    </tr>
                                                </tbody>
                                            </table>
                                        </div>
                                        {/* END table-responsive */}
                                    </div>
                                </Col>
                            </Row>
                        </Col>
                        <Col lg={3} xs={12}>
                            {/* Activity feed */}
                            <div className="card">
                                <div className="card-heading">
                                    {/* START dropdown */}
                                    <div className="pull-right">
                                        <Dropdown pullRight id="dd2">
                                            <Dropdown.Toggle bsStyle="default" noCaret className="btn btn-flat btn-flat-icon ripple">
                                              <em className="ion-android-more-vertical"></em>
                                            </Dropdown.Toggle>
                                            <Dropdown.Menu className="md-dropdown-menu" >
                                                <MenuItem eventKey="1">Last 30 days</MenuItem>
                                                <MenuItem eventKey="2">Last week</MenuItem>
                                                <MenuItem eventKey="3">Last year</MenuItem>
                                            </Dropdown.Menu>
                                        </Dropdown>
                                    </div>
                                    {/* END dropdown */}
                                    <div className="card-title">Activity</div><small>What's people doing right now</small>
                                </div>
                                <div className="card-body bb">
                                    <p className="pull-left mr"><a href=""><img src="img/user/04.jpg" alt="User" className="img-circle thumb32"/></a></p>
                                    <div className="oh">
                                        <p><strong className="spr">Adam</strong><span className="spr">posted in</span><a href="">Material</a></p>
                                        <p className="bl pl"><i>That's awesome!</i></p>
                                        <div className="clearfix">
                                            <div className="pull-left text-muted"><em className="ion-android-time mr-sm"></em><span>2 hours ago</span></div>
                                        </div>
                                    </div>
                                </div>
                                <div className="card-body bb">
                                    <p className="pull-left mr"><a href=""><img src="img/user/06.jpg" alt="User" className="img-circle thumb32"/></a></p>
                                    <div className="oh">
                                        <p><strong className="spr">Dora</strong><span>added a new task</span></p>
                                        <p><em className="ion-calendar icon-fw"></em><a href="">Start migration</a></p>
                                        <div className="clearfix">
                                            <div className="pull-left text-muted"><em className="ion-android-time mr-sm"></em><span>3 hours ago</span></div>
                                            <div className="pull-right"><span>2</span><em className="ion-star ml-sm text-warning"></em></div>
                                        </div>
                                    </div>
                                </div>
                                <div className="card-body bb">
                                    <p className="pull-left mr"><a href=""><img src="img/user/07.jpg" alt="User" className="img-circle thumb32"/></a></p>
                                    <div className="oh">
                                        <p><strong className="spr">Kathryn</strong><span className="spr">published</span><a href="">Trip</a></p>
                                        <p><a href=""><img src="img/pic1.jpg" alt="Pic" className="mr-sm thumb48"/></a><a href=""><img src="img/pic2.jpg" alt="Pic" className="mr-sm thumb48"/></a></p>
                                        <div className="clearfix">
                                            <div className="pull-left text-muted"><em className="ion-android-time mr-sm"></em><span>4 hours ago</span></div>
                                            <div className="pull-right"><span>2</span><em className="ion-ios-heart ml-sm text-danger"></em></div>
                                        </div>
                                    </div>
                                </div>
                                <div className="card-body bb">
                                    <p className="pull-left mr"><a href=""><img src="img/user/02.jpg" alt="User" className="img-circle thumb32"/></a></p>
                                    <div className="oh">
                                        <p><strong className="spr">Daniel</strong><span className="spr">joined to</span><a href="">Group</a></p>
                                        <p><span className="image-list"><a href=""><img src="img/user/03.jpg" alt="User" className="img-circle thumb32"/></a><a href=""><img src="img/user/04.jpg" alt="User" className="img-circle thumb32"/></a><a href=""><img src="img/user/05.jpg" alt="User" className="img-circle thumb32"/></a><a href=""><img src="img/user/07.jpg" alt="User" className="img-circle thumb32"/></a><strong><a href="" className="ml-sm link-unstyled">+3</a></strong></span></p>
                                        <div className="clearfix">
                                            <div className="pull-left text-muted"><em className="ion-android-time mr-sm"></em><span>yesterday</span></div>
                                        </div>
                                    </div>
                                </div>
                                <div className="card-body bb">
                                    <p className="pull-left mr"><a href=""><img src="img/user/03.jpg" alt="User" className="img-circle thumb32"/></a></p>
                                    <div className="oh">
                                        <p><strong className="spr">Leroy Day</strong><span className="spr">wakes up!</span></p>
                                        <p className="bl pl"><i>That's awesome!</i></p>
                                        <div className="clearfix">
                                            <div className="pull-left text-muted"><em className="ion-android-time mr-sm"></em><span>2 weeks ago</span></div>
                                        </div>
                                    </div>
                                </div><a href="" className="card-footer btn btn-flat btn-default"><small className="text-center text-muted lh1">See more activities</small></a>
                            </div>
                        </Col>
                    </Row>
                    <Row>
                        <Col lg={4} md={6}>
                            <div className="card">
                                <div className="card-heading bg-pink-500 ripple ripple-block">
                                    <div className="card-title text-center"><small>October</small>
                                        <h4 className="mv-sm text-md">12</h4><span>Monday</span>
                                    </div>
                                </div>
                                <div className="ui-datepicker-responsive">
                                    <div id="dashboard-datepicker" data-date="04/04/2016" className="ui-datepicker"></div>
                                </div>
                            </div>
                        </Col>
                        <Col lg={4} md={6}>
                            <div className="card">
                                <div className="card-heading">
                                    <div className="card-title">
                                        <div className="pull-right"><em className="ion-ios-people text-soft icon-lg"></em></div>Resources distribution
                                    </div>
                                </div>
                                <div className="card-body pt0">
                                    <div id="vector-map" style={{height:'240px'}} className="vector-map"></div>
                                </div>
                                <ul className="list-group m0">
                                    <li className="list-group-item"><span>Silicon Valley</span><span className="badge badge-xs bg-green-500">15</span></li>
                                    <li className="list-group-item"><span>Chicago</span><span className="badge badge-xs bg-pink-500">9</span></li>
                                    <li className="list-group-item"><span>Houston</span><span className="badge badge-xs bg-purple-500">3</span></li>
                                    <li className="list-group-item"><span>Others</span><span className="badge badge-xs bg-blue-500">25</span></li>
                                </ul>
                            </div>
                        </Col>
                        <Col lg={4} md={12}>
                            <div className="card">
                                <div className="card-heading">
                                    <div className="card-title">
                                        <div className="pull-right"><a href=""><em className="ion-plus-round text-soft"></em></a></div>Tasks
                                    </div>
                                </div>
                                <div className="card-body text-center pt0">
                                    <div id="dashboard-easypiechartTask" data-percent="85" className="easypie-chart block-center"><small className="percentage">Goal</small></div>
                                    <p className="mv">85% productivity</p>
                                </div>
                                <div className="list-group m0"><a href="" className="list-group-item bt0"><span className="text-sm">Work on project presentation</span><span className="pull-right"><em className="ion-ios-arrow-right"></em></span></a><a href="" className="list-group-item"><span className="text-sm">Prepare a call with Robert</span><span className="pull-right"><em className="ion-ios-arrow-right"></em></span></a><a href="" className="list-group-item"><span className="text-sm">Release a new version</span><span className="pull-right"><em className="ion-ios-arrow-right"></em></span></a><a href="" className="list-group-item bb0"><span className="text-sm">Tell Houston there's no problem</span><span className="pull-right"><em className="ion-ios-arrow-right"></em></span></a></div>
                                <div id="flot-task-chart" data-height="90" className="flot-chart flot-chart-sm"></div>
                            </div>
                        </Col>
                    </Row>
                </Grid>
            </section>
        );
    }
}

export default Dashboard;
