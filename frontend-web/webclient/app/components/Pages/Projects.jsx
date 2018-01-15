import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button, ProgressBar, Label } from 'react-bootstrap';

class Projects extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <Grid fluid>
                    <Row className="mv">
                        <Col sm={4}>
                            <button type="button" className="btn btn-info"><em className="ion-plus mr-sm"></em>Add Project</button>
                        </Col>
                        <Col sm={8} className="text-right hidden-xs">
                            <form className="form-inline">
                                <div className="form-group mr">
                                    <label className="mr"><small>Status</small></label>
                                    <select className="form-control input-sm">
                                        <option>Completed</option>
                                        <option>Active</option>
                                        <option>Paused</option>
                                        <option>Canceled</option>
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label className="mr"><small>Sort by</small></label>
                                    <select className="form-control input-sm">
                                        <option>Name</option>
                                        <option>Status</option>
                                        <option>Start Date</option>
                                        <option>Finish Date</option>
                                    </select>
                                </div>
                            </form>
                        </Col>
                    </Row>
                    <Row>
                        <Col md={6} lg={4}>
                            {/* Project card */}
                            <div className="card">
                                <div className="card-heading">
                                    <div className="pull-right">
                                        <Label bsStyle="info">active</Label>
                                    </div>
                                    <div className="card-title">Project #A</div><small>Web development</small>
                                </div>
                                <div className="card-body">
                                    <p><strong>Description:</strong></p>
                                    <div className="pl-lg mb-lg">Ut turpis urna, tristique sed adipiscing nec, luctus quis leo. Fusce nec volutpat ante.</div>
                                    <p><strong>Members:</strong></p>
                                    <div className="pl-lg mb-lg">
                                        <a href="" className="inline">
                                            <img src="img/user/02.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/04.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/05.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/06.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                    </div>
                                    <p><strong>Activity:</strong></p>
                                    <div className="pl-lg">
                                        <ul className="list-inline m0">
                                            <li className="mr">
                                                <h4 className="m0">17</h4>
                                                <p className="text-muted">Issues</p>
                                            </li>
                                            <li className="mr">
                                                <h4 className="m0">780</h4>
                                                <p className="text-muted">Posts</p>
                                            </li>
                                            <li>
                                                <h4 className="m0">23</h4>
                                                <p className="text-muted">Tests</p>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                                <div className="card-footer">
                                    <p><small>56%</small></p>
                                    <ProgressBar now={56} bsStyle="info" className="progress-xs m0"/>
                                </div>
                            </div>
                            {/* end Project card */}
                        </Col>
                        <Col md={6} lg={4}>
                            {/* Project card */}
                            <div className="card">
                                <div className="card-heading">
                                    <div className="pull-right">
                                        <Label bsStyle="info">active</Label>
                                    </div>
                                    <div className="card-title">Project #B</div><small>Web development</small>
                                </div>
                                <div className="card-body">
                                    <p><strong>Description:</strong></p>
                                    <div className="pl-lg mb-lg">Ut turpis urna, tristique sed adipiscing nec, luctus quis leo. Fusce nec volutpat ante.</div>
                                    <p><strong>Members:</strong></p>
                                    <div className="pl-lg mb-lg">
                                        <a href="" className="inline">
                                            <img src="img/user/04.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/03.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/07.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/03.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                    </div>
                                    <p><strong>Activity:</strong></p>
                                    <div className="pl-lg">
                                        <ul className="list-inline m0">
                                            <li className="mr">
                                                <h4 className="m0">17</h4>
                                                <p className="text-muted">Issues</p>
                                            </li>
                                            <li className="mr">
                                                <h4 className="m0">780</h4>
                                                <p className="text-muted">Posts</p>
                                            </li>
                                            <li>
                                                <h4 className="m0">23</h4>
                                                <p className="text-muted">Tests</p>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                                <div className="card-footer">
                                    <p><small>56%</small></p>
                                    <ProgressBar className="progress-xs m0" now={56} bsStyle="danger" />
                                </div>
                            </div>
                            {/* end Project card */}
                        </Col>
                        <Col md={6} lg={4}>
                            {/* Project card */}
                            <div className="card">
                                <div className="card-heading">
                                    <div className="pull-right">
                                        <Label bsStyle="warning">paused</Label>
                                    </div>
                                    <div className="card-title">Project #C</div><small>Web development</small>
                                </div>
                                <div className="card-body">
                                    <p><strong>Description:</strong></p>
                                    <div className="pl-lg mb-lg">Ut turpis urna, tristique sed adipiscing nec, luctus quis leo. Fusce nec volutpat ante.</div>
                                    <p><strong>Members:</strong></p>
                                    <div className="pl-lg mb-lg">
                                        <a href="" className="inline">
                                            <img src="img/user/04.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/03.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/07.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/03.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                    </div>
                                    <p><strong>Activity:</strong></p>
                                    <div className="pl-lg">
                                        <ul className="list-inline m0">
                                            <li className="mr">
                                                <h4 className="m0">17</h4>
                                                <p className="text-muted">Issues</p>
                                            </li>
                                            <li className="mr">
                                                <h4 className="m0">780</h4>
                                                <p className="text-muted">Posts</p>
                                            </li>
                                            <li>
                                                <h4 className="m0">23</h4>
                                                <p className="text-muted">Tests</p>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                                <div className="card-footer">
                                    <p><small>64%</small></p>
                                    <ProgressBar className="progress-xs m0" now={64} bsStyle="success" />
                                </div>
                            </div>
                            {/* end Project card */}
                        </Col>
                        <Col md={6} lg={4}>
                            {/* Project card */}
                            <div className="card">
                                <div className="card-heading">
                                    <div className="pull-right">
                                        <Label bsStyle="info">active</Label>
                                    </div>
                                    <div className="card-title">Project #D</div><small>Web development</small>
                                </div>
                                <div className="card-body">
                                    <p><strong>Description:</strong></p>
                                    <div className="pl-lg mb-lg">Ut turpis urna, tristique sed adipiscing nec, luctus quis leo. Fusce nec volutpat ante.</div>
                                    <p><strong>Members:</strong></p>
                                    <div className="pl-lg mb-lg">
                                        <a href="" className="inline">
                                            <img src="img/user/03.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/07.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/06.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/05.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                    </div>
                                    <p><strong>Activity:</strong></p>
                                    <div className="pl-lg">
                                        <ul className="list-inline m0">
                                            <li className="mr">
                                                <h4 className="m0">17</h4>
                                                <p className="text-muted">Issues</p>
                                            </li>
                                            <li className="mr">
                                                <h4 className="m0">780</h4>
                                                <p className="text-muted">Posts</p>
                                            </li>
                                            <li>
                                                <h4 className="m0">23</h4>
                                                <p className="text-muted">Tests</p>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                                <div className="card-footer">
                                    <p><small>36%</small></p>
                                    <ProgressBar className="progress-xs m0" now={36} bsStyle="warning" />
                                </div>
                            </div>
                            {/* end Project card */}
                        </Col>
                        <Col md={6} lg={4}>
                            {/* Project card */}
                            <div className="card">
                                <div className="card-heading">
                                    <div className="pull-right">
                                        <Label bsStyle="info">active</Label>
                                    </div>
                                    <div className="card-title">Project #A</div><small>Web development</small>
                                </div>
                                <div className="card-body">
                                    <p><strong>Description:</strong></p>
                                    <div className="pl-lg mb-lg">Ut turpis urna, tristique sed adipiscing nec, luctus quis leo. Fusce nec volutpat ante.</div>
                                    <p><strong>Members:</strong></p>
                                    <div className="pl-lg mb-lg">
                                        <a href="" className="inline">
                                            <img src="img/user/02.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/04.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/05.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/06.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                    </div>
                                    <p><strong>Activity:</strong></p>
                                    <div className="pl-lg">
                                        <ul className="list-inline m0">
                                            <li className="mr">
                                                <h4 className="m0">17</h4>
                                                <p className="text-muted">Issues</p>
                                            </li>
                                            <li className="mr">
                                                <h4 className="m0">780</h4>
                                                <p className="text-muted">Posts</p>
                                            </li>
                                            <li>
                                                <h4 className="m0">23</h4>
                                                <p className="text-muted">Tests</p>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                                <div className="card-footer">
                                    <p><small>56%</small></p>
                                    <ProgressBar className="progress-xs m0" now={56} bsStyle="info" />
                                </div>
                            </div>
                            {/* end Project card */}
                        </Col>
                        <Col md={6} lg={4}>
                            {/* Project card */}
                            <div className="card">
                                <div className="card-heading">
                                    <div className="pull-right">
                                        <Label bsStyle="success">completed</Label>
                                    </div>
                                    <div className="card-title">Project #B</div><small>Web development</small>
                                </div>
                                <div className="card-body">
                                    <p><strong>Description:</strong></p>
                                    <div className="pl-lg mb-lg">Ut turpis urna, tristique sed adipiscing nec, luctus quis leo. Fusce nec volutpat ante.</div>
                                    <p><strong>Members:</strong></p>
                                    <div className="pl-lg mb-lg">
                                        <a href="" className="inline">
                                            <img src="img/user/04.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/03.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/07.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/03.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                    </div>
                                    <p><strong>Activity:</strong></p>
                                    <div className="pl-lg">
                                        <ul className="list-inline m0">
                                            <li className="mr">
                                                <h4 className="m0">17</h4>
                                                <p className="text-muted">Issues</p>
                                            </li>
                                            <li className="mr">
                                                <h4 className="m0">780</h4>
                                                <p className="text-muted">Posts</p>
                                            </li>
                                            <li>
                                                <h4 className="m0">23</h4>
                                                <p className="text-muted">Tests</p>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                                <div className="card-footer">
                                    <p><small>100%</small></p>
                                    <ProgressBar className="progress-xs m0" now={100} bsStyle="success" />
                                </div>
                            </div>
                            {/* end Project card */}
                        </Col>
                        <Col md={6} lg={4}>
                            {/* Project card */}
                            <div className="card">
                                <div className="card-heading">
                                    <div className="pull-right">
                                        <Label bsStyle="warning">paused</Label>
                                    </div>
                                    <div className="card-title">Project #C</div><small>Web development</small>
                                </div>
                                <div className="card-body">
                                    <p><strong>Description:</strong></p>
                                    <div className="pl-lg mb-lg">Ut turpis urna, tristique sed adipiscing nec, luctus quis leo. Fusce nec volutpat ante.</div>
                                    <p><strong>Members:</strong></p>
                                    <div className="pl-lg mb-lg">
                                        <a href="" className="inline">
                                            <img src="img/user/04.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/03.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/07.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/03.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                    </div>
                                    <p><strong>Activity:</strong></p>
                                    <div className="pl-lg">
                                        <ul className="list-inline m0">
                                            <li className="mr">
                                                <h4 className="m0">17</h4>
                                                <p className="text-muted">Issues</p>
                                            </li>
                                            <li className="mr">
                                                <h4 className="m0">780</h4>
                                                <p className="text-muted">Posts</p>
                                            </li>
                                            <li>
                                                <h4 className="m0">23</h4>
                                                <p className="text-muted">Tests</p>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                                <div className="card-footer">
                                    <p><small>64%</small></p>
                                    <ProgressBar className="progress-xs m0" now={64} bsStyle="success" />
                                </div>
                            </div>
                            {/* end Project card */}
                        </Col>
                        <Col md={6} lg={4}>
                            {/* Project card */}
                            <div className="card">
                                <div className="card-heading">
                                    <div className="pull-right">
                                        <Label bsStyle="info">active</Label>
                                    </div>
                                    <div className="card-title">Project #D</div><small>Web development</small>
                                </div>
                                <div className="card-body">
                                    <p><strong>Description:</strong></p>
                                    <div className="pl-lg mb-lg">Ut turpis urna, tristique sed adipiscing nec, luctus quis leo. Fusce nec volutpat ante.</div>
                                    <p><strong>Members:</strong></p>
                                    <div className="pl-lg mb-lg">
                                        <a href="" className="inline">
                                            <img src="img/user/03.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/07.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/06.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/05.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                    </div>
                                    <p><strong>Activity:</strong></p>
                                    <div className="pl-lg">
                                        <ul className="list-inline m0">
                                            <li className="mr">
                                                <h4 className="m0">17</h4>
                                                <p className="text-muted">Issues</p>
                                            </li>
                                            <li className="mr">
                                                <h4 className="m0">780</h4>
                                                <p className="text-muted">Posts</p>
                                            </li>
                                            <li>
                                                <h4 className="m0">23</h4>
                                                <p className="text-muted">Tests</p>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                                <div className="card-footer">
                                    <p><small>36%</small></p>
                                    <ProgressBar className="progress-xs m0" now={36} bsStyle="warning" />
                                </div>
                            </div>
                            {/* end Project card */}
                        </Col>
                        <Col md={6} lg={4}>
                            {/* Project card */}
                            <div className="card">
                                <div className="card-heading">
                                    <div className="pull-right">
                                        <Label bsStyle="info">active</Label>
                                    </div>
                                    <div className="card-title">Project #A</div><small>Web development</small>
                                </div>
                                <div className="card-body">
                                    <p><strong>Description:</strong></p>
                                    <div className="pl-lg mb-lg">Ut turpis urna, tristique sed adipiscing nec, luctus quis leo. Fusce nec volutpat ante.</div>
                                    <p><strong>Members:</strong></p>
                                    <div className="pl-lg mb-lg">
                                        <a href="" className="inline">
                                            <img src="img/user/02.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/04.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/05.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                        <a href="" className="inline">
                                            <img src="img/user/06.jpg" alt="project member" className="img-circle thumb32"/>
                                        </a>
                                    </div>
                                    <p><strong>Activity:</strong></p>
                                    <div className="pl-lg">
                                        <ul className="list-inline m0">
                                            <li className="mr">
                                                <h4 className="m0">17</h4>
                                                <p className="text-muted">Issues</p>
                                            </li>
                                            <li className="mr">
                                                <h4 className="m0">780</h4>
                                                <p className="text-muted">Posts</p>
                                            </li>
                                            <li>
                                                <h4 className="m0">23</h4>
                                                <p className="text-muted">Tests</p>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                                <div className="card-footer">
                                    <p><small>56%</small></p>
                                    <ProgressBar className="progress-xs m0" now={56} bsStyle="info" />
                                </div>
                            </div>
                            {/* end Project card */}
                        </Col>
                    </Row>
                </Grid>
            </section>
        );
    }
}

export default Projects;
