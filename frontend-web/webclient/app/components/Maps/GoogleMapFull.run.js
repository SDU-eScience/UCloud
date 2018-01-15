function googleMapsFull() {

    if (document.getElementById('mapfull-markers')) {

        var myMarkers = [
            {id: 0, name: 'Canada',        coords: {latitude: 56.130366, longitude: -106.346771 } },
            {id: 1, name: 'New York',      coords: {latitude: 40.712784, longitude: -74.005941 } },
            {id: 2, name: 'Toronto',       coords: {latitude: 43.653226, longitude: -79.383184 } },
            {id: 3, name: 'San Francisco', coords: {latitude: 37.774929, longitude: -122.419416 } },
            {id: 4, name: 'Utah',          coords: {latitude: 39.320980, longitude: -111.093731 } }
        ];

        var mapFull = new GMaps({
            div: '#mapfull-markers',
            lat: myMarkers[0].coords.latitude,
            lng: myMarkers[0].coords.longitude,
            zoom: 4
        });

        for (var i = 0; i < myMarkers.length; i++) {
            mapFull.addMarker({
                lat: myMarkers[i].coords.latitude,
                lng: myMarkers[i].coords.longitude,
                //title: 'Marker with InfoWindow',
                infoWindow: {
                    content: '<p>' + myMarkers[i].name + '</p>'
                }
            });
        }
        // Panto marker using the data attribute
        $('#markers-list').on('click', '[data-panto-marker]', function() {
            var markers = mapFull.markers;
            var id = $(this).data('pantoMarker');
            if (markers[id])
                mapFull.map.panTo(markers[id].getPosition());
        });
    }
}

export default googleMapsFull;