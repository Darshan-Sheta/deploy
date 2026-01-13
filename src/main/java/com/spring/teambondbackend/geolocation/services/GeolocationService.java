package com.spring.teambondbackend.geolocation.services;

import com.opencagedata.jopencage.JOpenCageGeocoder;
import com.opencagedata.jopencage.model.JOpenCageForwardRequest;
import com.opencagedata.jopencage.model.JOpenCageLatLng;
import com.opencagedata.jopencage.model.JOpenCageResponse;
import com.opencagedata.jopencage.model.JOpenCageReverseRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GeolocationService {
    @Value("${open.cage.api}")
    private String apiKey ;
    public String getLocationFromCoordinates(Double latitude , Double longitude){

        JOpenCageGeocoder jOpenCageGeocoder = new JOpenCageGeocoder(apiKey);

        JOpenCageReverseRequest request = new JOpenCageReverseRequest( latitude , longitude );
        request.setLanguage("en"); // show results in a specific language using an IETF format language code
        request.setLimit(5); // only return the first 5 results (default is 10)
        request.setNoAnnotations(true); // exclude additional info such as calling code, timezone, and currency
        request.setMinConfidence(3); // restrict to results with a confidence rating of at least 3 (out of 10)

        JOpenCageResponse response = jOpenCageGeocoder.reverse(request);

        // get the formatted address of the first result:
        String formattedAddress = response.getResults().get(0).getFormatted();
        // formattedAddress is now 'Travessera de Gràcia, 142, 08012 Barcelona, España'
        System.out.println(formattedAddress);
        return formattedAddress;
    }

    public List<Double> getCoordinatesFromLocation(String location){
        System.out.println(location);
        JOpenCageGeocoder jOpenCageGeocoder = new JOpenCageGeocoder(apiKey);
        JOpenCageForwardRequest request = new JOpenCageForwardRequest(location);

        JOpenCageResponse response = jOpenCageGeocoder.forward(request);
        System.out.println(response.getResults().get(0).getFormatted());
        JOpenCageLatLng firstResultLatLng = response.getFirstPosition(); // get the coordinate pair of the first result
        System.out.println(firstResultLatLng.getLat().toString() + "," + firstResultLatLng.getLng().toString()); // prints -33.9275623,18.4571101
        List<Double> coordinates = new ArrayList<>();
        coordinates.add(firstResultLatLng.getLat());
        coordinates.add(firstResultLatLng.getLng());
        return coordinates;
    }

}
