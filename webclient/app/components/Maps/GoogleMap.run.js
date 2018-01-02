function googleMaps() {

    if (document.getElementById('map')) {
        var map = new GMaps({
            div: '#map',
            lat: -12.043333,
            lng: -77.028333
        });

        GMaps.geocode({
            address: '276 N TUSTIN ST, ORANGE, CA 92867',
            callback: function(results, status) {
                if (status === 'OK') {
                    var latlng = results[0].geometry.location;
                    map.setCenter(latlng.lat(), latlng.lng());
                    map.addMarker({
                        lat: latlng.lat(),
                        lng: latlng.lng()
                    });
                }
            }
        });
    }

    if (document.getElementById('map-markers')) {

        var mapMarkers = new GMaps({
            div: '#map-markers',
            lat: -12.043333,
            lng: -77.028333
        });
        mapMarkers.addMarker({
            lat: -12.043333,
            lng: -77.03,
            title: 'Lima',
            details: {
                database_id: 42,
                author: 'HPNeo'
            },
            click: function(e) {
                if (console.log)
                    console.log(e);
                alert('You clicked in this marker');
            }
        });
        mapMarkers.addMarker({
            lat: -12.042,
            lng: -77.028333,
            title: 'Marker with InfoWindow',
            infoWindow: {
                content: '<p>HTML Content</p>'
            }
        });
    }

    // Panorama

    if (document.getElementById('panorama')) {
        GMaps.createPanorama({
            el: '#panorama',
            lat: 42.3455,
            lng: -71.0983
        });
    }
}

export default googleMaps;
