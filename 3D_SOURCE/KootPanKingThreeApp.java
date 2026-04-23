import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Application;
import javafx.animation.Animation;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import javafx.util.Duration;
public class KootPanKingThreeApp {
	// ─────────────────────────────────────────────────────────────
	// 팝업의 "서브메뉴(Menu)"만 mac 스타 Apply
	// 부모 ContextMenu 는 절대 수정Other지 않음
	// ─────────────────────────────────────────────────────────────
	private static  String SUBMENU_MAC_CAPTION_STYLE =
    "-fx-background-color: rgba(255,255,255,0.01);" +
    "-fx-text-fill: #1f1f1f;" +
    "-fx-font-size: 13px;" +
    "-fx-font-weight: normal;";
	private static  String SUBMENU_MAC_ITEM_NORMAL_STYLE =
    "-fx-background-color: rgba(255,255,255,0.96);" +
    "-fx-text-fill: #1f1f1f;" +
    "-fx-font-size: 13px;" +
    "-fx-background-radius: 10;" +
    "-fx-padding: 7 16 7 16;";
	private static  String SUBMENU_MAC_ITEM_HOVER_STYLE =
    "-fx-background-color: linear-gradient(to bottom, #4da3ff 0%, #0a84ff 100%);" +
    "-fx-text-fill: white;" +
    "-fx-font-size: 13px;" +
    "-fx-background-radius: 10;" +
    "-fx-padding: 7 16 7 16;";
	private void enhancePopupSubMenusMacOnly(javafx.scene.control.ContextMenu popup) {
		if (popup == null) return;
		for (javafx.scene.control.MenuItem item : popup.getItems()) {
			if (item instanceof javafx.scene.control.Menu subMenu) {
				styleMacSubMenuRecursive(subMenu);
			}
		}
		popup.setOnShown(ev -> javafx.application.Platform.runLater(() -> {
			for (javafx.scene.control.MenuItem item : popup.getItems()) {
				if (item instanceof javafx.scene.control.Menu subMenu) {
					wireMacSubMenuRecursive(subMenu);
				}
			}
		}));
	}
	private void styleMacSubMenuRecursive(javafx.scene.control.Menu menu) {
		if (menu == null) return;
		menu.setStyle(SUBMENU_MAC_CAPTION_STYLE);
		for (javafx.scene.control.MenuItem child : menu.getItems()) {
			if (child instanceof javafx.scene.control.SeparatorMenuItem) continue;
			if (child instanceof javafx.scene.control.Menu nested) {
				nested.setStyle(SUBMENU_MAC_CAPTION_STYLE);
				styleMacSubMenuRecursive(nested);
				} else {
				child.setStyle(SUBMENU_MAC_ITEM_NORMAL_STYLE);
			}
		}
	}
	private void wireMacSubMenuRecursive(javafx.scene.control.Menu menu) {
		if (menu == null) return;
		javafx.scene.Node menuNode = menu.getStyleableNode();
		if (menuNode != null && menuNode.getProperties().putIfAbsent("macSubmenuCaptionWired", Boolean.TRUE) == null) {
			menuNode.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
				menu.setStyle(
					"-fx-background-color: rgba(255,255,255,0.16);" +
					"-fx-text-fill: #111111;" +
					"-fx-font-size: 13px;" +
					"-fx-font-weight: normal;" +
					"-fx-background-radius: 8;"
				);
			});
			menuNode.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
				menu.setStyle(SUBMENU_MAC_CAPTION_STYLE);
			});
		}
		for (javafx.scene.control.MenuItem child : menu.getItems()) {
			if (child instanceof javafx.scene.control.SeparatorMenuItem) continue;
			javafx.scene.Node node = child.getStyleableNode();
			if (node != null && node.getProperties().putIfAbsent("macSubmenuItemWired", Boolean.TRUE) == null) {
				child.setStyle(SUBMENU_MAC_ITEM_NORMAL_STYLE);
				node.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
					child.setStyle(SUBMENU_MAC_ITEM_HOVER_STYLE);
					node.setScaleX(1.01);
					node.setScaleY(1.01);
				});
				node.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
					child.setStyle(SUBMENU_MAC_ITEM_NORMAL_STYLE);
					node.setScaleX(1.0);
					node.setScaleY(1.0);
				});
			}
			if (child instanceof javafx.scene.control.Menu nested) {
				wireMacSubMenuRecursive(nested);
			}
		}
	}
	private   String thisProgramName = "[KootPanKingThree 3D_Clock (v1.0)]";
	private MainWindow mainWindow; // 인스턴스별 메인 윈도우
    // AlarmController alarmController;
    private Properties config = new Properties();
    // private IniController iniController ;
    // ── Settings Save file Folder 결정 (우선순위 3단계) ─────────────────
    String EXE_PATH = ""; // ← 추가
    private String APP_DIR ;
	String SETTINGS_DIR;
	String CONFIG_FILE;
    // ── 인스턴스별 Settings File Path 및 자식 여부 ─────────────────────
    // 기본 인스턴스 : clock_settings.ini  (CONFIG_FILE 과 동)
    // 자식 인스턴스 : clock_settings_<CityName>.ini
    // String myConfigFile = CONFIG_FILE; // 기본값: 부모와 동
	String myConfigFile;
	GmailSender gmail = GmailSender.getInstance();
	Kakao kakao = new Kakao();
	TelegramBot tg;
    AppRestarter.ShutdownGuard shutdownGuard; // Force Shutdown Detected 훅
    AppRestarter appRestarter;                // Restart / AppCDS Manage
    TOOLS.CaptureManager screenCapture;             // Theme 캡처
    private final FxGPUNeon fxGPUNeon = new FxGPUNeon(); // FxGPUNeon 인스턴스 (inner class 생성용)
    FxGPUNeon.ClockController clockController; // FX 시계 컨트롤러 (카메라 프레임 주입용)
    private String theCityName = "Local";
    private java.time.ZoneId theTimeZone = java.time.ZoneId.systemDefault();
    // ── 세계시계 자식 창 목록 ─────────────────────────────────────────
    private String startArg1 = "default1", startArg2 = "default2", startArg3 = "default3";
    // ── 스마트폰 카메라 ────────────────────────────────────────────
    boolean cameraMode = false;
    TOOLS.CaptureManager.Camera camera = null;
    // cameraUrl → AppContext.getCameraUrl() / setCameraUrl() 로 이동
    boolean cameraFlipH = false; // Flip Horizontal
    boolean cameraFlipV = false; // Flip Vertical
    // ── ITS 교통 CCTV ────────────────────────────────────────────────────
    ItsCctvManager itsCctv = null;   // lazy-init: getItsCctv() 로 접근
    // ── YouTube 실시간 배경 ──────────────────────────────────────────
    BackgroundPlayer.YoutubePlayer ytPlayer = null; // lazy-init
    // youtubeUrl → AppContext.getYoutubeUrl() / setYoutubeUrl() 로 이동
    // ytdlpPath  → AppContext.getYtdlpPath()  / setYtdlpPath()  로 이동
    // ── 로컬 MP4 배경 재생 ───────────────────────────────────────────
    String localMp4LastFile = "";                   // ini: localmp4.lastFile
    double localMp4Volume   = 1.0;                  // ini: localmp4.volume (0.0~1.0)
    // ── Video 녹화 ────────────────────────────────────────────
    volatile boolean      videoRecording  = false;   // 녹Tue 중 플래그
    // ffmpeg 실행File Path (ini Save/로드)
    // ffmpegPath → AppContext.getFfmpegPath() / setFfmpegPath() 로 이동
    // ── 5초 간격 Image 시퀀스 Save (ffmpeg 없을 때 대체 모드) ──
    volatile boolean      imageSeqRecording  = false;
    File                  imageSeqOutputDir  = null;
    volatile long         imageSeqLastSaveMs = 0L;
	long     IMAGE_SEQ_INTERVAL_MS = 5_000L;
    File                  videoOutputFile = null;    // 최종 Save될 mp4 File
    File                  videoTempDir    = null;    // 임h JPEG 프레임 Folder
    AtomicInteger         videoFrameIndex = new AtomicInteger(0);
    javafx.scene.control.MenuItem camVideoItem = null; // 메뉴 참조 (Text 업데이트용)
    // ── 녹화 중 오버레이 ────────────────────────────────────────
    private javafx.stage.Stage             recOverlayStage    = null;
    private javafx.animation.Timeline      recOverlayTimeline = null;
    ChimeController chimeController;          // Chime
    // ── chime pending (loadConfig 시점에 chimeController 미생성) ──
    private boolean   pendingChimeEnabled  = false;
    private String    pendingChimeFile     = "";
    private int       pendingChimeDuration = 0;    // 0=15s, 1=30s, 2=Full playback
    private boolean[] pendingChimeMinutes  = null;
    private int       pendingChimeVolume   = 80;
    GoogleCalendarService googleCalendarService = new GoogleCalendarService();
    NaverCalendarService  naverCalendarService  = new NaverCalendarService();
    private int pendingRadius = -1;           // loadConfig에서 읽은 반지름 임h 보관
    private double pendingRainbowIntervalSec = 0.5; // loadConfig에서 읽은 Rainbow 인터벌
    // ── 시분초 행 pending ───────────────────────────────────────────────
    private boolean pendingDigitalShow        = true;
    /** 팝업 메뉴 디지탈 체크item — setOnShowing 에서 상태 동기Tue */
    private javafx.scene.control.CheckMenuItem digitalMenuItem = null;
    private int     pendingDigitalFormatIndex = 0;
    private String  pendingDigitalFontFamily  = "Consolas";
    private double  pendingDigitalFontSize    = 50.0;
    private int     pendingDigitalColorRgb    = 0xFFFFFFFF;
    private int     pendingDigitalScrollDir   = 3;
    private double  pendingDigitalScrollSpeed = 1.5;
    private boolean pendingDigitalNeon       = false;
    // ── 날짜 행 pending ─────────────────────────────────────────────────
    private boolean pendingFaceDateShow       = true;
    private int     pendingFaceDateFormatIndex= 0;
    private String  pendingFaceDateFontFamily = "Monospaced";
    private double  pendingFaceDateFontSize   = 60.0;
    private int     pendingFaceDateColorRgb   = 0xFF003333;
    private boolean pendingFaceDateNeon      = false;
    private FxGPUNeon.AppState.NeonBlinkStyle pendingDigitalNeonBlinkStyle = FxGPUNeon.AppState.NeonBlinkStyle.NONE;
    // Bug7: 날짜 스크롤 pending
    private int     pendingFaceDateScrollDir   = 3;
    private double  pendingFaceDateScrollSpeed = 2.9;
    boolean alwaysOnTop = true;
    boolean showDigital = true;
    boolean showNumbers = true;
    String theme = "Light";
    float opacity = 1.0f;
    int showInterval = 0;
    int animInterval = 0;
    java.awt.Font numberFont = new java.awt.Font("Georgia", java.awt.Font.BOLD, 14);
    java.awt.Font digitalFont = new java.awt.Font("Consolas", java.awt.Font.PLAIN, 14);
    java.awt.Color digitalColor = java.awt.Color.WHITE;
    java.awt.Color tickColor = null;
    boolean tickVisible = true;
    boolean secondVisible = true;
    java.awt.Color hourColor = new java.awt.Color(30, 50, 210);
    java.awt.Color minuteColor = new java.awt.Color(10, 160, 30);
    java.awt.Color secondColor = new java.awt.Color(220, 30, 30);
    java.awt.Color numberColor = null;
    boolean showLunar = false;
    java.awt.Color borderColor = null;
    int borderWidth = -1;
    int borderAlpha = 255;
    boolean borderVisible = true;
    boolean galaxyMode = false;
    float galaxySpeed = 0.004f;
    boolean matrixMode = false;
    float matrixSpeed = 1.5f;
    boolean matrix2Mode = false;
    float matrix2Speed = 1.5f;
    boolean matrix3Mode = false;
    float matrix3Speed = 1.5f;
    boolean rainMode = false;
    boolean snowMode = false;
    boolean fireMode = false;
    boolean sparkleMode = false;
    boolean bubbleMode = false;
    boolean neonMode = false;
    boolean neonDigital = false;
    boolean digitalNoBg = false;
    private int rainbowSeconds = 30;   // INI: rainbowSeconds (기본 30s)
	// ── Constructor (기본, ini 로드) ───────────────────────────
    public KootPanKingThreeApp() {
        this(null, "Local", java.time.ZoneId.systemDefault());
		System.out.println ("■[KootPanKingThreeApp]-0");
		System.out.println("this.cityName = [" + this.theCityName + "]");
	}
    public KootPanKingThreeApp(String configFile, String cityName, java.time.ZoneId zoneId) {
		System.out.println ("■■■■■[KootPanKingThreeApp]-1");
        this.APP_DIR = AppContext.getAPP_DIR();
        this.SETTINGS_DIR = AppContext.SETTINGS_DIR;
        this.CONFIG_FILE = AppContext.CONFIG_FILE;
        this.myConfigFile = (configFile == null || configFile.isEmpty()) ? CONFIG_FILE : configFile;
        this.theCityName = (cityName == null || cityName.isEmpty()) ? "Local" : cityName;
        this.theTimeZone = (zoneId == null) ? java.time.ZoneId.systemDefault() : zoneId;
        this.config = new Properties();
		/*
			this.appRestarter = KootPanKingThree.getAppRestarter();
			if (this.appRestarter == null) {
            throw new IllegalStateException("AppRestarter not initialized in main()");
			}
			this.appRestarter.setTelegramBot(this.tg);
		*/
        // this.shutdownGuard = new AppRestarter.ShutdownGuard(gmail, tg);
        // this.appRestarter.buildAppCdsIfNeeded(this::saveConfig);
        // this.shutdownGuard.register();
		// if (tg.polling && this.cityName == "Local" ) tg.startPolling();
		System.out.println("this.cityName = [" + this.theCityName + "]");
	}  //  KootPanKingThreeApp
	public void saveConfig() {
	}
	public void close() {
		System.out.println("[KootPanKingThreeApp.close] " + theCityName);
		try {
			// saveConfig();
			} catch (Exception e) {
			AppLogger.logException(e);
		}
		try {
			stopYoutube();
			} catch (Exception e) {
			AppLogger.logException(e);
		}
		try {
			stopItsCctv();
			} catch (Exception e) {
			AppLogger.logException(e);
		}
		try {
			stopCamera();
			} catch (Exception e) {
			AppLogger.logException(e);
		}
		try {
			if (ytPlayer != null) {
				ytPlayer.stop();
			}
			} catch (Exception e) {
			AppLogger.logException(e);
		}
		try {
			if (clockController != null && clockController.getStage() != null) {
				Stage st = clockController.getStage();
				if (st.isShowing()) st.hide();
			}
			} catch (Exception e) {
			AppLogger.logException(e);
		}
	}
    private void applyCityContextToClock(String clockPrefix) {
        if (clockController == null) return;
        FxGPUNeon.AppState st = clockController.getAppState();
        if (st == null) return;
        st.theTimeZone = (this.theTimeZone != null)
		? this.theTimeZone
		: java.time.ZoneId.systemDefault();
        if (clockPrefix != null && !clockPrefix.trim().isEmpty()) {
            st.cityPrefix = clockPrefix.trim();
			} else if (theCityName != null && !"Local".equalsIgnoreCase(theCityName.trim())) {
            st.cityPrefix = theCityName.trim();
			} else {
            st.cityPrefix = "";
		}
        this.theTimeZone = st.theTimeZone;
        this.theCityName = (st.cityPrefix == null || st.cityPrefix.trim().isEmpty())
		? "Local"
		: st.cityPrefix.trim();
        System.out.println("[applyCityContextToClock] theCityName = [" + theCityName + "]");
        System.out.println("[applyCityContextToClock] theTimeZone = [" + st.theTimeZone + "]");
        System.out.println("[applyCityContextToClock] cityPrefix = [" + st.cityPrefix + "]");
	}
	public void startInstance(Stage stage, java.util.List<String> rawArgs) {
		System.out.println ("■■■■■[startInstance] , myConfigFile = [" + myConfigFile + "]");
        String arg1 = rawArgs.size() > 0 ? rawArgs.get(0) : "default1";
        String arg2 = rawArgs.size() > 1 ? rawArgs.get(1) : "default2";
        String arg3 = rawArgs.size() > 2 ? rawArgs.get(2) : "default3";
        startArg1 = arg1; startArg2 = arg2; startArg3 = arg3;
        // ── 1. mainWindow 생성 (시계보다 먼저) ─────────────────
        // Stage mainStage = new Stage();
        // mainWindow = new MainWindow(mainStage);
        // mainWindow.log(thisProgramName + " initialization 중...");
        // ── 2. 시계 생성 ─────────────────────────────────────────
        clockController =
		fxGPUNeon.new ClockController(stage, arg1, arg2, arg3, this::addAppMenuItems);
        clockController.setIniFile(myConfigFile); // INI File 지정 → start() 에서 자동 복원
        clockController.start();
        applyCityContextToClock(null);
        // ── 3. 디지탈 시계 더블클릭 → Settings 다이얼로그 ────────────
        clockController.setOnDigitalSettingsRequest(() ->
            Platform.runLater(() ->
			showDigitalSettingsDialog(stage)));
			/*
				// ── 4. ClockHostCallback 주입 ─────────────────────────────
				mainWindow.setClockHost(new MainWindow.ClockHostCallback() {
				@Override public javafx.scene.control.Menu buildGlobalMenu() {
				return buildWorldClockMenu();
				}
				@Override public void exitAll() {
				saveConfig();
				AppLogger.close();
				Platform.exit();
				// System.exit(0);
				}
				@Override public void showLogFile() { openLogFile(); }
				@Override public void deleteOldLogs() {
				String p = AppLogger.getLogFilePath();
				if (p == null || p.isEmpty()) return;
				java.io.File dir = new java.io.File(p).getParentFile();
				if (dir == null || !dir.exists()) return;
				java.io.File cur = new java.io.File(p);
				java.io.File[] old = dir.listFiles(f ->
				f.isFile() && f.getName().endsWith(".txt")
				&& !f.getAbsolutePath().equals(cur.getAbsolutePath()));
				if (old != null) for (java.io.File f : old) f.delete();
				}
				@Override public String getLogFilePath() {
				return AppLogger.getLogFilePath();
				}
				@Override public void showConfigFile() { openConfigFile(); }
				@Override public void showAbout() {
				Platform.runLater(() -> showAboutDialog());
				}
				@Override public String getConfigFilePath() {
				return IniController.getPrimaryConfigFilePath();
				}
				@Override public void onClose() {
				config.remove("mainWindow");
				saveConfig();
				}
				@Override public void showChimeDialog() {
				if (chimeController != null) chimeController.showChimeDialog();
				}
				@Override public javafx.scene.control.Menu buildGmailCalendarMenu() {
				return buildGmailMenu();
				}
				@Override public javafx.scene.control.Menu buildKakaoMenu() {
				return buildKakaoMenuFx();
				}
				@Override public javafx.scene.control.Menu buildTelegramMenu() {
				return buildTelegramMenuFx();
				}
				@Override public String getConfig(String key, String defaultValue) {
				return config.getProperty(key, defaultValue);
				}
				@Override public void setMultipleConfigAndSave(String... entries) {
				for (int i = 0; i + 1 < entries.length; i += 2)
				config.setProperty(entries[i], entries[i + 1]);
				saveConfig();
				}
				@Override public void setConfigAndSave(String key, String value) {
				config.setProperty(key, value);
				saveConfig();
				}
				@Override public GmailSender getGmail() { return gmail; }
				@Override public void moveToTopRight() { resetToCenter(); }
				});
			*/
			if (mainWindow != null) {
				if (mainWindow != null) {
					MainWindow.log("Clock initialized.");
				}
			}
	}
    /** 앱 제어 메뉴 item을 팝업에 추가 — KootPanKingThree 전담 */
    /** 세계시계 서브메뉴 — popup과 mainWindow 양쪽에서 공유 */
    private javafx.scene.control.Menu buildWorldClockMenu() {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("🌍 World Clock");
        // {메뉴 표시명, ZoneId, 시계 날짜 prefix (이모지 없음 — Canvas 렌더 호환)}
        String[][] cities = {
            {"🇰🇷 Seoul",    "Asia/Seoul",          "Seoul"},
            {"🇯🇵 Tokyo",    "Asia/Tokyo",          "Tokyo"},
            {"🇨🇳 Beijing",  "Asia/Shanghai",       "Beijing"},
            {"🇹🇭 Bangkok",    "Asia/Bangkok",        "Bangkok"},
            {"🇮🇳 Mumbai",  "Asia/Kolkata",        "Mumbai"},
            {"🇦🇪 Dubai",  "Asia/Dubai",          "Dubai"},
            {"🇷🇺 Moscow","Europe/Moscow",        "Moscow"},
            {"🇬🇧 London",    "Europe/London",       "London"},
            {"🇫🇷 Paris",    "Europe/Paris",        "Paris"},
            {"🇩🇪 Berlin",  "Europe/Berlin",       "Berlin"},
            {"🇺🇸 New York",    "America/New_York",    "New York"},
            {"🇺🇸 Chicago",  "America/Chicago",     "Chicago"},
            {"🇺🇸 LA",      "America/Los_Angeles", "LA"},
            {"🇧🇷 São Paulo","America/Sao_Paulo",   "São Paulo"},
            {"🇦🇺 Sydney",  "Australia/Sydney",    "Sydney"},
		};
        for (String[] c : cities) {
            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(c[0]);
			String zoneStr  = c[1];
			String menuName = c[0]; // 창 제Thu용 (이모지 포함)
			String prefix   = c[2]; // 시계 날짜 prefix (한글/영문만)
            // item.setOnAction(e -> openChildClock(menuName, java.time.ZoneId.of(zoneStr), prefix));
            item.setOnAction(e -> {
				System.out.println("■■■■■ MainWindow.getInstance()");
				MainWindow mainWindow = MainWindow.getInstance();
			mainWindow.openTheCity(menuName, java.time.ZoneId.of(zoneStr), prefix);});
            menu.getItems().add(item);
		}
        return menu;
	}
    private static  String POPUP_NEON_STYLE = """
    -fx-background-color: linear-gradient(to bottom, rgba(255,105,180,0.95), rgba(255,182,193,0.92));
    -fx-border-color: #ff5fa2;
    -fx-border-width: 1.8;
    -fx-background-insets: 0;
    -fx-background-radius: 12;
    -fx-border-radius: 12;
    -fx-padding: 6;
    -fx-effect: dropshadow(gaussian, rgba(255,105,180,0.70), 24, 0.45, 0, 0);
    """;
    private static  String MENU_ITEM_NORMAL_STYLE = """
    -fx-text-fill: black;
    -fx-font-size: 14px;
    -fx-font-weight: bold;
    -fx-background-color: transparent;
    """;
    private static  String MENU_ITEM_HOVER_STYLE = """
    -fx-text-fill: black;
    -fx-font-size: 15px;
    -fx-font-weight: bold;
    -fx-background-color: linear-gradient(to right, rgba(255,255,255,0.38), rgba(255,255,255,0.12));
    -fx-effect: dropshadow(gaussian, rgba(255,255,255,0.55), 16, 0.35, 0, 0);
    """;
    private static  String MENU_ITEM_DISABLED_STYLE = """
    -fx-text-fill: rgba(0,0,0,0.65);
    -fx-font-size: 14px;
    -fx-font-weight: bold;
    -fx-background-color: transparent;
    -fx-opacity: 1.0;
    """;
    private static  String MENU_CAPTION_STYLE = """
    -fx-text-fill: black;
    -fx-font-size: 14px;
    -fx-font-weight: bold;
    """;
    private static  String MENU_TITLE_STYLE = """
    -fx-text-fill: #111111;
    -fx-font-size: 15px;
    -fx-font-weight: 900;
    -fx-background-color: linear-gradient(to right, rgba(255,255,255,0.45), rgba(255,255,255,0.18));
    -fx-background-radius: 9;
    -fx-border-color: rgba(255,255,255,0.85);
    -fx-border-radius: 9;
    -fx-padding: 6 10 6 10;
    -fx-effect: dropshadow(gaussian, rgba(255,255,255,0.55), 14, 0.30, 0, 0);
    """;
	private void enhancePopupMenuNeon(javafx.scene.control.ContextMenu popup) {
		popup.setStyle(POPUP_NEON_STYLE);
		applyNeonStylesRecursively(popup.getItems());
		popup.setOnShown(ev -> Platform.runLater(() -> {
			// disabled 항목 글자 흰색으로 강제 표시
			javafx.scene.Scene sc = popup.getScene();
			if (sc != null) {
				String css = "data:text/css," +
                java.net.URLEncoder.encode(
                    ".menu-item { -fx-opacity: 1.0; }" +
                    ".menu-item > .label { -fx-text-fill: black; }" +
                    ".menu-item:disabled { -fx-opacity: 1.0; }" +
                    ".menu-item:disabled > .label { -fx-text-fill: rgba(0,0,0,0.65); }" +
                    ".menu > .label { -fx-text-fill: black; }" +
                    ".menu > .arrow, .menu-item > .arrow { -fx-background-color: black; }",
                    java.nio.charset.StandardCharsets.UTF_8
				);
				if (!sc.getStylesheets().contains(css)) {
					sc.getStylesheets().add(css);
				}
			}
			wirePopupPulseEffects(popup);
		}));
	}
	/*
		private void enhancePopupMenuNeon(javafx.scene.control.ContextMenu popup) {
        popup.setStyle(POPUP_NEON_STYLE);
        applyNeonStylesRecursively(popup.getItems());
        popup.setOnShown(ev -> Platform.runLater(() -> wirePopupPulseEffects(popup)));
		}
	*/
    private void applyNeonStylesRecursively(java.util.List<javafx.scene.control.MenuItem> items) {
        for (javafx.scene.control.MenuItem item : items) {
            if (item == null) continue;
            applyNeonStyle(item);
            if (item instanceof javafx.scene.control.Menu subMenu) {
                subMenu.setStyle(MENU_CAPTION_STYLE);
                applyNeonStylesRecursively(subMenu.getItems());
			}
		}
	}
	private void applyNeonStyle(javafx.scene.control.MenuItem item) {
		if (item instanceof javafx.scene.control.SeparatorMenuItem) return;
		if (Boolean.TRUE.equals(item.getProperties().get("popupProgramTitle"))) return;
		// 현재 상태에 맞는 초기 스타 Apply
		item.setStyle(item.isDisable() ? MENU_ITEM_DISABLED_STYLE : MENU_ITEM_NORMAL_STYLE);
		// disable 상태가 바뀔 때마다 자동으로 스타 갱신
		item.disableProperty().addListener((obs, wasDisabled, isNowDisabled) ->
			item.setStyle(isNowDisabled ? MENU_ITEM_DISABLED_STYLE : MENU_ITEM_NORMAL_STYLE)
		);
	}
	/*
		private void applyNeonStyle(javafx.scene.control.MenuItem item) {
        if (item instanceof javafx.scene.control.SeparatorMenuItem) return;
        item.setStyle(MENU_ITEM_NORMAL_STYLE);
		}
	*/
    private void wirePopupPulseEffects(javafx.scene.control.ContextMenu popup) {
        for (javafx.scene.control.MenuItem item : popup.getItems()) {
            wireMenuItemPulseRecursively(item);
		}
	}
    private void wireMenuItemPulseRecursively(javafx.scene.control.MenuItem item) {
        if (item == null || item instanceof javafx.scene.control.SeparatorMenuItem) return;
        if (Boolean.TRUE.equals(item.getProperties().get("popupProgramTitle"))) return;
        javafx.scene.Node node = item.getStyleableNode();
        if (node != null && node.getProperties().putIfAbsent("neonPulseWired", Boolean.TRUE) == null) {
            node.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
                item.setStyle(MENU_ITEM_HOVER_STYLE);
                startPulse(item);
			});
            node.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                // item.setStyle(MENU_ITEM_NORMAL_STYLE);
				item.setStyle(item.isDisable() ? MENU_ITEM_DISABLED_STYLE : MENU_ITEM_NORMAL_STYLE);
				stopPulse(item);
			});
		}
        if (item instanceof javafx.scene.control.Menu subMenu) {
            javafx.scene.Node submenuNode = subMenu.getStyleableNode();
            if (submenuNode != null && submenuNode.getProperties().putIfAbsent("neonMenuCaptionStyled", Boolean.TRUE) == null) {
                subMenu.setStyle(MENU_CAPTION_STYLE);
			}
            for (javafx.scene.control.MenuItem child : subMenu.getItems()) {
                wireMenuItemPulseRecursively(child);
			}
		}
	}
    private void startPulse(javafx.scene.control.MenuItem item) {
        javafx.animation.ScaleTransition oldPulse =
		(javafx.animation.ScaleTransition) item.getProperties().get("pulseTransition");
        if (oldPulse != null) oldPulse.stop();
        javafx.scene.Node node = item.getStyleableNode();
        if (node == null) return;
        javafx.animation.ScaleTransition pulse =
		new javafx.animation.ScaleTransition(javafx.util.Duration.millis(560), node);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.08);
        pulse.setToY(1.08);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
        pulse.play();
        item.getProperties().put("pulseTransition", pulse);
	}
    private void stopPulse(javafx.scene.control.MenuItem item) {
        javafx.animation.ScaleTransition pulse =
		(javafx.animation.ScaleTransition) item.getProperties().get("pulseTransition");
        if (pulse != null) {
            pulse.stop();
            item.getProperties().remove("pulseTransition");
		}
        javafx.scene.Node node = item.getStyleableNode();
        if (node != null) {
            node.setScaleX(1.0);
            node.setScaleY(1.0);
		}
	}
	private void emphasizePopupProgramTitle(javafx.scene.control.ContextMenu popup) {
		if (popup == null || popup.getItems().isEmpty()) return;
		javafx.scene.control.MenuItem first = popup.getItems().get(0);
		if (first == null || first instanceof javafx.scene.control.SeparatorMenuItem) return;
		first.getProperties().put("popupProgramTitle", Boolean.TRUE);
		String title = "[" + theCityName + "] KootPanKingThree";
		first.setText(title);
		first.setStyle(MENU_TITLE_STYLE);
	}
	private void addAppMenuItems(javafx.scene.control.ContextMenu popup) {
		System.out.println ("■[addAppMenuItems]-0");
		enhancePopupMenuNeon(popup);
		restoreParentPopupTextVisible(popup);
		// ── chimeController 최초 initialization ─────────────────────────────────
		initChimeControllerIfNeeded((javafx.stage.Stage) popup.getOwnerWindow());
		// ── 서브메뉴 구성 ───────────────────────────────────────────────
		javafx.scene.control.MenuItem chimeItem    = buildChimeMenuItem();
		javafx.scene.control.Menu     phoneCam     = buildPhoneCamMenu(popup);
		javafx.scene.control.Menu     ytMenu       = buildYtMenu(popup);
		javafx.scene.control.Menu     localMp4Menu = buildLocalMp4Menu(popup);
		javafx.scene.control.Menu     cctv         = buildCctvMenu(popup);
		javafx.scene.control.Menu     gmailMenu    = buildGmailMenu(popup);
		javafx.scene.control.Menu     kakaoMenu    = buildKakaoMenu();
		javafx.scene.control.Menu     telegramMenu = buildTelegramMenu(popup);
		javafx.scene.control.Menu     lifeMenu     = buildLifeMenu(popup);
		javafx.scene.control.Menu     system       = buildSystemMenu(popup);
		javafx.scene.control.MenuItem mainWindowItem =
		new javafx.scene.control.MenuItem("🪟 MainWindow");
		mainWindowItem.setOnAction(e -> {
			System.out.println("■■■■■ MainWindow.getInstance()");
			MainWindow mainWindow = MainWindow.getInstance();
			if (mainWindow != null) mainWindow.toggleTheMainWindow();
		});
		// ── Top-5 항목 생성 (child 시계 포함 표시) ──────────────────────
		// [중앙 고정]
		javafx.scene.control.MenuItem centerItem =
		new javafx.scene.control.MenuItem("📌 Center");
		centerItem.setOnAction(e -> resetToCenter());
		// [디지탈 on/off]
		javafx.scene.control.CheckMenuItem digitalItem =
		new javafx.scene.control.CheckMenuItem("🕐 Digital on/off");
		digitalItem.setSelected(clockController != null && getDigitalState());
		digitalItem.setOnAction(e -> {
			setDigitalState(digitalItem.isSelected());
			saveConfig();
		});
		digitalMenuItem = digitalItem;
		if (clockController != null) {
			clockController.onPopupShowing = () ->
			digitalMenuItem.setSelected(getDigitalState());
		}
		// [디지탈 시계 Settings]
		javafx.scene.control.MenuItem digitalSettingsItem =
		new javafx.scene.control.MenuItem("Digital Clock Settings");
		digitalSettingsItem.setOnAction(e ->
		showDigitalSettingsDialog((javafx.stage.Stage) popup.getOwnerWindow()));
		// [메인 시계 Settings]
		javafx.scene.control.MenuItem menuSetup =
		new javafx.scene.control.MenuItem("Clock Settings");
		menuSetup.setOnAction(e -> {
			if (clockController != null) clockController.openSetup();
		});
		// ── 팝업 조립 ────────────────────────────────────────────────────
		// [흔들림] 은 FxGPUNeon.buildGraphicsMenu() 가 이미 추가함
		// Top-5 + separator
		popup.getItems().addAll(
			centerItem, digitalItem, digitalSettingsItem, menuSetup,
			new javafx.scene.control.SeparatorMenuItem()
		);
		// [세계 시계] — 로컬 시계만
		popup.getItems().addAll(
			buildWorldClockMenu(),
			new javafx.scene.control.SeparatorMenuItem()
		);
		// 미디어 배경
		popup.getItems().addAll(
			phoneCam, ytMenu, localMp4Menu, cctv,
			new javafx.scene.control.SeparatorMenuItem()
		);
		// Chime벨
		popup.getItems().addAll(
			chimeItem,
			new javafx.scene.control.SeparatorMenuItem()
		);
		// 커뮤니케이션
		popup.getItems().addAll(
			gmailMenu, kakaoMenu, telegramMenu,
			new javafx.scene.control.SeparatorMenuItem()
		);
		// 생활Tools
		popup.getItems().addAll(
			lifeMenu,
			new javafx.scene.control.SeparatorMenuItem()
		);
		// 시스템
		popup.getItems().add(system);
		// 시스템
		popup.getItems().addAll(
			new javafx.scene.control.SeparatorMenuItem(),
		mainWindowItem);
		applyNeonStylesRecursively(popup.getItems());
		restoreParentPopupTextVisible(popup);
		emphasizePopupProgramTitle(popup);
		enhancePopupSubMenusMacOnly(popup);
	}
	// ── chimeController initialization (Stage 확정 후 최초 1회) ───────────────────
	private void initChimeControllerIfNeeded(javafx.stage.Stage owner) {
		if (chimeController != null) return;
        chimeController = new ChimeController(owner, new ChimeController.HostCallback() {
			@Override public boolean isChild() { return false; }
			@Override public java.time.ZoneId getTimeZone() { return theTimeZone; }
			@Override public void startRainbow(int durationSec) {
				if (clockController != null)
				Platform.runLater(() -> clockController.startRainbow(durationSec));
			}
			// ── Video Chime벨 → BackgroundPlayer.YoutubePlayer 연결 ──
			@Override
			public BackgroundPlayer.YoutubePlayer getVideoPlayer() {
				if (ytPlayer == null) {
					ytPlayer = new BackgroundPlayer.YoutubePlayer(
						new BackgroundPlayer.YoutubePlayer.HostCallback() {
							@Override public void attachMediaView(javafx.scene.Node v) {
								if (clockController != null) clockController.attachMediaView(v);
							}
							@Override public void detachMediaView() {
								if (clockController != null) clockController.detachMediaView();
							}
							@Override public void onYoutubeFrame(javafx.scene.image.WritableImage frame) {
								fxGPUNeon.cameraActive = true;
								if (clockController != null) clockController.setCameraFrame(frame);
							}
							@Override public void clearYoutubeFrame() {
								fxGPUNeon.cameraActive = false;
								if (clockController != null) clockController.setCameraFrame(null);
							}
							@Override public void onStatusMessage(String message) {
								if (clockController != null) clockController.showStatusMessage(message);
							}
							@Override public String getSettingsDir() { return SETTINGS_DIR; }
							@Override public String getYtDlpPath()   { return AppContext.getYtdlpPath(); }
							@Override public String getFfmpegPath()  { return AppContext.getFfmpegPath(); }
						});
				}
				return ytPlayer;
			}
		});
		// loadConfig() 에서 임h 보관한 pending 값 Apply
		chimeController.setEnabled(pendingChimeEnabled);
		chimeController.setFile(pendingChimeFile);
		chimeController.setDuration(pendingChimeDuration);
		chimeController.setVolume(pendingChimeVolume);
		if (pendingChimeMinutes != null) chimeController.setMinutes(pendingChimeMinutes);
		chimeController.startCheckTimer();
		// 레인보우 인터벌 주입
		if (clockController != null) clockController.setRainbowInterval(pendingRainbowIntervalSec);
		// ── 시분초 / 날짜 pending → AppState 반영 ───────────────
		if (clockController != null) {
			FxGPUNeon.AppState st = clockController.getAppState();
			// 날짜·시분초는 개별 Settings값으로 독립 반영 (master switch Enable 금지)
			st.showDigital  = pendingDigitalShow;
			st.showFaceDate = pendingFaceDateShow;
			st.digitalFormatIndex = pendingDigitalFormatIndex;
			st.digitalFontFamily  = pendingDigitalFontFamily;
			st.digitalFontSize    = pendingDigitalFontSize;
			st.digitalColorRgb    = pendingDigitalColorRgb;
			st.digitalScrollDir   = pendingDigitalScrollDir;
			st.digitalScrollSpeed = pendingDigitalScrollSpeed;
			st.faceDateFormatIndex= pendingFaceDateFormatIndex;
			st.faceDateFontFamily = pendingFaceDateFontFamily;
			st.faceDateFontSize   = pendingFaceDateFontSize;
			st.faceDateColorRgb   = pendingFaceDateColorRgb;
			st.faceDateScrollDir   = pendingFaceDateScrollDir;
			st.faceDateScrollSpeed = pendingFaceDateScrollSpeed;
			// 상태 Apply 후 씬 재빌드
			clockController.applyDigitalSettings();
			// 팝업 메뉴 체크 상태 동기화
			if (digitalMenuItem != null)
			digitalMenuItem.setSelected(getDigitalState());
		}
	}
	// ── Chime벨 메뉴 아이템 ──────────────────────────────────────────────
	private javafx.scene.control.MenuItem buildChimeMenuItem() {
        javafx.scene.control.MenuItem chimeItem =
		new javafx.scene.control.MenuItem("🔔 Chime Settings...");
        chimeItem.setOnAction(e -> {
            // MainWindow의 Chime벨 다이얼로그 Enable (마스터 Settings 공유)
            MainWindow mw = MainWindow.getInstance();
            if (mw != null) {
                mw.showChimeDialogPublic((javafx.stage.Stage) chimeItem.getParentPopup().getOwnerWindow());
				} else if (chimeController != null) {
                // fallback: 자체 컨트롤러 (자식 시계 등 MainWindow 없을 때)
                chimeController.showChimeDialog();
			}
		});
        javafx.scene.control.Menu phoneCam = new javafx.scene.control.Menu("📷 Phone Camera");
		return chimeItem;
	}
	// ── YouTube 실시간 세계도h 메뉴 ─────────────────────────────────────
	private javafx.scene.control.Menu buildYtMenu(
		javafx.scene.control.ContextMenu popup) {
        javafx.scene.control.Menu ytMenu = new javafx.scene.control.Menu("▶ YouTube Live World Cities");
        java.util.List<String[]> ytList = BackgroundPlayer.YoutubePlayer.loadStreamIni(SETTINGS_DIR);
        if (ytList.isEmpty()) {
            javafx.scene.control.MenuItem emptyItem =
			new javafx.scene.control.MenuItem("(No list — check youTubeCctv.ini)");
            emptyItem.setDisable(true);
            ytMenu.getItems().add(emptyItem);
			} else {
            for (String[] entry : ytList) {
                String city   = entry[0];
                String url    = entry[1];
                javafx.scene.control.MenuItem cityItem =
				new javafx.scene.control.MenuItem(city);
                cityItem.setOnAction(ev -> {
                    // exe Path 미Settings h → 메시지만 표시Other고 종료
                    String ytdlp  = AppContext.getYtdlpPath();
                    String ffmpeg = AppContext.getFfmpegPath();
                    if (ytdlp.isEmpty() || !new java.io.File(ytdlp).exists()
						|| ffmpeg.isEmpty() || !new java.io.File(ffmpeg).exists()) {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
						javafx.scene.control.Alert.AlertType.INFORMATION);
                        alert.setTitle("YouTube Setup Required");
                        alert.setHeaderText(null);
                        alert.setContentText(
                            "In [YouTube Stream URL & Settings] menu,\n" +
						"please set the paths for (yt-dlp.exe) and (ffmpeg.exe).");
                        alert.initOwner((javafx.stage.Stage) popup.getOwnerWindow());
                        alert.showAndWait();
                        return;
					}
                    AppContext.setYoutubeUrl(url);
                    stopCamera(); stopItsCctv();
                    startYoutube(url);
                    saveConfig();
				});
                ytMenu.getItems().add(cityItem);
			}
		}
        ytMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        javafx.scene.control.MenuItem ytUrlItem =
		new javafx.scene.control.MenuItem("🔗 Direct URL & YouTube Settings");
        ytUrlItem.setOnAction(ev -> {
            javafx.stage.Stage owner = (javafx.stage.Stage) popup.getOwnerWindow();
            showYoutubeSettingsDialog(owner);
		});
        javafx.scene.control.MenuItem ytDlItem =
		new javafx.scene.control.MenuItem("⬇ Download City List");
        ytDlItem.setOnAction(ev ->
            new Thread(() -> {
                BackgroundPlayer.YoutubePlayer.downloadIni(SETTINGS_DIR);
                Platform.runLater(() -> {
                    javafx.scene.control.Alert a = new javafx.scene.control.Alert(
					javafx.scene.control.Alert.AlertType.INFORMATION);
                    a.setTitle("Download List");
                    a.setContentText("youTubeCctv.ini downloaded.\nReopen menu to apply.");
                    a.initOwner((javafx.stage.Stage) popup.getOwnerWindow());
                    a.showAndWait();
				});
			}, "YT-INI-Download").start()
		);
        javafx.scene.control.MenuItem ytStopItem =
		new javafx.scene.control.MenuItem("⏹ Stop Stream");
        ytStopItem.setOnAction(ev -> stopYoutube());
        ytMenu.getItems().addAll(ytUrlItem, ytDlItem,
		new javafx.scene.control.SeparatorMenuItem(), ytStopItem);
        ytMenu.setOnShowing(ev ->
		ytStopItem.setDisable(ytPlayer == null || !ytPlayer.isRunning()));
		return ytMenu;
	}
	// ── 로컬 MP4 배경 재생 메뉴 ──────────────────────────────────────────
	private javafx.scene.control.Menu buildLocalMp4Menu(
		javafx.scene.control.ContextMenu popup) {
        javafx.scene.control.Menu localMp4Menu =
		new javafx.scene.control.Menu("📂 Local MP4 Background");
        javafx.scene.control.MenuItem mp4OpenItem =
		new javafx.scene.control.MenuItem("📁 Select File...");
        mp4OpenItem.setOnAction(ev -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Select video file for background");
            fc.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter(
				"Video Files", "*.mp4", "*.m4v", "*.mov", "*.mkv", "*.avi", "*.webm"),
                new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
			);
            // 마지막 Path 기억
            if (!localMp4LastFile.isEmpty()) {
                File lastDir = new File(localMp4LastFile).getParentFile();
                if (lastDir != null && lastDir.exists())
				fc.setInitialDirectory(lastDir);
			}
            javafx.stage.Stage owner = (javafx.stage.Stage) popup.getOwnerWindow();
            File chosen = fc.showOpenDialog(owner);
            if (chosen != null) {
                startLocalMp4(chosen);
			}
		});
        javafx.scene.control.MenuItem mp4ReplayItem =
		new javafx.scene.control.MenuItem("🔄 Replay Last File");
        mp4ReplayItem.setOnAction(ev -> {
            if (localMp4LastFile.isEmpty()) return;
            File f = new File(localMp4LastFile);
            if (!f.exists()) {
                javafx.scene.control.Alert a = new javafx.scene.control.Alert(
				javafx.scene.control.Alert.AlertType.WARNING);
                a.setTitle("File Not Found");
                a.setContentText("Cannot find last file:\n" + localMp4LastFile);
                a.initOwner((javafx.stage.Stage) popup.getOwnerWindow());
                a.showAndWait();
                return;
			}
            startLocalMp4(f);
		});
        javafx.scene.control.MenuItem mp4StopItem =
		new javafx.scene.control.MenuItem("⏹ Stop MP4");
        mp4StopItem.setOnAction(ev -> stopLocalMp4());
        // ── 볼륨 슬라이더 ──────────────────────────────────────────
        javafx.scene.control.Label volLabel =
		new javafx.scene.control.Label(
		String.format("🔊 Volume: %d%%", (int)(localMp4Volume * 100)));
        volLabel.setStyle("-fx-font-size:12px; -fx-font-weight:bold;");
        javafx.scene.control.Slider volSlider =
		new javafx.scene.control.Slider(0.0, 1.0, localMp4Volume);
        volSlider.setShowTickMarks(true);
        volSlider.setShowTickLabels(true);
        volSlider.setMajorTickUnit(0.5);
        volSlider.setMinorTickCount(4);
        volSlider.setPrefWidth(200);
        volSlider.valueProperty().addListener((obs, ov, nv) -> {
            localMp4Volume = nv.doubleValue();
            volLabel.setText(String.format("🔊 Volume: %d%%", (int)(localMp4Volume * 100)));
            if (ytPlayer != null) ytPlayer.setVolume(localMp4Volume);
		});
        // 🔇 음소거 토글 버튼
        javafx.scene.control.Button muteBtn = new javafx.scene.control.Button("🔇 Mute");
        muteBtn.setStyle("-fx-font-size:11px;");
        muteBtn.setOnAction(ev -> {
            if (localMp4Volume > 0.0) {
                // 현재 볼륨 Save 후 0으로
                volSlider.setValue(0.0);
				} else {
                // 음소거 해제 → 80% 복원
                volSlider.setValue(0.8);
			}
		});
        javafx.scene.layout.VBox volBox = new javafx.scene.layout.VBox(4,
		volLabel, volSlider, muteBtn);
        volBox.setPadding(new javafx.geometry.Insets(6, 14, 6, 14));
        javafx.scene.control.CustomMenuItem volItem =
		new javafx.scene.control.CustomMenuItem(volBox, false); // false = 드래그 중 메뉴 유지
        localMp4Menu.getItems().addAll(
            mp4OpenItem, mp4ReplayItem,
            new javafx.scene.control.SeparatorMenuItem(),
            volItem,
            new javafx.scene.control.SeparatorMenuItem(),
            mp4StopItem
		);
        localMp4Menu.setOnShowing(ev -> {
            mp4ReplayItem.setDisable(localMp4LastFile.isEmpty());
            mp4StopItem.setDisable(ytPlayer == null || !ytPlayer.isRunning());
		});
		return localMp4Menu;
	}
	// ── 스마트폰 카메라 메뉴 ────────────────────────────────────────────
	private javafx.scene.control.Menu buildPhoneCamMenu(
		javafx.scene.control.ContextMenu popup) {
        javafx.scene.control.Menu phoneCam = new javafx.scene.control.Menu("📷 Phone Camera");
        javafx.scene.control.MenuItem camStart      = new javafx.scene.control.MenuItem("▶ Connect Phone Camera");
        javafx.scene.control.MenuItem camSnapshot   = new javafx.scene.control.MenuItem("📸 Save Image");
        camVideoItem = new javafx.scene.control.MenuItem("🎬 Start Video Recording");
        javafx.scene.control.MenuItem camCapture    = camVideoItem; // 별칭 (Other위 코드 호환)
        javafx.scene.control.MenuItem camStop       = new javafx.scene.control.MenuItem("⏹ Stop Phone Camera");
        // 초기 activation 상태: 연결 중 때만 Image Save/중지 activation
        camSnapshot .setDisable(!cameraMode);
        camVideoItem.setDisable(!cameraMode);
        camStop     .setDisable(!cameraMode);
        camStart.setOnAction(e -> {
            // FX 스레드에서 커스텀 카메라 Settings 다이얼로그 표시
            javafx.stage.Stage owner = (javafx.stage.Stage) popup.getOwnerWindow();
            javafx.stage.Stage dlg = new javafx.stage.Stage();
            dlg.initOwner(owner);
            dlg.initStyle(javafx.stage.StageStyle.UTILITY);
            dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dlg.setAlwaysOnTop(true);
            dlg.setTitle("Phone Camera Settings");
            // Info Text
            javafx.scene.control.Label header = new javafx.scene.control.Label(
                "1. Launch [IP Webcam] app on your phone\n" +
                "2. Scroll to the bottom of the app\n" +
                "3. Tap [Start Server]\n" +
			"4. Enter the displayed address  Yes: http://192.168.0.70:8080");
            header.setStyle("-fx-font-size:12px;");
            // 주소 입력 필드
            javafx.scene.control.Label urlLabel = new javafx.scene.control.Label("URL:");
            javafx.scene.control.TextField urlField = new javafx.scene.control.TextField(AppContext.getCameraUrl());
            urlField.setPrefWidth(320);
            javafx.scene.layout.HBox urlRow = new javafx.scene.layout.HBox(8, urlLabel, urlField);
            urlRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            // 반전 체크박스
            javafx.scene.control.CheckBox cbFlipH = new javafx.scene.control.CheckBox("Flip Horizontal");
            cbFlipH.setSelected(cameraFlipH);
            javafx.scene.control.CheckBox cbFlipV = new javafx.scene.control.CheckBox("Flip Vertical");
            cbFlipV.setSelected(cameraFlipV);
            javafx.scene.layout.HBox flipRow = new javafx.scene.layout.HBox(16, cbFlipH, cbFlipV);
            flipRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            // ── Video Tools(FFmpeg) 지정 행 ──────────────────────
            javafx.scene.control.Label ffmpegLabel = new javafx.scene.control.Label(
			"Video tool (ffmpeg.path): " + (AppContext.getFfmpegPath().isEmpty() ? "(not set)" : AppContext.getFfmpegPath()));
            ffmpegLabel.setStyle("-fx-font-size:11px; -fx-text-fill:#555;");
            ffmpegLabel.setWrapText(true);
            ffmpegLabel.setMaxWidth(380);
            javafx.scene.control.Button btnFfmpeg = new javafx.scene.control.Button("Set Video Tool");
            btnFfmpeg.setOnAction(ev -> {
                javafx.stage.FileChooser ffChooser = new javafx.stage.FileChooser();
                ffChooser.setTitle("Select ffmpeg.exe — saved to ffmpeg.path");
                boolean isWin2 = System.getProperty("os.name", "").toLowerCase().contains("win");
                ffChooser.getExtensionFilters().addAll(
                    new javafx.stage.FileChooser.ExtensionFilter(
					isWin2 ? "ffmpeg (ffmpeg.exe)" : "ffmpeg", isWin2 ? "ffmpeg.exe" : "ffmpeg"),
				new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*"));
                // 초기 디렉터리: 기존 Path 부모 → C:fmpegin → user.home
                File initFf = AppContext.getFfmpegPath().isEmpty() ? null : new File(AppContext.getFfmpegPath()).getParentFile();
                if (initFf == null || !initFf.exists())
				initFf = isWin2 ? new File("C:\\ffmpeg\\bin") : new File("/usr/local/bin");
                if (initFf == null || !initFf.exists())
				initFf = new File(System.getProperty("user.home"));
                ffChooser.setInitialDirectory(initFf.exists() ? initFf : null);
                File chFf = ffChooser.showOpenDialog(dlg);
                if (chFf != null && chFf.isFile()) {
                    AppContext.setFfmpegPath(chFf.getAbsolutePath());
                    ffmpegLabel.setText("Video tool (ffmpeg.path): " + AppContext.getFfmpegPath());
                    System.out.println("[FFmpeg] set Done: " + AppContext.getFfmpegPath());
				}
			});
            javafx.scene.layout.HBox ffmpegRow = new javafx.scene.layout.HBox(8, btnFfmpeg, ffmpegLabel);
            ffmpegRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            // 확인 / 취소 버튼
            javafx.scene.control.Button btnOk     = new javafx.scene.control.Button("OK");
            javafx.scene.control.Button btnCancel = new javafx.scene.control.Button("Cancel");
            btnOk.setDefaultButton(true);
            btnCancel.setCancelButton(true);
            javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(10, btnOk, btnCancel);
            btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10,
			header, urlRow, flipRow, ffmpegRow, btnRow);
            root.setPadding(new javafx.geometry.Insets(16));
            root.setStyle("-fx-background-color: white;");
            dlg.setScene(new javafx.scene.Scene(root));
            dlg.sizeToScene();
            btnCancel.setOnAction(ev -> dlg.close());
            btnOk.setOnAction(ev -> {
                String input = urlField.getText();
                if (input == null || input.trim().isEmpty()) { dlg.close(); return; }
                String url = input.trim();
                if (!url.contains("/mjpeg")) url = url.replaceAll("/+$", "");
                AppContext.setCameraUrl(url);
                cameraFlipH = cbFlipH.isSelected();
                cameraFlipV = cbFlipV.isSelected();
                // 반전 플래그를 FxGPUNeon 에 전달
                fxGPUNeon.cameraFlipH = cameraFlipH;
                fxGPUNeon.cameraFlipV = cameraFlipV;
                saveConfig();
                dlg.close();
                stopCamera();
                startCamera(url);
                camSnapshot .setDisable(false);
                camVideoItem.setDisable(false);
                camStop     .setDisable(false);
			});
            dlg.showAndWait();
		});
        // ── Image Save (단 프레임 스냅샷) ──────────────────────────
        camSnapshot.setOnAction(e -> {
            if (!cameraMode) {
                showAlert("Save Image", "Camera is not running.");
                return;
			}
            captureCamera((javafx.stage.Stage) popup.getOwnerWindow());
		});
        // ── Video 녹화 시작 / 중지 토글 ──────────────────────────────
        camVideoItem.setOnAction(e -> {
            if (!cameraMode) {
                showAlert("Video Recording", "Camera is not running.");
                return;
			}
            if (videoRecording) {
                // MP4 녹화 중 → 중지
                stopVideoRecording();
				} else if (imageSeqRecording) {
                // Image 시퀀스 Save 중 → 중지
                stopImageSequenceRecording();
				} else {
                // 녹화 Start: Save Path Select 다이얼로그
                javafx.stage.Stage owner = (javafx.stage.Stage) popup.getOwnerWindow();
                javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
                fc.setTitle("Select video save location");
                fc.getExtensionFilters().add(
				new javafx.stage.FileChooser.ExtensionFilter("MP4 Video", "*.mp4"));
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                fc.setInitialFileName("cam_" + ts + ".mp4");
                fc.setInitialDirectory(new File(AppContext.getAPP_DIR()));
                File chosen = fc.showSaveDialog(owner);
                if (chosen != null) {
                    startVideoRecording(chosen);
				}
			}
		});
        camStop.setOnAction(e -> {
            stopVideoRecording();
            stopImageSequenceRecording();
            stopCamera();
            camSnapshot .setDisable(true);
            camVideoItem.setDisable(true);
            camStop     .setDisable(true);
            camVideoItem.setText("🎬 Start Video Recording");
		});
        javafx.scene.control.MenuItem camInstall = new javafx.scene.control.MenuItem("📲 Install IP WebCam (Play Store)");
        camInstall.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(
				"https://play.google.com/store/apps/details?id=com.pas.webcam&pcampaignid=web_share"));
				} catch (Exception ex) {
                System.out.println("[CameraInstall] failed to open browser: " + ex.getMessage());
			}
		});
        javafx.scene.control.MenuItem camGuide = new javafx.scene.control.MenuItem("📖 Usage Guide");
        camGuide.setOnAction(e -> {
			String GUIDE_URL     = "https://blog.naver.com/garpsu/224213426659";
			String GUIDE_MSG     = "How to connect PC and phone camera: " + GUIDE_URL;
			String GUIDE_SUBJECT = "[KootPanKing] PC & Phone Camera Connection Guide";
            // ① 브라우저 열기
            new Thread(() -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(GUIDE_URL));
					} catch (Exception ex) {
                    System.out.println("[CameraGuide] failed to open browser: " + ex.getMessage());
				}
			}, "CameraGuide-Browser").start();
            // ② 텔레그램 전송
            // boolean tgOk = tg.polling && !tg.botToken.isEmpty() && !tg.myChatId.isEmpty();
            boolean tgOk = tg.polling ;
            if (tgOk) {
                new Thread(() -> {
                    // try { tg.sendTelegram(tg.myChatId, GUIDE_MSG);
                    try { tg.sendTelegram( GUIDE_MSG);
                        System.out.println("[CameraGuide] Telegram send done");
						} catch (Exception ex) {
                        System.out.println("[CameraGuide] Telegram send Failed: " + ex.getMessage());
					}
				}, "CameraGuide-Telegram").start();
			}
            // ③ 이Sending email
            boolean emailOk = gmail.isConfigured() && !gmail.lastTo.isEmpty();
            if (emailOk) {
                new Thread(() -> {
                    try { gmail.sendOneSmtp(gmail.lastTo, GUIDE_SUBJECT, GUIDE_MSG);
						System.out.println("[CameraGuide] email send Done");
						} catch (Exception ex) {
                        System.out.println("[CameraGuide] email send Failed: " + ex.getMessage());
						AppLogger.logException(ex);
					}
				}, "CameraGuide-Email").start();
			}
		});
        phoneCam.getItems().addAll(
            camStart, camSnapshot, camVideoItem, camStop,
            new javafx.scene.control.SeparatorMenuItem(),
            camInstall,
            new javafx.scene.control.SeparatorMenuItem(),
            camGuide
		);
		return phoneCam;
	}
	// ── ITS 교통 CCTV 메뉴 ──────────────────────────────────────────────
	private javafx.scene.control.Menu buildCctvMenu(
		javafx.scene.control.ContextMenu popup) {
        javafx.scene.control.Menu cctv = new javafx.scene.control.Menu("🚦 ITS Traffic CCTV");
        // ── (A) API 키 Settings ──────────────────────────────────────────────
        javafx.scene.control.MenuItem cctvKeyItem =
		new javafx.scene.control.MenuItem("🔑 API Key Settings...");
        cctvKeyItem.setOnAction(e -> {
			ItsCctvManager mgr = getItsCctv();
            String cur = AppContext.getItsCctvApiKey();
            javafx.scene.control.TextField keyField =
			new javafx.scene.control.TextField(cur);
            keyField.setPrefColumnCount(36);
            javafx.scene.control.Button fetchBtn =
			new javafx.scene.control.Button("🌐 Fetch Key from Server");
            fetchBtn.setOnAction(ev -> {
                fetchBtn.setDisable(true);
                fetchBtn.setText("⏳ Fetching...");
                new Thread(() -> {
                    try {
                        @SuppressWarnings("deprecation")
                        java.net.URL dlUrl = new java.net.URL(
						"https://raw.githubusercontent.com/GarpsuKim/KootPanKing/main/INI_bak/ITS_API_KEY.txt");
                        try (java.io.BufferedReader br2 = new java.io.BufferedReader(
						new java.io.InputStreamReader(dlUrl.openStream()))) {
						String line2 = br2.readLine();
						if (line2 != null) {
							String key = line2.trim();
							Platform.runLater(() -> keyField.setText(key));
						}
                        }
                        Platform.runLater(() -> fetchBtn.setText("✅ Received"));
						} catch (Exception ex) {
                        Platform.runLater(() -> {
                            fetchBtn.setText("❌ Fetch failed");
                            fetchBtn.setDisable(false);
						});
					}
				}, "ItsKeyFetch").start();
			});
            javafx.scene.control.Label label = new javafx.scene.control.Label(
                "ITS Korea Traffic API Key\n" +
                "Apply: https://www.its.go.kr\n" +
                "Register → Open Data → CCTV → Apply for API key\n" +
                "Current key: " + (cur.isEmpty() ? "(none)"
				: cur.substring(0, Math.min(8, cur.length())) + "..."));
				label.setWrapText(true);
				javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8,
				label, keyField, fetchBtn);
				content.setPadding(new javafx.geometry.Insets(10));
				javafx.scene.control.Dialog<String> dlg = new javafx.scene.control.Dialog<>();
				dlg.setTitle("ITS API Key Settings");
				dlg.getDialogPane().setContent(content);
				dlg.getDialogPane().getButtonTypes().addAll(
					javafx.scene.control.ButtonType.OK,
				javafx.scene.control.ButtonType.CANCEL);
				javafx.stage.Stage owner = (javafx.stage.Stage) popup.getOwnerWindow();
				if (owner != null) dlg.initOwner(owner);
				dlg.setResultConverter(bt -> {
					if (bt == javafx.scene.control.ButtonType.OK)
                    return keyField.getText().trim();
					return null;
				});
				dlg.showAndWait().ifPresent(input -> {
					AppContext.setItsCctvApiKey(input);
					mgr.setApiKey(input);
					javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
					javafx.scene.control.Alert.AlertType.INFORMATION);
					alert.setTitle("ITS CCTV");
					alert.setHeaderText(null);
					alert.setContentText("API key saved.");
					if (owner != null) alert.initOwner(owner);
					alert.showAndWait();
				});
		});
        // ── (B) 목록 조회 및 연결 ────────────────────────────────────────
        javafx.scene.control.MenuItem cctvConnectItem =
		new javafx.scene.control.MenuItem("▶ Browse & Connect CCTV");
        cctvConnectItem.setDisable(AppContext.getItsCctvApiKey().isEmpty());
        cctvConnectItem.setOnAction(e ->
			{
				ItsCctvManager mgr = getItsCctv();
				if (AppContext.getItsCctvApiKey().isEmpty()) {
					javafx.scene.control.Alert warn = new javafx.scene.control.Alert(
					javafx.scene.control.Alert.AlertType.WARNING);
					warn.setTitle("ITS CCTV");
					warn.setContentText("Please set the API key first.");
					warn.showAndWait();
					return;
				}
				cctvConnectItem.setDisable(true);
				cctvConnectItem.setText("⏳ Loading...");
				mgr.fetchList(
					() -> {
						cctvConnectItem.setDisable(false);
						cctvConnectItem.setText("▶ Browse & Connect CCTV");
						java.util.List<ItsCctvManager.CctvItem> fetched = mgr.getItems();
						if (fetched.isEmpty()) {
							javafx.scene.control.Alert warn = new javafx.scene.control.Alert(
							javafx.scene.control.Alert.AlertType.WARNING);
							warn.setTitle("ITS CCTV");
							warn.setContentText("No CCTV found.");
							warn.showAndWait();
							return;
						}
						javafx.stage.Stage owner = (javafx.stage.Stage) popup.getOwnerWindow();
						ItsCctvManager.CctvItem selected =
						showCctvSelectDialog(owner, fetched, mgr);
						if (selected == null) return;
						int idx = fetched.indexOf(selected);
						stopCamera();
						startItsCctv();
						mgr.select(idx);
						saveConfig();
					},
					err -> {
						cctvConnectItem.setDisable(false);
						cctvConnectItem.setText("▶ Browse & Connect CCTV");
						javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
						javafx.scene.control.Alert.AlertType.ERROR);
						alert.setTitle("ITS CCTV Error");
						alert.setContentText("CCTV query failed:\n" + err
						+ "\n\nCheck your API key and network connection.");
						alert.showAndWait();
					}
				);
			}
		);
        // ── (C) 이전 / 다음 카메라 ──────────────────────────────────────
        javafx.scene.control.MenuItem cctvPrev =
		new javafx.scene.control.MenuItem("◀ Previous Camera");
        cctvPrev.setOnAction(e -> {
            ItsCctvManager mgr = getItsCctv();
            if (mgr.isRunning() && !mgr.getItems().isEmpty()) {
                mgr.prev();
                System.out.println("[ItsCctv] Previous: " + mgr.currentName());
			}
		});
        javafx.scene.control.MenuItem cctvNext =
		new javafx.scene.control.MenuItem("▶ Next Camera");
        cctvNext.setOnAction(e -> {
            ItsCctvManager mgr = getItsCctv();
            if (mgr.isRunning() && !mgr.getItems().isEmpty()) {
                mgr.next();
                System.out.println("[ItsCctv] Next: " + mgr.currentName());
			}
		});
        // ── (D) 중지 ────────────────────────────────────────────────────
        javafx.scene.control.MenuItem cctvStop =
		new javafx.scene.control.MenuItem("⏹ Stop CCTV");
        cctvStop.setOnAction(e -> stopItsCctv());
        // ── (E) ITS API 신청 Info ────────────────────────────────────────
        javafx.scene.control.MenuItem cctvGuide =
		new javafx.scene.control.MenuItem("📖 ITS API Application Guide");
        cctvGuide.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(
				new java.net.URI("https://www.its.go.kr/opendata/opendataList?service=cctv"));
				} catch (Exception ex) {
                System.out.println("[ItsCctv] failed to open browser: " + ex.getMessage());
			}
		});
        cctv.setOnShowing(e -> {
            ItsCctvManager mgr = getItsCctv();
            boolean running = mgr.isRunning();
            boolean hasItems = !mgr.getItems().isEmpty();
            cctvConnectItem.setDisable(AppContext.getItsCctvApiKey().isEmpty());
            cctvPrev.setDisable(!(running && hasItems));
            cctvNext.setDisable(!(running && hasItems));
            cctvStop.setDisable(!running);
		});
        cctv.getItems().addAll(
            cctvKeyItem,
            new javafx.scene.control.SeparatorMenuItem(),
            cctvConnectItem,
            cctvPrev,
            cctvNext,
            new javafx.scene.control.SeparatorMenuItem(),
            cctvStop,
            new javafx.scene.control.SeparatorMenuItem(),
            cctvGuide
		);
		return cctv;
	}
	// ── Gmail / Calendar 메뉴 ────────────────────────────────────────────
	private javafx.scene.control.Menu buildGmailMenu(
		javafx.scene.control.ContextMenu popup) {
        javafx.scene.control.Menu gmailMenu = new javafx.scene.control.Menu("📧 Gmail / Calendar");
        // Gmail Settings → MainWindow 다이얼로그
        javafx.scene.control.MenuItem gmailSettings = new javafx.scene.control.MenuItem("Gmail Settings");
        gmailSettings.setOnAction(e -> {
            MainWindow mw = MainWindow.getInstance();
            if (mw != null) mw.showGmailSetupDialog((javafx.stage.Stage) popup.getOwnerWindow());
		});
        javafx.scene.control.MenuItem calGuide = menuItem("Calendar Setup Guide");
        // 구글 캘린더 서브메뉴
        javafx.scene.control.Menu googleCal = new javafx.scene.control.Menu("📧 Google Calendar");
        googleCal.getItems().addAll(
            calMenuAction("Next 3 days", () -> showCalendarResult("Google", "google", 3, "next")),
            calMenuAction("Next 7 days", () -> showCalendarResult("Google", "google", 7, "next")),
            calMenuAction("Past 7 days", () -> showCalendarResult("Google", "google", 7, "past")),
            calMenuAction("This month",  () -> showCalendarResult("Google", "google", 0, "month")),
            calMenuAction("Next month",  () -> showCalendarResult("Google", "google", 0, "nextmonth"))
		);
        // 네이버 캘린더 서브메뉴
        javafx.scene.control.Menu naverCal = new javafx.scene.control.Menu("🟢 Naver Calendar");
        naverCal.getItems().addAll(
            calMenuAction("Next 3 days", () -> showCalendarResult("Naver", "naver", 3, "next")),
            calMenuAction("Next 7 days", () -> showCalendarResult("Naver", "naver", 7, "next")),
            calMenuAction("Past 7 days", () -> showCalendarResult("Naver", "naver", 7, "past")),
            calMenuAction("This month",  () -> showCalendarResult("Naver", "naver", 0, "month")),
            calMenuAction("Next month",  () -> showCalendarResult("Naver", "naver", 0, "nextmonth"))
		);
        // 네이버 Settings → MainWindow 다이얼로그
        javafx.scene.control.MenuItem naverSettings = new javafx.scene.control.MenuItem("Naver Settings");
        naverSettings.setOnAction(e -> {
            MainWindow mw = MainWindow.getInstance();
            if (mw != null) mw.showNaverSettingsDialog((javafx.stage.Stage) popup.getOwnerWindow());
		});
        gmailMenu.getItems().addAll(
            gmailSettings,
            calGuide,
            new javafx.scene.control.SeparatorMenuItem(),
            googleCal,
            new javafx.scene.control.SeparatorMenuItem(),
            naverCal,
            new javafx.scene.control.SeparatorMenuItem(),
            naverSettings
		);
		return gmailMenu;
	}
	// ── 카카오톡 메뉴 ────────────────────────────────────────────────────
	private javafx.scene.control.Menu buildKakaoMenu() {
        javafx.scene.control.Menu kakaoMenu = new javafx.scene.control.Menu("KakaoTalk...");
        kakaoMenu.getItems().addAll(
            menuItem("KakaoTalk Logged in"),
            menuItem("Send message to myself..."),
            menuItem("Setup Guide...")
		);
		return kakaoMenu;
	}
	// ── 텔레그램 메뉴 ────────────────────────────────────────────────────
	private javafx.scene.control.Menu buildTelegramMenu(
		javafx.scene.control.ContextMenu popup) {
        javafx.scene.control.Menu telegramMenu = new javafx.scene.control.Menu("Telegram");
        javafx.scene.control.MenuItem tgSettings = new javafx.scene.control.MenuItem("Telegram Settings");
        javafx.scene.control.MenuItem tgHelp     = new javafx.scene.control.MenuItem("Telegram Setup Guide");
        tgSettings.setOnAction(e -> {
            MainWindow mw = MainWindow.getInstance();
            if (mw != null) mw.showTelegramSettingsDialog((javafx.stage.Stage) popup.getOwnerWindow());
            else if (tg != null) tg.showTelegramDialog((javafx.stage.Stage) popup.getOwnerWindow());
		});
        tgHelp.setOnAction(e -> tg.showTelegramHelp((javafx.stage.Stage) popup.getOwnerWindow()));
        telegramMenu.getItems().addAll(tgSettings, tgHelp);
		return telegramMenu;
	}
	// ── 시스템 메뉴 ─────────────────────────────────────────────────────
	private javafx.scene.control.Menu buildSystemMenu(
		javafx.scene.control.ContextMenu popup) {
        javafx.scene.control.Menu system = new javafx.scene.control.Menu("System...");
        javafx.scene.control.MenuItem logItem = new javafx.scene.control.MenuItem("Log");
        logItem.setOnAction(e -> openLogFile());
        javafx.scene.control.MenuItem configItem = new javafx.scene.control.MenuItem("[" + theCityName + "] Edit Config File");
		// configItem.setOnAction(e -> AppContext.openCONFIG_FILE(myConfigFile));
		configItem.setOnAction(e -> {	MainWindow.doShowConfigFile(myConfigFile);	});
		javafx.scene.control.MenuItem iniCopyItem =
        new javafx.scene.control.MenuItem("[" + theCityName + "] Copy Config");
		iniCopyItem.setOnAction(e -> copyIniFile());
		/*
			javafx.scene.control.MenuItem iniItem = new javafx.scene.control.MenuItem("Download ini");
			iniItem.setOnAction(e -> downloadIniFile());
		*/
        // ── 부팅 자동 실행 (CheckMenuItem) ──────────────────────
		/*
			javafx.scene.control.CheckMenuItem autoStartItem =
			new javafx.scene.control.CheckMenuItem("Auto-start on boot");
			autoStartItem.setSelected(isAutoStartRegistered());
			autoStartItem.setOnAction(e -> toggleAutoStart(autoStartItem,
			(javafx.stage.Stage) popup.getOwnerWindow()));
		*/
        // ── EXIT (15초 타이머 확인 다이얼로그) ───────────────────
        javafx.scene.control.MenuItem exitItem = new javafx.scene.control.MenuItem("EXIT");
        exitItem.setOnAction(e -> showExitDialog((javafx.stage.Stage) popup.getOwnerWindow()));
        javafx.scene.control.MenuItem aboutItem = new javafx.scene.control.MenuItem("About");
        aboutItem.setOnAction(e -> showAboutDialog());
        javafx.scene.control.MenuItem mainWindowItem =
		new javafx.scene.control.MenuItem("MainWindow");
        mainWindowItem.setOnAction(e -> {
			System.out.println("■■■■■ MainWindow.getInstance()");
			MainWindow mainWindow = MainWindow.getInstance();
			if ( mainWindow != null )  mainWindow.toggleTheMainWindow();
		});
        javafx.scene.control.MenuItem closeItem = new javafx.scene.control.MenuItem("Close");
		closeItem.setOnAction(e -> {
			System.out.println("[clockController.close] : " + theCityName);
			MainWindow mw = MainWindow.getInstance();
			if (mw != null && theTimeZone != null) {
				mw.closeTheCity(theTimeZone);
				return;
			}
			if (clockController != null && clockController.getStage() != null) {
				clockController.getStage().close();
			}
		});
		//   closeItem.setOnAction(e -> {
		//          System.out.println("[clockController.close] : " + theCityName );
		//			MainWindow.getInstance().closeTheCity(theTimeZone);
		//			/*
		//				if (clockController != null && clockController.getStage() != null) {
		//				clockController.getStage().close();
		//				}
		//				if (mainWindow != null) mainWindow.getStage().hide();
		//			*/
		//   });
        javafx.scene.control.MenuItem restartItem = new javafx.scene.control.MenuItem("Restart");
        restartItem.setOnAction(e -> {
            try { appRestarter.restartApp(() -> saveConfig()); } catch (Exception ex) {
                System.out.println("[Restart] " + ex.getMessage());
			}
		});
        system.getItems().addAll(
            aboutItem,
            logItem,
            configItem,
            iniCopyItem,
            new javafx.scene.control.SeparatorMenuItem(),
            // menuItem("트레이로 보내기"),
            // autoStartItem,
            new javafx.scene.control.SeparatorMenuItem(),
            mainWindowItem,
            // new javafx.scene.control.SeparatorMenuItem(),
            closeItem,
            restartItem,
            exitItem
		);
		return system;
	}
	/*
		// ── 시스템 메뉴 ─────────────────────────────────────────────────────
		private javafx.scene.control.Menu buildSystemMenu(
		javafx.scene.control.ContextMenu popup) {
        javafx.scene.control.Menu system = new javafx.scene.control.Menu("System...");
		}
	*/
	//  ──────────────────────────── 서브메뉴 팩토리 끝 ────────────────────────────
	private void copyIniFile() {
		try {
			String msgName = theCityName + " ini";
			// 1. 없으면 바로 복사
			if (!AppContext.cityConfigFileExists(theCityName)) {
				myConfigFile = AppContext.copyCityConfigFile(
					theCityName,
					theCityName,
					theTimeZone.getId()
				);
				javafx.scene.control.Alert ok = new javafx.scene.control.Alert(
					javafx.scene.control.Alert.AlertType.INFORMATION
				);
				ok.setTitle("Copy Config");
				ok.setHeaderText(null);
				ok.setContentText("(" + msgName + ") copied.");
				ok.showAndWait();
				return;
			}
			// 2. 이미 있으면 확인
			javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
				javafx.scene.control.Alert.AlertType.CONFIRMATION
			);
			confirm.setTitle("Copy Config");
			confirm.setHeaderText(null);
			confirm.setContentText("(" + msgName + ") already exists.");
			java.util.Optional<javafx.scene.control.ButtonType> result = confirm.showAndWait();
			// 3. yes 이면 복사
			if (result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK) {
				myConfigFile = AppContext.copyCityConfigFile(
					theCityName,
					theCityName,
					theTimeZone.getId()
				);
				javafx.scene.control.Alert ok = new javafx.scene.control.Alert(
					javafx.scene.control.Alert.AlertType.INFORMATION
				);
				ok.setTitle("Copy Config");
				ok.setHeaderText(null);
				ok.setContentText("(" + msgName + ") copied.");
				ok.showAndWait();
			}
			} catch (Exception e) {
			System.out.println("[INI COPY] Failed: " + e.getMessage());
			AppLogger.logException(e);
			javafx.scene.control.Alert err = new javafx.scene.control.Alert(
				javafx.scene.control.Alert.AlertType.ERROR
			);
			err.setTitle("Copy Config");
			err.setHeaderText(null);
			err.setContentText("Config copy failed:\n" + e.getMessage());
			err.showAndWait();
		}
	}
	/*
		private void copyIniFile() {
		try {
		String copiedPath = AppContext.ensureCityConfigFile(
		theCityName,
		theCityName,
		theTimeZone.getId()
		);
		// 현재 인스턴스가 그 File을 바로 가리키게 갱신
		myConfigFile = copiedPath;
		System.out.println("[INI COPY] " + copiedPath);
		} catch (Exception e) {
		System.out.println("[INI COPY] Failed: " + e.getMessage());
		AppLogger.logException(e);
		}
		}
	*/
	private void restoreParentPopupTextVisible(javafx.scene.control.ContextMenu popup) {
		if (popup == null) return;
		for (MenuItem item : popup.getItems()) {
			if (item instanceof javafx.scene.control.SeparatorMenuItem) continue;
			if (Boolean.TRUE.equals(item.getProperties().get("popupProgramTitle"))) continue;
			String old = item.getStyle();
			if (old == null) old = "";
			item.setStyle(old
				+ "-fx-text-fill: black;"
				+ "-fx-font-weight: bold;"
			+ "-fx-opacity: 1.0;");
		}
	}
    // ── mainWindow ClockHostCallback용 메뉴 빌더 ──────────────
    /** Gmail/Calendar 메뉴 — MainWindow ClockHostCallback 에서 호출 */
    private javafx.scene.control.Menu buildGmailMenu() {
        javafx.scene.control.Menu menu =
		new javafx.scene.control.Menu("📧 Gmail / Calendar");
        javafx.scene.control.MenuItem gmailSettings = new javafx.scene.control.MenuItem("Gmail Settings");
        gmailSettings.setOnAction(e -> {
            MainWindow mw = MainWindow.getInstance();
            if (mw != null) mw.showGmailSetupDialog(mw.getStage());
		});
        javafx.scene.control.MenuItem guide = new javafx.scene.control.MenuItem("Calendar Setup Guide");
        javafx.scene.control.Menu googleCal =
		new javafx.scene.control.Menu("📧 Google Calendar");
        googleCal.getItems().addAll(
            calMenuAction("Next 3 days", () -> showCalendarResult("Google","google",3,"next")),
            calMenuAction("Next 7 days", () -> showCalendarResult("Google","google",7,"next")),
            calMenuAction("Past 7 days", () -> showCalendarResult("Google","google",7,"past")),
            calMenuAction("This month",  () -> showCalendarResult("Google","google",0,"month")),
            calMenuAction("Next month",  () -> showCalendarResult("Google","google",0,"nextmonth"))
		);
        javafx.scene.control.Menu naverCal =
		new javafx.scene.control.Menu("🟢 Naver Calendar");
        naverCal.getItems().addAll(
            calMenuAction("Next 3 days", () -> showCalendarResult("Naver","naver",3,"next")),
            calMenuAction("Next 7 days", () -> showCalendarResult("Naver","naver",7,"next")),
            calMenuAction("Past 7 days", () -> showCalendarResult("Naver","naver",7,"past")),
            calMenuAction("This month",  () -> showCalendarResult("Naver","naver",0,"month")),
            calMenuAction("Next month",  () -> showCalendarResult("Naver","naver",0,"nextmonth"))
		);
        javafx.scene.control.MenuItem naverSettings = new javafx.scene.control.MenuItem("Naver Settings");
        naverSettings.setOnAction(e -> {
            MainWindow mw = MainWindow.getInstance();
            if (mw != null) mw.showNaverSettingsDialog(mw.getStage());
		});
        menu.getItems().addAll(
            gmailSettings, guide, new javafx.scene.control.SeparatorMenuItem(),
            googleCal, new javafx.scene.control.SeparatorMenuItem(),
		naverCal,  new javafx.scene.control.SeparatorMenuItem(), naverSettings);
        return menu;
	}
    /** 카카오톡 메뉴 — MainWindow ClockHostCallback 에서 호출 */
    private javafx.scene.control.Menu buildKakaoMenuFx() {
        javafx.scene.control.Menu menu =
		new javafx.scene.control.Menu("KakaoTalk...");
        menu.getItems().addAll(
            menuItem("KakaoTalk Logged in"),
            menuItem("Send message to myself..."),
		menuItem("Setup Guide..."));
        return menu;
	}
    /** Telegram 메뉴 — MainWindow ClockHostCallback 에서 호출 */
    private javafx.scene.control.Menu buildTelegramMenuFx() {
        javafx.scene.control.Menu menu =
		new javafx.scene.control.Menu("Telegram");
        javafx.scene.control.MenuItem settings =
		new javafx.scene.control.MenuItem("Telegram Settings");
        javafx.scene.control.MenuItem help =
		new javafx.scene.control.MenuItem("Telegram Setup Guide");
        settings.setOnAction(e -> {
            MainWindow mw = MainWindow.getInstance();
            if (mw != null) mw.showTelegramSettingsDialog(mw.getStage());
            else if (tg != null && mainWindow != null) tg.showTelegramDialog(mainWindow.getStage());
		});
        help.setOnAction(e -> {
            if (mainWindow != null)
			tg.showTelegramHelp(mainWindow.getStage());
		});
        menu.getItems().addAll(settings, help);
        return menu;
	}
    // ══════════════════════════════════════════════════════════════════
    //  스마트폰 카메라 기능
    // ══════════════════════════════════════════════════════════════════
    /** IP Webcam 스트림 연결 시작 — Wed신 프레임을 시계 배경 Images로 직접 주입 */
    void startCamera(String streamUrl) {
		// ── YouTube WebView 배경과 중복 표h 방지 ─────────────────
        stopYoutube();
        if (camera != null) camera.stop();
        // cameraActive 플래그를 먼저 세팅: 이후 콜백이 유효함을 표시
        if (clockController != null) fxGPUNeon.cameraActive = true;
        camera = new TOOLS.CaptureManager.Camera(frame -> {
            // Camera-Reader(백그라운드) 스레드 → FX 스레드로 위임
            // setCameraFrame 내부에서 cameraActive == false 이면 즉h 반환(중지 후 큐 잔류 차단)
            if (clockController != null) {
                Platform.runLater(() -> clockController.setCameraFrame(frame));
			}
            // ── Video 녹화: 백그라운드 스레드에서 직접 프레임 Save ──
            if (videoRecording && videoTempDir != null) {
                java.awt.image.BufferedImage awt = camera != null ? camera.getLastFrameAWT() : null;
                if (awt != null) {
                    int idx = videoFrameIndex.getAndIncrement();
                    File frameFile = new File(videoTempDir, String.format("frame_%06d.jpg", idx));
                    try {
                        javax.imageio.ImageIO.write(awt, "jpg", frameFile);
					} catch (Exception ignored) {}
				}
			}
            // ── 5초 간격 Image 시퀀스 Save ──────────────────────
            if (imageSeqRecording && imageSeqOutputDir != null) {
                long now = System.currentTimeMillis();
                if (now - imageSeqLastSaveMs >= IMAGE_SEQ_INTERVAL_MS) {
                    imageSeqLastSaveMs = now;
                    java.awt.image.BufferedImage awt = camera != null ? camera.getLastFrameAWT() : null;
                    if (awt != null) {
                        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
                        File imgFile = new File(imageSeqOutputDir, "img_" + ts + ".jpg");
                        try {
                            javax.imageio.ImageIO.write(awt, "jpg", imgFile);
                            System.out.println("[ImgSeq] Save: " + imgFile.getName());
						} catch (Exception ignored) {}
					}
				}
			}
		});
        cameraMode = true;
        camera.start(streamUrl);
        System.out.println("[Camera] connection start: " + streamUrl);
	}
    /** 카메라 스트림 Stop 및 상태 initialized */
    void stopCamera() {
        // 녹화 중이면 먼저 마무리
        stopVideoRecording();
        stopImageSequenceRecording();
        // ① cameraActive 를 먼저 false 로 Settings —
        //    이후 Platform.runLater 큐에서 꺼내지는 프레임 콜백이
        //    setCameraFrame 내부의 if (!cameraActive) return; 에 걸려 차단된다.
        if (clockController != null) fxGPUNeon.cameraActive = false;
        if (camera != null) {
            camera.stop();
            camera = null;
		}
        cameraMode = false;
        // ② 배경 완전 initialization — FX 스레드(메뉴 액션)에서 직접 동기 호출
        //    슬라이드쇼 [중지] 버튼과 동한 패턴: Platform.runLater 없음
        if (clockController != null) {
            clockController.setCameraFrame(null);
		}
        System.out.println("[Camera] Stopped");
	}
    // ══════════════════════════════════════════════════════════════════
    //  ITS 교통 CCTV 기능
    // ══════════════════════════════════════════════════════════════════
    /** lazy-init: 첫 접근 h HostCallback 을 주입Other여 ItsCctvManager 생성 */
    ItsCctvManager getItsCctv() {
        if (itsCctv == null) {
            itsCctv = new ItsCctvManager(new ItsCctvManager.HostCallback() {
                @Override
                public void setItsCctvImage(String label,
					javafx.scene.image.WritableImage image) {
                    // Platform.runLater 로 FX 스레드에서 호출됨이 보장됨
                    if (clockController == null) return;
                    if (image == null) {
                        // 중지: 카메라 중지와 동한 패턴으로 배경 initialization
                        fxGPUNeon.cameraActive = false;
                        clockController.setCameraFrame(null);
						} else {
                        // 활성: WritableImage 를 카메라 프레임 Path로 주입
                        fxGPUNeon.cameraActive = true;
                        clockController.setCameraFrame(image);
					}
				}
                @Override
                public void repaintClock() {
                    // setCameraFrame 내부에서 이미 applyBackgroundImage() 호출됨 — 추가 작업 불필요
				}
			});
		}
        return itsCctv;
	}
    /**
		* ITS CCTV 시작.
		* 스마트폰 카메라 등 다른 배경 소스를 먼저 해제한 뒤 타이머를 시작한다.
		* FX 스레드에서 호출해야 한다 (메뉴 액션 콜백 = FX 스레드).
	*/
    void startItsCctv() {
		// ── YouTube WebView 배경과 중복 표h 방지 ─────────────────
        stopYoutube();
        // CCTV 시작 전 현재 배경 상태를 스냅샷으로 Save (중지 h 원상복귀용)
        if (clockController != null) clockController.saveBackgroundSnapshot();
        stopCamera();                   // 카메라 스트림 Stop (배경 initialized 포함)
        fxGPUNeon.cameraActive = true;  // CCTV Images 주입 허용
        getItsCctv().start();
	}
    /**
		* ITS CCTV 중지.
		* 타이머를 중지Other고 배경을 initialization한다.
		* FX 스레드에서 호출해야 한다.
	*/
    void stopItsCctv() {
        if (itsCctv != null && itsCctv.isRunning()) {
            itsCctv.stop();
		}
        fxGPUNeon.cameraActive = false;
        if (clockController != null) {
            // CCTV 시작 전 상태로 원상복귀 (슬라이드쇼 [중지] 버튼과 동한 패턴)
            clockController.restoreBackgroundSnapshot();
		}
        System.out.println("[ItsCctv] stop done -> previous background restored");
	}
    // ══════════════════════════════════════════════════════════════════
    //  YouTube 실시간 배경 (yt-dlp + ffmpeg chunk → JavaFX MediaPlayer)
    // ══════════════════════════════════════════════════════════════════
    // ══════════════════════════════════════════════════════════════════
    //  1. 중앙 고정 — 초기 위치/테마 복원
    // ══════════════════════════════════════════════════════════════════
    private void resetToCenter() {
        if (clockController == null) return;
        // GOLD 테마 + 기본 반지름 + 화면 중앙
        clockController.resetToDefault();
        saveConfig();
	}
    // ══════════════════════════════════════════════════════════════════
    //  2. EXIT — 15초 타이머 확인 다이얼로그
    // ══════════════════════════════════════════════════════════════════
    private void showExitDialog(javafx.stage.Stage owner) {
		System.out.println(" EXIT — 15-second timer confirmation dialog (1)");
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(owner);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("Exit  —  closes in 15s");
        javafx.scene.control.Label msg =
		new javafx.scene.control.Label("Exit KootPanKingThree?");
        msg.setStyle("-fx-font-size:14px; -fx-padding: 20 24 10 24;");
        javafx.scene.control.Label countLabel =
		new javafx.scene.control.Label("Closes automatically in 15 seconds.");
        countLabel.setStyle("-fx-font-size:11px; -fx-text-fill:#888; -fx-padding: 0 24 10 24;");
        javafx.scene.control.Button yesBtn = new javafx.scene.control.Button("Exit (Yes)");
        javafx.scene.control.Button noBtn  = new javafx.scene.control.Button("Cancel (No)");
        yesBtn.setStyle("-fx-font-size:13px; -fx-pref-width:100;");
        noBtn .setStyle("-fx-font-size:13px; -fx-pref-width:100;");
        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(12, yesBtn, noBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER);
        btnRow.setPadding(new javafx.geometry.Insets(0, 0, 16, 0));
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(4, msg, countLabel, btnRow);
        root.setStyle("-fx-background-color: white;");
        dlg.setScene(new javafx.scene.Scene(root));
        // 15초 카운트다운
		int[] sec = {15};
        javafx.animation.Timeline countdown = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ev -> {
                sec[0]--;
                dlg.setTitle("Exit  —  " + sec[0] + "s auto close");
                countLabel.setText(sec[0] + "s auto close.");
                if (sec[0] <= 0) dlg.close();
			})
		);
        countdown.setCycleCount(15);
        countdown.play();
        yesBtn.setOnAction(e -> {
			System.out.println(" EXIT — 15-second timer confirmation dialog (yesBtn.setOnAction)");
            countdown.stop(); dlg.close();
            saveConfig(); Platform.exit();
		});
        noBtn.setOnAction(e -> {
			System.out.println(" EXIT — 15-second timer confirmation dialog (yesBtn.setOnAction)");
		countdown.stop(); dlg.close(); });
        dlg.setOnHidden(e -> countdown.stop());
        dlg.showAndWait();
	}
    // ══════════════════════════════════════════════════════════════════
    //  3. 부팅 자동 실행 — 레지스트리 등록/해제
    // ══════════════════════════════════════════════════════════════════
    private static  String RUN_KEY =
	"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static  String RUN_VALUE = "KootPanKingThree";
    private boolean isAutoStartRegistered() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
			"reg", "query", RUN_KEY, "/v", RUN_VALUE);
            pb.redirectErrorStream(true);
            return pb.start().waitFor() == 0;
		} catch (Exception e) { return false; }
	}
    private void toggleAutoStart(javafx.scene.control.CheckMenuItem item,
		javafx.stage.Stage owner) {
		boolean enable = item.isSelected();
        try {
            ProcessBuilder pb;
            if (enable) {
                // 현재 실행 File Path 확보
                String exe = ProcessHandle.current().info().command()
				.orElse(System.getProperty("java.class.path"));
                pb = new ProcessBuilder(
                    "reg", "add", RUN_KEY,
                    "/v", RUN_VALUE, "/t", "REG_SZ",
				"/d", "\"" + exe + "\"", "/f");
				} else {
                pb = new ProcessBuilder(
				"reg", "delete", RUN_KEY, "/v", RUN_VALUE, "/f");
			}
            pb.redirectErrorStream(true);
            int ret = pb.start().waitFor();
            if (ret == 0) {
                showAutoCloseAlert(owner,
                    enable ? "✅ Auto-start Registered!\nWill start automatically on next boot."
				: "✅ Auto-start removed!", "Auto-start", 5);
				} else {
                item.setSelected(!enable);
                showAlert(owner, "❌ Auto-start " + (enable?"register":"remove") + " failed\nAdministrator privileges may be required.", "Auto-start");
			}
			} catch (Exception ex) {
            item.setSelected(!enable);
            showAlert(owner, "Error: " + ex.getMessage(), "Auto-start");
		}
	}
    private void showAutoCloseAlert(javafx.stage.Stage owner, String message, String title, int seconds) {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(owner);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle(title + "  —  " + seconds + "s auto close");
        javafx.scene.control.Label lbl = new javafx.scene.control.Label(message);
        lbl.setStyle("-fx-font-size:13px; -fx-padding: 18 24 12 24;");
        javafx.scene.control.Button ok = new javafx.scene.control.Button("OK");
        ok.setStyle("-fx-pref-width:80;");
        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(ok);
        row.setAlignment(javafx.geometry.Pos.CENTER);
        row.setPadding(new javafx.geometry.Insets(0, 0, 14, 0));
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(4, lbl, row);
        root.setStyle("-fx-background-color:white;");
        dlg.setScene(new javafx.scene.Scene(root));
		int[] sec = {seconds};
        javafx.animation.Timeline t = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
                sec[0]--;
                dlg.setTitle(title + "  —  " + sec[0] + "s auto close");
                if (sec[0] <= 0) dlg.close();
			})
		);
        t.setCycleCount(seconds);
        t.play();
        ok.setOnAction(e -> { t.stop(); dlg.close(); });
        dlg.setOnHidden(e -> t.stop());
        dlg.show();
	}
    private void showAlert(javafx.stage.Stage owner, String msg, String title) {
        javafx.scene.control.Alert a = new javafx.scene.control.Alert(
		javafx.scene.control.Alert.AlertType.ERROR);
        a.initOwner(owner);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
	}
    // ══════════════════════════════════════════════════════════════════
    //  4. 생활Tools 메뉴
    // ══════════════════════════════════════════════════════════════════
    private javafx.scene.control.Menu buildLifeMenu(javafx.scene.control.ContextMenu popup) {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("Utilities");
        String[][] links = {
            {"🌏 Astronomy Guide", "https://astro.kasi.re.kr/index"},
            {"🕐 TIME.IS",    "https://time.is"},
            {"🕰 TIME&DATE",  "https://www.timeanddate.com/worldclock/full.html"},
            {"🌤 Weather",       "https://www.weather.go.kr/w/index.do"},
		};
        for (String[] e : links) {
            String label = e[0], url = e[1];
            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(label);
            item.setOnAction(ev -> openBrowser(url));
            menu.getItems().add(item);
		}
        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        javafx.scene.control.MenuItem calItem =
		new javafx.scene.control.MenuItem("📅 Perpetual Calendar");
        calItem.setOnAction(ev -> openCalendarHtml(
		(javafx.stage.Stage) popup.getOwnerWindow()));
        javafx.scene.control.MenuItem calUpdateItem =
		new javafx.scene.control.MenuItem("🔄 Update Calendar");
        calUpdateItem.setOnAction(ev -> updateCalendarHtml(
		(javafx.stage.Stage) popup.getOwnerWindow()));
        menu.getItems().addAll(calItem, calUpdateItem);
        return menu;
	}
    private void openBrowser(String url) {
        try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); }
        catch (Exception ex) { System.out.println("[Browser] failed: " + ex.getMessage()); }
	}
    private java.io.File getCalendarFile() {
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        java.io.File dir = new java.io.File(appData
            + java.io.File.separator + "KootPanKingThree"
		+ java.io.File.separator + "data");
        if (!dir.exists()) dir.mkdirs();
        return new java.io.File(dir, "calendar.html");
	}
    private void openCalendarHtml(javafx.stage.Stage owner) {
        java.io.File f = getCalendarFile();
        if (!f.exists()) {
            showAlert(owner, "Please run [Update Calendar] first.", "Calendar");
            return;
		}
        try { java.awt.Desktop.getDesktop().browse(f.toURI()); }
        catch (Exception ex) { showAlert(owner, "Failed to open browser: " + ex.getMessage(), "Calendar"); }
	}
    private void updateCalendarHtml(javafx.stage.Stage owner) {
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
		javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirm.initOwner(owner);
        confirm.setTitle("Update Calendar");
        confirm.setHeaderText(null);
        confirm.setContentText("Auto-updates the calendar including temporary holidays.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != javafx.scene.control.ButtonType.OK) return;
			String URL =
			"https://raw.githubusercontent.com/GarpsuKim/Calendar_Lunar_-_HTML/main/Calendar.html";
			java.io.File dest = getCalendarFile();
            new Thread(() -> {
                try {
                    java.net.HttpURLConnection con =
					(java.net.HttpURLConnection) new java.net.URI(URL).toURL().openConnection();
                    con.setConnectTimeout(10000); con.setReadTimeout(30000); con.connect();
                    int code = con.getResponseCode();
                    if (code != 200) {
                        con.disconnect();
                        Platform.runLater(() -> showAlert(owner,
						"Download failed (HTTP " + code + ")", "Update Calendar"));
                        return;
					}
                    try (java.io.InputStream in = con.getInputStream();
						java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                        in.transferTo(out);
					}
                    con.disconnect();
                    Platform.runLater(() -> {
                        javafx.scene.control.Alert ok = new javafx.scene.control.Alert(
						javafx.scene.control.Alert.AlertType.INFORMATION);
                        ok.initOwner(owner);
                        ok.setTitle("Update Calendar");
                        ok.setContentText("Update complete.\nSaved at: " + dest.getAbsolutePath());
                        ok.showAndWait();
					});
					} catch (Exception ex) {
                    Platform.runLater(() -> showAlert(owner, "Error: " + ex.getMessage(), "Update Calendar"));
				}
			}, "CalendarUpdate").start();
		});
	}
    // ══════════════════════════════════════════════════════════════════
    //  5. 디지탈 시계 상태 접근 / Settings 다이얼로그
    // ══════════════════════════════════════════════════════════════════
    private boolean getDigitalState() {
        return clockController != null && clockController.getDigitalState();
	}
    private void setDigitalState(boolean on) {
        if (clockController != null) clockController.setDigitalState(on);
	}
    // Bug8: 시스템 다크모드 감지 유틸리티
    private  boolean isSystemDarkMode() {
        // Windows: 레지스트리 AppsUseLightTheme 키로 판별 (0=다크, 1=라이트)
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                ProcessBuilder pb = new ProcessBuilder(
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
				"/v", "AppsUseLightTheme");
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                String out = new String(proc.getInputStream().readAllBytes());
                proc.waitFor();
                // 값이 0x0 이면 다크모드
                return out.contains("0x0") && !out.contains("0x1");
			}
		} catch (Exception ignored) {}
        return false;
	}
    private  java.util.List<String> _cachedFontFamilies = null;
    private  java.util.List<String> getCachedFontFamilies() {
        if (_cachedFontFamilies == null) {
            _cachedFontFamilies = javafx.scene.text.Font.getFamilies()
			.stream().limit(200).toList();
		}
        return _cachedFontFamilies;
	}
    /** 날짜 / Time 통합 Settings 다이얼로그. */
    void showDigitalSettingsDialog(javafx.stage.Stage owner) {
        if (clockController == null) return;
        FxGPUNeon.AppState st = clockController.getAppState();
        st.neonFaceDate = pendingFaceDateNeon;
        st.neonFaceTime = pendingDigitalNeon;
        st.digitalNeonBlinkStyle = pendingDigitalNeonBlinkStyle;
        boolean darkMode = isSystemDarkMode();
        String dlgBg      = darkMode ? "#2b2b2b" : "#ffffff";
        String dlgFg      = darkMode ? "#e0e0e0" : "#000000";
        String dlgInputBg = darkMode ? "#3c3c3c" : "#ffffff";
        String commonInputStyle = String.format(
            "-fx-background-color:%s; -fx-text-fill:%s; -fx-border-color:%s; -fx-border-width:1;",
		dlgInputBg, dlgFg, darkMode ? "#555555" : "#cccccc");
        String labelStyle  = String.format("-fx-text-fill:%s;", dlgFg);
        String headerStyle = String.format("-fx-font-weight:bold; -fx-font-size:13; -fx-text-fill:%s;", dlgFg);
        String rootStyle   = String.format("-fx-background-color:%s;", dlgBg);
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("Date / Time Settings");
        java.util.List<String> allFonts = getCachedFontFamilies();
        // ═══════════════════════ 날짜 행 섹션 ════════════════════════
        javafx.scene.control.Label dateHeader = new javafx.scene.control.Label("● Date");
        dateHeader.setStyle(headerStyle);
        javafx.scene.control.CheckBox dateOnOff = new javafx.scene.control.CheckBox("Show");
        dateOnOff.setSelected(st.showFaceDate);
        dateOnOff.setStyle(labelStyle);
        javafx.scene.control.CheckBox dateNeonOn = new javafx.scene.control.CheckBox("Neon Effect");
        dateNeonOn.setSelected(st.neonFaceDate);
        dateNeonOn.setStyle(labelStyle);
        javafx.scene.control.Label dateFmtLbl = new javafx.scene.control.Label("Format");
        dateFmtLbl.setStyle(labelStyle);
        javafx.scene.control.ComboBox<String> dateFmtBox = new javafx.scene.control.ComboBox<>();
        dateFmtBox.getItems().addAll("Mon DD, Day", "YYYY-MM-DD (Day)", "MM/DD (Day)", "Mon DD");
        dateFmtBox.getSelectionModel().select(st.faceDateFormatIndex);
        dateFmtBox.setPrefWidth(170);
        dateFmtBox.setStyle(commonInputStyle);
        javafx.scene.control.Label dateFontLbl = new javafx.scene.control.Label("Font");
        dateFontLbl.setStyle(labelStyle);
        javafx.scene.control.ComboBox<String> dateFontBox = new javafx.scene.control.ComboBox<>();
        dateFontBox.getItems().addAll(allFonts);
        dateFontBox.setValue(st.faceDateFontFamily);
        dateFontBox.setEditable(false);
        dateFontBox.setPrefWidth(180);
        dateFontBox.setStyle(commonInputStyle);
        javafx.scene.control.Label dateSizeLbl = new javafx.scene.control.Label("Size");
        dateSizeLbl.setStyle(labelStyle);
        javafx.scene.control.Spinner<Integer> dateSizeSpinner =
		new javafx.scene.control.Spinner<>(8, 72, (int) st.faceDateFontSize);
        dateSizeSpinner.setPrefWidth(72);
        dateSizeSpinner.setEditable(true);
        dateSizeSpinner.setStyle(commonInputStyle);
        int drgb = st.faceDateColorRgb;
        javafx.scene.control.Label dateColorLbl = new javafx.scene.control.Label("Color");
        dateColorLbl.setStyle(labelStyle);
        javafx.scene.control.ColorPicker dateColorPicker = new javafx.scene.control.ColorPicker(
		javafx.scene.paint.Color.rgb((drgb>>16)&0xFF,(drgb>>8)&0xFF,drgb&0xFF,((drgb>>24)&0xFF)/255.0));
        dateColorPicker.setPrefWidth(130);
        // 날짜 스크롤
        javafx.scene.control.Label dateScrollLbl = new javafx.scene.control.Label("Scroll");
        dateScrollLbl.setStyle(labelStyle);
        javafx.scene.control.ToggleGroup dateDirGroup = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.RadioButton drbFixed = new javafx.scene.control.RadioButton("Fixed");
        javafx.scene.control.RadioButton drbRTL   = new javafx.scene.control.RadioButton("RTL");
        javafx.scene.control.RadioButton drbLTR   = new javafx.scene.control.RadioButton("LTR");
        javafx.scene.control.RadioButton drbPing  = new javafx.scene.control.RadioButton("Ping-pong");
        drbFixed.setToggleGroup(dateDirGroup); drbRTL.setToggleGroup(dateDirGroup);
        drbLTR.setToggleGroup(dateDirGroup);   drbPing.setToggleGroup(dateDirGroup);
        for (javafx.scene.control.RadioButton rb :
		new javafx.scene.control.RadioButton[]{drbFixed,drbRTL,drbLTR,drbPing})
		rb.setStyle(labelStyle);
        switch (st.faceDateScrollDir) {
            case 0 -> drbFixed.setSelected(true);
            case 2 -> drbLTR  .setSelected(true);
            case 3 -> drbPing .setSelected(true);
            default-> drbRTL  .setSelected(true);
		}
        javafx.scene.layout.HBox dateDirRow =
		new javafx.scene.layout.HBox(10, drbFixed, drbRTL, drbLTR, drbPing);
        javafx.scene.control.Label dateSpeedLbl = new javafx.scene.control.Label("Speed");
        dateSpeedLbl.setStyle(labelStyle);
        javafx.scene.control.Slider dateSpeedSlider =
		new javafx.scene.control.Slider(0.2, 6.0, st.faceDateScrollSpeed);
        dateSpeedSlider.setShowTickMarks(true); dateSpeedSlider.setShowTickLabels(true);
        dateSpeedSlider.setMajorTickUnit(2.0);  dateSpeedSlider.setPrefWidth(200);
        javafx.scene.control.Label dateSpeedVal =
		new javafx.scene.control.Label(String.format("%.1f", st.faceDateScrollSpeed));
        dateSpeedVal.setStyle(labelStyle);
        dateSpeedSlider.valueProperty().addListener((ob,ov,nv) ->
		dateSpeedVal.setText(String.format("%.1f", nv.doubleValue())));
        javafx.scene.layout.GridPane dateGrid = new javafx.scene.layout.GridPane();
        dateGrid.setHgap(8); dateGrid.setVgap(6);
        dateGrid.add(dateOnOff,    0, 0, 2, 1);
        dateGrid.add(dateNeonOn,   2, 0, 2, 1);
        dateGrid.add(dateFmtLbl,   0, 1); dateGrid.add(dateFmtBox,      1, 1, 3, 1);
        dateGrid.add(dateFontLbl,  0, 2); dateGrid.add(dateFontBox,     1, 2, 3, 1);
        dateGrid.add(dateSizeLbl,  0, 3); dateGrid.add(dateSizeSpinner, 1, 3);
        dateGrid.add(dateColorLbl, 2, 3); dateGrid.add(dateColorPicker, 3, 3);
        dateGrid.add(dateScrollLbl, 0, 4); dateGrid.add(dateDirRow,     1, 4, 3, 1);
        dateGrid.add(dateSpeedLbl,  0, 5);
        dateGrid.add(new javafx.scene.layout.HBox(6, dateSpeedSlider, dateSpeedVal), 1, 5, 3, 1);
        // ═══════════════════════ 시분초 행 섹션 ══════════════════════
        javafx.scene.control.Label timeHeader = new javafx.scene.control.Label("● Time (H:M:S)");
        timeHeader.setStyle(headerStyle);
        javafx.scene.control.CheckBox timeOnOff = new javafx.scene.control.CheckBox("Show");
        timeOnOff.setSelected(st.showDigital);
        timeOnOff.setStyle(labelStyle);
        javafx.scene.control.CheckBox timeNeonOn = new javafx.scene.control.CheckBox("Neon Effect");
        timeNeonOn.setSelected(st.neonFaceTime);
        timeNeonOn.setStyle(labelStyle);
        javafx.scene.control.Label timeFmtLbl = new javafx.scene.control.Label("Format");
        timeFmtLbl.setStyle(labelStyle);
        javafx.scene.control.ComboBox<String> timeFmtBox = new javafx.scene.control.ComboBox<>();
        timeFmtBox.getItems().addAll(
		"HH:mm:SS AM/PM", "HH:mm AM/PM [Day]", "HH:mm AM/PM", "HH:mm:SS");
        timeFmtBox.getSelectionModel().select(st.digitalFormatIndex);
        timeFmtBox.setPrefWidth(170);
        timeFmtBox.setStyle(commonInputStyle);
        javafx.scene.control.Label timeFontLbl = new javafx.scene.control.Label("Font");
        timeFontLbl.setStyle(labelStyle);
        javafx.scene.control.ComboBox<String> timeFontBox = new javafx.scene.control.ComboBox<>();
        timeFontBox.getItems().addAll(allFonts);
        timeFontBox.setValue(st.digitalFontFamily);
        timeFontBox.setEditable(false);
        timeFontBox.setPrefWidth(180);
        timeFontBox.setStyle(commonInputStyle);
        javafx.scene.control.Label timeSizeLbl = new javafx.scene.control.Label("Size");
        timeSizeLbl.setStyle(labelStyle);
        javafx.scene.control.Spinner<Integer> timeSizeSpinner =
		new javafx.scene.control.Spinner<>(8, 72, (int) st.digitalFontSize);
        timeSizeSpinner.setPrefWidth(72);
        timeSizeSpinner.setEditable(true);
        timeSizeSpinner.setStyle(commonInputStyle);
        int trgb = st.digitalColorRgb;
        javafx.scene.control.Label timeColorLbl = new javafx.scene.control.Label("Color");
        timeColorLbl.setStyle(labelStyle);
        javafx.scene.control.ColorPicker timeColorPicker = new javafx.scene.control.ColorPicker(
		javafx.scene.paint.Color.rgb((trgb>>16)&0xFF,(trgb>>8)&0xFF,trgb&0xFF,((trgb>>24)&0xFF)/255.0));
        timeColorPicker.setPrefWidth(130);
        // 시분초 스크롤
        javafx.scene.control.Label scrollLbl = new javafx.scene.control.Label("Scroll");
        scrollLbl.setStyle(labelStyle);
        javafx.scene.control.ToggleGroup dirGroup = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.RadioButton rbFixed = new javafx.scene.control.RadioButton("Fixed");
        javafx.scene.control.RadioButton rbRTL   = new javafx.scene.control.RadioButton("RTL");
        javafx.scene.control.RadioButton rbLTR   = new javafx.scene.control.RadioButton("LTR");
        javafx.scene.control.RadioButton rbPing  = new javafx.scene.control.RadioButton("Ping-pong");
        rbFixed.setToggleGroup(dirGroup); rbRTL.setToggleGroup(dirGroup);
        rbLTR.setToggleGroup(dirGroup);   rbPing.setToggleGroup(dirGroup);
        for (javafx.scene.control.RadioButton rb :
		new javafx.scene.control.RadioButton[]{rbFixed,rbRTL,rbLTR,rbPing})
		rb.setStyle(labelStyle);
        switch (st.digitalScrollDir) {
            case 0 -> rbFixed.setSelected(true);
            case 2 -> rbLTR  .setSelected(true);
            case 3 -> rbPing .setSelected(true);
            default-> rbRTL  .setSelected(true);
		}
        javafx.scene.layout.HBox dirRow =
		new javafx.scene.layout.HBox(10, rbFixed, rbRTL, rbLTR, rbPing);
        javafx.scene.control.Label speedLbl = new javafx.scene.control.Label("Speed");
        speedLbl.setStyle(labelStyle);
        javafx.scene.control.Slider speedSlider =
		new javafx.scene.control.Slider(0.2, 6.0, st.digitalScrollSpeed);
        speedSlider.setShowTickMarks(true); speedSlider.setShowTickLabels(true);
        speedSlider.setMajorTickUnit(2.0);  speedSlider.setPrefWidth(200);
        javafx.scene.control.Label speedVal =
		new javafx.scene.control.Label(String.format("%.1f", st.digitalScrollSpeed));
        speedVal.setStyle(labelStyle);
        speedSlider.valueProperty().addListener((ob,ov,nv) ->
		speedVal.setText(String.format("%.1f", nv.doubleValue())));
        javafx.scene.layout.GridPane timeGrid = new javafx.scene.layout.GridPane();
        timeGrid.setHgap(8); timeGrid.setVgap(6);
        timeGrid.add(timeOnOff,    0, 0, 2, 1);
        timeGrid.add(timeNeonOn,   2, 0, 2, 1);
        timeGrid.add(timeFmtLbl,   0, 1); timeGrid.add(timeFmtBox,      1, 1, 3, 1);
        timeGrid.add(timeFontLbl,  0, 2); timeGrid.add(timeFontBox,     1, 2, 3, 1);
        timeGrid.add(timeSizeLbl,  0, 3); timeGrid.add(timeSizeSpinner, 1, 3);
        timeGrid.add(timeColorLbl, 2, 3); timeGrid.add(timeColorPicker, 3, 3);
        timeGrid.add(scrollLbl,    0, 4); timeGrid.add(dirRow,          1, 4, 3, 1);
        timeGrid.add(speedLbl,     0, 5);
        timeGrid.add(new javafx.scene.layout.HBox(6, speedSlider, speedVal), 1, 5, 3, 1);
        // ═══════════════════════ Digital Neon Blink ══════════════════════
        javafx.scene.control.Label blinkHeader = new javafx.scene.control.Label("● Digital Neon Blink");
        blinkHeader.setStyle(headerStyle);
        javafx.scene.control.ToggleGroup blinkGroup = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.RadioButton blinkNone   = new javafx.scene.control.RadioButton("None (always on)");
        javafx.scene.control.RadioButton blinkPulse  = new javafx.scene.control.RadioButton("Soft Pulse");
        javafx.scene.control.RadioButton blinkSharp  = new javafx.scene.control.RadioButton("Sharp Blink");
        javafx.scene.control.RadioButton blinkRandom = new javafx.scene.control.RadioButton("Random Blink");
        for (javafx.scene.control.RadioButton rb :
			new javafx.scene.control.RadioButton[]{blinkNone, blinkPulse, blinkSharp, blinkRandom}) {
            rb.setToggleGroup(blinkGroup);
            rb.setStyle(labelStyle);
		}
        switch (st.digitalNeonBlinkStyle) {
            case PULSE -> blinkPulse.setSelected(true);
            case SHARP -> blinkSharp.setSelected(true);
            case RANDOM -> blinkRandom.setSelected(true);
            default -> blinkNone.setSelected(true);
		}
        javafx.scene.layout.HBox blinkRow1 = new javafx.scene.layout.HBox(14, blinkNone, blinkPulse);
        javafx.scene.layout.HBox blinkRow2 = new javafx.scene.layout.HBox(14, blinkSharp, blinkRandom);
        // ═══════════════════════ 즉h Apply 로직 ══════════════════════
        // 확인 버튼 누르기 전이라도 값 변경 h AppState에 즉h 반영 + 화면 갱신
        Runnable applyNow = () -> {
            // 날짜
            st.showFaceDate        = dateOnOff.isSelected();
            st.neonFaceDate      = dateNeonOn.isSelected();
            st.faceDateFormatIndex = dateFmtBox.getSelectionModel().getSelectedIndex();
            if (dateFontBox.getValue() != null) st.faceDateFontFamily = dateFontBox.getValue();
            st.faceDateFontSize    = dateSizeSpinner.getValue();
            javafx.scene.paint.Color dc = dateColorPicker.getValue();
            st.faceDateColorRgb = ((int)(dc.getOpacity()*255)<<24)
			| ((int)(dc.getRed()*255)<<16) | ((int)(dc.getGreen()*255)<<8)
			| (int)(dc.getBlue()*255);
            if      (drbFixed.isSelected()) st.faceDateScrollDir = 0;
            else if (drbLTR  .isSelected()) st.faceDateScrollDir = 2;
            else if (drbPing .isSelected()) st.faceDateScrollDir = 3;
            else                            st.faceDateScrollDir = 1;
            st.faceDateScrollSpeed  = dateSpeedSlider.getValue();
            st.faceDateScrollOffset = Double.NaN;
            st.faceDatePingPongDir  = 1;
            // 시분초
            st.showDigital        = timeOnOff.isSelected();
            st.neonFaceTime     = timeNeonOn.isSelected();
            st.digitalFormatIndex = timeFmtBox.getSelectionModel().getSelectedIndex();
            if (timeFontBox.getValue() != null) st.digitalFontFamily = timeFontBox.getValue();
            st.digitalFontSize    = timeSizeSpinner.getValue();
            javafx.scene.paint.Color tc = timeColorPicker.getValue();
            st.digitalColorRgb = ((int)(tc.getOpacity()*255)<<24)
			| ((int)(tc.getRed()*255)<<16) | ((int)(tc.getGreen()*255)<<8)
			| (int)(tc.getBlue()*255);
            if      (rbFixed.isSelected()) st.digitalScrollDir = 0;
            else if (rbLTR  .isSelected()) st.digitalScrollDir = 2;
            else if (rbPing .isSelected()) st.digitalScrollDir = 3;
            else                           st.digitalScrollDir = 1;
            st.digitalScrollSpeed  = speedSlider.getValue();
            st.digitalScrollOffset = Double.NaN;
            st.faceScrollOffset    = Double.NaN;
            st.facePingPongDir     = 1;
            if (blinkPulse.isSelected()) st.digitalNeonBlinkStyle = FxGPUNeon.AppState.NeonBlinkStyle.PULSE;
            else if (blinkSharp.isSelected()) st.digitalNeonBlinkStyle = FxGPUNeon.AppState.NeonBlinkStyle.SHARP;
            else if (blinkRandom.isSelected()) st.digitalNeonBlinkStyle = FxGPUNeon.AppState.NeonBlinkStyle.RANDOM;
            else st.digitalNeonBlinkStyle = FxGPUNeon.AppState.NeonBlinkStyle.NONE;
            // 화면 즉h 반영 — 개별 showDigital/showFaceDate 값을 그대로 Enable
            // (setDigitalState 호출 금지: 둘을 같은 값으로 덮어쓰므로)
            if (clockController != null) clockController.applyDigitalSettings();
            // 팝업 메뉴 체크 상태 동기화
            if (digitalMenuItem != null)
			digitalMenuItem.setSelected(getDigitalState());
		};
        // 모든 컨트롤에 즉h Apply 리스너
        dateOnOff.setOnAction(e -> applyNow.run());
        dateNeonOn.setOnAction(e -> applyNow.run());
        dateFmtBox.getSelectionModel().selectedIndexProperty()
		.addListener((o,ov,nv) -> applyNow.run());
        dateFontBox.getSelectionModel().selectedItemProperty()
		.addListener((o,ov,nv) -> applyNow.run());
        dateSizeSpinner.valueProperty().addListener((o,ov,nv) -> applyNow.run());
        dateColorPicker.valueProperty().addListener((o,ov,nv) -> applyNow.run());
        dateDirGroup.selectedToggleProperty().addListener((o,ov,nv) -> applyNow.run());
        dateSpeedSlider.valueProperty().addListener((o,ov,nv) -> applyNow.run());
        timeOnOff.setOnAction(e -> applyNow.run());
        timeNeonOn.setOnAction(e -> applyNow.run());
        timeFmtBox.getSelectionModel().selectedIndexProperty()
		.addListener((o,ov,nv) -> applyNow.run());
        timeFontBox.getSelectionModel().selectedItemProperty()
		.addListener((o,ov,nv) -> applyNow.run());
        timeSizeSpinner.valueProperty().addListener((o,ov,nv) -> applyNow.run());
        timeColorPicker.valueProperty().addListener((o,ov,nv) -> applyNow.run());
        dirGroup.selectedToggleProperty().addListener((o,ov,nv) -> applyNow.run());
        speedSlider.valueProperty().addListener((o,ov,nv) -> applyNow.run());
        blinkGroup.selectedToggleProperty().addListener((o,ov,nv) -> applyNow.run());
        // ═══════════════════════ 버튼 행 ══════════════════════════
        javafx.scene.control.Button okBtn     = new javafx.scene.control.Button("OK");
        javafx.scene.control.Button cancelBtn = new javafx.scene.control.Button("Cancel");
        okBtn.setDefaultButton(true); cancelBtn.setCancelButton(true);
        okBtn.setPrefWidth(80);       cancelBtn.setPrefWidth(80);
        okBtn.setOnAction(e -> {
            applyNow.run();      // 마지막 상태 확정
            saveDigitalConfig();
            dlg.close();
		});
        cancelBtn.setOnAction(e -> dlg.close());
        javafx.scene.layout.HBox btnRow =
		new javafx.scene.layout.HBox(10, okBtn, cancelBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10);
        root.setPadding(new javafx.geometry.Insets(16, 18, 14, 18));
        root.setStyle(rootStyle);
        root.getChildren().addAll(
            dateHeader, dateGrid,
            new javafx.scene.control.Separator(),
            timeHeader, timeGrid,
            new javafx.scene.control.Separator(),
            blinkHeader, blinkRow1, blinkRow2,
            new javafx.scene.control.Separator(),
            btnRow
		);
        dlg.setScene(new javafx.scene.Scene(root));
        dlg.sizeToScene();
        dlg.showAndWait();
	}
    private void saveDigitalConfig() {
        if (clockController == null) return;
        FxGPUNeon.AppState st = clockController.getAppState();
        config.setProperty("digital.show",        String.valueOf(st.showDigital));
        config.setProperty("digital.formatIndex", String.valueOf(st.digitalFormatIndex));
        config.setProperty("digital.fontFamily",  st.digitalFontFamily);
        config.setProperty("digital.fontSize",    String.valueOf((int) st.digitalFontSize));
        config.setProperty("digital.colorRgb",    String.valueOf(st.digitalColorRgb));
        config.setProperty("digital.neon",        String.valueOf(st.neonFaceTime));
        config.setProperty("digital.neonBlinkStyle", String.valueOf(st.digitalNeonBlinkStyle));
        config.setProperty("digital.scrollDir",   String.valueOf(st.digitalScrollDir));
        config.setProperty("digital.scrollSpeed", String.valueOf(st.digitalScrollSpeed));
        config.setProperty("faceDate.show",        String.valueOf(st.showFaceDate));
        config.setProperty("faceDate.formatIndex", String.valueOf(st.faceDateFormatIndex));
        config.setProperty("faceDate.fontFamily",  st.faceDateFontFamily);
        config.setProperty("faceDate.fontSize",    String.valueOf((int) st.faceDateFontSize));
        config.setProperty("faceDate.colorRgb",    String.valueOf(st.faceDateColorRgb));
        config.setProperty("faceDate.neon",       String.valueOf(st.neonFaceDate));
        // Bug7: 날짜 스크롤 Save
        config.setProperty("faceDate.scrollDir",   String.valueOf(st.faceDateScrollDir));
        config.setProperty("faceDate.scrollSpeed", String.valueOf(st.faceDateScrollSpeed));
        // saveConfig() 는 호출Other지 않음 — 이 메서드가 saveConfig() 안에서 호출되므로 재귀 방지
	}
    // ══════════════════════════════════════════════════════════════════
    //  YouTube 스트림 URL & Settings 다이얼로그
    // ══════════════════════════════════════════════════════════════════
    /**
		* [YouTube 스트림 URL & Settings] 다이얼로그.
		* - URL 입력 필드
		* - yt-dlp.exe Path 표h + 지정 버튼
		* - ffmpeg.exe Path 표h + 지정 버튼
		* - [확인] h URL + exe Path ini Save 후 YouTube 시작
		* - [취소] h 아무것도 안 함
	*/
    private void showYoutubeSettingsDialog(javafx.stage.Stage owner) {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(owner);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("YouTube Stream URL & Settings");
        // ── URL 입력 ──────────────────────────────────────────────────
        javafx.scene.control.Label urlLabel = new javafx.scene.control.Label("YouTube URL:");
        javafx.scene.control.TextField urlField = new javafx.scene.control.TextField(AppContext.getYoutubeUrl());
        urlField.setPrefWidth(380);
        urlField.setPromptText("https://www.youtube.com/live/...");
        // ── yt-dlp.exe Path ───────────────────────────────────────────
        javafx.scene.control.Label ytdlpLabel = new javafx.scene.control.Label("yt-dlp.exe:");
        javafx.scene.control.TextField ytdlpField = new javafx.scene.control.TextField(AppContext.getYtdlpPath());
        ytdlpField.setPrefWidth(300);
        ytdlpField.setEditable(false);
        ytdlpField.setPromptText("(not set)");
        javafx.scene.control.Button ytdlpBtn = new javafx.scene.control.Button("Set yt-dlp.exe");
        ytdlpBtn.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Select yt-dlp.exe location");
            fc.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("yt-dlp", "yt-dlp.exe"),
                new javafx.stage.FileChooser.ExtensionFilter("Executable", "*.exe"),
                new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
			);
            String cur = ytdlpField.getText().trim();
            if (!cur.isEmpty()) {
                java.io.File prev = new java.io.File(cur).getParentFile();
                if (prev != null && prev.exists()) fc.setInitialDirectory(prev);
			}
            java.io.File chosen = fc.showOpenDialog(dlg);
            if (chosen != null) ytdlpField.setText(chosen.getAbsolutePath());
		});
        // ── ffmpeg.exe Path ───────────────────────────────────────────
        javafx.scene.control.Label ffmpegLabel = new javafx.scene.control.Label("ffmpeg.exe:");
        javafx.scene.control.TextField ffmpegField = new javafx.scene.control.TextField(AppContext.getFfmpegPath());
        ffmpegField.setPrefWidth(300);
        ffmpegField.setEditable(false);
        ffmpegField.setPromptText("(not set)");
        javafx.scene.control.Button ffmpegBtn = new javafx.scene.control.Button("Set ffmpeg.exe");
        ffmpegBtn.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Select ffmpeg.exe location");
            fc.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("ffmpeg", "ffmpeg.exe"),
                new javafx.stage.FileChooser.ExtensionFilter("Executable", "*.exe"),
                new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
			);
            String cur = ffmpegField.getText().trim();
            if (!cur.isEmpty()) {
                java.io.File prev = new java.io.File(cur).getParentFile();
                if (prev != null && prev.exists()) fc.setInitialDirectory(prev);
			}
            java.io.File chosen = fc.showOpenDialog(dlg);
            if (chosen != null) ffmpegField.setText(chosen.getAbsolutePath());
		});
        // ── 버튼: 확인 / 취소 ─────────────────────────────────────────
        javafx.scene.control.Button okBtn     = new javafx.scene.control.Button("OK");
        javafx.scene.control.Button cancelBtn = new javafx.scene.control.Button("Cancel");
        okBtn.setDefaultButton(true);
        cancelBtn.setCancelButton(true);
        okBtn.setOnAction(e -> {
            String newUrl    = urlField.getText().trim();
            String newYtdlp  = ytdlpField.getText().trim();
            String newFfmpeg = ffmpegField.getText().trim();
            // exe Path ini Save (URL과 무관Other게 항상 Save)
            AppContext.setYtdlpPath(newYtdlp);
            AppContext.setFfmpegPath(newFfmpeg);
            if (!newUrl.isEmpty()) {
                AppContext.setYoutubeUrl(newUrl);
                // exe Path가 모두 지정된 경우만 재생 시작
                if (!newYtdlp.isEmpty() && new java.io.File(newYtdlp).exists()
					&& !newFfmpeg.isEmpty() && new java.io.File(newFfmpeg).exists()) {
                    stopCamera(); stopItsCctv();
                    startYoutube(newUrl);
				}
			}
            saveConfig();
            dlg.close();
		});
        cancelBtn.setOnAction(e -> dlg.close());
        // ── 레이아웃 ──────────────────────────────────────────────────
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(16, 18, 10, 18));
        // URL 행
        grid.add(urlLabel,  0, 0);
        grid.add(urlField,  1, 0, 2, 1);
        // yt-dlp 행
        grid.add(ytdlpLabel, 0, 1);
        grid.add(ytdlpField, 1, 1);
        grid.add(ytdlpBtn,   2, 1);
        // ffmpeg 행
        grid.add(ffmpegLabel, 0, 2);
        grid.add(ffmpegField, 1, 2);
        grid.add(ffmpegBtn,   2, 2);
        // 버튼 행
        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(8, okBtn, cancelBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        btnRow.setPadding(new javafx.geometry.Insets(6, 18, 12, 18));
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(0, grid, btnRow);
        root.setStyle("-fx-background-color: white;");
        dlg.setScene(new javafx.scene.Scene(root));
        dlg.showAndWait();
	}
    /** YouTube / 라이브 스트림 배경 시작. FX 스레드에서 호출. */
    void startYoutube(String url) {
		if (url == null || url.isEmpty()) return;
        if (ytPlayer == null) {
            ytPlayer = new BackgroundPlayer.YoutubePlayer(
                new BackgroundPlayer.YoutubePlayer.HostCallback() {
                    @Override public void attachMediaView(javafx.scene.Node v) {
                        if (clockController != null) clockController.attachMediaView(v);
					}
                    @Override public void detachMediaView() {
                        if (clockController != null) clockController.detachMediaView();
					}
                    @Override public void onYoutubeFrame(javafx.scene.image.WritableImage frame) {
                        fxGPUNeon.cameraActive = true;
                        if (clockController != null) clockController.setCameraFrame(frame);
					}
                    @Override public void clearYoutubeFrame() {
                        fxGPUNeon.cameraActive = false;
                        if (clockController != null) clockController.setCameraFrame(null);
					}
                    @Override public void onStatusMessage(String message) {
                        if (clockController != null) clockController.showStatusMessage(message);
					}
                    @Override public String getSettingsDir() { return SETTINGS_DIR; }
                    @Override public String getYtDlpPath()  { return AppContext.getYtdlpPath(); }
                    @Override public String getFfmpegPath()  { return AppContext.getFfmpegPath(); }
				});
				// 테마 변경 h YouTube 중지 콜백 주입
				if (clockController != null)
                clockController.setStopYoutubeCallback(this::stopYoutube);
		}
        ytPlayer.start(url);
	}
    /** YouTube 배경 Stop. FX 스레드에서 호출. */
    void stopYoutube() {
        if (ytPlayer != null) ytPlayer.stop();
	}
    // ══════════════════════════════════════════════════════════════════
    //  로컬 MP4 배경 재생
    // ══════════════════════════════════════════════════════════════════
    /**
		* 로컬 MP4 File을 시계 배경으로 재생.
		*  - ffmpeg(ffmpeg.path) 있으면 모든 코덱 지원 (H.265·AV1·VP9 포함)
		*  - ffmpeg 없으면 JavaFX MediaPlayer 폴백 (H.264 한정)
		* FX 스레드에서 호출.
	*/
    void startLocalMp4(File file) {
		if (file == null || !file.exists()) return;
        stopCamera();
        stopItsCctv();
        stopYoutube();
        // ytPlayer 재Enable (HostCallback 은 YouTube 와 동)
        if (ytPlayer == null) {
            ytPlayer = new BackgroundPlayer.YoutubePlayer(
                new BackgroundPlayer.YoutubePlayer.HostCallback() {
                    @Override public void attachMediaView(javafx.scene.Node v) {
                        if (clockController != null) clockController.attachMediaView(v);
					}
                    @Override public void detachMediaView() {
                        if (clockController != null) clockController.detachMediaView();
					}
                    @Override public void onYoutubeFrame(javafx.scene.image.WritableImage frame) {
                        fxGPUNeon.cameraActive = true;
                        if (clockController != null) clockController.setCameraFrame(frame);
					}
                    @Override public void clearYoutubeFrame() {
                        fxGPUNeon.cameraActive = false;
                        if (clockController != null) clockController.setCameraFrame(null);
					}
                    @Override public void onStatusMessage(String message) {
                        if (clockController != null) clockController.showStatusMessage(message);
					}
                    @Override public String getSettingsDir() { return SETTINGS_DIR; }
                    @Override public String getYtDlpPath()   { return AppContext.getYtdlpPath(); }
                    @Override public String getFfmpegPath()  { return AppContext.getFfmpegPath(); }
				});
				if (clockController != null)
                clockController.setStopYoutubeCallback(this::stopYoutube);
		}
        localMp4LastFile = file.getAbsolutePath();
        ytPlayer.startLocalMp4(file);
        ytPlayer.setVolume(localMp4Volume); // ini에서 복원한 볼륨 즉h Apply
        saveConfig();
        System.out.println("[KPK] local MP4 playback start: " + file.getName());
	}
    /** 로컬 MP4 재생 Stop (stopYoutube 와 공유). */
    void stopLocalMp4() {
        stopYoutube();
        localMp4LastFile = "";
        saveConfig();
	}
    /**
		* ITS 교통 CCTV Select 다이얼로그 (JavaFX).
		* 도시명 필터 필드 + ListView 로 구성.
		* 확인 h 필터 키워드를 mgr.setFilter() 에 반영 → 이전/다음 순환 범위 Apply.
		*
		* @return Select한 CctvItem, 취소 h null
	*/
    private ItsCctvManager.CctvItem showCctvSelectDialog(
		javafx.stage.Stage owner,
		java.util.List<ItsCctvManager.CctvItem> allItems,
		ItsCctvManager mgr) {
        // ── 필터 바 ───────────────────────────────────────────────────
        javafx.scene.control.TextField filterField =
		new javafx.scene.control.TextField();
        filterField.setPromptText("Enter city/region name (e.g. Seoul, Tokyo)");
        javafx.scene.control.Button filterBtn =
		new javafx.scene.control.Button("Filter");
        javafx.scene.control.Label countLabel =
		new javafx.scene.control.Label("Total: " + allItems.size() + "");
        javafx.scene.layout.HBox filterBar = new javafx.scene.layout.HBox(6,
		filterField, filterBtn, countLabel);
        filterBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.HBox.setHgrow(filterField,
		javafx.scene.layout.Priority.ALWAYS);
        // ── ListView ──────────────────────────────────────────────────
        javafx.collections.ObservableList<ItsCctvManager.CctvItem> listData =
		javafx.collections.FXCollections.observableArrayList(allItems);
        javafx.scene.control.ListView<ItsCctvManager.CctvItem> listView =
		new javafx.scene.control.ListView<>(listData);
        listView.setPrefSize(480, 560);
        if (!listData.isEmpty()) listView.getSelectionModel().selectFirst();
        // ── 필터 로직 ─────────────────────────────────────────────────
        Runnable applyFilter = () -> {
            String kw = filterField.getText().trim();
            listData.clear();
            java.util.List<ItsCctvManager.CctvItem> filtered;
            if (kw.isEmpty()) {
                filtered = allItems;
				} else {
                filtered = new java.util.ArrayList<>();
                for (ItsCctvManager.CctvItem it : allItems) {
                    if (it.name.contains(kw)) filtered.add(it);
				}
			}
            listData.addAll(filtered);
            countLabel.setText((kw.isEmpty() ? "Total: " : "Results: ") + filtered.size() + "");
            if (!listData.isEmpty()) listView.getSelectionModel().selectFirst();
		};
        filterBtn.setOnAction(ev -> applyFilter.run());
        filterField.setOnAction(ev -> applyFilter.run());   // Enter 키
        // ── 다이얼로그 구성 ───────────────────────────────────────────
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8,
		filterBar, listView);
        content.setPadding(new javafx.geometry.Insets(10));
        javafx.scene.control.Dialog<ItsCctvManager.CctvItem> dlg =
		new javafx.scene.control.Dialog<>();
        dlg.setTitle("ITS Traffic CCTV Selection");
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(
            javafx.scene.control.ButtonType.OK,
		javafx.scene.control.ButtonType.CANCEL);
        if (owner != null) dlg.initOwner(owner);
        // 더블클릭 → OK 버튼 fire
        listView.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2
				&& listView.getSelectionModel().getSelectedItem() != null) {
                javafx.scene.Node okBtn = dlg.getDialogPane()
				.lookupButton(javafx.scene.control.ButtonType.OK);
                okBtn.fireEvent(new javafx.event.ActionEvent());
			}
		});
        dlg.setResultConverter(bt -> {
            if (bt == javafx.scene.control.ButtonType.OK) {
                ItsCctvManager.CctvItem sel =
				listView.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    mgr.setFilter(filterField.getText().trim());
				}
                return sel;
			}
            return null;
		});
        return dlg.showAndWait().orElse(null);
	}
    /** 녹화 중 깜빡이는 오버레이 표시 */
    private void showRecordingOverlay(String fileName) {
        hideRecordingOverlay(); // 혹시 이미 열려 있으면 닫기
        recOverlayStage = new javafx.stage.Stage();
        recOverlayStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        recOverlayStage.setAlwaysOnTop(true);
        recOverlayStage.setResizable(false);
        // ── 레이블 구성 ─────────────────────────────────────────
        javafx.scene.control.Label recLabel = new javafx.scene.control.Label("🔴 REC  녹화 중...");
        recLabel.setStyle(
            "-fx-text-fill: white;" +
            "-fx-font-size: 15px;" +
            "-fx-font-weight: bold;" +
            "-fx-font-family: 'Malgun Gothic';"
        );
        javafx.scene.control.Label fileLabel = new javafx.scene.control.Label(fileName);
        fileLabel.setStyle(
            "-fx-text-fill: #cccccc;" +
            "-fx-font-size: 11px;" +
            "-fx-font-family: 'Malgun Gothic';"
        );
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(4, recLabel, fileLabel);
        box.setPadding(new javafx.geometry.Insets(10, 16, 10, 16));
        box.setStyle(
            "-fx-background-color: rgba(0,0,0,0.82);" +
            "-fx-background-radius: 8;"
        );
        javafx.scene.Scene scene = new javafx.scene.Scene(box);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        recOverlayStage.setScene(scene);
        // ── 화면 우하단 배치 ────────────────────────────────────
        javafx.geometry.Rectangle2D screen =
            javafx.stage.Screen.getPrimary().getVisualBounds();
        recOverlayStage.show();
        recOverlayStage.setX(screen.getMaxX() - recOverlayStage.getWidth()  - 20);
        recOverlayStage.setY(screen.getMaxY() - recOverlayStage.getHeight() - 20);
        // ── 0.6초 간격 깜빡임 ───────────────────────────────────
        recOverlayTimeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(0.6), ev -> {
                double op = recOverlayStage.getOpacity();
                recOverlayStage.setOpacity(op > 0.5 ? 0.25 : 1.0);
            })
        );
        recOverlayTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        recOverlayTimeline.play();
    }
    /** 녹화 중 오버레이 숨김 */
    private void hideRecordingOverlay() {
        if (recOverlayTimeline != null) { recOverlayTimeline.stop(); recOverlayTimeline = null; }
        if (recOverlayStage   != null) { recOverlayStage.close();   recOverlayStage    = null; }
    }
    /** 현재 프레임을 APP_DIR/img/ 에 저장하고 결과를 5초 타이머 메시지로 표시 */
    void captureCamera(javafx.stage.Stage owner) {
        if (camera == null) return;
        java.io.File saveDir = new java.io.File(AppContext.getAPP_DIR());
        if (!saveDir.exists()) saveDir.mkdirs();
        String saved = camera.capture(saveDir);
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
				javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.initOwner(owner);
            alert.setTitle("Camera Capture");
            alert.setHeaderText(null);
            if (saved != null) {
                System.out.println("[Camera] capture saved: " + saved);
                String fileName = new java.io.File(saved).getName();
                alert.setContentText("📸 저장 완료: " + saveDir.getAbsolutePath() + "\n" + fileName);
            } else {
                System.out.println("[Camera] capture failed");
                alert.setAlertType(javafx.scene.control.Alert.AlertType.WARNING);
                alert.setTitle("Camera Capture");
                alert.setContentText("❌ 저장 실패\n저장 경로: " + saveDir.getAbsolutePath());
            }
            alert.show();
            // 5초 후 자동 닫힘
            javafx.animation.PauseTransition pause =
				new javafx.animation.PauseTransition(javafx.util.Duration.seconds(5));
            pause.setOnFinished(ev -> alert.close());
            pause.play();
		});
	}
    // ══════════════════════════════════════════════════════════════════
    //  Video 녹화 기능 (JPEG 프레임 → FFmpeg → MP4)
    //
    //  동작 방식:
    //    1) startVideoRecording() 호출 h 임h Folder(videoTempDir) 생성
    //    2) startCamera() 의 FrameListener 콜백에서 매 프레임을
    //       frame_000000.jpg, frame_000001.jpg ... 으로 Save
    //    3) stopVideoRecording() 호출 h 녹화 플래그 해제 후
    //       백그라운드에서 FFmpeg 로 MP4 인코딩
    //    4) FFmpeg 없으면 프레임 JPEG 들을 ZIP 으로 묶어 폴백 Save
    //    5) 인코딩 When done, 임h Folder Delete
    // ══════════════════════════════════════════════════════════════════
    /**
		* Video 녹화 시작.
		* @param outputFile Save할 .mp4 File (Enable자가 FileChooser로 Select)
	*/
    void startVideoRecording(File outputFile) {
        if (videoRecording || imageSeqRecording) return; // 이미 녹Tue 중
        // ── FFmpeg 탐색 + 동작 검증 ──────────────────────────────
        String ffExe = findFfmpeg();
        boolean ffOk = verifyFfmpeg(ffExe);
        if (!ffOk) {
            // FFmpeg 미Settings 또는 비정상 → 자동으로 5초 간격 Image Save
            System.out.println("[VideoRec] FFmpeg unavailable → 5sec Image save mode auto switch");
            String baseName = outputFile.getName().replaceFirst("\\.[^.]+$", "");
            File imgDir = new File(outputFile.getParentFile(), baseName + "_images");
            startImageSequenceRecording(imgDir);
            Platform.runLater(() -> {
                javafx.scene.control.Alert info = new javafx.scene.control.Alert(
				javafx.scene.control.Alert.AlertType.INFORMATION);
                info.setTitle("Cannot save video — saving images instead");
                info.setHeaderText(null);
                info.setContentText(
                    "ffmpeg.path is not set or file not found.\n\n" +
                    "Images will be saved every 5 seconds.\n" +
                    "Save folder: " + imgDir.getAbsolutePath() + "\n\n" +
                    "To save video:\n" +
                    "[Connect Phone Camera] → [Set Video Tool] button\n" +
				"Please set ffmpeg.exe.");
                // info.setAlwaysOnTop(true);
                info.show();
                javafx.animation.PauseTransition p =
				new javafx.animation.PauseTransition(javafx.util.Duration.seconds(8));
                p.setOnFinished(ev -> info.close());
                p.play();
			});
            return;
		}
        // ── FFmpeg 정상 → MP4 녹화 ───────────────────────────────
        String baseName = outputFile.getName().replaceFirst("\\.[^.]+$", "");
        videoTempDir = new File(outputFile.getParentFile(),
		"_frames_" + baseName + "_" + System.currentTimeMillis());
        if (!videoTempDir.mkdirs()) {
            showAlert("Video Recording", "Failed to create temp folder:\n" + videoTempDir.getAbsolutePath());
            return;
		}
        videoOutputFile = outputFile;
        videoFrameIndex.set(0);
        videoRecording  = true;
        Platform.runLater(() -> {
            if (camVideoItem != null)
			camVideoItem.setText("⏹ Stop Recording");
            showRecordingOverlay(outputFile.getName());
		});
        System.out.println("[VideoRec] recording start → " + outputFile.getAbsolutePath());
	}
    /**
		* Video 녹화 중지 및 MP4 인코딩.
		* 녹화 중이 아니면 아무것도 Other지 않는다.
		* 백그라운드(VideoEncoder) 스레드에서 FFmpeg 호출 후 Done 다이얼로그 표시.
	*/
    void stopVideoRecording() {
        if (!videoRecording) return;
        videoRecording = false;
        // 메뉴 Text 복원
        Platform.runLater(() -> {
            if (camVideoItem != null)
			camVideoItem.setText("🎬 Start Video Recording");
            hideRecordingOverlay();
		});
		File tempDir    = videoTempDir;
		File outputFile = videoOutputFile;
        videoTempDir    = null;
        videoOutputFile = null;
        int frameCount = videoFrameIndex.get();
        System.out.println("[VideoRec] recording stop — frame count: " + frameCount
		+ " → " + (outputFile != null ? outputFile.getAbsolutePath() : "(null)"));
        if (frameCount == 0 || tempDir == null || outputFile == null) {
            deleteDir(tempDir);
            return;
		}
        // 백그라운드에서 FFmpeg 인코딩
        new Thread(() -> {
            boolean ffmpegOk = false;
            try {
                ffmpegOk = encodeWithFfmpeg(tempDir, outputFile, frameCount);
				} catch (Exception ex) {
                System.out.println("[VideoRec] FFmpeg error: " + ex.getMessage());
				AppLogger.logException(ex);
			}
			boolean success = ffmpegOk;
			File fallbackZip;
            if (!success) {
                // 폴백: JPEG 프레임들을 ZIP 으로 Save
                fallbackZip = new File(outputFile.getParentFile(),
				outputFile.getName().replaceFirst("\\.[^.]+$", "") + "_frames.zip");
                packFramesToZip(tempDir, fallbackZip, frameCount);
				} else {
                fallbackZip = null;
			}
            deleteDir(tempDir); // 임h Folder 정리
            Platform.runLater(() -> showVideoResult(success, outputFile, fallbackZip, frameCount));
		}, "VideoEncoder").start();
	}
    /**
		* FFmpeg 으로 JPEG 시퀀스 → MP4 인코딩.
		* 프레임 레이트는 실제 캡처 FPS 를 추정Other여 Enable (기본 15fps).
		* @return 인코딩 성공 여부
	*/
    private boolean encodeWithFfmpeg(File frameDir, File outputFile, int frameCount) throws Exception {
        // FFmpeg 실행 File 탐색: PATH 에서 ffmpeg / ffmpeg.exe 찾기
        String ffmpegExe = findFfmpeg();
        if (ffmpegExe == null) {
            System.out.println("[VideoRec] FFmpeg  not found.");
            return false;
		}
        // 입력 패턴: frame_000000.jpg
        String inputPattern = new File(frameDir, "frame_%06d.jpg").getAbsolutePath();
        // 프레임 레이트 추정: 실제 녹화 환경은 ~10~15fps (IP Webcam 기본)
        // 정확한 FPS 계산은 타임스탬프 기반이 필요Other나 여기서는 15fps 고정
        String fps = "15";
        ProcessBuilder pb = new ProcessBuilder(
            ffmpegExe,
            "-y",                      // 덮어쓰기
            "-framerate", fps,
            "-i", inputPattern,
            "-c:v", "libx264",
            "-preset", "fast",
            "-pix_fmt", "yuv420p",
            "-movflags", "+faststart",
            outputFile.getAbsolutePath()
		);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        // FFmpeg 출력 소비 (블로킹 방지)
        try (java.io.InputStream is = proc.getInputStream()) {
            byte[] buf = new byte[4096];
            while (is.read(buf) != -1) {}
		}
        int exitCode = proc.waitFor();
        System.out.println("[VideoRec] FFmpeg exit code: " + exitCode);
        return exitCode == 0 && outputFile.exists() && outputFile.length() > 0;
	}
    /**
		* FFmpeg 실행File Path 반환.
		* ini 의 ffmpeg.path 키 값만 Enable한다. 자동 탐색 없음.
		* [폰 카메라 연결] 다이얼로그의 [Video Tools 지정] 버튼으로 Settings.
		* @return 실행 가능한 ffmpeg 절대Path, 미Settings/File없음이면 null
	*/
    private String findFfmpeg() {
        String ffmpegPath = AppContext.getFfmpegPath();
        if (ffmpegPath == null || ffmpegPath.trim().isEmpty()) {
            System.out.println("[FFmpeg] ffmpeg.path not set");
            return null;
		}
        File f = new File(ffmpegPath.trim());
        if (f.isFile()) {
            System.out.println("[FFmpeg] Enable: " + f.getAbsolutePath());
            return f.getAbsolutePath();
		}
        System.out.println("[FFmpeg] File not found: " + ffmpegPath);
        return null;
	}
    /**
		* FFmpeg 정상 동작 여부 확인.
		* ffmpeg -version 실행 후 종료 코드 0 이면 정상.
	*/
    private boolean verifyFfmpeg(String exePath) {
        if (exePath == null) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder(exePath, "-version");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (java.io.InputStream is = proc.getInputStream()) {
                byte[] buf = new byte[4096];
                while (is.read(buf) != -1) {}
			}
            int code = proc.waitFor();
            System.out.println("[FFmpeg] verify exit code: " + code);
            return code == 0;
			} catch (Exception e) {
            System.out.println("[FFmpeg] verify Failed: " + e.getMessage());
			AppLogger.logException(e);
            return false;
		}
	}
    /** JPEG 프레임들을 ZIP 으로 묶기 (FFmpeg 폴백) */
    private void packFramesToZip(File frameDir, File zipFile, int frameCount) {
        try (java.util.zip.ZipOutputStream zos =
			new java.util.zip.ZipOutputStream(new java.io.BufferedOutputStream(
			new FileOutputStream(zipFile)))) {
            for (int i = 0; i < frameCount; i++) {
                File f = new File(frameDir, String.format("frame_%06d.jpg", i));
                if (!f.exists()) continue;
                zos.putNextEntry(new java.util.zip.ZipEntry(f.getName()));
                try (java.io.InputStream is = new java.io.FileInputStream(f)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) != -1) zos.write(buf, 0, n);
				}
                zos.closeEntry();
			}
            System.out.println("[VideoRec] ZIP fallback Save: " + zipFile.getAbsolutePath());
			} catch (Exception ex) {
            System.out.println("[VideoRec] ZIP Save Failed: " + ex.getMessage());
			AppLogger.logException(ex);
		}
	}
    /** 임h 디렉Sat리 재귀 Delete */
    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        dir.delete();
	}
    // ══════════════════════════════════════════════════════════════════
    //  Image 시퀀스 Save (FFmpeg 없을 때 대체 모드)
    // ══════════════════════════════════════════════════════════════════
    /**
		* 5초 간격 Image 시퀀스 Save 시작.
		* @param outputDir Image를 Save할 Folder
	*/
    void startImageSequenceRecording(File outputDir) {
        if (imageSeqRecording) return;
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            showAlert("Save Image", "Failed to create folder:\n" + outputDir.getAbsolutePath());
            return;
		}
        imageSeqOutputDir  = outputDir;
        imageSeqLastSaveMs = 0L; // 첫 프레임 즉h Save
        imageSeqRecording  = true;
        Platform.runLater(() -> {
            if (camVideoItem != null)
			camVideoItem.setText("⏹ Stop Image Saving (5s interval)");
            showRecordingOverlay("📷 IMG  " + outputDir.getName());
		});
        System.out.println("[ImgSeq] start → " + outputDir.getAbsolutePath());
	}
    /**
		* Image 시퀀스 Save 중지. Save 중이 아니면 아무것도 Other지 않는다.
	*/
    void stopImageSequenceRecording() {
        if (!imageSeqRecording) return;
        imageSeqRecording = false;
        Platform.runLater(() -> {
            if (camVideoItem != null)
			camVideoItem.setText("🎬 Start Video Recording");
            hideRecordingOverlay();
		});
        File dir = imageSeqOutputDir;
        imageSeqOutputDir = null;
        int count = 0;
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles(f -> f.getName().endsWith(".jpg"));
            count = (files != null) ? files.length : 0;
		}
		int saved = count;
		File Dir = dir;
        Platform.runLater(() -> showImageSeqResult(Dir, saved));
        System.out.println("[ImgSeq] Stopped — saved file count: " + saved);
	}
    /** Images 시퀀스 Save Done 다이얼로그 */
    private void showImageSeqResult(File dir, int count) {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("Image Save Complete");
        String msg = (dir != null && dir.exists())
		? "✅ Images saved successfully\n\n"
		+ "Location: " + dir.getAbsolutePath() + "\n"
		+ "Files saved: " + count + "\n"
		+ "Interval: 5 seconds\n\n"
		+ "Install FFmpeg to save as MP4:\n"
		+ "https://ffmpeg.org/download.html"
		: "❌ Image save failed or folder not found";
        javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(msg);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefSize(420, 200);
        javafx.scene.control.Button btnOk = new javafx.scene.control.Button("OK");
        btnOk.setDefaultButton(true);
        btnOk.setOnAction(ev -> dlg.close());
        javafx.scene.control.Button btnOpen = new javafx.scene.control.Button("Open Folder");
        btnOpen.setDisable(dir == null || !dir.exists());
        btnOpen.setOnAction(ev -> {
            try { if (dir != null) java.awt.Desktop.getDesktop().open(dir); }
            catch (Exception ex) { System.out.println("[ImgSeq] Open Folder failed: " + ex.getMessage()); }
		});
        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(10, btnOpen, btnOk);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        btnRow.setPadding(new javafx.geometry.Insets(6, 10, 6, 10));
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(8, ta, btnRow);
        root.setPadding(new javafx.geometry.Insets(12));
        dlg.setScene(new javafx.scene.Scene(root));
        dlg.sizeToScene();
        dlg.show();
        javafx.animation.PauseTransition pause =
		new javafx.animation.PauseTransition(javafx.util.Duration.seconds(5));
        pause.setOnFinished(ev -> dlg.close());
        pause.play();
	}
    /** 인코딩 Done 결과 다이얼로그 */
    private void showVideoResult(boolean mp4Ok, File outputFile,
		File fallbackZip, int frameCount) {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("Video Save Complete");
        String msg;
        if (mp4Ok) {
            long mb = outputFile.length() / (1024 * 1024);
            msg = "✅ MP4 saved!\n\n"
			+ "File: " + outputFile.getName() + "\n"
			+ "Size: " + mb + " MB\n"
			+ "Frames: " + frameCount + "\n\n"
			+ outputFile.getAbsolutePath();
			} else if (fallbackZip != null && fallbackZip.exists()) {
            long mb = fallbackZip.length() / (1024 * 1024);
            msg = "⚠ FFmpeg not found — MP4 conversion unavailable.\n"
			+ "JPEG frames saved as ZIP.\n\n"
			+ "File: " + fallbackZip.getName() + "\n"
			+ "Size: " + mb + " MB\n"
			+ "Frames: " + frameCount + "\n\n"
			+ "Install FFmpeg to convert to MP4:\n"
			+ "https://ffmpeg.org/download.html\n\n"
			+ fallbackZip.getAbsolutePath();
			} else {
            msg = "❌ Save failed. Frames: " + frameCount;
		}
        javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(msg);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefSize(420, 220);
        javafx.scene.control.Button btnOk = new javafx.scene.control.Button("OK");
        btnOk.setDefaultButton(true);
        btnOk.setOnAction(ev -> dlg.close());
        // File 탐색기에서 열기 버튼
        File showFile = mp4Ok ? outputFile : fallbackZip;
        javafx.scene.control.Button btnOpen = new javafx.scene.control.Button("Open Folder");
        btnOpen.setDisable(showFile == null || !showFile.exists());
        btnOpen.setOnAction(ev -> {
            try {
                if (showFile != null && showFile.getParentFile() != null)
				java.awt.Desktop.getDesktop().open(showFile.getParentFile());
				} catch (Exception ex) {
                System.out.println("[VideoRec] Folder open Failed: " + ex.getMessage());
			}
		});
        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(10, btnOpen, btnOk);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        btnRow.setPadding(new javafx.geometry.Insets(6, 10, 6, 10));
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(8, ta, btnRow);
        root.setPadding(new javafx.geometry.Insets(12));
        dlg.setScene(new javafx.scene.Scene(root));
        dlg.sizeToScene();
        dlg.show();
        // 5초 자동 닫힘
        javafx.animation.PauseTransition pause =
		new javafx.animation.PauseTransition(javafx.util.Duration.seconds(5));
        pause.setOnFinished(ev -> dlg.close());
        pause.play();
	}
    /** 간단한 Info Notice 다이얼로그 */
    private void showAlert(String title, String msg) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
			javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.show();
		});
	}
    /** 미구현 메뉴 placeholder */
    private javafx.scene.control.MenuItem menuItem(String name) {
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(name);
        item.setOnAction(e -> System.out.println("[Menu] " + name + " (not implemented)"));
        return item;
	}
    // ──────────────────────────────────────────────────────────────────
    // 캘린더 메뉴 항목 팩토리 — 라벨 + 동작 연결 (5)
    // ──────────────────────────────────────────────────────────────────
    private javafx.scene.control.MenuItem calMenuAction(String label, Runnable action) {
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(label);
        item.setOnAction(e -> new Thread(action::run, "CalMenuFetch").start());
        return item;
	}
    // ──────────────────────────────────────────────────────────────────
    // 캘린더 검색 실행 → showScheduleDialog 공유 (5)(6)
    //
    // provider : "google" | "naver"
    // mode     : "next" | "past" | "month" | "nextmonth"
    // days     : next/past  때  수 (month 계열은 0 — 무시됨)
    // ──────────────────────────────────────────────────────────────────
    private void showCalendarResult(String providerLabel,
		String provider,
		int days,
		String mode) {
        try {
            String title;
            String content;
            if ("google".equals(provider)) {
                if (!googleCalendarService.isInitialized()) {
                    javafx.application.Platform.runLater(() ->
                        showScheduleDialog("📧 Google Calendar",
                            "Google Calendar is not initialized.\n" +
						"Please check credentials.json."));
						return;
				}
                java.util.List<GoogleCalendarService.CalendarEvent> events;
                switch (mode) {
                    case "next":
					events = googleCalendarService.getNextDays(days);
					title  = "📧 Google next " + days + " days schedule";
					break;
                    case "past":
					events = googleCalendarService.getPastDays(days);
					title  = "📧 Google past " + days + " days schedule";
					break;
                    case "month":
					events = googleCalendarService.getThisMonth();
					title  = "📧 Google this month";
					break;
                    case "nextmonth":
					events = googleCalendarService.getNextMonth();
					title  = "📧 Google next month";
					break;
                    default:
					events = java.util.Collections.emptyList();
					title  = "📧 Google Calendar";
				}
                content = GoogleCalendarService.formatEvents(title, events);
				} else { // naver
                if (!naverCalendarService.isInitialized()) {
                    javafx.application.Platform.runLater(() ->
                        showScheduleDialog("🟢 Naver Calendar",
                            "Naver Calendar is not initialized.\n" +
						"Check naver.caldav.id / naver.caldav.password in clock_settings.ini."));
						return;
				}
                java.util.List<NaverCalendarService.CalendarEvent> events;
                switch (mode) {
                    case "next":
					events = naverCalendarService.getNextDays(days);
					title  = "🟢 Naver next " + days + " days schedule";
					break;
                    case "past":
					events = naverCalendarService.getPastDays(days);
					title  = "🟢 Naver past " + days + " days schedule";
					break;
                    case "month":
					events = naverCalendarService.getThisMonth();
					title  = "🟢 Naver this month";
					break;
                    case "nextmonth":
					events = naverCalendarService.getNextMonth();
					title  = "🟢 Naver next month";
					break;
                    default:
					events = java.util.Collections.emptyList();
					title  = "🟢 Naver Calendar";
				}
                content = NaverCalendarService.formatEvents(title, events);
			}
			String dlgTitle   = title;
			String dlgContent = content;
            // 6) showScheduleDialog 공유 — FX 스레드에서 표시
            javafx.application.Platform.runLater(() ->
			showScheduleDialog(dlgTitle, dlgContent));
			} catch (Exception e) {
			String err = e.getMessage();
            javafx.application.Platform.runLater(() ->
			showScheduleDialog("Calendar Error", "Schedule query failed:\n" + err));
		}
	}
    // ── 시스템 메뉴 동작 ────────────────────────────────────────
    private void openLogFile() {
		MainWindow.doShowLogFile();
		/*
			try {
            String path = AppLogger.getLogFilePath();
            if (path == null || path.trim().isEmpty()) return;
            java.io.File f = new java.io.File(path);
            if (!f.exists()) return;
            if (java.awt.Desktop.isDesktopSupported())
			java.awt.Desktop.getDesktop().open(f);
			} catch (Exception e) {
            System.err.println("로그 File open Failed: " + e.getMessage());
			}
		*/
	}
    private void openConfigFile() {
		/*
			try {
            java.io.File f = new java.io.File(IniController.getPrimaryConfigFilePath());
            if (!f.exists()) { System.out.println("Settings File Not Found: " + f.getAbsolutePath()); return; }
            IniController.openPrimaryConfigFile();
			} catch (Exception e) {
            System.err.println("Settings File open Failed: " + e.getMessage());
			}
		*/
	}
    private void downloadIniFile() {
		/*
			try {
            java.io.File f = new java.io.File(IniController.getPrimaryConfigFilePath());
            if (f.exists()) { System.out.println("ini already exists: " + f.getAbsolutePath()); return; }
            boolean ok = IniController.ensurePrimaryConfigFile();
            System.out.println("ini Download " + (ok ? "Done" : "Failed") + ": " + f.getAbsolutePath());
			} catch (Exception e) {
            System.err.println("ini Download Failed: " + e.getMessage());
			}
		*/
	}
    /** About 다이얼로그 — 48s 카운트다운 후 자동 닫힘, 컬러 item + 블로그 Links */
    private void showAboutDialog() {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setResizable(false);
        // ── 48초 카운트다운 타이틀 ──────────────────────────────
		int[] sec = {48};
        dlg.setTitle(thisProgramName + "  —  " + sec[0] + "s auto close");
        // ── 컬러 Text 항목 ────────────────────────────────────
        javafx.scene.text.TextFlow tf = new javafx.scene.text.TextFlow();
        tf.setPadding(new javafx.geometry.Insets(12, 16, 8, 16));
        tf.setPrefWidth(460);
        String[][] items = {
            {"• Marble-textured analog clock",                                         "#2aa198"},
            {"• Fully customizable clock design",                                             "#268bd2"},
            {"• World city clocks",                                               "#6c71c4"},
            {"• (Coming) Telegram, Gmail, Naver, KakaoTalk, Smart Camera, Live CCTV...", "#b58900"},
            {"• KIM GAP-SU , 2026-3-18 , Seoul, Republic of Korea",                               "#dc322f"}
		};
        for (String[] row : items) {
            javafx.scene.text.Text t = new javafx.scene.text.Text(row[0] + "\n");
            t.setFill(javafx.scene.paint.Color.web(row[1]));
            t.setStyle("-fx-font-family: 'Malgun Gothic'; -fx-font-size: 14px;");
            tf.getChildren().add(t);
		}
        // ── 구분선 ────────────────────────────────────────────
        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        // ── 블로그 Links ──────────────────────────────────────
		String blogUrl = "https://blog.naver.com/garpsu/224213400580";
        javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink(
		"→ Details: " + blogUrl);
        link.setStyle("-fx-font-family: 'Malgun Gothic'; -fx-font-size: 12px;");
        link.setOnAction(ev -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(blogUrl));
				} catch (Exception ex) {
                System.out.println("[About] Links open Failed: " + ex.getMessage());
			}
		});
        // ── OK 버튼 ───────────────────────────────────────────
        javafx.scene.control.Button okBtn = new javafx.scene.control.Button("OK");
        okBtn.setDefaultButton(true);
        javafx.scene.layout.HBox linkBox = new javafx.scene.layout.HBox(link);
        linkBox.setPadding(new javafx.geometry.Insets(4, 10, 2, 10));
        javafx.scene.layout.HBox btnBox = new javafx.scene.layout.HBox(okBtn);
        btnBox.setAlignment(javafx.geometry.Pos.CENTER);
        btnBox.setPadding(new javafx.geometry.Insets(4, 10, 8, 10));
        // ── 레이아웃 ─────────────────────────────────────────
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(tf, sep, linkBox, btnBox);
        dlg.setScene(new javafx.scene.Scene(root));
        // ── 카운트다운 타이머 ─────────────────────────────────
		javafx.animation.Timeline[] holder = {null};
        Runnable doClose = () -> { if (holder[0] != null) holder[0].stop(); dlg.close(); };
        okBtn.setOnAction(ev -> doClose.run());
        javafx.animation.Timeline tl = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ev -> {
                sec[0]--;
                dlg.setTitle(thisProgramName + "  —  " + sec[0] + "s auto close");
                if (sec[0] <= 0) doClose.run();
			}));
			tl.setCycleCount(48);
			holder[0] = tl;
			dlg.setOnHidden(ev -> { if (holder[0] != null) holder[0].stop(); });
			dlg.show();
			tl.play();
	}
    /** schedule 다이얼로그 — 300s 카운트다운 후 자동 닫힘 (initialized 3 days Show 및 팝업 메뉴 공유) */
    public void showScheduleDialog(String title, String content) {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setTitle(title);
        dlg.setAlwaysOnTop(true);
        dlg.setResizable(true);
        // ── 내용 ─────────────────────────────────────────────
        javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(content);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setStyle("-fx-font-family: 'Malgun Gothic'; -fx-font-size: 13px;");
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(ta);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        // ── Other단 ─────────────────────────────────────────────
        javafx.scene.control.Label countdown = new javafx.scene.control.Label("Auto close: 300s");
        countdown.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        javafx.scene.control.Button closeBtn = new javafx.scene.control.Button("Close");
        closeBtn.setDefaultButton(true);
        javafx.scene.layout.HBox bottom = new javafx.scene.layout.HBox(12, countdown, closeBtn);
        bottom.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        bottom.setPadding(new javafx.geometry.Insets(6, 10, 6, 10));
        // ── 레이아웃 ─────────────────────────────────────────
        javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane();
        root.setCenter(sp);
        root.setBottom(bottom);
        dlg.setScene(new javafx.scene.Scene(root, 480, 400));
        // ── 300초 카운트다운 타이머 ───────────────────────────
		int[] remain = {300};
		javafx.animation.Timeline[] holder = {null};
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
			}));
			tl.setCycleCount(300);
			holder[0] = tl;
			dlg.setOnHidden(e -> { if (holder[0] != null) holder[0].stop(); });
			dlg.show();
			tl.play();
	}
}