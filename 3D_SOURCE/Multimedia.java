import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Multimedia
 * ──────────────────────────────────────────────────────────────
 * 이미지 파일 선택·로드·Tab 표시, 이전/다음 이동, 슬라이드쇼를 담당.
 * MainWindow 에서는 openImageFile() 만 호출한다.
 */
public class Multimedia {

    // ── 지원 이미지 확장자 ────────────────────────────────────────
    private static final String[] IMAGE_EXTS = {
        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp", "*.tif", "*.tiff"
    };
    private static final String EXT_DESC =
        "Image Files (PNG, JPG, GIF, BMP, WEBP, TIF)";

    @SuppressWarnings("unchecked")
    private static final java.util.Set<String> EXT_SET =
        new java.util.HashSet<>(Arrays.asList(
            "png","jpg","jpeg","gif","bmp","webp","tif","tiff"));

    // ── 슬라이드쇼 간격 목록 (초) ────────────────────────────────
    private static final Integer[] SLIDE_INTERVALS =
        { 1, 2, 3, 4, 5, 10, 15, 20, 30, 60, 90, 120, 180, 300 };

    // ═══════════════════════════════════════════════════════════
    //  공개 진입점
    // ═══════════════════════════════════════════════════════════
    /**
     * 이미지 파일 선택 대화상자를 열고, 선택된 파일을 centerTabs 에 탭으로 표시.
     * 같은 폴더 탭이 이미 열려 있으면 해당 파일로 이동한다.
     */
    public static void openImageFile(Stage owner, TabPane centerTabs) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open Image File");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(EXT_DESC, IMAGE_EXTS),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        String lastDir = AppContext.get("multimedia.lastImageDir", "");
        if (!lastDir.isEmpty()) {
            File dir = new File(lastDir);
            if (dir.exists() && dir.isDirectory()) fc.setInitialDirectory(dir);
        }

        File selected = fc.showOpenDialog(owner);
        if (selected == null) return;

        AppContext.set("multimedia.lastImageDir",
                selected.getParentFile().getAbsolutePath());
        AppContext.save();

        // 폴더 내 이미지 파일 목록 수집 (이름 오름차순)
        List<File> folderList = collectImageFiles(selected.getParentFile());

        // 선택 파일의 인덱스 계산
        int startIndex = 0;
        String selPath = selected.getAbsolutePath();
        for (int i = 0; i < folderList.size(); i++) {
            if (folderList.get(i).getAbsolutePath().equals(selPath)) {
                startIndex = i;
                break;
            }
        }
        final int fIndex = startIndex;

        new Thread(() -> {
            try {
                Image img = loadImage(selected);
                Platform.runLater(() ->
                    showImageTab(selected, img, folderList, fIndex,
                                 centerTabs, owner));
            } catch (Exception ex) {
                Platform.runLater(() ->
                    alertStatic("Image load failed:\n" + ex.getMessage(),
                                "Image", owner));
            }
        }, "ImageLoader").start();
    }

    // ═══════════════════════════════════════════════════════════
    //  내부 유틸
    // ═══════════════════════════════════════════════════════════
    private static List<File> collectImageFiles(File dir) {
        List<File> list = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) return list;
        File[] files = dir.listFiles(
            f -> f.isFile() && EXT_SET.contains(ext(f.getName())));
        if (files == null) return list;
        Arrays.sort(files, Comparator.comparing(f -> f.getName().toLowerCase()));
        list.addAll(Arrays.asList(files));
        return list;
    }

    private static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    /** 백그라운드 스레드에서 호출. */
    private static Image loadImage(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            Image img = new Image(fis);
            if (img.isError()) throw new Exception(
                img.getException() != null
                    ? img.getException().getMessage() : "unknown");
            return img;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  탭 생성 / 재활용 (FX 스레드)
    // ═══════════════════════════════════════════════════════════
    private static void showImageTab(File file, Image image,
                                     List<File> folderList, int startIndex,
                                     TabPane centerTabs, Stage mainStage) {

        // 폴더 단위 탭 키 (같은 폴더면 탭 재활용)
        String tabKey = "image-folder:" + file.getParentFile().getAbsolutePath();

        for (Tab t : centerTabs.getTabs()) {
            if (tabKey.equals(t.getUserData())) {
                Object ctrl = t.getProperties().get("ctrl");
                if (ctrl instanceof ImageTabController) {
                    ((ImageTabController) ctrl).navigateTo(startIndex);
                }
                centerTabs.getSelectionModel().select(t);
                if (mainStage != null) { mainStage.show(); mainStage.toFront(); }
                return;
            }
        }

        ImageTabController ctrl = new ImageTabController(
            folderList, mainStage, centerTabs);

        Tab tab = ctrl.buildTab(file, image, startIndex);
        tab.setUserData(tabKey);
        tab.getProperties().put("ctrl", ctrl);
        tab.setOnClosed(e -> ctrl.stopSlideshow());

        centerTabs.getTabs().add(tab);
        centerTabs.getSelectionModel().select(tab);
        if (mainStage != null) { mainStage.show(); mainStage.toFront(); }
    }

    // ═══════════════════════════════════════════════════════════
    //  ImageTabController — 탭 하나의 상태·동작 전담
    // ═══════════════════════════════════════════════════════════
    private static class ImageTabController {

        static final double MIN_ZOOM = 0.05;
        static final double MAX_ZOOM = 16.0;

        // ── 상태 (람다 캡처용 배열) ────────────────────────────────
        final int[]             currentIndex     = { 0 };
        final double[]          zoom             = { 1.0 };
        final double[]          imgW             = { 0 };
        final double[]          imgH             = { 0 };
        final boolean[]         slideshowRunning = { false };
        final PauseTransition[] slideTimer       = { null };

        // ── 참조 ──────────────────────────────────────────────────
        List<File>        fileList;
        Stage             mainStage;
        TabPane           centerTabs;
        Tab               tab;

        // ── UI 참조 ───────────────────────────────────────────────
        ImageView         imageView;
        StackPane         imagePane;
        ScrollPane        scrollPane;
        Label             infoLabel;
        Label             zoomLabel;
        Button            btnSlideshow;
        ComboBox<Integer> intervalBox;

        ImageTabController(List<File> fileList, Stage mainStage,
                           TabPane centerTabs) {
            this.fileList   = fileList;
            this.mainStage  = mainStage;
            this.centerTabs = centerTabs;
        }

        // ── 탭 UI 빌드 ─────────────────────────────────────────────
        Tab buildTab(File file, Image image, int startIndex) {
            currentIndex[0] = startIndex;
            imgW[0] = image.getWidth();
            imgH[0] = image.getHeight();

            // ImageView
            imageView = new ImageView(image);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.setFitWidth(imgW[0]);
            imageView.setFitHeight(imgH[0]);

            imagePane = new StackPane(imageView);
            imagePane.setAlignment(Pos.CENTER);
            imagePane.setStyle("-fx-background-color: #1a1a1a;");

            scrollPane = new ScrollPane(imagePane);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            scrollPane.setStyle(
                "-fx-background: #1a1a1a; -fx-background-color: #1a1a1a;");

            // Ctrl+휠 줌
            scrollPane.setOnScroll(e -> {
                if (!e.isControlDown()) return;
                double delta = e.getDeltaY() > 0 ? 1.15 : (1.0 / 1.15);
                zoom[0] = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom[0] * delta));
                applyZoom();
                e.consume();
            });

            // 최초 렌더링 후 fit
            scrollPane.widthProperty().addListener((obs, ov, nv) -> {
                if (zoom[0] == 1.0) doFit();
            });
            scrollPane.heightProperty().addListener((obs, ov, nv) -> {
                if (zoom[0] == 1.0) doFit();
            });

            // ── 하단 정보 라벨 ───────────────────────────────────
            infoLabel = new Label();
            infoLabel.setMaxWidth(Double.MAX_VALUE);
            infoLabel.setStyle(
                "-fx-background-color: rgba(30,30,40,0.88);"
                + "-fx-text-fill: #cccccc;"
                + "-fx-font-family: 'Malgun Gothic'; -fx-font-size: 11px;"
                + "-fx-padding: 3 6 3 6;");
            updateInfoLabel(file);

            // ── 줌 버튼 ──────────────────────────────────────────
            Button btnFit     = makeBtn("Fit", "Fit to window");
            Button btnOrig    = makeBtn("1:1", "Actual size");
            Button btnZoomOut = makeBtn("－",  "Zoom out  (Ctrl+Wheel)");
            Button btnZoomIn  = makeBtn("＋",  "Zoom in   (Ctrl+Wheel)");
            zoomLabel = new Label("100%");
            zoomLabel.setStyle(
                "-fx-text-fill:#cccccc; -fx-font-size:11px;"
                + "-fx-min-width:40px; -fx-alignment:CENTER;");

            btnFit.setOnAction(e     -> { doFit(); });
            btnOrig.setOnAction(e    -> { zoom[0] = 1.0; applyZoom(); });
            btnZoomOut.setOnAction(e -> {
                zoom[0] = Math.max(MIN_ZOOM, zoom[0] / 1.25); applyZoom(); });
            btnZoomIn.setOnAction(e  -> {
                zoom[0] = Math.min(MAX_ZOOM, zoom[0] * 1.25); applyZoom(); });

            // ── 이전 / 다음 버튼 ─────────────────────────────────
            Button btnPrev = makeBtn("◀", "Previous image");
            Button btnNext = makeBtn("▶", "Next image");
            btnPrev.setOnAction(e -> navigateBy(-1));
            btnNext.setOnAction(e -> navigateBy(+1));

            // ── 슬라이드쇼 버튼 ──────────────────────────────────
            btnSlideshow = makeBtn("⏵ Slide", "Slideshow (random order, toggle)");
            btnSlideshow.setOnAction(e -> toggleSlideshow());

            // ── 간격 콤보박스 ────────────────────────────────────
            intervalBox = new ComboBox<>();
            intervalBox.getItems().addAll(SLIDE_INTERVALS);
            intervalBox.setValue(5);
            intervalBox.setPrefWidth(68);
            intervalBox.setStyle(
                "-fx-font-size: 11px; -fx-padding: 0 2 0 2;");
            Tooltip.install(intervalBox,
                new Tooltip("Slideshow interval (seconds)"));

            // ── 구분자 ───────────────────────────────────────────
            Label sep = new Label("|");
            sep.setStyle(
                "-fx-text-fill: rgba(200,200,200,0.30);"
                + "-fx-font-size: 14px; -fx-padding: 0 2 0 2;");

            // ── 하단 오른쪽 툴바 ─────────────────────────────────
            //  [Fit][1:1][－][zoom%][＋]  |  [◀][▶]  [⏵ Slide][comboBox]
            HBox toolBar = new HBox(4,
                btnFit, btnOrig, btnZoomOut, zoomLabel, btnZoomIn,
                sep,
                btnPrev, btnNext,
                btnSlideshow, intervalBox
            );
            toolBar.setAlignment(Pos.CENTER_LEFT);
            toolBar.setPadding(new Insets(2, 8, 2, 8));
            toolBar.setStyle("-fx-background-color: rgba(30,30,40,0.88);");

            HBox bottomBar = new HBox(infoLabel, toolBar);
            HBox.setHgrow(infoLabel, Priority.ALWAYS);
            bottomBar.setAlignment(Pos.CENTER_LEFT);
            bottomBar.setStyle("-fx-background-color: rgba(30,30,40,0.88);");

            BorderPane pane = new BorderPane(scrollPane);
            pane.setBottom(bottomBar);

            tab = new Tab("🖼 " + file.getName(), pane);
            tab.setClosable(true);
            return tab;
        }

        // ── 줌 ────────────────────────────────────────────────────
        void applyZoom() {
            double w = imgW[0] * zoom[0];
            double h = imgH[0] * zoom[0];
            imageView.setFitWidth(w);
            imageView.setFitHeight(h);
            imagePane.setMinWidth(w);
            imagePane.setMinHeight(h);
            zoomLabel.setText(Math.round(zoom[0] * 100) + "%");
        }

        void doFit() {
            double vpW = scrollPane.getViewportBounds().getWidth();
            double vpH = scrollPane.getViewportBounds().getHeight();
            if (vpW <= 0 || vpH <= 0 || imgW[0] <= 0 || imgH[0] <= 0) return;
            zoom[0] = Math.min(vpW / imgW[0], vpH / imgH[0]);
            applyZoom();
        }

        // ── 이미지 이동 ───────────────────────────────────────────
        void navigateBy(int delta) {
            if (fileList == null || fileList.isEmpty()) return;
            int n = fileList.size();
            navigateTo(((currentIndex[0] + delta) % n + n) % n);
        }

        void navigateTo(int index) {
            if (fileList == null || fileList.isEmpty()) return;
            int n = fileList.size();
            currentIndex[0] = ((index % n) + n) % n;
            File f = fileList.get(currentIndex[0]);
            new Thread(() -> {
                try {
                    Image img = loadImage(f);
                    Platform.runLater(() -> applyNewImage(f, img));
                } catch (Exception ex) {
                    Platform.runLater(() ->
                        alertStatic("Load failed:\n" + ex.getMessage(),
                                    "Image", mainStage));
                }
            }, "ImageNav").start();
        }

        /** 이미지·정보·탭 제목 갱신 후 슬라이드쇼 다음 예약 (FX 스레드) */
        void applyNewImage(File f, Image img) {
            imgW[0] = img.getWidth();
            imgH[0] = img.getHeight();
            imageView.setImage(img);
            zoom[0] = 1.0;
            doFit();
            updateInfoLabel(f);
            tab.setText("🖼 " + f.getName());

            if (slideshowRunning[0]) scheduleNext();
        }

        void updateInfoLabel(File f) {
            int total = (fileList == null) ? 1 : fileList.size();
            infoLabel.setText(
                " " + f.getName()
                + "  |  " + (int) imgW[0] + " × " + (int) imgH[0] + " px"
                + "  |  " + formatSize(f.length())
                + "  [" + (currentIndex[0] + 1) + " / " + total + "]");
        }

        // ── 슬라이드쇼 ───────────────────────────────────────────
        void toggleSlideshow() {
            if (slideshowRunning[0]) stopSlideshow();
            else                     startSlideshow();
        }

        void startSlideshow() {
            if (fileList == null || fileList.isEmpty()) return;
            slideshowRunning[0] = true;
            btnSlideshow.setText("⏹ Stop");
            btnSlideshow.setStyle(btnSlideshow.getStyle()
                + "-fx-background-color: rgba(160,55,55,0.90);");
            scheduleNext();
        }

        void stopSlideshow() {
            slideshowRunning[0] = false;
            cancelTimer();
            if (btnSlideshow != null) {
                btnSlideshow.setText("⏵ Slide");
                // 원래 스타일로 복원
                btnSlideshow.setStyle(
                    "-fx-background-color: rgba(80,80,100,0.70);"
                    + "-fx-text-fill: #dddddd; -fx-font-size: 11px;"
                    + "-fx-padding: 1 7 1 7; -fx-cursor: hand;"
                    + "-fx-background-radius: 4;");
            }
        }

        /** 콤보박스의 현재 간격(초)으로 다음 이미지 전환 예약 */
        void scheduleNext() {
            if (!slideshowRunning[0]) return;
            cancelTimer();
            int secs = (intervalBox != null && intervalBox.getValue() != null)
                       ? intervalBox.getValue() : 5;
            PauseTransition pt = new PauseTransition(Duration.seconds(secs));
            pt.setOnFinished(e -> {
                if (!slideshowRunning[0]) return;
                navigateTo(randomOther(currentIndex[0]));
                // applyNewImage() → scheduleNext() 연쇄 호출됨
            });
            slideTimer[0] = pt;
            pt.play();
        }

        void cancelTimer() {
            if (slideTimer[0] != null) {
                slideTimer[0].stop();
                slideTimer[0] = null;
            }
        }

        int randomOther(int exclude) {
            int n = fileList.size();
            if (n <= 1) return 0;
            int next;
            do { next = new Random().nextInt(n); } while (next == exclude);
            return next;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  공용 헬퍼
    // ═══════════════════════════════════════════════════════════
    private static Button makeBtn(String text, String tooltip) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: rgba(80,80,100,0.70);"
            + "-fx-text-fill: #dddddd; -fx-font-size: 11px;"
            + "-fx-padding: 1 7 1 7; -fx-cursor: hand;"
            + "-fx-background-radius: 4;");
        btn.setTooltip(new Tooltip(tooltip));
        return btn;
    }

    private static String formatSize(long b) {
        if (b < 1024)        return b + " B";
        if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024.0);
        return String.format("%.2f MB", b / (1024.0 * 1024));
    }

    private static void alertStatic(String msg, String title, Stage owner) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    // ═══════════════════════════════════════════════════════════
    //  슬라이드쇼 공개 토글 / 상태 조회
    //  (MainWindow 메뉴 CheckMenuItem 과 탭 버튼이 공유)
    // ═══════════════════════════════════════════════════════════
    /**
     * 현재 선택된 이미지 탭의 슬라이드쇼를 토글한다.
     * 이미지 탭이 선택되어 있지 않으면 아무 동작 없음.
     */
    public static void toggleSlideshow(TabPane tabs) {
        if (tabs == null) return;
        Tab sel = tabs.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Object ctrl = sel.getProperties().get("ctrl");
        if (ctrl instanceof ImageTabController)
            ((ImageTabController) ctrl).toggleSlideshow();
    }

    /**
     * 현재 선택된 이미지 탭의 슬라이드쇼 실행 여부 반환.
     */
    public static boolean isSlideshowRunning(TabPane tabs) {
        if (tabs == null) return false;
        Tab sel = tabs.getSelectionModel().getSelectedItem();
        if (sel == null) return false;
        Object ctrl = sel.getProperties().get("ctrl");
        if (ctrl instanceof ImageTabController)
            return ((ImageTabController) ctrl).slideshowRunning[0];
        return false;
    }

    // ═══════════════════════════════════════════════════════════
    //  CameraTabHandle — 카메라 탭 핸들 (MainWindow 에서 참조)
    // ═══════════════════════════════════════════════════════════
    public static class CameraTabHandle {
        private final ImageView  imageView;
        private final StackPane  imagePane;
        private final ScrollPane scrollPane;
        private final Tab        tab;
        private final TabPane    ownerTabs;
        private Runnable         onStopCallback = null;

        boolean flipH = false;
        boolean flipV = false;
        double  zoom  = 1.0;

        static final double MIN_ZOOM = 0.1;
        static final double MAX_ZOOM = 8.0;

        CameraTabHandle(ImageView iv, StackPane ip, ScrollPane sp,
                        Tab t, TabPane tabs) {
            this.imageView  = iv;
            this.imagePane  = ip;
            this.scrollPane = sp;
            this.tab        = t;
            this.ownerTabs  = tabs;
        }

        /** 새 프레임 반영 (FX 스레드에서 호출) */
        public void onFrame(javafx.scene.image.WritableImage frame) {
            if (frame == null || imageView == null) return;
            imageView.setImage(frame);
            // 첫 프레임 도착 시 뷰포트에 맞게 fit
            if (imageView.getFitWidth() <= 0) fitToViewport();
        }

        /** 카메라 중지 시 화면 초기화 (FX 스레드에서 호출) */
        public void onStopped() {
            if (imageView != null) imageView.setImage(null);
        }

        public void setFlipH(boolean v) {
            flipH = v;
            if (imageView != null) imageView.setScaleX(v ? -1.0 : 1.0);
        }

        public void setFlipV(boolean v) {
            flipV = v;
            if (imageView != null) imageView.setScaleY(v ? -1.0 : 1.0);
        }

        public boolean isFlipH()    { return flipH; }
        public boolean isFlipV()    { return flipV; }
        public boolean isTabOpen()  {
            return ownerTabs != null && ownerTabs.getTabs().contains(tab);
        }
        public void selectTab() {
            if (ownerTabs != null && isTabOpen())
                ownerTabs.getSelectionModel().select(tab);
        }
        public void setOnStopCallback(Runnable cb) { this.onStopCallback = cb; }

        private void fitToViewport() {
            double vpW = scrollPane.getViewportBounds().getWidth();
            double vpH = scrollPane.getViewportBounds().getHeight();
            if (vpW <= 0 || vpH <= 0) return;
            imageView.setFitWidth(vpW);
            imageView.setFitHeight(vpH);
            imagePane.setMinWidth(vpW);
            imagePane.setMinHeight(vpH);
        }

        void applyZoom() {
            javafx.scene.image.Image img = imageView.getImage();
            if (img == null) return;
            double w = img.getWidth()  * zoom;
            double h = img.getHeight() * zoom;
            imageView.setFitWidth(w);
            imageView.setFitHeight(h);
            imagePane.setMinWidth(w);
            imagePane.setMinHeight(h);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  openCameraTab — 카메라 탭 생성 / 재활용 (FX 스레드)
    // ═══════════════════════════════════════════════════════════
    /**
     * 카메라 라이브 피드 탭을 열고 CameraTabHandle 을 반환한다.
     * 이미 같은 탭이 열려 있으면 flip 설정만 갱신 후 재활용.
     * MainWindow 의 Connect 메뉴에서 호출한다.
     *
     * @param centerTabs  탭을 추가할 TabPane
     * @param mainStage   부모 Stage
     * @param initFlipH   초기 Flip Horizontal 상태
     * @param initFlipV   초기 Flip Vertical   상태
     */
    public static CameraTabHandle openCameraTab(TabPane centerTabs,
                                                 Stage mainStage,
                                                 boolean initFlipH,
                                                 boolean initFlipV) {
        final String TAB_KEY = "camera-live-tab";

        // ── 이미 열린 탭 재활용 ──────────────────────────────────
        for (Tab t : centerTabs.getTabs()) {
            if (TAB_KEY.equals(t.getUserData())) {
                Object h = t.getProperties().get("camHandle");
                if (h instanceof CameraTabHandle handle) {
                    handle.setFlipH(initFlipH);
                    handle.setFlipV(initFlipV);
                    centerTabs.getSelectionModel().select(t);
                    if (mainStage != null) {
                        mainStage.show(); mainStage.toFront();
                    }
                    return handle;
                }
            }
        }

        // ── ImageView / StackPane / ScrollPane ───────────────────
        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        StackPane imagePane = new StackPane(imageView);
        imagePane.setAlignment(Pos.CENTER);
        imagePane.setStyle("-fx-background-color: #111111;");

        ScrollPane scrollPane = new ScrollPane(imagePane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setStyle("-fx-background: #111111; -fx-background-color: #111111;");

        // ── 초기 flip 적용 ────────────────────────────────────────
        if (initFlipH) imageView.setScaleX(-1.0);
        if (initFlipV) imageView.setScaleY(-1.0);

        // ── 핸들 forward-reference ────────────────────────────────
        CameraTabHandle[] handleRef = {null};

        // ── 우하단 버튼 바 ────────────────────────────────────────
        // [＋] [－] [↕] [↔]  ← 영어/기호만 사용
        Button btnZoomIn  = makeCamBtn("＋",  "Zoom In");
        Button btnZoomOut = makeCamBtn("－",  "Zoom Out");
        Button btnFlipV   = makeCamBtn("↕",  "Flip Vertical");
        Button btnFlipH   = makeCamBtn("↔",  "Flip Horizontal");

        // active 버튼 스타일
        final String BTN_NORMAL = "-fx-background-color:rgba(80,80,100,0.78);"
            + "-fx-text-fill:#dddddd;-fx-font-size:13px;"
            + "-fx-padding:2 9 2 9;-fx-cursor:hand;-fx-background-radius:4;";
        final String BTN_ACTIVE = "-fx-background-color:rgba(50,110,200,0.90);"
            + "-fx-text-fill:#ffffff;-fx-font-size:13px;"
            + "-fx-padding:2 9 2 9;-fx-cursor:hand;-fx-background-radius:4;";

        btnZoomIn.setOnAction(e -> {
            CameraTabHandle h = handleRef[0];
            if (h == null) return;
            h.zoom = Math.min(CameraTabHandle.MAX_ZOOM, h.zoom * 1.25);
            h.applyZoom();
        });
        btnZoomOut.setOnAction(e -> {
            CameraTabHandle h = handleRef[0];
            if (h == null) return;
            h.zoom = Math.max(CameraTabHandle.MIN_ZOOM, h.zoom / 1.25);
            h.applyZoom();
        });
        btnFlipH.setOnAction(e -> {
            CameraTabHandle h = handleRef[0];
            if (h == null) return;
            h.setFlipH(!h.isFlipH());
            btnFlipH.setStyle(h.isFlipH() ? BTN_ACTIVE : BTN_NORMAL);
            // AppContext 에 즉시 반영
            AppContext.set("camera.flipH", String.valueOf(h.isFlipH()));
            AppContext.save();
        });
        btnFlipV.setOnAction(e -> {
            CameraTabHandle h = handleRef[0];
            if (h == null) return;
            h.setFlipV(!h.isFlipV());
            btnFlipV.setStyle(h.isFlipV() ? BTN_ACTIVE : BTN_NORMAL);
            AppContext.set("camera.flipV", String.valueOf(h.isFlipV()));
            AppContext.save();
        });

        // 초기 버튼 스타일 반영
        btnFlipH.setStyle(initFlipH ? BTN_ACTIVE : BTN_NORMAL);
        btnFlipV.setStyle(initFlipV ? BTN_ACTIVE : BTN_NORMAL);
        btnZoomIn.setStyle(BTN_NORMAL);
        btnZoomOut.setStyle(BTN_NORMAL);

        // 상태 라벨
        Label statusLbl = new Label("  📷 Live Camera");
        statusLbl.setStyle(
            "-fx-text-fill:#aaaaaa;-fx-font-family:'Malgun Gothic';"
            + "-fx-font-size:11px;");
        statusLbl.setMaxWidth(Double.MAX_VALUE);

        HBox btnBar = new HBox(4, btnZoomOut, btnZoomIn, btnFlipV, btnFlipH);
        btnBar.setAlignment(Pos.CENTER_RIGHT);
        btnBar.setPadding(new Insets(3, 8, 3, 8));

        HBox bottomBar = new HBox(statusLbl, btnBar);
        HBox.setHgrow(statusLbl, Priority.ALWAYS);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setStyle("-fx-background-color:rgba(18,18,28,0.92);"
            + "-fx-border-color:rgba(80,80,100,0.40);-fx-border-width:1 0 0 0;");

        // fit viewport on first layout
        scrollPane.widthProperty().addListener((obs, ov, nv) -> {
            if (handleRef[0] != null && handleRef[0].zoom == 1.0
                    && imageView.getImage() == null) {
                double vpW = scrollPane.getViewportBounds().getWidth();
                double vpH = scrollPane.getViewportBounds().getHeight();
                if (vpW > 0 && vpH > 0) {
                    imageView.setFitWidth(vpW);
                    imageView.setFitHeight(vpH);
                }
            }
        });

        // ── 레이아웃 조립 ────────────────────────────────────────
        BorderPane pane = new BorderPane(scrollPane);
        pane.setBottom(bottomBar);

        // ── 탭 생성 ──────────────────────────────────────────────
        Tab tab = new Tab("📷 Camera", pane);
        tab.setClosable(true);
        tab.setUserData(TAB_KEY);

        // ── 핸들 생성 ────────────────────────────────────────────
        CameraTabHandle handle = new CameraTabHandle(
            imageView, imagePane, scrollPane, tab, centerTabs);
        handle.setFlipH(initFlipH);
        handle.setFlipV(initFlipV);
        handleRef[0] = handle;
        tab.getProperties().put("camHandle", handle);

        // 탭 닫힐 때 stop 콜백 호출
        tab.setOnClosed(e -> {
            if (handle.onStopCallback != null) handle.onStopCallback.run();
        });

        centerTabs.getTabs().add(tab);
        centerTabs.getSelectionModel().select(tab);
        if (mainStage != null) { mainStage.show(); mainStage.toFront(); }

        return handle;
    }

    private static Button makeCamBtn(String text, String tooltip) {
        Button btn = new Button(text);
        btn.setTooltip(new Tooltip(tooltip));
        return btn;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  TelegramCamRecorder — /cam /rec 텔레그램 카메라 명령 처리
    //
    //  동작:
    //    /cam        → 현재 프레임 1장 즉시 전송 (sendPhoto)
    //    /rec [N]    → N초(기본 10, 최대 60) mp4 클립 녹화 → 전송 (sendVideo)
    //    /rec stop   → 진행 중인 녹화 즉시 중단 후 전송
    //
    //  사용 예:
    //    TelegramCamRecorder rec = new TelegramCamRecorder(camera, tg, workDir);
    //    rec.sendSnapshot(chatId);
    //    rec.startRec(chatId, 10);
    //    rec.stopRec(chatId);
    // ═══════════════════════════════════════════════════════════════════
    // ═══════════════════════════════════════════════════════════════════
    //  TelegramCamRecorder  v2
    //
    //  /cam        → 현재 프레임 1장 전송
    //  /camHello   → 카메라 시작 + 10초 무한 루프 (전송 + PC저장)
    //  /recstop    → 전송 중단, 카메라 유지
    //  /camBye     → 마지막 클립 완료 후 카메라 종료
    //
    //  클립마다: encode → APP_DIR/img/cam_*.mp4 저장 + Telegram sendVideo
    // ═══════════════════════════════════════════════════════════════════
    public static class TelegramCamRecorder {

        // ── 녹화 상태 ─────────────────────────────────────────────────
        public enum RecStatus {
            IDLE,       // 비활성
            CAPTURING,  // 🎥 촬영중
            ENCODING,   // 🎥 촬영중 + 💾 저장중
            SENDING     // 🎥 촬영중 + 💾 저장중 + 📡 전송중
        }
        public interface StatusListener {
            void onStatus(RecStatus status);
        }
        private StatusListener statusListener = null;
        public void setStatusListener(StatusListener l) { statusListener = l; }
        private void notify(RecStatus s) {
            if (statusListener != null) statusListener.onStatus(s);
        }

        private static final int CLIP_SEC  = 10;   // 클립 길이 (초)
        private static final int FPS       = 10;   // 캡처 프레임율
        private static final int OUT_WIDTH = 640;  // 출력 해상도 너비 (높이는 비율 자동)
        private static final int CRF       = 28;   // 인코딩 품질 (낮을수록 고품질·대용량, 기본 23)

        // ── 전송 이력 (static: 인스턴스 교체 후에도 유지) ──────────────
        private static final java.util.Set<String> sentVideoNames =
            java.util.Collections.synchronizedSet(
                new java.util.LinkedHashSet<>());

        private final TOOLS.CaptureManager.Camera camera;
        private final TelegramBot                 tg;
        private final File                        workDir;    // 임시 프레임 작업 폴더
        private final File                        imgSaveDir; // PC 영구 저장 폴더 (APP_DIR/img/)

        private volatile boolean continuousRec    = false;
        private volatile Thread  continuousThread = null;

        public TelegramCamRecorder(TOOLS.CaptureManager.Camera camera,
                                   TelegramBot tg, File workDir) {
            this.camera     = camera;
            this.tg         = tg;
            this.workDir    = workDir;
            this.imgSaveDir = new File(AppContext.getAPP_DIR(), "img");
            workDir.mkdirs();
            imgSaveDir.mkdirs();
        }

        private volatile boolean singleRec   = false;
        private volatile Thread  singleThread = null;

        public boolean isContinuousRec() { return continuousRec; }
        public boolean isSingleRec()     { return singleRec; }

        // ── /cam — 현재 프레임 1장 전송 ──────────────────────────────
        public void sendSnapshot(String chatId) {
            if (camera == null || camera.getLastFrameAWT() == null) {
                tg.sendTelegram("❌ 카메라 프레임 없음 — 연결을 확인하세요");
                return;
            }
            new Thread(() -> {
                File tmpDir = new File(workDir, "snap_" + System.currentTimeMillis());
                try {
                    tmpDir.mkdirs();
                    String saved = camera.capture(tmpDir);
                    if (saved != null) {
                        tg.sendFile(chatId, new File(saved));
                        System.out.println("[TgCam] snapshot sent: " + saved);
                    } else {
                        tg.sendTelegram("❌ 프레임 캡처 실패");
                    }
                } catch (Exception e) {
                    tg.sendTelegram("❌ 캡처 오류: " + e.getMessage());
                } finally {
                    deleteDir(tmpDir);
                }
            }, "TgCamSnap").start();
        }

        // ── /rec N — 단발 N초 클립 녹화 후 mp4 전송 ─────────────────
        public void startRec(String chatId, int seconds) {
            if (singleRec) {
                tg.sendTelegram("⚠️ 이미 단발 녹화 중입니다. /rec stop 으로 중단하세요");
                return;
            }
            if (continuousRec) {
                tg.sendTelegram("⚠️ 연속 녹화 중입니다. /recstop 후 사용하세요");
                return;
            }
            String ffExe = checkFfmpeg();
            if (ffExe == null) return;
            int sec = Math.max(1, Math.min(60, seconds));
            singleRec = true;
            final String ff = ffExe;
            singleThread = new Thread(() -> doSingleRec(chatId, sec, ff), "TgCamSingle");
            singleThread.setDaemon(true);
            singleThread.start();
        }

        public void stopRec(String chatId) {
            if (!singleRec) { tg.sendTelegram("⚠️ 단발 녹화 중이 아닙니다"); return; }
            singleRec = false;
        }

        private void doSingleRec(String chatId, int seconds, String ffExe) {
            String ts     = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            File frameDir = new File(workDir, "single_" + ts);
            File pcMp4    = new File(imgSaveDir, "cam_" + ts + ".mp4");
            File tgTmp    = new File(workDir,    "tg_"  + ts + ".mp4");
            tg.sendTelegram("🔴 REC " + seconds + "초 녹화 시작...");
            try {
                frameDir.mkdirs();
                // ── 🎥 촬영중 ─────────────────────────────────────────
                notify(RecStatus.CAPTURING);
                long intervalMs = 1000L / FPS;
                long endTime    = System.currentTimeMillis() + seconds * 1000L;
                while (singleRec && System.currentTimeMillis() < endTime) {
                    long t0 = System.currentTimeMillis();
                    try { camera.capture(frameDir); } catch (Exception ex) {}
                    long sleep = intervalMs - (System.currentTimeMillis() - t0);
                    if (sleep > 0) Thread.sleep(sleep);
                }
                singleRec = false;
                File imgSubDir = new File(frameDir, "img");
                if (!imgSubDir.exists()) { tg.sendTelegram("❌ 프레임 없음"); return; }
                File[] frames = imgSubDir.listFiles(f -> f.getName().endsWith(".jpg"));
                if (frames == null || frames.length == 0) { tg.sendTelegram("❌ 프레임 없음"); return; }
                java.util.Arrays.sort(frames, java.util.Comparator.comparing(File::getName));
                for (int i = 0; i < frames.length; i++)
                    frames[i].renameTo(new File(frameDir, String.format("frame_%06d.jpg", i)));
                // ── 💾 저장중 ─────────────────────────────────────────
                notify(RecStatus.ENCODING);
                boolean ok = encode(ffExe, frameDir, pcMp4);
                if (ok) {
                    // ── 📡 전송중 ─────────────────────────────────────
                    notify(RecStatus.SENDING);
                    String clipName = pcMp4.getName();
                    if (sentVideoNames.contains(clipName)) {
                        System.out.println("[TgCamSingle] SKIP already-sent: " + clipName);
                    } else {
                        java.nio.file.Files.copy(pcMp4.toPath(), tgTmp.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        tg.sendVideo(chatId, tgTmp);
                        sentVideoNames.add(clipName);
                        System.out.println("[TgCamSingle] SENT: " + clipName
                            + "  (list=" + sentVideoNames.size() + ")");
                        tg.sendTelegram("✅ " + seconds + "초 클립 전송 완료");
                    }
                } else {
                    tg.sendTelegram("❌ 인코딩 실패");
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                tg.sendTelegram("❌ 오류: " + e.getMessage());
            } finally {
                singleRec = false;
                notify(RecStatus.IDLE);
                deleteDir(frameDir);
                if (tgTmp.exists()) tgTmp.delete();
            }
        }

        // ── /camHello — 10초 무한 루프 시작 ─────────────────────────
        public void startContinuousRec(String chatId) {
            if (continuousRec) {
                tg.sendTelegram("⚠️ 이미 연속 녹화 중입니다\n중단: /recstop  종료: /camBye");
                return;
            }
            String ffExe = checkFfmpeg();
            if (ffExe == null) return;
            continuousRec = true;
            final String ff = ffExe;
            continuousThread = new Thread(() -> doContinuousLoop(chatId, ff), "TgCamLoop");
            continuousThread.setDaemon(true);
            continuousThread.start();
            tg.sendTelegram("🔴 연속 녹화 시작\n" +
                "• 10초마다 PC 저장 + Telegram 전송\n" +
                "• 저장 위치: img/cam_*.mp4\n" +
                "⏹ 전송 중단: /recstop\n" +
                "🛑 카메라 종료: /camBye");
        }

        // ── /recstop — 전송 중단, 카메라 유지 ───────────────────────
        public void stopContinuousRec(String chatId) {
            if (!continuousRec) {
                tg.sendTelegram("⚠️ 연속 녹화 중이 아닙니다");
                return;
            }
            continuousRec = false;
            tg.sendTelegram("⏹ 전송 중단됨 (현재 클립 완료 후 중단)\n" +
                "카메라는 계속 촬영 중\n" +
                "재시작: /camHello  종료: /camBye");
        }

        // ── /camBye — 현재 클립 완료 후 카메라 종료 신호 ────────────
        public void camBye(String chatId) {
            continuousRec = false;
            tg.sendTelegram("🛑 /camBye — 현재 클립 완료 후 카메라 종료...");
        }

        // ── MainWindow 에서 camBye 후 스레드 종료 대기 ───────────────
        public void waitForStop() {
            Thread t = continuousThread;
            if (t != null && t.isAlive()) {
                try { t.join(35_000L); } catch (InterruptedException ie) {}
            }
        }

        // ── 10초 무한 루프 (백그라운드) ──────────────────────────────
        private void doContinuousLoop(String chatId, String ffExe) {
            System.out.println("[TgCamLoop] start");
            int clipIdx = 0;
            while (continuousRec) {
                clipIdx++;
                String ts     = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
                                    .format(new java.util.Date());
                File frameDir = new File(workDir, "frm_" + ts);
                File pcMp4    = new File(imgSaveDir, "cam_" + ts + ".mp4");
                File tgTmp    = new File(workDir,    "tg_"  + ts + ".mp4");
                try {
                    frameDir.mkdirs();

                    // ── 🎥 촬영중 ─────────────────────────────────────
                    notify(RecStatus.CAPTURING);
                    long intervalMs = 1000L / FPS;
                    long endTime    = System.currentTimeMillis() + CLIP_SEC * 1000L;
                    while (System.currentTimeMillis() < endTime) {
                        long t0 = System.currentTimeMillis();
                        try { camera.capture(frameDir); } catch (Exception ex) {}
                        long sleep = intervalMs - (System.currentTimeMillis() - t0);
                        if (sleep > 0) Thread.sleep(sleep);
                    }

                    // ── 프레임 시퀀스 변환 ────────────────────────────
                    File imgSubDir = new File(frameDir, "img");
                    if (!imgSubDir.exists()) { System.out.println("[TgCamLoop] no imgDir"); continue; }
                    File[] frames = imgSubDir.listFiles(f -> f.getName().endsWith(".jpg"));
                    if (frames == null || frames.length == 0) { System.out.println("[TgCamLoop] no frames"); continue; }
                    java.util.Arrays.sort(frames, java.util.Comparator.comparing(File::getName));
                    for (int i = 0; i < frames.length; i++)
                        frames[i].renameTo(new File(frameDir, String.format("frame_%06d.jpg", i)));

                    // ── 💾 저장중 ─────────────────────────────────────
                    notify(RecStatus.ENCODING);
                    boolean ok = encode(ffExe, frameDir, pcMp4);
                    if (!ok) { tg.sendTelegram("⚠️ 클립 " + clipIdx + " 인코딩 실패"); continue; }
                    System.out.println("[TgCamLoop] clip " + clipIdx + " saved: " + pcMp4.getName());

                    // ── 📡 전송중 ─────────────────────────────────────
                    notify(RecStatus.SENDING);
                    String clipName = pcMp4.getName();
                    if (sentVideoNames.contains(clipName)) {
                        System.out.println("[TgCamLoop] SKIP already-sent: " + clipName);
                    } else {
                        java.nio.file.Files.copy(pcMp4.toPath(), tgTmp.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        tg.sendVideo(chatId, tgTmp);
                        sentVideoNames.add(clipName);
                        System.out.println("[TgCamLoop] clip " + clipIdx + " SENT: " + clipName
                            + "  (list=" + sentVideoNames.size() + ")");
                    }

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    tg.sendTelegram("⚠️ 클립 " + clipIdx + " 오류: " + e.getMessage());
                    System.out.println("[TgCamLoop] clip " + clipIdx + " error: " + e.getMessage());
                } finally {
                    deleteDir(frameDir);
                    if (tgTmp.exists()) tgTmp.delete();
                }
            }
            continuousRec = false;
            notify(RecStatus.IDLE);
            System.out.println("[TgCamLoop] stopped — total clips: " + clipIdx);
        }

        // ── ffmpeg 체크 ───────────────────────────────────────────────
        private String checkFfmpeg() {
            String p = AppContext.getFfmpegPath();
            if (p == null || p.isEmpty() || !new File(p).isFile()) {
                tg.sendTelegram("❌ ffmpeg 미설정\n[폰 카메라 연결] → [Set Video Tool]");
                return null;
            }
            return p;
        }

        // ── ffmpeg 인코딩 ─────────────────────────────────────────────
        private boolean encode(String ffExe, File frameDir, File outMp4) throws Exception {
            String inputPat = new File(frameDir, "frame_%06d.jpg").getAbsolutePath();
            // scale=OUT_WIDTH:-2 : 너비를 OUT_WIDTH 로 축소, 높이는 짝수 유지 자동 계산
            String scaleFilter = "scale=" + OUT_WIDTH + ":-2";
            ProcessBuilder pb = new ProcessBuilder(
                ffExe, "-y",
                "-framerate", String.valueOf(FPS),
                "-i", inputPat,
                "-vf", scaleFilter,
                "-c:v", "libx264", "-preset", "ultrafast",
                "-crf", String.valueOf(CRF),
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart",
                outMp4.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (java.io.InputStream is = proc.getInputStream()) {
                byte[] buf = new byte[4096];
                while (is.read(buf) != -1) {}
            }
            int code = proc.waitFor();
            return code == 0 && outMp4.exists() && outMp4.length() > 0;
        }

        // ── 디렉터리 재귀 삭제 ────────────────────────────────────────
        private void deleteDir(File dir) {
            if (dir == null || !dir.exists()) return;
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) {
                if (f.isDirectory()) deleteDir(f); else f.delete();
            }
            dir.delete();
        }
    }
}
