import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * CaptureManager - 화면 캡처 + IP 카메라 스트림 통합 클래스 (JavaFX 전용)
 *
 * ── Swing → JavaFX 변경사항 ──────────────────────────────────────────
 *   · JPanel / JFrame / ImageIcon 등 Swing 의존성 완전 제거
 *   · clockPanel: JPanel → javafx.scene.Node  (FX 스냅샷 캡처)
 *   · showImageWindow: JFrame → JavaFX Stage
 *   · Camera.FrameListener 콜백 타입: BufferedImage → WritableImage
 *     (WritableImage 는 FX 씬에 직접 주입 가능)
 *
 * ══════════════════════════════════════════════════════════════════
 *  ScreenCapture 기능 (CaptureManager 인스턴스 메서드)
 * ══════════════════════════════════════════════════════════════════
 *   captureClockScreen()  : ClockNode 스냅샷 → 임시 PNG   ★ FX 스레드 필요
 *   captureFullScreen()   : 전체 모니터 캡처  → 임시 PNG
 *   captureMonitor(int)   : 특정 모니터 캡처  → 임시 PNG
 *   showImageWindow(File) : 수신 이미지를 JavaFX Stage 서브 윈도우에 표시
 *
 * ══════════════════════════════════════════════════════════════════
 *  Camera 기능 (CaptureManager.Camera 이너 클래스)
 * ══════════════════════════════════════════════════════════════════
 *   cam.start(url)          : MJPEG 스트림 수신 시작
 *   cam.stop()              : 스트림 중지
 *   cam.capture(dir)        : 현재 프레임을 dir/img/cam_*.jpg 저장
 *   cam.isRunning()         : 스트림 실행 여부
 *   cam.getLastFrame()      : 마지막 수신 WritableImage  (씬 주입용)
 *   cam.getLastFrameAWT()   : 마지막 수신 BufferedImage (파일 저장용)
 */
public class CaptureManager {

    // ── 시계 노드 참조 (FX 스냅샷 캡처용) ──────────────────────────
    /** null 허용 — 캡처 불필요 시 null 전달 가능 */
    private final javafx.scene.Node clockNode;

    /** 여러 이미지 창이 겹치지 않도록 오프셋 순환 */
    private int imageWindowOffset = 0;

    // ── 생성자 ───────────────────────────────────────────────────────

    /**
     * @param clockNode 시계 씬 노드 (captureClockScreen 용). null 가능.
     */
    public CaptureManager(javafx.scene.Node clockNode) {
        this.clockNode = clockNode;
    }

    // ═══════════════════════════════════════════════════════════════
    //  ScreenCapture 기능 — 화면 캡처 및 이미지 표시
    // ═══════════════════════════════════════════════════════════════

    /**
     * ClockNode 스냅샷을 캡처하여 임시 PNG 파일로 저장.
     * <b>반드시 JavaFX Application Thread 에서 호출해야 한다.</b>
     * 백그라운드 스레드에서 필요하면 Platform.runLater 로 래핑하라.
     *
     * @return 저장된 PNG 파일
     */
    public File captureClockScreen() throws Exception {
        if (clockNode == null)
            throw new IllegalStateException("clockNode 가 설정되지 않았습니다.");

        WritableImage snapshot = clockNode.snapshot(null, null);
        BufferedImage  awtImg  = SwingFXUtils.fromFXImage(snapshot, null);

        File outFile = new File(System.getProperty("java.io.tmpdir"),
            "clock_capture_" + System.currentTimeMillis() + ".png");
        ImageIO.write(awtImg, "PNG", outFile);
        return outFile;
    }

    /**
     * 모든 모니터를 포함한 전체 화면을 캡처.
     * AWT Robot 을 사용하므로 백그라운드 스레드에서도 호출 가능.
     */
    public File captureFullScreen() throws Exception {
        java.awt.Rectangle fullBounds = new java.awt.Rectangle();
        for (java.awt.GraphicsDevice gd :
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            fullBounds = fullBounds.union(gd.getDefaultConfiguration().getBounds());
        }
        BufferedImage img = new java.awt.Robot().createScreenCapture(fullBounds);
        File outFile = new File(System.getProperty("java.io.tmpdir"),
            "screenshot_" + System.currentTimeMillis() + ".png");
        ImageIO.write(img, "PNG", outFile);
        return outFile;
    }

    /**
     * 특정 모니터를 캡처.
     * @param monitorIndex 0 부터 시작하는 모니터 인덱스
     */
    public File captureMonitor(int monitorIndex) throws Exception {
        java.awt.GraphicsDevice[] screens =
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        if (monitorIndex >= screens.length)
            throw new Exception("모니터 " + (monitorIndex + 1) + "이 없습니다. "
                + "(연결된 모니터: " + screens.length + "개)");
        java.awt.Rectangle bounds = screens[monitorIndex].getDefaultConfiguration().getBounds();
        BufferedImage img = new java.awt.Robot().createScreenCapture(bounds);
        File outFile = new File(System.getProperty("java.io.tmpdir"),
            "monitor" + (monitorIndex + 1) + "_" + System.currentTimeMillis() + ".png");
        ImageIO.write(img, "PNG", outFile);
        return outFile;
    }

    /**
     * 이미지 파일을 새 JavaFX Stage 서브 윈도우에 표시.
     * 화면 크기의 80% 를 최대 크기로 자동 스케일.
     * 여러 창이 열릴 경우 30px 씩 오프셋하여 겹침 방지.
     * 내부에서 Platform.runLater 를 사용하므로 어느 스레드에서나 호출 가능.
     *
     * @param imageFile 표시할 이미지 파일
     */
    public void showImageWindow(File imageFile) {
        final int offset = imageWindowOffset;
        imageWindowOffset = (imageWindowOffset + 1) % 10;

        Platform.runLater(() -> {
            try {
                Image fxImg = new Image(imageFile.toURI().toString(), true);

                javafx.geometry.Rectangle2D screen = Screen.getPrimary().getVisualBounds();
                double maxW = screen.getWidth()  * 0.80;
                double maxH = screen.getHeight() * 0.80;

                ImageView iv = new ImageView(fxImg);
                iv.setPreserveRatio(true);
                iv.setFitWidth(maxW);
                iv.setFitHeight(maxH);
                iv.setSmooth(true);

                StackPane pane = new StackPane(iv);
                pane.setPadding(new Insets(4));
                pane.setStyle("-fx-background-color: #1a1a1a;");

                Stage stage = new Stage(StageStyle.DECORATED);
                stage.setTitle("📷 " + imageFile.getName());
                stage.setAlwaysOnTop(true);
                stage.setScene(new Scene(pane, maxW + 8, maxH + 8, Color.BLACK));

                double ox = offset * 30;
                double oy = offset * 30;
                stage.setX(screen.getMinX() + (screen.getWidth()  - stage.getWidth())  / 2 + ox);
                stage.setY(screen.getMinY() + (screen.getHeight() - stage.getHeight()) / 2 + oy);

                stage.show();
                System.out.println("[ImageWindow] 표시: " + imageFile.getName());

            } catch (Exception e) {
                System.out.println("[ImageWindow] 표시 실패: " + e.getMessage());
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  Camera — IP Webcam MJPEG 스트림 수신
    //
    //  IP Webcam MJPEG 포맷:
    //    Content-Type: multipart/x-mixed-replace; boundary=--myboundary
    //    각 파트: --myboundary\r\nContent-Type: image/jpeg\r\n\r\n<JPEG>\r\n
    //
    //  저장 파일명: img/cam_yyyyMMdd_HHmmss_SSS.jpg
    //
    //  사용법:
    //    CaptureManager.Camera cam = new CaptureManager.Camera(frameListener);
    //    cam.start("http://192.168.x.x:8080");
    //    cam.stop();
    //    cam.capture(saveDir);
    // ═══════════════════════════════════════════════════════════════

    public static class Camera {

        /**
         * 새 프레임 도착 시 콜백.
         * WritableImage 는 JavaFX 이미지이므로 FX 씬에 즉시 적용 가능.
         * <b>콜백은 백그라운드(Camera-Reader) 스레드에서 호출된다.</b>
         * FX 씬 노드를 직접 수정하려면 Platform.runLater 를 사용하라.
         */
        public interface FrameListener {
            void onFrame(WritableImage frame);
        }

        private final FrameListener    listener;
        private volatile boolean       running      = false;
        private volatile BufferedImage lastFrameAWT = null;   // 파일 저장용
        private volatile WritableImage lastFrame    = null;   // FX 씬 주입용
        private Thread readerThread;

        public Camera(FrameListener listener) {
            this.listener = listener;
        }

        public boolean isRunning()             { return running; }
        public boolean isConnected()           { return running && lastFrame != null; }
        /** 마지막 수신 JavaFX 이미지 (FxGPUNeon 배경 주입용) */
        public WritableImage getLastFrame()    { return lastFrame; }
        /** 마지막 수신 AWT 이미지 (파일 저장용) */
        public BufferedImage getLastFrameAWT() { return lastFrameAWT; }

        /** MJPEG 스트림 수신 시작 */
        public void start(String streamUrl) {
            stop();
            running = true;
            readerThread = new Thread(() -> {
                int failCount = 0;
                final int MAX_FAIL = 5;
                while (running) {
                    try {
                        connectAndRead(streamUrl);
                        failCount = 0;
                    } catch (Exception e) {
                        if (running) {
                            failCount++;
                            System.out.println("[Camera] 연결 오류 (" + failCount + "/" + MAX_FAIL
                                + "), 3초 후 재시도: " + e.getMessage());
                            if (failCount >= MAX_FAIL) {
                                System.out.println("[Camera] 연속 " + MAX_FAIL + "회 실패 → 자동 중지");
                                running      = false;
                                lastFrame    = null;
                                lastFrameAWT = null;
                                break;
                            }
                            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                        }
                    }
                }
            }, "Camera-Reader");
            readerThread.setDaemon(true);
            readerThread.start();
            System.out.println("[Camera] 스트림 시작: " + streamUrl);
        }

        /** 스트림 중지 */
        public void stop() {
            running = false;
            if (readerThread != null) {
                readerThread.interrupt();
                readerThread = null;
            }
            lastFrame    = null;
            lastFrameAWT = null;
            System.out.println("[Camera] 스트림 중지");
        }

        /**
         * 현재 프레임을 saveDir/img/ 폴더에 저장.
         * 파일명: cam_yyyyMMdd_HHmmss_SSS.jpg
         * @return 저장된 파일 경로 (실패 시 null)
         */
        public String capture(File saveDir) {
            BufferedImage frame = lastFrameAWT;
            if (frame == null) {
                System.out.println("[Camera] 캡처 실패: 수신된 프레임 없음");
                return null;
            }
            try {
                File imgDir = new File(saveDir, "img");
                if (!imgDir.exists()) imgDir.mkdirs();

                String ts   = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
                File   file = new File(imgDir, "cam_" + ts + ".jpg");
                ImageIO.write(frame, "jpg", file);
                System.out.println("[Camera] 저장 완료: " + file.getAbsolutePath());
                return file.getAbsolutePath();
            } catch (Exception e) {
                System.out.println("[Camera] 저장 오류: " + e.getMessage());
                return null;
            }
        }

        // ── MJPEG 스트림 파싱 ───────────────────────────────────────

        private void connectAndRead(String streamUrl) throws Exception {
            @SuppressWarnings("deprecation")
            URL url = new URL(streamUrl + "/video");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.connect();

            String contentType = conn.getContentType();
            String boundary = "--myboundary";
            if (contentType != null && contentType.contains("boundary=")) {
                boundary = contentType.split("boundary=")[1].trim();
                if (!boundary.startsWith("--")) boundary = "--" + boundary;
            }

            InputStream in = new BufferedInputStream(conn.getInputStream(), 65536);

            while (running) {
                if (!skipToBoundary(in, boundary)) break;

                int contentLength = -1;
                String hLine;
                while (!(hLine = readLine(in)).isEmpty()) {
                    if (hLine.toLowerCase().startsWith("content-length:")) {
                        try { contentLength = Integer.parseInt(hLine.split(":")[1].trim()); }
                        catch (Exception ignored) {}
                    }
                }

                byte[] jpegBytes;
                if (contentLength > 0) {
                    jpegBytes = readBytes(in, contentLength);
                } else {
                    jpegBytes = readUntilBoundary(in, boundary);
                }
                if (jpegBytes == null || jpegBytes.length == 0) continue;

                try {
                    BufferedImage awtImg = ImageIO.read(new ByteArrayInputStream(jpegBytes));
                    if (awtImg != null) {
                        // AWT → JavaFX WritableImage 변환 (백그라운드 스레드에서 안전)
                        WritableImage fxImg = SwingFXUtils.toFXImage(awtImg, null);
                        lastFrameAWT = awtImg;
                        lastFrame    = fxImg;
                        if (listener != null) listener.onFrame(fxImg);
                    }
                } catch (Exception ignored) {}
            }
            conn.disconnect();
        }

        private boolean skipToBoundary(InputStream in, String boundary) throws IOException {
            while (running) {
                String line = readLine(in);
                if (line == null) return false;
                if (line.startsWith(boundary)) return true;
            }
            return false;
        }

        private String readLine(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') break;
                if (c != '\r') sb.append((char) c);
            }
            return c == -1 ? null : sb.toString();
        }

        private byte[] readBytes(InputStream in, int len) throws IOException {
            byte[] buf = new byte[len];
            int    off = 0;
            while (off < len) {
                int n = in.read(buf, off, len - off);
                if (n < 0) break;
                off += n;
            }
            return buf;
        }

        private byte[] readUntilBoundary(InputStream in, String boundary) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
            byte[] bnd = ("\r\n" + boundary).getBytes("UTF-8");
            int    idx = 0;
            int    c;
            while ((c = in.read()) != -1) {
                if (c == bnd[idx]) {
                    idx++;
                    if (idx == bnd.length) {
                        byte[] data = baos.toByteArray();
                        int end = data.length;
                        if (end >= 2 && data[end-2] == '\r' && data[end-1] == '\n') end -= 2;
                        return java.util.Arrays.copyOf(data, end);
                    }
                } else {
                    if (idx > 0) { baos.write(bnd, 0, idx); idx = 0; }
                    baos.write(c);
                }
            }
            return baos.toByteArray();
        }
    }
}
