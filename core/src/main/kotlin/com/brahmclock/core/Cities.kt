package com.brahmclock.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Small offline city list for manual location and for naming GPS fixes without a geocoder. */
object Cities {
    val all: List<GeoPoint> = listOf(
        GeoPoint(28.6139, 77.2090, "New Delhi"),
        GeoPoint(19.0760, 72.8777, "Mumbai"),
        GeoPoint(12.9716, 77.5946, "Bengaluru"),
        GeoPoint(13.0827, 80.2707, "Chennai"),
        GeoPoint(22.5726, 88.3639, "Kolkata"),
        GeoPoint(17.3850, 78.4867, "Hyderabad"),
        GeoPoint(18.5204, 73.8567, "Pune"),
        GeoPoint(23.0225, 72.5714, "Ahmedabad"),
        GeoPoint(26.9124, 75.7873, "Jaipur"),
        GeoPoint(26.8467, 80.9462, "Lucknow"),
        GeoPoint(26.4499, 80.3319, "Kanpur"),
        GeoPoint(21.1458, 79.0882, "Nagpur"),
        GeoPoint(22.7196, 75.8577, "Indore"),
        GeoPoint(23.2599, 77.4126, "Bhopal"),
        GeoPoint(26.2183, 78.1828, "Gwalior"),
        GeoPoint(23.1765, 75.7885, "Ujjain"),
        GeoPoint(25.5941, 85.1376, "Patna"),
        GeoPoint(25.3176, 82.9739, "Varanasi"),
        GeoPoint(25.4358, 81.8463, "Prayagraj"),
        GeoPoint(27.1767, 78.0081, "Agra"),
        GeoPoint(27.4924, 77.6737, "Mathura"),
        GeoPoint(29.9457, 78.1642, "Haridwar"),
        GeoPoint(30.0869, 78.2676, "Rishikesh"),
        GeoPoint(30.3165, 78.0322, "Dehradun"),
        GeoPoint(30.7333, 76.7794, "Chandigarh"),
        GeoPoint(31.6340, 74.8723, "Amritsar"),
        GeoPoint(34.0837, 74.7973, "Srinagar"),
        GeoPoint(32.7266, 74.8570, "Jammu"),
        GeoPoint(31.1048, 77.1734, "Shimla"),
        GeoPoint(21.1702, 72.8311, "Surat"),
        GeoPoint(22.3072, 73.1812, "Vadodara"),
        GeoPoint(22.3039, 70.8022, "Rajkot"),
        GeoPoint(24.5854, 73.7125, "Udaipur"),
        GeoPoint(26.2389, 73.0243, "Jodhpur"),
        GeoPoint(21.2514, 81.6296, "Raipur"),
        GeoPoint(23.3441, 85.3096, "Ranchi"),
        GeoPoint(20.2961, 85.8245, "Bhubaneswar"),
        GeoPoint(19.8135, 85.8312, "Puri"),
        GeoPoint(26.1445, 91.7362, "Guwahati"),
        GeoPoint(17.6868, 83.2185, "Visakhapatnam"),
        GeoPoint(16.5062, 80.6480, "Vijayawada"),
        GeoPoint(13.6288, 79.4192, "Tirupati"),
        GeoPoint(9.9252, 78.1198, "Madurai"),
        GeoPoint(11.0168, 76.9558, "Coimbatore"),
        GeoPoint(9.9312, 76.2673, "Kochi"),
        GeoPoint(8.5241, 76.9366, "Thiruvananthapuram"),
        GeoPoint(12.2958, 76.6394, "Mysuru"),
        GeoPoint(12.9141, 74.8560, "Mangaluru"),
        GeoPoint(15.4909, 73.8278, "Panaji"),
        GeoPoint(19.9975, 73.7898, "Nashik"),
        GeoPoint(27.7172, 85.3240, "Kathmandu"),
        GeoPoint(23.8103, 90.4125, "Dhaka"),
        GeoPoint(6.9271, 79.8612, "Colombo"),
        GeoPoint(24.8607, 67.0011, "Karachi"),
        GeoPoint(25.2048, 55.2708, "Dubai"),
        GeoPoint(1.3521, 103.8198, "Singapore"),
        GeoPoint(13.7563, 100.5018, "Bangkok"),
        GeoPoint(35.6762, 139.6503, "Tokyo"),
        GeoPoint(-33.8688, 151.2093, "Sydney"),
        GeoPoint(51.5074, -0.1278, "London"),
        GeoPoint(48.8566, 2.3522, "Paris"),
        GeoPoint(52.5200, 13.4050, "Berlin"),
        GeoPoint(55.7558, 37.6173, "Moscow"),
        GeoPoint(40.7128, -74.0060, "New York"),
        GeoPoint(43.6532, -79.3832, "Toronto"),
        GeoPoint(41.8781, -87.6298, "Chicago"),
        GeoPoint(37.7749, -122.4194, "San Francisco"),
        GeoPoint(34.0522, -118.2437, "Los Angeles"),
        GeoPoint(-26.2041, 28.0473, "Johannesburg"),
        GeoPoint(-1.2921, 36.8219, "Nairobi"),
    ).sortedBy { it.label }

    fun search(query: String): List<GeoPoint> =
        if (query.isBlank()) all else all.filter { it.label.contains(query.trim(), ignoreCase = true) }

    /** Nearest listed city within [maxKm], used to label a GPS fix. */
    fun nearest(lat: Double, lon: Double, maxKm: Double = 60.0): GeoPoint? =
        all.map { it to distanceKm(lat, lon, it.lat, it.lon) }
            .filter { it.second <= maxKm }
            .minByOrNull { it.second }
            ?.first

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a))
    }
}
