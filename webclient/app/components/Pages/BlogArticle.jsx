import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

class BlogArticle extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <Grid fluid className="bg-full bg-pic1">
                    <div className="container container-md">
                        <div className="card animated fadeInUp b0">
                            <div className="card-item card-media bg-pic4">
                                <div className="card-item-text bg-transparent">
                                    <h4>A truly relaxing moment</h4>
                                </div>
                            </div>
                            <div className="card-offset">
                                <div className="card-offset-item text-right">
                                    <button type="button" className="btn-raised btn btn-danger btn-circle btn-lg"><em className="ion-ios-heart-outline"></em></button>
                                </div>
                            </div>
                            <div className="card-body pt0">
                                <div className="clearfix">
                                    <p className="pull-left mr"><a href="#"><img src="img/user/02.jpg" alt="User" className="img-circle thumb48" /></a></p>
                                    <div className="oh">
                                        <div className="mt-sm"><strong className="mr-sm">Cody Armstrong</strong></div>
                                        <div className="clearfix">
                                            <div className="pull-left text-muted"><span>a week ago</span></div>
                                        </div>
                                    </div>
                                </div>
                                <div className="container container-xs reader-block">
                                    <p>Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Morbi sollicitudin, massa quis posuere mollis, turpis mi suscipit magna, ac tristique tellus arcu pretium mi. Sed quam magna, tincidunt ac fermentum eget, vestibulum id magna. Nullam felis ipsum, tempus at fermentum nec, porttitor eu libero. Curabitur porttitor fermentum dapibus.</p>
                                    <p>Praesent a rhoncus magna. Nam nec tellus tortor. Proin adipiscing, dolor faucibus ullamcorper interdum, urna justo convallis arcu, ultrices auctor enim turpis ut purus. Fusce a egestas turpis. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin euismod malesuada dui ac laoreet. Donec purus leo, lobortis vel volutpat vel, dictum nec augue. Proin ullamcorper lectus ut libero blandit pretium.</p>
                                    <p className="text-center"><img src="img/pic5.jpg" alt="Article Image" className="img-thumbnail" /></p>
                                    <p>Cras fringilla ipsum sed elit sodales malesuada. Nam sem magna, tristique non facilisis a, porta ut elit. Vestibulum mauris nisl, dictum vitae dapibus iaculis, tempor sed libero. Ut vestibulum est eget justo facilisis ullamcorper. Pellentesque sodales aliquam lorem, eu tempor enim pretium ac. Aliquam vitae purus dolor. Etiam odio ante, placerat eu accumsan ut, consectetur vel mi. Ut eu aliquam orci.</p>
                                </div>
                            </div>
                            <div className="card-body bg-gray-lighter">
                                <form action="" className="mt">
                                    <div className="input-group mda-input-group">
                                        <div className="mda-form-group float-label">
                                            <div className="mda-form-control">
                                                <textarea rows="1" aria-multiline="true" tabIndex="0" aria-invalid="false" className="form-control"></textarea>
                                                <div className="mda-form-control-line"></div>
                                                <label className="m0">Comments</label>
                                            </div><span className="mda-form-msg right">Any message here</span>
                                        </div><span className="input-group-btn">
                                            <button type="button" className="btn btn-flat btn-success btn-circle"><em className="ion-checkmark-round"></em></button></span>
                                    </div>
                                </form>
                                <div className="mt">
                                    <div className="media">
                                        <div className="media-left"><a href="#"><img src="img/user/01.jpg" alt="Image Post" className="media-object img-circle thumb48" /></a></div>
                                        <div className="media-body">
                                            <h6 className="mt0 mb-sm">Joel Murray<small className="text-muted">2 days ago</small></h6>
                                            <div className="mb">Pellentesque mauris orci, lobortis in tristique mollis, consequat eu magna.</div>
                                            <ul className="list-inline">
                                                <li><a href="#" className="text-soft"><em className="ion-thumbsup icon-2x"></em></a></li>
                                                <li><a href="#" className="text-soft"><em className="ion-thumbsdown icon-2x"></em></a></li>
                                                <li><a href="#" className="text-soft"><em className="ion-android-share-alt icon-2x"></em></a></li>
                                            </ul>
                                        </div>
                                    </div>
                                    <div className="media">
                                        <div className="media-left"><a href="#"><img src="img/user/02.jpg" alt="Image Post" className="media-object img-circle thumb48" /></a></div>
                                        <div className="media-body">
                                            <h6 className="mt0 mb-sm">Daryl Parker<small className="text-muted">2 days ago</small></h6>
                                            <div className="mb">Duis elit dolor, gravida vitae varius in, viverra eu nibh. In sit amet accumsan arcu.</div>
                                            <ul className="list-inline">
                                                <li><a href="#" className="text-soft"><em className="ion-thumbsup icon-2x"></em></a></li>
                                                <li><a href="#" className="text-soft"><em className="ion-thumbsdown icon-2x"></em></a></li>
                                                <li><a href="#" className="text-soft"><em className="ion-android-share-alt icon-2x"></em></a></li>
                                            </ul>
                                            <div className="media">
                                                <div className="media-left"><a href="#"><img src="img/user/03.jpg" alt="Image Post" className="media-object img-circle thumb48" /></a></div>
                                                <div className="media-body">
                                                    <h6 className="mt0 mb-sm">Eddie Kelly<small className="text-muted">2 days ago</small></h6>
                                                    <div className="mb">Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Nulla condimentum viverra vestibulum. Fusce eleifend ornare ante at dignissim.</div>
                                                    <ul className="list-inline">
                                                        <li><a href="#" className="text-soft"><em className="ion-thumbsup icon-2x"></em></a></li>
                                                        <li><a href="#" className="text-soft"><em className="ion-thumbsdown icon-2x"></em></a></li>
                                                        <li><a href="#" className="text-soft"><em className="ion-android-share-alt icon-2x"></em></a></li>
                                                    </ul>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div className="clearfix mv-lg">
                            <h5 className="pull-left"><a href="#" className="btn btn-circle btn-default"><em className="ion-ios-arrow-thin-left icon-2x"></em></a><span className="ml text-white">Older</span></h5>
                            <h5 className="pull-right"><span className="mr text-white">Newer</span><a href="#" className="btn btn-circle btn-default"><em className="ion-ios-arrow-thin-right icon-2x"></em></a></h5>
                        </div>
                    </div>
                </Grid>
            </section>
        );
    }
}

export default BlogArticle;
