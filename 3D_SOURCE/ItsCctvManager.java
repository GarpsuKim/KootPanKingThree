import javafx.application.Platform;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.PixelFormat;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * ItsCctvManager (JavaFX 버전)
 *
 * ── API ──────────────────────────────────────────────────────
 *   엔드포인트 : https://openapi.its.go.kr:9443/cctvInfo
 *   파라미터   : apiKey, type=its(국도), cctvType=3(정지영상 JPEG URL),
 *               minX/maxX/minY/maxY (경도/위도 전국 범위), getType=json
 *   응답 JSON  : response.data[].{ cctvname, cctvurl, coordx, coordy }
 *
 * ── 동작 방식 ─────────────────────────────────────────────────
 *   cctvType=3 으로 JPEG URL 목록을 받아온 뒤,
 *   JavaFX Timeline 으로 REFRESH_SEC 초마다 이미지를 fetch →
 *   BufferedImage → WritableImage 변환 → HostCallback.setItsCctvImage() 호출.
 *
 * ── 변경 내역 (Swing → JavaFX) ───────────────────────────────
 *   · javax.swing.Timer          → javafx.animation.Timeline
 *   · SwingUtilities.invokeLater → Platform.runLater
 *   · BufferedImage 콜백 제거    → WritableImage 직접 변환 후 전달
 *   · HostCallback 인터페이스     → JavaFX 친화적으로 재정의
 *
 * ── 순수 Java + JavaFX ───────────────────────────────────────
 *   외부 라이브러리 불필요. HttpURLConnection + ImageIO + JavaFX Image API.
 */
public class ItsCctvManager {

    // ── 공개 상수 ────────────────────────────────────────────
    public static final String BASE_URL =
        "https://openapi.its.go.kr:9443/cctvInfo";

    private static final double MIN_X = 124.0, MAX_X = 132.0;   // 경도 (전국)
    private static final double MIN_Y = 33.0,  MAX_Y = 39.0;    // 위도 (전국)

    private static final int REFRESH_SEC      = 5;        // 정지영상 갱신 주기 (초)
    private static final int CONNECT_TIMEOUT  = 8_000;
    private static final int READ_TIMEOUT     = 8_000;

    // ── CCTV 항목 ────────────────────────────────────────────
    public static class CctvItem {
        public final String name;
        public final String url;   // 정지영상 JPEG URL
        public final double x, y;
        public CctvItem(String name, String url, double x, double y) {
            this.name = name; this.url = url; this.x = x; this.y = y;
        }
        @Override public String toString() { return name; }
    }

    // ── 호스트 콜백 (JavaFX 전용) ────────────────────────────
    public interface HostCallback {
        /**
         * 갱신된 JPEG 이미지를 배경으로 설정.
         * FX Application Thread 에서 호출됨이 보장된다.
         *
         * @param label  "[ITS CCTV] 카메라명" 형태 레이블 (로그/UI 표시용)
         * @param image  변환된 WritableImage. null 이면 배경 초기화.
         */
        void setItsCctvImage(String label, WritableImage image);

        /** 화면 갱신 요청. FX Application Thread 에서 호출됨이 보장된다. */
        void repaintClock();
    }

    // ── 필드 ─────────────────────────────────────────────────
    private String           apiKey      = "";
    private List<CctvItem>   items       = new ArrayList<>();
    private List<CctvItem>   activeItems = new ArrayList<>();  // 필터 적용 목록 (없으면 전체)
    private int              current     = 0;
    private volatile boolean running     = false;

    /** JavaFX Timeline — Swing Timer 대체. FX 스레드에서만 접근. */
    private Timeline         timeline    = null;
    private final ExecutorService refreshExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ItsCctv-Refresh");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private volatile boolean    refreshPending  = false;
    private final AtomicLong    refreshVersion  = new AtomicLong(0L);

    private final HostCallback host;

    // ── 생성자 ───────────────────────────────────────────────
    public ItsCctvManager(HostCallback host) {
        this.host = host;
    }

    // ── 설정 접근자 ──────────────────────────────────────────
    public String        getApiKey()       { return apiKey; }
    public void          setApiKey(String k) { this.apiKey = k != null ? k.trim() : ""; }
    public boolean       isRunning()       { return running; }
    public List<CctvItem> getItems()       { return Collections.unmodifiableList(items); }
    public int           getCurrentIndex() { return current; }

    // ── 공개 API ─────────────────────────────────────────────

    /**
     * API 로 CCTV 목록 조회 (백그라운드 스레드).
     * 완료 시 FX 스레드에서 onSuccess / onError 콜백.
     */
    public void fetchList(Runnable onSuccess, Consumer<String> onError) {
        new Thread(() -> {
            try {
                List<CctvItem> result = callApi();
                Platform.runLater(() -> {
                    items       = result;
                    activeItems = new ArrayList<>(result);  // 기본: 전체
                    current     = 0;
                    onSuccess.run();
                });
            } catch (Exception e) {
                Platform.runLater(() -> onError.accept(e.getMessage()));
            }
        }, "ItsCctv-Fetch").start();
    }

    /**
     * 필터링된 목록(activeItems) 기준으로 인덱스 지정.
     * 선택 다이얼로그에서 선택한 아이템의 items(전체) 인덱스를 받아
     * activeItems 내 위치로 매핑한다.
     */
    public void select(int globalIndex) {
        if (items.isEmpty()) return;
        CctvItem target = items.get(Math.max(0, Math.min(globalIndex, items.size() - 1)));
        int ai = activeItems.indexOf(target);
        if (ai < 0) {
            // 필터에 없는 항목 → activeItems 를 전체로 리셋
            activeItems = new ArrayList<>(items);
            ai = activeItems.indexOf(target);
        }
        current = ai < 0 ? 0 : ai;
        fetchAndDisplay(activeItems.get(current));
        if (running) {
            stopTimeline();
            startTimeline();
        }
    }

    /** 다음 CCTV (activeItems 내 순환) */
    public void next() {
        if (activeItems.isEmpty()) return;
        current = (current + 1) % activeItems.size();
        fetchAndDisplay(activeItems.get(current));
        if (running) { stopTimeline(); startTimeline(); }
    }

    /** 이전 CCTV (activeItems 내 순환) */
    public void prev() {
        if (activeItems.isEmpty()) return;
        current = (current - 1 + activeItems.size()) % activeItems.size();
        fetchAndDisplay(activeItems.get(current));
        if (running) { stopTimeline(); startTimeline(); }
    }

    /**
     * 필터 설정 (선택 다이얼로그의 필터 필드 확인 시 호출).
     * keyword 가 비어 있으면 전체 목록으로 복원.
     * PgUp/PgDn(이전/다음) 순환이 필터 범위 안에서 이루어진다.
     */
    public void setFilter(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            activeItems = new ArrayList<>(items);
        } else {
            activeItems = new ArrayList<>();
            for (CctvItem it : items) {
                if (it.name.contains(keyword.trim())) activeItems.add(it);
            }
            if (activeItems.isEmpty()) activeItems = new ArrayList<>(items);
        }
        current = 0;
        System.out.println("[ItsCctv] 필터: " + keyword + " -> " + activeItems.size() + "개");
    }

    /** 갱신 타이머 시작 — FX 스레드에서 호출해야 한다. */
    public void start() {
        if (activeItems.isEmpty() && !items.isEmpty()) activeItems = new ArrayList<>(items);
        if (activeItems.isEmpty()) return;
        running = true;
        stopTimeline();
        refreshNow();          // 즉시 1회 표시
        startTimeline();
        System.out.println("[ItsCctv] 시작: " + currentName());
    }

    /** 갱신 타이머 중지 — FX 스레드에서 호출해야 한다. */
    public void stop() {
        running = false;
        refreshPending = false;
        refreshVersion.incrementAndGet();
        stopTimeline();
        System.out.println("[ItsCctv] 중지");
    }

    public String currentName() {
        if (activeItems.isEmpty()) return "";
        return activeItems.get(current).name;
    }

    // ── 내부: Timeline 관리 ──────────────────────────────────

    /** FX 스레드에서만 호출. */
    private void startTimeline() {
        timeline = new Timeline(new KeyFrame(
            Duration.seconds(REFRESH_SEC),
            e -> refreshNow()
        ));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    /** FX 스레드에서만 호출. */
    private void stopTimeline() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }

    // ── 내부: 이미지 갱신 ────────────────────────────────────

    private void refreshNow() {
        if (!running || activeItems.isEmpty()) return;
        long version = refreshVersion.incrementAndGet();
        if (!refreshInFlight.compareAndSet(false, true)) {
            refreshPending = true;
            return;
        }
        fetchAndDisplay(activeItems.get(current), version);
    }

    private void fetchAndDisplay(CctvItem item) {
        long version = refreshVersion.incrementAndGet();
        if (!refreshInFlight.compareAndSet(false, true)) {
            refreshPending = true;
            return;
        }
        fetchAndDisplay(item, version);
    }

    /**
     * 백그라운드 스레드에서 JPEG fetch → BufferedImage → WritableImage 변환 →
     * Platform.runLater 로 FX 스레드에서 HostCallback 호출.
     */
    private void fetchAndDisplay(CctvItem item, long version) {
        final String imgUrl = item.url;
        final String name   = item.name;
        refreshExecutor.execute(() -> {
            try {
                BufferedImage awt = fetchImage(imgUrl);
                if (awt == null) return;
                WritableImage fxImg = awtToFx(awt);
                Platform.runLater(() -> {
                    if (!running) return;   // 중지 후 큐 잔류 차단
                    if (version != refreshVersion.get()) return;
                    host.setItsCctvImage("[ITS CCTV] " + name, fxImg);
                    host.repaintClock();
                });
            } catch (Exception e) {
                if (version == refreshVersion.get()) {
                    System.out.println("[ItsCctv] 이미지 갱신 실패: " + e.getMessage());
                }
            } finally {
                refreshInFlight.set(false);
                if (running && refreshPending) {
                    refreshPending = false;
                    Platform.runLater(this::refreshNow);
                }
            }
        });
    }

    // ── 내부: AWT → FX 이미지 변환 ──────────────────────────

    /**
     * java.awt.image.BufferedImage → javafx.scene.image.WritableImage.
     * ARGB 픽셀 배열을 통해 변환하므로 javax.imageio 외 별도 라이브러리 불필요.
     */
    private static WritableImage awtToFx(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();

        // TYPE_INT_ARGB 로 통일 (원본 타입이 달라도 안전하게 변환)
        BufferedImage argb;
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            argb = src;
        } else {
            argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = argb.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
        }

        int[] pixels = new int[w * h];
        argb.getRGB(0, 0, w, h, pixels, 0, w);

        WritableImage fxImg = new WritableImage(w, h);
        PixelWriter pw = fxImg.getPixelWriter();
        pw.setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        return fxImg;
    }

    // ── 내부: API 호출 ───────────────────────────────────────

    private List<CctvItem> callApi() throws Exception {
        if (apiKey.isEmpty()) throw new Exception("API 키가 입력되지 않았습니다.");

        String urlStr = BASE_URL
            + "?apiKey=" + apiKey
            + "&type=its"
            + "&cctvType=3"
            + "&minX=" + MIN_X + "&maxX=" + MAX_X
            + "&minY=" + MIN_Y + "&maxY=" + MAX_Y
            + "&getType=json";

        String json = httpGet(urlStr);
        return parseJson(json);
    }

    /** JSON 파싱 (외부 라이브러리 없이 substring 방식) */
    private List<CctvItem> parseJson(String json) throws Exception {
        List<CctvItem> list = new ArrayList<>();

        int dataIdx = json.indexOf("\"data\"");
        if (dataIdx < 0) {
            String msg = extractField(json, "message");
            if (msg.isEmpty()) msg = "데이터 없음 (범위 내 CCTV 없음)";
            throw new Exception(msg);
        }

        int arrStart = json.indexOf('[', dataIdx);
        int arrEnd   = findMatchingBracket(json, arrStart);
        if (arrStart < 0 || arrEnd < 0) throw new Exception("JSON 파싱 오류");

        String arr = json.substring(arrStart + 1, arrEnd);
        int pos = 0;
        while (pos < arr.length()) {
            int ob = arr.indexOf('{', pos);
            if (ob < 0) break;
            int oe = findMatchingBrace(arr, ob);
            if (oe < 0) break;
            String obj  = arr.substring(ob + 1, oe);
            String name = extractField(obj, "cctvname");
            String url  = extractField(obj, "cctvurl");
            String sx   = extractField(obj, "coordx");
            String sy   = extractField(obj, "coordy");
            if (!name.isEmpty() && !url.isEmpty()) {
                double x = 0, y = 0;
                try { x = Double.parseDouble(sx); } catch (Exception ignored) {}
                try { y = Double.parseDouble(sy); } catch (Exception ignored) {}
                list.add(new CctvItem(name, url, x, y));
            }
            pos = oe + 1;
        }
        if (list.isEmpty()) throw new Exception("해당 범위에 정지영상 CCTV 없음");
        return list;
    }

    private String extractField(String obj, String key) {
        String pat = "\"" + key + "\"";
        int ki = obj.indexOf(pat);
        if (ki < 0) return "";
        int ci = obj.indexOf(':', ki + pat.length());
        if (ci < 0) return "";
        ci++;
        while (ci < obj.length() && obj.charAt(ci) == ' ') ci++;
        if (ci >= obj.length()) return "";
        if (obj.charAt(ci) == '"') {
            int end = obj.indexOf('"', ci + 1);
            return end < 0 ? "" : obj.substring(ci + 1, end);
        } else {
            int end = ci;
            while (end < obj.length() && obj.charAt(end) != ',' && obj.charAt(end) != '}') end++;
            return obj.substring(ci, end).trim();
        }
    }

    private int findMatchingBracket(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if      (s.charAt(i) == '[') depth++;
            else if (s.charAt(i) == ']') { if (--depth == 0) return i; }
        }
        return -1;
    }

    private int findMatchingBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if      (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}') { if (--depth == 0) return i; }
        }
        return -1;
    }

    // ── 내부: HTTP 유틸 ──────────────────────────────────────

    private BufferedImage fetchImage(String urlStr) throws Exception {
        @SuppressWarnings("deprecation")
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(CONNECT_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        con.setRequestProperty("Accept",     "image/jpeg,image/*");
        int code = con.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        BufferedImage img = javax.imageio.ImageIO.read(con.getInputStream());
        con.disconnect();
        return img;
    }

    private String httpGet(String urlStr) throws Exception {
        @SuppressWarnings("deprecation")
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(CONNECT_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        con.setRequestProperty("Accept", "application/json");
        int code = con.getResponseCode();
        BufferedReader br = new BufferedReader(
            new InputStreamReader(
                code == 200 ? con.getInputStream() : con.getErrorStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        con.disconnect();
        return sb.toString();
    }
}
