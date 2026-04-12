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
    private static  Stage    stage;
    private static  TextArea logArea;
    private static  Label    statusBar;
	
    // ─────────────────────────────────────────────────────────────
    private static final String APP_NAME = "[KootPanKingThree 3차원_끝판왕 (v1.0)]";
	
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
		this.stage = primaryStage;
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
    public void theMainWindow(Stage primaryStage) {
        appDir      = AppContext.APP_DIR;
        settingsDir = AppContext.SETTINGS_DIR;		
		System.out.println("■■■■■ theMainWindow(Stage primaryStage)");	
		
		this.stage = primaryStage;
        primaryStage.setTitle("끝판왕 (KootPanKingThree Ver 1.1)");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setStyle(logStyle());
        logArea.setFont(javafx.scene.text.Font.font("Malgun Gothic", 13));
        ScrollPane scrollPane = new ScrollPane(logArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle(scrollPaneStyle());
        statusBar = new Label("준비");
        statusBar.setMaxWidth(Double.MAX_VALUE);
        statusBar.setStyle(statusBarStyle());
        BorderPane root = new BorderPane();
        root.setStyle(rootStyle());
        root.setCenter(scrollPane);
        root.setBottom(statusBar);
        root.setTop(buildMenuBar());
        Scene scene = new Scene(root, 860, 520);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest((WindowEvent e) -> {
            e.consume();
            doClose();
		});
        // primaryStage.show();
		openTheCity ( " ", java.time.ZoneId.systemDefault() , " ");	
	}
	public void toggleTheMainWindow() {
		System.out.println("■■■■■ toggleTheMainWindow()");
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(this::toggleTheMainWindow);
			return;
		}
		if (this.stage == null) {
			System.out.println("toggleTheMainWindow(): stage == null");
			return;
		}
		if (this.stage.isShowing()) {
			this.stage.hide();
			} else {
			this.stage.show();
			this.stage.toFront();
			this.stage.requestFocus();
		}
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
    private void saveConfig() {
	}
	
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
	
    /** 설정 파일 열기 */
	private void openConfigFile() {
		boolean ok = AppContext.openCONFIG_FILE();
		if (!ok) {
			showAlert("설정 파일을 열지 못했습니다.\n" + AppContext.CONFIG_FILE, "기본 설정 파일");
		}
	}
	
    /** ChimeController */
    private void initChimeControllerIfNeeded(Stage owner) {
        if (chimeController != null) return;
        chimeController = new ChimeController(owner, new ChimeController.HostCallback() {
            @Override public boolean isChild() { return false; }
            @Override public java.time.ZoneId getTimeZone() { return java.time.ZoneId.systemDefault(); }
            @Override public void startRainbow(int durationSec) { /* 시계 미연동 시 무시 */ }
            @Override public BackgroundPlayer.YoutubePlayer getVideoPlayer() { return null; }
		});
        // pending 설정 복원
        try {
            chimeController.setEnabled(Boolean.parseBoolean(config.getProperty("chimeEnabled","false")));
            chimeController.setFile(config.getProperty("chimeFile",""));
            chimeController.setDuration(Integer.parseInt(config.getProperty("chimeDuration","0")));
            chimeController.setVolume(Integer.parseInt(config.getProperty("chimeVolume","80")));
            String minsStr = config.getProperty("chimeMinutes","0");
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
		
        javafx.scene.control.MenuItem send = new javafx.scene.control.MenuItem("지금 Gmail 보내기");
        // stub — GmailSender 에 실제 다이얼로그 메서드가 추가되면 여기서 호출
		
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
		
        javafx.scene.control.MenuItem naverCfg = new javafx.scene.control.MenuItem("네이버 캘린더 설정");
        naverCfg.setOnAction(e -> showAlert(
            "네이버 CalDAV 계정 정보를 설정 파일(clock_settings.ini)에 입력하세요.\n\n" +
		"  naver.caldav.id=네이버아이디\n  naver.caldav.password=비밀번호", "네이버 캘린더 설정"));
		
        menu.getItems().addAll(
            send, guide, new javafx.scene.control.SeparatorMenuItem(),
            googleCal, new javafx.scene.control.SeparatorMenuItem(),
            naverCal,  new javafx.scene.control.SeparatorMenuItem(), naverCfg
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
		
        javafx.scene.control.MenuItem settings = new javafx.scene.control.MenuItem("텔레그램 설정...");
        javafx.scene.control.MenuItem help     = new javafx.scene.control.MenuItem("텔레그램 설정 안내");
		
        settings.setOnAction(e -> {
            if (tg != null) tg.showTelegramDialog(stage);
		});
        help.setOnAction(e -> {
            if (tg != null) tg.showTelegramHelp(stage);
		});
        menu.getItems().addAll(settings, help);
        return menu;
	}
	
    // ── About 다이얼로그 ──────────────────────────────────────────
    private void showAboutDialog() {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setResizable(false);
		
        final int[] sec = {48};
        dlg.setTitle(APP_NAME + "  —  " + sec[0] + "초 후 닫힘");
		
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
                dlg.setTitle(APP_NAME + "  —  " + sec[0] + "초 후 닫힘");
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
    private void setMultipleConfigAndSave(String... entries) {
        for (int i = 0; i + 1 < entries.length; i += 2)
		config.setProperty(entries[i], entries[i + 1]);
        saveConfig();
	}
	
    /** 단일 설정값 저장 */
    private void setConfigAndSave(String key, String value) {
        config.setProperty(key, value);
        saveConfig();
	}
	
    /** 프로그램 완전 종료 */
    private void exitAll() {
        saveConfig();
        AppLogger.close();
        Platform.exit();
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
            () -> {
                initChimeControllerIfNeeded(stage);
                if (chimeController != null) chimeController.showChimeDialog();
			}));
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
		
        java.util.List<Integer> filledSlots = new java.util.ArrayList<>();
        for (int i = 0; i < OFFICE_SLOT_COUNT; i++) {
            String n = getConfigValue(slotNameKey(i), "");
            String p = getConfigValue(slotPathKey(i), "");
            if (!n.isEmpty() && !p.isEmpty()) filledSlots.add(i);
		}
        for (int fi = 0; fi < filledSlots.size(); fi++) {
            int slotIdx  = filledSlots.get(fi);
            String name  = getConfigValue(slotNameKey(slotIdx), "");
            String path  = getConfigValue(slotPathKey(slotIdx), "");
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
	
    private int findNextEmptySlot() {
        for (int i = 0; i < OFFICE_SLOT_COUNT; i++) {
            String name = getConfigValue(slotNameKey(i), "");
            String path = getConfigValue(slotPathKey(i), "");
            if (name.isEmpty() || path.isEmpty()) return i;
		}
        return OFFICE_SLOT_COUNT;
	}
	
    private Menu buildSlotSubMenu(Menu officeMenu, int slotIdx,
		String name, String path, int prevSlot, int nextSlot) {
        Menu slotMenu = makeMenu("📌 " + name, path);
		
        slotMenu.getItems().add(makeRichMenuItem("▶", "실행",
		path, null, () -> launchByPath(path)));
        slotMenu.getItems().add(new SeparatorMenuItem());
		
        MenuItem delItem = makeRichMenuItem("🗑️", "삭제", "이 항목을 삭제합니다", null, () -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(stage);
            confirm.setTitle("업무도구 삭제");
            confirm.setHeaderText(null);
            confirm.setContentText("「" + name + "」 을 삭제하시겠습니까?");
            confirm.setOnShown(ev ->
			((Stage) confirm.getDialogPane().getScene().getWindow()).setAlwaysOnTop(true));
            confirm.showAndWait().ifPresent(btn -> {
                if (btn != ButtonType.OK) return;
                setMultipleConfigAndSave(slotNameKey(slotIdx), "", slotPathKey(slotIdx), "");
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
		"화면 테마를 전환합니다", null, this::toggleTheme));
        return menu;
	}
	
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
	
    private void doClose() {
        config.remove("mainWindow");
        saveConfig();
        stage.hide();
	}
	
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
        exitAll();
	}
	
    private void showUpgradeStub() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("프로그램 업그레이드");
        alert.setHeaderText(null);
        alert.setContentText("이 기능은 현재 지원되지 않습니다.");
        alert.showAndWait();
	}
	
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
	
    private void doShowConfigFile() {
        openConfigFile();
	}
	
    private void doShowAbout() {
        showAboutDialog();
	}
	
    private void doContactDeveloper() {
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
        RadioButton rbErr = new RadioButton("오류 신고");  rbErr.setToggleGroup(typeGroup);
        RadioButton rbImp = new RadioButton("개선 요청");  rbImp.setToggleGroup(typeGroup);
        RadioButton rbAdd = new RadioButton("추가 요청");  rbAdd.setToggleGroup(typeGroup);
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
            boolean useDev = !gmail.isConfigured();
            String from = useDev ? GmailSender.devGmailId()   : gmail.from;
            String pass = useDev ? GmailSender.devGmailPass() : gmail.pass;
            if (from.isEmpty() || pass.isEmpty()) {
                showAlert("발송 계정을 확인할 수 없습니다.", "오류"); return;
			}
            String subject  = "[끝판왕 문의] " + (type.isEmpty() ? "" : type + " - ") + name;
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
    //  텍스트 파일 뷰어 (기존 코드 그대로)
    // ═══════════════════════════════════════════════════════════
    private void openTextFileWindow(File file) {
        new Thread(() -> {
            try {
                MainWindow.TextFileReader.Result r = MainWindow.TextFileReader.read(file);
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
    //  업무도구 헬퍼 (기존 코드 그대로)
    // ═══════════════════════════════════════════════════════════
    private static final int OFFICE_SLOT_COUNT = 20;
    private static String slotNameKey(int i) { return "office.slot." + i + ".name"; }
    private static String slotPathKey(int i) { return "office.slot." + i + ".path"; }
	
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
        String curName = getConfigValue(slotNameKey(idx), "");
        String curPath = getConfigValue(slotPathKey(idx), "");
		
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
            setMultipleConfigAndSave(slotNameKey(idx), n, slotPathKey(idx), p);
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
        String sn = getConfigValue(slotNameKey(src), "");
        String sp = getConfigValue(slotPathKey(src), "");
        String dn = getConfigValue(slotNameKey(dst), "");
        String dp = getConfigValue(slotPathKey(dst), "");
        setMultipleConfigAndSave(
            slotNameKey(src), dn, slotPathKey(src), dp,
		slotNameKey(dst), sn, slotPathKey(dst), sp);
	}
	
    private void rebuildMenuBar() {
        Platform.runLater(() ->
		((BorderPane) stage.getScene().getRoot()).setTop(buildMenuBar()));
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
    //  로그 내부 구현 (기존 코드 그대로)
    // ═══════════════════════════════════════════════════════════
    private void appendLog(String message) {
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
	
    // ── 테마 토글 ──────────────────────────────────────────────
    private void toggleTheme() {
        themeMode = (themeMode == ThemeMode.PINK_GLASS) ? ThemeMode.BASIC : ThemeMode.PINK_GLASS;
        logArea.setStyle(logStyle());
        ((BorderPane) stage.getScene().getRoot()).setStyle(rootStyle());
        statusBar.setStyle(statusBarStyle());
        rebuildMenuBar();
	}
	
    // ── 메뉴 팩토리 (기존 코드 그대로) ──────────────────────────
    private Menu makeMenu(String text) { return makeMenu(text, null); }
	
    private Menu makeMenu(String text, String helpText) {
        Menu m = new Menu(text);
        m.setStyle("-fx-font-family:'Malgun Gothic'; -fx-font-size:13px; -fx-text-fill:" + fgColor() + ";");
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
    private String fgColor()         { return themeMode == ThemeMode.PINK_GLASS ? FG : "#1a3a6b"; }
    private String barBgColor()      { return themeMode == ThemeMode.PINK_GLASS ? BAR_BG : "rgba(210,228,255,0.78)"; }
    private String borderColor()     { return themeMode == ThemeMode.PINK_GLASS ? GLASS_BORDER : "rgba(100,140,200,0.40)"; }
    private String glassPanelColor() { return themeMode == ThemeMode.PINK_GLASS ? GLASS_PANEL : "rgba(255,255,255,0.55)"; }
    private String glassHoverColor() { return themeMode == ThemeMode.PINK_GLASS ? GLASS_HOVER  : "rgba(210,228,255,0.65)"; }
	
    // ── 스타일 빌더 ───────────────────────────────────────────
    private String logStyle() {
        return "-fx-font-family: 'Malgun Gothic'; -fx-font-size: 13px;"
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
		+ "-fx-font-family: 'Malgun Gothic'; -fx-font-size: 11px;"
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
    public static class TextFileReader {
        public static class Result {
            public final String encLabel;
            public final String content;
            Result(String e, String c) { encLabel = e; content = c; }
		}
		
        public static Result read(File file) throws Exception {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            String[] candidates = {"UTF-8", "EUC-KR", "MS949", "ISO-8859-1"};
            for (String enc : candidates) {
                try {
                    String s = new String(bytes, enc);
                    return new Result(enc, s);
				} catch (Exception ignored) {}
			}
            return new Result("UTF-8", new String(bytes, "UTF-8"));
		}
		
        public static String readContent(File file) throws Exception {
            return read(file).content;
		}
	}
}
