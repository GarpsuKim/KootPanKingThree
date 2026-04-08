import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
	* FxSplashWindow — SplashWindow 의 JavaFX 구현체
	*
	* ── 기능 ─────────────────────────────────────────────────────────
	*   • 시계(KootPanKingThree) 표시 전 가장 먼저 열림
	*   • 스크롤 가능한 로그 텍스트 창 — 진행 메시지를 누적 표시
	*   • 메뉴바: [File] [세계시계] [도구] [생활도구] [업무도구] [Help]
	*
	* ── 예외 사항 ────────────────────────────────────────────────────
	*   • File 메뉴 : Global 항목 삭제
	*   • 세계시계(Global) 메뉴 : 보존, 구현체 삭제 (stub)
	*   • 도구 메뉴 : 알람관리 삭제
	*   • 생활도구 메뉴 : 현재위치 삭제
	*   • Help 메뉴 : 프로그램 업그레이드 보존, 구현체 삭제 (stub)
	*
	* ── ClockHostCallback ───────────────────────────────────────────
	*   KootPanKing Three 가 구현체를 setClockHost() 로 주입한다.
*/
public class FxSplashWindow {
	
    // ── 색 상수 ──────────────────────────────────────────────────
    private static final String BG      = "linear-gradient(to bottom right, #fff7fb 0%, #ffe8f4 45%, #ffd6eb 100%)";
    private static final String FG      = "#6b2148";
    private static final String TS_CLR  = "#d14f92";
    private static final String BAR_BG  = "rgba(255, 214, 235, 0.78)";
    private static final String MENU_BG = "-fx-background-color: rgba(255, 196, 224, 0.72);"
	+ "-fx-background-insets: 0;"
	+ "-fx-border-color: rgba(209, 79, 146, 0.45);"
	+ "-fx-border-width: 0 0 1 0;";
    private static final String GLASS_PANEL = "rgba(255, 255, 255, 0.32)";
    private static final String GLASS_BORDER = "rgba(209, 79, 146, 0.35)";
    private static final String GLASS_HOVER  = "rgba(255, 255, 255, 0.52)";
	
    // ── 테마 모드 ───────────────────────────────────────────────
    private enum ThemeMode { BASIC, PINK_GLASS }
    private ThemeMode themeMode = ThemeMode.PINK_GLASS;
	
    private static final String LOG_STYLE =
	"-fx-font-family: 'Malgun Gothic'; -fx-font-size: 13px;"
	+ "-fx-text-fill: " + FG + ";"
	+ "-fx-background-color: " + BG + ";"
	+ "-fx-control-inner-background: rgba(255,255,255,0.30);"
	+ "-fx-background-radius: 16;"
	+ "-fx-border-color: " + GLASS_BORDER + ";"
	+ "-fx-border-radius: 16;"
	+ "-fx-border-width: 1;";
	
    // ── UI 컴포넌트 ───────────────────────────────────────────────
    private final Stage     stage;
    private final TextArea  logArea;
    private final Label     statusBar;
	
    // ── 시계 호스트 ───────────────────────────────────────────────
    private ClockHostCallback clockHost = null;
	
    // ═══════════════════════════════════════════════════════════
    //  ClockHostCallback
    // ═══════════════════════════════════════════════════════════
    public interface ClockHostCallback {
        /** 세계시계 서브메뉴 (Menu 반환) */
        javafx.scene.control.Menu buildGlobalMenu();
        /** 프로그램 완전 종료 */
        void exitAll();
        /** Help → Log 조회 */
        void showLogFile();
        /** Help → 지난 Log 삭제 */
        void deleteOldLogs();
        /** 현재 로그 파일 경로 */
        String getLogFilePath();
        /** Help → 기본설정파일 */
        void showConfigFile();
        /** Help → About */
        void showAbout();
        /** 설정 파일 경로 */
        String getConfigFilePath();
        /** X 버튼 / Close */
        void onClose();
        /** 도구 → 차임벨 설정 */
        void showChimeDialog();
        /** 도구 → Gmail/Calendar 서브메뉴 */
        javafx.scene.control.Menu buildGmailCalendarMenu();
        /** 도구 → 카카오톡 서브메뉴 */
        javafx.scene.control.Menu buildKakaoMenu();
        /** 도구 → 텔레그램 서브메뉴 */
        javafx.scene.control.Menu buildTelegramMenu();
        /** ini config 읽기 */
        String getConfig(String key, String defaultValue);
        /** ini config 여러 개 쓰기 + 저장 */
        void setMultipleConfigAndSave(String... entries);
        /** ini config 1개 쓰기 + 저장 */
        void setConfigAndSave(String key, String value);
        /** GmailSender 접근 */
        GmailSender getGmail();
        /** 시계 창을 현재 모니터 우상단으로 이동 */
        void moveToTopRight();
	}
	
    // ═══════════════════════════════════════════════════════════
    //  생성자
    // ═══════════════════════════════════════════════════════════
    public FxSplashWindow(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("끝판왕 (KootPanKingThree Ver 1.1)");
		
        // ── 로그 영역 ────────────────────────────────────────────
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setStyle(logStyle());
        logArea.setFont(Font.font("Malgun Gothic", 13));
		
        ScrollPane scrollPane = new ScrollPane(logArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle(scrollPaneStyle());
        scrollPane.viewportBoundsProperty();
		
        // ── 상태바 ───────────────────────────────────────────────
        statusBar = new Label(" 준비");
        statusBar.setMaxWidth(Double.MAX_VALUE);
        statusBar.setStyle(
		statusBarStyle());
		
        // ── 루트 레이아웃 ────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setStyle(rootStyle());
        root.setCenter(scrollPane);
        root.setBottom(statusBar);
        root.setTop(buildMenuBar());
		
        Scene scene = new Scene(root, 860, 520);
        stage.setScene(scene);
		
        // ── 창 닫기 이벤트 ───────────────────────────────────────
        stage.setOnCloseRequest((WindowEvent e) -> {
            e.consume();
            doClose();
		});
		
        stage.show();
	}
	
    // ═══════════════════════════════════════════════════════════
    //  공개 API
    // ═══════════════════════════════════════════════════════════
	
    /** ClockHostCallback 주입. 메뉴바 재빌드. */
    public void setClockHost(ClockHostCallback cb) {
        this.clockHost = cb;
        Platform.runLater(() ->
		((BorderPane) stage.getScene().getRoot()).setTop(buildMenuBar()));
	}
	
    /** 로그 메시지 추가 (FX 스레드 안팎 모두 안전). */
    public void log(String message) {
        if (Platform.isFxApplicationThread()) {
            appendLog(message);
			} else {
            Platform.runLater(() -> appendLog(message));
		}
	}
	
    /** 구분선 추가 */
    public void logSep() {
        log("─────────────────────────────────────────────────────────────");
	}
	
    /** 상태바 텍스트 갱신 */
    public void setStatus(String text) {
        Platform.runLater(() -> setRuntimeStatus(text));
	}
	
    public Stage getStage() { return stage; }
	
    // ═══════════════════════════════════════════════════════════
    //  메뉴바 빌드
    // ═══════════════════════════════════════════════════════════
    private MenuBar buildMenuBar() {
        MenuBar bar = new MenuBar();
        bar.setStyle(menuBarStyle());
        bar.getMenus().addAll(
            buildFileMenu(),
            buildGlobalMenu(),
            buildToolsMenu(),
            buildLifeMenu(),
            buildOfficeMenu(),
            buildHelpMenu()
		);
        return bar;
	}
	
    // ── [File] 메뉴 ──────────────────────────────────────────────
    //   • Global 항목 삭제 (요구사항 2)
    private Menu buildFileMenu000() {
        Menu menu = makeMenu("File", "파일 열기, 창 닫기, 프로그램 종료");
		
        menu.getItems().add(makeSectionHeader("기본 기능"));
		
        // Open
        MenuItem openItem = makeRichMenuItem("📂", "Open",
		"텍스트 파일을 열어 새 창에 표시", "Ctrl+O", this::doOpen);
        openItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+O"));
        menu.getItems().add(openItem);
		
        // Close
        MenuItem closeItem = makeRichMenuItem("🪟", "Close",
		"이 창을 닫습니다 (시계는 유지)", "Ctrl+W", this::doClose);
        closeItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+W"));
        menu.getItems().add(closeItem);
		
        // Exit
        MenuItem exitItem = makeRichMenuItem("⏻", "Exit",
		"프로그램을 종료합니다", "Ctrl+Q", this::doExit);
        exitItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+Q"));
        menu.getItems().add(exitItem);
		
        return menu;
	}
	
    // ── [세계시계] 메뉴 — 보존, 구현체 삭제 (요구사항 3) ──────────
    private Menu buildGlobalMenu() {
        if (clockHost != null) {
            Menu m = clockHost.buildGlobalMenu();
            if (m != null) {
                m.setText("🌍 세계시계");
                return m;
			}
		}
        // clockHost 미주입 or 반환값 없음 → stub
        Menu stub = makeMenu("🌍 세계시계");
        MenuItem disabled = makeMenuItem("시계 초기화 후 사용 가능", null);
        disabled.setDisable(true);
        stub.getItems().add(disabled);
        return stub;
	}
	
    // ── [도구] 메뉴 — 알람관리 삭제 (요구사항 4) ─────────────────
    private Menu buildToolsMenu000() {
        Menu menu = makeMenu("도구");
		
        // 차임벨 설정
        MenuItem chimeItem = makeMenuItem("차임벨 설정...", null);
        chimeItem.setOnAction(e -> { if (clockHost != null) clockHost.showChimeDialog(); });
        menu.getItems().add(chimeItem);
		
        // ✂ 알람 관리 삭제 (요구사항 4)
		
        menu.getItems().add(new SeparatorMenuItem());
		
        // Gmail / Calendar
        if (clockHost != null) {
            Menu gmailMenu = clockHost.buildGmailCalendarMenu();
            if (gmailMenu != null) menu.getItems().add(gmailMenu);
			} else {
            menu.getItems().add(makeDisabledItem("Gmail / Calendar"));
		}
		
        // 카카오톡
        if (clockHost != null) {
            Menu kakaoMenu = clockHost.buildKakaoMenu();
            if (kakaoMenu != null) menu.getItems().add(kakaoMenu);
			} else {
            menu.getItems().add(makeDisabledItem("카카오톡"));
		}
		
        // 텔레그램
        if (clockHost != null) {
            Menu tgMenu = clockHost.buildTelegramMenu();
            if (tgMenu != null) menu.getItems().add(tgMenu);
			} else {
            menu.getItems().add(makeDisabledItem("텔레그램"));
		}
		
        return menu;
	}
	
    // ── [생활도구] 메뉴 — 현재위치 삭제 (요구사항 5) ─────────────
	
	
    // ── [업무도구] 메뉴 ──────────────────────────────────────────
    private static final int OFFICE_SLOT_COUNT = 20;
    private static String slotNameKey(int i) { return "office.slot." + i + ".name"; }
    private static String slotPathKey(int i) { return "office.slot." + i + ".path"; }
	
	
	
    private Menu buildSlotMenu(int idx) {
        String name = (clockHost != null) ? clockHost.getConfig(slotNameKey(idx), "") : "";
        String path = (clockHost != null) ? clockHost.getConfig(slotPathKey(idx), "") : "";
        boolean reg = !name.isEmpty() && !path.isEmpty();
		
        Menu slotMenu = makeMenu(reg ? "📌 " + name : "── 빈 슬롯 " + (idx + 1) + " ──");
		
        if (reg) {
            MenuItem runItem = makeMenuItem("▶  실행", path);
            final String p = path;
            runItem.setOnAction(e -> launchByPath(p));
            slotMenu.getItems().add(runItem);
            slotMenu.getItems().add(new SeparatorMenuItem());
			
            MenuItem editItem = makeMenuItem("✏️  수정", null);
            editItem.setOnAction(e -> openSlotEditor(idx));
            slotMenu.getItems().add(editItem);
			
            MenuItem delItem = makeMenuItem("🗑️  삭제", null);
            delItem.setOnAction(e -> deleteSlot(idx, name));
            slotMenu.getItems().add(delItem);
			
            slotMenu.getItems().add(new SeparatorMenuItem());
			
            MenuItem upItem = makeMenuItem("⬆️  위로 이동", null);
            upItem.setDisable(idx <= 0);
            upItem.setOnAction(e -> { swapSlots(idx, idx - 1); rebuildMenuBar(); });
            slotMenu.getItems().add(upItem);
			
            MenuItem downItem = makeMenuItem("⬇️  아래로 이동", null);
            downItem.setDisable(idx >= OFFICE_SLOT_COUNT - 1);
            downItem.setOnAction(e -> { swapSlots(idx, idx + 1); rebuildMenuBar(); });
            slotMenu.getItems().add(downItem);
			} else {
            MenuItem addItem = makeMenuItem("➕  등록", null);
            addItem.setOnAction(e -> openSlotEditor(idx));
            slotMenu.getItems().add(addItem);
		}
        return slotMenu;
	}
	
    // ── [Help] 메뉴 ──────────────────────────────────────────────
    //   • 프로그램 업그레이드: 보존, 구현체 삭제 → stub (요구사항 6)
    private Menu buildHelpMenu() {
        Menu menu = makeMenu("Help", "로그, 설정, 다운로드, About, 개발자 문의");
		
        menu.getItems().add(makeSectionHeader("유지보수"));
        menu.getItems().add(makeRichMenuItem("🔄", "프로그램 업그레이드",
		"GitHub 에서 최신 버전을 내려받아 덮어씁니다", null, this::showUpgradeStub));
		
        menu.getItems().add(makeSectionHeader("로그 / 설정"));
        menu.getItems().add(makeRichMenuItem("📋", "Log조회",
		"현재 로그 파일을 표시합니다", null, this::doShowLogFile));
        menu.getItems().add(makeRichMenuItem("🗑", "지난Log데이타 삭제",
		"이전 날짜 로그 파일을 삭제합니다", null, this::doDeleteOldLogs));
        menu.getItems().add(makeRichMenuItem("⚙️", "기본 설정 파일",
		"설정 파일(ini)을 표시합니다", null, this::doShowConfigFile));
		
        menu.getItems().add(makeSectionHeader("링크"));
        menu.getItems().add(makeRichMenuItem("👨‍💻", "개발자 소개",
            "김갑수 / 대한민국 서울", null,
		() -> openBrowser("https://github.com/GarpsuKim")));
        menu.getItems().add(makeRichMenuItem("⬇", "설치 파일",
            "끝판왕 설치파일 다운로드", null,
		() -> openBrowser("https://github.com/GarpsuKim/KootPanKingThree/releases/tag/KootPanKingThree")));
        menu.getItems().add(makeRichMenuItem("🧩", "프로그램 소스",
            "Java 프로그램 소스", null,
		() -> openBrowser("https://github.com/GarpsuKim/KootPanKingThree")));
        menu.getItems().add(makeRichMenuItem("☕", "Java/JVM",
            "Java 환경 설치파일 다운로드", null,
		() -> openBrowser("https://www.oracle.com/java")));
		
        menu.getItems().add(makeSectionHeader("정보"));
        MenuItem aboutItem = makeRichMenuItem("ℹ️", "About",
		"프로그램 정보", "F1", this::doShowAbout);
        aboutItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("F1"));
        menu.getItems().add(aboutItem);
		
        menu.getItems().add(makeRichMenuItem("📩", "오류 신고 및 개발자에게 문의",
		"개발자에게 이메일로 문의합니다", null, this::doContactDeveloper));
		
        menu.getItems().add(makeSectionHeader("화면"));
        menu.getItems().add(makeRichMenuItem(
            themeMode == ThemeMode.PINK_GLASS ? "💙" : "💖",
            themeMode == ThemeMode.PINK_GLASS ? "기본 테마로 되돌리기" : "핑크 글래스로 전환",
            "화면 테마를 전환합니다",
            null,
		this::toggleTheme));
		
        return menu;
	}
	
    // ═══════════════════════════════════════════════════════════
    //  메뉴 액션 구현
    // ═══════════════════════════════════════════════════════════
	
    /** File → Open */
    private void doOpen() {
        new Thread(() -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("텍스트 파일 열기");
            fc.setInitialDirectory(new File(System.getProperty("user.home")));
            fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                    "텍스트 파일 (*.txt, *.log, *.ini, *.java ...)",
                    "*.txt","*.log","*.ini","*.java","*.md","*.csv",
				"*.properties","*.xml","*.json","*.html","*.htm"),
                new FileChooser.ExtensionFilter("모든 파일", "*.*")
			);
            Platform.runLater(() -> {
                File file = fc.showOpenDialog(stage);
                if (file == null || !file.exists()) return;
                openTextFileWindow(file);
			});
		}, "FileChooserInit").start();
	}
	
    /** File → Close */
    private void doClose() {
        if (clockHost != null) clockHost.onClose();
        stage.hide();
	}
	
    /** File → Exit */
    private void doExit() {
        Stage dlg = new Stage();
        dlg.initOwner(stage);
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.initStyle(StageStyle.UTILITY);
        dlg.setTitle("종료 확인");
        dlg.setAlwaysOnTop(true);
		
        Label msg = new Label("프로그램을 종료하시겠습니까?");
        msg.setStyle("-fx-font-family:'Malgun Gothic'; -fx-font-size:13px;");
		
        Button yes = new Button("Yes");
        Button no  = new Button("No");
        yes.setPrefWidth(72); no.setPrefWidth(72);
		
        final boolean[] confirmed = {false};
        final int[] sec = {15};
        Label timerLbl = new Label("자동 취소까지: 15초");
        timerLbl.setStyle("-fx-text-fill:#888888; -fx-font-size:11px;");
		
        javafx.animation.Timeline countdown = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ev -> {
                sec[0]--;
                timerLbl.setText("자동 취소까지: " + sec[0] + "초");
                dlg.setTitle("종료 확인 — " + sec[0] + "초 후 취소");
                if (sec[0] <= 0) dlg.close();
			})
		);
        countdown.setCycleCount(15);
        countdown.play();
		
        yes.setOnAction(e -> { confirmed[0] = true;  countdown.stop(); dlg.close(); });
        no .setOnAction(e -> { confirmed[0] = false; countdown.stop(); dlg.close(); });
		
        HBox btns = new HBox(10, yes, no);
        btns.setAlignment(Pos.CENTER);
		
        VBox root = new VBox(12, msg, timerLbl, btns);
        root.setPadding(new Insets(16));
        root.setAlignment(Pos.CENTER);
		
        dlg.setScene(new Scene(root));
        dlg.sizeToScene();
        dlg.showAndWait();
		
        if (!confirmed[0]) return;
        if (clockHost != null) clockHost.exitAll();
        else System.exit(0);
	}
	
    /** Help → 프로그램 업그레이드 (stub — 요구사항 6) */
    private void showUpgradeStub() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("프로그램 업그레이드");
        alert.setHeaderText(null);
        alert.setContentText("이 기능은 현재 지원되지 않습니다.");
        alert.showAndWait();
	}
	
    /** Help → Log 조회 */
    private void doShowLogFile() {
        try {
            String path = AppLogger.getLogFilePath();
            if (path == null || path.trim().isEmpty()) return;
            java.io.File f = new java.io.File(path);
            if (!f.exists()) return;
            if (java.awt.Desktop.isDesktopSupported())
			java.awt.Desktop.getDesktop().open(f);
			} catch (Exception e) {
            System.err.println("로그 파일 열기 실패: " + e.getMessage());
		}
	}
    private void doShowLogFileHTML() {
        if (clockHost == null) { showNotReady(); return; }
        String logPath = clockHost.getLogFilePath();
        if (logPath == null || logPath.isEmpty()) {
            showAlert("로그 파일 경로를 찾을 수 없습니다.", "Log조회"); return;
		}
        File logFile = new File(logPath);
        if (!logFile.exists()) {
            showAlert("로그 파일이 존재하지 않습니다.\n" + logPath, "Log조회"); return;
		}
        try {
            String text = FxSplashWindow.TextFileReader.readContent(logFile);
            String escaped = text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
            File htmlFile = File.createTempFile("applog_", ".html");
            htmlFile.deleteOnExit();
            try (java.io.PrintWriter pw = new java.io.PrintWriter(
			new java.io.OutputStreamWriter(new java.io.FileOutputStream(htmlFile), "UTF-8"))) {
			pw.println("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>끝판왕 로그</title><style>");
			pw.println("body{font-family:'Consolas','Malgun Gothic',monospace;background:#0d0d0d;color:#c8ffc8;padding:20px;line-height:1.6;}");
			pw.println("pre{white-space:pre-wrap;font-size:13px;}</style></head><body><pre>");
			pw.println(escaped);
			pw.println("</pre></body></html>");
            }
            java.awt.Desktop.getDesktop().browse(htmlFile.toURI());
			} catch (Exception ex) {
            showAlert("로그 파일 열기 실패: " + ex.getMessage(), "오류");
		}
	}
	
    /** Help → 지난 Log 삭제 */
    private void doDeleteOldLogs() {
        if (clockHost == null) { showNotReady(); return; }
        String logPath = clockHost.getLogFilePath();
        if (logPath == null || logPath.isEmpty()) {
            showAlert("로그 파일 경로를 찾을 수 없습니다.", "Log삭제"); return;
		}
        File logDir = new File(logPath).getParentFile();
        if (logDir == null || !logDir.exists()) {
            showAlert("로그 폴더를 찾을 수 없습니다.", "Log삭제"); return;
		}
        File current = new File(logPath);
        File[] old = logDir.listFiles(f ->
            f.isFile() && f.getName().endsWith(".txt")
		&& !f.getAbsolutePath().equals(current.getAbsolutePath()));
        if (old == null || old.length == 0) {
            showAlert("삭제할 지난 로그 파일이 없습니다.", "Log삭제"); return;
		}
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("지난Log데이타 삭제");
        confirm.setHeaderText(null);
        confirm.setContentText("지난 로그 파일 " + old.length + "개를 삭제하시겠습니까?\n"
		+ "폴더: " + logDir.getAbsolutePath());
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            int deleted = 0;
            for (File f : old) if (f.delete()) deleted++;
            showAlert(deleted + "개 삭제 완료.", "Log삭제");
		});
	}
	
    /** Help → 기본 설정 파일 */
    private void doShowConfigFile() {
        if (clockHost == null) { showNotReady(); return; }
        clockHost.showConfigFile();
	}
	
    /** Help → About */
    private void doShowAbout() {
        if (clockHost == null) { showNotReady(); return; }
        clockHost.showAbout();
	}
	
    /** Help → 오류 신고 */
    private void doContactDeveloper() {
        if (clockHost == null) {
            showAlert("시계가 초기화되지 않았습니다. 잠시 후 다시 시도하세요.", "개발자 문의");
            return;
		}
        final String RECEIVER = "garpsu@naver.com";
        Stage dlg = new Stage();
        dlg.initOwner(stage);
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.initStyle(StageStyle.UTILITY);
        dlg.setTitle("프로그램 오류 신고 및 개발자에게 문의");
        dlg.setAlwaysOnTop(true);
		
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8); grid.setVgap(8);
        grid.setPadding(new Insets(14));
		
        grid.add(new Label("수신자:"), 0, 0);
        grid.add(new Label(RECEIVER), 1, 0);
		
        grid.add(new Label("발신자 전화:"), 0, 1);
        TextField phoneField = new TextField(); phoneField.setPromptText("010-xxxx-yyyy");
        grid.add(phoneField, 1, 1);
		
        grid.add(new Label("발신자 성명:"), 0, 2);
        TextField nameField = new TextField();
        grid.add(nameField, 1, 2);
		
        grid.add(new Label("문의 유형:"), 0, 3);
        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton rbErr  = new RadioButton("오류 신고");  rbErr.setToggleGroup(typeGroup);
        RadioButton rbImp  = new RadioButton("개선 요청");  rbImp.setToggleGroup(typeGroup);
        RadioButton rbAdd  = new RadioButton("추가 요청");  rbAdd.setToggleGroup(typeGroup);
        HBox typeRow = new HBox(8, rbErr, rbImp, rbAdd);
        grid.add(typeRow, 1, 3);
		
        grid.add(new Label("내용:"), 0, 4);
        TextArea bodyArea = new TextArea(); bodyArea.setPrefRowCount(7); bodyArea.setWrapText(true);
        grid.add(bodyArea, 1, 4);
		
        Button okBtn  = new Button("확인");  okBtn.setPrefWidth(80);
        Button canBtn = new Button("취소");  canBtn.setPrefWidth(80);
        canBtn.setOnAction(e -> dlg.close());
        okBtn.setOnAction(ev -> {
            String phone = phoneField.getText().trim();
            String name  = nameField.getText().trim();
            String body  = bodyArea.getText().trim();
            String type  = rbErr.isSelected() ? "오류 신고"
			: rbImp.isSelected() ? "개선 요청"
			: rbAdd.isSelected() ? "추가 요청" : "";
            if (phone.isEmpty() || name.isEmpty() || body.isEmpty()) {
                showAlert("전화번호, 성명, 내용은 필수 입력입니다.", "입력 확인"); return;
			}
            if (!phone.matches("010-\\d{3,4}-\\d{4}")) {
                showAlert("전화번호 형식이 올바르지 않습니다.\n010-xxxx-yyyy", "입력 확인"); return;
			}
            GmailSender gmail = clockHost.getGmail();
            boolean useDev = !gmail.isConfigured();
            String from = useDev ? GmailSender.devGmailId()   : gmail.from;
            String pass = useDev ? GmailSender.devGmailPass() : gmail.pass;
            if (from.isEmpty() || pass.isEmpty()) {
                showAlert("발송 계정을 확인할 수 없습니다.", "오류"); return;
			}
            String subject = "[끝판왕 문의] " + (type.isEmpty() ? "" : type + " - ") + name;
            String mailBody = (type.isEmpty() ? "" : "■ 문의 유형 : " + type + "\n")
			+ "■ 성명 : " + name + "\n■ 전화 : " + phone + "\n"
			+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" + body + "\n"
			+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" + GmailSender.APP_SIGNATURE;
            okBtn.setDisable(true);
            okBtn.setText("전송 중...");
            new Thread(() -> {
                try {
                    gmail.smtpSend(from, pass, from, RECEIVER, subject, mailBody);
                    Platform.runLater(() -> {
                        dlg.close();
                        showAlert("✅ 전송 완료!\n수신자: " + RECEIVER, "전송 완료");
					});
					} catch (Exception ex) {
                    Platform.runLater(() -> {
                        okBtn.setDisable(false); okBtn.setText("확인");
                        showAlert("❌ 전송 실패: " + ex.getMessage(), "전송 오류");
					});
				}
			}, "ContactDeveloper").start();
		});
		
        HBox btns = new HBox(8, okBtn, canBtn);
        btns.setAlignment(Pos.CENTER_RIGHT);
        VBox root = new VBox(0, grid, btns);
        btns.setPadding(new Insets(0, 14, 10, 14));
		
        dlg.setScene(new Scene(root));
        dlg.sizeToScene();
        dlg.setResizable(true);
        dlg.showAndWait();
	}
	
    // ═══════════════════════════════════════════════════════════
    //  텍스트 파일 뷰어
    // ═══════════════════════════════════════════════════════════
    private void openTextFileWindow(File file) {
        new Thread(() -> {
            try {
                FxSplashWindow.TextFileReader.Result r = FxSplashWindow.TextFileReader.read(file);
                Platform.runLater(() -> showTextWindow(file, r.encLabel, r.content));
                log("파일 열기: " + file.getName());
				} catch (Exception ex) {
                Platform.runLater(() -> showAlert("파일 읽기 실패:\n" + ex.getMessage(), "Open"));
                log("[ERROR] 파일 열기 실패: " + file.getName() + " — " + ex.getMessage());
			}
		}, "FileOpen").start();
	}
	
    private void showTextWindow(File file, String encLabel, String content) {
        Stage sub = new Stage();
        sub.setTitle("📄 " + file.getName());
		
        TextArea ta = new TextArea(content);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setStyle(
            "-fx-font-family: '돋움체'; -fx-font-size: 16px;"
            + "-fx-text-fill: " + fgColor() + ";"
            + "-fx-background-color: " + (themeMode == ThemeMode.PINK_GLASS ? "rgba(255,255,255,0.30)" : "#ebf5ff") + ";"
			+ "-fx-control-inner-background: " + (themeMode == ThemeMode.PINK_GLASS ? "rgba(255,255,255,0.30)" : "#ebf5ff") + ";"
            + "-fx-background-radius: 16;"
            + "-fx-border-color: " + borderColor() + ";"
		+ "-fx-border-radius: 16;");
		
        ScrollPane sp = new ScrollPane(ta);
        sp.setFitToWidth(true); sp.setFitToHeight(true);
		
        Label info = new Label(" " + encLabel + "  |  "
		+ file.getAbsolutePath() + "  (" + file.length() + " bytes)");
        info.setStyle(
            "-fx-background-color: " + barBgColor() + "; -fx-text-fill: " + fgColor() + ";"
            + "-fx-font-family:'Malgun Gothic'; -fx-font-size:11px;"
		+ "-fx-padding: 2 4 2 4; -fx-border-color:" + borderColor() + "; -fx-border-width:1 0 0 0;");
        info.setMaxWidth(Double.MAX_VALUE);
		
        BorderPane root = new BorderPane(sp);
        root.setBottom(info);
		
        javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        int w = (int) Math.min(screen.getWidth() * 0.7, 1100);
        int h = (int) Math.min(screen.getHeight() * 0.7, 800);
        sub.setScene(new Scene(root, Math.max(600, w), Math.max(400, h)));
        sub.show();
	}
	
    // ═══════════════════════════════════════════════════════════
    //  업무도구 헬퍼
    // ═══════════════════════════════════════════════════════════
    private void launchByRegistry(String exeName) {
        String path = null;
        try {
            Process proc = new ProcessBuilder(
                "reg", "query",
                "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\" + exeName,
			"/ve").start();
            java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(proc.getInputStream(), "MS949"));
            String ln;
            while ((ln = br.readLine()) != null)
			if (ln.contains("REG_SZ")) { path = ln.split("REG_SZ")[1].trim(); break; }
		} catch (Exception ex) { path = null; }
        if (path == null || path.isEmpty()) {
            showAlert(exeName + " 경로를 찾을 수 없습니다.", "업무도구"); return;
		}
        launchByPath(path);
	}
	
    private void launchByPath(String path) {
        try {
            if (path.toLowerCase().endsWith(".lnk"))
			new ProcessBuilder("cmd", "/c", "start", "", path).start();
            else
			new ProcessBuilder(path).start();
			} catch (Exception ex) {
            showAlert("실행 실패: " + ex.getMessage(), "업무도구");
		}
	}
	
    private void openSlotEditor(int idx) {
        if (clockHost == null) { showNotReady(); return; }
        String curName = clockHost.getConfig(slotNameKey(idx), "");
        String curPath = clockHost.getConfig(slotPathKey(idx), "");
		
        Stage dlg = new Stage();
        dlg.initOwner(stage);
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.initStyle(StageStyle.UTILITY);
        dlg.setTitle("업무도구 슬롯 " + (idx + 1) + " 등록");
		
        TextField nameField = new TextField(curName); nameField.setPrefWidth(220);
        TextField pathField = new TextField(curPath); pathField.setPrefWidth(280);
        Button browseBtn = new Button("찾아보기...");
        browseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("실행 파일 선택");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
			"실행 가능 파일 (*.exe, *.bat, *.lnk)", "*.exe","*.bat","*.cmd","*.lnk"));
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("모든 파일","*.*"));
            File cur = pathField.getText().isEmpty() ? null : new File(pathField.getText()).getParentFile();
            if (cur != null && cur.exists()) fc.setInitialDirectory(cur);
            File sel = fc.showOpenDialog(dlg);
            if (sel != null) {
                pathField.setText(sel.getAbsolutePath());
                if (nameField.getText().trim().isEmpty()) {
                    String fn = sel.getName();
                    int dot = fn.lastIndexOf('.');
                    if (dot > 0) fn = fn.substring(0, dot);
                    nameField.setText(fn);
				}
			}
		});
		
        Button ok  = new Button("확인");  ok.setPrefWidth(72);
        Button can = new Button("취소");  can.setPrefWidth(72);
        can.setOnAction(e -> dlg.close());
        ok.setOnAction(e -> {
            String n = nameField.getText().trim();
            String p = pathField.getText().trim();
            if (n.isEmpty() || p.isEmpty()) {
                showAlert("이름과 경로를 모두 입력하세요.", "업무도구"); return;
			}
            clockHost.setMultipleConfigAndSave(slotNameKey(idx), n, slotPathKey(idx), p);
            dlg.close();
            rebuildMenuBar();
		});
		
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(6); grid.setVgap(8); grid.setPadding(new Insets(14));
        grid.add(new Label("이름:"), 0, 0); grid.add(nameField, 1, 0, 2, 1);
        grid.add(new Label("경로:"), 0, 1); grid.add(pathField, 1, 1); grid.add(browseBtn, 2, 1);
		
        HBox btns = new HBox(8, ok, can);
        btns.setAlignment(Pos.CENTER_RIGHT);
        btns.setPadding(new Insets(0, 14, 10, 14));
		
        VBox root = new VBox(0, grid, btns);
        dlg.setScene(new Scene(root));
        dlg.sizeToScene();
        dlg.showAndWait();
	}
	
    private void deleteSlot(int idx, String displayName) {
        if (clockHost == null) { showNotReady(); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("업무도구 삭제");
        confirm.setHeaderText(null);
        confirm.setContentText("「" + displayName + "」 을 삭제하시겠습니까?");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            clockHost.setMultipleConfigAndSave(slotNameKey(idx), "", slotPathKey(idx), "");
            rebuildMenuBar();
		});
	}
	
    private void swapSlots(int src, int dst) {
        if (clockHost == null) { showNotReady(); return; }
        String sn = clockHost.getConfig(slotNameKey(src), "");
        String sp = clockHost.getConfig(slotPathKey(src), "");
        String dn = clockHost.getConfig(slotNameKey(dst), "");
        String dp = clockHost.getConfig(slotPathKey(dst), "");
        clockHost.setMultipleConfigAndSave(
            slotNameKey(src), dn, slotPathKey(src), dp,
		slotNameKey(dst), sn, slotPathKey(dst), sp);
	}
	
    private void rebuildMenuBar() {
        Platform.runLater(() ->
		((BorderPane) stage.getScene().getRoot()).setTop(buildMenuBar()));
	}
	
    // ═══════════════════════════════════════════════════════════
    //  생활도구 헬퍼
    // ═══════════════════════════════════════════════════════════
    private java.io.File getCalendarFile() {
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        java.io.File dir = new java.io.File(appData
            + java.io.File.separator + "KootPanKingThree"
		+ java.io.File.separator + "data");
        if (!dir.exists()) dir.mkdirs();
        return new java.io.File(dir, "calendar.html");
	}
	
    private void openCalendarHtml() {
        java.io.File f = getCalendarFile();
        if (!f.exists()) { showAlert("[만년달력 갱신]을 먼저 실행하세요.", "만년달력"); return; }
        try { java.awt.Desktop.getDesktop().browse(f.toURI()); }
        catch (Exception ex) { showAlert("브라우저 열기 실패: " + ex.getMessage(), "만년달력"); }
	}
	
    private void updateCalendarHtml() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("만년달력 갱신");
        confirm.setHeaderText(null);
        confirm.setContentText("임시 공휴일 추가 등 만년달력을 자동 갱신합니다.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            final String URL =
			"https://raw.githubusercontent.com/GarpsuKim/Calendar_Lunar_-_HTML/main/Calendar.html";
            final java.io.File dest = getCalendarFile();
            new Thread(() -> {
                try {
                    java.net.HttpURLConnection con =
					(java.net.HttpURLConnection) new java.net.URI(URL).toURL().openConnection();
                    con.setConnectTimeout(10000); con.setReadTimeout(30000); con.connect();
                    int code = con.getResponseCode();
                    if (code != 200) { con.disconnect();
					Platform.runLater(() -> showAlert("다운로드 실패 (HTTP " + code + ")", "만년달력 갱신")); return; }
                    try (java.io.InputStream in = con.getInputStream();
						java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                        in.transferTo(out);
					}
                    con.disconnect();
                    Platform.runLater(() -> showAlert(
					"만년달력이 갱신되었습니다.\n저장 위치: " + dest.getAbsolutePath(), "만년달력 갱신"));
					} catch (Exception ex) {
                    Platform.runLater(() -> showAlert("다운로드 오류: " + ex.getMessage(), "만년달력 갱신"));
				}
			}, "CalendarUpdate").start();
		});
	}
	
    private void openBrowser(String url) {
        try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); }
        catch (Exception ex) { showAlert("브라우저 열기 실패: " + ex.getMessage(), "오류"); }
	}
	
    // ═══════════════════════════════════════════════════════════
    //  로그 내부 구현
    // ═══════════════════════════════════════════════════════════
    private void appendLog(String message) {
        String ts = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        logArea.appendText("[" + ts + "] " + message + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);
        String short_ = message.length() > 80 ? message.substring(0, 78) + "…" : message;
        setRuntimeStatus(short_);
	}
	
    // ═══════════════════════════════════════════════════════════
    //  유틸
    // ═══════════════════════════════════════════════════════════
    private void showNotReady() {
        showAlert("시계가 아직 초기화되지 않았습니다.\n잠시 후 다시 시도하세요.", "알림");
	}
	
    private void showAlert(String msg, String title) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.initOwner(stage);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
		});
	}
	
    // ── 메뉴 스타일 / 상태바 헬퍼 ──────────────────────────────
    private String defaultStatusText = " 준비";
    private String lastRuntimeStatus = defaultStatusText;
    private boolean menuStatusActive = false;
	
    private void setRuntimeStatus(String text) {
        lastRuntimeStatus = " " + text;
        if (!menuStatusActive) statusBar.setText(lastRuntimeStatus);
	}
	
    private void showMenuStatus(String text) {
        menuStatusActive = true;
        statusBar.setText(" " + text);
	}
	
    private void clearMenuStatus() {
        menuStatusActive = false;
        statusBar.setText(lastRuntimeStatus != null ? lastRuntimeStatus : defaultStatusText);
	}
	
    private Menu makeMenu(String text) {
        return makeMenu(text, null);
	}
	
    private Menu makeMenu(String text, String helpText) {
        Menu m = new Menu(text);
        m.setStyle("-fx-font-family:'Malgun Gothic'; -fx-font-size:13px; -fx-text-fill:" + fgColor() + ";");
        m.setOnShowing(e -> {
            if (helpText != null && !helpText.isEmpty()) showMenuStatus(helpText);
		});
        m.setOnHidden(e -> clearMenuStatus());
        return m;
	}
	
	
	private MenuItem makeMenuItem(String text, String tooltip) {
        return makeRichMenuItem(null, text, tooltip, null, null);
	}
	
    private MenuItem makeDisabledItem(String text) {
        MenuItem item = makeRichMenuItem("⛔", text, "현재는 사용할 수 없는 항목입니다.", null, null);
        item.setDisable(true);
        return item;
	}
	
    private String bgColor() {
        return themeMode == ThemeMode.PINK_GLASS ? BG : "#ebf5ff";
	}
	
    private String fgColor() {
        return themeMode == ThemeMode.PINK_GLASS ? FG : "#14325a";
	}
	
    private String subTextColor() {
        return themeMode == ThemeMode.PINK_GLASS ? "#b45d8f" : "#5078b4";
	}
	
    private String barBgColor() {
        return themeMode == ThemeMode.PINK_GLASS ? BAR_BG : "#c8e1f5";
	}
	
    private String borderColor() {
        return themeMode == ThemeMode.PINK_GLASS ? GLASS_BORDER : "#8cb4d2";
	}
	
    private String glassPanelColor() {
        return themeMode == ThemeMode.PINK_GLASS ? GLASS_PANEL : "rgba(255,255,255,0.85)";
	}
	
    private String glassHoverColor() {
        return themeMode == ThemeMode.PINK_GLASS ? GLASS_HOVER : "#dcefff";
	}
	
    private String menuBarStyle() {
        return themeMode == ThemeMode.PINK_GLASS
		? MENU_BG
		: "-fx-background-color: #add8e6;"
		+ "-fx-background-insets: 0;"
		+ "-fx-border-color: #8cb4d2;"
		+ "-fx-border-width: 0 0 1 0;";
	}
	
    private String logStyle() {
        if (themeMode == ThemeMode.PINK_GLASS) return LOG_STYLE;
        return "-fx-font-family: 'Malgun Gothic'; -fx-font-size: 13px;"
		+ "-fx-text-fill: #14325a;"
		+ "-fx-background-color: #ebf5ff;"
		+ "-fx-control-inner-background: #ebf5ff;"
		+ "-fx-background-radius: 8;"
		+ "-fx-border-color: #8cb4d2;"
		+ "-fx-border-radius: 8;"
		+ "-fx-border-width: 1;";
	}
	
    private String scrollPaneStyle() {
        return themeMode == ThemeMode.PINK_GLASS
		? "-fx-background: transparent; -fx-background-color: transparent;"
		: "-fx-background-color: #ebf5ff;";
	}
	
    private String rootStyle() {
        if (themeMode == ThemeMode.PINK_GLASS) {
            return "-fx-background-color: " + BG + ";"
			+ "-fx-padding: 12;"
			+ "-fx-background-radius: 18;";
		}
        return "-fx-background-color: #ebf5ff;";
	}
	
    private String statusBarStyle() {
        return "-fx-background-color: " + barBgColor() + ";"
		+ "-fx-text-fill: " + fgColor() + ";"
		+ "-fx-font-family: 'Malgun Gothic'; -fx-font-size: 12px;"
		+ "-fx-padding: 2 8 2 8;"
		+ "-fx-border-color: " + borderColor() + "; -fx-border-width: 1 0 0 0;";
	}
	
    private void applyTheme() {
        if (stage.getScene() == null) return;
        BorderPane root = (BorderPane) stage.getScene().getRoot();
        root.setStyle(rootStyle());
        if (root.getCenter() instanceof ScrollPane sp) {
            sp.setStyle(scrollPaneStyle());
		}
        logArea.setStyle(logStyle());
        statusBar.setStyle(statusBarStyle());
        root.setTop(buildMenuBar());
	}
	
    private void toggleTheme() {
        themeMode = (themeMode == ThemeMode.PINK_GLASS) ? ThemeMode.BASIC : ThemeMode.PINK_GLASS;
        applyTheme();
	}
	
    // ═══════════════════════════════════════════════════════════
    //  TextFileReader — 인코딩 자동 탐지 파일 읽기
    //  (UTF-8 BOM → UTF-16 BE BOM → UTF-8 → CP949 순)
    // ═══════════════════════════════════════════════════════════
    static class TextFileReader {
        public static class Result {
            public final String encLabel;
            public final String content;
            public Result(String encLabel, String content) {
                this.encLabel = encLabel;
                this.content  = content;
			}
		}
		
        public static Result read(java.io.File file) throws java.io.IOException {
            String enc = "CP949", encLabel = "[ CP949 ]";
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] bom = new byte[3];
                int bomRead = fis.read(bom, 0, 3);
                if (bomRead >= 3 && bom[0]==(byte)0xEF && bom[1]==(byte)0xBB && bom[2]==(byte)0xBF)
				return new Result("[ UTF-8 BOM ]", readAll(fis, "UTF-8"));
                if (bomRead >= 2 && bom[0]==(byte)0xFE && bom[1]==(byte)0xFF) {
                    java.io.InputStream rest = new java.io.SequenceInputStream(
					new java.io.ByteArrayInputStream(bom, 0, bomRead), fis);
                    return new Result("[ UTF-16 BE BOM ]", readAll(rest, "UTF-16BE"));
				}
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(
				(int) Math.min(file.length(), 4 * 1024 * 1024));
                baos.write(bom, 0, bomRead);
                byte[] buf = new byte[8192]; int n;
                while ((n = fis.read(buf)) != -1) baos.write(buf, 0, n);
                byte[] all = baos.toByteArray();
                if (isValidUTF8(all)) { enc = "UTF-8"; encLabel = "[ UTF-8 ]"; }
                return new Result(encLabel, readAll(new java.io.ByteArrayInputStream(all), enc));
			}
		}
		
        public static String readContent(java.io.File file) throws java.io.IOException {
            return read(file).content;
		}
		
        private static String readAll(java.io.InputStream is, String enc) throws java.io.IOException {
            java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(is, enc), 1024 * 1024);
            StringBuilder sb = new StringBuilder();
            String line; boolean first = true;
            while ((line = br.readLine()) != null) {
                if (!first) sb.append("\n");
                sb.append(line); first = false;
			}
            return sb.toString();
		}
		
        private static boolean isValidUTF8(byte[] all) {
            try {
                java.nio.charset.CharsetDecoder dec =
				java.nio.charset.StandardCharsets.UTF_8.newDecoder();
                dec.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
                dec.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
                dec.decode(java.nio.ByteBuffer.wrap(all));
                return true;
			} catch (Exception e) { return false; }
		}
	}
	
	private MenuItem makeRichMenuItem(String icon, String text, String helpText, String shortcut, Runnable action) {
		Label iconLabel = new Label(icon == null ? "" : icon);
		iconLabel.setMinWidth(24);
		
		Label textLabel = new Label(text);
		textLabel.setStyle("-fx-font-family:'Malgun Gothic'; -fx-font-size:13px; -fx-text-fill:" + fgColor() + ";");
		
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		
		Label shortcutLabel = new Label(shortcut == null ? "" : shortcut);
		shortcutLabel.setStyle("-fx-font-family:'Consolas'; -fx-font-size:11px; -fx-text-fill:" + subTextColor() + ";");
		
		HBox row = new HBox(8, iconLabel, textLabel, spacer, shortcutLabel);
		row.setAlignment(Pos.CENTER_LEFT);
		row.setPadding(new Insets(4, 12, 4, 8));
		row.setStyle("-fx-background-color: " + glassPanelColor() + "; -fx-background-radius: 14; -fx-border-color: transparent; -fx-border-radius: 14;");
		
		CustomMenuItem item = new CustomMenuItem(row, true);
		
		if (helpText != null) {
			Tooltip tip = new Tooltip(helpText);
			tip.setStyle("-fx-font-family:'Malgun Gothic'; -fx-font-size:12px; -fx-background-color:" + (themeMode == ThemeMode.PINK_GLASS ? "rgba(255,245,251,0.95)" : "rgba(240,248,255,0.96)") + "; -fx-text-fill:" + fgColor() + "; -fx-background-radius:10; -fx-border-color:" + borderColor() + "; -fx-border-radius:10;");
			Tooltip.install(row, tip);
			
			row.setOnMouseEntered(e -> {
				row.setStyle("-fx-background-color:" + glassHoverColor() + "; -fx-background-radius: 14; -fx-border-color:" + borderColor() + "; -fx-border-radius:14; -fx-effect: dropshadow(gaussian, rgba(209,79,146,0.14), 12, 0.20, 0, 2);");
				showMenuStatus(helpText);
			});
			
			row.setOnMouseExited(e -> {
				row.setStyle("-fx-background-color: " + glassPanelColor() + "; -fx-background-radius: 14; -fx-border-color: transparent; -fx-border-radius:14;");
				clearMenuStatus();
			});
		}
		
		item.setOnAction(e -> {
			if (action != null) action.run();
		});
		
		return item;
	}
	
	private CustomMenuItem makeSectionHeader(String text) {
		Label label = new Label(text);
		label.setStyle("-fx-font-size:11px; -fx-font-weight:bold; -fx-text-fill:" + (themeMode == ThemeMode.PINK_GLASS ? "#c24d8f" : "#5078b4") + ";");
		HBox box = new HBox(label);
		box.setPadding(new Insets(8, 10, 5, 10));
		box.setStyle("-fx-background-color: " + (themeMode == ThemeMode.PINK_GLASS ? "rgba(255,255,255,0.22)" : "rgba(255,255,255,0.70)") + "; -fx-background-radius: 10;");
		box.setMouseTransparent(true);
		
		CustomMenuItem item = new CustomMenuItem(box, false);
		item.setDisable(true);
		return item;
	}
	
	private Menu buildFileMenu() {
		Menu menu = makeMenu("File", "파일 열기 및 프로그램 제어");
		
		menu.getItems().add(makeSectionHeader("파일"));
		
		menu.getItems().add(makeRichMenuItem("📂", "Open",
		"텍스트 파일을 열어 새 창에 표시", "Ctrl+O", this::doOpen));
		
		menu.getItems().add(makeRichMenuItem("🪟", "Close",
		"이 창을 닫습니다 (시계 유지)", "Ctrl+W", this::doClose));
		
		menu.getItems().add(makeSectionHeader("종료"));
		
		menu.getItems().add(makeRichMenuItem("⏻", "Exit",
		"프로그램 완전 종료", "Ctrl+Q", this::doExit));
		
		return menu;
	}
	
	private Menu buildToolsMenu() {
		Menu menu = makeMenu("도구", "알림 및 외부 서비스 기능");
		
		menu.getItems().add(makeSectionHeader("알림"));
		
		menu.getItems().add(makeRichMenuItem("🔔", "차임벨 설정",
			"정각 알림 설정", null,
		() -> { if (clockHost != null) clockHost.showChimeDialog(); }));
		
		menu.getItems().add(makeSectionHeader("연동"));
		
		if (clockHost != null) {
			menu.getItems().add(clockHost.buildGmailCalendarMenu());
			menu.getItems().add(clockHost.buildKakaoMenu());
			menu.getItems().add(clockHost.buildTelegramMenu());
		}
		
		return menu;
	}
	
	private Menu buildLifeMenu() {
		Menu menu = makeMenu("생활도구", "시간/날씨/천문 정보");
		
		menu.getItems().add(makeSectionHeader("외부 서비스"));
		
		menu.getItems().add(makeRichMenuItem("🌏", "생활천문관", null, null,
		() -> openBrowser("https://astro.kasi.re.kr/index")));
		
		menu.getItems().add(makeRichMenuItem("🕐", "TIME.IS", null, null,
		() -> openBrowser("https://time.is")));
		
		menu.getItems().add(makeRichMenuItem("🕰", "TIME&DATE", null, null,
		() -> openBrowser("https://www.timeanddate.com")));
		
		menu.getItems().add(makeRichMenuItem("🌤", "날씨", null, null,
		() -> openBrowser("https://www.weather.go.kr")));
		
		menu.getItems().add(makeSectionHeader("도구"));
		
		menu.getItems().add(makeRichMenuItem("📅", "만년달력",
		"브라우저로 열기", null, this::openCalendarHtml));
		
		menu.getItems().add(makeRichMenuItem("🔄", "달력 갱신",
		"GitHub 최신 다운로드", null, this::updateCalendarHtml));
		
		return menu;
	}
	
	private Menu buildOfficeMenu() {
		Menu menu = makeMenu("업무도구", "업무 프로그램 실행");
		populateOfficeMenuItems(menu);
		return menu;
	}
	
	private void populateOfficeMenuItems(Menu menu) {
		menu.getItems().clear();
		menu.getItems().add(makeSectionHeader("Office"));
		
		menu.getItems().add(makeRichMenuItem("📊", "Excel", null, null,
		() -> launchByRegistry("excel.exe")));
		
		menu.getItems().add(makeRichMenuItem("📝", "Word", null, null,
		() -> launchByRegistry("winword.exe")));
		
		menu.getItems().add(makeRichMenuItem("📑", "PowerPoint", null, null,
		() -> launchByRegistry("powerpnt.exe")));
		
		menu.getItems().add(makeSectionHeader("등록된 도구"));
		
		// 채워진 슬롯 인덱스 목록 수집 (위/아래 이웃 슬롯 계산용)
		java.util.List<Integer> filledSlots = new java.util.ArrayList<>();
		for (int i = 0; i < OFFICE_SLOT_COUNT; i++) {
			String n = clockHost != null ? clockHost.getConfig(slotNameKey(i), "") : "";
			String p = clockHost != null ? clockHost.getConfig(slotPathKey(i), "") : "";
			if (!n.isEmpty() && !p.isEmpty()) filledSlots.add(i);
		}
		for (int fi = 0; fi < filledSlots.size(); fi++) {
			int slotIdx  = filledSlots.get(fi);
			String name  = clockHost.getConfig(slotNameKey(slotIdx), "");
			String path  = clockHost.getConfig(slotPathKey(slotIdx), "");
			int prevSlot = fi > 0 ? filledSlots.get(fi - 1) : -1;
			int nextSlot = fi < filledSlots.size() - 1 ? filledSlots.get(fi + 1) : -1;
			menu.getItems().add(buildSlotSubMenu(menu, slotIdx, name, path, prevSlot, nextSlot));
		}
		
		menu.getItems().add(makeSectionHeader("관리"));
		
		int nextEmpty = findNextEmptySlot();
		if (nextEmpty < OFFICE_SLOT_COUNT) {
			menu.getItems().add(makeRichMenuItem("➕", "새 도구 등록",
				"업무 프로그램 추가 (슬롯 " + (nextEmpty + 1) + "/" + OFFICE_SLOT_COUNT + ")", null,
			() -> openSlotEditor(findNextEmptySlot())));
			} else {
			MenuItem fullItem = makeRichMenuItem("🚫", "슬롯이 가득 찼습니다",
			"최대 " + OFFICE_SLOT_COUNT + "개까지 등록 가능합니다", null, null);
			fullItem.setDisable(true);
			menu.getItems().add(fullItem);
		}
		
	}
	
	/** 다음 빈 슬롯 인덱스 반환. 모두 찼으면 OFFICE_SLOT_COUNT 반환. */
	private int findNextEmptySlot() {
		for (int i = 0; i < OFFICE_SLOT_COUNT; i++) {
			String name = clockHost != null ? clockHost.getConfig(slotNameKey(i), "") : "";
			String path = clockHost != null ? clockHost.getConfig(slotPathKey(i), "") : "";
			if (name.isEmpty() || path.isEmpty()) return i;
		}
		return OFFICE_SLOT_COUNT;
	}
	
	/**
		* 등록된 슬롯 하나를 서브메뉴(Menu)로 생성한다. — Swing buildSlotMenu 와 동일한 구조
		*
		* 등록된 슬롯 : "📌 앱이름 ▶"  →  [▶ 실행]  [─]  [🗑️ 삭제]  [─]  [↑ 위로]  [↓ 아래로]
		*
		* 동작 후 populateOfficeMenuItems(officeMenu) + officeMenu.show() 로
		* 업무도구 메뉴를 다시 열어 준다.
	*/
	private Menu buildSlotSubMenu(Menu officeMenu, int slotIdx,
		String name, String path, int prevSlot, int nextSlot) {
		
		Menu slotMenu = makeMenu("📌 " + name, path);
		
		// ── ▶ 실행 ────────────────────────────────────────────
		slotMenu.getItems().add(makeRichMenuItem("▶", "실행",
		path, null, () -> launchByPath(path)));
		
		slotMenu.getItems().add(new SeparatorMenuItem());
		
		// ── 🗑️ 삭제 ───────────────────────────────────────────
		MenuItem delItem = makeRichMenuItem("🗑️", "삭제", "이 항목을 삭제합니다", null, () -> {
			if (clockHost == null) { showNotReady(); return; }
			Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
			confirm.initOwner(stage);
			confirm.setTitle("업무도구 삭제");
			confirm.setHeaderText(null);
			confirm.setContentText("「" + name + "」 을 삭제하시겠습니까?");
			confirm.setOnShown(ev ->
			((Stage) confirm.getDialogPane().getScene().getWindow()).setAlwaysOnTop(true));
			confirm.showAndWait().ifPresent(btn -> {
				if (btn != ButtonType.OK) return;
				clockHost.setMultipleConfigAndSave(slotNameKey(slotIdx), "", slotPathKey(slotIdx), "");
				Platform.runLater(() -> {
					populateOfficeMenuItems(officeMenu);
					officeMenu.show();
				});
			});
		});
		slotMenu.getItems().add(delItem);
		
		slotMenu.getItems().add(new SeparatorMenuItem());
		
		// ── ↑ 위로 이동 ───────────────────────────────────────
		MenuItem upItem = makeRichMenuItem("↑", "위로 이동", "목록에서 한 칸 위로", null, () -> {
			swapSlots(slotIdx, prevSlot);
			Platform.runLater(() -> {
				populateOfficeMenuItems(officeMenu);
				officeMenu.show();
			});
		});
		upItem.setDisable(prevSlot < 0);
		slotMenu.getItems().add(upItem);
		
		// ── ↓ 아래로 이동 ─────────────────────────────────────
		MenuItem downItem = makeRichMenuItem("↓", "아래로 이동", "목록에서 한 칸 아래로", null, () -> {
			swapSlots(slotIdx, nextSlot);
			Platform.runLater(() -> {
				populateOfficeMenuItems(officeMenu);
				officeMenu.show();
			});
		});
		downItem.setDisable(nextSlot < 0);
		slotMenu.getItems().add(downItem);
		
		return slotMenu;
	}
	
	/** 작은 스타일 툴팁 */
	private Tooltip makeSmallTooltip(String text) {
		Tooltip t = new Tooltip(text);
		t.setStyle("-fx-font-family:'Malgun Gothic'; -fx-font-size:11px;"
			+ "-fx-background-color:rgba(255,245,251,0.95); -fx-text-fill:" + fgColor()
		+ "; -fx-background-radius:8; -fx-border-color:" + borderColor() + "; -fx-border-radius:8;");
		return t;
	}
	
	
}
