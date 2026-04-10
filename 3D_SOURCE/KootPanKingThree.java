import javafx.application.Application;
import javafx.stage.Stage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
import javafx.application.Platform;

public class KootPanKingThree extends Application {
	public static    AppRestarter.ShutdownGuard shutdownGuard; // 강제 종료 감지 훅
    public static AppRestarter appRestarter;                // 재시작 / AppCDS 관리
    // public static Properties config = new Properties();
    public static IniController iniController ;
	public static	String APP_DIR = "";
	public static	String SETTINGS_DIR = IniController.getDefaultSettingsDir();
	public static	String configFile  = IniController.getPrimaryConfigFilePath();
	public static	IniController ini = new IniController(	APP_DIR, SETTINGS_DIR, configFile,"Local"	);
	public static final GmailSender gmail = GmailSender.getInstance();
	public static	TelegramBot tg;
    public static CaptureManager screenCapture;             // 화면 캡처
    public static CaptureManager.Camera camera = null;
    public static ChimeController chimeController;          // 차임벨
    public static GoogleCalendarService googleCalendarService = new GoogleCalendarService();
    public static NaverCalendarService  naverCalendarService  = new NaverCalendarService();
	public static String startupScheduleText = "";
	public static KootPanKingThreeApp app ;

	@Override
    public void start(Stage stage) {
		System.out.println("[start(Stage stage)-------000]");
	    startupScheduleText() ;
        app = new KootPanKingThreeApp();
        app.startInstance(stage, getParameters().getRaw());
		System.out.println("[start(Stage stage)-------999]");
	}
	private static void startupScheduleText() {
		new Thread(() -> {
			try {
				Thread.sleep(10_000); // 10초 대기
			} catch (InterruptedException ignored) {}
			
			if (startupScheduleText != null && !startupScheduleText.isEmpty()) {
				Platform.runLater(() -> {
					app.showScheduleDialog("📅 향후 3일 일정", startupScheduleText);
				});
			}
		}, "StartupScheduleDelay").start();
	}
    private static void firstFinalGmail() {
		// GmailSender gmail = GmailSender.getInstance();
		gmail.init(ini);
		// gmail.sendStartupNotice(startupScheduleText);  // 쓰레드 동기화 안되어서 텔레그램으로 이동
		// 5. 종료 메일 hook (1회)
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				String now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date());
				gmail.sendShutdownNoticeSync(
					"🔴 [앱 종료] ",
					GmailSender.APP_SIGNATURE + "앱이 종료됩니다.\n\n종료 시각: " + now
				);
				} catch (Exception e) {
				e.printStackTrace();
			}
		}, "Gmail-ShutdownHook"));
	}
	public static void main(String[] args) {
		// 1. 로거 먼저
		AppLogger.init();
		ini.ensureInitialized();
		ini.load();
		telegramSetup();
		firstFinalGmail();
		// 6. 앱 실행
		try {
			System.out.println("[(Application.launch)-------000]");
			Application.launch(KootPanKingThree.class, args);
			System.out.println("[(Application.launch)-------999]");
			} finally {
			System.out.println("[Launcher] bye bye");
		}
		try {
			if (tg != null) {
				tg.stopPolling();   // 반드시 필요 (없으면 추가)
			}
		} catch (Exception ignored) {}
		AppLogger.close();
	}
	private static void telegramSetup() {
		tg = TelegramBot.getInstance(new TelegramBot.CommandHandler() {
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
				System.out.println("=======텔레그램 원격 종료");
				if (shutdownGuard != null) shutdownGuard.cancel();
				saveConfig();
				String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
				gmail.sendShutdownNoticeSync(
					"텔레그램 원격 종료 알림",
					GmailSender.APP_SIGNATURE + "텔레그램 명령으로 PC가 종료됩니다.\n\n종료 시각: " + now
				);
				System.out.println("AppLogger.close");
				AppLogger.close();
				try {
					Runtime.getRuntime().exec(new String[]{"shutdown", "-s", "-f", "-t", "0"});
					} catch (Exception e) {
					System.out.println("[Shutdown] " + e.getMessage());
					AppLogger.logException(e);
				}
			}
			@Override public void rebootPC() {
				System.out.println("=======텔레그램 원격 재시작");
				if (shutdownGuard != null) shutdownGuard.cancel();
				saveConfig();
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
				// KootPanKingThreeApp.this.saveConfig();
			}
		});
		
		tg.init(ini);
		
		/*
			this.appRestarter = KootPanKingThree.getAppRestarter();
			if (this.appRestarter == null) {
            throw new IllegalStateException("AppRestarter not initialized in main()");
			}
			this.appRestarter.setTelegramBot(this.tg);
		*/
        // loadConfig();
        // this.gmail.exeFilePath = !EXE_PATH.isEmpty() ? EXE_PATH : AppLogger.getExeFilePath();
        // this.gmail.logFilePath = AppLogger.getLogFilePath();
		/*
			if (this.appRestarter != null) {
            this.appRestarter.setCachedPaths(
			config.getProperty("app.exePath", ""),
			config.getProperty("app.javawPath", ""),
			config.getProperty("app.jsaPath", "")
			);
			}
		*/
        
		screenCapture = new CaptureManager(null);
		// tg.kakao = kakao;
		tg.appDir = APP_DIR;
		// kakao.appDir = APP_DIR;
		// kakao.onTokenSaved = this::saveConfig;
		googleCalendarService.setAppDir(SETTINGS_DIR);
		tg.calendarService = googleCalendarService;
		tg.naverCalendarService = naverCalendarService;
		
		// ── 네이버 캘린더 자격증명 로드 ───────────────────────
		naverCalendarService.setCredentials(
			ini.getProperties().getProperty("naver.caldav.id", ""),
			ini.getProperties().getProperty("naver.caldav.password", "")
		);
		tg.naverCalendarService = naverCalendarService;
		
		new Thread(() -> {
            if (NaverCalendarService.credentialsExist(
			naverCalendarService.naverId, naverCalendarService.naverPassword)) {
			naverCalendarService.init();
            }
            if (googleCalendarService.credentialsExist()) {
				googleCalendarService.init();
			}
            //  if (tg.myChatId.isEmpty() || tg.botToken.isEmpty()) return;
            try {
				StringBuilder sb = new StringBuilder("📅 향후 3일 일정\n\n");
				if (googleCalendarService.isInitialized()) {
					java.util.List<GoogleCalendarService.CalendarEvent> gEvents =
					googleCalendarService.getNextDays(3);
					sb.append(GoogleCalendarService.formatEvents("📧 구글", gEvents)).append("\n");
				}
				if (naverCalendarService.isInitialized()) {
					java.util.List<NaverCalendarService.CalendarEvent> nEvents =
					naverCalendarService.getNextDays(3);
					sb.append(NaverCalendarService.formatEvents("🟢 네이버", nEvents));
				}
				startupScheduleText = sb.toString().trim();
				if (startupScheduleText.isEmpty()) return;
				gmail.sendStartupNotice(startupScheduleText);
				tg.sendTelegram(startupScheduleText);
				// javafx.application.Platform.runLater(() ->	showScheduleDialog("📅 향후 3일 일정", startupScheduleText));
				} catch (Exception e) {
				System.out.println("[CalendarInit] 일정 조회 실패: " + e.getMessage());
				AppLogger.logException(e);
			}
		}, "CalendarInit").start();
		/*
			new Thread(() -> {
            if (!kakao.kakaoRestApiKey.isEmpty()
			&& !kakao.kakaoClientSecret.isEmpty()
			&& !kakao.kakaoRefreshToken.isEmpty()) {
			try {
			kakao.autoRefreshLogin();
			} catch (Exception e) {
			AppLogger.logException(e);
			}
			}
            // gmail.sendStartupNotice();
            tg.sendStartupNotice();
			}, "KakaoAutoLogin").start();
		*/
		
        // this.shutdownGuard = new AppRestarter.ShutdownGuard(gmail, tg);
        // this.appRestarter.buildAppCdsIfNeeded(this::saveConfig);
        // this.shutdownGuard.register();
		
		// telegramSetup() 끝부분
		tg.polling = true;
		tg.startPolling();
		tg.sendStartupNotice();
	}
}