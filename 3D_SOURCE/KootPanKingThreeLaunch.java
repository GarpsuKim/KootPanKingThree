import javafx.application.Application;
import javafx.stage.Stage;
import javafx.application.Platform;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class KootPanKingThreeLaunch extends Application {
    // public static AppRestarter appRestarter;                // 재시작 / AppCDS 관리
	public static final GmailSender gmail = GmailSender.getInstance();
	public static TelegramBot tg;
    public static CaptureManager screenCapture;             // 화면 캡처
    public static CaptureManager.Camera camera = null;
    public static ChimeController chimeController;          // 차임벨
    public static GoogleCalendarService googleCalendarService = new GoogleCalendarService();
    public static NaverCalendarService  naverCalendarService  = new NaverCalendarService();
	public static String startupScheduleText = "";
	public static KootPanKingThreeApp app ;
	public static Kakao kakao = new Kakao();
	public static MainWindow mainWindow = new MainWindow();;
	private static AppRestarter.ShutdownGuard shutdownGuard; // 강제 종료 감지 훅
	public KootPanKingThreeLaunch () {
		System.out.println("[KootPanKingThreeLaunch ()]");
	};
	@Override
    public void start(Stage primaryStage) {
		System.out.println("■■■■■ start(Stage primaryStage)");	
		showStartupScheduleTextLater();
		googleCalendarService.setSETTINGS_DIR(AppContext.SETTINGS_DIR);
        mainWindow.theMainWindow(primaryStage);
		telegramSetup();
	}
	private static void showStartupScheduleTextLater() {
		new Thread(() -> {
			try {
				Thread.sleep(30_000);
			} catch (InterruptedException ignored) {}
			int waitCount = 0;
			while (mainWindow == null && waitCount < 200) { // 최대 10초
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignored) {}
				waitCount++;
			}
			if (mainWindow == null) {
				System.out.println("mainWindow 초기화 안 됨");
				return;
			}
			// if (startupScheduleText != null && !startupScheduleText.isEmpty()) {
			javafx.application.Platform.runLater(() -> {
				System.out.println("일정 통보 다이알로그 등록");
				firstFinalGmail();
				KakaoSetup();
				mainWindow.showScheduleDialog("📅 향후 3일 일정", startupScheduleText);
				shutdownGuard = new AppRestarter.ShutdownGuard(gmail, tg); // 강제 종료 감지 훅 등록
				shutdownGuard.register(); 
			});
			// }
		}, "StartupScheduleDelay").start();
	}
    private static void firstFinalGmail() {
		gmail.init();
		gmail.sendStartupNotice(startupScheduleText);
	}
	private static void telegramSetup() {
		googleCalendarService.setSETTINGS_DIR(AppContext.SETTINGS_DIR);
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
				// if (shutdownGuard != null) shutdownGuard.cancel();
				// saveConfig();
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
		
		tg.init();
		
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
		
		tg.calendarService = googleCalendarService;
		tg.naverCalendarService = naverCalendarService;
		
		// ── 네이버 캘린더 자격증명 로드 ───────────────────────
		naverCalendarService.setCredentials(
			AppContext.get("naver.caldav.id", ""),
			AppContext.get("naver.caldav.password", "")
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
					sb.append(GoogleCalendarService.formatEvents("📧 구글 📧", gEvents)).append("\n\n\n");
				}
				if (naverCalendarService.isInitialized()) {
					java.util.List<NaverCalendarService.CalendarEvent> nEvents =
					naverCalendarService.getNextDays(3);
					sb.append(NaverCalendarService.formatEvents("🟢 네이버 🟢", nEvents));
				}
				startupScheduleText = sb.toString().trim();
				if (startupScheduleText.isEmpty()) return;
				tg.sendTelegram(startupScheduleText);
				// javafx.application.Platform.runLater(() ->	showScheduleDialog("📅 향후 3일 일정", startupScheduleText));
				} catch (Exception e) {
				System.out.println("[CalendarInit] 일정 조회 실패: " + e.getMessage());
				AppLogger.logException(e);
			}
		}, "CalendarInit").start();
		
        // this.shutdownGuard = new AppRestarter.ShutdownGuard(gmail, tg);
        // this.appRestarter.buildAppCdsIfNeeded(this::saveConfig);
        // this.shutdownGuard.register();
		
		tg.polling = true;
		tg.startPolling();
		tg.sendStartupNotice();
	}	
	
	private static void KakaoSetup() {
		tg.kakao = kakao;
		kakao.appDir = AppContext.APP_DIR;
		kakao.kakaoRestApiKey   = AppContext.get("kakao.apiKey", "");
		kakao.kakaoClientSecret = AppContext.get("kakao.clientSecret", "");
		kakao.kakaoRefreshToken = AppContext.get("kakao.refreshToken", "");
		
		// 필요하면
		// kakao.onTokenSaved = KootPanKingThree::saveMainIni;
		
		new Thread(() -> {
            if (!kakao.kakaoRestApiKey.isEmpty()
				&& !kakao.kakaoClientSecret.isEmpty()
				&& !kakao.kakaoRefreshToken.isEmpty()) {
				try {	kakao.autoRefreshLogin();	} catch (Exception e) {
					System.out.println("[] Kakao 실패: " + e.getMessage());
					AppLogger.logException(e);	
				}
			}
            // gmail.sendStartupNotice();
            // tg.sendStartupNotice();
		}, "KakaoAutoLogin").start();
	}	
	
	public static void main(String[] args) {
		AppLogger.init();
		AppContext.init();
		/*
			firstFinalGmail();
			telegramSetup();
			KakaoSetup();
		*/
		try {
			System.out.println("[(Application.launch)-------000]");
			launch();
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
	
}