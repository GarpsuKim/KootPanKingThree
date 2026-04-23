import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.stage.Modality;
import javafx.geometry.Insets;
import javafx.geometry.Point3D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.stage.FileChooser;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javafx.collections.ObservableList;

import java.time.LocalTime;
import java.io.File;
import java.awt.Desktop;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.scene.layout.Region;

/**
	* FxGPUNeon - 순수 JavaFX 3D 그래픽 클래스
	*
	* 제어 로직 없음. KootPanKingThree 가 ClockController 를 생성/제어한다.
	*
	* 주요 구성:
	* - ClockController  : 씬 생명주기 관리
	* - AppState         : 렌더링 상태
	* - SceneAssembler   : 3D 씬 조립
	* - OverlayRenderer  : 2D 오버레이
	* - MenuController   : 우클릭 메뉴 / 설정
	* - SetupPanelController : 설정 패널
*/
public class FxGPUNeon {
    private static final Logger LOG = Logger.getLogger(FxGPUNeon.class.getName());
    private static final String thisProgramName = "[KootPanKingThree 끝판왕 (v2.0)]";
    public boolean cameraActive = false;  //  카메라 스트림 활성 여부.
	// KootPanKingThree.startCamera/stopCamera 에서 직접 읽고 씀.
	// stopCamera() 에서 false 로 먼저 세팅 → runLater 큐 잔류 프레임 차단.
    public boolean cameraFlipH = false;   // 좌우 반전 (KootPanKingThree에서 설정)
    public boolean cameraFlipV = false;   // 상하 반전 (KootPanKingThree에서 설정)

	// ───────────────────────── Controller ─────────────────────────
    final class ClockController {
        private final Stage mainStage;
        private String  arg1;
        private String  arg2;
        private String  arg3;

        private final AppState state = new AppState();
        private final SceneAssembler assembler = new SceneAssembler(state);
        private final OverlayRenderer overlayRenderer = new OverlayRenderer(state);

        private PerspectiveCamera camera;
        private Canvas            overlay;
        private StackPane         root;
        private SubScene                     subScene;
        private AnimationTimer               timer;
        private javafx.scene.Group ytHiddenGroup = null; // YouTube MediaView 렌더링용 (opacity=0)
        private javafx.scene.control.Label statusLabel = null; // YouTube 로딩 메시지

        // ── 그래픽 팝업 메뉴 (구 MenuController 그래픽 담당 부분) ─────
        private final ContextMenu      popup      = new ContextMenu();
        private final CheckMenuItem    menuSwing  = new CheckMenuItem("흔들림");
        /** 팝업 표시 시 KootPanKingThree 가 추가로 실행할 콜백 (메뉴 체크 동기화용) */
        Runnable onPopupShowing = null;
        private double  dragSX, dragSY, stageX, stageY;
        private double  rotDX,  rotDY;
        private boolean rightDragMoved;
        private Stage   setupStage;
        private boolean setupStageOpening;
        // ── FX INI 저장/복원 ──────────────────────────────────────────
        private FxIniManager fxIni = null;
        private javafx.animation.Timeline saveDebounceTimer = null;
        /** loadFxState() 에서 읽은 저장된 창 위치 (start() 후 적용) */
        private double pendingStageX = Double.NaN;
        private double pendingStageY = Double.NaN;
        /** 카메라 스트림 활성 여부.
			*  KootPanKingThree.startCamera/stopCamera 에서 직접 읽고 씀.
		*  stopCamera() 에서 false 로 먼저 세팅 → runLater 큐 잔류 프레임 차단. */
        // boolean cameraActive = false;

        ClockController(Stage mainStage, String arg1, String arg2, String arg3,
			java.util.function.Consumer<ContextMenu> appMenuBuilder) {
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
            this.mainStage = Objects.requireNonNull(mainStage);
            System.out.println("[ClockController] , arg1, arg2, arg3 = [" + arg1 + "],[" + arg2  + "],[" + arg3  + "]");
            buildGraphicsMenu(appMenuBuilder);
		}

        // ── FX INI 저장/복원 메서드 ──────────────────────────────────
        /** start() 이전에 호출. 이후 start() 내부에서 자동으로 상태를 복원한다. */
        public void setIniFile(String path) {
            System.out.println("[setIniFile] 파일 지정: [" + path + "]");
            if (path == null || path.isEmpty()) return;
            fxIni = new FxIniManager(path);
		}

        /** 300ms 디바운스 저장 — 드래그·스크롤 등 연속 이벤트에서 호출 */
        public void scheduleSave() {
            if (fxIni == null) return;
            if (saveDebounceTimer != null) saveDebounceTimer.stop();
            saveDebounceTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(300),
				e -> saveFxNow()));
				saveDebounceTimer.setCycleCount(1);
				saveDebounceTimer.play();
		}

        /** 즉시 저장 (FX 스레드에서 호출) */
        private void saveFxNow() {
            if (fxIni == null) return;
            fxIni.save(state, mainStage.getX(), mainStage.getY());
		}

        /**
			* INI 에서 AppState 를 복원한다.
			* start() 내부에서 buildMaterials() 직후, buildAll() 직전에 호출된다.
			* 창 위치는 pendingStageX/Y 에 보관 → mainStage.show() 후에 적용.
		*/
        private void loadFxState() {
            if (fxIni == null) return;
            double[] pos = fxIni.load(state);
            if (pos != null && !Double.isNaN(pos[0]) && !Double.isNaN(pos[1])) {
                pendingStageX = pos[0];
                pendingStageY = pos[1];
			}
		}

        void start() {

            // 다중 모니터 전체를 덮는 가상 데스크탑 영역 계산
            double vdMinX = Double.MAX_VALUE, vdMinY = Double.MAX_VALUE;
            double vdMaxX = -Double.MAX_VALUE, vdMaxY = -Double.MAX_VALUE;
            for (Screen scr : Screen.getScreens()) {
                Rectangle2D b = scr.getVisualBounds();
                if (b.getMinX() < vdMinX) vdMinX = b.getMinX();
                if (b.getMinY() < vdMinY) vdMinY = b.getMinY();
                if (b.getMaxX() > vdMaxX) vdMaxX = b.getMaxX();
                if (b.getMaxY() > vdMaxY) vdMaxY = b.getMaxY();
			}
            Rectangle2D vb = new Rectangle2D(vdMinX, vdMinY, vdMaxX - vdMinX, vdMaxY - vdMinY);
            state.viewportWidth  = (int) Math.ceil(vb.getWidth());
            state.viewportHeight = (int) Math.ceil(vb.getHeight());

            // ── INI 복원: buildMaterials() 이전에 AppState 를 먼저 채운다 ──────
            loadFxState();
            assembler.buildMaterials();
            assembler.buildAll();

            camera = new PerspectiveCamera(true);
            updateCamera();

            subScene = new SubScene(assembler.root3D, state.viewportWidth, state.viewportHeight, true, SceneAntialiasing.BALANCED);
            subScene.setFill(Color.TRANSPARENT);
            subScene.setCamera(camera);
            subScene.setPickOnBounds(true);

            overlay = new Canvas(state.viewportWidth, state.viewportHeight);
            overlay.setMouseTransparent(true);
            overlay.setOpacity(state.clockOpacity);
            assembler.root3D.setOpacity(state.clockOpacity);

            root = new StackPane(subScene, overlay);
            root.setStyle("-fx-background-color: transparent;");

            // YouTube 프레임 캡처용 숨겨진 그룹 (opacity=0, 씬 그래프 렌더링 필요)
            ytHiddenGroup = new javafx.scene.Group();
            ytHiddenGroup.setOpacity(0);
            ytHiddenGroup.setMouseTransparent(true);
            root.getChildren().add(ytHiddenGroup);

            // YouTube 로딩 메시지 라벨 (화면 하단 중앙)
            statusLabel = new javafx.scene.control.Label();
            statusLabel.setVisible(false);
            statusLabel.setMouseTransparent(true);
            statusLabel.setStyle(
                "-fx-background-color: rgba(0,0,0,0.65);" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-padding: 8 16 8 16;" +
			"-fx-background-radius: 8;");
            javafx.scene.layout.StackPane.setAlignment(statusLabel, javafx.geometry.Pos.BOTTOM_CENTER);
            javafx.scene.layout.StackPane.setMargin(statusLabel, new javafx.geometry.Insets(0, 0, 30, 0));
            root.getChildren().add(statusLabel);

            Scene scene = new Scene(root, state.viewportWidth, state.viewportHeight, Color.TRANSPARENT);
            installHandlers(scene);
            startAnimation();

            mainStage.initStyle(StageStyle.TRANSPARENT);
            mainStage.setTitle("KootPanKingThree Refactored");
            mainStage.setScene(scene);
            mainStage.setX(vb.getMinX());
            mainStage.setY(vb.getMinY());
            mainStage.setWidth(vb.getWidth());
            mainStage.setHeight(vb.getHeight());
            mainStage.setOnHidden(e -> { if (timer != null) timer.stop(); });

			Rectangle2D vb0 = Screen.getPrimary().getVisualBounds();
			mainStage.setX(vb0.getMinX() + (vb0.getWidth() - mainStage.getWidth()) / 2);
			mainStage.setY(vb0.getMinY() + (vb0.getHeight() - mainStage.getHeight()) / 2);
            mainStage.show();
            mainStage.requestFocus();

            // ── show() 이후: 저장된 위치/배경/레인보우 복원 ────────────
            javafx.application.Platform.runLater(() -> {
                // 창 위치 복원
                if (!Double.isNaN(pendingStageX) && !Double.isNaN(pendingStageY)) {
                    mainStage.setX(pendingStageX);
                    mainStage.setY(pendingStageY);
                    System.out.println("[FxIni] 창 위치 복원: (" + pendingStageX + ", " + pendingStageY + ")");
				}
                // 배경 이미지 복원
                if (state.slideshowEnabled && !state.slideshowFiles.isEmpty()) {
                    state.slideshowIndex = 0;
                    loadBackgroundImageFromFile(state.slideshowFiles.get(0));
                    System.out.println("[FxIni] 슬라이드쇼 복원: " + state.slideshowFiles.size() + "장");
					} else if (state.backgroundImageFile != null && state.backgroundImageFile.exists()) {
                    loadBackgroundImageFromFile(state.backgroundImageFile);
                    System.out.println("[FxIni] 배경 이미지 복원: " + state.backgroundImageFile.getName());
				}
                // 레인보우 복원
                if (state.rainbowMode) {
                    assembler.startRainbow(0);
                    System.out.println("[FxIni] 레인보우 모드 복원");
				}
                // 디지탈 시계 재빌드 (복원된 showDigital/showFaceDate 반영)
                if (state.showDigital || state.showFaceDate) {
                    assembler.buildFaceDateTimeGroup();
				}
			});
		}

        /** 현재 Stage 반환 — 앱 메뉴/다이얼로그 owner 연결용 */
        Stage getStage() { return mainStage; }
        /**
			* 제거된 YouTube/WebView 기능 호환용 no-op 메서드.
		*/
        void startYoutube(String streamUrl) {
            // YouTube/WebView 기능 제거됨
		}

        /** 제거된 YouTube/WebView 기능 호환용 no-op 메서드. */
        void showYoutubeLoading(boolean show) {
            // YouTube/WebView 기능 제거됨
		}

        /** 제거된 YouTube/WebView 기능 호환용 no-op 메서드. */
        void stopYoutube() {
            if (stopYoutubeCallback != null) stopYoutubeCallback.run();
		}

        private Runnable stopYoutubeCallback = null;
        /** KootPanKingThree에서 주입. 테마 변경 시 YouTube 중지에 사용. */
        public void setStopYoutubeCallback(Runnable cb) { this.stopYoutubeCallback = cb; }

        private Runnable onDigitalSettingsRequest = null;
        /** 디지탈 영역 더블클릭 시 설정 다이얼로그 콜백. KootPanKingThree에서 주입. */
        public void setOnDigitalSettingsRequest(Runnable cb) {
            this.onDigitalSettingsRequest = cb;
            assembler.setFaceTimeClickHandler(cb);
		}

        private void installHandlers(Scene scene) {
            // ── 마우스 이벤트 (구 MenuController.install) ───────────────
            subScene.setOnMousePressed(e -> {
                mainStage.requestFocus();
                popup.hide();
                if (e.getButton() == MouseButton.PRIMARY) {
                    dragSX = e.getScreenX(); dragSY = e.getScreenY();
                    stageX = mainStage.getX(); stageY = mainStage.getY();
					} else if (e.getButton() == MouseButton.SECONDARY) {
                    rotDX = e.getX(); rotDY = e.getY();
                    rightDragMoved = false;
                    state.autoSpeed = 0.0;
				}
			});
            subScene.setOnMouseDragged(e -> {
                if (e.isPrimaryButtonDown()) {
                    mainStage.setX(stageX + e.getScreenX() - dragSX);
                    mainStage.setY(stageY + e.getScreenY() - dragSY);
					} else if (e.isSecondaryButtonDown()) {
                    double dx = e.getX() - rotDX;
                    double dy = e.getY() - rotDY;
                    if (Math.abs(dx) > 3 || Math.abs(dy) > 3) rightDragMoved = true;
                    state.manualRotAngleY += dx * 0.55;
                    state.manualRotAngleX = AppState.clamp(
                        state.manualRotAngleX + dy * 0.55,
                        AppState.ROOT_ROTATE_X_MIN - state.baseAngleX - state.autoRotAngleX,
					AppState.ROOT_ROTATE_X_MAX - state.baseAngleX - state.autoRotAngleX);
                    rotDX = e.getX(); rotDY = e.getY();
                    assembler.applyRootRotation();
				}
			});
            subScene.setOnMouseReleased(e -> {
                if (e.getButton() == MouseButton.SECONDARY) {
                    state.autoSpeed = state.paused ? 0.0 : state.savedSpeed;
                    assembler.applyRootRotation();
				}
                scheduleSave(); // 드래그/회전 종료 시 저장
			});
            subScene.setOnScroll(e -> {
                state.coinRadius = AppState.clamp(
                    state.coinRadius + e.getDeltaY() * 0.5,
				AppState.COIN_RADIUS_MIN, AppState.COIN_RADIUS_MAX);
                assembler.applyGeometryScale();
                updateCamera();
                scheduleSave(); // 크기 변경 시 저장
                e.consume();
			});

            subScene.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                    popup.show(subScene, e.getScreenX(), e.getScreenY());
				}
			});
            scene.setOnKeyPressed(e -> {
                KeyCode code = e.getCode();
                if (code == KeyCode.SPACE) {
                    state.paused = !state.paused;
                    state.autoSpeed = state.paused ? 0.0 : state.savedSpeed;
					} else if (code == KeyCode.PLUS || code == KeyCode.EQUALS || code == KeyCode.ADD) {
                    if (!state.paused) {
                        state.savedSpeed = AppState.clamp(state.savedSpeed + AppState.SPEED_STEP, AppState.SPEED_MIN, AppState.SPEED_MAX);
                        state.autoSpeed = state.savedSpeed;
					}
					} else if (code == KeyCode.MINUS || code == KeyCode.SUBTRACT) {
                    if (!state.paused) {
                        state.savedSpeed = AppState.clamp(state.savedSpeed - AppState.SPEED_STEP, AppState.SPEED_MIN, AppState.SPEED_MAX);
                        state.autoSpeed = state.savedSpeed;
					}
				}
			});
		}

        // ── 그래픽 팝업 메뉴 구성 ────────────────────────────────────
        private void buildGraphicsMenu(java.util.function.Consumer<ContextMenu> appMenuBuilder) {
            System.out.println("[buildGraphicsMenu]");

            MenuItem header = new MenuItem("KootPanKingThree");
            header.setDisable(true);
            header.setStyle("-fx-opacity:1; -fx-text-fill:#555555; -fx-font-weight:bold;");

            menuSwing.setSelected(!state.paused);
            menuSwing.setOnAction(e -> {
                state.paused = !menuSwing.isSelected();
                state.autoSpeed = state.paused ? 0.0 : state.savedSpeed;
			});
            popup.setOnShowing(e -> {
                menuSwing.setSelected(!state.paused);
                if (onPopupShowing != null) onPopupShowing.run();
			});

            // header + 흔들림만 추가.
            // 중앙고정·디지탈on/off·디지탈시계설정·메인시계설정·separator 는
            // appMenuBuilder(KootPanKingThree.addAppMenuItems) 가 순서대로 추가한다.
            popup.getItems().addAll(
                header,
                new javafx.scene.control.SeparatorMenuItem(),
                menuSwing
			);
            // 앱 제어 항목은 KootPanKingThree 가 추가
            if (appMenuBuilder != null) appMenuBuilder.accept(popup);
		}

        // ── 시계 설정 패널 토글 ──────────────────────────────────────
        private void toggleSetup() {
            if (setupStage != null && setupStage.isShowing()) {
                setupStage.close(); setupStage = null; return;
			}
            if (setupStageOpening) return;
            setupStageOpening = true;
            try {
                // onBloomUpdate 래핑: 색상/네온 변경 시 자동 저장
                Runnable bloomAndSave = () -> { updateSubSceneBloom(); scheduleSave(); };
                SetupPanelController panel = new SetupPanelController(
                    state, assembler, overlay,
                    this::updateCamera,
                    this::loadBackgroundImageFromFile,
                    this::configureSlideshowFromSelectedFile,
                    this::toggleSetup,
                    bloomAndSave,
                    this::stopYoutube,
				this::scheduleSave);  // onSave: 비색상 변경도 즉시 저장
                setupStage = panel.build(mainStage);
                setupStage.setOnHidden(e -> setupStage = null);
				} finally {
                setupStageOpening = false;
			}
		}

        /** 메인 시계 설정 패널 열기 — KootPanKingThree 등 외부에서 호출 가능. */
        public void openSetup() { toggleSetup(); }

        // ── 종료 확인 다이얼로그 ─────────────────────────────────────
        void confirmExit() {
	        System.out.println("[confirmExit()]");
            Stage dialog = new Stage();
            dialog.initOwner(mainStage);
            dialog.initStyle(StageStyle.UTILITY);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setAlwaysOnTop(true);
            dialog.setTitle("종료 확인");

            Label label     = new Label("프로그램을 종료하시겠습니까?");
            Label timerLbl  = new Label("자동 취소까지: 30초");
            timerLbl.setStyle("-fx-text-fill:#888888; -fx-font-size:11px;");
            Button yes = new Button("예");
            Button no  = new Button("아니오 (30)");
            yes.setDefaultButton(true);
            no.setCancelButton(true);

            final javafx.animation.Timeline[] holder = {null};
            final boolean[] decided = {false};

            Runnable doExit = () -> {
                decided[0] = true;
                if (holder[0] != null) holder[0].stop();
		        System.out.println("[dialog.close()]");
				dialog.close();
                try { if (setupStage != null) setupStage.close(); mainStage.close(); } catch (Exception e) { AppLogger.logException(e);}
		        System.out.println("[Platform.exit()]");
				Platform.exit();
			};
            Runnable doCancel = () -> {
                decided[0] = true;
                if (holder[0] != null) holder[0].stop();
                dialog.close();
			};
            yes.setOnAction(e -> doExit.run());
            no.setOnAction(e -> doCancel.run());

            HBox btns = new HBox(10, yes, no); btns.setAlignment(javafx.geometry.Pos.CENTER);
            VBox root = new VBox(10, label, timerLbl, btns);
            root.setAlignment(javafx.geometry.Pos.CENTER);
            root.setStyle("-fx-padding:16; -fx-background-color:white;");
            dialog.setScene(new Scene(root, 300, 130));

            final int[] remain = {30};
            javafx.animation.Timeline tl = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ev -> {
                    remain[0]--;
                    no.setText("아니오 (" + remain[0] + ")");
                    timerLbl.setText("자동 취소까지: " + remain[0] + "초");
                    if (remain[0] <= 5) timerLbl.setStyle("-fx-text-fill:#cc4444; -fx-font-size:11px; -fx-font-weight:bold;");
                    if (remain[0] <= 0 && !decided[0]) doCancel.run();
				}));
				tl.setCycleCount(30);
				holder[0] = tl;
				dialog.setOnHidden(e -> { if (holder[0] != null) holder[0].stop(); });
				dialog.show();
				tl.play();
		}

        // ── FX 알림 헬퍼 ────────────────────────────────────────────
        void showInfo(String title, String text) {
            Stage d = new Stage();
            d.initOwner(mainStage); d.initStyle(StageStyle.UTILITY);
            d.initModality(Modality.APPLICATION_MODAL); d.setAlwaysOnTop(true); d.setTitle(title);
            Label lbl = new Label(text); Button ok = new Button("확인");
            ok.setDefaultButton(true); ok.setOnAction(e -> d.close());
            VBox root = new VBox(12, lbl, ok);
            root.setAlignment(javafx.geometry.Pos.CENTER);
            root.setStyle("-fx-padding:16; -fx-background-color:white;");
            d.setScene(new Scene(root, 420, 140)); d.showAndWait();
		}

        void showError(String title, String text) { showInfo(title, text); }

        private double computeNeonOpacity() {
            switch (state.neonBlinkStyle) {
                case NONE -> { return 1.0; }
                case PULSE -> {
                    state.neonFlickerPhase += state.neonFlickerSpeed * (2.0 * Math.PI / 60.0);
                    if (state.neonFlickerPhase >= 2.0 * Math.PI) {
                        state.neonFlickerPhase -= 2.0 * Math.PI;
					}
                    double wave = 0.5 + 0.5 * Math.sin(state.neonFlickerPhase);
                    return AppState.clamp(1.0 - state.neonFlickerDepth * (1.0 - wave), 0.0, 1.0);
				}
                case SHARP -> {
                    state.neonFlickerPhase += state.neonFlickerSpeed * (2.0 * Math.PI / 60.0);
                    if (state.neonFlickerPhase >= 2.0 * Math.PI) {
                        state.neonFlickerPhase -= 2.0 * Math.PI;
					}
                    double norm = state.neonFlickerPhase / (2.0 * Math.PI);
                    return (norm < 0.70) ? 1.0 : AppState.clamp(1.0 - state.neonFlickerDepth, 0.0, 1.0);
				}
                case RANDOM -> {
                    if (state.neonRandomCounter <= 0) {
                        state.neonRandomOn = !state.neonRandomOn;
                        int base = (int) (60.0 / Math.max(0.2, state.neonFlickerSpeed));
                        if (state.neonRandomOn) {
                            state.neonRandomCounter = base + (int) (Math.random() * base);
							} else {
                            state.neonRandomCounter = Math.max(1, base / 4 + (int) (Math.random() * base / 4));
						}
					}
                    state.neonRandomCounter--;
                    return state.neonRandomOn ? 1.0 : AppState.clamp(1.0 - state.neonFlickerDepth, 0.0, 1.0);
				}
                default -> { return 1.0; }
			}
		}

        private void startAnimation() {
            timer = new AnimationTimer() {

                @Override
                public void handle(long nowNanos) {
                    if (state.autoSpeed != 0.0) {
                        state.autoPhase += state.autoSpeed;
                        state.autoRotAngleY = Math.sin(state.autoPhase) * (state.swingRangeY * 0.5);
                        state.autoRotAngleX = Math.sin(state.autoPhase * AppState.AUTO_X_SWING_RATE) * (state.swingRangeX * 0.5);
					}
                    assembler.applyRootRotation();

                    LocalTime t = LocalTime.now(state.theTimeZone);
                    double ns = t.getNano() / 1_000_000_000.0;
                    double sec = t.getSecond() + ns;
                    double min = t.getMinute() + sec / 60.0;
                    double hr = (t.getHour() % 12) + min / 60.0;

                    assembler.setHandAngles(hr * 30.0, min * 6.0, sec * 6.0);

                    // ── 네온 점멸: 스타일별 opacity 계산 ──────────────────
                    // [BugFix3] NONE 스타일이면 opacity는 항상 1.0 고정 → 매 프레임 갱신 불필요.
                    // 네온이 하나라도 켜져 있고 점멸 스타일이 NONE이 아닐 때만 갱신.
                    if (assembler.isAnyNeonOn()
						&& state.neonBlinkStyle != AppState.NeonBlinkStyle.NONE) {
                        double opacity = computeNeonOpacity();
                        assembler.setNeonFlickerOpacity(opacity);
					}

                    if (state.slideshowEnabled && state.slideshowFiles.size() > 1) {
                        if (state.slideshowLastSwitchNanos == 0L) {
                            state.slideshowLastSwitchNanos = nowNanos;
							} else if (nowNanos - state.slideshowLastSwitchNanos >= state.slideshowIntervalNanos) {
                            state.slideshowLastSwitchNanos = nowNanos;
                            state.slideshowIndex = (state.slideshowIndex + 1) % state.slideshowFiles.size();
                            loadBackgroundImageFromFile(state.slideshowFiles.get(state.slideshowIndex));
						}
					}
                    overlayRenderer.draw(overlay);
                    // 통합 페이스 디지탈 텍스처 갱신 (showDigital / showFaceDate 제어)
                    assembler.updateFaceDateTimeTextures();
                    // Bug1: transparentMode 일 때는 항상 숨김
                    // showDigital OR showFaceDate 중 하나라도 켜져 있으면 그룹 표시
                    boolean dtVisible = (state.showDigital || state.showFaceDate) && !state.transparentMode;
                    assembler.faceDateTimeGroup.setVisible(dtVisible);
				}			};
				timer.start();
		}
        void loadBackgroundImageFromFile(File file) {
            if (file == null) return;
            try {
                // [수정] false(동기) → true(백그라운드) 로딩:
                //   동기 로딩은 AnimationTimer(= JavaFX Application Thread) 안에서 호출될 때
                //   이미지 크기에 따라 수십~수백 ms씩 프레임을 블록한다.
                //   백그라운드 로딩 후 progressProperty 리스너에서 씬을 업데이트한다.
                Image img = new Image(file.toURI().toString(), true);
				img.exceptionProperty().addListener((obs, oldEx, ex) -> {
					if (ex != null) {
						LOG.log(Level.WARNING, "배경 이미지 비동기 로드 실패: " + file, ex);
						System.out.println("배경 이미지 로드 실패: " + file.getAbsolutePath() + " : " + ex.getMessage());
						return;
					}
				});

                img.progressProperty().addListener((obs, ov, nv) -> {
                    if (nv.doubleValue() >= 1.0 && !img.isError()) {
                        // progressProperty 변경 이벤트는 JavaFX Application Thread에서 전달되므로
                        // Platform.runLater 없이 직접 씬을 업데이트해도 안전하다.
                        state.backgroundImage = img;
                        state.backgroundImageFile = file;
                        assembler.applyBackgroundImage();
                        assembler.applyVisibilityState();
					}
				});
                // 이미 캐시된 이미지처럼 즉시 완료된 경우 처리 (드물지만 안전망)
                if (img.getProgress() >= 1.0 && !img.isError()) {
                    state.backgroundImage = img;
                    state.backgroundImageFile = file;
                    assembler.applyBackgroundImage();
                    assembler.applyVisibilityState();
				}
				} catch (Exception e) {
                LOG.log(Level.WARNING, "배경 이미지 로드 실패: " + file, e);
				System.out.println("배경 이미지 로드 실패: " + file.getAbsolutePath() + " : " + e.getMessage());
				AppLogger.logException(e);
			}
		}

        void configureSlideshowFromSelectedFile(File selectedFile) {
            state.slideshowEnabled = false;
            state.slideshowFiles.clear();
            state.slideshowIndex = -1;
            state.slideshowLastSwitchNanos = 0L;
            if (selectedFile == null) return;
            File folder = selectedFile.getParentFile();
            if (folder == null || !folder.isDirectory()) return;

			System.out.println("[슬라이드 쇼] 선택된 폴더 : " + selectedFile.getAbsolutePath() );

            File[] files = folder.listFiles(f -> {
                String name = f.getName().toLowerCase();
                return f.isFile() && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
					|| name.endsWith(".bmp") || name.endsWith(".gif")
					//  || name.endsWith(".webp")
				);			});
				if (files == null || files.length == 0) return;
				Arrays.sort(files, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
				state.slideshowFiles.addAll(Arrays.asList(files));
				for (int i = 0; i < state.slideshowFiles.size(); i++) {
					if (state.slideshowFiles.get(i).equals(selectedFile)) {
						state.slideshowIndex = i;
						break;
					}
				}
				if (state.slideshowIndex < 0) state.slideshowIndex = 0;
				state.slideshowEnabled = state.slideshowFiles.size() > 1;
		}

        private void updateCamera() {
            if (camera == null) return;
            // 카메라 거리는 설정창 슬라이더 값만 반영.
            // coinRadius 보정을 하면 휠 리사이징이 상쇄되므로 제거.
            camera.setTranslateZ(-state.cameraDistance);
            camera.setNearClip(AppState.CAMERA_NEAR_CLIP);
            camera.setFarClip(AppState.CAMERA_FAR_CLIP);
            camera.setFieldOfView(AppState.CAMERA_FOV_DEGREES);
		}

        /**
			* [NEON Fix] SubScene 전체 Bloom 방식 폐기.
			*
			* 기존 방식의 문제:
			*   SubScene 은 2D 이미지로 플래튼된 뒤 Bloom 이 걸리기 때문에,
			*   금색처럼 원래 밝은 표면(faceView, rimView 등)이 selfIlluminationMap 없이도
			*   Bloom threshold(0.3)를 넘어버려 시계 전체가 발광하는 오동작이 발생한다.
			*
			* 수정 방식:
			*   SubScene 에는 Effect 를 걸지 않는다.
			*   대신 applyNeonEffects() 에서 각 Node 에 직접
			*   Bloom + DropShadow 체인 Effect 를 걸어 해당 파트만 번지게 한다.
			*   이 메서드는 하위 호환을 위해 남겨 두지만 내부는 비워 둔다.
		*/
        void updateSubSceneBloom() {
            if (subScene == null) return;
            // SubScene 전체 Bloom 제거 — 개별 Node Effect 로 대체됨
            subScene.setEffect(null);
		}

        // ── 레인보우 베젤 효과 (외부 호출용) ─────────────────────────────
        /**
			* 레인보우 베젤 효과 시작.
			* @param durationSec 0=영구(토글 ON), >0=지정 초 후 자동 종료
		*/
        public void startRainbow(int durationSec) {
            assembler.startRainbow(durationSec);
		}

        /** 레인보우 베젤 효과 즉시 중지 및 원래 색 복원 */
        public void stopRainbow() {
            assembler.stopRainbow();
		}

        /** 레인보우 interval 설정 (AppState 갱신) */
        public void setRainbowInterval(double intervalSec) {
            state.rainbowIntervalSec = intervalSec;
		}

        /** rainbowIntervalSec 반환 */
        public double getRainbowInterval() {
            return state.rainbowIntervalSec;
		}

        /** rainbowMode 반환 */
        public boolean isRainbowMode() {
            return state.rainbowMode;
		}

        // ── 스마트폰 카메라 프레임 → 시계 배경 이미지 영역 주입 ──────────
        /* *
			* Camera.FrameListener 콜백에서 수신된 WritableImage 를
			* 시계 앞면(faceView) 배경 이미지로 직접 주입한다.
			*
			* <p>호출 스레드: 백그라운드(Camera-Reader) 스레드.
			* 씬 그래프 변경은 Platform.runLater 로 FX 스레드에 위임한다.</p>
			*
			* <p>사용 예 (KootPanKingThree.startCamera):
			* <pre>
			*   camera = new CaptureManager.Camera(frame ->
			*       clockController.setCameraFrame(frame));
			* </pre>
			* </p>
			*
			* @param fxImage 카메라 수신 프레임 (null 이면 무시)
		*/
        /* *
			* 카메라 프레임 → 시계 배경 주입 (활성) / 배경 완전 초기화 (중지).
			*
			* ── 슬라이드쇼 [중지] 버튼과 동일한 패턴 ──
			*   Platform.runLater 를 쓰지 않고 FX 스레드에서 즉시 동기 호출한다.
			*   · 활성(fxImage != null): AnimationTimer 핸들러 → 항상 FX 스레드
			*   · 중지(fxImage == null): stopCamera() → camStop.setOnAction → FX 스레드
			*
			* ── 카메라 프레임 주입 시 ──
			*   AnimationTimer 에서 매 프레임 호출하지 않고,
			*   Camera-Reader 스레드 콜백에서 Platform.runLater 를 통해 FX 스레드로 위임.
			*   단, cameraActive == false 이면 runLater 큐에서 꺼내더라도 즉시 반환.
		*/
        // ── CCTV 시작 전 배경 스냅샷 저장 / 복원 ────────────────────────
        /** CCTV 시작 직전에 현재 배경 상태를 스냅샷으로 저장한다. */
        public void saveBackgroundSnapshot() {
            _snapImage         = state.backgroundImage;
            _snapFile          = state.backgroundImageFile;
            _snapSlideEnabled  = state.slideshowEnabled;
            _snapSlideFiles    = new java.util.ArrayList<>(state.slideshowFiles);
            _snapSlideIndex    = state.slideshowIndex;
            _snapSlideInterval = state.slideshowIntervalNanos;
            System.out.println("[ItsCctv] 배경 스냅샷 저장: file=" + _snapFile
			+ " slide=" + _snapSlideEnabled + " files=" + _snapSlideFiles.size());
		}

        /** CCTV 중지 후 saveBackgroundSnapshot() 으로 저장한 배경 상태로 복원한다. */
        public void restoreBackgroundSnapshot() {
            // 슬라이드쇼가 켜져 있었으면 파일 목록 + 인덱스 복원
            if (_snapSlideEnabled && !_snapSlideFiles.isEmpty()) {
                state.slideshowFiles.clear();
                state.slideshowFiles.addAll(_snapSlideFiles);
                state.slideshowIndex         = Math.max(0, _snapSlideIndex);
                state.slideshowIntervalNanos  = _snapSlideInterval;
                state.slideshowLastSwitchNanos = 0L;
                state.slideshowEnabled        = true;
                // 현재 인덱스 파일을 배경으로 즉시 로드
                loadBackgroundImageFromFile(state.slideshowFiles.get(state.slideshowIndex));
                System.out.println("[ItsCctv] 슬라이드쇼 복원: " + state.slideshowFiles.size() + "장");
				} else if (_snapFile != null) {
                // 단일 이미지였으면 그 파일을 다시 로드
                state.slideshowEnabled = false;
                state.slideshowFiles.clear();
                loadBackgroundImageFromFile(_snapFile);
                System.out.println("[ItsCctv] 단일 배경 이미지 복원: " + _snapFile.getName());
				} else if (_snapImage != null) {
                // WritableImage(카메라 프레임 등)를 직접 복원
                state.backgroundImage     = _snapImage;
                state.backgroundImageFile = null;
                state.slideshowEnabled    = false;
                assembler.applyBackgroundImage();
                assembler.applyVisibilityState();
                System.out.println("[ItsCctv] 인메모리 배경 이미지 복원");
				} else {
                // 원래 배경이 없었음 → 슬라이드 중지 버튼과 동일하게 완전 초기화
                state.backgroundImage         = null;
                state.backgroundImageFile     = null;
                state.slideshowEnabled        = false;
                state.slideshowFiles.clear();
                state.slideshowIndex          = -1;
                state.slideshowLastSwitchNanos = 0L;
                assembler.applyBackgroundImage();
                assembler.applyVisibilityState();
                System.out.println("[ItsCctv] 배경 없음 → 초기화");
			}
            // 스냅샷 클리어
            _snapImage = null; _snapFile = null;
            _snapSlideEnabled = false; _snapSlideFiles = null;
		}

        // 스냅샷 저장용 임시 필드
        private javafx.scene.image.Image _snapImage         = null;
        private java.io.File             _snapFile          = null;
        private boolean                  _snapSlideEnabled  = false;
        private java.util.List<java.io.File> _snapSlideFiles = null;
        private int                      _snapSlideIndex    = -1;
        private long                     _snapSlideInterval = 2_000_000_000L;

        public void setCameraFrame(javafx.scene.image.WritableImage fxImage) {
            // 이 메서드는 반드시 FX Application Thread 에서 호출해야 한다.
            // · 중지(null): stopCamera() 가 FX 스레드(메뉴 액션)에서 직접 호출
            // · 활성(frame): Platform.runLater 로 FX 스레드에 위임 후 호출
            if (fxImage == null) {
                // ── 카메라 중지: 슬라이드쇼 [중지] 버튼과 완전히 동일한 처리 ──
                cameraActive              = false;
                state.backgroundImage     = null;
                state.backgroundImageFile = null;
                state.slideshowEnabled    = false;
                state.slideshowFiles.clear();
                state.slideshowIndex      = -1;
                state.slideshowLastSwitchNanos = 0L;
                assembler.applyBackgroundImage();
                assembler.applyVisibilityState();
				} else {
                // ── 카메라 활성: cameraActive 체크 후 주입 ──────────────────
                // stopCamera() 이후 큐에 남아있던 runLater 가 뒤늦게 실행될 경우
                // cameraActive == false 이므로 여기서 차단한다.
                if (!cameraActive) return;
                state.backgroundImage     = fxImage;
                state.backgroundImageFile = null;
                state.slideshowEnabled    = false;
                assembler.applyBackgroundImage();
                assembler.applyVisibilityState();
			}
		}

        /** 중앙 고정: GOLD 테마 + 기본 반지름 + 주 모니터 중앙으로 이동. */
        public void resetToDefault() {
            state.applyTheme(AppState.Theme.GOLD);
            state.coinRadius = AppState.BASE_COIN_RADIUS;
            assembler.rebuildMaterialsAndScene();
            javafx.geometry.Rectangle2D vb0 = javafx.stage.Screen.getPrimary().getVisualBounds();
            mainStage.setX(vb0.getMinX() + (vb0.getWidth()  - mainStage.getWidth())  / 2);
            mainStage.setY(vb0.getMinY() + (vb0.getHeight() - mainStage.getHeight()) / 2);
            scheduleSave();
		}

        /** 디지탈 표시 여부 반환 — 날짜 OR 시분초 중 하나라도 켜져 있으면 true. */
        public boolean getDigitalState() {
            return state.showDigital || state.showFaceDate;
		}

        /**
			* 팝업 메뉴 마스터 스위치 전용.
			* ON: 날짜+시분초 둘 다 켜고 재빌드.
			* OFF: 날짜+시분초 둘 다 끄고 씬에서 완전 제거.
			* ※ 설정 다이얼로그에서는 이 메서드를 호출하지 말 것.
		*/
        public void setDigitalState(boolean on) {
            state.showDigital  = on;
            state.showFaceDate = on;
            if (on) {
                assembler.buildFaceDateTimeGroup();
				} else {
                assembler.removeDigitalGroup();
			}
            // 메뉴 체크 상태 동기화
            if (onPopupShowing != null) onPopupShowing.run();
            scheduleSave();
		}

        /**
			* 설정 다이얼로그 전용 재빌드.
			* showDigital/showFaceDate 는 이미 AppState 에 설정된 값을 그대로 사용.
			* 둘 다 false 이면 씬에서 제거, 하나라도 true 이면 재빌드.
		*/
        public void applyDigitalSettings() {
            boolean anyOn = state.showDigital || state.showFaceDate;
            if (anyOn) {
                assembler.buildFaceDateTimeGroup();
				} else {
                assembler.removeDigitalGroup();
			}
            scheduleSave();
		}

        /** removeDigitalGroup 외부 접근용 */
        public void removeDigitalObjects() {
            assembler.removeDigitalGroup();
		}

        /** AppState 직접 접근 (KootPanKingThree 설정 다이얼로그용). */
        public AppState getAppState() { return state; }

        /** KootPanKingThree 에서 페이스 날짜/시간 그룹 재빌드 요청 */
        public void rebuildFaceDateTimeGroup() {
            assembler.buildFaceDateTimeGroup();
		}
        /** 페이스 날짜/시간 ON/OFF — showFaceDate 와 showDigital 을 함께 고려 */
        public void setFaceDateTimeVisible(boolean on) {
            state.showFaceDate = on;
            boolean groupVisible = (on || state.showDigital) && !state.transparentMode;
            assembler.faceDateTimeGroup.setVisible(groupVisible);
            if (assembler.faceDateBox != null) assembler.faceDateBox.setVisible(on);
            scheduleSave();
		}

        public void showStatusMessage(String message) {
            if (statusLabel == null) return;
            if (message == null || message.isEmpty()) {
                statusLabel.setVisible(false);
				} else {
                statusLabel.setText(message);
                statusLabel.setVisible(true);
			}
		}

        // ── YouTube 프레임 캡처용 MediaView 관리 ──────────────────
        /** MediaView를 숨겨진 그룹에 추가 (스냅샷용, 화면에는 안 보임). FX 스레드 전용. */
        public void attachMediaView(javafx.scene.Node view) {
            if (ytHiddenGroup != null) {
                ytHiddenGroup.getChildren().setAll(view);
			}
		}

        /** MediaView를 숨겨진 그룹에서 제거. FX 스레드 전용. */
        public void detachMediaView() {
            if (ytHiddenGroup != null) ytHiddenGroup.getChildren().clear();
		}
	}

    // ───────────────────────── State ─────────────────────────
    final class AppState {
        static final int UI_MAX_VIEWPORT = 920;
        static final int SEGS = 128;

        static final double BASE_COIN_RADIUS = 140.0;
        static final double BASE_COIN_HEIGHT = 18.0;
        static final double BASE_RIM_EXTRA = 11.0;

        static final double SPEED_MIN = 0.001;
        static final double SPEED_MAX = 0.060;
        static final double SPEED_STEP = 0.003;

        static final double CAMERA_NEAR_CLIP = 0.1;
        static final double CAMERA_FAR_CLIP = 5000.0;
        static final double CAMERA_FOV_DEGREES = 40.0;

        static final double AUTO_X_SWING_RATE = 0.37;
        static final double ROOT_ROTATE_X_MIN = -75.0;
        static final double ROOT_ROTATE_X_MAX = 75.0;
        static final double OVERLAY_GLASS_ALPHA = 0.24;
        static final double NUMBER_FONT_SIZE_RATIO = 0.118;
        static final double GLASS_INNER_RATIO = 0.80;
        static final double GLASS_OUTER_RATIO = 0.944;
        static final double TICK_HOUR_INNER_RATIO = 0.79;
        static final double TICK_MINUTE_INNER_RATIO = 0.84;

        // 마우스 휠 리사이징 범위: 현재 슬라이더 min(70) * 50% ~ max(220) * 3배
        static final double COIN_RADIUS_MIN = 35.0;
        static final double COIN_RADIUS_MAX = 660.0;

        int viewportWidth = 820;
        int viewportHeight = 820;

        double coinRadius = BASE_COIN_RADIUS;
        double cameraDistance = 360.0;

        double autoPhase = 0.0;
        double autoSpeed = 0.010;
        double savedSpeed = 0.010;
        boolean paused = false;
        double swingRangeY = 84.0;
        double swingRangeX = 32.0;
        double baseAngleX = 22.0;

        double autoRotAngleY = 0.0;
        double autoRotAngleX = 0.0;
        double manualRotAngleY = 0.0;
        double manualRotAngleX = 0.0;

        boolean showNumbers = true;
        boolean showGlass = false;
        /** 글래스 레이어 불투명도 (0.0~1.0). 설정창 슬라이더로 조정. */
        double glassOpacity = OVERLAY_GLASS_ALPHA;
        boolean showConvexGlass = false;  // 볼록 유리 효과 on/off (crystalMode=1 전용)
        double youtubeScale = 0.5; // YouTube 영상 확대/축소 (0.0=전체보기, 1.0=꽉채우기)
        int crystalMode = 0;
        double clockOpacity = 1.0;
        boolean interactiveResizing = false;
        double resizeSavedAutoSpeed = 0.0;

        Color faceColor = Color.color(0.90, 0.68, 0.10);
        Color backColor = Color.color(0.55, 0.36, 0.04);
        Color rimColor = Color.color(1.00, 0.88, 0.32);
        Color hourHandColor = Color.color(0.22, 0.12, 0.00);
        Color minuteHandColor = Color.color(0.18, 0.10, 0.00);
        Color secondHandColor = Color.color(0.88, 0.15, 0.00);
        Color fiveMinuteTickColor = Color.color(1.00, 0.95, 0.62);
        Color oneMinuteTickColor = Color.color(0.78, 0.58, 0.10);
        Color numberColor = Color.color(0.92, 0.82, 0.40);

        // ── NEON 효과 플래그 (색깔 설정 항목별) ────────────────────────
        boolean neonFace          = false;
        boolean neonBack          = false;
        boolean neonRim           = false;
        boolean neonHourHand      = false;
        boolean neonMinuteHand    = false;
        boolean neonSecondHand    = false;
        boolean neonFiveMinuteTick = false;
        boolean neonOneMinuteTick  = false;
        boolean neonNumber        = false;

        // ── NEON 점멸 설정 ───────────────────────────────────────────
        /**
			* 점멸 스타일.
			* NONE   : 항상 켜짐 (점멸 없음)
			* PULSE  : 부드러운 맥박 (sin 파형, 자연스러운 호흡)
			* SHARP  : 날카로운 깜빡임 (on/off 교차, 실제 네온관 느낌)
			* RANDOM : 불규칙 깜빡임 (랜덤 주기, 오래된 네온관 느낌)
		*/
        enum NeonBlinkStyle { NONE, PULSE, SHARP, RANDOM }
        NeonBlinkStyle neonBlinkStyle = NeonBlinkStyle.PULSE;
        /**
			* 점멸 속도 (Hz). 1.0 = 초당 1사이클.
			* 슬라이더 범위: 0.2 ~ 6.0
		*/
        double neonFlickerSpeed   = 1.2;
        /**
			* 점멸 깊이 (0.0~1.0).
			* 0.0 = 전혀 안 꺼짐(항상 최대), 1.0 = 완전히 꺼졌다 켜짐.
			* 기본 0.55 → 밝기가 45%~100% 사이에서 진동.
		*/
        double neonFlickerDepth   = 0.55;
        /** 점멸 위상 (AnimationTimer가 매 프레임 누적) */
        double neonFlickerPhase   = 0.0;
        /** RANDOM 스타일 전용: 다음 깜빡임까지 남은 프레임 카운터 */
        int    neonRandomCounter  = 0;
        /** RANDOM 스타일 전용: 현재 ON(true)/OFF(false) 상태 */
        boolean neonRandomOn      = true;

        double oneMinuteTickHeight = 1.6;
        double fiveMinuteTickHeight = 3.5;
        double numberHeightScale = 1.0;

        String numberFont = "Georgia";

        // ── 레인보우 베젤 효과 ───────────────────────────────────────────
        /** 레인보우 모드 ON/OFF */
        boolean rainbowMode = false;
        /** 색 변경 간격 (초): 0.5,1,2,3,4,5,10,15,20,30,60 중 선택 */
        double rainbowIntervalSec = 0.5;

        // ── 투명 모드 ────────────────────────────────────────────────────
        /** 앞면·본체·뒷면·유리 모두 숨겨 시계 내부를 투명하게 만드는 모드 */
        boolean transparentMode = false;

        // ── 시분초 행 (하단) ─────────────────────────────────────────
        /** 시분초 행 표시 ON/OFF */
        boolean showDigital = true;
        /** 시분초 표시 방식 인덱스 (0~3) */
        int digitalFormatIndex = 0;
        /** 시분초 폰트 패밀리 */
        String digitalFontFamily = "Consolas";
        /** 시분초 폰트 크기 */
        double digitalFontSize   = 50.0;   // 기본 크기
        /** 시분초 글자 색 (0xAARRGGBB) */
        int digitalColorRgb = 0xFFFFFFFF;
        /** 시분초 네온 효과 ON/OFF */
        boolean neonFaceTime = false;
        /** 스크롤 방향: 0=고정, 1=우→좌, 2=좌→우, 3=핑퐁 */
        int digitalScrollDir = 3;          // 핑퐁
        /** 핑퐁 전용 이동 방향 */
        int digitalPingPongDir = 1;
        /** 스크롤 속도 */
        double digitalScrollSpeed = 3.0;
        /** 스크롤 X 오프셋 (NaN=미초기화) */
        double digitalScrollOffset = Double.NaN;
        /** 페이스 시간 행 스크롤 오프셋 */
        double faceScrollOffset = Double.NaN;
        /** 페이스 시간 행 핑퐁 방향 */
        int    facePingPongDir  = 1;

        // ── 날짜 행 스크롤 (Bug7 추가) ──────────────────────────────
        /** 날짜 행 스크롤 방향: 0=고정, 1=우→좌, 2=좌→우, 3=핑퐁 */
        int    faceDateScrollDir   = 3;    // 핑퐁
        /** 날짜 행 스크롤 속도 */
        double faceDateScrollSpeed = 1.5;  // 기본 속도
        /** 날짜 행 스크롤 X 오프셋 (NaN=미초기화) */
        double faceDateScrollOffset = Double.NaN;
        /** 날짜 행 핑퐁 방향 */
        int    faceDatePingPongDir  = 1;

        // ── 날짜 행 (상단) ───────────────────────────────────────────
        /** 날짜 행 표시 ON/OFF */
        boolean showFaceDate = true;
        /** 날짜 표시 방식 인덱스 (0~3) */
        int faceDateFormatIndex = 0;
        /** 날짜 폰트 패밀리 */
        String faceDateFontFamily = "HY견고딕";
        /** 날짜 폰트 크기 */
        double faceDateFontSize = 60.0;    // 기본 크기
        /** 날짜 글자 색 (0xAARRGGBB) */
        int faceDateColorRgb = 0xFF003333; // #003333 진한 청록
        /** 날짜 네온 효과 ON/OFF */
        boolean neonFaceDate = false;
        NeonBlinkStyle digitalNeonBlinkStyle = NeonBlinkStyle.NONE;

        // ── 배경 이미지 ──────────────────────────────────────────────────
        /** 시계 앞면(faceView) 동그라미에 매핑할 사용자 지정 이미지. null이면 기본 금속 색상 사용 */
        Image backgroundImage = null;
        File backgroundImageFile = null;
        List<File> slideshowFiles = new ArrayList<>();
        int slideshowIndex = -1;
        boolean slideshowEnabled = false;
        long slideshowLastSwitchNanos = 0L;
        /** 슬라이드쇼 전환 간격 (나노초). 설정창 리스트박스로 변경 가능. 기본값 2초. */
        long slideshowIntervalNanos = 2_000_000_000L;

        // ── 설정창 위치 기준점 ────────────────────────────────────────

        // ── 설정창 외관 ───────────────────────────────────────────────
        /** 설정창 배경색 (hex, 기본 흰색). [...] 버튼으로 변경 가능. */
        String dialogBgColor    = "#ffffff";
        /** 설정창 메뉴 폰트. "System"이면 JavaFX 기본 폰트 사용. */
        String dialogFontFamily = "System";

        // ── 세계시계 타임존 / 도시명 ──────────────────────────────────
        /** 세계시계용 타임존. 기본값은 시스템(Local). */
        java.time.ZoneId theTimeZone  = java.time.ZoneId.systemDefault();
        /** 날짜 앞에 표시할 도시명. 빈 문자열이면 표시 안 함. */
        String cityPrefix = "";

        // ── 테마 프리셋 ──────────────────────────────────────────────
        enum Theme { GOLD, SILVER, COPPER, MIDNIGHT, ROSE_GOLD }

        void applyTheme(Theme theme) {
            switch (theme) {
                case GOLD -> {
                    faceColor         = Color.color(0.90, 0.68, 0.10);
                    backColor         = Color.color(0.55, 0.36, 0.04);
                    rimColor          = Color.color(1.00, 0.88, 0.32);
                    hourHandColor     = Color.color(0.22, 0.12, 0.00);
                    minuteHandColor   = Color.color(0.18, 0.10, 0.00);
                    secondHandColor   = Color.color(0.88, 0.15, 0.00);
                    fiveMinuteTickColor = Color.color(1.00, 0.95, 0.62);
                    oneMinuteTickColor  = Color.color(0.78, 0.58, 0.10);
                    numberColor       = Color.color(0.92, 0.82, 0.40);
				}
                case SILVER -> {
                    faceColor         = Color.color(0.82, 0.82, 0.86);
                    backColor         = Color.color(0.50, 0.50, 0.55);
                    rimColor          = Color.color(0.95, 0.95, 0.97);
                    hourHandColor     = Color.color(0.18, 0.18, 0.22);
                    minuteHandColor   = Color.color(0.15, 0.15, 0.18);
                    secondHandColor   = Color.color(0.75, 0.10, 0.10);
                    fiveMinuteTickColor = Color.color(0.92, 0.92, 0.96);
                    oneMinuteTickColor  = Color.color(0.62, 0.62, 0.68);
                    numberColor       = Color.color(0.96, 0.96, 1.00);
				}
                case COPPER -> {
                    faceColor         = Color.color(0.72, 0.40, 0.20);
                    backColor         = Color.color(0.42, 0.22, 0.08);
                    rimColor          = Color.color(0.85, 0.52, 0.28);
                    hourHandColor     = Color.color(0.20, 0.10, 0.04);
                    minuteHandColor   = Color.color(0.16, 0.08, 0.02);
                    secondHandColor   = Color.color(0.20, 0.62, 0.45);
                    fiveMinuteTickColor = Color.color(0.92, 0.68, 0.44);
                    oneMinuteTickColor  = Color.color(0.60, 0.35, 0.16);
                    numberColor       = Color.color(0.96, 0.74, 0.50);
				}
                case MIDNIGHT -> {
                    faceColor         = Color.color(0.08, 0.10, 0.22);
                    backColor         = Color.color(0.04, 0.05, 0.12);
                    rimColor          = Color.color(0.20, 0.24, 0.48);
                    hourHandColor     = Color.color(0.70, 0.75, 1.00);
                    minuteHandColor   = Color.color(0.55, 0.60, 0.90);
                    secondHandColor   = Color.color(0.20, 0.80, 1.00);
                    fiveMinuteTickColor = Color.color(0.40, 0.50, 0.90);
                    oneMinuteTickColor  = Color.color(0.20, 0.26, 0.55);
                    numberColor       = Color.color(0.65, 0.75, 1.00);
				}
                case ROSE_GOLD -> {
                    faceColor         = Color.color(0.88, 0.58, 0.52);
                    backColor         = Color.color(0.55, 0.30, 0.26);
                    rimColor          = Color.color(1.00, 0.76, 0.70);
                    hourHandColor     = Color.color(0.28, 0.10, 0.08);
                    minuteHandColor   = Color.color(0.22, 0.08, 0.06);
                    secondHandColor   = Color.color(0.80, 0.20, 0.30);
                    fiveMinuteTickColor = Color.color(1.00, 0.84, 0.80);
                    oneMinuteTickColor  = Color.color(0.76, 0.52, 0.48);
                    numberColor       = Color.color(1.00, 0.88, 0.84);
				}
			}
		}

        static double clamp(double v, double lo, double hi) {
            return Math.max(lo, Math.min(hi, v));
		}

        double getComposedRotAngleX() {
            return clamp(baseAngleX + autoRotAngleX + manualRotAngleX, ROOT_ROTATE_X_MIN, ROOT_ROTATE_X_MAX);
		}

        double getComposedRotAngleY() {
            return autoRotAngleY + manualRotAngleY;
		}

        double computeOverlayProjectionScale() {
            double fovRadians = Math.toRadians(CAMERA_FOV_DEGREES);
            return (viewportHeight * 0.5) / Math.tan(fovRadians * 0.5);
		}
	}

    // ───────────────────────── Scene Assembler ─────────────────────────
    final class SceneAssembler {
        private final AppState state;

        final Group root3D = new Group();
        private final Rotate rootRotY = new Rotate(0, Rotate.Y_AXIS);
        private final Rotate rootRotX = new Rotate(0, Rotate.X_AXIS);
        private final Scale coinScale = new Scale(1, 1, 1);

        private final Group coinGroup = new Group();
        private final Group numbersGroup = new Group();
        private final Group handsGroup = new Group();
        private final Group lightsGroup = new Group();
		private Group backTextGroup;

        private final Materials materials = new Materials();

        private Rotate hourRotation;
        private Rotate minuteRotation;
        private Rotate secondRotation;
        private MeshView glassView;
        private MeshView faceView;
        private MeshView backView;
        private final Group imageLayerGroup = new Group();
        private Group tickGroup;
        private MeshView capRingView;
        private MeshView frontBevelView;
        private final Group crystalGroup = new Group();
        private final List<Node> crystalNodes = new ArrayList<>();
        private Cylinder coinBodyView;

        // ── 페이스 날짜/시간 3D 노드 (coinGroup 자식 — 코인과 함께 회전) ──
        private final Group  faceDateTimeGroup = new Group();
        private Box          faceDateBox     = null;
        private Box          faceTimeBox     = null;
        private WritableImage faceDateTexImg = null;
        private WritableImage faceTimeTexImg = null;
        private PhongMaterial faceDateMat    = null;
        private PhongMaterial faceTimeMat    = null;

        // [추가] 배경 이미지 텍스처 스냅샷 캐시:
        //   applyBackgroundImage()가 호출될 때마다 512×512 Canvas를 새로 그리고 snapshot()하는 것은
        //   슬라이드쇼 전환(2초마다) 및 interactiveResize 종료 시마다 발생해 불필요한 비용.
        //   이전에 스냅샷한 Image와 동일한 source Image이면 재사용한다.
        private Image         cachedSnapshotSource = null;
        private WritableImage cachedTexSnapshot    = null;
        private PhongMaterial cachedImgMaterial    = null;  // Fix4: 동일 스냅샷이면 재사용
        private MeshView      texturedFaceView     = null;  // 배경 이미지용 앞면 텍스처 디스크 (backView를 교체하지 않음)
        private boolean       frontFaceVisible     = true;

        // ── 레인보우 베젤 효과 ───────────────────────────────────────────
        private Timeline rainbowCycleTimeline = null;  // 색 순환 타이머
        private Timeline rainbowStopTimeline  = null;  // 자동 종료 타이머
        private final int[] rainbowColorIdx = {0};
        private boolean rainbowActive = false;
        private static final Color[] RAINBOW_COLORS = {
            Color.RED,
            Color.ORANGE,
            Color.YELLOW,
            Color.color(0.0,  0.85, 0.0),   // 초록
            Color.color(0.0,  0.5,  1.0),   // 파랑
            Color.color(0.29, 0.0,  0.51),  // 남색
            Color.color(0.72, 0.0,  1.0),   // 보라
		};

        /**
			* 레인보우 베젤 효과 시작.
			* @param durationSec 0=무한(토글 ON), >0=해당 초 후 자동 종료 (단, rainbowMode=true 이면 무시)
		*/
        void startRainbow(int durationSec) {
            rainbowActive = true;
            rainbowColorIdx[0] = 0;
            // 기존 타이머 정리
            if (rainbowCycleTimeline != null) rainbowCycleTimeline.stop();
            if (rainbowStopTimeline  != null) rainbowStopTimeline.stop();

            // 색 순환 타이머: state.rainbowIntervalSec 간격
            double intervalSec = Math.max(0.1, state.rainbowIntervalSec);
            rainbowCycleTimeline = new Timeline(
                new KeyFrame(Duration.seconds(intervalSec), e -> applyNextRainbowColor())
			);
            rainbowCycleTimeline.setCycleCount(Timeline.INDEFINITE);
            rainbowCycleTimeline.play();
            applyNextRainbowColor(); // 즉시 첫 색 적용

            // 자동 종료 타이머 (durationSec>0 이고 영구 모드 아닐 때)
            if (durationSec > 0 && !state.rainbowMode) {
                rainbowStopTimeline = new Timeline(
                    new KeyFrame(Duration.seconds(durationSec), e -> stopRainbow())
				);
                rainbowStopTimeline.setCycleCount(1);
                rainbowStopTimeline.play();
			}
		}

        /** 레인보우 베젤 효과 중지 및 원래 색 복원 */
        void stopRainbow() {
            if (rainbowCycleTimeline != null) { rainbowCycleTimeline.stop(); rainbowCycleTimeline = null; }
            if (rainbowStopTimeline  != null) { rainbowStopTimeline.stop();  rainbowStopTimeline  = null; }
            rainbowActive = false;
            restoreRimFromRainbow();
		}

        private void applyNextRainbowColor() {
            int idx = rainbowColorIdx[0] % RAINBOW_COLORS.length;
            rainbowColorIdx[0]++;
            Color c = RAINBOW_COLORS[idx];

            // ── 베젤 재질: diffuse + selfIllumination ────────────────────
            // selfIlluminationMap 으로 표면 자체발광 → 재질만 바꾸고
            // 노드 레벨 Effect 는 coinBodyView 에 InnerShadow 가 걸리면
            // 시계 면 전체(numbersGroup 포함)가 가려지는 문제 발생하므로
            // 순수 테두리 노드(capRingView, frontBevelView)에만 DropShadow 적용.
            if (materials.rim != null) {
                materials.rim.setDiffuseColor(c);
                materials.rim.setSelfIlluminationMap(makeGlowTexture(c.brighter()));
			}
            // coinBodyView: 시계 전체 Cylinder → Effect 절대 금지
            //   (InnerShadow 는 Cylinder 바운딩박스 안을 덮어 숫자 가림,
            //    DropShadow 도 3D SubScene 합성 순서에서 오버드로우 유발)
            if (coinBodyView != null) coinBodyView.setEffect(null);

            // capRingView / frontBevelView: 순수 베젤 테두리 → DropShadow만 (InnerShadow 없음)
            applyBezelRimGlow(capRingView,    c);
            applyBezelRimGlow(frontBevelView, c);

            // ── 숫자 1~12: 순차 오프셋 무지개 색 ────────────────────────
            // 숫자 i 번째에 (idx+i)%7 색 배정 → 시계판 전체가 스펙트럼으로 펼쳐짐
            // 노드 Effect 없음 — selfIlluminationMap 으로만 발광
            if (numbersGroup != null && state.showNumbers) {
                java.util.List<javafx.scene.Node> nums = numbersGroup.getChildren();
                for (int i = 0; i < nums.size(); i++) {
                    Color nc = RAINBOW_COLORS[(idx + i) % RAINBOW_COLORS.length];
                    applyRainbowColorRecursive(nums.get(i), nc);
				}
			}
		}

        /**
			* 베젤 테두리 노드(capRingView, frontBevelView)에만 적용하는 발광 효과.
			* InnerShadow 를 사용하지 않음 — 시계 면을 덮지 않는 순수 외부 후광.
			* DropShadow(큰 반경) + Bloom(threshold 0.75)
		*/
        private static void applyBezelRimGlow(javafx.scene.Node node, Color c) {
            if (node == null) return;
            javafx.scene.effect.DropShadow glow = new javafx.scene.effect.DropShadow(
			javafx.scene.effect.BlurType.GAUSSIAN, c, 38, 0.68, 0, 0);
            javafx.scene.effect.Bloom bloom = new javafx.scene.effect.Bloom(0.75);
            bloom.setInput(glow);
            node.setEffect(bloom);
		}

        /**
			* 숫자 노드 재귀 순회 → PhongMaterial diffuse + selfIlluminationMap 변경.
			* Effect 는 건드리지 않음 (3D SubScene 합성 오버드로우 방지).
		*/
        private static void applyRainbowColorRecursive(javafx.scene.Node node, Color c) {
            if (node instanceof Shape3D) {
                javafx.scene.paint.Material mat = ((Shape3D) node).getMaterial();
                if (mat instanceof PhongMaterial) {
                    PhongMaterial pm = (PhongMaterial) mat;
                    pm.setDiffuseColor(c);
                    pm.setSelfIlluminationMap(makeGlowTexture(c.brighter()));
				}
				} else if (node instanceof Group) {
                for (javafx.scene.Node child : ((Group) node).getChildren())
				applyRainbowColorRecursive(child, c);
			}
		}

        /**
			* 숫자 재질 diffuse 를 원래 numberColor 로 복원.
			* selfIlluminationMap 복원은 applyNeonToGroup() 가 담당.
		*/
        private static void restoreNumberDiffuseRecursive(javafx.scene.Node node, Color c) {
            if (node instanceof Shape3D) {
                javafx.scene.paint.Material mat = ((Shape3D) node).getMaterial();
                if (mat instanceof PhongMaterial)
				((PhongMaterial) mat).setDiffuseColor(c);
				} else if (node instanceof Group) {
                for (javafx.scene.Node child : ((Group) node).getChildren())
				restoreNumberDiffuseRecursive(child, c);
			}
		}

        /** 레인보우 종료 후 원래 베젤/숫자 색·효과 복원 */
        private void restoreRimFromRainbow() {
            // ── 베젤 재질 복원 ────────────────────────────────────────────
            if (materials.rim != null) {
                materials.rim.setDiffuseColor(state.rimColor);
                materials.rim.setSelfIlluminationMap(
				state.neonRim ? makeGlowTexture(deriveNeonColor(state.rimColor)) : null);
			}
            // ── 베젤 노드 Effect 복원 ─────────────────────────────────────
            applyNeonEffectToNode(coinBodyView,   state.neonRim, state.rimColor);
            applyNeonEffectToNode(capRingView,    state.neonRim, state.rimColor);
            applyNeonEffectToNode(frontBevelView, state.neonRim, state.rimColor);

            // ── 숫자 색·효과 복원 ─────────────────────────────────────────
            if (numbersGroup != null) {
                for (javafx.scene.Node n : numbersGroup.getChildren())
				restoreNumberDiffuseRecursive(n, state.numberColor);
                applyNeonToGroup(numbersGroup, state.neonNumber, state.numberColor);
                applyNeonEffectToGroup(numbersGroup, state.neonNumber, state.numberColor);
			}
		}

        SceneAssembler(AppState state) {
            this.state = state;
		}

        void buildMaterials() {
            materials.face = metal(state.faceColor, 0.55, 16);
            materials.back = metal(state.backColor, 0.45, 10);
            materials.rim = metal(state.rimColor, 1.00, 82);
            materials.hour = metal(state.hourHandColor, 0.35, 12);
            materials.minute = metal(state.minuteHandColor, 0.30, 12);
            materials.second = metal(state.secondHandColor, 0.60, 28);
            materials.tickHour = tickFaceMaterial(state.fiveMinuteTickColor);
            materials.tickMinute = tickFaceMaterial(state.oneMinuteTickColor);
            materials.gem = metal(Color.color(1.00, 0.94, 0.52), 1.00, 95);

            PhongMaterial glass = new PhongMaterial();
            glass.setDiffuseColor(Color.color(0.78, 0.92, 1.00, 0.18));
            glass.setSpecularColor(Color.color(1, 1, 1, 0.90));
            glass.setSpecularPower(120);
            materials.glass = glass;

            materials.faceFlat = matte(darken(state.faceColor, 0.03));
            materials.backFlat = matte(darken(state.backColor, 0.02));
		}

        void buildAll() {
            root3D.getChildren().clear();
            coinGroup.getChildren().clear();
            numbersGroup.getChildren().clear();
            handsGroup.getChildren().clear();
            lightsGroup.getChildren().clear();
            // coinGroup이 클리어되므로 texturedFaceView 참조도 초기화
            texturedFaceView = null;

            buildCoinShell();
            buildRim();
            buildTicks();
            buildHands();
            buildGem();
            buildBackEngraving();
            rebuildNumbers();
            rebuildGlass();
            rebuildCrystalEffect();
            buildLights();

            coinGroup.getTransforms().setAll(coinScale, rootRotY, rootRotX);
            root3D.getChildren().addAll(coinGroup, lightsGroup, imageLayerGroup);

            // ── 페이스 날짜/시간: coinGroup 자식으로 추가 ─────────────
            buildFaceDateTimeGroup();

            applyGeometryScale();
            applyVisibilityState();
            applyInteractiveResizeState(); // 내부에서 applyBackgroundImage() 호출
            applyRootRotation();
            // ↑ applyInteractiveResizeState()가 이미 applyBackgroundImage()를 호출하므로
            // 여기서 중복 호출 제거
		}

        void applyRootRotation() {
            rootRotY.setAngle(state.autoRotAngleY + state.manualRotAngleY);
            rootRotX.setAngle(AppState.clamp(state.baseAngleX + state.autoRotAngleX + state.manualRotAngleX, AppState.ROOT_ROTATE_X_MIN, AppState.ROOT_ROTATE_X_MAX));
		}

        /**
			* 사용자가 선택한 이미지를 시계 앞면(동그라미)의 배경으로 설정한다.
			* state.backgroundImage == null 이면 기본 금속 재질로 복원한다.
			*
			* 구현 방법:
			*  - faceView 는 MeshFactory.makeDisk() 로 생성된 UV-less 단순 메시이므로,
			*    원형 디스크에 이미지를 표시하기 위해 2D Canvas 에 원형 클리핑된 이미지를
			*    스냅샷으로 렌더링한 뒤 PhongMaterial.setDiffuseMap() 으로 매핑한다.
			*  - UV 좌표가 없는 기존 makeDisk() 메시 대신 makeTexturedDisk() 를 사용해
			*    UV 좌표를 포함한 새 faceView 로 교체한다.
			*
			* [NPE Fix] QuantumRenderer-0 / UploadingPainter NPE 수정:
			*  - texturedFaceView 를 coinGroup 에서 제거·교체하지 않는다.
			*    렌더 스레드(QuantumRenderer-0)가 업로드 중인 노드를
			*    Application Thread 가 동시에 교체하면 UploadingPainter 가 NPE 를 던진다.
			*  - 최초 1회만 coinGroup 에 추가하고, 이후에는 setVisible() 로만 ON/OFF.
			*  - cachedImgMaterial 객체도 재생성하지 않고 setDiffuseMap() 으로 텍스처만 교체.
		*/
        /** 앞면 원판(face/texturedFace) 표시 여부 설정 */
        void setFrontFaceVisible(boolean visible) {
            frontFaceVisible = visible;
            if (faceView != null) {
                faceView.setVisible(visible && state.backgroundImage == null && !state.interactiveResizing);
			}
            if (texturedFaceView != null) {
                texturedFaceView.setVisible(visible && state.backgroundImage != null && !state.interactiveResizing);
			}
		}

        void applyBackgroundImage() {
            if (faceView == null || backView == null) return;

            // ── 이미지 없음: texturedFaceView 숨기고 원래 faceView 복원 ─────────
            // [NPE Fix] coinGroup 에서 제거하지 않고 setVisible(false) 로만 숨긴다.
            if (state.backgroundImage == null) {
                if (texturedFaceView != null) {
                    texturedFaceView.setVisible(false);
                    texturedFaceView.setOpacity(0.0);
				}
                faceView.setVisible(frontFaceVisible);
                faceView.setOpacity(1.0);
                faceView.setMaterial(materials.face);
                faceView.setCullFace(CullFace.NONE);
                backView.setVisible(true);
                backView.setOpacity(1.0);
                backView.setMaterial(materials.back);
                backView.setCullFace(CullFace.NONE);
                cachedSnapshotSource = null;
                cachedTexSnapshot    = null;
                return;
			}

            // ── 이미지 있음: 앞면에 텍스처 디스크를 겹쳐 표시 ───────────────
            backView.setVisible(true);
            backView.setOpacity(1.0);
            backView.setMaterial(materials.back);

            int texSize = 512;
            WritableImage texImg;
            if (state.backgroundImage == cachedSnapshotSource && cachedTexSnapshot != null) {
                texImg = cachedTexSnapshot;
				} else {
                javafx.scene.canvas.Canvas texCanvas = new javafx.scene.canvas.Canvas(texSize, texSize);
                javafx.scene.canvas.GraphicsContext gc = texCanvas.getGraphicsContext2D();

                gc.save();
                gc.beginPath();
                gc.arc(texSize * 0.5, texSize * 0.5, texSize * 0.5, texSize * 0.5, 0, 360);
                gc.closePath();
                gc.clip();

                double imgW  = state.backgroundImage.getWidth();
                double imgH  = state.backgroundImage.getHeight();
                double scaleMin = Math.min(texSize / imgW, texSize / imgH); // 전체 보기
                double scaleMax = Math.max(texSize / imgW, texSize / imgH); // 꽉 채우기
                // cameraActive(YouTube/카메라)일 때 youtubeScale 적용, 이미지 파일은 항상 꽉채우기
                double scale = cameraActive
				? scaleMin + (scaleMax - scaleMin) * state.youtubeScale
				: scaleMax;
                double drawW = imgW * scale;
                double drawH = imgH * scale;
                double drawX = (texSize - drawW) * 0.5;
                double drawY = (texSize - drawH) * 0.5;

                // 카메라 프레임 반전 보정: cameraFlipH(좌우), cameraFlipV(상하) 플래그에 따라 처리.
                // 파일 이미지(슬라이드쇼 등)는 그대로 그린다.
                if (cameraActive && (cameraFlipH || cameraFlipV)) {
                    double tx = cameraFlipH ? texSize : 0;
                    double ty = cameraFlipV ? texSize : 0;
                    double sx = cameraFlipH ? -1 : 1;
                    double sy = cameraFlipV ? -1 : 1;
                    gc.translate(tx, ty);
                    gc.scale(sx, sy);
				}
                gc.drawImage(state.backgroundImage, drawX, drawY, drawW, drawH);
                gc.restore();

                javafx.scene.SnapshotParameters sp = new javafx.scene.SnapshotParameters();
                sp.setFill(javafx.scene.paint.Color.TRANSPARENT);
                texImg = texCanvas.snapshot(sp, null);

                cachedSnapshotSource = state.backgroundImage;
                cachedTexSnapshot    = texImg;

                // [NPE Fix] Material 객체 재생성 금지 — setDiffuseMap() 으로 텍스처만 교체.
                // cachedImgMaterial = null 로 날리면 QuantumRenderer 가 들고 있던
                // 참조가 무효화되어 UploadingPainter NPE 가 발생한다.
                if (cachedImgMaterial == null) {
                    cachedImgMaterial = new PhongMaterial();
                    cachedImgMaterial.setDiffuseColor(Color.WHITE);
                    cachedImgMaterial.setSpecularColor(Color.color(0.08, 0.08, 0.08));
                    cachedImgMaterial.setSpecularPower(4);
				}
                cachedImgMaterial.setDiffuseMap(texImg);
			}

            // [NPE Fix] texturedFaceView 는 최초 1회만 생성·coinGroup 추가.
            // 이후 슬라이드쇼 전환 시에는 절대 coinGroup 에서 제거하거나 교체하지 않는다.
            // QuantumRenderer 가 업로드 중인 노드를 Application Thread 가 교체하면 NPE.
            if (texturedFaceView == null) {
                double r    = AppState.BASE_COIN_RADIUS * 0.990;
                double zOff = -(AppState.BASE_COIN_HEIGHT * 0.5 + 0.45);
                texturedFaceView = MeshFactory.makeTexturedDisk(r, AppState.SEGS, zOff);
                texturedFaceView.setCullFace(CullFace.NONE);
                texturedFaceView.setMaterial(cachedImgMaterial);

                // faceView 바로 뒤에 삽입 (없으면 마지막에 추가)
                int idx = coinGroup.getChildren().indexOf(faceView);
                if (idx >= 0) {
                    coinGroup.getChildren().add(idx + 1, texturedFaceView);
					} else {
                    coinGroup.getChildren().add(texturedFaceView);
				}
			}

            faceView.setVisible(false);
            faceView.setOpacity(0.0);
            texturedFaceView.setVisible(frontFaceVisible);
            texturedFaceView.setOpacity(frontFaceVisible ? 1.0 : 0.0);
		}

        /**
			* 네온 점멸: 네온이 켜진 모든 Node 의 opacity 를 매 프레임 갱신.
			* DropShadow/Bloom Effect 가 걸린 Node 의 opacity 를 조정하면
			* Effect 출력(후광)도 함께 희미해지므로 자연스러운 점멸이 된다.
		*/
        void setNeonFlickerOpacity(double opacity) {
            // 네온 ON 파트만 opacity 조정. OFF 파트는 건드리지 않음.
            if (state.neonFace) {
                if (state.backgroundImage == null && faceView != null)
				faceView.setOpacity(opacity);
                else if (state.backgroundImage != null && texturedFaceView != null)
				texturedFaceView.setOpacity(opacity);
			}
            if (state.neonBack && backView != null)
			backView.setOpacity(opacity);
            if (state.neonRim) {
                if (coinBodyView != null) coinBodyView.setOpacity(opacity);
                if (capRingView  != null) capRingView.setOpacity(opacity);
                if (frontBevelView != null) frontBevelView.setOpacity(opacity);
			}
            if (handsGroup != null && handsGroup.getChildren().size() >= 3) {
                if (state.neonHourHand)   handsGroup.getChildren().get(0).setOpacity(opacity);
                if (state.neonMinuteHand) handsGroup.getChildren().get(1).setOpacity(opacity);
                if (state.neonSecondHand) handsGroup.getChildren().get(2).setOpacity(opacity);
			}
            if (tickGroup != null && (state.neonFiveMinuteTick || state.neonOneMinuteTick)) {
                // [BugFix6] 인덱스 기반 판별 대신 UserData("HOUR"/"MINUTE")로 구분
                for (javafx.scene.Node child : tickGroup.getChildren()) {
                    boolean isHour = "HOUR".equals(child.getUserData());
                    if (isHour  && state.neonFiveMinuteTick) child.setOpacity(opacity);
                    if (!isHour && state.neonOneMinuteTick)  child.setOpacity(opacity);
				}
			}
            // [BugFix1] numbersGroup 전체에 setOpacity()를 걸면 Group 레벨 opacity가
            // selfIlluminationMap/Effect 위에 덮어씌워져 숫자 1~12가 모두 흐릿/투명해지는 버그.
            // 각 숫자 Node에 개별 opacity를 적용해야 점멸이 올바르게 동작한다.
            if (state.neonNumber && numbersGroup != null) {
                for (javafx.scene.Node child : numbersGroup.getChildren()) {
                    child.setOpacity(opacity);
				}
			}
		}

        void setHandAngles(double hrAngle, double minAngle, double secAngle) {
            if (hourRotation != null) hourRotation.setAngle(hrAngle);
            if (minuteRotation != null) minuteRotation.setAngle(minAngle);
            if (secondRotation != null) secondRotation.setAngle(secAngle);
		}

        void rebuildMaterialsAndScene() {
            buildMaterials();
            buildAll();
            applyNeonEffects();   // 재빌드 후 네온 상태 복원
		}

        void rebuildAllGeometry() {
            applyGeometryScale();
            applyVisibilityState();
		}

        void applyGeometryScale() {
            double scale = state.coinRadius / AppState.BASE_COIN_RADIUS;
            coinScale.setX(scale);
            coinScale.setY(scale);
            coinScale.setZ(scale);
            // Bug9: 코인 스케일이 변경될 때 faceDateTimeGroup의 Z 좌표를
            // 스케일-독립적인 절대값으로 재계산해야 한다.
            // coinGroup 에 coinScale(Scale 변환)이 걸려 있으므로
            // faceDateBox/faceTimeBox 의 translateZ 는 스케일 적용 전 로컬 좌표다.
            // numberFrontZ() 를 다시 호출해 최신 numberHeightScale 기반 Z 를 재적용한다.
            updateFaceDateTimeZ();
		}

        // ════════════════════════════════════════════════════════════
        //  페이스 날짜/시간 — 코인 앞면에 직접 새겨진 3D 텍스트 패널
        // ════════════════════════════════════════════════════════════

        /**
			* 숫자(1~12) 돌출 높이(numberHeightScale)에 맞춰
			* faceDateTimeGroup 의 Z 위치를 계산.
			*
			* 숫자 노드 배치 Z = -(BASE_COIN_HEIGHT/2 + 7.2)
			* 돌출 두께 = max(1, fontSize * EXTRUDE_THICKNESS_RATIO * scale)
			* → 앞면 Z = 배치Z - halfThickness
			* faceDateTimeBox 는 그보다 1.0 단위 앞에 배치.
		*/
        private double numberFrontZ() {
            double fontSize  = AppState.BASE_COIN_RADIUS * AppState.NUMBER_FONT_SIZE_RATIO;
            double thickness = Math.max(1.0, fontSize * 0.18 * state.numberHeightScale);
            return -(AppState.BASE_COIN_HEIGHT * 0.5 + 7.2 + thickness * 0.5 + 1.0);
		}

        /** 날짜/시분초 디지탈 패널은 손 네온보다 앞, 베젤보다 뒤에 둔다. */
        private double digitalFrontZ() {
            return -20.15;
		}

        /**
			* 코인 앞면에 날짜(상단)·시간(하단)을 표시하는 두 개의 Box를 생성.
			* buildAll() / numberHeightScale 변경 시 호출. coinGroup 에 추가.
		*/
        void buildFaceDateTimeGroup() {
            coinGroup.getChildren().remove(faceDateTimeGroup);
            faceDateTimeGroup.getChildren().clear();

            if (!state.showDigital && !state.showFaceDate) return;

            double r = AppState.BASE_COIN_RADIUS;
            // 텍스트 패널 Z: 시계 앞면(-9.45)보다 앞에 배치해 가려지지 않게 함
            final double textPanelZ = digitalFrontZ();

            double bW    = r * 0.90;
            double dateH = r * 0.18;
            double timeH = r * 0.22;
            double dateY = -r * 0.22;
            double timeY =  r * 0.22;

            // ── 날짜 텍스처 패널 ──────────────────────────────────────────
            int dtW = 512, dtH = 80;
            faceDateTexImg = new WritableImage(dtW, dtH);
            faceDateMat    = new PhongMaterial();
            faceDateMat.setDiffuseMap(faceDateTexImg);
            faceDateMat.setDiffuseColor(javafx.scene.paint.Color.WHITE);
            faceDateMat.setSpecularColor(javafx.scene.paint.Color.color(0.04, 0.04, 0.04));
            faceDateMat.setSpecularPower(2);

            faceDateBox = new Box(bW, dateH, 0.1);
            faceDateBox.setMaterial(faceDateMat);
            faceDateBox.setTranslateX(0);
            faceDateBox.setTranslateY(dateY);
            faceDateBox.setTranslateZ(textPanelZ);
            faceDateBox.setMouseTransparent(true);

            // ── 시분초 텍스처 패널 ────────────────────────────────────────
            int tmW = 512, tmH = 96;
            faceTimeTexImg = new WritableImage(tmW, tmH);
            faceTimeMat    = new PhongMaterial();
            faceTimeMat.setDiffuseMap(faceTimeTexImg);
            faceTimeMat.setDiffuseColor(javafx.scene.paint.Color.WHITE);
            faceTimeMat.setSpecularColor(javafx.scene.paint.Color.color(0.04, 0.04, 0.04));
            faceTimeMat.setSpecularPower(2);

            faceTimeBox = new Box(bW, timeH, 0.1);
            faceTimeBox.setMaterial(faceTimeMat);
            faceTimeBox.setTranslateX(0);
            faceTimeBox.setTranslateY(timeY);
            faceTimeBox.setTranslateZ(textPanelZ);
            faceTimeBox.setMouseTransparent(false);

            faceDateTimeGroup.getChildren().addAll(faceDateBox, faceTimeBox);
            faceDateTimeGroup.setVisible(state.showDigital || state.showFaceDate);
            coinGroup.getChildren().add(faceDateTimeGroup);

            if (faceTimeClickCallback != null) setFaceTimeClickHandler(faceTimeClickCallback);

            state.faceScrollOffset     = Double.NaN;
            state.facePingPongDir      = 1;
            state.faceDateScrollOffset = Double.NaN;
            state.faceDatePingPongDir  = 1;

            updateFaceDateTimeTextures();
		}

        /**
			* 디지탈 OFF 시 faceDateTimeGroup 의 모든 자식을 제거하고
			* coinGroup 에서도 분리한다. 렌더링 비용 완전 제거.
			* ON 복구는 buildFaceDateTimeGroup() 재호출.
		*/
        void removeDigitalGroup() {
            faceDateTimeGroup.getChildren().clear();
            coinGroup.getChildren().remove(faceDateTimeGroup);
            faceDateBox = null;  faceTimeBox = null;
            faceDateTexImg = null; faceTimeTexImg = null;
            faceDateMat = null;  faceTimeMat = null;
            _dateCachedCanvas = null;
            _timeCachedCanvas = null;
		}

        /**
			* 숫자 높이 변경 시 Z만 갱신 (buildFaceDateTimeGroup 전체 재빌드 없이).
			* rebuildNumbers() 끝에서 호출.
		*/
        void updateFaceDateTimeZ() {
            if (faceDateBox == null || faceTimeBox == null) return;
            final double textPanelZ = digitalFrontZ();
            faceDateBox.setTranslateZ(textPanelZ);
            faceTimeBox.setTranslateZ(textPanelZ);
		}

        /** 매 프레임 날짜·시간 텍스처 갱신. AnimationTimer 에서 호출. */
        void updateFaceDateTimeTextures() {
            // 날짜 또는 시간 중 하나라도 켜져 있어야 진입
            if (!state.showDigital && !state.showFaceDate) return;
            if (faceDateTexImg == null || faceTimeTexImg == null) return;
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(state.theTimeZone);
            String[] wd = {"일","월","화","수","목","금","토"};
            String dow = wd[now.getDayOfWeek().getValue() % 7];
            int h24  = now.getHour();
            int h12  = h24 % 12 == 0 ? 12 : h24 % 12;
            String ampm = h24 < 12 ? "오전" : "오후";
            // ── 날짜 행 (상단) ───────────────────────────────────────
            faceDateBox.setVisible(state.showFaceDate);
            if (state.showFaceDate) {
                String dateStr;
                switch (state.faceDateFormatIndex) {
                    case 1  -> dateStr = String.format("%d-%02d-%02d (%s)",
					now.getYear(), now.getMonthValue(), now.getDayOfMonth(), dow);
                    case 2  -> dateStr = String.format("%02d/%02d (%s)",
					now.getMonthValue(), now.getDayOfMonth(), dow);
                    case 3  -> dateStr = String.format("%d월 %d일",
					now.getMonthValue(), now.getDayOfMonth());
                    default -> dateStr = String.format("%d월 %d일 (%s)",
					now.getMonthValue(), now.getDayOfMonth(), dow);
				}
                // 세계시계 도시명 prefix
                if (state.cityPrefix != null && !state.cityPrefix.isEmpty())
				dateStr = "(" + state.cityPrefix + ") " + dateStr;
                int drgb = state.faceDateColorRgb;
                javafx.scene.paint.Color dcol = javafx.scene.paint.Color.rgb(
                    (drgb >> 16) & 0xFF, (drgb >> 8) & 0xFF, drgb & 0xFF,
				((drgb >> 24) & 0xFF) / 255.0);
                renderFaceText(faceDateTexImg, dateStr, dcol,
                    state.faceDateFontFamily, state.faceDateFontSize > 0
				? state.faceDateFontSize : (int) faceDateTexImg.getHeight() * 0.68);
			}
            // ── 시간 행 (하단) ───────────────────────────────────────
            faceTimeBox.setVisible(state.showDigital);
            if (state.showDigital) {
                String timeStr;
                switch (state.digitalFormatIndex) {
                    case 1  -> timeStr = String.format("%02d:%02d %s [%s]", h24, now.getMinute(), ampm, dow);
                    case 2  -> timeStr = String.format("%02d:%02d [%s]", h24, now.getMinute(), ampm);
                    case 3  -> timeStr = String.format("%02d:%02d:%02d", h24, now.getMinute(), now.getSecond());
                    default -> timeStr = String.format("%02d:%02d:%02d [%s]", h24, now.getMinute(), now.getSecond(), ampm);
				}
                if (state.cityPrefix != null && !state.cityPrefix.isEmpty())
				timeStr = timeStr + " (" + state.cityPrefix + ")";
				int trgb = state.digitalColorRgb;
                javafx.scene.paint.Color tcol = javafx.scene.paint.Color.rgb(
                    (trgb >> 16) & 0xFF, (trgb >> 8) & 0xFF, trgb & 0xFF,
				((trgb >> 24) & 0xFF) / 255.0);
                renderFaceTimeScrolled(faceTimeTexImg, timeStr, tcol);
			}
		}
        private double computeBlinkOpacity(AppState.NeonBlinkStyle style) {
            switch (style) {
                case NONE -> { return 1.0; }
                case PULSE -> {
                    state.neonFlickerPhase += state.neonFlickerSpeed * (2.0 * Math.PI / 60.0);
                    if (state.neonFlickerPhase >= 2.0 * Math.PI) state.neonFlickerPhase -= 2.0 * Math.PI;
                    double wave = 0.5 + 0.5 * Math.sin(state.neonFlickerPhase);
                    return AppState.clamp(1.0 - state.neonFlickerDepth * (1.0 - wave), 0.0, 1.0);
				}
                case SHARP -> {
                    state.neonFlickerPhase += state.neonFlickerSpeed * (2.0 * Math.PI / 60.0);
                    if (state.neonFlickerPhase >= 2.0 * Math.PI) state.neonFlickerPhase -= 2.0 * Math.PI;
                    double norm = state.neonFlickerPhase / (2.0 * Math.PI);
                    return (norm < 0.70) ? 1.0 : AppState.clamp(1.0 - state.neonFlickerDepth, 0.0, 1.0);
				}
                case RANDOM -> {
                    if (state.neonRandomCounter <= 0) {
                        state.neonRandomOn = !state.neonRandomOn;
                        int base = (int) (60.0 / Math.max(0.2, state.neonFlickerSpeed));
                        if (state.neonRandomOn) state.neonRandomCounter = base + (int) (Math.random() * base);
                        else state.neonRandomCounter = Math.max(1, base / 4 + (int) (Math.random() * base / 4));
					}
                    state.neonRandomCounter--;
                    return state.neonRandomOn ? 1.0 : AppState.clamp(1.0 - state.neonFlickerDepth, 0.0, 1.0);
				}
                default -> { return 1.0; }
			}
		}

        private javafx.scene.paint.Color neonCoreColor(javafx.scene.paint.Color base) {
            double maxc = Math.max(base.getRed(), Math.max(base.getGreen(), base.getBlue()));
            if (maxc < 0.35) {
                // 아주 어두운 색(#003333 등)은 그대로 쓰면 네온이 거의 안 보인다.
                return javafx.scene.paint.Color.color(
                    Math.min(1.0, base.getRed()   * 0.35 + 0.70),
                    Math.min(1.0, base.getGreen() * 0.35 + 0.88),
                    Math.min(1.0, base.getBlue()  * 0.35 + 0.88),
                    1.0
				);
			}
            return javafx.scene.paint.Color.color(
                Math.min(1.0, base.getRed()   * 0.55 + 0.52),
                Math.min(1.0, base.getGreen() * 0.55 + 0.52),
                Math.min(1.0, base.getBlue()  * 0.55 + 0.52),
                1.0
			);
		}

        private javafx.scene.paint.Color mixColor(
			javafx.scene.paint.Color a,
			javafx.scene.paint.Color b,
			double t,
			double alpha) {
            double u = AppState.clamp(t, 0.0, 1.0);
            return javafx.scene.paint.Color.color(
                a.getRed()   * (1.0 - u) + b.getRed()   * u,
                a.getGreen() * (1.0 - u) + b.getGreen() * u,
                a.getBlue()  * (1.0 - u) + b.getBlue()  * u,
                alpha
			);
		}

        private void drawFaceTextWithOptionalNeon(
			javafx.scene.canvas.GraphicsContext gc,
			String text,
			double x,
			double y,
			javafx.scene.text.TextAlignment align,
			javafx.scene.paint.Color color,
			boolean neonOn) {
            gc.setTextAlign(align);

            if (!neonOn) {
                gc.setFill(color);
                gc.fillText(text, x, y);
                return;
			}

            double blink = computeBlinkOpacity(state.digitalNeonBlinkStyle);
            javafx.scene.paint.Color core = neonCoreColor(color);

            // 네온이 켜지면 중심 텍스트 자체도 밝아져야 점멸이 눈에 들어온다.
            double fillMix = 0.58 + 0.42 * blink;
            javafx.scene.paint.Color fillColor = mixColor(color, core, fillMix, 1.0);
            javafx.scene.paint.Color lineColor = mixColor(color, core, 0.82 + 0.18 * blink, 1.0);

            javafx.scene.effect.DropShadow outerGlow = new javafx.scene.effect.DropShadow(
                javafx.scene.effect.BlurType.GAUSSIAN,
                javafx.scene.paint.Color.color(core.getRed(), core.getGreen(), core.getBlue(), 0.42 * blink),
                5.5,
                0.35,
                0, 0
			);
            javafx.scene.effect.DropShadow innerGlow = new javafx.scene.effect.DropShadow(
                javafx.scene.effect.BlurType.GAUSSIAN,
                javafx.scene.paint.Color.color(core.getRed(), core.getGreen(), core.getBlue(), 0.90 * blink),
                2.2,
                0.18,
                0, 0
			);
            innerGlow.setInput(outerGlow);

            gc.setEffect(innerGlow);
            gc.setStroke(lineColor);
            gc.setLineWidth(0.95);
            gc.strokeText(text, x, y);
            gc.setFill(fillColor);
            gc.fillText(text, x, y);
            gc.setEffect(null);

            // 코어 라인을 한 번 더 얇게 덮어 네온 튜브 중심선을 또렷하게 만든다.
            gc.setStroke(mixColor(core, javafx.scene.paint.Color.WHITE, 0.35, 0.95));
            gc.setLineWidth(0.36);
            gc.strokeText(text, x, y);
            gc.setFill(fillColor);
            gc.fillText(text, x, y);
		}

        // Bug4: renderFaceText / renderFaceTimeScrolled 에서 매 프레임 Canvas 를 생성하면
        // GC 부담이 크다. 크기가 동일하면 같은 Canvas 인스턴스를 재사용한다.
        private javafx.scene.canvas.Canvas _dateCachedCanvas  = null;
        private javafx.scene.canvas.Canvas _timeCachedCanvas  = null;

        /**
			* 날짜 행 — Bug7: 스크롤 지원 (state.faceDateScrollDir 사용).
			*   0=고정(중앙), 1=우→좌, 2=좌→우, 3=핑퐁
			* Bug4: Canvas 재사용.
		*/
        private void renderFaceText(WritableImage target, String text,
			javafx.scene.paint.Color col,
			String fontFamily, double fontSize) {
            int W = (int) target.getWidth(), H = (int) target.getHeight();
			int WW = W + W ;
            if (_dateCachedCanvas == null
				|| (int) _dateCachedCanvas.getWidth()  != W
				|| (int) _dateCachedCanvas.getHeight() != H) {
                _dateCachedCanvas = new javafx.scene.canvas.Canvas(W, H);
			}
            javafx.scene.canvas.Canvas c = _dateCachedCanvas;
            javafx.scene.canvas.GraphicsContext gc = c.getGraphicsContext2D();
            gc.clearRect(0, 0, W, H);
            // 음각 효과는 3D 벽이 담당 → 텍스처는 투명 배경에 글자만

            gc.setFont(javafx.scene.text.Font.font(fontFamily, FontWeight.BOLD, fontSize));
            gc.setTextBaseline(VPos.CENTER);

            // ── X 위치 계산 (스크롤 처리) ────────────────────────────
            double drawX;
            if (state.faceDateScrollDir == 0) {
                drawX = W / 2.0;
                gc.setTextAlign(TextAlignment.CENTER);
				} else {
                javafx.scene.text.Text m = new javafx.scene.text.Text(text);
                m.setFont(gc.getFont());
                double tw = m.getLayoutBounds().getWidth();
                if (state.faceDateScrollDir == 3) {
                    if (Double.isNaN(state.faceDateScrollOffset)) state.faceDateScrollOffset = 0;
                    drawX = state.faceDateScrollOffset;
                    state.faceDateScrollOffset += state.faceDatePingPongDir * state.faceDateScrollSpeed * 0.5;
					// 오른쪽으로 진행 중: 글자가 완전히 오른쪽 밖으로 사라진 뒤 반전
					if (state.faceDatePingPongDir > 0 && state.faceDateScrollOffset > W) {
						state.faceDateScrollOffset = W;
						state.faceDatePingPongDir = -1;
					}
					// 왼쪽으로 진행 중: 글자가 완전히 왼쪽 밖으로 사라진 뒤 반전
					if (state.faceDatePingPongDir < 0 && state.faceDateScrollOffset + tw < 0) {
						state.faceDateScrollOffset = -tw;
						state.faceDatePingPongDir = 1;
					}
					/*
						if (state.faceDateScrollOffset + tw > W) { state.faceDateScrollOffset = W - tw; state.faceDatePingPongDir = -1; }
						if (state.faceDateScrollOffset < 0)       { state.faceDateScrollOffset = 0;     state.faceDatePingPongDir =  1; }
					*/
					} else {
                    if (Double.isNaN(state.faceDateScrollOffset))
					state.faceDateScrollOffset = (state.faceDateScrollDir == 1) ? W : -tw;
                    drawX = state.faceDateScrollOffset;
                    if (state.faceDateScrollDir == 1) {
                        state.faceDateScrollOffset -= state.faceDateScrollSpeed * 0.5;
                        if (state.faceDateScrollOffset + tw < 0) state.faceDateScrollOffset = W;
						} else {
                        state.faceDateScrollOffset += state.faceDateScrollSpeed * 0.5;
                        if (state.faceDateScrollOffset > W) state.faceDateScrollOffset = -tw;
					}
				}
                gc.setTextAlign(TextAlignment.LEFT);
			}

            // 얇은 네온 + 본문 렌더링
            drawFaceTextWithOptionalNeon(
                gc, text, drawX, H / 2.0,
                state.faceDateScrollDir == 0 ? javafx.scene.text.TextAlignment.CENTER : javafx.scene.text.TextAlignment.LEFT,
                col,
                state.neonFaceDate
			);

            javafx.scene.SnapshotParameters sp = new javafx.scene.SnapshotParameters();
            sp.setFill(javafx.scene.paint.Color.TRANSPARENT);
            c.snapshot(sp, target);
		}

        /**
			* 시간 행 — 스크롤 렌더링, 그림자 없음.
			* state.digitalScrollDir / digitalScrollSpeed / faceScrollOffset / facePingPongDir 사용.
			*
			* Bug3 수정: 방향 1(우→좌) / 2(좌→우) 에서 텍스트가 완전히 화면 밖으로 나가는
			* 순간 오프셋을 리셋할 때 texW (또는 -tw) 로 설정하면 한 프레임 동안 텍스트가
			* 보이지 않는다. texW + tw (또는 -tw - padding) 로 초기화해 연속성을 보장한다.
		*/
        private void renderFaceTimeScrolled(WritableImage target, String text,
			javafx.scene.paint.Color col) {
            int texW = (int) target.getWidth();
            int texH = (int) target.getHeight();

            if (_timeCachedCanvas == null
				|| (int) _timeCachedCanvas.getWidth()  != texW
				|| (int) _timeCachedCanvas.getHeight() != texH) {
                _timeCachedCanvas = new javafx.scene.canvas.Canvas(texW, texH);
			}
            javafx.scene.canvas.Canvas c = _timeCachedCanvas;
            javafx.scene.canvas.GraphicsContext gc = c.getGraphicsContext2D();
            gc.clearRect(0, 0, texW, texH);
            // 음각 효과는 3D 벽이 담당 → 텍스처는 투명 배경에 글자만

            double fs = Math.max(10, state.digitalFontSize > 0
			? state.digitalFontSize : texH * 0.7);
            gc.setFont(Font.font(state.digitalFontFamily, FontWeight.BOLD, fs));
            gc.setTextBaseline(VPos.CENTER);

            // ── X 위치 계산 (스크롤 처리) ────────────────────────────
            javafx.scene.text.Text m = new javafx.scene.text.Text(text);
            m.setFont(gc.getFont());
            double tw = m.getLayoutBounds().getWidth();
            double drawX;

            if (state.digitalScrollDir == 0) {
                drawX = texW / 2.0;
                gc.setTextAlign(TextAlignment.CENTER);
				} else if (state.digitalScrollDir == 3) {
                if (Double.isNaN(state.faceScrollOffset)) state.faceScrollOffset = 0;
                drawX = state.faceScrollOffset;
                state.faceScrollOffset += state.facePingPongDir * state.digitalScrollSpeed * 0.5;

				// 👉 오른쪽 완전 이탈 후 반사
				if (state.facePingPongDir > 0 && state.faceScrollOffset > texW) {
					state.faceScrollOffset = texW;
					state.facePingPongDir = -1;
				}
				// 👉 왼쪽 완전 이탈 후 반사
				if (state.facePingPongDir < 0 && state.faceScrollOffset + tw < 0) {
					state.faceScrollOffset = -tw;
					state.facePingPongDir = 1;
				}
				/*
					if (state.faceScrollOffset + tw > texW) { state.faceScrollOffset = texW - tw; state.facePingPongDir = -1; }
					if (state.faceScrollOffset < 0)          { state.faceScrollOffset = 0;         state.facePingPongDir =  1; }
				*/
                gc.setTextAlign(TextAlignment.LEFT);
				} else {
                if (Double.isNaN(state.faceScrollOffset))
				state.faceScrollOffset = (state.digitalScrollDir == 1) ? texW : -tw;
                drawX = state.faceScrollOffset;
                if (state.digitalScrollDir == 1) {
                    state.faceScrollOffset -= state.digitalScrollSpeed * 0.5;
                    if (state.faceScrollOffset + tw < 0) state.faceScrollOffset = texW;
					} else {
                    state.faceScrollOffset += state.digitalScrollSpeed * 0.5;
                    if (state.faceScrollOffset > texW) state.faceScrollOffset = -tw;
				}
                gc.setTextAlign(TextAlignment.LEFT);
			}

            // 얇은 네온 + 본문 렌더링
            drawFaceTextWithOptionalNeon(
                gc, text, drawX, texH / 2.0,
                state.digitalScrollDir == 0 ? javafx.scene.text.TextAlignment.CENTER : javafx.scene.text.TextAlignment.LEFT,
                col,
                state.neonFaceTime
			);

            javafx.scene.SnapshotParameters sp = new javafx.scene.SnapshotParameters();
            sp.setFill(javafx.scene.paint.Color.TRANSPARENT);
            c.snapshot(sp, target);
		}

        /** faceTimeBox 더블클릭 핸들러 등록 (설정 다이얼로그 열기). ClockController 에서 주입. */
        private Runnable faceTimeClickCallback = null;
        void setFaceTimeClickHandler(Runnable onDoubleClick) {
            this.faceTimeClickCallback = onDoubleClick;
            if (faceTimeBox == null || faceTimeClickCallback == null) return;
            faceTimeBox.setMouseTransparent(false);
            faceTimeBox.setOnMouseClicked(e -> {
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY
					&& e.getClickCount() == 2) {
                    faceTimeClickCallback.run();
				}
			});
		}

        void applyVisibilityState() {
            // ── 투명 모드: 앞면·본체·뒷면·유리 숨김, 베젤·바늘·눈금·숫자는 유지 ──
			if (state.transparentMode) {
				if (backTextGroup != null) backTextGroup.setVisible(false);
			}
			if (state.transparentMode) {
                if (coinBodyView != null) coinBodyView.setVisible(false);
                if (faceView != null) {
                    faceView.setVisible(false);
                    faceView.setOpacity(0.0);
				}
                if (backView != null) backView.setVisible(false);
                if (texturedFaceView != null) {
                    texturedFaceView.setVisible(false);
                    texturedFaceView.setOpacity(0.0);
				}
                if (glassView != null) {
                    glassView.setVisible(false);
                    glassView.setOpacity(0.0);
				}
                // 투명 모드에서는 앞/뒤 면과 유리 관련 보조층도 모두 숨긴다.
                if (imageLayerGroup != null) imageLayerGroup.setVisible(false);
                if (crystalGroup != null) crystalGroup.setVisible(false);
                // Bug1: 투명 모드에서 faceDateTimeGroup 도 숨긴다.
                faceDateTimeGroup.setVisible(false);

                if (handsGroup    != null) handsGroup.setVisible(!state.interactiveResizing);
                if (numbersGroup  != null) numbersGroup.setVisible(state.showNumbers && !state.interactiveResizing);
                if (tickGroup     != null) tickGroup.setVisible(!state.interactiveResizing);
                if (capRingView   != null) capRingView.setVisible(!state.interactiveResizing);
                if (frontBevelView != null) frontBevelView.setVisible(!state.interactiveResizing);
                return;
			}
            if (coinBodyView != null) coinBodyView.setVisible(!state.interactiveResizing);
            if (backView != null) backView.setVisible(!state.interactiveResizing);
            if (imageLayerGroup != null) imageLayerGroup.setVisible(true);
            if (numbersGroup != null) numbersGroup.setVisible(state.showNumbers && !state.interactiveResizing);
            if (faceView != null) {
                faceView.setVisible(frontFaceVisible && state.backgroundImage == null && !state.interactiveResizing);
                faceView.setOpacity(frontFaceVisible && state.backgroundImage == null ? 1.0 : 0.0);
			}
            if (texturedFaceView != null) {
                texturedFaceView.setVisible(frontFaceVisible && state.backgroundImage != null && !state.interactiveResizing);
                texturedFaceView.setOpacity(frontFaceVisible && state.backgroundImage != null ? 1.0 : 0.0);
			}
            if (glassView != null) glassView.setVisible(state.showGlass && !state.interactiveResizing && state.backgroundImage == null);
            applyCrystalVisibility();
            if (tickGroup != null) tickGroup.setVisible(!state.interactiveResizing);
            if (capRingView != null) capRingView.setVisible(!state.interactiveResizing);
            if (frontBevelView != null) frontBevelView.setVisible(!state.interactiveResizing);
		}

        /**
			* [NEON Fix3 & Fix4] 네온 효과 적용 — 개별 Node Effect 방식.
			*
			* selfIlluminationMap 만으로는 표면이 자체 발광하지만 외곽 번짐이 없다.
			* SubScene 전체 Bloom 은 금색 표면도 같이 번지는 부작용이 있으므로 폐기.
			*
			* 수정 방식:
			*   1) PhongMaterial.selfIlluminationMap → 표면 자체발광 유지 (어두운 배경에서 보임)
			*   2) 각 대상 Node 에 직접 Bloom + DropShadow(색상 후광) Effect 체인 적용
			*      → 해당 파트만 번지고, 금색 시계 본체는 영향 없음
			*
			* [Fix3] 배경 이미지가 있으면 faceView 가 숨겨지므로 neonFace 는 스킵.
			* [Fix4] frontBevelView 는 이제 materials.rim 을 공유하므로
			*         applyNeonToMaterial(materials.rim) 한 번으로 자동 반영됨.
			*         별도 applyNeonToShape(frontBevelView) 호출 제거.
		*/
        void applyNeonEffects() {
            // ── 재질 selfIlluminationMap ──────────────────────────────────
            // [Fix3] 배경 이미지 ON 시 faceView 숨김 → neonFace 재질 적용 스킵
            if (state.backgroundImage == null) {
                applyNeonToMaterial(materials.face, state.neonFace, state.faceColor);
				} else {
                applyNeonToMaterial(materials.face, false, state.faceColor);
			}
            applyNeonToMaterial(materials.back,       state.neonBack,           state.backColor);
            applyNeonToMaterial(materials.rim,        state.neonRim,            state.rimColor);
            applyNeonToMaterial(materials.hour,       state.neonHourHand,       state.hourHandColor);
            applyNeonToMaterial(materials.minute,     state.neonMinuteHand,     state.minuteHandColor);
            applyNeonToMaterial(materials.second,     state.neonSecondHand,     state.secondHandColor);
            applyNeonToMaterial(materials.tickHour,   state.neonFiveMinuteTick, state.fiveMinuteTickColor);
            applyNeonToMaterial(materials.tickMinute, state.neonOneMinuteTick,  state.oneMinuteTickColor);
            // 숫자는 재질을 공유하지 않으므로 재귀 적용
            applyNeonToGroup(numbersGroup, state.neonNumber, state.numberColor);

            // ── 개별 Node Effect (Bloom + DropShadow 후광) ───────────────
            // [Fix4] frontBevelView 는 materials.rim 공유 → selfIlluminationMap 자동 반영.
            //        Node Effect 는 별도로 필요하므로 coinBodyView/faceView/backView 등
            //        각 파트 대표 Node 에 직접 적용한다.
            applyNeonEffectToNode(coinBodyView,    state.neonRim,            state.rimColor);
            // 배경 이미지 없으면 faceView, 있으면 texturedFaceView에 네온 적용
            applyNeonEffectToNode(faceView,
			state.neonFace && state.backgroundImage == null, state.faceColor);
            if (state.backgroundImage != null && texturedFaceView != null)
			applyNeonEffectToNode(texturedFaceView, state.neonFace, state.faceColor);
            applyNeonEffectToNode(backView,        state.neonBack,           state.backColor);
            applyNeonEffectToNode(capRingView,     state.neonRim,            state.rimColor);
            applyNeonEffectToNode(frontBevelView,  state.neonRim,            state.rimColor);
            // 침은 개별 Group 별로 따로 처리 (시침/분침/초침 색이 다를 수 있음)
            if (handsGroup.getChildren().size() >= 3) {
                applyNeonEffectToNode(handsGroup.getChildren().get(0), state.neonHourHand,   state.hourHandColor);
                applyNeonEffectToNode(handsGroup.getChildren().get(1), state.neonMinuteHand, state.minuteHandColor);
                applyNeonEffectToNode(handsGroup.getChildren().get(2), state.neonSecondHand, state.secondHandColor);
			}
            // [BugFix6] 눈금은 5분/1분이 섞여 있으므로 각 Node 별로 개별 처리.
            // 인덱스 기반 판별(tickIdx%5) 대신 UserData("HOUR"/"MINUTE")로 안전하게 구분.
            if (tickGroup != null) {
                for (javafx.scene.Node child : tickGroup.getChildren()) {
                    boolean isHourTick = "HOUR".equals(child.getUserData());
                    if (isHourTick) {
                        applyNeonEffectToNode(child, state.neonFiveMinuteTick, state.fiveMinuteTickColor);
						} else {
                        applyNeonEffectToNode(child, state.neonOneMinuteTick, state.oneMinuteTickColor);
					}
				}
			}
            applyNeonEffectToGroup(numbersGroup, state.neonNumber, state.numberColor);
		}

        /** PhongMaterial 에 직접 selfIlluminationMap 적용/해제 */
        private static void applyNeonToMaterial(PhongMaterial mat, boolean on, Color baseColor) {
            if (mat == null) return;
            mat.setSelfIlluminationMap(on ? makeGlowTexture(deriveNeonColor(baseColor)) : null);
		}

        /** [NEON Fix] SubScene Bloom 제거로 이 메서드는 더 이상 사용되지 않음. 하위 호환용으로 유지. */
        boolean isAnyNeonOn() {
            return state.neonFace || state.neonBack || state.neonRim
			|| state.neonHourHand || state.neonMinuteHand || state.neonSecondHand
			|| state.neonFiveMinuteTick || state.neonOneMinuteTick || state.neonNumber;
		}

        /** @deprecated frontBevelView 가 materials.rim 을 공유하므로 별도 호출 불필요. 미사용. */
        @Deprecated
        @SuppressWarnings("unused")
        private static void applyNeonToShape(Shape3D shape, boolean on, Color baseColor) {
            if (shape == null) return;
            javafx.scene.paint.Material mat = shape.getMaterial();
            if (!(mat instanceof PhongMaterial)) return;
            PhongMaterial pm = (PhongMaterial) mat;
            pm.setSelfIlluminationMap(on ? makeGlowTexture(deriveNeonColor(baseColor)) : null);
		}

        /**
			* [NEON Fix] 단일 Node 에 Bloom + DropShadow 후광 Effect 를 직접 적용/해제.
			* SubScene 전체 Bloom 대신 이 방식을 사용하면 해당 파트만 번진다.
			*
			* Effect 체인: DropShadow(네온 색, 넓은 반경) → Bloom(낮은 threshold)
			* DropShadow 가 외곽 후광을 만들고, Bloom 이 밝은 영역을 추가로 번지게 한다.
		*/
        private static void applyNeonEffectToNode(javafx.scene.Node node, boolean on, Color baseColor) {
            if (node == null) return;
            if (on) {
                Color neon = deriveNeonColor(baseColor);
                // DropShadow: 네온 색 후광, 반경 18px, 퍼짐 0.6
                javafx.scene.effect.DropShadow glow = new javafx.scene.effect.DropShadow(
				javafx.scene.effect.BlurType.GAUSSIAN, neon, 18, 0.6, 0, 0);
                // Bloom: threshold 0.0 → Node 자체 밝은 픽셀만 번짐 (주변 영향 없음)
                javafx.scene.effect.Bloom bloom = new javafx.scene.effect.Bloom(0.0);
                bloom.setInput(glow);
                node.setEffect(bloom);
				} else {
                node.setEffect(null);
                // [BugFix2] 네온 OFF 시 점멸로 낮아진 opacity를 반드시 1.0으로 복원.
                // setNeonFlickerOpacity()가 점멸 중 opacity를 낮춰 놓은 상태에서
                // 네온을 끄면 Effect만 제거되고 opacity가 잔류해 파트가 반투명으로 남는 버그 수정.
                node.setOpacity(1.0);
			}
		}

        /** Group 전체에 동일 네온 Effect 적용 (Group 레벨에 한 번만 걸면 하위 전체 적용됨) */
        private static void applyNeonEffectToGroup(Group group, boolean on, Color baseColor) {
            if (group == null) return;
            applyNeonEffectToNode(group, on, baseColor);
            // [BugFix1+2] 네온 OFF 시 점멸로 낮아진 자식 Node들의 opacity도 복원.
            // setNeonFlickerOpacity()가 자식 Node에 개별 opacity를 걸었으므로
            // 그룹 Effect 제거만으로는 자식 opacity가 복원되지 않는다.
            if (!on) {
                for (javafx.scene.Node child : group.getChildren()) {
                    child.setOpacity(1.0);
				}
			}
		}

        /** Group 전체 하위 Shape3D 재귀 적용 */
        private static void applyNeonToGroup(Group group, boolean on, Color baseColor) {
            if (group == null) return;
            javafx.scene.image.WritableImage tex = on ? makeGlowTexture(deriveNeonColor(baseColor)) : null;
            applyIllumRecursive(group, on, tex);
		}

        private static void applyIllumRecursive(javafx.scene.Node node, boolean on,
			javafx.scene.image.WritableImage tex) {
            if (node instanceof Shape3D) {
                javafx.scene.paint.Material mat = ((Shape3D) node).getMaterial();
                if (mat instanceof PhongMaterial) {
                    ((PhongMaterial) mat).setSelfIlluminationMap(on ? tex : null);
				}
				} else if (node instanceof Group) {
                for (javafx.scene.Node child : ((Group) node).getChildren()) {
                    applyIllumRecursive(child, on, tex);
				}
			}
		}

        /**
			* 단색 자체발광 텍스처 생성 (4×4 px 최소 크기).
			* selfIlluminationMap 은 흰색(1,1,1)이 완전 발광이므로
			* 네온 색을 그대로 넣으면 해당 색으로 표면이 빛난다.
		*/
        private static javafx.scene.image.WritableImage makeGlowTexture(Color c) {
            int S = 4;
            javafx.scene.image.WritableImage img = new javafx.scene.image.WritableImage(S, S);
            javafx.scene.image.PixelWriter pw = img.getPixelWriter();
            for (int y = 0; y < S; y++)
			for (int x = 0; x < S; x++)
			pw.setColor(x, y, c);
            return img;
		}

        private static Color deriveNeonColor(Color base) {
            // HSB 채도 MAX, 밝기 1.0 → 형광 네온 색
            return Color.hsb(base.getHue(), 1.0, 1.0, 1.0);
		}

        void setInteractiveResizing(boolean resizing) {
            if (state.interactiveResizing == resizing) return;
            state.interactiveResizing = resizing;
            if (resizing) {
                state.resizeSavedAutoSpeed = state.autoSpeed;
                state.autoSpeed = 0.0;
				} else {
                state.autoSpeed = state.paused ? 0.0 : state.resizeSavedAutoSpeed;
			}
            applyInteractiveResizeState();
		}

        void applyInteractiveResizeState() {
            boolean stable = state.interactiveResizing;
            if (coinBodyView != null) coinBodyView.setVisible(!stable);
            if (faceView != null) {
                faceView.setMaterial(stable ? materials.faceFlat : materials.face);
                // 배경 이미지가 있으면 faceView는 항상 숨김 (texturedFaceView가 대신 표시됨)
                faceView.setVisible(frontFaceVisible && state.backgroundImage == null && !stable);
			}
            if (texturedFaceView != null) {
                texturedFaceView.setVisible(frontFaceVisible && state.backgroundImage != null && !stable);
			}
            if (backView != null) {
                backView.setMaterial(stable ? materials.backFlat : materials.back);
                backView.setVisible(!stable);
			}
            // innerRingView, frontBowlView, backBowlView 제거됨 — 참조 제거
            if (tickGroup != null) tickGroup.setVisible(!stable);
            if (numbersGroup != null) numbersGroup.setVisible(state.showNumbers && !stable);
            if (capRingView != null) capRingView.setVisible(!stable);
            if (frontBevelView != null) frontBevelView.setVisible(!stable);
            if (glassView != null) {
                glassView.setVisible(state.showGlass && !stable && state.backgroundImage == null);
                glassView.setOpacity(stable ? 0.0 : (state.backgroundImage != null ? 0.0 : state.glassOpacity));
			}
            applyCrystalVisibility();
            // 인터랙티브 리사이징 종료 후 이미지 재질 복원
            if (!stable) applyBackgroundImage();
		}

        void rebuildNumbers() {
            coinGroup.getChildren().remove(numbersGroup);
            numbersGroup.getChildren().clear();
            if (state.showNumbers) {
                NumberFactory factory = new NumberFactory(state);
                double baseRadius = AppState.BASE_COIN_RADIUS * 0.70;
                double fontSize = AppState.BASE_COIN_RADIUS * AppState.NUMBER_FONT_SIZE_RATIO;
                for (int n = 1; n <= 12; n++) {
                    double angle = Math.toRadians(n * 30.0 - 90.0);
                    double x = Math.cos(angle) * baseRadius;
                    double y = Math.sin(angle) * baseRadius;
                    Group numberNode = factory.createNumber(String.valueOf(n), fontSize);
                    numberNode.setTranslateX(x);
                    numberNode.setTranslateY(y);
                    // [B1-Fix] numberHeightScale에 따라 두께가 달라지므로
                    // TranslateZ를 동적으로 계산. 숫자 중심 Z = -(COIN_H/2 + 7.2),
                    // 앞면 Z = 중심 - halfThickness. 여기서 배치는 중심 기준이므로 7.2 고정.
                    double numThickness = Math.max(1.0, fontSize * 0.18 * state.numberHeightScale);
                    numberNode.setTranslateZ(-(AppState.BASE_COIN_HEIGHT * 0.5 + 7.2 + numThickness * 0.5));
                    // [BugFix5] 새로 생성된 numberNode의 opacity를 명시적으로 1.0으로 초기화.
                    numberNode.setOpacity(1.0);
                    numbersGroup.getChildren().add(numberNode);
				}
			}
            coinGroup.getChildren().add(numbersGroup);
            // [B5-Fix] interactiveResizing 중에는 숫자를 숨겨야 함 + opacity 초기화
            numbersGroup.setOpacity(1.0);
            numbersGroup.setVisible(state.showNumbers && !state.interactiveResizing);
            applyNeonEffects();   // 숫자 재빌드 후 네온 상태 복원
            updateFaceDateTimeZ(); // 숫자 높이 변경 → faceDateTimeGroup Z 동기화
		}
        void rebuildGlass() {
            if (glassView != null) {
                coinGroup.getChildren().remove(glassView);
                glassView = null;
			}
            if (!state.showGlass) return;
            double innerR = AppState.BASE_COIN_RADIUS * AppState.GLASS_INNER_RATIO;
            double outerR = AppState.BASE_COIN_RADIUS * AppState.GLASS_OUTER_RATIO;
            // glassView Z를 눈금 높이에 연동 — 눈금 앞면보다 2.5 단위 앞(카메라 쪽)에 위치
            double maxTickD = Math.max(state.fiveMinuteTickHeight, state.oneMinuteTickHeight);
            double glassZ   = -(AppState.BASE_COIN_HEIGHT * 0.5 + maxTickD + 2.5);
            glassView = MeshFactory.makeRingDisk(innerR, outerR, AppState.SEGS, glassZ);
            glassView.setMaterial(materials.glass);
            glassView.setCullFace(CullFace.NONE);
            glassView.setOpacity(state.interactiveResizing ? 0.0 : (state.backgroundImage != null ? 0.0 : state.glassOpacity));
            glassView.setVisible(state.showGlass && !state.interactiveResizing && state.backgroundImage == null);
            coinGroup.getChildren().add(glassView);
		}

        /** 글래스 레이어 불투명도를 state.glassOpacity 값으로 즉시 반영 */
        void applyGlassOpacity() {
            if (glassView != null && state.showGlass
				&& !state.interactiveResizing && state.backgroundImage == null) {
                glassView.setOpacity(state.glassOpacity);
			}
		}

        void rebuildCrystalEffect() {
            coinGroup.getChildren().remove(crystalGroup);
            crystalGroup.getChildren().clear();
            crystalNodes.clear();

            if (state.crystalMode <= 0) {
                return;
			}

            switch (state.crystalMode) {
                case 1 -> buildCrystalConvexGlass();
                case 2 -> buildCrystalReflectiveArc();
                case 3 -> buildCrystalRainbowSegments();
                case 4 -> buildCrystalDeepBlue();
                default -> { }
			}

            coinGroup.getChildren().add(crystalGroup);
            applyCrystalVisibility();
		}

        private void applyCrystalVisibility() {
            boolean modeActive = state.crystalMode > 0
			&& !(state.crystalMode == 1 && !state.showConvexGlass);
            boolean visible = state.showGlass && !state.interactiveResizing && modeActive && state.backgroundImage == null;
            crystalGroup.setVisible(visible);
            crystalGroup.setManaged(false);
		}

        void applyCrystalVisibilityPublic() {
            applyCrystalVisibility();
		}

        private void buildCrystalConvexGlass() {
            // 기존 4개 디스크(opacity 0.05~0.11) → 단일 디스크로 통합
            // Z-fighting 완전 제거, glassView(z=−12.0)보다 1.0 앞에 배치
            double radius = AppState.BASE_COIN_RADIUS * 0.92;
            MeshView disk = MeshFactory.makeDisk(radius, AppState.SEGS, -(AppState.BASE_COIN_HEIGHT * 0.5 + 4.0));
            disk.setCullFace(CullFace.NONE);
            disk.setMaterial(alphaMaterial(Color.color(1.0, 1.0, 1.0), 0.10, 110));
            crystalGroup.getChildren().add(disk);
            crystalNodes.add(disk);
		}

        private void buildCrystalReflectiveArc() {
            // Fix2: arc/edge Z를 -(half+0.10)=-9.10에서 -(half+2.0)=-11.0으로 이동.
            //   faceView(Z=-9.45)와 간격이 0.35에 불과해 CullFace.NONE 상태에서
            //   Z-fighting이 발생하던 문제를 해소. 2.0 간격 → 충분한 여유.
            MeshView arc = MeshFactory.makeAnnularSector(
				AppState.BASE_COIN_RADIUS * 0.52,
				AppState.BASE_COIN_RADIUS * 0.92,
				180,
				360,
				AppState.SEGS / 2,
				-(AppState.BASE_COIN_HEIGHT * 0.5 + 2.0)
			);
            arc.setCullFace(CullFace.NONE);
            arc.setMaterial(alphaMaterial(Color.WHITE, 0.18, 95));
            crystalGroup.getChildren().add(arc);

            MeshView edge = MeshFactory.makeArcPolylineRibbon(
				AppState.BASE_COIN_RADIUS * 0.92,
				180,
				360,
				AppState.SEGS / 2,
				AppState.BASE_COIN_RADIUS * 0.012,
				-(AppState.BASE_COIN_HEIGHT * 0.5 + 1.9)   // arc보다 0.1 뒤 → edge 중복 없음
			);
            edge.setCullFace(CullFace.NONE);
            edge.setMaterial(alphaMaterial(Color.WHITE, 0.42, 120));
            crystalGroup.getChildren().add(edge);

            crystalNodes.add(arc);
            crystalNodes.add(edge);
		}

        private void buildCrystalRainbowSegments() {
            Color[] rainbow = {
				Color.color(1.0, 0.2, 0.2),
				Color.color(1.0, 0.7, 0.1),
				Color.color(0.9, 1.0, 0.1),
				Color.color(0.1, 1.0, 0.3),
				Color.color(0.1, 0.6, 1.0),
				Color.color(0.6, 0.1, 1.0)
			};
            double start = 0;
            double step = 360.0 / 24.0;
            for (int i = 0; i < 24; i++) {
                MeshView seg = MeshFactory.makeAnnularSector(
					AppState.BASE_COIN_RADIUS * 0.36,
					AppState.BASE_COIN_RADIUS * 0.98,
					start + i * step,
					start + (i + 1) * step,
					6,
					-(AppState.BASE_COIN_HEIGHT * 0.5 + 2.0)  // Fix2: +0.10 → +2.0
				);
                seg.setCullFace(CullFace.NONE);
                seg.setMaterial(alphaMaterial(rainbow[i % rainbow.length], 0.14, 80));
                crystalGroup.getChildren().add(seg);
                crystalNodes.add(seg);
			}
		}

        private void buildCrystalDeepBlue() {
            MeshView disk = MeshFactory.makeDisk(AppState.BASE_COIN_RADIUS * 0.98, AppState.SEGS,
			-(AppState.BASE_COIN_HEIGHT * 0.5 + 2.0));  // Fix2: +0.10 → +2.0
            disk.setCullFace(CullFace.NONE);
            disk.setMaterial(alphaMaterial(Color.color(0.05, 0.15, 0.55), 0.22, 70));
            crystalGroup.getChildren().add(disk);
            crystalNodes.add(disk);
		}

        private PhongMaterial alphaMaterial(Color base, double opacity, double specPower) {
            PhongMaterial m = new PhongMaterial();
            m.setDiffuseColor(Color.color(base.getRed(), base.getGreen(), base.getBlue(), opacity));
            m.setSpecularColor(Color.color(
				AppState.clamp(base.getRed() + 0.20, 0, 1),
				AppState.clamp(base.getGreen() + 0.20, 0, 1),
				AppState.clamp(base.getBlue() + 0.20, 0, 1),
				Math.min(1.0, opacity + 0.25)
			));
            m.setSpecularPower(specPower);
            return m;
		}



        private void buildCoinShell() {
            coinBodyView = new Cylinder(AppState.BASE_COIN_RADIUS, AppState.BASE_COIN_HEIGHT, AppState.SEGS);
            coinBodyView.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
            coinBodyView.setMaterial(materials.rim);

            faceView = MeshFactory.makeDisk(AppState.BASE_COIN_RADIUS * 0.990, AppState.SEGS, -(AppState.BASE_COIN_HEIGHT * 0.5 + 0.45));
            faceView.setMaterial(materials.face);
            faceView.setCullFace(CullFace.NONE); // 앞뒤 모두 렌더 — 법선 방향 의존 제거

            // backView: 동전 뒷면. +Z 방향에 직접 배치하고 CullFace.NONE으로 양면 렌더.
            // 이전 구현(local Z=+10.45 후 Rotate(180,X)) 은 변환 후 Z=-10.45가 되어
            // faceView(Z=-9.45) 보다 1.0 앞에 오는 치명적 Z 오류가 있었음.
            backView = MeshFactory.makeDisk(AppState.BASE_COIN_RADIUS * 0.990, AppState.SEGS, (AppState.BASE_COIN_HEIGHT * 0.5 + 0.45));
            backView.setMaterial(materials.back);
            backView.setCullFace(CullFace.NONE);

            // frontBowlView 삭제: faceView와 Z-fighting + CullFace.NONE 역법선이 앞면 무늬 원인
            // backBowlView  삭제: backView와 Z-fighting으로 후면 노이즈 발생
            // innerRingView 삭제: faceView와 색상 차이 0.04로 식별 불가, Z-fighting 기여

            coinGroup.getChildren().addAll(coinBodyView, faceView, backView);
		}

        private void buildRim() {
            double rimR = AppState.BASE_COIN_RADIUS + 6.0;
            double rimT = AppState.BASE_COIN_HEIGHT + AppState.BASE_RIM_EXTRA * 2.2;

            MeshView side = MeshFactory.makeOpenCylinderSide(rimR, rimT, AppState.SEGS);
            side.setCullFace(CullFace.NONE);
            side.setMaterial(materials.rim);

            capRingView = MeshFactory.makeRingDisk(AppState.BASE_COIN_RADIUS * 0.986, rimR, AppState.SEGS, -(rimT * 0.5 + 0.30));
            capRingView.setCullFace(CullFace.NONE);
            capRingView.setMaterial(materials.rim);

            frontBevelView = MeshFactory.makeBevelRing(AppState.BASE_COIN_RADIUS * 0.945, rimR, -(AppState.BASE_COIN_HEIGHT * 0.5 + 0.55), -(rimT * 0.5 + 0.05), AppState.SEGS);
            frontBevelView.setCullFace(CullFace.NONE);
            // [NEON Fix2] 별도 임시 재질 대신 materials.rim 을 공유.
            // 이전: metal(lighten(rimColor, 0.04), 1.0, 92) 로 새 재질 생성
            //   → applyNeonToShape() 가 이 임시 재질에 selfIlluminationMap 을 세팅하지만
            //     applyNeonToMaterial(materials.rim, ...) 과는 별개 객체라
            //     materials.rim 의 네온이 꺼질 때 이 임시 재질은 초기화되지 않는 불일치 발생.
            // 수정: materials.rim 을 직접 사용 → applyNeonToMaterial(materials.rim) 한 번으로
            //     side/capRingView/frontBevelView 세 군데 동시에 정확히 반영됨.
            frontBevelView.setMaterial(materials.rim);

            coinGroup.getChildren().addAll(side, capRingView, frontBevelView);
		}

        private void buildTicks() {
            tickGroup = new Group();

            // 눈금을 글래스 레이어 앞(카메라 쪽)에 배치한다.
            // glassZ = -(BASE_COIN_HEIGHT/2 + maxTickD + 2.5)  ← rebuildGlass()와 동일 공식
            // 눈금 중심 Z = glassZ - d/2 - 0.5
            //   → 눈금 앞면 Z = glassZ - d - 0.5  (글래스보다 최소 (d/2+0.5) 앞)
            // 이 공식은 눈금 높이(d) 슬라이더가 변해도 항상 글래스 앞을 유지한다.
            double maxTickD = Math.max(state.fiveMinuteTickHeight, state.oneMinuteTickHeight);
            double glassZ   = -(AppState.BASE_COIN_HEIGHT * 0.5 + maxTickD + 2.5);

            for (int i = 0; i < 60; i++) {
                double angle = Math.toRadians(i * 6.0);
                boolean isHour = i % 5 == 0;
                double r = AppState.BASE_COIN_RADIUS * (isHour ? AppState.TICK_HOUR_INNER_RATIO : AppState.TICK_MINUTE_INNER_RATIO);
                double len = AppState.BASE_COIN_RADIUS * (isHour ? 0.14 : 0.07);
                double w   = AppState.BASE_COIN_RADIUS * (isHour ? 0.028 : 0.012);
                double d = isHour ? state.fiveMinuteTickHeight : state.oneMinuteTickHeight;
                MeshView tickBody = MeshFactory.makeTickBox((float) w, (float) len, (float) d);
                double tx = Math.sin(angle) * (r + len * 0.5 - AppState.BASE_COIN_RADIUS * 0.01);
                double ty = -Math.cos(angle) * (r + len * 0.5 - AppState.BASE_COIN_RADIUS * 0.01);
                // 눈금 중심을 glassZ 기준으로 앞쪽에 고정
                double bodyZ = glassZ - d * 0.5 - 0.5;

                tickBody.setTranslateX(tx);
                tickBody.setTranslateY(ty);
                tickBody.setTranslateZ(bodyZ);
                tickBody.getTransforms().add(new Rotate(Math.toDegrees(angle), new Point3D(0, 0, 1)));
                tickBody.setMaterial(isHour ? materials.tickHour : materials.tickMinute);
                // [BugFix6] 인덱스 기반 isHour 판별 대신 UserData에 눈금 종류를 저장.
                // applyNeonEffects/setNeonFlickerOpacity에서 순서 의존 없이 안전하게 구분 가능.
                tickBody.setUserData(isHour ? "HOUR" : "MINUTE");
                tickGroup.getChildren().add(tickBody);
			}
            coinGroup.getChildren().add(tickGroup);
		}

        private void buildHands() {
            double globalLift = 3.2;
            // numberLift 연동 제거: 숫자 높이 슬라이더와 무관하게 침 Z는 항상 초기값 고정
            double numberLift = 0.0;

            // Fix3: capRingView Z = -(rimHalf + 0.30) = -21.40. 침이 베젤 캡을 관통하지 않도록
            //   각 침의 앞면이 bezelSafeZ 보다 안쪽에 머무르게 클램핑한다.
            //   bezelSafeZ = capRingView + 1.0 margin = -20.40
            //   각 침의 앞면 = centerZ - halfDepth 이므로 centerZ >= bezelSafeZ + halfDepth
            double rimT = AppState.BASE_COIN_HEIGHT + AppState.BASE_RIM_EXTRA * 2.2;
            double bezelSafeZ = -(rimT * 0.5 + 0.30 - 1.0);   // = -20.40

            double rawHourZ   = -(AppState.BASE_COIN_HEIGHT * 0.5 + 2.8 + globalLift + numberLift * 0.45);
            double rawMinuteZ = -(AppState.BASE_COIN_HEIGHT * 0.5 + 4.9 + globalLift + numberLift * 0.70);
            double rawSecondZ = -(AppState.BASE_COIN_HEIGHT * 0.5 + 6.8 + globalLift + numberLift);

            // halfDepth: Box 두께의 절반 (Z축 방향)
            double hourHalf   = 3.6 * 0.5;
            double minuteHalf = 2.7 * 0.5;
            double secondHalf = 2.0 * 0.5;

            // clamp: 침 중심 Z가 bezelSafeZ + halfDepth 보다 음수가 되지 않게
            double hourGroupZ   = Math.max(rawHourZ,   bezelSafeZ + hourHalf);
            double minuteGroupZ = Math.max(rawMinuteZ, bezelSafeZ + minuteHalf);
            double secondGroupZ = Math.max(rawSecondZ, bezelSafeZ + secondHalf);

            Box hour = makeHand(AppState.BASE_COIN_RADIUS * 0.40, AppState.BASE_COIN_RADIUS * 0.030, 3.6, materials.hour);
            hourRotation = new Rotate(0, new Point3D(0, 0, 1));
            Group hourGroup = new Group(hour);
            hourGroup.getTransforms().add(hourRotation);
            hourGroup.setTranslateZ(hourGroupZ);

            Box minute = makeHand(AppState.BASE_COIN_RADIUS * 0.60, AppState.BASE_COIN_RADIUS * 0.020, 2.7, materials.minute);
            minuteRotation = new Rotate(0, new Point3D(0, 0, 1));
            Group minuteGroup = new Group(minute);
            minuteGroup.getTransforms().add(minuteRotation);
            minuteGroup.setTranslateZ(minuteGroupZ);

            Box second = makeHand(AppState.BASE_COIN_RADIUS * 0.72, AppState.BASE_COIN_RADIUS * 0.010, 2.0, materials.second);
            secondRotation = new Rotate(0, new Point3D(0, 0, 1));
            Group secondGroup = new Group(second);
            secondGroup.getTransforms().add(secondRotation);
            secondGroup.setTranslateZ(secondGroupZ);

            handsGroup.getChildren().setAll(hourGroup, minuteGroup, secondGroup);
            coinGroup.getChildren().add(handsGroup);
		}

        private void buildGem() {
            double globalLift = 3.2;
            // numberLift 연동 제거: 침과 동일하게 보석 중심축도 초기값 고정
            double numberLift = 0.0;
            double axisLift = globalLift + numberLift;

            Cylinder base = new Cylinder(AppState.BASE_COIN_RADIUS * 0.060, AppState.BASE_COIN_RADIUS * 0.040, AppState.SEGS / 2);
            base.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
            base.setTranslateZ(-(AppState.BASE_COIN_HEIGHT * 0.5 + 4.8 + axisLift));
            base.setMaterial(metal(Color.color(0.20, 0.12, 0.00), 0.45, 10));

            Cylinder cap = new Cylinder(AppState.BASE_COIN_RADIUS * 0.040, AppState.BASE_COIN_RADIUS * 0.075, AppState.SEGS / 2);
            cap.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
            cap.setTranslateZ(-(AppState.BASE_COIN_HEIGHT * 0.5 + 7.8 + axisLift));
            cap.setMaterial(materials.gem);

            coinGroup.getChildren().addAll(base, cap);
		}

        private void buildBackEngraving() {
            Group engraving = new Group();
			backTextGroup = new Group();

            Group line1 = createBackEngravingLine("기 증 : 김 갑 수",
				AppState.BASE_COIN_RADIUS * 0.080,
				darken(state.backColor, 0.30),
				Color.color(0.78, 0.68, 0.42),
			0.055);
            Group line2 = createBackEngravingLine("2026년 3월 31일",
				AppState.BASE_COIN_RADIUS * 0.062,
				darken(state.backColor, 0.26),
				Color.color(0.72, 0.62, 0.38),
			0.048);

            double yCenter = AppState.BASE_COIN_RADIUS * 0.30;
            double lineGap = AppState.BASE_COIN_RADIUS * 0.078;
            line1.setTranslateY(yCenter - lineGap * 0.55);
            line2.setTranslateY(yCenter + lineGap * 0.55);

            // backView(+9.45)보다 아주 조금 앞에 배치해서 뒷면에서만 보이게 한다.
            double z = AppState.BASE_COIN_HEIGHT * 0.5 + 0.64;
            engraving.setTranslateZ(z);

            // 뒷면에서 읽히도록 반전
            engraving.getTransforms().add(new Rotate(180, Rotate.Y_AXIS));

			engraving.getChildren().addAll(line1, line2);
			backTextGroup = engraving;
			coinGroup.getChildren().add(engraving);
		}

        private Group createBackEngravingLine(String text, double fontSize, Color core, Color highlight, double trackingRatio) {
            Group base = createBackEngravingGlyphRun(
				text, fontSize, trackingRatio,
				new Scale(0.78, 0.78, 0.16),
				0.0, 0.0, 0.0,
				laserEngravingMaterial(core, highlight, 9)
			);

            Group glint = createBackEngravingGlyphRun(
				text, fontSize, trackingRatio,
				new Scale(0.76, 0.76, 0.06),
				-fontSize * 0.008, -fontSize * 0.012, -0.10,
				laserEngravingMaterial(
					Color.color(
						AppState.clamp(highlight.getRed() + 0.06, 0, 1),
						AppState.clamp(highlight.getGreen() + 0.06, 0, 1),
						AppState.clamp(highlight.getBlue() + 0.05, 0, 1)
					),
					Color.color(0.92, 0.86, 0.70),
				32)
			);

            return new Group(base, glint);
		}

        private Group createBackEngravingGlyphRun(String text, double fontSize, double trackingRatio,
			Scale glyphScale, double dx, double dy, double dz,
			PhongMaterial material) {
            NumberFactory factory = new NumberFactory(state);
            List<Node> glyphs = new ArrayList<>();
            List<Double> widths = new ArrayList<>();
            double tracking = fontSize * trackingRatio;
            double totalWidth = 0.0;

            for (int i = 0; i < text.length(); i++) {
                String ch = text.substring(i, i + 1);
                if (" ".equals(ch)) {
                    double spaceWidth = fontSize * 0.34;
                    glyphs.add(null);
                    widths.add(spaceWidth);
                    totalWidth += spaceWidth;
					} else {
                    Group glyph = factory.createNumber(ch, fontSize);
                    glyph.getTransforms().add(new Scale(glyphScale.getX(), glyphScale.getY(), glyphScale.getZ()));
                    applyMaterialRecursive(glyph, material);
                    double w = Math.max(fontSize * 0.20, glyph.getLayoutBounds().getWidth());
                    glyphs.add(glyph);
                    widths.add(w);
                    totalWidth += w;
				}
                if (i < text.length() - 1) totalWidth += tracking;
			}

            Group run = new Group();
            double cursor = -totalWidth * 0.5;
            for (int i = 0; i < glyphs.size(); i++) {
                Node glyph = glyphs.get(i);
                double w = widths.get(i);
                if (glyph != null) {
                    glyph.setTranslateX(cursor + w * 0.5 + dx);
                    glyph.setTranslateY(dy);
                    glyph.setTranslateZ(dz);
                    run.getChildren().add(glyph);
				}
                cursor += w;
                if (i < glyphs.size() - 1) cursor += tracking;
			}
            return run;
		}

        private PhongMaterial laserEngravingMaterial(Color diffuse, Color specular, double power) {
            PhongMaterial m = new PhongMaterial();
            m.setDiffuseColor(diffuse);
            m.setSpecularColor(specular);
            m.setSpecularPower(power);
            return m;
		}

        private void applyMaterialRecursive(Node node, PhongMaterial material) {
            if (node instanceof MeshView mv) {
                mv.setMaterial(material);
				} else if (node instanceof Group g) {
                for (Node child : g.getChildren()) {
                    applyMaterialRecursive(child, material);
				}
			}
		}


        private void buildLights() {
            PointLight warm = new PointLight(Color.color(1.00, 0.98, 0.92));
            warm.setTranslateX(-220);
            warm.setTranslateY(-330);
            warm.setTranslateZ(-300);

            PointLight gold = new PointLight(Color.color(0.40, 0.36, 0.28));
            gold.setTranslateX(260);
            gold.setTranslateY(180);
            gold.setTranslateZ(-120);

            AmbientLight ambient = new AmbientLight(Color.color(0.34, 0.34, 0.34));
            lightsGroup.getChildren().addAll(warm, gold, ambient);
		}

        private Box makeHand(double len, double w, double d, PhongMaterial mat) {
            Box box = new Box(w, len, d);
            box.setTranslateY(-len * 0.5);
            box.setMaterial(mat);
            return box;
		}

        private PhongMaterial tickFaceMaterial(Color base) {
            PhongMaterial m = new PhongMaterial();
            m.setDiffuseColor(base);
            m.setSpecularColor(Color.color(
				AppState.clamp(base.getRed() * 0.35 + 0.04, 0, 1),
				AppState.clamp(base.getGreen() * 0.35 + 0.04, 0, 1),
				AppState.clamp(base.getBlue() * 0.35 + 0.04, 0, 1)
			));
            m.setSpecularPower(4);
            return m;
		}

        private PhongMaterial metal(Color base, double specBoost, double specPower) {
            PhongMaterial m = new PhongMaterial();
            m.setDiffuseColor(base);
            double r = Math.min(1.0, base.getRed() * specBoost + 0.22);
            double g = Math.min(1.0, base.getGreen() * specBoost + 0.18);
            double b = Math.min(1.0, base.getBlue() * specBoost + 0.06);
            m.setSpecularColor(Color.color(r, g, b));
            m.setSpecularPower(specPower);
            return m;
		}

        // #6: static으로 승격 — NumberFactory에서도 중복 정의 없이 재사용
        static PhongMaterial matte(Color base) {
            PhongMaterial m = new PhongMaterial();
            m.setDiffuseColor(base);
            m.setSpecularColor(Color.color(
				AppState.clamp(base.getRed() + 0.03, 0, 1),
				AppState.clamp(base.getGreen() + 0.03, 0, 1),
				AppState.clamp(base.getBlue() + 0.02, 0, 1)
			));
            m.setSpecularPower(3);
            return m;
		}

        static Color lighten(Color c, double delta) {
            return Color.color(AppState.clamp(c.getRed() + delta, 0, 1),
				AppState.clamp(c.getGreen() + delta, 0, 1),
			AppState.clamp(c.getBlue() + delta, 0, 1));
		}

        static Color darken(Color c, double delta) {
            return Color.color(AppState.clamp(c.getRed() - delta, 0, 1),
				AppState.clamp(c.getGreen() - delta, 0, 1),
			AppState.clamp(c.getBlue() - delta, 0, 1));
		}

        final class Materials {
            PhongMaterial face;
            PhongMaterial back;
            PhongMaterial rim;
            PhongMaterial hour;
            PhongMaterial minute;
            PhongMaterial second;
            PhongMaterial tickHour;
            PhongMaterial tickMinute;
            PhongMaterial gem;
            PhongMaterial glass;
            PhongMaterial faceFlat;
            PhongMaterial backFlat;
		}
	}

    // ───────────────────────── Number Factory ─────────────────────────
    final class NumberFactory {
        private static final int MASK_SUPERSAMPLE = 6;
        private static final double ALPHA_THRESHOLD = 0.06;
        private static final double DESIRED_HEIGHT_RATIO = 1.12;
        private static final double EXTRUDE_THICKNESS_RATIO = 0.18;
        private static final double FRONT_BEVEL_THICKNESS_RATIO = 0.040;
        // FRONT_BEVEL_SCALE, SHADOW_OFFSET_RATIO — 미사용으로 제거 (#5)

        private final AppState state;

        NumberFactory(AppState state) {
            this.state = state;
		}

        Group createNumber(String text, double fontSize) {
            try {
                MaskData mask = renderTextMask(text, fontSize, state.numberFont);

                Group g = new Group();
                MeshView body = buildExtrudedText(
					mask,
					fontSize,
					0.0,
					1.0,
					extrudedMetalMaterial(state.numberColor, 0.06, 128)
				);

                g.getChildren().add(body);
                return g;
				} catch (Exception ex) {
				System.out.println("Failed to build extruded number for " + text + ", falling back to flat text ," +  ex.getMessage());
				AppLogger.logException(ex);
				return fallbackTextNumber(text, fontSize);
			}
		}

        private MeshView buildExtrudedText(MaskData mask, double fontSize, double zOffset, double faceScale, PhongMaterial material) {
            boolean[][] filled = mask.filled;
            int h = filled.length;
            int w = filled[0].length;

            float pixel = 1.0f;
            float xCenter = (w * pixel) * 0.5f;
            float yCenter = (h * pixel) * 0.5f;
            float thickness = (float) Math.max(1.0, h * EXTRUDE_THICKNESS_RATIO * state.numberHeightScale);
            float halfT = thickness * 0.5f;
            float bevelThickness = (float) Math.max(0.8, h * FRONT_BEVEL_THICKNESS_RATIO);
            float frontStart = zOffset == 0.0 ? 0.0f : (float) zOffset - bevelThickness * 0.5f;
            float frontEnd = zOffset == 0.0 ? halfT : (float) zOffset + bevelThickness * 0.5f;
            float backZ0 = (float) zOffset - halfT;
            float backZ1 = zOffset == 0.0 ? halfT : (float) zOffset - bevelThickness * 0.5f;

            MeshBuilder mb = new MeshBuilder();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (!filled[y][x]) continue;
                    float x0 = (float) ((x * pixel - xCenter) * faceScale);
                    float x1 = (float) ((((x + 1) * pixel) - xCenter) * faceScale);
                    float y0 = (float) ((y * pixel - yCenter) * faceScale);
                    float y1 = (float) ((((y + 1) * pixel) - yCenter) * faceScale);

                    if (zOffset == 0.0) {
                        mb.addQuad(x0, y0, halfT, x1, y0, halfT, x1, y1, halfT, x0, y1, halfT, false, 1);
                        mb.addQuad(x1, y0, -halfT, x0, y0, -halfT, x0, y1, -halfT, x1, y1, -halfT, false, 1);
						} else {
                        mb.addQuad(x0, y0, frontEnd, x1, y0, frontEnd, x1, y1, frontEnd, x0, y1, frontEnd, false, 1);
                        mb.addQuad(x1, y0, frontStart, x0, y0, frontStart, x0, y1, frontStart, x1, y1, frontStart, false, 1);
					}

                    boolean leftOpen = x == 0 || !filled[y][x - 1];
                    boolean rightOpen = x == w - 1 || !filled[y][x + 1];
                    boolean topOpen = y == 0 || !filled[y - 1][x];
                    boolean bottomOpen = y == h - 1 || !filled[y + 1][x];

                    float zA = zOffset == 0.0 ? -halfT : frontStart;
                    float zB = zOffset == 0.0 ? halfT : frontEnd;
                    if (leftOpen)  mb.addQuad(x0, y0, zA, x0, y0, zB, x0, y1, zB, x0, y1, zA, false, 2);
                    if (rightOpen) mb.addQuad(x1, y0, zB, x1, y0, zA, x1, y1, zA, x1, y1, zB, false, 2);
                    if (topOpen)   mb.addQuad(x0, y0, zA, x1, y0, zA, x1, y0, zB, x0, y0, zB, false, 2);
                    if (bottomOpen)mb.addQuad(x0, y1, zB, x1, y1, zB, x1, y1, zA, x0, y1, zA, false, 2);
				}
			}

            TriangleMesh mesh = mb.build();
            MeshView mv = new MeshView(mesh);
            mv.setCullFace(CullFace.BACK);
            mv.setDrawMode(DrawMode.FILL);
            mv.setMaterial(material);

            double rawHeight = Math.max(1.0, h * faceScale);
            double desiredHeight = fontSize * DESIRED_HEIGHT_RATIO;
            double uniformScale = desiredHeight / rawHeight;
            mv.getTransforms().add(new Scale(uniformScale, uniformScale, uniformScale));
            return mv;
		}

        private PhongMaterial extrudedMetalMaterial(Color base, double specLift, double specPower) {
            PhongMaterial m = new PhongMaterial();
            m.setDiffuseColor(base);
            m.setSpecularColor(Color.color(
				AppState.clamp(base.getRed() + 0.22 + specLift, 0, 1),
				AppState.clamp(base.getGreen() + 0.18 + specLift, 0, 1),
				AppState.clamp(base.getBlue() + 0.10 + specLift, 0, 1),
			1.0));
            m.setSpecularPower(specPower);
            return m;
		}

        private Group fallbackTextNumber(String text, double fontSize) {
            // [B6/B7-Fix] 2D Text 노드 대신 PhongMaterial Box로 렌더링
            // → restoreNumberDiffuseRecursive / applyRainbowColorRecursive 모두 적용됨
            double boxW = fontSize * 1.1;
            double boxH = fontSize * 1.2;
            double boxD = Math.max(1.0, fontSize * 0.18 * state.numberHeightScale);

            // 텍스처 생성
            int tw = (int) Math.max(32, boxW * 4);
            int th = (int) Math.max(32, boxH * 4);
            WritableImage texImg = new WritableImage(tw, th);
            Canvas canvas = new Canvas(tw, th);
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.TRANSPARENT);
            gc.clearRect(0, 0, tw, th);
            gc.setFont(Font.font(state.numberFont, FontWeight.BOLD, th * 0.75));
            gc.setFill(Color.WHITE);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.fillText(text, tw * 0.5, th * 0.5);
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            canvas.snapshot(sp, texImg);

            PhongMaterial mat = extrudedMetalMaterial(state.numberColor, 0.06, 128);
            mat.setDiffuseMap(texImg);

            Box box = new Box(boxW, boxH, boxD);
            box.setMaterial(mat);
            box.setCullFace(CullFace.BACK);
            Group g = new Group(box);
            return g;
		}

        private MaskData renderTextMask(String text, double fontSize, String fontFamily) {
            double renderSize = fontSize * MASK_SUPERSAMPLE;
            Text measure = new Text(text);
            measure.setFont(Font.font(fontFamily, FontWeight.BOLD, renderSize));
            double pad = Math.max(20, renderSize * 0.22);
            int w = (int) Math.ceil(measure.getLayoutBounds().getWidth() + pad * 2);
            int h = (int) Math.ceil(measure.getLayoutBounds().getHeight() + pad * 2);

            Canvas c = new Canvas(w, h);
            GraphicsContext gc = c.getGraphicsContext2D();
            gc.setFill(Color.TRANSPARENT);
            gc.clearRect(0, 0, w, h);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.setFont(Font.font(fontFamily, FontWeight.BOLD, renderSize));
            gc.setFill(Color.WHITE);
            gc.fillText(text, w * 0.5, h * 0.52);

            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            WritableImage img = c.snapshot(sp, null);
            PixelReader pr = img.getPixelReader();
            int minX = w, minY = h, maxX = -1, maxY = -1;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (pr.getColor(x, y).getOpacity() >= ALPHA_THRESHOLD) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
					}
				}
			}
            if (maxX < minX || maxY < minY) {
                return new MaskData(new boolean[][]{{true}});
			}
            int tw = maxX - minX + 1;
            int th = maxY - minY + 1;
            boolean[][] filled = new boolean[th][tw];
            for (int y = 0; y < th; y++) {
                for (int x = 0; x < tw; x++) {
                    filled[y][x] = pr.getColor(minX + x, minY + y).getOpacity() >= ALPHA_THRESHOLD;
				}
			}
            return new MaskData(filled);
		}

        // #6: matte(), lighten() 중복 제거 — SceneAssembler의 static 메서드로 위임
        // (호출 예: SceneAssembler.matte(color), SceneAssembler.lighten(color, delta))

        // #8: BoundsLike inner class 제거 — fallbackTextNumber에서 지역 변수로 대체됨

        final class MaskData {
            final boolean[][] filled;
            MaskData(boolean[][] filled) { this.filled = filled; }
		}

        // ③ 버텍스 중복 제거 MeshBuilder
        // 이전: addQuad() 마다 항상 4개 포인트를 무조건 추가 → 인접 쿼드의 공유 에지 좌표가 2번씩 저장
        // 개선: HashMap으로 (x,y,z)→index를 관리해 이미 추가된 포인트는 재사용
        //       결과: 포인트 배열 크기 대폭 감소 + 스무딩 그룹이 공유 버텍스를 통해 실제로 동작
        //
        // [수정] 이전 단일 long 키 방식은 z 비트가 x/y 비트 구간과 겹쳐 충돌 가능성이 있었음.
        //        외부 맵(xy long) + 내부 맵(z int) 2단계 구조로 완전한 충돌 없는 키를 보장.
        final class MeshBuilder {
            // 외부 키: x(하위 32비트) | y(상위 32비트)  →  내부 키: z 비트 패턴 → 인덱스
            private final java.util.HashMap<Long, java.util.HashMap<Integer, Integer>>
			vertexIndex = new java.util.HashMap<>();
            private final List<Float>   points    = new ArrayList<>();
            private final List<Integer> faces      = new ArrayList<>();
            private final List<Integer> smoothing  = new ArrayList<>();

            // 포인트를 deduplicate하여 추가하고, 해당 인덱스를 반환
            private int addOrGetPoint(float x, float y, float z) {
                // x는 하위 32비트, y는 상위 32비트 — 겹치지 않으므로 충돌 없음
                long xyKey = ((long) Float.floatToRawIntBits(x) & 0xFFFFFFFFL)
				| (((long) Float.floatToRawIntBits(y) & 0xFFFFFFFFL) << 32);
                int zBits = Float.floatToRawIntBits(z);

                java.util.HashMap<Integer, Integer> zMap =
				vertexIndex.computeIfAbsent(xyKey, k -> new java.util.HashMap<>());
                Integer existing = zMap.get(zBits);
                if (existing != null) return existing;

                int idx = points.size() / 3;
                points.add(x);
                points.add(y);
                points.add(z);
                zMap.put(zBits, idx);
                return idx;
			}

            void addQuad(float x0, float y0, float z0,
				float x1, float y1, float z1,
				float x2, float y2, float z2,
				float x3, float y3, float z3,
				boolean flip, int smoothingGroup) {
                int i0 = addOrGetPoint(x0, y0, z0);
                int i1 = addOrGetPoint(x1, y1, z1);
                int i2 = addOrGetPoint(x2, y2, z2);
                int i3 = addOrGetPoint(x3, y3, z3);
                if (!flip) {
                    addFace(i0, i1, i2, smoothingGroup);
                    addFace(i0, i2, i3, smoothingGroup);
					} else {
                    addFace(i0, i2, i1, smoothingGroup);
                    addFace(i0, i3, i2, smoothingGroup);
				}
			}

            private void addFace(int a, int b, int c, int smoothingGroup) {
                faces.add(a); faces.add(0);
                faces.add(b); faces.add(0);
                faces.add(c); faces.add(0);
                smoothing.add(smoothingGroup);
			}

            TriangleMesh build() {
                TriangleMesh mesh = new TriangleMesh();
                float[] pts = new float[points.size()];
                for (int i = 0; i < points.size(); i++) pts[i] = points.get(i);
                int[] fcs = new int[faces.size()];
                for (int i = 0; i < faces.size(); i++) fcs[i] = faces.get(i);
                int[] sm = new int[smoothing.size()];
                for (int i = 0; i < smoothing.size(); i++) sm[i] = smoothing.get(i);
                mesh.getPoints().setAll(pts);
                mesh.getTexCoords().setAll(0, 0);
                mesh.getFaces().setAll(fcs);
                mesh.getFaceSmoothingGroups().setAll(sm);
                return mesh;
			}
		}
	}

    // ───────────────────────── Mesh Factory ─────────────────────────
    final class MeshFactory {
        /**
			* UV 좌표를 포함한 원형 디스크 메시.
			* 텍스처 이미지를 원형 앞면에 매핑할 때 사용한다.
			* UV: 중앙=(0.5,0.5), 가장자리=단위원 위의 점을 [0,1]로 정규화
		*/
        static MeshView makeTexturedDisk(double r, int segs, double zOffset) {
            TriangleMesh mesh = new TriangleMesh();

            // Points: 0=center, 1..segs=rim
            mesh.getPoints().addAll(0f, 0f, (float) zOffset); // center
            for (int i = 0; i < segs; i++) {
                double a = 2 * Math.PI * i / segs;
                mesh.getPoints().addAll(
					(float) (Math.cos(a) * r),
					(float) (Math.sin(a) * r),
				(float) zOffset);
			}

            // TexCoords: 0=center(0.5,0.5), 1..segs=rim UV
            mesh.getTexCoords().addAll(0.5f, 0.5f); // index 0 = center
            for (int i = 0; i < segs; i++) {
                double a = 2 * Math.PI * i / segs;
                // UV: 원점 중앙, 반지름 0.5, Y축 반전(JavaFX UV는 위쪽이 0)
                float u = (float) (0.5 + 0.5 * Math.cos(a));
                // 배경 이미지가 상하 반전되어 보이지 않도록 V축을 뒤집지 않는다.
                float v = (float) (0.5 + 0.5 * Math.sin(a));
                mesh.getTexCoords().addAll(u, v);
			}

            // Faces: center(pt0,tc0) + two rim pts
            for (int i = 0; i < segs; i++) {
                int pA = 0,       tA = 0;
                int pB = i + 1,   tB = i + 1;
                int pC = ((i + 1) % segs) + 1, tC = ((i + 1) % segs) + 1;
                mesh.getFaces().addAll(pA, tA, pC, tC, pB, tB);
			}
            return new MeshView(mesh);
		}

        static MeshView makeDisk(double r, int segs, double zOffset) {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getTexCoords().addAll(0, 0);
            mesh.getPoints().addAll(0f, 0f, (float) zOffset);
            for (int i = 0; i < segs; i++) {
                double a = 2 * Math.PI * i / segs;
                mesh.getPoints().addAll((float) (Math.cos(a) * r), (float) (Math.sin(a) * r), (float) zOffset);
			}
            for (int i = 0; i < segs; i++) {
                int a = 0, b = i + 1, c = ((i + 1) % segs) + 1;
                // [BugFix7] 와인딩 순서 수정: a->c->b (CCW, 법선이 -Z 카메라 방향)
                // 이전 a->b->c (CW)는 법선이 +Z를 향해 조명 계산이 반전됐음.
                // CullFace.NONE으로 양면 렌더하므로 보이긴 했지만 조명이 어둡게 적용됐음.
                mesh.getFaces().addAll(a, 0, c, 0, b, 0);
			}
            return new MeshView(mesh);
		}

        static MeshView makeRingDisk(double rIn, double rOut, int segs, double zOffset) {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getTexCoords().addAll(0, 0);
            for (int i = 0; i < segs; i++) {
                double a = 2 * Math.PI * i / segs;
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                mesh.getPoints().addAll(cos * (float) rIn, sin * (float) rIn, (float) zOffset);
                mesh.getPoints().addAll(cos * (float) rOut, sin * (float) rOut, (float) zOffset);
			}
            for (int i = 0; i < segs; i++) {
                int i0 = i * 2, i1 = i * 2 + 1;
                int j0 = ((i + 1) % segs) * 2, j1 = ((i + 1) % segs) * 2 + 1;
                mesh.getFaces().addAll(i0, 0, j0, 0, i1, 0);
                mesh.getFaces().addAll(j0, 0, j1, 0, i1, 0);
			}
            return new MeshView(mesh);
		}

        static MeshView makeOpenCylinderSide(double r, double h, int segs) {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getTexCoords().addAll(0, 0);
            float zFront = (float) (-h * 0.5);
            float zBack = (float) (h * 0.5);
            for (int i = 0; i < segs; i++) {
                double a = 2 * Math.PI * i / segs;
                float x = (float) (Math.cos(a) * r);
                float y = (float) (Math.sin(a) * r);
                mesh.getPoints().addAll(x, y, zFront);
                mesh.getPoints().addAll(x, y, zBack);
			}
            for (int i = 0; i < segs; i++) {
                int i0 = i * 2, i1 = i * 2 + 1;
                int j0 = ((i + 1) % segs) * 2, j1 = ((i + 1) % segs) * 2 + 1;
                mesh.getFaces().addAll(i0, 0, j0, 0, j1, 0);
                mesh.getFaces().addAll(i0, 0, j1, 0, i1, 0);
			}
            return new MeshView(mesh);
		}

        static MeshView makeBevelRing(double innerR, double outerR, double zInner, double zOuter, int segs) {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getTexCoords().addAll(0, 0);
            for (int i = 0; i < segs; i++) {
                double a = 2 * Math.PI * i / segs;
                float cos = (float) Math.cos(a);
                float sin = (float) Math.sin(a);
                mesh.getPoints().addAll(cos * (float) innerR, sin * (float) innerR, (float) zInner);
                mesh.getPoints().addAll(cos * (float) outerR, sin * (float) outerR, (float) zOuter);
			}
            for (int i = 0; i < segs; i++) {
                int i0 = i * 2, i1 = i * 2 + 1;
                int j0 = ((i + 1) % segs) * 2, j1 = ((i + 1) % segs) * 2 + 1;
                mesh.getFaces().addAll(i0, 0, j0, 0, j1, 0);
                mesh.getFaces().addAll(i0, 0, j1, 0, i1, 0);
			}
            return new MeshView(mesh);
		}

        static MeshView makeAnnularSector(double rIn, double rOut, double startDeg, double endDeg, int segs, double zOffset) {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getTexCoords().addAll(0, 0);
            double start = Math.toRadians(startDeg);
            double end = Math.toRadians(endDeg);
            int steps = Math.max(1, segs);
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double a = start + (end - start) * t;
                float cos = (float) Math.cos(a);
                float sin = (float) Math.sin(a);
                mesh.getPoints().addAll(cos * (float) rIn, sin * (float) rIn, (float) zOffset);
                mesh.getPoints().addAll(cos * (float) rOut, sin * (float) rOut, (float) zOffset);
			}
            for (int i = 0; i < steps; i++) {
                int i0 = i * 2, i1 = i * 2 + 1;
                int j0 = (i + 1) * 2, j1 = (i + 1) * 2 + 1;
                mesh.getFaces().addAll(i0, 0, j0, 0, i1, 0);
                mesh.getFaces().addAll(j0, 0, j1, 0, i1, 0);
			}
            return new MeshView(mesh);
		}

        static MeshView makeArcPolylineRibbon(double radius, double startDeg, double endDeg, int segs, double thickness, double zOffset) {
            return makeAnnularSector(Math.max(0.0, radius - thickness * 0.5), radius + thickness * 0.5, startDeg, endDeg, segs, zOffset);
		}

        // 입체 눈금 전용 5면체 메시 (앞면 + 4 측면, 뒷면 없음)
        // Box는 뒷면 폴리곤이 faceView를 관통해 Z-fighting 노이즈를 일으킴
        // 뒷면만 제거해 입체감(측면 음영)을 살리면서 노이즈를 완전 차단
        static MeshView makeTickBox(float w, float h, float d) {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getTexCoords().addAll(0, 0);
            float hw = w * 0.5f, hh = h * 0.5f, hd = d * 0.5f;
            float x0 = -hw, x1 = hw, y0 = -hh, y1 = hh;

            // 카메라가 -Z 쪽에서 +Z 방향을 보기 때문에
            // 실제로 사용자에게 보이는 눈금 윗면은 local -Z 쪽이어야 한다.
            // 이전 구현은 윗면/뒷면 축이 반대로 잡혀 있어 색 변경 시 측면만 두드러지고
            // 정면 윗면은 거의 반영되지 않는 것처럼 보였다.
            float zFront = -hd;
            float zBack  =  hd;

            // points: front 4 + back 4 = 8
            mesh.getPoints().addAll(
                x0, y0, zFront, // 0 front left-top
                x1, y0, zFront, // 1 front right-top
                x1, y1, zFront, // 2 front right-bottom
                x0, y1, zFront, // 3 front left-bottom
                x0, y0, zBack,  // 4 back left-top
                x1, y0, zBack,  // 5 back right-top
                x1, y1, zBack,  // 6 back right-bottom
                x0, y1, zBack   // 7 back left-bottom
			);

            // front face only (toward camera, normal -Z)
            // camera is on -Z side looking toward +Z, so the visible front face
            // must wind clockwise from the viewer side.
            mesh.getFaces().addAll(0,0, 2,0, 1,0,  0,0, 3,0, 2,0);
            // left side
            mesh.getFaces().addAll(0,0, 4,0, 7,0,  0,0, 7,0, 3,0);
            // right side
            mesh.getFaces().addAll(5,0, 1,0, 2,0,  5,0, 2,0, 6,0);
            // top side
            mesh.getFaces().addAll(4,0, 5,0, 1,0,  4,0, 1,0, 0,0);
            // bottom side
            mesh.getFaces().addAll(3,0, 2,0, 6,0,  3,0, 6,0, 7,0);

            MeshView mv = new MeshView(mesh);
            mv.setCullFace(CullFace.NONE);
            return mv;
		}
	}

    // ───────────────────────── Overlay ─────────────────────────
    final class OverlayRenderer {
        private final AppState state;
        OverlayRenderer(AppState state) { this.state = state; }

        void draw(Canvas overlay) {
            GraphicsContext gc = overlay.getGraphicsContext2D();
            gc.clearRect(0, 0, state.viewportWidth, state.viewportHeight);

            if (state.paused) {
                gc.setFill(Color.color(1.0, 0.85, 0.20, 0.90));
                gc.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.TOP);
                gc.fillText("[ PAUSED ]", state.viewportWidth / 2.0, 10);
			}
            // 디지탈 시계는 faceDateTimeGroup(코인 페이스)에서 렌더링
		}
	}

    // ───────────────────────── Setup Panel ─────────────────────────
    final class SetupPanelController {
        private final AppState state;
        private final SceneAssembler assembler;
        private final Canvas overlay;
        private final Runnable onCameraSettingsChanged;
        private final java.util.function.Consumer<File> onLoadBackgroundFile;
        private final java.util.function.Consumer<File> onStartSlideshow;
        private final Runnable onRebuild;
        private final Runnable onBloomUpdate;
        private final Runnable onStopYoutube;
        private final Runnable onSave;   // 상태 변경 시 즉시 INI 저장

        SetupPanelController(AppState state, SceneAssembler assembler, Canvas overlay,
			Runnable onCameraSettingsChanged,
			java.util.function.Consumer<File> onLoadBackgroundFile,
			java.util.function.Consumer<File> onStartSlideshow,
			Runnable onRebuild,
			Runnable onBloomUpdate,
			Runnable onStopYoutube,
			Runnable onSave) {
            this.state = state;
            this.assembler = assembler;
            this.overlay = overlay;
            this.onCameraSettingsChanged = onCameraSettingsChanged;
            this.onLoadBackgroundFile = onLoadBackgroundFile;
            this.onStartSlideshow = onStartSlideshow;
            this.onRebuild = onRebuild;
            this.onBloomUpdate = onBloomUpdate;
            this.onStopYoutube = onStopYoutube;
            this.onSave = onSave;
		}

        Stage build(Stage owner) {
            Stage s = new Stage();
            // initOwner 제거: owner(mainStage)는 투명 오버레이로 primary 모니터 전체를 덮음.
            // initOwner가 있으면 JavaFX/OS가 show() 시점에 Stage를 owner 기준(= primary 모니터)
            // 중앙으로 강제 배치한 뒤 Platform.runLater가 실행되므로, 보조 모니터에서 열 때
            // 항상 primary 모니터 중앙으로 먼저 이동했다가 튀어오는 현상이 발생한다.
            // owner 없이 alwaysOnTop만 유지하면 위치를 show() 이전에 자유롭게 지정할 수 있다.
            s.setAlwaysOnTop(true);
            s.setResizable(true);
            s.setMinWidth(980);
            s.setMinHeight(860);
            s.setTitle("KootPanKingThree 설정");

            VBox root = new VBox(8);
            root.setPadding(new Insets(14, 18, 14, 18));
            applyRootStyle(root);

            // ── 테마 프리셋 ──────────────────────────────────────────────
            root.getChildren().add(section("● 테마 프리셋"));
            String[][] themes = {
                {"🥇 금", "GOLD"}, {"🥈 은", "SILVER"}, {"🥉 동", "COPPER"},
                {"🌙 야간", "MIDNIGHT"}, {"🌸 로즈골드", "ROSE_GOLD"}
			};
            HBox themeRow = new HBox(6);
            for (String[] t : themes) {
                Button btn = new Button(t[0]);
                btn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;");
                btn.setOnAction(e -> {
                    if (onStopYoutube != null) onStopYoutube.run(); // YouTube 중지
                    state.applyTheme(AppState.Theme.valueOf(t[1]));
                    assembler.rebuildMaterialsAndScene();
                    if (onSave != null) onSave.run();
				});
                themeRow.getChildren().add(btn);
			}

            // ── 6번째: 레인보우 토글 버튼 ────────────────────────────────
            Button rainbowBtn = new Button(state.rainbowMode ? "🌈 레인보우 ON" : "🌈 레인보우");
            rainbowBtn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;"
			+ (state.rainbowMode ? " -fx-background-color:#cc44ff; -fx-text-fill:white;" : ""));
            rainbowBtn.setOnAction(e -> {
                state.rainbowMode = !state.rainbowMode;
                if (state.rainbowMode) {
                    assembler.startRainbow(0); // 0 = 영구
                    rainbowBtn.setText("🌈 레인보우 ON");
                    rainbowBtn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;"
					+ " -fx-background-color:#cc44ff; -fx-text-fill:white;");
					} else {
                    assembler.stopRainbow();
                    rainbowBtn.setText("🌈 레인보우");
                    rainbowBtn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;");
				}
                if (onSave != null) onSave.run();
			});
            themeRow.getChildren().add(rainbowBtn);

            // ── 7번째: 투명 모드 토글 버튼 ──────────────────────────────
            Button transparentBtn = new Button(state.transparentMode ? "\uD83E\uDEDF 투명 ON" : "\uD83E\uDEDF 투명");
            transparentBtn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;"
			+ (state.transparentMode ? " -fx-background-color:#00bcd4; -fx-text-fill:white;" : ""));
            transparentBtn.setOnAction(e -> {
                state.transparentMode = !state.transparentMode;
                assembler.applyVisibilityState();
                if (state.transparentMode) {
                    transparentBtn.setText("\uD83E\uDEDF 투명 ON");
                    transparentBtn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;"
					+ " -fx-background-color:#00bcd4; -fx-text-fill:white;");
					} else {
                    transparentBtn.setText("\uD83E\uDEDF 투명");
                    transparentBtn.setStyle("-fx-font-size:11px; -fx-padding:4 8 4 8;");
				}
                if (onSave != null) onSave.run();
			});
            themeRow.getChildren().add(transparentBtn);
            root.getChildren().add(themeRow);

            // 레인보우 색 변경 간격 선택기 (레인보우 버튼과 같은 줄)
            javafx.scene.control.Label rainbowLbl = new javafx.scene.control.Label("색 변경 간격:");
            rainbowLbl.setStyle("-fx-text-fill:" + dialogTextColor() + ";");
            javafx.scene.control.ChoiceBox<String> rainbowIntervalBox = new javafx.scene.control.ChoiceBox<>();
            double[] intervalValues = {0.5, 1, 2, 3, 4, 5, 10, 15, 20, 30, 60};
            for (double v : intervalValues) rainbowIntervalBox.getItems().add(v < 1 ? v + "초" : (int)v + "초");
            // 현재 값 선택
            double curInterval = state.rainbowIntervalSec;
            int rainbowSelIdx = 0; // 기본 0.5초
            for (int i = 0; i < intervalValues.length; i++) {
                if (Math.abs(intervalValues[i] - curInterval) < 0.01) { rainbowSelIdx = i; break; }
			}
            rainbowIntervalBox.getSelectionModel().select(rainbowSelIdx);
            rainbowIntervalBox.getSelectionModel().selectedIndexProperty().addListener((ob, ov, nv) -> {
                state.rainbowIntervalSec = intervalValues[nv.intValue()];
                if (state.rainbowMode || assembler.rainbowActive) {
                    assembler.startRainbow(state.rainbowMode ? 0 : 30);
				}
                if (onSave != null) onSave.run();
			});
            root.getChildren().add(new HBox(8, rainbowLbl, rainbowIntervalBox));

            // ── 색깔 설정 ────────────────────────────────────────────────
            root.getChildren().add(section("● 색깔 설정"));
            GridPane cg = new GridPane();
            cg.setHgap(12); cg.setVgap(4);
            cg.add(colorRowNeon("앞면",     state.faceColor,           c -> { state.faceColor = c;           assembler.rebuildMaterialsAndScene(); assembler.applyNeonEffects(); onBloomUpdate.run(); },
			state.neonFace,           v -> { state.neonFace = v;           assembler.applyNeonEffects(); onBloomUpdate.run(); }), 0, 0);
            cg.add(colorRowNeon("뒷면",     state.backColor,           c -> { state.backColor = c;           assembler.rebuildMaterialsAndScene(); assembler.applyNeonEffects(); onBloomUpdate.run(); },
			state.neonBack,           v -> { state.neonBack = v;           assembler.applyNeonEffects(); onBloomUpdate.run(); }), 0, 1);
            cg.add(colorRowNeon("베젤",     state.rimColor,            c -> { state.rimColor = c;            assembler.rebuildMaterialsAndScene(); assembler.applyNeonEffects(); onBloomUpdate.run(); },
			state.neonRim,            v -> { state.neonRim = v;            assembler.applyNeonEffects(); onBloomUpdate.run(); }), 0, 2);
            cg.add(colorRowNeon("시침",     state.hourHandColor,       c -> { state.hourHandColor = c;       assembler.rebuildMaterialsAndScene(); assembler.applyNeonEffects(); onBloomUpdate.run(); },
			state.neonHourHand,       v -> { state.neonHourHand = v;       assembler.applyNeonEffects(); onBloomUpdate.run(); }), 0, 3);
            cg.add(colorRowNeon("분침",     state.minuteHandColor,     c -> { state.minuteHandColor = c;     assembler.rebuildMaterialsAndScene(); assembler.applyNeonEffects(); onBloomUpdate.run(); },
			state.neonMinuteHand,     v -> { state.neonMinuteHand = v;     assembler.applyNeonEffects(); onBloomUpdate.run(); }), 0, 4);
            cg.add(colorRowNeon("초침",     state.secondHandColor,     c -> { state.secondHandColor = c;     assembler.rebuildMaterialsAndScene(); assembler.applyNeonEffects(); onBloomUpdate.run(); },
			state.neonSecondHand,     v -> { state.neonSecondHand = v;     assembler.applyNeonEffects(); onBloomUpdate.run(); }), 0, 5);
            cg.add(colorRowNeon("5분 눈금", state.fiveMinuteTickColor, c -> { state.fiveMinuteTickColor = c; assembler.rebuildMaterialsAndScene(); assembler.applyNeonEffects(); onBloomUpdate.run(); },
			state.neonFiveMinuteTick, v -> { state.neonFiveMinuteTick = v; assembler.applyNeonEffects(); onBloomUpdate.run(); }), 0, 6);
            cg.add(colorRowNeon("1분 눈금", state.oneMinuteTickColor,  c -> { state.oneMinuteTickColor = c;  assembler.rebuildMaterialsAndScene(); assembler.applyNeonEffects(); onBloomUpdate.run(); },
			state.neonOneMinuteTick,  v -> { state.neonOneMinuteTick = v;  assembler.applyNeonEffects(); onBloomUpdate.run(); }), 0, 7);
            cg.add(colorRowNeon("숫자",     state.numberColor,         c -> { state.numberColor = c;         assembler.rebuildNumbers();           assembler.applyNeonEffects(); onBloomUpdate.run(); },
			state.neonNumber,         v -> { state.neonNumber = v;         assembler.applyNeonEffects(); onBloomUpdate.run(); }), 0, 8);
            root.getChildren().add(cg);

            // ── 네온 점멸 스타일 ─────────────────────────────────────────
            root.getChildren().add(section("● 네온 점멸"));

            // 라디오 버튼 그룹
            ToggleGroup blinkGroup = new ToggleGroup();
            RadioButton rbNone   = neonRadio("없음 (항상 켜짐)",  blinkGroup,
			state.neonBlinkStyle == AppState.NeonBlinkStyle.NONE);
            RadioButton rbPulse  = neonRadio("부드러운 맥박",     blinkGroup,
			state.neonBlinkStyle == AppState.NeonBlinkStyle.PULSE);
            RadioButton rbSharp  = neonRadio("날카로운 깜빡임",   blinkGroup,
			state.neonBlinkStyle == AppState.NeonBlinkStyle.SHARP);
            RadioButton rbRandom = neonRadio("불규칙 깜빡임",     blinkGroup,
			state.neonBlinkStyle == AppState.NeonBlinkStyle.RANDOM);

            blinkGroup.selectedToggleProperty().addListener((ob, ov, nv) -> {
                if (nv == rbNone)   state.neonBlinkStyle = AppState.NeonBlinkStyle.NONE;
                else if (nv == rbPulse)  state.neonBlinkStyle = AppState.NeonBlinkStyle.PULSE;
                else if (nv == rbSharp)  state.neonBlinkStyle = AppState.NeonBlinkStyle.SHARP;
                else if (nv == rbRandom) state.neonBlinkStyle = AppState.NeonBlinkStyle.RANDOM;
                state.neonFlickerPhase = 0.0;
                state.neonRandomCounter = 0;
                if (onSave != null) onSave.run();
			});

            HBox radioRow1 = new HBox(14, rbNone, rbPulse);
            HBox radioRow2 = new HBox(14, rbSharp, rbRandom);
            radioRow1.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            radioRow2.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            root.getChildren().addAll(radioRow1, radioRow2);

            // 속도 슬라이더
            Slider blinkSpeed = slider(0.2, 6.0, state.neonFlickerSpeed);
            Label blinkSpeedLabel = value(String.format("%.1f Hz", state.neonFlickerSpeed));
            blinkSpeed.valueProperty().addListener((ob, ov, nv) -> {
                state.neonFlickerSpeed = nv.doubleValue();
                blinkSpeedLabel.setText(String.format("%.1f Hz", state.neonFlickerSpeed));
                state.neonFlickerPhase = 0.0;
			});
            blinkSpeed.setOnMouseReleased(e -> { if (onSave != null) onSave.run(); });
            root.getChildren().add(new HBox(8, plain("속도"), blinkSpeed, blinkSpeedLabel));

            // 깊이 슬라이더 (꺼지는 정도)
            Slider blinkDepth = slider(0.0, 1.0, state.neonFlickerDepth);
            Label blinkDepthLabel = value(String.format("%.0f%%", state.neonFlickerDepth * 100));
            blinkDepth.valueProperty().addListener((ob, ov, nv) -> {
                state.neonFlickerDepth = nv.doubleValue();
                blinkDepthLabel.setText(String.format("%.0f%%", state.neonFlickerDepth * 100));
			});
            blinkDepth.setOnMouseReleased(e -> { if (onSave != null) onSave.run(); });
            root.getChildren().add(new HBox(8, plain("깊이"), blinkDepth, blinkDepthLabel));

            // ── 높이 조정 ────────────────────────────────────────────────
            root.getChildren().add(section("● 높이 조정"));
            Slider minuteTickHeight = slider(0.4, 13.5, state.oneMinuteTickHeight);
            Label minuteTickHeightLabel = value(String.format("%.2f", state.oneMinuteTickHeight));
            minuteTickHeight.valueProperty().addListener((ob, ov, nv) -> {
                state.oneMinuteTickHeight = nv.doubleValue();
                minuteTickHeightLabel.setText(String.format("%.2f", state.oneMinuteTickHeight));
                assembler.rebuildMaterialsAndScene();
			});
            minuteTickHeight.setOnMouseReleased(e -> { if (onSave != null) onSave.run(); });
            root.getChildren().add(new HBox(8, plain("1분 눈금 높이"), minuteTickHeight, minuteTickHeightLabel));

            Slider hourTickHeight = slider(1.0, 24.0, state.fiveMinuteTickHeight);
            Label hourTickHeightLabel = value(String.format("%.2f", state.fiveMinuteTickHeight));
            hourTickHeight.valueProperty().addListener((ob, ov, nv) -> {
                state.fiveMinuteTickHeight = nv.doubleValue();
                hourTickHeightLabel.setText(String.format("%.2f", state.fiveMinuteTickHeight));
                assembler.rebuildMaterialsAndScene();
			});
            hourTickHeight.setOnMouseReleased(e -> { if (onSave != null) onSave.run(); });
            root.getChildren().add(new HBox(8, plain("5분 눈금 높이"), hourTickHeight, hourTickHeightLabel));

            Slider numberHeight = slider(0.4, 9.0, state.numberHeightScale);
            Label numberHeightLabel = value(String.format("%.2f", state.numberHeightScale));
            numberHeight.valueProperty().addListener((ob, ov, nv) -> {
                state.numberHeightScale = nv.doubleValue();
                numberHeightLabel.setText(String.format("%.2f", state.numberHeightScale));
                assembler.rebuildMaterialsAndScene();
			});
            numberHeight.setOnMouseReleased(e -> { if (onSave != null) onSave.run(); });
            root.getChildren().add(new HBox(8, plain("숫자 높이"), numberHeight, numberHeightLabel));

            // ── 시계 크기 / 투명도 ───────────────────────────────────────
            root.getChildren().add(section("● 시계 크기 / 투명도"));

            // 카메라 거리 (첫 번째 항목)
            Slider cameraDistance = slider(160, 860, state.cameraDistance);
            Label cameraDistanceLabel = value(String.format("%.0f", state.cameraDistance));
            cameraDistance.valueProperty().addListener((ob, ov, nv) -> {
                state.cameraDistance = nv.doubleValue();
                cameraDistanceLabel.setText(String.format("%.0f", state.cameraDistance));
                onCameraSettingsChanged.run();
			});
            cameraDistance.setOnMouseReleased(e -> { if (onSave != null) onSave.run(); });
            root.getChildren().add(new HBox(8, plain("카메라 거리"), cameraDistance, cameraDistanceLabel));

            Slider size = slider(70, 220, state.coinRadius);
            Label sizeLabel = value(String.format("%.0f", state.coinRadius));
            size.valueChangingProperty().addListener((ob, wasChanging, isChanging) -> {
                assembler.setInteractiveResizing(isChanging);
                assembler.applyVisibilityState();
			});
            size.setOnMousePressed(e -> assembler.setInteractiveResizing(true));
            size.setOnMouseReleased(e -> {
                assembler.setInteractiveResizing(false);
                assembler.applyVisibilityState();
                if (onSave != null) onSave.run();
			});
            size.valueProperty().addListener((ob, ov, nv) -> {
                state.coinRadius = nv.doubleValue();
                sizeLabel.setText(String.format("%.0f", state.coinRadius));
                assembler.applyGeometryScale();
                if (!state.interactiveResizing) assembler.applyVisibilityState();
			});
            root.getChildren().add(new HBox(8, plain("크기"), size, sizeLabel));

            // YouTube 확대/축소 슬라이더 (0=전체보기, 1=꽉채우기)
            Slider ytZoom = slider(0.0, 1.0, state.youtubeScale);
            Label ytZoomLabel = value(String.format("%.0f%%", state.youtubeScale * 100));
            ytZoom.valueProperty().addListener((ob, ov, nv) -> {
                state.youtubeScale = nv.doubleValue();
                ytZoomLabel.setText(String.format("%.0f%%", state.youtubeScale * 100));
                assembler.applyBackgroundImage();
			});
            ytZoom.setOnMouseReleased(e -> { if (onSave != null) onSave.run(); });
            root.getChildren().add(new HBox(8, plain("YouTube 확대"), ytZoom, ytZoomLabel));

            Slider opacity = slider(0.10, 1.00, state.clockOpacity);
            Label opacityLabel = value(String.format("%.0f%%", state.clockOpacity * 100));
            opacity.valueProperty().addListener((ob, ov, nv) -> {
                state.clockOpacity = nv.doubleValue();
                assembler.root3D.setOpacity(state.clockOpacity);
                overlay.setOpacity(state.clockOpacity);
                opacityLabel.setText(String.format("%.0f%%", state.clockOpacity * 100));
			});
            opacity.setOnMouseReleased(e -> { if (onSave != null) onSave.run(); });
            root.getChildren().add(new HBox(8, plain("불투명도"), opacity, opacityLabel));

            // ── 흔들기 ───────────────────────────────────────────────────
            root.getChildren().add(section("● 흔들기"));
            Slider speed = slider(AppState.SPEED_MIN, AppState.SPEED_MAX, state.savedSpeed);
            Label speedLabel = value(String.format("%.3f", state.savedSpeed));
            speed.valueProperty().addListener((ob, ov, nv) -> {
                state.savedSpeed = nv.doubleValue();
                if (!state.paused) state.autoSpeed = state.savedSpeed;
                speedLabel.setText(String.format("%.3f", state.savedSpeed));
			});
            speed.setOnMouseReleased(e -> { if (onSave != null) onSave.run(); });
            root.getChildren().add(new HBox(8, plain("속도"), speed, speedLabel));

            Slider swingY = slider(20, 180, state.swingRangeY);
            Label swingYLabel = value(String.format("%.0f°", state.swingRangeY));
            swingY.valueProperty().addListener((ob, ov, nv) -> {
                state.swingRangeY = nv.doubleValue();
                swingYLabel.setText(String.format("%.0f°", state.swingRangeY));
			});
            swingY.setOnMouseReleased(e -> { if (onSave != null) onSave.run(); });
            root.getChildren().add(new HBox(8, plain("좌우"), swingY, swingYLabel));

            Slider swingX = slider(0, 90, state.swingRangeX);
            Label swingXLabel = value(String.format("%.0f°", state.swingRangeX));
            swingX.valueProperty().addListener((ob, ov, nv) -> {
                state.swingRangeX = nv.doubleValue();
                swingXLabel.setText(String.format("%.0f°", state.swingRangeX));
			});
            swingX.setOnMouseReleased(e -> { if (onSave != null) onSave.run(); });
            root.getChildren().add(new HBox(8, plain("상하"), swingX, swingXLabel));

            addDigitalSettingsSection(root);

            // ── 배경 이미지 / 슬라이드쇼 (통합) ─────────────────────────
            root.getChildren().add(section("● 배경 이미지 / 슬라이드쇼"));

            // 현재 상태 레이블
            String initLabelText = state.backgroundImageFile == null ? "(없음)"
			: (state.slideshowEnabled
				? "슬라이드 쇼: " + state.backgroundImageFile.getParentFile().getName()
			: state.backgroundImageFile.getName());
            Label imgFileLabel = plain(initLabelText);
            imgFileLabel.setStyle("-fx-text-fill:#444444; -fx-font-style:italic;");

            // 전환 간격 ChoiceBox (슬라이드쇼 체크박스와 연동)
            int[] intervalSecs = {1, 2, 3, 4, 5, 10, 15, 20, 30, 60, 90, 120, 180, 300};
            ChoiceBox<String> intervalBox = new ChoiceBox<>();
            int currentSecs = (int)(state.slideshowIntervalNanos / 1_000_000_000L);
            int selIdx = 1;
            for (int i = 0; i < intervalSecs.length; i++) {
                int sec = intervalSecs[i];
                String lbl;
                if (sec < 60)           lbl = sec + "초";
                else if (sec % 60 == 0) lbl = (sec / 60) + "분";
                else                    lbl = (sec / 60) + "분 " + (sec % 60) + "초";
                intervalBox.getItems().add(lbl);
                if (sec == currentSecs) selIdx = i;
			}
            intervalBox.getSelectionModel().select(selIdx);
            intervalBox.setDisable(!state.slideshowEnabled);
            intervalBox.getSelectionModel().selectedIndexProperty().addListener((ob, ov, nv) -> {
                if (nv != null && nv.intValue() >= 0 && nv.intValue() < intervalSecs.length) {
                    state.slideshowIntervalNanos = (long) intervalSecs[nv.intValue()] * 1_000_000_000L;
                    state.slideshowLastSwitchNanos = 0L;
                    if (onSave != null) onSave.run();
				}
			});

            // 슬라이드 쇼 체크박스 — 이미지 선택 후 부모 폴더 자동 적용
            CheckBox cbSlideshow = new CheckBox("슬라이드 쇼");
            cbSlideshow.setSelected(state.slideshowEnabled);
            cbSlideshow.setDisable(state.backgroundImageFile == null);
            cbSlideshow.setStyle("-fx-text-fill:" + dialogTextColor() + ";");
            cbSlideshow.setOnAction(e -> {
                if (cbSlideshow.isSelected()) {
                    if (state.backgroundImageFile != null) {
                        // 이미 선택된 이미지 파일의 부모 폴더를 자동 적용 — 별도 파일 선택 없음
                        // configureSlideshowFromSelectedFile은 동기 실행이므로 직후 state 확인 가능
                        onStartSlideshow.accept(state.backgroundImageFile);
                        // Fix2: 폴더에 이미지가 1개뿐이면 slideshowEnabled = false로 남음 → 체크박스·레이블 반영
                        if (state.slideshowEnabled) {
                            imgFileLabel.setText("슬라이드 쇼: "
							+ state.backgroundImageFile.getParentFile().getName());
                            imgFileLabel.setStyle("-fx-text-fill:#004400; -fx-font-style:normal;");
							} else {
                            cbSlideshow.setSelected(false);
                            imgFileLabel.setText("⚠ 이미지가 1개뿐 — 슬라이드쇼 불가");
                            imgFileLabel.setStyle("-fx-text-fill:#886600;");
						}
						} else {
                        cbSlideshow.setSelected(false);
					}
					} else {
                    state.slideshowEnabled = false;
                    state.slideshowFiles.clear();
                    state.slideshowIndex = -1;
                    state.slideshowLastSwitchNanos = 0L;
                    imgFileLabel.setText(state.backgroundImageFile != null
					? state.backgroundImageFile.getName() : "(없음)");
                    imgFileLabel.setStyle("-fx-text-fill:#444444; -fx-font-style:italic;");
				}
                intervalBox.setDisable(!state.slideshowEnabled);
                if (onSave != null) onSave.run();
			});

            // 이미지 선택 버튼
            Button btnSelectImg = new Button("이미지 선택");
            btnSelectImg.setOnAction(e -> {
                javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
                fc.setTitle("배경 이미지 선택");
                fc.getExtensionFilters().addAll(
					new javafx.stage.FileChooser.ExtensionFilter(
					"이미지 파일", "*.png","*.jpg","*.jpeg","*.bmp","*.gif"),
					// "이미지 파일", "*.png","*.jpg","*.jpeg","*.bmp","*.gif","*.webp"),
				new javafx.stage.FileChooser.ExtensionFilter("모든 파일", "*.*"));
                File chosen = fc.showOpenDialog(s);
                if (chosen != null) {
                    try {
                        Image img = new Image(chosen.toURI().toString(), false);
                        if (img.isError()) {
                            imgFileLabel.setText("⚠ 이미지 로드 실패");
                            imgFileLabel.setStyle("-fx-text-fill:#cc0000;");
							} else {
                            // 슬라이드쇼 초기화 후 단일 이미지로 적용
                            state.slideshowEnabled = false;
                            state.slideshowFiles.clear();
                            state.slideshowIndex = -1;
                            state.slideshowLastSwitchNanos = 0L;
                            cbSlideshow.setSelected(false);
                            intervalBox.setDisable(true);
                            // Fix1: backgroundImageFile을 비동기 완료 전에 즉시 설정.
                            //   loadBackgroundImageFromFile은 true(비동기)로 로딩하므로
                            //   progressProperty 리스너가 완료되기 전에 사용자가 슬라이드쇼
                            //   체크박스를 누르면 configureSlideshowFromSelectedFile(null)이
                            //   호출되는 버그를 방지한다.
                            state.backgroundImageFile = chosen;
                            onLoadBackgroundFile.accept(chosen);
                            imgFileLabel.setText(chosen.getName());
                            imgFileLabel.setStyle("-fx-text-fill:#004400; -fx-font-style:normal;");
                            cbSlideshow.setDisable(false);
                            if (onSave != null) onSave.run();
						}
						} catch (Exception ex) {
						System.out.println("⚠ 오류: " + chosen.getName() + " , " + chosen.toURI().toString() + " , " + ex.getMessage());
                        imgFileLabel.setText("⚠ 오류: " + ex.getMessage());
                        imgFileLabel.setStyle("-fx-text-fill:#cc0000;");
						AppLogger.logException(ex);
					}
				}
			});

            // 중지 버튼 — 이미지 + 슬라이드쇼 모두 초기화
            Button btnStop = new Button("중지");
            btnStop.setOnAction(e -> {
                state.backgroundImage = null;
                state.backgroundImageFile = null;
                state.slideshowEnabled = false;
                state.slideshowFiles.clear();
                state.slideshowIndex = -1;
                state.slideshowLastSwitchNanos = 0L;
                cbSlideshow.setSelected(false);
                cbSlideshow.setDisable(true);
                intervalBox.setDisable(true);
                imgFileLabel.setText("(없음)");
                imgFileLabel.setStyle("-fx-text-fill:#444444; -fx-font-style:italic;");
                assembler.applyBackgroundImage();
                assembler.applyVisibilityState();
                if (onSave != null) onSave.run();
			});

            // [이미지 선택] [☐ 슬라이드 쇼] [전환간격▼] [중지]
            HBox imgControlRow = new HBox(6, btnSelectImg, cbSlideshow, intervalBox, btnStop);
            imgControlRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            root.getChildren().addAll(imgControlRow, imgFileLabel);

            // ── 표시 옵션 (수평 배열) ──────────────────────────────────────
            root.getChildren().add(section("● 표시 옵션"));
            CheckBox cbNum = checkbox("시 숫자 표시", state.showNumbers,
			v -> { state.showNumbers = v; assembler.applyVisibilityState(); if (onSave != null) onSave.run(); });

            Slider glassOpacitySlider = slider(0.0, 1.0, state.glassOpacity);
            Label glassOpacityLabel = value(String.format("%.0f%%", state.glassOpacity * 100));
            glassOpacitySlider.valueProperty().addListener((ob, ov, nv) -> {
                state.glassOpacity = nv.doubleValue();
                glassOpacityLabel.setText(String.format("%.0f%%", state.glassOpacity * 100));
                assembler.applyGlassOpacity();
			});
            glassOpacitySlider.setOnMouseReleased(e -> { if (onSave != null) onSave.run(); });
            HBox glassOpacityRow = new HBox(8, plain("  └ 불투명도"), glassOpacitySlider, glassOpacityLabel);
            glassOpacityRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            glassOpacityRow.setVisible(state.showGlass);
            glassOpacityRow.setManaged(state.showGlass);

            CheckBox cbGlass = checkbox("글래스 레이어", state.showGlass, v -> {
                state.showGlass = v;
                assembler.rebuildGlass();
                assembler.applyVisibilityState();
                glassOpacityRow.setVisible(v);
                glassOpacityRow.setManaged(v);
                if (onSave != null) onSave.run();
			});

            CheckBox cbConvex = checkbox("볼록 유리 효과", state.showConvexGlass,
			v -> { state.showConvexGlass = v; assembler.applyCrystalVisibilityPublic(); if (onSave != null) onSave.run(); });
            cbConvex.setDisable(state.crystalMode != 1);
            HBox optRow = new HBox(16, cbNum, cbGlass, cbConvex);
            optRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            root.getChildren().addAll(optRow, glassOpacityRow);

            // ── 폰트 + 카메라 ────────────────────────────────────────────
            root.getChildren().add(section("● 폰트"));
            ChoiceBox<String> numFont = new ChoiceBox<>();
            numFont.getItems().addAll("Georgia","Arial","Times New Roman","Verdana",
			"Tahoma","Consolas","Courier New","Impact");
            numFont.setValue(state.numberFont);
            numFont.getSelectionModel().selectedItemProperty().addListener((ob, ov, nv) -> {
                if (nv != null) { state.numberFont = nv; assembler.rebuildNumbers(); if (onSave != null) onSave.run(); }
			});
            root.getChildren().add(new HBox(10, plain("숫자 폰트"), numFont));

            // ── 크리스탈 / 반사 효과 (수평 배열) ────────────────────────
            root.getChildren().add(section("● 크리스탈 / 반사 효과"));
            ToggleGroup tg = new ToggleGroup();
            String[] crystalLabels = {"없음", "볼록 유리", "반사 호", "레인보우", "딥 블루"};
            HBox crystalRow = new HBox(10);
            crystalRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            for (int i = 0; i < crystalLabels.length; i++) {
                final int mode = i;
                RadioButton rb = new RadioButton(crystalLabels[i]);
                rb.setStyle("-fx-text-fill:" + dialogTextColor() + ";");
                rb.setToggleGroup(tg);
                rb.setSelected(state.crystalMode == i);
                rb.setOnAction(e -> {
                    state.crystalMode = mode;
                    assembler.rebuildCrystalEffect();
                    assembler.applyVisibilityState();
                    cbConvex.setDisable(mode != 1);
                    if (onSave != null) onSave.run();
				});
                crystalRow.getChildren().add(rb);
			}
            root.getChildren().add(crystalRow);

            // ── 타이틀바: 제목 + 오른쪽 상단 [...] 버튼 ──────────────────
            Label titleLabel = new Label("KootPanKingThree 설정");
            titleLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(titleLabel, javafx.scene.layout.Priority.ALWAYS);
            titleLabel.setStyle("-fx-text-fill:#000000; -fx-font-weight:bold; -fx-padding:8 10 8 10;");

            Button btnAppearance = new Button("...");
            btnAppearance.setStyle("-fx-font-size:12px; -fx-padding:4 10 4 10; -fx-cursor:hand;");
            btnAppearance.setTooltip(new Tooltip("설정창 배경색 / 메뉴 폰트 변경"));
            btnAppearance.setOnAction(e -> openAppearanceDialog(s));

            HBox titleBar = new HBox(titleLabel, btnAppearance);
            titleBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            titleBar.setStyle("-fx-background-color:#f0f0f0; -fx-padding:0 6 0 0;");
            final double[] offset = new double[2];
            titleBar.setOnMousePressed(e -> {
                offset[0] = e.getScreenX() - s.getX();
                offset[1] = e.getScreenY() - s.getY();
			});
            titleBar.setOnMouseDragged(e -> {
                s.setX(e.getScreenX() - offset[0]);
                s.setY(e.getScreenY() - offset[1]);
			});

            // 설정창 내용이 많아졌으므로 세로 스크롤 추가 + 최소 너비 보장
            root.setMinWidth(920);
            root.setPrefWidth(920);

            ScrollPane scroll = new ScrollPane(root);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setStyle("-fx-background-color:transparent; -fx-background:transparent;");

            // 화면 높이의 85% 까지만 설정창이 커지도록 제한
            Screen curScreen = Screen.getScreensForRectangle(
				owner.getX() + state.viewportWidth * 0.5,
			owner.getY() + state.viewportHeight * 0.5, 1, 1)
			.stream().findFirst().orElse(Screen.getPrimary());
            double maxH = curScreen.getVisualBounds().getHeight() * 0.85;
            scroll.setMaxHeight(maxH);

            VBox container = new VBox(titleBar, scroll);
            Scene sc = new Scene(container, 980, 860);
            s.setScene(sc);
            s.sizeToScene();

            // 설정창은 마우스 커서가 있는 모니터(= 현재 시계를 보고 있는 모니터)의 중앙에 띄운다.
            // mainStage는 가상 데스크탑 전체를 덮으므로 owner 크기/위치로 모니터를 특정할 수 없다.
            // 시계 중심 좌표(JavaFX 논리픽셀)로 현재 모니터를 판별한다.
            // mainStage는 드래그로 이동 가능하고 시계는 항상 그 중앙에 렌더링되므로
            // mainStage.getX/Y() + viewport 절반 = 실제 시계가 표시되는 화면의 중심.
            // AWT MouseInfo는 물리픽셀이라 HiDPI 환경에서 JavaFX 좌표계와 불일치한다.
            double clockCX = owner.getX() + state.viewportWidth  * 0.5;
            double clockCY = owner.getY() + state.viewportHeight * 0.5;
            Screen targetScreen = Screen.getScreensForRectangle(clockCX, clockCY, 1, 1)
			.stream().findFirst()
			.orElse(Screen.getPrimary());
            final Rectangle2D vb = targetScreen.getVisualBounds();

            // show() 이후 runLater에서 setX/setY:
            // show() 전에는 s.getWidth/Height()가 0 → 중심 보정 불가
            // show() 후 runLater에서 정확한 창 크기로 중앙 정렬
            s.show();
            javafx.application.Platform.runLater(() -> {
                s.setX(vb.getMinX() + (vb.getWidth()  - s.getWidth())  * 0.5);
                s.setY(vb.getMinY() + (vb.getHeight() - s.getHeight()) * 0.5);
			});
            return s;
		}


        private void addDigitalSettingsSection(VBox root) {
            root.getChildren().add(section("● 디지탈 시계 설정"));

            java.util.List<String> allFonts = javafx.scene.text.Font.getFamilies();

            Label note = plain("날짜 행과 시분초 행의 모든 설정을 메인 설정으로 통합했습니다.");
            note.setStyle(note.getStyle() + " -fx-font-style:italic;");
            root.getChildren().add(note);

            GridPane dateGrid = new GridPane();
            dateGrid.setHgap(10);
            dateGrid.setVgap(8);

            CheckBox dateOnOff = checkbox("날짜 표시", state.showFaceDate, v -> {
                state.showFaceDate = v;
                refreshDigitalSection();
			});
            CheckBox dateNeonOn = checkbox("날짜 네온", state.neonFaceDate, v -> {
                state.neonFaceDate = v;
                assembler.updateFaceDateTimeTextures();
                refreshDigitalSection();
			});

            ComboBox<String> dateFmtBox = new ComboBox<>();
            dateFmtBox.getItems().addAll("N월 N일, 요일", "YYYY-MM-DD (요일)", "MM/DD (요일)", "N월 N일");
            dateFmtBox.getSelectionModel().select(state.faceDateFormatIndex);
            dateFmtBox.setPrefWidth(220);
            dateFmtBox.valueProperty().addListener((ob, ov, nv) -> {
                state.faceDateFormatIndex = Math.max(0, dateFmtBox.getSelectionModel().getSelectedIndex());
                refreshDigitalSection();
			});

            ComboBox<String> dateFontBox = new ComboBox<>();
            dateFontBox.getItems().addAll(allFonts);
            dateFontBox.setValue(state.faceDateFontFamily);
            dateFontBox.setPrefWidth(260);
            dateFontBox.valueProperty().addListener((ob, ov, nv) -> {
                if (nv != null) {
                    state.faceDateFontFamily = nv;
                    refreshDigitalSection();
				}
			});

            Spinner<Integer> dateSizeSpinner = new Spinner<>(8, 120, (int) state.faceDateFontSize);
            dateSizeSpinner.setEditable(true);
            dateSizeSpinner.valueProperty().addListener((ob, ov, nv) -> {
                if (nv != null) {
                    state.faceDateFontSize = nv;
                    refreshDigitalSection();
				}
			});

            int drgb = state.faceDateColorRgb;
            ColorPicker dateColorPicker = new ColorPicker(
			Color.rgb((drgb >> 16) & 0xFF, (drgb >> 8) & 0xFF, drgb & 0xFF, ((drgb >> 24) & 0xFF) / 255.0));
            dateColorPicker.setOnAction(e -> {
                Color c = dateColorPicker.getValue();
                state.faceDateColorRgb =
				(((int) Math.round(c.getOpacity() * 255)) << 24) |
				(((int) Math.round(c.getRed() * 255)) << 16) |
				(((int) Math.round(c.getGreen() * 255)) << 8) |
				((int) Math.round(c.getBlue() * 255));
                refreshDigitalSection();
			});

            ToggleGroup dateDirGroup = new ToggleGroup();
            RadioButton drbFixed = neonRadio("고정", dateDirGroup, state.faceDateScrollDir == 0);
            RadioButton drbRTL   = neonRadio("우→좌", dateDirGroup, state.faceDateScrollDir == 1);
            RadioButton drbLTR   = neonRadio("좌→우", dateDirGroup, state.faceDateScrollDir == 2);
            RadioButton drbPing  = neonRadio("핑퐁", dateDirGroup, state.faceDateScrollDir == 3);
            dateDirGroup.selectedToggleProperty().addListener((ob, ov, nv) -> {
                if (nv == drbFixed) state.faceDateScrollDir = 0;
                else if (nv == drbLTR) state.faceDateScrollDir = 2;
                else if (nv == drbPing) state.faceDateScrollDir = 3;
                else state.faceDateScrollDir = 1;
                state.faceDateScrollOffset = Double.NaN;
                refreshDigitalSection();
			});
            HBox dateDirRow = new HBox(10, drbFixed, drbRTL, drbLTR, drbPing);
            dateDirRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Slider dateSpeedSlider = slider(0.2, 6.0, state.faceDateScrollSpeed);
            Label dateSpeedVal = value(String.format("%.1f", state.faceDateScrollSpeed));
            dateSpeedSlider.valueProperty().addListener((ob, ov, nv) -> {
                state.faceDateScrollSpeed = nv.doubleValue();
                dateSpeedVal.setText(String.format("%.1f", state.faceDateScrollSpeed));
			});

            dateGrid.add(dateOnOff, 0, 0);
            dateGrid.add(dateNeonOn, 1, 0);
            dateGrid.add(plain("날짜 형식"), 0, 1); dateGrid.add(dateFmtBox, 1, 1, 3, 1);
            dateGrid.add(plain("날짜 폰트"), 0, 2); dateGrid.add(dateFontBox, 1, 2, 3, 1);
            dateGrid.add(plain("날짜 크기"), 0, 3); dateGrid.add(dateSizeSpinner, 1, 3);
            dateGrid.add(plain("날짜 색상"), 2, 3); dateGrid.add(dateColorPicker, 3, 3);
            dateGrid.add(plain("날짜 스크롤"), 0, 4); dateGrid.add(dateDirRow, 1, 4, 3, 1);
            dateGrid.add(plain("날짜 속도"), 0, 5); dateGrid.add(dateSpeedSlider, 1, 5, 2, 1); dateGrid.add(dateSpeedVal, 3, 5);
            root.getChildren().add(dateGrid);

            GridPane timeGrid = new GridPane();
            timeGrid.setHgap(10);
            timeGrid.setVgap(8);

            CheckBox timeOnOff = checkbox("시분초 표시", state.showDigital, v -> {
                state.showDigital = v;
                refreshDigitalSection();
			});
            CheckBox timeNeonOn = checkbox("시분초 네온", state.neonFaceTime, v -> {
                state.neonFaceTime = v;
                assembler.updateFaceDateTimeTextures();
                refreshDigitalSection();
			});

            ComboBox<String> timeFmtBox = new ComboBox<>();
            timeFmtBox.getItems().addAll("HH:mm:ss", "hh:mm:ss a", "HH:mm", "hh:mm a");
            timeFmtBox.getSelectionModel().select(state.digitalFormatIndex);
            timeFmtBox.setPrefWidth(220);
            timeFmtBox.valueProperty().addListener((ob, ov, nv) -> {
                state.digitalFormatIndex = Math.max(0, timeFmtBox.getSelectionModel().getSelectedIndex());
                refreshDigitalSection();
			});

            ComboBox<String> timeFontBox = new ComboBox<>();
            timeFontBox.getItems().addAll(allFonts);
            timeFontBox.setValue(state.digitalFontFamily);
            timeFontBox.setPrefWidth(260);
            timeFontBox.valueProperty().addListener((ob, ov, nv) -> {
                if (nv != null) {
                    state.digitalFontFamily = nv;
                    refreshDigitalSection();
				}
			});

            Spinner<Integer> timeSizeSpinner = new Spinner<>(8, 120, (int) state.digitalFontSize);
            timeSizeSpinner.setEditable(true);
            timeSizeSpinner.valueProperty().addListener((ob, ov, nv) -> {
                if (nv != null) {
                    state.digitalFontSize = nv;
                    refreshDigitalSection();
				}
			});

            int trgb = state.digitalColorRgb;
            ColorPicker timeColorPicker = new ColorPicker(
			Color.rgb((trgb >> 16) & 0xFF, (trgb >> 8) & 0xFF, trgb & 0xFF, ((trgb >> 24) & 0xFF) / 255.0));
            timeColorPicker.setOnAction(e -> {
                Color c = timeColorPicker.getValue();
                state.digitalColorRgb =
				(((int) Math.round(c.getOpacity() * 255)) << 24) |
				(((int) Math.round(c.getRed() * 255)) << 16) |
				(((int) Math.round(c.getGreen() * 255)) << 8) |
				((int) Math.round(c.getBlue() * 255));
                refreshDigitalSection();
			});

            ToggleGroup timeDirGroup = new ToggleGroup();
            RadioButton trbFixed = neonRadio("고정", timeDirGroup, state.digitalScrollDir == 0);
            RadioButton trbRTL   = neonRadio("우→좌", timeDirGroup, state.digitalScrollDir == 1);
            RadioButton trbLTR   = neonRadio("좌→우", timeDirGroup, state.digitalScrollDir == 2);
            RadioButton trbPing  = neonRadio("핑퐁", timeDirGroup, state.digitalScrollDir == 3);
            timeDirGroup.selectedToggleProperty().addListener((ob, ov, nv) -> {
                if (nv == trbFixed) state.digitalScrollDir = 0;
                else if (nv == trbLTR) state.digitalScrollDir = 2;
                else if (nv == trbPing) state.digitalScrollDir = 3;
                else state.digitalScrollDir = 1;
                state.digitalScrollOffset = Double.NaN;
                state.faceScrollOffset = Double.NaN;
                refreshDigitalSection();
			});
            HBox timeDirRow = new HBox(10, trbFixed, trbRTL, trbLTR, trbPing);
            timeDirRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Slider timeSpeedSlider = slider(0.2, 6.0, state.digitalScrollSpeed);
            Label timeSpeedVal = value(String.format("%.1f", state.digitalScrollSpeed));
            timeSpeedSlider.valueProperty().addListener((ob, ov, nv) -> {
                state.digitalScrollSpeed = nv.doubleValue();
                timeSpeedVal.setText(String.format("%.1f", state.digitalScrollSpeed));
			});

            ComboBox<String> digitalBlinkBox = new ComboBox<>();
            digitalBlinkBox.getItems().addAll("NONE", "PULSE", "SHARP", "RANDOM");
            digitalBlinkBox.setValue(String.valueOf(state.digitalNeonBlinkStyle));
            digitalBlinkBox.setPrefWidth(160);
            digitalBlinkBox.valueProperty().addListener((ob, ov, nv) -> {
                if (nv != null) {
                    state.digitalNeonBlinkStyle = AppState.NeonBlinkStyle.valueOf(nv);
                    refreshDigitalSection();
				}
			});

            timeGrid.add(timeOnOff, 0, 0);
            timeGrid.add(timeNeonOn, 1, 0);
            timeGrid.add(plain("시분초 형식"), 0, 1); timeGrid.add(timeFmtBox, 1, 1, 3, 1);
            timeGrid.add(plain("시분초 폰트"), 0, 2); timeGrid.add(timeFontBox, 1, 2, 3, 1);
            timeGrid.add(plain("시분초 크기"), 0, 3); timeGrid.add(timeSizeSpinner, 1, 3);
            timeGrid.add(plain("시분초 색상"), 2, 3); timeGrid.add(timeColorPicker, 3, 3);
            timeGrid.add(plain("시분초 스크롤"), 0, 4); timeGrid.add(timeDirRow, 1, 4, 3, 1);
            timeGrid.add(plain("시분초 속도"), 0, 5); timeGrid.add(timeSpeedSlider, 1, 5, 2, 1); timeGrid.add(timeSpeedVal, 3, 5);
            timeGrid.add(plain("디지탈 네온 점멸"), 0, 6); timeGrid.add(digitalBlinkBox, 1, 6);
            root.getChildren().add(timeGrid);
		}

        private void refreshDigitalSection() {
            assembler.updateFaceDateTimeTextures();
            boolean visible = (state.showDigital || state.showFaceDate) && !state.transparentMode;
            assembler.faceDateTimeGroup.setVisible(visible);
            if (state.showDigital || state.showFaceDate) {
                assembler.buildFaceDateTimeGroup();
				} else {
                assembler.removeDigitalGroup();
			}
		}

        /** [...] 버튼 클릭 → 설정창 외관(배경색·메뉴 폰트) 변경 팝업 */
        private void openAppearanceDialog(Stage owner) {
            Stage popup = new Stage();
            popup.initOwner(owner);
            popup.initStyle(StageStyle.UTILITY);
            popup.setTitle("외관 설정");
            popup.setAlwaysOnTop(true);

            Label lb1 = new Label("배경색");
            ColorPicker bgPicker = new ColorPicker(Color.web(state.dialogBgColor));

            Label lb2 = new Label("메뉴 폰트");
            ChoiceBox<String> fontBox = new ChoiceBox<>();
            fontBox.getItems().addAll("System", "맑은 고딕", "나눔고딕", "나눔명조",
			"Arial", "Consolas", "Georgia", "Tahoma", "Verdana");
            fontBox.setValue(state.dialogFontFamily);

            Button btnApply = new Button("적용");
            btnApply.setDefaultButton(true);
            btnApply.setOnAction(ev -> {
                Color picked = bgPicker.getValue();
                state.dialogBgColor = String.format("#%02x%02x%02x",
					(int)(picked.getRed()   * 255),
					(int)(picked.getGreen() * 255),
				(int)(picked.getBlue()  * 255));
                state.dialogFontFamily = fontBox.getValue();
                popup.close();
                owner.close();     // 현재 설정창 닫기 → onHidden: setupStage=null
                onRebuild.run();   // 동기 순서 보장
                if (onSave != null) onSave.run();
			});

            GridPane gp = new GridPane();
            gp.setHgap(12); gp.setVgap(10);
            gp.setPadding(new Insets(16));
            gp.add(lb1,      0, 0); gp.add(bgPicker, 1, 0);
            gp.add(lb2,      0, 1); gp.add(fontBox,  1, 1);
            gp.add(btnApply, 1, 2);

            popup.setScene(new Scene(gp));
            popup.sizeToScene();
            popup.show();
		}

        /** root VBox에 dialogBgColor + dialogFontFamily CSS 적용 */
        private void applyRootStyle(VBox root) {
            String fontPart = "System".equals(state.dialogFontFamily)
			? "" : "-fx-font-family:'" + state.dialogFontFamily + "';";
            root.setStyle("-fx-background-color:" + state.dialogBgColor
			+ "; -fx-font-size:12px;" + fontPart);
		}

        /** 배경색 밝기(luminance)에 따라 텍스트 색을 자동 결정 */
        private String dialogTextColor() {
            try {
                Color c = Color.web(state.dialogBgColor);
                double lum = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
                return lum > 0.5 ? "#000000" : "#ffffff";
				} catch (Exception ignored) {
				AppLogger.logException(ignored);
                return "#000000";
			}
		}

        private HBox colorRow(String name, Color init, java.util.function.Consumer<Color> onChange) {
            Label lb = plain(name);
            lb.setMinWidth(90);
            ColorPicker cp = new ColorPicker(init);
            cp.setPrefWidth(92);
            cp.setOnAction(e -> onChange.accept(cp.getValue()));
            HBox row = new HBox(8, lb, cp);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            return row;
		}

        /**
			* 색깔 설정 행 + NEON 체크박스.
			* 레이아웃: [이름 라벨] [ColorPicker] [✦ NEON 체크박스]
		*/
        private HBox colorRowNeon(String name, Color init,
			java.util.function.Consumer<Color> onChange,
			boolean neonInit,
			java.util.function.Consumer<Boolean> onNeon) {
            Label lb = plain(name);
            lb.setMinWidth(52);

            ColorPicker cp = new ColorPicker(init);
            cp.setPrefWidth(88);

            CheckBox neonCb = new CheckBox("✦");
            neonCb.setSelected(neonInit);
            neonCb.setTooltip(new Tooltip("NEON 발광 효과"));
            updateNeonCbStyle(neonCb, neonInit, init);

            // cp.setOnAction 은 한 번만 등록 (이전에 두 번 등록하여 첫 번째가 덮어씌워지던 버그 수정)
            cp.setOnAction(e -> {
                onChange.accept(cp.getValue());
                updateNeonCbStyle(neonCb, neonCb.isSelected(), cp.getValue());
			});
            neonCb.setOnAction(e -> {
                boolean checked = neonCb.isSelected();
                onNeon.accept(checked);
                updateNeonCbStyle(neonCb, checked, cp.getValue());
			});

            HBox row = new HBox(6, lb, cp, neonCb);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            return row;
		}

        /** NEON 체크박스 스타일: 체크 시 형광색 텍스트, 미체크 시 기본 색 */
        private void updateNeonCbStyle(CheckBox cb, boolean on, Color base) {
            if (on) {
                // 베이스 컬러에서 HSB 채도 MAX, 밝기 1.0으로 형광 색 계산
                String neonHex = toHexColor(Color.hsb(base.getHue(), 1.0, 1.0));
                cb.setStyle("-fx-text-fill:" + neonHex
					+ "; -fx-font-weight:bold;"
				+ " -fx-effect: dropshadow(gaussian," + neonHex + ",6,0.8,0,0);");
				} else {
                cb.setStyle("-fx-text-fill:" + dialogTextColor() + ";");
			}
		}

        private static String toHexColor(Color c) {
            return String.format("#%02x%02x%02x",
				(int)(c.getRed()   * 255),
				(int)(c.getGreen() * 255),
			(int)(c.getBlue()  * 255));
		}

        private CheckBox checkbox(String text, boolean init, java.util.function.Consumer<Boolean> onChange) {
            CheckBox cb = new CheckBox(text);
            cb.setSelected(init);
            cb.setStyle("-fx-text-fill:" + dialogTextColor() + ";");
            cb.setOnAction(e -> onChange.accept(cb.isSelected()));
            return cb;
		}

        private Slider slider(double min, double max, double value) {
            Slider s = new Slider(min, max, value);
            s.setPrefWidth(180);
            return s;
		}

        private Label section(String s) {
            Label l = new Label(s);
            l.setStyle("-fx-text-fill:" + dialogTextColor()
			+ "; -fx-font-weight:bold; -fx-padding:6 0 2 0;");
            return l;
		}

        private Label plain(String s) {
            Label l = new Label(s);
            l.setStyle("-fx-text-fill:" + dialogTextColor() + ";");
            return l;
		}

        private Label value(String s) {
            Label l = plain(s);
            l.setMinWidth(46);
            return l;
		}

        /** 네온 점멸 스타일 라디오 버튼 */
        private RadioButton neonRadio(String text, ToggleGroup group, boolean selected) {
            RadioButton rb = new RadioButton(text);
            rb.setToggleGroup(group);
            rb.setSelected(selected);
            rb.setStyle("-fx-text-fill:" + dialogTextColor() + "; -fx-font-size:11px;");
            return rb;
		}
	}

    // ───────────────────────── FX INI Manager ─────────────────────────
    /**
		* 인스턴스별 시계 상태를 INI 파일에 저장/복원한다.
		* 모든 키는 _FX. 접두어를 사용한다. 기존 INI 의 다른 키는 보존된다.
	*/
    final class FxIniManager {
        private final String iniPath;

        FxIniManager(String iniPath) { this.iniPath = iniPath; }

        // ── 저장 ──────────────────────────────────────────────────────
        synchronized void save(AppState st, double stageX, double stageY) {
            java.util.Properties p = loadRaw();
            // 창 위치
            set(p, "_FX.stageX",    stageX);
            set(p, "_FX.stageY",    stageY);
            // 형태
            set(p, "_FX.coinRadius",           st.coinRadius);
            set(p, "_FX.clockOpacity",         st.clockOpacity);
            set(p, "_FX.paused",               st.paused);
            set(p, "_FX.savedSpeed",           st.savedSpeed);
            set(p, "_FX.swingRangeY",          st.swingRangeY);
            set(p, "_FX.swingRangeX",          st.swingRangeX);
            set(p, "_FX.baseAngleX",           st.baseAngleX);
            set(p, "_FX.showNumbers",          st.showNumbers);
            set(p, "_FX.showGlass",            st.showGlass);
            set(p, "_FX.glassOpacity",         st.glassOpacity);
            set(p, "_FX.showConvexGlass",      st.showConvexGlass);
            set(p, "_FX.crystalMode",          st.crystalMode);
            set(p, "_FX.transparentMode",      st.transparentMode);
            set(p, "_FX.youtubeScale",         st.youtubeScale);
            set(p, "_FX.numberHeightScale",    st.numberHeightScale);
            set(p, "_FX.numberFont",           st.numberFont);
            set(p, "_FX.oneMinuteTickHeight",  st.oneMinuteTickHeight);
            set(p, "_FX.fiveMinuteTickHeight", st.fiveMinuteTickHeight);
            set(p, "_FX.cameraDistance",       st.cameraDistance);
            // 색상 (hex)
            set(p, "_FX.faceColor",            colorToHex(st.faceColor));
            set(p, "_FX.backColor",            colorToHex(st.backColor));
            set(p, "_FX.rimColor",             colorToHex(st.rimColor));
            set(p, "_FX.hourHandColor",        colorToHex(st.hourHandColor));
            set(p, "_FX.minuteHandColor",      colorToHex(st.minuteHandColor));
            set(p, "_FX.secondHandColor",      colorToHex(st.secondHandColor));
            set(p, "_FX.fiveMinuteTickColor",  colorToHex(st.fiveMinuteTickColor));
            set(p, "_FX.oneMinuteTickColor",   colorToHex(st.oneMinuteTickColor));
            set(p, "_FX.numberColor",          colorToHex(st.numberColor));
            // NEON 플래그
            set(p, "_FX.neonFace",             st.neonFace);
            set(p, "_FX.neonBack",             st.neonBack);
            set(p, "_FX.neonRim",              st.neonRim);
            set(p, "_FX.neonHourHand",         st.neonHourHand);
            set(p, "_FX.neonMinuteHand",       st.neonMinuteHand);
            set(p, "_FX.neonSecondHand",       st.neonSecondHand);
            set(p, "_FX.neonFiveMinuteTick",   st.neonFiveMinuteTick);
            set(p, "_FX.neonOneMinuteTick",    st.neonOneMinuteTick);
            set(p, "_FX.neonNumber",           st.neonNumber);
            set(p, "_FX.neonBlinkStyle",       st.neonBlinkStyle.name());
            set(p, "_FX.neonFlickerSpeed",     st.neonFlickerSpeed);
            set(p, "_FX.neonFlickerDepth",     st.neonFlickerDepth);
            // 레인보우
            set(p, "_FX.rainbowMode",          st.rainbowMode);
            set(p, "_FX.rainbowIntervalSec",   st.rainbowIntervalSec);
            // 디지탈 시계
            set(p, "_FX.showDigital",              st.showDigital);
            set(p, "_FX.digitalFormatIndex",       st.digitalFormatIndex);
            set(p, "_FX.digitalFontFamily",        st.digitalFontFamily);
            set(p, "_FX.digitalFontSize",          st.digitalFontSize);
            set(p, "_FX.digitalColorRgb",          st.digitalColorRgb);
            set(p, "_FX.digitalScrollDir",         st.digitalScrollDir);
            set(p, "_FX.digitalScrollSpeed",       st.digitalScrollSpeed);
            set(p, "_FX.neonFaceTime",             st.neonFaceTime);
            set(p, "_FX.digitalNeonBlinkStyle",    st.digitalNeonBlinkStyle.name());
            // 날짜
            set(p, "_FX.showFaceDate",             st.showFaceDate);
            set(p, "_FX.faceDateFormatIndex",      st.faceDateFormatIndex);
            set(p, "_FX.faceDateFontFamily",       st.faceDateFontFamily);
            set(p, "_FX.faceDateFontSize",         st.faceDateFontSize);
            set(p, "_FX.faceDateColorRgb",         st.faceDateColorRgb);
            set(p, "_FX.faceDateScrollDir",        st.faceDateScrollDir);
            set(p, "_FX.faceDateScrollSpeed",      st.faceDateScrollSpeed);
            set(p, "_FX.neonFaceDate",             st.neonFaceDate);
            // 배경
            set(p, "_FX.backgroundImageFile",
			st.backgroundImageFile != null ? st.backgroundImageFile.getAbsolutePath() : "");
            set(p, "_FX.slideshowEnabled",         st.slideshowEnabled);
            set(p, "_FX.slideshowIntervalNanos",   st.slideshowIntervalNanos);
            if (st.slideshowFiles != null && !st.slideshowFiles.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (java.io.File f : st.slideshowFiles) {
                    if (sb.length() > 0) sb.append(';');
                    sb.append(f.getAbsolutePath());
				}
                p.setProperty("_FX.slideshowFiles", sb.toString());
				} else {
                p.setProperty("_FX.slideshowFiles", "");
			}
            // 다이얼로그 외관
            set(p, "_FX.dialogBgColor",            st.dialogBgColor);
            set(p, "_FX.dialogFontFamily",         st.dialogFontFamily);
            // 세계시계
            set(p, "_FX.timeZone",                 st.theTimeZone.getId());
            set(p, "_FX.cityPrefix",               st.cityPrefix);
            writeRaw(p);
		}

        /**
			* INI 에서 _FX.* 키를 AppState 에 복원한다.
			* @return [stageX, stageY] — 저장된 값 없으면 [NaN, NaN]
		*/
        double[] load(AppState st) {
            java.util.Properties p = loadRaw();
            if (p.isEmpty()) return new double[]{Double.NaN, Double.NaN};

            double stageX = getDouble(p, "_FX.stageX", Double.NaN);
            double stageY = getDouble(p, "_FX.stageY", Double.NaN);
            // 형태
            st.coinRadius           = getDouble(p, "_FX.coinRadius",           st.coinRadius);
            st.clockOpacity         = getDouble(p, "_FX.clockOpacity",         st.clockOpacity);
            st.paused               = getBool  (p, "_FX.paused",               st.paused);
            st.savedSpeed           = getDouble(p, "_FX.savedSpeed",           st.savedSpeed);
            st.autoSpeed            = st.paused ? 0.0 : st.savedSpeed;
            st.swingRangeY          = getDouble(p, "_FX.swingRangeY",          st.swingRangeY);
            st.swingRangeX          = getDouble(p, "_FX.swingRangeX",          st.swingRangeX);
            st.baseAngleX           = getDouble(p, "_FX.baseAngleX",           st.baseAngleX);
            st.showNumbers          = getBool  (p, "_FX.showNumbers",          st.showNumbers);
            st.showGlass            = getBool  (p, "_FX.showGlass",            st.showGlass);
            st.glassOpacity         = getDouble(p, "_FX.glassOpacity",         st.glassOpacity);
            st.showConvexGlass      = getBool  (p, "_FX.showConvexGlass",      st.showConvexGlass);
            st.crystalMode          = getInt   (p, "_FX.crystalMode",          st.crystalMode);
            st.transparentMode      = getBool  (p, "_FX.transparentMode",      st.transparentMode);
            st.youtubeScale         = getDouble(p, "_FX.youtubeScale",         st.youtubeScale);
            st.numberHeightScale    = getDouble(p, "_FX.numberHeightScale",    st.numberHeightScale);
            st.numberFont           = getStr   (p, "_FX.numberFont",           st.numberFont);
            st.oneMinuteTickHeight  = getDouble(p, "_FX.oneMinuteTickHeight",  st.oneMinuteTickHeight);
            st.fiveMinuteTickHeight = getDouble(p, "_FX.fiveMinuteTickHeight", st.fiveMinuteTickHeight);
            st.cameraDistance       = getDouble(p, "_FX.cameraDistance",       st.cameraDistance);
            // 색상
            st.faceColor           = hexToColor(p, "_FX.faceColor",           st.faceColor);
            st.backColor           = hexToColor(p, "_FX.backColor",           st.backColor);
            st.rimColor            = hexToColor(p, "_FX.rimColor",            st.rimColor);
            st.hourHandColor       = hexToColor(p, "_FX.hourHandColor",       st.hourHandColor);
            st.minuteHandColor     = hexToColor(p, "_FX.minuteHandColor",     st.minuteHandColor);
            st.secondHandColor     = hexToColor(p, "_FX.secondHandColor",     st.secondHandColor);
            st.fiveMinuteTickColor = hexToColor(p, "_FX.fiveMinuteTickColor", st.fiveMinuteTickColor);
            st.oneMinuteTickColor  = hexToColor(p, "_FX.oneMinuteTickColor",  st.oneMinuteTickColor);
            st.numberColor         = hexToColor(p, "_FX.numberColor",         st.numberColor);
            // NEON
            st.neonFace             = getBool(p, "_FX.neonFace",           st.neonFace);
            st.neonBack             = getBool(p, "_FX.neonBack",           st.neonBack);
            st.neonRim              = getBool(p, "_FX.neonRim",            st.neonRim);
            st.neonHourHand         = getBool(p, "_FX.neonHourHand",       st.neonHourHand);
            st.neonMinuteHand       = getBool(p, "_FX.neonMinuteHand",     st.neonMinuteHand);
            st.neonSecondHand       = getBool(p, "_FX.neonSecondHand",     st.neonSecondHand);
            st.neonFiveMinuteTick   = getBool(p, "_FX.neonFiveMinuteTick", st.neonFiveMinuteTick);
            st.neonOneMinuteTick    = getBool(p, "_FX.neonOneMinuteTick",  st.neonOneMinuteTick);
            st.neonNumber           = getBool(p, "_FX.neonNumber",         st.neonNumber);
            String blinkName = getStr(p, "_FX.neonBlinkStyle", "");
            if (!blinkName.isEmpty()) {
                try { st.neonBlinkStyle = AppState.NeonBlinkStyle.valueOf(blinkName); }
                catch (Exception ignored) {}
			}
            st.neonFlickerSpeed   = getDouble(p, "_FX.neonFlickerSpeed", st.neonFlickerSpeed);
            st.neonFlickerDepth   = getDouble(p, "_FX.neonFlickerDepth", st.neonFlickerDepth);
            // 레인보우
            st.rainbowMode        = getBool  (p, "_FX.rainbowMode",        st.rainbowMode);
            st.rainbowIntervalSec = getDouble(p, "_FX.rainbowIntervalSec", st.rainbowIntervalSec);
            // 디지탈
            st.showDigital        = getBool  (p, "_FX.showDigital",           st.showDigital);
            st.digitalFormatIndex = getInt   (p, "_FX.digitalFormatIndex",    st.digitalFormatIndex);
            st.digitalFontFamily  = getStr   (p, "_FX.digitalFontFamily",     st.digitalFontFamily);
            st.digitalFontSize    = getDouble(p, "_FX.digitalFontSize",       st.digitalFontSize);
            st.digitalColorRgb    = getInt   (p, "_FX.digitalColorRgb",       st.digitalColorRgb);
            st.digitalScrollDir   = getInt   (p, "_FX.digitalScrollDir",      st.digitalScrollDir);
            st.digitalScrollSpeed = getDouble(p, "_FX.digitalScrollSpeed",    st.digitalScrollSpeed);
            st.neonFaceTime       = getBool  (p, "_FX.neonFaceTime",          st.neonFaceTime);
            String dnb = getStr(p, "_FX.digitalNeonBlinkStyle", "");
            if (!dnb.isEmpty()) {
                try { st.digitalNeonBlinkStyle = AppState.NeonBlinkStyle.valueOf(dnb); }
                catch (Exception ignored) {}
			}
            // 날짜
            st.showFaceDate        = getBool  (p, "_FX.showFaceDate",        st.showFaceDate);
            st.faceDateFormatIndex = getInt   (p, "_FX.faceDateFormatIndex", st.faceDateFormatIndex);
            st.faceDateFontFamily  = getStr   (p, "_FX.faceDateFontFamily",  st.faceDateFontFamily);
            st.faceDateFontSize    = getDouble(p, "_FX.faceDateFontSize",    st.faceDateFontSize);
            st.faceDateColorRgb    = getInt   (p, "_FX.faceDateColorRgb",    st.faceDateColorRgb);
            st.faceDateScrollDir   = getInt   (p, "_FX.faceDateScrollDir",   st.faceDateScrollDir);
            st.faceDateScrollSpeed = getDouble(p, "_FX.faceDateScrollSpeed", st.faceDateScrollSpeed);
            st.neonFaceDate        = getBool  (p, "_FX.neonFaceDate",        st.neonFaceDate);
            // 배경
            String bgPath = getStr(p, "_FX.backgroundImageFile", "");
            if (!bgPath.isEmpty()) {
                java.io.File bgFile = new java.io.File(bgPath);
                if (bgFile.exists()) st.backgroundImageFile = bgFile;
			}
            st.slideshowEnabled       = getBool(p, "_FX.slideshowEnabled",      st.slideshowEnabled);
            st.slideshowIntervalNanos = getLong(p, "_FX.slideshowIntervalNanos", st.slideshowIntervalNanos);
            String ssFiles = getStr(p, "_FX.slideshowFiles", "");
            if (!ssFiles.isEmpty()) {
                st.slideshowFiles.clear();
                for (String fp : ssFiles.split(";")) {
                    if (!fp.trim().isEmpty()) {
                        java.io.File f = new java.io.File(fp.trim());
                        if (f.exists()) st.slideshowFiles.add(f);
					}
				}
			}
            // 다이얼로그 외관
            st.dialogBgColor    = getStr(p, "_FX.dialogBgColor",    st.dialogBgColor);
            st.dialogFontFamily = getStr(p, "_FX.dialogFontFamily", st.dialogFontFamily);
            // 세계시계
            String tz = getStr(p, "_FX.timeZone", "");
            if (!tz.isEmpty()) {
                try { st.theTimeZone = java.time.ZoneId.of(tz); }
                catch (Exception ignored) {}
			}
            st.cityPrefix = getStr(p, "_FX.cityPrefix", st.cityPrefix);
            System.out.println("[FxIni] 복원 완료: " + iniPath);
            return new double[]{stageX, stageY};
		}

        // ── 파일 I/O ─────────────────────────────────────────────────
        private java.util.Properties loadRaw() {
            java.util.Properties p = new java.util.Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(iniPath)) {
                p.load(fis);
			} catch (Exception ignored) {}
            return p;
		}
        private synchronized void writeRaw(java.util.Properties p) {
            try {
                java.io.File f = new java.io.File(iniPath);
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
                    p.store(fos, "KootPanKingThree FX Clock State");
				}
			} catch (Exception e) { AppLogger.logException(e); }
		}
        // ── set 헬퍼 ─────────────────────────────────────────────────
        private void set(java.util.Properties p, String k, double v)  { p.setProperty(k, String.valueOf(v)); }
        private void set(java.util.Properties p, String k, int v)     { p.setProperty(k, String.valueOf(v)); }
        private void set(java.util.Properties p, String k, long v)    { p.setProperty(k, String.valueOf(v)); }
        private void set(java.util.Properties p, String k, boolean v) { p.setProperty(k, String.valueOf(v)); }
        private void set(java.util.Properties p, String k, String v)  { p.setProperty(k, v != null ? v : ""); }
        // ── get 헬퍼 ─────────────────────────────────────────────────
        private String  getStr   (java.util.Properties p, String k, String def)  {
            String v = p.getProperty(k); return (v == null || v.isEmpty()) ? def : v;
		}
        private double  getDouble(java.util.Properties p, String k, double def) {
            try { return Double.parseDouble(p.getProperty(k,"")); } catch (Exception e) { return def; }
		}
        private int     getInt   (java.util.Properties p, String k, int def) {
            try { return Integer.parseInt(p.getProperty(k,"")); } catch (Exception e) { return def; }
		}
        private long    getLong  (java.util.Properties p, String k, long def) {
            try { return Long.parseLong(p.getProperty(k,"")); } catch (Exception e) { return def; }
		}
        private boolean getBool  (java.util.Properties p, String k, boolean def) {
            String v = p.getProperty(k); return (v == null) ? def : Boolean.parseBoolean(v);
		}
        // ── 색상 변환 ─────────────────────────────────────────────────
        private String colorToHex(javafx.scene.paint.Color c) {
            if (c == null) return "";
            return String.format("#%02x%02x%02x",
			(int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
		}
        private javafx.scene.paint.Color hexToColor(
			java.util.Properties p, String k, javafx.scene.paint.Color def) {
            String hex = p.getProperty(k, "");
            if (hex.isEmpty()) return def;
            try { return javafx.scene.paint.Color.web(hex); }
            catch (Exception e) { return def; }
		}
	}
}
