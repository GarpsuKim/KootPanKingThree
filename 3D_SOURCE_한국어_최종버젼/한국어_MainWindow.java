import javafx.application.Application;
import javafx.stage.Stage;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Map;

public class MainWindow {
	private static volatile MainWindow instance;
	private static volatile boolean fxStarted = false;
	// ── 색 상수 ──────────────────────────────────────────────────
    private static final String BG      = "linear-gradient(to bottom right, #fff7fb 0%, #ffe8f4 45%, #ffd6eb 100%)";
    private static final String FG      = "#6b2148";
    private static final String TS_CLR  = "#d14f92";
    private static final String BAR_BG  = "rgba(255, 214, 235, 0.78)";
    private static final String MENU_BG = "-fx-background-color: rgba(255, 196, 224, 0.72);"
	+ "-fx-background-insets: 0;"
	+ "-fx-border-color: rgba(209, 79, 146, 0.45);"
	+ "-fx-border-width: 0 0 1 0;";
    private static final String GLASS_PANEL  = "rgba(255, 255, 255, 0.32)";
    private static final String GLASS_BORDER = "rgba(209, 79, 146, 0.35)";
    private static final String GLASS_HOVER  = "rgba(255, 255, 255, 0.52)";
    // ── 테마 모드 ───────────────────────────────────────────────
    private enum ThemeMode { BASIC, PINK_GLASS }
    private static  ThemeMode themeMode = ThemeMode.PINK_GLASS;
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
    private static  Stage    theStage;
    private static  TextArea logArea;
    private static  Label    statusBar;
    private static  TabPane  centerTabs;  // 파일 탭 추가용
    // ─────────────────────────────────────────────────────────────
    private static final String APP_NAME_title = AppContext.APP_NAME_title;
    private static String       appDir;
    private static String       settingsDir;
    private static String       configFile;
    private Properties   config     = new Properties();
    // private IniController iniController;
    // ── 세계시계 자식 창 ──────────────────────────────────────────
    // private final java.util.Map<java.time.ZoneId, Stage> childStages = new java.util.LinkedHashMap<>();
	private static interface CityWindowHandle {
		void focus();
		void close();
		boolean isShowing();
	}
	private static final java.util.Map<java.time.ZoneId, CityWindowHandle> childStages = new java.util.LinkedHashMap<>();
    private static String startArg1 = "default1", startArg2 = "default2", startArg3 = "default3";
    private static  GmailSender gmail  = GmailSender.getInstance();
    private static  Kakao        kakao  = new Kakao();
    private static  TelegramBot        tg;
    private static  ChimeController    chimeController; // lazy-init
    private static  GoogleCalendarService googleCalendarService = new GoogleCalendarService();
    private static  NaverCalendarService  naverCalendarService  = new NaverCalendarService();
    private final AppRestarter.PCShortcut pcShortcut = new AppRestarter.PCShortcut();
    private final AppRestarter appRestarter = new AppRestarter(gmail, null);
    // ═══════════════════════════════════════════════════════════
    //  생성자 / JavaFX 진입점
    // ═══════════════════════════════════════════════════════════
	public MainWindow() {
	    instance = this;
		System.out.println("■■■■■ MainWindow()");
	}
	public static MainWindow getInstance() {
		System.out.println("■■■■■ MainWindow.getInstance()");
		return instance;
	}
	public static boolean isFxStarted() {
		System.out.println("■■■■■ MainWindow.isFxStarted()");
		return fxStarted;
	}
	/*
		@Override
		public void start(Stage primaryStage) {
	    instance = this;
		fxStarted = true;
		theStage = primaryStage;
        // ── 1. 경로 초기화 ──────────────────────────────────────
		System.out.println("■■■■■ start(Stage primaryStage)");
        appDir      = resolveAppDir();
        settingsDir = resolveSettingsDir();
        configFile  = IniController.getPrimaryConfigFilePath();
        iniController = new IniController(appDir, settingsDir, configFile, "Local");
        iniController.ensureInitialized();
        iniController.load();
        config = iniController.getProperties();
        // ── 5. UI 구성 ──────────────────────────────────────────
        theMainWindow(primaryStage);
		}
	*/
    // ═══════════════════════════════════════════════════════════
    //  UI 초기화
    // ═══════════════════════════════════════════════════════════
    public void theMainWindow(Stage primaryStage , String arg1 , String arg2 , String arg3) {
        appDir      = AppContext.getAPP_DIR();
        settingsDir = AppContext.SETTINGS_DIR;
		String args = "[" + arg1 + "] , [" + arg2 + "] , [" + arg3 + "]" ;
		System.out.println("■■■■■ theMainWindow(Stage primaryStage) " + args );
		theStage = primaryStage;
        primaryStage.setTitle(APP_NAME_title + args );
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setStyle(logStyle());
        logArea.setFont(javafx.scene.text.Font.font(AppContext.getUiFontFamily(), AppContext.getUiFontSize()));
        ScrollPane scrollPane = new ScrollPane(logArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle(scrollPaneStyle());
        statusBar = new Label("준비");
        statusBar.setMaxWidth(Double.MAX_VALUE);
        statusBar.setStyle(statusBarStyle());
		
        // ── 즐겨찾기 콜백 등록 (buildGeneralPane 호출 전) ────────
        pcShortcut.favoriteCallback = (favName, favPath) -> {
            int slot = AppContext.nextEmptyFavoriteSlot();
            if (slot >= AppContext.FAVORITE_SLOT_COUNT) {
                showAlert("즐겨찾기 슬롯이 가득 찼습니다 (최대 "
				+ AppContext.FAVORITE_SLOT_COUNT + "개).", "즐겨찾기");
                return;
			}
            AppContext.setFavorite(slot, favName, favPath);
            rebuildMenuBar();
            setStatus("즐겨찾기 추가: " + favName);
		};
        centerTabs = new TabPane();
        centerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        Tab logTab     = new Tab("📋 로그",    scrollPane);
        Tab generalTab = new Tab("📦 일반 앱", pcShortcut.buildGeneralPane());
        Tab systemTab  = new Tab("⚙ 시스템 앱", pcShortcut.buildSystemPane());
        logTab.setClosable(false);
        generalTab.setClosable(false);
        systemTab.setClosable(false);
        centerTabs.getTabs().addAll(logTab, generalTab, systemTab);
        centerTabs.getSelectionModel().select(generalTab); // 기본 탭: 일반 앱
		
        BorderPane root = new BorderPane();
        root.setStyle(rootStyle());
        root.setCenter(centerTabs);
        root.setBottom(statusBar);
        root.setTop(buildMenuBar());
        Scene scene = new Scene(root, 860, 520);
        AppContext.applyGlobalFont(scene);  // 전역 폰트 적용
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest((WindowEvent e) -> {
            e.consume();
            doClose();
		});
        primaryStage.show();
        // ── AppRestarter FX 컨텍스트 주입 ───────────────────────
        AppRestarter.setOwnerStage(primaryStage);
        AppRestarter.setExitCallback(this::exitAll);
        openTheCity ( " ", java.time.ZoneId.systemDefault() , " ");
	}
	public void toggleTheMainWindow() {
		System.out.println("■■■■■ toggleTheMainWindow()");
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(this::toggleTheMainWindow);
			return;
		}
		if (theStage == null) {
			System.out.println("toggleTheMainWindow(): theStage == null");
			return;
		}
		if (theStage.isShowing()) {
			theStage.hide();
			} else {
			theStage.show();
			theStage.setAlwaysOnTop(true);
			theStage.toFront();
			theStage.requestFocus();
			theStage.setAlwaysOnTop(false);
		}
	}
	/** 메인창을 항상 앞으로 보이게 (숨겨져 있어도 show) */
	public void showTheMainWindow() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(this::showTheMainWindow);
			return;
		}
		if (theStage == null) return;
		theStage.show();
		theStage.setAlwaysOnTop(true);
		theStage.toFront();
		theStage.requestFocus();
		theStage.setAlwaysOnTop(false);
	}

	public static void toggleMainWindowSafe() {
		System.out.println("■■■■■ toggleMainWindowSafe()");
		MainWindow w = instance;
		if (w == null) {
			System.out.println("toggleMainWindowSafe(): MainWindow instance == null");
			return;
		}
		if (!fxStarted) {
			System.out.println("toggleMainWindowSafe(): JavaFX start() 아직 호출 전");
			return;
		}
		Platform.runLater(w::toggleTheMainWindow);
	}
    // ═══════════════════════════════════════════════════════════
    // private void saveConfig() {	}
    /** config 저장 */
	/*
		private void saveConfig() {
        try {
		config.setProperty("gmail.from",            gmail.from);
		config.setProperty("gmail.pass",            gmail.pass);
		config.setProperty("gmail.lastTo",          gmail.lastTo);
		config.setProperty("kakao.apiKey",          kakao.kakaoRestApiKey);
		config.setProperty("kakao.clientSecret",    kakao.kakaoClientSecret);
		config.setProperty("kakao.refreshToken",    kakao.kakaoRefreshToken);
		if (chimeController != null) {
		config.setProperty("chimeEnabled",  String.valueOf(chimeController.isEnabled()));
		config.setProperty("chimeFile",     chimeController.getFile());
		config.setProperty("chimeDuration", String.valueOf(chimeController.getDuration()));
		config.setProperty("chimeVolume",   String.valueOf(chimeController.getVolume()));
		boolean[] mins = chimeController.getMinutes();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 60; i++) if (mins[i]) { if (sb.length() > 0) sb.append(','); sb.append(i); }
		config.setProperty("chimeMinutes", sb.toString());
		}
		if (iniController != null) {
		iniController.save();
		} else {
		try (FileOutputStream fos = new FileOutputStream(configFile)) {
		config.store(fos, "KootPanKingThree Settings");
		} catch (IOException ignored) {}
		}
		} catch (Exception e) {
		System.out.println("saveConfig() Exception: " + e.getMessage());
		}
		}
	*/
	/*
		//   로그 파일 열기
		private void openLogFile() {
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
	*/
    /** 설정 파일 열기 */
	/*
		private void openConfigFile() {
		boolean ok = AppContext.openCONFIG_FILE();
		if (!ok) {
		showAlert("설정 파일을 열지 못했습니다.\n" + AppContext.CONFIG_FILE, "기본 설정 파일");
		}
		}
	*/
    /** ChimeController — 마스터 ini(AppContext)에서 설정 복원 */
    private void initChimeControllerIfNeeded(Stage owner) {
        if (chimeController != null) return;
        chimeController = new ChimeController(owner, new ChimeController.HostCallback() {
            @Override public boolean isChild() { return false; }
            @Override public java.time.ZoneId getTimeZone() { return java.time.ZoneId.systemDefault(); }
            @Override public void startRainbow(int durationSec) { /* 시계 미연동 시 무시 */ }
            @Override public BackgroundPlayer.YoutubePlayer getVideoPlayer() { return null; }
		});
        try {
            chimeController.setEnabled(AppContext.getBoolean("chimeEnabled", false));
            chimeController.setFile(AppContext.get("chimeFile", ""));
            chimeController.setDuration(AppContext.getInt("chimeDuration", 0));
            chimeController.setVolume(AppContext.getInt("chimeVolume", 80));
            String minsStr = AppContext.get("chimeMinutes", "0");
            boolean[] loadedMins = new boolean[60];
            if (!minsStr.isEmpty()) {
                for (String s : minsStr.split(",")) {
                    try {
                        int idx = Integer.parseInt(s.trim());
                        if (idx >= 0 && idx < 60) loadedMins[idx] = true;
					} catch (NumberFormatException ignored) {}
				}
			}
            chimeController.setMinutes(loadedMins);
            chimeController.startCheckTimer();
		} catch (Exception ignored) {}
	}
    /** 팝업 메뉴(KootPanKingThreeApp)에서 호출 — 마스터 차임벨 다이얼로그 표시 후 AppContext에 저장 */
    public void showChimeDialogPublic(Stage owner) {
        if (owner == null) owner = theStage;
        initChimeControllerIfNeeded(owner);
        if (chimeController == null) return;
        chimeController.showChimeDialog();
        // 다이얼로그 닫힌 후 마스터 ini 저장
        saveChimeToAppContext();
	}
    /** 차임벨 설정을 마스터 ini(AppContext)에 저장 */
    private void saveChimeToAppContext() {
        if (chimeController == null) return;
        AppContext.set("chimeEnabled",  String.valueOf(chimeController.isEnabled()));
        AppContext.set("chimeFile",     chimeController.getFile());
        AppContext.set("chimeDuration", String.valueOf(chimeController.getDuration()));
        AppContext.set("chimeVolume",   String.valueOf(chimeController.getVolume()));
        boolean[] mins = chimeController.getMinutes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            if (mins[i]) { if (sb.length() > 0) sb.append(','); sb.append(i); }
		}
        AppContext.set("chimeMinutes", sb.toString());
        AppContext.save();
	}
    // ── 세계시계 서브메뉴 ────────────────────────────────────────
    private javafx.scene.control.Menu buildGlobalMenu() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("🌍 세계시계");
        // {메뉴 표시명, ZoneId, 시계 날짜 prefix}
        String[][] cities = {
            {"🇰🇷 서울",    "Asia/Seoul",          "서울"},
            {"🇯🇵 도쿄",    "Asia/Tokyo",          "도쿄"},
            {"🇨🇳 베이징",  "Asia/Shanghai",       "베이징"},
            {"🇹🇭 방콕",    "Asia/Bangkok",        "방콕"},
            {"🇮🇳 뭄바이",  "Asia/Kolkata",        "뭄바이"},
            {"🇦🇪 두바이",  "Asia/Dubai",          "두바이"},
            {"🇷🇺 모스크바","Europe/Moscow",        "모스크바"},
            {"🇫🇷 파리",    "Europe/Paris",        "파리"},
            {"🇩🇪 베를린",  "Europe/Berlin",       "베를린"},
            {"🇬🇧 런던",    "Europe/London",       "런던"},
            {"🇺🇸 뉴욕",    "America/New_York",    "뉴욕"},
            {"🇺🇸 시카고",  "America/Chicago",     "시카고"},
            {"US 덴버",  "America/Denver",     "덴버"},
            {"US 디트로이트",  "America/Detroit",     "디트로이트"},
			{"🇺🇸 LA",      "America/Los_Angeles", "L.A."},
            {"🇺🇸 알래스카",      "America/Anchorage", "알래스카"},
			{"🇺🇸 하와이",      "Pacific/Honolulu", "하와이"},
            {"🇧🇷 상파울루","America/Sao_Paulo",   "상파울루"},
            {"🇦🇺 시드니",  "Australia/Sydney",    "시드니"},
		};
        for (String[] c : cities) {
            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(c[0]);
            final String zoneStr  = c[1];
            final String menuName = c[0];
            final String prefix   = c[2];
            item.setOnAction(e -> {
                System.out.println("세계시계 요청: " + menuName + " (" + zoneStr + ")");
                openTheCity(menuName, java.time.ZoneId.of(zoneStr), prefix);
			});
            menu.getItems().add(item);
		}
        return menu;
	}
    // ── 세계시계 자식 창 열기 ─────────────────────────────────────
	public void openTheCity(String cityName, java.time.ZoneId zoneId, String clockPrefix) {
		System.out.println("■■■■■ [openTheCity] cityName=" + cityName + " zone=" + zoneId);
		CityWindowHandle existing = childStages.get(zoneId);
		if (existing != null) {
			if (existing.isShowing()) {
				existing.focus();
				return;
				} else {
				childStages.remove(zoneId);
			}
		}
		Stage childStage = new Stage();
		String childConfigFile =
		AppContext.ensureCityConfigFile(cityName, clockPrefix, zoneId.getId());
		KootPanKingThreeApp childApp =
        new KootPanKingThreeApp(childConfigFile, clockPrefix, zoneId);
		childApp.startInstance(childStage, Arrays.asList(startArg1, startArg2, startArg3));
		// 메인 시계(시스템 기본 timezone)면 KootPanKingThreeLaunch.app에 할당
		if (zoneId.equals(java.time.ZoneId.systemDefault()))
		KootPanKingThreeLaunch.app = childApp;
		// 메인 시계(시스템 기본 timezone)면 KootPanKingThreeLaunch.app에 할당
		if (zoneId.equals(java.time.ZoneId.systemDefault()))
		KootPanKingThreeLaunch.app = childApp;
		CityWindowHandle handle = new CityWindowHandle() {
			private boolean closing = false;
			@Override
			public void focus() {
				if (!Platform.isFxApplicationThread()) {
					Platform.runLater(this::focus);
					return;
				}
				if (childStage.isShowing()) {
					childStage.show();
					childStage.toFront();
					childStage.requestFocus();
				}
			}
			@Override
			public void close() {
				if (!Platform.isFxApplicationThread()) {
					Platform.runLater(this::close);
					return;
				}
				if (closing) return;
				closing = true;
				childStages.remove(zoneId);
				try {
					childApp.close();
					} catch (Exception ex) {
					AppLogger.logException(ex);
				}
				try {
					if (childStage.isShowing()) {
						childStage.close();
						} else {
						childStage.hide();
					}
					} catch (Exception ex) {
					AppLogger.logException(ex);
				}
			}
			/*
				@Override
				public void close() {
				if (!Platform.isFxApplicationThread()) {
				Platform.runLater(this::close);
				return;
				}
				if (closing) return;
				closing = true;
				childStages.remove(zoneId);
				try {
				if (childStage.isShowing()) {
				childStage.close();
				} else {
				childStage.hide();
				}
				} catch (Exception ex) {
				AppLogger.logException(ex);
				}
				}
			*/
			@Override
			public boolean isShowing() {
				return childStage.isShowing();
			}
		};
		childStages.put(zoneId, handle);
		childStage.setOnCloseRequest(e -> {
			e.consume();
			handle.close();
		});
		childStage.setOnHidden(e -> childStages.remove(zoneId));
	}
	public void focusTheCity(java.time.ZoneId zoneId) {
		CityWindowHandle h = childStages.get(zoneId);
		if (h != null) h.focus();
	}
	public void closeTheCity(java.time.ZoneId zoneId) {
		CityWindowHandle h = childStages.get(zoneId);
		if (h != null) h.close();
	}
	public void closeAllCities() {
		java.util.List<CityWindowHandle> list =
        new java.util.ArrayList<>(childStages.values());
		for (CityWindowHandle h : list) {
			if (h != null) h.close();
		}
	}
	/*
		public void openTheCity(String cityName, java.time.ZoneId zoneId, String clockPrefix) {
        System.out.println("■■■■■ [openTheCity] cityName=" + cityName + " zone=" + zoneId);
        Stage existingStage = childStages.get(zoneId);
        if (existingStage != null) {
		existingStage.toFront();
		existingStage.requestFocus();
		return;
		}
        Stage childStage = new Stage();
        String safeName = cityName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        String childConfigFile = settingsDir + "clock_settings_" + safeName + ".ini";
        java.io.File childIni = new java.io.File(childConfigFile);
        if (!childIni.exists()) {
		// saveConfig();
		java.util.Properties copy = new java.util.Properties();
		copy.putAll(config);
		copy.setProperty("cityName", clockPrefix);
		copy.setProperty("timeZone", zoneId.getId());
		try (java.io.FileOutputStream fos = new java.io.FileOutputStream(childIni)) {
		copy.store(fos, "KootPanKingThree Child Settings - " + cityName);
		} catch (Exception ex) {
		AppLogger.logException(ex);
		}
		}
        KootPanKingThreeApp childApp = new KootPanKingThreeApp(childConfigFile, clockPrefix, zoneId);
        childApp.startInstance(childStage, Arrays.asList(startArg1, startArg2, startArg3));
        childStages.put(zoneId, childStage);
        childStage.setOnCloseRequest(e -> childStages.remove(zoneId));
		}
	*/
    // ── Gmail / Calendar 서브메뉴 ────────────────────────────────
    private javafx.scene.control.Menu buildGmailMenu() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("📧 Gmail / Calendar");
        // "지금 Gmail 보내기" → "Gmail 설정" 으로 명칭 변경 + 다이얼로그 연결
        javafx.scene.control.MenuItem gmailSettings = new javafx.scene.control.MenuItem("Gmail 설정");
        gmailSettings.setOnAction(e -> showGmailSettingsDialog(theStage));
        javafx.scene.control.MenuItem guide = new javafx.scene.control.MenuItem("Calendar 설정 안내");
        guide.setOnAction(e -> {
            try { java.awt.Desktop.getDesktop().browse(
			new java.net.URI("https://support.google.com/calendar/answer/99358"));
            } catch (Exception ex) { showAlert("브라우저 열기 실패: " + ex.getMessage(), "안내"); }
		});
        javafx.scene.control.Menu googleCal = new javafx.scene.control.Menu("📧 구글 캘린더");
        googleCal.getItems().addAll(
            calMenuAction("향후 3일", () -> showCalendarResult("구글","google",3,"next")),
            calMenuAction("향후 7일", () -> showCalendarResult("구글","google",7,"next")),
            calMenuAction("지난 7일", () -> showCalendarResult("구글","google",7,"past")),
            calMenuAction("이번 달",  () -> showCalendarResult("구글","google",0,"month")),
            calMenuAction("다음 달",  () -> showCalendarResult("구글","google",0,"nextmonth"))
		);
        javafx.scene.control.Menu naverCal = new javafx.scene.control.Menu("🟢 네이버 캘린더");
        naverCal.getItems().addAll(
            calMenuAction("향후 3일", () -> showCalendarResult("네이버","naver",3,"next")),
            calMenuAction("향후 7일", () -> showCalendarResult("네이버","naver",7,"next")),
            calMenuAction("지난 7일", () -> showCalendarResult("네이버","naver",7,"past")),
            calMenuAction("이번 달",  () -> showCalendarResult("네이버","naver",0,"month")),
            calMenuAction("다음 달",  () -> showCalendarResult("네이버","naver",0,"nextmonth"))
		);
        // 네이버 설정 서브메뉴
        javafx.scene.control.Menu naverMenu = new javafx.scene.control.Menu("🟢 네이버 설정");
        javafx.scene.control.MenuItem naverGuide  = new javafx.scene.control.MenuItem("네이버 설정 안내");
        javafx.scene.control.MenuItem naverPasswd = new javafx.scene.control.MenuItem("비밀번호 설정");
        naverGuide.setOnAction(e -> showAlert(
            "네이버 CalDAV 서비스를 활성화한 후\n계정 정보를 아래 [비밀번호 설정]에서 입력하세요.\n\n" +
            "CalDAV 활성화: 네이버 캘린더 → 설정 → CalDAV 연동",
		"네이버 설정 안내"));
        naverPasswd.setOnAction(e -> showNaverSettingsDialog(theStage));
        naverMenu.getItems().addAll(naverGuide, naverPasswd);
        menu.getItems().addAll(
            gmailSettings, guide, new javafx.scene.control.SeparatorMenuItem(),
            googleCal, new javafx.scene.control.SeparatorMenuItem(),
            naverCal, new javafx.scene.control.SeparatorMenuItem(),
            naverMenu
		);
        return menu;
	}
    private javafx.scene.control.MenuItem calMenuAction(String label, Runnable action) {
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(label);
        item.setOnAction(e -> new Thread(action, "CalendarQuery").start());
        return item;
	}
    private void showCalendarResult(String service, String type, int days, String range) {
        try {
            String title;
            String content;
            if ("google".equals(type)) {
                if (!googleCalendarService.isInitialized()) googleCalendarService.init();
                java.util.List<GoogleCalendarService.CalendarEvent> events;
                switch (range) {
                    case "next":      events = googleCalendarService.getNextDays(days);  title = "📧 구글 향후 " + days + "일"; break;
                    case "past":      events = googleCalendarService.getPastDays(days);   title = "📧 구글 지난 " + days + "일"; break;
                    case "month":     events = googleCalendarService.getThisMonth();      title = "📧 구글 이번 달"; break;
                    case "nextmonth": events = googleCalendarService.getNextMonth();      title = "📧 구글 다음 달"; break;
                    default:          events = java.util.Collections.emptyList();         title = "📧 구글 캘린더";
				}
                content = GoogleCalendarService.formatEvents(title, events);
				} else {
                if (!naverCalendarService.isInitialized()) naverCalendarService.init();
                java.util.List<NaverCalendarService.CalendarEvent> events;
                switch (range) {
                    case "next":      events = naverCalendarService.getNextDays(days);   title = "🟢 네이버 향후 " + days + "일"; break;
                    case "past":      events = naverCalendarService.getPastDays(days);    title = "🟢 네이버 지난 " + days + "일"; break;
                    case "month":     events = naverCalendarService.getThisMonth();       title = "🟢 네이버 이번 달"; break;
                    case "nextmonth": events = naverCalendarService.getNextMonth();       title = "🟢 네이버 다음 달"; break;
                    default:          events = java.util.Collections.emptyList();         title = "🟢 네이버 캘린더";
				}
                content = NaverCalendarService.formatEvents(title, events);
			}
            final String dlgTitle   = title;
            final String dlgContent = content;
            Platform.runLater(() -> showScheduleDialog(dlgTitle, dlgContent));
			} catch (Exception e) {
            final String err = e.getMessage();
            Platform.runLater(() -> showScheduleDialog("캘린더 오류", "일정 조회 실패:\n" + err));
		}
	}
    // ── 카카오톡 서브메뉴 ─────────────────────────────────────────
    private javafx.scene.control.Menu buildKakaoMenuFx() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("카카오톡...");
        javafx.scene.control.MenuItem loginItem = new javafx.scene.control.MenuItem("카카오 로그인됨");
        javafx.scene.control.MenuItem sendItem  = new javafx.scene.control.MenuItem("나에게 메시지 보내기...");
        javafx.scene.control.MenuItem guideItem = new javafx.scene.control.MenuItem("설정 안내...");
        guideItem.setOnAction(e -> showAlert(
            "카카오 REST API Key / Client Secret / Refresh Token 을\n" +
            "설정 파일(clock_settings.ini)에 입력하세요.\n\n" +
		"  kakao.apiKey=...\n  kakao.clientSecret=...\n  kakao.refreshToken=...", "카카오톡 설정"));
        menu.getItems().addAll(loginItem, sendItem, guideItem);
        return menu;
	}
    // ── 텔레그램 서브메뉴 ─────────────────────────────────────────
    private javafx.scene.control.Menu buildTelegramMenuFx() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("텔레그램");
        javafx.scene.control.MenuItem tokenSettings = new javafx.scene.control.MenuItem("텔레그램 비밀번호 설정");
        javafx.scene.control.MenuItem help          = new javafx.scene.control.MenuItem("텔레그램 설정 안내");
        tokenSettings.setOnAction(e -> showTelegramSettingsDialog(theStage));
        help.setOnAction(e -> {
            if (tg != null) tg.showTelegramHelp(theStage);
		});
        menu.getItems().addAll(tokenSettings, help);
        return menu;
	}
    // ── Gmail 설정 다이얼로그 (ID + 비밀번호) ───────────────────
    public void showGmailSettingsDialog(javafx.stage.Stage owner) {
        if (owner == null) owner = theStage;
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(owner);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("Gmail 설정");
        // ── 레이블 ───────────────────────────────────────────────
        javafx.scene.control.Label idLbl   = new javafx.scene.control.Label("발신자 Gmail ID:");
        javafx.scene.control.Label passLbl = new javafx.scene.control.Label("발신자 (앱) 비밀번호:");
        javafx.scene.control.Label toLbl   = new javafx.scene.control.Label("수신자 이메일 ID:");
        // ── 발신자 ID ────────────────────────────────────────────
        javafx.scene.control.TextField idField = new javafx.scene.control.TextField(AppContext.getGmailFrom());
        idField.setPrefWidth(280);
        idField.setPromptText("example@gmail.com");
        // ── 발신자 비밀번호 (숨김 + 표시 토글) ─────────────────
        javafx.scene.control.PasswordField passField = new javafx.scene.control.PasswordField();
        passField.setText(AppContext.getGmailPass());
        passField.setPrefWidth(280);
        passField.setPromptText("Google 앱 비밀번호 16자리");
        javafx.scene.control.CheckBox showPass = new javafx.scene.control.CheckBox("표시");
        javafx.scene.control.TextField passVisible = new javafx.scene.control.TextField(AppContext.getGmailPass());
        passVisible.setPrefWidth(280);
        passVisible.setVisible(false);
        passVisible.setManaged(false);
        showPass.setOnAction(ev -> {
            boolean show = showPass.isSelected();
            passField.setVisible(!show);  passField.setManaged(!show);
            passVisible.setVisible(show); passVisible.setManaged(show);
            if (show) passVisible.setText(passField.getText());
            else      passField.setText(passVisible.getText());
		});
        // ── 수신자 이메일 ID ─────────────────────────────────────
        javafx.scene.control.TextField toField = new javafx.scene.control.TextField(
		AppContext.get("gmail.lastTo", ""));
        toField.setPrefWidth(280);
        toField.setPromptText("수신자@example.com");
        // ── 그리드 ───────────────────────────────────────────────
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8); grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(16));
        grid.add(idLbl,   0, 0); grid.add(idField,   1, 0, 2, 1);
        grid.add(passLbl, 0, 1); grid.add(passField, 1, 1); grid.add(showPass, 2, 1);
        grid.add(new javafx.scene.control.Label(""), 0, 2);
        grid.add(passVisible, 1, 2, 2, 1);
        grid.add(toLbl,   0, 3); grid.add(toField,   1, 3, 2, 1);
        // ── 힌트 ─────────────────────────────────────────────────
        javafx.scene.control.Label hint = new javafx.scene.control.Label(
		"※ Google 계정 → 보안 → 앱 비밀번호에서 생성하세요.");
        hint.setStyle("-fx-font-size:11px; -fx-text-fill:#777;");
        hint.setWrapText(true);
        hint.setPadding(new javafx.geometry.Insets(0, 16, 4, 16));
        // ── 테스트 결과 레이블 ───────────────────────────────────
        javafx.scene.control.Label resultLbl = new javafx.scene.control.Label("");
        resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold;");
        resultLbl.setWrapText(true);
        resultLbl.setMaxWidth(380);
        resultLbl.setPadding(new javafx.geometry.Insets(0, 16, 4, 16));
        // ── 버튼: 저장 / 테스트 발송 / 취소 ────────────────────
        javafx.scene.control.Button okBtn   = new javafx.scene.control.Button("저장");
        javafx.scene.control.Button testBtn = new javafx.scene.control.Button("테스트 Gmail 발송");
        javafx.scene.control.Button canBtn  = new javafx.scene.control.Button("취소");
        okBtn.setDefaultButton(true); canBtn.setCancelButton(true);
        okBtn.setPrefWidth(72); canBtn.setPrefWidth(120); testBtn.setPrefWidth(130);
        // 현재 입력 필드에서 pass 문자열을 읽는 헬퍼
        java.util.function.Supplier<String> getCurrentPass = () ->
		showPass.isSelected() ? passVisible.getText().trim()
		: passField.getText().trim();
        canBtn.setOnAction(ev -> dlg.close());
        okBtn.setOnAction(ev -> {
            String id   = idField.getText().trim();
            String pass = getCurrentPass.get();
            String to   = toField.getText().trim();
            AppContext.setGmailFrom(id);
            AppContext.setGmailPass(pass);
            if (!to.isEmpty()) {
                AppContext.set("gmail.lastTo", to);
                AppContext.save();
			}
            if (gmail != null) { gmail.from = id; gmail.pass = pass; gmail.lastTo = to; }
            dlg.close();
            showAlert("Gmail 설정이 저장되었습니다.", "Gmail 설정");
		});
        testBtn.setOnAction(ev -> {
            String id   = idField.getText().trim();
            String pass = getCurrentPass.get();
            String to   = toField.getText().trim();
            if (id.isEmpty() || pass.isEmpty() || to.isEmpty()) {
                resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#cc0000;");
                resultLbl.setText("⚠ 발신자 ID · 비밀번호 · 수신자를 모두 입력하세요.");
                return;
			}
            testBtn.setDisable(true);
            testBtn.setText("발송 중...");
            resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#555555;");
            resultLbl.setText("⏳ 테스트 메일 발송 중...");
            final String fId = id, fPass = pass, fTo = to;
            // ── 첨부 파일 수집: 마스터 ini + 로그 파일 ──────────
            final java.util.List<java.io.File> attachFiles = new java.util.ArrayList<>();
            java.io.File iniFile = new java.io.File(AppContext.CONFIG_FILE);
            if (iniFile.exists()) attachFiles.add(iniFile);
            String logPath = AppLogger.getLogFilePath();
            if (logPath != null && !logPath.isEmpty()) {
                java.io.File logFile = new java.io.File(logPath);
                if (logFile.exists()) attachFiles.add(logFile);
			}
            new Thread(() -> {
                String result = gmail != null
				? gmail.testSend(fId, fPass, fTo, attachFiles)
				: GmailSender.getInstance().testSend(fId, fPass, fTo, attachFiles);
                javafx.application.Platform.runLater(() -> {
                    testBtn.setDisable(false);
                    testBtn.setText("테스트 Gmail 발송");
                    boolean ok = result == null || result.isEmpty();
                    resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:"
					+ (ok ? "#006600;" : "#cc0000;"));
                    resultLbl.setText(ok ? "✅ 테스트 메일 발송 성공!" : "❌ " + result);
				});
			}, "GmailTestSend").start();
		});
        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(8, testBtn, okBtn, canBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        btnRow.setPadding(new javafx.geometry.Insets(0, 16, 12, 16));
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(0, grid, hint, resultLbl, btnRow);
        root.setStyle("-fx-background-color: white;");
        dlg.setScene(new javafx.scene.Scene(root));
        dlg.sizeToScene();
        dlg.showAndWait();
	}
    // ── 네이버 설정 다이얼로그 (ID + 비밀번호) ─────────────────
    public void showNaverSettingsDialog(javafx.stage.Stage owner) {
        if (owner == null) owner = theStage;
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(owner);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("네이버 CalDAV 설정");
        javafx.scene.control.Label idLbl   = new javafx.scene.control.Label("네이버 ID:");
        javafx.scene.control.Label passLbl = new javafx.scene.control.Label("비밀번호:");
        javafx.scene.control.TextField idField = new javafx.scene.control.TextField(AppContext.getNaverId());
        idField.setPrefWidth(260);
        idField.setPromptText("네이버 아이디");
        javafx.scene.control.PasswordField passField = new javafx.scene.control.PasswordField();
        passField.setText(AppContext.getNaverPassword());
        passField.setPrefWidth(260);
        passField.setPromptText("네이버 비밀번호");
        javafx.scene.control.CheckBox showPass = new javafx.scene.control.CheckBox("표시");
        javafx.scene.control.TextField passVisible = new javafx.scene.control.TextField(AppContext.getNaverPassword());
        passVisible.setPrefWidth(260);
        passVisible.setVisible(false);
        passVisible.setManaged(false);
        showPass.setOnAction(ev -> {
            boolean show = showPass.isSelected();
            passField.setVisible(!show);  passField.setManaged(!show);
            passVisible.setVisible(show); passVisible.setManaged(show);
            if (show) passVisible.setText(passField.getText());
            else      passField.setText(passVisible.getText());
		});
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8); grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(16));
        grid.add(idLbl,   0, 0); grid.add(idField,   1, 0, 2, 1);
        grid.add(passLbl, 0, 1); grid.add(passField, 1, 1); grid.add(showPass, 2, 1);
        grid.add(new javafx.scene.control.Label(""), 0, 2);
        grid.add(passVisible, 1, 2, 2, 1);
        javafx.scene.control.Label hint = new javafx.scene.control.Label(
		"※ 네이버 캘린더 → 설정 → CalDAV 연동을 먼저 활성화하세요.");
        hint.setStyle("-fx-font-size:11px; -fx-text-fill:#777;");
        hint.setWrapText(true);
        javafx.scene.control.Button okBtn  = new javafx.scene.control.Button("저장");
        javafx.scene.control.Button canBtn = new javafx.scene.control.Button("취소");
        okBtn.setDefaultButton(true); canBtn.setCancelButton(true);
        okBtn.setPrefWidth(72); canBtn.setPrefWidth(72);
        canBtn.setOnAction(ev -> dlg.close());
        okBtn.setOnAction(ev -> {
            String id   = idField.getText().trim();
            String pass = showPass.isSelected() ? passVisible.getText().trim()
			: passField.getText().trim();
            AppContext.setNaverId(id);
            AppContext.setNaverPassword(pass);
            // NaverCalendarService에 즉시 반영
            if (naverCalendarService != null) naverCalendarService.setCredentials(id, pass);
            dlg.close();
            showAlert("네이버 설정이 저장되었습니다.", "네이버 설정");
		});
        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(8, okBtn, canBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        btnRow.setPadding(new javafx.geometry.Insets(0, 16, 12, 16));
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(0, grid, hint, btnRow);
        hint.setPadding(new javafx.geometry.Insets(0, 16, 6, 16));
        root.setStyle("-fx-background-color: white;");
        dlg.setScene(new javafx.scene.Scene(root));
        dlg.sizeToScene();
        dlg.showAndWait();
	}
    // ── 텔레그램 설정 다이얼로그 (botToken + myChatId) ─────────
    public void showTelegramSettingsDialog(javafx.stage.Stage owner) {
        if (owner == null) owner = theStage;
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(owner);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("텔레그램 설정");
        javafx.scene.control.Label tokenLbl  = new javafx.scene.control.Label("Bot Token:");
        javafx.scene.control.Label chatIdLbl = new javafx.scene.control.Label("My Chat ID:");
        javafx.scene.control.TextField tokenField = new javafx.scene.control.TextField(AppContext.getTelegramBotToken());
        tokenField.setPrefWidth(320);
        tokenField.setPromptText("123456789:ABCdef...");
        javafx.scene.control.TextField chatIdField = new javafx.scene.control.TextField(AppContext.getTelegramMyChatId());
        chatIdField.setPrefWidth(320);
        chatIdField.setPromptText("숫자 Chat ID");
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8); grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(16));
        grid.add(tokenLbl,  0, 0); grid.add(tokenField,  1, 0);
        grid.add(chatIdLbl, 0, 1); grid.add(chatIdField, 1, 1);
        javafx.scene.control.Label hint = new javafx.scene.control.Label(
            "※ BotFather에서 봇을 생성한 뒤 Token을 입력하세요.\n" +
		"   Chat ID는 @userinfobot 에서 확인할 수 있습니다.");
        hint.setStyle("-fx-font-size:11px; -fx-text-fill:#777;");
        hint.setWrapText(true);
        javafx.scene.control.Button okBtn  = new javafx.scene.control.Button("저장");
        javafx.scene.control.Button canBtn = new javafx.scene.control.Button("취소");
        okBtn.setDefaultButton(true); canBtn.setCancelButton(true);
        okBtn.setPrefWidth(72); canBtn.setPrefWidth(72);
        canBtn.setOnAction(ev -> dlg.close());
        okBtn.setOnAction(ev -> {
            String token  = tokenField.getText().trim();
            String chatId = chatIdField.getText().trim();
            AppContext.setTelegramBotToken(token);
            AppContext.setTelegramMyChatId(chatId);
            // TelegramBot 인스턴스에 즉시 반영
            // if (tg != null) { tg.botToken = token; tg.myChatId = chatId; }
            dlg.close();
            showAlert("텔레그램 설정이 저장되었습니다.", "텔레그램 설정");
		});
        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(8, okBtn, canBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        btnRow.setPadding(new javafx.geometry.Insets(0, 16, 12, 16));
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(0, grid, hint, btnRow);
        hint.setPadding(new javafx.geometry.Insets(0, 16, 6, 16));
        root.setStyle("-fx-background-color: white;");
        dlg.setScene(new javafx.scene.Scene(root));
        dlg.sizeToScene();
        dlg.showAndWait();
	}
    // ── About 다이얼로그 ──────────────────────────────────────────
    private void showAboutDialog() {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setResizable(false);
        final int[] sec = {48};
        dlg.setTitle(APP_NAME_title + "  —  " + sec[0] + "초 후 닫힘");
        javafx.scene.text.TextFlow tf = new javafx.scene.text.TextFlow();
        tf.setPadding(new javafx.geometry.Insets(12, 16, 8, 16));
        tf.setPrefWidth(460);
        String[][] items = {
            {"• 대리석 질감 아나로그 시계",                                        "#2aa198"},
            {"• 자유 자재 시계 디자인",                                            "#268bd2"},
            {"• 전세계 주요도시 시계",                                              "#6c71c4"},
            {"• 텔레그램, GMail, 네이버, 카카오톡, 스마트 카메라, 실시간CCTV ...", "#b58900"},
            {"• 김갑수 , 2026-3-18 , 대한민국 서울",                              "#dc322f"}
		};
        for (String[] row : items) {
            javafx.scene.text.Text t = new javafx.scene.text.Text(row[0] + "\n");
            t.setFill(javafx.scene.paint.Color.web(row[1]));
            t.setStyle("-fx-font-family: 'Malgun Gothic'; -fx-font-size: 14px;");
            tf.getChildren().add(t);
		}
        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        final String blogUrl = "https://blog.naver.com/garpsu/224213400580";
        javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink(
		"→ 자세한 안내 : " + blogUrl);
        link.setStyle("-fx-font-family: 'Malgun Gothic'; -fx-font-size: 12px;");
        link.setOnAction(ev -> {
            try { java.awt.Desktop.getDesktop().browse(new java.net.URI(blogUrl)); }
            catch (Exception ex) { System.out.println("[About] 링크 열기 실패: " + ex.getMessage()); }
		});
        javafx.scene.control.Button okBtn = new javafx.scene.control.Button("OK");
        okBtn.setDefaultButton(true);
        javafx.scene.layout.HBox linkBox = new javafx.scene.layout.HBox(link);
        linkBox.setPadding(new javafx.geometry.Insets(4, 10, 2, 10));
        javafx.scene.layout.HBox btnBox = new javafx.scene.layout.HBox(okBtn);
        btnBox.setAlignment(javafx.geometry.Pos.CENTER);
        btnBox.setPadding(new javafx.geometry.Insets(4, 10, 8, 10));
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(tf, sep, linkBox, btnBox);
        dlg.setScene(new javafx.scene.Scene(root));
        final javafx.animation.Timeline[] holder = {null};
        Runnable doClose = () -> { if (holder[0] != null) holder[0].stop(); dlg.close(); };
        okBtn.setOnAction(ev -> doClose.run());
        javafx.animation.Timeline tl = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ev -> {
                sec[0]--;
                dlg.setTitle(APP_NAME_title + "  —  " + sec[0] + "초 후 닫힘");
                if (sec[0] <= 0) doClose.run();
			})
		);
        tl.setCycleCount(48);
        holder[0] = tl;
        dlg.setOnHidden(ev -> { if (holder[0] != null) holder[0].stop(); });
        dlg.show();
        tl.play();
	}
    /** 일정 다이얼로그 — 300초 카운트다운 후 자동 닫힘 */
    public void showScheduleDialog(String title, String content) {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setTitle(title);
        dlg.setAlwaysOnTop(true);
        dlg.setResizable(true);
        javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(content);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setStyle("-fx-font-family: 'Malgun Gothic'; -fx-font-size: 13px;");
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(ta);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        javafx.scene.control.Label countdown = new javafx.scene.control.Label("자동 닫힘: 300초");
        countdown.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        javafx.scene.control.Button closeBtn = new javafx.scene.control.Button("닫기");
        closeBtn.setDefaultButton(true);
        javafx.scene.layout.HBox bottom = new javafx.scene.layout.HBox(12, countdown, closeBtn);
        bottom.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        bottom.setPadding(new javafx.geometry.Insets(6, 10, 6, 10));
        javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane();
        root.setCenter(sp);
        root.setBottom(bottom);
        dlg.setScene(new javafx.scene.Scene(root, 480, 400));
        final int[] remain = {300};
        final javafx.animation.Timeline[] holder = {null};
        Runnable doClose = () -> {
            if (holder[0] != null) holder[0].stop();
            dlg.close();
		};
        closeBtn.setOnAction(e -> doClose.run());
        javafx.animation.Timeline tl = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ev -> {
                remain[0]--;
                countdown.setText("자동 닫힘: " + remain[0] + "초");
                if (remain[0] <= 30)
				countdown.setStyle("-fx-text-fill: #cc4444; -fx-font-size: 11px; -fx-font-weight: bold;");
                if (remain[0] <= 0) doClose.run();
			})
		);
        tl.setCycleCount(300);
        holder[0] = tl;
        dlg.setOnHidden(e -> { if (holder[0] != null) holder[0].stop(); });
        dlg.show();
        tl.play();
	}
    // ═══════════════════════════════════════════════════════════
    //  공개 API
    // ═══════════════════════════════════════════════════════════
    /** 설정값 조회 */
    private String getConfigValue(String key, String defaultValue) {
        return config.getProperty(key, defaultValue);
	}
    /** 여러 설정값 저장 */
	/*
		private void setMultipleConfigAndSave(String... entries) {
        for (int i = 0; i + 1 < entries.length; i += 2)
		config.setProperty(entries[i], entries[i + 1]);
        saveConfig();
		}
	*/
    /** 단일 설정값 저장 */
	/*
		private void setConfigAndSave(String key, String value) {
        config.setProperty(key, value);
        saveConfig();
		}
	*/
	
    /** 프로그램 완전 종료 */
    private void exitAll() {
        // saveConfig();
        AppLogger.close();
        Platform.exit();
	}
    /** 로그 메시지 추가 (FX 스레드 안팎 모두 안전). */
    public static void log(String message) {
        if (Platform.isFxApplicationThread()) {
            appendLog(message);
			} else {
            Platform.runLater(() -> appendLog(message));
		}
	}
    /** 구분선 추가 */
    public static void logSep() {
        log("─────────────────────────────────────────────────────────────");
	}
    /** 상태바 텍스트 갱신 */
    public static void setStatus(String text) {
        Platform.runLater(() -> setRuntimeStatus(text));
	}
    public Stage getStage() { return theStage; }
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
	        pcShortcut.createMenuWithWindow(theStage),
            buildHelpMenu()
		);
        return bar;
	}
    private Menu buildFileMenu() {
        Menu menu = makeMenu("File", "파일 열기 및 프로그램 제어");
		
		menu.getItems().add(makeRichMenuItem("📁", "SuperDir",
		"디렉터리 재귀 탐색기", null, () -> BackgroundPlayer.SuperDir.open(theStage)));
		
        menu.getItems().add(makeSectionHeader("파일"));
        menu.getItems().add(makeRichMenuItem("📂", "Open",
		"텍스트 파일을 열어 새 창에 표시", "Ctrl+O", this::doOpen));
        menu.getItems().add(makeRichMenuItem("🪟", "Close",
		"이 창을 닫습니다 (시계 유지)", "Ctrl+W", this::doClose));
        menu.getItems().add(makeSectionHeader("설정"));
        menu.getItems().add(makeRichMenuItem("🔤", "Font",
		"UI 전체 폰트 변경", null, this::showFontDialog));
        menu.getItems().add(makeSectionHeader("종료"));
        menu.getItems().add(makeRichMenuItem("🔄", "프로그램 재시작",
		"앱을 저장 후 재시작합니다", null, this::doRestart));
        menu.getItems().add(makeRichMenuItem("⏻", "프로그램 종료",
		"프로그램 종료", "Ctrl+Q", this::doExit));
		
        menu.getItems().add(makeRichMenuItem("⏻", "Restart",
		"시스템을 재부팅하고 프로그램을 재시작합니다.", "", this::doWindowsReboot));
		
        return menu;
	}
    private Menu buildToolsMenu() {
        Menu menu = makeMenu("도구", "알림 및 외부 서비스 기능");
        menu.getItems().add(makeSectionHeader("알림"));
        menu.getItems().add(makeRichMenuItem("🔔", "차임벨 설정",
            "정각 알림 설정", null,
		() -> showChimeDialogPublic(theStage)));
		menu.getItems().add(makeSectionHeader("연동"));
		menu.getItems().add(buildGmailMenu());
		menu.getItems().add(buildKakaoMenuFx());
		menu.getItems().add(buildTelegramMenuFx());
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
        Menu menu = makeMenu("즐겨찾기", "즐겨찾기 프로그램 실행");
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
        java.util.List<Integer> filledSlots = new java.util.ArrayList<>();
        for (int i = 0; i < AppContext.FAVORITE_SLOT_COUNT; i++) {
            String n = AppContext.getFavoriteName(i);
            String p = AppContext.getFavoritePath(i);
            if (!n.isEmpty() && !p.isEmpty()) filledSlots.add(i);
		}
        for (int fi = 0; fi < filledSlots.size(); fi++) {
            int slotIdx  = filledSlots.get(fi);
            String name  = AppContext.getFavoriteName(slotIdx);
            String path  = AppContext.getFavoritePath(slotIdx);
            int prevSlot = fi > 0 ? filledSlots.get(fi - 1) : -1;
            int nextSlot = fi < filledSlots.size() - 1 ? filledSlots.get(fi + 1) : -1;
            menu.getItems().add(buildSlotSubMenu(menu, slotIdx, name, path, prevSlot, nextSlot));
		}
        menu.getItems().add(makeSectionHeader("관리"));
        int nextEmpty = AppContext.nextEmptyFavoriteSlot();
        if (nextEmpty < AppContext.FAVORITE_SLOT_COUNT) {
            menu.getItems().add(makeRichMenuItem("➕", "새 도구 등록",
                "즐겨찾기 추가 (슬롯 " + (nextEmpty + 1) + "/" + AppContext.FAVORITE_SLOT_COUNT + ")", null,
			() -> openSlotEditor(AppContext.nextEmptyFavoriteSlot())));
			} else {
            MenuItem fullItem = makeRichMenuItem("🚫", "슬롯이 가득 찼습니다",
			"최대 " + AppContext.FAVORITE_SLOT_COUNT + "개까지 등록 가능합니다", null, null);
            fullItem.setDisable(true);
            menu.getItems().add(fullItem);
		}
	}
    private int findNextEmptySlot() {
        return AppContext.nextEmptyFavoriteSlot();
	}
    private Menu buildSlotSubMenu(Menu officeMenu, int slotIdx,
		String name, String path, int prevSlot, int nextSlot) {
        Menu slotMenu = makeMenu("📌 " + name, path);
        slotMenu.getItems().add(makeRichMenuItem("▶", "실행",
		path, null, () -> launchByPath(path)));
        slotMenu.getItems().add(new SeparatorMenuItem());
        MenuItem delItem = makeRichMenuItem("🗑️", "삭제", "이 항목을 삭제합니다", null, () -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(theStage);
            confirm.setTitle("즐겨찾기 삭제");
            confirm.setHeaderText(null);
            confirm.setContentText("「" + name + "」 을 삭제하시겠습니까?");
            confirm.setOnShown(ev ->
			((Stage) confirm.getDialogPane().getScene().getWindow()).setAlwaysOnTop(true));
            confirm.showAndWait().ifPresent(btn -> {
                if (btn != ButtonType.OK) return;
                AppContext.removeFavorite(slotIdx);
                Platform.runLater(() -> {
                    populateOfficeMenuItems(officeMenu);
                    officeMenu.show();
				});
			});
		});
        slotMenu.getItems().add(delItem);
        slotMenu.getItems().add(new SeparatorMenuItem());
        MenuItem upItem = makeRichMenuItem("↑", "위로 이동", "목록에서 한 칸 위로", null, () -> {
            swapSlots(slotIdx, prevSlot);
            Platform.runLater(() -> { populateOfficeMenuItems(officeMenu); officeMenu.show(); });
		});
        upItem.setDisable(prevSlot < 0);
        slotMenu.getItems().add(upItem);
        MenuItem downItem = makeRichMenuItem("↓", "아래로 이동", "목록에서 한 칸 아래로", null, () -> {
            swapSlots(slotIdx, nextSlot);
            Platform.runLater(() -> { populateOfficeMenuItems(officeMenu); officeMenu.show(); });
		});
        downItem.setDisable(nextSlot < 0);
        slotMenu.getItems().add(downItem);
        return slotMenu;
	}
	private Menu buildHelpMenu() {
		Menu menu = makeMenu("Help", "로그, 설정, 다운로드, About, 개발자 문의");
		// ── 유지보수 ─────────────────────────────────────────────
		menu.getItems().add(makeSectionHeader("유지보수"));
		menu.getItems().add(makeRichMenuItem("🔄", "프로그램 업그레이드",
		"GitHub 에서 최신 버전을 내려받아 덮어씁니다", null, this::showUpgradeStub));
		// ── 부팅 자동 실행 (CheckMenuItem) ──────────────────────
		CheckMenuItem autoStartItem = new CheckMenuItem("🖥  부팅 자동 실행");
		autoStartItem.setStyle(
			"-fx-font-family:'Malgun Gothic'; -fx-font-size:13px;"
		+ " -fx-text-fill:" + fgColor() + ";");
		autoStartItem.setOnAction(e -> {
			boolean enable = autoStartItem.isSelected();
			new Thread(() -> {
				boolean ok = AppRestarter.AutoStart.set(enable);
				Platform.runLater(() -> {
					if (!ok) {
						autoStartItem.setSelected(!enable);   // 실패 시 원래 상태 복원
						showAlert("자동 실행 " + (enable ? "등록" : "해제") + " 실패.\n"
						+ "관리자 권한 문제일 수 있습니다.", "부팅 자동 실행");
					}
					// 성공 시 별도 알림 없음 — 체크 상태 자체가 피드백
				});
			}, "AutoStartToggle").start();
		});
		menu.getItems().add(autoStartItem);
		// ── onShowing: 상태바 + 자동 실행 등록 여부 동기화 ──────
		// makeMenu() 가 세팅한 onShowing 을 아래에서 재정의 (상태바 문자열 포함)
		menu.setOnShowing(e -> {
			showMenuStatus("로그, 설정, 다운로드, About, 개발자 문의");
			new Thread(() -> {
				boolean checked = AppRestarter.AutoStart.check();
				Platform.runLater(() -> autoStartItem.setSelected(checked));
			}, "AutoStartCheck").start();
		});
		// ── 로그 / 설정 ─────────────────────────────────────────
		menu.getItems().add(makeSectionHeader("로그 / 설정"));
		menu.getItems().add(makeRichMenuItem("📋", "Log조회",
		"현재 로그 파일을 표시합니다", null, MainWindow::doShowLogFile));
		menu.getItems().add(makeRichMenuItem("🗑", "지난Log데이타 삭제",
		"이전 날짜 로그 파일을 삭제합니다", null, this::doDeleteOldLogs));
		menu.getItems().add(makeRichMenuItem("⚙️", "기본 설정 파일",	"설정 파일(ini)을 표시합니다", null, () -> doShowConfigFile(AppContext.CONFIG_FILE)));
		// ── 개발 도구 ──────────────────────────────────────────
		menu.getItems().add(makeSectionHeader("개발 도구"));
		menu.getItems().add(makeRichMenuItem("📝", "Notepad++",
			"Notepad++ 홈페이지 열기", null,
			() -> openBrowser("https://notepad-plus-plus.org/downloads/")));
		// ── 링크 ────────────────────────────────────────────────
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
		// ── 정보 ────────────────────────────────────────────────
		menu.getItems().add(makeSectionHeader("정보"));
		MenuItem aboutItem = makeRichMenuItem("ℹ️", "About",
		"프로그램 정보", "F1", this::doShowAbout);
		aboutItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("F1"));
		menu.getItems().add(aboutItem);
		menu.getItems().add(makeRichMenuItem("📩", "오류 신고 및 개발자에게 문의",
		"개발자에게 이메일로 문의합니다", null, this::doContactDeveloper));
		// ── 화면 ────────────────────────────────────────────────
		menu.getItems().add(makeSectionHeader("화면"));
		menu.getItems().add(makeRichMenuItem(
			themeMode == ThemeMode.PINK_GLASS ? "💙" : "💖",
			themeMode == ThemeMode.PINK_GLASS ? "기본 테마로 되돌리기" : "핑크 글래스로 전환",
		"화면 테마를 전환합니다", null, this::toggleTheme));
		return menu;
	}
	/*
		private Menu buildHelpMenu000() {
        Menu menu = makeMenu("Help", "로그, 설정, 다운로드, About, 개발자 문의");
        menu.getItems().add(makeSectionHeader("유지보수"));
        menu.getItems().add(makeRichMenuItem("🔄", "프로그램 업그레이드",
		"GitHub 에서 최신 버전을 내려받아 덮어씁니다", null, this::showUpgradeStub));
        menu.getItems().add(makeSectionHeader("로그 / 설정"));
        menu.getItems().add(makeRichMenuItem("📋", "Log조회",
		"현재 로그 파일을 표시합니다", null, MainWindow::doShowLogFile));
        menu.getItems().add(makeRichMenuItem("🗑", "지난Log데이타 삭제",
		"이전 날짜 로그 파일을 삭제합니다", null, this::doDeleteOldLogs));
        menu.getItems().add(makeRichMenuItem("⚙️", "기본 설정 파일",
		"설정 파일(ini)을 표시합니다", null, () -> doShowConfigFile(AppContext.CONFIG_FILE)));
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
		"화면 테마를 전환합니다", null, this::toggleTheme));
        return menu;
		}
	*/
    // ═══════════════════════════════════════════════════════════
    //  메뉴 액션
    // ═══════════════════════════════════════════════════════════
    private void doOpen() {
        new Thread(() -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("텍스트 파일 열기");
            fc.setInitialDirectory(new File(System.getProperty("user.home")));
            fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                    "텍스트 파일 (*.txt, *.log, *.ini, *.java ...)",
                    "*.txt","*.log","*.ini","*.java","*.md","*.csv","*.bat",
                    "*.kt","*.scala","*.groovy","*.clj","*.cmd","*.sh","*.gradle",
				"*.properties","*.xml","*.json","*.html","*.htm","*.","*.cpp","*.py"),
                new FileChooser.ExtensionFilter("모든 파일", "*.*")
			);
            Platform.runLater(() -> {
                File file = fc.showOpenDialog(theStage);
                if (file == null || !file.exists()) return;
                openTextFileWindow(file);
			});
		}, "FileChooserInit").start();
	}
    private void doClose() {
        config.remove("mainWindow");
        // saveConfig();
        theStage.hide();
	}
    /** 앱 재시작 */
    private void doRestart() {
		String title = "프로그램 재시작";
		String labelMessage = "설정을 저장하고 프로그램을 재시작하겠습니까 ?";
		String timerlMessage = "자동 취소까지: 15초";
		int second	= 15;
		if (yesNoTimerConfirm(title, labelMessage, timerlMessage , second )) {		
			System.out.println("프로그램 재시작 : doRestart --> appRestarter.restartApp");
			appRestarter.restartApp();
		}
	}
	private void doExit() {
		String title = "프로그램  종료 확인";
		String labelMessage = "프로그램을 종료하시겠습니까 ?";
		String timerlMessage = "자동 취소까지: 15초";
		int second	= 15;
		if (yesNoTimerConfirm(title, labelMessage, timerlMessage , second ))	exitAll();
	}
    private void doWindowsReboot() {
		String title = "시스템 재부팅 확인";
		String labelMessage = "■시스템을 재부팅■하시겠습니까?";
		String timerlMessage = "자동 취소까지: 15초";
		int second	= 15;
		if (yesNoTimerConfirm(title, labelMessage, timerlMessage , second )){
			String title0 = "시스템 재부팅 진행중";
			String labelMessage0 = " 시스템 재부팅 진행중입니다.";
			String timerlMessage0 = "자동 취소까지: 30초";
			int second0	= 30;
			if (!yesNoTimerConfirm(title0, labelMessage0, timerlMessage0 , second0 )){
				try {
					TelegramBot        tg = TelegramBot.getInstance() ;
					tg.init();
					tg.sendShutdownNotice(true);
					new Thread(() -> {
						System.out.println("=======텔레그램 원격 재시작");
						// if (shutdownGuard != null) shutdownGuard.cancel();
						// saveConfig();
						String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
						gmail.sendShutdownNoticeSync(
							"텔레그램 원격 재시작 알림",
							GmailSender.APP_SIGNATURE + "텔레그램 명령으로 PC가 재시작됩니다.\n\n재시작 시각: " + now
						);
						System.out.println("AppLogger.close");
						AppLogger.close();
						try {
							Runtime.getRuntime().exec(new String[]{"shutdown", "-r", "-f", "-t", "0"});
							} catch (Exception e) {
							System.out.println("[Reboot] " + e.getMessage());
						}
					}, "Reboot").start();
					} catch (Exception e) {
					System.out.println("[Reboot & Try] " + e.getMessage());
				}
			};
		};
	}
	public static boolean yesNoTimerConfirm(String title, String labelMessage, String timerlMessage , int second ) {	
        final boolean[] confirmed = {false};
        Stage dlg = new Stage();
        dlg.initOwner(theStage);
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.initStyle(StageStyle.UTILITY);
        dlg.setTitle(title);
        dlg.setAlwaysOnTop(true);
        Label msg = new Label(labelMessage);
        msg.setStyle("-fx-font-family:'Malgun Gothic'; -fx-font-size:13px;");
        Button yes = new Button("Yes");
        Button no  = new Button("No");
        yes.setPrefWidth(72); no.setPrefWidth(72);
        final int[]     sec       = {second};
        Label timerLbl = new Label(timerlMessage);
        timerLbl.setStyle("-fx-text-fill:#888888; -fx-font-size:11px;");
        javafx.animation.Timeline countdown = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ev -> {
                sec[0]--;
                timerLbl.setText("자동 취소까지: " + sec[0] + "초");
                dlg.setTitle("종료 확인 — " + sec[0] + "초 후 취소");
                if (sec[0] <= 0) dlg.close();
			})
		);
        countdown.setCycleCount(second);
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
		return confirmed[0];
	}
	private void showUpgradeStub() {
        Stage dlg = new Stage();
        dlg.initOwner(theStage);
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.initStyle(StageStyle.UTILITY);
        dlg.setTitle("Upgrade 확인");
        dlg.setAlwaysOnTop(true);
        Label msg = new Label("프로그램을 Upgrade 하시겠습니까?");
        msg.setStyle("-fx-font-family:'Malgun Gothic'; -fx-font-size:13px;");
        Button yes = new Button("Yes");
        Button no  = new Button("No");
        yes.setPrefWidth(72); no.setPrefWidth(72);
        final boolean[] confirmed = {false};
        final int[]     sec       = {15};
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
		AppRestarter.doUpgrade();
	}
    public static void doShowLogFile() {
        try {
            String path = AppLogger.getLogFilePath();
            if (path == null || path.trim().isEmpty()) return;
            java.io.File fffLog = new java.io.File(path);
            if (!fffLog.exists()) return;
			openTextFileWindow(fffLog);
			} catch (Exception e) {
            System.err.println("로그 파일 열기 실패: " + e.getMessage());
		}
	}
    private void doDeleteOldLogs() {
        String logPath = AppLogger.getLogFilePath();
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
        confirm.initOwner(theStage);
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
    public static void doShowConfigFile(String path) {
        try {
            if (path == null || path.trim().isEmpty()) return;
            java.io.File CFG = new java.io.File(path);
            if (!CFG.exists()) return;
			openTextFileWindow(CFG);
			} catch (Exception e) {
            System.err.println("파일 열기 실패 [" + path + "] , " + e.getMessage());
		}
	}
    private void doShowAbout() {   showAboutDialog();	}
    private void doContactDeveloper() {
        final String RECEIVER = "garpsu@naver.com";
        Stage dlg = new Stage();
        dlg.initOwner(theStage);
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
        RadioButton rbErr = new RadioButton("오류 신고");  rbErr.setToggleGroup(typeGroup);
        RadioButton rbImp = new RadioButton("개선 요청");  rbImp.setToggleGroup(typeGroup);
        RadioButton rbAdd = new RadioButton("추가 요청");  rbAdd.setToggleGroup(typeGroup);
        HBox typeRow = new HBox(8, rbErr, rbImp, rbAdd);
        grid.add(typeRow, 1, 3);
        grid.add(new Label("내용:"), 0, 4);
        TextArea bodyArea = new TextArea(); bodyArea.setPrefRowCount(7); bodyArea.setWrapText(true);
        grid.add(bodyArea, 1, 4);
        // ── 사용자 첨부 파일 목록 ─────────────────────────────
        java.util.List<java.io.File> userAttachFiles = new java.util.ArrayList<>();
        javafx.collections.ObservableList<String> attachNames =
		javafx.collections.FXCollections.observableArrayList();
        javafx.scene.control.ListView<String> attachList = new javafx.scene.control.ListView<>(attachNames);
        attachList.setPrefHeight(72);
        attachList.setPlaceholder(new Label("(첨부 파일 없음)"));
        Button attachBtn  = new Button("📎 파일 첨부");
        Button attachDelBtn = new Button("삭제");
        attachDelBtn.setDisable(true);
        attachList.getSelectionModel().selectedIndexProperty().addListener(
		(ob, ov, nv) -> attachDelBtn.setDisable(nv.intValue() < 0));
        attachBtn.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("첨부 파일 선택 (여러 개 가능)");
            fc.getExtensionFilters().add(
			new FileChooser.ExtensionFilter("모든 파일", "*.*"));
            java.util.List<java.io.File> chosen = fc.showOpenMultipleDialog(dlg);
            if (chosen != null) {
                for (java.io.File f : chosen) {
                    if (!userAttachFiles.contains(f)) {
                        userAttachFiles.add(f);
                        attachNames.add(f.getName() + "  (" + (f.length() / 1024) + " KB)");
					}
				}
			}
		});
        attachDelBtn.setOnAction(ev -> {
            int idx = attachList.getSelectionModel().getSelectedIndex();
            if (idx >= 0) {
                userAttachFiles.remove(idx);
                attachNames.remove(idx);
			}
		});
        HBox attachBtnRow = new HBox(6, attachBtn, attachDelBtn);
        VBox attachBox = new VBox(4, attachBtnRow, attachList);
        attachBox.setPadding(new Insets(0));
        grid.add(new Label("파일 첨부:"), 0, 5);
        grid.add(attachBox, 1, 5);
        // ── 전송 결과 표시 라벨 ──────────────────────────────────
        Label resultLbl = new Label("");
        resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold;");
        resultLbl.setWrapText(true);
        resultLbl.setMaxWidth(400);
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
            // ── 입력 유효성 검사 (dlg owner로 직접 Alert) ────────
            if (phone.isEmpty() || name.isEmpty() || body.isEmpty()) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.initOwner(dlg); a.setTitle("입력 확인");
                a.setHeaderText(null);
                a.setContentText("전화번호, 성명, 내용은 필수 입력입니다.");
                a.showAndWait(); return;
			}
            if (!phone.matches("010-\\d{3,4}-\\d{4}")) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.initOwner(dlg); a.setTitle("입력 확인");
                a.setHeaderText(null);
                a.setContentText("전화번호 형식이 올바르지 않습니다.\n010-xxxx-yyyy");
                a.showAndWait(); return;
			}
            boolean useDev = !gmail.isConfigured();
            String from = useDev ? GmailSender.devGmailId()   : gmail.from;
            String pass = useDev ? GmailSender.devGmailPass() : gmail.pass;
            if (from.isEmpty() || pass.isEmpty()) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.initOwner(dlg); a.setTitle("오류");
                a.setHeaderText(null);
                a.setContentText("발송 계정을 확인할 수 없습니다.\n[도구 → Gmail 설정]에서 Gmail ID와 비밀번호를 먼저 설정하세요.");
                a.showAndWait(); return;
			}
            String subject  = "[끝판왕 문의] " + (type.isEmpty() ? "" : type + " - ") + name;
            String mailBody = (type.isEmpty() ? "" : "■ 문의 유형 : " + type + "\n")
			+ "■ 성명 : " + name + "\n■ 전화 : " + phone + "\n"
			+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" + body + "\n"
			+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" + GmailSender.APP_SIGNATURE;
            // ── 첨부 파일: 마스터 ini + 로그 + 사용자 선택 ─────
            java.util.List<java.io.File> attachFiles = new java.util.ArrayList<>();
            java.io.File iniFile = new java.io.File(AppContext.CONFIG_FILE);
            if (iniFile.exists()) attachFiles.add(iniFile);
            String logPath = AppLogger.getLogFilePath();
            if (logPath != null && !logPath.isEmpty()) {
                java.io.File logFile = new java.io.File(logPath);
                if (logFile.exists()) attachFiles.add(logFile);
			}
            attachFiles.addAll(userAttachFiles);  // 사용자 추가 파일
            okBtn.setDisable(true);
            okBtn.setText("전송 중...");
            resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#555555;");
            resultLbl.setText("⏳ 발송 중...");
            final String fFrom = from, fPass = pass, fSubject = subject, fBody = mailBody;
            final java.util.List<java.io.File> fAttach = attachFiles;
            new Thread(() -> {
                String err = null;
                try {
					Map<String, String> env = System.getenv();
					String SyetemENV = "";
					for (Map.Entry<String, String> entry : env.entrySet()) {
						SyetemENV = SyetemENV + "\n" + entry.getKey() + " = " + entry.getValue();
					}
					Properties props = System.getProperties();
					String getPropertyValue = "";
					for (String key : props.stringPropertyNames()) {
						getPropertyValue = getPropertyValue + "\n" + key + " = " + props.getProperty(key);
					}
					String mailBodyText = fBody + "\n[SyetemENV]■■■■■\n" + SyetemENV
					+ "\n\n\n[getPropertyValue]■■■■■\n" + getPropertyValue;
                    gmail.sendOneSmtpWithAttachments(fFrom, fPass, RECEIVER, fSubject, mailBodyText, fAttach);
					} catch (Exception ex) {
                    err = ex.getMessage();
				}
                final String fErr = err;
                Platform.runLater(() -> {
                    okBtn.setDisable(false);
                    okBtn.setText("확인");
                    if (fErr == null) {
                        resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#006600;");
                        resultLbl.setText("✅ 전송 완료! → " + RECEIVER);
						} else {
                        resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#cc0000;");
                        resultLbl.setText("❌ 전송 실패: " + fErr);
					}
				});
			}, "ContactDeveloper").start();
		});
        HBox btns = new HBox(8, okBtn, canBtn);
        btns.setAlignment(Pos.CENTER_RIGHT);
        resultLbl.setPadding(new Insets(0, 14, 4, 14));
        VBox root = new VBox(0, grid, resultLbl, btns);
        btns.setPadding(new Insets(0, 14, 10, 14));
        dlg.setScene(new Scene(root));
        dlg.sizeToScene();
        dlg.setResizable(true);
        dlg.showAndWait();
	}
    // ═══════════════════════════════════════════════════════════
    //  텍스트 파일 뷰어 (기존 코드 그대로)
    // ═══════════════════════════════════════════════════════════
    public static void openTextFileWindow(File file) {
        new Thread(() -> {
            try {
                MainWindow.TextFileReader.read(file);
                final String            enc0   = MainWindow.TextFileReader.encLabel;
                // final java.util.ArrayList<String> lines0 = MainWindow.TextFileReader.content;
                Platform.runLater(() -> showTextWindow(file, enc0, MainWindow.TextFileReader.content));
                log("파일 열기: " + file.getName());
				} catch (Exception ex) {
                Platform.runLater(() -> showAlert("파일 읽기 실패:\n" + ex.getMessage(), "Open"));
                log("[ERROR] 파일 열기 실패: " + file.getName() + " — " + ex.getMessage());
			}
		}, "FileOpen").start();
	}
	
	/** 텍스트 내용을 새 창에 표시 */
	/*
		private static void showTextWindowSwing(File file, String encLabel, String content) {
		JFrame sub = new JFrame("📄 " + file.getName());
		sub.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		JTextArea ta = new JTextArea(content);
		ta.setEditable(false);
		ta.setFont(new Font("돋움체", Font.PLAIN, 16));
		ta.setBackground(new Color(235, 245, 255));
		ta.setForeground(new Color( 20,  50,  90));
		ta.setCaretColor(new Color( 20,  50,  90));
		ta.setLineWrap(true);
		ta.setWrapStyleWord(false);
		ta.setMargin(new Insets(6, 8, 6, 8));
		
		// ── 줄번호 패널 (modelToView2D 기반) ─────────────────────────
		final JScrollPane[] spRef = { null };  // 순환 참조 해결용
		JPanel lineNumPanel = new JPanel() {
		@Override
		protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
		RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setFont(ta.getFont());
		g2.setColor(new Color(100, 130, 170));
		
		FontMetrics fm = g2.getFontMetrics();
		int lineCount = ta.getLineCount();
		for (int i = 0; i < lineCount; i++) {
		try {
		int startOffset = ta.getLineStartOffset(i);
		java.awt.geom.Rectangle2D r =
		ta.modelToView2D(startOffset);
		if (r == null) continue;
		int y = (int) r.getY() + fm.getAscent();
		String num = String.format("%4d", i + 1);
		g2.drawString(num, 4, y);
		} catch (Exception ignored) {}
		}
		}
		
		@Override
		public Dimension getPreferredSize() {
		int digits = String.valueOf(ta.getLineCount()).length();
		int w = ta.getFontMetrics(ta.getFont()).charWidth('0') * (digits + 2) + 12;
		int viewH = (spRef[0] != null) ? spRef[0].getViewport().getHeight() : 0;
		int h = Math.max(ta.getPreferredSize().height, viewH);
		return new Dimension(w, h);
		}
		};
		lineNumPanel.setBackground(new Color(210, 225, 240));
		lineNumPanel.setOpaque(true);
		
		JScrollPane sp = new JScrollPane(ta);
		spRef[0] = sp;
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.getViewport().setBackground(new Color(235, 245, 255));
		sp.setRowHeaderView(lineNumPanel);
		
		// ta 크기/내용/스크롤 변경 시 줄번호 패널 갱신
		ta.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
		public void insertUpdate(javax.swing.event.DocumentEvent e)  { lineNumPanel.revalidate(); lineNumPanel.repaint(); }
		public void removeUpdate(javax.swing.event.DocumentEvent e)  { lineNumPanel.revalidate(); lineNumPanel.repaint(); }
		public void changedUpdate(javax.swing.event.DocumentEvent e) { lineNumPanel.revalidate(); lineNumPanel.repaint(); }
		});
		ta.addComponentListener(new ComponentAdapter() {
		@Override public void componentResized(ComponentEvent e) { lineNumPanel.revalidate(); lineNumPanel.repaint(); }
		});
		sp.getViewport().addChangeListener(e -> { lineNumPanel.revalidate(); lineNumPanel.repaint(); });
		
		// 상태바: 인코딩 + 파일 경로 + 크기
		JLabel info = new JLabel(
		" " + encLabel + "  |  " + file.getAbsolutePath() + "  (" + file.length() + " bytes)");
		info.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
		info.setForeground(new Color( 20,  60, 120));
		info.setBackground(new Color(200, 225, 245));
		info.setOpaque(true);
		info.setBorder(BorderFactory.createCompoundBorder(
		BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(140, 180, 210)),
		BorderFactory.createEmptyBorder(2, 4, 2, 4)));
		
		sub.setLayout(new BorderLayout());
		sub.add(sp,   BorderLayout.CENTER);
		sub.add(info, BorderLayout.SOUTH);
		
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int w = Math.max(600, Math.min((int)(screen.width  * 0.70), 1100));
		int h = Math.max(400, Math.min((int)(screen.height * 0.70),  800));
		sub.setSize(w, h);
		
		sub.setLocationRelativeTo(this);
		Point p = sub.getLocation();
		sub.setLocation(p.x + 30, p.y + 30);
		
		sub.setVisible(true);
		}
	*/
	
	private static void showTextWindow(File file, String encLabel, java.util.ArrayList<String> lines) {
        // ── 이미 열린 탭이면 포커스만 이동 ───────────────────────
        String tabKey = file.getAbsolutePath();
        for (Tab t : centerTabs.getTabs()) {
            if (tabKey.equals(t.getUserData())) {
                centerTabs.getSelectionModel().select(t);
                theStage.show(); theStage.toFront();
                return;
			}
		}
        // ── ListView + VirtualFlow: 보이는 셀만 렌더링 ────────────
        // TextArea 는 전체 내용을 DOM 에 올려서 대용량 불가
        // ListView 는 VirtualFlow 로 화면에 보이는 셀만 생성 → 70만 줄도 즉시 로드
        int lineCount = lines.size();
        int digits    = String.valueOf(lineCount).length();
        String fmt    = "%" + digits + "d";

        String numFg  = themeMode == ThemeMode.PINK_GLASS ? "#b06090" : "#6080aa";
        String numBg  = themeMode == ThemeMode.PINK_GLASS
                        ? "rgba(255,210,240,0.45)" : "rgba(210,225,245,0.70)";
        String textBg = themeMode == ThemeMode.PINK_GLASS ? "rgba(255,255,255,0.30)" : "#ebf5ff";
        double numAreaWidth = Math.max(46, digits * 11 + 26);

        javafx.collections.ObservableList<String> items =
            javafx.collections.FXCollections.observableArrayList(lines);
        javafx.scene.control.ListView<String> listView = new javafx.scene.control.ListView<>(items);
        listView.setStyle(
            // "-fx-font-family: '돋움체'; -fx-font-size: 16px;"
            "-fx-font-family: 'Consolas'; -fx-font-size: 16px;"
            + "-fx-background-color: " + textBg + ";"
            + "-fx-border-color: " + borderColor() + ";"
            + "-fx-border-width: 0;");

        final String fFmt    = fmt;
        final double fNumW   = numAreaWidth;
        final String fNumFg  = numFg;
        final String fNumBg  = numBg;
        final String fTextBg = textBg;

        listView.setCellFactory(lv -> new javafx.scene.control.ListCell<String>() {
            private final Label numLabel  = new Label();
            private final Label textLabel = new Label();
            private final HBox  row       = new HBox(numLabel, textLabel);
            {
                // 줄 번호 영역
                numLabel.setMinWidth(fNumW); numLabel.setMaxWidth(fNumW);
                numLabel.setAlignment(Pos.CENTER_RIGHT);
                numLabel.setStyle(
                    // "-fx-font-family:'돋움체'; -fx-font-size:16px;"
                    "-fx-font-family:'Consolas'; -fx-font-size:16px;"
                    + "-fx-text-fill:" + fNumFg + ";"
                    + "-fx-background-color:" + fNumBg + ";"
                    + "-fx-padding:0 8 0 4;"
                    + "-fx-border-color:" + borderColor() + "; -fx-border-width:0 1 0 0;");
                // 본문 영역
                textLabel.setWrapText(true);
                textLabel.setStyle(
                    // "-fx-font-family:'돋움체'; -fx-font-size:16px;"
                    "-fx-font-family:'Consolas'; -fx-font-size:16px;"
                    + "-fx-text-fill:" + fgColor() + ";"
                    + "-fx-background-color:" + fTextBg + ";"
                    + "-fx-padding:2 4 2 6;");
                HBox.setHgrow(textLabel, Priority.ALWAYS);
                textLabel.setMaxWidth(Double.MAX_VALUE);
                row.setFillHeight(true);
                row.setStyle("-fx-background-color:" + fTextBg + ";");
                setPadding(new Insets(0));
                setStyle("-fx-background-color:transparent; -fx-padding:0;");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    numLabel.setText(String.format(fFmt, getIndex() + 1));
                    textLabel.setText(item);
                    setGraphic(row);
                }
            }
        });

        // ── 하단 정보 바 ────────────────────────────────────────
        Label info = new Label(" " + encLabel + "  |  "
            + file.getAbsolutePath() + "  (" + file.length() + " bytes)"
            + "  [" + lineCount + " 줄]");
        info.setStyle(
            "-fx-background-color: " + barBgColor() + "; -fx-text-fill: " + fgColor() + ";"
            + "-fx-font-family:'Malgun Gothic'; -fx-font-size:11px;"
            + "-fx-padding: 2 4 2 4; -fx-border-color:" + borderColor() + "; -fx-border-width:1 0 0 0;");
        info.setMaxWidth(Double.MAX_VALUE);

        // ── [Notepad++] 버튼 ────────────────────────────────────
        Button nppBtn = new Button("Notepad++");
        nppBtn.setStyle(
            "-fx-background-color:#f5c400; -fx-text-fill:#222; -fx-font-weight:bold;"
            + "-fx-font-family:'Malgun Gothic'; -fx-font-size:11px;"
            + "-fx-padding:2 8 2 8; -fx-cursor:hand;");
        nppBtn.setOnAction(ev -> {
            // 시스템 영역에 설치된 Notepad++ 실행
            String[] nppPaths = {
                "C:\\Program Files\\Notepad++\\notepad++.exe",
                "C:\\Program Files (x86)\\Notepad++\\notepad++.exe"
            };
            String nppExe = null;
            for (String p : nppPaths)
                if (new java.io.File(p).exists()) { nppExe = p; break; }
            if (nppExe != null) {
                try {
                    new ProcessBuilder(nppExe, file.getAbsolutePath()).start();
                } catch (Exception ex) {
                    showAlert("Notepad++ 실행 실패:\n" + ex.getMessage(), "Notepad++");
                }
            } else {
                // 미설치 → 안내 메시지 + 홈페이지
                showAlert(
                    "Notepad++이 설치되어 있지 않습니다.\n"
                    + "Notepad++ 설치를 먼저 하세요.\n\n"
                    + "홈페이지: https://notepad-plus-plus.org/downloads/",
                    "Notepad++");
                try {
                    java.awt.Desktop.getDesktop().browse(
                        new java.net.URI("https://notepad-plus-plus.org/downloads/"));
                } catch (Exception ignored) {}
            }
        });
        HBox bottomBar = new HBox(info, nppBtn);
        HBox.setHgrow(info, Priority.ALWAYS);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setStyle("-fx-background-color:" + barBgColor()
            + "; -fx-border-color:" + borderColor() + "; -fx-border-width:1 0 0 0;");

        BorderPane pane = new BorderPane(listView);
        pane.setBottom(bottomBar);
        // ── 새 탭으로 추가 ──────────────────────────────────────
        Tab tab = new Tab("📄 " + file.getName(), pane);
        tab.setUserData(tabKey);
        tab.setClosable(true);
        centerTabs.getTabs().add(tab);
        centerTabs.getSelectionModel().select(tab);
        theStage.show(); theStage.toFront();
	}
    // ═══════════════════════════════════════════════════════════
    //  즐겨찾기 헬퍼 (기존 코드 그대로)
    // ═══════════════════════════════════════════════════════════
    // OFFICE_SLOT_COUNT / slotNameKey / slotPathKey → AppContext.FAVORITE_SLOT_COUNT / getFavoriteName / getFavoritePath 로 이전
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
            showAlert(exeName + " 경로를 찾을 수 없습니다.", "즐겨찾기"); return;
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
            showAlert("실행 실패: " + ex.getMessage(), "즐겨찾기");
		}
	}
    private void openSlotEditor(int idx) {
        String curName = AppContext.getFavoriteName(idx);
        String curPath = AppContext.getFavoritePath(idx);
        Stage dlg = new Stage();
        dlg.initOwner(theStage);
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.initStyle(StageStyle.UTILITY);
        dlg.setTitle("즐겨찾기 슬롯 " + (idx + 1) + " 등록");
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
                showAlert("이름과 경로를 모두 입력하세요.", "즐겨찾기"); return;
			}
            AppContext.setFavorite(idx, n, p);
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
    private void swapSlots(int src, int dst) {
        String sn = AppContext.getFavoriteName(src);
        String sp = AppContext.getFavoritePath(src);
        String dn = AppContext.getFavoriteName(dst);
        String dp = AppContext.getFavoritePath(dst);
        AppContext.setFavorite(src, dn, dp);
        AppContext.setFavorite(dst, sn, sp);
	}
    private void rebuildMenuBar() {
        Platform.runLater(() ->
		((BorderPane) theStage.getScene().getRoot()).setTop(buildMenuBar()));
	}
    // ═══════════════════════════════════════════════════════════
    //  생활도구 헬퍼 (기존 코드 그대로)
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
        confirm.initOwner(theStage);
        confirm.setTitle("만년달력 갱신");
        confirm.setHeaderText(null);
        confirm.setContentText("임시 공휴일 추가 등 만년달력을 자동 갱신합니다.");
        confirm.showAndWait().ifPresent(btn -> {
			if (btn != ButtonType.OK) return;
			updateCalendarHtml2();
		});
	}
	private void updateCalendarHtml2(){
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
	}
	
    private void openBrowser(String url) {
        try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); }
        catch (Exception ex) { showAlert("브라우저 열기 실패: " + ex.getMessage(), "오류"); }
	}
    // ═══════════════════════════════════════════════════════════
    //  로그 내부 구현 (기존 코드 그대로)
    // ═══════════════════════════════════════════════════════════
    private static void appendLog(String message) {
        String ts = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        logArea.appendText("[" + ts + "] " + message + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);
        String short_ = message.length() > 80 ? message.substring(0, 78) + "…" : message;
        setRuntimeStatus(short_);
	}
    // ═══════════════════════════════════════════════════════════
    //  유틸 (기존 코드 그대로)
    // ═══════════════════════════════════════════════════════════
    private void showNotReady() {
        showAlert("시계가 아직 초기화되지 않았습니다.\n잠시 후 다시 시도하세요.", "알림");
	}
    private static void showAlert(String msg, String title) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.initOwner(theStage);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
		});
	}
    private static String defaultStatusText = " 준비";
    private static String lastRuntimeStatus = defaultStatusText;
    private static boolean menuStatusActive = false;
    private static void setRuntimeStatus(String text) {
        lastRuntimeStatus = " " + text;
        if (!menuStatusActive) statusBar.setText(lastRuntimeStatus);
	}
    private static void showMenuStatus(String text) {
        menuStatusActive = true;
        statusBar.setText(" " + text);
	}
    private static void clearMenuStatus() {
        menuStatusActive = false;
        statusBar.setText(lastRuntimeStatus != null ? lastRuntimeStatus : defaultStatusText);
	}
    // ── 폰트 선택 다이얼로그 ────────────────────────────────────
    private void showFontDialog() {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(theStage);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("폰트 선택");
		
        // 시스템 폰트 목록
        java.util.List<String> fonts =
		new java.util.ArrayList<>(javafx.scene.text.Font.getFamilies());
		
        javafx.scene.control.ListView<String> listView =
		new javafx.scene.control.ListView<>(
		javafx.collections.FXCollections.observableArrayList(fonts));
        listView.setPrefHeight(340);
		
        // 현재 선택 폰트 표시
        String curFamily = AppContext.getUiFontFamily();
        if (fonts.contains(curFamily))
		listView.getSelectionModel().select(curFamily);
        listView.scrollTo(listView.getSelectionModel().getSelectedIndex());
		
        // 미리보기
        javafx.scene.control.Label preview = new javafx.scene.control.Label("가나다 ABC 123 미리보기");
        preview.setStyle("-fx-font-size: 14px;");
        listView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) preview.setStyle(
			"-fx-font-family: '" + n + "'; -fx-font-size: 14px;");
		});
		
        // 크기
        javafx.scene.control.Label sizeLbl =
		new javafx.scene.control.Label("크기:");
        javafx.scene.control.Spinner<Integer> sizeSpinner =
		new javafx.scene.control.Spinner<>(8, 24, AppContext.getUiFontSize());
        sizeSpinner.setEditable(true);
        sizeSpinner.setPrefWidth(75);
		
        javafx.scene.layout.HBox sizeBox = new javafx.scene.layout.HBox(8, sizeLbl, sizeSpinner);
        sizeBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        sizeBox.setPadding(new javafx.geometry.Insets(6, 0, 6, 0));
		
        // 버튼
        javafx.scene.control.Button btnOk =
		new javafx.scene.control.Button("확인");
        javafx.scene.control.Button btnCancel =
		new javafx.scene.control.Button("취소");
        btnOk.setDefaultButton(true);
        btnCancel.setCancelButton(true);
		
        btnOk.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                AppContext.setUiFontFamily(sel);
                AppContext.setUiFontSize(sizeSpinner.getValue());
                // 즉시 전체 적용
                AppContext.applyGlobalFont(theStage.getScene());
                logArea.setStyle(logStyle());
                logArea.setFont(javafx.scene.text.Font.font(
				AppContext.getUiFontFamily(), AppContext.getUiFontSize()));
                statusBar.setStyle(statusBarStyle());
                rebuildMenuBar();
			}
            dlg.close();
		});
        btnCancel.setOnAction(e -> dlg.close());
        javafx.scene.layout.HBox btnBox =
		new javafx.scene.layout.HBox(8, btnOk, btnCancel);
        btnBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(8,
            new javafx.scene.control.Label("폰트 선택:"),
		listView, preview, sizeBox, btnBox);
        root.setPadding(new javafx.geometry.Insets(12));
        root.setPrefWidth(320);
        dlg.setScene(new javafx.scene.Scene(root));
        dlg.showAndWait();
	}
    // ── 메인창 보이기/숨기기 토글 (글로벌 마우스 훅에서 호출) ──
    public void toggleWindow() {
        if (theStage == null) return;
        if (theStage.isShowing()) {
            theStage.hide();
            System.out.println("[MainWindow] 메인창 숨김");
			} else {
            theStage.show();
            theStage.toFront();
            System.out.println("[MainWindow] 메인창 표시");
		}
	}
    // ── 테마 토글 ──────────────────────────────────────────────
    private void toggleTheme() {
        themeMode = (themeMode == ThemeMode.PINK_GLASS) ? ThemeMode.BASIC : ThemeMode.PINK_GLASS;
        logArea.setStyle(logStyle());
        ((BorderPane) theStage.getScene().getRoot()).setStyle(rootStyle());
        statusBar.setStyle(statusBarStyle());
        rebuildMenuBar();
	}
    // ── 메뉴 팩토리 (기존 코드 그대로) ──────────────────────────
    private Menu makeMenu(String text) { return makeMenu(text, null); }
    private Menu makeMenu(String text, String helpText) {
        Menu m = new Menu(text);
        m.setStyle("-fx-font-family:'" + AppContext.getUiFontFamily() + "'; -fx-font-size:" + AppContext.getUiFontSize() + "px; -fx-text-fill:" + fgColor() + ";");
        m.setOnShowing(e -> { if (helpText != null && !helpText.isEmpty()) showMenuStatus(helpText); });
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
    private MenuItem makeRichMenuItem(String icon, String text, String helpText,
		String shortcut, Runnable action) {
        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 8, 3, 8));
        row.setStyle("-fx-background-color: " + glassPanelColor()
		+ "; -fx-background-radius: 14; -fx-border-color: transparent; -fx-border-radius:14;");
        if (icon != null && !icon.isEmpty()) {
            Label iconLabel = new Label(icon);
            iconLabel.setStyle("-fx-font-size:14px;");
            row.getChildren().add(iconLabel);
		}
        Label textLabel = new Label(text);
        textLabel.setStyle("-fx-font-family:'Malgun Gothic'; -fx-font-size:13px; -fx-text-fill:" + fgColor() + ";");
        row.getChildren().add(textLabel);
        if (shortcut != null && !shortcut.isEmpty()) {
            javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
            javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            Label scLabel = new Label(shortcut);
            scLabel.setStyle("-fx-font-family:'Malgun Gothic'; -fx-font-size:11px; -fx-text-fill:" + TS_CLR + ";");
            row.getChildren().addAll(spacer, scLabel);
		}
        CustomMenuItem item = new CustomMenuItem(row, true);
        if (helpText != null && !helpText.isEmpty()) {
            Tooltip.install(row, makeSmallTooltip(helpText));
            row.setOnMouseEntered(e -> {
                row.setStyle("-fx-background-color: " + glassHoverColor()
                    + "; -fx-background-radius: 14; -fx-border-color:" + borderColor()
				+ "; -fx-border-radius:14;");
                showMenuStatus(helpText);
			});
            row.setOnMouseExited(e -> {
                row.setStyle("-fx-background-color: " + glassPanelColor()
				+ "; -fx-background-radius: 14; -fx-border-color: transparent; -fx-border-radius:14;");
                clearMenuStatus();
			});
		}
        item.setOnAction(e -> { if (action != null) action.run(); });
        return item;
	}
    private CustomMenuItem makeSectionHeader(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size:11px; -fx-font-weight:bold; -fx-text-fill:"
		+ (themeMode == ThemeMode.PINK_GLASS ? "#c24d8f" : "#5078b4") + ";");
        HBox box = new HBox(label);
        box.setPadding(new Insets(8, 10, 5, 10));
        box.setStyle("-fx-background-color: "
            + (themeMode == ThemeMode.PINK_GLASS ? "rgba(255,255,255,0.22)" : "rgba(255,255,255,0.70)")
		+ "; -fx-background-radius: 10;");
        box.setMouseTransparent(true);
        CustomMenuItem item = new CustomMenuItem(box, false);
        item.setDisable(true);
        return item;
	}
    private Tooltip makeSmallTooltip(String text) {
        Tooltip t = new Tooltip(text);
        t.setStyle("-fx-font-family:'Malgun Gothic'; -fx-font-size:11px;"
            + "-fx-background-color:rgba(255,245,251,0.95); -fx-text-fill:" + fgColor()
		+ "; -fx-background-radius:8; -fx-border-color:" + borderColor() + "; -fx-border-radius:8;");
        return t;
	}
    // ── 색 헬퍼 ───────────────────────────────────────────────
    private String bgColor()         { return themeMode == ThemeMode.PINK_GLASS ? BG : "#ebf5ff"; }
    private static String fgColor()         { return themeMode == ThemeMode.PINK_GLASS ? FG : "#1a3a6b"; }
    private static String barBgColor()      { return themeMode == ThemeMode.PINK_GLASS ? BAR_BG : "rgba(210,228,255,0.78)"; }
    private static String borderColor()     { return themeMode == ThemeMode.PINK_GLASS ? GLASS_BORDER : "rgba(100,140,200,0.40)"; }
    private String glassPanelColor() { return themeMode == ThemeMode.PINK_GLASS ? GLASS_PANEL : "rgba(255,255,255,0.55)"; }
    private String glassHoverColor() { return themeMode == ThemeMode.PINK_GLASS ? GLASS_HOVER  : "rgba(210,228,255,0.65)"; }
    // ── 스타일 빌더 ───────────────────────────────────────────
    private String logStyle() {
        return "-fx-font-family: '" + AppContext.getUiFontFamily() + "'; -fx-font-size: " + AppContext.getUiFontSize() + "px;"
		+ "-fx-text-fill: " + fgColor() + ";"
		+ "-fx-background-color: " + (themeMode == ThemeMode.PINK_GLASS ? "rgba(255,255,255,0.30)" : "#ebf5ff") + ";"
		+ "-fx-control-inner-background: " + (themeMode == ThemeMode.PINK_GLASS ? "rgba(255,255,255,0.30)" : "#ebf5ff") + ";"
		+ "-fx-background-radius: 16;"
		+ "-fx-border-color: " + borderColor() + ";"
		+ "-fx-border-radius: 16;"
		+ "-fx-border-width: 1;";
	}
    private String scrollPaneStyle() {
        return "-fx-background: transparent; -fx-background-color: transparent;"
		+ "-fx-border-color: transparent;";
	}
    private String statusBarStyle() {
        return "-fx-background-color: " + barBgColor() + ";"
		+ "-fx-text-fill: " + fgColor() + ";"
		+ "-fx-font-family: '" + AppContext.getUiFontFamily() + "'; -fx-font-size: " + Math.max(9, AppContext.getUiFontSize() - 2) + "px;"
		+ "-fx-padding: 3 6 3 6;";
	}
    private String rootStyle() {
        return "-fx-background-color: " + bgColor() + ";";
	}
    private String menuBarStyle() {
        return themeMode == ThemeMode.PINK_GLASS ? MENU_BG
		: "-fx-background-color: rgba(210,228,255,0.72);"
		+ "-fx-border-color: rgba(100,140,200,0.45); -fx-border-width: 0 0 1 0;";
	}
    // ═══════════════════════════════════════════════════════════
    //  TextFileReader 내부 유틸 클래스 (기존 코드 그대로)
    // ═══════════════════════════════════════════════════════════
	static class TextFileReader {
		public static String encLabel;
		public static java.util.ArrayList<String> content;
		public static void read(java.io.File file) throws java.io.IOException {
			byte[] all;
			try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
				all = fis.readAllBytes();
			}
			String enc = "CP949";
			encLabel = "[ CP949 ]";
			// ── BOM 감지 및 제거 ──────────────────────────────────
			if (all.length >= 3 && all[0]==(byte)0xEF && all[1]==(byte)0xBB && all[2]==(byte)0xBF) {
				enc = "UTF-8";    encLabel = "[ UTF-8 BOM ]";
				all = java.util.Arrays.copyOfRange(all, 3, all.length);
			} else if (all.length >= 2 && all[0]==(byte)0xFF && all[1]==(byte)0xFE) {
				enc = "UTF-16LE"; encLabel = "[ UTF-16 LE BOM ]";
				all = java.util.Arrays.copyOfRange(all, 2, all.length);
			} else if (all.length >= 2 && all[0]==(byte)0xFE && all[1]==(byte)0xFF) {
				enc = "UTF-16BE"; encLabel = "[ UTF-16 BE BOM ]";
				all = java.util.Arrays.copyOfRange(all, 2, all.length);
			} else if (isValidUTF8(all)) {
				enc = "UTF-8";    encLabel = "[ UTF-8 ]";
			}
			// ── CR/LF 정규화 후 순수 줄 목록으로 분리 ───────────
			String raw = new String(all, enc).replace("\r\n", "\n").replace("\r", "\n");
			content = new java.util.ArrayList<>(java.util.Arrays.asList(raw.split("\n", -1)));
		}
		public static void read(String fullPath) throws java.io.IOException {
			read(new java.io.File(fullPath));
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
	}  //  TextFileReader
}