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
import javafx.application.Platform;
import javafx.stage.Stage;

public class KootPanKingThree extends Application {
    private static final String thisProgramName = "[KootPanKingThree 3차원_끝판왕 (v1.0)]";
	
    static KootPanKingThree instance;  // FxGPUNeon 종료 콜백용
    static FxSplashWindow splashWindow; // 메인 윈도우
	
    AlarmController alarmController;
    private static Properties config = new Properties();
    private static IniController iniController ;
	
    boolean isChild = false;        // 복사 생성자에서 true 로 설정 (MenuBuilder에서 접근)
	
    // ── 설정 파일 저장 폴더 결정 (우선순위 3단계) ─────────────────
    static String EXE_PATH = ""; // ← 추가
    private static final String APP_DIR = resolveAppDir();
    static final String SETTINGS_DIR = resolveSettingsDir();
    static final String CONFIG_FILE = SETTINGS_DIR + "clock_settings.ini";
	
    // ── 인스턴스별 설정 파일 경로 및 자식 여부 ─────────────────────
    // 기본 인스턴스 : clock_settings.ini  (CONFIG_FILE 과 동일)
    // 자식 인스턴스 : clock_settings_<CityName>.ini
    String myConfigFile = CONFIG_FILE; // 기본값: 부모와 동일
    static final TelegramBot tgMain = new TelegramBot(null);
    static final GmailSender gmail = new GmailSender();
    final Kakao kakao = new Kakao();
    final TelegramBot tg = new TelegramBot(new TelegramBot.CommandHandler() {
        @Override public java.io.File captureClockScreen() throws Exception {
            if (screenCapture == null) throw new IllegalStateException("screenCapture not initialized");
            return screenCapture.captureClockScreen();
		}
		
        @Override public java.io.File captureFullScreen() throws Exception {
            if (screenCapture == null) throw new IllegalStateException("screenCapture not initialized");
            return screenCapture.captureFullScreen();
		}
		
        @Override public java.io.File captureMonitor(int i) throws Exception {
            if (screenCapture == null) throw new IllegalStateException("screenCapture not initialized");
            return screenCapture.captureMonitor(i);
		}
		
        @Override public void shutdownPC() {
            if (isChild) return;
            if (shutdownGuard != null) shutdownGuard.cancel();
            saveConfig();
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            gmail.sendShutdownNoticeSync(
                "텔레그램 원격 종료 알림",
                GmailSender.APP_SIGNATURE + "텔레그램 명령으로 PC가 종료됩니다.\n\n종료 시각: " + now
			);
            AppLogger.close();
            try {
                Runtime.getRuntime().exec(new String[]{"shutdown", "-s", "-f", "-t", "0"});
				} catch (Exception e) {
                System.out.println("[Shutdown] " + e.getMessage());
				AppLogger.logException(e);
			}
		}
		
        @Override public void rebootPC() {
            if (isChild) return;
            if (shutdownGuard != null) shutdownGuard.cancel();
            saveConfig();
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            gmail.sendShutdownNoticeSync(
                "텔레그램 원격 재시작 알림",
                GmailSender.APP_SIGNATURE + "텔레그램 명령으로 PC가 재시작됩니다.\n\n재시작 시각: " + now
			);
            AppLogger.close();
            try {
                Runtime.getRuntime().exec(new String[]{"shutdown", "-r", "-f", "-t", "0"});
				} catch (Exception e) {
                System.out.println("[Reboot] " + e.getMessage());
			}
		}
		
        @Override public void showImage(java.io.File imageFile) {
            if (screenCapture != null) {
                screenCapture.showImageWindow(imageFile);
			}
		}
		
        @Override public void playMedia(java.io.File mediaFile) {
            if (chimeController != null) {
                chimeController.playMediaFile(mediaFile);
			}
		}
		
        @Override public void saveConfig() {
            KootPanKingThree.this.saveConfig();
		}
		
        @Override public String getFirstAlarmTelegramChatId() {
            if (alarmController == null) return "";
            for (AlarmController.AlarmEntry a : alarmController.getAlarmList()) {
                if (!a.telegramChatId.isEmpty()) return a.telegramChatId;
			}
            return "";
		}
	});
	
    AppRestarter.ShutdownGuard shutdownGuard; // 강제 종료 감지 훅
    AppRestarter appRestarter;                // 재시작 / AppCDS 관리
    CaptureManager screenCapture;             // 화면 캡처
    FxGPUNeon.ClockController clockController; // FX 시계 컨트롤러 (카메라 프레임 주입용)
	
    // ── 스마트폰 카메라 ────────────────────────────────────────────
    boolean cameraMode = false;

    CaptureManager.Camera camera = null;
    String cameraUrl   = "http://192.168.0.100:8080"; // 마지막 사용 URL
    boolean cameraFlipH = false; // 좌우 반전
    boolean cameraFlipV = false; // 상하 반전

    // ── ITS 교통 CCTV ────────────────────────────────────────────────────
    ItsCctvManager itsCctv = null;   // lazy-init: getItsCctv() 로 접근

    // ── YouTube 실시간 배경 ──────────────────────────────────────────
    BackgroundPlayer.YoutubePlayer ytPlayer = null; // lazy-init
    String youtubeUrl = "";                         // 마지막 사용 URL
    String ytdlpPath  = "";                         // ini: youtube.ytdlp.path

    // ── 로컬 MP4 배경 재생 ───────────────────────────────────────────
    String localMp4LastFile = "";                   // ini: localmp4.lastFile
    double localMp4Volume   = 1.0;                  // ini: localmp4.volume (0.0~1.0)

    // ── 동영상 녹화 ────────────────────────────────────────────
    volatile boolean      videoRecording  = false;   // 녹화 중 플래그

    // ffmpeg 실행파일 경로 (ini 저장/로드)
    String ffmpegPath = "";   // ffmpeg 경로 (ini 키: ffmpeg.path)
	
    // ── 5초 간격 이미지 시퀀스 저장 (ffmpeg 없을 때 대체 모드) ──
    volatile boolean      imageSeqRecording  = false;
    File                  imageSeqOutputDir  = null;
    volatile long         imageSeqLastSaveMs = 0L;
    static final long     IMAGE_SEQ_INTERVAL_MS = 5_000L;
    File                  videoOutputFile = null;    // 최종 저장될 mp4 파일
    File                  videoTempDir    = null;    // 임시 JPEG 프레임 폴더
    AtomicInteger         videoFrameIndex = new AtomicInteger(0);
    javafx.scene.control.MenuItem camVideoItem = null; // 메뉴 참조 (텍스트 업데이트용)
    ChimeController chimeController;          // 차임벨
    // ── chime pending (loadConfig 시점에 chimeController 미생성) ──
    private boolean   pendingChimeEnabled  = false;
    private String    pendingChimeFile     = "";
    private int       pendingChimeDuration = 0;    // 0=15초, 1=30초, 2=끝까지
    private boolean[] pendingChimeMinutes  = null;
    private int       pendingChimeVolume   = 80;

    GoogleCalendarService googleCalendarService = new GoogleCalendarService();
    NaverCalendarService  naverCalendarService  = new NaverCalendarService();
	
    private int pendingRadius = -1;           // loadConfig에서 읽은 반지름 임시 보관
    private double pendingRainbowIntervalSec = 0.5; // loadConfig에서 읽은 레인보우 인터벌

    // ── 시분초 행 pending ───────────────────────────────────────────────
    private boolean pendingDigitalShow        = false;
    private int     pendingDigitalFormatIndex = 0;
    private String  pendingDigitalFontFamily  = "Consolas";
    private double  pendingDigitalFontSize    = 20.0;
    private int     pendingDigitalColorRgb    = 0xFFFFFFFF;
    private int     pendingDigitalScrollDir   = 1;
    private double  pendingDigitalScrollSpeed = 1.5;
    // ── 날짜 행 pending ─────────────────────────────────────────────────
    private boolean pendingFaceDateShow       = true;
    private int     pendingFaceDateFormatIndex= 0;
    private String  pendingFaceDateFontFamily = "Consolas";
    private double  pendingFaceDateFontSize   = 20.0;
    private int     pendingFaceDateColorRgb   = 0xFFFF2222;
	
    boolean alwaysOnTop = true;
    boolean showDigital = true;
    boolean showNumbers = true;
    String theme = "Light";
    float opacity = 1.0f;
    String cityName = "Local";
    java.time.ZoneId timeZone = java.time.ZoneId.systemDefault();
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
	
    private int rainbowSeconds = 30;   // INI: rainbowSeconds (기본 30초)
	
    // ── Constructor (기본, ini 로드) ───────────────────────────
    public KootPanKingThree() {
        myConfigFile = CONFIG_FILE;
        instance = this;
	    iniController = new IniController(APP_DIR, SETTINGS_DIR, myConfigFile, isChild, cityName);
        iniController.initialize();
        iniController.load();
        config = iniController.getProperties();
        loadConfig();
        // tgMain 에 토큰 동기화 — main() 종료 후 shutdown notice 전송에 사용
        tgMain.botToken = tg.botToken;
        tgMain.myChatId = tg.myChatId;
        // AppLogger 경로를 GmailSender 에 주입
        gmail.exeFilePath = !EXE_PATH.isEmpty() ? EXE_PATH : AppLogger.getExeFilePath();
        gmail.logFilePath = AppLogger.getLogFilePath();
        // ── 분리된 서비스 객체 초기화 ────────────────────────
        appRestarter = new AppRestarter(gmail, tg);
        appRestarter.setCachedPaths(
            config.getProperty("app.exePath", ""),
            config.getProperty("app.javawPath", ""),
            config.getProperty("app.jsaPath", "")
		);
        System.out.println("[KootPanKingThree] NEW build - AppRestarter OK");
		
        screenCapture = new CaptureManager(null); // clockPanel은 initUI 후 주입
		
        tg.kakao = kakao;          // 카카오 미러링 주입
        tg.appDir = APP_DIR;        // APP_DIR 주입 — txt/ini 경로 기준
        kakao.appDir = APP_DIR;     // APP_DIR 주입 — txt/ini 경로 기준
        kakao.onTokenSaved = this::saveConfig; // 로그인 성공 시 refresh_token ini 저장
		
        // ── 캘린더 서비스 초기화 및 tg 주입 ──────────────────────
        googleCalendarService.setAppDir(SETTINGS_DIR);
        tg.calendarService         = googleCalendarService;
        tg.naverCalendarService    = naverCalendarService;
		
        // 네이버: credentials 로드됐으면 백그라운드 초기화
        new Thread(() -> {
            if (NaverCalendarService.credentialsExist(
			naverCalendarService.naverId, naverCalendarService.naverPassword)) {
			naverCalendarService.init();
            }
            // 구글: credentials.json 있으면 백그라운드 초기화 (없으면 조용히 건너뜀)
            if (googleCalendarService.credentialsExist()) {
                googleCalendarService.init();
			}
			
            // ── 초기화 완료 후 향후 3일 일정 텔레그램 전송 ──────
            if (tg.myChatId.isEmpty() || tg.botToken.isEmpty()) return;
            try {
                StringBuilder sb = new StringBuilder("📅 향후 3일 일정\n\n");
				
                // 구글 캘린더
                if (googleCalendarService.isInitialized()) {
                    java.util.List<GoogleCalendarService.CalendarEvent> gEvents =
					googleCalendarService.getNextDays(3);
                    sb.append(GoogleCalendarService.formatEvents("📧 구글", gEvents)).append("\n");
				}
				
                // 네이버 캘린더
                if (naverCalendarService.isInitialized()) {
                    java.util.List<NaverCalendarService.CalendarEvent> nEvents =
					naverCalendarService.getNextDays(3);
                    sb.append(NaverCalendarService.formatEvents("🟢 네이버", nEvents));
				}
				
                String content = sb.toString().trim();
                if (content.isEmpty()) return;
				
                // 텔레그램 전송
                tg.send(tg.myChatId, content);
				
                // 화면 다이얼로그 표시 (FX 스레드)
                javafx.application.Platform.runLater(() ->
				showScheduleDialog("📅 향후 3일 일정", content));
				
				} catch (Exception e) {
                System.out.println("[CalendarInit] 일정 조회 실패: " + e.getMessage());
				AppLogger.logException(e);
			}
		}, "CalendarInit").start();
		
        // 카카오 자동 로그인 후 시작 알림 전송
        // - 카카오 로그인 완료 후 tg.sendStartupNotice() 해야 미러링이 동작함
        // - 카카오 로그인 실패해도 이메일/텔레그램 알림은 반드시 전송
		
        new Thread(() -> {
            if (!kakao.kakaoRestApiKey.isEmpty()
                && !kakao.kakaoClientSecret.isEmpty()
                && !kakao.kakaoRefreshToken.isEmpty()) {
                try {
                    kakao.autoRefreshLogin();
				} catch (Exception e) { AppLogger.logException(e);}
			}
            gmail.sendStartupNotice();
            tg.sendStartupNotice();
		}, "KakaoAutoLogin").start();
		
        shutdownGuard = new AppRestarter.ShutdownGuard(gmail, tg); // 강제 종료 감지 훅 등록
        appRestarter.buildAppCdsIfNeeded(KootPanKingThree::saveConfigStatic);
        shutdownGuard.register();                     // Shutdown Hook (Windows종료/kill/Ctrl+C)
		
        // 텔레그램 폴링 시작 (ini polling=true 인 경우)
        if (tg.polling) tg.startPolling();
	}
	
	private static void saveConfigStatic() {
		if (instance != null) {
			instance.saveConfig();
		}
	}
	static String resolveAppDir() {
        // ── EXE_PATH 탐색 (실행파일 위치 파악용 — 데이터 경로와 무관) ──
        // ① sun.java.command — quoted 경로(공백 포함) 대응 (AppLogger 기준)
		try {
            String sc = System.getProperty("sun.java.command", "").trim();
            String first;
            if (sc.startsWith("\"")) {
                int end = sc.indexOf("\"", 1);
                first = (end > 1) ? sc.substring(1, end) : "";
				} else {
                first = sc.split("\\s+")[0];
			}
			
            if (first.endsWith(".exe")) {
                EXE_PATH = new File(first).getAbsolutePath();
                System.out.println("[AppDir] EXE 감지: " + EXE_PATH);
				} else if (first.endsWith(".jar")) {
                File jarFile = new File(first).getAbsoluteFile();
                File exeCandidate = new File(jarFile.getParentFile(), "KootPanKingThree.exe");
                if (exeCandidate.exists()) {
                    EXE_PATH = exeCandidate.getAbsolutePath();
                    System.out.println("[AppDir] JAR 옆 EXE 감지: " + EXE_PATH);
					} else {
                    EXE_PATH = jarFile.getAbsolutePath();
                    System.out.println("[AppDir] JAR 감지 (EXE 없음): " + EXE_PATH);
				}
			}
		} catch (Exception ignored) {}
		
        // ② CodeSource 폴백 — javaw.exe 반환 시 건너뜀 (AppLogger 기준)
        if (EXE_PATH.isEmpty()) {
            try {
                java.security.CodeSource cs = KootPanKingThree.class.getProtectionDomain().getCodeSource();
                if (cs != null) {
                    File loc = new File(cs.getLocation().toURI()).getAbsoluteFile();
                    String locName = loc.getName().toLowerCase();
                    if (locName.equals("java.exe") || locName.equals("javaw.exe")
                        || locName.equals("java") || locName.equals("javaw")) {
                        // skip
						} else if (loc.isDirectory()) {
                        File exeCandidate = new File(loc, "KootPanKingThree.exe");
                        if (exeCandidate.exists()) {
                            EXE_PATH = exeCandidate.getAbsolutePath();
                            System.out.println("[AppDir] CodeSource(dir) EXE 감지: " + EXE_PATH);
						}
						} else if (locName.endsWith(".jar")) {
                        File exeCandidate = new File(loc.getParentFile(), "KootPanKingThree.exe");
                        if (exeCandidate.exists()) {
                            EXE_PATH = exeCandidate.getAbsolutePath();
							} else {
                            EXE_PATH = loc.getAbsolutePath();
						}
                        System.out.println("[AppDir] CodeSource(jar) 감지: " + EXE_PATH);
						} else {
                        EXE_PATH = loc.getAbsolutePath();
                        System.out.println("[AppDir] CodeSource 감지: " + EXE_PATH);
					}
				}
			} catch (Exception ignored) {}
		}
        // ③ ProcessHandle (Java 9+) - Java 8 비호환으로 생략
		
        // ── 데이터 폴더는 항상 %APPDATA%\KootPanKingThree\ 고정 ──
        // 실행파일(exe/jar) 위치와 무관하게 데이터는 APPDATA 에만 저장
		
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        File dir = new File(appData + File.separator + "KootPanKingThree");
        if (!dir.exists()) dir.mkdirs();
        System.out.println("[AppDir] 데이터 폴더(APPDATA 고정): " + dir.getAbsolutePath());
        return dir.getAbsolutePath() + File.separator;
	}
	
    /**
		* settings 폴더 경로 결정.
		* %APPDATA%\KootPanKingThree\settings\ 로 고정.
		* 재설치 시 삭제되지 않도록 실행 폴더 대신 APPDATA 아래에 위치.
	*/
    private static String resolveSettingsDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        return appData + File.separator + "KootPanKingThree"
		+ File.separator + "settings" + File.separator;
	}
	
    // ═══════════════════════════════════════════════════════════
    //  Config load / save
    // ═══════════════════════════════════════════════════════════
	
    private void loadConfig() {
        if (config == null) config = new Properties();
		
        try {
            // ── 공통 항목 (부모/자식 모두 로드) ──────────────────────
			alwaysOnTop = Boolean.parseBoolean(config.getProperty("alwaysOnTop", "true"));
            showDigital = Boolean.parseBoolean(config.getProperty("showDigital", "true"));
            showNumbers = Boolean.parseBoolean(config.getProperty("showNumbers", "true"));
            theme = config.getProperty("theme", "Light");
            opacity = Float.parseFloat(config.getProperty("opacity", "1.0"));
            cityName = config.getProperty("cityName", "Local");
            String tz = config.getProperty("timeZone", "local");
            timeZone = tz.equals("local") ? java.time.ZoneId.systemDefault() : java.time.ZoneId.of(tz);
            showInterval = Integer.parseInt(config.getProperty("showInterval", "0"));
            animInterval = Integer.parseInt(config.getProperty("animInterval", "0"));
			
            numberFont = new java.awt.Font(
                config.getProperty("fontName", "Georgia"),
                java.awt.Font.BOLD,
                Integer.parseInt(config.getProperty("fontSize", "14"))
			);
            digitalFont = new java.awt.Font(
                config.getProperty("digFontName", "Consolas"),
                java.awt.Font.PLAIN,
                Integer.parseInt(config.getProperty("digFontSize", "14"))
			);
			
            String tc = config.getProperty("tickColor", "");
            if (!tc.isEmpty()) tickColor = new java.awt.Color(Integer.parseInt(tc));
            tickVisible = Boolean.parseBoolean(config.getProperty("tickVisible", "true"));
            secondVisible = Boolean.parseBoolean(config.getProperty("secondVisible", "true"));
			
            String hc = config.getProperty("hourColor", "");
            if (!hc.isEmpty()) hourColor = new java.awt.Color(Integer.parseInt(hc));
            String mc = config.getProperty("minuteColor", "");
            if (!mc.isEmpty()) minuteColor = new java.awt.Color(Integer.parseInt(mc));
            String sc2 = config.getProperty("secondColor", "");
            if (!sc2.isEmpty()) secondColor = new java.awt.Color(Integer.parseInt(sc2));
            String nc = config.getProperty("numberColor", "");
            if (!nc.isEmpty()) numberColor = new java.awt.Color(Integer.parseInt(nc));
            String dc = config.getProperty("digitalColor", "");
            digitalColor = dc.isEmpty() ? java.awt.Color.WHITE : new java.awt.Color(Integer.parseInt(dc));
			
            showLunar = Boolean.parseBoolean(config.getProperty("showLunar", "false"));
			
            String bc = config.getProperty("borderColor", "");
            if (!bc.isEmpty()) borderColor = new java.awt.Color(Integer.parseInt(bc));
            borderWidth = Integer.parseInt(config.getProperty("borderWidth", "-1"));
            borderAlpha = Integer.parseInt(config.getProperty("borderAlpha", "255"));
            borderVisible = Boolean.parseBoolean(config.getProperty("borderVisible", "true"));
			
            galaxyMode = Boolean.parseBoolean(config.getProperty("galaxyMode", "false"));
            try { galaxySpeed = Float.parseFloat(config.getProperty("galaxySpeed", "0.004")); } catch (Exception ignored) {}
            matrixMode = Boolean.parseBoolean(config.getProperty("matrixMode", "false"));
            try { matrixSpeed = Float.parseFloat(config.getProperty("matrixSpeed", "1.5")); } catch (Exception ignored) {}
            matrix2Mode = Boolean.parseBoolean(config.getProperty("matrix2Mode", "false"));
            try { matrix2Speed = Float.parseFloat(config.getProperty("matrix2Speed", "1.5")); } catch (Exception ignored) {}
            matrix3Mode = Boolean.parseBoolean(config.getProperty("matrix3Mode", "false"));
            try { matrix3Speed = Float.parseFloat(config.getProperty("matrix3Speed", "1.5")); } catch (Exception ignored) {}
            rainMode = Boolean.parseBoolean(config.getProperty("rainMode", "false"));
            snowMode = Boolean.parseBoolean(config.getProperty("snowMode", "false"));
            fireMode = Boolean.parseBoolean(config.getProperty("fireMode", "false"));
            sparkleMode = Boolean.parseBoolean(config.getProperty("sparkleMode", "false"));
            bubbleMode = Boolean.parseBoolean(config.getProperty("bubbleMode", "false"));
            neonMode = Boolean.parseBoolean(config.getProperty("neonMode", "false"));
            neonDigital = Boolean.parseBoolean(config.getProperty("neonDigital", "false"));
            digitalNoBg = Boolean.parseBoolean(config.getProperty("digitalNoBg", "false"));
			
            try {
                int r = Integer.parseInt(config.getProperty("clockRadius", "-1"));
                if (r >= 80 && r <= 700) pendingRadius = r;
			} catch (Exception ignored) {}
			
            rainbowSeconds = Integer.parseInt(config.getProperty("rainbowSeconds", "30"));
            pendingRainbowIntervalSec = Double.parseDouble(config.getProperty("rainbowIntervalSec", "0.5"));
            cameraUrl   = config.getProperty("camera.url", "http://192.168.0.100:8080");
            cameraFlipH = Boolean.parseBoolean(config.getProperty("camera.flipH", "false"));
            cameraFlipV = Boolean.parseBoolean(config.getProperty("camera.flipV", "false"));
            ffmpegPath  = config.getProperty("ffmpeg.path", "");
            // 구버전 키 마이그레이션 (MP4.FFMPEG / youtube.ffmpeg.path → ffmpeg.path)
            // 파일이 실제로 존재하는 경로만 채택
            if (ffmpegPath.isEmpty() || !new java.io.File(ffmpegPath).exists()) {
                String leg1 = config.getProperty("MP4.FFMPEG", "");
                String leg2 = config.getProperty("youtube.ffmpeg.path", "");
                if (!leg1.isEmpty() && new java.io.File(leg1).exists())       ffmpegPath = leg1;
                else if (!leg2.isEmpty() && new java.io.File(leg2).exists())  ffmpegPath = leg2;
                if (!ffmpegPath.isEmpty())
                    System.out.println("[Config] ffmpeg.path 마이그레이션: " + ffmpegPath);
            }
            // ITS 교통 CCTV API 키 복원
            { String _itsKey = config.getProperty("its.cctv.apiKey", "");
              if (!_itsKey.isEmpty()) getItsCctv().setApiKey(_itsKey); }
            youtubeUrl     = config.getProperty("youtube.url", "");
            ytdlpPath      = config.getProperty("youtube.ytdlp.path", "");
            localMp4LastFile = config.getProperty("localmp4.lastFile", "");
            localMp4Volume   = Double.parseDouble(config.getProperty("localmp4.volume", "1.0"));
            // ── 시분초 행 설정 ────────────────────────────────────────
            pendingDigitalShow        = Boolean.parseBoolean(config.getProperty("digital.show", "false"));
            pendingDigitalFormatIndex = Integer.parseInt(config.getProperty("digital.formatIndex", "0"));
            pendingDigitalFontFamily  = config.getProperty("digital.fontFamily", "Consolas");
            pendingDigitalFontSize    = Double.parseDouble(config.getProperty("digital.fontSize", "20"));
            pendingDigitalColorRgb    = Integer.parseInt(config.getProperty("digital.colorRgb", String.valueOf(0xFFFFFFFF)));
            pendingDigitalScrollDir   = Integer.parseInt(config.getProperty("digital.scrollDir", "1"));
            pendingDigitalScrollSpeed = Double.parseDouble(config.getProperty("digital.scrollSpeed", "1.5"));
            // ── 날짜 행 설정 ─────────────────────────────────────────
            pendingFaceDateShow        = Boolean.parseBoolean(config.getProperty("faceDate.show", "true"));
            pendingFaceDateFormatIndex = Integer.parseInt(config.getProperty("faceDate.formatIndex", "0"));
            pendingFaceDateFontFamily  = config.getProperty("faceDate.fontFamily", "Consolas");
            pendingFaceDateFontSize    = Double.parseDouble(config.getProperty("faceDate.fontSize", "20"));
            pendingFaceDateColorRgb    = Integer.parseInt(config.getProperty("faceDate.colorRgb", String.valueOf(0xFFFF2222)));
        } catch (Exception ignored) {}

        try {
            // ── 차임벨 설정 로드 (chimeController 생성 전 → pending 보관) ──
            pendingChimeEnabled  = Boolean.parseBoolean(config.getProperty("chimeEnabled", "false"));
            pendingChimeFile     = config.getProperty("chimeFile", "");
            pendingChimeDuration = Integer.parseInt(config.getProperty("chimeDuration", "0"));
            pendingChimeVolume   = Integer.parseInt(config.getProperty("chimeVolume", "80"));
            String minsStr = config.getProperty("chimeMinutes", "0");
            boolean[] loadedMins = new boolean[60];
            if (!minsStr.isEmpty()) {
                for (String s : minsStr.split(",")) {
                    try {
                        int idx = Integer.parseInt(s.trim());
                        if (idx >= 0 && idx < 60) loadedMins[idx] = true;
                    } catch (NumberFormatException ignored2) {}
                }
            }
            pendingChimeMinutes = loadedMins;
        } catch (Exception ignored) {}
		
        try {
            // ── 서비스/계정 설정 로드 ─────────────────────────────
			gmail.from = config.getProperty("gmail.from", "");
            gmail.pass = config.getProperty("gmail.pass", "");
            gmail.lastTo = config.getProperty("gmail.lastTo", "");
			
            kakao.kakaoRestApiKey = config.getProperty("kakao.apiKey", "");
            kakao.kakaoClientSecret = config.getProperty("kakao.clientSecret", "");
            kakao.kakaoRefreshToken = config.getProperty("kakao.refreshToken", "");
			
            tg.botToken = config.getProperty("tg.botToken", "");
            tg.myChatId = config.getProperty("tg.myChatId", "");
            tg.polling = Boolean.parseBoolean(config.getProperty("tg.polling", "false"));
			
            // ── 네이버 캘린더 자격증명 로드 ───────────────────────
            naverCalendarService.setCredentials(
                config.getProperty("naver.caldav.id", ""),
                config.getProperty("naver.caldav.password", "")
			);
			
            if (appRestarter != null) {
                appRestarter.setCachedPaths(
                    config.getProperty("app.exePath", ""),
                    config.getProperty("app.javawPath", ""),
                    config.getProperty("app.jsaPath", "")
				);
			}
		} catch (Exception ignored) {}
	}
	
    void saveConfig() {
        // ── 공통 항목 (부모/자식 모두 저장) ──────────────────────────
		config.setProperty("alwaysOnTop", String.valueOf(alwaysOnTop));
        config.setProperty("showDigital", String.valueOf(showDigital));
        config.setProperty("showNumbers", String.valueOf(showNumbers));
        config.setProperty("theme", theme);
        config.setProperty("opacity", String.valueOf(opacity));
        config.setProperty("cityName", cityName);
        config.setProperty("timeZone", timeZone.getId());
        config.setProperty("showInterval", String.valueOf(showInterval));
        config.setProperty("animInterval", String.valueOf(animInterval));
        config.setProperty("fontName", numberFont.getFamily());
        config.setProperty("fontSize", String.valueOf(numberFont.getSize()));
        config.setProperty("digFontName", digitalFont.getFamily());
        config.setProperty("digFontSize", String.valueOf(digitalFont.getSize()));
        if (tickColor != null) config.setProperty("tickColor", String.valueOf(tickColor.getRGB()));
        config.setProperty("tickVisible", String.valueOf(tickVisible));
        config.setProperty("secondVisible", String.valueOf(secondVisible));
        config.setProperty("hourColor", String.valueOf(hourColor.getRGB()));
        config.setProperty("minuteColor", String.valueOf(minuteColor.getRGB()));
        config.setProperty("secondColor", String.valueOf(secondColor.getRGB()));
        if (numberColor != null) config.setProperty("numberColor", String.valueOf(numberColor.getRGB()));
        config.setProperty("digitalColor", String.valueOf(digitalColor.getRGB()));
        config.setProperty("showLunar", String.valueOf(showLunar));
        config.setProperty("borderWidth", String.valueOf(borderWidth));
        config.setProperty("borderAlpha", String.valueOf(borderAlpha));
        config.setProperty("borderVisible", String.valueOf(borderVisible));
        if (borderColor != null) config.setProperty("borderColor", String.valueOf(borderColor.getRGB()));
		
        config.setProperty("galaxyMode", String.valueOf(galaxyMode));
        config.setProperty("galaxySpeed", String.valueOf(galaxySpeed));
        config.setProperty("matrixMode", String.valueOf(matrixMode));
        config.setProperty("matrixSpeed", String.valueOf(matrixSpeed));
        config.setProperty("matrix2Mode", String.valueOf(matrix2Mode));
        config.setProperty("matrix2Speed", String.valueOf(matrix2Speed));
        config.setProperty("matrix3Mode", String.valueOf(matrix3Mode));
        config.setProperty("matrix3Speed", String.valueOf(matrix3Speed));
        config.setProperty("rainMode", String.valueOf(rainMode));
        config.setProperty("snowMode", String.valueOf(snowMode));
        config.setProperty("fireMode", String.valueOf(fireMode));
        config.setProperty("sparkleMode", String.valueOf(sparkleMode));
        config.setProperty("bubbleMode", String.valueOf(bubbleMode));
        config.setProperty("neonMode", String.valueOf(neonMode));
        config.setProperty("neonDigital", String.valueOf(neonDigital));
        config.setProperty("digitalNoBg", String.valueOf(digitalNoBg));
        config.setProperty("rainbowSeconds", String.valueOf(rainbowSeconds));
        if (clockController != null)
            config.setProperty("rainbowIntervalSec", String.valueOf(clockController.getRainbowInterval()));
        config.setProperty("camera.url",   cameraUrl);
        config.setProperty("camera.flipH", String.valueOf(cameraFlipH));
        config.setProperty("camera.flipV", String.valueOf(cameraFlipV));
        config.setProperty("ffmpeg.path",   ffmpegPath);
        config.setProperty("its.cctv.apiKey", itsCctv != null ? itsCctv.getApiKey() : "");
        config.setProperty("youtube.url", youtubeUrl);
        config.setProperty("youtube.ytdlp.path", ytdlpPath);
        config.setProperty("localmp4.lastFile", localMp4LastFile);
        config.setProperty("localmp4.volume",   String.valueOf(localMp4Volume));

        // ── 차임벨 설정 저장 ─────────────────────────────────
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
		
        // app.exePath 는 부모/자식 모두 저장
        // ★ 우선순위: EXE_PATH(resolveAppDir .exe 직접 감지) > AppLogger > appRestarter 캐시(이전 ini값)
        String exePath = EXE_PATH;
        if (exePath == null || exePath.isEmpty()) {
            String fromLogger = AppLogger.getExeFilePath();
            if (fromLogger != null && !fromLogger.isEmpty() && !fromLogger.equals("(unknown)")) {
                exePath = fromLogger;
			}
		}
        if ((exePath == null || exePath.isEmpty()) && appRestarter != null) {
            exePath = appRestarter.getCachedExePath();
		}
        if (exePath != null && !exePath.isEmpty()) {
            config.setProperty("app.exePath", exePath);
            if (appRestarter != null && !appRestarter.getCachedExePath().equals(exePath)) {
                appRestarter.setCachedPaths(
                    exePath,
                    config.getProperty("app.javawPath", ""),
                    config.getProperty("app.jsaPath", "")
				);
			}
		}
		// ── 부모 전용 항목 ────────────────────────────────────
        config.setProperty("gmail.from", gmail.from);
        config.setProperty("gmail.pass", gmail.pass);
        config.setProperty("gmail.lastTo", gmail.lastTo);
        config.setProperty("kakao.apiKey", kakao.kakaoRestApiKey);
        config.setProperty("kakao.clientSecret", kakao.kakaoClientSecret);
        config.setProperty("kakao.refreshToken", kakao.kakaoRefreshToken);
        config.setProperty("tg.botToken", tg.botToken);
        config.setProperty("tg.myChatId", tg.myChatId);
        config.setProperty("tg.polling", String.valueOf(tg.polling));
        if (appRestarter != null) {
            if (!appRestarter.getCachedJavawPath().isEmpty()) {
                config.setProperty("app.javawPath", appRestarter.getCachedJavawPath());
			}
            if (!appRestarter.getCachedJsaPath().isEmpty()) {
                config.setProperty("app.jsaPath", appRestarter.getCachedJsaPath());
			}
		}
		// ── 파일 저장 (각 인스턴스 자신의 ini 에 기록) ──────────────
        if (iniController != null) {
            iniController.save();
			} else {
            try (FileOutputStream fos = new FileOutputStream(myConfigFile)) {
                config.store(fos, "KootPanKingThree Settings");
			} catch (IOException ignored) {}
		}
	}
	private static void sendStartupNoticeController() {
		// ── Gmail + Telegram: ini 로드 → 시작 알림 ───────────────
        // AppLogger.init() 이 이미 호출된 이후이므로 두 경로 모두 확정 상태
        gmail.exeFilePath = AppLogger.getExeFilePath();
        gmail.logFilePath = AppLogger.getLogFilePath();
		try {
            IniController ini = new IniController(APP_DIR, SETTINGS_DIR, CONFIG_FILE, false, "Local");
            ini.initialize();
            ini.load();
            Properties p = ini.getProperties();
            gmail.from = p.getProperty("gmail.from", "");
            gmail.pass = p.getProperty("gmail.pass", "");
            gmail.lastTo = p.getProperty("gmail.lastTo", "");
            tgMain.botToken = p.getProperty("tg.botToken", "");
            tgMain.myChatId = p.getProperty("tg.myChatId", "");
            tgMain.polling = Boolean.parseBoolean(p.getProperty("tg.polling", "false"));
			} catch (Exception e) {
            System.out.println("[main] ini 로드 실패: " + e.getMessage());
		}
        gmail.sendStartupNotice();  // from/pass/lastTo 미설정 시 내부에서 자동 스킵
		tgMain.sendStartupNotice();  // from/pass/lastTo 미설정 시 내부에서 자동 스킵
	}
    @Override
    public void start(Stage stage) {
        java.util.List<String> rawArgs = getParameters().getRaw();
        String arg1 = rawArgs.size() > 0 ? rawArgs.get(0) : "default1";
        String arg2 = rawArgs.size() > 1 ? rawArgs.get(1) : "default2";
        String arg3 = rawArgs.size() > 2 ? rawArgs.get(2) : "default3";

        // ── 1. SplashWindow 생성 (시계보다 먼저) ─────────────────
        Stage splashStage = new Stage();
        splashWindow = new FxSplashWindow(splashStage);
        splashWindow.log(thisProgramName + " 초기화 중...");

        // ── 2. 시계 생성 ─────────────────────────────────────────
        clockController =
            new FxGPUNeon.ClockController(stage, arg1, arg2, arg3, this::addAppMenuItems);
        clockController.start();

        // ── 3. 디지탈 시계 더블클릭 → 설정 다이얼로그 ────────────
        clockController.setOnDigitalSettingsRequest(() ->
            Platform.runLater(() ->
                showDigitalSettingsDialog((javafx.stage.Stage) stage)));

        // ── 4. ClockHostCallback 주입 ─────────────────────────────
        splashWindow.setClockHost(new FxSplashWindow.ClockHostCallback() {

            @Override public javafx.scene.control.Menu buildGlobalMenu() {
                // Global 서브메뉴 — 자식 시계 목록 (미구현 시 null 반환)
                return null;
            }

            @Override public void exitAll() {
                saveConfig();
                AppLogger.close();
                Platform.exit();
                System.exit(0);
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
                Platform.runLater(() -> {
                    javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION);
                    a.setTitle("About");
                    a.setHeaderText(thisProgramName);
                    a.setContentText("3D 코인 아날로그 시계 — KootPanKingThree\n개발: 김갑수 / 대한민국 서울");
                    a.showAndWait();
                });
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

        splashWindow.log("시계 초기화 완료.");
    }
	
    /** 앱 제어 메뉴 항목을 팝업에 추가 — KootPanKingThree 전담 */
    private void addAppMenuItems(javafx.scene.control.ContextMenu popup) {

        // ── chimeController 초기화 (Stage 확정된 이후 최초 1회) ──
        if (chimeController == null) {
            javafx.stage.Stage owner = (javafx.stage.Stage) popup.getOwnerWindow();
            chimeController = new ChimeController(owner, new ChimeController.HostCallback() {
                @Override public boolean isChild()               { return isChild; }
                @Override public java.time.ZoneId getTimeZone() { return timeZone; }
                @Override public void startRainbow(int durationSec) {
                    if (clockController != null)
                        Platform.runLater(() -> clockController.startRainbow(durationSec));
                }
                // ── 동영상 차임벨 → BackgroundPlayer.YoutubePlayer 연결 ──
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
                                    FxGPUNeon.cameraActive = true;
                                    if (clockController != null) clockController.setCameraFrame(frame);
                                }
                                @Override public void clearYoutubeFrame() {
                                    FxGPUNeon.cameraActive = false;
                                    if (clockController != null) clockController.setCameraFrame(null);
                                }
                                @Override public void onStatusMessage(String message) {
                                    if (clockController != null) clockController.showStatusMessage(message);
                                }
                                @Override public String getSettingsDir() { return SETTINGS_DIR; }
                                @Override public String getYtDlpPath()   { return ytdlpPath; }
                                @Override public String getFfmpegPath()  { return ffmpegPath; }
                            });
                    }
                    return ytPlayer;
                }
            });
            // loadConfig() 에서 임시 보관한 pending 값 적용
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
                FxGPUNeon.AppState st = FxGPUNeon.ClockController.getAppState(clockController);
                st.showDigital        = pendingDigitalShow;
                st.digitalFormatIndex = pendingDigitalFormatIndex;
                st.digitalFontFamily  = pendingDigitalFontFamily;
                st.digitalFontSize    = pendingDigitalFontSize;
                st.digitalColorRgb    = pendingDigitalColorRgb;
                st.digitalScrollDir   = pendingDigitalScrollDir;
                st.digitalScrollSpeed = pendingDigitalScrollSpeed;
                st.showFaceDate       = pendingFaceDateShow;
                st.faceDateFormatIndex= pendingFaceDateFormatIndex;
                st.faceDateFontFamily = pendingFaceDateFontFamily;
                st.faceDateFontSize   = pendingFaceDateFontSize;
                st.faceDateColorRgb   = pendingFaceDateColorRgb;
            }
        }

        // ── 차임벨 메뉴 아이템 ────────────────────────────────
        javafx.scene.control.MenuItem chimeItem =
            new javafx.scene.control.MenuItem("🔔 차임벨 설정...");
        chimeItem.setOnAction(e -> {
            if (!isChild) {
                chimeController.showChimeDialog();
                saveConfig(); // showAndWait() 반환 후 즉시 저장
            }
        });

        javafx.scene.control.Menu phoneCam = new javafx.scene.control.Menu("📷 스마트폰 카메라");

        // ── YouTube 실시간 세계도시 메뉴 ─────────────────────────────
        javafx.scene.control.Menu ytMenu = new javafx.scene.control.Menu("▶ YouTube 실시간 세계도시");

        java.util.List<String[]> ytList = BackgroundPlayer.YoutubePlayer.loadStreamIni(SETTINGS_DIR);
        if (ytList.isEmpty()) {
            javafx.scene.control.MenuItem emptyItem =
                new javafx.scene.control.MenuItem("(목록 없음 - youTubeCctv.ini 확인)");
            emptyItem.setDisable(true);
            ytMenu.getItems().add(emptyItem);
        } else {
            for (String[] entry : ytList) {
                String city   = entry[0];
                String url    = entry[1];
                javafx.scene.control.MenuItem cityItem =
                    new javafx.scene.control.MenuItem(city);
                cityItem.setOnAction(ev -> {
                    // exe 경로 미설정 시 → 메시지만 표시하고 종료
                    String ytdlp  = ytdlpPath;
                    String ffmpeg = ffmpegPath;
                    if (ytdlp.isEmpty() || !new java.io.File(ytdlp).exists()
                     || ffmpeg.isEmpty() || !new java.io.File(ffmpeg).exists()) {
                        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.INFORMATION);
                        alert.setTitle("YouTube 설정 필요");
                        alert.setHeaderText(null);
                        alert.setContentText(
                            "[YouTube 스트림 URL & 설정] 메뉴에서\n" +
                            "(yt-dlp.exe) (ffmpeg.exe) 실행 파일 경로 설정을 미리 하세요.");
                        alert.initOwner((javafx.stage.Stage) popup.getOwnerWindow());
                        alert.showAndWait();
                        return;
                    }
                    youtubeUrl = url;
                    stopCamera(); stopItsCctv();
                    startYoutube(url);
                    saveConfig();
                });
                ytMenu.getItems().add(cityItem);
            }
        }

        ytMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());

        javafx.scene.control.MenuItem ytUrlItem =
            new javafx.scene.control.MenuItem("🔗 URL직접입력 & YouTube설정");
        ytUrlItem.setOnAction(ev -> {
            javafx.stage.Stage owner = (javafx.stage.Stage) popup.getOwnerWindow();
            showYoutubeSettingsDialog(owner);
        });

        javafx.scene.control.MenuItem ytDlItem =
            new javafx.scene.control.MenuItem("⬇ 도시 목록 다운로드");
        ytDlItem.setOnAction(ev ->
            new Thread(() -> {
                BackgroundPlayer.YoutubePlayer.downloadIni(SETTINGS_DIR);
                Platform.runLater(() -> {
                    javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION);
                    a.setTitle("목록 다운로드");
                    a.setContentText("youTubeCctv.ini 다운로드 완료.\n메뉴를 다시 열면 반영됩니다.");
                    a.initOwner((javafx.stage.Stage) popup.getOwnerWindow());
                    a.showAndWait();
                });
            }, "YT-INI-Download").start()
        );

        javafx.scene.control.MenuItem ytStopItem =
            new javafx.scene.control.MenuItem("⏹ 스트림 정지");
        ytStopItem.setOnAction(ev -> stopYoutube());

        ytMenu.getItems().addAll(ytUrlItem, ytDlItem,
            new javafx.scene.control.SeparatorMenuItem(), ytStopItem);

        ytMenu.setOnShowing(ev ->
            ytStopItem.setDisable(ytPlayer == null || !ytPlayer.isRunning()));

        // ── 로컬 MP4 배경 재생 메뉴 ────────────────────────────────
        javafx.scene.control.Menu localMp4Menu =
            new javafx.scene.control.Menu("📂 로컬 MP4 배경 재생");

        javafx.scene.control.MenuItem mp4OpenItem =
            new javafx.scene.control.MenuItem("📁 파일 선택...");
        mp4OpenItem.setOnAction(ev -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("배경으로 재생할 동영상 파일 선택");
            fc.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter(
                    "동영상 파일", "*.mp4", "*.m4v", "*.mov", "*.mkv", "*.avi", "*.webm"),
                new javafx.stage.FileChooser.ExtensionFilter("모든 파일", "*.*")
            );
            // 마지막 경로 기억
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
            new javafx.scene.control.MenuItem("🔄 마지막 파일 다시 재생");
        mp4ReplayItem.setOnAction(ev -> {
            if (localMp4LastFile.isEmpty()) return;
            File f = new File(localMp4LastFile);
            if (!f.exists()) {
                javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING);
                a.setTitle("파일 없음");
                a.setContentText("마지막 파일을 찾을 수 없습니다:\n" + localMp4LastFile);
                a.initOwner((javafx.stage.Stage) popup.getOwnerWindow());
                a.showAndWait();
                return;
            }
            startLocalMp4(f);
        });

        javafx.scene.control.MenuItem mp4StopItem =
            new javafx.scene.control.MenuItem("⏹ MP4 재생 정지");
        mp4StopItem.setOnAction(ev -> stopLocalMp4());

        // ── 볼륨 슬라이더 ──────────────────────────────────────────
        javafx.scene.control.Label volLabel =
            new javafx.scene.control.Label(
                String.format("🔊 볼륨: %d%%", (int)(localMp4Volume * 100)));
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
            volLabel.setText(String.format("🔊 볼륨: %d%%", (int)(localMp4Volume * 100)));
            if (ytPlayer != null) ytPlayer.setVolume(localMp4Volume);
        });

        // 🔇 음소거 토글 버튼
        javafx.scene.control.Button muteBtn = new javafx.scene.control.Button("🔇 음소거");
        muteBtn.setStyle("-fx-font-size:11px;");
        muteBtn.setOnAction(ev -> {
            if (localMp4Volume > 0.0) {
                // 현재 볼륨 저장 후 0으로
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

		
        javafx.scene.control.MenuItem camStart      = new javafx.scene.control.MenuItem("▶ 폰 카메라 연결");
        javafx.scene.control.MenuItem camSnapshot   = new javafx.scene.control.MenuItem("📸 이미지 저장");
        camVideoItem = new javafx.scene.control.MenuItem("🎬 동영상 녹화 시작");
        javafx.scene.control.MenuItem camCapture    = camVideoItem; // 별칭 (하위 코드 호환)
        javafx.scene.control.MenuItem camStop       = new javafx.scene.control.MenuItem("⏹ 폰 카메라 중지");
		
        // 초기 활성화 상태: 연결 중일 때만 이미지 저장/중지 활성화
        camSnapshot .setDisable(!cameraMode);
        camVideoItem.setDisable(!cameraMode);
        camStop     .setDisable(!cameraMode);
		
        camStart.setOnAction(e -> {
            // FX 스레드에서 커스텀 카메라 설정 다이얼로그 표시
            javafx.stage.Stage owner = (javafx.stage.Stage) popup.getOwnerWindow();
			
            javafx.stage.Stage dlg = new javafx.stage.Stage();
            dlg.initOwner(owner);
            dlg.initStyle(javafx.stage.StageStyle.UTILITY);
            dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dlg.setAlwaysOnTop(true);
            dlg.setTitle("스마트폰 카메라 설정");
			
            // 안내 텍스트
            javafx.scene.control.Label header = new javafx.scene.control.Label(
                "1. 스마트폰에서 [IP Webcam] 앱을 실행하세요\n" +
                "2. 앱 화면을 맨 아래로 스크롤하세요\n" +
                "3. [서버 시작]을 누르세요\n" +
			"4. 표시되는 주소를 입력하세요  예: http://192.168.0.70:8080");
            header.setStyle("-fx-font-size:12px;");
			
            // 주소 입력 필드
            javafx.scene.control.Label urlLabel = new javafx.scene.control.Label("주소:");
            javafx.scene.control.TextField urlField = new javafx.scene.control.TextField(cameraUrl);
            urlField.setPrefWidth(320);
			
            javafx.scene.layout.HBox urlRow = new javafx.scene.layout.HBox(8, urlLabel, urlField);
            urlRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
			
            // 반전 체크박스
            javafx.scene.control.CheckBox cbFlipH = new javafx.scene.control.CheckBox("좌우 반전");
            cbFlipH.setSelected(cameraFlipH);
            javafx.scene.control.CheckBox cbFlipV = new javafx.scene.control.CheckBox("상하 반전");
            cbFlipV.setSelected(cameraFlipV);
			
            javafx.scene.layout.HBox flipRow = new javafx.scene.layout.HBox(16, cbFlipH, cbFlipV);
            flipRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
			
            // ── 동영상 도구(FFmpeg) 지정 행 ──────────────────────
            javafx.scene.control.Label ffmpegLabel = new javafx.scene.control.Label(
			"동영상 도구(ffmpeg.path): " + (ffmpegPath.isEmpty() ? "(미지정)" : ffmpegPath));
            ffmpegLabel.setStyle("-fx-font-size:11px; -fx-text-fill:#555;");
            ffmpegLabel.setWrapText(true);
            ffmpegLabel.setMaxWidth(380);
			
            javafx.scene.control.Button btnFfmpeg = new javafx.scene.control.Button("동영상 도구 지정");
            btnFfmpeg.setOnAction(ev -> {
                javafx.stage.FileChooser ffChooser = new javafx.stage.FileChooser();
                ffChooser.setTitle("ffmpeg.exe 선택 — ffmpeg.path 에 저장됩니다");
                boolean isWin2 = System.getProperty("os.name", "").toLowerCase().contains("win");
                ffChooser.getExtensionFilters().addAll(
                    new javafx.stage.FileChooser.ExtensionFilter(
					isWin2 ? "ffmpeg (ffmpeg.exe)" : "ffmpeg", isWin2 ? "ffmpeg.exe" : "ffmpeg"),
				new javafx.stage.FileChooser.ExtensionFilter("모든 파일", "*.*"));
                // 초기 디렉터리: 기존 경로 부모 → C:fmpegin → user.home
                File initFf = ffmpegPath.isEmpty() ? null : new File(ffmpegPath).getParentFile();
                if (initFf == null || !initFf.exists())
				initFf = isWin2 ? new File("C:\\ffmpeg\\bin") : new File("/usr/local/bin");
                if (initFf == null || !initFf.exists())
				initFf = new File(System.getProperty("user.home"));
                ffChooser.setInitialDirectory(initFf.exists() ? initFf : null);
                File chFf = ffChooser.showOpenDialog(dlg);
                if (chFf != null && chFf.isFile()) {
                    ffmpegPath = chFf.getAbsolutePath();
                    saveConfig();
                    ffmpegLabel.setText("동영상 도구(ffmpeg.path): " + ffmpegPath);
                    System.out.println("[FFmpeg] 지정 완료: " + ffmpegPath);
				}
			});
			
            javafx.scene.layout.HBox ffmpegRow = new javafx.scene.layout.HBox(8, btnFfmpeg, ffmpegLabel);
            ffmpegRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
			
            // 확인 / 취소 버튼
            javafx.scene.control.Button btnOk     = new javafx.scene.control.Button("확인");
            javafx.scene.control.Button btnCancel = new javafx.scene.control.Button("취소");
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
                cameraUrl   = url;
                cameraFlipH = cbFlipH.isSelected();
                cameraFlipV = cbFlipV.isSelected();
                // 반전 플래그를 FxGPUNeon 에 전달
                FxGPUNeon.cameraFlipH = cameraFlipH;
                FxGPUNeon.cameraFlipV = cameraFlipV;
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
		
        // ── 이미지 저장 (단일 프레임 스냅샷) ──────────────────────────
        camSnapshot.setOnAction(e -> {
            if (!cameraMode) {
                showAlert("이미지 저장", "카메라가 실행 중이 아닙니다.");
                return;
			}
            captureCamera((javafx.stage.Stage) popup.getOwnerWindow());
		});
		
        // ── 동영상 녹화 시작 / 중지 토글 ──────────────────────────────
        camVideoItem.setOnAction(e -> {
            if (!cameraMode) {
                showAlert("동영상 녹화", "카메라가 실행 중이 아닙니다.");
                return;
			}
            if (videoRecording) {
                // MP4 녹화 중 → 중지
                stopVideoRecording();
				} else if (imageSeqRecording) {
                // 이미지 시퀀스 저장 중 → 중지
                stopImageSequenceRecording();
				} else {
                // 녹화 시작: 저장 경로 선택 다이얼로그
                javafx.stage.Stage owner = (javafx.stage.Stage) popup.getOwnerWindow();
                javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
                fc.setTitle("동영상 저장 위치 선택");
                fc.getExtensionFilters().add(
				new javafx.stage.FileChooser.ExtensionFilter("MP4 동영상", "*.mp4"));
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                fc.setInitialFileName("cam_" + ts + ".mp4");
                fc.setInitialDirectory(new File(APP_DIR));
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
            camVideoItem.setText("🎬 동영상 녹화 시작");
		});
		
        javafx.scene.control.MenuItem camInstall = new javafx.scene.control.MenuItem("📲 IP WebCam 설치 (Play Store)");
        camInstall.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(
				"https://play.google.com/store/apps/details?id=com.pas.webcam&pcampaignid=web_share"));
				} catch (Exception ex) {
                System.out.println("[CameraInstall] 브라우저 열기 실패: " + ex.getMessage());
			}
		});
		
        javafx.scene.control.MenuItem camGuide = new javafx.scene.control.MenuItem("📖 사용방법 안내");
        camGuide.setOnAction(e -> {
            final String GUIDE_URL     = "https://blog.naver.com/garpsu/224213426659";
            final String GUIDE_MSG     = "PC와 스마트폰 카메라 연결 방법 안내 : " + GUIDE_URL;
            final String GUIDE_SUBJECT = "[끝판왕] PC와 스마트폰 카메라 연결 방법 안내";
            // ① 브라우저 열기
            new Thread(() -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(GUIDE_URL));
					} catch (Exception ex) {
                    System.out.println("[CameraGuide] 브라우저 열기 실패: " + ex.getMessage());
				}
			}, "CameraGuide-Browser").start();
            // ② 텔레그램 전송
            boolean tgOk = tg.polling && !tg.botToken.isEmpty() && !tg.myChatId.isEmpty();
            if (tgOk) {
                new Thread(() -> {
                    try { tg.send(tg.myChatId, GUIDE_MSG);
                        System.out.println("[CameraGuide] 텔레그램 전송 완료");
						} catch (Exception ex) {
                        System.out.println("[CameraGuide] 텔레그램 전송 실패: " + ex.getMessage());
					}
				}, "CameraGuide-Telegram").start();
			}
            // ③ 이메일 전송
            boolean emailOk = gmail.isConfigured() && !gmail.lastTo.isEmpty();
            if (emailOk) {
                new Thread(() -> {
                    try { gmail.sendOneSmtp(gmail.lastTo, GUIDE_SUBJECT, GUIDE_MSG);
						System.out.println("[CameraGuide] 이메일 전송 완료");
						} catch (Exception ex) {
                        System.out.println("[CameraGuide] 이메일 전송 실패: " + ex.getMessage());
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
		
        javafx.scene.control.Menu cctv = new javafx.scene.control.Menu("🚦 ITS 교통 CCTV");

        // ── (A) API 키 설정 ──────────────────────────────────────────────
        javafx.scene.control.MenuItem cctvKeyItem =
            new javafx.scene.control.MenuItem("🔑 API 키 설정...");
        cctvKeyItem.setOnAction(e -> {
            if (isChild) return;
            ItsCctvManager mgr = getItsCctv();
            String cur = mgr.getApiKey();

            javafx.scene.control.TextField keyField =
                new javafx.scene.control.TextField(cur);
            keyField.setPrefColumnCount(36);

            javafx.scene.control.Button fetchBtn =
                new javafx.scene.control.Button("🌐 서버에서 키 값 수신");
            fetchBtn.setOnAction(ev -> {
                fetchBtn.setDisable(true);
                fetchBtn.setText("⏳ 수신 중...");
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
                        Platform.runLater(() -> fetchBtn.setText("✅ 수신 완료"));
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            fetchBtn.setText("❌ 수신 실패");
                            fetchBtn.setDisable(false);
                        });
                    }
                }, "ItsKeyFetch").start();
            });

            javafx.scene.control.Label label = new javafx.scene.control.Label(
                "ITS 국가교통정보센터 API 키\n" +
                "발급: https://www.its.go.kr\n" +
                "회원가입 → 오픈데이터 → CCTV 화상자료 → 인증키 신청\n" +
                "현재 키: " + (cur.isEmpty() ? "(없음)"
                    : cur.substring(0, Math.min(8, cur.length())) + "..."));
            label.setWrapText(true);

            javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8,
                label, keyField, fetchBtn);
            content.setPadding(new javafx.geometry.Insets(10));

            javafx.scene.control.Dialog<String> dlg = new javafx.scene.control.Dialog<>();
            dlg.setTitle("ITS API 키 설정");
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
                mgr.setApiKey(input);
                saveConfig();
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("ITS CCTV");
                alert.setHeaderText(null);
                alert.setContentText("API 키가 저장되었습니다.");
                if (owner != null) alert.initOwner(owner);
                alert.showAndWait();
            });
        });

        // ── (B) 목록 조회 및 연결 ────────────────────────────────────────
        javafx.scene.control.MenuItem cctvConnectItem =
            new javafx.scene.control.MenuItem("▶ CCTV 목록 조회 및 연결");
        cctvConnectItem.setDisable(itsCctv == null || getItsCctv().getApiKey().isEmpty());

        cctvConnectItem.setOnAction(e -> {
            if (isChild) return;
            ItsCctvManager mgr = getItsCctv();
            if (mgr.getApiKey().isEmpty()) {
                javafx.scene.control.Alert warn = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING);
                warn.setTitle("ITS CCTV");
                warn.setContentText("먼저 API 키를 설정하세요.");
                warn.showAndWait();
                return;
            }
            cctvConnectItem.setDisable(true);
            cctvConnectItem.setText("⏳ 조회 중...");

            mgr.fetchList(
                () -> {
                    cctvConnectItem.setDisable(false);
                    cctvConnectItem.setText("▶ CCTV 목록 조회 및 연결");
                    java.util.List<ItsCctvManager.CctvItem> fetched = mgr.getItems();
                    if (fetched.isEmpty()) {
                        javafx.scene.control.Alert warn = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.WARNING);
                        warn.setTitle("ITS CCTV");
                        warn.setContentText("조회된 CCTV가 없습니다.");
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
                    cctvConnectItem.setText("▶ CCTV 목록 조회 및 연결");
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("ITS CCTV 오류");
                    alert.setContentText("CCTV 조회 실패:\n" + err
                        + "\n\nAPI 키와 네트워크 연결을 확인하세요.");
                    alert.showAndWait();
                }
            );
        });

        // ── (C) 이전 / 다음 카메라 ──────────────────────────────────────
        javafx.scene.control.MenuItem cctvPrev =
            new javafx.scene.control.MenuItem("◀ 이전 카메라");
        cctvPrev.setOnAction(e -> {
            ItsCctvManager mgr = getItsCctv();
            if (mgr.isRunning() && !mgr.getItems().isEmpty()) {
                mgr.prev();
                System.out.println("[ItsCctv] 이전: " + mgr.currentName());
            }
        });

        javafx.scene.control.MenuItem cctvNext =
            new javafx.scene.control.MenuItem("▶ 다음 카메라");
        cctvNext.setOnAction(e -> {
            ItsCctvManager mgr = getItsCctv();
            if (mgr.isRunning() && !mgr.getItems().isEmpty()) {
                mgr.next();
                System.out.println("[ItsCctv] 다음: " + mgr.currentName());
            }
        });

        // ── (D) 중지 ────────────────────────────────────────────────────
        javafx.scene.control.MenuItem cctvStop =
            new javafx.scene.control.MenuItem("⏹ CCTV 중지");
        cctvStop.setOnAction(e -> stopItsCctv());

        // ── (E) ITS API 신청 안내 ────────────────────────────────────────
        javafx.scene.control.MenuItem cctvGuide =
            new javafx.scene.control.MenuItem("📖 ITS API 신청 안내");
        cctvGuide.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(
                    new java.net.URI("https://www.its.go.kr/opendata/opendataList?service=cctv"));
            } catch (Exception ex) {
                System.out.println("[ItsCctv] 브라우저 열기 실패: " + ex.getMessage());
            }
        });

        cctv.setOnShowing(e -> {
            ItsCctvManager mgr = getItsCctv();
            boolean running = mgr.isRunning();
            boolean hasItems = !mgr.getItems().isEmpty();
            cctvConnectItem.setDisable(mgr.getApiKey().isEmpty());
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
		
        // ── Gmail / Calendar 메뉴 (3)(4) ────────────────────────────────
        javafx.scene.control.Menu gmailMenu = new javafx.scene.control.Menu("📧 Gmail / Calendar");
		
        // 기존 항목
        javafx.scene.control.MenuItem gmailSend = menuItem("지금 Gmail 보내기");
        javafx.scene.control.MenuItem calGuide  = menuItem("Calendar 설정 안내");
		
        // 구글 캘린더 서브메뉴 (3)
        javafx.scene.control.Menu googleCal = new javafx.scene.control.Menu("📧 구글 캘린더");
        googleCal.getItems().addAll(
            calMenuAction("향후 3일", () -> showCalendarResult("구글", "google", 3, "next")),
            calMenuAction("향후 7일", () -> showCalendarResult("구글", "google", 7, "next")),
            calMenuAction("지난 7일", () -> showCalendarResult("구글", "google", 7, "past")),
            calMenuAction("이번 달",  () -> showCalendarResult("구글", "google", 0, "month")),
            calMenuAction("다음 달",  () -> showCalendarResult("구글", "google", 0, "nextmonth"))
		);
		
        // 네이버 캘린더 서브메뉴 (4)
        javafx.scene.control.Menu naverCal = new javafx.scene.control.Menu("🟢 네이버 캘린더");
        naverCal.getItems().addAll(
            calMenuAction("향후 3일", () -> showCalendarResult("네이버", "naver", 3, "next")),
            calMenuAction("향후 7일", () -> showCalendarResult("네이버", "naver", 7, "next")),
            calMenuAction("지난 7일", () -> showCalendarResult("네이버", "naver", 7, "past")),
            calMenuAction("이번 달",  () -> showCalendarResult("네이버", "naver", 0, "month")),
            calMenuAction("다음 달",  () -> showCalendarResult("네이버", "naver", 0, "nextmonth"))
		);
		
        javafx.scene.control.MenuItem naverCfg = menuItem("네이버 캘린더 설정");
		
        gmailMenu.getItems().addAll(
            gmailSend,
            calGuide,
            new javafx.scene.control.SeparatorMenuItem(),
            googleCal,
            new javafx.scene.control.SeparatorMenuItem(),
            naverCal,
            new javafx.scene.control.SeparatorMenuItem(),
            naverCfg
		);
		
        javafx.scene.control.Menu kakaoMenu = new javafx.scene.control.Menu("카카오톡...");
        kakaoMenu.getItems().addAll(
            menuItem("카카오 로그인됨"),
            menuItem("나에게 메시지 보내기..."),
            menuItem("설정 안내...")
		);
		
        // ── 텔레그램 (tg 직접 접근 가능) ────────────────────────
        javafx.scene.control.Menu telegramMenu = new javafx.scene.control.Menu("텔레그램");
        javafx.scene.control.MenuItem tgSettings = new javafx.scene.control.MenuItem("텔레그램 설정...");
        javafx.scene.control.MenuItem tgHelp     = new javafx.scene.control.MenuItem("텔레그램 설정 안내");
        tgSettings.setOnAction(e -> tg.showTelegramDialog((javafx.stage.Stage) popup.getOwnerWindow()));
        tgHelp.setOnAction(e ->     tg.showTelegramHelp((javafx.stage.Stage) popup.getOwnerWindow()));
        telegramMenu.getItems().addAll(tgSettings, tgHelp);
		
        // ── 시스템 ───────────────────────────────────────────────
        javafx.scene.control.Menu system = new javafx.scene.control.Menu("시스템...");
        javafx.scene.control.MenuItem logItem = new javafx.scene.control.MenuItem("Log");
        logItem.setOnAction(e -> openLogFile());
        javafx.scene.control.MenuItem configItem = new javafx.scene.control.MenuItem("기본설정파일...");
        configItem.setOnAction(e -> openConfigFile());
        javafx.scene.control.MenuItem iniItem = new javafx.scene.control.MenuItem("ini 다운로드");
        iniItem.setOnAction(e -> downloadIniFile());

        // ── 부팅 자동 실행 (CheckMenuItem) ──────────────────────
        javafx.scene.control.CheckMenuItem autoStartItem =
            new javafx.scene.control.CheckMenuItem("PC 부팅 시 자동 실행");
        autoStartItem.setSelected(isAutoStartRegistered());
        autoStartItem.setOnAction(e -> toggleAutoStart(autoStartItem,
            (javafx.stage.Stage) popup.getOwnerWindow()));

        // ── EXIT (15초 타이머 확인 다이얼로그) ───────────────────
        javafx.scene.control.MenuItem exitItem = new javafx.scene.control.MenuItem("EXIT");
        exitItem.setOnAction(e -> showExitDialog((javafx.stage.Stage) popup.getOwnerWindow()));

        javafx.scene.control.MenuItem aboutItem = new javafx.scene.control.MenuItem("About");
        aboutItem.setOnAction(e -> {
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
            a.initOwner((javafx.stage.Stage) popup.getOwnerWindow());
            a.setTitle("About");
            a.setHeaderText(thisProgramName);
            a.setContentText("3D 코인 아날로그 시계 — KootPanKingThree\n개발: 김갑수 / 대한민국 서울");
            a.showAndWait();
        });

        javafx.scene.control.MenuItem mainWindowItem =
            new javafx.scene.control.MenuItem("MainWindow");
        mainWindowItem.setOnAction(e -> {
            if (splashWindow != null) {
                splashWindow.getStage().show();
                splashWindow.getStage().toFront();
            }
        });

        javafx.scene.control.MenuItem closeItem = new javafx.scene.control.MenuItem("Close");
        closeItem.setOnAction(e -> {
            if (splashWindow != null) splashWindow.getStage().hide();
        });

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
            iniItem,
            new javafx.scene.control.SeparatorMenuItem(),
            menuItem("트레이로 보내기"),
            autoStartItem,
            new javafx.scene.control.SeparatorMenuItem(),
            mainWindowItem,
            new javafx.scene.control.SeparatorMenuItem(),
            closeItem,
            restartItem,
            exitItem
        );

        // ── 생활도구 ─────────────────────────────────────────────
        javafx.scene.control.Menu lifeMenu = buildLifeMenu(popup);

        // ── 디지탈 시계 토글 + 설정 ──────────────────────────────
        javafx.scene.control.CheckMenuItem digitalItem =
            new javafx.scene.control.CheckMenuItem("🕐 시계 면 디지탈 on/off");
        digitalItem.setSelected(clockController != null && getDigitalState());
        digitalItem.setOnAction(e -> {
            boolean on = digitalItem.isSelected();
            setDigitalState(on);
            saveConfig();
        });

        javafx.scene.control.MenuItem digitalSettingsItem =
            new javafx.scene.control.MenuItem("디지탈 시계 설정");
        digitalSettingsItem.setOnAction(e ->
            showDigitalSettingsDialog((javafx.stage.Stage) popup.getOwnerWindow()));

        // ── 중앙 고정 (최상단) ────────────────────────────────────
        javafx.scene.control.MenuItem centerItem = new javafx.scene.control.MenuItem("📌 중앙 고정");
        centerItem.setOnAction(e -> resetToCenter());

        popup.getItems().addAll(
            centerItem,
            new javafx.scene.control.SeparatorMenuItem(),
            phoneCam, ytMenu, localMp4Menu, cctv,
            new javafx.scene.control.SeparatorMenuItem(),
            digitalItem,
            digitalSettingsItem,
            new javafx.scene.control.SeparatorMenuItem(),
            chimeItem,
            new javafx.scene.control.SeparatorMenuItem(),
            gmailMenu, kakaoMenu, telegramMenu,
            new javafx.scene.control.SeparatorMenuItem(),
            lifeMenu,
            new javafx.scene.control.SeparatorMenuItem(),
            system
        );
    }

    // ── SplashWindow ClockHostCallback용 메뉴 빌더 ──────────────
    /** Gmail/Calendar 메뉴 — FxSplashWindow ClockHostCallback 에서 호출 */
    private javafx.scene.control.Menu buildGmailMenu() {
        javafx.scene.control.Menu menu =
            new javafx.scene.control.Menu("📧 Gmail / Calendar");
        javafx.scene.control.MenuItem send = new javafx.scene.control.MenuItem("지금 Gmail 보내기");
        javafx.scene.control.MenuItem guide = new javafx.scene.control.MenuItem("Calendar 설정 안내");
        javafx.scene.control.Menu googleCal =
            new javafx.scene.control.Menu("📧 구글 캘린더");
        googleCal.getItems().addAll(
            calMenuAction("향후 3일", () -> showCalendarResult("구글","google",3,"next")),
            calMenuAction("향후 7일", () -> showCalendarResult("구글","google",7,"next")),
            calMenuAction("지난 7일", () -> showCalendarResult("구글","google",7,"past")),
            calMenuAction("이번 달",  () -> showCalendarResult("구글","google",0,"month")),
            calMenuAction("다음 달",  () -> showCalendarResult("구글","google",0,"nextmonth"))
        );
        javafx.scene.control.Menu naverCal =
            new javafx.scene.control.Menu("🟢 네이버 캘린더");
        naverCal.getItems().addAll(
            calMenuAction("향후 3일", () -> showCalendarResult("네이버","naver",3,"next")),
            calMenuAction("향후 7일", () -> showCalendarResult("네이버","naver",7,"next")),
            calMenuAction("지난 7일", () -> showCalendarResult("네이버","naver",7,"past")),
            calMenuAction("이번 달",  () -> showCalendarResult("네이버","naver",0,"month")),
            calMenuAction("다음 달",  () -> showCalendarResult("네이버","naver",0,"nextmonth"))
        );
        javafx.scene.control.MenuItem naverCfg =
            new javafx.scene.control.MenuItem("네이버 캘린더 설정");
        menu.getItems().addAll(
            send, guide, new javafx.scene.control.SeparatorMenuItem(),
            googleCal, new javafx.scene.control.SeparatorMenuItem(),
            naverCal,  new javafx.scene.control.SeparatorMenuItem(), naverCfg);
        return menu;
    }

    /** 카카오톡 메뉴 — FxSplashWindow ClockHostCallback 에서 호출 */
    private javafx.scene.control.Menu buildKakaoMenuFx() {
        javafx.scene.control.Menu menu =
            new javafx.scene.control.Menu("카카오톡...");
        menu.getItems().addAll(
            menuItem("카카오 로그인됨"),
            menuItem("나에게 메시지 보내기..."),
            menuItem("설정 안내..."));
        return menu;
    }

    /** 텔레그램 메뉴 — FxSplashWindow ClockHostCallback 에서 호출 */
    private javafx.scene.control.Menu buildTelegramMenuFx() {
        javafx.scene.control.Menu menu =
            new javafx.scene.control.Menu("텔레그램");
        javafx.scene.control.MenuItem settings =
            new javafx.scene.control.MenuItem("텔레그램 설정...");
        javafx.scene.control.MenuItem help =
            new javafx.scene.control.MenuItem("텔레그램 설정 안내");
        settings.setOnAction(e -> {
            if (splashWindow != null)
                tg.showTelegramDialog(splashWindow.getStage());
        });
        help.setOnAction(e -> {
            if (splashWindow != null)
                tg.showTelegramHelp(splashWindow.getStage());
        });
        menu.getItems().addAll(settings, help);
        return menu;
    }
	
    // ══════════════════════════════════════════════════════════════════
    //  스마트폰 카메라 기능
    // ══════════════════════════════════════════════════════════════════
	
    /** IP Webcam 스트림 연결 시작 — 수신 프레임을 시계 배경 이미지로 직접 주입 */
    void startCamera(String streamUrl) {
        if (isChild) return;
        // ── YouTube WebView 배경과 중복 표시 방지 ─────────────────
        stopYoutube();
        if (camera != null) camera.stop();
        // cameraActive 플래그를 먼저 세팅: 이후 콜백이 유효함을 표시
        if (clockController != null) FxGPUNeon.cameraActive = true;
        camera = new CaptureManager.Camera(frame -> {
            // Camera-Reader(백그라운드) 스레드 → FX 스레드로 위임
            // setCameraFrame 내부에서 cameraActive == false 이면 즉시 반환(중지 후 큐 잔류 차단)
            if (clockController != null) {
                Platform.runLater(() -> clockController.setCameraFrame(frame));
			}
            // ── 동영상 녹화: 백그라운드 스레드에서 직접 프레임 저장 ──
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
            // ── 5초 간격 이미지 시퀀스 저장 ──────────────────────
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
                            System.out.println("[ImgSeq] 저장: " + imgFile.getName());
						} catch (Exception ignored) {}
					}
				}
			}
		});
        cameraMode = true;
        camera.start(streamUrl);
        System.out.println("[Camera] 연결 시작: " + streamUrl);
	}
	
    /** 카메라 스트림 중지 및 상태 초기화 */
    void stopCamera() {
        // 녹화 중이면 먼저 마무리
        stopVideoRecording();
        stopImageSequenceRecording();
        // ① cameraActive 를 먼저 false 로 설정 —
        //    이후 Platform.runLater 큐에서 꺼내지는 프레임 콜백이
        //    setCameraFrame 내부의 if (!cameraActive) return; 에 걸려 차단된다.
        if (clockController != null) FxGPUNeon.cameraActive = false;
        if (camera != null) {
            camera.stop();
            camera = null;
		}
        cameraMode = false;
        // ② 배경 완전 초기화 — FX 스레드(메뉴 액션)에서 직접 동기 호출
        //    슬라이드쇼 [중지] 버튼과 동일한 패턴: Platform.runLater 없음
        if (clockController != null) {
            clockController.setCameraFrame(null);
		}
        System.out.println("[Camera] 중지");
	}

    // ══════════════════════════════════════════════════════════════════
    //  ITS 교통 CCTV 기능
    // ══════════════════════════════════════════════════════════════════

    /** lazy-init: 첫 접근 시 HostCallback 을 주입하여 ItsCctvManager 생성 */
    ItsCctvManager getItsCctv() {
        if (itsCctv == null) {
            itsCctv = new ItsCctvManager(new ItsCctvManager.HostCallback() {
                @Override
                public void setItsCctvImage(String label,
                                             javafx.scene.image.WritableImage image) {
                    // Platform.runLater 로 FX 스레드에서 호출됨이 보장됨
                    if (clockController == null) return;
                    if (image == null) {
                        // 중지: 카메라 중지와 동일한 패턴으로 배경 초기화
                        FxGPUNeon.cameraActive = false;
                        clockController.setCameraFrame(null);
                    } else {
                        // 활성: WritableImage 를 카메라 프레임 경로로 주입
                        FxGPUNeon.cameraActive = true;
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
        if (isChild) return;
        // ── YouTube WebView 배경과 중복 표시 방지 ─────────────────
        stopYoutube();
        // CCTV 시작 전 현재 배경 상태를 스냅샷으로 저장 (중지 시 원상복귀용)
        if (clockController != null) clockController.saveBackgroundSnapshot();
        stopCamera();                   // 카메라 스트림 중지 (배경 초기화 포함)
        FxGPUNeon.cameraActive = true;  // CCTV 이미지 주입 허용
        getItsCctv().start();
    }

    /**
     * ITS CCTV 중지.
     * 타이머를 중지하고 배경을 초기화한다.
     * FX 스레드에서 호출해야 한다.
     */
    void stopItsCctv() {
        if (itsCctv != null && itsCctv.isRunning()) {
            itsCctv.stop();
        }
        FxGPUNeon.cameraActive = false;
        if (clockController != null) {
            // CCTV 시작 전 상태로 원상복귀 (슬라이드쇼 [중지] 버튼과 동일한 패턴)
            clockController.restoreBackgroundSnapshot();
        }
        System.out.println("[ItsCctv] 중지 완료 → 이전 배경 복원");
    }

    // ══════════════════════════════════════════════════════════════════
    //  YouTube 실시간 배경 (yt-dlp + ffmpeg 청크 → JavaFX MediaPlayer)
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
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(owner);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("종료  —  15초 후 자동 닫힘");

        javafx.scene.control.Label msg =
            new javafx.scene.control.Label("KootPanKingThree 를 종료할까요?");
        msg.setStyle("-fx-font-size:14px; -fx-padding: 20 24 10 24;");

        javafx.scene.control.Label countLabel =
            new javafx.scene.control.Label("15초 후 자동으로 닫힙니다.");
        countLabel.setStyle("-fx-font-size:11px; -fx-text-fill:#888; -fx-padding: 0 24 10 24;");

        javafx.scene.control.Button yesBtn = new javafx.scene.control.Button("종료 (Yes)");
        javafx.scene.control.Button noBtn  = new javafx.scene.control.Button("취소 (No)");
        yesBtn.setStyle("-fx-font-size:13px; -fx-pref-width:100;");
        noBtn .setStyle("-fx-font-size:13px; -fx-pref-width:100;");

        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(12, yesBtn, noBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER);
        btnRow.setPadding(new javafx.geometry.Insets(0, 0, 16, 0));

        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(4, msg, countLabel, btnRow);
        root.setStyle("-fx-background-color: white;");
        dlg.setScene(new javafx.scene.Scene(root));

        // 15초 카운트다운
        final int[] sec = {15};
        javafx.animation.Timeline countdown = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), ev -> {
                sec[0]--;
                dlg.setTitle("종료  —  " + sec[0] + "초 후 자동 닫힘");
                countLabel.setText(sec[0] + "초 후 자동으로 닫힙니다.");
                if (sec[0] <= 0) dlg.close();
            })
        );
        countdown.setCycleCount(15);
        countdown.play();

        yesBtn.setOnAction(e -> {
            countdown.stop(); dlg.close();
            saveConfig(); Platform.exit();
        });
        noBtn.setOnAction(e -> { countdown.stop(); dlg.close(); });
        dlg.setOnHidden(e -> countdown.stop());
        dlg.showAndWait();
    }

    // ══════════════════════════════════════════════════════════════════
    //  3. 부팅 자동 실행 — 레지스트리 등록/해제
    // ══════════════════════════════════════════════════════════════════

    private static final String RUN_KEY =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String RUN_VALUE = "KootPanKingThree";

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
        if (isChild) { item.setSelected(false); return; }
        boolean enable = item.isSelected();
        try {
            ProcessBuilder pb;
            if (enable) {
                // 현재 실행 파일 경로 확보
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
                    enable ? "✅ 자동 실행 등록 완료!\n다음 부팅부터 자동으로 시작됩니다."
                           : "✅ 자동 실행 해제 완료!", "자동 실행", 5);
            } else {
                item.setSelected(!enable);
                showAlert(owner, "❌ 자동 실행 " + (enable?"등록":"해제") + " 실패\n관리자 권한이 필요할 수 있습니다.", "자동 실행");
            }
        } catch (Exception ex) {
            item.setSelected(!enable);
            showAlert(owner, "오류: " + ex.getMessage(), "자동 실행");
        }
    }

    private void showAutoCloseAlert(javafx.stage.Stage owner, String message, String title, int seconds) {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(owner);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle(title + "  —  " + seconds + "초 후 닫힘");

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

        final int[] sec = {seconds};
        javafx.animation.Timeline t = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
                sec[0]--;
                dlg.setTitle(title + "  —  " + sec[0] + "초 후 닫힘");
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
    //  4. 생활도구 메뉴
    // ══════════════════════════════════════════════════════════════════

    private javafx.scene.control.Menu buildLifeMenu(javafx.scene.control.ContextMenu popup) {
        javafx.scene.control.Menu menu = new javafx.scene.control.Menu("생활도구");
        String[][] links = {
            {"🌏 생활천문관", "https://astro.kasi.re.kr/index"},
            {"🕐 TIME.IS",    "https://time.is"},
            {"🕰 TIME&DATE",  "https://www.timeanddate.com/worldclock/full.html"},
            {"🌤 날씨",       "https://www.weather.go.kr/w/index.do"},
        };
        for (String[] e : links) {
            String label = e[0], url = e[1];
            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(label);
            item.setOnAction(ev -> openBrowser(url));
            menu.getItems().add(item);
        }
        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());

        javafx.scene.control.MenuItem calItem =
            new javafx.scene.control.MenuItem("📅 만년달력");
        calItem.setOnAction(ev -> openCalendarHtml(
            (javafx.stage.Stage) popup.getOwnerWindow()));

        javafx.scene.control.MenuItem calUpdateItem =
            new javafx.scene.control.MenuItem("🔄 만년달력 갱신");
        calUpdateItem.setOnAction(ev -> updateCalendarHtml(
            (javafx.stage.Stage) popup.getOwnerWindow()));

        menu.getItems().addAll(calItem, calUpdateItem);
        return menu;
    }

    private void openBrowser(String url) {
        try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); }
        catch (Exception ex) { System.out.println("[Browser] 실패: " + ex.getMessage()); }
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
            showAlert(owner, "[만년달력 갱신]을 먼저 실행하세요.", "만년달력");
            return;
        }
        try { java.awt.Desktop.getDesktop().browse(f.toURI()); }
        catch (Exception ex) { showAlert(owner, "브라우저 열기 실패: " + ex.getMessage(), "만년달력"); }
    }

    private void updateCalendarHtml(javafx.stage.Stage owner) {
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirm.initOwner(owner);
        confirm.setTitle("만년달력 갱신");
        confirm.setHeaderText(null);
        confirm.setContentText("임시 공휴일 추가 등 만년달력을 자동 갱신합니다.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != javafx.scene.control.ButtonType.OK) return;
            final String URL =
                "https://raw.githubusercontent.com/GarpsuKim/Calendar_Lunar_-_HTML/main/Calendar.html";
            final java.io.File dest = getCalendarFile();
            new Thread(() -> {
                try {
                    java.net.HttpURLConnection con =
                        (java.net.HttpURLConnection) new java.net.URI(URL).toURL().openConnection();
                    con.setConnectTimeout(10000); con.setReadTimeout(30000); con.connect();
                    int code = con.getResponseCode();
                    if (code != 200) {
                        con.disconnect();
                        Platform.runLater(() -> showAlert(owner,
                            "다운로드 실패 (HTTP " + code + ")", "만년달력 갱신"));
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
                        ok.setTitle("만년달력 갱신");
                        ok.setContentText("갱신 완료.\n저장 위치: " + dest.getAbsolutePath());
                        ok.showAndWait();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> showAlert(owner, "오류: " + ex.getMessage(), "만년달력 갱신"));
                }
            }, "CalendarUpdate").start();
        });
    }

    // ══════════════════════════════════════════════════════════════════
    //  5. 디지탈 시계 상태 접근 / 설정 다이얼로그
    // ══════════════════════════════════════════════════════════════════

    private boolean getDigitalState() {
        return clockController != null && FxGPUNeon.ClockController.getDigitalState(clockController);
    }

    private void setDigitalState(boolean on) {
        if (clockController != null) FxGPUNeon.ClockController.setDigitalState(clockController, on);
    }

    /** 날짜 / 시분초 통합 설정 다이얼로그. */
    void showDigitalSettingsDialog(javafx.stage.Stage owner) {
        if (clockController == null) return;
        FxGPUNeon.AppState st = FxGPUNeon.ClockController.getAppState(clockController);

        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("날짜 / 시분초 설정");

        java.util.List<String> allFonts = javafx.scene.text.Font.getFamilies()
            .stream().limit(200).toList();

        // ══════════════════════════════════════════════════════════
        //  날짜 행 섹션
        // ══════════════════════════════════════════════════════════
        javafx.scene.control.Label dateHeader = new javafx.scene.control.Label("● 날짜");
        dateHeader.setStyle("-fx-font-weight:bold; -fx-font-size:13;");

        javafx.scene.control.CheckBox dateOnOff = new javafx.scene.control.CheckBox("표시");
        dateOnOff.setSelected(st.showFaceDate);

        javafx.scene.control.Label dateFmtLbl = new javafx.scene.control.Label("형식");
        javafx.scene.control.ComboBox<String> dateFmtBox = new javafx.scene.control.ComboBox<>();
        dateFmtBox.getItems().addAll(
            "N월 N일, 요일",
            "YYYY-MM-DD (요일)",
            "MM/DD (요일)",
            "N월 N일"
        );
        dateFmtBox.getSelectionModel().select(st.faceDateFormatIndex);
        dateFmtBox.setPrefWidth(170);

        javafx.scene.control.Label dateFontLbl  = new javafx.scene.control.Label("폰트");
        javafx.scene.control.ComboBox<String> dateFontBox = new javafx.scene.control.ComboBox<>();
        dateFontBox.getItems().addAll(allFonts);
        dateFontBox.setValue(st.faceDateFontFamily);
        dateFontBox.setEditable(false);
        dateFontBox.setPrefWidth(180);

        javafx.scene.control.Label dateSizeLbl = new javafx.scene.control.Label("크기");
        javafx.scene.control.Spinner<Integer> dateSizeSpinner =
            new javafx.scene.control.Spinner<>(8, 72, (int) st.faceDateFontSize);
        dateSizeSpinner.setPrefWidth(72);
        dateSizeSpinner.setEditable(true);

        int drgb = st.faceDateColorRgb;
        javafx.scene.control.Label dateColorLbl = new javafx.scene.control.Label("색");
        javafx.scene.control.ColorPicker dateColorPicker = new javafx.scene.control.ColorPicker(
            javafx.scene.paint.Color.rgb((drgb>>16)&0xFF,(drgb>>8)&0xFF,drgb&0xFF,((drgb>>24)&0xFF)/255.0));
        dateColorPicker.setPrefWidth(130);

        javafx.scene.layout.GridPane dateGrid = new javafx.scene.layout.GridPane();
        dateGrid.setHgap(8); dateGrid.setVgap(6);
        dateGrid.add(dateOnOff,    0, 0, 4, 1);
        dateGrid.add(dateFmtLbl,   0, 1); dateGrid.add(dateFmtBox,      1, 1, 3, 1);
        dateGrid.add(dateFontLbl,  0, 2); dateGrid.add(dateFontBox,     1, 2, 3, 1);
        dateGrid.add(dateSizeLbl,  0, 3); dateGrid.add(dateSizeSpinner, 1, 3);
        dateGrid.add(dateColorLbl, 2, 3); dateGrid.add(dateColorPicker, 3, 3);

        // ══════════════════════════════════════════════════════════
        //  시분초 행 섹션
        // ══════════════════════════════════════════════════════════
        javafx.scene.control.Label timeHeader = new javafx.scene.control.Label("● 시분초");
        timeHeader.setStyle("-fx-font-weight:bold; -fx-font-size:13;");

        javafx.scene.control.CheckBox timeOnOff = new javafx.scene.control.CheckBox("표시");
        timeOnOff.setSelected(st.showDigital);

        javafx.scene.control.Label timeFmtLbl = new javafx.scene.control.Label("형식");
        javafx.scene.control.ComboBox<String> timeFmtBox = new javafx.scene.control.ComboBox<>();
        timeFmtBox.getItems().addAll(
            "HH:mm:SS 오전/오후",
            "HH:mm 오전/오후 [요일]",
            "HH:mm 오전/오후",
            "HH:mm:SS"
        );
        timeFmtBox.getSelectionModel().select(st.digitalFormatIndex);
        timeFmtBox.setPrefWidth(170);

        javafx.scene.control.Label timeFontLbl  = new javafx.scene.control.Label("폰트");
        javafx.scene.control.ComboBox<String> timeFontBox = new javafx.scene.control.ComboBox<>();
        timeFontBox.getItems().addAll(allFonts);
        timeFontBox.setValue(st.digitalFontFamily);
        timeFontBox.setEditable(false);
        timeFontBox.setPrefWidth(180);

        javafx.scene.control.Label timeSizeLbl = new javafx.scene.control.Label("크기");
        javafx.scene.control.Spinner<Integer> timeSizeSpinner =
            new javafx.scene.control.Spinner<>(8, 72, (int) st.digitalFontSize);
        timeSizeSpinner.setPrefWidth(72);
        timeSizeSpinner.setEditable(true);

        int trgb = st.digitalColorRgb;
        javafx.scene.control.Label timeColorLbl = new javafx.scene.control.Label("색");
        javafx.scene.control.ColorPicker timeColorPicker = new javafx.scene.control.ColorPicker(
            javafx.scene.paint.Color.rgb((trgb>>16)&0xFF,(trgb>>8)&0xFF,trgb&0xFF,((trgb>>24)&0xFF)/255.0));
        timeColorPicker.setPrefWidth(130);

        // 스크롤 방향
        javafx.scene.control.Label scrollLbl = new javafx.scene.control.Label("스크롤");
        javafx.scene.control.ToggleGroup dirGroup = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.RadioButton rbFixed = new javafx.scene.control.RadioButton("고정");
        javafx.scene.control.RadioButton rbRTL   = new javafx.scene.control.RadioButton("우→좌");
        javafx.scene.control.RadioButton rbLTR   = new javafx.scene.control.RadioButton("좌→우");
        javafx.scene.control.RadioButton rbPing  = new javafx.scene.control.RadioButton("핑퐁");
        rbFixed.setToggleGroup(dirGroup); rbRTL.setToggleGroup(dirGroup);
        rbLTR.setToggleGroup(dirGroup);   rbPing.setToggleGroup(dirGroup);
        switch (st.digitalScrollDir) {
            case 0 -> rbFixed.setSelected(true);
            case 2 -> rbLTR  .setSelected(true);
            case 3 -> rbPing .setSelected(true);
            default-> rbRTL  .setSelected(true);
        }
        javafx.scene.layout.HBox dirRow = new javafx.scene.layout.HBox(10, rbFixed, rbRTL, rbLTR, rbPing);

        // 스크롤 속도
        javafx.scene.control.Label speedLbl = new javafx.scene.control.Label("속도");
        javafx.scene.control.Slider speedSlider =
            new javafx.scene.control.Slider(0.2, 6.0, st.digitalScrollSpeed);
        speedSlider.setShowTickMarks(true); speedSlider.setShowTickLabels(true);
        speedSlider.setMajorTickUnit(2.0);  speedSlider.setPrefWidth(200);
        javafx.scene.control.Label speedVal =
            new javafx.scene.control.Label(String.format("%.1f", st.digitalScrollSpeed));
        speedSlider.valueProperty().addListener((ob, ov, nv) ->
            speedVal.setText(String.format("%.1f", nv.doubleValue())));

        javafx.scene.layout.GridPane timeGrid = new javafx.scene.layout.GridPane();
        timeGrid.setHgap(8); timeGrid.setVgap(6);
        timeGrid.add(timeOnOff,    0, 0, 4, 1);
        timeGrid.add(timeFmtLbl,   0, 1); timeGrid.add(timeFmtBox,      1, 1, 3, 1);
        timeGrid.add(timeFontLbl,  0, 2); timeGrid.add(timeFontBox,     1, 2, 3, 1);
        timeGrid.add(timeSizeLbl,  0, 3); timeGrid.add(timeSizeSpinner, 1, 3);
        timeGrid.add(timeColorLbl, 2, 3); timeGrid.add(timeColorPicker, 3, 3);
        timeGrid.add(scrollLbl,    0, 4); timeGrid.add(dirRow,          1, 4, 3, 1);
        timeGrid.add(speedLbl,     0, 5);
        timeGrid.add(new javafx.scene.layout.HBox(6, speedSlider, speedVal), 1, 5, 3, 1);

        // ── 버튼 ──────────────────────────────────────────────────
        javafx.scene.control.Button okBtn     = new javafx.scene.control.Button("확인");
        javafx.scene.control.Button cancelBtn = new javafx.scene.control.Button("취소");
        okBtn.setDefaultButton(true); cancelBtn.setCancelButton(true);
        okBtn.setPrefWidth(80);       cancelBtn.setPrefWidth(80);

        okBtn.setOnAction(e -> {
            // 날짜 행
            st.showFaceDate        = dateOnOff.isSelected();
            st.faceDateFormatIndex = dateFmtBox.getSelectionModel().getSelectedIndex();
            st.faceDateFontFamily  = dateFontBox.getValue();
            st.faceDateFontSize    = dateSizeSpinner.getValue();
            javafx.scene.paint.Color dc = dateColorPicker.getValue();
            st.faceDateColorRgb = ((int)(dc.getOpacity()*255)<<24)
                | ((int)(dc.getRed()*255)<<16) | ((int)(dc.getGreen()*255)<<8)
                | (int)(dc.getBlue()*255);

            // 시분초 행
            st.showDigital        = timeOnOff.isSelected();
            st.digitalFormatIndex = timeFmtBox.getSelectionModel().getSelectedIndex();
            st.digitalFontFamily  = timeFontBox.getValue();
            st.digitalFontSize    = timeSizeSpinner.getValue();
            javafx.scene.paint.Color tc = timeColorPicker.getValue();
            st.digitalColorRgb = ((int)(tc.getOpacity()*255)<<24)
                | ((int)(tc.getRed()*255)<<16) | ((int)(tc.getGreen()*255)<<8)
                | (int)(tc.getBlue()*255);
            if (rbFixed.isSelected()) st.digitalScrollDir = 0;
            else if (rbLTR.isSelected()) st.digitalScrollDir = 2;
            else if (rbPing.isSelected()) st.digitalScrollDir = 3;
            else st.digitalScrollDir = 1;
            st.digitalScrollSpeed  = speedSlider.getValue();
            st.digitalScrollOffset = Double.NaN;
            st.faceScrollOffset    = Double.NaN;
            st.facePingPongDir     = 1;

            // faceDateTimeGroup visibility 즉시 반영
            FxGPUNeon.ClockController.setDigitalState(clockController, st.showDigital || st.showFaceDate);

            saveDigitalConfig();
            dlg.close();
        });
        cancelBtn.setOnAction(e -> dlg.close());

        javafx.scene.layout.HBox btnRow = new javafx.scene.layout.HBox(10, okBtn, cancelBtn);
        btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();

        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10);
        root.setPadding(new javafx.geometry.Insets(16, 18, 14, 18));
        root.setStyle("-fx-background-color: white;");
        root.getChildren().addAll(
            dateHeader, dateGrid,
            sep,
            timeHeader, timeGrid,
            btnRow
        );

        dlg.setScene(new javafx.scene.Scene(root));
        dlg.sizeToScene();
        dlg.showAndWait();
    }

    private void saveDigitalConfig() {
        if (clockController == null) return;
        FxGPUNeon.AppState st = FxGPUNeon.ClockController.getAppState(clockController);
        config.setProperty("digital.show",        String.valueOf(st.showDigital));
        config.setProperty("digital.formatIndex", String.valueOf(st.digitalFormatIndex));
        config.setProperty("digital.fontFamily",  st.digitalFontFamily);
        config.setProperty("digital.fontSize",    String.valueOf((int) st.digitalFontSize));
        config.setProperty("digital.colorRgb",    String.valueOf(st.digitalColorRgb));
        config.setProperty("digital.scrollDir",   String.valueOf(st.digitalScrollDir));
        config.setProperty("digital.scrollSpeed", String.valueOf(st.digitalScrollSpeed));
        config.setProperty("faceDate.show",        String.valueOf(st.showFaceDate));
        config.setProperty("faceDate.formatIndex", String.valueOf(st.faceDateFormatIndex));
        config.setProperty("faceDate.fontFamily",  st.faceDateFontFamily);
        config.setProperty("faceDate.fontSize",    String.valueOf((int) st.faceDateFontSize));
        config.setProperty("faceDate.colorRgb",    String.valueOf(st.faceDateColorRgb));
        saveConfig();
    }

    // ══════════════════════════════════════════════════════════════════
    //  YouTube 스트림 URL & 설정 다이얼로그
    // ══════════════════════════════════════════════════════════════════

    /**
     * [YouTube 스트림 URL & 설정] 다이얼로그.
     * - URL 입력 필드
     * - yt-dlp.exe 경로 표시 + 지정 버튼
     * - ffmpeg.exe 경로 표시 + 지정 버튼
     * - [확인] 시 URL + exe 경로 ini 저장 후 YouTube 시작
     * - [취소] 시 아무것도 안 함
     */
    private void showYoutubeSettingsDialog(javafx.stage.Stage owner) {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initOwner(owner);
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("YouTube 스트림 URL & 설정");

        // ── URL 입력 ──────────────────────────────────────────────────
        javafx.scene.control.Label urlLabel = new javafx.scene.control.Label("YouTube URL:");
        javafx.scene.control.TextField urlField = new javafx.scene.control.TextField(youtubeUrl);
        urlField.setPrefWidth(380);
        urlField.setPromptText("https://www.youtube.com/live/...");

        // ── yt-dlp.exe 경로 ───────────────────────────────────────────
        javafx.scene.control.Label ytdlpLabel = new javafx.scene.control.Label("yt-dlp.exe:");
        javafx.scene.control.TextField ytdlpField = new javafx.scene.control.TextField(ytdlpPath);
        ytdlpField.setPrefWidth(300);
        ytdlpField.setEditable(false);
        ytdlpField.setPromptText("(미지정)");

        javafx.scene.control.Button ytdlpBtn = new javafx.scene.control.Button("yt-dlp.exe 지정");
        ytdlpBtn.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("yt-dlp.exe 위치 선택");
            fc.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("yt-dlp", "yt-dlp.exe"),
                new javafx.stage.FileChooser.ExtensionFilter("실행 파일", "*.exe"),
                new javafx.stage.FileChooser.ExtensionFilter("모든 파일", "*.*")
            );
            String cur = ytdlpField.getText().trim();
            if (!cur.isEmpty()) {
                java.io.File prev = new java.io.File(cur).getParentFile();
                if (prev != null && prev.exists()) fc.setInitialDirectory(prev);
            }
            java.io.File chosen = fc.showOpenDialog(dlg);
            if (chosen != null) ytdlpField.setText(chosen.getAbsolutePath());
        });

        // ── ffmpeg.exe 경로 ───────────────────────────────────────────
        String ffmpegCur = ffmpegPath;
        javafx.scene.control.Label ffmpegLabel = new javafx.scene.control.Label("ffmpeg.exe:");
        javafx.scene.control.TextField ffmpegField = new javafx.scene.control.TextField(ffmpegCur);
        ffmpegField.setPrefWidth(300);
        ffmpegField.setEditable(false);
        ffmpegField.setPromptText("(미지정)");

        javafx.scene.control.Button ffmpegBtn = new javafx.scene.control.Button("ffmpeg.exe 지정");
        ffmpegBtn.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("ffmpeg.exe 위치 선택");
            fc.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("ffmpeg", "ffmpeg.exe"),
                new javafx.stage.FileChooser.ExtensionFilter("실행 파일", "*.exe"),
                new javafx.stage.FileChooser.ExtensionFilter("모든 파일", "*.*")
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
        javafx.scene.control.Button okBtn     = new javafx.scene.control.Button("확인");
        javafx.scene.control.Button cancelBtn = new javafx.scene.control.Button("취소");
        okBtn.setDefaultButton(true);
        cancelBtn.setCancelButton(true);

        okBtn.setOnAction(e -> {
            String newUrl    = urlField.getText().trim();
            String newYtdlp  = ytdlpField.getText().trim();
            String newFfmpeg = ffmpegField.getText().trim();

            // exe 경로 ini 저장 (URL과 무관하게 항상 저장)
            ytdlpPath = newYtdlp;
            config.setProperty("youtube.ytdlp.path", newYtdlp);
            ffmpegPath = newFfmpeg;
            config.setProperty("ffmpeg.path", newFfmpeg);

            if (!newUrl.isEmpty()) {
                youtubeUrl = newUrl;
                config.setProperty("youtube.url", newUrl);
                // exe 경로가 모두 지정된 경우만 재생 시작
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
        if (isChild) return;
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
                        FxGPUNeon.cameraActive = true;
                        if (clockController != null) clockController.setCameraFrame(frame);
                    }
                    @Override public void clearYoutubeFrame() {
                        FxGPUNeon.cameraActive = false;
                        if (clockController != null) clockController.setCameraFrame(null);
                    }
                    @Override public void onStatusMessage(String message) {
                        if (clockController != null) clockController.showStatusMessage(message);
                    }
                    @Override public String getSettingsDir() { return SETTINGS_DIR; }
                    @Override public String getYtDlpPath()  { return ytdlpPath; }
                    @Override public String getFfmpegPath()  {
                        return ffmpegPath;
                    }
                });
            // 테마 변경 시 YouTube 중지 콜백 주입
            if (clockController != null)
                clockController.setStopYoutubeCallback(this::stopYoutube);
        }
        ytPlayer.start(url);
    }

    /** YouTube 배경 중지. FX 스레드에서 호출. */
    void stopYoutube() {
        if (ytPlayer != null) ytPlayer.stop();
    }

    // ══════════════════════════════════════════════════════════════════
    //  로컬 MP4 배경 재생
    // ══════════════════════════════════════════════════════════════════

    /**
     * 로컬 MP4 파일을 시계 배경으로 재생.
     *  - ffmpeg(ffmpeg.path) 있으면 모든 코덱 지원 (H.265·AV1·VP9 포함)
     *  - ffmpeg 없으면 JavaFX MediaPlayer 폴백 (H.264 한정)
     * FX 스레드에서 호출.
     */
    void startLocalMp4(File file) {
        if (isChild) return;
        if (file == null || !file.exists()) return;

        stopCamera();
        stopItsCctv();
        stopYoutube();

        // ytPlayer 재사용 (HostCallback 은 YouTube 와 동일)
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
                        FxGPUNeon.cameraActive = true;
                        if (clockController != null) clockController.setCameraFrame(frame);
                    }
                    @Override public void clearYoutubeFrame() {
                        FxGPUNeon.cameraActive = false;
                        if (clockController != null) clockController.setCameraFrame(null);
                    }
                    @Override public void onStatusMessage(String message) {
                        if (clockController != null) clockController.showStatusMessage(message);
                    }
                    @Override public String getSettingsDir() { return SETTINGS_DIR; }
                    @Override public String getYtDlpPath()   { return ytdlpPath; }
                    @Override public String getFfmpegPath()  { return ffmpegPath; }
                });
            if (clockController != null)
                clockController.setStopYoutubeCallback(this::stopYoutube);
        }

        localMp4LastFile = file.getAbsolutePath();
        ytPlayer.startLocalMp4(file);
        ytPlayer.setVolume(localMp4Volume); // ini에서 복원한 볼륨 즉시 적용
        saveConfig();
        System.out.println("[KPK] 로컬 MP4 재생 시작: " + file.getName());
    }

    /** 로컬 MP4 재생 중지 (stopYoutube 와 공유). */
    void stopLocalMp4() {
        stopYoutube();
        localMp4LastFile = "";
        saveConfig();
    }

    /**
     * ITS 교통 CCTV 선택 다이얼로그 (JavaFX).
     * 도시명 필터 필드 + ListView 로 구성.
     * 확인 시 필터 키워드를 mgr.setFilter() 에 반영 → 이전/다음 순환 범위 적용.
     *
     * @return 선택한 CctvItem, 취소 시 null
     */
    private ItsCctvManager.CctvItem showCctvSelectDialog(
            javafx.stage.Stage owner,
            java.util.List<ItsCctvManager.CctvItem> allItems,
            ItsCctvManager mgr) {

        // ── 필터 바 ───────────────────────────────────────────────────
        javafx.scene.control.TextField filterField =
            new javafx.scene.control.TextField();
        filterField.setPromptText("도시·지역 이름 입력 (예: 서울, 부산, 고양)");

        javafx.scene.control.Button filterBtn =
            new javafx.scene.control.Button("필터");

        javafx.scene.control.Label countLabel =
            new javafx.scene.control.Label("총 " + allItems.size() + "개");

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
            countLabel.setText((kw.isEmpty() ? "총 " : "필터 결과 ") + filtered.size() + "개");
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
        dlg.setTitle("ITS 교통 CCTV 선택");
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
	
    /** 현재 프레임을 APP_DIR 에 저장하고 결과 다이얼로그 표시 */
    void captureCamera(javafx.stage.Stage owner) {
        if (camera == null) return;
        java.io.File saveDir = new java.io.File(APP_DIR);
        String saved = camera.capture(saveDir);
        if (saved != null) {
            System.out.println("[Camera] 캡처 저장: " + saved);
            String fileName = new java.io.File(saved).getName();
            javafx.application.Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
				javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.initOwner(owner);
                alert.setTitle("카메라 캡처");
                alert.setHeaderText(null);
                alert.setContentText("📸 저장 완료: img/" + fileName);
                alert.show();
                // 2초 후 자동 닫힘
                javafx.animation.PauseTransition pause =
				new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
                pause.setOnFinished(ev -> alert.close());
                pause.play();
			});
		}
	}
	
    // ══════════════════════════════════════════════════════════════════
    //  동영상 녹화 기능 (JPEG 프레임 → FFmpeg → MP4)
    //
    //  동작 방식:
    //    1) startVideoRecording() 호출 시 임시 폴더(videoTempDir) 생성
    //    2) startCamera() 의 FrameListener 콜백에서 매 프레임을
    //       frame_000000.jpg, frame_000001.jpg ... 으로 저장
    //    3) stopVideoRecording() 호출 시 녹화 플래그 해제 후
    //       백그라운드에서 FFmpeg 로 MP4 인코딩
    //    4) FFmpeg 없으면 프레임 JPEG 들을 ZIP 으로 묶어 폴백 저장
    //    5) 인코딩 완료 후 임시 폴더 삭제
    // ══════════════════════════════════════════════════════════════════
	
    /**
		* 동영상 녹화 시작.
		* @param outputFile 저장할 .mp4 파일 (사용자가 FileChooser로 선택)
	*/
    void startVideoRecording(File outputFile) {
        if (videoRecording || imageSeqRecording) return; // 이미 녹화 중
		
        // ── FFmpeg 탐색 + 동작 검증 ──────────────────────────────
        String ffExe = findFfmpeg();
        boolean ffOk = verifyFfmpeg(ffExe);
		
        if (!ffOk) {
            // FFmpeg 미설정 또는 비정상 → 자동으로 5초 간격 이미지 저장
            System.out.println("[VideoRec] FFmpeg 불가 → 5초 이미지 저장으로 자동 전환");
            String baseName = outputFile.getName().replaceFirst("\\.[^.]+$", "");
            File imgDir = new File(outputFile.getParentFile(), baseName + "_images");
            startImageSequenceRecording(imgDir);
            Platform.runLater(() -> {
                javafx.scene.control.Alert info = new javafx.scene.control.Alert(
				javafx.scene.control.Alert.AlertType.INFORMATION);
                info.setTitle("동영상 저장 불가 — 이미지 저장으로 대체");
                info.setHeaderText(null);
                info.setContentText(
                    "ffmpeg.path 가 미설정되었거나 파일을 찾을 수 없습니다.\n\n" +
                    "5초 간격으로 이미지를 저장합니다.\n" +
                    "저장 폴더: " + imgDir.getAbsolutePath() + "\n\n" +
                    "동영상 저장을 원하면:\n" +
                    "[폰 카메라 연결] → [동영상 도구 지정] 버튼으로\n" +
				"ffmpeg.exe 를 지정하세요.");
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
            showAlert("동영상 녹화", "임시 폴더 생성 실패:\n" + videoTempDir.getAbsolutePath());
            return;
		}
        videoOutputFile = outputFile;
        videoFrameIndex.set(0);
        videoRecording  = true;
        Platform.runLater(() -> {
            if (camVideoItem != null)
			camVideoItem.setText("⏹ 동영상 녹화 중지");
		});
        System.out.println("[VideoRec] 녹화 시작 → " + outputFile.getAbsolutePath());
	}
	
    /**
		* 동영상 녹화 중지 및 MP4 인코딩.
		* 녹화 중이 아니면 아무것도 하지 않는다.
		* 백그라운드(VideoEncoder) 스레드에서 FFmpeg 호출 후 완료 다이얼로그 표시.
	*/
    void stopVideoRecording() {
        if (!videoRecording) return;
        videoRecording = false;
        // 메뉴 텍스트 복원
        Platform.runLater(() -> {
            if (camVideoItem != null)
			camVideoItem.setText("🎬 동영상 녹화 시작");
		});
		
        final File tempDir    = videoTempDir;
        final File outputFile = videoOutputFile;
        videoTempDir    = null;
        videoOutputFile = null;
        int frameCount = videoFrameIndex.get();
		
        System.out.println("[VideoRec] 녹화 중지 — 프레임 수: " + frameCount
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
                System.out.println("[VideoRec] FFmpeg 오류: " + ex.getMessage());
				AppLogger.logException(ex);				
			}
			
            final boolean success = ffmpegOk;
            final File fallbackZip;
			
            if (!success) {
                // 폴백: JPEG 프레임들을 ZIP 으로 저장
                fallbackZip = new File(outputFile.getParentFile(),
				outputFile.getName().replaceFirst("\\.[^.]+$", "") + "_frames.zip");
                packFramesToZip(tempDir, fallbackZip, frameCount);
				} else {
                fallbackZip = null;
			}
			
            deleteDir(tempDir); // 임시 폴더 정리
			
            Platform.runLater(() -> showVideoResult(success, outputFile, fallbackZip, frameCount));
		}, "VideoEncoder").start();
	}
	
    /**
		* FFmpeg 으로 JPEG 시퀀스 → MP4 인코딩.
		* 프레임 레이트는 실제 캡처 FPS 를 추정하여 사용 (기본 15fps).
		* @return 인코딩 성공 여부
	*/
    private boolean encodeWithFfmpeg(File frameDir, File outputFile, int frameCount) throws Exception {
        // FFmpeg 실행 파일 탐색: PATH 에서 ffmpeg / ffmpeg.exe 찾기
        String ffmpegExe = findFfmpeg();
        if (ffmpegExe == null) {
            System.out.println("[VideoRec] FFmpeg 를 찾을 수 없습니다.");
            return false;
		}
        // 입력 패턴: frame_000000.jpg
        String inputPattern = new File(frameDir, "frame_%06d.jpg").getAbsolutePath();
        // 프레임 레이트 추정: 실제 녹화 환경은 ~10~15fps (IP Webcam 기본)
        // 정확한 FPS 계산은 타임스탬프 기반이 필요하나 여기서는 15fps 고정
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
        System.out.println("[VideoRec] FFmpeg 종료 코드: " + exitCode);
        return exitCode == 0 && outputFile.exists() && outputFile.length() > 0;
	}
	
    /**
		* FFmpeg 실행파일 경로 반환.
		* ini 의 ffmpeg.path 키 값만 사용한다. 자동 탐색 없음.
		* [폰 카메라 연결] 다이얼로그의 [동영상 도구 지정] 버튼으로 설정.
		* @return 실행 가능한 ffmpeg 절대경로, 미설정/파일없음이면 null
	*/
    private String findFfmpeg() {
        if (ffmpegPath == null || ffmpegPath.trim().isEmpty()) {
            System.out.println("[FFmpeg] ffmpeg.path 미설정");
            return null;
		}
        File f = new File(ffmpegPath.trim());
        if (f.isFile()) {
            System.out.println("[FFmpeg] 사용: " + f.getAbsolutePath());
            return f.getAbsolutePath();
		}
        System.out.println("[FFmpeg] 파일 없음: " + ffmpegPath);
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
            System.out.println("[FFmpeg] verify 종료 코드: " + code);
            return code == 0;
			} catch (Exception e) {
            System.out.println("[FFmpeg] verify 실패: " + e.getMessage());
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
            System.out.println("[VideoRec] ZIP 폴백 저장: " + zipFile.getAbsolutePath());
			} catch (Exception ex) {
            System.out.println("[VideoRec] ZIP 저장 실패: " + ex.getMessage());
			AppLogger.logException(ex);
		}
	}
	
    /** 임시 디렉토리 재귀 삭제 */
    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        dir.delete();
	}
	
    // ══════════════════════════════════════════════════════════════════
    //  이미지 시퀀스 저장 (FFmpeg 없을 때 대체 모드)
    // ══════════════════════════════════════════════════════════════════
	
    /**
		* 5초 간격 이미지 시퀀스 저장 시작.
		* @param outputDir 이미지를 저장할 폴더
	*/
    void startImageSequenceRecording(File outputDir) {
        if (imageSeqRecording) return;
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            showAlert("이미지 저장", "폴더 생성 실패:\n" + outputDir.getAbsolutePath());
            return;
		}
        imageSeqOutputDir  = outputDir;
        imageSeqLastSaveMs = 0L; // 첫 프레임 즉시 저장
        imageSeqRecording  = true;
        Platform.runLater(() -> {
            if (camVideoItem != null)
			camVideoItem.setText("⏹ 이미지 저장 중지 (5초 간격)");
		});
        System.out.println("[ImgSeq] 시작 → " + outputDir.getAbsolutePath());
	}
	
    /**
		* 이미지 시퀀스 저장 중지. 저장 중이 아니면 아무것도 하지 않는다.
	*/
    void stopImageSequenceRecording() {
        if (!imageSeqRecording) return;
        imageSeqRecording = false;
        Platform.runLater(() -> {
            if (camVideoItem != null)
			camVideoItem.setText("🎬 동영상 녹화 시작");
		});
        File dir = imageSeqOutputDir;
        imageSeqOutputDir = null;
		
        int count = 0;
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles(f -> f.getName().endsWith(".jpg"));
            count = (files != null) ? files.length : 0;
		}
        final int saved = count;
        final File finalDir = dir;
        Platform.runLater(() -> showImageSeqResult(finalDir, saved));
        System.out.println("[ImgSeq] 중지 — 저장 파일 수: " + saved);
	}
	
    /** 이미지 시퀀스 저장 완료 다이얼로그 */
    private void showImageSeqResult(File dir, int count) {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("이미지 저장 완료");
		
        String msg = (dir != null && dir.exists())
		? "✅ 이미지 저장 완료\n\n"
		+ "저장 위치: " + dir.getAbsolutePath() + "\n"
		+ "저장된 파일 수: " + count + "개\n"
		+ "저장 간격: 5초\n\n"
		+ "FFmpeg 설치 후 MP4 저장 가능:\n"
		+ "https://ffmpeg.org/download.html"
		: "❌ 이미지 저장 실패 또는 폴더 없음";
		
        javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(msg);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefSize(420, 200);
		
        javafx.scene.control.Button btnOk = new javafx.scene.control.Button("확인");
        btnOk.setDefaultButton(true);
        btnOk.setOnAction(ev -> dlg.close());
		
        javafx.scene.control.Button btnOpen = new javafx.scene.control.Button("폴더 열기");
        btnOpen.setDisable(dir == null || !dir.exists());
        btnOpen.setOnAction(ev -> {
            try { if (dir != null) java.awt.Desktop.getDesktop().open(dir); }
            catch (Exception ex) { System.out.println("[ImgSeq] 폴더 열기 실패: " + ex.getMessage()); }
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
		new javafx.animation.PauseTransition(javafx.util.Duration.seconds(30));
        pause.setOnFinished(ev -> dlg.close());
        pause.play();
	}
	
    /** 인코딩 완료 결과 다이얼로그 */
    private void showVideoResult(boolean mp4Ok, File outputFile,
		File fallbackZip, int frameCount) {
        javafx.stage.Stage dlg = new javafx.stage.Stage();
        dlg.initStyle(javafx.stage.StageStyle.UTILITY);
        dlg.setAlwaysOnTop(true);
        dlg.setTitle("동영상 저장 완료");
		
        String msg;
        if (mp4Ok) {
            long mb = outputFile.length() / (1024 * 1024);
            msg = "✅ MP4 저장 완료!\n\n"
			+ "파일: " + outputFile.getName() + "\n"
			+ "크기: " + mb + " MB\n"
			+ "프레임 수: " + frameCount + "\n\n"
			+ outputFile.getAbsolutePath();
			} else if (fallbackZip != null && fallbackZip.exists()) {
            long mb = fallbackZip.length() / (1024 * 1024);
            msg = "⚠ FFmpeg 를 찾을 수 없어 MP4 변환 불가.\n"
			+ "JPEG 프레임을 ZIP 으로 저장했습니다.\n\n"
			+ "파일: " + fallbackZip.getName() + "\n"
			+ "크기: " + mb + " MB\n"
			+ "프레임 수: " + frameCount + "\n\n"
			+ "MP4 로 변환하려면 FFmpeg 를 설치하세요:\n"
			+ "https://ffmpeg.org/download.html\n\n"
			+ fallbackZip.getAbsolutePath();
			} else {
            msg = "❌ 저장 실패. 프레임 수: " + frameCount;
		}
		
        javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(msg);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefSize(420, 220);
		
        javafx.scene.control.Button btnOk = new javafx.scene.control.Button("확인");
        btnOk.setDefaultButton(true);
        btnOk.setOnAction(ev -> dlg.close());
		
        // 파일 탐색기에서 열기 버튼
        File showFile = mp4Ok ? outputFile : fallbackZip;
        javafx.scene.control.Button btnOpen = new javafx.scene.control.Button("폴더 열기");
        btnOpen.setDisable(showFile == null || !showFile.exists());
        btnOpen.setOnAction(ev -> {
            try {
                if (showFile != null && showFile.getParentFile() != null)
				java.awt.Desktop.getDesktop().open(showFile.getParentFile());
				} catch (Exception ex) {
                System.out.println("[VideoRec] 폴더 열기 실패: " + ex.getMessage());
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
		
        // 30초 자동 닫힘
        javafx.animation.PauseTransition pause =
		new javafx.animation.PauseTransition(javafx.util.Duration.seconds(30));
        pause.setOnFinished(ev -> dlg.close());
        pause.play();
	}
	
    /** 간단한 정보 알림 다이얼로그 */
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
        item.setOnAction(e -> System.out.println("[Menu] " + name + " (미구현)"));
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
    // days     : next/past 일 때 일 수 (month 계열은 0 — 무시됨)
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
                        showScheduleDialog("📧 구글 캘린더",
                            "구글 Calendar가 초기화되지 않았습니다.\n" +
						"credentials.json 파일을 확인하세요."));
						return;
				}
                java.util.List<GoogleCalendarService.CalendarEvent> events;
                switch (mode) {
                    case "next":
					events = googleCalendarService.getNextDays(days);
					title  = "📧 구글 향후 " + days + "일 일정";
					break;
                    case "past":
					events = googleCalendarService.getPastDays(days);
					title  = "📧 구글 지난 " + days + "일 일정";
					break;
                    case "month":
					events = googleCalendarService.getThisMonth();
					title  = "📧 구글 이번 달 일정";
					break;
                    case "nextmonth":
					events = googleCalendarService.getNextMonth();
					title  = "📧 구글 다음 달 일정";
					break;
                    default:
					events = java.util.Collections.emptyList();
					title  = "📧 구글 캘린더";
				}
                content = GoogleCalendarService.formatEvents(title, events);
				
				} else { // naver
                if (!naverCalendarService.isInitialized()) {
                    javafx.application.Platform.runLater(() ->
                        showScheduleDialog("🟢 네이버 캘린더",
                            "네이버 Calendar가 초기화되지 않았습니다.\n" +
						"clock_settings.ini 의 naver.caldav.id / naver.caldav.password 를 확인하세요."));
						return;
				}
                java.util.List<NaverCalendarService.CalendarEvent> events;
                switch (mode) {
                    case "next":
					events = naverCalendarService.getNextDays(days);
					title  = "🟢 네이버 향후 " + days + "일 일정";
					break;
                    case "past":
					events = naverCalendarService.getPastDays(days);
					title  = "🟢 네이버 지난 " + days + "일 일정";
					break;
                    case "month":
					events = naverCalendarService.getThisMonth();
					title  = "🟢 네이버 이번 달 일정";
					break;
                    case "nextmonth":
					events = naverCalendarService.getNextMonth();
					title  = "🟢 네이버 다음 달 일정";
					break;
                    default:
					events = java.util.Collections.emptyList();
					title  = "🟢 네이버 캘린더";
				}
                content = NaverCalendarService.formatEvents(title, events);
			}
			
            final String dlgTitle   = title;
            final String dlgContent = content;
			
            // 6) showScheduleDialog 공유 — FX 스레드에서 표시
            javafx.application.Platform.runLater(() ->
			showScheduleDialog(dlgTitle, dlgContent));
			
			} catch (Exception e) {
            final String err = e.getMessage();
            javafx.application.Platform.runLater(() ->
			showScheduleDialog("캘린더 오류", "일정 조회 실패:\n" + err));
		}
	}
	
    // ── 시스템 메뉴 동작 ────────────────────────────────────────
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
	
    private void openConfigFile() {
        try {
            java.io.File f = new java.io.File(IniController.getPrimaryConfigFilePath());
            if (!f.exists()) { System.out.println("설정 파일 없음: " + f.getAbsolutePath()); return; }
            IniController.openPrimaryConfigFile();
			} catch (Exception e) {
            System.err.println("설정 파일 열기 실패: " + e.getMessage());
		}
	}
	
    private void downloadIniFile() {
        try {
            java.io.File f = new java.io.File(IniController.getPrimaryConfigFilePath());
            if (f.exists()) { System.out.println("ini 이미 존재: " + f.getAbsolutePath()); return; }
            boolean ok = IniController.ensurePrimaryConfigFile();
            System.out.println("ini 다운로드 " + (ok ? "완료" : "실패") + ": " + f.getAbsolutePath());
			} catch (Exception e) {
            System.err.println("ini 다운로드 실패: " + e.getMessage());
		}
	}
	
    /** 일정 다이얼로그 — 300초 카운트다운 후 자동 닫힘 (초기화 3일 표시 및 팝업 메뉴 공유) */
    private void showScheduleDialog(String title, String content) {
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
		
        // ── 하단 ─────────────────────────────────────────────
        javafx.scene.control.Label countdown = new javafx.scene.control.Label("자동 닫힘: 300초");
        countdown.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
		
        javafx.scene.control.Button closeBtn = new javafx.scene.control.Button("닫기");
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
			}));
			tl.setCycleCount(300);
			holder[0] = tl;
			dlg.setOnHidden(e -> { if (holder[0] != null) holder[0].stop(); });
			
			dlg.show();
			tl.play();
	}
	
    public static void main(String[] args) {
        AppLogger.init();
        AppLogger.writeToFile("[ " + thisProgramName + " ] [main] 시작");
        Application.launch(KootPanKingThree.class, args);
        // ── 정상 종료 ─────────────────────────────────────────────
        if (instance != null && instance.shutdownGuard != null) {
            instance.shutdownGuard.cancel();
		}
        if (instance != null) instance.stopCamera();
        if (instance != null) instance.stopItsCctv();
        if (instance != null) instance.stopYoutube();
        tgMain.sendShutdownNoticeSync(); // 종료 알림 1회
        gmail.sendShutdownNoticeSync();
        System.out.println("[ " + thisProgramName + " ] [main] bye bye");
        AppLogger.close();
	}
}
