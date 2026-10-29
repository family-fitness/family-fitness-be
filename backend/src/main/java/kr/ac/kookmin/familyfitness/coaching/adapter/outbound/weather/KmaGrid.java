package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.weather;

/**
 * 기상청 동네예보 격자 좌표(nx, ny). 위도 · 경도를 람베르트 정각원추 도법(LCC)으로 5km 격자에 옮긴다.
 * 상수와 식은 기상청이 공공데이터포털에 함께 올린 「격자 위경도 변환」 예제를 그대로 따른다.
 */
record KmaGrid(int nx, int ny) {
    private static final double EARTH_RADIUS_KM = 6371.00877;
    private static final double GRID_KM = 5.0;
    private static final double STANDARD_LAT1 = 30.0;
    private static final double STANDARD_LAT2 = 60.0;
    private static final double ORIGIN_LON = 126.0;
    private static final double ORIGIN_LAT = 38.0;
    private static final double ORIGIN_X = 43;
    private static final double ORIGIN_Y = 136;

    static KmaGrid of(double latitude, double longitude) {
        double rad = Math.PI / 180.0;
        double re = EARTH_RADIUS_KM / GRID_KM;
        double slat1 = STANDARD_LAT1 * rad;
        double slat2 = STANDARD_LAT2 * rad;
        double olon = ORIGIN_LON * rad;
        double olat = ORIGIN_LAT * rad;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        double ra = Math.tan(Math.PI * 0.25 + latitude * rad * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = longitude * rad - olon;
        if (theta > Math.PI) theta -= 2.0 * Math.PI;
        if (theta < -Math.PI) theta += 2.0 * Math.PI;
        theta *= sn;

        int nx = (int) Math.floor(ra * Math.sin(theta) + ORIGIN_X + 0.5);
        int ny = (int) Math.floor(ro - ra * Math.cos(theta) + ORIGIN_Y + 0.5);
        return new KmaGrid(nx, ny);
    }
}
