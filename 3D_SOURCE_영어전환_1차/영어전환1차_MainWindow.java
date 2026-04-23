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
    private static  TabPane  centerTabs;  // File 탭 추가용
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
        // ── 1. Path initialization ──────────────────────────────────────
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
    //  UI initialization
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
        statusBar = new Label("Ready");
        statusBar.setMaxWidth(Double.MAX_VALUE);
        statusBar.setStyle(statusBarStyle());
		
        // ── Favorites 콜백 등록 (buildGeneralPane 호출 전) ────────
        pcShortcut.favoriteCallback = (favName, favPath) -> {
            int slot = AppContext.nextEmptyFavoriteSlot();
            if (slot >= AppContext.FAVORITE_SLOT_COUNT) {
                showAlert("Favorites slot is full (max "
				+ AppContext.FAVORITE_SLOT_COUNT + ").", "Favorites");
                return;
			}
            AppContext.setFavorite(slot, favName, favPath);
            rebuildMenuBar();
            setStatus("Favorite added: " + favName);
		};
        centerTabs = new TabPane();
        centerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        Tab logTab     = new Tab("📋 Log",    scrollPane);
        Tab generalTab = new Tab("📦 General", pcShortcut.buildGeneralPane());
        Tab systemTab  = new Tab("⚙ System", pcShortcut.buildSystemPane());
        logTab.setClosable(false);
        generalTab.setClosable(false);
        systemTab.setClosable(false);
        centerTabs.getTabs().addAll(logTab, generalTab, systemTab);
        centerTabs.getSelectionModel().select(generalTab); // 기본 탭: General Apps
		
        BorderPane root = new BorderPane();
        root.setStyle(rootStyle());
        root.setCenter(centerTabs);
        root.setBottom(statusBar);
        root.setTop(buildMenuBar());
        Scene scene = new Scene(root, 860, 520);
        AppContext.applyGlobalFont(scene);  // 전역 Font Apply
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest((WindowEvent e) -> {
            e.consume();
            doClose();
		});
        primaryStage.show();
        // ── AppRestarter FX 컨Text 주입 ───────────────────────
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
    /** config Save */
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
		//   로그 File 열기
		private void openLogFile() {
        try {
		String path = AppLogger.getLogFilePath();
		if (path == null || path.trim().isEmpty()) return;
		java.io.File f = new java.io.File(path);
		if (!f.exists()) return;
		if (java.awt.Desktop.isDesktopSupported())
		java.awt.Desktop.getDesktop().open(f);
		} catch (Exception e) {
		System.err.println("로그 File 열기 Failed: " + e.getMessage());
		}
		}
	*/
    /** Settings File 열기 */
	/*
		private void openConfigFile() {
		boolean ok = AppContext.openCONFIG_FILE();
		if (!ok) {
		showAlert("Failed to open settings file.\n" + AppContext.CONFIG_FILE, "Default Settings File");
		}
		}
	*/
    /** ChimeController — 마스터 ini(AppContext)에서 Settings 복원 */
    private void initChimeControllerIfNeeded(Stage owner) {
        if (chimeController != null) return;
        chimeController = new ChimeController(owner, new ChimeController.HostCallback() {
            @Override public boolean isChild() { return false; }
            @Override public java.time.ZoneId getTimeZone() { return java.time.ZoneId.systemDefault(); }
            @Override public void startRainbow(int durationSec) { /* 시계 미Integrations h 무h */ }
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
    /** 팝업 메뉴(KootPanKingThreeApp)에서 호출 — 마스터 Chime 다이얼로그 Show 후 AppContext에 Save */
    public void showChimeDialogPublic(Stage owner) {
        if (owner == null) owner = theStage;
        initChimeControllerIfNeeded(owner);
        if (chimeController == null) return;
        chimeController.showChimeDialog();
        // 다이얼로그 닫힌 후 마스터 ini Save
        saveChimeToAppContext();
	}
    /** Chime Settings을 마스터 ini(AppContext)에 Save */
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
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("🌍 World Clock");
        // {메뉴 표시명, ZoneId, 시계 날짜 prefix}
        String[][] cities = {
            {"🇰🇷 Seoul",    "Asia/Seoul",          "Seoul"},
            {"🇯🇵 Tokyo",    "Asia/Tokyo",          "Tokyo"},
            {"🇨🇳 Beijing",  "Asia/Shanghai",       "Beijing"},
            {"🇹🇭 Bangkok",    "Asia/Bangkok",        "Bangkok"},
            {"🇮🇳 Mumbai",  "Asia/Kolkata",        "Mumbai"},
            {"🇦🇪 Dubai",  "Asia/Dubai",          "Dubai"},
            {"🇷🇺 Moscow","Europe/Moscow",        "Moscow"},
            {"🇫🇷 Paris",    "Europe/Paris",        "Paris"},
            {"🇩🇪 Berlin",  "Europe/Berlin",       "Berlin"},
            {"🇬🇧 London",    "Europe/London",       "London"},
            {"🇺🇸 New York",    "America/New_York",    "New York"},
            {"🇺🇸 Chicago",  "America/Chicago",     "Chicago"},
            {"🇺🇸 Denver",  "America/Denver",     "Denver"},
            {"🇺🇸 Detroit",  "America/Detroit",     "Detroit"},
			{"🇺🇸 LA",      "America/Los_Angeles", "L.A."},
            {"🇺🇸 Alaska",      "America/Anchorage", "Alaska"},
			{"🇺🇸 Hawaii",      "Pacific/Honolulu", "Hawaii"},
            {"🇧🇷 São Paulo","America/Sao_Paulo",   "São Paulo"},
            {"🇦🇺 Sydney",  "Australia/Sydney",    "Sydney"},
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
        // "지금 Gmail 보내기" → "Gmail Settings" 으로 명칭 변경 + 다이얼로그 연결
        javafx.scene.control.MenuItem gmailSettings = new javafx.scene.control.MenuItem("Gmail Settings");
        gmailSettings.setOnAction(e -> showGmailSettingsDialog(theStage));
        javafx.scene.control.MenuItem guide = new javafx.scene.control.MenuItem("Calendar Setup Guide");
        guide.setOnAction(e -> {
            try { java.awt.Desktop.getDesktop().browse(
			new java.net.URI("https://support.google.com/calendar/answer/99358"));
            } catch (Exception ex) { showAlert("Failed to open browser: " + ex.getMessage(), "Info"); }
		});
        javafx.scene.control.Menu googleCal = new javafx.scene.control.Menu("📧 Google Calendar");
        googleCal.getItems().addAll(
            calMenuAction("Next 3 days", () -> showCalendarResult("Google","google",3,"next")),
            calMenuAction("Next 7 days", () -> showCalendarResult("Google","google",7,"next")),
            calMenuAction("Past 7 days", () -> showCalendarResult("Google","google",7,"past")),
            calMenuAction("This month",  () -> showCalendarResult("Google","google",0,"month")),
            calMenuAction("Next month",  () -> showCalendarResult("Google","google",0,"nextmonth"))
		);
        javafx.scene.control.Menu naverCal = new javafx.scene.control.Menu("🟢 Naver Calendar");
        naverCal.getItems().addAll(
            calMenuAction("Next 3 days", () -> showCalendarResult("Naver","naver",3,"next")),
            calMenuAction("Next 7 days", () -> showCalendarResult("Naver","naver",7,"next")),
            calMenuAction("Past 7 days", () -> showCalendarResult("Naver","naver",7,"past")),
            calMenuAction("This month",  () -> showCalendarResult("Naver","naver",0,"month")),
            calMenuAction("Next month",  () -> showCalendarResult("Naver","naver",0,"nextmonth"))
		);
        // 네이버 Settings 서브메뉴
        javafx.scene.control.Menu naverMenu = new javafx.scene.control.Menu("🟢 Naver Settings");
        javafx.scene.control.MenuItem naverGuide  = new javafx.scene.control.MenuItem("Naver Settings Guide");
        javafx.scene.control.MenuItem naverPasswd = new javafx.scene.control.MenuItem("Password Settings");
        naverGuide.setOnAction(e -> showAlert(
            "Enable Naver CalDAV service,\nenter your credentials in [Password Settings] below.\n\n" +
            "Enable CalDAV: Naver Calendar → Settings → CalDAV",
		"Naver Settings Guide"));
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
                    case "next":      events = googleCalendarService.getNextDays(days);  title = "📧 Google next " + days + "Sun"; break;
                    case "past":      events = googleCalendarService.getPastDays(days);   title = "📧 Google past " + days + "Sun"; break;
                    case "month":     events = googleCalendarService.getThisMonth();      title = "📧 Google This month"; break;
                    case "nextmonth": events = googleCalendarService.getNextMonth();      title = "📧 Google Next month"; break;
                    default:          events = java.util.Collections.emptyList();         title = "📧 Google Calendar";
				}
                content = GoogleCalendarService.formatEvents(title, events);
				} else {
                if (!naverCalendarService.isInitialized()) naverCalendarService.init();
                java.util.List<NaverCalendarService.CalendarEvent> events;
                switch (range) {
                    case "next":      events = naverCalendarService.getNextDays(days);   title = "🟢 Naver next " + days + "Sun"; break;
                    case "past":      events = naverCalendarService.getPastDays(days);    title = "🟢 Naver past " + days + "Sun"; break;
                    case "month":     events = naverCalendarService.getThisMonth();       title = "🟢 Naver This month"; break;
                    case "nextmonth": events = naverCalendarService.getNextMonth();       title = "🟢 Naver Next month"; break;
                    default:          events = java.util.Collections.emptyList();         title = "🟢 Naver Calendar";
				}
                content = NaverCalendarService.formatEvents(title, events);
			}
            final String dlgTitle   = title;
            final String dlgContent = content;
            Platform.runLater(() -> showScheduleDialog(dlgTitle, dlgContent));
			} catch (Exception e) {
            final String err = e.getMessage();
            Platform.runLater(() -> showScheduleDialog("Calendar Error", "Schedule query failed:\n" + err));
		}
	}
    // ── 카카오톡 서브메뉴 ─────────────────────────────────────────
    private javafx.scene.control.Menu buildKakaoMenuFx() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("KakaoTalk...");
        javafx.scene.control.MenuItem loginItem = new javafx.scene.control.MenuItem("KakaoTalk Logged in");
        javafx.scene.control.MenuItem sendItem  = new javafx.scene.control.MenuItem("Send message to myself...");
        javafx.scene.control.MenuItem guideItem = new javafx.scene.control.MenuItem("Setup Guide...");
        guideItem.setOnAction(e -> showAlert(
            "Enter KakaoTalk REST API Key / Client Secret / Refresh Token\n" +
            "in clock_settings.ini.\n\n" +
		"  kakao.apiKey=...\n  kakao.clientSecret=...\n  kakao.refreshToken=...", "KakaoTalk Settings"));
        menu.getItems().addAll(loginItem, sendItem, guideItem);
        return menu;
	}
    // ── 텔레그램 서브메뉴 ─────────────────────────────────────────
    private javafx.scene.control.Menu buildTelegramMenuFx() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("Telegram");
        javafx.scene.control.MenuItem tokenSettings = new javafx.scene.control.MenuItem("Telegram Password Settings");
        javafx.scene.control.MenuItem help          = new javafx.scene.control.MenuItem("Telegram Setup Guide");
        tokenSettings.setOnAction(e -> showTelegramSettingsDialog(theStage));
        help.setOnAction(e -> {
            if (tg != null) tg.showTelegramHelp(theStage);
		});
        menu.getItems().addAll(tokenSettings, help);
        return menu;
	}
    // ── Gmail Settings 다이얼로그 (ID + Password) ───────────────────
    public void showGmailSettingsDialog(javafx.stage.Stage owner) {
        if (owner == null) owner = theStage;
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(owner);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("Gmail Settings");
        // ── 레이블 ───────────────────────────────────────────────
        javafx.scene.control.Label idLbl   = new javafx.scene.control.Label("Sender Gmail ID:");
        javafx.scene.control.Label passLbl = new javafx.scene.control.Label("Sender (App) Password:");
        javafx.scene.control.Label toLbl   = new javafx.scene.control.Label("Recipient Email ID:");
        // ── Sender ID ────────────────────────────────────────────
        javafx.scene.control.TextField idField = new javafx.scene.control.TextField(AppContext.getGmailFrom());
        idField.setPrefWidth(280);
        idField.setPromptText("example@gmail.com");
        // ── Sender Password (숨김 + 표h 토글) ─────────────────
        javafx.scene.control.PasswordField passField = new javafx.scene.control.PasswordField();
        passField.setText(AppContext.getGmailPass());
        passField.setPrefWidth(280);
        passField.setPromptText("Google App Password (16 chars)");
        javafx.scene.control.CheckBox showPass = new javafx.scene.control.CheckBox("Show");
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
        // ── Recipient Email ID ─────────────────────────────────────
        javafx.scene.control.TextField toField = new javafx.scene.control.TextField(
		AppContext.get("gmail.lastTo", ""));
        toField.setPrefWidth(280);
        toField.setPromptText("Recipient@example.com");
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
		"※ Generate at: Google Account → Security → App Passwords.");
        hint.setStyle("-fx-font-size:11px; -fx-text-fill:#777;");
        hint.setWrapText(true);
        hint.setPadding(new javafx.geometry.Insets(0, 16, 4, 16));
        // ── 테스트 결과 레이블 ───────────────────────────────────
        javafx.scene.control.Label resultLbl = new javafx.scene.control.Label("");
        resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold;");
        resultLbl.setWrapText(true);
        resultLbl.setMaxWidth(380);
        resultLbl.setPadding(new javafx.geometry.Insets(0, 16, 4, 16));
        // ── 버튼: Save / 테스트 Sending / 취소 ────────────────────
        javafx.scene.control.Button okBtn   = new javafx.scene.control.Button("Save");
        javafx.scene.control.Button testBtn = new javafx.scene.control.Button("Send Test Email");
        javafx.scene.control.Button canBtn  = new javafx.scene.control.Button("Cancel");
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
            showAlert("Gmail Settings saved.", "Gmail Settings");
		});
        testBtn.setOnAction(ev -> {
            String id   = idField.getText().trim();
            String pass = getCurrentPass.get();
            String to   = toField.getText().trim();
            if (id.isEmpty() || pass.isEmpty() || to.isEmpty()) {
                resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#cc0000;");
                resultLbl.setText("⚠ Please fill in Sender ID, Password, and Recipient.");
                return;
			}
            testBtn.setDisable(true);
            testBtn.setText("Sending...");
            resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#555555;");
            resultLbl.setText("⏳ Sending test email...");
            final String fId = id, fPass = pass, fTo = to;
            // ── 첨부 File 수집: 마스터 ini + 로그 File ──────────
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
                    testBtn.setText("Send Test Email");
                    boolean ok = result == null || result.isEmpty();
                    resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:"
					+ (ok ? "#006600;" : "#cc0000;"));
                    resultLbl.setText(ok ? "✅ Test email sent!" : "❌ " + result);
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
    // ── 네이버 Settings 다이얼로그 (ID + Password) ─────────────────
    public void showNaverSettingsDialog(javafx.stage.Stage owner) {
        if (owner == null) owner = theStage;
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(owner);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("Naver CalDAV Settings");
        javafx.scene.control.Label idLbl   = new javafx.scene.control.Label("Naver ID:");
        javafx.scene.control.Label passLbl = new javafx.scene.control.Label("Password:");
        javafx.scene.control.TextField idField = new javafx.scene.control.TextField(AppContext.getNaverId());
        idField.setPrefWidth(260);
        idField.setPromptText("Naver ID");
        javafx.scene.control.PasswordField passField = new javafx.scene.control.PasswordField();
        passField.setText(AppContext.getNaverPassword());
        passField.setPrefWidth(260);
        passField.setPromptText("Naver Password");
        javafx.scene.control.CheckBox showPass = new javafx.scene.control.CheckBox("Show");
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
		"※ Enable CalDAV first: Naver Calendar → Settings → CalDAV.");
        hint.setStyle("-fx-font-size:11px; -fx-text-fill:#777;");
        hint.setWrapText(true);
        javafx.scene.control.Button okBtn  = new javafx.scene.control.Button("Save");
        javafx.scene.control.Button canBtn = new javafx.scene.control.Button("Cancel");
        okBtn.setDefaultButton(true); canBtn.setCancelButton(true);
        okBtn.setPrefWidth(72); canBtn.setPrefWidth(72);
        canBtn.setOnAction(ev -> dlg.close());
        okBtn.setOnAction(ev -> {
            String id   = idField.getText().trim();
            String pass = showPass.isSelected() ? passVisible.getText().trim()
			: passField.getText().trim();
            AppContext.setNaverId(id);
            AppContext.setNaverPassword(pass);
            // NaverCalendarService에 즉h 반영
            if (naverCalendarService != null) naverCalendarService.setCredentials(id, pass);
            dlg.close();
            showAlert("Naver Settings saved.", "Naver Settings");
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
    // ── 텔레그램 Settings 다이얼로그 (botToken + myChatId) ─────────
    public void showTelegramSettingsDialog(javafx.stage.Stage owner) {
        if (owner == null) owner = theStage;
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(owner);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("Telegram Settings");
        javafx.scene.control.Label tokenLbl  = new javafx.scene.control.Label("Bot Token:");
        javafx.scene.control.Label chatIdLbl = new javafx.scene.control.Label("My Chat ID:");
        javafx.scene.control.TextField tokenField = new javafx.scene.control.TextField(AppContext.getTelegramBotToken());
        tokenField.setPrefWidth(320);
        tokenField.setPromptText("123456789:ABCdef...");
        javafx.scene.control.TextField chatIdField = new javafx.scene.control.TextField(AppContext.getTelegramMyChatId());
        chatIdField.setPrefWidth(320);
        chatIdField.setPromptText("Numbers Chat ID");
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8); grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(16));
        grid.add(tokenLbl,  0, 0); grid.add(tokenField,  1, 0);
        grid.add(chatIdLbl, 0, 1); grid.add(chatIdField, 1, 1);
        javafx.scene.control.Label hint = new javafx.scene.control.Label(
            "※ Create a bot with BotFather, then enter the Token.\n" +
		"   Chat ID can be found via @userinfobot.");
        hint.setStyle("-fx-font-size:11px; -fx-text-fill:#777;");
        hint.setWrapText(true);
        javafx.scene.control.Button okBtn  = new javafx.scene.control.Button("Save");
        javafx.scene.control.Button canBtn = new javafx.scene.control.Button("Cancel");
        okBtn.setDefaultButton(true); canBtn.setCancelButton(true);
        okBtn.setPrefWidth(72); canBtn.setPrefWidth(72);
        canBtn.setOnAction(ev -> dlg.close());
        okBtn.setOnAction(ev -> {
            String token  = tokenField.getText().trim();
            String chatId = chatIdField.getText().trim();
            AppContext.setTelegramBotToken(token);
            AppContext.setTelegramMyChatId(chatId);
            // TelegramBot 인스턴스에 즉h 반영
            // if (tg != null) { tg.botToken = token; tg.myChatId = chatId; }
            dlg.close();
            showAlert("Telegram Settings saved.", "Telegram Settings");
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
        dlg.setTitle(APP_NAME_title + "  —  " + sec[0] + "s auto close");
        javafx.scene.text.TextFlow tf = new javafx.scene.text.TextFlow();
        tf.setPadding(new javafx.geometry.Insets(12, 16, 8, 16));
        tf.setPrefWidth(460);
        String[][] items = {
            {"• Marble-textured analog clock",                                        "#2aa198"},
            {"• Fully customizable clock design",                                            "#268bd2"},
            {"• World city clocks",                                              "#6c71c4"},
            {"• Telegram, GMail, Naver, KakaoTalk, Smart Camera, Live CCTV...", "#b58900"},
            {"• KIM GAP-SU , 2026-3-18 , Seoul, Republic of Korea",                              "#dc322f"}
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
		"→ Details: " + blogUrl);
        link.setStyle("-fx-font-family: 'Malgun Gothic'; -fx-font-size: 12px;");
        link.setOnAction(ev -> {
            try { java.awt.Desktop.getDesktop().browse(new java.net.URI(blogUrl)); }
            catch (Exception ex) { System.out.println("[About] Links 열기 failed: " + ex.getMessage()); }
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
                dlg.setTitle(APP_NAME_title + "  —  " + sec[0] + "s auto close");
                if (sec[0] <= 0) doClose.run();
			})
		);
        tl.setCycleCount(48);
        holder[0] = tl;
        dlg.setOnHidden(ev -> { if (holder[0] != null) holder[0].stop(); });
        dlg.show();
        tl.play();
	}
    /** schedule 다이얼로그 — 300s 카운트다운 후 자동 닫힘 */
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
        javafx.scene.control.Label countdown = new javafx.scene.control.Label("Auto close: 300s");
        countdown.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        javafx.scene.control.Button closeBtn = new javafx.scene.control.Button("Close");
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
                countdown.setText("Auto close: " + remain[0] + "s");
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
    /** Settings값 조회 */
    private String getConfigValue(String key, String defaultValue) {
        return config.getProperty(key, defaultValue);
	}
    /** 여러 Settings값 Save */
	/*
		private void setMultipleConfigAndSave(String... entries) {
        for (int i = 0; i + 1 < entries.length; i += 2)
		config.setProperty(entries[i], entries[i + 1]);
        saveConfig();
		}
	*/
    /** 단Sun Settings값 Save */
	/*
		private void setConfigAndSave(String key, String value) {
        config.setProperty(key, value);
        saveConfig();
		}
	*/
	
    /** 프로그램 완전 Shutdown */
    private void exitAll() {
        // saveConfig();
        AppLogger.close();
        Platform.exit();
	}
    /** 로그 Message 추가 (FX 스레드 안팎 모두 안전). */
    public static void log(String message) {
        if (Platform.isFxApplicationThread()) {
            appendLog(message);
			} else {
            Platform.runLater(() -> appendLog(message));
		}
	}
    /** 구m선 추가 */
    public static void logSep() {
        log("─────────────────────────────────────────────────────────────");
	}
    /** 상태바 Text 갱신 */
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
        Menu menu = makeMenu("File", "Open Files & App Control");
		
		menu.getItems().add(makeRichMenuItem("📁", "SuperDir",
		"Recursive Directory Explorer", null, () -> BackgroundPlayer.SuperDir.open(theStage)));
		
        menu.getItems().add(makeSectionHeader("File"));
        menu.getItems().add(makeRichMenuItem("📂", "Open",
		"Open text file in new window", "Ctrl+O", this::doOpen));
        menu.getItems().add(makeRichMenuItem("🪟", "Close",
		"Close this window (clock stays)", "Ctrl+W", this::doClose));
        menu.getItems().add(makeSectionHeader("Settings"));
        menu.getItems().add(makeRichMenuItem("🔤", "Font",
		"Change All UI Fonts", null, this::showFontDialog));
        menu.getItems().add(makeSectionHeader("Shutdown"));
        menu.getItems().add(makeRichMenuItem("🔄", "Restart Program",
		"Save and restart app", null, this::doRestart));
        menu.getItems().add(makeRichMenuItem("⏻", "Exit Program",
		"Exit Program", "Ctrl+Q", this::doExit));
		
        menu.getItems().add(makeRichMenuItem("⏻", "Restart",
		"Reboot system and restart program.", "", this::doWindowsReboot));
		
        return menu;
	}
    private Menu buildToolsMenu() {
        Menu menu = makeMenu("Tools", "Notifications and external services");
        menu.getItems().add(makeSectionHeader("Notice"));
        menu.getItems().add(makeRichMenuItem("🔔", "Chime Settings",
            "Chime Settings", null,
		() -> showChimeDialogPublic(theStage)));
		menu.getItems().add(makeSectionHeader("Integrations"));
		menu.getItems().add(buildGmailMenu());
		menu.getItems().add(buildKakaoMenuFx());
		menu.getItems().add(buildTelegramMenuFx());
		return menu;
	}
    private Menu buildLifeMenu() {
        Menu menu = makeMenu("Utilities", "Time/Weather/Astronomy");
        menu.getItems().add(makeSectionHeader("External Services"));
        menu.getItems().add(makeRichMenuItem("🌏", "Astronomy Guide", null, null,
		() -> openBrowser("https://astro.kasi.re.kr/index")));
        menu.getItems().add(makeRichMenuItem("🕐", "TIME.IS", null, null,
		() -> openBrowser("https://time.is")));
        menu.getItems().add(makeRichMenuItem("🕰", "TIME&DATE", null, null,
		() -> openBrowser("https://www.timeanddate.com")));
        menu.getItems().add(makeRichMenuItem("🌤", "Weather", null, null,
		() -> openBrowser("https://www.weather.go.kr")));
        menu.getItems().add(makeSectionHeader("Tools"));
        menu.getItems().add(makeRichMenuItem("📅", "Calendar",
		"Open in Browser", null, this::openCalendarHtml));
        menu.getItems().add(makeRichMenuItem("🔄", "Update Calendar",
		"Download latest from GitHub", null, this::updateCalendarHtml));
        return menu;
	}
    private Menu buildOfficeMenu() {
        Menu menu = makeMenu("Favorites", "Run Favorite Programs");
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
        menu.getItems().add(makeSectionHeader("Registered Tools"));
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
        menu.getItems().add(makeSectionHeader("Manage"));
        int nextEmpty = AppContext.nextEmptyFavoriteSlot();
        if (nextEmpty < AppContext.FAVORITE_SLOT_COUNT) {
            menu.getItems().add(makeRichMenuItem("➕", "Register New Tool",
                "Add Favorite (Slot " + (nextEmpty + 1) + "/" + AppContext.FAVORITE_SLOT_COUNT + ")", null,
			() -> openSlotEditor(AppContext.nextEmptyFavoriteSlot())));
			} else {
            MenuItem fullItem = makeRichMenuItem("🚫", "Slots are full",
			"Max " + AppContext.FAVORITE_SLOT_COUNT + " slots", null, null);
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
        slotMenu.getItems().add(makeRichMenuItem("▶", "Run",
		path, null, () -> launchByPath(path)));
        slotMenu.getItems().add(new SeparatorMenuItem());
        MenuItem delItem = makeRichMenuItem("🗑️", "Delete", "Delete this item", null, () -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(theStage);
            confirm.setTitle("Favorites Delete");
            confirm.setHeaderText(null);
            confirm.setContentText("\"" + name + "\" - Delete?");
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
        MenuItem upItem = makeRichMenuItem("↑", "Move Up", "Move up one position", null, () -> {
            swapSlots(slotIdx, prevSlot);
            Platform.runLater(() -> { populateOfficeMenuItems(officeMenu); officeMenu.show(); });
		});
        upItem.setDisable(prevSlot < 0);
        slotMenu.getItems().add(upItem);
        MenuItem downItem = makeRichMenuItem("↓", "Move Down", "Move down one position", null, () -> {
            swapSlots(slotIdx, nextSlot);
            Platform.runLater(() -> { populateOfficeMenuItems(officeMenu); officeMenu.show(); });
		});
        downItem.setDisable(nextSlot < 0);
        slotMenu.getItems().add(downItem);
        return slotMenu;
	}
	private Menu buildHelpMenu() {
		Menu menu = makeMenu("Help", "Log, Settings, Download, About, Contact");
		// ── 유지보수 ─────────────────────────────────────────────
		menu.getItems().add(makeSectionHeader("Maintenance"));
		menu.getItems().add(makeRichMenuItem("🔄", "Program Upgrade",
		"Download and overwrite with latest from GitHub", null, this::showUpgradeStub));
		// ── 부팅 자동 실행 (CheckMenuItem) ──────────────────────
		CheckMenuItem autoStartItem = new CheckMenuItem("🖥  Auto-start on Boot");
		autoStartItem.setStyle(
			"-fx-font-family:'Malgun Gothic'; -fx-font-size:13px;"
		+ " -fx-text-fill:" + fgColor() + ";");
		autoStartItem.setOnAction(e -> {
			boolean enable = autoStartItem.isSelected();
			new Thread(() -> {
				boolean ok = AppRestarter.AutoStart.set(enable);
				Platform.runLater(() -> {
					if (!ok) {
						autoStartItem.setSelected(!enable);   // Failed h 원래 상태 복원
						showAlert("Auto-start " + (enable ? "register" : "remove") + " Failed.\n"
						+ "Admin privileges may be required.", "Auto-start on Boot");
					}
					// 성공 h 별도 Notice 없음 — 체크 상태 자체가 피드백
				});
			}, "AutoStartToggle").start();
		});
		menu.getItems().add(autoStartItem);
		// ── onShowing: 상태바 + 자동 실행 등록 여부 동기화 ──────
		// makeMenu() 가 세팅한 onShowing 을 아래에서 재정의 (상태바 문자열 포함)
		menu.setOnShowing(e -> {
			showMenuStatus("Log, Settings, Download, About, Contact");
			new Thread(() -> {
				boolean checked = AppRestarter.AutoStart.check();
				Platform.runLater(() -> autoStartItem.setSelected(checked));
			}, "AutoStartCheck").start();
		});
		// ── Log / Settings ─────────────────────────────────────────
		menu.getItems().add(makeSectionHeader("Log / Settings"));
		menu.getItems().add(makeRichMenuItem("📋", "View Log",
		"Show current log file", null, MainWindow::doShowLogFile));
		menu.getItems().add(makeRichMenuItem("🗑", "Delete Old Logs",
		"Delete old log files", null, this::doDeleteOldLogs));
		menu.getItems().add(makeRichMenuItem("⚙️", "Default Settings File",	"Show settings file (ini)", null, () -> doShowConfigFile(AppContext.CONFIG_FILE)));
		// ── 개Dev Tools ──────────────────────────────────────────
		menu.getItems().add(makeSectionHeader("Dev Tools"));
		menu.getItems().add(makeRichMenuItem("📝", "Notepad++",
			"Open Notepad++ website", null,
			() -> openBrowser("https://notepad-plus-plus.org/downloads/")));
		// ── Links ────────────────────────────────────────────────
		menu.getItems().add(makeSectionHeader("Links"));
		menu.getItems().add(makeRichMenuItem("👨‍💻", "Developer Blog",
			"KIM GAP-SU / Seoul, Republic of Korea", null,
		() -> openBrowser("https://github.com/GarpsuKim")));
		menu.getItems().add(makeRichMenuItem("⬇", "Installer",
			"Download KootPanKing installer", null,
		() -> openBrowser("https://github.com/GarpsuKim/KootPanKingThree/releases/tag/KootPanKingThree")));
		menu.getItems().add(makeRichMenuItem("🧩", "Source Code",
			"Java source code", null,
		() -> openBrowser("https://github.com/GarpsuKim/KootPanKingThree")));
		menu.getItems().add(makeRichMenuItem("☕", "Java/JVM",
			"Download Java runtime installer", null,
		() -> openBrowser("https://www.oracle.com/java")));
		// ── Info ────────────────────────────────────────────────
		menu.getItems().add(makeSectionHeader("Info"));
		MenuItem aboutItem = makeRichMenuItem("ℹ️", "About",
		"Program Info", "F1", this::doShowAbout);
		aboutItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("F1"));
		menu.getItems().add(aboutItem);
		menu.getItems().add(makeRichMenuItem("📩", "Error Report & Contact",
		"Contact developer via email", null, this::doContactDeveloper));
		// ── 화면 ────────────────────────────────────────────────
		menu.getItems().add(makeSectionHeader("Theme"));
		menu.getItems().add(makeRichMenuItem(
			themeMode == ThemeMode.PINK_GLASS ? "💙" : "💖",
			themeMode == ThemeMode.PINK_GLASS ? "Switch to Default Theme" : "Switch to Pink Glass Theme",
		"Switch UI theme", null, this::toggleTheme));
		return menu;
	}
	/*
		private Menu buildHelpMenu000() {
        Menu menu = makeMenu("Help", "Log, Settings, Download, About, Contact");
        menu.getItems().add(makeSectionHeader("Maintenance"));
        menu.getItems().add(makeRichMenuItem("🔄", "Program Upgrade",
		"Download and overwrite with latest from GitHub", null, this::showUpgradeStub));
        menu.getItems().add(makeSectionHeader("Log / Settings"));
        menu.getItems().add(makeRichMenuItem("📋", "View Log",
		"Show current log file", null, MainWindow::doShowLogFile));
        menu.getItems().add(makeRichMenuItem("🗑", "Delete Old Logs",
		"Delete old log files", null, this::doDeleteOldLogs));
        menu.getItems().add(makeRichMenuItem("⚙️", "Default Settings File",
		"Show settings file (ini)", null, () -> doShowConfigFile(AppContext.CONFIG_FILE)));
        menu.getItems().add(makeSectionHeader("Links"));
        menu.getItems().add(makeRichMenuItem("👨‍💻", "Developer Blog",
		"KIM GAP-SU / Seoul, Republic of Korea", null,
		() -> openBrowser("https://github.com/GarpsuKim")));
        menu.getItems().add(makeRichMenuItem("⬇", "Installer",
		"Download KootPanKing installer", null,
		() -> openBrowser("https://github.com/GarpsuKim/KootPanKingThree/releases/tag/KootPanKingThree")));
        menu.getItems().add(makeRichMenuItem("🧩", "Source Code",
		"Java source code", null,
		() -> openBrowser("https://github.com/GarpsuKim/KootPanKingThree")));
        menu.getItems().add(makeRichMenuItem("☕", "Java/JVM",
		"Download Java runtime installer", null,
		() -> openBrowser("https://www.oracle.com/java")));
        menu.getItems().add(makeSectionHeader("Info"));
        MenuItem aboutItem = makeRichMenuItem("ℹ️", "About",
		"Program Info", "F1", this::doShowAbout);
        aboutItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("F1"));
        menu.getItems().add(aboutItem);
        menu.getItems().add(makeRichMenuItem("📩", "Error Report & Contact",
		"Contact developer via email", null, this::doContactDeveloper));
        menu.getItems().add(makeSectionHeader("Theme"));
        menu.getItems().add(makeRichMenuItem(
		themeMode == ThemeMode.PINK_GLASS ? "💙" : "💖",
		themeMode == ThemeMode.PINK_GLASS ? "Switch to Default Theme" : "Switch to Pink Glass Theme",
		"Switch UI theme", null, this::toggleTheme));
        return menu;
		}
	*/
    // ═══════════════════════════════════════════════════════════
    //  메뉴 액션
    // ═══════════════════════════════════════════════════════════
    private void doOpen() {
        new Thread(() -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Open Text File");
            fc.setInitialDirectory(new File(System.getProperty("user.home")));
            fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                    "Text File (*.txt, *.log, *.ini, *.java ...)",
                    "*.txt","*.log","*.ini","*.java","*.md","*.csv","*.bat",
                    "*.kt","*.scala","*.groovy","*.clj","*.cmd","*.sh","*.gradle",
				"*.properties","*.xml","*.json","*.html","*.htm","*.","*.cpp","*.py"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
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
    /** App Restart */
    private void doRestart() {
		String title = "Restart Program";
		String labelMessage = "Save settings and restart?";
		String timerlMessage = "Auto cancel in: 15s";
		int second	= 15;
		if (yesNoTimerConfirm(title, labelMessage, timerlMessage , second )) {		
			System.out.println("프로그램 재시작 : doRestart --> appRestarter.restartApp");
			appRestarter.restartApp();
		}
	}
	private void doExit() {
		String title = "Confirm Exit";
		String labelMessage = "Exit the program?";
		String timerlMessage = "Auto cancel in: 15s";
		int second	= 15;
		if (yesNoTimerConfirm(title, labelMessage, timerlMessage , second ))	exitAll();
	}
    private void doWindowsReboot() {
		String title = "Confirm System Reboot";
		String labelMessage = "Reboot the system?";
		String timerlMessage = "Auto cancel in: 15s";
		int second	= 15;
		if (yesNoTimerConfirm(title, labelMessage, timerlMessage , second )){
			String title0 = "System Rebooting";
			String labelMessage0 = " System reboot in progress.";
			String timerlMessage0 = "Auto cancel in: 30s";
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
							"Telegram Remote Reboot Notice",
							GmailSender.APP_SIGNATURE + "PC is rebooting via Telegram command.\n\nTime: " + now
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
                timerLbl.setText("Auto cancel in: " + sec[0] + "s");
                dlg.setTitle("Confirm Exit — " + sec[0] + "s auto cancel");
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
        dlg.setTitle("Upgrade OK");
        dlg.setAlwaysOnTop(true);
        Label msg = new Label("Upgrade the program?");
        msg.setStyle("-fx-font-family:'Malgun Gothic'; -fx-font-size:13px;");
        Button yes = new Button("Yes");
        Button no  = new Button("No");
        yes.setPrefWidth(72); no.setPrefWidth(72);
        final boolean[] confirmed = {false};
        final int[]     sec       = {15};
        Label timerLbl = new Label("Auto cancel in: 15s");
        timerLbl.setStyle("-fx-text-fill:#888888; -fx-font-size:11px;");
        javafx.animation.Timeline countdown = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ev -> {
                sec[0]--;
                timerLbl.setText("Auto cancel in: " + sec[0] + "s");
                dlg.setTitle("Confirm Exit — " + sec[0] + "s auto cancel");
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
            System.err.println("로그 File 열기 Failed: " + e.getMessage());
		}
	}
    private void doDeleteOldLogs() {
        String logPath = AppLogger.getLogFilePath();
        if (logPath == null || logPath.isEmpty()) {
            showAlert("Log file path not found.", "LogDelete"); return;
		}
        File logDir = new File(logPath).getParentFile();
        if (logDir == null || !logDir.exists()) {
            showAlert("Log folder not found.", "LogDelete"); return;
		}
        File current = new File(logPath);
        File[] old = logDir.listFiles(f ->
            f.isFile() && f.getName().endsWith(".txt")
		&& !f.getAbsolutePath().equals(current.getAbsolutePath()));
        if (old == null || old.length == 0) {
            showAlert("No old log files to delete.", "LogDelete"); return;
		}
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(theStage);
        confirm.setTitle("Delete Old Logs");
        confirm.setHeaderText(null);
        confirm.setContentText("Delete " + old.length + " old log files?\n"
		+ "Folder: " + logDir.getAbsolutePath());
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            int deleted = 0;
            for (File f : old) if (f.delete()) deleted++;
            showAlert(deleted + " Delete Done.", "LogDelete");
		});
	}
    public static void doShowConfigFile(String path) {
        try {
            if (path == null || path.trim().isEmpty()) return;
            java.io.File CFG = new java.io.File(path);
            if (!CFG.exists()) return;
			openTextFileWindow(CFG);
			} catch (Exception e) {
            System.err.println("File 열기 Failed [" + path + "] , " + e.getMessage());
		}
	}
    private void doShowAbout() {   showAboutDialog();	}
    private void doContactDeveloper() {
        final String RECEIVER = "garpsu@naver.com";
        Stage dlg = new Stage();
        dlg.initOwner(theStage);
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.initStyle(StageStyle.UTILITY);
        dlg.setTitle("Error Report & Contact Developer");
        dlg.setAlwaysOnTop(true);
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8); grid.setVgap(8);
        grid.setPadding(new Insets(14));
        grid.add(new Label("Recipient:"), 0, 0);
        grid.add(new Label(RECEIVER), 1, 0);
        grid.add(new Label("Sender Phone:"), 0, 1);
        TextField phoneField = new TextField(); phoneField.setPromptText("010-xxxx-yyyy");
        grid.add(phoneField, 1, 1);
        grid.add(new Label("Sender Name:"), 0, 2);
        TextField nameField = new TextField();
        grid.add(nameField, 1, 2);
        grid.add(new Label("Type:"), 0, 3);
        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton rbErr = new RadioButton("Error Report");  rbErr.setToggleGroup(typeGroup);
        RadioButton rbImp = new RadioButton("Improvement Request");  rbImp.setToggleGroup(typeGroup);
        RadioButton rbAdd = new RadioButton("Feature Request");  rbAdd.setToggleGroup(typeGroup);
        HBox typeRow = new HBox(8, rbErr, rbImp, rbAdd);
        grid.add(typeRow, 1, 3);
        grid.add(new Label("Content:"), 0, 4);
        TextArea bodyArea = new TextArea(); bodyArea.setPrefRowCount(7); bodyArea.setWrapText(true);
        grid.add(bodyArea, 1, 4);
        // ── Enable자 첨부 File 목록 ─────────────────────────────
        java.util.List<java.io.File> userAttachFiles = new java.util.ArrayList<>();
        javafx.collections.ObservableList<String> attachNames =
		javafx.collections.FXCollections.observableArrayList();
        javafx.scene.control.ListView<String> attachList = new javafx.scene.control.ListView<>(attachNames);
        attachList.setPrefHeight(72);
        attachList.setPlaceholder(new Label("(No attachments)"));
        Button attachBtn  = new Button("📎 Attach File");
        Button attachDelBtn = new Button("Delete");
        attachDelBtn.setDisable(true);
        attachList.getSelectionModel().selectedIndexProperty().addListener(
		(ob, ov, nv) -> attachDelBtn.setDisable(nv.intValue() < 0));
        attachBtn.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select files to attach (multiple OK)");
            fc.getExtensionFilters().add(
			new FileChooser.ExtensionFilter("All Files", "*.*"));
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
        grid.add(new Label("Attachments:"), 0, 5);
        grid.add(attachBox, 1, 5);
        // ── 전송 결과 표h 라벨 ──────────────────────────────────
        Label resultLbl = new Label("");
        resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold;");
        resultLbl.setWrapText(true);
        resultLbl.setMaxWidth(400);
        Button okBtn  = new Button("OK");  okBtn.setPrefWidth(80);
        Button canBtn = new Button("Cancel");  canBtn.setPrefWidth(80);
        canBtn.setOnAction(e -> dlg.close());
        okBtn.setOnAction(ev -> {
            String phone = phoneField.getText().trim();
            String name  = nameField.getText().trim();
            String body  = bodyArea.getText().trim();
            String type  = rbErr.isSelected() ? "Error Report"
			: rbImp.isSelected() ? "Improvement Request"
			: rbAdd.isSelected() ? "Feature Request" : "";
            // ── 입력 유효성 검사 (dlg owner로 직접 Alert) ────────
            if (phone.isEmpty() || name.isEmpty() || body.isEmpty()) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.initOwner(dlg); a.setTitle("Input Required");
                a.setHeaderText(null);
                a.setContentText("Phone, name, and content are required.");
                a.showAndWait(); return;
			}
            if (!phone.matches("010-\\d{3,4}-\\d{4}")) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.initOwner(dlg); a.setTitle("Input Required");
                a.setHeaderText(null);
                a.setContentText("Invalid phone format.\n010-xxxx-yyyy");
                a.showAndWait(); return;
			}
            boolean useDev = !gmail.isConfigured();
            String from = useDev ? GmailSender.devGmailId()   : gmail.from;
            String pass = useDev ? GmailSender.devGmailPass() : gmail.pass;
            if (from.isEmpty() || pass.isEmpty()) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.initOwner(dlg); a.setTitle("Error");
                a.setHeaderText(null);
                a.setContentText("Sender account not found.\nSet Gmail ID and Password in [Tools → Gmail Settings] first..");
                a.showAndWait(); return;
			}
            String subject  = "[KootPanKing Inquiry] " + (type.isEmpty() ? "" : type + " - ") + name;
            String mailBody = (type.isEmpty() ? "" : "■ Type: " + type + "\n")
			+ "■ Name: " + name + "\n■ Phone: " + phone + "\n"
			+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" + body + "\n"
			+ "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" + GmailSender.APP_SIGNATURE;
            // ── 첨부 File: 마스터 ini + 로그 + Enable자 Select ─────
            java.util.List<java.io.File> attachFiles = new java.util.ArrayList<>();
            java.io.File iniFile = new java.io.File(AppContext.CONFIG_FILE);
            if (iniFile.exists()) attachFiles.add(iniFile);
            String logPath = AppLogger.getLogFilePath();
            if (logPath != null && !logPath.isEmpty()) {
                java.io.File logFile = new java.io.File(logPath);
                if (logFile.exists()) attachFiles.add(logFile);
			}
            attachFiles.addAll(userAttachFiles);  // Enable자 추가 File
            okBtn.setDisable(true);
            okBtn.setText("Sending...");
            resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#555555;");
            resultLbl.setText("⏳ Sending...");
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
                    okBtn.setText("OK");
                    if (fErr == null) {
                        resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#006600;");
                        resultLbl.setText("✅ Sent! → " + RECEIVER);
						} else {
                        resultLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#cc0000;");
                        resultLbl.setText("❌ Send failed: " + fErr);
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
    //  Text File 뷰어 (기존 코드 그대로)
    // ═══════════════════════════════════════════════════════════
    public static void openTextFileWindow(File file) {
        new Thread(() -> {
            try {
                MainWindow.TextFileReader.read(file);
                final String            enc0   = MainWindow.TextFileReader.encLabel;
                // final java.util.ArrayList<String> lines0 = MainWindow.TextFileReader.content;
                Platform.runLater(() -> showTextWindow(file, enc0, MainWindow.TextFileReader.content));
                log("Opening file: " + file.getName());
				} catch (Exception ex) {
                Platform.runLater(() -> showAlert("File read failed:\n" + ex.getMessage(), "Open"));
                log("[ERROR] File open failed: " + file.getName() + " — " + ex.getMessage());
			}
		}, "FileOpen").start();
	}
	
	/** Text 내용을 새 창에 Show */
	/*
		private static void showTextWindowSwing(File file, String encLabel, String content) {
		JFrame sub = new JFrame("📄 " + file.getName());
		sub.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		JTextArea ta = new JTextArea(content);
		ta.setEditable(false);
		ta.setFont(new Font("Monospaced", Font.PLAIN, 16));
		ta.setBackground(new Color(235, 245, 255));
		ta.setForeground(new Color( 20,  50,  90));
		ta.setCaretColor(new Color( 20,  50,  90));
		ta.setLineWrap(true);
		ta.setWrapStyleWord(false);
		ta.setMargin(new Insets(6, 8, 6, 8));
		
		// ── lines번호 패널 (modelToView2D 기반) ─────────────────────────
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
		
		// ta 크기/내용/스크롤 변경 h lines번호 패널 갱신
		ta.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
		public void insertUpdate(javax.swing.event.DocumentEvent e)  { lineNumPanel.revalidate(); lineNumPanel.repaint(); }
		public void removeUpdate(javax.swing.event.DocumentEvent e)  { lineNumPanel.revalidate(); lineNumPanel.repaint(); }
		public void changedUpdate(javax.swing.event.DocumentEvent e) { lineNumPanel.revalidate(); lineNumPanel.repaint(); }
		});
		ta.addComponentListener(new ComponentAdapter() {
		@Override public void componentResized(ComponentEvent e) { lineNumPanel.revalidate(); lineNumPanel.repaint(); }
		});
		sp.getViewport().addChangeListener(e -> { lineNumPanel.revalidate(); lineNumPanel.repaint(); });
		
		// 상태바: 인코딩 + File Path + 크기
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
        // TextArea 는 All 내용을 DOM 에 올려서 대용량 불가
        // ListView 는 VirtualFlow 로 화면에 보이는 셀만 생성 → 70만 lines도 즉h 로드
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
            // "-fx-font-family: 'Monospaced'; -fx-font-size: 16px;"
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
                // lines 번호 영역
                numLabel.setMinWidth(fNumW); numLabel.setMaxWidth(fNumW);
                numLabel.setAlignment(Pos.CENTER_RIGHT);
                numLabel.setStyle(
                    // "-fx-font-family:'Monospaced'; -fx-font-size:16px;"
                    "-fx-font-family:'Consolas'; -fx-font-size:16px;"
                    + "-fx-text-fill:" + fNumFg + ";"
                    + "-fx-background-color:" + fNumBg + ";"
                    + "-fx-padding:0 8 0 4;"
                    + "-fx-border-color:" + borderColor() + "; -fx-border-width:0 1 0 0;");
                // 본문 영역
                textLabel.setWrapText(true);
                textLabel.setStyle(
                    // "-fx-font-family:'Monospaced'; -fx-font-size:16px;"
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

        // ── Other단 Info 바 ────────────────────────────────────────
        Label info = new Label(" " + encLabel + "  |  "
            + file.getAbsolutePath() + "  (" + file.length() + " bytes)"
            + "  [" + lineCount + " lines]");
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
                    showAlert("Notepad++ launch failed:\n" + ex.getMessage(), "Notepad++");
                }
            } else {
                // 미설치 → Info 메시지 + 홈페이지
                showAlert(
                    "Notepad++ is not installed.\n"
                    + "Please install Notepad++ first.\n\n"
                    + "Website: https://notepad-plus-plus.org/downloads/",
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
    //  Favorites 헬퍼 (기존 코드 그대로)
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
            showAlert(exeName + " path not found.", "Favorites"); return;
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
            showAlert("Execution failed: " + ex.getMessage(), "Favorites");
		}
	}
    private void openSlotEditor(int idx) {
        String curName = AppContext.getFavoriteName(idx);
        String curPath = AppContext.getFavoritePath(idx);
        Stage dlg = new Stage();
        dlg.initOwner(theStage);
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.initStyle(StageStyle.UTILITY);
        dlg.setTitle("Favorites Slot " + (idx + 1) + " Register");
        TextField nameField = new TextField(curName); nameField.setPrefWidth(220);
        TextField pathField = new TextField(curPath); pathField.setPrefWidth(280);
        Button browseBtn = new Button("Browse...");
        browseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Executable");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
			"Executable (*.exe, *.bat, *.lnk)", "*.exe","*.bat","*.cmd","*.lnk"));
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files","*.*"));
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
        Button ok  = new Button("OK");  ok.setPrefWidth(72);
        Button can = new Button("Cancel");  can.setPrefWidth(72);
        can.setOnAction(e -> dlg.close());
        ok.setOnAction(e -> {
            String n = nameField.getText().trim();
            String p = pathField.getText().trim();
            if (n.isEmpty() || p.isEmpty()) {
                showAlert("Please enter both name and path.", "Favorites"); return;
			}
            AppContext.setFavorite(idx, n, p);
            dlg.close();
            rebuildMenuBar();
		});
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(6); grid.setVgap(8); grid.setPadding(new Insets(14));
        grid.add(new Label("Name:"), 0, 0); grid.add(nameField, 1, 0, 2, 1);
        grid.add(new Label("Path:"), 0, 1); grid.add(pathField, 1, 1); grid.add(browseBtn, 2, 1);
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
    //  생활Tools 헬퍼 (기존 코드 그대로)
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
        if (!f.exists()) { showAlert("Please run [Update Calendar] first.", "Calendar"); return; }
        try { java.awt.Desktop.getDesktop().browse(f.toURI()); }
        catch (Exception ex) { showAlert("Failed to open browser: " + ex.getMessage(), "Calendar"); }
	}
    private void updateCalendarHtml() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(theStage);
        confirm.setTitle("Update Calendar");
        confirm.setHeaderText(null);
        confirm.setContentText("Auto-updates the calendar including temporary holidays.");
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
				Platform.runLater(() -> showAlert("Download failed (HTTP " + code + ")", "Update Calendar")); return; }
				try (java.io.InputStream in = con.getInputStream();
					java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
					in.transferTo(out);
				}
				con.disconnect();
				Platform.runLater(() -> showAlert(
				"Calendar updated.\nLocation: " + dest.getAbsolutePath(), "Update Calendar"));
				} catch (Exception ex) {
				Platform.runLater(() -> showAlert("Download Error: " + ex.getMessage(), "Update Calendar"));
			}
		}, "CalendarUpdate").start();
	}
	
    private void openBrowser(String url) {
        try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); }
        catch (Exception ex) { showAlert("Failed to open browser: " + ex.getMessage(), "Error"); }
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
        showAlert("Clock is not yet initialized.\nPlease try again in a moment.", "Notice");
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
    private static String defaultStatusText = " Ready";
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
    // ── 폰트 Select 다이얼로그 ────────────────────────────────────
    private void showFontDialog() {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(theStage);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("Font Selection");
		
        // 시스템 폰트 목록
        java.util.List<String> fonts =
		new java.util.ArrayList<>(javafx.scene.text.Font.getFamilies());
		
        javafx.scene.control.ListView<String> listView =
		new javafx.scene.control.ListView<>(
		javafx.collections.FXCollections.observableArrayList(fonts));
        listView.setPrefHeight(340);
		
        // 현재 Select 폰트 표시
        String curFamily = AppContext.getUiFontFamily();
        if (fonts.contains(curFamily))
		listView.getSelectionModel().select(curFamily);
        listView.scrollTo(listView.getSelectionModel().getSelectedIndex());
		
        // 미리보기
        javafx.scene.control.Label preview = new javafx.scene.control.Label("ABC abc 123 Preview");
        preview.setStyle("-fx-font-size: 14px;");
        listView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) preview.setStyle(
			"-fx-font-family: '" + n + "'; -fx-font-size: 14px;");
		});
		
        // 크기
        javafx.scene.control.Label sizeLbl =
		new javafx.scene.control.Label("Size:");
        javafx.scene.control.Spinner<Integer> sizeSpinner =
		new javafx.scene.control.Spinner<>(8, 24, AppContext.getUiFontSize());
        sizeSpinner.setEditable(true);
        sizeSpinner.setPrefWidth(75);
		
        javafx.scene.layout.HBox sizeBox = new javafx.scene.layout.HBox(8, sizeLbl, sizeSpinner);
        sizeBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        sizeBox.setPadding(new javafx.geometry.Insets(6, 0, 6, 0));
		
        // 버튼
        javafx.scene.control.Button btnOk =
		new javafx.scene.control.Button("OK");
        javafx.scene.control.Button btnCancel =
		new javafx.scene.control.Button("Cancel");
        btnOk.setDefaultButton(true);
        btnCancel.setCancelButton(true);
		
        btnOk.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                AppContext.setUiFontFamily(sel);
                AppContext.setUiFontSize(sizeSpinner.getValue());
                // 즉h All Apply
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
            new javafx.scene.control.Label("Select Font:"),
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
        MenuItem item = makeRichMenuItem("⛔", text, "This feature is currently unavailable.", null, null);
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
    // ── 스타 빌더 ───────────────────────────────────────────
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
			// ── CR/LF 정규화 후 순수 lines 목록으로 분리 ───────────
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