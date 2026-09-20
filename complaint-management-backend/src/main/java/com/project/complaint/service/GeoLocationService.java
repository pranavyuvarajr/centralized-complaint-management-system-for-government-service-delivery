package com.project.complaint.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.complaint.exception.GeocoderUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Location helpers for the complaint form:
 * <ul>
 *   <li>forward search (address / street / place name -> coordinates) for the
 *   location search box,</li>
 *   <li>reverse lookup (coordinates -> readable address), also used to store a
 *   human-readable address on each complaint that has a location,</li>
 *   <li>coordinate validation.</li>
 * </ul>
 * Search uses Photon (an OpenStreetMap geocoder built for search-as-you-type,
 * good at partial words and small streets) as the primary provider and
 * Nominatim as a fallback / recall booster; which one goes first, their base
 * URLs (e.g. to point at a self-hosted instance) and an optional country
 * preference are configurable via app.geocoder.* properties. Every lookup is
 * best-effort with short timeouts and a small in-memory cache.
 * <p>
 * The photo's EXIF GPS reader is kept for reference but is no longer used to
 * decide a complaint's location — photo and location are independent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeoLocationService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private static final String USER_AGENT = "ComplaintManagementSystem/1.0 (civic complaint routing)";
    private static final int MAX_RESULTS = 10;
    /** If the primary provider gives fewer than this, the other one is asked too and results are merged. */
    private static final int MIN_RESULTS_BEFORE_SECOND_OPINION = 3;

    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final int CACHE_MAX_ENTRIES = 500;
    /** Nominatim's public usage policy: at most one request per second. */
    private static final long NOMINATIM_MIN_GAP_MS = 1100;

    private static final Set<String> POI_KEYS = Set.of("amenity", "shop", "tourism", "leisure", "office", "craft",
            "healthcare", "historic", "man_made", "public_transport", "railway", "aeroway");

    private final ObjectMapper objectMapper;

    @Value("${app.geocoder.provider:photon}")
    private String provider;
    @Value("${app.geocoder.photon-url:https://photon.komoot.io}")
    private String photonUrl;
    @Value("${app.geocoder.nominatim-url:https://nominatim.openstreetmap.org}")
    private String nominatimUrl;
    /** Comma-separated ISO country codes to prefer (e.g. "in"); blank = worldwide. */
    @Value("${app.geocoder.country-codes:in}")
    private String countryCodes;

    private record CacheEntry<T>(T value, long expiresAt) {}

    private final Map<String, CacheEntry<List<SearchResult>>> searchCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<String>> reverseCache = new ConcurrentHashMap<>();
    private final Object nominatimLock = new Object();
    private long lastNominatimCallMs = 0;

    /** Result of a location lookup: coordinates plus how reliable they are. */
    public record GeoResult(double latitude, double longitude, boolean fromPhotoGps) {}

    /**
     * A single forward-geocoding result. displayName is the full one-line
     * label (what gets shown/stored); name + secondary split it into a
     * primary line (place or street) and a context line (area, city, state)
     * for the picker UI; type is one of street, place, poi, address.
     */
    public record SearchResult(String displayName, String name, String secondary, String type,
                               double latitude, double longitude) {}

    // ------------------------------------------------------------------
    // Coordinates / EXIF
    // ------------------------------------------------------------------

    /**
     * Reads GPS tags from a photo's EXIF data. No longer used to place
     * complaints (photo and location are independent); kept as a utility.
     */
    public Optional<double[]> extractGpsFromPhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) return Optional.empty();
        try (ByteArrayInputStream in = new ByteArrayInputStream(file.getBytes())) {
            Metadata metadata = ImageMetadataReader.readMetadata(in);
            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDirectory == null) return Optional.empty();
            GeoLocation geoLocation = gpsDirectory.getGeoLocation();
            if (geoLocation == null || geoLocation.isZero()) return Optional.empty();
            return Optional.of(new double[]{geoLocation.getLatitude(), geoLocation.getLongitude()});
        } catch (Exception e) {
            log.debug("No readable GPS EXIF in uploaded photo: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isValidCoordinate(Double latitude, Double longitude) {
        return latitude != null && longitude != null
                && !latitude.isNaN() && !longitude.isNaN()
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180
                && !(latitude == 0 && longitude == 0);
    }

    // ------------------------------------------------------------------
    // Reverse geocoding
    // ------------------------------------------------------------------

    /**
     * Human-readable address for coordinates. Best-effort: on any failure
     * falls back to a plain coordinate string so it never blocks a complaint
     * from being submitted.
     */
    public String reverseGeocode(double latitude, double longitude) {
        return reverseLookup(latitude, longitude)
                .orElseGet(() -> String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude));
    }

    /** Address for coordinates, or empty when no provider could resolve one. */
    public Optional<String> reverseLookup(double latitude, double longitude) {
        String key = String.format(Locale.ROOT, "%.5f,%.5f", latitude, longitude);
        CacheEntry<String> cached = reverseCache.get(key);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            return Optional.ofNullable(cached.value());
        }

        String address = null;
        try {
            address = reverseNominatim(latitude, longitude);
        } catch (Exception e) {
            log.debug("Nominatim reverse geocoding failed: {}", e.getMessage());
        }
        if (address == null) {
            try {
                address = reversePhoton(latitude, longitude);
            } catch (Exception e) {
                log.debug("Photon reverse geocoding failed: {}", e.getMessage());
            }
        }

        if (address != null) {
            putCache(reverseCache, key, address);
        }
        return Optional.ofNullable(address);
    }

    private String reverseNominatim(double lat, double lon) throws IOException, InterruptedException {
        String url = String.format(Locale.ROOT,
                "%s/reverse?lat=%f&lon=%f&format=json&zoom=18&addressdetails=0&accept-language=en",
                trimSlash(nominatimUrl), lat, lon);
        throttleNominatim();
        JsonNode root = getJson(url);
        JsonNode displayName = root.get("display_name");
        return displayName != null && !displayName.asText().isBlank() ? displayName.asText() : null;
    }

    private String reversePhoton(double lat, double lon) throws IOException, InterruptedException {
        String url = String.format(Locale.ROOT, "%s/reverse?lon=%f&lat=%f&lang=en", trimSlash(photonUrl), lon, lat);
        JsonNode features = getJson(url).path("features");
        if (features.isArray() && !features.isEmpty()) {
            SearchResult result = parsePhotonFeature(features.get(0));
            if (result != null) return result.displayName();
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Forward search
    // ------------------------------------------------------------------

    /**
     * Searches places, streets and addresses. lat/lon (optional) bias the
     * results toward where the user is looking. Returns an empty list when
     * nothing matches; throws {@link GeocoderUnavailableException} only when
     * every provider failed.
     */
    public List<SearchResult> search(String query, Double lat, Double lon) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) return List.of();
        if (q.length() > 200) q = q.substring(0, 200);

        boolean biased = isValidCoordinate(lat, lon);
        String key = q.toLowerCase(Locale.ROOT) + "|" + (biased ? String.format(Locale.ROOT, "%.2f,%.2f", lat, lon) : "-");
        CacheEntry<List<SearchResult>> cached = searchCache.get(key);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) {
            return cached.value();
        }

        boolean photonFirst = !"nominatim".equalsIgnoreCase(provider == null ? "" : provider.trim());
        List<SearchResult> primary = null;
        List<SearchResult> secondary = null;
        Exception firstFailure = null;

        try {
            primary = photonFirst ? searchPhoton(q, biased ? lat : null, biased ? lon : null)
                                  : searchNominatim(q, biased ? lat : null, biased ? lon : null);
        } catch (Exception e) {
            firstFailure = e;
            log.debug("Primary geocoder failed for \"{}\": {}", q, e.getMessage());
        }

        if (primary == null || primary.size() < MIN_RESULTS_BEFORE_SECOND_OPINION) {
            try {
                secondary = photonFirst ? searchNominatim(q, biased ? lat : null, biased ? lon : null)
                                        : searchPhoton(q, biased ? lat : null, biased ? lon : null);
            } catch (Exception e) {
                log.debug("Secondary geocoder failed for \"{}\": {}", q, e.getMessage());
            }
        }

        if (primary == null && secondary == null) {
            throw new GeocoderUnavailableException(
                    "Location search is temporarily unavailable. You can still tap the map to drop a pin.");
        }

        List<SearchResult> merged = merge(primary, secondary);
        putCache(searchCache, key, merged);
        return merged;
    }

    private List<SearchResult> searchPhoton(String q, Double lat, Double lon) throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(trimSlash(photonUrl))
                .append("/api?q=").append(URLEncoder.encode(q, StandardCharsets.UTF_8))
                .append("&limit=15&lang=en");
        if (lat != null && lon != null) {
            url.append(String.format(Locale.ROOT, "&lat=%f&lon=%f", lat, lon));
        }
        JsonNode features = getJson(url.toString()).path("features");

        List<SearchResult> all = new ArrayList<>();
        List<SearchResult> preferredCountry = new ArrayList<>();
        Set<String> preferred = preferredCountries();
        if (features.isArray()) {
            for (JsonNode feature : features) {
                SearchResult r = parsePhotonFeature(feature);
                if (r == null) continue;
                all.add(r);
                String cc = feature.path("properties").path("countrycode").asText("").toLowerCase(Locale.ROOT);
                if (preferred.isEmpty() || cc.isEmpty() || preferred.contains(cc)) {
                    preferredCountry.add(r);
                }
            }
        }
        // Prefer the configured country, but never hide everything if nothing matches it.
        return preferredCountry.isEmpty() ? all : preferredCountry;
    }

    private List<SearchResult> searchNominatim(String q, Double lat, Double lon) throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(trimSlash(nominatimUrl))
                .append("/search?q=").append(URLEncoder.encode(q, StandardCharsets.UTF_8))
                .append("&format=jsonv2&addressdetails=1&dedupe=1&limit=").append(MAX_RESULTS)
                .append("&accept-language=en");
        String codes = String.join(",", preferredCountries());
        if (!codes.isEmpty()) url.append("&countrycodes=").append(codes);
        if (lat != null && lon != null) {
            // A soft preference (not bounded=1): results inside this box rank first.
            url.append(String.format(Locale.ROOT, "&viewbox=%.4f,%.4f,%.4f,%.4f",
                    lon - 0.5, lat + 0.5, lon + 0.5, lat - 0.5));
        }
        throttleNominatim();
        JsonNode root = getJson(url.toString());

        List<SearchResult> results = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                SearchResult r = parseNominatimNode(node);
                if (r != null) results.add(r);
            }
        }
        return results;
    }

    // ------------------------------------------------------------------
    // Parsing / labelling
    // ------------------------------------------------------------------

    private SearchResult parsePhotonFeature(JsonNode feature) {
        JsonNode props = feature.path("properties");
        JsonNode coords = feature.path("geometry").path("coordinates");
        if (!coords.isArray() || coords.size() < 2) return null;
        double lon = coords.get(0).asDouble();
        double lat = coords.get(1).asDouble();
        if (!isValidCoordinate(lat, lon)) return null;

        String name = text(props, "name");
        String street = text(props, "street");
        String house = text(props, "housenumber");
        String type = text(props, "type");
        String osmKey = text(props, "osm_key");

        String primary = !name.isEmpty() ? name
                : !street.isEmpty() ? (house.isEmpty() ? street : house + " " + street)
                : firstNonEmpty(text(props, "locality"), text(props, "district"), text(props, "city"),
                        text(props, "county"), text(props, "state"));
        if (primary.isEmpty()) return null;

        List<String> context = new ArrayList<>();
        if (!name.isEmpty() && !street.isEmpty()) context.add(house.isEmpty() ? street : street + " " + house);
        context.add(text(props, "locality"));
        context.add(text(props, "district"));
        context.add(text(props, "city"));
        context.add(text(props, "county"));
        context.add(text(props, "state"));
        context.add(text(props, "postcode"));
        String secondary = joinDistinct(context, primary);

        String kind;
        if ("highway".equals(osmKey) || "street".equals(type)) kind = "street";
        else if ("place".equals(osmKey) || "boundary".equals(osmKey)) kind = "place";
        else if (POI_KEYS.contains(osmKey)) kind = "poi"; // a named shop/cafe/office etc., even though Photon types it "house"
        else if ("house".equals(type)) kind = "address";
        else if (Set.of("locality", "district", "city", "state", "county", "country", "region").contains(type)) kind = "place";
        else kind = "poi";

        return new SearchResult(secondary.isEmpty() ? primary : primary + ", " + secondary,
                primary, secondary, kind, lat, lon);
    }

    private SearchResult parseNominatimNode(JsonNode node) {
        JsonNode latNode = node.get("lat");
        JsonNode lonNode = node.get("lon");
        if (latNode == null || lonNode == null) return null;
        double lat = latNode.asDouble();
        double lon = lonNode.asDouble();
        if (!isValidCoordinate(lat, lon)) return null;

        JsonNode address = node.path("address");
        String name = text(node, "name");
        String road = firstNonEmpty(text(address, "road"), text(address, "pedestrian"), text(address, "footway"),
                text(address, "path"), text(address, "residential"));
        String house = text(address, "house_number");
        String area = firstNonEmpty(text(address, "neighbourhood"), text(address, "suburb"), text(address, "quarter"),
                text(address, "city_district"), text(address, "hamlet"));
        String town = firstNonEmpty(text(address, "city"), text(address, "town"), text(address, "village"),
                text(address, "municipality"));

        String primary = !name.isEmpty() ? name
                : !road.isEmpty() ? (house.isEmpty() ? road : house + " " + road)
                : firstNonEmpty(area, town, text(address, "county"), text(address, "state"));
        if (primary.isEmpty()) {
            String display = text(node, "display_name");
            if (display.isEmpty()) return null;
            primary = display.split(",")[0].trim();
        }

        List<String> context = new ArrayList<>();
        if (!name.isEmpty() && !road.isEmpty()) context.add(road);
        context.add(area);
        context.add(town);
        context.add(text(address, "state_district"));
        context.add(text(address, "state"));
        context.add(text(address, "postcode"));
        String secondary = joinDistinct(context, primary);

        String category = text(node, "category");
        String kind;
        if ("highway".equals(category)) kind = "street";
        else if ("place".equals(category) || "boundary".equals(category)) kind = "place";
        else if ("building".equals(category) && !house.isEmpty()) kind = "address";
        else kind = "poi";

        String displayName = secondary.isEmpty() ? primary : primary + ", " + secondary;
        if (secondary.isEmpty() && !text(node, "display_name").isEmpty()) displayName = text(node, "display_name");
        return new SearchResult(displayName, primary, secondary, kind, lat, lon);
    }

    /** Primary results first, then extra results from the other provider that aren't near-duplicates. */
    private List<SearchResult> merge(List<SearchResult> primary, List<SearchResult> secondary) {
        List<SearchResult> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<SearchResult> list : Arrays.asList(primary, secondary)) {
            if (list == null) continue;
            for (SearchResult r : list) {
                if (out.size() >= MAX_RESULTS) return out;
                // Same name within ~100 m (three decimals) counts as the same place.
                String key = r.name().toLowerCase(Locale.ROOT) + "|"
                        + String.format(Locale.ROOT, "%.3f,%.3f", r.latitude(), r.longitude());
                if (seen.add(key)) out.add(r);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private JsonNode getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " from " + URI.create(url).getHost());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    /** Spaces out calls to the public Nominatim instance to respect its one-request-per-second policy. */
    private void throttleNominatim() throws InterruptedException {
        synchronized (nominatimLock) {
            long wait = lastNominatimCallMs + NOMINATIM_MIN_GAP_MS - System.currentTimeMillis();
            if (wait > 0) Thread.sleep(wait);
            lastNominatimCallMs = System.currentTimeMillis();
        }
    }

    private Set<String> preferredCountries() {
        Set<String> codes = new HashSet<>();
        if (countryCodes != null) {
            for (String c : countryCodes.split(",")) {
                if (!c.isBlank()) codes.add(c.trim().toLowerCase(Locale.ROOT));
            }
        }
        return codes;
    }

    private <T> void putCache(Map<String, CacheEntry<T>> cache, String key, T value) {
        long now = System.currentTimeMillis();
        if (cache.size() >= CACHE_MAX_ENTRIES) {
            cache.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
            if (cache.size() >= CACHE_MAX_ENTRIES) cache.clear();
        }
        cache.put(key, new CacheEntry<>(value, now + CACHE_TTL_MS));
    }

    private static String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    /** Joins non-empty parts with ", ", dropping repeats and anything equal to the primary line. */
    private static String joinDistinct(List<String> parts, String primary) {
        List<String> kept = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(primary.toLowerCase(Locale.ROOT));
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (seen.add(p.toLowerCase(Locale.ROOT))) kept.add(p);
        }
        return String.join(", ", kept);
    }
}
