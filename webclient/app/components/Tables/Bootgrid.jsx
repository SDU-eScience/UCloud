import React from 'react';
import pubsub from 'pubsub-js';

import './Bootgrid.scss';
import BootgridRun from './Bootgrid.run';

class Bootgrid extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        BootgridRun();
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <h5 className="mt0">Basic Example</h5>
                    <div className="card">
                        <div className="table-responsive bootgrid">
                            <table id="bootgrid-basic" className="table table-striped">
                                <thead>
                                    <tr>
                                        <th data-column-id="id" data-type="numeric">ID</th>
                                        <th data-column-id="sender">Sender</th>
                                        <th data-column-id="received" data-order="desc">Received</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td>10238</td>
                                        <td>eduardo@pingpong.com</td>
                                        <td>14.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10243</td>
                                        <td>eduardo@pingpong.com</td>
                                        <td>19.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10248</td>
                                        <td>eduardo@pingpong.com</td>
                                        <td>24.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10253</td>
                                        <td>eduardo@pingpong.com</td>
                                        <td>29.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10234</td>
                                        <td>lila@google.com</td>
                                        <td>10.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10239</td>
                                        <td>lila@google.com</td>
                                        <td>15.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10244</td>
                                        <td>lila@google.com</td>
                                        <td>20.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10249</td>
                                        <td>lila@google.com</td>
                                        <td>25.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10237</td>
                                        <td>robert@bingo.com</td>
                                        <td>13.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10242</td>
                                        <td>robert@bingo.com</td>
                                        <td>18.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10247</td>
                                        <td>robert@bingo.com</td>
                                        <td>23.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10252</td>
                                        <td>robert@bingo.com</td>
                                        <td>28.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10236</td>
                                        <td>simon@yahoo.com</td>
                                        <td>12.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10241</td>
                                        <td>simon@yahoo.com</td>
                                        <td>17.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10246</td>
                                        <td>simon@yahoo.com</td>
                                        <td>22.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10251</td>
                                        <td>simon@yahoo.com</td>
                                        <td>27.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10235</td>
                                        <td>tim@microsoft.com</td>
                                        <td>11.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10240</td>
                                        <td>tim@microsoft.com</td>
                                        <td>16.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10245</td>
                                        <td>tim@microsoft.com</td>
                                        <td>21.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10250</td>
                                        <td>tim@microsoft.com</td>
                                        <td>26.10.2013</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <h5>Row selection</h5>
                    <div className="card">
                        <div className="table-responsive bootgrid">
                            <table id="bootgrid-selection" className="table table-striped">
                                <thead>
                                    <tr>
                                        <th data-column-id="id" data-type="numeric" data-identifier="true">ID</th>
                                        <th data-column-id="sender">Sender</th>
                                        <th data-column-id="received" data-order="desc">Received</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td>10238</td>
                                        <td>eduardo@pingpong.com</td>
                                        <td>14.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10243</td>
                                        <td>eduardo@pingpong.com</td>
                                        <td>19.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10248</td>
                                        <td>eduardo@pingpong.com</td>
                                        <td>24.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10253</td>
                                        <td>eduardo@pingpong.com</td>
                                        <td>29.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10234</td>
                                        <td>lila@google.com</td>
                                        <td>10.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10239</td>
                                        <td>lila@google.com</td>
                                        <td>15.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10244</td>
                                        <td>lila@google.com</td>
                                        <td>20.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10249</td>
                                        <td>lila@google.com</td>
                                        <td>25.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10237</td>
                                        <td>robert@bingo.com</td>
                                        <td>13.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10242</td>
                                        <td>robert@bingo.com</td>
                                        <td>18.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10247</td>
                                        <td>robert@bingo.com</td>
                                        <td>23.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10252</td>
                                        <td>robert@bingo.com</td>
                                        <td>28.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10236</td>
                                        <td>simon@yahoo.com</td>
                                        <td>12.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10241</td>
                                        <td>simon@yahoo.com</td>
                                        <td>17.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10246</td>
                                        <td>simon@yahoo.com</td>
                                        <td>22.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10251</td>
                                        <td>simon@yahoo.com</td>
                                        <td>27.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10235</td>
                                        <td>tim@microsoft.com</td>
                                        <td>11.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10240</td>
                                        <td>tim@microsoft.com</td>
                                        <td>16.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10245</td>
                                        <td>tim@microsoft.com</td>
                                        <td>21.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10250</td>
                                        <td>tim@microsoft.com</td>
                                        <td>26.10.2013</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <h5>Commands</h5>
                    <div className="card">
                        <div className="table-responsive bootgrid">
                            <table id="bootgrid-command" className="table table-striped table-vmiddle">
                                <thead>
                                    <tr>
                                        <th data-column-id="id" data-type="numeric">ID</th>
                                        <th data-column-id="sender">Sender</th>
                                        <th data-column-id="received" data-order="desc">Received</th>
                                        <th data-column-id="commands" data-formatter="commands" data-sortable="false">
                                            <div className="text-right">Commands</div>
                                        </th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td>10238</td>
                                        <td>eduardo@pingpong.com</td>
                                        <td>14.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10243</td>
                                        <td>eduardo@pingpong.com</td>
                                        <td>19.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10248</td>
                                        <td>eduardo@pingpong.com</td>
                                        <td>24.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10253</td>
                                        <td>eduardo@pingpong.com</td>
                                        <td>29.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10234</td>
                                        <td>lila@google.com</td>
                                        <td>10.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10239</td>
                                        <td>lila@google.com</td>
                                        <td>15.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10244</td>
                                        <td>lila@google.com</td>
                                        <td>20.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10249</td>
                                        <td>lila@google.com</td>
                                        <td>25.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10237</td>
                                        <td>robert@bingo.com</td>
                                        <td>13.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10242</td>
                                        <td>robert@bingo.com</td>
                                        <td>18.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10247</td>
                                        <td>robert@bingo.com</td>
                                        <td>23.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10252</td>
                                        <td>robert@bingo.com</td>
                                        <td>28.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10236</td>
                                        <td>simon@yahoo.com</td>
                                        <td>12.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10241</td>
                                        <td>simon@yahoo.com</td>
                                        <td>17.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10246</td>
                                        <td>simon@yahoo.com</td>
                                        <td>22.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10251</td>
                                        <td>simon@yahoo.com</td>
                                        <td>27.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10235</td>
                                        <td>tim@microsoft.com</td>
                                        <td>11.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10240</td>
                                        <td>tim@microsoft.com</td>
                                        <td>16.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10245</td>
                                        <td>tim@microsoft.com</td>
                                        <td>21.10.2013</td>
                                    </tr>
                                    <tr>
                                        <td>10250</td>
                                        <td>tim@microsoft.com</td>
                                        <td>26.10.2013</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default Bootgrid;

